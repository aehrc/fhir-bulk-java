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

import static au.csiro.utils.TimeoutUtils.hasExpired;
import static au.csiro.utils.TimeoutUtils.toTimeoutAt;

import au.csiro.filestore.FileStore;
import au.csiro.fhir.export.BulkExportException;
import au.csiro.fhir.export.BulkExportException.DownloadError;
import au.csiro.fhir.export.BulkExportException.HttpError;
import au.csiro.fhir.export.BulkExportException.Timeout;
import au.csiro.filestore.FileStore.FileHandle;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpResponse;
import org.apache.http.MalformedChunkCodingException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

/**
 * A template class for concurrent download of multiple URLs into a file store. The file store can
 * be any concrete implementation of the {@link FileStore} abstraction.
 * <p>
 * A download whose transfer is cut short mid-body is retried, since that is a transient fault.
 * Once a single download has used up its attempts this implementation fails fast: all the
 * remaining downloads are terminated.
 * <p>
 * No cleanup is performed on failure - partial results may be left for some of the URLs.
 */

@Slf4j
public class UrlDownloadTemplate {

  /**
   * A single entry in the list of URLs to download.
   */
  @Value
  public static class UrlDownloadEntry {

    /**
     * The source URL to download from.
     */
    @Nonnull
    URI source;

    /**
     * The destination file to write the downloaded content to.
     */
    @Nonnull
    FileHandle destination;
  }

  /**
   * The HTTP client to use for downloading. The lifecycle of the client should be managed
   * externally.
   */
  @Nonnull
  HttpClient httpClient;

  /**
   * The executor service to use for concurrent downloads. The lifecycle of the executor should be
   * managed externally.
   */
  @Nonnull
  ExecutorService executorService;

  /**
   * The configuration governing how a failed download is retried.
   */
  @Nonnull
  DownloadConfig config;

  /**
   * Recognises a transfer that was cut short before the message was complete. Apache's body
   * decoders raise these when the connection closes or is reset part way through, and no other
   * part of a download does, so a broken transfer is told apart from a destination that cannot be
   * written to by the type of the failure rather than by where it was thrown.
   *
   * @param e the failure that ended the attempt
   * @return true if the transfer was cut short, and so is worth repeating
   */
  private static boolean isTruncatedBody(@Nonnull final IOException e) {
    // ConnectionClosedException is a Content-Length delimited body ending early, and
    // MalformedChunkCodingException, with its TruncatedChunkException subclass, the chunked
    // equivalent. A SocketException is the same fault arriving as a reset rather than a close.
    return e instanceof ConnectionClosedException
        || e instanceof MalformedChunkCodingException
        || e instanceof SocketException;
  }

  @Value
  class UriDownloadTask implements Callable<Long> {

    @Nonnull
    URI source;

    @Nonnull
    FileHandle destination;

    @Override
    public Long call() throws Exception {
      // A body that ends early is only discovered while it is being read, which is after the HTTP
      // client has stopped considering the request retryable. Retry here so that one interrupted
      // transfer does not discard an export that may run to thousands of files. The file is
      // rewritten from the start, so a partial write from the previous attempt is replaced.
      //
      // Only a transfer cut short is retried. A destination that cannot be written to will not be
      // fixed by fetching the body again, and re-downloading a large file to meet the same full
      // disk or rejected write wastes the transfer. Failures of the request itself are left to the
      // HTTP client, which retries them already.
      final int maxAttempts = config.getMaxRetries() + 1;
      for (int attempt = 1; ; attempt++) {
        try {
          return attemptDownload();
        } catch (final IOException e) {
          if (!isTruncatedBody(e)) {
            throw e;
          }
          if (attempt >= maxAttempts) {
            log.error("Failed to download {} after {} attempts", source, attempt);
            throw e;
          }
          log.warn("Download of {} failed on attempt {} of {} ({}), retrying", source,
              attempt, maxAttempts, e.getMessage());
        }
      }
    }

    /**
     * Performs a single download attempt.
     *
     * @return the number of bytes written
     * @throws IOException if the body ends before it has been read in full, the request fails, or
     * the destination cannot be written to
     */
    private long attemptDownload() throws IOException {
      log.debug("Starting download from:  {}  to: {}", source, destination);
      final HttpResponse result = httpClient.execute(new HttpGet(source));
      if (result.getStatusLine().getStatusCode() != 200) {
        // The server has rejected the request rather than failed to deliver it, so repeating it
        // would only add load. HttpError is not an IOException and so is not retried.
        log.error("Failed to download: {}. Status: {}", source, result.getStatusLine());
        throw new HttpError(
            "Failed to download: " + source, result.getStatusLine().getStatusCode());
      }
      try (final InputStream is = result.getEntity().getContent()) {
        final long bytesWritten = destination.writeAll(is);
        log.debug("Downloaded {} bytes from:  {}  to: {}", bytesWritten, source, destination);
        return bytesWritten;
      }
    }
  }

  /**
   * Creates a new instance of the template.
   *
   * @param httpClient the HTTP client to use for downloading (its life cycle should be managed
   * externally).
   * @param executorService the executor service to use for concurrent downloads (its life cycle
   * should be managed externally).
   * @param config the configuration governing how a failed download is retried.
   */
  public UrlDownloadTemplate(@Nonnull final HttpClient httpClient,
      @Nonnull final ExecutorService executorService, @Nonnull final DownloadConfig config) {
    this.httpClient = httpClient;
    this.executorService = executorService;
    this.config = config;
  }

  /**
   * Downloads the given URLs concurrently to provided destinations in a
   * {@link FileStore}.
   *
   * @param urlsToDownload the list of URLs to download together with their desired destinations.
   * @param timeout the maximum time to wait for the downloads to complete. Zero or negative values
   * are treated as infinite.
   * @return a list of the number of bytes downloaded for each URL in the same order as the input
   */
  public List<Long> download(@Nonnull final List<UrlDownloadEntry> urlsToDownload,
      @Nonnull final Duration timeout) {

    final Instant timeoutAt = toTimeoutAt(timeout);

    final Collection<Callable<Long>> tasks = urlsToDownload.stream()
        .map(e -> new UriDownloadTask(e.getSource(), e.getDestination()))
        .collect(Collectors.toUnmodifiableList());

    // submitting the task independently
    final List<Future<Long>> futures = tasks.stream().map(executorService::submit)
        .collect(Collectors.toUnmodifiableList());

    try {
      // wait for all the futures to complete or any to fail
      while (!futures.stream().allMatch(Future::isDone)
          && futures.stream().noneMatch(f -> asException(f).isPresent())) {
        if (hasExpired(timeoutAt)) {
          log.error("Cancelling download due to time limit {} exceeded at: {}", timeout,
              timeoutAt);
          throw new Timeout("Download timed out at: " + timeout);
        }
        TimeUnit.SECONDS.sleep(1);
      }
      // check if any of the futures failed
      futures.stream().map(UrlDownloadTemplate::asException)
          .filter(Optional::isPresent).flatMap(Optional::stream)
          .findAny()
          .ifPresent(e -> {
            log.error("Cancelling the download because of '{}'", unwrap(e).getMessage());
            throw new DownloadError("Download failed", unwrap(e));
          });
      return futures.stream().map(UrlDownloadTemplate::asValue).collect(Collectors.toList());
    } catch (final InterruptedException ex) {
      log.debug("Download interrupted", ex);
      throw new BulkExportException.SystemError("Download interrupted", ex);
    } finally {
      // cancel all the futures
      futures.forEach(f -> f.cancel(true));
    }
  }

  private static <T> Optional<Exception> asException(@Nonnull final Future<T> f) {
    try {
      if (f.isDone()) {
        f.get();
      }
      return Optional.empty();
    } catch (final Exception ex) {
      return Optional.of(ex);
    }
  }

  private static <T> T asValue(@Nonnull final Future<T> f) {
    if (!f.isDone()) {
      throw new IllegalStateException("Future is not done");
    }
    try {
      return f.get();
    } catch (final Exception ex) {
      throw new IllegalStateException("Unexpected exception from successful future", ex);
    }
  }

  private static Throwable unwrap(@Nonnull final Exception futureEx) {
    if (futureEx instanceof ExecutionException) {
      return futureEx.getCause();
    } else {
      return futureEx;
    }
  }
}
