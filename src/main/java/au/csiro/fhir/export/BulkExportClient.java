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

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import au.csiro.fhir.auth.AuthConfig;
import au.csiro.fhir.auth.SMARTTokenCredentialFactory;
import au.csiro.fhir.auth.TokenCredentialFactory;
import au.csiro.fhir.export.BulkExportResult.FileResult;
import au.csiro.fhir.export.download.UrlDownloadTemplate;
import au.csiro.fhir.export.download.UrlDownloadTemplate.UrlDownloadEntry;
import au.csiro.fhir.export.ws.AssociatedData;
import au.csiro.fhir.export.ws.AsyncConfig;
import au.csiro.fhir.export.ws.AsyncResponseCallback;
import au.csiro.fhir.export.ws.BulkExportAsyncService;
import au.csiro.fhir.export.ws.BulkExportRequest;
import au.csiro.fhir.export.ws.BulkExportRequest.GroupLevel;
import au.csiro.fhir.export.ws.BulkExportRequest.Level;
import au.csiro.fhir.export.ws.BulkExportRequest.PatientLevel;
import au.csiro.fhir.export.ws.BulkExportRequest.SystemLevel;
import au.csiro.fhir.export.ws.BulkExportResponse;
import au.csiro.fhir.export.ws.BulkExportTemplate;
import au.csiro.fhir.model.Reference;
import au.csiro.filestore.FileStore;
import au.csiro.filestore.FileStore.FileHandle;
import au.csiro.filestore.FileStoreFactory;
import au.csiro.http.HttpClientConfig;
import au.csiro.http.TokenAuthRequestInterceptor;
import au.csiro.http.TokenCredentials;
import au.csiro.utils.ExecutorServiceResource;
import au.csiro.utils.TimeoutUtils;
import au.csiro.utils.ValidationUtils;
import com.google.common.collect.Streams;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.hibernate.validator.constraints.URL;


/**
 * A client for the FHIR Bulk Data Export API.
 *
 * @see <a href="https://build.fhir.org/ig/HL7/bulk-data/export.html">FHIR Bulk Export</a>
 */
@Value
@Slf4j
@Builder(setterPrefix = "with", buildMethodName = "buildUnchecked")
public class BulkExportClient {

  /**
   * The URL of the FHIR server to export from.
   */
  @Nonnull
  @URL
  String fhirEndpointUrl;

  /**
   * The operation to perform.
   */
  @Nonnull
  @Builder.Default
  Level level = new SystemLevel();

  /**
   * The format of the output data. The value of the `_outputFormat` parameter in the export
   * request.
   */
  @Nonnull
  @Builder.Default
  String outputFormat = "application/fhir+ndjson";

  /**
   * The time to start the export from. If null, the export will start from the beginning of time.
   * The value of the `_since` parameter in the export request.
   */
  @Nullable
  @Builder.Default
  Instant since = null;

  /**
   * The types of resources to export. The value of the `_type` parameter in the export request.
   */
  @Nonnull
  @Singular("type")
  List<String> types;

  /**
   * The reference to the patients to include in the export. The value of the `patient` parameter in
   * the export request.
   */
  @Nonnull
  @Singular("patient")
  List<Reference> patients;

  /**
   * The elements to include in the export. The value of the `_elements` parameter in the export
   * request.
   */
  @Nonnull
  @Singular("element")
  List<String> elements;

  /**
   * The type filters to apply to the export. The value of the `_typeFilter` parameter in the
   * export
   */
  @Nonnull
  @Singular("typeFilter")
  List<String> typeFilters;


  /**
   * When provided, a server with support for the parameter and requested values SHALL return or
   * omit a pre-defined set of FHIR resources associated with the request. The value of the
   * 'includeAssociatedData' query parameter.
   */
  @Nonnull
  @Singular("includeAssociatedDatum")
  List<AssociatedData> includeAssociatedData;

  /**
   * The directory to write the output files to. This describes the location in the format expected
   * by the {@link FileStoreFactory} configured in this client.
   */
  @Nonnull
  String outputDir;

  /**
   * The extension to use for the output files.
   */
  @Nonnull
  @Builder.Default
  String outputExtension = "ndjson";

  /**
   * The maximum time to wait for the export to complete. If zero or negative (default), the export
   * will not time out.
   */
  @Nonnull
  @Builder.Default
  Duration timeout = Duration.ZERO;

  /**
   * The maximum number of concurrent downloads to perform.
   */
  @Builder.Default
  @Min(1)
  int maxConcurrentDownloads = 10;

  /**
   * The factory to use to create the {@link FileStore} to write the output files to.
   */
  @Nonnull
  @Builder.Default
  FileStoreFactory fileStoreFactory = FileStoreFactory.getLocal();

  /**
   * The configuration for the HTTP client.
   */
  @Nonnull
  @Valid
  @Builder.Default
  HttpClientConfig httpClientConfig = HttpClientConfig.builder().build();

  /**
   * The configuration for the async operations.
   */
  @Nonnull
  @Valid
  @Builder.Default
  AsyncConfig asyncConfig = AsyncConfig.builder().build();


  /**
   * The configuration for the authentication.
   */
  @Nonnull
  @Valid
  @Builder.Default
  AuthConfig authConfig = AuthConfig.builder().build();

  /**
   * A builder for the {@link BulkExportClient}.
   */
  public static class BulkExportClientBuilder {

    /**
     * Sets the list of the associated data to include in form their string codes.
     *
     * @param associatedDataCodes the list of associated data codes
     * @return the builder
     */
    public BulkExportClientBuilder withIncludeAssociatedData(
        @Nonnull final List<String> associatedDataCodes) {
      return withIncludeAssociatedData(associatedDataCodes.stream()
          .map(AssociatedData::fromCode)
          .collect(Collectors.toUnmodifiableList()));
    }
    
    /**
     * Build a valid {@link BulkExportClient} instance.
     *
     * @return the client
     * @throws ConstraintViolationException if the client configuration is invalid
     */
    public BulkExportClient build() throws ConstraintViolationException {
      final BulkExportClient client = buildUnchecked();
      ValidationUtils.ensureValid(client, "Invalid Bulk Export Client Configuration");
      return client;
    }
  }

  /**
   * Create a builder for a system-level export.
   *
   * @return the builder configured for a system-level export
   */
  @Nonnull
  public static BulkExportClientBuilder systemBuilder() {
    return BulkExportClient.builder().withLevel(new SystemLevel());
  }

  /**
   * Create a builder for a patient-level export.
   *
   * @return the builder configured for a patient-level export
   */
  @Nonnull
  public static BulkExportClientBuilder patientBuilder() {
    return BulkExportClient.builder().withLevel(new PatientLevel());
  }

  /**
   * Create a builder for a group-level export.
   *
   * @param groupId the group ID to export
   * @return the builder configured for a group-level export
   */
  @Nonnull
  public static BulkExportClientBuilder groupBuilder(@Nonnull final String groupId) {
    return BulkExportClient.builder().withLevel(new GroupLevel(groupId));
  }

  /**
   * Export data from the FHIR server.
   *
   * @return the result of the export
   * @throws BulkExportException if the export fails
   */
  public BulkExportResult export() {
    try (
        final TokenCredentialFactory tokenAuthFactory = createTokenProvider();
        final FileStore fileStore = createFileStore();
        final CloseableHttpClient httpClient = createHttpClient(tokenAuthFactory);
        final ExecutorServiceResource executorServiceResource = createExecutorServiceResource()
    ) {
      final BulkExportTemplate bulkExportTemplate = new BulkExportTemplate(
          new BulkExportAsyncService(httpClient, URI.create(fhirEndpointUrl)),
          asyncConfig);
      final UrlDownloadTemplate downloadTemplate = new UrlDownloadTemplate(httpClient,
          executorServiceResource.getExecutorService());

      final BulkExportResult result = doExport(fileStore, bulkExportTemplate, downloadTemplate);
      log.info("Export successful: {}", result);
      return result;
    } catch (final IOException ex) {
      throw new BulkExportException.SystemError("System error in bulk export", ex);
    }
  }

  BulkExportResult doExport(@Nonnull final FileStore fileStore,
      @Nonnull final BulkExportTemplate bulkExportTemplate,
      @Nonnull final UrlDownloadTemplate downloadTemplate)
      throws IOException {

    final Instant timeoutAt = TimeoutUtils.toTimeoutAt(timeout);
    log.debug("Setting timeout at: {} for requested timeout of: {}", timeoutAt, timeout);

    final FileHandle destinationDir = fileStore.get(outputDir);

    // try to create the destination dir here
    // to fail early if it already exists of cannot be created
    if (destinationDir.exists()) {
      throw new BulkExportException(
          "Destination directory already exists: " + destinationDir.getLocation());
    } else {
      log.debug("Creating destination directory: {}", destinationDir.getLocation());
      destinationDir.mkdirs();
    }
    return bulkExportTemplate.export(buildBulkExportRequest(),
        new AsyncResponseCallback<>() {
          @Nonnull
          @Override
          public BulkExportResult handleResponse(@Nonnull final BulkExportResponse response,
              @Nonnull final Duration timeoutAfter)
              throws IOException {
            log.debug("Export request completed: {}", response);

            final List<UrlDownloadEntry> downloadList = getUrlDownloadEntries(response,
                destinationDir);
            log.debug("Downloading entries: {}", downloadList);
            final List<Long> fileSizes = downloadTemplate.download(downloadList, timeoutAfter);
            final FileHandle successMarker = destinationDir.child("_SUCCESS");
            log.debug("Marking download as complete with: {}", successMarker.getLocation());
            successMarker.writeAll(new ByteArrayInputStream(new byte[0]));
            return buildResult(response, downloadList, fileSizes);
          }
        },
        TimeoutUtils.toTimeoutAfter(timeoutAt));
  }

  BulkExportRequest buildBulkExportRequest() {
    return BulkExportRequest.builder()
        .level(level)
        ._outputFormat(outputFormat)
        ._type(types)
        ._since(since)
        ._elements(elements)
        ._typeFilter(typeFilters)
        .includeAssociatedData(includeAssociatedData)
        .patient(patients)
        .build();
  }

  @Nonnull
  private BulkExportResult buildResult(@Nonnull final BulkExportResponse response,
      @Nonnull final List<UrlDownloadEntry> downloadList, @Nonnull final List<Long> fileSizes) {

    return BulkExportResult.of(
        response.getTransactionTime(),
        Streams.zip(downloadList.stream(), fileSizes.stream(),
                (de, size) -> FileResult.of(de.getSource(), de.getDestination().toUri(), size))
            .collect(Collectors.toUnmodifiableList())
    );
  }

  @Nonnull
  List<UrlDownloadEntry> getUrlDownloadEntries(@Nonnull final BulkExportResponse response,
      @Nonnull final FileHandle destinationDir) {
    final Map<String, List<String>> urlsByType = response.getOutput().stream().collect(
        Collectors.groupingBy(BulkExportResponse.FileItem::getType, LinkedHashMap::new,
            mapping(BulkExportResponse.FileItem::getUrl, toList())));

    return urlsByType.entrySet().stream()
        .flatMap(entry -> IntStream.range(0, entry.getValue().size())
            .mapToObj(index -> new UrlDownloadEntry(
                    URI.create(entry.getValue().get(index)),
                    destinationDir.child(toFileName(entry.getKey(), index, outputExtension))
                )
            )
        ).collect(Collectors.toUnmodifiableList());
  }

  @Nonnull
  static String toFileName(@Nonnull final String resource, final int chunkNo,
      @Nonnull final String extension) {
    return String.format("%s.%04d.%s", resource, chunkNo, extension);
  }

  @Nonnull
  private FileStore createFileStore() throws IOException {
    log.debug("Creating FileStore of: {} for outputDir: {}", fileStoreFactory, outputDir);
    return fileStoreFactory.createFileStore(outputDir);
  }

  @Nonnull
  private TokenCredentialFactory createTokenProvider() {
    return SMARTTokenCredentialFactory.create(authConfig);
  }

  @Nonnull
  private CloseableHttpClient createHttpClient(
      @Nonnull final TokenCredentialFactory tokenAuthFactory) {
    log.debug("Creating HttpClient with configuration: {}", httpClientConfig);

    final URI endpointURI = URI.create(fhirEndpointUrl);
    final HttpHost httpHost = new HttpHost(endpointURI.getHost(), endpointURI.getPort(),
        endpointURI.getScheme());
    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    final Optional<TokenCredentials> tokenCredentials = tokenAuthFactory.createCredentials(
        endpointURI, authConfig);
    tokenCredentials.ifPresent(
        cr -> credentialsProvider.setCredentials(new AuthScope(httpHost), cr));
    return httpClientConfig.clientBuilder()
        .setDefaultCredentialsProvider(credentialsProvider)
        .addInterceptorFirst(new TokenAuthRequestInterceptor())
        .build();
  }

  @Nonnull
  private ExecutorServiceResource createExecutorServiceResource() {
    if (maxConcurrentDownloads <= 0) {
      throw new IllegalArgumentException(
          "maxConcurrentDownloads must be positive: " + maxConcurrentDownloads);
    }
    if (httpClientConfig.getMaxConnectionsPerRoute() < maxConcurrentDownloads) {
      log.warn("maxConnectionsPerRoute is less than maxConcurrentDownloads: {} < {}",
          httpClientConfig.getMaxConnectionsPerRoute(), maxConcurrentDownloads);
    }
    log.debug("Creating ExecutorService with maxConcurrentDownloads: {}", maxConcurrentDownloads);
    return ExecutorServiceResource.of(Executors.newFixedThreadPool(maxConcurrentDownloads));
  }
}

