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

package au.csiro.pathling.export.ws;

import au.csiro.pathling.export.BulkExportException;
import au.csiro.pathling.export.BulkExportException.HttpError;
import au.csiro.pathling.export.fhir.FhirJsonSupport;
import au.csiro.pathling.export.fhir.OperationOutcome;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nonnull;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;

/**
 * Http Client ResponseHandler for the FHIR async request protocol.
 * <p>
 * Depending on the state of the protocol will produce either an intermediate response, final
 * successful response of specified type or throw an exception with the error details.
 *
 * @param <T> type of the expected final successful response.
 */
@Slf4j
class AsynResponseHandler<T extends AsyncResponse> implements ResponseHandler<AsyncResponse> {

  public static final String X_PROGRESS_HEADER = "x-progress";

  @Nonnull
  private final Class<T> responseClass;

  private AsynResponseHandler(@Nonnull final Class<T> responseClass) {
    this.responseClass = responseClass;
  }

  /**
   * Processes the response and returns the appropriate response object.
   *
   * @param response The http response to process
   * @return The appropriate {@link AsyncResponse}  object
   * @throws HttpError if an error occurs reading the response
   */
  @Override
  public AsyncResponse handleResponse(final HttpResponse response) {

    final int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode == HttpStatus.SC_OK) {
      return produceFinalResponse(response);
    } else if (statusCode == HttpStatus.SC_ACCEPTED) {
      return produceAcceptedResponse(response);
    } else {
      throw produceHttpError(response);
    }
  }

  @Nonnull
  private HttpError produceHttpError(@Nonnull final HttpResponse response) {
    log.debug("Http error in async request: {}", response);
    final Optional<OperationOutcome> maybeOutcome = Optional.ofNullable(response.getEntity())
        .flatMap(e -> Optional.ofNullable(e.getContentType()))
        .map(Header::getValue)
        .filter(s -> s.contains("json"))
        .flatMap(__ -> quietBodyAsString(response))
        .flatMap(OperationOutcome::parse);
    return new HttpError("Async Http resonse error", response.getStatusLine().getStatusCode(),
        maybeOutcome, getRetryAfterValue(response));
  }

  @Nonnull
  private AcceptedAsyncResponse produceAcceptedResponse(@Nonnull final HttpResponse response) {
    EntityUtils.consumeQuietly(response.getEntity());
    return AcceptedAsyncResponse.builder()
        .contentLocation(Optional.ofNullable(response.getFirstHeader(HttpHeaders.CONTENT_LOCATION))
            .flatMap(h -> Optional.ofNullable(h.getValue())))
        .progress(Optional.ofNullable(response.getFirstHeader(X_PROGRESS_HEADER))
            .flatMap(h -> Optional.ofNullable(h.getValue())))
        .retryAfter(getRetryAfterValue(response))
        .build();
  }

  @Nonnull
  private static Optional<RetryValue> getRetryAfterValue(@Nonnull final HttpResponse response) {
    return Optional.ofNullable(response.getFirstHeader(HttpHeaders.RETRY_AFTER))
        .flatMap(h -> Optional.ofNullable(h.getValue()))
        .flatMap(RetryValue::parseHttpValue);
  }

  @Nonnull
  private T produceFinalResponse(@Nonnull final HttpResponse response) {
    return quietBodyAsString(response).flatMap(s -> FhirJsonSupport.fromJson(s, responseClass))
        .orElseThrow(() -> new BulkExportException.ProtocolError(
            "Invalid successful response: " + response.getStatusLine()));
  }

  @Nonnull
  private Optional<String> quietBodyAsString(@Nonnull final HttpResponse response) {
    try {
      return Optional.of(EntityUtils.toString(response.getEntity()));
    } catch (final IOException __) {
      return Optional.empty();
    }
  }

  /**
   * Creates a new instance of the handler.
   *
   * @param responseClass the class of the expected final successful response.
   * @param <T> type of the expected final successful response.
   * @return a new instance of the handler.
   */
  @Nonnull
  public static <T extends AsyncResponse> AsynResponseHandler<T> of(
      @Nonnull final Class<T> responseClass) {
    return new AsynResponseHandler<>(responseClass);
  }

}
