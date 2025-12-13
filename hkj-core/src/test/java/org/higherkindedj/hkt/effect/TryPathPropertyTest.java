// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for TryPath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs.
 */
class TryPathPropertyTest {

  @Provide
  Arbitrary<TryPath<Integer>> tryPaths() {
    Arbitrary<TryPath<Integer>> successes =
        Arbitraries.integers().between(-1000, 1000).map(Path::success);
    Arbitrary<TryPath<Integer>> failures =
        Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(20)
            .map(msg -> Path.failure(new RuntimeException(msg)));

    return Arbitraries.frequencyOf(Tuple.of(4, successes), Tuple.of(1, failures));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToStringFunctions() {
    return Arbitraries.of(
        (Function<Integer, String>) (i -> "value:" + i),
        (Function<Integer, String>) (i -> String.valueOf(i * 2)),
        (Function<Integer, String>) Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToIntFunctions() {
    return Arbitraries.of(
        (Function<String, Integer>) String::length,
        (Function<String, Integer>) String::hashCode,
        (Function<String, Integer>) (s -> s.isEmpty() ? 0 : 1));
  }

  @Provide
  Arbitrary<Function<Integer, TryPath<String>>> intToTryStringFunctions() {
    return Arbitraries.of(
        (Function<Integer, TryPath<String>>)
            (i ->
                i % 2 == 0 ? Path.success("even:" + i) : Path.failure(new RuntimeException("odd"))),
        (Function<Integer, TryPath<String>>)
            (i ->
                i > 0
                    ? Path.success("positive:" + i)
                    : Path.failure(new RuntimeException("not positive"))),
        (Function<Integer, TryPath<String>>) (i -> Path.success("value:" + i)));
  }

  @Provide
  Arbitrary<Function<String, TryPath<String>>> stringToTryStringFunctions() {
    return Arbitraries.of(
        (Function<String, TryPath<String>>)
            (s ->
                s.isEmpty()
                    ? Path.failure(new RuntimeException("empty"))
                    : Path.success(s.toUpperCase())),
        (Function<String, TryPath<String>>)
            (s ->
                s.length() > 3
                    ? Path.success("long:" + s)
                    : Path.failure(new RuntimeException("too short"))),
        (Function<String, TryPath<String>>) (s -> Path.success("transformed:" + s)));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("tryPaths") TryPath<Integer> path) {
    TryPath<Integer> result = path.map(Function.identity());

    if (path.run().isSuccess()) {
      assertThat(result.run()).isEqualTo(path.run());
    } else {
      assertThat(result.run().isFailure()).isTrue();
    }
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("tryPaths") TryPath<Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    TryPath<Integer> leftSide = path.map(f).map(g);
    TryPath<Integer> rightSide = path.map(f.andThen(g));

    if (path.run().isSuccess()) {
      assertThat(leftSide.run()).isEqualTo(rightSide.run());
    } else {
      assertThat(leftSide.run().isFailure()).isTrue();
      assertThat(rightSide.run().isFailure()).isTrue();
    }
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: Path.success(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToTryStringFunctions") Function<Integer, TryPath<String>> f) {

    TryPath<String> leftSide = Path.success(value).via(f);
    TryPath<String> rightSide = f.apply(value);

    if (rightSide.run().isSuccess()) {
      assertThat(leftSide.run()).isEqualTo(rightSide.run());
    } else {
      assertThat(leftSide.run().isFailure()).isEqualTo(rightSide.run().isFailure());
    }
  }

  @Property
  @Label("Monad Right Identity Law: path.via(Path::success) == path")
  void rightIdentityLaw(@ForAll("tryPaths") TryPath<Integer> path) {
    TryPath<Integer> result = path.via(Path::success);

    if (path.run().isSuccess()) {
      assertThat(result.run()).isEqualTo(path.run());
    } else {
      assertThat(result.run().isFailure()).isTrue();
    }
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("tryPaths") TryPath<Integer> path,
      @ForAll("intToTryStringFunctions") Function<Integer, TryPath<String>> f,
      @ForAll("stringToTryStringFunctions") Function<String, TryPath<String>> g) {

    TryPath<String> leftSide = path.via(f).via(g);
    TryPath<String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.run().isSuccess()).isEqualTo(rightSide.run().isSuccess());
    if (leftSide.run().isSuccess()) {
      assertThat(leftSide.run()).isEqualTo(rightSide.run());
    }
  }

  // ===== Derived Properties =====

  @Property
  @Label("map over Failure preserves the failure")
  void mapPreservesFailure(@ForAll("intToStringFunctions") Function<Integer, String> f) {
    RuntimeException error = new RuntimeException("test error");
    TryPath<Integer> failure = Path.failure(error);
    TryPath<String> result = failure.map(f);

    assertThat(result.run().isFailure()).isTrue();
  }

  @Property
  @Label("map over Success applies the function")
  void mapAppliesFunctionToSuccess(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    TryPath<Integer> success = Path.success(value);
    TryPath<String> result = success.map(f);

    assertThat(result.run().isSuccess()).isTrue();
    assertThat(result.getOrElse(null)).isEqualTo(f.apply(value));
  }

  @Property
  @Label("zipWith combines Success values")
  void zipWithCombinesSuccessValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    TryPath<Integer> pathA = Path.success(a);
    TryPath<Integer> pathB = Path.success(b);

    TryPath<Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.run().isSuccess()).isTrue();
    assertThat(result.getOrElse(null)).isEqualTo(a + b);
  }

  @Property
  @Label("zipWith returns Failure if either input is Failure")
  void zipWithReturnsFailureIfEitherIsFailure(
      @ForAll @IntRange(min = -100, max = 100) int value, @ForAll boolean firstIsFailure) {

    RuntimeException error = new RuntimeException("error");
    TryPath<Integer> pathA = firstIsFailure ? Path.failure(error) : Path.success(value);
    TryPath<Integer> pathB = firstIsFailure ? Path.success(value) : Path.failure(error);

    TryPath<Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.run().isFailure()).isTrue();
  }

  @Property
  @Label("recover provides fallback for Failure")
  void recoverProvidesFallbackForFailure(
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    TryPath<Integer> failure = Path.failure(new RuntimeException("error"));
    TryPath<Integer> result = failure.recover(e -> fallbackValue);

    assertThat(result.run().isSuccess()).isTrue();
    assertThat(result.getOrElse(null)).isEqualTo(fallbackValue);
  }

  @Property
  @Label("recover does not change Success")
  void recoverDoesNotChangeSuccess(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    TryPath<Integer> success = Path.success(value);
    TryPath<Integer> result = success.recover(e -> fallbackValue);

    assertThat(result.run().isSuccess()).isTrue();
    assertThat(result.getOrElse(null)).isEqualTo(value);
  }

  @Property
  @Label("mapException transforms the exception")
  void mapExceptionTransformsException() {
    RuntimeException original = new RuntimeException("original");
    TryPath<Integer> failure = Path.failure(original);
    TryPath<Integer> result =
        failure.mapException(e -> new IllegalStateException("wrapped: " + e.getMessage()));

    assertThat(result.run().isFailure()).isTrue();
    result
        .run()
        .fold(
            v -> null,
            e -> {
              assertThat(e).isInstanceOf(IllegalStateException.class);
              assertThat(e.getMessage()).contains("wrapped");
              return null;
            });
  }

  @Property(tries = 50)
  @Label("via and flatMap are equivalent")
  @SuppressWarnings("unchecked")
  void viaAndFlatMapEquivalent(
      @ForAll("tryPaths") TryPath<Integer> path,
      @ForAll("intToTryStringFunctions") Function<Integer, TryPath<String>> f) {

    TryPath<String> viaResult = path.via(f);
    // flatMap returns Chainable<B>, cast to TryPath since we know the implementation
    TryPath<String> flatMapResult = (TryPath<String>) path.flatMap(f);

    assertThat(viaResult.run().isSuccess()).isEqualTo(flatMapResult.run().isSuccess());
    if (viaResult.run().isSuccess()) {
      assertThat(viaResult.run()).isEqualTo(flatMapResult.run());
    }
  }
}
