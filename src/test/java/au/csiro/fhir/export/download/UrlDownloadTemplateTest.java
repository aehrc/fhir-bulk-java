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

package au.csiro.fhir.export.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import au.csiro.fhir.export.BulkExportException.DownloadError;
import au.csiro.fhir.export.BulkExportException.HttpError;
import au.csiro.fhir.export.BulkExportException.Timeout;
import au.csiro.fhir.export.download.UrlDownloadTemplate.UrlDownloadEntry;
import au.csiro.filestore.FileStore.FileHandle;
import au.csiro.utils.TimeoutUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nonnull;
import org.apache.commons.io.IOUtils;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UrlDownloadTemplateTest {

  @Mock
  ExecutorService executorService;

  @Mock
  HttpClient httpClient;

  @Mock
  HttpResponse httpResponse;

  @Mock
  FileHandle fileHandle;

  @Captor
  ArgumentCaptor<HttpUriRequest> httpRequestCaptor;

  /**
   * What a caller who does not customise the download gets.
   */
  private static final DownloadConfig DEFAULTS = DownloadConfig.builder().build();

  @Test
  void testDownloadsAllUrlsSuccessfully() {
    final UrlDownloadTemplate template = new UrlDownloadTemplate(httpClient, executorService,
        DEFAULTS);
    when(executorService.submit(eq(template.new UriDownloadTask(URI.create("http://foo.bar/file1"),
        FileHandle.ofLocal("file1"))))).thenReturn(CompletableFuture.completedFuture(3L));
    when(executorService.submit(eq(template.new UriDownloadTask(URI.create("http://foo.bar/file2"),
        FileHandle.ofLocal("file2"))))).thenReturn(CompletableFuture.completedFuture(7L));

    final List<Long> result = template.download(List.of(
        new UrlDownloadEntry(URI.create("http://foo.bar/file1"), FileHandle.ofLocal("file1")),
        new UrlDownloadEntry(URI.create("http://foo.bar/file2"), FileHandle.ofLocal("file2"))
    ), TimeoutUtils.TIMEOUT_INFINITE);
    assertEquals(List.of(3L, 7L), result);
  }

  @SuppressWarnings("unchecked")
  @Test
  void testThrowsTimeoutExceptionWhenTimeout() {
    final UrlDownloadTemplate template = new UrlDownloadTemplate(httpClient, executorService,
        DEFAULTS);
    when(executorService.submit(Mockito.any(Callable.class))).thenReturn(new CompletableFuture<>());

    final Timeout ex = assertThrows(Timeout.class,
        () -> template.download(List.of(
            new UrlDownloadEntry(URI.create("http://foo.bar/file1"), FileHandle.ofLocal("file1")),
            new UrlDownloadEntry(URI.create("http://foo.bar/file2"), FileHandle.ofLocal("file2"))
        ), Duration.ofMillis(100)));
    assertEquals("Download timed out at: PT0.1S", ex.getMessage());
  }


  @Test
  void testFailsOnErrorFast() {
    final IOException downloadEx = new IOException("IO Error");
    final CompletableFuture<Long> runningFuture = new CompletableFuture<>();

    final UrlDownloadTemplate template = new UrlDownloadTemplate(httpClient, executorService,
        DEFAULTS);
    when(executorService.submit(eq(template.new UriDownloadTask(URI.create("http://foo.bar/file1"),
        FileHandle.ofLocal("file1"))))).thenReturn(
        CompletableFuture.failedFuture(downloadEx));
    when(executorService.submit(eq(template.new UriDownloadTask(URI.create("http://foo.bar/file2"),
        FileHandle.ofLocal("file2"))))).thenReturn(runningFuture);

    final DownloadError ex = assertThrows(DownloadError.class,
        () -> template.download(List.of(
            new UrlDownloadEntry(URI.create("http://foo.bar/file1"), FileHandle.ofLocal("file1")),
            new UrlDownloadEntry(URI.create("http://foo.bar/file2"), FileHandle.ofLocal("file2"))
        ), TimeoutUtils.TIMEOUT_INFINITE));

    assertEquals("Download failed", ex.getMessage());
    assertEquals(downloadEx, ex.getCause());
    assertTrue(runningFuture.isCancelled());
  }

  @Test
  void testDownloadTaskGetsTheFileOnSuccess() throws Exception {
    final InputStream responseStream = new ByteArrayInputStream(new byte[]{1, 2, 3});
    when(httpClient.execute(Mockito.any())).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(
        new BasicStatusLine(new ProtocolVersion("http", 1, 1), 200, "OK"));
    when(httpResponse.getEntity()).thenReturn(new InputStreamEntity(responseStream, 3));
    when(fileHandle.writeAll(Mockito.eq(responseStream))).thenReturn(7L);
    final UrlDownloadTemplate template = new UrlDownloadTemplate(httpClient, executorService,
        DEFAULTS);
    final UrlDownloadTemplate.UriDownloadTask task = template.new UriDownloadTask(
        URI.create("http://foo.bar/file1"),
        fileHandle);
    assertEquals(7L, task.call());
    verify(httpClient).execute(httpRequestCaptor.capture());
    assertEquals("http://foo.bar/file1", httpRequestCaptor.getValue().getURI().toString());
    assertEquals("GET", httpRequestCaptor.getValue().getMethod());
  }

  @Test
  void testDownloadTaskFailsOnHttpError() throws Exception {
    when(httpClient.execute(Mockito.any())).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(
        new BasicStatusLine(new ProtocolVersion("http", 1, 1), 500, "Internal Server Error"));
    final UrlDownloadTemplate template = new UrlDownloadTemplate(httpClient, executorService,
        DEFAULTS);
    final HttpError ex = assertThrows(HttpError.class, () -> template.new UriDownloadTask(
        URI.create("http://foo.bar/file1"),
        fileHandle).call());
    assertEquals("Failed to download: http://foo.bar/file1: [statusCode: 500]", ex.getMessage());
  }

  /**
   * A body that yields some bytes and then fails, as one cut short mid-transfer does.
   *
   * @param bytesBeforeFailure the number of bytes served before the failure
   * @param failure the failure to raise once those bytes have been served
   * @return the body
   */
  @Nonnull
  private static InputStream bodyFailingAfter(final int bytesBeforeFailure,
      @Nonnull final IOException failure) {
    return new InputStream() {

      private int served;

      @Override
      public int read() throws IOException {
        if (served++ < bytesBeforeFailure) {
          return 1;
        }
        throw failure;
      }
    };
  }

  /**
   * A body cut short the way a Content-Length delimited one is, which is the failure reported in
   * the originating issue.
   *
   * @param bytesBeforeFailure the number of bytes served before the truncation
   * @return the body
   */
  @Nonnull
  private static InputStream truncatedBody(final int bytesBeforeFailure) {
    return bodyFailingAfter(bytesBeforeFailure,
        new ConnectionClosedException("Premature end of Content-Length delimited message body"));
  }

  /**
   * A body that serves its bytes but fails on close, as Apache's stream does when the consumer
   * stops before the end of the message and the remainder is drained.
   */
  @Nonnull
  private static InputStream bodyFailingOnClose() {
    return new InputStream() {

      @Override
      public int read() {
        return 1;
      }

      @Override
      public void close() throws IOException {
        throw new ConnectionClosedException(
            "Premature end of Content-Length delimited message body");
      }
    };
  }

  /**
   * Copies the body through to a sink, so that a read failure surfaces from where it would in
   * production: part way through the copy that {@code writeAll} performs.
   */
  private void writeAllConsumesTheBody() throws IOException {
    when(fileHandle.writeAll(Mockito.any())).thenAnswer(invocation ->
        IOUtils.copyLarge(invocation.getArgument(0, InputStream.class),
            OutputStream.nullOutputStream()));
  }

  /**
   * A response body that ends early is only discovered once it is being read, which is past the
   * point where the HTTP client will retry for us. Exports of large resource types run to thousands
   * of files, so without a retry here a single truncated body discards the whole export.
   */
  @Test
  void testDownloadTaskRetriesAfterTruncatedBody() throws Exception {
    when(httpClient.execute(Mockito.any())).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(
        new BasicStatusLine(new ProtocolVersion("http", 1, 1), 200, "OK"));
    when(httpResponse.getEntity())
        .thenReturn(new InputStreamEntity(truncatedBody(1), 3))
        .thenReturn(new InputStreamEntity(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3));
    writeAllConsumesTheBody();

    final UrlDownloadTemplate template = new UrlDownloadTemplate(httpClient, executorService,
        DEFAULTS);
    final long written = template.new UriDownloadTask(URI.create("http://foo.bar/file1"),
        fileHandle).call();

    assertEquals(3L, written);
    verify(httpClient, Mockito.times(2)).execute(Mockito.any());
  }

  /**
   * A truncation is not always discovered by a read. Apache's stream drains the rest of the message
   * on close when the consumer stopped short of it, and raises the same failure from there, so the
   * read side has to be recognised on close as well.
   */
  @Test
  void testDownloadTaskRetriesAfterTruncationFoundOnClose() throws Exception {
    when(httpClient.execute(Mockito.any())).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(
        new BasicStatusLine(new ProtocolVersion("http", 1, 1), 200, "OK"));
    when(httpResponse.getEntity())
        .thenReturn(new InputStreamEntity(bodyFailingOnClose(), 3))
        .thenReturn(new InputStreamEntity(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3));
    // Stops short of the end of the message, leaving the remainder to be drained on close.
    when(fileHandle.writeAll(Mockito.any())).thenAnswer(invocation -> {
      invocation.getArgument(0, InputStream.class).read();
      return 3L;
    });

    final UrlDownloadTemplate template = new UrlDownloadTemplate(httpClient, executorService,
        DEFAULTS);
    final long written = template.new UriDownloadTask(URI.create("http://foo.bar/file1"),
        fileHandle).call();

    assertEquals(3L, written);
    verify(httpClient, Mockito.times(2)).execute(Mockito.any());
  }

  /**
   * Retrying cannot mask a source that is genuinely broken, so the failure is still surfaced once
   * the attempts are used up, and as the original exception rather than a wrapper.
   */
  @Test
  void testDownloadTaskGivesUpAfterRepeatedFailures() throws Exception {
    when(httpClient.execute(Mockito.any())).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(
        new BasicStatusLine(new ProtocolVersion("http", 1, 1), 200, "OK"));
    when(httpResponse.getEntity()).thenAnswer(
        invocation -> new InputStreamEntity(truncatedBody(1), 3));
    writeAllConsumesTheBody();

    final UrlDownloadTemplate template = new UrlDownloadTemplate(httpClient, executorService,
        DEFAULTS);
    final ConnectionClosedException ex = assertThrows(ConnectionClosedException.class,
        () -> template.new UriDownloadTask(URI.create("http://foo.bar/file1"), fileHandle).call());

    assertEquals("Premature end of Content-Length delimited message body", ex.getMessage());
    verify(httpClient, Mockito.times(DEFAULTS.getMaxRetries() + 1)).execute(Mockito.any());
  }

  /**
   * The number of retries is taken from the configuration, so that a caller can turn retrying off
   * or widen it. Zero means the single attempt that was made before retrying existed.
   */
  @Test
  void testDownloadTaskHonoursConfiguredRetries() throws Exception {
    when(httpClient.execute(Mockito.any())).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(
        new BasicStatusLine(new ProtocolVersion("http", 1, 1), 200, "OK"));
    when(httpResponse.getEntity()).thenAnswer(
        invocation -> new InputStreamEntity(truncatedBody(1), 3));
    writeAllConsumesTheBody();

    final UrlDownloadTemplate template = new UrlDownloadTemplate(httpClient, executorService,
        DownloadConfig.builder().maxRetries(0).build());
    assertThrows(ConnectionClosedException.class,
        () -> template.new UriDownloadTask(URI.create("http://foo.bar/file1"), fileHandle).call());

    verify(httpClient, Mockito.times(1)).execute(Mockito.any());
  }

  /**
   * A connection reset part way through is the same fault as a body that ends early, arriving as a
   * reset rather than a close, and is equally past the point where the HTTP client will retry.
   */
  @Test
  void testDownloadTaskRetriesAfterConnectionReset() throws Exception {
    when(httpClient.execute(Mockito.any())).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(
        new BasicStatusLine(new ProtocolVersion("http", 1, 1), 200, "OK"));
    when(httpResponse.getEntity())
        .thenReturn(new InputStreamEntity(
            bodyFailingAfter(1, new SocketException("Connection reset")), 3))
        .thenReturn(new InputStreamEntity(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3));
    writeAllConsumesTheBody();

    final UrlDownloadTemplate template = new UrlDownloadTemplate(httpClient, executorService,
        DEFAULTS);
    final long written = template.new UriDownloadTask(URI.create("http://foo.bar/file1"),
        fileHandle).call();

    assertEquals(3L, written);
    verify(httpClient, Mockito.times(2)).execute(Mockito.any());
  }

  /**
   * Retrying is confined to a transfer that was cut short, which is recognised by the type of the
   * failure. Any other way of reading the body failing is not known to be transient, so repeating
   * the transfer for it is not assumed to help.
   */
  @Test
  void testDownloadTaskDoesNotRetryOtherReadFailures() throws Exception {
    when(httpClient.execute(Mockito.any())).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(
        new BasicStatusLine(new ProtocolVersion("http", 1, 1), 200, "OK"));
    when(httpResponse.getEntity()).thenReturn(new InputStreamEntity(
        bodyFailingAfter(1, new IOException("Malformed response")), 3));
    writeAllConsumesTheBody();

    final UrlDownloadTemplate template = new UrlDownloadTemplate(httpClient, executorService,
        DEFAULTS);
    final IOException ex = assertThrows(IOException.class,
        () -> template.new UriDownloadTask(URI.create("http://foo.bar/file1"), fileHandle).call());

    assertEquals("Malformed response", ex.getMessage());
    verify(httpClient, Mockito.times(1)).execute(Mockito.any());
  }

  /**
   * A destination that cannot be written to is not a broken transfer. Fetching the body again
   * cannot fix a full disk or a rejected write, so the transfer is not repeated for it.
   */
  @Test
  void testDownloadTaskDoesNotRetryDestinationFailure() throws Exception {
    when(httpClient.execute(Mockito.any())).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(
        new BasicStatusLine(new ProtocolVersion("http", 1, 1), 200, "OK"));
    when(httpResponse.getEntity()).thenReturn(
        new InputStreamEntity(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3));
    when(fileHandle.writeAll(Mockito.any()))
        .thenThrow(new IOException("No space left on device"));

    final UrlDownloadTemplate template = new UrlDownloadTemplate(httpClient, executorService,
        DEFAULTS);
    final IOException ex = assertThrows(IOException.class,
        () -> template.new UriDownloadTask(URI.create("http://foo.bar/file1"), fileHandle).call());

    assertEquals("No space left on device", ex.getMessage());
    verify(httpClient, Mockito.times(1)).execute(Mockito.any());
  }

  /**
   * A rejected request is a decision by the server rather than a broken transfer, so repeating it
   * only adds load.
   */
  @Test
  void testDownloadTaskDoesNotRetryHttpError() throws Exception {
    when(httpClient.execute(Mockito.any())).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(
        new BasicStatusLine(new ProtocolVersion("http", 1, 1), 403, "Forbidden"));

    final UrlDownloadTemplate template = new UrlDownloadTemplate(httpClient, executorService,
        DEFAULTS);
    assertThrows(HttpError.class, () -> template.new UriDownloadTask(
        URI.create("http://foo.bar/file1"), fileHandle).call());

    verify(httpClient, Mockito.times(1)).execute(Mockito.any());
  }
}
