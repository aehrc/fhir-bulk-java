# FHIR Bulk Java

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

## Contributing

Please read CONTRIBUTING.md for details on our code of conduct, and the process for submitting pull requests to us.  


## License

This project is licensed under the Apache 2.0 Licence - see the LICENSE file for details

## Acknowledgments

- Bulk Export Specification: https://build.fhir.org/ig/HL7/bulk-data/export.html
- Bulk Data Demo Server: https://bulk-data.smarthealthit.org/
- Bulk Data Client JS: https://github.com/smart-on-fhir/bulk-data-client
