// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;

/**
 * Property-based tests for Either Functor laws using jQwik.
 *
 * <p>This test class demonstrates the use of property-based testing to verify Functor laws hold for
 * Either across a wide range of inputs, providing more comprehensive coverage than example-based
 * tests.
 *
 * <p>Either is right-biased, meaning map operations only apply to Right values, while Left values
 * are passed through unchanged.
 */
class EitherFunctorPropertyTest {

  private final EitherFunctor<String> functor = EitherFunctor.instance();

  /** Provides arbitrary Either<String, Integer> values for testing */
  @Provide
  Arbitrary<Either<String, Integer>> eitherInts() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .injectNull(0.15) // 15% chance of null -> converts to Left
        .flatMap(
            i -> {
              if (i == null) {
                return Arbitraries.just(Either.left("null value"));
              }
              // 20% chance of Left with various error messages
              if (i % 5 == 0) {
                return Arbitraries.of(
                        "error: negative",
                        "error: too large",
                        "error: invalid",
                        "error: out of range")
                    .map(Either::left);
              }
              return Arbitraries.just(Either.right(i));
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
  void functorIdentityLaw(@ForAll("eitherInts") Either<String, Integer> either) {
    Kind<EitherKind.Witness<String>, Integer> kindEither = EITHER.widen(either);
    Function<Integer, Integer> identity = x -> x;

    Kind<EitherKind.Witness<String>, Integer> result = functor.map(identity, kindEither);

    assertThat(EITHER.narrow(result)).isEqualTo(either);
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
      @ForAll("eitherInts") Either<String, Integer> either,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    Kind<EitherKind.Witness<String>, Integer> kindEither = EITHER.widen(either);

    // Left side: map(g ∘ f)
    Function<Integer, Integer> composed = i -> g.apply(f.apply(i));
    Kind<EitherKind.Witness<String>, Integer> leftSide = functor.map(composed, kindEither);

    // Right side: map(g, map(f, fa))
    Kind<EitherKind.Witness<String>, String> intermediate = functor.map(f, kindEither);
    Kind<EitherKind.Witness<String>, Integer> rightSide = functor.map(g, intermediate);

    assertThat(EITHER.narrow(leftSide)).isEqualTo(EITHER.narrow(rightSide));
  }

  /**
   * Property: map preserves Left values
   *
   * <p>Mapping any function over a Left should always return the same Left unchanged.
   */
  @Property
  @Label("Mapping over Left always returns the same Left")
  void mapPreservesLeft(@ForAll("intToStringFunctions") Function<Integer, String> f) {
    Either<String, Integer> left = Either.left("error: test");
    Kind<EitherKind.Witness<String>, Integer> kindLeft = EITHER.widen(left);

    Kind<EitherKind.Witness<String>, String> result = functor.map(f, kindLeft);

    Either<String, String> narrowed = EITHER.narrow(result);
    assertThat(narrowed.isLeft()).isTrue();
    assertThat(narrowed.getLeft()).isEqualTo("error: test");
  }

  /**
   * Property: map applies function to Right values
   *
   * <p>Mapping a function over Right(x) should return Right(f(x)).
   */
  @Property
  @Label("Mapping over Right applies the function")
  void mapAppliesFunctionToRight(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    Either<String, Integer> right = Either.right(value);
    Kind<EitherKind.Witness<String>, Integer> kindRight = EITHER.widen(right);

    Kind<EitherKind.Witness<String>, String> result = functor.map(f, kindRight);
    Either<String, String> narrowed = EITHER.narrow(result);

    assertThat(narrowed.isRight()).isTrue();
    assertThat(narrowed.getRight()).isEqualTo(f.apply(value));
  }

  /**
   * Property: Multiple maps can be composed
   *
   * <p>Demonstrates that mapping multiple times is equivalent to a single composed map.
   */
  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("eitherInts") Either<String, Integer> either) {
    Kind<EitherKind.Witness<String>, Integer> kindEither = EITHER.widen(either);

    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> double_ = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    // Apply functions one at a time
    Kind<EitherKind.Witness<String>, Integer> step1 = functor.map(addOne, kindEither);
    Kind<EitherKind.Witness<String>, Integer> step2 = functor.map(double_, step1);
    Kind<EitherKind.Witness<String>, Integer> step3 = functor.map(subtract3, step2);

    // Apply composed function
    Function<Integer, Integer> composed = x -> subtract3.apply(double_.apply(addOne.apply(x)));
    Kind<EitherKind.Witness<String>, Integer> composedResult = functor.map(composed, kindEither);

    assertThat(EITHER.narrow(step3)).isEqualTo(EITHER.narrow(composedResult));
  }

  /**
   * Property: Left values propagate through map chains
   *
   * <p>A Left value at any point in the chain causes all subsequent maps to be skipped.
   */
  @Property
  @Label("Left values short-circuit map chains")
  void leftValuesShortCircuit(@ForAll String errorMessage) {
    Either<String, Integer> left = Either.left(errorMessage);
    Kind<EitherKind.Witness<String>, Integer> kindLeft = EITHER.widen(left);

    // Chain multiple maps - none should execute
    Kind<EitherKind.Witness<String>, Integer> result =
        functor.map(x -> x - 100, functor.map(x -> x * 1000, functor.map(x -> x + 500, kindLeft)));

    Either<String, Integer> narrowed = EITHER.narrow(result);
    assertThat(narrowed.isLeft()).isTrue();
    assertThat(narrowed.getLeft()).isEqualTo(errorMessage);
  }
}
