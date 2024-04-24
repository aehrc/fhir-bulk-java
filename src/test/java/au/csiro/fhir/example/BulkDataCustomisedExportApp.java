package au.csiro.fhir.example;

import au.csiro.fhir.export.BulkExportClient;
import au.csiro.fhir.export.BulkExportResult;
import au.csiro.fhir.export.ws.AsyncConfig;
import au.csiro.http.HttpClientConfig;

import java.time.Duration;
import java.time.Instant;


/**
 * The minimal example
 */
public class BulkDataCustomisedExportApp {

  public static void main(String[] args) {
    // NO ERRORS
    final String fhirEndpointUrl = "https://bulk-data.smarthealthit.org/eyJlcnIiOiIiLCJwYWdlIjoxMDAwMCwiZHVyIjoxMCwidGx0IjoxNSwibSI6MSwic3R1Ijo0LCJkZWwiOjB9/fhir";
    final String outputDir = "target/export-" + Instant.now().toEpochMilli();

    
    
    final AsyncConfig asyncConfig = AsyncConfig.builder()
        .maxTransientErrors(5)
        .maxPoolingDelay(Duration.ofMinutes(5))
        .build();

    final HttpClientConfig httpClientConfig = HttpClientConfig.builder()
        .retryCount(5)
        .socketTimeout(10_000)
        .build();
    
    final BulkExportResult result = BulkExportClient.systemBuilder()
        .withFhirEndpointUrl(fhirEndpointUrl)
        .withOutputDir(outputDir)
        .withMaxConcurrentDownloads(5)
        .withTimeout(Duration.ofMinutes(60))
        .withAsyncConfig(asyncConfig)
        .withHttpClientConfig(httpClientConfig)
        .build()
        .export();
    
    System.out.print(result);
  }
}
