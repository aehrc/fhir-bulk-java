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

package au.csiro.fhir.auth;

import au.csiro.http.TokenCredentials;
import java.io.Closeable;
import java.net.URI;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Provides tokens for authentication.
 */
public interface TokenCredentialFactory extends Closeable {

  /**
   * Creates a token credential for the given FHIR endpoint and authentication configuration.
   *
   * @param fhirEndpoint the FHIR endpoint
   * @param authConfig the authentication configuration
   * @return the token credential
   */
  @Nonnull
  Optional<TokenCredentials> createCredentials(@Nonnull final URI fhirEndpoint,
      @Nonnull final AuthConfig authConfig);

}
