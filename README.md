# FHIR Bulk Client for Java

[![Test](https://github.com/aehrc/fhir-bulk-client/workflows/Verify/badge.svg)](https://github.com/aehrc/fhir-bulk-client/actions?query=workflow%3AVerify)

This project is a Java library with the API for the FHIR Bulk Export Client
compatible with
the [Bulk Data Access specification](https://hl7.org/fhir/uv/bulkdata/export.html).

Supported features:

- Fluent Java API for configuring and running the export
- System, group and patient level export
- All Bulk Data v2 [query parameters](https://hl7.org/fhir/uv/bulkdata/export.html#query-parameters) 
  (with some limitations for patient references)
- Asymmetric and
  symmetric [SMART authentication profiles](https://www.hl7.org/fhir/smart-app-launch/client-authentication.html)
- Automatic token endpoint discovery with SMART configuration discovery
- Parallel download with configurable concurrency
- Download destination to a file system abstraction with implementation for local
  filesystem
- Predictable naming of downloaded files
- Customisable run-time configuration
- `slf4j` based logging

Limitations:

- Only `reference` elements are supported in the `Reference` resource 
  (see: `patient` query parameter)
- Attachment references in downloaded resources are not downloaded
- No support to request lenient processing at the server 
  (`Prefer: handling=lenient`)
- `deleted` and `error` urls in the kick-off response are not downloaded
- Downloaded files are NOT validated in any way

## Getting Started

To use the FHIR Bulk Java client in your project, add the following dependency:

```xml
<dependency>
    <groupId>au.csiro.fhir</groupId>
    <artifactId>bulk-export</artifactId>
    <version>[desired version]</version>
</dependency>
```

The FHIR build export is implemented in
the `au.csiro.fhir.export.BulkExportClient` class.
Use one of the builder methods to create a client instance and run the export.

```java 
final BulkExportClient client = BulkExportClient.systemBuilder()
        .withFhirEndpointUrl(fhirEndpointUrl)
        .withOutputDir(outputDir)
        .build();

final BulkExportResult result = client.export();
// do something with the result
```

See: [Usage examples](#usage-examples) for more details of how to use the client
in common scenarios.

### Building locally

Prerequisites:

- Java 11+
- Maven 3+

Build the project and run unit tests with:

```bash
mvn clean install
```

## Usage examples

### Default system level export (unauthenticated)

System level export with default settings form
the [demo server](https://bulk-data.smarthealthit.org/) with unauthenticated
access. The exported files will be downloaded to the `output-dir` directory with
the default `.ndjson` extension.

```java
// The FHIR endpoint URL for https://bulk-data.smarthealthit.org/.
final String fhirEndpointUrl = "https://bulk-data.smarthealthit.org/eyJlcnIiOiIiLCJwYWdlIjoxMDAwMCwiZHVyIjoxMCwidGx0IjoxNSwibSI6MSwic3R1Ijo0LCJkZWwiOjB9/fhir";

// The directory where the exported files will be saved.
final String outputDir = "output-dir";

// Build the client with a system level builder and run the export.
final BulkExportResult result = BulkExportClient.systemBuilder()
        .withFhirEndpointUrl(fhirEndpointUrl)
        .withOutputDir(outputDir)
        .build()
        .export();

// Examine the result.
result.getTransactionTime();
```

### Fully customised group level export (unauthenticated)

Group level export for the `'BMCHealthNet'` group with all supported export
parameters (except
for `patient`) from the [demo server](https://bulk-data.smarthealthit.org/) with
unauthenticated access.

Please note that properties such as `types` or `elements` which correspond to
coma-delimited query parameters in
the [bulk data kick-off reqeust specification](https://hl7.org/fhir/uv/bulkdata/export.html#bulk-data-kick-off-request)
can accept both lists of objects 
(e.g. `withTypes(List.of("Patient","Condition"))`) and individual objects 
(e.g. `withType("Observation")`), all of which are combined to form the final
value.

In this case the final value of the `_type` query parameter is
to: `Patient,Condition,Observation`.

```java
// The FHIR endpoint URL for https://bulk-data.smarthealthit.org/.
final String fhirEndpointUrl = "https://bulk-data.smarthealthit.org/eyJlcnIiOiIiLCJwYWdlIjoxMDAwMCwiZHVyIjoxMCwidGx0IjoxNSwibSI6MSwic3R1Ijo0LCJkZWwiOjB9/fhir";

// The directory where the exported files will be saved.
final String outputDir = "output-dir";

// Create a group level builder for 'BMCHealthNet' group.
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
```

### Patient level export with references (unauthenticated)

Patient level export from the
[demo server](https://bulk-data.smarthealthit.org/) with unauthenticated access
and explicit set of Patient references to be included in the export.

Please note that currently only the `reference` element is supported in
the `Reference` resource.

```java
// The FHIR endpoint URL for https://bulk-data.smarthealthit.org/.
final String fhirEndpointUrl = "https://bulk-data.smarthealthit.org/eyJlcnIiOiIiLCJwYWdlIjoxMDAwMCwiZHVyIjoxMCwidGx0IjoxNSwibSI6MSwic3R1Ijo0LCJkZWwiOjB9/fhir";

// The directory where the exported files will be saved.
final String outputDir = "output-dir";

// Create a patient level builder.
final BulkExportResult result = BulkExportClient.patientBuilder()
        .withFhirEndpointUrl(fhirEndpointUrl)
        .withOutputDir(outputDir)
        .withPatient(Reference.of("Patient/736a19c8-eea5-32c5-67ad-1947661de21a"))
        .withPatient(Reference.of("Patient/26d06b50-7868-829d-cf71-9f9a68901a81"))
        .withPatient(Reference.of("Patient/0b733e7c-12a6-ddb0-d5e1-e32372002621"))
        .build()
        .export();

```

### Asymmetric authentication with JWK key

Export with the asymmetric authentication. The asymmetric authentication profile
is implicitly selected by setting the `privateKeyJWK` property in
the `AuthConfig` object.

- The client ID needs to be obtained from the server and the public key
  associated with the private key used for authentication needs to be
  registered with the server beforehand.
- The private key is provided to the client in the JSON Web Key (JWK) format
  it needs to be compatible with one of the supported algorithms (e.g. `RS384`).
- The client by default uses SMART configuration discovery to obtain the token
  endpoint from the FHIR endpoint.

```java
// The authenticated FHIR endpoint URL for https://bulk-data.smarthealthit.org/.
final String fhirEndpointUrl = "https://bulk-data.smarthealthit.org/eyJlcnIiOiIiLCJwYWdlIjoxMDAwMCwiZHVyIjoxMCwidGx0IjoxNSwibSI6MSwic3R1Ijo0LCJkZWwiOjAsInNlY3VyZSI6MX0/fhir";

// The directory where the exported files will be saved.
final String outputDir = "output-dir";

// The authentication parameters.
final String clientId = "eyJhbGciOiJIUzI..."; // The client ID from the bulk-data server.
final String privateKeyJWK = "{ "\"kty\":\"RSA\", ...}"; // The private key in the JSON Web Key format.

// Create an authentication configuration, the asymmetric authentication profile 
// is implicitly selected by setting the privateKeyJWK property.
final AuthConfig authConfig = AuthConfig.builder()
        .enabled(true)
        .useSMART(true) // Use SMART configuration discovery (default).
        .clientId(clientId)
        .privateKeyJWK(privateKeyJWK)
        .scope("system/*.read")
        .tokenExpiryTolerance(30)
        .build();

// Use the system level builder with the authentication configuration.
final BulkExportResult result = BulkExportClient.systemBuilder()
        .withFhirEndpointUrl(fhirEndpointUrl)
        .withOutputDir(outputDir)
        .withAuthConfig(authConfig) // configure authentication in the client
        .build()
        .export();
```

### Symmetric authentication with client secret and explict token endpoint

Export with the symmetric authentication. The symmetric authentication profile
is implicitly selected by setting the `clientSecret` property in
the `AuthConfig` object.

SMART configuration discovery is disabled by setting the `useSMART` property
to `false` and the token endpoint is explicitly set in the `tokenEndpoint`
property.

```java
// The authenticated FHIR endpoint at Cerner.
final String fhirEndpointUrl = "https://fhir-ehr-code.cerner.com/r4/ec2458f2-1e24-41c8-b71b-0e701af7583d";

// The directory where the exported files will be saved.
final String outputDir = "output-dir";

// The authentication parameters.
final String clientId = "4ccde388-...";
final String clientSecret = "XYX.....";

// Create an authentication configuration, the symmetric authentication profile 
// is implicitly selected by setting the clientSecret property.
final AuthConfig authConfig = AuthConfig.builder()
        .enabled(true)
        .useSMART(false) // Disable SMART configuration discovery and configure the token endpoint explicitly.
        .tokenEndpoint(
                "https://authorization.cerner.com/tenants/ec2458f2-1e24-41c8-b71b-0e701af7583d/protocols/oauth2/profiles/smart-v1/token")
        .clientId(clientId)
        .clientSecret(clientSecret)
        .scope("system/Patient.read")
        .build();

// Use the system level builder with the authentication configuration.
final BulkExportResult result = BulkExportClient.systemBuilder()
        .withFhirEndpointUrl(fhirEndpointUrl)
        .withOutputDir(outputDir)
        .withAuthConfig(authConfig) // Configure authentication in the client.
        .build()
        .export();
```

### Customised run-time configuration

Various aspects of the client can be customised either directly in the builder
or by providing a customised sub-configuration objects.

- Maximum concurrent downloads or export timeout can be set directly in the
  builder
- `AsyncConfig` - customise the asynchronous protocol configuration (e.g. max
  transient errors, max pooling delay)
- `HttpClientConfig` - customise the HTTP client configuration (e.g. retry
  count, socket timeout)

```java
// The FHIR endpoint URL for https://bulk-data.smarthealthit.org/.
final String fhirEndpointUrl = "https://bulk-data.smarthealthit.org/eyJlcnIiOiIiLCJwYWdlIjoxMDAwMCwiZHVyIjoxMCwidGx0IjoxNSwibSI6MSwic3R1Ijo0LCJkZWwiOjB9/fhir";

// The directory where the exported files will be saved.
final String outputDir = "output-dir";

// Create a customised FHIR async protocol configuration.
final AsyncConfig asyncConfig = AsyncConfig.builder()
        .maxTransientErrors(5)
        .maxPoolingDelay(Duration.ofMinutes(5))
        .build();

// Create a customised HTTP client configuration.
final HttpClientConfig httpClientConfig = HttpClientConfig.builder()
        .retryCount(5)
        .socketTimeout(10_000)
        .build();

// Build the client with a system level and customised run-time configuration.
final BulkExportResult result = BulkExportClient.systemBuilder()
        .withFhirEndpointUrl(fhirEndpointUrl)
        .withOutputDir(outputDir)
        .withMaxConcurrentDownloads(5)
        .withTimeout(Duration.ofMinutes(60))
        .withAsyncConfig(asyncConfig)
        .withHttpClientConfig(httpClientConfig)
        .build()
        .export();
```

## Contributing

Please read [CONTRIBUTING.md](./CONTRIBUTING.md) for details on our code of
conduct, and the process for submitting pull requests to us.

## Licensing and attribution

FHIR Bulk Export Client is copyright © 2024, Commonwealth Scientific and
Industrial
Research Organisation
(CSIRO) ABN 41 687 119 230. Licensed under
the [Apache License, version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

This means that you are free to use, modify and redistribute the software as
you wish, even for commercial purposes.

**FHIR Bulk Export Client is experimental software, use it at your own risk!**
You can get a
full description of the current set of known issues
[here](https://github.com/aehrc/fhir-bulk-export/issues).
