// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for LazyPath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs. LazyPath represents
 * deferred computations that are memoized on first evaluation.
 */
@Label("LazyPath Property-Based Tests")
class LazyPathPropertyTest {

  @Provide
  Arbitrary<LazyPath<Integer>> lazyPaths() {
    return Arbitraries.oneOf(
        // Pure values
        Arbitraries.integers().between(-1000, 1000).map(LazyPath::now),
        // Deferred computations
        Arbitraries.integers().between(-1000, 1000).map(i -> LazyPath.defer(() -> i)),
        // Computed values
        Arbitraries.integers().between(-1000, 1000).map(i -> LazyPath.defer(() -> i * 2)));
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
  Arbitrary<Function<Integer, LazyPath<String>>> intToLazyStringFunctions() {
    return Arbitraries.of(
        i -> LazyPath.now("value:" + i),
        i -> LazyPath.defer(() -> "deferred:" + i),
        i -> LazyPath.defer(() -> i > 0 ? "positive" : "non-positive"));
  }

  @Provide
  Arbitrary<Function<String, LazyPath<String>>> stringToLazyStringFunctions() {
    return Arbitraries.of(
        s -> LazyPath.now(s.toUpperCase()),
        s -> LazyPath.defer(() -> s + "!"),
        s -> LazyPath.defer(() -> s.isEmpty() ? "empty" : s));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("lazyPaths") LazyPath<Integer> path) {
    LazyPath<Integer> result = path.map(Function.identity());
    assertThat(result.get()).isEqualTo(path.get());
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("lazyPaths") LazyPath<Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    LazyPath<Integer> leftSide = path.map(f).map(g);
    LazyPath<Integer> rightSide = path.map(f.andThen(g));

    assertThat(leftSide.get()).isEqualTo(rightSide.get());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: LazyPath.now(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToLazyStringFunctions") Function<Integer, LazyPath<String>> f) {

    LazyPath<String> leftSide = LazyPath.now(value).via(f);
    LazyPath<String> rightSide = f.apply(value);

    assertThat(leftSide.get()).isEqualTo(rightSide.get());
  }

  @Property
  @Label("Monad Right Identity Law: path.via(LazyPath::now) == path")
  void rightIdentityLaw(@ForAll("lazyPaths") LazyPath<Integer> path) {
    LazyPath<Integer> result = path.via(LazyPath::now);
    assertThat(result.get()).isEqualTo(path.get());
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("lazyPaths") LazyPath<Integer> path,
      @ForAll("intToLazyStringFunctions") Function<Integer, LazyPath<String>> f,
      @ForAll("stringToLazyStringFunctions") Function<String, LazyPath<String>> g) {

    LazyPath<String> leftSide = path.via(f).via(g);
    LazyPath<String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.get()).isEqualTo(rightSide.get());
  }

  // ===== Derived Properties =====

  @Property
  @Label("pure creates immediately evaluated lazy value")
  void pureCreatesImmediateValue(@ForAll @IntRange(min = -100, max = 100) int value) {
    LazyPath<Integer> path = LazyPath.now(value);
    assertThat(path.get()).isEqualTo(value);
  }

  @Property
  @Label("defer creates deferred computation")
  void deferCreatesDeferred(@ForAll @IntRange(min = -100, max = 100) int value) {
    AtomicInteger counter = new AtomicInteger(0);
    LazyPath<Integer> path =
        LazyPath.defer(
            () -> {
              counter.incrementAndGet();
              return value;
            });

    // Not evaluated yet
    assertThat(counter.get()).isEqualTo(0);

    // First evaluation
    assertThat(path.get()).isEqualTo(value);
    assertThat(counter.get()).isEqualTo(1);
  }

  @Property
  @Label("lazy values are memoized")
  void lazyValuesAreMemoized(@ForAll @IntRange(min = -100, max = 100) int value) {
    AtomicInteger counter = new AtomicInteger(0);
    LazyPath<Integer> path =
        LazyPath.defer(
            () -> {
              counter.incrementAndGet();
              return value;
            });

    // Multiple evaluations
    path.get();
    path.get();
    path.get();

    // Should only evaluate once
    assertThat(counter.get()).isEqualTo(1);
  }

  @Property
  @Label("map preserves laziness")
  void mapPreservesLaziness(@ForAll @IntRange(min = -100, max = 100) int value) {
    AtomicInteger counter = new AtomicInteger(0);
    LazyPath<Integer> original =
        LazyPath.defer(
            () -> {
              counter.incrementAndGet();
              return value;
            });

    LazyPath<String> mapped = original.map(i -> "value:" + i);

    // Not evaluated yet
    assertThat(counter.get()).isEqualTo(0);

    // Evaluate mapped
    assertThat(mapped.get()).isEqualTo("value:" + value);
    assertThat(counter.get()).isEqualTo(1);
  }

  @Property
  @Label("via preserves laziness")
  void viaPreservesLaziness(@ForAll @IntRange(min = -100, max = 100) int value) {
    AtomicInteger counter = new AtomicInteger(0);
    LazyPath<Integer> original =
        LazyPath.defer(
            () -> {
              counter.incrementAndGet();
              return value;
            });

    LazyPath<String> chained = original.via(i -> LazyPath.now("value:" + i));

    // Not evaluated yet
    assertThat(counter.get()).isEqualTo(0);

    // Evaluate chained
    assertThat(chained.get()).isEqualTo("value:" + value);
    assertThat(counter.get()).isEqualTo(1);
  }

  @Property
  @Label("zipWith combines two lazy values")
  void zipWithCombinesTwoLazyValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    LazyPath<Integer> pathA = LazyPath.now(a);
    LazyPath<Integer> pathB = LazyPath.now(b);

    LazyPath<Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.get()).isEqualTo(a + b);
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("lazyPaths") LazyPath<Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    LazyPath<Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    LazyPath<Integer> composed = path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(stepByStep.get()).isEqualTo(composed.get());
  }
}
