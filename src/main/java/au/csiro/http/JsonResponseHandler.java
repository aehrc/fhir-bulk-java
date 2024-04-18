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

package au.csiro.http;

import au.csiro.fhir.export.BulkExportException.HttpError;
import au.csiro.fhir.export.ws.AsyncResponse;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;

/**
 * Http Client ResponseHandler for the Json responses to be mapped to Java Object using Gson.
 *
 * @param <T> type of the expected final successful response.
 */
@Slf4j
public class JsonResponseHandler<T> implements ResponseHandler<T> {

  private static final Gson GSON_LOWER_CASE_WITH_UNDERSCORES = new GsonBuilder()
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
      .create();

  @Nonnull
  private final Class<T> responseClass;

  @Nonnull
  private final Gson gson;


  JsonResponseHandler(@Nonnull final Class<T> responseClass, @Nonnull final Gson gson) {
    this.responseClass = responseClass;
    this.gson = gson;
  }

  /**
   * Processes the response and returns the appropriate response object.
   *
   * @param response The http response to process
   * @return The appropriate {@link AsyncResponse}  object
   * @throws HttpError if an error occurs reading the response
   */
  @Override
  public T handleResponse(final HttpResponse response) throws IOException {

    final int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode == HttpStatus.SC_OK) {
      return produceResponse(response);
    } else {
      throw new ClientProtocolException("Unexpected status code: " + statusCode);
    }
  }

  private T produceResponse(@Nonnull final HttpResponse response) throws IOException {
    final String jsonBody = Optional.ofNullable(response.getEntity())
        .filter(e -> e.getContentType() != null && e.getContentType().getValue()
            .contains(ContentType.APPLICATION_JSON.getMimeType()))
        .flatMap(this::quietBodyAsString)
        .orElseThrow(() -> new ClientProtocolException("Response entity is not a JSON"));
    try {
      return gson.fromJson(jsonBody, responseClass);
    } catch (final JsonParseException ex) {
      throw new ClientProtocolException("Failed to parse response body as JSON", ex);
    }
  }

  @Nonnull
  private Optional<String> quietBodyAsString(@Nonnull final HttpEntity body) {
    try {
      return Optional.of(EntityUtils.toString(body));
    } catch (final IOException ex) {
      log.warn("Failed to read response body", ex);
      return Optional.empty();
    }
  }

  /**
   * Creates a new instance of the handler with the LOWER_CASE_WITH_UNDERSCORES naming policy.
   *
   * @param responseClass the class of the expected final successful response.
   * @param <T> type of the expected final successful response.
   * @return a new instance of the handler.
   */
  @Nonnull
  public static <T> JsonResponseHandler<T> lowerCaseWithUnderscore(
      @Nonnull final Class<T> responseClass) {
    return of(responseClass, GSON_LOWER_CASE_WITH_UNDERSCORES);
  }

  /**
   * Creates a new instance of the handler with specified Gson instance.
   *
   * @param responseClass the class of the expected final successful response.
   * @param gson the Gson instance to use for deserialization.
   * @param <T> type of the expected final successful response.
   * @return a new instance of the handler.
   */
  @Nonnull
  public static <T> JsonResponseHandler<T> of(
      @Nonnull final Class<T> responseClass, @Nonnull final Gson gson) {
    return new JsonResponseHandler<>(responseClass, gson);
  }

}
