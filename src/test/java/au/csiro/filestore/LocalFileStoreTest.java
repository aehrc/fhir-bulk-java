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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LocalFileStore}.
 *
 * @author Piotr Szul
 * @author John Grimes
 */
public class LocalFileStoreTest extends AbstractFileStoreFactoryTest {

  final FileStoreFactory fileStoreFactory = FileStoreFactory.getLocal();

  @BeforeEach
  void setUp() throws IOException {
    fileStore = fileStoreFactory.createFileStore(testRootDir.toString());
  }

  @Test
  void testWriteAllRefusesToOverwriteExistingFile() throws IOException {
    final Path existing = testRootDir.resolve("existing.txt");
    Files.writeString(existing, "original");

    assertThrows(FileAlreadyExistsException.class, () -> fileStore.get(existing.toString())
        .writeAll(IOUtils.toInputStream("payload", StandardCharsets.UTF_8)));

    assertEquals("original", Files.readString(existing));
  }

  @Test
  void testWriteAllRefusesToWriteOverSymlinkAtDestination() throws IOException {
    // A symlink already present under the name of a file about to be written is an existing entry
    // as far as CREATE_NEW is concerned, so it is refused rather than followed to its target.
    final Path target = testRootDir.resolve("target.txt");
    Files.writeString(target, "original");
    final Path link = createSymbolicLink(testRootDir.resolve("link.txt"), target);

    assertThrows(FileAlreadyExistsException.class, () -> fileStore.get(link.toString())
        .writeAll(IOUtils.toInputStream("payload", StandardCharsets.UTF_8)));

    assertEquals("original", Files.readString(target));
  }

  @Test
  void testWriteAllWorksThroughSymlinkedParentDirectory() throws IOException {
    // Reaching other storage through a symlinked directory is a legitimate layout, so symlinks
    // above the file being written must still be followed.
    final Path storageDir = Files.createDirectory(testRootDir.resolve("storage"));
    final Path linkedDir = createSymbolicLink(testRootDir.resolve("linked"), storageDir);

    fileStore.get(linkedDir.resolve("Patient.0000.ndjson").toString())
        .writeAll(IOUtils.toInputStream("payload", StandardCharsets.UTF_8));

    assertEquals("payload", Files.readString(storageDir.resolve("Patient.0000.ndjson")));
  }

  @Test
  void testMkdirsSucceedsWhenDirectoryAlreadyExists() throws IOException {
    // File.mkdirs() returns false for an existing directory, which must not be reported as a
    // failure to create it.
    final Path existing = Files.createDirectory(testRootDir.resolve("existing-dir"));

    assertTrue(fileStore.get(existing.toString()).mkdirs());
  }

  @Test
  void testMkdirsFailsWhenDirectoryCannotBeCreated() throws IOException {
    // A dangling symlink is neither creatable as a directory nor already one, so mkdirs() has to
    // report the failure rather than let the export proceed against a directory that is not there.
    final Path dangling = createSymbolicLink(testRootDir.resolve("dangling"),
        testRootDir.resolve("absent"));

    assertFalse(fileStore.get(dangling.toString()).mkdirs());
  }

  /**
   * Creates a symbolic link, skipping the calling test on platforms that do not permit it.
   */
  @Nonnull
  private static Path createSymbolicLink(@Nonnull final Path link, @Nonnull final Path target)
      throws IOException {
    try {
      return Files.createSymbolicLink(link, target);
    } catch (final UnsupportedOperationException | FileSystemException ex) {
      // Skip on platforms that disallow symlink creation (e.g. Windows without privilege).
      assumeTrue(false, "Symbolic links are not supported in this environment.");
      throw new AssertionError("unreachable");
    }
  }
}
