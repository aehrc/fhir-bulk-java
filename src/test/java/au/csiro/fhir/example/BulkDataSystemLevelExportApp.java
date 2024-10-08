package au.csiro.fhir.example;

import au.csiro.fhir.export.BulkExportClient;
import au.csiro.fhir.export.BulkExportResult;
import au.csiro.fhir.export.ws.AsyncConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;


/**
 * The minimal example
 */
public class BulkDataSystemLevelExportApp {

  public static void main(String[] args) {
    // NO ERRORS
    final String fhirEndpointUrl = "https://bulk-data.smarthealthit.org/eyJlcnIiOiIiLCJwYWdlIjoxMDAwMCwiZHVyIjoxMCwidGx0IjoxNSwibSI6MSwic3R1Ijo0LCJkZWwiOjB9/fhir";
    final Instant from = Instant.parse("2020-01-01T00:00:00.000Z");
    // Bulk Export Demo Server
    final String outputDir = "target/export-" + Instant.now().toEpochMilli();

    System.out.println(
        "Exporting" + "\n from: " + fhirEndpointUrl + "\n to: " + outputDir + "\n since: " + from);

    final BulkExportResult result = BulkExportClient.systemBuilder()
        .withFhirEndpointUrl(fhirEndpointUrl)
        .withOutputDir(outputDir)
        .build()
        .export();
    
    
    
    
    System.out.print(result);
  }
}
