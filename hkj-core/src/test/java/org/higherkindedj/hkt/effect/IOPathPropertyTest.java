// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for IOPath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs. Since IOPath represents
 * deferred computations, laws are verified by executing the effects and comparing results.
 */
class IOPathPropertyTest {

  @Provide
  Arbitrary<IOPath<Integer>> ioPaths() {
    return Arbitraries.integers().between(-1000, 1000).map(Path::ioPure);
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
  Arbitrary<Function<Integer, IOPath<String>>> intToIOStringFunctions() {
    return Arbitraries.of(
        (Function<Integer, IOPath<String>>) (i -> Path.ioPure("value:" + i)),
        (Function<Integer, IOPath<String>>) (i -> Path.io(() -> "computed:" + (i * 2))),
        (Function<Integer, IOPath<String>>) (i -> Path.ioPure(String.valueOf(i))));
  }

  @Provide
  Arbitrary<Function<String, IOPath<String>>> stringToIOStringFunctions() {
    return Arbitraries.of(
        (Function<String, IOPath<String>>) (s -> Path.ioPure(s.toUpperCase())),
        (Function<String, IOPath<String>>) (s -> Path.io(() -> "length:" + s.length())),
        (Function<String, IOPath<String>>) (s -> Path.ioPure("transformed:" + s)));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id).unsafeRun() == path.unsafeRun()")
  void functorIdentityLaw(@ForAll("ioPaths") IOPath<Integer> path) {
    IOPath<Integer> result = path.map(Function.identity());
    assertThat(result.unsafeRun()).isEqualTo(path.unsafeRun());
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("ioPaths") IOPath<Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    IOPath<Integer> leftSide = path.map(f).map(g);
    IOPath<Integer> rightSide = path.map(f.andThen(g));

    assertThat(leftSide.unsafeRun()).isEqualTo(rightSide.unsafeRun());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: Path.ioPure(a).via(f).unsafeRun() == f(a).unsafeRun()")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToIOStringFunctions") Function<Integer, IOPath<String>> f) {

    IOPath<String> leftSide = Path.ioPure(value).via(f);
    IOPath<String> rightSide = f.apply(value);

    assertThat(leftSide.unsafeRun()).isEqualTo(rightSide.unsafeRun());
  }

  @Property
  @Label("Monad Right Identity Law: path.via(Path::ioPure).unsafeRun() == path.unsafeRun()")
  void rightIdentityLaw(@ForAll("ioPaths") IOPath<Integer> path) {
    IOPath<Integer> result = path.via(Path::ioPure);
    assertThat(result.unsafeRun()).isEqualTo(path.unsafeRun());
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("ioPaths") IOPath<Integer> path,
      @ForAll("intToIOStringFunctions") Function<Integer, IOPath<String>> f,
      @ForAll("stringToIOStringFunctions") Function<String, IOPath<String>> g) {

    IOPath<String> leftSide = path.via(f).via(g);
    IOPath<String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.unsafeRun()).isEqualTo(rightSide.unsafeRun());
  }

  // ===== Derived Properties =====

  @Property
  @Label("map applies the function to the computed value")
  void mapAppliesFunction(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    IOPath<Integer> path = Path.ioPure(value);
    IOPath<String> result = path.map(f);

    assertThat(result.unsafeRun()).isEqualTo(f.apply(value));
  }

  @Property
  @Label("zipWith combines computed values")
  void zipWithCombinesValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    IOPath<Integer> pathA = Path.ioPure(a);
    IOPath<Integer> pathB = Path.ioPure(b);

    IOPath<Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.unsafeRun()).isEqualTo(a + b);
  }

  @Property
  @Label("then executes effects in sequence")
  void thenExecutesInSequence(
      @ForAll @IntRange(min = -100, max = 100) int first,
      @ForAll @IntRange(min = -100, max = 100) int second) {

    int[] order = {0};
    IOPath<Integer> pathA =
        Path.io(
            () -> {
              order[0] = 1;
              return first;
            });
    IOPath<Integer> pathB =
        Path.io(
            () -> {
              assertThat(order[0]).isEqualTo(1);
              order[0] = 2;
              return second;
            });

    IOPath<Integer> result = pathA.then(() -> pathB);

    assertThat(result.unsafeRun()).isEqualTo(second);
    assertThat(order[0]).isEqualTo(2);
  }

  @Property
  @Label("runSafe captures exceptions")
  void runSafeCapturesExceptions() {
    RuntimeException error = new RuntimeException("test error");
    IOPath<Integer> failing =
        Path.io(
            () -> {
              throw error;
            });

    var result = failing.runSafe();

    assertThat(result.isFailure()).isTrue();
  }

  @Property
  @Label("handleError provides fallback for exceptions")
  void handleErrorProvidesFallback(
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    RuntimeException error = new RuntimeException("test error");
    IOPath<Integer> failing =
        Path.io(
            () -> {
              throw error;
            });

    IOPath<Integer> recovered = failing.handleError(e -> fallbackValue);

    assertThat(recovered.unsafeRun()).isEqualTo(fallbackValue);
  }

  @Property
  @Label("handleError does not change successful computation")
  void handleErrorDoesNotChangeSuccess(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    IOPath<Integer> success = Path.ioPure(value);
    IOPath<Integer> result = success.handleError(e -> fallbackValue);

    assertThat(result.unsafeRun()).isEqualTo(value);
  }

  @Property(tries = 50)
  @Label("via and flatMap are equivalent")
  void viaAndFlatMapEquivalent(
      @ForAll("ioPaths") IOPath<Integer> path,
      @ForAll("intToIOStringFunctions") Function<Integer, IOPath<String>> f) {

    IOPath<String> viaResult = path.via(f);
    IOPath<String> flatMapResult = path.flatMap(f);

    assertThat(viaResult.unsafeRun()).isEqualTo(flatMapResult.unsafeRun());
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("ioPaths") IOPath<Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    IOPath<Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    IOPath<Integer> composed = path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(stepByStep.unsafeRun()).isEqualTo(composed.unsafeRun());
  }

  @Property
  @Label("toTryPath converts result to Try")
  void toTryPathConvertsResult(@ForAll @IntRange(min = -100, max = 100) int value) {
    IOPath<Integer> path = Path.ioPure(value);
    TryPath<Integer> tryPath = path.toTryPath();

    assertThat(tryPath.run().isSuccess()).isTrue();
    assertThat(tryPath.getOrElse(null)).isEqualTo(value);
  }

  @Property
  @Label("toTryPath captures exceptions")
  void toTryPathCapturesExceptions() {
    RuntimeException error = new RuntimeException("test error");
    IOPath<Integer> failing =
        Path.io(
            () -> {
              throw error;
            });

    TryPath<Integer> tryPath = failing.toTryPath();

    assertThat(tryPath.run().isFailure()).isTrue();
  }
}
