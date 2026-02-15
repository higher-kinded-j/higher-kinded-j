// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherTraverse Foldable Operations Complete Test Suite")
class EitherFoldableTest extends EitherTestBase {

  private Foldable<EitherKind.Witness<String>> foldable;
  private Monoid<String> validMonoid;
  private Function<Integer, String> validFoldMapFunction;

  @BeforeEach
  void setUpFoldable() {
    foldable = EitherTraverse.instance();
    validMonoid = Monoids.string();
    validFoldMapFunction = TestFunctions.INT_TO_STRING;
    validateRequiredFixtures();
  }

  @Nested
  @DisplayName("Complete Foldable Test Suite")
  class CompleteFoldableTestSuite {

    @Test
    @DisplayName("Run complete Foldable test pattern")
    void runCompleteFoldableTestPattern() {
      TypeClassTest.<EitherKind.Witness<String>>foldable(EitherTraverse.class)
          .<Integer>instance(foldable)
          .withKind(validKind)
          .withOperations(validMonoid, validFoldMapFunction)
          .testAll();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(EitherFoldableTest.class);

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
    @DisplayName("foldMap() on Right applies function")
    void foldMapOnRightAppliesFunction() {
      // Verify input is a Right with expected value
      assertThatEither(narrowToEither(validKind)).isRight().hasRight(DEFAULT_RIGHT_VALUE);

      Monoid<String> stringMonoid = Monoids.string();
      Function<Integer, String> foldFunction = i -> "Value:" + i;

      String result = foldable.foldMap(stringMonoid, foldFunction, validKind);

      assertThat(result).isEqualTo("Value:" + DEFAULT_RIGHT_VALUE);
    }

    @Test
    @DisplayName("foldMap() on Left returns monoid empty")
    void foldMapOnLeftReturnsEmpty() {
      Kind<EitherKind.Witness<String>, Integer> leftKind = leftKind(TestErrorType.DEFAULT);

      // Verify input is a Left with expected error
      assertThatEither(narrowToEither(leftKind)).isLeft().hasLeft(TestErrorType.DEFAULT.message());

      Monoid<String> stringMonoid = Monoids.string();
      Function<Integer, String> foldFunction = i -> "Value:" + i;

      String result = foldable.foldMap(stringMonoid, foldFunction, leftKind);

      assertThat(result).isEqualTo(stringMonoid.empty());
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("foldMap() with different monoids")
    void foldMapWithDifferentMonoids() {
      // Verify our test data
      assertThatEither(narrowToEither(validKind)).isRight().hasRightNonNull();

      // Integer addition
      Monoid<Integer> intAddition = Monoids.integerAddition();
      Function<Integer, Integer> doubleFunc = i -> i * 2;
      Integer intResult = foldable.foldMap(intAddition, doubleFunc, validKind);
      assertThat(intResult).isEqualTo(DEFAULT_RIGHT_VALUE * 2);

      // Integer multiplication
      Monoid<Integer> intMultiplication = Monoids.integerMultiplication();
      Function<Integer, Integer> identityFunc = i -> i;
      Integer multResult = foldable.foldMap(intMultiplication, identityFunc, validKind);
      assertThat(multResult).isEqualTo(DEFAULT_RIGHT_VALUE);

      // Boolean AND
      Monoid<Boolean> andMonoid = Monoids.booleanAnd();
      Function<Integer, Boolean> isPositive = i -> i > 0;
      Boolean andResult = foldable.foldMap(andMonoid, isPositive, validKind);
      assertThat(andResult).isTrue();

      // Boolean OR
      Monoid<Boolean> orMonoid = Monoids.booleanOr();
      Function<Integer, Boolean> isNegative = i -> i < 0;
      Boolean orResult = foldable.foldMap(orMonoid, isNegative, validKind);
      assertThat(orResult).isFalse();
    }
  }

  @Nested
  @DisplayName("Individual Test Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<EitherKind.Witness<String>>foldable(EitherTraverse.class)
          .<Integer>instance(foldable)
          .withKind(validKind)
          .withOperations(validMonoid, validFoldMapFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<EitherKind.Witness<String>>foldable(EitherTraverse.class)
          .<Integer>instance(foldable)
          .withKind(validKind)
          .withOperations(validMonoid, validFoldMapFunction)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<EitherKind.Witness<String>>foldable(EitherTraverse.class)
          .<Integer>instance(foldable)
          .withKind(validKind)
          .withOperations(validMonoid, validFoldMapFunction)
          .testExceptions();
    }
  }

  @Nested
  @DisplayName("Monoid Properties Tests")
  class MonoidPropertiesTests {

    @Test
    @DisplayName("foldMap() respects monoid identity")
    void foldMapRespectsMonoidIdentity() {
      Monoid<String> stringMonoid = Monoids.string();
      Kind<EitherKind.Witness<String>, Integer> leftKind = leftKind(TestErrorType.DEFAULT);

      // Verify we're testing with Left values
      assertThatEither(narrowToEither(leftKind)).isLeft().hasLeft(TestErrorType.DEFAULT.message());

      // Left should always give identity
      String leftResult = foldable.foldMap(stringMonoid, TestFunctions.INT_TO_STRING, leftKind);
      assertThat(leftResult).isEqualTo(stringMonoid.empty());

      // Multiple Left values should all give identity
      Monoid<Integer> intMonoid = Monoids.integerAddition();
      Kind<EitherKind.Witness<String>, Integer> left1 = leftKind(TestErrorType.ERROR_1);
      Kind<EitherKind.Witness<String>, Integer> left2 = leftKind(TestErrorType.ERROR_2);

      // Verify both are Left with correct errors
      assertThatEither(narrowToEither(left1)).isLeft().hasLeft(TestErrorType.ERROR_1.message());
      assertThatEither(narrowToEither(left2)).isLeft().hasLeft(TestErrorType.ERROR_2.message());

      assertThat(foldable.foldMap(intMonoid, i -> i, left1)).isEqualTo(intMonoid.empty());
      assertThat(foldable.foldMap(intMonoid, i -> i, left2)).isEqualTo(intMonoid.empty());
    }

    @Test
    @DisplayName("foldMap() with list monoid")
    void foldMapWithListMonoid() {
      Kind<EitherKind.Witness<String>, Integer> leftKind = leftKind(TestErrorType.DEFAULT);

      // Verify test data structure
      assertThatEither(narrowToEither(validKind)).isRight().hasRight(DEFAULT_RIGHT_VALUE);
      assertThatEither(narrowToEither(leftKind)).isLeft().hasLeft(TestErrorType.DEFAULT.message());

      Monoid<List<Integer>> listMonoid = Monoids.list();
      Function<Integer, List<Integer>> singletonList = List::of;

      List<Integer> rightResult = foldable.foldMap(listMonoid, singletonList, validKind);
      assertThat(rightResult).containsExactly(DEFAULT_RIGHT_VALUE);

      List<Integer> leftResult = foldable.foldMap(listMonoid, singletonList, leftKind);
      assertThat(leftResult).isEmpty();
    }

    @Test
    @DisplayName("foldMap() with set monoid")
    void foldMapWithSetMonoid() {
      Kind<EitherKind.Witness<String>, Integer> leftKind = leftKind(TestErrorType.DEFAULT);

      // Verify test data structure
      assertThatEither(narrowToEither(validKind)).isRight().hasRight(DEFAULT_RIGHT_VALUE);
      assertThatEither(narrowToEither(leftKind)).isLeft().hasLeft(TestErrorType.DEFAULT.message());

      Monoid<Set<Integer>> setMonoid = Monoids.set();
      Function<Integer, Set<Integer>> singletonSet = Set::of;

      Set<Integer> rightResult = foldable.foldMap(setMonoid, singletonSet, validKind);
      assertThat(rightResult).containsExactly(DEFAULT_RIGHT_VALUE);

      Set<Integer> leftResult = foldable.foldMap(setMonoid, singletonSet, leftKind);
      assertThat(leftResult).isEmpty();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("foldMap() with null values in Right")
    void foldMapWithNullValuesInRight() {
      Kind<EitherKind.Witness<String>, Integer> rightNull = rightKind((Integer) null);

      // Verify we're testing with Right containing null
      assertThatEither(narrowToEither(rightNull)).isRight().hasRightNull();

      Monoid<String> stringMonoid = Monoids.string();

      Function<Integer, String> nullSafeFunction = i -> i == null ? "null" : i.toString();

      String result = foldable.foldMap(stringMonoid, nullSafeFunction, rightNull);
      assertThat(result).isEqualTo("null");
    }

    @Test
    @DisplayName("foldMap() with null error in Left")
    void foldMapWithNullErrorInLeft() {
      Kind<EitherKind.Witness<String>, Integer> leftNull = EITHER.widen(Either.left(null));

      // Verify we're testing with Left containing null
      assertThatEither(narrowToEither(leftNull)).isLeft().hasLeftNull();

      Monoid<String> stringMonoid = Monoids.string();

      String result = foldable.foldMap(stringMonoid, TestFunctions.INT_TO_STRING, leftNull);
      assertThat(result).isEqualTo(stringMonoid.empty());
    }

    @Test
    @DisplayName("foldMap() with complex transformations")
    void foldMapWithComplexTransformations() {
      // Verify input
      assertThatEither(narrowToEither(validKind)).isRight().hasRight(DEFAULT_RIGHT_VALUE);

      Monoid<String> stringMonoid = Monoids.string();

      Function<Integer, String> complexFunction =
          i -> {
            if (i < 0) return "negative,";
            if (i == 0) return "zero,";
            return "positive:" + i + ",";
          };

      String result = foldable.foldMap(stringMonoid, complexFunction, validKind);
      assertThat(result).isEqualTo("positive:" + DEFAULT_RIGHT_VALUE + ",");
    }
  }

  @Nested
  @DisplayName("Type Safety Tests")
  class TypeSafetyTests {

    @Test
    @DisplayName("foldMap() with different error types")
    void foldMapWithDifferentErrorTypes() {
      Foldable<EitherKind.Witness<ComplexTestError>> complexFoldable = EitherTraverse.instance();

      var rightValue = EITHER.widen(Either.<ComplexTestError, Integer>right(100));
      var leftValue =
          EITHER.widen(
              Either.<ComplexTestError, Integer>left(
                  ComplexTestError.high("E500", "Server error")));

      // Verify structure with EitherAssert
      assertThatEither(EITHER.narrow(rightValue)).isRight().hasRight(100);
      assertThatEither(EITHER.narrow(leftValue))
          .isLeft()
          .hasLeftSatisfying(
              error -> {
                assertThat(error.code()).isEqualTo("E500");
                assertThat(error.severity()).isEqualTo(10);
                assertThat(error.message()).isEqualTo("Server error");
              });

      Monoid<Integer> intMonoid = Monoids.integerAddition();

      assertThat(complexFoldable.foldMap(intMonoid, i -> i * 2, rightValue)).isEqualTo(200);
      assertThat(complexFoldable.foldMap(intMonoid, i -> i * 2, leftValue)).isEqualTo(0);
    }

    @Test
    @DisplayName("foldMap() with nested structures")
    void foldMapWithNestedStructures() {
      var listRight = EITHER.widen(Either.<String, List<Integer>>right(List.of(1, 2, 3)));

      // Verify structure
      assertThatEither(EITHER.narrow(listRight))
          .isRight()
          .hasRightSatisfying(
              list -> {
                assertThat(list).containsExactly(1, 2, 3);
              });

      Foldable<EitherKind.Witness<String>> foldableList = EitherTraverse.instance();
      Monoid<Integer> intMonoid = Monoids.integerAddition();

      Function<List<Integer>, Integer> sumFunction =
          list -> list.stream().mapToInt(Integer::intValue).sum();

      Integer result = foldableList.foldMap(intMonoid, sumFunction, listRight);
      assertThat(result).isEqualTo(6);
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("foldMap() with large values")
    void foldMapWithLargeValues() {
      var largeRight = rightKind(1_000_000);

      // Verify we're testing with the expected value
      assertThatEither(narrowToEither(largeRight)).isRight().hasRight(1_000_000);

      Monoid<String> stringMonoid = Monoids.string();
      Function<Integer, String> expensiveFunction = i -> "Value:" + i + ",";

      String result = foldable.foldMap(stringMonoid, expensiveFunction, largeRight);
      assertThat(result).isEqualTo("Value:1000000,");
    }

    @Test
    @DisplayName("foldMap() with complex data structures")
    void foldMapWithComplexDataStructures() {
      Map<String, Integer> complexMap = Map.of("a", 1, "b", 2, "c", 3);

      var mapRight = EITHER.widen(Either.<String, Map<String, Integer>>right(complexMap));

      // Verify structure
      assertThatEither(EITHER.narrow(mapRight))
          .isRight()
          .hasRightSatisfying(
              map -> {
                assertThat(map).hasSize(3).containsEntry("a", 1).containsEntry("b", 2);
              });

      Foldable<EitherKind.Witness<String>> mapFoldable = EitherTraverse.instance();
      Monoid<Integer> intMonoid = Monoids.integerAddition();

      Function<Map<String, Integer>, Integer> sumValues =
          map -> map.values().stream().mapToInt(Integer::intValue).sum();

      Integer result = mapFoldable.foldMap(intMonoid, sumValues, mapRight);
      assertThat(result).isEqualTo(6);
    }

    @Test
    @DisplayName("foldMap() efficient with Left values")
    void foldMapEfficientWithLeftValues() {
      Kind<EitherKind.Witness<String>, Integer> leftKind = leftKind(TestErrorType.DEFAULT);

      // Verify we're testing with Left
      assertThatEither(narrowToEither(leftKind)).isLeft().hasLeft(TestErrorType.DEFAULT.message());

      // Left values should not execute function, so even expensive functions are safe
      Function<Integer, String> expensiveFunc = i -> "expensive:" + i;

      String result = foldable.foldMap(validMonoid, expensiveFunc, leftKind);

      // Should complete quickly without calling expensive function
      assertThat(result).isEqualTo(validMonoid.empty());
    }
  }

  @Nested
  @DisplayName("Monoid Law Verification")
  class MonoidLawTests {

    @Test
    @DisplayName("foldMap() preserves monoid associativity")
    void foldMapPreservesMonoidAssociativity() {
      // For Either, we can only test this with Right values since Left always returns empty
      // Verify input
      assertThatEither(narrowToEither(validKind)).isRight().hasRight(DEFAULT_RIGHT_VALUE);

      Monoid<String> stringMonoid = Monoids.string();

      // Single Right value should equal itself
      String single = foldable.foldMap(stringMonoid, i -> "test:" + i, validKind);
      assertThat(single).isEqualTo("test:" + DEFAULT_RIGHT_VALUE);

      // Identity element behaviour
      String identity = foldable.foldMap(stringMonoid, i -> stringMonoid.empty(), validKind);
      assertThat(identity).isEqualTo(stringMonoid.empty());
    }

    @Test
    @DisplayName("foldMap() composition with different monoids")
    void foldMapCompositionWithDifferentMonoids() {
      // Test that different monoids produce consistent results
      // Verify input
      assertThatEither(narrowToEither(validKind)).isRight().hasRight(DEFAULT_RIGHT_VALUE);

      Function<Integer, Integer> mapper = i -> i + 10;

      // Addition monoid
      Monoid<Integer> addMonoid = Monoids.integerAddition();
      Integer addResult = foldable.foldMap(addMonoid, mapper, validKind);
      assertThat(addResult).isEqualTo(DEFAULT_RIGHT_VALUE + 10);

      // Multiplication monoid
      Monoid<Integer> multMonoid = Monoids.integerMultiplication();
      Integer multResult = foldable.foldMap(multMonoid, mapper, validKind);
      assertThat(multResult).isEqualTo(DEFAULT_RIGHT_VALUE + 10);

      // Both should have same mapped value but different combination behaviour
      assertThat(addResult).isEqualTo(multResult); // Since there's only one element
    }
  }
}
