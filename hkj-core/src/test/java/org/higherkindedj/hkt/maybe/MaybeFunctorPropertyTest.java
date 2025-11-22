// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;

/**
 * Property-based tests for Maybe Functor laws using jQwik.
 *
 * <p>This test class demonstrates the use of property-based testing to verify Functor laws hold for
 * all possible inputs, providing more comprehensive coverage than example-based tests.
 */
class MaybeFunctorPropertyTest {

  private final MaybeFunctor functor = MaybeFunctor.INSTANCE;

  /** Provides arbitrary Maybe<Integer> values for testing */
  @Provide
  Arbitrary<Maybe<Integer>> maybeInts() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .injectNull(0.1) // 10% chance of null
        .map(i -> i == null ? Maybe.nothing() : Maybe.just(i));
  }

  /** Provides arbitrary functions for Integer -> String transformations */
  @Provide
  Arbitrary<Function<Integer, String>> intToStringFunctions() {
    return Arbitraries.of(
        (Function<Integer, String>) (i -> "value:" + i),
        (Function<Integer, String>) (i -> String.valueOf(i * 2)),
        (Function<Integer, String>) (i -> "test-" + i),
        (Function<Integer, String>) (i -> i.toString()));
  }

  /** Provides arbitrary functions for String -> Integer transformations */
  @Provide
  Arbitrary<Function<String, Integer>> stringToIntFunctions() {
    return Arbitraries.of(
        (Function<String, Integer>) String::length,
        (Function<String, Integer>) (s -> s.hashCode()),
        (Function<String, Integer>) (s -> s.isEmpty() ? 0 : 1));
  }

  /**
   * Property: Functor Identity Law
   *
   * <p>For all values {@code fa}, mapping the identity function should return the original value:
   * {@code map(id, fa) == fa}
   */
  @Property
  @Label("Functor Identity Law: map(id) = id")
  void functorIdentityLaw(@ForAll("maybeInts") Maybe<Integer> maybe) {
    Kind<MaybeKind.Witness, Integer> kindMaybe = MAYBE.widen(maybe);
    Function<Integer, Integer> identity = x -> x;

    Kind<MaybeKind.Witness, Integer> result = functor.map(identity, kindMaybe);

    assertThat(MAYBE.narrow(result)).isEqualTo(maybe);
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
      @ForAll("maybeInts") Maybe<Integer> maybe,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    Kind<MaybeKind.Witness, Integer> kindMaybe = MAYBE.widen(maybe);

    // Left side: map(g ∘ f)
    Function<Integer, Integer> composed = i -> g.apply(f.apply(i));
    Kind<MaybeKind.Witness, Integer> leftSide = functor.map(composed, kindMaybe);

    // Right side: map(g, map(f, fa))
    Kind<MaybeKind.Witness, String> intermediate = functor.map(f, kindMaybe);
    Kind<MaybeKind.Witness, Integer> rightSide = functor.map(g, intermediate);

    assertThat(MAYBE.narrow(leftSide)).isEqualTo(MAYBE.narrow(rightSide));
  }

  /**
   * Property: map preserves Nothing
   *
   * <p>Mapping any function over Nothing should always return Nothing.
   */
  @Property
  @Label("Mapping over Nothing always returns Nothing")
  void mapPreservesNothing(@ForAll("intToStringFunctions") Function<Integer, String> f) {
    Maybe<Integer> nothing = Maybe.nothing();
    Kind<MaybeKind.Witness, Integer> kindNothing = MAYBE.widen(nothing);

    Kind<MaybeKind.Witness, String> result = functor.map(f, kindNothing);

    assertThat(MAYBE.narrow(result).isNothing()).isTrue();
  }

  /**
   * Property: map applies function to Just values
   *
   * <p>Mapping a function over Just(x) should return Just(f(x)).
   */
  @Property
  @Label("Mapping over Just applies the function")
  void mapAppliesFunctionToJust(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    Maybe<Integer> just = Maybe.just(value);
    Kind<MaybeKind.Witness, Integer> kindJust = MAYBE.widen(just);

    Kind<MaybeKind.Witness, String> result = functor.map(f, kindJust);
    Maybe<String> narrowed = MAYBE.narrow(result);

    assertThat(narrowed.isJust()).isTrue();
    assertThat(narrowed.get()).isEqualTo(f.apply(value));
  }

  /**
   * Property: Multiple maps can be composed
   *
   * <p>Demonstrates that mapping multiple times is equivalent to a single composed map.
   */
  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("maybeInts") Maybe<Integer> maybe) {
    Kind<MaybeKind.Witness, Integer> kindMaybe = MAYBE.widen(maybe);

    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> double_ = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    // Apply functions one at a time
    Kind<MaybeKind.Witness, Integer> step1 = functor.map(addOne, kindMaybe);
    Kind<MaybeKind.Witness, Integer> step2 = functor.map(double_, step1);
    Kind<MaybeKind.Witness, Integer> step3 = functor.map(subtract3, step2);

    // Apply composed function
    Function<Integer, Integer> composed = x -> subtract3.apply(double_.apply(addOne.apply(x)));
    Kind<MaybeKind.Witness, Integer> composedResult = functor.map(composed, kindMaybe);

    assertThat(MAYBE.narrow(step3)).isEqualTo(MAYBE.narrow(composedResult));
  }
}
