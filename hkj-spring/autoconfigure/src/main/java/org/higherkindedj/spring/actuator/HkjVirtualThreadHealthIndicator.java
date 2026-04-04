// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.actuator;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Health indicator for VTask and VStream virtual thread operations.
 *
 * <p>Monitors error rates for virtual thread based computations to ensure system stability. Health
 * is determined by comparing the failure rate against a configurable threshold.
 *
 * <p>Example health response:
 *
 * <pre>{@code
 * {
 * "status": "UP",
 * "details": {
 * "vtask.successCount": 100.0,
 * "vtask.errorCount": 0.0,
 * "vtask.totalCount": 100.0,
 * "vtask.successRate": 1.0,
 * "vstream.successCount": 50.0,
 * "vstream.errorCount": 5.0,
 * "vstream.totalCount": 55.0,
 * "vstream.successRate": 0.909
 * }
 * }
 * }</pre>
 */
public class HkjVirtualThreadHealthIndicator implements HealthIndicator {

  private final HkjMetricsService metricsService;
  private final double errorThreshold;

  /**
   * Creates a new HkjVirtualThreadHealthIndicator with a default error threshold of 50%.
   *
   * @param metricsService the service providing VTask and VStream metrics
   */
  public HkjVirtualThreadHealthIndicator(HkjMetricsService metricsService) {
    this(metricsService, 0.5);
  }

  /**
   * Creates a new HkjVirtualThreadHealthIndicator with a custom error threshold.
   *
   * @param metricsService the service providing VTask and VStream metrics
   * @param errorThreshold the error rate threshold (0.0 to 1.0) above which status becomes DOWN
   */
  public HkjVirtualThreadHealthIndicator(HkjMetricsService metricsService, double errorThreshold) {
    this.metricsService = metricsService;
    this.errorThreshold = errorThreshold;
  }

  @Override
  public Health health() {
    if (metricsService == null) {
      return Health.outOfService().withDetail("reason", "Metrics service is unavailable").build();
    }

    try {
      double vTaskSuccess = metricsService.getVTaskSuccessCount();
      double vTaskError = metricsService.getVTaskErrorCount();
      double vStreamSuccess = metricsService.getVStreamSuccessCount();
      double vStreamError = metricsService.getVStreamErrorCount();

      double vTaskSuccessRate = calculateSuccessRate(vTaskSuccess, vTaskError);
      double vStreamSuccessRate = calculateSuccessRate(vStreamSuccess, vStreamError);

      boolean vTaskDegraded = (1.0 - vTaskSuccessRate) > errorThreshold;
      boolean vStreamDegraded = (1.0 - vStreamSuccessRate) > errorThreshold;

      Health.Builder builder = (vTaskDegraded || vStreamDegraded) ? Health.down() : Health.up();

      return builder
          .withDetail("vtask.successCount", vTaskSuccess)
          .withDetail("vtask.errorCount", vTaskError)
          .withDetail("vtask.totalCount", vTaskSuccess + vTaskError)
          .withDetail("vtask.successRate", vTaskSuccessRate)
          .withDetail("vstream.successCount", vStreamSuccess)
          .withDetail("vstream.errorCount", vStreamError)
          .withDetail("vstream.totalCount", vStreamSuccess + vStreamError)
          .withDetail("vstream.successRate", vStreamSuccessRate)
          .build();
    } catch (Exception e) {
      return Health.down().withDetail("error", e.getMessage()).build();
    }
  }

  private double calculateSuccessRate(double success, double error) {
    double total = success + error;
    return total > 0 ? success / total : 1.0;
  }
}
