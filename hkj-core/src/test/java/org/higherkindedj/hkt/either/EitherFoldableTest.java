// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.test.assertions.FoldableAssertions.*;
import static org.higherkindedj.hkt.test.data.TestExceptions.*;
import static org.higherkindedj.hkt.test.data.TestFunctions.*;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherTraverse Foldable Operations Tests")
class EitherFoldableTest {

  private static final String ERROR_VALUE = "TestError";
  private static final Integer SUCCESS_VALUE = 42;

  private Foldable<EitherKind.Witness<String>> foldable;
  private Kind<EitherKind.Witness<String>, Integer> rightKind;
  private Kind<EitherKind.Witness<String>, Integer> leftKind;

  @BeforeEach
  void setUp() {
    foldable = EitherTraverse.instance();
    rightKind = EITHER.widen(Either.right(SUCCESS_VALUE));
    leftKind = EITHER.widen(Either.left(ERROR_VALUE));
  }

  @Nested
  @DisplayName("FoldMap Operation Tests")
  class FoldMapOperationTests {

    @Test
    @DisplayName("foldMap() on Right applies function")
    void foldMapOnRightAppliesFunction() {
      Monoid<String> stringMonoid = Monoids.string();
      Function<Integer, String> foldFunction = i -> "Value:" + i;

      String result = foldable.foldMap(stringMonoid, foldFunction, rightKind);

      assertThat(result).isEqualTo("Value:" + SUCCESS_VALUE);
    }

    @Test
    @DisplayName("foldMap() on Left returns monoid empty")
    void foldMapOnLeftReturnsEmpty() {
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
      Integer intResult = foldable.foldMap(intAddition, doubleFunc, rightKind);
      assertThat(intResult).isEqualTo(SUCCESS_VALUE * 2);

      // Integer multiplication
      Monoid<Integer> intMultiplication = Monoids.integerMultiplication();
      Function<Integer, Integer> identityFunc = i -> i;
      Integer multResult = foldable.foldMap(intMultiplication, identityFunc, rightKind);
      assertThat(multResult).isEqualTo(SUCCESS_VALUE);

      // Boolean AND
      Monoid<Boolean> andMonoid = Monoids.booleanAnd();
      Function<Integer, Boolean> isPositive = i -> i > 0;
      Boolean andResult = foldable.foldMap(andMonoid, isPositive, rightKind);
      assertThat(andResult).isTrue();

      // Boolean OR
      Monoid<Boolean> orMonoid = Monoids.booleanOr();
      Function<Integer, Boolean> isNegative = i -> i < 0;
      Boolean orResult = foldable.foldMap(orMonoid, isNegative, rightKind);
      assertThat(orResult).isFalse();
    }

    @Test
    @DisplayName("foldMap() null validations")
    void foldMapNullValidations() {
      Monoid<String> validMonoid = Monoids.string();
      Function<Integer, String> validFunction = INT_TO_STRING;

      assertFoldMapMonoidNull(() -> foldable.foldMap(null, validFunction, rightKind));
      assertFoldMapFunctionNull(() -> foldable.foldMap(validMonoid, null, rightKind));
      assertFoldMapKindNull(() -> foldable.foldMap(validMonoid, validFunction, null));
    }
  }

  @Nested
  @DisplayName("Monoid Properties Tests")
  class MonoidPropertiesTests {

    @Test
    @DisplayName("foldMap() respects monoid identity")
    void foldMapRespectsMonoidIdentity() {
      Monoid<String> stringMonoid = Monoids.string();

      // Left should always give identity
      String leftResult = foldable.foldMap(stringMonoid, INT_TO_STRING, leftKind);
      assertThat(leftResult).isEqualTo(stringMonoid.empty());

      // Multiple Left values should all give identity
      Monoid<Integer> intMonoid = Monoids.integerAddition();
      Kind<EitherKind.Witness<String>, Integer> left1 = EITHER.widen(Either.left("E1"));
      Kind<EitherKind.Witness<String>, Integer> left2 = EITHER.widen(Either.left("E2"));

      assertThat(foldable.foldMap(intMonoid, i -> i, left1)).isEqualTo(intMonoid.empty());
      assertThat(foldable.foldMap(intMonoid, i -> i, left2)).isEqualTo(intMonoid.empty());
    }

    @Test
    @DisplayName("foldMap() with list monoid")
    void foldMapWithListMonoid() {
      Monoid<List<Integer>> listMonoid = Monoids.list();
      Function<Integer, List<Integer>> singletonList = List::of;

      List<Integer> rightResult = foldable.foldMap(listMonoid, singletonList, rightKind);
      assertThat(rightResult).containsExactly(SUCCESS_VALUE);

      List<Integer> leftResult = foldable.foldMap(listMonoid, singletonList, leftKind);
      assertThat(leftResult).isEmpty();
    }

    @Test
    @DisplayName("foldMap() with set monoid")
    void foldMapWithSetMonoid() {
      Monoid<java.util.Set<Integer>> setMonoid = Monoids.set();
      Function<Integer, java.util.Set<Integer>> singletonSet = Set::of;

      java.util.Set<Integer> rightResult = foldable.foldMap(setMonoid, singletonSet, rightKind);
      assertThat(rightResult).containsExactly(SUCCESS_VALUE);

      java.util.Set<Integer> leftResult = foldable.foldMap(setMonoid, singletonSet, leftKind);
      assertThat(leftResult).isEmpty();
    }
  }

  @Nested
  @DisplayName("Exception Handling Tests")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("foldMap() propagates function exceptions on Right")
    void foldMapPropagatesFunctionExceptions() {
      RuntimeException testException = runtime("foldMap test");
      Monoid<String> stringMonoid = Monoids.string();
      Function<Integer, String> throwingFunction =
          i -> {
            throw testException;
          };

      assertThatThrownBy(() -> foldable.foldMap(stringMonoid, throwingFunction, rightKind))
          .isSameAs(testException);
    }

    @Test
    @DisplayName("foldMap() on Left doesn't call throwing function")
    void foldMapOnLeftDoesntCallThrowingFunction() {
      RuntimeException testException = runtime("should not throw");
      Monoid<String> stringMonoid = Monoids.string();
      Function<Integer, String> throwingFunction =
          i -> {
            throw testException;
          };

      // Should not throw because function not called on Left
      String result = foldable.foldMap(stringMonoid, throwingFunction, leftKind);
      assertThat(result).isEqualTo(stringMonoid.empty());
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("foldMap() with null values in Right")
    void foldMapWithNullValuesInRight() {
      Kind<EitherKind.Witness<String>, Integer> rightNull = EITHER.widen(Either.right(null));
      Monoid<String> stringMonoid = Monoids.string();

      Function<Integer, String> nullSafeFunction = i -> i == null ? "null" : i.toString();

      String result = foldable.foldMap(stringMonoid, nullSafeFunction, rightNull);
      assertThat(result).isEqualTo("null");
    }

    @Test
    @DisplayName("foldMap() with null error in Left")
    void foldMapWithNullErrorInLeft() {
      Kind<EitherKind.Witness<String>, Integer> leftNull = EITHER.widen(Either.left(null));
      Monoid<String> stringMonoid = Monoids.string();

      String result = foldable.foldMap(stringMonoid, INT_TO_STRING, leftNull);
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

      String result = foldable.foldMap(stringMonoid, complexFunction, rightKind);
      assertThat(result).isEqualTo("positive:" + SUCCESS_VALUE + ",");
    }
  }

  @Nested
  @DisplayName("Type Safety Tests")
  class TypeSafetyTests {

    @Test
    @DisplayName("foldMap() with different error types")
    void foldMapWithDifferentErrorTypes() {
      record ComplexError(String code, int severity) {}

      Foldable<EitherKind.Witness<ComplexError>> complexFoldable = EitherTraverse.instance();

      Kind<EitherKind.Witness<ComplexError>, Integer> rightValue = EITHER.widen(Either.right(100));
      Kind<EitherKind.Witness<ComplexError>, Integer> leftValue =
          EITHER.widen(Either.left(new ComplexError("E500", 5)));

      Monoid<Integer> intMonoid = Monoids.integerAddition();

      assertThat(complexFoldable.foldMap(intMonoid, i -> i * 2, rightValue)).isEqualTo(200);
      assertThat(complexFoldable.foldMap(intMonoid, i -> i * 2, leftValue)).isEqualTo(0);
    }

    @Test
    @DisplayName("foldMap() with nested structures")
    void foldMapWithNestedStructures() {
      Kind<EitherKind.Witness<String>, java.util.List<Integer>> listRight =
          EITHER.widen(Either.right(java.util.List.of(1, 2, 3)));

      Foldable<EitherKind.Witness<String>> foldableList = EitherTraverse.instance();
      Monoid<Integer> intMonoid = Monoids.integerAddition();

      Function<java.util.List<Integer>, Integer> sumFunction =
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
      Kind<EitherKind.Witness<String>, Integer> largeRight = EITHER.widen(Either.right(1_000_000));

      Monoid<String> stringMonoid = Monoids.string();
      Function<Integer, String> expensiveFunction = i -> "Value:" + i + ",";

      String result = foldable.foldMap(stringMonoid, expensiveFunction, largeRight);
      assertThat(result).isEqualTo("Value:1000000,");
    }

    @Test
    @DisplayName("foldMap() with complex data structures")
    void foldMapWithComplexDataStructures() {
      java.util.Map<String, Integer> complexMap = java.util.Map.of("a", 1, "b", 2, "c", 3);

      Kind<EitherKind.Witness<String>, java.util.Map<String, Integer>> mapRight =
          EITHER.widen(Either.right(complexMap));

      Foldable<EitherKind.Witness<String>> mapFoldable = EitherTraverse.instance();
      Monoid<Integer> intMonoid = Monoids.integerAddition();

      Function<java.util.Map<String, Integer>, Integer> sumValues =
          map -> map.values().stream().mapToInt(Integer::intValue).sum();

      Integer result = mapFoldable.foldMap(intMonoid, sumValues, mapRight);
      assertThat(result).isEqualTo(6);
    }
  }
}
