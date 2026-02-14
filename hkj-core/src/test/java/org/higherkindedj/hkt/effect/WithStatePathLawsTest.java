// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Law verification tests for WithStatePath.
 *
 * <p>Verifies that WithStatePath satisfies Functor and Monad laws.
 */
@DisplayName("WithStatePath Law Verification Tests")
class WithStatePathLawsTest {

  private static final int INITIAL_STATE = 0;
  private static final int TEST_VALUE = 42;
  private static final Function<Integer, Integer> ADD_ONE = x -> x + 1;
  private static final Function<Integer, Integer> DOUBLE = x -> x * 2;
  private static final Function<Integer, String> INT_TO_STRING = x -> "value:" + x;
  private static final Function<String, Integer> STRING_LENGTH = String::length;

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLawsTests {

    @TestFactory
    @DisplayName("Functor Identity Law: path.map(id) == path")
    Stream<DynamicTest> functorIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Identity law holds for pure value",
              () -> {
                WithStatePath<Integer, Integer> path = WithStatePath.pure(TEST_VALUE);
                WithStatePath<Integer, Integer> result = path.map(Function.identity());
                assertThat(result.run(INITIAL_STATE)).isEqualTo(path.run(INITIAL_STATE));
              }),
          DynamicTest.dynamicTest(
              "Identity law holds with state modification",
              () -> {
                WithStatePath<Integer, Integer> path =
                    WithStatePath.<Integer>get().map(s -> s + 10);
                WithStatePath<Integer, Integer> result = path.map(Function.identity());
                assertThat(result.run(5)).isEqualTo(path.run(5));
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for pure value",
              () -> {
                WithStatePath<Integer, Integer> path = WithStatePath.pure(TEST_VALUE);
                WithStatePath<Integer, Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                WithStatePath<Integer, Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(leftSide.run(INITIAL_STATE)).isEqualTo(rightSide.run(INITIAL_STATE));
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                WithStatePath<Integer, Integer> path = WithStatePath.pure(TEST_VALUE);
                WithStatePath<Integer, Integer> leftSide =
                    path.map(INT_TO_STRING).map(STRING_LENGTH);
                WithStatePath<Integer, Integer> rightSide =
                    path.map(INT_TO_STRING.andThen(STRING_LENGTH));
                assertThat(leftSide.run(INITIAL_STATE)).isEqualTo(rightSide.run(INITIAL_STATE));
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    private final Function<Integer, WithStatePath<Integer, String>> intToStateString =
        x -> WithStatePath.<Integer, String>pure("result:" + x);

    private final Function<String, WithStatePath<Integer, Integer>> stringToStateInt =
        s -> WithStatePath.pure(s.length());

    @TestFactory
    @DisplayName("Left Identity Law: WithStatePath.pure(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity with pure function",
              () -> {
                int value = 10;
                WithStatePath<Integer, String> leftSide =
                    WithStatePath.<Integer, Integer>pure(value).via(intToStateString);
                WithStatePath<Integer, String> rightSide = intToStateString.apply(value);
                assertThat(leftSide.run(INITIAL_STATE)).isEqualTo(rightSide.run(INITIAL_STATE));
              }),
          DynamicTest.dynamicTest(
              "Left identity with state-modifying function",
              () -> {
                int value = 5;
                Function<Integer, WithStatePath<Integer, Integer>> modifyAndReturn =
                    x ->
                        WithStatePath.<Integer>modify(s -> s + x)
                            .then(() -> WithStatePath.pure(x * 2));
                WithStatePath<Integer, Integer> leftSide =
                    WithStatePath.<Integer, Integer>pure(value).via(modifyAndReturn);
                WithStatePath<Integer, Integer> rightSide = modifyAndReturn.apply(value);
                assertThat(leftSide.run(10)).isEqualTo(rightSide.run(10));
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(x -> WithStatePath.pure(x)) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for pure value",
              () -> {
                WithStatePath<Integer, Integer> path = WithStatePath.pure(TEST_VALUE);
                WithStatePath<Integer, Integer> result = path.via(x -> WithStatePath.pure(x));
                assertThat(result.run(INITIAL_STATE)).isEqualTo(path.run(INITIAL_STATE));
              }),
          DynamicTest.dynamicTest(
              "Right identity holds with state access",
              () -> {
                WithStatePath<Integer, Integer> path = WithStatePath.<Integer>get().map(s -> s * 2);
                WithStatePath<Integer, Integer> result = path.via(x -> WithStatePath.pure(x));
                assertThat(result.run(7)).isEqualTo(path.run(7));
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for pure value",
              () -> {
                WithStatePath<Integer, Integer> path = WithStatePath.pure(10);
                WithStatePath<Integer, Integer> leftSide =
                    path.via(intToStateString).via(stringToStateInt);
                WithStatePath<Integer, Integer> rightSide =
                    path.via(x -> intToStateString.apply(x).via(stringToStateInt));
                assertThat(leftSide.run(INITIAL_STATE)).isEqualTo(rightSide.run(INITIAL_STATE));
              }),
          DynamicTest.dynamicTest(
              "Associativity holds with state modifications",
              () -> {
                Function<Integer, WithStatePath<Integer, Integer>> addToState =
                    x ->
                        WithStatePath.<Integer>modify(s -> s + x).then(() -> WithStatePath.pure(x));
                Function<Integer, WithStatePath<Integer, Integer>> multiplyState =
                    x ->
                        WithStatePath.<Integer>modify(s -> s * x).then(() -> WithStatePath.pure(x));

                WithStatePath<Integer, Integer> path = WithStatePath.pure(3);
                WithStatePath<Integer, Integer> leftSide = path.via(addToState).via(multiplyState);
                WithStatePath<Integer, Integer> rightSide =
                    path.via(x -> addToState.apply(x).via(multiplyState));
                assertThat(leftSide.run(1)).isEqualTo(rightSide.run(1));
              }));
    }
  }

  @Nested
  @DisplayName("Additional Invariants")
  class AdditionalInvariantsTests {

    @TestFactory
    @DisplayName("State threading is correct through via chain")
    Stream<DynamicTest> stateThreadingIsCorrect() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "State modifications accumulate correctly",
              () -> {
                WithStatePath<Integer, Integer> path =
                    WithStatePath.<Integer>modify(s -> s + 1)
                        .then(() -> WithStatePath.<Integer>modify(s -> s * 2))
                        .then(() -> WithStatePath.get());

                var result = path.run(5);
                // (5 + 1) * 2 = 12
                assertThat(result.value()).isEqualTo(12);
                assertThat(result.state()).isEqualTo(12);
              }),
          DynamicTest.dynamicTest(
              "get returns current state",
              () -> {
                WithStatePath<Integer, Integer> path = WithStatePath.get();
                assertThat(path.run(42).value()).isEqualTo(42);
                assertThat(path.run(42).state()).isEqualTo(42);
              }),
          DynamicTest.dynamicTest(
              "set replaces state",
              () -> {
                WithStatePath<Integer, Integer> path =
                    WithStatePath.<Integer>set(100).then(() -> WithStatePath.get());
                assertThat(path.run(0).value()).isEqualTo(100);
                assertThat(path.run(0).state()).isEqualTo(100);
              }));
    }
  }
}
