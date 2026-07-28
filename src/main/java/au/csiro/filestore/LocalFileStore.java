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

package au.csiro.filestore;

import lombok.Value;
import org.apache.commons.io.IOUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import javax.annotation.Nonnull;

/**
 * An implementation of {@link FileStore} that uses the local filesystem.
 *
 * @author Piotr Szul
 * @author John Grimes
 */
class LocalFileStore implements FileStore {

  static final LocalFileStore.Factory FACTORY = new LocalFileStore.Factory();

  static class Factory implements FileStoreFactory {

    private final LocalFileStore localFileStore = new LocalFileStore();

    @Nonnull
    @Override
    public FileStore createFileStore(@Nonnull final String location) {
      return localFileStore;
    }
  }

  @Value
  static class LocalFileHandle implements FileHandle {

    @Nonnull
    File file;

    LocalFileHandle(@Nonnull final File file) {
      this.file = file;
    }

    @Override
    public boolean exists() {
      return file.exists();
    }

    @Override
    public boolean mkdirs() {
      // File.mkdirs() returns false both when creation fails and when the directory is already
      // there, so the directory has to be checked for as well to report success correctly.
      return file.mkdirs() || file.isDirectory();
    }

    @Nonnull
    @Override
    public FileHandle child(@Nonnull final String childName) {
      return new LocalFileHandle(new File(file, childName));
    }

    @Nonnull
    @Override
    public String getLocation() {
      return file.getPath();
    }

    @Nonnull
    @Override
    public URI toUri() {
      return file.toURI();
    }

    @Override
    public long writeAll(@Nonnull final InputStream is) throws IOException {
      // Open with CREATE_NEW so that an existing entry at the destination is never written over,
      // which covers a symlink pre-placed under the name of a file about to be downloaded.
      // Symlinks encountered higher up the path are still followed, as pointing an output
      // directory at other storage through a symlink is a legitimate thing to do.
      try (final OutputStream os = Files.newOutputStream(file.toPath(),
          StandardOpenOption.CREATE_NEW)) {
        return IOUtils.copyLarge(is, os);
      }
    }

    @Override
    @Nonnull
    public String toString() {
      return getLocation();
    }

    @Nonnull
    public static LocalFileHandle of(@Nonnull final String location) {
      return new LocalFileHandle(new File(location));
    }
  }

  @Nonnull
  @Override
  public FileHandle get(@Nonnull final String location) {
    return LocalFileHandle.of(location);
  }

  @Override
  public void close() {
    // do nothing
  }

}
