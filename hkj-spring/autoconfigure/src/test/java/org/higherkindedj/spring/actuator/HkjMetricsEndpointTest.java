// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.actuator;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.higherkindedj.spring.autoconfigure.HkjProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HkjMetricsEndpoint Tests")
class HkjMetricsEndpointTest {

  private HkjProperties properties;
  private MeterRegistry meterRegistry;
  private HkjMetricsService metricsService;
  private HkjMetricsEndpoint endpoint;

  @BeforeEach
  void setUp() {
    properties = new HkjProperties();
    meterRegistry = new SimpleMeterRegistry();
    metricsService = new HkjMetricsService(meterRegistry);
    endpoint = new HkjMetricsEndpoint(properties, metricsService);
  }

  @Nested
  @DisplayName("Configuration Section Tests")
  class ConfigurationSectionTests {

    @Test
    @DisplayName("Should include web configuration with default values")
    void shouldIncludeWebConfigurationWithDefaults() {
      Map<String, Object> result = endpoint.hkjMetrics();

      assertThat(result).containsKey("configuration");
      Map<String, Object> config = (Map<String, Object>) result.get("configuration");
      assertThat(config).containsKey("web");

      Map<String, Object> web = (Map<String, Object>) config.get("web");
      assertThat(web.get("eitherPathEnabled")).isEqualTo(true);
      assertThat(web.get("maybePathEnabled")).isEqualTo(true);
      assertThat(web.get("tryPathEnabled")).isEqualTo(true);
      assertThat(web.get("validationPathEnabled")).isEqualTo(true);
      assertThat(web.get("ioPathEnabled")).isEqualTo(true);
      assertThat(web.get("completableFuturePathEnabled")).isEqualTo(true);
      assertThat(web.get("defaultErrorStatus")).isEqualTo(400);
    }

    @Test
    @DisplayName("Should include web configuration with custom values")
    void shouldIncludeWebConfigurationWithCustomValues() {
      properties.getWeb().setEitherPathEnabled(false);
      properties.getWeb().setMaybePathEnabled(false);
      properties.getWeb().setTryPathEnabled(false);
      properties.getWeb().setValidationPathEnabled(false);
      properties.getWeb().setIoPathEnabled(false);
      properties.getWeb().setCompletableFuturePathEnabled(false);
      properties.getWeb().setDefaultErrorStatus(500);

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> config = (Map<String, Object>) result.get("configuration");
      Map<String, Object> web = (Map<String, Object>) config.get("web");

      assertThat(web.get("eitherPathEnabled")).isEqualTo(false);
      assertThat(web.get("maybePathEnabled")).isEqualTo(false);
      assertThat(web.get("tryPathEnabled")).isEqualTo(false);
      assertThat(web.get("validationPathEnabled")).isEqualTo(false);
      assertThat(web.get("ioPathEnabled")).isEqualTo(false);
      assertThat(web.get("completableFuturePathEnabled")).isEqualTo(false);
      assertThat(web.get("defaultErrorStatus")).isEqualTo(500);
    }

    @Test
    @DisplayName("Should include jackson configuration with default values")
    void shouldIncludeJacksonConfigurationWithDefaults() {
      Map<String, Object> result = endpoint.hkjMetrics();

      Map<String, Object> config = (Map<String, Object>) result.get("configuration");
      assertThat(config).containsKey("jackson");

      Map<String, Object> jackson = (Map<String, Object>) config.get("jackson");
      assertThat(jackson.get("customSerializersEnabled")).isEqualTo(true);
      assertThat(jackson.get("eitherFormat")).isEqualTo("TAGGED");
      assertThat(jackson.get("validatedFormat")).isEqualTo("TAGGED");
      assertThat(jackson.get("maybeFormat")).isEqualTo("TAGGED");
    }

    @Test
    @DisplayName("Should include jackson configuration with custom values")
    void shouldIncludeJacksonConfigurationWithCustomValues() {
      properties.getJson().setCustomSerializersEnabled(false);
      properties.getJson().setEitherFormat(HkjProperties.Jackson.SerializationFormat.SIMPLE);
      properties.getJson().setValidatedFormat(HkjProperties.Jackson.SerializationFormat.SIMPLE);
      properties.getJson().setMaybeFormat(HkjProperties.Jackson.SerializationFormat.SIMPLE);

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> config = (Map<String, Object>) result.get("configuration");
      Map<String, Object> jackson = (Map<String, Object>) config.get("jackson");

      assertThat(jackson.get("customSerializersEnabled")).isEqualTo(false);
      assertThat(jackson.get("eitherFormat")).isEqualTo("SIMPLE");
      assertThat(jackson.get("validatedFormat")).isEqualTo("SIMPLE");
      assertThat(jackson.get("maybeFormat")).isEqualTo("SIMPLE");
    }

    @Test
    @DisplayName("Should always include configuration section")
    void shouldAlwaysIncludeConfigurationSection() {
      Map<String, Object> result = endpoint.hkjMetrics();

      assertThat(result).containsKeys("configuration");
      Map<String, Object> config = (Map<String, Object>) result.get("configuration");
      assertThat(config).containsKeys("web", "jackson");
    }
  }

  @Nested
  @DisplayName("Either Metrics Tests")
  class EitherMetricsTests {

    @Test
    @DisplayName("Should report Either metrics with zero counts")
    void shouldReportEitherMetricsWithZeroCounts() {
      Map<String, Object> result = endpoint.hkjMetrics();

      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> either = (Map<String, Object>) metrics.get("either");

      assertThat(either.get("successCount")).isEqualTo(0L);
      assertThat(either.get("errorCount")).isEqualTo(0L);
      assertThat(either.get("totalCount")).isEqualTo(0L);
      assertThat(either.get("successRate")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should report Either metrics with only successes")
    void shouldReportEitherMetricsWithOnlySuccesses() {
      metricsService.recordEitherSuccess();
      metricsService.recordEitherSuccess();
      metricsService.recordEitherSuccess();

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> either = (Map<String, Object>) metrics.get("either");

      assertThat(either.get("successCount")).isEqualTo(3L);
      assertThat(either.get("errorCount")).isEqualTo(0L);
      assertThat(either.get("totalCount")).isEqualTo(3L);
      assertThat(either.get("successRate")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should report Either metrics with only errors")
    void shouldReportEitherMetricsWithOnlyErrors() {
      metricsService.recordEitherError("Error1");
      metricsService.recordEitherError("Error2");

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> either = (Map<String, Object>) metrics.get("either");

      assertThat(either.get("successCount")).isEqualTo(0L);
      assertThat(either.get("errorCount")).isEqualTo(2L);
      assertThat(either.get("totalCount")).isEqualTo(2L);
      assertThat(either.get("successRate")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should calculate Either success rate correctly")
    void shouldCalculateEitherSuccessRateCorrectly() {
      // 7 successes, 3 errors = 70% success rate
      for (int i = 0; i < 7; i++) {
        metricsService.recordEitherSuccess();
      }
      for (int i = 0; i < 3; i++) {
        metricsService.recordEitherError("Error");
      }

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> either = (Map<String, Object>) metrics.get("either");

      assertThat(either.get("successCount")).isEqualTo(7L);
      assertThat(either.get("errorCount")).isEqualTo(3L);
      assertThat(either.get("totalCount")).isEqualTo(10L);
      assertThat((double) either.get("successRate")).isCloseTo(0.7, within(0.001));
    }

    @Test
    @DisplayName("Should handle large Either counts")
    void shouldHandleLargeEitherCounts() {
      for (int i = 0; i < 1000; i++) {
        metricsService.recordEitherSuccess();
      }
      for (int i = 0; i < 500; i++) {
        metricsService.recordEitherError("Error");
      }

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> either = (Map<String, Object>) metrics.get("either");

      assertThat(either.get("successCount")).isEqualTo(1000L);
      assertThat(either.get("errorCount")).isEqualTo(500L);
      assertThat(either.get("totalCount")).isEqualTo(1500L);
      assertThat((double) either.get("successRate")).isCloseTo(0.6667, within(0.001));
    }
  }

  @Nested
  @DisplayName("Validated Metrics Tests")
  class ValidatedMetricsTests {

    @Test
    @DisplayName("Should report Validated metrics with zero counts")
    void shouldReportValidatedMetricsWithZeroCounts() {
      Map<String, Object> result = endpoint.hkjMetrics();

      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> validated = (Map<String, Object>) metrics.get("validated");

      assertThat(validated.get("validCount")).isEqualTo(0L);
      assertThat(validated.get("invalidCount")).isEqualTo(0L);
      assertThat(validated.get("totalCount")).isEqualTo(0L);
      assertThat(validated.get("validRate")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should report Validated metrics with only valid")
    void shouldReportValidatedMetricsWithOnlyValid() {
      metricsService.recordValidatedValid();
      metricsService.recordValidatedValid();
      metricsService.recordValidatedValid();
      metricsService.recordValidatedValid();

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> validated = (Map<String, Object>) metrics.get("validated");

      assertThat(validated.get("validCount")).isEqualTo(4L);
      assertThat(validated.get("invalidCount")).isEqualTo(0L);
      assertThat(validated.get("totalCount")).isEqualTo(4L);
      assertThat(validated.get("validRate")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should report Validated metrics with only invalid")
    void shouldReportValidatedMetricsWithOnlyInvalid() {
      metricsService.recordValidatedInvalid(2);
      metricsService.recordValidatedInvalid(3);

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> validated = (Map<String, Object>) metrics.get("validated");

      assertThat(validated.get("validCount")).isEqualTo(0L);
      assertThat(validated.get("invalidCount")).isEqualTo(2L);
      assertThat(validated.get("totalCount")).isEqualTo(2L);
      assertThat(validated.get("validRate")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should calculate Validated valid rate correctly")
    void shouldCalculateValidatedValidRateCorrectly() {
      // 8 valid, 2 invalid = 80% valid rate
      for (int i = 0; i < 8; i++) {
        metricsService.recordValidatedValid();
      }
      for (int i = 0; i < 2; i++) {
        metricsService.recordValidatedInvalid(1);
      }

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> validated = (Map<String, Object>) metrics.get("validated");

      assertThat(validated.get("validCount")).isEqualTo(8L);
      assertThat(validated.get("invalidCount")).isEqualTo(2L);
      assertThat(validated.get("totalCount")).isEqualTo(10L);
      assertThat((double) validated.get("validRate")).isCloseTo(0.8, within(0.001));
    }

    @Test
    @DisplayName("Should handle large Validated counts")
    void shouldHandleLargeValidatedCounts() {
      for (int i = 0; i < 750; i++) {
        metricsService.recordValidatedValid();
      }
      for (int i = 0; i < 250; i++) {
        metricsService.recordValidatedInvalid(1);
      }

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> validated = (Map<String, Object>) metrics.get("validated");

      assertThat(validated.get("validCount")).isEqualTo(750L);
      assertThat(validated.get("invalidCount")).isEqualTo(250L);
      assertThat(validated.get("totalCount")).isEqualTo(1000L);
      assertThat((double) validated.get("validRate")).isCloseTo(0.75, within(0.001));
    }
  }

  @Nested
  @DisplayName("EitherT Metrics Tests")
  class EitherTMetricsTests {

    @Test
    @DisplayName("Should report EitherT metrics with zero counts")
    void shouldReportEitherTMetricsWithZeroCounts() {
      Map<String, Object> result = endpoint.hkjMetrics();

      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> eitherT = (Map<String, Object>) metrics.get("eitherT");

      assertThat(eitherT.get("successCount")).isEqualTo(0L);
      assertThat(eitherT.get("errorCount")).isEqualTo(0L);
      assertThat(eitherT.get("totalCount")).isEqualTo(0L);
      assertThat(eitherT.get("successRate")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should report EitherT metrics with only successes")
    void shouldReportEitherTMetricsWithOnlySuccesses() {
      metricsService.recordEitherTSuccess();
      metricsService.recordEitherTSuccess();

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> eitherT = (Map<String, Object>) metrics.get("eitherT");

      assertThat(eitherT.get("successCount")).isEqualTo(2L);
      assertThat(eitherT.get("errorCount")).isEqualTo(0L);
      assertThat(eitherT.get("totalCount")).isEqualTo(2L);
      assertThat(eitherT.get("successRate")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should report EitherT metrics with only errors")
    void shouldReportEitherTMetricsWithOnlyErrors() {
      metricsService.recordEitherTError("AsyncError1");
      metricsService.recordEitherTError("AsyncError2");
      metricsService.recordEitherTError("AsyncError3");

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> eitherT = (Map<String, Object>) metrics.get("eitherT");

      assertThat(eitherT.get("successCount")).isEqualTo(0L);
      assertThat(eitherT.get("errorCount")).isEqualTo(3L);
      assertThat(eitherT.get("totalCount")).isEqualTo(3L);
      assertThat(eitherT.get("successRate")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should calculate EitherT success rate correctly")
    void shouldCalculateEitherTSuccessRateCorrectly() {
      // 9 successes, 1 error = 90% success rate
      for (int i = 0; i < 9; i++) {
        metricsService.recordEitherTSuccess();
      }
      metricsService.recordEitherTError("Error");

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> eitherT = (Map<String, Object>) metrics.get("eitherT");

      assertThat(eitherT.get("successCount")).isEqualTo(9L);
      assertThat(eitherT.get("errorCount")).isEqualTo(1L);
      assertThat(eitherT.get("totalCount")).isEqualTo(10L);
      assertThat((double) eitherT.get("successRate")).isCloseTo(0.9, within(0.001));
    }

    @Test
    @DisplayName("Should handle large EitherT counts")
    void shouldHandleLargeEitherTCounts() {
      for (int i = 0; i < 850; i++) {
        metricsService.recordEitherTSuccess();
      }
      for (int i = 0; i < 150; i++) {
        metricsService.recordEitherTError("Error");
      }

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> eitherT = (Map<String, Object>) metrics.get("eitherT");

      assertThat(eitherT.get("successCount")).isEqualTo(850L);
      assertThat(eitherT.get("errorCount")).isEqualTo(150L);
      assertThat(eitherT.get("totalCount")).isEqualTo(1000L);
      assertThat((double) eitherT.get("successRate")).isCloseTo(0.85, within(0.001));
    }
  }

  @Nested
  @DisplayName("Metrics Disabled Tests")
  class MetricsDisabledTests {

    @Test
    @DisplayName("Should report metrics disabled when metricsService is null")
    void shouldReportMetricsDisabledWhenServiceIsNull() {
      endpoint = new HkjMetricsEndpoint(properties, null);

      Map<String, Object> result = endpoint.hkjMetrics();

      assertThat(result).containsKey("metrics");
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      assertThat(metrics).containsEntry("enabled", false);
      assertThat(metrics).doesNotContainKeys("either", "validated", "eitherT");
    }

    @Test
    @DisplayName("Should still include configuration when metrics disabled")
    void shouldStillIncludeConfigurationWhenMetricsDisabled() {
      endpoint = new HkjMetricsEndpoint(properties, null);

      Map<String, Object> result = endpoint.hkjMetrics();

      assertThat(result).containsKey("configuration");
      Map<String, Object> config = (Map<String, Object>) result.get("configuration");
      assertThat(config).containsKeys("web", "jackson");
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should provide complete metrics snapshot")
    void shouldProvideCompleteMetricsSnapshot() {
      // Record various metrics
      metricsService.recordEitherSuccess();
      metricsService.recordEitherSuccess();
      metricsService.recordEitherError("Error1");

      metricsService.recordValidatedValid();
      metricsService.recordValidatedValid();
      metricsService.recordValidatedValid();
      metricsService.recordValidatedInvalid(2);

      metricsService.recordEitherTSuccess();
      metricsService.recordEitherTError("AsyncError");

      Map<String, Object> result = endpoint.hkjMetrics();

      // Verify structure
      assertThat(result).containsKeys("configuration", "metrics");

      // Verify configuration
      Map<String, Object> config = (Map<String, Object>) result.get("configuration");
      assertThat(config).containsKeys("web", "jackson");

      // Verify metrics
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      assertThat(metrics).containsKeys("either", "validated", "eitherT");

      // Verify Either metrics
      Map<String, Object> either = (Map<String, Object>) metrics.get("either");
      assertThat(either.get("successCount")).isEqualTo(2L);
      assertThat(either.get("errorCount")).isEqualTo(1L);
      assertThat(either.get("totalCount")).isEqualTo(3L);

      // Verify Validated metrics
      Map<String, Object> validated = (Map<String, Object>) metrics.get("validated");
      assertThat(validated.get("validCount")).isEqualTo(3L);
      assertThat(validated.get("invalidCount")).isEqualTo(1L);
      assertThat(validated.get("totalCount")).isEqualTo(4L);

      // Verify EitherT metrics
      Map<String, Object> eitherT = (Map<String, Object>) metrics.get("eitherT");
      assertThat(eitherT.get("successCount")).isEqualTo(1L);
      assertThat(eitherT.get("errorCount")).isEqualTo(1L);
      assertThat(eitherT.get("totalCount")).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should handle all metrics at zero")
    void shouldHandleAllMetricsAtZero() {
      Map<String, Object> result = endpoint.hkjMetrics();

      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");

      Map<String, Object> either = (Map<String, Object>) metrics.get("either");
      assertThat(either.get("totalCount")).isEqualTo(0L);
      assertThat(either.get("successRate")).isEqualTo(0.0);

      Map<String, Object> validated = (Map<String, Object>) metrics.get("validated");
      assertThat(validated.get("totalCount")).isEqualTo(0L);
      assertThat(validated.get("validRate")).isEqualTo(0.0);

      Map<String, Object> eitherT = (Map<String, Object>) metrics.get("eitherT");
      assertThat(eitherT.get("totalCount")).isEqualTo(0L);
      assertThat(eitherT.get("successRate")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should track metrics independently")
    void shouldTrackMetricsIndependently() {
      // Record only Either metrics
      metricsService.recordEitherSuccess();
      metricsService.recordEitherSuccess();

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");

      // Either should have counts
      Map<String, Object> either = (Map<String, Object>) metrics.get("either");
      assertThat(either.get("totalCount")).isEqualTo(2L);

      // Validated should be zero
      Map<String, Object> validated = (Map<String, Object>) metrics.get("validated");
      assertThat(validated.get("totalCount")).isEqualTo(0L);

      // EitherT should be zero
      Map<String, Object> eitherT = (Map<String, Object>) metrics.get("eitherT");
      assertThat(eitherT.get("totalCount")).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should reflect current configuration state")
    void shouldReflectCurrentConfigurationState() {
      // Modify configuration
      properties.getWeb().setDefaultErrorStatus(422);
      properties.getJson().setEitherFormat(HkjProperties.Jackson.SerializationFormat.SIMPLE);

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> config = (Map<String, Object>) result.get("configuration");

      Map<String, Object> web = (Map<String, Object>) config.get("web");
      assertThat(web.get("defaultErrorStatus")).isEqualTo(422);

      Map<String, Object> jackson = (Map<String, Object>) config.get("jackson");
      assertThat(jackson.get("eitherFormat")).isEqualTo("SIMPLE");
    }

    @Test
    @DisplayName("Should handle mixed success and failure rates")
    void shouldHandleMixedSuccessAndFailureRates() {
      // Either: 50% success
      metricsService.recordEitherSuccess();
      metricsService.recordEitherError("Error");

      // Validated: 25% valid
      metricsService.recordValidatedValid();
      metricsService.recordValidatedInvalid(1);
      metricsService.recordValidatedInvalid(1);
      metricsService.recordValidatedInvalid(1);

      // EitherT: 75% success
      metricsService.recordEitherTSuccess();
      metricsService.recordEitherTSuccess();
      metricsService.recordEitherTSuccess();
      metricsService.recordEitherTError("Error");

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");

      Map<String, Object> either = (Map<String, Object>) metrics.get("either");
      assertThat((double) either.get("successRate")).isCloseTo(0.5, within(0.001));

      Map<String, Object> validated = (Map<String, Object>) metrics.get("validated");
      assertThat((double) validated.get("validRate")).isCloseTo(0.25, within(0.001));

      Map<String, Object> eitherT = (Map<String, Object>) metrics.get("eitherT");
      assertThat((double) eitherT.get("successRate")).isCloseTo(0.75, within(0.001));
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle division by zero for success rates")
    void shouldHandleDivisionByZeroForSuccessRates() {
      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");

      // All rates should be 0.0, not NaN or Infinity
      Map<String, Object> either = (Map<String, Object>) metrics.get("either");
      assertThat(either.get("successRate")).isEqualTo(0.0);

      Map<String, Object> validated = (Map<String, Object>) metrics.get("validated");
      assertThat(validated.get("validRate")).isEqualTo(0.0);

      Map<String, Object> eitherT = (Map<String, Object>) metrics.get("eitherT");
      assertThat(eitherT.get("successRate")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should use long for counts not double")
    void shouldUseLongForCountsNotDouble() {
      metricsService.recordEitherSuccess();

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> either = (Map<String, Object>) metrics.get("either");

      assertThat(either.get("successCount")).isInstanceOf(Long.class);
      assertThat(either.get("errorCount")).isInstanceOf(Long.class);
      assertThat(either.get("totalCount")).isInstanceOf(Long.class);
      assertThat(either.get("successRate")).isInstanceOf(Double.class);
    }

    @Test
    @DisplayName("Should preserve insertion order in maps")
    void shouldPreserveInsertionOrderInMaps() {
      Map<String, Object> result = endpoint.hkjMetrics();

      // Top level should have configuration before metrics
      assertThat(result.keySet()).containsExactly("configuration", "metrics");

      Map<String, Object> config = (Map<String, Object>) result.get("configuration");
      assertThat(config.keySet()).containsExactly("web", "jackson");

      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      assertThat(metrics.keySet()).containsExactly("either", "validated", "eitherT");

      Map<String, Object> either = (Map<String, Object>) metrics.get("either");
      assertThat(either.keySet())
          .containsExactly("successCount", "errorCount", "totalCount", "successRate");
    }

    @Test
    @DisplayName("Should handle fractional success rates correctly")
    void shouldHandleFractionalSuccessRatesCorrectly() {
      // 1 success, 2 errors = 0.3333...
      metricsService.recordEitherSuccess();
      metricsService.recordEitherError("Error1");
      metricsService.recordEitherError("Error2");

      Map<String, Object> result = endpoint.hkjMetrics();
      Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
      Map<String, Object> either = (Map<String, Object>) metrics.get("either");

      double successRate = (double) either.get("successRate");
      assertThat(successRate).isCloseTo(0.3333, within(0.001));
      assertThat(successRate).isGreaterThan(0.0);
      assertThat(successRate).isLessThan(1.0);
    }
  }
}
