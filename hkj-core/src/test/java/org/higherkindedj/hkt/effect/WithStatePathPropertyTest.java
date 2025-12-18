// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Unit;

/**
 * Property-based tests for WithStatePath using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs. WithStatePath threads
 * state through computations, so tests verify both value and state transformations.
 */
@Label("WithStatePath Property-Based Tests")
class WithStatePathPropertyTest {

  private static final int INITIAL_STATE = 0;

  @Provide
  Arbitrary<WithStatePath<Integer, Integer>> statePaths() {
    return Arbitraries.oneOf(
        // Pure values
        Arbitraries.integers().between(-1000, 1000).map(WithStatePath::pure),
        // State-dependent values
        Arbitraries.just(WithStatePath.get()),
        Arbitraries.just(WithStatePath.<Integer>get().map(s -> s * 2)),
        Arbitraries.just(WithStatePath.inspect(s -> s + 100)));
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
  Arbitrary<Function<Integer, WithStatePath<Integer, String>>> intToStateStringFunctions() {
    return Arbitraries.of(
        i -> WithStatePath.<Integer>modify(s -> s + i).map(u -> "added:" + i),
        i -> WithStatePath.pure("value:" + i),
        i -> WithStatePath.inspect(s -> "state:" + s + ",value:" + i),
        i ->
            WithStatePath.<Integer>set(i * 10)
                .then(() -> WithStatePath.pure("set state to " + (i * 10))));
  }

  @Provide
  Arbitrary<Function<String, WithStatePath<Integer, String>>> stringToStateStringFunctions() {
    return Arbitraries.of(
        s -> WithStatePath.pure(s.toUpperCase()),
        s -> WithStatePath.<Integer>modify(state -> state + s.length()).map(u -> s + "!"),
        s -> WithStatePath.inspect(state -> s + "@" + state));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("statePaths") WithStatePath<Integer, Integer> path) {
    WithStatePath<Integer, Integer> result = path.map(Function.identity());
    assertThat(result.run(INITIAL_STATE)).isEqualTo(path.run(INITIAL_STATE));
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("statePaths") WithStatePath<Integer, Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    WithStatePath<Integer, Integer> leftSide = path.map(f).map(g);
    WithStatePath<Integer, Integer> rightSide = path.map(f.andThen(g));

    assertThat(leftSide.run(INITIAL_STATE)).isEqualTo(rightSide.run(INITIAL_STATE));
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: WithStatePath.pure(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStateStringFunctions") Function<Integer, WithStatePath<Integer, String>> f) {

    WithStatePath<Integer, String> leftSide = WithStatePath.<Integer, Integer>pure(value).via(f);
    WithStatePath<Integer, String> rightSide = f.apply(value);

    assertThat(leftSide.run(INITIAL_STATE)).isEqualTo(rightSide.run(INITIAL_STATE));
  }

  @Property
  @Label("Monad Right Identity Law: path.via(WithStatePath::pure) == path")
  void rightIdentityLaw(@ForAll("statePaths") WithStatePath<Integer, Integer> path) {
    WithStatePath<Integer, Integer> result = path.via(WithStatePath::pure);
    assertThat(result.run(INITIAL_STATE)).isEqualTo(path.run(INITIAL_STATE));
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("statePaths") WithStatePath<Integer, Integer> path,
      @ForAll("intToStateStringFunctions") Function<Integer, WithStatePath<Integer, String>> f,
      @ForAll("stringToStateStringFunctions") Function<String, WithStatePath<Integer, String>> g) {

    WithStatePath<Integer, String> leftSide = path.via(f).via(g);
    WithStatePath<Integer, String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.run(INITIAL_STATE)).isEqualTo(rightSide.run(INITIAL_STATE));
  }

  // ===== Derived Properties =====

  @Property
  @Label("get returns the current state")
  void getReturnsCurrentState(@ForAll @IntRange(min = -100, max = 100) int state) {
    WithStatePath<Integer, Integer> path = WithStatePath.get();
    assertThat(path.evalState(state)).isEqualTo(state);
  }

  @Property
  @Label("set updates the state")
  void setUpdatesState(
      @ForAll @IntRange(min = -100, max = 100) int initial,
      @ForAll @IntRange(min = -100, max = 100) int newState) {

    WithStatePath<Integer, Unit> path = WithStatePath.set(newState);
    assertThat(path.execState(initial)).isEqualTo(newState);
  }

  @Property
  @Label("modify transforms the state")
  void modifyTransformsState(@ForAll @IntRange(min = -100, max = 100) int initial) {
    WithStatePath<Integer, Unit> path = WithStatePath.modify(s -> s + 10);
    assertThat(path.execState(initial)).isEqualTo(initial + 10);
  }

  @Property
  @Label("inspect extracts from state without modifying")
  void inspectExtractsWithoutModifying(@ForAll @IntRange(min = -100, max = 100) int state) {
    WithStatePath<Integer, String> path = WithStatePath.inspect(s -> "state:" + s);

    var result = path.run(state);
    assertThat(result.value()).isEqualTo("state:" + state);
    assertThat(result.state()).isEqualTo(state); // State unchanged
  }

  @Property
  @Label("pure does not modify state")
  void pureDoesNotModifyState(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @IntRange(min = -100, max = 100) int state) {

    WithStatePath<Integer, Integer> path = WithStatePath.pure(value);
    var result = path.run(state);

    assertThat(result.value()).isEqualTo(value);
    assertThat(result.state()).isEqualTo(state);
  }

  @Property
  @Label("State changes thread through via chain")
  void stateThreadsThroughVia(@ForAll @IntRange(min = 0, max = 10) int initial) {
    WithStatePath<Integer, String> path =
        WithStatePath.<Integer>modify(s -> s + 1)
            .then(() -> WithStatePath.<Integer>modify(s -> s * 2))
            .then(() -> WithStatePath.inspect(s -> "final:" + s));

    var result = path.run(initial);
    // (initial + 1) * 2
    int expectedState = (initial + 1) * 2;
    assertThat(result.state()).isEqualTo(expectedState);
    assertThat(result.value()).isEqualTo("final:" + expectedState);
  }

  @Property
  @Label("zipWith combines values and threads state")
  void zipWithCombinesAndThreadsState() {
    WithStatePath<Integer, Integer> pathA = WithStatePath.<Integer>modify(s -> s + 1).map(u -> 10);
    WithStatePath<Integer, Integer> pathB = WithStatePath.<Integer>modify(s -> s + 2).map(u -> 20);

    WithStatePath<Integer, Integer> result = pathA.zipWith(pathB, Integer::sum);

    var output = result.run(0);
    assertThat(output.value()).isEqualTo(30); // 10 + 20
    assertThat(output.state()).isEqualTo(3); // 0 + 1 + 2
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("statePaths") WithStatePath<Integer, Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    WithStatePath<Integer, Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    WithStatePath<Integer, Integer> composed =
        path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(stepByStep.run(INITIAL_STATE)).isEqualTo(composed.run(INITIAL_STATE));
  }

  @Property
  @Label("evalState returns only the value")
  void evalStateReturnsOnlyValue(@ForAll @IntRange(min = -100, max = 100) int value) {
    WithStatePath<Integer, Integer> path = WithStatePath.pure(value);
    assertThat(path.evalState(999)).isEqualTo(value);
  }

  @Property
  @Label("execState returns only the state")
  void execStateReturnsOnlyState(@ForAll @IntRange(min = -100, max = 100) int newState) {
    WithStatePath<Integer, Unit> path = WithStatePath.set(newState);
    assertThat(path.execState(0)).isEqualTo(newState);
  }
}
