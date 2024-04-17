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

package au.csiro.pathling.export.download;

import static au.csiro.pathling.export.utils.TimeoutUtils.hasExpired;
import static au.csiro.pathling.export.utils.TimeoutUtils.toTimeoutAt;

import au.csiro.pathling.export.BulkExportException;
import au.csiro.pathling.export.BulkExportException.DownloadError;
import au.csiro.pathling.export.BulkExportException.HttpError;
import au.csiro.pathling.export.BulkExportException.Timeout;
import au.csiro.pathling.export.fs.FileStore.FileHandle;
import java.io.InputStream;
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
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

/**
 * A template class for concurrent download of multiple URLs into a file store. The file store can
 * be any concrete implementation of the {@link au.csiro.pathling.export.fs.FileStore} abstraction.
 * <p>
 * This implementation fails fast: all the downloads are terminated on the first failure in any of
 * the downloads.
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

  @Value
  class UriDownloadTask implements Callable<Long> {

    @Nonnull
    URI source;

    @Nonnull
    FileHandle destination;

    @Override
    public Long call() throws Exception {
      log.debug("Starting download from:  {}  to: {}", source, destination);
      final HttpResponse result = httpClient.execute(new HttpGet(source));
      if (result.getStatusLine().getStatusCode() != 200) {
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
   */
  public UrlDownloadTemplate(@Nonnull final HttpClient httpClient,
      @Nonnull final ExecutorService executorService) {
    this.httpClient = httpClient;
    this.executorService = executorService;
  }

  /**
   * Downloads the given URLs concurrently to provided destinations in a
   * {@link au.csiro.pathling.export.fs.FileStore}.
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
