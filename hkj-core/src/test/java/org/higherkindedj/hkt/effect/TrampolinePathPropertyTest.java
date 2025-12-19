// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for TrampolinePath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs. TrampolinePath represents
 * stack-safe recursive computations.
 */
@Label("TrampolinePath Property-Based Tests")
class TrampolinePathPropertyTest {

  @Provide
  Arbitrary<TrampolinePath<Integer>> trampolinePaths() {
    return Arbitraries.oneOf(
        // Pure values
        Arbitraries.integers().between(-1000, 1000).map(TrampolinePath::done),
        // Deferred computations
        Arbitraries.integers()
            .between(-1000, 1000)
            .map(i -> TrampolinePath.defer(() -> TrampolinePath.done(i))),
        // Nested defer
        Arbitraries.integers()
            .between(-1000, 1000)
            .map(
                i ->
                    TrampolinePath.defer(
                        () -> TrampolinePath.defer(() -> TrampolinePath.done(i * 2)))));
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
  Arbitrary<Function<Integer, TrampolinePath<String>>> intToTrampolineStringFunctions() {
    return Arbitraries.of(
        i -> TrampolinePath.done("value:" + i),
        i -> TrampolinePath.defer(() -> TrampolinePath.done("deferred:" + i)),
        i -> TrampolinePath.defer(() -> TrampolinePath.done(i > 0 ? "positive" : "non-positive")));
  }

  @Provide
  Arbitrary<Function<String, TrampolinePath<String>>> stringToTrampolineStringFunctions() {
    return Arbitraries.of(
        s -> TrampolinePath.done(s.toUpperCase()),
        s -> TrampolinePath.defer(() -> TrampolinePath.done(s + "!")),
        s -> TrampolinePath.defer(() -> TrampolinePath.done(s.isEmpty() ? "empty" : s)));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("trampolinePaths") TrampolinePath<Integer> path) {
    TrampolinePath<Integer> result = path.map(Function.identity());
    assertThat(result.run()).isEqualTo(path.run());
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("trampolinePaths") TrampolinePath<Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    TrampolinePath<Integer> leftSide = path.map(f).map(g);
    TrampolinePath<Integer> rightSide = path.map(f.andThen(g));

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: TrampolinePath.done(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToTrampolineStringFunctions") Function<Integer, TrampolinePath<String>> f) {

    TrampolinePath<String> leftSide = TrampolinePath.done(value).via(f);
    TrampolinePath<String> rightSide = f.apply(value);

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  @Property
  @Label("Monad Right Identity Law: path.via(TrampolinePath::done) == path")
  void rightIdentityLaw(@ForAll("trampolinePaths") TrampolinePath<Integer> path) {
    TrampolinePath<Integer> result = path.via(TrampolinePath::done);
    assertThat(result.run()).isEqualTo(path.run());
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("trampolinePaths") TrampolinePath<Integer> path,
      @ForAll("intToTrampolineStringFunctions") Function<Integer, TrampolinePath<String>> f,
      @ForAll("stringToTrampolineStringFunctions") Function<String, TrampolinePath<String>> g) {

    TrampolinePath<String> leftSide = path.via(f).via(g);
    TrampolinePath<String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  // ===== Stack Safety Properties =====

  @Property
  @Label("deep recursion is stack safe")
  void deepRecursionIsStackSafe(@ForAll @IntRange(min = 1000, max = 10000) int depth) {
    TrampolinePath<Integer> path = countTo(0, depth);
    assertThat(path.run()).isEqualTo(depth);
  }

  private TrampolinePath<Integer> countTo(int current, int target) {
    if (current >= target) {
      return TrampolinePath.done(current);
    }
    return TrampolinePath.defer(() -> countTo(current + 1, target));
  }

  @Property
  @Label("pure creates immediate value")
  void pureCreatesImmediateValue(@ForAll @IntRange(min = -100, max = 100) int value) {
    TrampolinePath<Integer> path = TrampolinePath.pure(value);
    assertThat(path.run()).isEqualTo(value);
  }

  @Property
  @Label("done creates immediate value")
  void doneCreatesImmediateValue(@ForAll @IntRange(min = -100, max = 100) int value) {
    TrampolinePath<Integer> path = TrampolinePath.done(value);
    assertThat(path.run()).isEqualTo(value);
  }

  @Property
  @Label("defer creates deferred computation")
  void deferCreatesDeferred(@ForAll @IntRange(min = -100, max = 100) int value) {
    TrampolinePath<Integer> path = TrampolinePath.defer(() -> TrampolinePath.done(value));
    assertThat(path.run()).isEqualTo(value);
  }

  @Property
  @Label("zipWith combines two trampoline values")
  void zipWithCombinesTwoValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    TrampolinePath<Integer> pathA = TrampolinePath.done(a);
    TrampolinePath<Integer> pathB = TrampolinePath.done(b);

    TrampolinePath<Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.run()).isEqualTo(a + b);
  }

  @Property
  @Label("zipWith is stack safe with deferred values")
  void zipWithIsStackSafe(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    TrampolinePath<Integer> pathA = TrampolinePath.defer(() -> TrampolinePath.done(a));
    TrampolinePath<Integer> pathB = TrampolinePath.defer(() -> TrampolinePath.done(b));

    TrampolinePath<Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.run()).isEqualTo(a + b);
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("trampolinePaths") TrampolinePath<Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    TrampolinePath<Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    TrampolinePath<Integer> composed =
        path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(stepByStep.run()).isEqualTo(composed.run());
  }

  @Property
  @Label("then discards first value correctly")
  void thenDiscardsFirstValue(
      @ForAll @IntRange(min = -100, max = 100) int first,
      @ForAll @IntRange(min = -100, max = 100) int second) {

    TrampolinePath<Integer> path =
        TrampolinePath.done(first).then(() -> TrampolinePath.done(second));

    assertThat(path.run()).isEqualTo(second);
  }

  @Property
  @Label("toIOPath preserves value")
  void toIOPathPreservesValue(@ForAll @IntRange(min = -100, max = 100) int value) {
    TrampolinePath<Integer> trampoline = TrampolinePath.done(value);
    IOPath<Integer> io = trampoline.toIOPath();

    assertThat(io.unsafeRun()).isEqualTo(value);
  }

  @Property
  @Label("toLazyPath preserves value")
  void toLazyPathPreservesValue(@ForAll @IntRange(min = -100, max = 100) int value) {
    TrampolinePath<Integer> trampoline = TrampolinePath.done(value);
    LazyPath<Integer> lazy = trampoline.toLazyPath();

    assertThat(lazy.get()).isEqualTo(value);
  }
}
