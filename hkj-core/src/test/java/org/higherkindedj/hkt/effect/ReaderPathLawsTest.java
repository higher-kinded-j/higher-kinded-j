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
 * Law verification tests for ReaderPath.
 *
 * <p>Verifies that ReaderPath satisfies Functor and Monad laws:
 *
 * <h2>Functor Laws</h2>
 *
 * <ul>
 *   <li>Identity: {@code path.map(id) == path}
 *   <li>Composition: {@code path.map(f).map(g) == path.map(g.compose(f))}
 * </ul>
 *
 * <h2>Monad Laws</h2>
 *
 * <ul>
 *   <li>Left Identity: {@code ReaderPath.pure(a).via(f) == f(a)}
 *   <li>Right Identity: {@code path.via(ReaderPath::pure) == path}
 *   <li>Associativity: {@code path.via(f).via(g) == path.via(x -> f(x).via(g))}
 * </ul>
 */
@DisplayName("ReaderPath Law Verification Tests")
class ReaderPathLawsTest {

  // Test environment record
  record Config(String host, int port, boolean debug) {}

  private static final Config TEST_CONFIG = new Config("localhost", 8080, true);
  private static final Config ALT_CONFIG = new Config("remote", 443, false);
  private static final int TEST_VALUE = 42;

  // Test functions
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
                ReaderPath<Config, Integer> path = ReaderPath.pure(TEST_VALUE);
                ReaderPath<Config, Integer> result = path.map(Function.identity());
                assertThat(result.run(TEST_CONFIG)).isEqualTo(path.run(TEST_CONFIG));
              }),
          DynamicTest.dynamicTest(
              "Identity law holds for asks",
              () -> {
                ReaderPath<Config, Integer> path = ReaderPath.asks(Config::port);
                ReaderPath<Config, Integer> result = path.map(Function.identity());
                assertThat(result.run(TEST_CONFIG)).isEqualTo(path.run(TEST_CONFIG));
              }),
          DynamicTest.dynamicTest(
              "Identity law holds with different environments",
              () -> {
                ReaderPath<Config, Integer> path = ReaderPath.asks(Config::port);
                ReaderPath<Config, Integer> result = path.map(Function.identity());
                assertThat(result.run(ALT_CONFIG)).isEqualTo(path.run(ALT_CONFIG));
              }));
    }

    @TestFactory
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    Stream<DynamicTest> functorCompositionLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Composition law holds for pure value",
              () -> {
                ReaderPath<Config, Integer> path = ReaderPath.pure(TEST_VALUE);

                ReaderPath<Config, Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                ReaderPath<Config, Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(leftSide.run(TEST_CONFIG)).isEqualTo(rightSide.run(TEST_CONFIG));
              }),
          DynamicTest.dynamicTest(
              "Composition law holds for asks",
              () -> {
                ReaderPath<Config, Integer> path = ReaderPath.asks(Config::port);

                ReaderPath<Config, Integer> leftSide = path.map(ADD_ONE).map(DOUBLE);
                ReaderPath<Config, Integer> rightSide = path.map(ADD_ONE.andThen(DOUBLE));

                assertThat(leftSide.run(TEST_CONFIG)).isEqualTo(rightSide.run(TEST_CONFIG));
              }),
          DynamicTest.dynamicTest(
              "Composition with type-changing functions",
              () -> {
                ReaderPath<Config, Integer> path = ReaderPath.pure(TEST_VALUE);

                ReaderPath<Config, Integer> leftSide = path.map(INT_TO_STRING).map(STRING_LENGTH);
                ReaderPath<Config, Integer> rightSide =
                    path.map(INT_TO_STRING.andThen(STRING_LENGTH));

                assertThat(leftSide.run(TEST_CONFIG)).isEqualTo(rightSide.run(TEST_CONFIG));
              }));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsTests {

    // Monadic functions for testing
    private final Function<Integer, ReaderPath<Config, String>> intToReaderString =
        x -> ReaderPath.asks(c -> c.host() + ":" + x);

    private final Function<String, ReaderPath<Config, Integer>> stringToReaderInt =
        s -> ReaderPath.asks(c -> s.length() + c.port());

    private final Function<Integer, ReaderPath<Config, Integer>> safeDouble =
        x -> ReaderPath.pure(x * 2);

    @TestFactory
    @DisplayName("Left Identity Law: ReaderPath.pure(a).via(f) == f(a)")
    Stream<DynamicTest> leftIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Left identity with environment-dependent function",
              () -> {
                int value = 10;

                ReaderPath<Config, String> leftSide =
                    ReaderPath.<Config, Integer>pure(value).via(intToReaderString);
                ReaderPath<Config, String> rightSide = intToReaderString.apply(value);

                assertThat(leftSide.run(TEST_CONFIG)).isEqualTo(rightSide.run(TEST_CONFIG));
              }),
          DynamicTest.dynamicTest(
              "Left identity with pure function",
              () -> {
                int value = 25;

                ReaderPath<Config, Integer> leftSide =
                    ReaderPath.<Config, Integer>pure(value).via(safeDouble);
                ReaderPath<Config, Integer> rightSide = safeDouble.apply(value);

                assertThat(leftSide.run(TEST_CONFIG)).isEqualTo(rightSide.run(TEST_CONFIG));
              }));
    }

    @TestFactory
    @DisplayName("Right Identity Law: path.via(ReaderPath::pure) == path")
    Stream<DynamicTest> rightIdentityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Right identity holds for pure value",
              () -> {
                ReaderPath<Config, Integer> path = ReaderPath.pure(TEST_VALUE);

                ReaderPath<Config, Integer> result = path.via(ReaderPath::pure);

                assertThat(result.run(TEST_CONFIG)).isEqualTo(path.run(TEST_CONFIG));
              }),
          DynamicTest.dynamicTest(
              "Right identity holds for asks",
              () -> {
                ReaderPath<Config, Integer> path = ReaderPath.asks(Config::port);

                ReaderPath<Config, Integer> result = path.via(ReaderPath::pure);

                assertThat(result.run(TEST_CONFIG)).isEqualTo(path.run(TEST_CONFIG));
              }),
          DynamicTest.dynamicTest(
              "Right identity holds with different environments",
              () -> {
                ReaderPath<Config, Integer> path = ReaderPath.asks(Config::port);

                ReaderPath<Config, Integer> result = path.via(ReaderPath::pure);

                assertThat(result.run(ALT_CONFIG)).isEqualTo(path.run(ALT_CONFIG));
              }));
    }

    @TestFactory
    @DisplayName("Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    Stream<DynamicTest> associativityLaw() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Associativity holds for pure value",
              () -> {
                ReaderPath<Config, Integer> path = ReaderPath.pure(10);

                ReaderPath<Config, Integer> leftSide =
                    path.via(intToReaderString).via(stringToReaderInt);
                ReaderPath<Config, Integer> rightSide =
                    path.via(x -> intToReaderString.apply(x).via(stringToReaderInt));

                assertThat(leftSide.run(TEST_CONFIG)).isEqualTo(rightSide.run(TEST_CONFIG));
              }),
          DynamicTest.dynamicTest(
              "Associativity holds for asks",
              () -> {
                ReaderPath<Config, Integer> path = ReaderPath.asks(Config::port);

                ReaderPath<Config, Integer> leftSide =
                    path.via(intToReaderString).via(stringToReaderInt);
                ReaderPath<Config, Integer> rightSide =
                    path.via(x -> intToReaderString.apply(x).via(stringToReaderInt));

                assertThat(leftSide.run(TEST_CONFIG)).isEqualTo(rightSide.run(TEST_CONFIG));
              }),
          DynamicTest.dynamicTest(
              "Associativity with same-type chain",
              () -> {
                ReaderPath<Config, Integer> path = ReaderPath.pure(100);
                Function<Integer, ReaderPath<Config, Integer>> addTen =
                    x -> ReaderPath.pure(x + 10);

                ReaderPath<Config, Integer> leftSide = path.via(safeDouble).via(addTen);
                ReaderPath<Config, Integer> rightSide =
                    path.via(x -> safeDouble.apply(x).via(addTen));

                assertThat(leftSide.run(TEST_CONFIG)).isEqualTo(rightSide.run(TEST_CONFIG));
              }));
    }
  }

  @Nested
  @DisplayName("Additional Invariants")
  class AdditionalInvariantsTests {

    @TestFactory
    @DisplayName("Environment access is consistent")
    Stream<DynamicTest> environmentAccessIsConsistent() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "ask returns the provided environment",
              () -> {
                ReaderPath<Config, Config> path = ReaderPath.ask();
                assertThat(path.run(TEST_CONFIG)).isEqualTo(TEST_CONFIG);
                assertThat(path.run(ALT_CONFIG)).isEqualTo(ALT_CONFIG);
              }),
          DynamicTest.dynamicTest(
              "asks extracts correctly from environment",
              () -> {
                ReaderPath<Config, String> hostPath = ReaderPath.asks(Config::host);
                ReaderPath<Config, Integer> portPath = ReaderPath.asks(Config::port);

                assertThat(hostPath.run(TEST_CONFIG)).isEqualTo("localhost");
                assertThat(portPath.run(TEST_CONFIG)).isEqualTo(8080);
                assertThat(hostPath.run(ALT_CONFIG)).isEqualTo("remote");
                assertThat(portPath.run(ALT_CONFIG)).isEqualTo(443);
              }));
    }

    @TestFactory
    @DisplayName("local modifies environment correctly")
    Stream<DynamicTest> localModifiesEnvironment() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "local applies modification to environment",
              () -> {
                ReaderPath<Config, Integer> path = ReaderPath.asks(Config::port);
                ReaderPath<Config, Integer> modified =
                    path.local(c -> new Config(c.host(), c.port() + 100, c.debug()));

                assertThat(path.run(TEST_CONFIG)).isEqualTo(8080);
                assertThat(modified.run(TEST_CONFIG)).isEqualTo(8180);
              }));
    }
  }
}
