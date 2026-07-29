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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import au.csiro.filestore.AbstractFileStoreFactoryTest;
import au.csiro.filestore.FileStore;
import au.csiro.filestore.FileStoreFactory;
import java.io.IOException;
import java.net.URI;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HdfsFileStoreFactory}.
 */
public class HdfsFileStoreFactoryTest extends AbstractFileStoreFactoryTest {

  final FileStoreFactory fileStoreFactory = new HdfsFileStoreFactory();

  @BeforeEach
  void setUp() throws IOException {
    fileStore = fileStoreFactory.createFileStore(testRootDir.toString());
  }

  /**
   * Closing a store must not disturb the filesystem instance that the host application shares
   * through the Hadoop filesystem cache. Closing a cached instance evicts it from the cache, so the
   * application's reference is left closed and a later lookup returns a different instance. For
   * schemes that guard against use after close, such as S3A, that leaves the application unable to
   * reach the scheme at all.
   */
  @Test
  void testClosingStoreLeavesTheCachedFileSystemInPlace() throws IOException {
    final URI location = URI.create(testRootDir.toString());
    final FileSystem cached = FileSystem.get(location, new Configuration());

    try (final FileStore store = fileStoreFactory.createFileStore(testRootDir.toString())) {
      store.get(testRootDir.resolve("some-directory").toString()).mkdirs();
    }

    // The cached instance must still be the one the application gets, and must not have been
    // evicted by the store closing it.
    assertSame(cached, FileSystem.get(location, new Configuration()));
  }

  /**
   * The filesystem is always borrowed, whether it came from the caller or from the Hadoop cache, so
   * closing a store must never close it.
   */
  @Test
  void testClosingStoreDoesNotCloseTheFileSystem() throws IOException {
    final FileSystem borrowed = mock(FileSystem.class);

    new HdfsFileStoreFactory.HdfsFileStore(borrowed).close();

    verify(borrowed, never()).close();
  }
}
