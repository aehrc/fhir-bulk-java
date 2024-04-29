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

package au.csiro.fhir.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import au.csiro.fhir.auth.AuthConfig;
import au.csiro.fhir.export.BulkExportClient.BulkExportClientBuilder;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BulkExportClient}.
 */
public class BulkExportClientBuilderTest {
  
  @Test
  void testFailsEarlyWithInvalidConfiguration() {

    final BulkExportClientBuilder invalidBuilder = BulkExportClient.builder()
        .withFhirEndpointUrl("invalid.url")
        .withOutputDir("output-dir")
        .withAuthConfig(AuthConfig.builder().enabled(true).build());

    final ConstraintViolationException ex = assertThrows(ConstraintViolationException.class,
        invalidBuilder::build);

    assertEquals(
        "Invalid Bulk Export Client Configuration\n"
            + "authConfig.clientId: must be supplied if auth is enabled\n"
            + "authConfig: either clientSecret or privateKeyJWK must be supplied if auth is enabled\n"
            + "fhirEndpointUrl: must be a valid URL",
        ex.getMessage());
  }

}
