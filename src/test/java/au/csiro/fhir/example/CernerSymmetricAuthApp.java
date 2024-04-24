package au.csiro.fhir.example;

import au.csiro.fhir.auth.AuthConfig;
import au.csiro.fhir.export.BulkExportClient;
import java.time.Instant;

public class CernerSymmetricAuthApp {
  public static void main(String[] args) {
    final String fhirEndpointUrl = "https://fhir-ehr-code.cerner.com/r4/ec2458f2-1e24-41c8-b71b-0e701af7583d";
    final String outputDir = "target/export-" + Instant.now().toEpochMilli();


    final String clientId = "4ccde388-534e-482b-b6ca-c55571432c08";
    final String clientSecret = System.getProperty("pszul.cerner.clientSecret");
    System.out.println("client secret: " + clientSecret);
    
    final AuthConfig authConfig = AuthConfig.builder()
        .enabled(true)
        .useSMART(false)
        .tokenEndpoint(
            "https://authorization.cerner.com/tenants/ec2458f2-1e24-41c8-b71b-0e701af7583d/protocols/oauth2/profiles/smart-v1/token")
        .clientId(clientId)
        .clientSecret(clientSecret)
        .scope("system/Patient.read")
        .build();

    BulkExportClient.systemBuilder()
        .withAuthConfig(authConfig)
        .withFhirEndpointUrl(fhirEndpointUrl)
        .withOutputDir(outputDir)
        .build()
        .export();
  }
}
