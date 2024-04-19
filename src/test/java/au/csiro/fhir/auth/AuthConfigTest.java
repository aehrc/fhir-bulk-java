package au.csiro.fhir.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import au.csiro.utils.ValidationUtils;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class AuthConfigTest {

  @Test
  void testValidIfDisabledDespiteMissingParameters() {
    final AuthConfig config = AuthConfig.builder()
        .enabled(false)
        .build();
    assertEquals(Collections.emptySet(), ValidationUtils.validate(config));
  }

  @Test
  void testValidSMARTWithSymmetricCredentials() {
    final AuthConfig config = AuthConfig.builder()
        .enabled(true)
        .useSMART(true)
        .clientId("client-id")
        .clientSecret("client-secret")
        .build();
    assertEquals(Collections.emptySet(), ValidationUtils.validate(config));
  }

  @Test
  void testValidNoSMARTWithAsymmetricCredentials() {
    final AuthConfig config = AuthConfig.builder()
        .enabled(true)
        .useSMART(false)
        .tokenEndpoint("https://example.com/token")
        .clientId("client-id")
        .privateKeyJWK("private-key-jwk")
        .build();
    assertEquals(Collections.emptySet(), ValidationUtils.validate(config));
  }

  @Test
  void testReportsViolationsForInvalidConfiguration() {
    final AuthConfig config = AuthConfig.builder()
        .enabled(true)
        .useSMART(false)
        .build();
    assertEquals(
        "clientId: must be supplied if auth is enabled\n"
            + "either clientSecret or privateKeyJWK must be supplied if auth is enabled\n"
            + "tokenEndpoint: must be supplied if SMART configuration is not used and auth is enabled",
        ValidationUtils.formatViolations(ValidationUtils.validate(config)));
  }
}
