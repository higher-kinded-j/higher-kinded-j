// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.patterns;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.test.data.TestData;

/**
 * Complete test pattern for KindHelper implementations using standardized validation framework.
 *
 * <p>Provides a comprehensive test suite for widen/narrow operations including:
 *
 * <ul>
 *   <li>Round-trip identity preservation
 *   <li>Null parameter validation using production validators
 *   <li>Invalid type validation with proper error messages
 *   <li>Idempotency testing
 *   <li>Edge case validation
 * </ul>
 *
 * <p>All validation assertions use the standardized validation framework to ensure test
 * expectations match production behavior exactly.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Complete Testing:</h3>
 *
 * <pre>{@code
 * @Test
 * void testEitherKindHelper() {
 *     KindHelperTestPattern.testComplete(
 *         Either.right("test"),
 *         Either.class,
 *         EITHER::widen,
 *         EITHER::narrow
 *     );
 * }
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 *
 * <pre>{@code
 * // Test only validations
 * KindHelperTestPattern.testValidations(Either.class, EITHER::widen, EITHER::narrow);
 *
 * // Test only round-trip behavior
 * KindHelperTestPattern.testRoundTrip(Either.right("test"), EITHER::widen, EITHER::narrow);
 * }</pre>
 *
 * <h3>Custom Edge Cases:</h3>
 *
 * <pre>{@code
 * // Test with multiple instances
 * List<Either<String, Integer>> testInstances = Arrays.asList(
 *     Either.left("error"),
 *     Either.right(42),
 *     Either.right(null)  // if supported
 * );
 * KindHelperTestPattern.testMultipleInstances(testInstances, Either.class, EITHER::widen, EITHER::narrow);
 * }</pre>
 *
 * @see org.higherkindedj.hkt.util.validation.KindValidator
 * @see ValidationTestBuilder
 */
public final class KindHelperTestPattern {

  private KindHelperTestPattern() {
    throw new AssertionError("KindHelperTestPattern is a utility class");
  }

  /**
   * Runs complete KindHelper test suite including all validations and edge cases.
   *
   * @param validInstance A valid instance of the concrete type
   * @param targetType The concrete type class (e.g., Either.class)
   * @param widenFunc Function to widen concrete type to Kind
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <T> The concrete type
   * @param <F> The witness type
   * @param <A> The value type
   */
  public static <T, F, A> void testComplete(
      T validInstance,
      Class<T> targetType,
      Function<T, Kind<F, A>> widenFunc,
      Function<Kind<F, A>, T> narrowFunc) {

    testRoundTrip(validInstance, widenFunc, narrowFunc);
    testValidations(targetType, widenFunc, narrowFunc);
    testInvalidType(targetType, widenFunc, narrowFunc);
    testIdempotency(validInstance, widenFunc, narrowFunc);
    testEdgeCases(validInstance, targetType, widenFunc, narrowFunc);
  }

  /**
   * Tests round-trip widen/narrow preserves identity.
   *
   * <p>Verifies that widening a concrete instance to Kind and then narrowing it back returns the
   * exact same instance (reference equality).
   *
   * @param validInstance A valid instance of the concrete type
   * @param widenFunc Function to widen concrete type to Kind
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <T> The concrete type
   * @param <F> The witness type
   * @param <A> The value type
   */
  public static <T, F, A> void testRoundTrip(
      T validInstance, Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {

    Kind<F, A> widened = widenFunc.apply(validInstance);
    T narrowed = narrowFunc.apply(widened);

    assertThat(narrowed)
        .as("Round-trip widen/narrow should preserve identity")
        .isSameAs(validInstance);
  }

  /**
   * Tests null parameter validations using production validators.
   *
   * <p>Uses the standardized validation framework to ensure test expectations match actual
   * production validation behavior.
   *
   * @param targetType The concrete type class
   * @param widenFunc Function to widen concrete type to Kind
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <T> The concrete type
   * @param <F> The witness type
   * @param <A> The value type
   */
  public static <T, F, A> void testValidations(
      Class<T> targetType, Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {

    ValidationTestBuilder.create()
        .assertWidenNull(() -> widenFunc.apply(null), targetType)
        .assertNarrowNull(() -> narrowFunc.apply(null), targetType)
        .execute();
  }

  /**
   * Tests invalid Kind type validation with proper error handling.
   *
   * <p>Verifies that attempting to narrow an invalid Kind type produces the expected error message
   * and exception type from production validators.
   *
   * @param targetType The concrete type class
   * @param widenFunc Function to widen concrete type to Kind
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <T> The concrete type
   * @param <F> The witness type
   * @param <A> The value type
   */
  public static <T, F, A> void testInvalidType(
      Class<T> targetType, Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {

    Kind<F, A> invalidKind = TestData.createDummyKind("invalid_" + targetType.getSimpleName());

    ValidationTestBuilder.create()
        .assertInvalidKindType(() -> narrowFunc.apply(invalidKind), targetType, invalidKind)
        .execute();
  }

  /**
   * Tests multiple round-trips preserve idempotency.
   *
   * <p>Verifies that performing multiple widen/narrow cycles doesn't affect the instance and
   * maintains reference equality.
   *
   * @param validInstance A valid instance of the concrete type
   * @param widenFunc Function to widen concrete type to Kind
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <T> The concrete type
   * @param <F> The witness type
   * @param <A> The value type
   */
  public static <T, F, A> void testIdempotency(
      T validInstance, Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {

    T current = validInstance;
    for (int i = 0; i < 3; i++) {
      Kind<F, A> widened = widenFunc.apply(current);
      current = narrowFunc.apply(widened);
    }

    assertThat(current).as("Multiple round-trips should preserve identity").isSameAs(validInstance);
  }

  /**
   * Tests edge cases and boundary conditions.
   *
   * <p>Additional testing for edge cases that might be specific to certain KindHelper
   * implementations.
   *
   * @param validInstance A valid instance of the concrete type
   * @param targetType The concrete type class
   * @param widenFunc Function to widen concrete type to Kind
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <T> The concrete type
   * @param <F> The witness type
   * @param <A> The value type
   */
  public static <T, F, A> void testEdgeCases(
      T validInstance,
      Class<T> targetType,
      Function<T, Kind<F, A>> widenFunc,
      Function<Kind<F, A>, T> narrowFunc) {

    // Test that widen always returns non-null
    Kind<F, A> widened = widenFunc.apply(validInstance);
    assertThat(widened).as("widen should always return non-null Kind").isNotNull();

    // Test that narrow always returns non-null for valid input
    T narrowed = narrowFunc.apply(widened);
    assertThat(narrowed).as("narrow should return non-null for valid Kind").isNotNull();

    // Test type consistency
    assertThat(narrowed)
        .as("narrowed result should be instance of target type")
        .isInstanceOf(targetType);
  }

  /**
   * Tests multiple instances to ensure consistency across different values.
   *
   * <p>Useful for testing KindHelpers with various instances of the concrete type to ensure
   * consistent behavior.
   *
   * @param instances Multiple instances to test
   * @param targetType The concrete type class
   * @param widenFunc Function to widen concrete type to Kind
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <T> The concrete type
   * @param <F> The witness type
   * @param <A> The value type
   */
  public static <T, F, A> void testMultipleInstances(
      java.util.List<T> instances,
      Class<T> targetType,
      Function<T, Kind<F, A>> widenFunc,
      Function<Kind<F, A>, T> narrowFunc) {

    assertThat(instances).as("instances list cannot be empty").isNotEmpty();

    for (int i = 0; i < instances.size(); i++) {
      T instance = instances.get(i);

      try {
        testRoundTrip(instance, widenFunc, narrowFunc);
      } catch (AssertionError e) {
        throw new AssertionError("Round-trip test failed for instance " + i + ": " + instance, e);
      }
    }
  }

  /**
   * Tests concurrent access to ensure thread safety (if applicable).
   *
   * <p>Some KindHelper implementations might need to be thread-safe. This test verifies concurrent
   * widen/narrow operations don't interfere.
   *
   * @param validInstance A valid instance of the concrete type
   * @param widenFunc Function to widen concrete type to Kind
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <T> The concrete type
   * @param <F> The witness type
   * @param <A> The value type
   */
  public static <T, F, A> void testConcurrentAccess(
      T validInstance, Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {

    // Simple concurrent test - run multiple operations in parallel
    java.util.concurrent.CompletableFuture<Void>[] futures =
        new java.util.concurrent.CompletableFuture[10];

    for (int i = 0; i < futures.length; i++) {
      futures[i] =
          java.util.concurrent.CompletableFuture.runAsync(
              () -> {
                testRoundTrip(validInstance, widenFunc, narrowFunc);
              });
    }

    // Wait for all to complete
    java.util.concurrent.CompletableFuture<Void> allTasks =
        java.util.concurrent.CompletableFuture.allOf(futures);

    assertThat(allTasks)
        .as("All concurrent operations should complete successfully")
        .succeedsWithin(java.time.Duration.ofSeconds(5));
  }

  /**
   * Validates that KindHelper implementation follows standardized patterns.
   *
   * <p>Performs static analysis of the implementation to ensure it follows the standardized error
   * handling patterns.
   *
   * @param targetType The concrete type class
   * @param helperClass The KindHelper implementation class
   */
  public static void validateImplementationStandards(Class<?> targetType, Class<?> helperClass) {

    assertThat(helperClass).as("KindHelper implementation class should be non-null").isNotNull();

    assertThat(targetType).as("Target type class should be non-null").isNotNull();

    // Verify naming conventions
    String helperName = helperClass.getSimpleName();
    String targetName = targetType.getSimpleName();

    assertThat(helperName)
        .as("KindHelper should follow naming convention")
        .satisfiesAnyOf(
            name -> assertThat(name).contains(targetName),
            name -> assertThat(name).contains("KindHelper"),
            name -> assertThat(name).contains("Helper"));

    // Verify the helper has standard methods
    try {
      helperClass.getMethod("widen", Object.class);
      helperClass.getMethod("narrow", Kind.class);
    } catch (NoSuchMethodException e) {
      throw new AssertionError("KindHelper should have standard widen/narrow methods", e);
    }
  }

  /**
   * Tests performance characteristics of widen/narrow operations.
   *
   * <p>Ensures that widen/narrow operations are efficient and don't have unexpected performance
   * characteristics.
   *
   * @param validInstance A valid instance of the concrete type
   * @param widenFunc Function to widen concrete type to Kind
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <T> The concrete type
   * @param <F> The witness type
   * @param <A> The value type
   */
  public static <T, F, A> void testPerformance(
      T validInstance, Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {

    int iterations = 10000;

    // Warm up
    for (int i = 0; i < 1000; i++) {
      Kind<F, A> widened = widenFunc.apply(validInstance);
      narrowFunc.apply(widened);
    }

    // Measure widen performance
    long widenStart = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      widenFunc.apply(validInstance);
    }
    long widenTime = System.nanoTime() - widenStart;

    // Measure narrow performance
    Kind<F, A> widened = widenFunc.apply(validInstance);
    long narrowStart = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      narrowFunc.apply(widened);
    }
    long narrowTime = System.nanoTime() - narrowStart;

    // Verify reasonable performance (should be very fast)
    double widenAvgNanos = (double) widenTime / iterations;
    double narrowAvgNanos = (double) narrowTime / iterations;

    assertThat(widenAvgNanos)
        .as("widen operation should be fast (< 1000ns average)")
        .isLessThan(1000.0);

    assertThat(narrowAvgNanos)
        .as("narrow operation should be fast (< 1000ns average)")
        .isLessThan(1000.0);
  }

  /**
   * Tests memory efficiency of widen/narrow operations.
   *
   * <p>Verifies that widen/narrow operations don't create excessive garbage or memory overhead.
   *
   * @param validInstance A valid instance of the concrete type
   * @param widenFunc Function to widen concrete type to Kind
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <T> The concrete type
   * @param <F> The witness type
   * @param <A> The value type
   */
  public static <T, F, A> void testMemoryEfficiency(
      T validInstance, Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {

    // Force garbage collection before testing
    System.gc();
    Runtime runtime = Runtime.getRuntime();
    long initialMemory = runtime.totalMemory() - runtime.freeMemory();

    // Perform many operations
    int iterations = 100000;
    for (int i = 0; i < iterations; i++) {
      Kind<F, A> widened = widenFunc.apply(validInstance);
      narrowFunc.apply(widened);
    }

    // Force garbage collection and measure
    System.gc();
    long finalMemory = runtime.totalMemory() - runtime.freeMemory();
    long memoryUsed = finalMemory - initialMemory;

    // Memory usage should be reasonable (less than 10MB for 100k operations)
    assertThat(memoryUsed)
        .as("widen/narrow operations should not consume excessive memory")
        .isLessThan(10 * 1024 * 1024); // 10MB
  }

  /**
   * Tests error message quality for validation failures.
   *
   * <p>Ensures that validation error messages are descriptive and helpful for debugging.
   *
   * @param targetType The concrete type class
   * @param widenFunc Function to widen concrete type to Kind
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <T> The concrete type
   * @param <F> The witness type
   * @param <A> The value type
   */
  public static <T, F, A> void testErrorMessageQuality(
      Class<T> targetType, Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {

    // Test widen null error message
    try {
      widenFunc.apply(null);
      throw new AssertionError("widen should have thrown for null input");
    } catch (NullPointerException e) {
      assertThat(e.getMessage())
          .as("widen null error message should be descriptive")
          .isNotNull()
          .isNotEmpty()
          .containsIgnoringCase(targetType.getSimpleName().toLowerCase());
    }

    // Test narrow null error message
    try {
      narrowFunc.apply(null);
      throw new AssertionError("narrow should have thrown for null input");
    } catch (Exception e) {
      assertThat(e.getMessage())
          .as("narrow null error message should be descriptive")
          .isNotNull()
          .isNotEmpty()
          .containsIgnoringCase(targetType.getSimpleName().toLowerCase());
    }

    // Test narrow invalid type error message
    Kind<F, A> invalidKind = TestData.createDummyKind("invalid");
    try {
      narrowFunc.apply(invalidKind);
      throw new AssertionError("narrow should have thrown for invalid Kind");
    } catch (Exception e) {
      assertThat(e.getMessage())
          .as("narrow invalid type error message should be descriptive")
          .isNotNull()
          .isNotEmpty()
          .satisfiesAnyOf(
              msg -> assertThat(msg).containsIgnoringCase(targetType.getSimpleName().toLowerCase()),
              msg -> assertThat(msg).containsIgnoringCase("kind"),
              msg -> assertThat(msg).containsIgnoringCase("type"));
    }
  }

  /**
   * Comprehensive test that validates all aspects of a KindHelper implementation.
   *
   * <p>This is the most thorough test method that includes all validation types. Use this when you
   * want complete confidence in your KindHelper implementation.
   *
   * @param validInstance A valid instance of the concrete type
   * @param targetType The concrete type class
   * @param helperClass The KindHelper implementation class
   * @param widenFunc Function to widen concrete type to Kind
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <T> The concrete type
   * @param <F> The witness type
   * @param <A> The value type
   */
  public static <T, F, A> void testComprehensive(
      T validInstance,
      Class<T> targetType,
      Class<?> helperClass,
      Function<T, Kind<F, A>> widenFunc,
      Function<Kind<F, A>, T> narrowFunc) {

    // Core functionality tests
    testComplete(validInstance, targetType, widenFunc, narrowFunc);

    // Implementation standards
    validateImplementationStandards(targetType, helperClass);

    // Error message quality
    testErrorMessageQuality(targetType, widenFunc, narrowFunc);

    // Performance characteristics (optional - can be expensive)
    if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
      testPerformance(validInstance, widenFunc, narrowFunc);
      testMemoryEfficiency(validInstance, widenFunc, narrowFunc);
    }

    // Thread safety (optional - can be expensive)
    if (Boolean.parseBoolean(System.getProperty("test.concurrency", "false"))) {
      testConcurrentAccess(validInstance, widenFunc, narrowFunc);
    }
  }

  /**
   * Quick test suitable for fast unit test suites.
   *
   * <p>Includes essential functionality and validation tests but skips performance and concurrency
   * tests.
   *
   * @param validInstance A valid instance of the concrete type
   * @param targetType The concrete type class
   * @param widenFunc Function to widen concrete type to Kind
   * @param narrowFunc Function to narrow Kind to concrete type
   * @param <T> The concrete type
   * @param <F> The witness type
   * @param <A> The value type
   */
  public static <T, F, A> void testQuick(
      T validInstance,
      Class<T> targetType,
      Function<T, Kind<F, A>> widenFunc,
      Function<Kind<F, A>, T> narrowFunc) {

    testRoundTrip(validInstance, widenFunc, narrowFunc);
    testValidations(targetType, widenFunc, narrowFunc);
    testInvalidType(targetType, widenFunc, narrowFunc);
  }
}
