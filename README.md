# FHIR Bulk Java

[![Test](https://github.com/aehrc/fhir-bulk-client/workflows/Verify/badge.svg)](https://github.com/aehrc/fhir-bulk-client/actions?query=workflow%3AVerify)

This project is a Java implementation of the FHIR Bulk Data Access (Flat FHIR) specification.

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites

- Java
- Maven

### Installation

1. Clone the repository
```bash
git clone https://github.com/aehrc/fhir-bulk-java.git`
```

2. Build the project and run unit tsts
```bash
mvn clean install
```

## Usage

This example shows how to run system level export of Patient and Condition resources from the https://bulk-data.smarthealthit.org/ demo server.

```java
    // the Fhir endpoint URL
    final String fhirEndpointUrl = "https://bulk-data.smarthealthit.org/eyJlcnIiOiIiLCJwYWdlIjoxMDAwMCwiZHVyIjoxMCwidGx0IjoxNSwibSI6MSwic3R1Ijo0LCJkZWwiOjB9/fhir";
    
    // the directory where the exported files will be saved
    final String outputDir = "target/export-" + Instant.now().toEpochMilli();

    final BulkExportResult result = BulkExportClient.systemBuilder()
        .withFhirEndpointUrl(fhirEndpointUrl)
        .withOutputDir(outputDir)
        .withTypes(List.of("Patient", "Condition"))
        .build()
        .export();
    
    System.out.println(result);
```

## References

- Bulk Export Specification: https://build.fhir.org/ig/HL7/bulk-data/export.html
- Bulk Data Demo Server: https://bulk-data.smarthealthit.org/
- Bulk Data Client JS: https://github.com/smart-on-fhir/bulk-data-client

## Contributing

Please read CONTRIBUTING.md for details on our code of conduct, and the process for submitting pull requests to us.

## Licensing and attribution

FHIR Bulk Export Client is copyright © 2024, Commonwealth Scientific and Industrial
Research Organisation
(CSIRO) ABN 41 687 119 230. Licensed under
the [Apache License, version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

This means that you are free to use, modify and redistribute the software as
you wish, even for commercial purposes.

**FHIR Bulk Export Client is experimental software, use it at your own risk!** You can get a
full description of the current set of known issues
[here](https://github.com/aehrc/fhir-bulk-export/issues).
