// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.WriterAssert.assertThatWriter;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
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

@DisplayName("WriterMonad")
class WriterMonadTest extends WriterTestBase {

  private Monad<WriterKind.Witness<String>> monad;

  @BeforeEach
  void setUpMonad() {
    monad = Instances.writer(STRING_MONOID);
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
    void rightIdentity(String label, Kind<WriterKind.Witness<String>, Integer> ma) {
      MonadLaws.assertRightIdentity(monad, ma, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("fixtures")
    void associativity(String label, Kind<WriterKind.Witness<String>, Integer> ma) {
      MonadLaws.assertAssociativity(monad, ma, testFunction, chainFunction, equalityChecker);
    }

    static Stream<Arguments> fixtures() {
      return Stream.of(
          Arguments.of("Writer(\"\", 0)", WRITER.widen(new Writer<String, Integer>("", 0))),
          Arguments.of("Writer(\"log\", 42)", WRITER.widen(new Writer<String, Integer>("log", 42))),
          Arguments.of("Writer(\"x\", -1)", WRITER.widen(new Writer<String, Integer>("x", -1))));
    }

    static Stream<Arguments> values() {
      return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("flatMap() sequences computations and combines logs")
    void flatMapSequencesComputationsAndCombinesLogs() {
      Kind<WriterKind.Witness<String>, String> result = monad.flatMap(validFlatMapper, validKind);

      Writer<String, String> writer = narrowToWriter(result);
      assertThatWriter(writer)
          .hasLog(DEFAULT_LOG + "Mapped:" + DEFAULT_VALUE + ";")
          .hasValue("Result:" + DEFAULT_VALUE);
    }

    @Test
    @DisplayName("flatMap() can chain multiple operations")
    void flatMapCanChainMultipleOperations() {
      Kind<WriterKind.Witness<String>, String> result =
          monad.flatMap(
              s ->
                  monad.flatMap(
                      s2 -> WRITER.widen(writerOf("final;", s2 + "!")),
                      WRITER.widen(writerOf("second;", s + "2"))),
              WRITER.widen(writerOf("first;", "1")));

      Writer<String, String> writer = narrowToWriter(result);
      assertThatWriter(writer).hasLog("first;second;final;").hasValue("12!");
    }

    @Test
    @DisplayName("of() creates Writer with empty log")
    void ofCreatesWriterWithEmptyLog() {
      Kind<WriterKind.Witness<String>, String> result = monad.of("success");

      Writer<String, String> writer = narrowToWriter(result);
      assertThatWriter(writer).hasEmptyLog().hasValue("success");
    }

    @Test
    @DisplayName("of() allows null values")
    void ofAllowsNullValues() {
      Kind<WriterKind.Witness<String>, String> result = monad.of(null);

      Writer<String, String> writer = narrowToWriter(result);
      assertThatWriter(writer).hasEmptyLog().hasNullValue();
    }

    @Test
    @DisplayName("map() applies function and preserves log")
    void mapAppliesFunctionAndPreservesLog() {
      Kind<WriterKind.Witness<String>, String> result = monad.map(validMapper, validKind);

      Writer<String, String> writer = narrowToWriter(result);
      assertThatWriter(writer).hasLog(DEFAULT_LOG).hasValue("Value:" + DEFAULT_VALUE);
    }

    @Test
    @DisplayName("ap() applies function to value and combines logs")
    void apAppliesFunctionAndCombinesLogs() {
      Kind<WriterKind.Witness<String>, Function<Integer, String>> funcKind =
          monad.of(i -> "value:" + i);
      Kind<WriterKind.Witness<String>, Integer> valueKind = monad.of(42);

      Kind<WriterKind.Witness<String>, String> result = monad.ap(funcKind, valueKind);

      Writer<String, String> writer = narrowToWriter(result);
      assertThatWriter(writer).hasValue("value:42");
    }

    @Test
    @DisplayName("map2() combines two Writers")
    void map2CombinesTwoWriters() {
      Kind<WriterKind.Witness<String>, Integer> w1 = WRITER.widen(writerOf("L1;", 10));
      Kind<WriterKind.Witness<String>, String> w2 = WRITER.widen(writerOf("L2;", "test"));

      BiFunction<Integer, String, String> combiner = (i, s) -> s + ":" + i;
      Kind<WriterKind.Witness<String>, String> result = monad.map2(w1, w2, combiner);

      Writer<String, String> writer = narrowToWriter(result);
      assertThatWriter(writer).hasLog("L1;L2;").hasValue("test:10");
    }
  }

  @Nested
  @DisplayName("KindHelper Round-Trip Tests")
  class KindHelperRoundTripTests {

    @Test
    @DisplayName("Test WriterKindHelper operations")
    void testWriterKindHelperOperations() {
      Writer<String, Integer> writer = writerOf("TestLog;", 42);

      KindHelperTests.writerKindHelper(writer).test();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Deep flatMap chaining")
    void deepFlatMapChaining() {
      Kind<WriterKind.Witness<String>, Integer> start = WRITER.widen(writerOf("start;", 1));

      Kind<WriterKind.Witness<String>, Integer> result = start;
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result =
            monad.flatMap(
                x -> WRITER.widen(writerOf("step" + increment + ";", x + increment)), result);
      }

      Writer<String, Integer> writer = narrowToWriter(result);
      assertThatWriter(writer)
          .hasValue(46) // 1 + 0 + 1 + 2 + ... + 9 = 46
          .satisfiesLog(
              log -> {
                assertThat(log).contains("start;");
                assertThat(log).contains("step0;");
                assertThat(log).contains("step9;");
              });
    }

    @Test
    @DisplayName("flatMap with empty log preservation")
    void flatMapWithEmptyLogPreservation() {
      Kind<WriterKind.Witness<String>, Integer> start = monad.of(1);

      Kind<WriterKind.Witness<String>, Integer> result = monad.flatMap(x -> monad.of(x + 1), start);

      Writer<String, Integer> writer = narrowToWriter(result);
      assertThatWriter(writer).hasEmptyLog().hasValue(2);
    }

    @Test
    @DisplayName("Test log accumulation order")
    void testLogAccumulationOrder() {
      Kind<WriterKind.Witness<String>, Integer> w1 = WRITER.widen(writerOf("A;", 1));
      Kind<WriterKind.Witness<String>, Integer> w2 = WRITER.widen(writerOf("B;", 2));
      Kind<WriterKind.Witness<String>, Integer> w3 = WRITER.widen(writerOf("C;", 3));

      Kind<WriterKind.Witness<String>, Integer> result =
          monad.flatMap(x1 -> monad.flatMap(x2 -> monad.map(x3 -> x1 + x2 + x3, w3), w2), w1);

      Writer<String, Integer> writer = narrowToWriter(result);
      assertThatWriter(writer).hasLog("A;B;C;").hasValue(6);
    }

    @Test
    @DisplayName("Test with null value in chain")
    void testWithNullValueInChain() {
      Writer<String, Integer> nullWriter = writerOf("null;", null);
      Kind<WriterKind.Witness<String>, Integer> nullKind = WRITER.widen(nullWriter);

      Kind<WriterKind.Witness<String>, String> result =
          monad.flatMap(i -> WRITER.widen(writerOf("process;", String.valueOf(i))), nullKind);

      Writer<String, String> writer = narrowToWriter(result);
      assertThatWriter(writer).hasLog("null;process;").hasValue("null");
    }

    @Test
    @DisplayName("Test tell with flatMap")
    void testTellWithFlatMap() {
      Kind<WriterKind.Witness<String>, Integer> start = monad.of(5);

      Kind<WriterKind.Witness<String>, String> result =
          monad.flatMap(
              i ->
                  monad.flatMap(
                      unit -> WRITER.widen(writerOf("end;", "complete")),
                      WRITER.tell("logged:" + i + ";")),
              start);

      Writer<String, String> writer = narrowToWriter(result);
      assertThatWriter(writer).hasLog("logged:5;end;").hasValue("complete");
    }
  }
}
