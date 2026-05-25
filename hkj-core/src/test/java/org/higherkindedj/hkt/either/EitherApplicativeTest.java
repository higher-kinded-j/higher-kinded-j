// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("EitherApplicative")
class EitherApplicativeTest extends EitherTestBase {

  private MonadError<EitherKind.Witness<String>, String> applicative;
  private Applicative<EitherKind.Witness<String>> applicativeTyped;

  @BeforeEach
  void setUpApplicative() {
    applicative = Instances.monadError(either());
    applicativeTyped = applicative;
    validateApplicativeFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("fixtures")
    void identity(String label, Kind<EitherKind.Witness<String>, Integer> v) {
      ApplicativeLaws.assertIdentity(applicativeTyped, v, equalityChecker);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(applicativeTyped, value, validMapper, equalityChecker);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(
          applicativeTyped, validFunctionKind, value, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("fixtures")
    void composition(String label, Kind<EitherKind.Witness<String>, Integer> w) {
      Kind<EitherKind.Witness<String>, Function<String, String>> u =
          EITHER.widen(Either.<String, Function<String, String>>right(s -> "u(" + s + ")"));
      Kind<EitherKind.Witness<String>, Function<Integer, String>> v =
          EITHER.widen(Either.<String, Function<Integer, String>>right(i -> "v" + i));
      ApplicativeLaws.assertComposition(applicativeTyped, u, v, w, equalityChecker);
    }

    static Stream<Arguments> fixtures() {
      return Stream.of(
          Arguments.of("Right(0)", EITHER.widen(Either.<String, Integer>right(0))),
          Arguments.of("Right(42)", EITHER.widen(Either.<String, Integer>right(42))),
          Arguments.of("Right(-1)", EITHER.widen(Either.<String, Integer>right(-1))),
          Arguments.of("Left(\"err\")", EITHER.widen(Either.<String, Integer>left("err"))));
    }

    static Stream<Arguments> values() {
      return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("ap() applies function to value - both Right")
    void apAppliesFunctionToValue() {
      Kind<EitherKind.Witness<String>, Function<Integer, String>> funcKind =
          applicative.of(i -> "value:" + i);
      Kind<EitherKind.Witness<String>, Integer> valueKind = applicative.of(DEFAULT_RIGHT_VALUE);

      var result = applicative.ap(funcKind, valueKind);

      assertThatEither(narrowToEither(result)).isRight().hasRight("value:42");
    }

    @Test
    @DisplayName("ap() propagates Left from function")
    void apPropagatesLeftFromFunction() {
      Kind<EitherKind.Witness<String>, Function<Integer, String>> funcKind =
          leftKind(TestErrorType.FUNCTION_ERROR);
      Kind<EitherKind.Witness<String>, Integer> valueKind = applicative.of(DEFAULT_RIGHT_VALUE);

      var result = applicative.ap(funcKind, valueKind);

      assertThatEither(narrowToEither(result))
          .isLeft()
          .hasLeft(TestErrorType.FUNCTION_ERROR.message());
    }

    @Test
    @DisplayName("map2() combines two Right values")
    void map2CombinesTwoRightValues() {
      var r1 = applicative.of(10);
      var r2 = applicative.of("test");

      var result = applicative.map2(r1, r2, (i, s) -> s + ":" + i);

      assertThatEither(narrowToEither(result)).isRight().hasRight("test:10");
    }

    @Test
    @DisplayName("map3() combines three Right values")
    void map3CombinesThreeRightValues() {
      var r1 = applicative.of(1);
      var r2 = applicative.of("test");
      var r3 = applicative.of(3.14);

      Function3<Integer, String, Double, String> combiner =
          (i, s, d) -> String.format("%s:%d:%.2f", s, i, d);

      var result = applicative.map3(r1, r2, r3, combiner);

      assertThatEither(narrowToEither(result)).isRight().hasRight("test:1:3.14");
    }

    @Test
    @DisplayName("map4() combines four Right values")
    void map4CombinesFourRightValues() {
      var r1 = applicative.of(1);
      var r2 = applicative.of("test");
      var r3 = applicative.of(3.14);
      var r4 = applicative.of(true);

      Function4<Integer, String, Double, Boolean, String> combiner =
          (i, s, d, b) -> String.format("%s:%d:%.2f:%b", s, i, d, b);

      var result = applicative.map4(r1, r2, r3, r4, combiner);

      assertThatEither(narrowToEither(result)).isRight().hasRight("test:1:3.14:true");
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("mapN operations with null values in Right")
    void mapNWithNullValuesInRight() {
      var rightNull = applicative.of(null);
      var rightValue = applicative.of("test");

      var result =
          applicative.map2(
              rightNull, rightValue, (i, s) -> (i == null ? "null" : i.toString()) + ":" + s);

      assertThatEither(narrowToEither(result)).isRight().hasRight("null:test");
    }

    @Test
    @DisplayName("mapN operations short-circuit on first Left")
    void mapNShortCircuitsOnFirstLeft() {
      Kind<EitherKind.Witness<String>, Integer> l1 = leftKind(TestErrorType.ERROR_1);
      Kind<EitherKind.Witness<String>, String> l2 = leftKind(TestErrorType.ERROR_2);
      Kind<EitherKind.Witness<String>, Double> l3 = leftKind(TestErrorType.ERROR_3);

      Function3<Integer, String, Double, String> combiner = (i, s, d) -> "result";

      Kind<EitherKind.Witness<String>, String> result = applicative.map3(l1, l2, l3, combiner);
      assertThatEither(narrowToEither(result)).isLeft().hasLeft(TestErrorType.ERROR_1.message());
    }

    @Test
    @DisplayName("Complex nested applicative operations")
    void complexNestedApplicativeOperations() {
      Kind<EitherKind.Witness<String>, Function<Integer, Function<String, String>>> nestedFunc =
          applicative.of(i -> s -> s + ":" + i);
      Kind<EitherKind.Witness<String>, Integer> intKind = applicative.of(DEFAULT_RIGHT_VALUE);
      Kind<EitherKind.Witness<String>, String> stringKind = applicative.of("test");

      var partialFunc = applicative.ap(nestedFunc, intKind);
      var result = applicative.ap(partialFunc, stringKind);

      assertThatEither(narrowToEither(result)).isRight().hasRight("test:42");
    }
  }
}
