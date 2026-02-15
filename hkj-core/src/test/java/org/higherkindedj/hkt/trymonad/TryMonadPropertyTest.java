// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;

/**
 * Property-based tests for Try Monad laws using jQwik.
 *
 * <p>This test class verifies that the Try monad satisfies the three monad laws across a wide range
 * of inputs:
 *
 * <ul>
 *   <li>Left Identity: {@code flatMap(of(a), f) == f(a)}
 *   <li>Right Identity: {@code flatMap(m, of) == m}
 *   <li>Associativity: {@code flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x), g))}
 * </ul>
 *
 * <p>Try is right-biased and fail-fast: the first Failure encountered stops the computation.
 */
class TryMonadPropertyTest {

  private final TryMonad monad = TryMonad.INSTANCE;

  /**
   * Helper method to compare Try instances semantically.
   *
   * <p>Since Try.Failure creates new exception objects, we can't use direct equality. This method
   * compares: - For Success: the contained values - For Failure: exception type and message
   */
  private <T> void assertTryEquals(Try<T> actual, Try<T> expected) throws Throwable {
    assertThat(actual.isSuccess()).isEqualTo(expected.isSuccess());
    assertThat(actual.isFailure()).isEqualTo(expected.isFailure());

    if (expected.isSuccess()) {
      assertThat(actual.get()).isEqualTo(expected.get());
    } else {
      Throwable expectedCause = ((Try.Failure<T>) expected).cause();
      Throwable actualCause = ((Try.Failure<T>) actual).cause();
      assertThat(actualCause.getClass()).isEqualTo(expectedCause.getClass());
      assertThat(actualCause.getMessage()).isEqualTo(expectedCause.getMessage());
    }
  }

  /** Provides arbitrary Try<Integer> values for testing */
  @Provide
  Arbitrary<Try<Integer>> tryInts() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15) // 15% chance of null -> Failure
        .flatMap(
            i -> {
              if (i == null) {
                return Arbitraries.just(Try.failure(new NullPointerException("null value")));
              }
              // 20% chance of Failure
              if (i % 5 == 0) {
                return Arbitraries.of(
                        new IllegalStateException("Invalid state"),
                        new ArithmeticException("Division by zero"),
                        new RuntimeException("Computation error"))
                    .map(Try::failure);
              }
              return Arbitraries.just(Try.success(i));
            });
  }

  /** Provides arbitrary flatMap functions (Integer -> Try<String>) */
  @Provide
  Arbitrary<Function<Integer, Try<String>>> intToTryStringFunctions() {
    return Arbitraries.of(
        i ->
            i % 2 == 0
                ? Try.success("even:" + i)
                : Try.failure(new IllegalArgumentException("odd number")),
        i ->
            i > 0
                ? Try.success("positive:" + i)
                : Try.failure(new IllegalArgumentException("non-positive")),
        i -> Try.success("value:" + i),
        i ->
            i == 0 ? Try.failure(new ArithmeticException("zero")) : Try.success(String.valueOf(i)));
  }

  /** Provides arbitrary flatMap functions (String -> Try<String>) */
  @Provide
  Arbitrary<Function<String, Try<String>>> stringToTryStringFunctions() {
    return Arbitraries.of(
        s ->
            s.isEmpty()
                ? Try.failure(new IllegalArgumentException("empty"))
                : Try.success(s.toUpperCase()),
        s ->
            s.length() > 3
                ? Try.success("long:" + s)
                : Try.failure(new IllegalArgumentException("too short")),
        s -> Try.success("transformed:" + s));
  }

  /**
   * Property: Monad Left Identity Law
   *
   * <p>For all values {@code a} and functions {@code f}: {@code flatMap(of(a), f) == f(a)}
   *
   * <p>This law states that wrapping a value with {@code of} and then flat-mapping is equivalent to
   * just applying the function.
   */
  @Property
  @Label("Monad Left Identity Law: flatMap(of(a), f) = f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToTryStringFunctions") Function<Integer, Try<String>> f)
      throws Throwable {

    // Left side: flatMap(of(a), f)
    Kind<TryKind.Witness, Integer> ofValue = monad.of(value);
    Kind<TryKind.Witness, String> leftSide = monad.flatMap(i -> TRY.widen(f.apply(i)), ofValue);

    // Right side: f(a)
    Try<String> rightSide = f.apply(value);

    assertTryEquals(TRY.narrow(leftSide), rightSide);
  }

  /**
   * Property: Monad Right Identity Law
   *
   * <p>For all monadic values {@code m}: {@code flatMap(m, of) == m}
   *
   * <p>This law states that flat-mapping with {@code of} should return the original value
   * unchanged.
   */
  @Property
  @Label("Monad Right Identity Law: flatMap(m, of) = m")
  void rightIdentityLaw(@ForAll("tryInts") Try<Integer> tryValue) {
    Kind<TryKind.Witness, Integer> kindTry = TRY.widen(tryValue);

    // flatMap(m, of)
    Kind<TryKind.Witness, Integer> result = monad.flatMap(monad::of, kindTry);

    assertThat(TRY.narrow(result)).isEqualTo(tryValue);
  }

  /**
   * Property: Monad Associativity Law
   *
   * <p>For all monadic values {@code m} and functions {@code f} and {@code g}: {@code
   * flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x), g))}
   *
   * <p>This law ensures that the order of nested flatMaps doesn't matter.
   */
  @Property
  @Label("Monad Associativity Law: flatMap(flatMap(m, f), g) = flatMap(m, x -> flatMap(f(x), g))")
  void associativityLaw(
      @ForAll("tryInts") Try<Integer> tryValue,
      @ForAll("intToTryStringFunctions") Function<Integer, Try<String>> f,
      @ForAll("stringToTryStringFunctions") Function<String, Try<String>> g)
      throws Throwable {

    Kind<TryKind.Witness, Integer> kindTry = TRY.widen(tryValue);

    // Left side: flatMap(flatMap(m, f), g)
    Function<Integer, Kind<TryKind.Witness, String>> fLifted = i -> TRY.widen(f.apply(i));
    Function<String, Kind<TryKind.Witness, String>> gLifted = s -> TRY.widen(g.apply(s));

    Kind<TryKind.Witness, String> innerFlatMap = monad.flatMap(fLifted, kindTry);
    Kind<TryKind.Witness, String> leftSide = monad.flatMap(gLifted, innerFlatMap);

    // Right side: flatMap(m, x -> flatMap(f(x), g))
    Function<Integer, Kind<TryKind.Witness, String>> composed =
        x -> monad.flatMap(gLifted, fLifted.apply(x));
    Kind<TryKind.Witness, String> rightSide = monad.flatMap(composed, kindTry);

    assertTryEquals(TRY.narrow(leftSide), TRY.narrow(rightSide));
  }

  /**
   * Property: flatMap over Failure always returns the same Failure
   *
   * <p>This is a derived property that helps verify correct fail-fast implementation.
   */
  @Property
  @Label("FlatMapping over Failure always returns the same Failure")
  void flatMapPreservesFailure(
      @ForAll("intToTryStringFunctions") Function<Integer, Try<String>> f) {

    Throwable exception = new RuntimeException("test failure");
    Try<Integer> failure = Try.failure(exception);
    Kind<TryKind.Witness, Integer> kindFailure = TRY.widen(failure);

    Kind<TryKind.Witness, String> result = monad.flatMap(i -> TRY.widen(f.apply(i)), kindFailure);

    Try<String> narrowed = TRY.narrow(result);
    assertThat(narrowed.isFailure()).isTrue();
    assertThat(((Try.Failure<String>) narrowed).cause()).isEqualTo(exception);
  }

  /**
   * Property: flatMap applies function to Success values and flattens
   *
   * <p>This property verifies that flatMap correctly extracts the value from Success, applies the
   * function, and flattens the result.
   */
  @Property
  @Label("FlatMap applies function and flattens for Success values")
  void flatMapAppliesAndFlattens(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToTryStringFunctions") Function<Integer, Try<String>> f)
      throws Throwable {

    Try<Integer> success = Try.success(value);
    Kind<TryKind.Witness, Integer> kindSuccess = TRY.widen(success);

    // Compute expected result once
    Try<String> expected = f.apply(value);
    Kind<TryKind.Witness, String> result = monad.flatMap(i -> TRY.widen(f.apply(i)), kindSuccess);

    // Result should equal f(value) semantically
    assertTryEquals(TRY.narrow(result), expected);
  }

  /**
   * Property: Chaining multiple flatMaps (fail-fast behavior)
   *
   * <p>Demonstrates that the first Failure in a chain stops all subsequent computations.
   */
  @Property(tries = 50)
  @Label("Multiple flatMap operations chain correctly (fail-fast)")
  void multipleFlatMapsChain(@ForAll("tryInts") Try<Integer> tryValue) throws Throwable {
    Kind<TryKind.Witness, Integer> kindTry = TRY.widen(tryValue);

    Function<Integer, Kind<TryKind.Witness, Integer>> addOne = i -> monad.of(i + 1);
    Function<Integer, Kind<TryKind.Witness, Integer>> double_ = i -> monad.of(i * 2);
    Function<Integer, Kind<TryKind.Witness, Integer>> conditionalFailure =
        i -> i > 100 ? TRY.widen(Try.failure(new IllegalStateException("too large"))) : monad.of(i);

    // Apply flatMaps in sequence
    Kind<TryKind.Witness, Integer> step1 = monad.flatMap(addOne, kindTry);
    Kind<TryKind.Witness, Integer> step2 = monad.flatMap(double_, step1);
    Kind<TryKind.Witness, Integer> step3 = monad.flatMap(conditionalFailure, step2);

    // Compose all operations
    Function<Integer, Kind<TryKind.Witness, Integer>> composed =
        i -> monad.flatMap(conditionalFailure, monad.flatMap(double_, addOne.apply(i)));
    Kind<TryKind.Witness, Integer> composedResult = monad.flatMap(composed, kindTry);

    assertTryEquals(TRY.narrow(step3), TRY.narrow(composedResult));
  }

  /**
   * Property: raiseError creates a Failure value
   *
   * <p>Verifies that the MonadError operation raiseError correctly creates a Failure.
   */
  @Property
  @Label("raiseError creates a Failure with the specified exception")
  void raiseErrorCreatesFailure(@ForAll String errorMessage) {
    Throwable exception = new RuntimeException(errorMessage);
    Kind<TryKind.Witness, Integer> error = monad.raiseError(exception);

    Try<Integer> narrowed = TRY.narrow(error);
    assertThat(narrowed.isFailure()).isTrue();
    Throwable cause = ((Try.Failure<Integer>) narrowed).cause();
    assertThat(cause).isInstanceOf(RuntimeException.class);
    assertThat(cause.getMessage()).isEqualTo(errorMessage);
  }

  /**
   * Property: handleErrorWith can recover from Failure values
   *
   * <p>Demonstrates error recovery using handleErrorWith.
   */
  @Property
  @Label("handleErrorWith can recover from Failure values")
  void handleErrorWithRecovers(@ForAll @IntRange(min = 0, max = 100) int recoveryValue)
      throws Throwable {
    Try<Integer> failure = Try.failure(new RuntimeException("failure"));
    Kind<TryKind.Witness, Integer> kindFailure = TRY.widen(failure);

    Kind<TryKind.Witness, Integer> recovered =
        monad.handleErrorWith(kindFailure, throwable -> monad.of(recoveryValue));

    Try<Integer> narrowed = TRY.narrow(recovered);
    assertThat(narrowed.isSuccess()).isTrue();
    assertThat(narrowed.get()).isEqualTo(recoveryValue);
  }

  /**
   * Property: recover provides fallback value for Failure
   *
   * <p>Demonstrates simple recovery with a fallback value.
   */
  @Property
  @Label("recover provides fallback value for Failure")
  void recoverProvidesFallback(@ForAll @IntRange(min = 0, max = 100) int fallbackValue)
      throws Throwable {
    Try<Integer> failure = Try.failure(new IllegalStateException("error"));
    Kind<TryKind.Witness, Integer> kindFailure = TRY.widen(failure);

    Kind<TryKind.Witness, Integer> recovered = monad.recover(kindFailure, fallbackValue);

    Try<Integer> narrowed = TRY.narrow(recovered);
    assertThat(narrowed.isSuccess()).isTrue();
    assertThat(narrowed.get()).isEqualTo(fallbackValue);
  }
}
