// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.higherkindedj.hkt.typeclass.StringMonoid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WriterMonad Complete Test Suite")
class WriterMonadTest extends TypeClassTestBase<WriterKind.Witness<String>, Integer, String> {

  private Monoid<String> stringMonoid;
  private WriterMonad<String> monad;

  @Override
  protected Kind<WriterKind.Witness<String>, Integer> createValidKind() {
    return WRITER.widen(new Writer<>("Log1;", 42));
  }

  @Override
  protected Kind<WriterKind.Witness<String>, Integer> createValidKind2() {
    return WRITER.widen(new Writer<>("Log2;", 24));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected Function<Integer, Kind<WriterKind.Witness<String>, String>> createValidFlatMapper() {
    return i -> WRITER.widen(new Writer<>("flat:" + i + ";", "flat:" + i));
  }

  @Override
  protected Kind<WriterKind.Witness<String>, Function<Integer, String>> createValidFunctionKind() {
    return WRITER.widen(new Writer<>("FuncLog;", TestFunctions.INT_TO_STRING));
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "Result:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return 42;
  }

  @Override
  protected Function<Integer, Kind<WriterKind.Witness<String>, String>> createTestFunction() {
    return i -> WRITER.widen(new Writer<>("test:" + i + ";", "test:" + i));
  }

  @Override
  protected Function<String, Kind<WriterKind.Witness<String>, String>> createChainFunction() {
    return s -> WRITER.widen(new Writer<>("chain:" + s + ";", s + "!"));
  }

  @Override
  protected BiPredicate<Kind<WriterKind.Witness<String>, ?>, Kind<WriterKind.Witness<String>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> WRITER.narrow(k1).equals(WRITER.narrow(k2));
  }

  @BeforeEach
  void setUpMonad() {
    stringMonoid = new StringMonoid();
    monad = new WriterMonad<>(stringMonoid);
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

      Writer<String, String> writer = WRITER.narrow(result);
      assertThat(writer.log()).isEqualTo("Log1;flat:42;");
      assertThat(writer.value()).isEqualTo("flat:42");
    }

    @Test
    @DisplayName("flatMap() can chain multiple operations")
    void flatMapCanChainMultipleOperations() {
      Kind<WriterKind.Witness<String>, String> result =
          monad.flatMap(
              s ->
                  monad.flatMap(
                      s2 -> WRITER.widen(new Writer<>("final;", s2 + "!")),
                      WRITER.widen(new Writer<>("second;", s + "2"))),
              WRITER.widen(new Writer<>("first;", "1")));

      Writer<String, String> writer = WRITER.narrow(result);
      assertThat(writer.log()).isEqualTo("first;second;final;");
      assertThat(writer.value()).isEqualTo("12!");
    }

    @Test
    @DisplayName("of() creates Writer with empty log")
    void ofCreatesWriterWithEmptyLog() {
      Kind<WriterKind.Witness<String>, String> result = monad.of("success");

      Writer<String, String> writer = WRITER.narrow(result);
      assertThat(writer.log()).isEqualTo(stringMonoid.empty());
      assertThat(writer.value()).isEqualTo("success");
    }

    @Test
    @DisplayName("of() allows null values")
    void ofAllowsNullValues() {
      Kind<WriterKind.Witness<String>, String> result = monad.of(null);

      Writer<String, String> writer = WRITER.narrow(result);
      assertThat(writer.log()).isEqualTo(stringMonoid.empty());
      assertThat(writer.value()).isNull();
    }

    @Test
    @DisplayName("map() applies function and preserves log")
    void mapAppliesFunctionAndPreservesLog() {
      Kind<WriterKind.Witness<String>, String> result = monad.map(validMapper, validKind);

      Writer<String, String> writer = WRITER.narrow(result);
      assertThat(writer.log()).isEqualTo("Log1;");
      assertThat(writer.value()).isEqualTo("42");
    }

    @Test
    @DisplayName("ap() applies function to value and combines logs")
    void apAppliesFunctionAndCombinesLogs() {
      Kind<WriterKind.Witness<String>, Function<Integer, String>> funcKind =
          monad.of(i -> "value:" + i);
      Kind<WriterKind.Witness<String>, Integer> valueKind = monad.of(42);

      Kind<WriterKind.Witness<String>, String> result = monad.ap(funcKind, valueKind);

      Writer<String, String> writer = WRITER.narrow(result);
      assertThat(writer.value()).isEqualTo("value:42");
    }

    @Test
    @DisplayName("map2() combines two Writers")
    void map2CombinesTwoWriters() {
      Kind<WriterKind.Witness<String>, Integer> w1 = WRITER.widen(new Writer<>("L1;", 10));
      Kind<WriterKind.Witness<String>, String> w2 = WRITER.widen(new Writer<>("L2;", "test"));

      BiFunction<Integer, String, String> combiner = (i, s) -> s + ":" + i;
      Kind<WriterKind.Witness<String>, String> result = monad.map2(w1, w2, combiner);

      Writer<String, String> writer = WRITER.narrow(result);
      assertThat(writer.log()).isEqualTo("L1;L2;");
      assertThat(writer.value()).isEqualTo("test:10");
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
      Writer<String, Integer> writer = new Writer<>("TestLog;", 42);

      CoreTypeTest.<String, Integer>writer(Writer.class)
          .withWriter(writer)
          .withMonoid(stringMonoid)
          .withMappers(validMapper)
          .testAll();
    }

    @Test
    @DisplayName("Test WriterKindHelper operations")
    void testWriterKindHelperOperations() {
      Writer<String, Integer> writer = new Writer<>("TestLog;", 42);

      CoreTypeTest.writerKindHelper(writer).test();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Deep flatMap chaining")
    void deepFlatMapChaining() {
      Kind<WriterKind.Witness<String>, Integer> start = WRITER.widen(new Writer<>("start;", 1));

      Kind<WriterKind.Witness<String>, Integer> result = start;
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result =
            monad.flatMap(
                x -> WRITER.widen(new Writer<>("step" + increment + ";", x + increment)), result);
      }

      Writer<String, Integer> writer = WRITER.narrow(result);
      assertThat(writer.value()).isEqualTo(46); // 1 + 0 + 1 + 2 + ... + 9 = 46
      assertThat(writer.log()).contains("start;");
      assertThat(writer.log()).contains("step0;");
      assertThat(writer.log()).contains("step9;");
    }

    @Test
    @DisplayName("flatMap with empty log preservation")
    void flatMapWithEmptyLogPreservation() {
      Kind<WriterKind.Witness<String>, Integer> start = monad.of(1);

      Kind<WriterKind.Witness<String>, Integer> result = monad.flatMap(x -> monad.of(x + 1), start);

      Writer<String, Integer> writer = WRITER.narrow(result);
      assertThat(writer.log()).isEqualTo(stringMonoid.empty());
      assertThat(writer.value()).isEqualTo(2);
    }

    @Test
    @DisplayName("Test log accumulation order")
    void testLogAccumulationOrder() {
      Kind<WriterKind.Witness<String>, Integer> w1 = WRITER.widen(new Writer<>("A;", 1));
      Kind<WriterKind.Witness<String>, Integer> w2 = WRITER.widen(new Writer<>("B;", 2));
      Kind<WriterKind.Witness<String>, Integer> w3 = WRITER.widen(new Writer<>("C;", 3));

      Kind<WriterKind.Witness<String>, Integer> result =
          monad.flatMap(x1 -> monad.flatMap(x2 -> monad.map(x3 -> x1 + x2 + x3, w3), w2), w1);

      Writer<String, Integer> writer = WRITER.narrow(result);
      assertThat(writer.log()).isEqualTo("A;B;C;");
      assertThat(writer.value()).isEqualTo(6);
    }

    @Test
    @DisplayName("Test with null value in chain")
    void testWithNullValueInChain() {
      Kind<WriterKind.Witness<String>, Integer> nullWriter =
          WRITER.widen(new Writer<>("null;", null));

      Kind<WriterKind.Witness<String>, String> result =
          monad.flatMap(i -> WRITER.widen(new Writer<>("process;", String.valueOf(i))), nullWriter);

      Writer<String, String> writer = WRITER.narrow(result);
      assertThat(writer.log()).isEqualTo("null;process;");
      assertThat(writer.value()).isEqualTo("null");
    }

    @Test
    @DisplayName("Test tell with flatMap")
    void testTellWithFlatMap() {
      Kind<WriterKind.Witness<String>, Integer> start = monad.of(5);

      Kind<WriterKind.Witness<String>, String> result =
          monad.flatMap(
              i ->
                  monad.flatMap(
                      unit -> WRITER.widen(new Writer<>("end;", "complete")),
                      WRITER.tell("logged:" + i + ";")),
              start);

      Writer<String, String> writer = WRITER.narrow(result);
      assertThat(writer.log()).isEqualTo("logged:5;end;");
      assertThat(writer.value()).isEqualTo("complete");
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("flatMap efficient with many operations")
    void flatMapEfficientWithManyOperations() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Kind<WriterKind.Witness<String>, Integer> start = WRITER.widen(new Writer<>("start;", 1));

        Kind<WriterKind.Witness<String>, Integer> result = start;
        for (int i = 0; i < 100; i++) {
          final int increment = i;
          result =
              monad.flatMap(
                  x -> WRITER.widen(new Writer<>("s" + increment + ";", x + increment)), result);
        }

        int expectedSum = 1 + (99 * 100) / 2;
        Writer<String, Integer> writer = WRITER.narrow(result);
        assertThat(writer.value()).isEqualTo(expectedSum);
      }
    }

    @Test
    @DisplayName("Multiple map2 operations")
    void multipleMap2Operations() {
      Kind<WriterKind.Witness<String>, Integer> w1 = WRITER.widen(new Writer<>("A;", 10));
      Kind<WriterKind.Witness<String>, Integer> w2 = WRITER.widen(new Writer<>("B;", 20));
      Kind<WriterKind.Witness<String>, Integer> w3 = WRITER.widen(new Writer<>("C;", 30));

      // Combine w1 and w2
      Kind<WriterKind.Witness<String>, Integer> combined12 = monad.map2(w1, w2, Integer::sum);

      // Combine result with w3
      Kind<WriterKind.Witness<String>, Integer> combined123 =
          monad.map2(combined12, w3, Integer::sum);

      Writer<String, Integer> writer = WRITER.narrow(combined123);
      assertThat(writer.log()).isEqualTo("A;B;C;");
      assertThat(writer.value()).isEqualTo(60);
    }

    @Test
    @DisplayName("Complex nested operations maintain log order")
    void complexNestedOperationsMaintainLogOrder() {
      Kind<WriterKind.Witness<String>, Integer> start = monad.of(1);

      for (int i = 0; i < 5; i++) {
        final int step = i;
        Kind<WriterKind.Witness<String>, Function<Integer, Integer>> funcKind =
            WRITER.widen(new Writer<>("Step" + step + ";", x -> x + 1));
        start = monad.ap(funcKind, start);
      }

      Writer<String, Integer> writer = WRITER.narrow(start);
      // ap combines logs as: function log + value log
      // In the loop, the function is created fresh each time with its log,
      // and start accumulates previous logs, so we get reverse order
      assertThat(writer.log()).isEqualTo("Step4;Step3;Step2;Step1;Step0;");
      assertThat(writer.value()).isEqualTo(6);
    }
  }
}
