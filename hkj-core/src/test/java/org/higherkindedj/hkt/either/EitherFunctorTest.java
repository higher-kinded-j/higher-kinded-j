// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.test.data.TestExceptions;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherFunctor Tests")
class EitherFunctorTest {

  record TestError(String code) {}

  private EitherFunctor<TestError> functor;

  @BeforeEach
  void setUp() {
    functor = new EitherFunctor<>();
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
  @DisplayName("Map Operation Tests")
  class MapOperationTests {

    @Test
    @DisplayName("map() on Right should apply function")
    void mapOnRightShouldApplyFunction() {
      Kind<EitherKind.Witness<TestError>, Integer> rightKind = right(42);

      Kind<EitherKind.Witness<TestError>, String> result =
          functor.map(TestFunctions.INT_TO_STRING, rightKind);

      Either<TestError, String> either = narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo("42");
    }

    @Test
    @DisplayName("map() on Left should pass through unchanged")
    void mapOnLeftShouldPassThrough() {
      Kind<EitherKind.Witness<TestError>, Integer> leftKind = left("E404");

      Kind<EitherKind.Witness<TestError>, String> result =
          functor.map(TestFunctions.INT_TO_STRING, leftKind);

      Either<TestError, String> either = narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(new TestError("E404"));
    }

    @Test
    @DisplayName("map() with various transformations")
    void mapWithVariousTransformations() {
      Kind<EitherKind.Witness<TestError>, Integer> rightKind = right(10);

      // Test multiple transformations
      Kind<EitherKind.Witness<TestError>, Integer> doubled =
          functor.map(TestFunctions.MULTIPLY_BY_2, rightKind);
      assertThat(narrow(doubled).getRight()).isEqualTo(20);

      Kind<EitherKind.Witness<TestError>, Integer> squared =
          functor.map(TestFunctions.SQUARE, rightKind);
      assertThat(narrow(squared).getRight()).isEqualTo(100);

      Kind<EitherKind.Witness<TestError>, Integer> incremented =
          functor.map(TestFunctions.INCREMENT, rightKind);
      assertThat(narrow(incremented).getRight()).isEqualTo(11);
    }

    @Test
    @DisplayName("map() with null result should be allowed")
    void mapWithNullResult() {
      Kind<EitherKind.Witness<TestError>, Integer> rightKind = right(42);

      Kind<EitherKind.Witness<TestError>, String> result =
          functor.map(TestFunctions.nullReturningFunction(), rightKind);

      Either<TestError, String> either = narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isNull();
    }
  }

  @Nested
  @DisplayName("Parameter Validation Tests")
  class ParameterValidationTests {

    @Test
    @DisplayName("map() should validate null function parameter")
    void mapShouldValidateNullFunction() {
      Kind<EitherKind.Witness<TestError>, Integer> validKind = right(10);

      ValidationTestBuilder.create()
          .assertFunctionNull(() -> functor.map(null, validKind), "function f", "map")
          .execute();
    }

    @Test
    @DisplayName("map() should validate null Kind parameter")
    void mapShouldValidateNullKind() {
      Function<Integer, String> validMapper = TestFunctions.INT_TO_STRING;

      ValidationTestBuilder.create()
          .assertKindNull(() -> functor.map(validMapper, null), "map")
          .execute();
    }

    @Test
    @DisplayName("map() comprehensive null validation")
    void mapComprehensiveNullValidation() {
      Kind<EitherKind.Witness<TestError>, Integer> validKind = right(10);
      Function<Integer, String> validMapper = TestFunctions.INT_TO_STRING;

      ValidationTestBuilder.create()
          .assertFunctionNull(() -> functor.map(null, validKind), "function f", "map")
          .assertKindNull(() -> functor.map(validMapper, null), "map")
          .execute();
    }
  }

  @Nested
  @DisplayName("Exception Handling Tests")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("map() should propagate function exceptions")
    void mapShouldPropagateExceptions() {
      Kind<EitherKind.Witness<TestError>, Integer> rightKind = right(42);
      RuntimeException testException = TestExceptions.runtime("map test");

      Function<Integer, String> throwingMapper = TestFunctions.throwingFunction(testException);

      assertThatThrownBy(() -> functor.map(throwingMapper, rightKind)).isSameAs(testException);
    }

    @Test
    @DisplayName("map() on Left should not execute throwing function")
    void mapOnLeftShouldNotExecuteThrowingFunction() {
      Kind<EitherKind.Witness<TestError>, Integer> leftKind = left("E500");
      RuntimeException testException = TestExceptions.runtime("should not throw");

      Function<Integer, String> throwingMapper = TestFunctions.throwingFunction(testException);

      // Should not throw because function is not executed on Left
      Kind<EitherKind.Witness<TestError>, String> result = functor.map(throwingMapper, leftKind);

      assertThat(narrow(result).isLeft()).isTrue();
      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("E500"));
    }
  }

  @Nested
  @DisplayName("Functor Laws Tests")
  class FunctorLawsTests {

    @Test
    @DisplayName("Identity Law: map(id, fa) == fa")
    void identityLaw() {
      Kind<EitherKind.Witness<TestError>, Integer> fa = right(42);
      Function<Integer, Integer> identity = a -> a;

      Kind<EitherKind.Witness<TestError>, Integer> result = functor.map(identity, fa);

      // For Either, we check value equality since map creates a new Right
      assertThat(narrow(result)).isEqualTo(narrow(fa));
    }

    @Test
    @DisplayName("Composition Law: map(g ∘ f) == map(g) ∘ map(f)")
    void compositionLaw() {
      Kind<EitherKind.Witness<TestError>, Integer> fa = right(5);
      Function<Integer, Integer> f = TestFunctions.MULTIPLY_BY_2;
      Function<Integer, String> g = TestFunctions.INT_TO_STRING;

      // Left side: map(g ∘ f, fa)
      Function<Integer, String> composed = TestFunctions.compose(f, g);
      Kind<EitherKind.Witness<TestError>, String> leftSide = functor.map(composed, fa);

      // Right side: map(g, map(f, fa))
      Kind<EitherKind.Witness<TestError>, Integer> intermediate = functor.map(f, fa);
      Kind<EitherKind.Witness<TestError>, String> rightSide = functor.map(g, intermediate);

      assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));
    }

    @Test
    @DisplayName("Functor laws hold for Left values")
    void functorLawsForLeftValues() {
      Kind<EitherKind.Witness<TestError>, Integer> leftKind = left("ERROR");

      // Identity law for Left
      Function<Integer, Integer> identity = a -> a;
      Kind<EitherKind.Witness<TestError>, Integer> identityResult = functor.map(identity, leftKind);
      assertThat(narrow(identityResult)).isSameAs(narrow(leftKind));

      // Composition law for Left
      Function<Integer, Integer> f = TestFunctions.MULTIPLY_BY_2;
      Function<Integer, String> g = TestFunctions.INT_TO_STRING;
      Function<Integer, String> composed = TestFunctions.compose(f, g);

      Kind<EitherKind.Witness<TestError>, String> composedResult = functor.map(composed, leftKind);
      assertThat(narrow(composedResult)).isSameAs(narrow(leftKind));
    }
  }

  @Nested
  @DisplayName("Type System Tests")
  class TypeSystemTests {

    @Test
    @DisplayName("map() should work with different type transformations")
    void mapWithDifferentTypes() {
      Kind<EitherKind.Witness<TestError>, String> stringKind = right("hello");

      // String to Integer
      Kind<EitherKind.Witness<TestError>, Integer> lengthResult =
          functor.map(TestFunctions.STRING_LENGTH, stringKind);
      assertThat(narrow(lengthResult).getRight()).isEqualTo(5);

      // String to String (uppercase)
      Kind<EitherKind.Witness<TestError>, String> upperResult =
          functor.map(TestFunctions.TO_UPPERCASE, stringKind);
      assertThat(narrow(upperResult).getRight()).isEqualTo("HELLO");
    }

    @Test
    @DisplayName("map() should handle complex types")
    void mapWithComplexTypes() {
      Kind<EitherKind.Witness<TestError>, java.util.List<Integer>> listKind =
          right(java.util.List.of(1, 2, 3));

      Kind<EitherKind.Witness<TestError>, Integer> sizeResult =
          functor.map(java.util.List::size, listKind);

      assertThat(narrow(sizeResult).getRight()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("map() integrates with TestFunctions utilities")
    void mapIntegratesWithTestFunctions() {
      Kind<EitherKind.Witness<TestError>, Integer> rightKind = right(100);

      // Chain multiple TestFunctions
      Kind<EitherKind.Witness<TestError>, String> result =
          functor.map(TestFunctions.INT_TO_STRING, rightKind);
      result = functor.map(TestFunctions.INT_TO_STRING, rightKind);
      result = functor.map(TestFunctions.APPEND_SUFFIX, result);
      result = functor.map(TestFunctions.TO_UPPERCASE, result);

      assertThat(narrow(result).getRight()).isEqualTo("100_TEST");
    }

    @Test
    @DisplayName("map() works with ValidationTestBuilder")
    void mapWorksWithValidationTestBuilder() {
      Kind<EitherKind.Witness<TestError>, Integer> validKind = right(42);

      ValidationTestBuilder.create()
          .assertFunctionNull(() -> functor.map(null, validKind), "function f", "map")
          .assertKindNull(() -> functor.map(TestFunctions.INT_TO_STRING, null), "map")
          .execute();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("map() with null values in Right")
    void mapWithNullValuesInRight() {
      Kind<EitherKind.Witness<TestError>, Integer> rightNull = right(null);

      Function<Integer, String> nullSafeMapper = i -> i == null ? "was null" : i.toString();

      Kind<EitherKind.Witness<TestError>, String> result = functor.map(nullSafeMapper, rightNull);

      assertThat(narrow(result).getRight()).isEqualTo("was null");
    }

    @Test
    @DisplayName("map() with null values in Left")
    void mapWithNullValuesInLeft() {
      Kind<EitherKind.Witness<TestError>, Integer> leftNull = EITHER.widen(Either.left(null));

      Kind<EitherKind.Witness<TestError>, String> result =
          functor.map(TestFunctions.INT_TO_STRING, leftNull);

      Either<TestError, String> either = narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isNull();
    }

    @Test
    @DisplayName("map() preserves Left through multiple transformations")
    void mapPreservesLeftThroughTransformations() {
      Kind<EitherKind.Witness<TestError>, Integer> leftKind = left("ERROR");

      // Multiple map operations - all should preserve the Left
      Kind<EitherKind.Witness<TestError>, Integer> step1 =
          functor.map(TestFunctions.MULTIPLY_BY_2, leftKind);
      Kind<EitherKind.Witness<TestError>, String> step2 =
          functor.map(TestFunctions.INT_TO_STRING, step1);
      Kind<EitherKind.Witness<TestError>, String> step3 =
          functor.map(TestFunctions.TO_UPPERCASE, step2);

      // All steps should return the same Left instance
      assertThat(narrow(step1)).isSameAs(narrow(leftKind));
      assertThat(narrow(step2)).isSameAs(narrow(leftKind));
      assertThat(narrow(step3)).isSameAs(narrow(leftKind));
    }
  }
}
