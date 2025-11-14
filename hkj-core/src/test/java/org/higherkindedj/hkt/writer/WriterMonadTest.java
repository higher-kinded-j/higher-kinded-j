// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.writer.WriterAssert.assertThatWriter;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WriterMonad Complete Test Suite")
class WriterMonadTest extends WriterTestBase {

  private WriterMonad<String> monad;

  @BeforeEach
  void setUpMonad() {
    monad = new WriterMonad<>(STRING_MONOID);
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Complete Monad Test Suite")
  class CompleteMonadTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      TypeClassTest.<WriterKind.Witness<String>>monad(WriterMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(WriterFunctor.class)
          .withApFrom(WriterApplicative.class)
          .withFlatMapFrom(WriterMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(WriterMonadTest.class);

      if (result.hasErrors()) {
        result.printReport();
        throw new AssertionError("Test structure validation failed");
      }
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
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<WriterKind.Witness<String>>monad(WriterMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<WriterKind.Witness<String>>monad(WriterMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(WriterFunctor.class)
          .withApFrom(WriterApplicative.class)
          .withFlatMapFrom(WriterMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<WriterKind.Witness<String>>monad(WriterMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<WriterKind.Witness<String>>monad(WriterMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Core Type Tests")
  class CoreTypeTests {

    @Test
    @DisplayName("Test Writer core operations")
    void testWriterCoreOperations() {
      Writer<String, Integer> writer = writerOf("TestLog;", 42);

      CoreTypeTest.<String, Integer>writer(Writer.class)
          .withWriter(writer)
          .withMonoid(STRING_MONOID)
          .withMappers(validMapper)
          .testAll();
    }

    @Test
    @DisplayName("Test WriterKindHelper operations")
    void testWriterKindHelperOperations() {
      Writer<String, Integer> writer = writerOf("TestLog;", 42);

      CoreTypeTest.writerKindHelper(writer).test();
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
