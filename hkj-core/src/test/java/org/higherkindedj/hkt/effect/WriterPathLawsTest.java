// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Monoid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Law verification tests for WriterPath.
 *
 * <p>Verifies that WriterPath satisfies Functor and Monad laws.
 */
@DisplayName("WriterPath Law Verification Tests")
class WriterPathLawsTest {

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
                WriterPath<List<String>, Integer> path = WriterPath.pure(TEST_VALUE, LOG_MONOID);
                WriterPath<List<String>, Integer> result = path.map(Function.identity());
                assertThat(result.value()).isEqualTo(path.value());
                assertThat(result.written()).isEqualTo(path.written());
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for writer with log",
              () -> {
                WriterPath<List<String>, Integer> path =
                    WriterPath.writer(TEST_VALUE, List.of("log entry"), LOG_MONOID);
                WriterPath<List<String>, Integer> result = path.map(Function.identity());
                assertThat(result.value()).isEqualTo(path.value());
                assertThat(result.written()).isEqualTo(path.written());
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for pure value",
              () -> {
                WriterPath<List<String>, Integer> path = WriterPath.pure(TEST_VALUE, LOG_MONOID);
                WriterPath<List<String>, Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                WriterPath<List<String>, Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));
                assertThat(leftSide.value()).isEqualTo(rightSide.value());
                assertThat(leftSide.written()).isEqualTo(rightSide.written());
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                WriterPath<List<String>, Integer> path = WriterPath.pure(TEST_VALUE, LOG_MONOID);
                WriterPath<List<String>, Integer> leftSide =
                    path.map(INT_TO_STRING).map(STRING_LENGTH);
                WriterPath<List<String>, Integer> rightSide =
                    path.map(INT_TO_STRING.andThen(STRING_LENGTH));
                assertThat(leftSide.value()).isEqualTo(rightSide.value());
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    private final Function<Integer, WriterPath<List<String>, String>> intToWriterString =
        x -> WriterPath.writer("result:" + x, List.of("processed"), LOG_MONOID);

    private final Function<String, WriterPath<List<String>, Integer>> stringToWriterInt =
        s -> WriterPath.writer(s.length(), List.of("measured length"), LOG_MONOID);

    @TestFactory
    @DisplayName("Left Identity Law: WriterPath.pure(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity with logging function",
              () -> {
                int value = 10;
                WriterPath<List<String>, String> leftSide =
                    WriterPath.<List<String>, Integer>pure(value, LOG_MONOID)
                        .via(intToWriterString);
                WriterPath<List<String>, String> rightSide = intToWriterString.apply(value);
                assertThat(leftSide.value()).isEqualTo(rightSide.value());
                assertThat(leftSide.written()).isEqualTo(rightSide.written());
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(x -> WriterPath.pure(x)) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for pure value",
              () -> {
                WriterPath<List<String>, Integer> path = WriterPath.pure(TEST_VALUE, LOG_MONOID);
                WriterPath<List<String>, Integer> result =
                    path.via(x -> WriterPath.pure(x, LOG_MONOID));
                assertThat(result.value()).isEqualTo(path.value());
                assertThat(result.written()).isEqualTo(path.written());
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for writer with log",
              () -> {
                WriterPath<List<String>, Integer> path =
                    WriterPath.writer(TEST_VALUE, List.of("original"), LOG_MONOID);
                WriterPath<List<String>, Integer> result =
                    path.via(x -> WriterPath.pure(x, LOG_MONOID));
                assertThat(result.value()).isEqualTo(path.value());
                assertThat(result.written()).isEqualTo(path.written());
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for pure value",
              () -> {
                WriterPath<List<String>, Integer> path = WriterPath.pure(10, LOG_MONOID);
                WriterPath<List<String>, Integer> leftSide =
                    path.via(intToWriterString).via(stringToWriterInt);
                WriterPath<List<String>, Integer> rightSide =
                    path.via(x -> intToWriterString.apply(x).via(stringToWriterInt));
                assertThat(leftSide.value()).isEqualTo(rightSide.value());
                assertThat(leftSide.written()).isEqualTo(rightSide.written());
              }));
    }
  }

  @Nested
  @DisplayName("Additional Invariants")
  class AdditionalInvariantsTests {

    @TestFactory
    @DisplayName("Log accumulation is associative")
    Stream<DynamicTest> logAccumulationIsAssociative() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Log accumulates in order through via chain",
              () -> {
                WriterPath<List<String>, Integer> path =
                    WriterPath.writer(1, List.of("first"), LOG_MONOID);
                WriterPath<List<String>, Integer> result =
                    path.via(x -> WriterPath.writer(x + 1, List.of("second"), LOG_MONOID))
                        .via(x -> WriterPath.writer(x + 1, List.of("third"), LOG_MONOID));
                assertThat(result.value()).isEqualTo(3);
                assertThat(result.written()).containsExactly("first", "second", "third");
              }));
    }
  }
}
