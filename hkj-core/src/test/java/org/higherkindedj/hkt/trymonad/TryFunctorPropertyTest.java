// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;

/**
 * Property-based tests for Try Functor laws using jQwik.
 *
 * <p>This test class demonstrates the use of property-based testing to verify Functor laws hold for
 * Try across a wide range of inputs, providing more comprehensive coverage than example-based
 * tests.
 *
 * <p>Try is right-biased: map operations only apply to Success values, while Failure values are
 * passed through unchanged.
 */
class TryFunctorPropertyTest {

  private final TryFunctor functor = new TryFunctor();

  /** Provides arbitrary Try<Integer> values for testing */
  @Provide
  Arbitrary<Try<Integer>> tryInts() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .injectNull(0.15) // 15% chance of null -> converts to Failure
        .flatMap(
            i -> {
              if (i == null) {
                return Arbitraries.just(Try.failure(new IllegalArgumentException("null value")));
              }
              // 20% chance of Failure with various exceptions
              if (i % 5 == 0) {
                return Arbitraries.of(
                        new IllegalStateException("Invalid state"),
                        new ArithmeticException("Math error"),
                        new RuntimeException("Runtime failure"))
                    .map(Try::failure);
              }
              return Arbitraries.just(Try.success(i));
            });
  }

  /** Provides arbitrary functions for Integer -> String transformations */
  @Provide
  Arbitrary<Function<Integer, String>> intToStringFunctions() {
    return Arbitraries.of(
        i -> "value:" + i, i -> String.valueOf(i * 2), i -> "test-" + i, Object::toString);
  }

  /** Provides arbitrary functions for String -> Integer transformations */
  @Provide
  Arbitrary<Function<String, Integer>> stringToIntFunctions() {
    return Arbitraries.of(String::length, String::hashCode, s -> s.isEmpty() ? 0 : 1);
  }

  /**
   * Property: Functor Identity Law
   *
   * <p>For all values {@code fa}, mapping the identity function should return the original value:
   * {@code map(id, fa) == fa}
   */
  @Property
  @Label("Functor Identity Law: map(id) = id")
  void functorIdentityLaw(@ForAll("tryInts") Try<Integer> tryValue) {
    Kind<TryKind.Witness, Integer> kindTry = TRY.widen(tryValue);
    Function<Integer, Integer> identity = x -> x;

    Kind<TryKind.Witness, Integer> result = functor.map(identity, kindTry);

    assertThat(TRY.narrow(result)).isEqualTo(tryValue);
  }

  /**
   * Property: Functor Composition Law
   *
   * <p>For all values {@code fa} and functions {@code f} and {@code g}: {@code map(g ∘ f, fa) ==
   * map(g, map(f, fa))}
   */
  @Property
  @Label("Functor Composition Law: map(g ∘ f) = map(g) ∘ map(f)")
  void functorCompositionLaw(
      @ForAll("tryInts") Try<Integer> tryValue,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    Kind<TryKind.Witness, Integer> kindTry = TRY.widen(tryValue);

    // Left side: map(g ∘ f)
    Function<Integer, Integer> composed = i -> g.apply(f.apply(i));
    Kind<TryKind.Witness, Integer> leftSide = functor.map(composed, kindTry);

    // Right side: map(g, map(f, fa))
    Kind<TryKind.Witness, String> intermediate = functor.map(f, kindTry);
    Kind<TryKind.Witness, Integer> rightSide = functor.map(g, intermediate);

    assertThat(TRY.narrow(leftSide)).isEqualTo(TRY.narrow(rightSide));
  }

  /**
   * Property: map preserves Failure
   *
   * <p>Mapping any function over a Failure should always return the same Failure unchanged.
   */
  @Property
  @Label("Mapping over Failure always returns the same Failure")
  void mapPreservesFailure(@ForAll("intToStringFunctions") Function<Integer, String> f) {
    Throwable exception = new IllegalStateException("test failure");
    Try<Integer> failure = Try.failure(exception);
    Kind<TryKind.Witness, Integer> kindFailure = TRY.widen(failure);

    Kind<TryKind.Witness, String> result = functor.map(f, kindFailure);

    Try<String> narrowed = TRY.narrow(result);
    assertThat(narrowed.isFailure()).isTrue();
    assertThat(((Try.Failure<String>) narrowed).cause()).isEqualTo(exception);
  }

  /**
   * Property: map applies function to Success values
   *
   * <p>Mapping a function over Success(x) should return Success(f(x)).
   */
  @Property
  @Label("Mapping over Success applies the function")
  void mapAppliesFunctionToSuccess(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f)
      throws Throwable {

    Try<Integer> success = Try.success(value);
    Kind<TryKind.Witness, Integer> kindSuccess = TRY.widen(success);

    Kind<TryKind.Witness, String> result = functor.map(f, kindSuccess);
    Try<String> narrowed = TRY.narrow(result);

    assertThat(narrowed.isSuccess()).isTrue();
    assertThat(narrowed.get()).isEqualTo(f.apply(value));
  }

  /**
   * Property: Multiple maps can be composed
   *
   * <p>Demonstrates that mapping multiple times is equivalent to a single composed map.
   */
  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("tryInts") Try<Integer> tryValue) {
    Kind<TryKind.Witness, Integer> kindTry = TRY.widen(tryValue);

    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> double_ = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    // Apply functions one at a time
    Kind<TryKind.Witness, Integer> step1 = functor.map(addOne, kindTry);
    Kind<TryKind.Witness, Integer> step2 = functor.map(double_, step1);
    Kind<TryKind.Witness, Integer> step3 = functor.map(subtract3, step2);

    // Apply composed function
    Function<Integer, Integer> composed = x -> subtract3.apply(double_.apply(addOne.apply(x)));
    Kind<TryKind.Witness, Integer> composedResult = functor.map(composed, kindTry);

    assertThat(TRY.narrow(step3)).isEqualTo(TRY.narrow(composedResult));
  }

  /**
   * Property: Failure values propagate through map chains
   *
   * <p>A Failure value at any point in the chain causes all subsequent maps to be skipped.
   */
  @Property
  @Label("Failure values short-circuit map chains")
  void failureValuesShortCircuit(@ForAll("tryInts") Try<Integer> tryValue) {
    if (tryValue.isSuccess()) {
      return; // Property only applies to Failure instances
    }

    Kind<TryKind.Witness, Integer> kindFailure = TRY.widen(tryValue);

    // Chain multiple maps - none should execute
    Kind<TryKind.Witness, Integer> result =
        functor.map(
            x -> x - 100, functor.map(x -> x * 1000, functor.map(x -> x + 500, kindFailure)));

    Try<Integer> narrowed = TRY.narrow(result);
    assertThat(narrowed).isEqualTo(tryValue);
  }

  /**
   * Property: map converts exceptions from functions into Failure
   *
   * <p>If a function throws an exception during map, Try should catch it and return Failure.
   */
  @Example
  @Label("Mapping a function that throws converts Success to Failure")
  void mapCatchesExceptions() {
    Try<Integer> successNull = Try.success(null);
    Kind<TryKind.Witness, Integer> kindSuccess = TRY.widen(successNull);

    // This function will throw NPE when given null
    Function<Integer, String> throwingFunction = i -> i.toString();
    Kind<TryKind.Witness, String> result = functor.map(throwingFunction, kindSuccess);
    Try<String> narrowed = TRY.narrow(result);

    // Should convert to Failure due to NPE
    assertThat(narrowed.isFailure()).isTrue();
    assertThat(((Try.Failure<String>) narrowed).cause()).isInstanceOf(NullPointerException.class);
  }
}
