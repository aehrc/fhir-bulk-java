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

package au.csiro.fhir.export.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import au.csiro.fhir.export.BulkExportException.ProtocolError;
import au.csiro.fhir.export.ws.BulkExportResponse.FileItem;
import au.csiro.fhir.model.FhirJsonSupport;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for the validation of {@link BulkExportResponse}.
 */
public class BulkExportResponseTest {

  @Nonnull
  private static BulkExportResponse responseWithType(@Nonnull final String type) {
    return responseWith(new FileItem(type, "https://foo.bar/1", 10));
  }

  @Nonnull
  private static BulkExportResponse responseWith(@Nonnull final FileItem... items) {
    return BulkExportResponse.builder()
        .transactionTime(Instant.now())
        .request("fake-request")
        .output(List.of(items))
        .deleted(Collections.emptyList())
        .error(Collections.emptyList())
        .build();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Patient", "Condition", "OperationOutcome", "CustomResource", "Base64X"})
  void testAcceptsValidResourceTypes(@Nonnull final String type) {
    responseWithType(type).validate();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "../../../secret",           // POSIX traversal
      "..\\..\\secret",            // Windows traversal
      "/etc/passwd",               // absolute path
      "C:evil",                    // Windows drive relative
      "..%2f..%2fsecret",          // url encoded traversal
      "%2e%2e%2fsecret",           // fully url encoded traversal
      "..",                        // bare parent reference
      ".",                         // bare current reference
      "",                          // empty
      " Patient",                  // leading space
      "Patient ",                  // trailing space
      "Pati\0ent",                 // embedded NUL
      "Patiént",              // non ascii
      "9Patient",                  // leading digit
      "Patient.0000"               // embedded dot
  })
  void testRejectsTypeThatIsNotAResourceTypeName(@Nonnull final String type) {
    final ProtocolError ex = assertThrows(ProtocolError.class,
        () -> responseWithType(type).validate());
    assertEquals("Manifest 'type' is not a valid FHIR resource type name: " + type,
        ex.getMessage());
  }

  @Test
  void testRejectsTypeThatIsTooLong() {
    final String type = "P".repeat(65);
    final ProtocolError ex = assertThrows(ProtocolError.class,
        () -> responseWithType(type).validate());
    assertEquals("Manifest 'type' is not a valid FHIR resource type name: " + type,
        ex.getMessage());
  }

  @Test
  void testRejectsInvalidTypeAmongstValidOnes() {
    final BulkExportResponse response = responseWith(
        new FileItem("Patient", "https://foo.bar/1", 10),
        new FileItem("../../../secret", "https://foo.bar/2", 10));

    final ProtocolError ex = assertThrows(ProtocolError.class, response::validate);
    assertEquals("Manifest 'type' is not a valid FHIR resource type name: ../../../secret",
        ex.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"file:///etc/passwd", "jar:file:///tmp/x.jar!/y", "data:text/plain,x",
      "ftp://foo.bar/1", "/relative/no/scheme"})
  void testRejectsUrlWithUnsupportedScheme(@Nonnull final String url) {
    final BulkExportResponse response = responseWith(new FileItem("Patient", url, 10));

    final ProtocolError ex = assertThrows(ProtocolError.class, response::validate);
    assertEquals("Manifest 'url' has an unsupported scheme: " + url, ex.getMessage());
  }

  @Test
  void testRejectsUrlThatIsNotAValidUri() {
    final BulkExportResponse response = responseWith(
        new FileItem("Patient", "https://foo.bar/has a space", 10));

    final ProtocolError ex = assertThrows(ProtocolError.class, response::validate);
    assertEquals("Manifest 'url' is not a valid URI: https://foo.bar/has a space",
        ex.getMessage());
  }

  @Test
  void testAcceptsHttpAndHttpsUrls() {
    responseWith(
        new FileItem("Patient", "http://foo.bar/1", 10),
        new FileItem("Condition", "HTTPS://foo.bar/2", 10)
    ).validate();
  }

  @Test
  void testRejectsManifestWithMissingTransactionTime() {
    // Gson populates fields reflectively, so a manifest omitting a value leaves it null despite
    // the @Nonnull annotation. Parsing rather than building is what reproduces this.
    final BulkExportResponse response = parse("{"
        + "\"request\": \"fake-request\","
        + "\"output\": [{\"type\": \"Patient\", \"url\": \"https://foo.bar/1\", \"count\": 1}]"
        + "}");

    final ProtocolError ex = assertThrows(ProtocolError.class, response::validate);
    assertEquals("Manifest is missing 'transactionTime'", ex.getMessage());
  }

  @Test
  void testRejectsManifestWithNullOutput() {
    final BulkExportResponse response = parse("{"
        + "\"transactionTime\": \"2023-01-01T00:00:00.000Z\","
        + "\"request\": \"fake-request\","
        + "\"output\": null"
        + "}");

    final ProtocolError ex = assertThrows(ProtocolError.class, response::validate);
    assertEquals("Manifest is missing 'output'", ex.getMessage());
  }

  @Test
  void testRejectsParsedManifestWithTraversalInType() {
    final BulkExportResponse response = parse("{"
        + "\"transactionTime\": \"2023-01-01T00:00:00.000Z\","
        + "\"request\": \"fake-request\","
        + "\"output\": [{\"type\": \"../../../secret\", \"url\": \"https://foo.bar/1\","
        + " \"count\": 1}]"
        + "}");

    final ProtocolError ex = assertThrows(ProtocolError.class, response::validate);
    assertEquals("Manifest 'type' is not a valid FHIR resource type name: ../../../secret",
        ex.getMessage());
  }

  @Test
  void testRejectsParsedManifestWithNullEntryInOutput() {
    final BulkExportResponse response = parse("{"
        + "\"transactionTime\": \"2023-01-01T00:00:00.000Z\","
        + "\"request\": \"fake-request\","
        + "\"output\": [{\"type\": \"Patient\", \"url\": \"https://foo.bar/1\"}, null]"
        + "}");

    final ProtocolError ex = assertThrows(ProtocolError.class, response::validate);
    assertEquals("Manifest 'output' contains a null entry", ex.getMessage());
  }

  @Test
  void testRejectsParsedManifestWithMissingType() {
    final BulkExportResponse response = parse("{"
        + "\"transactionTime\": \"2023-01-01T00:00:00.000Z\","
        + "\"request\": \"fake-request\","
        + "\"output\": [{\"url\": \"https://foo.bar/1\", \"count\": 1}]"
        + "}");

    final ProtocolError ex = assertThrows(ProtocolError.class, response::validate);
    assertEquals("Manifest 'type' is not a valid FHIR resource type name: null", ex.getMessage());
  }

  @Test
  void testRejectsParsedManifestWithMissingUrl() {
    final BulkExportResponse response = parse("{"
        + "\"transactionTime\": \"2023-01-01T00:00:00.000Z\","
        + "\"request\": \"fake-request\","
        + "\"output\": [{\"type\": \"Patient\", \"count\": 1}]"
        + "}");

    final ProtocolError ex = assertThrows(ProtocolError.class, response::validate);
    assertEquals("Manifest 'url' is missing for type: Patient", ex.getMessage());
  }

  @Test
  void testIgnoresValuesThatAreNotConsumed() {
    // The client does not read 'deleted', 'error' or 'count', so odd values there must not cause
    // an otherwise usable manifest to be rejected.
    final BulkExportResponse response = parse("{"
        + "\"transactionTime\": \"2023-01-01T00:00:00.000Z\","
        + "\"request\": \"fake-request\","
        + "\"output\": [{\"type\": \"Patient\", \"url\": \"https://foo.bar/1\", \"count\": -1}],"
        + "\"deleted\": [{\"type\": \"../../../secret\", \"url\": \"file:///x\", \"count\": 1}],"
        + "\"error\": [{\"type\": \"../../../secret\", \"url\": \"file:///x\", \"count\": 1}]"
        + "}");

    response.validate();
  }

  @Nonnull
  private static BulkExportResponse parse(@Nonnull final String json) {
    return FhirJsonSupport.fromJson(json, BulkExportResponse.class)
        .orElseThrow(() -> new AssertionError("Failed to parse: " + json));
  }
}
