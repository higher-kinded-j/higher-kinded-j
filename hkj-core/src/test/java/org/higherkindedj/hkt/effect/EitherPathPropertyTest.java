// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for EitherPath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs.
 */
class EitherPathPropertyTest {

  @Provide
  Arbitrary<EitherPath<String, Integer>> eitherPaths() {
    Arbitrary<EitherPath<String, Integer>> rights =
        Arbitraries.integers().between(-1000, 1000).map(Path::right);
    Arbitrary<EitherPath<String, Integer>> lefts =
        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).map(Path::left);

    return Arbitraries.frequencyOf(Tuple.of(4, rights), Tuple.of(1, lefts));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToStringFunctions() {
    return Arbitraries.of(i -> "value:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToIntFunctions() {
    return Arbitraries.of(String::length, String::hashCode, s -> s.isEmpty() ? 0 : 1);
  }

  @Provide
  Arbitrary<Function<Integer, EitherPath<String, String>>> intToEitherStringFunctions() {
    return Arbitraries.of(
        i -> i % 2 == 0 ? Path.right("even:" + i) : Path.left("odd"),
        i -> i > 0 ? Path.right("positive:" + i) : Path.left("not positive"),
        i -> Path.right("value:" + i));
  }

  @Provide
  Arbitrary<Function<String, EitherPath<String, String>>> stringToEitherStringFunctions() {
    return Arbitraries.of(
        s -> s.isEmpty() ? Path.left("empty") : Path.right(s.toUpperCase()),
        s -> s.length() > 3 ? Path.right("long:" + s) : Path.left("too short"),
        s -> Path.right("transformed:" + s));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("eitherPaths") EitherPath<String, Integer> path) {
    EitherPath<String, Integer> result = path.map(Function.identity());
    assertThat(result.run()).isEqualTo(path.run());
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("eitherPaths") EitherPath<String, Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    EitherPath<String, Integer> leftSide = path.map(f).map(g);
    EitherPath<String, Integer> rightSide = path.map(f.andThen(g));

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: Path.right(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToEitherStringFunctions") Function<Integer, EitherPath<String, String>> f) {

    EitherPath<String, String> leftSide = Path.<String, Integer>right(value).via(f);
    EitherPath<String, String> rightSide = f.apply(value);

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  @Property
  @Label("Monad Right Identity Law: path.via(Path::right) == path")
  void rightIdentityLaw(@ForAll("eitherPaths") EitherPath<String, Integer> path) {
    EitherPath<String, Integer> result = path.via(Path::right);
    assertThat(result.run()).isEqualTo(path.run());
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("eitherPaths") EitherPath<String, Integer> path,
      @ForAll("intToEitherStringFunctions") Function<Integer, EitherPath<String, String>> f,
      @ForAll("stringToEitherStringFunctions") Function<String, EitherPath<String, String>> g) {

    EitherPath<String, String> leftSide = path.via(f).via(g);
    EitherPath<String, String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  // ===== Derived Properties =====

  @Property
  @Label("map over Left preserves the error")
  void mapPreservesLeft(
      @ForAll @IntRange(min = 1, max = 10) int errorLength,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    String error = "e".repeat(errorLength);
    EitherPath<String, Integer> left = Path.left(error);
    EitherPath<String, String> result = left.map(f);

    assertThat(result.run().isLeft()).isTrue();
    assertThat(result.run().getLeft()).isEqualTo(error);
  }

  @Property
  @Label("map over Right applies the function")
  void mapAppliesFunctionToRight(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    EitherPath<String, Integer> right = Path.right(value);
    EitherPath<String, String> result = right.map(f);

    assertThat(result.run().isRight()).isTrue();
    assertThat(result.run().getRight()).isEqualTo(f.apply(value));
  }

  @Property
  @Label("zipWith combines Right values")
  void zipWithCombinesRightValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    EitherPath<String, Integer> pathA = Path.right(a);
    EitherPath<String, Integer> pathB = Path.right(b);

    EitherPath<String, Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.run().isRight()).isTrue();
    assertThat(result.run().getRight()).isEqualTo(a + b);
  }

  @Property
  @Label("zipWith returns first Left if either input is Left")
  void zipWithReturnsFirstLeft(
      @ForAll @IntRange(min = -100, max = 100) int value, @ForAll boolean firstIsLeft) {

    String error = "error";
    EitherPath<String, Integer> pathA = firstIsLeft ? Path.left(error) : Path.right(value);
    EitherPath<String, Integer> pathB = firstIsLeft ? Path.right(value) : Path.left(error);

    EitherPath<String, Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.run().isLeft()).isTrue();
    assertThat(result.run().getLeft()).isEqualTo(error);
  }

  @Property
  @Label("recover provides fallback for Left")
  void recoverProvidesFallbackForLeft(@ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    EitherPath<String, Integer> left = Path.left("error");
    EitherPath<String, Integer> result = left.recover(e -> fallbackValue);

    assertThat(result.run().isRight()).isTrue();
    assertThat(result.run().getRight()).isEqualTo(fallbackValue);
  }

  @Property
  @Label("recover does not change Right")
  void recoverDoesNotChangeRight(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    EitherPath<String, Integer> right = Path.right(value);
    EitherPath<String, Integer> result = right.recover(e -> fallbackValue);

    assertThat(result.run().isRight()).isTrue();
    assertThat(result.run().getRight()).isEqualTo(value);
  }

  @Property
  @Label("mapError transforms the error type")
  void mapErrorTransformsError(@ForAll @IntRange(min = 1, max = 10) int errorLength) {

    String error = "e".repeat(errorLength);
    EitherPath<String, Integer> left = Path.left(error);
    EitherPath<Integer, Integer> result = left.mapError(String::length);

    assertThat(result.run().isLeft()).isTrue();
    assertThat(result.run().getLeft()).isEqualTo(errorLength);
  }

  @Property
  @Label("swap converts Right to Left and vice versa")
  void swapConvertsRightAndLeft(@ForAll("eitherPaths") EitherPath<String, Integer> path) {
    EitherPath<Integer, String> swapped = path.swap();

    if (path.run().isRight()) {
      assertThat(swapped.run().isLeft()).isTrue();
      assertThat(swapped.run().getLeft()).isEqualTo(path.run().getRight());
    } else {
      assertThat(swapped.run().isRight()).isTrue();
      assertThat(swapped.run().getRight()).isEqualTo(path.run().getLeft());
    }
  }

  @Property(tries = 50)
  @Label("via and flatMap are equivalent")
  @SuppressWarnings("unchecked")
  void viaAndFlatMapEquivalent(
      @ForAll("eitherPaths") EitherPath<String, Integer> path,
      @ForAll("intToEitherStringFunctions") Function<Integer, EitherPath<String, String>> f) {

    EitherPath<String, String> viaResult = path.via(f);
    // flatMap returns Chainable<B>, cast to EitherPath since we know the implementation
    EitherPath<String, String> flatMapResult = (EitherPath<String, String>) path.flatMap(f);

    assertThat(viaResult.run()).isEqualTo(flatMapResult.run());
  }
}
