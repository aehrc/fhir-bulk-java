package au.csiro.fhir.example;

import au.csiro.fhir.auth.AuthConfig;
import au.csiro.fhir.export.BulkExportClient;
import au.csiro.fhir.export.BulkExportResult;
import au.csiro.fhir.export.ws.AssociatedData;
import java.time.Instant;
import java.util.List;

import static au.csiro.test.TestUtils.getResourceAsString;


/**
 * The minimal example
 */
public class BulkDataAsymmetricAuthApp {

  public static void main(String[] args) {
    final String fhirEndpointUrl = "https://bulk-data.smarthealthit.org/eyJlcnIiOiIiLCJwYWdlIjoxMDAwMCwiZHVyIjoxMCwidGx0IjoxNSwibSI6MSwic3R1Ijo0LCJkZWwiOjAsInNlY3VyZSI6MX0/fhir";
    final String outputDir = "target/export-" + Instant.now().toEpochMilli();


    final String clientId = getResourceAsString("auth/bulk_rs384_clientId.txt");
    final String privateKeyJWK = getResourceAsString("auth/bulk_rs384_priv_jwk.json");

    System.out.println("clientId: " + clientId);
    System.out.println("privateKeyJWK: " + privateKeyJWK);

    final AuthConfig authConfig = AuthConfig.builder()
        .enabled(true)
        .useSMART(true)
        .clientId(clientId)
        .privateKeyJWK(privateKeyJWK)
        .scope("system/*.read")
        .tokenExpiryTolerance(30)
        .build();
    
    final BulkExportResult result = BulkExportClient.systemBuilder()
        .withFhirEndpointUrl(fhirEndpointUrl)
        .withOutputDir(outputDir)
        .withAuthConfig(authConfig)
        .build()
        .export();

  }
}
