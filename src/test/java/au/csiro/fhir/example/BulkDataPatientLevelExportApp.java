package au.csiro.fhir.example;

import au.csiro.fhir.export.BulkExportClient;
import au.csiro.fhir.export.BulkExportResult;
import au.csiro.fhir.model.Reference;
import java.time.Duration;
import java.time.Instant;


/**
 * The minimal example
 */
public class BulkDataPatientLevelExportApp {

  public static void main(String[] args) {
    final String fhirEndpointUrl = "https://bulk-data.smarthealthit.org/eyJlcnIiOiIiLCJwYWdlIjoxMDAwMCwiZHVyIjoxMCwidGx0IjoxNSwibSI6MSwic3R1Ijo0LCJkZWwiOjB9/fhir";
    final String outputDir = "target/export-" + Instant.now().toEpochMilli();
    final BulkExportResult result = BulkExportClient.patientBuilder()
        .withFhirEndpointUrl(fhirEndpointUrl)
        .withOutputDir(outputDir)
        .withPatient(Reference.of("Patient/736a19c8-eea5-32c5-67ad-1947661de21a"))
        .withPatient(Reference.of("Patient/26d06b50-7868-829d-cf71-9f9a68901a81"))
        .withPatient(Reference.of("Patient/0b733e7c-12a6-ddb0-d5e1-e32372002621"))
        .build()
        .export();
  }
}
