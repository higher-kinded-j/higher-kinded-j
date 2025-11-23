// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.actuator;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

/**
 * Service for tracking higher-kinded-j Spring integration metrics.
 *
 * <p>Provides metrics for:
 *
 * <ul>
 *   <li>Either return value handler invocations (success/error)
 *   <li>Validated return value handler invocations (valid/invalid)
 *   <li>EitherT async return value handler invocations
 *   <li>Async operation execution times
 *   <li>Error type distributions
 * </ul>
 *
 * <p>Metrics are exposed via Spring Boot Actuator and can be consumed by monitoring systems like
 * Prometheus, Grafana, etc.
 *
 * <p>Example metrics:
 *
 * <pre>
 * hkj.either.invocations{result="success"} - Count of Either Right values
 * hkj.either.invocations{result="error"} - Count of Either Left values
 * hkj.validated.invocations{result="valid"} - Count of Validated Valid values
 * hkj.validated.invocations{result="invalid"} - Count of Validated Invalid values
 * hkj.either_t.invocations{result="success"} - Count of async Either Right values
 * hkj.either_t.invocations{result="error"} - Count of async Either Left values
 * hkj.either_t.async.duration - Timer for async operation execution times
 * </pre>
 */
public class HkjMetricsService {

  private final MeterRegistry meterRegistry;

  // Either metrics
  private final Counter eitherSuccessCounter;
  private final Counter eitherErrorCounter;

  // Validated metrics
  private final Counter validatedValidCounter;
  private final Counter validatedInvalidCounter;

  // EitherT async metrics
  private final Counter eitherTSuccessCounter;
  private final Counter eitherTErrorCounter;
  private final Timer eitherTAsyncTimer;

  /**
   * Creates a new HkjMetricsService with the given MeterRegistry.
   *
   * @param meterRegistry the Micrometer registry for metrics
   */
  public HkjMetricsService(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;

    // Initialize Either counters
    this.eitherSuccessCounter =
        Counter.builder("hkj.either.invocations")
            .description("Number of Either return value handler invocations")
            .tag("result", "success")
            .register(meterRegistry);

    this.eitherErrorCounter =
        Counter.builder("hkj.either.invocations")
            .description("Number of Either return value handler invocations")
            .tag("result", "error")
            .register(meterRegistry);

    // Initialize Validated counters
    this.validatedValidCounter =
        Counter.builder("hkj.validated.invocations")
            .description("Number of Validated return value handler invocations")
            .tag("result", "valid")
            .register(meterRegistry);

    this.validatedInvalidCounter =
        Counter.builder("hkj.validated.invocations")
            .description("Number of Validated return value handler invocations")
            .tag("result", "invalid")
            .register(meterRegistry);

    // Initialize EitherT async counters
    this.eitherTSuccessCounter =
        Counter.builder("hkj.either_t.invocations")
            .description("Number of EitherT async return value handler invocations")
            .tag("result", "success")
            .register(meterRegistry);

    this.eitherTErrorCounter =
        Counter.builder("hkj.either_t.invocations")
            .description("Number of EitherT async return value handler invocations")
            .tag("result", "error")
            .register(meterRegistry);

    // Initialize EitherT async timer
    this.eitherTAsyncTimer =
        Timer.builder("hkj.either_t.async.duration")
            .description("Duration of async EitherT operations")
            .register(meterRegistry);
  }

  /** Records a successful Either (Right) invocation. */
  public void recordEitherSuccess() {
    eitherSuccessCounter.increment();
  }

  /**
   * Records an error Either (Left) invocation.
   *
   * @param errorType the class name of the error type
   */
  public void recordEitherError(String errorType) {
    eitherErrorCounter.increment();
    // Also track error type distribution
    Counter.builder("hkj.either.errors")
        .description("Distribution of Either error types")
        .tag("error_type", errorType)
        .register(meterRegistry)
        .increment();
  }

  /** Records a valid Validated invocation. */
  public void recordValidatedValid() {
    validatedValidCounter.increment();
  }

  /**
   * Records an invalid Validated invocation.
   *
   * @param errorCount the number of validation errors
   */
  public void recordValidatedInvalid(int errorCount) {
    validatedInvalidCounter.increment();
    // Track error count distribution
    meterRegistry.summary("hkj.validated.error_count").record(errorCount);
  }

  /** Records a successful EitherT async (Right) invocation. */
  public void recordEitherTSuccess() {
    eitherTSuccessCounter.increment();
  }

  /**
   * Records an error EitherT async (Left) invocation.
   *
   * @param errorType the class name of the error type
   */
  public void recordEitherTError(String errorType) {
    eitherTErrorCounter.increment();
    // Also track error type distribution
    Counter.builder("hkj.either_t.errors")
        .description("Distribution of EitherT error types")
        .tag("error_type", errorType)
        .register(meterRegistry)
        .increment();
  }

  /**
   * Records the duration of an async EitherT operation.
   *
   * @param durationMillis the duration in milliseconds
   */
  public void recordEitherTAsyncDuration(long durationMillis) {
    eitherTAsyncTimer.record(durationMillis, TimeUnit.MILLISECONDS);
  }

  /**
   * Records an exception that occurred during async execution.
   *
   * @param exceptionType the class name of the exception
   */
  public void recordEitherTException(String exceptionType) {
    Counter.builder("hkj.either_t.exceptions")
        .description("Exceptions during async EitherT execution")
        .tag("exception_type", exceptionType)
        .register(meterRegistry)
        .increment();
  }

  /**
   * Gets the current count of Either success invocations.
   *
   * @return the count
   */
  public double getEitherSuccessCount() {
    return eitherSuccessCounter.count();
  }

  /**
   * Gets the current count of Either error invocations.
   *
   * @return the count
   */
  public double getEitherErrorCount() {
    return eitherErrorCounter.count();
  }

  /**
   * Gets the current count of Validated valid invocations.
   *
   * @return the count
   */
  public double getValidatedValidCount() {
    return validatedValidCounter.count();
  }

  /**
   * Gets the current count of Validated invalid invocations.
   *
   * @return the count
   */
  public double getValidatedInvalidCount() {
    return validatedInvalidCounter.count();
  }

  /**
   * Gets the current count of EitherT async success invocations.
   *
   * @return the count
   */
  public double getEitherTSuccessCount() {
    return eitherTSuccessCounter.count();
  }

  /**
   * Gets the current count of EitherT async error invocations.
   *
   * @return the count
   */
  public double getEitherTErrorCount() {
    return eitherTErrorCounter.count();
  }
}
