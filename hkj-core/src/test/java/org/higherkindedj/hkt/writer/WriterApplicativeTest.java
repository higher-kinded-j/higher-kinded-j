// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.writer.WriterAssert.assertThatWriter;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for WriterApplicative using standardised patterns.
 *
 * <p>Tests Applicative operations (of, ap, map2) for Writer with String logs.
 */
@DisplayName("WriterApplicative<W> Complete Test Suite")
class WriterApplicativeTest extends WriterTestBase {

  private WriterApplicative<String> applicative;

  @BeforeEach
  void setUpApplicative() {
    applicative = new WriterApplicative<>(STRING_MONOID);
  }

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

    @Test
    @DisplayName("Run complete Applicative test pattern")
    void runCompleteApplicativeTestPattern() {
      TypeClassTest.<WriterKind.Witness<String>>applicative(WriterApplicative.class)
          .<Integer>instance(applicative)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(WriterFunctor.class)
          .withApFrom(WriterApplicative.class)
          .withMap2From(Applicative.class)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<WriterKind.Witness<String>>applicative(WriterApplicative.class)
          .<Integer>instance(applicative)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .selectTests()
          .onlyOperations()
          .test();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<WriterKind.Witness<String>>applicative(WriterApplicative.class)
          .<Integer>instance(applicative)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(WriterFunctor.class)
          .withApFrom(WriterApplicative.class)
          .selectTests()
          .onlyValidations()
          .test();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<WriterKind.Witness<String>>applicative(WriterApplicative.class)
          .<Integer>instance(applicative)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .selectTests()
          .onlyExceptions()
          .test();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<WriterKind.Witness<String>>applicative(WriterApplicative.class)
          .<Integer>instance(applicative)
          .<String>withKind(validKind)
          .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, validMapper, equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
    }
  }

  @Nested
  @DisplayName("Applicative Operation Tests")
  class ApplicativeOperationTests {

    @Test
    @DisplayName("of() creates Writer with empty log")
    void ofCreatesWriterWithEmptyLog() {
      Kind<WriterKind.Witness<String>, Integer> result = applicative.of(42);
      Writer<String, Integer> writer = narrowToWriter(result);

      assertThatWriter(writer).hasEmptyLog().hasValue(42);
    }

    @Test
    @DisplayName("of() handles null values")
    void ofHandlesNullValues() {
      Kind<WriterKind.Witness<String>, Integer> result = applicative.of(null);
      Writer<String, Integer> writer = narrowToWriter(result);

      assertThatWriter(writer).hasEmptyLog().hasNullValue();
    }

    @Test
    @DisplayName("ap() applies function and combines logs")
    void apAppliesFunctionAndCombinesLogs() {
      Kind<WriterKind.Witness<String>, Function<Integer, String>> funcKind =
          WRITER.widen(writerOf("Func;", i -> "Result:" + i));
      Kind<WriterKind.Witness<String>, Integer> valueKind = WRITER.widen(writerOf("Value;", 10));

      Kind<WriterKind.Witness<String>, String> result = applicative.ap(funcKind, valueKind);
      Writer<String, String> writer = narrowToWriter(result);

      assertThatWriter(writer).hasLog("Func;Value;").hasValue("Result:10");
    }

    @Test
    @DisplayName("ap() handles empty logs correctly")
    void apHandlesEmptyLogsCorrectly() {
      Kind<WriterKind.Witness<String>, Function<Integer, String>> funcKind =
          applicative.of(i -> "Result:" + i);
      Kind<WriterKind.Witness<String>, Integer> valueKind = applicative.of(10);

      Kind<WriterKind.Witness<String>, String> result = applicative.ap(funcKind, valueKind);
      Writer<String, String> writer = narrowToWriter(result);

      assertThatWriter(writer).hasEmptyLog().hasValue("Result:10");
    }

    @Test
    @DisplayName("ap() combines logs in correct order")
    void apCombinesLogsInCorrectOrder() {
      Kind<WriterKind.Witness<String>, Function<Integer, String>> funcKind =
          WRITER.widen(writerOf("First;", i -> "Val:" + i));
      Kind<WriterKind.Witness<String>, Integer> valueKind = WRITER.widen(writerOf("Second;", 5));

      Kind<WriterKind.Witness<String>, String> result = applicative.ap(funcKind, valueKind);
      Writer<String, String> writer = narrowToWriter(result);

      // Function log comes first, then value log
      assertThatWriter(writer).hasLog("First;Second;").hasValue("Val:5");
    }

    @Test
    @DisplayName("map2() combines two Writers with combining function")
    void map2CombinesTwoWritersWithCombiningFunction() {
      Kind<WriterKind.Witness<String>, Integer> first = WRITER.widen(writerOf("Log1;", 10));
      Kind<WriterKind.Witness<String>, Integer> second = WRITER.widen(writerOf("Log2;", 20));
      BiFunction<Integer, Integer, String> combiner = (a, b) -> String.format("Sum:%d", a + b);

      Kind<WriterKind.Witness<String>, String> result = applicative.map2(first, second, combiner);
      Writer<String, String> writer = narrowToWriter(result);

      assertThatWriter(writer).hasLog("Log1;Log2;").hasValue("Sum:30");
    }

    @Test
    @DisplayName("map2() handles empty logs")
    void map2HandlesEmptyLogs() {
      Kind<WriterKind.Witness<String>, Integer> first = applicative.of(10);
      Kind<WriterKind.Witness<String>, Integer> second = applicative.of(20);
      BiFunction<Integer, Integer, Integer> combiner = Integer::sum;

      Kind<WriterKind.Witness<String>, Integer> result = applicative.map2(first, second, combiner);
      Writer<String, Integer> writer = narrowToWriter(result);

      assertThatWriter(writer).hasEmptyLog().hasValue(30);
    }

    @Test
    @DisplayName("map2() combines logs correctly")
    void map2CombinesLogsCorrectly() {
      Kind<WriterKind.Witness<String>, String> first = WRITER.widen(writerOf("A;", "Hello"));
      Kind<WriterKind.Witness<String>, String> second = WRITER.widen(writerOf("B;", "World"));
      BiFunction<String, String, String> combiner = (a, b) -> a + " " + b;

      Kind<WriterKind.Witness<String>, String> result = applicative.map2(first, second, combiner);
      Writer<String, String> writer = narrowToWriter(result);

      assertThatWriter(writer).hasLog("A;B;").hasValue("Hello World");
    }
  }

  @Nested
  @DisplayName("Applicative Law Tests")
  class ApplicativeLawTests {

    @Test
    @DisplayName("Identity Law: ap(of(id), v) == v")
    void identityLaw() {
      Kind<WriterKind.Witness<String>, Integer> value = WRITER.widen(writerOf("Value;", 42));
      Kind<WriterKind.Witness<String>, Function<Integer, Integer>> identity =
          applicative.of(Function.identity());

      Kind<WriterKind.Witness<String>, Integer> result = applicative.ap(identity, value);

      assertThat(narrowToWriter(result)).isEqualTo(narrowToWriter(value));
    }

    @Test
    @DisplayName("Homomorphism Law: ap(of(f), of(x)) == of(f(x))")
    void homomorphismLaw() {
      Function<Integer, String> func = i -> "Value:" + i;
      int value = 42;

      Kind<WriterKind.Witness<String>, Function<Integer, String>> funcKind = applicative.of(func);
      Kind<WriterKind.Witness<String>, Integer> valueKind = applicative.of(value);

      Kind<WriterKind.Witness<String>, String> leftSide = applicative.ap(funcKind, valueKind);
      Kind<WriterKind.Witness<String>, String> rightSide = applicative.of(func.apply(value));

      assertThat(narrowToWriter(leftSide)).isEqualTo(narrowToWriter(rightSide));
    }

    @Test
    @DisplayName("Interchange Law: ap(ff, of(x)) == ap(of(f -> f(x)), ff)")
    void interchangeLaw() {
      int value = 42;
      Kind<WriterKind.Witness<String>, Function<Integer, String>> funcKind =
          WRITER.widen(writerOf("Func;", i -> "Val:" + i));

      Kind<WriterKind.Witness<String>, Integer> valueKind = applicative.of(value);

      Kind<WriterKind.Witness<String>, String> leftSide = applicative.ap(funcKind, valueKind);

      Function<Function<Integer, String>, String> evalWithValue = f -> f.apply(value);
      Kind<WriterKind.Witness<String>, Function<Function<Integer, String>, String>> evalKind =
          applicative.of(evalWithValue);

      Kind<WriterKind.Witness<String>, String> rightSide = applicative.ap(evalKind, funcKind);

      assertThat(narrowToWriter(leftSide)).isEqualTo(narrowToWriter(rightSide));
    }

    @Test
    @DisplayName("Composition Law: Complex applicative composition")
    void compositionLaw() {
      Kind<WriterKind.Witness<String>, Function<String, String>> gKind =
          WRITER.widen(writerOf("G;", s -> s + "!"));
      Kind<WriterKind.Witness<String>, Function<Integer, String>> fKind =
          WRITER.widen(writerOf("F;", i -> "v" + i));
      Kind<WriterKind.Witness<String>, Integer> value = WRITER.widen(writerOf("Val;", 10));

      Function<
              Function<String, String>,
              Function<Function<Integer, String>, Function<Integer, String>>>
          compose = g -> f -> g.compose(f);

      // Left side: ap(ap(map(compose, g), f), value)
      Kind<
              WriterKind.Witness<String>,
              Function<Function<Integer, String>, Function<Integer, String>>>
          mappedCompose = applicative.map(compose, gKind);
      Kind<WriterKind.Witness<String>, Function<Integer, String>> composedFunc =
          applicative.ap(mappedCompose, fKind);
      Kind<WriterKind.Witness<String>, String> leftSide = applicative.ap(composedFunc, value);

      // Right side: ap(g, ap(f, value))
      Kind<WriterKind.Witness<String>, String> innerAp = applicative.ap(fKind, value);
      Kind<WriterKind.Witness<String>, String> rightSide = applicative.ap(gKind, innerAp);

      assertThat(narrowToWriter(leftSide)).isEqualTo(narrowToWriter(rightSide));
    }
  }

  @Nested
  @DisplayName("Edge Cases and Special Scenarios")
  class EdgeCasesTests {

    @Test
    @DisplayName("ap() handles null function result")
    void apHandlesNullFunctionResult() {
      Kind<WriterKind.Witness<String>, Function<Integer, String>> funcKind =
          WRITER.widen(writerOf("Func;", i -> null));
      Kind<WriterKind.Witness<String>, Integer> valueKind = WRITER.widen(writerOf("Value;", 10));

      Kind<WriterKind.Witness<String>, String> result = applicative.ap(funcKind, valueKind);
      Writer<String, String> writer = narrowToWriter(result);

      assertThatWriter(writer).hasLog("Func;Value;").hasNullValue();
    }

    @Test
    @DisplayName("ap() handles null input value")
    void apHandlesNullInputValue() {
      Kind<WriterKind.Witness<String>, Function<Integer, String>> funcKind =
          WRITER.widen(writerOf("Func;", i -> "Got:" + i));
      Writer<String, Integer> nullValueWriter = writerOf("Value;", null);
      Kind<WriterKind.Witness<String>, Integer> valueKind = WRITER.widen(nullValueWriter);

      Kind<WriterKind.Witness<String>, String> result = applicative.ap(funcKind, valueKind);
      Writer<String, String> writer = narrowToWriter(result);

      assertThatWriter(writer).hasLog("Func;Value;").hasValue("Got:null");
    }

    @Test
    @DisplayName("map2() handles null combining function result")
    void map2HandlesNullCombiningFunctionResult() {
      Kind<WriterKind.Witness<String>, Integer> first = WRITER.widen(writerOf("Log1;", 10));
      Kind<WriterKind.Witness<String>, Integer> second = WRITER.widen(writerOf("Log2;", 20));
      BiFunction<Integer, Integer, String> nullReturning = (a, b) -> null;

      Kind<WriterKind.Witness<String>, String> result =
          applicative.map2(first, second, nullReturning);
      Writer<String, String> writer = narrowToWriter(result);

      assertThatWriter(writer).hasLog("Log1;Log2;").hasNullValue();
    }

    @Test
    @DisplayName("Chaining multiple ap operations")
    void chainingMultipleApOperations() {
      // Create a curried function wrapped in Writer
      Function<Integer, Function<Integer, String>> curriedFunc =
          a -> b -> String.format("Sum:%d", a + b);

      Kind<WriterKind.Witness<String>, Function<Integer, Function<Integer, String>>> funcKind =
          WRITER.widen(writerOf("Func;", curriedFunc));
      Kind<WriterKind.Witness<String>, Integer> value1 = WRITER.widen(writerOf("Val1;", 10));
      Kind<WriterKind.Witness<String>, Integer> value2 = WRITER.widen(writerOf("Val2;", 20));

      // Apply first value to get partially applied function
      Kind<WriterKind.Witness<String>, Function<Integer, String>> partiallyApplied =
          applicative.ap(funcKind, value1);

      // Apply second value to get final result
      Kind<WriterKind.Witness<String>, String> result = applicative.ap(partiallyApplied, value2);

      Writer<String, String> writer = narrowToWriter(result);
      assertThatWriter(writer).hasLog("Func;Val1;Val2;").hasValue("Sum:30");
    }
  }

  @Nested
  @DisplayName("Performance and Characteristics")
  class PerformanceTests {

    @Test
    @DisplayName("Multiple ap operations maintain correct log order")
    void multipleApOperationsMaintainCorrectLogOrder() {
      Kind<WriterKind.Witness<String>, Integer> start = applicative.of(1);

      for (int i = 0; i < 5; i++) {
        final int step = i;
        Kind<WriterKind.Witness<String>, Function<Integer, Integer>> funcKind =
            WRITER.widen(writerOf("Step" + step + ";", x -> x + 1));
        start = applicative.ap(funcKind, start);
      }

      Writer<String, Integer> writer = narrowToWriter(start);
      // ap combines logs as: function log + value log
      // In the loop, the function is created fresh each time with its log,
      // and start accumulates previous logs, so we get reverse order
      assertThatWriter(writer).hasLog("Step4;Step3;Step2;Step1;Step0;").hasValue(6);
    }

    @Test
    @DisplayName("Complex map2 chains")
    void complexMap2Chains() {
      Kind<WriterKind.Witness<String>, Integer> w1 = WRITER.widen(writerOf("A;", 10));
      Kind<WriterKind.Witness<String>, Integer> w2 = WRITER.widen(writerOf("B;", 20));
      Kind<WriterKind.Witness<String>, Integer> w3 = WRITER.widen(writerOf("C;", 30));

      // Combine w1 and w2
      Kind<WriterKind.Witness<String>, Integer> combined12 = applicative.map2(w1, w2, Integer::sum);

      // Combine result with w3
      Kind<WriterKind.Witness<String>, Integer> combined123 =
          applicative.map2(combined12, w3, Integer::sum);

      Writer<String, Integer> writer = narrowToWriter(combined123);
      assertThatWriter(writer).hasLog("A;B;C;").hasValue(60);
    }
  }
}
