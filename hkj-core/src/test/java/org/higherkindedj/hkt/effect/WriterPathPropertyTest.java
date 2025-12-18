// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Unit;

/**
 * Property-based tests for WriterPath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs. WriterPath uses a Monoid
 * for log accumulation, so tests use a List<String> monoid.
 */
@Label("WriterPath Property-Based Tests")
class WriterPathPropertyTest {

  // Monoid for List<String> log accumulation
  private static final Monoid<List<String>> LOG_MONOID =
      new Monoid<>() {
        @Override
        public List<String> empty() {
          return List.of();
        }

        @Override
        public List<String> combine(List<String> a, List<String> b) {
          List<String> result = new ArrayList<>(a);
          result.addAll(b);
          return result;
        }
      };

  @Provide
  Arbitrary<WriterPath<List<String>, Integer>> writerPaths() {
    return Arbitraries.oneOf(
        // Pure values with empty log
        Arbitraries.integers().between(-1000, 1000).map(i -> WriterPath.pure(i, LOG_MONOID)),
        // Values with log entries
        Arbitraries.integers()
            .between(-1000, 1000)
            .map(i -> WriterPath.writer(i, List.of("value:" + i), LOG_MONOID)));
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
  Arbitrary<Function<Integer, WriterPath<List<String>, String>>> intToWriterStringFunctions() {
    return Arbitraries.of(
        i -> WriterPath.writer("result:" + i, List.of("processed " + i), LOG_MONOID),
        i -> WriterPath.pure("value:" + i, LOG_MONOID),
        i ->
            WriterPath.writer(
                i > 0 ? "positive" : "non-positive", List.of("checked sign of " + i), LOG_MONOID));
  }

  @Provide
  Arbitrary<Function<String, WriterPath<List<String>, String>>> stringToWriterStringFunctions() {
    return Arbitraries.of(
        s -> WriterPath.writer(s.toUpperCase(), List.of("uppercased"), LOG_MONOID),
        s -> WriterPath.pure("transformed:" + s, LOG_MONOID),
        s -> WriterPath.writer(s + "!", List.of("added exclamation"), LOG_MONOID));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("writerPaths") WriterPath<List<String>, Integer> path) {
    WriterPath<List<String>, Integer> result = path.map(Function.identity());
    assertThat(result.value()).isEqualTo(path.value());
    assertThat(result.written()).isEqualTo(path.written());
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("writerPaths") WriterPath<List<String>, Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    WriterPath<List<String>, Integer> leftSide = path.map(f).map(g);
    WriterPath<List<String>, Integer> rightSide = path.map(f.andThen(g));

    assertThat(leftSide.value()).isEqualTo(rightSide.value());
    assertThat(leftSide.written()).isEqualTo(rightSide.written());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: WriterPath.pure(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToWriterStringFunctions") Function<Integer, WriterPath<List<String>, String>> f) {

    WriterPath<List<String>, String> leftSide =
        WriterPath.<List<String>, Integer>pure(value, LOG_MONOID).via(f);
    WriterPath<List<String>, String> rightSide = f.apply(value);

    assertThat(leftSide.value()).isEqualTo(rightSide.value());
    // For left identity, pure has empty log, so combined log equals f's log
    assertThat(leftSide.written()).isEqualTo(rightSide.written());
  }

  @Property
  @Label("Monad Right Identity Law: path.via(x -> WriterPath.pure(x)) == path")
  void rightIdentityLaw(@ForAll("writerPaths") WriterPath<List<String>, Integer> path) {
    WriterPath<List<String>, Integer> result = path.via(x -> WriterPath.pure(x, LOG_MONOID));
    assertThat(result.value()).isEqualTo(path.value());
    // pure adds empty log, so written should be same
    assertThat(result.written()).isEqualTo(path.written());
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("writerPaths") WriterPath<List<String>, Integer> path,
      @ForAll("intToWriterStringFunctions") Function<Integer, WriterPath<List<String>, String>> f,
      @ForAll("stringToWriterStringFunctions")
          Function<String, WriterPath<List<String>, String>> g) {

    WriterPath<List<String>, String> leftSide = path.via(f).via(g);
    WriterPath<List<String>, String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.value()).isEqualTo(rightSide.value());
    assertThat(leftSide.written()).isEqualTo(rightSide.written());
  }

  // ===== Derived Properties =====

  @Property
  @Label("map does not affect the log")
  void mapDoesNotAffectLog(
      @ForAll("writerPaths") WriterPath<List<String>, Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    WriterPath<List<String>, String> result = path.map(f);
    assertThat(result.written()).isEqualTo(path.written());
  }

  @Property
  @Label("tell produces log with Unit value")
  void tellProducesLogWithUnit() {
    List<String> log = List.of("message");
    WriterPath<List<String>, Unit> path = WriterPath.tell(log, LOG_MONOID);

    assertThat(path.written()).isEqualTo(log);
    assertThat(path.value()).isEqualTo(Unit.INSTANCE);
  }

  @Property
  @Label("pure produces empty log")
  void pureProducesEmptyLog(@ForAll @IntRange(min = -100, max = 100) int value) {
    WriterPath<List<String>, Integer> path = WriterPath.pure(value, LOG_MONOID);
    assertThat(path.written()).isEmpty();
    assertThat(path.value()).isEqualTo(value);
  }

  @Property
  @Label("via accumulates logs from both computations")
  void viaAccumulatesLogs(@ForAll @IntRange(min = -100, max = 100) int value) {

    List<String> firstLog = List.of("first");
    List<String> secondLog = List.of("second");

    WriterPath<List<String>, Integer> first = WriterPath.writer(value, firstLog, LOG_MONOID);
    WriterPath<List<String>, String> result =
        first.via(v -> WriterPath.writer("result:" + v, secondLog, LOG_MONOID));

    assertThat(result.written()).containsExactly("first", "second");
  }

  @Property
  @Label("zipWith combines values and accumulates logs")
  void zipWithCombinesValuesAndLogs(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    WriterPath<List<String>, Integer> pathA =
        WriterPath.writer(a, List.of("got a=" + a), LOG_MONOID);
    WriterPath<List<String>, Integer> pathB =
        WriterPath.writer(b, List.of("got b=" + b), LOG_MONOID);

    WriterPath<List<String>, Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(result.value()).isEqualTo(a + b);
    assertThat(result.written()).containsExactly("got a=" + a, "got b=" + b);
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("writerPaths") WriterPath<List<String>, Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    WriterPath<List<String>, Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    WriterPath<List<String>, Integer> composed =
        path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(stepByStep.value()).isEqualTo(composed.value());
    assertThat(stepByStep.written()).isEqualTo(composed.written());
  }
}
