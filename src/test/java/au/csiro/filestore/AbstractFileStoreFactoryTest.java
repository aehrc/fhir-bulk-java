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

import au.csiro.filestore.FileStore.FileHandle;
import au.csiro.filestore.hdfs.HdfsFileStoreFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HdfsFileStoreFactory}.
 */
public abstract class AbstractFileStoreFactoryTest {

  protected FileStore fileStore;

  @TempDir
  protected Path testRootDir;
  
  @Test
  void testExistsWorks() {
    assertTrue(fileStore.get(testRootDir.toString()).exists());
    assertFalse(fileStore.get(testRootDir.resolve("non-existent").toString()).exists());
  }

  @Test
  void testChildWorks() {
    assertEquals(testRootDir.resolve("child").toString(),
        fileStore.get(testRootDir.toString()).child("child").getLocation());
  }

  @Test
  void testMkdirWorksANewDirectory() {
    assertTrue(fileStore.get(testRootDir.resolve("foo/bar").toString()).mkdirs());
    final File file = testRootDir.resolve("foo/bar").toFile();
    assertTrue(file.exists());
    assertTrue(file.isDirectory());
  }

  @Test
  void testMkdirsForAnExistingDirectory() {
    FileHandle existingDirHandle  = fileStore.get(testRootDir.toString());
    assertTrue(existingDirHandle.exists());
    assertTrue(existingDirHandle.mkdirs());
    final File file = testRootDir.toFile();
    assertTrue(file.exists());
    assertTrue(file.isDirectory());
  }

  @Test
  void testWriteAllWorks() throws IOException {
    final File file = testRootDir.resolve("file").toFile();
    fileStore.get(file.getPath())
        .writeAll(IOUtils.toInputStream("Hello, world!", StandardCharsets.UTF_8));
    assertTrue(file.exists());
    assertEquals("Hello, world!", FileUtils.readFileToString(file, StandardCharsets.UTF_8));
  }

  @Test
  void testToUriWorks() {
    Assertions.assertEquals("/foo/bar", FileHandle.ofLocal("/foo/bar").getLocation());
    Assertions.assertEquals(URI.create("file:/foo/bar"),
        FileHandle.ofLocal("/foo/bar").toUri());
  }
}
