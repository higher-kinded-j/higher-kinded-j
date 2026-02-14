// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

/**
 * Property-based tests for OptionalPath using jQwik.
 *
 * <p>OptionalPath wraps Java's Optional and can be either present (with a value) or absent (empty).
 * These tests verify that Functor and Monad laws hold for all cases.
 */
class OptionalPathPropertyTest {

  @Provide
  Arbitrary<OptionalPath<Integer>> optionalPaths() {
    return Arbitraries.oneOf(
        // Present paths
        Arbitraries.integers().between(-1000, 1000).map(Path::present),
        // Absent paths
        Arbitraries.just(Path.absent()));
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
  Arbitrary<Function<Integer, OptionalPath<String>>> intToOptionalPathStringFunctions() {
    return Arbitraries.of(
        i -> i % 2 == 0 ? Path.present("even:" + i) : Path.absent(),
        i -> i > 0 ? Path.present("positive:" + i) : Path.absent(),
        i -> Path.present("value:" + i),
        i -> i == 0 ? Path.absent() : Path.present("" + i));
  }

  @Provide
  Arbitrary<Function<String, OptionalPath<String>>> stringToOptionalPathStringFunctions() {
    return Arbitraries.of(
        s -> s.isEmpty() ? Path.absent() : Path.present(s.toUpperCase()),
        s -> s.length() > 3 ? Path.present("long:" + s) : Path.absent(),
        s -> Path.present("transformed:" + s));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("optionalPaths") OptionalPath<Integer> path) {
    OptionalPath<Integer> result = path.map(Function.identity());
    assertThat(result.run()).isEqualTo(path.run());
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("optionalPaths") OptionalPath<Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    OptionalPath<Integer> leftSide = path.map(f).map(g);
    OptionalPath<Integer> rightSide = path.map(f.andThen(g));

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: Path.present(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToOptionalPathStringFunctions") Function<Integer, OptionalPath<String>> f) {

    OptionalPath<String> leftSide = Path.present(value).via(f);
    OptionalPath<String> rightSide = f.apply(value);

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  @Property
  @Label("Monad Right Identity Law: path.via(a -> Path.present(a)) == path")
  void rightIdentityLaw(@ForAll("optionalPaths") OptionalPath<Integer> path) {
    OptionalPath<Integer> result = path.via(Path::present);
    assertThat(result.run()).isEqualTo(path.run());
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("optionalPaths") OptionalPath<Integer> path,
      @ForAll("intToOptionalPathStringFunctions") Function<Integer, OptionalPath<String>> f,
      @ForAll("stringToOptionalPathStringFunctions") Function<String, OptionalPath<String>> g) {

    OptionalPath<String> leftSide = path.via(f).via(g);
    OptionalPath<String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  // ===== Short-Circuit Properties =====

  @Property
  @Label("via short-circuits on absent")
  void viaShortCircuitsOnAbsent() {
    OptionalPath<Integer> absent = Path.absent();

    // via should not execute the function, just return absent
    OptionalPath<String> result = absent.via(i -> Path.present("should not be called"));

    assertThat(result.isEmpty()).isTrue();
  }

  @Property
  @Label("then short-circuits on absent")
  void thenShortCircuitsOnAbsent() {
    OptionalPath<Integer> absent = Path.absent();

    // then should not execute the supplier, just return absent
    OptionalPath<String> result = absent.then(() -> Path.present("should not be called"));

    assertThat(result.isEmpty()).isTrue();
  }

  // ===== Combinable Properties =====

  @Property
  @Label("zipWith combines present values")
  void zipWithCombinesPresentValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    OptionalPath<Integer> pathA = Path.present(a);
    OptionalPath<Integer> pathB = Path.present(b);

    OptionalPath<Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.isPresent()).isTrue();
    assertThat(result.run().get()).isEqualTo(a + b);
  }

  @Property
  @Label("zipWith returns absent when first is absent")
  void zipWithReturnsAbsentForFirstAbsent(@ForAll @IntRange(min = -100, max = 100) int b) {
    OptionalPath<Integer> pathA = Path.absent();
    OptionalPath<Integer> pathB = Path.present(b);

    OptionalPath<Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.isEmpty()).isTrue();
  }

  @Property
  @Label("zipWith returns absent when second is absent")
  void zipWithReturnsAbsentForSecondAbsent(@ForAll @IntRange(min = -100, max = 100) int a) {
    OptionalPath<Integer> pathA = Path.present(a);
    OptionalPath<Integer> pathB = Path.absent();

    OptionalPath<Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.isEmpty()).isTrue();
  }

  @Property
  @Label("zipWith returns absent when both are absent")
  void zipWithReturnsAbsentForBothAbsent() {
    OptionalPath<Integer> pathA = Path.absent();
    OptionalPath<Integer> pathB = Path.absent();

    OptionalPath<Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.isEmpty()).isTrue();
  }

  @Property
  @Label("zipWith3 combines three present values")
  void zipWith3CombinesThreePresentValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b,
      @ForAll @IntRange(min = -100, max = 100) int c) {

    OptionalPath<Integer> pathA = Path.present(a);
    OptionalPath<Integer> pathB = Path.present(b);
    OptionalPath<Integer> pathC = Path.present(c);

    OptionalPath<Integer> result = pathA.zipWith3(pathB, pathC, (x, y, z) -> x + y + z);

    assertThat(result.isPresent()).isTrue();
    assertThat(result.run().get()).isEqualTo(a + b + c);
  }

  @Property
  @Label("zipWith3 returns absent when any is absent")
  void zipWith3ReturnsAbsentWhenAnyAbsent(
      @ForAll @IntRange(min = 0, max = 2) int absentIndex,
      @ForAll @IntRange(min = -100, max = 100) int value1,
      @ForAll @IntRange(min = -100, max = 100) int value2) {

    OptionalPath<Integer> pathA = absentIndex == 0 ? Path.absent() : Path.present(value1);
    OptionalPath<Integer> pathB = absentIndex == 1 ? Path.absent() : Path.present(value2);
    OptionalPath<Integer> pathC = absentIndex == 2 ? Path.absent() : Path.present(value1);

    OptionalPath<Integer> result = pathA.zipWith3(pathB, pathC, (x, y, z) -> x + y + z);

    assertThat(result.isEmpty()).isTrue();
  }

  // ===== Filtering Properties =====

  @Property
  @Label("filter keeps values that match predicate")
  void filterKeepsMatchingValues(@ForAll @IntRange(min = 1, max = 100) int positiveValue) {
    OptionalPath<Integer> path = Path.present(positiveValue);
    OptionalPath<Integer> result = path.filter(i -> i > 0);

    assertThat(result.isPresent()).isTrue();
    assertThat(result.run().get()).isEqualTo(positiveValue);
  }

  @Property
  @Label("filter removes values that don't match predicate")
  void filterRemovesNonMatchingValues(@ForAll @IntRange(min = -100, max = 0) int nonPositiveValue) {
    OptionalPath<Integer> path = Path.present(nonPositiveValue);
    OptionalPath<Integer> result = path.filter(i -> i > 0);

    assertThat(result.isEmpty()).isTrue();
  }

  @Property
  @Label("filter on absent returns absent")
  void filterOnAbsentReturnsAbsent() {
    OptionalPath<Integer> absent = Path.absent();
    OptionalPath<Integer> result = absent.filter(i -> true);

    assertThat(result.isEmpty()).isTrue();
  }

  // ===== Recovery Properties =====

  @Property
  @Label("orElsePath provides fallback for absent")
  void orElsePathProvidesFallbackForAbsent(
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {
    OptionalPath<Integer> absent = Path.absent();
    OptionalPath<Integer> result = absent.orElsePath(fallbackValue);

    assertThat(result.isPresent()).isTrue();
    assertThat(result.run().get()).isEqualTo(fallbackValue);
  }

  @Property
  @Label("orElsePath does not change present")
  void orElsePathDoesNotChangePresent(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    OptionalPath<Integer> present = Path.present(value);
    OptionalPath<Integer> result = present.orElsePath(fallbackValue);

    assertThat(result.isPresent()).isTrue();
    assertThat(result.run().get()).isEqualTo(value);
  }

  @Property
  @Label("orElsePathGet provides lazy fallback for absent")
  void orElsePathGetProvidesLazyFallbackForAbsent(
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {
    OptionalPath<Integer> absent = Path.absent();
    OptionalPath<Integer> result = absent.orElsePathGet(() -> Path.present(fallbackValue));

    assertThat(result.isPresent()).isTrue();
    assertThat(result.run().get()).isEqualTo(fallbackValue);
  }

  @Property
  @Label("getOrElse returns value for present")
  void getOrElseReturnsValueForPresent(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @IntRange(min = -100, max = 100) int defaultValue) {

    OptionalPath<Integer> present = Path.present(value);
    Integer result = present.getOrElse(defaultValue);

    assertThat(result).isEqualTo(value);
  }

  @Property
  @Label("getOrElse returns default for absent")
  void getOrElseReturnsDefaultForAbsent(@ForAll @IntRange(min = -100, max = 100) int defaultValue) {
    OptionalPath<Integer> absent = Path.absent();
    Integer result = absent.getOrElse(defaultValue);

    assertThat(result).isEqualTo(defaultValue);
  }

  // ===== Conversion Properties =====

  @Property
  @Label("toMaybePath produces Just for present")
  void toMaybePathProducesJustForPresent(@ForAll @IntRange(min = -100, max = 100) int value) {
    OptionalPath<Integer> path = Path.present(value);
    MaybePath<Integer> maybe = path.toMaybePath();

    assertThat(maybe.run().isJust()).isTrue();
    assertThat(maybe.run().get()).isEqualTo(value);
  }

  @Property
  @Label("toMaybePath produces Nothing for absent")
  void toMaybePathProducesNothingForAbsent() {
    OptionalPath<Integer> absent = Path.absent();
    MaybePath<Integer> maybe = absent.toMaybePath();

    assertThat(maybe.run().isNothing()).isTrue();
  }

  @Property
  @Label("toEitherPath produces Right for present")
  void toEitherPathProducesRightForPresent(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @StringLength(min = 1, max = 10) String error) {

    OptionalPath<Integer> path = Path.present(value);
    EitherPath<String, Integer> either = path.toEitherPath(error);

    assertThat(either.run().isRight()).isTrue();
    assertThat(either.run().getRight()).isEqualTo(value);
  }

  @Property
  @Label("toEitherPath produces Left for absent")
  void toEitherPathProducesLeftForAbsent(@ForAll @StringLength(min = 1, max = 10) String error) {
    OptionalPath<Integer> absent = Path.absent();
    EitherPath<String, Integer> either = absent.toEitherPath(error);

    assertThat(either.run().isLeft()).isTrue();
    assertThat(either.run().getLeft()).isEqualTo(error);
  }

  // ===== Map Properties =====

  @Property
  @Label("map over absent returns absent")
  void mapPreservesAbsent(@ForAll("intToStringFunctions") Function<Integer, String> f) {
    OptionalPath<Integer> absent = Path.absent();
    OptionalPath<String> result = absent.map(f);

    assertThat(result.isEmpty()).isTrue();
  }

  @Property
  @Label("map over present applies the function")
  void mapAppliesFunctionToPresent(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    OptionalPath<Integer> present = Path.present(value);
    OptionalPath<String> result = present.map(f);

    assertThat(result.isPresent()).isTrue();
    assertThat(result.run().get()).isEqualTo(f.apply(value));
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("optionalPaths") OptionalPath<Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    OptionalPath<Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    OptionalPath<Integer> composed =
        path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(stepByStep.run()).isEqualTo(composed.run());
  }

  // ===== Equality Properties =====

  @Property
  @Label("OptionalPath equals is reflexive")
  void equalsReflexive(@ForAll("optionalPaths") OptionalPath<Integer> path) {
    assertThat(path).isEqualTo(path);
  }

  @Property
  @Label("OptionalPath equals is symmetric")
  void equalsSymmetric(@ForAll @IntRange(min = -100, max = 100) int value) {
    OptionalPath<Integer> path1 = Path.present(value);
    OptionalPath<Integer> path2 = Path.present(value);

    assertThat(path1.equals(path2)).isEqualTo(path2.equals(path1));
  }

  @Property
  @Label("All absent paths are equal")
  void allAbsentPathsAreEqual() {
    OptionalPath<Integer> absent1 = Path.absent();
    OptionalPath<String> absent2 = Path.absent();

    assertThat(absent1.run()).isEqualTo(absent2.run());
    assertThat(absent1.run()).isEqualTo(Optional.empty());
  }

  @Property
  @Label("OptionalPath hashCode is consistent with equals")
  void hashCodeConsistent(@ForAll @IntRange(min = -100, max = 100) int value) {
    OptionalPath<Integer> path1 = Path.present(value);
    OptionalPath<Integer> path2 = Path.present(value);

    if (path1.equals(path2)) {
      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }
  }
}
