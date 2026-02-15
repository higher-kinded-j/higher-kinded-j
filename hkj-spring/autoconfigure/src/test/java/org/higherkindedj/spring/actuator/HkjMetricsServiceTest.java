// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.actuator;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HkjMetricsService Tests")
class HkjMetricsServiceTest {

  private MeterRegistry meterRegistry;
  private HkjMetricsService metricsService;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    metricsService = new HkjMetricsService(meterRegistry);
  }

  @Nested
  @DisplayName("Either Metrics Tests")
  class EitherMetricsTests {

    @Test
    @DisplayName("Should record Either success")
    void shouldRecordEitherSuccess() {
      metricsService.recordEitherSuccess();

      assertThat(metricsService.getEitherSuccessCount()).isEqualTo(1.0);
      assertThat(metricsService.getEitherErrorCount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should record multiple Either successes")
    void shouldRecordMultipleEitherSuccesses() {
      metricsService.recordEitherSuccess();
      metricsService.recordEitherSuccess();
      metricsService.recordEitherSuccess();

      assertThat(metricsService.getEitherSuccessCount()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Should record Either error")
    void shouldRecordEitherError() {
      metricsService.recordEitherError("UserNotFoundError");

      assertThat(metricsService.getEitherErrorCount()).isEqualTo(1.0);
      assertThat(metricsService.getEitherSuccessCount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should record Either error with type distribution")
    void shouldRecordEitherErrorWithTypeDistribution() {
      metricsService.recordEitherError("UserNotFoundError");
      metricsService.recordEitherError("ValidationError");
      metricsService.recordEitherError("UserNotFoundError");

      assertThat(metricsService.getEitherErrorCount()).isEqualTo(3.0);

      // Verify error type distribution
      double userNotFoundCount =
          meterRegistry.counter("hkj.either.errors", "error_type", "UserNotFoundError").count();
      double validationErrorCount =
          meterRegistry.counter("hkj.either.errors", "error_type", "ValidationError").count();

      assertThat(userNotFoundCount).isEqualTo(2.0);
      assertThat(validationErrorCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record mixed Either successes and errors")
    void shouldRecordMixedEitherSuccessesAndErrors() {
      metricsService.recordEitherSuccess();
      metricsService.recordEitherSuccess();
      metricsService.recordEitherError("SomeError");
      metricsService.recordEitherSuccess();

      assertThat(metricsService.getEitherSuccessCount()).isEqualTo(3.0);
      assertThat(metricsService.getEitherErrorCount()).isEqualTo(1.0);
    }
  }

  @Nested
  @DisplayName("Validated Metrics Tests")
  class ValidatedMetricsTests {

    @Test
    @DisplayName("Should record Validated valid")
    void shouldRecordValidatedValid() {
      metricsService.recordValidatedValid();

      assertThat(metricsService.getValidatedValidCount()).isEqualTo(1.0);
      assertThat(metricsService.getValidatedInvalidCount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should record multiple Validated valids")
    void shouldRecordMultipleValidatedValids() {
      metricsService.recordValidatedValid();
      metricsService.recordValidatedValid();
      metricsService.recordValidatedValid();

      assertThat(metricsService.getValidatedValidCount()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Should record Validated invalid with error count")
    void shouldRecordValidatedInvalidWithErrorCount() {
      metricsService.recordValidatedInvalid(3);

      assertThat(metricsService.getValidatedInvalidCount()).isEqualTo(1.0);

      // Verify error count was recorded
      double errorCount = meterRegistry.summary("hkj.validated.error_count").count();
      assertThat(errorCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should track multiple error counts")
    void shouldTrackMultipleErrorCounts() {
      metricsService.recordValidatedInvalid(2);
      metricsService.recordValidatedInvalid(5);
      metricsService.recordValidatedInvalid(1);

      assertThat(metricsService.getValidatedInvalidCount()).isEqualTo(3.0);

      // Verify all error counts were recorded
      double totalErrors = meterRegistry.summary("hkj.validated.error_count").count();
      assertThat(totalErrors).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Should record mixed Validated valids and invalids")
    void shouldRecordMixedValidatedValidsAndInvalids() {
      metricsService.recordValidatedValid();
      metricsService.recordValidatedInvalid(2);
      metricsService.recordValidatedValid();
      metricsService.recordValidatedValid();
      metricsService.recordValidatedInvalid(1);

      assertThat(metricsService.getValidatedValidCount()).isEqualTo(3.0);
      assertThat(metricsService.getValidatedInvalidCount()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should record zero error count")
    void shouldRecordZeroErrorCount() {
      metricsService.recordValidatedInvalid(0);

      assertThat(metricsService.getValidatedInvalidCount()).isEqualTo(1.0);

      double errorCount = meterRegistry.summary("hkj.validated.error_count").count();
      assertThat(errorCount).isEqualTo(1.0);
    }
  }

  @Nested
  @DisplayName("EitherT Async Metrics Tests")
  class EitherTAsyncMetricsTests {

    @Test
    @DisplayName("Should record EitherT success")
    void shouldRecordEitherTSuccess() {
      metricsService.recordEitherTSuccess();

      assertThat(metricsService.getEitherTSuccessCount()).isEqualTo(1.0);
      assertThat(metricsService.getEitherTErrorCount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should record multiple EitherT successes")
    void shouldRecordMultipleEitherTSuccesses() {
      metricsService.recordEitherTSuccess();
      metricsService.recordEitherTSuccess();

      assertThat(metricsService.getEitherTSuccessCount()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should record EitherT error")
    void shouldRecordEitherTError() {
      metricsService.recordEitherTError("AsyncError");

      assertThat(metricsService.getEitherTErrorCount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record EitherT error with type distribution")
    void shouldRecordEitherTErrorWithTypeDistribution() {
      metricsService.recordEitherTError("TimeoutError");
      metricsService.recordEitherTError("NetworkError");
      metricsService.recordEitherTError("TimeoutError");

      assertThat(metricsService.getEitherTErrorCount()).isEqualTo(3.0);

      double timeoutCount =
          meterRegistry.counter("hkj.either_t.errors", "error_type", "TimeoutError").count();
      double networkCount =
          meterRegistry.counter("hkj.either_t.errors", "error_type", "NetworkError").count();

      assertThat(timeoutCount).isEqualTo(2.0);
      assertThat(networkCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record async duration")
    void shouldRecordAsyncDuration() {
      metricsService.recordEitherTAsyncDuration(150);

      double count = meterRegistry.timer("hkj.either_t.async.duration").count();
      assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record multiple async durations")
    void shouldRecordMultipleAsyncDurations() {
      metricsService.recordEitherTAsyncDuration(100);
      metricsService.recordEitherTAsyncDuration(200);
      metricsService.recordEitherTAsyncDuration(150);

      double count = meterRegistry.timer("hkj.either_t.async.duration").count();
      assertThat(count).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Should record async exception")
    void shouldRecordAsyncException() {
      metricsService.recordEitherTException("NullPointerException");

      double count =
          meterRegistry
              .counter("hkj.either_t.exceptions", "exception_type", "NullPointerException")
              .count();
      assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should record multiple different exceptions")
    void shouldRecordMultipleDifferentExceptions() {
      metricsService.recordEitherTException("NullPointerException");
      metricsService.recordEitherTException("IllegalStateException");
      metricsService.recordEitherTException("NullPointerException");

      double npeCount =
          meterRegistry
              .counter("hkj.either_t.exceptions", "exception_type", "NullPointerException")
              .count();
      double iseCount =
          meterRegistry
              .counter("hkj.either_t.exceptions", "exception_type", "IllegalStateException")
              .count();

      assertThat(npeCount).isEqualTo(2.0);
      assertThat(iseCount).isEqualTo(1.0);
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should track all metrics independently")
    void shouldTrackAllMetricsIndependently() {
      // Record various metrics
      metricsService.recordEitherSuccess();
      metricsService.recordEitherError("Error1");

      metricsService.recordValidatedValid();
      metricsService.recordValidatedInvalid(3);

      metricsService.recordEitherTSuccess();
      metricsService.recordEitherTError("AsyncError");
      metricsService.recordEitherTAsyncDuration(100);

      // Verify all metrics are tracked independently
      assertThat(metricsService.getEitherSuccessCount()).isEqualTo(1.0);
      assertThat(metricsService.getEitherErrorCount()).isEqualTo(1.0);

      assertThat(metricsService.getValidatedValidCount()).isEqualTo(1.0);
      assertThat(metricsService.getValidatedInvalidCount()).isEqualTo(1.0);

      assertThat(metricsService.getEitherTSuccessCount()).isEqualTo(1.0);
      assertThat(metricsService.getEitherTErrorCount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should handle high volume of metrics")
    void shouldHandleHighVolumeOfMetrics() {
      // Simulate high traffic
      for (int i = 0; i < 1000; i++) {
        metricsService.recordEitherSuccess();
      }

      for (int i = 0; i < 500; i++) {
        metricsService.recordEitherError("Error");
      }

      assertThat(metricsService.getEitherSuccessCount()).isEqualTo(1000.0);
      assertThat(metricsService.getEitherErrorCount()).isEqualTo(500.0);
    }

    @Test
    @DisplayName("Should handle zero metrics gracefully")
    void shouldHandleZeroMetricsGracefully() {
      // No metrics recorded
      assertThat(metricsService.getEitherSuccessCount()).isEqualTo(0.0);
      assertThat(metricsService.getEitherErrorCount()).isEqualTo(0.0);
      assertThat(metricsService.getValidatedValidCount()).isEqualTo(0.0);
      assertThat(metricsService.getValidatedInvalidCount()).isEqualTo(0.0);
      assertThat(metricsService.getEitherTSuccessCount()).isEqualTo(0.0);
      assertThat(metricsService.getEitherTErrorCount()).isEqualTo(0.0);
    }
  }

  @Nested
  @DisplayName("Meter Registry Integration Tests")
  class MeterRegistryIntegrationTests {

    @Test
    @DisplayName("Should register meters with correct names")
    void shouldRegisterMetersWithCorrectNames() {
      metricsService.recordEitherSuccess();
      metricsService.recordValidatedValid();
      metricsService.recordEitherTSuccess();

      assertThat(meterRegistry.find("hkj.either.invocations").counter()).isNotNull();
      assertThat(meterRegistry.find("hkj.validated.invocations").counter()).isNotNull();
      assertThat(meterRegistry.find("hkj.either_t.invocations").counter()).isNotNull();
    }

    @Test
    @DisplayName("Should register meters with correct tags")
    void shouldRegisterMetersWithCorrectTags() {
      metricsService.recordEitherSuccess();
      metricsService.recordEitherError("Error");

      assertThat(meterRegistry.find("hkj.either.invocations").tag("result", "success").counter())
          .isNotNull();
      assertThat(meterRegistry.find("hkj.either.invocations").tag("result", "error").counter())
          .isNotNull();
    }

    @Test
    @DisplayName("Should register error type meters")
    void shouldRegisterErrorTypeMeters() {
      metricsService.recordEitherError("UserNotFoundError");
      metricsService.recordEitherTError("AsyncError");

      assertThat(
              meterRegistry
                  .find("hkj.either.errors")
                  .tag("error_type", "UserNotFoundError")
                  .counter())
          .isNotNull();
      assertThat(
              meterRegistry.find("hkj.either_t.errors").tag("error_type", "AsyncError").counter())
          .isNotNull();
    }

    @Test
    @DisplayName("Should register async duration timer")
    void shouldRegisterAsyncDurationTimer() {
      metricsService.recordEitherTAsyncDuration(100);

      assertThat(meterRegistry.find("hkj.either_t.async.duration").timer()).isNotNull();
    }

    @Test
    @DisplayName("Should register validated error count summary")
    void shouldRegisterValidatedErrorCountSummary() {
      metricsService.recordValidatedInvalid(5);

      assertThat(meterRegistry.find("hkj.validated.error_count").summary()).isNotNull();
    }
  }
}
