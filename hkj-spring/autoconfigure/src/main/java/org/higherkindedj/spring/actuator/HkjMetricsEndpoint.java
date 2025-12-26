// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.actuator;

import java.util.LinkedHashMap;
import java.util.Map;
import org.higherkindedj.spring.autoconfigure.HkjProperties;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * Custom actuator endpoint for higher-kinded-j Spring integration metrics.
 *
 * <p>Exposed at: {@code /actuator/hkj}
 *
 * <p>Provides comprehensive information about:
 *
 * <ul>
 *   <li>HKJ configuration (enabled features, settings)
 *   <li>Handler invocation counts (Either, Validated, EitherT)
 *   <li>Success/error ratios
 *   <li>Jackson serialization settings
 * </ul>
 *
 * <p>Example response:
 *
 * <pre>{@code
 * {
 *   "configuration": {
 *     "web": {
 *       "eitherResponseEnabled": true,
 *       "validatedResponseEnabled": true,
 *       "asyncEitherTEnabled": true,
 *       "defaultErrorStatus": 400
 *     },
 *     "jackson": {
 *       "customSerializersEnabled": true,
 *       "eitherFormat": "TAGGED",
 *       "validatedFormat": "TAGGED"
 *     }
 *   },
 *   "metrics": {
 *     "either": {
 *       "successCount": 150,
 *       "errorCount": 25,
 *       "totalCount": 175,
 *       "successRate": 0.857
 *     },
 *     "validated": {
 *       "validCount": 200,
 *       "invalidCount": 50,
 *       "totalCount": 250,
 *       "validRate": 0.800
 *     },
 *     "eitherT": {
 *       "successCount": 75,
 *       "errorCount": 10,
 *       "totalCount": 85,
 *       "successRate": 0.882
 *     }
 *   }
 * }
 * }</pre>
 */
@Endpoint(id = "hkj")
public class HkjMetricsEndpoint {

  private final HkjProperties properties;
  private final HkjMetricsService metricsService;

  /**
   * Creates a new HkjMetricsEndpoint.
   *
   * @param properties the HKJ configuration properties
   * @param metricsService the metrics service (may be null if metrics disabled)
   */
  public HkjMetricsEndpoint(HkjProperties properties, HkjMetricsService metricsService) {
    this.properties = properties;
    this.metricsService = metricsService;
  }

  /**
   * Reads the current HKJ metrics and configuration.
   *
   * @return map containing configuration and metrics data
   */
  @ReadOperation
  public Map<String, Object> hkjMetrics() {
    Map<String, Object> result = new LinkedHashMap<>();

    // Configuration section
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("web", getWebConfig());
    config.put("jackson", getJacksonConfig());
    result.put("configuration", config);

    // Metrics section (if enabled)
    if (metricsService != null) {
      Map<String, Object> metrics = new LinkedHashMap<>();
      metrics.put("either", getEitherMetrics());
      metrics.put("validated", getValidatedMetrics());
      metrics.put("eitherT", getEitherTMetrics());
      result.put("metrics", metrics);
    } else {
      result.put("metrics", Map.of("enabled", false));
    }

    return result;
  }

  private Map<String, Object> getWebConfig() {
    Map<String, Object> web = new LinkedHashMap<>();
    web.put("eitherPathEnabled", properties.getWeb().isEitherPathEnabled());
    web.put("maybePathEnabled", properties.getWeb().isMaybePathEnabled());
    web.put("tryPathEnabled", properties.getWeb().isTryPathEnabled());
    web.put("validationPathEnabled", properties.getWeb().isValidationPathEnabled());
    web.put("ioPathEnabled", properties.getWeb().isIoPathEnabled());
    web.put("completableFuturePathEnabled", properties.getWeb().isCompletableFuturePathEnabled());
    web.put("defaultErrorStatus", properties.getWeb().getDefaultErrorStatus());
    return web;
  }

  private Map<String, Object> getJacksonConfig() {
    Map<String, Object> jackson = new LinkedHashMap<>();
    jackson.put("customSerializersEnabled", properties.getJson().isCustomSerializersEnabled());
    jackson.put("eitherFormat", properties.getJson().getEitherFormat().name());
    jackson.put("validatedFormat", properties.getJson().getValidatedFormat().name());
    jackson.put("maybeFormat", properties.getJson().getMaybeFormat().name());
    return jackson;
  }

  private Map<String, Object> getEitherMetrics() {
    Map<String, Object> either = new LinkedHashMap<>();
    double successCount = metricsService.getEitherSuccessCount();
    double errorCount = metricsService.getEitherErrorCount();
    double totalCount = successCount + errorCount;

    either.put("successCount", (long) successCount);
    either.put("errorCount", (long) errorCount);
    either.put("totalCount", (long) totalCount);
    either.put("successRate", totalCount > 0 ? successCount / totalCount : 0.0);

    return either;
  }

  private Map<String, Object> getValidatedMetrics() {
    Map<String, Object> validated = new LinkedHashMap<>();
    double validCount = metricsService.getValidatedValidCount();
    double invalidCount = metricsService.getValidatedInvalidCount();
    double totalCount = validCount + invalidCount;

    validated.put("validCount", (long) validCount);
    validated.put("invalidCount", (long) invalidCount);
    validated.put("totalCount", (long) totalCount);
    validated.put("validRate", totalCount > 0 ? validCount / totalCount : 0.0);

    return validated;
  }

  private Map<String, Object> getEitherTMetrics() {
    Map<String, Object> eitherT = new LinkedHashMap<>();
    double successCount = metricsService.getEitherTSuccessCount();
    double errorCount = metricsService.getEitherTErrorCount();
    double totalCount = successCount + errorCount;

    eitherT.put("successCount", (long) successCount);
    eitherT.put("errorCount", (long) errorCount);
    eitherT.put("totalCount", (long) totalCount);
    eitherT.put("successRate", totalCount > 0 ? successCount / totalCount : 0.0);

    return eitherT;
  }
}
