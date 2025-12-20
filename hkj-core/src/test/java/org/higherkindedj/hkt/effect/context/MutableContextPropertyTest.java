// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.state.StateTuple;

/**
 * Property-based tests for MutableContext using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs.
 */
class MutableContextPropertyTest {

  record Counter(int count) {}

  @Provide
  Arbitrary<MutableContext<IOKind.Witness, Counter, Integer>> statefulContexts() {
    return Arbitraries.integers().between(-1000, 1000).map(MutableContext::<Counter, Integer>pure);
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
  Arbitrary<Function<Integer, MutableContext<IOKind.Witness, Counter, String>>>
      intToMutableContextStringFunctions() {
    return Arbitraries.of(
        i -> MutableContext.pure("value:" + i),
        i -> MutableContext.io(s -> StateTuple.of(new Counter(s.count() + 1), "counted:" + i)),
        i -> MutableContext.pure("result:" + i));
  }

  @Provide
  Arbitrary<Function<String, MutableContext<IOKind.Witness, Counter, String>>>
      stringToMutableContextStringFunctions() {
    return Arbitraries.of(
        s -> MutableContext.pure(s.toUpperCase()),
        s ->
            MutableContext.io(
                state -> StateTuple.of(new Counter(state.count() + s.length()), s + "!")),
        s -> MutableContext.pure("final:" + s));
  }

  private static final Counter INITIAL_STATE = new Counter(0);

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: ctx.map(id) == ctx")
  void functorIdentityLaw(
      @ForAll("statefulContexts") MutableContext<IOKind.Witness, Counter, Integer> ctx) {
    MutableContext<IOKind.Witness, Counter, Integer> result = ctx.map(Function.identity());
    assertThat(result.runWith(INITIAL_STATE).unsafeRun())
        .isEqualTo(ctx.runWith(INITIAL_STATE).unsafeRun());
  }

  @Property
  @Label("Functor Composition Law: ctx.map(f).map(g) == ctx.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("statefulContexts") MutableContext<IOKind.Witness, Counter, Integer> ctx,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    MutableContext<IOKind.Witness, Counter, Integer> leftSide = ctx.map(f).map(g);
    MutableContext<IOKind.Witness, Counter, Integer> rightSide = ctx.map(f.andThen(g));

    assertThat(leftSide.runWith(INITIAL_STATE).unsafeRun())
        .isEqualTo(rightSide.runWith(INITIAL_STATE).unsafeRun());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: MutableContext.pure(a).flatMap(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToMutableContextStringFunctions")
          Function<Integer, MutableContext<IOKind.Witness, Counter, String>> f) {

    MutableContext<IOKind.Witness, Counter, String> leftSide =
        MutableContext.<Counter, Integer>pure(value).flatMap(f);
    MutableContext<IOKind.Witness, Counter, String> rightSide = f.apply(value);

    assertThat(leftSide.runWith(INITIAL_STATE).unsafeRun())
        .isEqualTo(rightSide.runWith(INITIAL_STATE).unsafeRun());
  }

  @Property
  @Label("Monad Right Identity Law: ctx.flatMap(MutableContext::pure) == ctx")
  void rightIdentityLaw(
      @ForAll("statefulContexts") MutableContext<IOKind.Witness, Counter, Integer> ctx) {
    MutableContext<IOKind.Witness, Counter, Integer> result = ctx.flatMap(MutableContext::pure);
    assertThat(result.runWith(INITIAL_STATE).unsafeRun())
        .isEqualTo(ctx.runWith(INITIAL_STATE).unsafeRun());
  }

  @Property
  @Label("Monad Associativity Law: ctx.flatMap(f).flatMap(g) == ctx.flatMap(x -> f(x).flatMap(g))")
  void associativityLaw(
      @ForAll("statefulContexts") MutableContext<IOKind.Witness, Counter, Integer> ctx,
      @ForAll("intToMutableContextStringFunctions")
          Function<Integer, MutableContext<IOKind.Witness, Counter, String>> f,
      @ForAll("stringToMutableContextStringFunctions")
          Function<String, MutableContext<IOKind.Witness, Counter, String>> g) {

    MutableContext<IOKind.Witness, Counter, String> leftSide = ctx.flatMap(f).flatMap(g);
    MutableContext<IOKind.Witness, Counter, String> rightSide =
        ctx.flatMap(x -> f.apply(x).flatMap(g));

    assertThat(leftSide.runWith(INITIAL_STATE).unsafeRun())
        .isEqualTo(rightSide.runWith(INITIAL_STATE).unsafeRun());
  }

  // ===== State-specific Properties =====

  @Property
  @Label("get() returns current state")
  void getReturnsCurrentState(@ForAll @IntRange(min = 0, max = 100) int initialCount) {
    Counter state = new Counter(initialCount);
    MutableContext<IOKind.Witness, Counter, Counter> ctx = MutableContext.get();

    assertThat(ctx.evalWith(state).unsafeRun()).isEqualTo(state);
  }

  @Property
  @Label("put() replaces state")
  void putReplacesState(
      @ForAll @IntRange(min = 0, max = 100) int initialCount,
      @ForAll @IntRange(min = 0, max = 100) int newCount) {
    Counter initial = new Counter(initialCount);
    Counter newState = new Counter(newCount);
    MutableContext<IOKind.Witness, Counter, ?> ctx = MutableContext.put(newState);

    assertThat(ctx.execWith(initial).unsafeRun()).isEqualTo(newState);
  }

  @Property
  @Label("modify() transforms state correctly")
  void modifyTransformsState(@ForAll @IntRange(min = 0, max = 100) int initialCount) {
    Counter initial = new Counter(initialCount);
    MutableContext<IOKind.Witness, Counter, ?> ctx =
        MutableContext.modify(s -> new Counter(s.count() + 10));

    assertThat(ctx.execWith(initial).unsafeRun().count()).isEqualTo(initialCount + 10);
  }

  @Property
  @Label("pure() does not change state")
  void pureDoesNotChangeState(@ForAll @IntRange(min = 0, max = 100) int initialCount) {
    Counter initial = new Counter(initialCount);
    MutableContext<IOKind.Witness, Counter, Integer> ctx = MutableContext.pure(42);

    assertThat(ctx.execWith(initial).unsafeRun()).isEqualTo(initial);
  }

  @Property
  @Label("state is threaded through flatMap")
  void stateIsThreadedThroughFlatMap(@ForAll @IntRange(min = 0, max = 50) int initialCount) {
    Counter initial = new Counter(initialCount);

    MutableContext<IOKind.Witness, Counter, Integer> workflow =
        MutableContext.<Counter>modify(s -> new Counter(s.count() + 1))
            .then(() -> MutableContext.modify(s -> new Counter(s.count() + 2)))
            .then(() -> MutableContext.<Counter>get().map(Counter::count));

    StateTuple<Counter, Integer> result = workflow.runWith(initial).unsafeRun();
    assertThat(result.value()).isEqualTo(initialCount + 3);
    assertThat(result.state().count()).isEqualTo(initialCount + 3);
  }

  @Property
  @Label("evalWith() returns value and discards final state")
  void evalWithReturnsValue(@ForAll @IntRange(min = -100, max = 100) int value) {
    MutableContext<IOKind.Witness, Counter, Integer> ctx = MutableContext.pure(value);

    Integer result = ctx.evalWith(INITIAL_STATE).unsafeRun();
    assertThat(result).isEqualTo(value);
  }

  @Property
  @Label("execWith() returns state and discards value")
  void execWithReturnsState(@ForAll @IntRange(min = 0, max = 100) int increment) {
    MutableContext<IOKind.Witness, Counter, ?> ctx =
        MutableContext.modify(s -> new Counter(s.count() + increment));

    Counter result = ctx.execWith(INITIAL_STATE).unsafeRun();
    assertThat(result.count()).isEqualTo(increment);
  }

  @Property
  @Label("via and flatMap are equivalent")
  void viaAndFlatMapEquivalent(
      @ForAll("statefulContexts") MutableContext<IOKind.Witness, Counter, Integer> ctx,
      @ForAll("intToMutableContextStringFunctions")
          Function<Integer, MutableContext<IOKind.Witness, Counter, String>> f) {

    StateTuple<Counter, String> viaResult = ctx.via(f).runWith(INITIAL_STATE).unsafeRun();
    StateTuple<Counter, String> flatMapResult = ctx.flatMap(f).runWith(INITIAL_STATE).unsafeRun();

    assertThat(viaResult).isEqualTo(flatMapResult);
  }
}
