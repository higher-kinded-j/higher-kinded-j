// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;

/**
 * Property-based tests for FreePath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs. FreePath represents
 * computations built from a functor that can be interpreted into any monad.
 */
@Label("FreePath Property-Based Tests")
class FreePathPropertyTest {

  private static final Monad<MaybeKind.Witness> MAYBE_MONAD = MaybeMonad.INSTANCE;
  private static final Natural<MaybeKind.Witness, MaybeKind.Witness> IDENTITY_NAT =
      Natural.identity();

  @Provide
  Arbitrary<FreePath<MaybeKind.Witness, Integer>> freePaths() {
    return Arbitraries.oneOf(
        // Pure values
        Arbitraries.integers().between(-1000, 1000).map(i -> FreePath.pure(i, MaybeMonad.INSTANCE)),
        // Lifted values (Just)
        Arbitraries.integers()
            .between(-1000, 1000)
            .map(i -> FreePath.liftF(MAYBE.just(i), MaybeMonad.INSTANCE)),
        // Chained pure values
        Arbitraries.integers()
            .between(-1000, 1000)
            .map(
                i ->
                    FreePath.pure(i, MaybeMonad.INSTANCE)
                        .via(x -> FreePath.pure(x * 2, MaybeMonad.INSTANCE))));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToStringFunctions() {
    return Arbitraries.of(
        i -> "value:" + i, i -> String.valueOf(i * 2), i -> "n" + i, Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToIntFunctions() {
    return Arbitraries.of(String::length, String::hashCode, s -> s.isEmpty() ? 0 : 1);
  }

  @Provide
  Arbitrary<Function<Integer, FreePath<MaybeKind.Witness, String>>> intToFreeStringFunctions() {
    return Arbitraries.of(
        i -> FreePath.pure("value:" + i, MaybeMonad.INSTANCE),
        i -> FreePath.liftF(MAYBE.just("lifted:" + i), MaybeMonad.INSTANCE),
        i -> FreePath.pure(i > 0 ? "positive" : "non-positive", MaybeMonad.INSTANCE));
  }

  @Provide
  Arbitrary<Function<String, FreePath<MaybeKind.Witness, String>>> stringToFreeStringFunctions() {
    return Arbitraries.of(
        s -> FreePath.pure(s.toUpperCase(), MaybeMonad.INSTANCE),
        s -> FreePath.liftF(MAYBE.just(s + "!"), MaybeMonad.INSTANCE),
        s -> FreePath.pure(s.isEmpty() ? "empty" : s, MaybeMonad.INSTANCE));
  }

  // Helper to run a FreePath and get value
  private <A> A run(FreePath<MaybeKind.Witness, A> path) {
    GenericPath<MaybeKind.Witness, A> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);
    return MAYBE.narrow(result.runKind()).get();
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("freePaths") FreePath<MaybeKind.Witness, Integer> path) {
    FreePath<MaybeKind.Witness, Integer> result = path.map(Function.identity());
    assertThat(run(result)).isEqualTo(run(path));
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("freePaths") FreePath<MaybeKind.Witness, Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    FreePath<MaybeKind.Witness, Integer> leftSide = path.map(f).map(g);
    FreePath<MaybeKind.Witness, Integer> rightSide = path.map(f.andThen(g));

    assertThat(run(leftSide)).isEqualTo(run(rightSide));
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: FreePath.pure(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToFreeStringFunctions")
          Function<Integer, FreePath<MaybeKind.Witness, String>> f) {

    FreePath<MaybeKind.Witness, String> leftSide = FreePath.pure(value, MaybeMonad.INSTANCE).via(f);
    FreePath<MaybeKind.Witness, String> rightSide = f.apply(value);

    assertThat(run(leftSide)).isEqualTo(run(rightSide));
  }

  @Property
  @Label("Monad Right Identity Law: path.via(x -> FreePath.pure(x)) == path")
  void rightIdentityLaw(@ForAll("freePaths") FreePath<MaybeKind.Witness, Integer> path) {
    FreePath<MaybeKind.Witness, Integer> result =
        path.via(x -> FreePath.pure(x, MaybeMonad.INSTANCE));
    assertThat(run(result)).isEqualTo(run(path));
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("freePaths") FreePath<MaybeKind.Witness, Integer> path,
      @ForAll("intToFreeStringFunctions") Function<Integer, FreePath<MaybeKind.Witness, String>> f,
      @ForAll("stringToFreeStringFunctions")
          Function<String, FreePath<MaybeKind.Witness, String>> g) {

    FreePath<MaybeKind.Witness, String> leftSide = path.via(f).via(g);
    FreePath<MaybeKind.Witness, String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(run(leftSide)).isEqualTo(run(rightSide));
  }

  // ===== Derived Properties =====

  @Property
  @Label("pure creates a value that can be interpreted")
  void pureCreatesInterpretableValue(@ForAll @IntRange(min = -100, max = 100) int value) {
    FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(value, MaybeMonad.INSTANCE);
    assertThat(run(path)).isEqualTo(value);
  }

  @Property
  @Label("liftF creates a value that can be interpreted")
  void liftFCreatesInterpretableValue(@ForAll @IntRange(min = -100, max = 100) int value) {
    Kind<MaybeKind.Witness, Integer> just = MAYBE.just(value);
    FreePath<MaybeKind.Witness, Integer> path = FreePath.liftF(just, MaybeMonad.INSTANCE);
    assertThat(run(path)).isEqualTo(value);
  }

  @Property
  @Label("zipWith combines two free values")
  void zipWithCombinesTwoValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    FreePath<MaybeKind.Witness, Integer> pathA = FreePath.pure(a, MaybeMonad.INSTANCE);
    FreePath<MaybeKind.Witness, Integer> pathB = FreePath.pure(b, MaybeMonad.INSTANCE);

    FreePath<MaybeKind.Witness, Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(run(result)).isEqualTo(a + b);
  }

  @Property
  @Label("zipWith with lifted values")
  void zipWithWithLiftedValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    FreePath<MaybeKind.Witness, Integer> pathA = FreePath.liftF(MAYBE.just(a), MaybeMonad.INSTANCE);
    FreePath<MaybeKind.Witness, Integer> pathB = FreePath.liftF(MAYBE.just(b), MaybeMonad.INSTANCE);

    FreePath<MaybeKind.Witness, Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(run(result)).isEqualTo(a + b);
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("freePaths") FreePath<MaybeKind.Witness, Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    FreePath<MaybeKind.Witness, Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    FreePath<MaybeKind.Witness, Integer> composed =
        path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(run(stepByStep)).isEqualTo(run(composed));
  }

  @Property
  @Label("then discards first value correctly")
  void thenDiscardsFirstValue(
      @ForAll @IntRange(min = -100, max = 100) int first,
      @ForAll @IntRange(min = -100, max = 100) int second) {

    FreePath<MaybeKind.Witness, Integer> path =
        FreePath.pure(first, MaybeMonad.INSTANCE)
            .then(() -> FreePath.pure(second, MaybeMonad.INSTANCE));

    assertThat(run(path)).isEqualTo(second);
  }

  @Property
  @Label("toFree returns underlying free monad")
  void toFreeReturnsUnderlyingFree(@ForAll @IntRange(min = -100, max = 100) int value) {
    FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(value, MaybeMonad.INSTANCE);

    var free = path.toFree();
    Kind<MaybeKind.Witness, Integer> result = free.foldMap(IDENTITY_NAT, MAYBE_MONAD);

    assertThat(MAYBE.narrow(result).get()).isEqualTo(value);
  }

  @Property
  @Label("functor returns the functor instance")
  void functorReturnsInstance(@ForAll @IntRange(min = -100, max = 100) int value) {
    FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(value, MaybeMonad.INSTANCE);

    assertThat(path.functor()).isEqualTo(MaybeMonad.INSTANCE);
  }
}
