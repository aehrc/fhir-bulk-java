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

import au.csiro.fhir.export.BulkExportException.ProtocolError;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

/**
 * The final response for a successful bulk export request.
 *
 * @see <a href="https://hl7.org/fhir/uv/bulkdata/export.html#response---complete-status"> Response
 * - Complete Status</a>
 */
@Value
@Builder
public class BulkExportResponse implements AsyncResponse {

  /**
   * The pattern that a manifest file type must match to be accepted as a FHIR resource type name.
   * A character class is used rather than the FHIR {@code ResourceType} value set because custom
   * resource types are legal FHIR and the value set is version specific.
   */
  private static final Pattern RESOURCE_TYPE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,63}");

  /**
   * The URL schemes that a manifest file url may use.
   */
  private static final Set<String> ALLOWED_URL_SCHEMES = Set.of("http", "https");

  /**
   * Indicates the server's time when the query is run. The 'transactionTime' response value.
   */
  @Nonnull
  Instant transactionTime;

  /**
   * The full URL of the original Bulk Data kick-off request. The 'request' response value.
   */
  @Nonnull
  String request;

  /**
   * Indicates whether downloading the generated files requires the same authorization mechanism as
   * the $export operation itself. The 'requiresAccessToken' response value.
   */
  boolean requiresAccessToken;

  /**
   * A list of file items with one entry for each generated file. The 'output' response value.
   */
  @Nonnull
  @Builder.Default
  List<FileItem> output = Collections.emptyList();

  /**
   * A list of deleted file items following the same structure as the output list.
   */
  @Nonnull
  @Builder.Default
  List<FileItem> deleted = Collections.emptyList();

  /**
   * A list of error items following the same structure as the output list.
   */
  @Nonnull
  @Builder.Default
  List<FileItem> error = Collections.emptyList();

  /**
   * Validates the server supplied values that this client consumes.
   * <p>
   * The response is deserialised reflectively, which bypasses the constructor and does not honour
   * {@link Nonnull}, so absent values need to be checked explicitly. Only the values that are
   * actually used are validated; constraining the rest would reject otherwise usable responses.
   * <p>
   * Validating the file type here is what confines download destinations to the output directory:
   * a type that matches {@link #RESOURCE_TYPE_PATTERN} cannot contain path separators, dot
   * segments, or a drive letter, and so cannot escape the directory it is resolved against.
   *
   * @throws ProtocolError if any of the consumed values is missing or malformed.
   */
  public void validate() {
    if (transactionTime == null) {
      throw new ProtocolError("Manifest is missing 'transactionTime'");
    }
    if (output == null) {
      throw new ProtocolError("Manifest is missing 'output'");
    }
    output.forEach(BulkExportResponse::validateFileItem);
  }

  private static void validateFileItem(@Nullable final FileItem fileItem) {
    // A JSON null in the output array deserialises to a null element, which has to be rejected
    // here so that a malformed manifest surfaces as a ProtocolError rather than as an NPE.
    if (fileItem == null) {
      throw new ProtocolError("Manifest 'output' contains a null entry");
    }
    final String type = fileItem.getType();
    if (type == null || !RESOURCE_TYPE_PATTERN.matcher(type).matches()) {
      throw new ProtocolError("Manifest 'type' is not a valid FHIR resource type name: " + type);
    }
    final String url = fileItem.getUrl();
    if (url == null) {
      throw new ProtocolError("Manifest 'url' is missing for type: " + type);
    }
    final URI uri;
    try {
      uri = new URI(url);
    } catch (final URISyntaxException ex) {
      throw new ProtocolError("Manifest 'url' is not a valid URI: " + url, ex);
    }
    final String scheme = uri.getScheme();
    if (scheme == null || !ALLOWED_URL_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
      throw new ProtocolError("Manifest 'url' has an unsupported scheme: " + url);
    }
  }

  /**
   * Represents a single file item in the response.
   */
  @Value
  public static class FileItem {

    /**
     * The type of the FHIR resource contained in the file.
     */
    @Nonnull
    String type;

    /**
     * The URL of the file.
     */
    @Nonnull
    String url;

    /**
     * The number of resources in the file.
     */
    long count;
  }
}
