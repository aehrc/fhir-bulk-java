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

import au.csiro.fhir.auth.AuthConfig;
import au.csiro.fhir.export.download.UrlDownloadTemplate.UrlDownloadEntry;
import au.csiro.fhir.export.ws.AssociatedData;
import au.csiro.fhir.export.ws.BulkExportRequest;
import au.csiro.fhir.export.ws.BulkExportResponse;
import au.csiro.fhir.export.ws.BulkExportResponse.FileItem;
import au.csiro.filestore.FileStore.FileHandle;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import javax.validation.ConstraintViolationException;
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

    final BulkExportClient client = BulkExportClient.builder()
        .withFhirEndpointUrl("http://example.com")
        .withOutputDir("output-dir")
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
  void testFailsEarlyWithInvalidConfiguration() {

    final BulkExportClient client = BulkExportClient.builder()
        .withFhirEndpointUrl("invalid.url")
        .withOutputDir("output-dir")
        .withAuthConfig(AuthConfig.builder().enabled(true).build())
        .build();

    final ConstraintViolationException ex = assertThrows(ConstraintViolationException.class,
        client::export);

    assertEquals(
        "Invalid Bulk Export Configuration\n"
            + "authConfig.clientId: must be supplied if auth is enabled\n"
            + "authConfig: either clientSecret or privateKeyJWK must be supplied if auth is enabled\n"
            + "fhirEndpointUrl: must be a valid URL",
        ex.getMessage());
  }

}
