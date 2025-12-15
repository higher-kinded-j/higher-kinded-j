// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

/**
 * Property-based tests for IdPath using jQwik.
 *
 * <p>IdPath is the simplest path type - it always contains a value and never fails. These tests
 * verify that Functor and Monad laws hold for all inputs.
 */
class IdPathPropertyTest {

  @Provide
  Arbitrary<IdPath<Integer>> idPaths() {
    return Arbitraries.integers().between(-1000, 1000).map(Path::id);
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
  Arbitrary<Function<Integer, IdPath<String>>> intToIdPathStringFunctions() {
    return Arbitraries.of(
        i -> Path.id("value:" + i),
        i -> Path.id(String.valueOf(i * 2)),
        i -> Path.id(i >= 0 ? "positive" : "negative"));
  }

  @Provide
  Arbitrary<Function<String, IdPath<String>>> stringToIdPathStringFunctions() {
    return Arbitraries.of(
        s -> Path.id(s.toUpperCase()),
        s -> Path.id("transformed:" + s),
        s -> Path.id(s.length() > 3 ? "long" : "short"));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("idPaths") IdPath<Integer> path) {
    IdPath<Integer> result = path.map(Function.identity());
    assertThat(result.get()).isEqualTo(path.get());
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("idPaths") IdPath<Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    IdPath<Integer> leftSide = path.map(f).map(g);
    IdPath<Integer> rightSide = path.map(f.andThen(g));

    assertThat(leftSide.get()).isEqualTo(rightSide.get());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: Path.id(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToIdPathStringFunctions") Function<Integer, IdPath<String>> f) {

    IdPath<String> leftSide = Path.id(value).via(f);
    IdPath<String> rightSide = f.apply(value);

    assertThat(leftSide.get()).isEqualTo(rightSide.get());
  }

  @Property
  @Label("Monad Right Identity Law: path.via(a -> Path.id(a)) == path")
  void rightIdentityLaw(@ForAll("idPaths") IdPath<Integer> path) {
    IdPath<Integer> result = path.via(Path::id);
    assertThat(result.get()).isEqualTo(path.get());
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("idPaths") IdPath<Integer> path,
      @ForAll("intToIdPathStringFunctions") Function<Integer, IdPath<String>> f,
      @ForAll("stringToIdPathStringFunctions") Function<String, IdPath<String>> g) {

    IdPath<String> leftSide = path.via(f).via(g);
    IdPath<String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.get()).isEqualTo(rightSide.get());
  }

  // ===== Combinable Properties =====

  @Property
  @Label("zipWith combines values correctly")
  void zipWithCombinesValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    IdPath<Integer> pathA = Path.id(a);
    IdPath<Integer> pathB = Path.id(b);

    IdPath<Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.get()).isEqualTo(a + b);
  }

  @Property
  @Label("zipWith3 combines three values correctly")
  void zipWith3CombinesThreeValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b,
      @ForAll @IntRange(min = -100, max = 100) int c) {

    IdPath<Integer> pathA = Path.id(a);
    IdPath<Integer> pathB = Path.id(b);
    IdPath<Integer> pathC = Path.id(c);

    IdPath<Integer> result = pathA.zipWith3(pathB, pathC, (x, y, z) -> x + y + z);

    assertThat(result.get()).isEqualTo(a + b + c);
  }

  @Property
  @Label("zipWith is associative with sum combiner")
  void zipWithAssociativity(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b,
      @ForAll @IntRange(min = -100, max = 100) int c) {

    IdPath<Integer> pathA = Path.id(a);
    IdPath<Integer> pathB = Path.id(b);
    IdPath<Integer> pathC = Path.id(c);

    // (a + b) + c
    IdPath<Integer> leftSide = pathA.zipWith(pathB, Integer::sum).zipWith(pathC, Integer::sum);
    // a + (b + c)
    IdPath<Integer> rightSide = pathA.zipWith(pathB.zipWith(pathC, Integer::sum), Integer::sum);

    assertThat(leftSide.get()).isEqualTo(rightSide.get());
  }

  // ===== Map Properties =====

  @Property
  @Label("map always succeeds for IdPath")
  void mapAlwaysSucceeds(
      @ForAll @IntRange(min = -1000, max = 1000) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    IdPath<Integer> path = Path.id(value);
    IdPath<String> result = path.map(f);

    assertThat(result.get()).isEqualTo(f.apply(value));
  }

  @Property
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("idPaths") IdPath<Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    IdPath<Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    IdPath<Integer> composed = path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(stepByStep.get()).isEqualTo(composed.get());
  }

  // ===== Conversion Properties =====

  @Property
  @Label("toMaybePath produces Just for non-null values")
  void toMaybePathProducesJust(@ForAll @IntRange(min = -100, max = 100) int value) {
    IdPath<Integer> path = Path.id(value);
    MaybePath<Integer> maybe = path.toMaybePath();

    assertThat(maybe.run().isJust()).isTrue();
    assertThat(maybe.run().get()).isEqualTo(value);
  }

  @Property
  @Label("toEitherPath produces Right")
  void toEitherPathProducesRight(@ForAll @IntRange(min = -100, max = 100) int value) {
    IdPath<Integer> path = Path.id(value);
    EitherPath<String, Integer> either = path.toEitherPath();

    assertThat(either.run().isRight()).isTrue();
    assertThat(either.run().getRight()).isEqualTo(value);
  }

  // ===== then() Properties =====

  @Property
  @Label("then replaces value with supplier result")
  void thenReplacesValue(
      @ForAll @IntRange(min = -100, max = 100) int original,
      @ForAll @StringLength(min = 1, max = 10) String replacement) {

    IdPath<Integer> path = Path.id(original);
    IdPath<String> result = path.then(() -> Path.id(replacement));

    assertThat(result.get()).isEqualTo(replacement);
  }

  // ===== Equality Properties =====

  @Property
  @Label("IdPath equals is reflexive")
  void equalsReflexive(@ForAll("idPaths") IdPath<Integer> path) {
    assertThat(path).isEqualTo(path);
  }

  @Property
  @Label("IdPath equals is symmetric")
  void equalsSymmetric(@ForAll @IntRange(min = -100, max = 100) int value) {
    IdPath<Integer> path1 = Path.id(value);
    IdPath<Integer> path2 = Path.id(value);

    assertThat(path1.equals(path2)).isEqualTo(path2.equals(path1));
  }

  @Property
  @Label("IdPath hashCode is consistent with equals")
  void hashCodeConsistent(@ForAll @IntRange(min = -100, max = 100) int value) {
    IdPath<Integer> path1 = Path.id(value);
    IdPath<Integer> path2 = Path.id(value);

    if (path1.equals(path2)) {
      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }
  }
}
