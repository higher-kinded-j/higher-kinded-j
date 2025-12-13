// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for MaybePath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs.
 */
class MaybePathPropertyTest {

  @Provide
  Arbitrary<MaybePath<Integer>> maybePaths() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .injectNull(0.15)
        .map(i -> i == null ? Path.nothing() : Path.just(i));
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
  Arbitrary<Function<Integer, MaybePath<String>>> intToMaybeStringFunctions() {
    return Arbitraries.of(
        i -> i % 2 == 0 ? Path.just("even:" + i) : Path.nothing(),
        i -> i > 0 ? Path.just("positive:" + i) : Path.nothing(),
        i -> Path.just("value:" + i),
        i -> i == 0 ? Path.nothing() : Path.just("" + i));
  }

  @Provide
  Arbitrary<Function<String, MaybePath<String>>> stringToMaybeStringFunctions() {
    return Arbitraries.of(
        s -> s.isEmpty() ? Path.nothing() : Path.just(s.toUpperCase()),
        s -> s.length() > 3 ? Path.just("long:" + s) : Path.nothing(),
        s -> Path.just("transformed:" + s));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("maybePaths") MaybePath<Integer> path) {
    MaybePath<Integer> result = path.map(Function.identity());
    assertThat(result.run()).isEqualTo(path.run());
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("maybePaths") MaybePath<Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    MaybePath<Integer> leftSide = path.map(f).map(g);
    MaybePath<Integer> rightSide = path.map(f.andThen(g));

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: Path.just(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToMaybeStringFunctions") Function<Integer, MaybePath<String>> f) {

    MaybePath<String> leftSide = Path.just(value).via(f);
    MaybePath<String> rightSide = f.apply(value);

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  @Property
  @Label("Monad Right Identity Law: path.via(Path::just) == path")
  void rightIdentityLaw(@ForAll("maybePaths") MaybePath<Integer> path) {
    MaybePath<Integer> result = path.via(Path::just);
    assertThat(result.run()).isEqualTo(path.run());
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("maybePaths") MaybePath<Integer> path,
      @ForAll("intToMaybeStringFunctions") Function<Integer, MaybePath<String>> f,
      @ForAll("stringToMaybeStringFunctions") Function<String, MaybePath<String>> g) {

    MaybePath<String> leftSide = path.via(f).via(g);
    MaybePath<String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  // ===== Derived Properties =====

  @Property
  @Label("map over Nothing always returns Nothing")
  void mapPreservesNothing(@ForAll("intToStringFunctions") Function<Integer, String> f) {
    MaybePath<Integer> nothing = Path.nothing();
    MaybePath<String> result = nothing.map(f);
    assertThat(result.run().isNothing()).isTrue();
  }

  @Property
  @Label("via over Nothing always returns Nothing")
  void viaPreservesNothing(
      @ForAll("intToMaybeStringFunctions") Function<Integer, MaybePath<String>> f) {
    MaybePath<Integer> nothing = Path.nothing();
    MaybePath<String> result = nothing.via(f);
    assertThat(result.run().isNothing()).isTrue();
  }

  @Property
  @Label("map over Just applies the function")
  void mapAppliesFunctionToJust(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    MaybePath<Integer> just = Path.just(value);
    MaybePath<String> result = just.map(f);

    assertThat(result.run().isJust()).isTrue();
    assertThat(result.run().get()).isEqualTo(f.apply(value));
  }

  @Property
  @Label("zipWith combines Just values")
  void zipWithCombinesJustValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    MaybePath<Integer> pathA = Path.just(a);
    MaybePath<Integer> pathB = Path.just(b);

    MaybePath<Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.run().isJust()).isTrue();
    assertThat(result.run().get()).isEqualTo(a + b);
  }

  @Property
  @Label("zipWith returns Nothing if either input is Nothing")
  void zipWithReturnsNothingIfEitherIsNothing(
      @ForAll @IntRange(min = -100, max = 100) int value, @ForAll boolean firstIsNothing) {

    MaybePath<Integer> pathA = firstIsNothing ? Path.nothing() : Path.just(value);
    MaybePath<Integer> pathB = firstIsNothing ? Path.just(value) : Path.nothing();

    MaybePath<Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.run().isNothing()).isTrue();
  }

  @Property
  @Label("recover provides fallback for Nothing")
  void recoverProvidesFallbackForNothing(
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    MaybePath<Integer> nothing = Path.nothing();
    MaybePath<Integer> result = nothing.recover(unit -> fallbackValue);

    assertThat(result.run().isJust()).isTrue();
    assertThat(result.run().get()).isEqualTo(fallbackValue);
  }

  @Property
  @Label("recover does not change Just")
  void recoverDoesNotChangeJust(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    MaybePath<Integer> just = Path.just(value);
    MaybePath<Integer> result = just.recover(unit -> fallbackValue);

    assertThat(result.run().isJust()).isTrue();
    assertThat(result.run().get()).isEqualTo(value);
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("maybePaths") MaybePath<Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    MaybePath<Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    MaybePath<Integer> composed = path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(stepByStep.run()).isEqualTo(composed.run());
  }

  @Property(tries = 50)
  @Label("via and flatMap are equivalent")
  @SuppressWarnings("unchecked")
  void viaAndFlatMapEquivalent(
      @ForAll("maybePaths") MaybePath<Integer> path,
      @ForAll("intToMaybeStringFunctions") Function<Integer, MaybePath<String>> f) {

    MaybePath<String> viaResult = path.via(f);
    // flatMap returns Chainable<B>, cast to MaybePath since we know the implementation
    MaybePath<String> flatMapResult = (MaybePath<String>) path.flatMap(f);

    assertThat(viaResult.run()).isEqualTo(flatMapResult.run());
  }
}
