// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;

/**
 * Property-based tests for GenericPath using jQwik.
 *
 * <p>These tests verify that GenericPath satisfies Functor and Monad laws when wrapping any monad
 * instance. We use MaybeMonad as the underlying monad for testing since it provides both success
 * and failure cases.
 */
class GenericPathPropertyTest {

  private static final Monad<MaybeKind.Witness> MAYBE_MONAD = MaybeMonad.INSTANCE;

  @Provide
  Arbitrary<GenericPath<MaybeKind.Witness, Integer>> genericPaths() {
    return Arbitraries.oneOf(
        // Just paths
        Arbitraries.integers()
            .between(-1000, 1000)
            .map(i -> GenericPath.of(MAYBE.widen(Maybe.just(i)), MAYBE_MONAD)),
        // Nothing paths
        Arbitraries.just(GenericPath.of(MAYBE.widen(Maybe.nothing()), MAYBE_MONAD)));
  }

  @Provide
  Arbitrary<GenericPath<MaybeKind.Witness, Integer>> justPaths() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .map(i -> GenericPath.of(MAYBE.widen(Maybe.just(i)), MAYBE_MONAD));
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
  Arbitrary<Function<Integer, GenericPath<MaybeKind.Witness, String>>>
      intToGenericPathStringFunctions() {
    return Arbitraries.of(
        i ->
            i % 2 == 0
                ? GenericPath.of(MAYBE.widen(Maybe.just("even:" + i)), MAYBE_MONAD)
                : GenericPath.of(MAYBE.widen(Maybe.nothing()), MAYBE_MONAD),
        i ->
            i > 0
                ? GenericPath.of(MAYBE.widen(Maybe.just("positive:" + i)), MAYBE_MONAD)
                : GenericPath.of(MAYBE.widen(Maybe.nothing()), MAYBE_MONAD),
        i -> GenericPath.of(MAYBE.widen(Maybe.just("value:" + i)), MAYBE_MONAD));
  }

  @Provide
  Arbitrary<Function<String, GenericPath<MaybeKind.Witness, String>>>
      stringToGenericPathStringFunctions() {
    return Arbitraries.of(
        s ->
            s.isEmpty()
                ? GenericPath.of(MAYBE.widen(Maybe.nothing()), MAYBE_MONAD)
                : GenericPath.of(MAYBE.widen(Maybe.just(s.toUpperCase())), MAYBE_MONAD),
        s ->
            s.length() > 3
                ? GenericPath.of(MAYBE.widen(Maybe.just("long:" + s)), MAYBE_MONAD)
                : GenericPath.of(MAYBE.widen(Maybe.nothing()), MAYBE_MONAD),
        s -> GenericPath.of(MAYBE.widen(Maybe.just("transformed:" + s)), MAYBE_MONAD));
  }

  // Helper to extract Maybe from GenericPath
  private <A> Maybe<A> extractMaybe(GenericPath<MaybeKind.Witness, A> path) {
    return MAYBE.narrow(path.runKind());
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("genericPaths") GenericPath<MaybeKind.Witness, Integer> path) {
    GenericPath<MaybeKind.Witness, Integer> result = path.map(Function.identity());
    assertThat(extractMaybe(result)).isEqualTo(extractMaybe(path));
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("genericPaths") GenericPath<MaybeKind.Witness, Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    GenericPath<MaybeKind.Witness, Integer> leftSide = path.map(f).map(g);
    GenericPath<MaybeKind.Witness, Integer> rightSide = path.map(f.andThen(g));

    assertThat(extractMaybe(leftSide)).isEqualTo(extractMaybe(rightSide));
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: GenericPath.pure(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToGenericPathStringFunctions")
          Function<Integer, GenericPath<MaybeKind.Witness, String>> f) {

    GenericPath<MaybeKind.Witness, String> leftSide = GenericPath.pure(value, MAYBE_MONAD).via(f);
    GenericPath<MaybeKind.Witness, String> rightSide = f.apply(value);

    assertThat(extractMaybe(leftSide)).isEqualTo(extractMaybe(rightSide));
  }

  @Property
  @Label("Monad Right Identity Law: path.via(a -> GenericPath.pure(a)) == path")
  void rightIdentityLaw(@ForAll("genericPaths") GenericPath<MaybeKind.Witness, Integer> path) {
    GenericPath<MaybeKind.Witness, Integer> result =
        path.via(a -> GenericPath.pure(a, MAYBE_MONAD));
    assertThat(extractMaybe(result)).isEqualTo(extractMaybe(path));
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("genericPaths") GenericPath<MaybeKind.Witness, Integer> path,
      @ForAll("intToGenericPathStringFunctions")
          Function<Integer, GenericPath<MaybeKind.Witness, String>> f,
      @ForAll("stringToGenericPathStringFunctions")
          Function<String, GenericPath<MaybeKind.Witness, String>> g) {

    GenericPath<MaybeKind.Witness, String> leftSide = path.via(f).via(g);
    GenericPath<MaybeKind.Witness, String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(extractMaybe(leftSide)).isEqualTo(extractMaybe(rightSide));
  }

  // ===== Combinable Properties =====

  @Property
  @Label("zipWith combines Just values correctly")
  void zipWithCombinesJustValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    GenericPath<MaybeKind.Witness, Integer> pathA =
        GenericPath.of(MAYBE.widen(Maybe.just(a)), MAYBE_MONAD);
    GenericPath<MaybeKind.Witness, Integer> pathB =
        GenericPath.of(MAYBE.widen(Maybe.just(b)), MAYBE_MONAD);

    GenericPath<MaybeKind.Witness, Integer> result = pathA.zipWith(pathB, Integer::sum);

    Maybe<Integer> resultMaybe = extractMaybe(result);
    assertThat(resultMaybe.isJust()).isTrue();
    assertThat(resultMaybe.get()).isEqualTo(a + b);
  }

  @Property
  @Label("zipWith returns Nothing when first is Nothing")
  void zipWithFirstNothing(@ForAll @IntRange(min = -100, max = 100) int b) {
    GenericPath<MaybeKind.Witness, Integer> pathA =
        GenericPath.of(MAYBE.widen(Maybe.nothing()), MAYBE_MONAD);
    GenericPath<MaybeKind.Witness, Integer> pathB =
        GenericPath.of(MAYBE.widen(Maybe.just(b)), MAYBE_MONAD);

    GenericPath<MaybeKind.Witness, Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(extractMaybe(result).isNothing()).isTrue();
  }

  @Property
  @Label("zipWith returns Nothing when second is Nothing")
  void zipWithSecondNothing(@ForAll @IntRange(min = -100, max = 100) int a) {
    GenericPath<MaybeKind.Witness, Integer> pathA =
        GenericPath.of(MAYBE.widen(Maybe.just(a)), MAYBE_MONAD);
    GenericPath<MaybeKind.Witness, Integer> pathB =
        GenericPath.of(MAYBE.widen(Maybe.nothing()), MAYBE_MONAD);

    GenericPath<MaybeKind.Witness, Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(extractMaybe(result).isNothing()).isTrue();
  }

  @Property
  @Label("zipWith3 combines three Just values correctly")
  void zipWith3CombinesThreeJustValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b,
      @ForAll @IntRange(min = -100, max = 100) int c) {

    GenericPath<MaybeKind.Witness, Integer> pathA =
        GenericPath.of(MAYBE.widen(Maybe.just(a)), MAYBE_MONAD);
    GenericPath<MaybeKind.Witness, Integer> pathB =
        GenericPath.of(MAYBE.widen(Maybe.just(b)), MAYBE_MONAD);
    GenericPath<MaybeKind.Witness, Integer> pathC =
        GenericPath.of(MAYBE.widen(Maybe.just(c)), MAYBE_MONAD);

    GenericPath<MaybeKind.Witness, Integer> result =
        pathA.zipWith3(pathB, pathC, (x, y, z) -> x + y + z);

    Maybe<Integer> resultMaybe = extractMaybe(result);
    assertThat(resultMaybe.isJust()).isTrue();
    assertThat(resultMaybe.get()).isEqualTo(a + b + c);
  }

  // ===== Map Properties =====

  @Property
  @Label("map over Just applies the function")
  void mapAppliesFunctionToJust(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    GenericPath<MaybeKind.Witness, Integer> path =
        GenericPath.of(MAYBE.widen(Maybe.just(value)), MAYBE_MONAD);
    GenericPath<MaybeKind.Witness, String> result = path.map(f);

    Maybe<String> resultMaybe = extractMaybe(result);
    assertThat(resultMaybe.isJust()).isTrue();
    assertThat(resultMaybe.get()).isEqualTo(f.apply(value));
  }

  @Property
  @Label("map over Nothing returns Nothing")
  void mapOverNothingReturnsNothing(@ForAll("intToStringFunctions") Function<Integer, String> f) {

    GenericPath<MaybeKind.Witness, Integer> path =
        GenericPath.of(MAYBE.widen(Maybe.nothing()), MAYBE_MONAD);
    GenericPath<MaybeKind.Witness, String> result = path.map(f);

    assertThat(extractMaybe(result).isNothing()).isTrue();
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("genericPaths") GenericPath<MaybeKind.Witness, Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    GenericPath<MaybeKind.Witness, Integer> stepByStep =
        path.map(addOne).map(doubleIt).map(subtract3);
    GenericPath<MaybeKind.Witness, Integer> composed =
        path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(extractMaybe(stepByStep)).isEqualTo(extractMaybe(composed));
  }

  // ===== Conversion Properties =====

  @Property
  @Label("toMaybePath correctly converts Just values")
  void toMaybePathConvertsJust(@ForAll @IntRange(min = -100, max = 100) int value) {
    GenericPath<MaybeKind.Witness, Integer> path =
        GenericPath.of(MAYBE.widen(Maybe.just(value)), MAYBE_MONAD);
    MaybePath<Integer> maybePath = path.toMaybePath(MAYBE::narrow);

    assertThat(maybePath.run().isJust()).isTrue();
    assertThat(maybePath.run().get()).isEqualTo(value);
  }

  @Property
  @Label("toMaybePath correctly converts Nothing")
  void toMaybePathConvertsNothing() {
    GenericPath<MaybeKind.Witness, Integer> path =
        GenericPath.of(MAYBE.widen(Maybe.nothing()), MAYBE_MONAD);
    MaybePath<Integer> maybePath = path.toMaybePath(MAYBE::narrow);

    assertThat(maybePath.run().isNothing()).isTrue();
  }

  // ===== then() Properties =====

  @Property
  @Label("then replaces Just value with supplier result")
  void thenReplacesJustValue(
      @ForAll @IntRange(min = -100, max = 100) int original,
      @ForAll @IntRange(min = -100, max = 100) int replacement) {

    GenericPath<MaybeKind.Witness, Integer> path =
        GenericPath.of(MAYBE.widen(Maybe.just(original)), MAYBE_MONAD);
    GenericPath<MaybeKind.Witness, Integer> result =
        path.then(() -> GenericPath.of(MAYBE.widen(Maybe.just(replacement)), MAYBE_MONAD));

    Maybe<Integer> resultMaybe = extractMaybe(result);
    assertThat(resultMaybe.isJust()).isTrue();
    assertThat(resultMaybe.get()).isEqualTo(replacement);
  }

  @Property
  @Label("then on Nothing returns Nothing")
  void thenOnNothingReturnsNothing(@ForAll @IntRange(min = -100, max = 100) int replacement) {
    GenericPath<MaybeKind.Witness, Integer> path =
        GenericPath.of(MAYBE.widen(Maybe.nothing()), MAYBE_MONAD);
    GenericPath<MaybeKind.Witness, Integer> result =
        path.then(() -> GenericPath.of(MAYBE.widen(Maybe.just(replacement)), MAYBE_MONAD));

    assertThat(extractMaybe(result).isNothing()).isTrue();
  }

  // ===== Equality Properties =====

  @Property
  @Label("GenericPath equals is reflexive")
  void equalsReflexive(@ForAll("genericPaths") GenericPath<MaybeKind.Witness, Integer> path) {
    assertThat(path).isEqualTo(path);
  }

  @Property
  @Label("GenericPath equals is symmetric")
  void equalsSymmetric(@ForAll @IntRange(min = -100, max = 100) int value) {
    GenericPath<MaybeKind.Witness, Integer> path1 =
        GenericPath.of(MAYBE.widen(Maybe.just(value)), MAYBE_MONAD);
    GenericPath<MaybeKind.Witness, Integer> path2 =
        GenericPath.of(MAYBE.widen(Maybe.just(value)), MAYBE_MONAD);

    assertThat(path1.equals(path2)).isEqualTo(path2.equals(path1));
  }

  @Property
  @Label("GenericPath hashCode is consistent with equals")
  void hashCodeConsistent(@ForAll @IntRange(min = -100, max = 100) int value) {
    GenericPath<MaybeKind.Witness, Integer> path1 =
        GenericPath.of(MAYBE.widen(Maybe.just(value)), MAYBE_MONAD);
    GenericPath<MaybeKind.Witness, Integer> path2 =
        GenericPath.of(MAYBE.widen(Maybe.just(value)), MAYBE_MONAD);

    if (path1.equals(path2)) {
      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }
  }

  // ===== pure() Factory Method Properties =====

  @Property
  @Label("GenericPath.pure creates Just containing the value")
  void pureCreatesJust(@ForAll @IntRange(min = -100, max = 100) int value) {
    GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(value, MAYBE_MONAD);

    Maybe<Integer> resultMaybe = extractMaybe(path);
    assertThat(resultMaybe.isJust()).isTrue();
    assertThat(resultMaybe.get()).isEqualTo(value);
  }
}
