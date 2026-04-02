// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

@DisplayName("HkjVirtualThreadHealthIndicator Tests")
class HkjVirtualThreadHealthIndicatorTest {

  private MeterRegistry meterRegistry;
  private HkjMetricsService metricsService;
  private HkjVirtualThreadHealthIndicator healthIndicator;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    metricsService = new HkjMetricsService(meterRegistry);
    healthIndicator = new HkjVirtualThreadHealthIndicator(metricsService);
  }

  @Nested
  @DisplayName("Null Metrics Tests")
  class NullMetricsTests {
    @Test
    @DisplayName("Should return OUT_OF_SERVICE when metrics service is null")
    void shouldReturnOutOfServiceWhenMetricsServiceIsNull() {
      HkjVirtualThreadHealthIndicator indicator = new HkjVirtualThreadHealthIndicator(null);
      Health health = indicator.health();
      assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
      assertThat(health.getDetails()).containsEntry("reason", "Metrics service is unavailable");
    }
  }

  @Nested
  @DisplayName("No Invocations Tests")
  class NoInvocationsTests {
    @Test
    @DisplayName("Should return UP when there are no invocations")
    void shouldReturnUpWhenNoInvocations() {
      Health health = healthIndicator.health();
      assertThat(health.getStatus()).isEqualTo(Status.UP);
      assertThat(health.getDetails()).containsEntry("vtask.successRate", 1.0);
      assertThat(health.getDetails()).containsEntry("vstream.successRate", 1.0);
    }
  }

  @Nested
  @DisplayName("Healthy State Tests")
  class HealthyStateTests {
    @Test
    @DisplayName("Should return UP when error rates are below threshold")
    void shouldReturnUpWhenHealthy() {
      // 90% success rate for VTask
      recordVTask(90, 10);
      // 80% success rate for VStream
      recordVStream(80, 20);

      Health health = healthIndicator.health();
      assertThat(health.getStatus()).isEqualTo(Status.UP);
      assertThat(health.getDetails()).containsEntry("vtask.successCount", 90L);
      assertThat(health.getDetails()).containsEntry("vstream.successCount", 80L);
    }
  }

  @Nested
  @DisplayName("Degraded State Tests")
  class DegradedStateTests {
    @Test
    @DisplayName("Should return DOWN when VTask error rate exceeds threshold")
    void shouldReturnDownWhenVTaskDegraded() {
      // 40% success rate (60% error rate > 50% threshold)
      recordVTask(40, 60);
      Health health = healthIndicator.health();
      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
      assertThat(health.getDetails()).containsEntry("vtask.successRate", 0.4);
    }

    @Test
    @DisplayName("Should return DOWN when VStream error rate exceeds threshold")
    void shouldReturnDownWhenVStreamDegraded() {
      // 30% success rate (70% error rate > 50% threshold)
      recordVStream(30, 70);
      Health health = healthIndicator.health();
      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
      assertThat(health.getDetails()).containsEntry("vstream.successRate", 0.3);
    }
  }

  @Nested
  @DisplayName("Custom Threshold Tests")
  class CustomThresholdTests {
    @Test
    @DisplayName("Should use custom threshold for health calculation")
    void shouldHandleCustomThreshold() {
      // Set strict 10% threshold (0.1 error threshold)
      healthIndicator = new HkjVirtualThreadHealthIndicator(metricsService, 0.1);

      // 15% error rate should trigger DOWN
      recordVTask(85, 15);

      Health health = healthIndicator.health();
      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
  }

  private void recordVTask(int success, int error) {
    for (int i = 0; i < success; i++) metricsService.recordVTaskSuccess();
    for (int i = 0; i < error; i++) metricsService.recordVTaskError("Error");
  }

  private void recordVStream(int success, int error) {
    for (int i = 0; i < success; i++) metricsService.recordVStreamSuccess();
    for (int i = 0; i < error; i++) metricsService.recordVStreamError("Error");
  }
}