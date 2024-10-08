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

import static au.csiro.test.TestUtils.anyResponseHandler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import au.csiro.fhir.export.ws.BulkExportRequest.GroupLevel;
import au.csiro.fhir.model.Parameters;
import au.csiro.fhir.model.Reference;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BulkExportAsyncServiceTest {

  @Mock
  HttpClient httpClient;

  @Mock
  AsyncResponse asyncResponse;

  @Captor
  ArgumentCaptor<HttpUriRequest> httpRequestCaptor;

  @Test
  void testKickOffSendsACorrectGetRequest() throws IOException {
    when(httpClient.execute(Mockito.any(HttpUriRequest.class), anyResponseHandler()))
        .thenReturn(asyncResponse);
    final BulkExportAsyncService service = new BulkExportAsyncService(httpClient,
        URI.create("http://example1.com/fhir"));
    final BulkExportRequest systemExportRequest = BulkExportRequest.builder()
        ._type(List.of("Patient", "Condition"))
        .build();
    final AsyncResponse response = service.kickOff(systemExportRequest);
    assertEquals(asyncResponse, response);
    Mockito.verify(httpClient)
        .execute(httpRequestCaptor.capture(), anyResponseHandler());
    assertEquals("GET", httpRequestCaptor.getValue().getMethod());
    assertEquals("http://example1.com/fhir/$export?_type=Patient%2CCondition",
        httpRequestCaptor.getValue().getURI().toString());
    assertEquals("application/fhir+json",
        httpRequestCaptor.getValue().getFirstHeader("accept").getValue());
    assertEquals("respond-async",
        httpRequestCaptor.getValue().getFirstHeader("prefer").getValue());
  }

  @Test
  void testKickOffSendsACorrectPostRequest() throws IOException {
    when(httpClient.execute(Mockito.any(HttpUriRequest.class), anyResponseHandler()))
        .thenReturn(asyncResponse);
    final BulkExportAsyncService service = new BulkExportAsyncService(httpClient,
        URI.create("http://example1.com/fhir"));
    final BulkExportRequest systemExportRequest = BulkExportRequest.builder()
        .level(new GroupLevel("id0001"))
        ._type(List.of("Patient", "Condition"))
        .patient(List.of(Reference.of("Patient/0001")))
        .build();
    final AsyncResponse response = service.kickOff(systemExportRequest);
    assertEquals(asyncResponse, response);
    Mockito.verify(httpClient)
        .execute(httpRequestCaptor.capture(), anyResponseHandler());
    assertEquals("POST", httpRequestCaptor.getValue().getMethod());
    assertEquals("http://example1.com/fhir/Group/id0001/$export",
        httpRequestCaptor.getValue().getURI().toString());
    assertEquals("application/fhir+json",
        httpRequestCaptor.getValue().getFirstHeader("accept").getValue());
    assertEquals("respond-async",
        httpRequestCaptor.getValue().getFirstHeader("prefer").getValue());
    final HttpEntity postEntity = ((HttpPost) httpRequestCaptor.getValue()).getEntity();
    assertEquals("application/fhir+json; charset=UTF-8", postEntity.getContentType().getValue());
    assertEquals(
        Parameters.of(
            Parameters.Parameter.of("_type", "Patient,Condition"),
            Parameters.Parameter.of("patient", Reference.of("Patient/0001"))
        ).toJson(),
        EntityUtils.toString(postEntity));
  }

  @Test
  void testCheckStatusSendsCorrectGetRequest() throws IOException {
    when(httpClient.execute(Mockito.any(HttpUriRequest.class), anyResponseHandler()))
        .thenReturn(asyncResponse);
    final BulkExportAsyncService service = new BulkExportAsyncService(httpClient,
        URI.create("http://example1.com/fhir"));
    final AsyncResponse response = service.checkStatus(URI.create("http://example.com/$pool1"));
    assertEquals(asyncResponse, response);
    Mockito.verify(httpClient)
        .execute(httpRequestCaptor.capture(), anyResponseHandler());
    assertEquals("GET", httpRequestCaptor.getValue().getMethod());
    assertEquals("http://example.com/$pool1", httpRequestCaptor.getValue().getURI().toString());
    assertEquals("application/json",
        httpRequestCaptor.getValue().getFirstHeader("accept").getValue());
  }
}
