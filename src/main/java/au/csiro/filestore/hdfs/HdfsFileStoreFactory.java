/*
 * Copyright 2023 Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.csiro.filestore.hdfs;

import au.csiro.filestore.FileStore;
import au.csiro.filestore.FileStoreFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import javax.annotation.Nonnull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

/**
 * File store factory based on Apache Hadoop HDFS FileSystem.
 *
 * <p>Unless a filesystem is supplied explicitly through {@link #forFileSystem(FileSystem)},
 * filesystems are resolved with {@link FileSystem#get(URI, Configuration)}, which returns an
 * instance from the JVM-wide Hadoop filesystem cache that this library shares with the rest of the
 * host application. Either way the filesystem is borrowed rather than owned, so closing a store
 * never closes the filesystem behind it. Releasing instances is the host application's business;
 * Hadoop closes whatever remains in the cache at JVM shutdown, unless the application has turned
 * that off by setting {@code fs.automatic.close} to false.
 *
 * <p>That obligation becomes a real one in a server that impersonates its users. The cache is keyed
 * on scheme, authority and user, so each distinct user reaching a destination creates an instance
 * that lives until the JVM exits. A server using {@code UserGroupInformation.doAs} should call
 * {@code FileSystem.closeAllForUGI} when it tears a user's session down, or instances will
 * accumulate for as long as the process runs.
 *
 * <p>As with {@link FileSystem#get(URI, Configuration)}, the configuration held by this factory is
 * respected only on a cache miss. Because the key is scheme, authority and user alone, a
 * destination the host application has already opened resolves to that existing instance and the
 * configuration is ignored entirely. It takes effect only where the cache has to construct
 * something, which is why {@link #HdfsFileStoreFactory(Configuration)} should be given the
 * configuration the application itself uses, rather than relying on
 * {@link #HdfsFileStoreFactory()}.
 */
public class HdfsFileStoreFactory implements FileStoreFactory {

  @Nonnull
  private final Configuration configuration;

  /**
   * Creates a factory that resolves filesystems through the Hadoop filesystem cache.
   *
   * <p>Pass the configuration the host application already holds — under Spark, the Hadoop
   * configuration carrying its {@code spark.hadoop.*} settings — rather than a freshly
   * constructed one. It is consulted only when the cache has to build a filesystem, but that is
   * precisely when getting it wrong matters, because what gets built is then cached under the
   * destination's key and handed to everything that asks for it afterwards, the host application
   * included.
   *
   * @param configuration the configuration to construct filesystems from, on a cache miss
   */
  public HdfsFileStoreFactory(@Nonnull final Configuration configuration) {
    this.configuration = configuration;
  }

  /**
   * Creates a factory that resolves filesystems through the Hadoop filesystem cache, using a
   * default {@link Configuration} where the cache has to construct one.
   *
   * <p>A default configuration sees only what is on the classpath, in {@code core-site.xml} and its
   * companions. It sees nothing the host application has configured programmatically, so on a cache
   * miss for a destination such as {@code s3a://} the filesystem is built without the application's
   * credentials, endpoint or region, and will usually fail to authenticate. The failure also
   * outlives this export: the misconfigured instance is left in the JVM-wide cache under the
   * destination's key, and the host application resolves that same instance from then on.
   *
   * <p>Prefer {@link #HdfsFileStoreFactory(Configuration)}. This constructor is only appropriate
   * where the classpath configuration is genuinely complete, such as a standalone command line
   * invocation, or where the destination filesystem is known to be open already.
   */
  public HdfsFileStoreFactory() {
    this(new Configuration());
  }

  /**
   * Creates a factory that hands out stores over a filesystem supplied by the caller.
   *
   * <p>Use this when the calling application holds a filesystem that the Hadoop cache will not
   * return for the destination, such as a decorated instance or one opened under a different user.
   * The supplied filesystem is borrowed on the same terms as a cached one: the stores never close
   * it, and it remains the caller's to release.
   *
   * <p>The stores this factory creates ignore the location they are asked for and route every
   * operation through the supplied filesystem. A location naming a different filesystem fails with
   * Hadoop's "Wrong FS" error, but only where it names one: a location with no scheme is resolved
   * against the supplied filesystem without complaint, whatever was intended.
   *
   * <p>Note that the supplied filesystem also fixes the identity that writes are performed as, in
   * place of the one the cache would have selected. A Hadoop filesystem captures its user when it
   * is constructed and keeps it for every later operation, so a server that impersonates its users
   * through {@code UserGroupInformation.doAs} loses that attribution here: every export writes as
   * whichever user opened the supplied instance, whoever requested it. This fails silently, so
   * prefer a factory built with {@link #HdfsFileStoreFactory(Configuration)} where per-user
   * attribution matters, and let the cache select the filesystem per user.
   *
   * @param fileSystem the filesystem to write through
   * @return a factory that creates stores over the supplied filesystem
   */
  @Nonnull
  public static FileStoreFactory forFileSystem(@Nonnull final FileSystem fileSystem) {
    return location -> new HdfsFileStore(fileSystem);
  }

  @Nonnull
  @Override
  public FileStore createFileStore(@Nonnull final String location) throws IOException {
    return new HdfsFileStore(FileSystem.get(URI.create(location), configuration));
  }

  @Slf4j
  static class HdfsFileStore implements FileStore {

    @Nonnull
    private final FileSystem fileSystem;

    /**
     * Creates a store over a filesystem supplied by the caller. The filesystem is borrowed: the
     * caller retains ownership and remains responsible for closing it.
     *
     * @param fileSystem the filesystem to use
     */
    HdfsFileStore(@Nonnull final FileSystem fileSystem) {
      this.fileSystem = fileSystem;
    }

    @Nonnull
    @Override
    public FileHandle get(@Nonnull final String location) {
      return new HdfsFileHandle(new Path(location));
    }

    @Override
    public void close() {
      // The filesystem is borrowed, never owned, so this store does not close it. It comes either
      // from the caller or from the JVM-wide Hadoop cache, which is shared with the host
      // application; closing a cached instance evicts it and leaves the application holding a
      // closed filesystem. Cached instances are released by Hadoop's own shutdown hook.
      //
      // Files are already durable without this: each write is committed by the try-with-resources
      // around fileSystem.create in writeAll, which finalises the file through the HDFS write
      // pipeline and completes the upload on object stores.
    }

    @Value
    class HdfsFileHandle implements FileHandle {

      @Nonnull
      Path path;

      @Override
      public boolean exists() {
        try {
          return fileSystem.exists(path);
        } catch (final IOException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public boolean mkdirs() {
        try {
          return fileSystem.mkdirs(path);
        } catch (final IOException e) {
          throw new RuntimeException(e);
        }
      }

      @Nonnull
      @Override
      public FileHandle child(@Nonnull final String childName) {
        return new HdfsFileHandle(new Path(path, childName));
      }

      @Nonnull
      @Override
      public String getLocation() {
        return path.toString();
      }

      @Nonnull
      @Override
      public URI toUri() {
        return path.toUri();
      }

      @Override
      public long writeAll(@Nonnull final InputStream is) throws IOException {
        try (final OutputStream os = fileSystem.create(path)) {
          return IOUtils.copyLarge(is, os);
        }
      }

      @Override
      @Nonnull
      public String toString() {
        return getLocation();
      }
    }
  }
}
