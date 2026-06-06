// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.higherkindedj.hkt.assertions.WriterAssert.assertThatWriter;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.higherkindedj.hkt.test.fixtures.TestFunctions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("WriterFunctor")
class WriterFunctorTest extends WriterTestBase {

  private WriterFunctor<String> functor;

  @BeforeEach
  void setUpFunctor() {
    functor = new WriterFunctor<>();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.writer.WriterLawFixtures#kinds")
    void identity(String label, Kind<WriterKind.Witness<String>, Integer> fa) {
      FunctorLaws.assertIdentity(functor, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.writer.WriterLawFixtures#kinds")
    void composition(String label, Kind<WriterKind.Witness<String>, Integer> fa) {
      FunctorLaws.assertComposition(functor, fa, validMapper, secondMapper, equalityChecker);
    }
  }

  @Test
  @DisplayName("Functor contract — operations, validations & exceptions (laws verified above)")
  void functorContract() {
    TypeClassContract.<WriterKind.Witness<String>>functor(WriterFunctor.class)
        .<Integer>instance(functor)
        .<String>withKind(validKind)
        .withMapper(validMapper)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operations")
  class Operations {

    @Test
    @DisplayName("map() applies the function and preserves the log")
    void mapAppliesFunctionAndPreservesLog() {
      var result = functor.map(i -> "Value:" + i, defaultKind());
      assertThatWriter(narrowToWriter(result))
          .hasLog(DEFAULT_LOG)
          .hasValue("Value:" + DEFAULT_VALUE);
    }

    @Test
    @DisplayName("map() preserves an empty log")
    void mapWorksWithEmptyLog() {
      var result = functor.map(Object::toString, WRITER.widen(valueWriter(42)));
      assertThatWriter(narrowToWriter(result)).hasEmptyLog().hasValue("42");
    }

    @Test
    @DisplayName("map() passes a null value through to the mapper")
    void mapHandlesNullValue() {
      var result = functor.map(i -> "Got:" + i, WRITER.widen(writerOf("NullValue;", null)));
      assertThatWriter(narrowToWriter(result)).hasLog("NullValue;").hasValue("Got:null");
    }

    @Test
    @DisplayName("map() preserves the log when the mapper returns null")
    void mapHandlesFunctionReturningNull() {
      var result = functor.map(TestFunctions.nullReturningFunction(), defaultKind());
      assertThatWriter(narrowToWriter(result)).hasLog(DEFAULT_LOG).hasNullValue();
    }
  }

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    @DisplayName("chained map() leaves the original log untouched")
    void mapChainsAndPreservesLog() {
      Kind<WriterKind.Witness<String>, Integer> start = WRITER.widen(writerOf("Start;", 5));

      var doubled = functor.map(i -> i * 2, start);
      var added = functor.map(i -> i + 10, doubled);
      var result = functor.map(Object::toString, added);

      assertThatWriter(narrowToWriter(result)).hasLog("Start;").hasValue("20");
    }

    @Test
    @DisplayName("map() is stack-safe for deep chains")
    void mapIsStackSafeForDeepChains() {
      Kind<WriterKind.Witness<String>, Integer> result = WRITER.widen(writerOf("Deep;", 0));
      for (int i = 0; i < 10_000; i++) {
        result = functor.map(x -> x + 1, result);
      }

      assertThatWriter(narrowToWriter(result)).hasLog("Deep;").hasValue(10_000);
    }
  }
}
