// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.test.assertions.ApplicativeAssertions.*;
import static org.higherkindedj.hkt.test.assertions.MonadAssertions.assertMapKindNull;
import static org.higherkindedj.hkt.test.data.TestExceptions.*;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherMonad Applicative Operations Tests")
class EitherApplicativeTest {

  record TestError(String code) {}

  private EitherMonad<TestError> applicative;

  @BeforeEach
  void setUp() {
    applicative = EitherMonad.instance();
  }

  // Helper methods
  private <R> Either<TestError, R> narrow(Kind<EitherKind.Witness<TestError>, R> kind) {
    return EITHER.narrow(kind);
  }

  private <R> Kind<EitherKind.Witness<TestError>, R> right(R value) {
    return EITHER.widen(Either.right(value));
  }

  private <R> Kind<EitherKind.Witness<TestError>, R> left(String errorCode) {
    return EITHER.widen(Either.left(new TestError(errorCode)));
  }

  @Nested
  @DisplayName("Of Operation Tests")
  class OfOperationTests {

    @Test
    @DisplayName("of() creates Right instances")
    void ofCreatesRightInstances() {
      Kind<EitherKind.Witness<TestError>, String> result = applicative.of("success");

      Either<TestError, String> either = narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo("success");
    }

    @Test
    @DisplayName("of() accepts null values")
    void ofAcceptsNullValues() {
      Kind<EitherKind.Witness<TestError>, String> result = applicative.of(null);

      Either<TestError, String> either = narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isNull();
    }

    @Test
    @DisplayName("of() with different types")
    void ofWithDifferentTypes() {
      assertThat(narrow(applicative.of(42)).getRight()).isEqualTo(42);
      assertThat(narrow(applicative.of("test")).getRight()).isEqualTo("test");
      assertThat(narrow(applicative.of(true)).getRight()).isTrue();
    }
  }

  @Nested
  @DisplayName("Ap Operation Tests")
  class ApOperationTests {

    @Test
    @DisplayName("ap() applies function to value - both Right")
    void apAppliesFunctionToValue() {
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> funcKind =
          right(i -> "value:" + i);
      Kind<EitherKind.Witness<TestError>, Integer> valueKind = right(42);

      Kind<EitherKind.Witness<TestError>, String> result = applicative.ap(funcKind, valueKind);

      assertThat(narrow(result).getRight()).isEqualTo("value:42");
    }

    @Test
    @DisplayName("ap() propagates Left from function")
    void apPropagatesLeftFromFunction() {
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> funcKind = left("FUNC_ERR");
      Kind<EitherKind.Witness<TestError>, Integer> valueKind = right(42);

      Kind<EitherKind.Witness<TestError>, String> result = applicative.ap(funcKind, valueKind);

      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("FUNC_ERR"));
    }

    @Test
    @DisplayName("ap() propagates Left from value")
    void apPropagatesLeftFromValue() {
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> funcKind =
          right(i -> "value:" + i);
      Kind<EitherKind.Witness<TestError>, Integer> valueKind = left("VAL_ERR");

      Kind<EitherKind.Witness<TestError>, String> result = applicative.ap(funcKind, valueKind);

      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("VAL_ERR"));
    }

    @Test
    @DisplayName("ap() with both Left returns function error")
    void apWithBothLeftReturnsFirstError() {
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> funcKind = left("FUNC_ERR");
      Kind<EitherKind.Witness<TestError>, Integer> valueKind = left("VAL_ERR");

      Kind<EitherKind.Witness<TestError>, String> result = applicative.ap(funcKind, valueKind);

      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("FUNC_ERR"));
    }

    @Test
    @DisplayName("ap() null validations")
    void apNullValidations() {
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> validFunc =
          right(Object::toString);
      Kind<EitherKind.Witness<TestError>, Integer> validValue = right(42);

      assertApFunctionKindNull(() -> applicative.ap(null, validValue));
      assertApArgumentKindNull(() -> applicative.ap(validFunc, null));
    }
  }

  @Nested
  @DisplayName("Map2 Operation Tests")
  class Map2OperationTests {

    @Test
    @DisplayName("map2() combines two Right values")
    void map2CombinesTwoRightValues() {
      Kind<EitherKind.Witness<TestError>, Integer> r1 = right(10);
      Kind<EitherKind.Witness<TestError>, String> r2 = right("test");

      BiFunction<Integer, String, String> combiner = (i, s) -> s + ":" + i;
      Kind<EitherKind.Witness<TestError>, String> result = applicative.map2(r1, r2, combiner);

      assertThat(narrow(result).getRight()).isEqualTo("test:10");
    }

    @Test
    @DisplayName("map2() propagates first Left")
    void map2PropagatesFirstLeft() {
      Kind<EitherKind.Witness<TestError>, Integer> l1 = left("E1");
      Kind<EitherKind.Witness<TestError>, String> r2 = right("test");

      BiFunction<Integer, String, String> combiner = (i, s) -> s + ":" + i;
      Kind<EitherKind.Witness<TestError>, String> result = applicative.map2(l1, r2, combiner);

      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("E1"));
    }

    @Test
    @DisplayName("map2() propagates second Left")
    void map2PropagatesSecondLeft() {
      Kind<EitherKind.Witness<TestError>, Integer> r1 = right(10);
      Kind<EitherKind.Witness<TestError>, String> l2 = left("E2");

      BiFunction<Integer, String, String> combiner = (i, s) -> s + ":" + i;
      Kind<EitherKind.Witness<TestError>, String> result = applicative.map2(r1, l2, combiner);

      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("E2"));
    }

    @Test
    @DisplayName("map2() null validations")
    void map2NullValidations() {
      Kind<EitherKind.Witness<TestError>, Integer> validK1 = right(10);
      Kind<EitherKind.Witness<TestError>, String> validK2 = right("test");
      BiFunction<Integer, String, String> validFunc = (i, s) -> s + i;

      // map2 delegates to map and ap internally, so errors come from those operations
      assertMapKindNull(() -> applicative.map2(null, validK2, validFunc));
      assertApArgumentKindNull(() -> applicative.map2(validK1, null, validFunc));
      assertMap2FunctionNull(
          () -> applicative.map2(validK1, validK2, (BiFunction<Integer, String, String>) null));
    }
  }

  @Nested
  @DisplayName("Map3 Operation Tests")
  class Map3OperationTests {

    @Test
    @DisplayName("map3() combines three Right values")
    void map3CombinesThreeRightValues() {
      Kind<EitherKind.Witness<TestError>, Integer> r1 = right(1);
      Kind<EitherKind.Witness<TestError>, String> r2 = right("test");
      Kind<EitherKind.Witness<TestError>, Double> r3 = right(3.14);

      Function3<Integer, String, Double, String> combiner =
          (i, s, d) -> String.format("%s:%d:%.2f", s, i, d);

      Kind<EitherKind.Witness<TestError>, String> result = applicative.map3(r1, r2, r3, combiner);

      assertThat(narrow(result).getRight()).isEqualTo("test:1:3.14");
    }

    @Test
    @DisplayName("map3() propagates first Left")
    void map3PropagatesFirstLeft() {
      Kind<EitherKind.Witness<TestError>, Integer> l1 = left("E1");
      Kind<EitherKind.Witness<TestError>, String> r2 = right("test");
      Kind<EitherKind.Witness<TestError>, Double> r3 = right(3.14);

      Function3<Integer, String, Double, String> combiner = (i, s, d) -> "result";

      Kind<EitherKind.Witness<TestError>, String> result = applicative.map3(l1, r2, r3, combiner);

      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("E1"));
    }

    @Test
    @DisplayName("map3() null validations")
    void map3NullValidations() {
      Kind<EitherKind.Witness<TestError>, Integer> r1 = right(1);
      Kind<EitherKind.Witness<TestError>, String> r2 = right("test");
      Kind<EitherKind.Witness<TestError>, Double> r3 = right(3.14);
      Function3<Integer, String, Double, String> validFunc = (i, s, d) -> "result";

      assertMap3FirstKindNull(() -> applicative.map3(null, r2, r3, validFunc));
      assertMap3SecondKindNull(() -> applicative.map3(r1, null, r3, validFunc));
      assertMap3ThirdKindNull(() -> applicative.map3(r1, r2, null, validFunc));
      assertMap3FunctionNull(() -> applicative.map3(r1, r2, r3, null));
    }
  }

  @Nested
  @DisplayName("Map4 Operation Tests")
  class Map4OperationTests {

    @Test
    @DisplayName("map4() combines four Right values")
    void map4CombinesFourRightValues() {
      Kind<EitherKind.Witness<TestError>, Integer> r1 = right(1);
      Kind<EitherKind.Witness<TestError>, String> r2 = right("test");
      Kind<EitherKind.Witness<TestError>, Double> r3 = right(3.14);
      Kind<EitherKind.Witness<TestError>, Boolean> r4 = right(true);

      Function4<Integer, String, Double, Boolean, String> combiner =
          (i, s, d, b) -> String.format("%s:%d:%.2f:%b", s, i, d, b);

      Kind<EitherKind.Witness<TestError>, String> result =
          applicative.map4(r1, r2, r3, r4, combiner);

      assertThat(narrow(result).getRight()).isEqualTo("test:1:3.14:true");
    }

    @Test
    @DisplayName("map4() propagates fourth Left")
    void map4PropagatesFourthLeft() {
      Kind<EitherKind.Witness<TestError>, Integer> r1 = right(1);
      Kind<EitherKind.Witness<TestError>, String> r2 = right("test");
      Kind<EitherKind.Witness<TestError>, Double> r3 = right(3.14);
      Kind<EitherKind.Witness<TestError>, Boolean> l4 = left("E4");

      Function4<Integer, String, Double, Boolean, String> combiner = (i, s, d, b) -> "result";

      Kind<EitherKind.Witness<TestError>, String> result =
          applicative.map4(r1, r2, r3, l4, combiner);

      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("E4"));
    }

    @Test
    @DisplayName("map4() null validations")
    void map4NullValidations() {
      Kind<EitherKind.Witness<TestError>, Integer> r1 = right(1);
      Kind<EitherKind.Witness<TestError>, String> r2 = right("test");
      Kind<EitherKind.Witness<TestError>, Double> r3 = right(3.14);
      Kind<EitherKind.Witness<TestError>, Boolean> r4 = right(true);
      Function4<Integer, String, Double, Boolean, String> validFunc = (i, s, d, b) -> "result";

      assertMap4FirstKindNull(() -> applicative.map4(null, r2, r3, r4, validFunc));
      assertMap4SecondKindNull(() -> applicative.map4(r1, null, r3, r4, validFunc));
      assertMap4ThirdKindNull(() -> applicative.map4(r1, r2, null, r4, validFunc));
      assertMap4FourthKindNull(() -> applicative.map4(r1, r2, r3, null, validFunc));
      assertMap4FunctionNull(() -> applicative.map4(r1, r2, r3, r4, null));
    }
  }

  @Nested
  @DisplayName("Exception Handling Tests")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("ap() propagates function exceptions")
    void apPropagatesFunctionExceptions() {
      RuntimeException testException = runtime("ap test");
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> throwingFunc =
          right(
              i -> {
                throw testException;
              });
      Kind<EitherKind.Witness<TestError>, Integer> validValue = right(42);

      assertThatThrownBy(() -> applicative.ap(throwingFunc, validValue)).isSameAs(testException);
    }

    @Test
    @DisplayName("map2() propagates function exceptions")
    void map2PropagatesFunctionExceptions() {
      RuntimeException testException = runtime("map2 test");
      Kind<EitherKind.Witness<TestError>, Integer> r1 = right(10);
      Kind<EitherKind.Witness<TestError>, String> r2 = right("test");
      BiFunction<Integer, String, String> throwingFunc =
          (i, s) -> {
            throw testException;
          };

      assertThatThrownBy(() -> applicative.map2(r1, r2, throwingFunc)).isSameAs(testException);
    }

    @Test
    @DisplayName("map3() propagates function exceptions")
    void map3PropagatesFunctionExceptions() {
      RuntimeException testException = runtime("map3 test");
      Function3<Integer, String, Double, String> throwingFunc =
          (i, s, d) -> {
            throw testException;
          };

      assertThatThrownBy(() -> applicative.map3(right(1), right("test"), right(1.0), throwingFunc))
          .isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("mapN operations with null values in Right")
    void mapNWithNullValuesInRight() {
      Kind<EitherKind.Witness<TestError>, Integer> rightNull = right(null);
      Kind<EitherKind.Witness<TestError>, String> rightValue = right("test");

      BiFunction<Integer, String, String> nullSafeFunc =
          (i, s) -> (i == null ? "null" : i.toString()) + ":" + s;

      Kind<EitherKind.Witness<TestError>, String> result =
          applicative.map2(rightNull, rightValue, nullSafeFunc);

      assertThat(narrow(result).getRight()).isEqualTo("null:test");
    }

    @Test
    @DisplayName("mapN operations short-circuit on first Left")
    void mapNShortCircuitsOnFirstLeft() {
      Kind<EitherKind.Witness<TestError>, Integer> l1 = left("E1");
      Kind<EitherKind.Witness<TestError>, String> l2 = left("E2");
      Kind<EitherKind.Witness<TestError>, Double> l3 = left("E3");

      Function3<Integer, String, Double, String> combiner = (i, s, d) -> "result";

      Kind<EitherKind.Witness<TestError>, String> result = applicative.map3(l1, l2, l3, combiner);

      // Should return first error
      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("E1"));
    }
  }
}
