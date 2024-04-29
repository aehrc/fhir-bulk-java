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

import au.csiro.fhir.auth.AuthConfig.ValidAuthConfig;
import au.csiro.utils.ValidationUtils.ViolationAccumulator;
import java.io.Serializable;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.annotation.Nullable;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import org.hibernate.validator.constraints.URL;

/**
 * Configuration relating to authentication of requests to the terminology service.
 *
 * @author John Grimes
 */
@Data
@Builder
@ValidAuthConfig
public class AuthConfig implements Serializable {

  private static final long serialVersionUID = 6321330066417583745L;

  /**
   * Enables authentication of requests to the terminology server.
   */

  @Builder.Default
  private boolean enabled = false;


  /**
   * Use SMART configuration to discover token endpoint.
   */
  @Builder.Default
  private boolean useSMART = true;

  /**
   * An OAuth2 token endpoint for use with the client credentials grant. Only applicable if
   * {@link #useSMART} is false.
   */
  @Nullable
  @URL
  private String tokenEndpoint;

  /**
   * A client ID for use with the client credentials grant.
   */
  @Nullable
  private String clientId;

  /**
   * A client secret for use with the symmetric client authentication.
   */
  @Nullable
  @ToString.Exclude
  private String clientSecret;

  /**
   * A private key in JWK format to use with the asymmetric client authentication.
   */
  @Nullable
  @ToString.Exclude
  private String privateKeyJWK;


  /**
   * Send the basic authentication credentials in the form body instead of the Authorization
   * header.
   */
  @Builder.Default
  private boolean useFormForBasicAuth = false;

  /**
   * A scope value for use with the client credentials grant.
   */
  @Nullable
  private String scope;

  /**
   * The minimum number of seconds that a token should have before expiry when deciding whether to
   * send it with a terminology request.
   */
  @NotNull
  @Min(0)
  @Builder.Default
  private long tokenExpiryTolerance = 120;

  /**
   * The maximum number of seconds that a token should be cached for.
   */
  @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @Constraint(validatedBy = AuthConfigValidator.class)
  @Documented
  public @interface ValidAuthConfig {

    /**
     * @return the error message template
     */
    String message() default "Invalid AuthConfig";

    /**
     * @return the groups the constraint belongs to
     */
    Class<?>[] groups() default {};

    /**
     * @return the payload associated to the constraint
     */
    Class<? extends Payload>[] payload() default {};

  }
  
  /**
   * Validates the configuration.
   */
  public static class AuthConfigValidator implements
      ConstraintValidator<ValidAuthConfig, AuthConfig> {

    @Override
    public boolean isValid(final AuthConfig value,
        final ConstraintValidatorContext context) {
      if (value.isEnabled()) {
        return ViolationAccumulator.withNoDefault(context)
            .checkThat(value.isUseSMART() || value.getTokenEndpoint() != null,
                "must be supplied if SMART configuration is not used and auth is enabled",
                "tokenEndpoint")
            .checkThat(value.getClientId() != null, "must be supplied if auth is enabled",
                "clientId")
            .checkThat(value.getClientSecret() != null || value.getPrivateKeyJWK() != null,
                "either clientSecret or privateKeyJWK must be supplied if auth is enabled")
            .isValid();
      }
      return true;
    }
  }
}
