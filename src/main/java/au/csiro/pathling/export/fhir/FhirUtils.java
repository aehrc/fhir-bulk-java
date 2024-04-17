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

package au.csiro.pathling.export.fhir;

import lombok.experimental.UtilityClass;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.annotation.Nonnull;

/**
 * Utility methods for working with FHIR types and resources.
 */
@UtilityClass
public class FhirUtils {

  private static final ZoneId UTC_ZONE_ID = ZoneId.of("UTC");

  private static final DateTimeFormatter FHIR_INSTANT_FORMAT = DateTimeFormatter
      .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(UTC_ZONE_ID);

  /**
   * Formats an {@link Instant} as a FHIR instant string.
   * @param instant The instant to format.
   * @return The formatted string.
   */
  @Nonnull
  public static String formatFhirInstant(@Nonnull final Instant instant) {
    return FHIR_INSTANT_FORMAT.format(instant);
  }

  /**
   * Parses a FHIR instant string into an {@link Instant}.
   * @param instantString The string to parse.
   * @return The parsed instant.
   */
  @Nonnull
  public static Instant parseFhirInstant(@Nonnull final String instantString) {
    return FHIR_INSTANT_FORMAT.parse(instantString, Instant::from);
  }
}
