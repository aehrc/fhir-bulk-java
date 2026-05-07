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
 *
 * @author John Grimes
 */

package au.csiro.filestore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LocalFileStore}.
 */
public class LocalFileStoreTest extends AbstractFileStoreFactoryTest {

  final FileStoreFactory fileStoreFactory = FileStoreFactory.getLocal();

  @BeforeEach
  void setUp() throws IOException {
    fileStore = fileStoreFactory.createFileStore(testRootDir.toString());
  }

  @Test
  void writeAllRefusesToFollowSymlinkAtDestination() throws IOException {
    // A pre-placed symlink at the destination must not be followed; otherwise an attacker who
    // could plant a symlink in the staging directory could redirect downloads to arbitrary files.
    final Path target = testRootDir.resolve("target.txt");
    Files.writeString(target, "original");
    final Path link = testRootDir.resolve("link.txt");
    try {
      Files.createSymbolicLink(link, target);
    } catch (final UnsupportedOperationException | FileSystemException e) {
      // Skip on platforms that disallow symlink creation (e.g. Windows without privilege).
      assumeTrue(false, "Symbolic links are not supported in this environment.");
    }

    assertThrows(IOException.class, () -> fileStore.get(link.toString())
        .writeAll(IOUtils.toInputStream("payload", StandardCharsets.UTF_8)));

    // The symlink target must remain unchanged.
    assertEquals("original", Files.readString(target));
  }

  @Test
  void writeAllRefusesToOverwriteExistingFile() throws IOException {
    // CREATE_NEW prevents accidental or malicious overwrites of existing files in the staging
    // directory.
    final Path existing = testRootDir.resolve("existing.txt");
    Files.writeString(existing, "original");

    assertThrows(IOException.class, () -> fileStore.get(existing.toString())
        .writeAll(IOUtils.toInputStream("payload", StandardCharsets.UTF_8)));

    assertEquals("original", Files.readString(existing));
  }
}
