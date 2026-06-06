// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.WriterAssert.assertThatWriter;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
    @MethodSource("org.higherkindedj.hkt.writer.WriterLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(monad, value, testFunction, equalityChecker);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.writer.WriterLawFixtures#kinds")
    void rightIdentity(String label, Kind<WriterKind.Witness<String>, Integer> ma) {
      MonadLaws.assertRightIdentity(monad, ma, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.writer.WriterLawFixtures#kinds")
    void associativity(String label, Kind<WriterKind.Witness<String>, Integer> ma) {
      MonadLaws.assertAssociativity(monad, ma, testFunction, chainFunction, equalityChecker);
    }
  }

  @Test
  @DisplayName("Monad contract — operations, validations & exceptions (laws verified above)")
  void monadContract() {
    TypeClassContract.<WriterKind.Witness<String>>monad(WriterMonad.class)
        .<Integer>instance(monad)
        .<String>withKind(validKind)
        .withMonadOperations(
            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("flatMap() sequences computations and concatenates logs")
    void flatMapSequencesComputationsAndCombinesLogs() {
      var result = monad.flatMap(validFlatMapper, validKind);
      assertThatWriter(narrowToWriter(result))
          .hasLog(DEFAULT_LOG + "Mapped:" + DEFAULT_VALUE + ";")
          .hasValue("Result:" + DEFAULT_VALUE);
    }

    @Test
    @DisplayName("flatMap() chains multiple operations, accumulating every log")
    void flatMapCanChainMultipleOperations() {
      var result =
          monad.flatMap(
              s ->
                  monad.flatMap(
                      s2 -> WRITER.widen(writerOf("final;", s2 + "!")),
                      WRITER.widen(writerOf("second;", s + "2"))),
              WRITER.widen(writerOf("first;", "1")));

      assertThatWriter(narrowToWriter(result)).hasLog("first;second;final;").hasValue("12!");
    }

    @Test
    @DisplayName("of() creates a Writer with an empty log")
    void ofCreatesWriterWithEmptyLog() {
      assertThatWriter(narrowToWriter(monad.of("success"))).hasEmptyLog().hasValue("success");
    }

    @Test
    @DisplayName("of() allows null values")
    void ofAllowsNullValues() {
      assertThatWriter(narrowToWriter(monad.of(null))).hasEmptyLog().hasNullValue();
    }

    @Test
    @DisplayName("map() applies the function and preserves the log")
    void mapAppliesFunctionAndPreservesLog() {
      var result = monad.map(validMapper, validKind);
      assertThatWriter(narrowToWriter(result))
          .hasLog(DEFAULT_LOG)
          .hasValue("Value:" + DEFAULT_VALUE);
    }

    @Test
    @DisplayName("ap() applies the function and concatenates logs")
    void apAppliesFunctionAndCombinesLogs() {
      var funcKind = monad.of((Function<Integer, String>) i -> "value:" + i);
      var result = monad.ap(funcKind, monad.of(42));
      assertThatWriter(narrowToWriter(result)).hasValue("value:42");
    }

    @Test
    @DisplayName("map2() combines two Writers and concatenates their logs")
    void map2CombinesTwoWriters() {
      Kind<WriterKind.Witness<String>, Integer> w1 = WRITER.widen(writerOf("L1;", 10));
      Kind<WriterKind.Witness<String>, String> w2 = WRITER.widen(writerOf("L2;", "test"));
      BiFunction<Integer, String, String> combiner = (i, s) -> s + ":" + i;

      var result = monad.map2(w1, w2, combiner);
      assertThatWriter(narrowToWriter(result)).hasLog("L1;L2;").hasValue("test:10");
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("deep flatMap chaining accumulates logs in order")
    void deepFlatMapChaining() {
      Kind<WriterKind.Witness<String>, Integer> result = WRITER.widen(writerOf("start;", 1));
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result =
            monad.flatMap(
                x -> WRITER.widen(writerOf("step" + increment + ";", x + increment)), result);
      }

      assertThatWriter(narrowToWriter(result))
          .hasValue(46) // 1 + 0 + 1 + 2 + ... + 9 = 46
          .satisfiesLog(log -> assertThat(log).contains("start;", "step0;", "step9;"));
    }

    @Test
    @DisplayName("flatMap() over an empty-log Writer leaves the log empty")
    void flatMapWithEmptyLogPreservation() {
      var result = monad.flatMap(x -> monad.of(x + 1), monad.of(1));
      assertThatWriter(narrowToWriter(result)).hasEmptyLog().hasValue(2);
    }

    @Test
    @DisplayName("flatMap() passes a null value through the chain")
    void flatMapHandlesNullValueInChain() {
      Kind<WriterKind.Witness<String>, Integer> nullKind = WRITER.widen(writerOf("null;", null));

      var result =
          monad.flatMap(i -> WRITER.widen(writerOf("process;", String.valueOf(i))), nullKind);
      assertThatWriter(narrowToWriter(result)).hasLog("null;process;").hasValue("null");
    }

    @Test
    @DisplayName("flatMap() interleaves tell() logs with computed values")
    void flatMapInterleavesTellLogs() {
      var result =
          monad.flatMap(
              i ->
                  monad.flatMap(
                      _ -> WRITER.widen(writerOf("end;", "complete")),
                      WRITER.tell("logged:" + i + ";")),
              monad.of(5));

      assertThatWriter(narrowToWriter(result)).hasLog("logged:5;end;").hasValue("complete");
    }
  }
}
