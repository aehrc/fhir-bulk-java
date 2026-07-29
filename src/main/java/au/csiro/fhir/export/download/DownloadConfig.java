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

package au.csiro.fhir.export.download;

import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Value;

/**
 * Configuration relating to the download of the output files of an export.
 */
@Value
@Builder
public class DownloadConfig {

  /**
   * The number of times a single file is retried after its first attempt fails. Zero disables
   * retrying, so the default of four allows five attempts in total.
   */
  @Builder.Default
  @Min(0)
  int maxRetries = 4;
}
