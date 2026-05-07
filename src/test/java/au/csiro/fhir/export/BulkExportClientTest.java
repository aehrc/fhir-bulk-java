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

package au.csiro.fhir.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import au.csiro.fhir.export.download.UrlDownloadTemplate.UrlDownloadEntry;
import au.csiro.fhir.export.ws.AssociatedData;
import au.csiro.fhir.export.ws.BulkExportRequest;
import au.csiro.fhir.export.ws.BulkExportResponse;
import au.csiro.fhir.export.ws.BulkExportResponse.FileItem;
import au.csiro.filestore.FileStore.FileHandle;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BulkExportClient}.
 */
public class BulkExportClientTest {

  BulkExportClient client = BulkExportClient.builder()
      .withFhirEndpointUrl("http://example.com")
      .withOutputDir("output-dir")
      .build();

  @Test
  void testMapsMultiPartResourceToSeparateFiles() {

    final BulkExportResponse response = BulkExportResponse.builder()
        .transactionTime(Instant.now())
        .request("fake-request")
        .output(List.of(
            new FileItem("Condition", "http:/foo.bar/1", 10),
            new FileItem("Condition", "http:/foo.bar/2", 10),
            new FileItem("Condition", "http:/foo.bar/3", 10)
        ))
        .deleted(Collections.emptyList())
        .error(Collections.emptyList())
        .build();

    final List<UrlDownloadEntry> downloadUrls = client.getUrlDownloadEntries(
        response, FileHandle.ofLocal("output-dir"));

    assertEquals(
        List.of(
            new UrlDownloadEntry(URI.create("http:/foo.bar/1"),
                FileHandle.ofLocal("output-dir/Condition.0000.ndjson")),
            new UrlDownloadEntry(URI.create("http:/foo.bar/2"),
                FileHandle.ofLocal("output-dir/Condition.0001.ndjson")),
            new UrlDownloadEntry(URI.create("http:/foo.bar/3"),
                FileHandle.ofLocal("output-dir/Condition.0002.ndjson"))
        ),
        downloadUrls
    );
  }

  @Test
  void testMapsDifferentResourceToSeparateFiles() {
    // When outputExtension is explicitly set, it should override the inferred extension.
    final BulkExportClient client = BulkExportClient.builder()
        .withFhirEndpointUrl("http://example.com")
        .withOutputDir("output-dir")
        .withOutputFormat("application/x-custom")
        .withOutputExtension("xjson")
        .build();

    final BulkExportResponse response = BulkExportResponse.builder()
        .transactionTime(Instant.now())
        .request("fake-request")
        .output(List.of(
            new FileItem("Patient", "http:/foo.bar/1", 10),
            new FileItem("Condition", "http:/foo.bar/2", 10),
            new FileItem("Observation", "http:/foo.bar/3", 10)
        ))
        .deleted(Collections.emptyList())
        .error(Collections.emptyList())
        .build();

    final List<UrlDownloadEntry> downloadUrls = client.getUrlDownloadEntries(
        response, FileHandle.ofLocal("output-dir"));

    assertEquals(
        List.of(
            new UrlDownloadEntry(URI.create("http:/foo.bar/1"),
                FileHandle.ofLocal("output-dir/Patient.0000.xjson")),
            new UrlDownloadEntry(URI.create("http:/foo.bar/2"),
                FileHandle.ofLocal("output-dir/Condition.0000.xjson")),
            new UrlDownloadEntry(URI.create("http:/foo.bar/3"),
                FileHandle.ofLocal("output-dir/Observation.0000.xjson"))
        ),
        downloadUrls
    );
  }

  @Test
  void testBuildsRequestWithRequestedAssociatedData() {

    final BulkExportClient client = BulkExportClient.builder()
        .withFhirEndpointUrl("http://example.com")
        .withOutputDir("output-dir")
        .withOutputExtension("xjson")
        .withIncludeAssociatedData(List.of("RelevantProvenanceResources", "_customXXX"))
        .withIncludeAssociatedDatum(AssociatedData.custom("customYYY"))
        .build();

    assertEquals(BulkExportRequest.builder()
            ._outputFormat("application/fhir+ndjson")
            .includeAssociatedData(List.of(AssociatedData.RELEVANT_PROVENANCE_RESOURCES,
                AssociatedData.custom("customXXX"), AssociatedData.custom("customYYY")))
            .build(),
        client.buildBulkExportRequest());
  }

  @Test
  void testExtensionFromFormatReturnsNdjsonForStandardMimeType() {
    // The standard FHIR Bulk Data MIME type should produce ndjson extension.
    assertEquals("ndjson",
        BulkExportClient.extensionFromFormat("application/fhir+ndjson", null));
  }

  @Test
  void testExtensionFromFormatReturnsNdjsonForAlternativeMimeType() {
    // The alternative NDJSON MIME type should also produce ndjson extension.
    assertEquals("ndjson",
        BulkExportClient.extensionFromFormat("application/x-ndjson", null));
  }

  @Test
  void testExtensionFromFormatReturnsNdjsonForShortForm() {
    // The short form "ndjson" should produce ndjson extension.
    assertEquals("ndjson", BulkExportClient.extensionFromFormat("ndjson", null));
  }

  @Test
  void testExtensionFromFormatReturnsParquetForMimeType() {
    // The Parquet MIME type should produce parquet extension.
    assertEquals("parquet",
        BulkExportClient.extensionFromFormat("application/vnd.apache.parquet", null));
  }

  @Test
  void testExtensionFromFormatReturnsParquetForShortForm() {
    // The short form "parquet" should produce parquet extension.
    assertEquals("parquet", BulkExportClient.extensionFromFormat("parquet", null));
  }

  @Test
  void testExtensionFromFormatReturnsNdjsonForUnknownFormat() {
    // Unknown formats should return the default ndjson extension when no override is set.
    assertEquals("ndjson",
        BulkExportClient.extensionFromFormat("application/unknown", null));
  }

  @Test
  void testExtensionFromFormatIsCaseInsensitive() {
    // Format matching should be case-insensitive.
    assertEquals("ndjson",
        BulkExportClient.extensionFromFormat("APPLICATION/FHIR+NDJSON", null));
    assertEquals("parquet",
        BulkExportClient.extensionFromFormat("APPLICATION/VND.APACHE.PARQUET", null));
  }

  @Test
  void testExtensionFromFormatReturnsOverrideWhenSet() {
    // When an override is explicitly set, it should be used regardless of the format.
    assertEquals("custom",
        BulkExportClient.extensionFromFormat("application/fhir+ndjson", "custom"));
    assertEquals("custom",
        BulkExportClient.extensionFromFormat("application/vnd.apache.parquet", "custom"));
    assertEquals("custom",
        BulkExportClient.extensionFromFormat("application/unknown", "custom"));
  }

  @Test
  void testUnknownFormatDefaultsToNdjsonExtension() {
    // A client configured with an unknown format and no explicit outputExtension should
    // default to ndjson file extensions.
    final BulkExportClient unknownFormatClient = BulkExportClient.builder()
        .withFhirEndpointUrl("http://example.com")
        .withOutputDir("output-dir")
        .withOutputFormat("application/x-custom")
        .build();

    final BulkExportResponse response = BulkExportResponse.builder()
        .transactionTime(Instant.now())
        .request("fake-request")
        .output(List.of(
            new FileItem("Patient", "http:/foo.bar/1", 10)
        ))
        .deleted(Collections.emptyList())
        .error(Collections.emptyList())
        .build();

    final List<UrlDownloadEntry> downloadUrls = unknownFormatClient.getUrlDownloadEntries(
        response, FileHandle.ofLocal("output-dir"));

    assertEquals(
        List.of(
            new UrlDownloadEntry(URI.create("http:/foo.bar/1"),
                FileHandle.ofLocal("output-dir/Patient.0000.ndjson"))
        ),
        downloadUrls
    );
  }

  @Test
  void testParquetFormatProducesParquetFileExtensions() {
    // A client configured with Parquet format should produce .parquet file extensions.
    final BulkExportClient parquetClient = BulkExportClient.builder()
        .withFhirEndpointUrl("http://example.com")
        .withOutputDir("output-dir")
        .withOutputFormat("application/vnd.apache.parquet")
        .build();

    final BulkExportResponse response = BulkExportResponse.builder()
        .transactionTime(Instant.now())
        .request("fake-request")
        .output(List.of(
            new FileItem("Patient", "http:/foo.bar/1", 10),
            new FileItem("Observation", "http:/foo.bar/2", 10)
        ))
        .deleted(Collections.emptyList())
        .error(Collections.emptyList())
        .build();

    final List<UrlDownloadEntry> downloadUrls = parquetClient.getUrlDownloadEntries(
        response, FileHandle.ofLocal("output-dir"));

    assertEquals(
        List.of(
            new UrlDownloadEntry(URI.create("http:/foo.bar/1"),
                FileHandle.ofLocal("output-dir/Patient.0000.parquet")),
            new UrlDownloadEntry(URI.create("http:/foo.bar/2"),
                FileHandle.ofLocal("output-dir/Observation.0000.parquet"))
        ),
        downloadUrls
    );
  }

  @Test
  void testRejectsTypeWithPathTraversal() {
    // A manifest type containing directory traversal sequences must be rejected.
    final BulkExportResponse response = BulkExportResponse.builder()
        .transactionTime(Instant.now())
        .request("fake-request")
        .output(List.of(
            new FileItem("Patient", "http:/foo.bar/1", 10),
            new FileItem("../../../secret", "http:/foo.bar/2", 10)
        ))
        .deleted(Collections.emptyList())
        .error(Collections.emptyList())
        .build();

    final BulkExportException ex = assertThrows(BulkExportException.class,
        () -> client.getUrlDownloadEntries(response, FileHandle.ofLocal("output-dir")));

    assertTrue(ex.getMessage().contains("invalid path separators"));
  }

  @Test
  void testRejectsTypeWithAbsolutePath() {
    // A manifest type resolving to an absolute path must be rejected with the absolute-path
    // message specifically (the absolute-path check runs before the separator check).
    final BulkExportResponse response = BulkExportResponse.builder()
        .transactionTime(Instant.now())
        .request("fake-request")
        .output(List.of(
            new FileItem("/etc/passwd", "http:/foo.bar/1", 10)
        ))
        .deleted(Collections.emptyList())
        .error(Collections.emptyList())
        .build();

    final BulkExportException ex = assertThrows(BulkExportException.class,
        () -> client.getUrlDownloadEntries(response, FileHandle.ofLocal("output-dir")));

    assertTrue(ex.getMessage().contains("absolute path"),
        "Expected absolute-path rejection but got: " + ex.getMessage());
  }

  @Test
  void testConfinesTypeWithUrlEncodedTraversal() {
    // URL-encoded traversal sequences in the type field are not decoded by the local file
    // system, so the resulting file remains inside the output directory.
    final BulkExportResponse response = BulkExportResponse.builder()
        .transactionTime(Instant.now())
        .request("fake-request")
        .output(List.of(
            new FileItem("..%2f..%2fsecret", "http:/foo.bar/1", 10)
        ))
        .deleted(Collections.emptyList())
        .error(Collections.emptyList())
        .build();

    final FileHandle outputDir = FileHandle.ofLocal("output-dir");
    final List<UrlDownloadEntry> entries = client.getUrlDownloadEntries(response, outputDir);

    // The destination must be a direct child of the staging directory, with the URL-encoded
    // sequence preserved as a single filename component (i.e. not decoded into traversal).
    assertEquals(1, entries.size());
    final Path expected = Paths.get("output-dir", "..%2f..%2fsecret.0000.ndjson");
    final Path actual = Paths.get(entries.get(0).getDestination().getLocation());
    assertEquals(expected, actual);
    assertEquals(Paths.get("output-dir").toAbsolutePath().normalize(),
        actual.toAbsolutePath().normalize().getParent());
  }

  @Test
  void testAcceptsRemoteSchemeWhenParentHasQueryString() {
    // The non-file scheme branch must not be confused by query strings or fragments on the
    // parent URI: only scheme, authority and path participate in containment.
    final BulkExportResponse response = BulkExportResponse.builder()
        .transactionTime(Instant.now())
        .request("fake-request")
        .output(List.of(
            new FileItem("Patient", "http:/foo.bar/1", 10)
        ))
        .deleted(Collections.emptyList())
        .error(Collections.emptyList())
        .build();

    final FileHandle outputDir = new RemoteFileHandle(
        URI.create("s3://bucket/output?versionId=abc"));

    final List<UrlDownloadEntry> entries = client.getUrlDownloadEntries(response, outputDir);

    assertEquals(1, entries.size());
    assertEquals("s3://bucket/output/Patient.0000.ndjson",
        entries.get(0).getDestination().toUri().toString());
  }

  @Test
  void testRejectsRemoteSchemeWithMismatchedAuthority() {
    // A child whose URI differs in authority must be rejected even when the path appears to
    // sit beneath the parent path.
    final BulkExportResponse response = BulkExportResponse.builder()
        .transactionTime(Instant.now())
        .request("fake-request")
        .output(List.of(
            new FileItem("Patient", "http:/foo.bar/1", 10)
        ))
        .deleted(Collections.emptyList())
        .error(Collections.emptyList())
        .build();

    // Parent on bucket-a; child URI synthesised on bucket-b. This simulates a misbehaving
    // FileStore implementation and verifies the authority check catches it.
    final FileHandle outputDir = new RemoteFileHandle(URI.create("s3://bucket-a/output")) {
      @Nonnull
      @Override
      public FileHandle child(@Nonnull final String childName) {
        return new RemoteFileHandle(URI.create("s3://bucket-b/output/" + childName));
      }
    };

    final BulkExportException ex = assertThrows(BulkExportException.class,
        () -> client.getUrlDownloadEntries(response, outputDir));
    assertTrue(ex.getMessage().contains("outside the output directory"));
  }

  @Test
  void testRejectsRemoteSchemeWithTraversalInChildPath() {
    // A child URI whose normalised path escapes the parent's path must be rejected.
    final BulkExportResponse response = BulkExportResponse.builder()
        .transactionTime(Instant.now())
        .request("fake-request")
        .output(List.of(
            new FileItem("Patient", "http:/foo.bar/1", 10)
        ))
        .deleted(Collections.emptyList())
        .error(Collections.emptyList())
        .build();

    final FileHandle outputDir = new RemoteFileHandle(URI.create("s3://bucket/output")) {
      @Nonnull
      @Override
      public FileHandle child(@Nonnull final String childName) {
        return new RemoteFileHandle(URI.create("s3://bucket/output/../../secret/" + childName));
      }
    };

    final BulkExportException ex = assertThrows(BulkExportException.class,
        () -> client.getUrlDownloadEntries(response, outputDir));
    assertTrue(ex.getMessage().contains("outside the output directory"));
  }

  /**
   * Minimal FileHandle stub for exercising the non-file scheme branch of the descendant check.
   * Only toUri, getLocation and child are used by getUrlDownloadEntries.
   */
  private static class RemoteFileHandle implements FileHandle {

    @Nonnull
    private final URI uri;

    RemoteFileHandle(@Nonnull final URI uri) {
      this.uri = uri;
    }

    @Override
    public boolean exists() {
      return false;
    }

    @Override
    public boolean mkdirs() {
      return true;
    }

    @Nonnull
    @Override
    public FileHandle child(@Nonnull final String childName) {
      // Default behaviour: append the child name to the path component, preserving the
      // authority and dropping any query/fragment so the child is a clean path beneath the
      // parent.
      final String basePath = uri.getPath() == null ? "" : uri.getPath();
      final String separator = basePath.endsWith("/") ? "" : "/";
      final URI childUri = URI.create(uri.getScheme() + "://" + uri.getAuthority()
          + basePath + separator + childName);
      return new RemoteFileHandle(childUri);
    }

    @Nonnull
    @Override
    public String getLocation() {
      return uri.toString();
    }

    @Nonnull
    @Override
    public URI toUri() {
      return uri;
    }

    @Override
    public long writeAll(@Nonnull final java.io.InputStream inputStream) {
      throw new UnsupportedOperationException();
    }
  }
}
