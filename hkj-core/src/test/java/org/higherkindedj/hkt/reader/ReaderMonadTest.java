// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.ReaderAssert.assertThatReader;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.api.KindHelperTests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("ReaderMonad")
class ReaderMonadTest extends ReaderTestBase {

  private Monad<ReaderKind.Witness<TestConfig>> monad;

  @BeforeEach
  void setUpMonad() {
    monad = Instances.monad(reader());
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(monad, value, testFunction, equalityChecker);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("fixtures")
    void rightIdentity(String label, Kind<ReaderKind.Witness<TestConfig>, Integer> ma) {
      MonadLaws.assertRightIdentity(monad, ma, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("fixtures")
    void associativity(String label, Kind<ReaderKind.Witness<TestConfig>, Integer> ma) {
      MonadLaws.assertAssociativity(monad, ma, testFunction, chainFunction, equalityChecker);
    }

    static Stream<Arguments> fixtures() {
      return Stream.of(
          Arguments.of("reader(url length)", READER.reader((TestConfig cfg) -> cfg.url().length())),
          Arguments.of("pure(42)", READER.reader((TestConfig cfg) -> 42)),
          Arguments.of(
              "reader(maxConnections)", READER.reader((TestConfig cfg) -> cfg.maxConnections())));
    }

    static Stream<Arguments> values() {
      return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("of() creates constant Reader")
    void ofCreatesConstantReader() {
      Kind<ReaderKind.Witness<TestConfig>, String> result = monad.of("constantValue");

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader).whenRunWith(TEST_CONFIG).produces("constantValue");
      assertThatReader(reader).whenRunWith(ALTERNATIVE_CONFIG).produces("constantValue");
      assertThatReader(reader).isConstantFor(TEST_CONFIG, ALTERNATIVE_CONFIG);
    }

    @Test
    @DisplayName("map() applies function to result")
    void mapAppliesFunctionToResult() {
      Kind<ReaderKind.Witness<TestConfig>, String> result = monad.map(validMapper, validKind);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader)
          .whenRunWith(TEST_CONFIG)
          .produces("Connections: " + DEFAULT_MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("flatMap() sequences computations")
    void flatMapSequencesComputations() {
      Kind<ReaderKind.Witness<TestConfig>, String> result =
          monad.flatMap(validFlatMapper, validKind);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader)
          .whenRunWith(TEST_CONFIG)
          .produces(DEFAULT_URL + ":" + DEFAULT_MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("flatMap() passes environment correctly")
    void flatMapPassesEnvironmentCorrectly() {
      Function<Integer, Kind<ReaderKind.Witness<TestConfig>, String>> flatMapper =
          conns -> READER.widen(Reader.of(config -> config.url() + " [" + conns + "]"));

      Kind<ReaderKind.Witness<TestConfig>, String> result = monad.flatMap(flatMapper, validKind);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader)
          .whenRunWith(TEST_CONFIG)
          .produces(DEFAULT_URL + " [" + DEFAULT_MAX_CONNECTIONS + "]");
    }

    @Test
    @DisplayName("ap() applies function to value - both from environment")
    void apAppliesFunctionToValue() {
      Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> funcKind =
          READER.widen(Reader.of(config -> i -> config.url() + ":" + i));
      Kind<ReaderKind.Witness<TestConfig>, Integer> valueKind = validKind;

      Kind<ReaderKind.Witness<TestConfig>, String> result = monad.ap(funcKind, valueKind);

      Reader<TestConfig, String> reader = narrowToReader(result);
      assertThatReader(reader)
          .whenRunWith(TEST_CONFIG)
          .produces(DEFAULT_URL + ":" + DEFAULT_MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("ap() works with constant function and value")
    void apWorksWithConstantFunctionAndValue() {
      Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> funcKind =
          monad.of(i -> "Num" + i);
      Kind<ReaderKind.Witness<TestConfig>, Integer> valueKind = monad.of(100);

      Kind<ReaderKind.Witness<TestConfig>, String> result = monad.ap(funcKind, valueKind);

      assertThat(runReader(result, TEST_CONFIG)).isEqualTo("Num100");
    }

    @Test
    @DisplayName("map2() combines two Reader values")
    void map2CombinesTwoReaderValues() {
      Kind<ReaderKind.Witness<TestConfig>, Integer> r1 = validKind;
      Kind<ReaderKind.Witness<TestConfig>, Integer> r2 = validKind2;

      BiFunction<Integer, Integer, String> combiner = (a, b) -> a + "," + b;
      Kind<ReaderKind.Witness<TestConfig>, String> result = monad.map2(r1, r2, combiner);

      assertThat(runReader(result, TEST_CONFIG))
          .isEqualTo(DEFAULT_MAX_CONNECTIONS + "," + (DEFAULT_MAX_CONNECTIONS * 2));
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Deep flatMap chaining")
    void deepFlatMapChaining() {
      Kind<ReaderKind.Witness<TestConfig>, Integer> start = READER.widen(Reader.of(config -> 1));

      Kind<ReaderKind.Witness<TestConfig>, Integer> result = start;
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result = monad.flatMap(x -> monad.of(x + increment), result);
      }

      assertThat(runReader(result, TEST_CONFIG)).isEqualTo(46); // 1 + 0 + 1 + 2 + ... + 9 = 46
    }

    @Test
    @DisplayName("ask() identity - returns environment")
    void askIdentityReturnsEnvironment() {
      Kind<ReaderKind.Witness<TestConfig>, TestConfig> askReader = READER.ask();
      assertThat(runReader(askReader, TEST_CONFIG)).isSameAs(TEST_CONFIG);
    }

    @Test
    @DisplayName("constant() ignores environment")
    void constantIgnoresEnvironment() {
      Kind<ReaderKind.Witness<TestConfig>, String> constantReader = READER.constant("fixed");

      Reader<TestConfig, String> reader = narrowToReader(constantReader);
      assertThatReader(reader).isConstantFor(TEST_CONFIG, ALTERNATIVE_CONFIG);
    }

    @Test
    @DisplayName("map() preserves environment threading")
    void mapPreservesEnvironmentThreading() {
      Kind<ReaderKind.Witness<TestConfig>, TestConfig> askReader = READER.ask();
      Kind<ReaderKind.Witness<TestConfig>, String> mappedAsk =
          monad.map(TestConfig::url, askReader);

      assertThat(runReader(mappedAsk, TEST_CONFIG)).isEqualTo(DEFAULT_URL);
    }

    @Test
    @DisplayName("Exception in function propagates through map")
    void exceptionInFunctionPropagatesThroughMap() {
      RuntimeException testException = new RuntimeException("Test exception: map test");
      Function<Integer, String> throwingMapper =
          i -> {
            throw testException;
          };

      Kind<ReaderKind.Witness<TestConfig>, String> result = monad.map(throwingMapper, validKind);
      Reader<TestConfig, String> reader = narrowToReader(result);

      assertThatThrownBy(() -> reader.run(TEST_CONFIG)).isSameAs(testException);
    }

    @Test
    @DisplayName("Exception in function propagates through flatMap")
    void exceptionInFunctionPropagatesThroughFlatMap() {
      RuntimeException testException = new RuntimeException("Test exception: flatMap test");
      Function<Integer, Kind<ReaderKind.Witness<TestConfig>, String>> throwingFlatMapper =
          i -> {
            throw testException;
          };

      Kind<ReaderKind.Witness<TestConfig>, String> result =
          monad.flatMap(throwingFlatMapper, validKind);
      Reader<TestConfig, String> reader = narrowToReader(result);

      assertThatThrownBy(() -> reader.run(TEST_CONFIG)).isSameAs(testException);
    }

    @Test
    @DisplayName("Exception in ap function propagates")
    void exceptionInApFunctionPropagates() {
      RuntimeException testException = new RuntimeException("Test exception: ap test");
      Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> funcKind =
          monad.of(
              i -> {
                throw testException;
              });

      Kind<ReaderKind.Witness<TestConfig>, String> result = monad.ap(funcKind, validKind);
      Reader<TestConfig, String> reader = narrowToReader(result);

      assertThatThrownBy(() -> reader.run(TEST_CONFIG)).isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("mapN Tests")
  class MapNTests {
    Kind<ReaderKind.Witness<TestConfig>, Integer> r1 =
        READER.widen(Reader.of(TestConfig::maxConnections));
    Kind<ReaderKind.Witness<TestConfig>, String> r2 = READER.widen(Reader.of(TestConfig::url));
    Kind<ReaderKind.Witness<TestConfig>, Double> r3 =
        READER.widen(Reader.of(config -> config.maxConnections() * 1.5));
    Kind<ReaderKind.Witness<TestConfig>, Boolean> r4 =
        READER.widen(Reader.of(config -> config.url().startsWith("jdbc")));

    @Test
    @DisplayName("map2() combines results")
    void map2CombinesResults() {
      Kind<ReaderKind.Witness<TestConfig>, String> result =
          monad.map2(r1, r2, (conns, url) -> url + " (" + conns + ")");
      assertThat(runReader(result, TEST_CONFIG))
          .isEqualTo(DEFAULT_URL + " (" + DEFAULT_MAX_CONNECTIONS + ")");
    }

    @Test
    @DisplayName("map3() combines results")
    void map3CombinesResults() {
      Function3<Integer, String, Double, String> f3 =
          (i, s, d) -> String.format("I:%d S:%s D:%.1f", i, s, d);
      Kind<ReaderKind.Witness<TestConfig>, String> result = monad.map3(r1, r2, r3, f3);
      assertThat(runReader(result, TEST_CONFIG))
          .isEqualTo(
              String.format(
                  "I:%d S:%s D:%.1f",
                  DEFAULT_MAX_CONNECTIONS, DEFAULT_URL, DEFAULT_MAX_CONNECTIONS * 1.5));
    }

    @Test
    @DisplayName("map4() combines results")
    void map4CombinesResults() {
      Function4<Integer, String, Double, Boolean, String> f4 =
          (i, s, d, b) -> String.format("I:%d S:%s D:%.1f B:%b", i, s, d, b);
      Kind<ReaderKind.Witness<TestConfig>, String> result = monad.map4(r1, r2, r3, r4, f4);
      assertThat(runReader(result, TEST_CONFIG))
          .isEqualTo(
              String.format(
                  "I:%d S:%s D:%.1f B:%b",
                  DEFAULT_MAX_CONNECTIONS,
                  DEFAULT_URL,
                  DEFAULT_MAX_CONNECTIONS * 1.5,
                  DEFAULT_URL.startsWith("jdbc")));
    }
  }

  @Nested
  @DisplayName("KindHelper Round-Trip Tests")
  class KindHelperRoundTripTests {

    @Test
    @DisplayName("Test ReaderKindHelper")
    void testReaderKindHelper() {
      Reader<TestConfig, String> reader = Reader.constant("test");

      KindHelperTests.readerKindHelper(reader).test();
    }
  }
}
