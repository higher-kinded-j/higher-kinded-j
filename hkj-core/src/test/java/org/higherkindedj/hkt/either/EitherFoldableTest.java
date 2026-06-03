// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.higherkindedj.hkt.test.fixtures.TestFunctions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherTraverse — Foldable")
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

  @Test
  @DisplayName("Foldable contract — operations, validations & exceptions (Foldable has no laws)")
  void foldableContract() {
    TypeClassContract.<EitherKind.Witness<String>>foldable(EitherTraverse.class)
        .<Integer>instance(foldable)
        .withKind(validKind)
        .withOperations(validMonoid, validFoldMapFunction)
        .verify();
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("foldMap() on Right applies function")
    void foldMapOnRightAppliesFunction() {
      Monoid<String> stringMonoid = Monoids.string();
      Function<Integer, String> foldFunction = i -> "Value:" + i;

      String result = foldable.foldMap(stringMonoid, foldFunction, validKind);

      assertThat(result).isEqualTo("Value:" + DEFAULT_RIGHT_VALUE);
    }

    @Test
    @DisplayName("foldMap() on Left returns monoid empty")
    void foldMapOnLeftReturnsEmpty() {
      Kind<EitherKind.Witness<String>, Integer> leftKind = leftKind(TestErrorType.DEFAULT);

      Monoid<String> stringMonoid = Monoids.string();
      Function<Integer, String> foldFunction = i -> "Value:" + i;

      String result = foldable.foldMap(stringMonoid, foldFunction, leftKind);

      assertThat(result).isEqualTo(stringMonoid.empty());
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("foldMap() with different monoids")
    void foldMapWithDifferentMonoids() {
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
  @DisplayName("Monoid Properties Tests")
  class MonoidPropertiesTests {

    @Test
    @DisplayName("foldMap() respects monoid identity")
    void foldMapRespectsMonoidIdentity() {
      Monoid<String> stringMonoid = Monoids.string();
      Kind<EitherKind.Witness<String>, Integer> leftKind = leftKind(TestErrorType.DEFAULT);

      // Left should always give identity
      String leftResult = foldable.foldMap(stringMonoid, TestFunctions.INT_TO_STRING, leftKind);
      assertThat(leftResult).isEqualTo(stringMonoid.empty());

      // Multiple Left values should all give identity
      Monoid<Integer> intMonoid = Monoids.integerAddition();
      Kind<EitherKind.Witness<String>, Integer> left1 = leftKind(TestErrorType.ERROR_1);
      Kind<EitherKind.Witness<String>, Integer> left2 = leftKind(TestErrorType.ERROR_2);
      assertThat(foldable.foldMap(intMonoid, i -> i, left1)).isEqualTo(intMonoid.empty());
      assertThat(foldable.foldMap(intMonoid, i -> i, left2)).isEqualTo(intMonoid.empty());
    }

    @Test
    @DisplayName("foldMap() with list monoid")
    void foldMapWithListMonoid() {
      Kind<EitherKind.Witness<String>, Integer> leftKind = leftKind(TestErrorType.DEFAULT);
      Monoid<List<Integer>> listMonoid = Monoids.list();
      Function<Integer, List<Integer>> singletonList = List::of;

      assertThat(foldable.foldMap(listMonoid, singletonList, validKind))
          .containsExactly(DEFAULT_RIGHT_VALUE);
      assertThat(foldable.foldMap(listMonoid, singletonList, leftKind)).isEmpty();
    }

    @Test
    @DisplayName("foldMap() with set monoid")
    void foldMapWithSetMonoid() {
      Kind<EitherKind.Witness<String>, Integer> leftKind = leftKind(TestErrorType.DEFAULT);
      Monoid<Set<Integer>> setMonoid = Monoids.set();
      Function<Integer, Set<Integer>> singletonSet = Set::of;

      assertThat(foldable.foldMap(setMonoid, singletonSet, validKind))
          .containsExactly(DEFAULT_RIGHT_VALUE);
      assertThat(foldable.foldMap(setMonoid, singletonSet, leftKind)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("foldMap() with null values in Right")
    void foldMapWithNullValuesInRight() {
      Kind<EitherKind.Witness<String>, Integer> rightNull = rightKind(null);
      Monoid<String> stringMonoid = Monoids.string();
      Function<Integer, String> nullSafeFunction = String::valueOf;

      String result = foldable.foldMap(stringMonoid, nullSafeFunction, rightNull);
      assertThat(result).isEqualTo("null");
    }

    @Test
    @DisplayName("foldMap() with null error in Left")
    void foldMapWithNullErrorInLeft() {
      Kind<EitherKind.Witness<String>, Integer> leftNull = EITHER.widen(Either.left(null));
      Monoid<String> stringMonoid = Monoids.string();

      String result = foldable.foldMap(stringMonoid, TestFunctions.INT_TO_STRING, leftNull);
      assertThat(result).isEqualTo(stringMonoid.empty());
    }

    @Test
    @DisplayName("foldMap() with complex transformations")
    void foldMapWithComplexTransformations() {
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

      Monoid<Integer> intMonoid = Monoids.integerAddition();

      assertThat(complexFoldable.foldMap(intMonoid, i -> i * 2, rightValue)).isEqualTo(200);
      assertThat(complexFoldable.foldMap(intMonoid, i -> i * 2, leftValue)).isEqualTo(0);
    }

    @Test
    @DisplayName("foldMap() with nested structures")
    void foldMapWithNestedStructures() {
      var listRight = EITHER.widen(Either.<String, List<Integer>>right(List.of(1, 2, 3)));

      Foldable<EitherKind.Witness<String>> foldableList = EitherTraverse.instance();
      Monoid<Integer> intMonoid = Monoids.integerAddition();

      Function<List<Integer>, Integer> sumFunction =
          list -> list.stream().mapToInt(Integer::intValue).sum();

      Integer result = foldableList.foldMap(intMonoid, sumFunction, listRight);
      assertThat(result).isEqualTo(6);
    }
  }

  @Nested
  @DisplayName("Large-Input Tests")
  class LargeInputTests {

    @Test
    @DisplayName("foldMap() with large values")
    void foldMapWithLargeValues() {
      var largeRight = rightKind(1_000_000);
      Monoid<String> stringMonoid = Monoids.string();
      Function<Integer, String> function = i -> "Value:" + i + ",";

      String result = foldable.foldMap(stringMonoid, function, largeRight);
      assertThat(result).isEqualTo("Value:1000000,");
    }

    @Test
    @DisplayName("foldMap() with complex data structures")
    void foldMapWithComplexDataStructures() {
      Map<String, Integer> complexMap = Map.of("a", 1, "b", 2, "c", 3);
      var mapRight = EITHER.widen(Either.<String, Map<String, Integer>>right(complexMap));

      Foldable<EitherKind.Witness<String>> mapFoldable = EitherTraverse.instance();
      Monoid<Integer> intMonoid = Monoids.integerAddition();

      Function<Map<String, Integer>, Integer> sumValues =
          map -> map.values().stream().mapToInt(Integer::intValue).sum();

      Integer result = mapFoldable.foldMap(intMonoid, sumValues, mapRight);
      assertThat(result).isEqualTo(6);
    }

    @Test
    @DisplayName("foldMap() does not run the function on Left values")
    void foldMapDoesNotRunFunctionOnLeft() {
      Kind<EitherKind.Witness<String>, Integer> leftKind = leftKind(TestErrorType.DEFAULT);

      // Left short-circuits, so a function that would throw is never invoked.
      Function<Integer, String> throwingFunc =
          _ -> {
            throw new AssertionError("function must not run on a Left");
          };

      String result = foldable.foldMap(validMonoid, throwingFunc, leftKind);

      assertThat(result).isEqualTo(validMonoid.empty());
    }
  }

  @Nested
  @DisplayName("Monoid Law Verification")
  class MonoidLawTests {

    @Test
    @DisplayName("foldMap() respects the monoid identity element")
    void foldMapRespectsIdentityElement() {
      Monoid<String> stringMonoid = Monoids.string();

      // A single Right folds to exactly its mapped value.
      String single = foldable.foldMap(stringMonoid, i -> "test:" + i, validKind);
      assertThat(single).isEqualTo("test:" + DEFAULT_RIGHT_VALUE);

      // Mapping every element to the identity yields the identity.
      String identity = foldable.foldMap(stringMonoid, _ -> stringMonoid.empty(), validKind);
      assertThat(identity).isEqualTo(stringMonoid.empty());
    }

    @Test
    @DisplayName("foldMap() with a single element is monoid-independent")
    void foldMapSingleElementIsMonoidIndependent() {
      Function<Integer, Integer> mapper = i -> i + 10;

      Integer addResult = foldable.foldMap(Monoids.integerAddition(), mapper, validKind);
      Integer multResult = foldable.foldMap(Monoids.integerMultiplication(), mapper, validKind);

      // With a single element neither monoid combines anything, so both equal the mapped value.
      assertThat(addResult).isEqualTo(DEFAULT_RIGHT_VALUE + 10);
      assertThat(multResult).isEqualTo(DEFAULT_RIGHT_VALUE + 10);
      assertThat(addResult).isEqualTo(multResult);
    }
  }
}
