// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.test.assertions.VTaskPathAssert.assertThatVTaskPath;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for VTaskPath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs. Since VTaskPath represents
 * deferred computations that execute on virtual threads, laws are verified by executing the effects
 * and comparing results.
 */
class VTaskPathPropertyTest {

  @Provide
  Arbitrary<VTaskPath<Integer>> vtaskPaths() {
    return Arbitraries.integers().between(-1000, 1000).map(Path::vtaskPure);
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
  Arbitrary<Function<Integer, VTaskPath<String>>> intToVTaskStringFunctions() {
    return Arbitraries.of(
        i -> Path.vtaskPure("value:" + i),
        i -> Path.vtask(() -> "computed:" + (i * 2)),
        i -> Path.vtaskPure(String.valueOf(i)));
  }

  @Provide
  Arbitrary<Function<String, VTaskPath<String>>> stringToVTaskStringFunctions() {
    return Arbitraries.of(
        s -> Path.vtaskPure(s.toUpperCase()),
        s -> Path.vtask(() -> "length:" + s.length()),
        s -> Path.vtaskPure("transformed:" + s));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id).unsafeRun() == path.unsafeRun()")
  void functorIdentityLaw(@ForAll("vtaskPaths") VTaskPath<Integer> path) {
    VTaskPath<Integer> result = path.map(Function.identity());
    assertThatVTaskPath(result).isEquivalentTo(path);
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("vtaskPaths") VTaskPath<Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    VTaskPath<Integer> leftSide = path.map(f).map(g);
    VTaskPath<Integer> rightSide = path.map(f.andThen(g));

    assertThatVTaskPath(leftSide).isEquivalentTo(rightSide);
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: Path.vtaskPure(a).via(f).unsafeRun() == f(a).unsafeRun()")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToVTaskStringFunctions") Function<Integer, VTaskPath<String>> f) {

    VTaskPath<String> leftSide = Path.vtaskPure(value).via(f);
    VTaskPath<String> rightSide = f.apply(value);

    assertThatVTaskPath(leftSide).isEquivalentTo(rightSide);
  }

  @Property
  @Label("Monad Right Identity Law: path.via(Path::vtaskPure).unsafeRun() == path.unsafeRun()")
  void rightIdentityLaw(@ForAll("vtaskPaths") VTaskPath<Integer> path) {
    VTaskPath<Integer> result = path.via(Path::vtaskPure);
    assertThatVTaskPath(result).isEquivalentTo(path);
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("vtaskPaths") VTaskPath<Integer> path,
      @ForAll("intToVTaskStringFunctions") Function<Integer, VTaskPath<String>> f,
      @ForAll("stringToVTaskStringFunctions") Function<String, VTaskPath<String>> g) {

    VTaskPath<String> leftSide = path.via(f).via(g);
    VTaskPath<String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThatVTaskPath(leftSide).isEquivalentTo(rightSide);
  }

  // ===== Derived Properties =====

  @Property
  @Label("map applies the function to the computed value")
  void mapAppliesFunction(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    VTaskPath<Integer> path = Path.vtaskPure(value);
    VTaskPath<String> result = path.map(f);

    assertThatVTaskPath(result).succeeds().hasValue(f.apply(value));
  }

  @Property
  @Label("zipWith combines computed values")
  void zipWithCombinesValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    VTaskPath<Integer> pathA = Path.vtaskPure(a);
    VTaskPath<Integer> pathB = Path.vtaskPure(b);

    VTaskPath<Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThatVTaskPath(result).succeeds().hasValue(a + b);
  }

  @Property
  @Label("then executes effects in sequence")
  void thenExecutesInSequence(
      @ForAll @IntRange(min = -100, max = 100) int first,
      @ForAll @IntRange(min = -100, max = 100) int second) {

    int[] order = {0};
    VTaskPath<Integer> pathA =
        Path.vtask(
            () -> {
              order[0] = 1;
              return first;
            });
    VTaskPath<Integer> pathB =
        Path.vtask(
            () -> {
              assertThat(order[0]).isEqualTo(1);
              order[0] = 2;
              return second;
            });

    VTaskPath<Integer> result = pathA.then(() -> pathB);

    assertThatVTaskPath(result).succeeds().hasValue(second);
    assertThat(order[0]).isEqualTo(2);
  }

  @Property
  @Label("runSafe captures exceptions")
  void runSafeCapturesExceptions() {
    RuntimeException error = new RuntimeException("test error");
    VTaskPath<Integer> failing =
        Path.vtask(
            () -> {
              throw error;
            });

    assertThatVTaskPath(failing).fails().withExceptionType(RuntimeException.class);
  }

  @Property
  @Label("handleError provides fallback for exceptions")
  void handleErrorProvidesFallback(@ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    RuntimeException error = new RuntimeException("test error");
    VTaskPath<Integer> failing =
        Path.vtask(
            () -> {
              throw error;
            });

    VTaskPath<Integer> recovered = failing.handleError(e -> fallbackValue);

    assertThatVTaskPath(recovered).succeeds().hasValue(fallbackValue);
  }

  @Property
  @Label("handleError does not change successful computation")
  void handleErrorDoesNotChangeSuccess(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    VTaskPath<Integer> success = Path.vtaskPure(value);
    VTaskPath<Integer> result = success.handleError(e -> fallbackValue);

    assertThatVTaskPath(result).succeeds().hasValue(value);
  }

  @Property(tries = 50)
  @Label("via and flatMap are equivalent")
  void viaAndFlatMapEquivalent(
      @ForAll("vtaskPaths") VTaskPath<Integer> path,
      @ForAll("intToVTaskStringFunctions") Function<Integer, VTaskPath<String>> f) {

    VTaskPath<String> viaResult = path.via(f);
    VTaskPath<String> flatMapResult = path.flatMap(f);

    assertThatVTaskPath(viaResult).isEquivalentTo(flatMapResult);
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("vtaskPaths") VTaskPath<Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    VTaskPath<Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    VTaskPath<Integer> composed = path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThatVTaskPath(stepByStep).isEquivalentTo(composed);
  }

  @Property
  @Label("toTryPath converts result to Try")
  void toTryPathConvertsResult(@ForAll @IntRange(min = -100, max = 100) int value) {
    VTaskPath<Integer> path = Path.vtaskPure(value);

    // Verify original path works
    assertThatVTaskPath(path).succeeds().hasValue(value);

    // Verify conversion
    TryPath<Integer> tryPath = path.toTryPath();
    assertThat(tryPath.run().isSuccess()).isTrue();
    assertThat(tryPath.getOrElse(null)).isEqualTo(value);
  }

  @Property
  @Label("toTryPath captures exceptions")
  void toTryPathCapturesExceptions() {
    RuntimeException error = new RuntimeException("test error");
    VTaskPath<Integer> failing =
        Path.vtask(
            () -> {
              throw error;
            });

    assertThatVTaskPath(failing).fails().withExceptionType(RuntimeException.class);

    TryPath<Integer> tryPath = failing.toTryPath();
    assertThat(tryPath.run().isFailure()).isTrue();
  }
}
