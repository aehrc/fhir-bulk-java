package au.csiro.fhir.example;

import au.csiro.fhir.export.BulkExportClient;
import au.csiro.fhir.export.BulkExportResult;
import au.csiro.fhir.export.ws.AssociatedData;
import au.csiro.fhir.model.Reference;
import java.time.Instant;
import java.util.List;


/**
 * The minimal example
 */
public class BulkDataGroupLevelExportApp {

  public static void main(String[] args) {
    final String fhirEndpointUrl = "https://bulk-data.smarthealthit.org/eyJlcnIiOiIiLCJwYWdlIjoxMDAwMCwiZHVyIjoxMCwidGx0IjoxNSwibSI6MSwic3R1Ijo0LCJkZWwiOjB9/fhir";
    final String outputDir = "target/export-" + Instant.now().toEpochMilli();
    final BulkExportResult result = BulkExportClient.groupBuilder("BMCHealthNet")
        .withFhirEndpointUrl(fhirEndpointUrl)
        .withOutputDir(outputDir)
        .withOutputFormat("ndjson")
        .withSince(Instant.parse("2015-01-01T00:00:00.000Z"))
        .withTypes(List.of("Patient", "Condition"))
        .withType("Observation")
        .withElements(List.of("id", "status"))
        .withIncludeAssociatedDatum(AssociatedData.LATEST_PROVENANCE_RESOURCES)
        .withIncludeAssociatedDatum(AssociatedData.custom("custom-value"))
        .withTypeFilter("Patient?status=active")
        .build()
        .export();
  }
}
