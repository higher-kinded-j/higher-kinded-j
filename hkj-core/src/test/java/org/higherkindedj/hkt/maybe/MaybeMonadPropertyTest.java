// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;

/**
 * Property-based tests for Maybe Monad laws using jQwik.
 *
 * <p>This test class verifies that the Maybe monad satisfies the three monad laws across a wide
 * range of inputs:
 *
 * <ul>
 *   <li>Left Identity: {@code flatMap(of(a), f) == f(a)}
 *   <li>Right Identity: {@code flatMap(m, of) == m}
 *   <li>Associativity: {@code flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x), g))}
 * </ul>
 */
class MaybeMonadPropertyTest {

  private final MaybeMonad monad = MaybeMonad.INSTANCE;

  /** Provides arbitrary Maybe<Integer> values for testing */
  @Provide
  Arbitrary<Maybe<Integer>> maybeInts() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15) // 15% chance of null -> Nothing
        .map(i -> i == null ? Maybe.nothing() : Maybe.just(i));
  }

  /** Provides arbitrary flatMap functions (Integer -> Maybe<String>) */
  @Provide
  Arbitrary<Function<Integer, Maybe<String>>> intToMaybeStringFunctions() {
    return Arbitraries.of(
        i -> i % 2 == 0 ? Maybe.just("even:" + i) : Maybe.nothing(),
        i -> i > 0 ? Maybe.just("positive:" + i) : Maybe.nothing(),
        i -> Maybe.just("value:" + i),
        i -> i == 0 ? Maybe.nothing() : Maybe.just(String.valueOf(i)));
  }

  /** Provides arbitrary flatMap functions (String -> Maybe<String>) */
  @Provide
  Arbitrary<Function<String, Maybe<String>>> stringToMaybeStringFunctions() {
    return Arbitraries.of(
        s -> s.isEmpty() ? Maybe.nothing() : Maybe.just(s.toUpperCase()),
        s -> s.length() > 3 ? Maybe.just("long:" + s) : Maybe.nothing(),
        s -> Maybe.just("transformed:" + s));
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
      @ForAll("intToMaybeStringFunctions") Function<Integer, Maybe<String>> f) {

    // Left side: flatMap(of(a), f)
    Kind<MaybeKind.Witness, Integer> ofValue = monad.of(value);
    Kind<MaybeKind.Witness, String> leftSide = monad.flatMap(i -> MAYBE.widen(f.apply(i)), ofValue);

    // Right side: f(a)
    Maybe<String> rightSide = f.apply(value);

    assertThat(MAYBE.narrow(leftSide)).isEqualTo(rightSide);
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
  void rightIdentityLaw(@ForAll("maybeInts") Maybe<Integer> maybe) {
    Kind<MaybeKind.Witness, Integer> kindMaybe = MAYBE.widen(maybe);

    // flatMap(m, of)
    Kind<MaybeKind.Witness, Integer> result = monad.flatMap(monad::of, kindMaybe);

    assertThat(MAYBE.narrow(result)).isEqualTo(maybe);
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
      @ForAll("maybeInts") Maybe<Integer> maybe,
      @ForAll("intToMaybeStringFunctions") Function<Integer, Maybe<String>> f,
      @ForAll("stringToMaybeStringFunctions") Function<String, Maybe<String>> g) {

    Kind<MaybeKind.Witness, Integer> kindMaybe = MAYBE.widen(maybe);

    // Left side: flatMap(flatMap(m, f), g)
    Function<Integer, Kind<MaybeKind.Witness, String>> fLifted = i -> MAYBE.widen(f.apply(i));
    Function<String, Kind<MaybeKind.Witness, String>> gLifted = s -> MAYBE.widen(g.apply(s));

    Kind<MaybeKind.Witness, String> innerFlatMap = monad.flatMap(fLifted, kindMaybe);
    Kind<MaybeKind.Witness, String> leftSide = monad.flatMap(gLifted, innerFlatMap);

    // Right side: flatMap(m, x -> flatMap(f(x), g))
    Function<Integer, Kind<MaybeKind.Witness, String>> composed =
        x -> monad.flatMap(gLifted, fLifted.apply(x));
    Kind<MaybeKind.Witness, String> rightSide = monad.flatMap(composed, kindMaybe);

    assertThat(MAYBE.narrow(leftSide)).isEqualTo(MAYBE.narrow(rightSide));
  }

  /**
   * Property: flatMap over Nothing always returns Nothing
   *
   * <p>This is a derived property that helps verify correct implementation.
   */
  @Property
  @Label("FlatMapping over Nothing always returns Nothing")
  void flatMapPreservesNothing(
      @ForAll("intToMaybeStringFunctions") Function<Integer, Maybe<String>> f) {

    Maybe<Integer> nothing = Maybe.nothing();
    Kind<MaybeKind.Witness, Integer> kindNothing = MAYBE.widen(nothing);

    Kind<MaybeKind.Witness, String> result =
        monad.flatMap(i -> MAYBE.widen(f.apply(i)), kindNothing);

    assertThat(MAYBE.narrow(result).isNothing()).isTrue();
  }

  /**
   * Property: flatMap applies function to Just values and flattens
   *
   * <p>This property verifies that flatMap correctly extracts the value from Just, applies the
   * function, and flattens the result.
   */
  @Property
  @Label("FlatMap applies function and flattens for Just values")
  void flatMapAppliesAndFlattens(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToMaybeStringFunctions") Function<Integer, Maybe<String>> f) {

    Maybe<Integer> just = Maybe.just(value);
    Kind<MaybeKind.Witness, Integer> kindJust = MAYBE.widen(just);

    Kind<MaybeKind.Witness, String> result = monad.flatMap(i -> MAYBE.widen(f.apply(i)), kindJust);

    // Result should equal f(value) directly
    assertThat(MAYBE.narrow(result)).isEqualTo(f.apply(value));
  }

  /**
   * Property: Chaining multiple flatMaps
   *
   * <p>Demonstrates that complex chains of flatMap operations maintain law compliance.
   */
  @Property(tries = 50)
  @Label("Multiple flatMap operations chain correctly")
  void multipleFlatMapsChain(@ForAll("maybeInts") Maybe<Integer> maybe) {
    Kind<MaybeKind.Witness, Integer> kindMaybe = MAYBE.widen(maybe);

    Function<Integer, Kind<MaybeKind.Witness, Integer>> addOne = i -> monad.of(i + 1);
    Function<Integer, Kind<MaybeKind.Witness, Integer>> double_ = i -> monad.of(i * 2);
    Function<Integer, Kind<MaybeKind.Witness, Integer>> conditionalNothing =
        i -> i > 100 ? MAYBE.widen(Maybe.nothing()) : monad.of(i);

    // Apply flatMaps in sequence
    Kind<MaybeKind.Witness, Integer> step1 = monad.flatMap(addOne, kindMaybe);
    Kind<MaybeKind.Witness, Integer> step2 = monad.flatMap(double_, step1);
    Kind<MaybeKind.Witness, Integer> step3 = monad.flatMap(conditionalNothing, step2);

    // Compose all operations
    Function<Integer, Kind<MaybeKind.Witness, Integer>> composed =
        i -> monad.flatMap(conditionalNothing, monad.flatMap(double_, addOne.apply(i)));
    Kind<MaybeKind.Witness, Integer> composedResult = monad.flatMap(composed, kindMaybe);

    assertThat(MAYBE.narrow(step3)).isEqualTo(MAYBE.narrow(composedResult));
  }
}
