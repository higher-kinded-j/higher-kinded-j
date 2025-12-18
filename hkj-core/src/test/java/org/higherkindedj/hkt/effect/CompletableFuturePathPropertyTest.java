// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for CompletableFuturePath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs. CompletableFuturePath
 * represents asynchronous computations.
 */
@Label("CompletableFuturePath Property-Based Tests")
class CompletableFuturePathPropertyTest {

  @Provide
  Arbitrary<CompletableFuturePath<Integer>> futurePaths() {
    return Arbitraries.oneOf(
        // Pure values
        Arbitraries.integers().between(-1000, 1000).map(CompletableFuturePath::completed),
        // From completed futures
        Arbitraries.integers()
            .between(-1000, 1000)
            .map(i -> CompletableFuturePath.fromFuture(CompletableFuture.completedFuture(i))),
        // Async computations
        Arbitraries.integers()
            .between(-1000, 1000)
            .map(i -> CompletableFuturePath.supplyAsync(() -> i)));
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
  Arbitrary<Function<Integer, CompletableFuturePath<String>>> intToFutureStringFunctions() {
    return Arbitraries.of(
        i -> CompletableFuturePath.completed("value:" + i),
        i -> CompletableFuturePath.supplyAsync(() -> "async:" + i),
        i -> CompletableFuturePath.completed(i > 0 ? "positive" : "non-positive"));
  }

  @Provide
  Arbitrary<Function<String, CompletableFuturePath<String>>> stringToFutureStringFunctions() {
    return Arbitraries.of(
        s -> CompletableFuturePath.completed(s.toUpperCase()),
        s -> CompletableFuturePath.supplyAsync(() -> s + "!"),
        s -> CompletableFuturePath.completed(s.isEmpty() ? "empty" : s));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("futurePaths") CompletableFuturePath<Integer> path) {
    CompletableFuturePath<Integer> result = path.map(Function.identity());
    assertThat(result.run().join()).isEqualTo(path.run().join());
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("futurePaths") CompletableFuturePath<Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    CompletableFuturePath<Integer> leftSide = path.map(f).map(g);
    CompletableFuturePath<Integer> rightSide = path.map(f.andThen(g));

    assertThat(leftSide.run().join()).isEqualTo(rightSide.run().join());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: CompletableFuturePath.completed(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToFutureStringFunctions") Function<Integer, CompletableFuturePath<String>> f) {

    CompletableFuturePath<String> leftSide = CompletableFuturePath.completed(value).via(f);
    CompletableFuturePath<String> rightSide = f.apply(value);

    assertThat(leftSide.run().join()).isEqualTo(rightSide.run().join());
  }

  @Property
  @Label("Monad Right Identity Law: path.via(CompletableFuturePath::completed) == path")
  void rightIdentityLaw(@ForAll("futurePaths") CompletableFuturePath<Integer> path) {
    CompletableFuturePath<Integer> result = path.via(CompletableFuturePath::completed);
    assertThat(result.run().join()).isEqualTo(path.run().join());
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("futurePaths") CompletableFuturePath<Integer> path,
      @ForAll("intToFutureStringFunctions") Function<Integer, CompletableFuturePath<String>> f,
      @ForAll("stringToFutureStringFunctions") Function<String, CompletableFuturePath<String>> g) {

    CompletableFuturePath<String> leftSide = path.via(f).via(g);
    CompletableFuturePath<String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.run().join()).isEqualTo(rightSide.run().join());
  }

  // ===== Derived Properties =====

  @Property
  @Label("pure creates completed future")
  void pureCreatesCompletedFuture(@ForAll @IntRange(min = -100, max = 100) int value) {
    CompletableFuturePath<Integer> path = CompletableFuturePath.completed(value);
    CompletableFuture<Integer> future = path.run();

    assertThat(future.isDone()).isTrue();
    assertThat(future.join()).isEqualTo(value);
  }

  @Property
  @Label("async creates async computation")
  void asyncCreatesAsyncComputation(@ForAll @IntRange(min = -100, max = 100) int value) {
    CompletableFuturePath<Integer> path = CompletableFuturePath.supplyAsync(() -> value);
    assertThat(path.run().join()).isEqualTo(value);
  }

  @Property
  @Label("zipWith combines two futures")
  void zipWithCombinesTwoFutures(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    CompletableFuturePath<Integer> pathA = CompletableFuturePath.completed(a);
    CompletableFuturePath<Integer> pathB = CompletableFuturePath.completed(b);

    CompletableFuturePath<Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.run().join()).isEqualTo(a + b);
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("futurePaths") CompletableFuturePath<Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    CompletableFuturePath<Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    CompletableFuturePath<Integer> composed =
        path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(stepByStep.run().join()).isEqualTo(composed.run().join());
  }
}
