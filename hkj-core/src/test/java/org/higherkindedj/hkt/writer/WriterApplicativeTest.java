// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.higherkindedj.hkt.assertions.WriterAssert.assertThatWriter;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("WriterApplicative")
class WriterApplicativeTest extends WriterTestBase {

  private WriterApplicative<String> applicative;

  @BeforeEach
  void setUpApplicative() {
    applicative = new WriterApplicative<>(STRING_MONOID);
    validateApplicativeFixtures();
  }

  // No separate Applicative contract smoke: the Writer Monad extends this Applicative, so its
  // of/ap/map2 null-argument validation and exception propagation are already covered by the
  // contract in WriterMonadTest. A dedicated Applicative contract would only duplicate it.

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.writer.WriterLawFixtures#kinds")
    void identity(String label, Kind<WriterKind.Witness<String>, Integer> v) {
      ApplicativeLaws.assertIdentity(applicative, v, equalityChecker);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.writer.WriterLawFixtures#values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(applicative, value, validMapper, equalityChecker);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.writer.WriterLawFixtures#values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(applicative, validFunctionKind, value, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.writer.WriterLawFixtures#kinds")
    void composition(String label, Kind<WriterKind.Witness<String>, Integer> w) {
      Kind<WriterKind.Witness<String>, Function<String, String>> u =
          WRITER.widen(new Writer<String, Function<String, String>>("u", s -> "u(" + s + ")"));
      Kind<WriterKind.Witness<String>, Function<Integer, String>> v =
          WRITER.widen(new Writer<String, Function<Integer, String>>("v", i -> "v" + i));
      ApplicativeLaws.assertComposition(applicative, u, v, w, equalityChecker);
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("of() creates a Writer with an empty log")
    void ofCreatesWriterWithEmptyLog() {
      assertThatWriter(narrowToWriter(applicative.of(42))).hasEmptyLog().hasValue(42);
    }

    @Test
    @DisplayName("of() allows null values")
    void ofHandlesNullValues() {
      assertThatWriter(narrowToWriter(applicative.of(null))).hasEmptyLog().hasNullValue();
    }

    @Test
    @DisplayName("ap() applies the function and concatenates function-then-value logs")
    void apAppliesFunctionAndCombinesLogs() {
      Kind<WriterKind.Witness<String>, Function<Integer, String>> funcKind =
          WRITER.widen(writerOf("Func;", i -> "Result:" + i));
      Kind<WriterKind.Witness<String>, Integer> valueKind = WRITER.widen(writerOf("Value;", 10));

      var result = applicative.ap(funcKind, valueKind);
      assertThatWriter(narrowToWriter(result)).hasLog("Func;Value;").hasValue("Result:10");
    }

    @Test
    @DisplayName("ap() with two empty logs stays empty")
    void apHandlesEmptyLogs() {
      var funcKind = applicative.of((Function<Integer, String>) i -> "Result:" + i);
      var result = applicative.ap(funcKind, applicative.of(10));
      assertThatWriter(narrowToWriter(result)).hasEmptyLog().hasValue("Result:10");
    }

    @Test
    @DisplayName("map2() combines two Writers and concatenates their logs")
    void map2CombinesTwoWriters() {
      Kind<WriterKind.Witness<String>, Integer> first = WRITER.widen(writerOf("Log1;", 10));
      Kind<WriterKind.Witness<String>, Integer> second = WRITER.widen(writerOf("Log2;", 20));
      BiFunction<Integer, Integer, String> combiner = (a, b) -> "Sum:" + (a + b);

      var result = applicative.map2(first, second, combiner);
      assertThatWriter(narrowToWriter(result)).hasLog("Log1;Log2;").hasValue("Sum:30");
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("ap() keeps both logs when the function result is null")
    @SuppressWarnings("DataFlowIssue") // the mapper deliberately returns null
    void apHandlesNullFunctionResult() {
      Kind<WriterKind.Witness<String>, Function<Integer, String>> funcKind =
          WRITER.widen(writerOf("Func;", _ -> null));
      Kind<WriterKind.Witness<String>, Integer> valueKind = WRITER.widen(writerOf("Value;", 10));

      var result = applicative.ap(funcKind, valueKind);
      assertThatWriter(narrowToWriter(result)).hasLog("Func;Value;").hasNullValue();
    }

    @Test
    @DisplayName("ap() passes a null input value through to the function")
    void apHandlesNullInputValue() {
      Kind<WriterKind.Witness<String>, Function<Integer, String>> funcKind =
          WRITER.widen(writerOf("Func;", i -> "Got:" + i));
      Kind<WriterKind.Witness<String>, Integer> valueKind = WRITER.widen(writerOf("Value;", null));

      var result = applicative.ap(funcKind, valueKind);
      assertThatWriter(narrowToWriter(result)).hasLog("Func;Value;").hasValue("Got:null");
    }

    @Test
    @DisplayName("map2() keeps both logs when the combiner returns null")
    void map2HandlesNullCombiningFunctionResult() {
      Kind<WriterKind.Witness<String>, Integer> first = WRITER.widen(writerOf("Log1;", 10));
      Kind<WriterKind.Witness<String>, Integer> second = WRITER.widen(writerOf("Log2;", 20));

      var result = applicative.map2(first, second, (_, _) -> null);
      assertThatWriter(narrowToWriter(result)).hasLog("Log1;Log2;").hasNullValue();
    }

    @Test
    @DisplayName("chained ap() of a curried function accumulates every log in order")
    void chainingMultipleApOperations() {
      Function<Integer, Function<Integer, String>> curried = a -> b -> "Sum:" + (a + b);
      Kind<WriterKind.Witness<String>, Function<Integer, Function<Integer, String>>> funcKind =
          WRITER.widen(writerOf("Func;", curried));
      Kind<WriterKind.Witness<String>, Integer> value1 = WRITER.widen(writerOf("Val1;", 10));
      Kind<WriterKind.Witness<String>, Integer> value2 = WRITER.widen(writerOf("Val2;", 20));

      var partiallyApplied = applicative.ap(funcKind, value1);
      var result = applicative.ap(partiallyApplied, value2);

      assertThatWriter(narrowToWriter(result)).hasLog("Func;Val1;Val2;").hasValue("Sum:30");
    }
  }
}
