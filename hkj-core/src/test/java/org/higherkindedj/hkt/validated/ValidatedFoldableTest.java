// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
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

@DisplayName("ValidatedTraverse — Foldable")
class ValidatedFoldableTest extends ValidatedTestBase {

  private Foldable<ValidatedKind.Witness<String>> foldable;
  private Monoid<String> validMonoid;
  private Function<Integer, String> validFoldMapFunction;

  @BeforeEach
  void setUpFoldable() {
    foldable = ValidatedTraverse.instance();
    validMonoid = Monoids.string();
    validFoldMapFunction = TestFunctions.INT_TO_STRING;
    validateRequiredFixtures();
  }

  @Test
  @DisplayName("Foldable contract — operations, validations & exceptions (Foldable has no laws)")
  void foldableContract() {
    TypeClassContract.<ValidatedKind.Witness<String>>foldable(ValidatedTraverse.class)
        .<Integer>instance(foldable)
        .withKind(validKind)
        .withOperations(validMonoid, validFoldMapFunction)
        .verify();
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("foldMap() on Valid applies function")
    void foldMapOnValidAppliesFunction() {
      String result = foldable.foldMap(validMonoid, i -> "Value:" + i, validKind);
      assertThat(result).isEqualTo("Value:" + DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("foldMap() on Invalid returns monoid empty")
    void foldMapOnInvalidReturnsEmpty() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);

      String result = foldable.foldMap(validMonoid, i -> "Value:" + i, invalid);
      assertThat(result).isEqualTo(validMonoid.empty());
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("foldMap() with different monoids")
    void foldMapWithDifferentMonoids() {
      // Integer addition
      Monoid<Integer> intAddition = Monoids.integerAddition();
      Integer addResult = foldable.foldMap(intAddition, i -> i * 2, validKind);
      assertThat(addResult).isEqualTo(DEFAULT_VALID_VALUE * 2);

      // Integer multiplication
      Monoid<Integer> intMultiplication = Monoids.integerMultiplication();
      Integer multResult = foldable.foldMap(intMultiplication, i -> i, validKind);
      assertThat(multResult).isEqualTo(DEFAULT_VALID_VALUE);

      // Boolean AND
      Monoid<Boolean> andMonoid = Monoids.booleanAnd();
      Boolean andResult = foldable.foldMap(andMonoid, i -> i > 0, validKind);
      assertThat(andResult).isTrue();

      // Boolean OR
      Monoid<Boolean> orMonoid = Monoids.booleanOr();
      Boolean orResult = foldable.foldMap(orMonoid, i -> i < 0, validKind);
      assertThat(orResult).isFalse();
    }
  }

  @Nested
  @DisplayName("Monoid Properties Tests")
  class MonoidPropertiesTests {

    @Test
    @DisplayName("foldMap() respects monoid identity for Invalid")
    void foldMapRespectsMonoidIdentity() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);

      // Invalid should always give the monoid identity, whatever the monoid.
      assertThat(foldable.foldMap(validMonoid, validFoldMapFunction, invalid))
          .isEqualTo(validMonoid.empty());

      Monoid<Integer> intMonoid = Monoids.integerAddition();
      assertThat(foldable.foldMap(intMonoid, i -> i, invalid)).isEqualTo(intMonoid.empty());
    }

    @Test
    @DisplayName("foldMap() with list monoid")
    void foldMapWithListMonoid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);
      Monoid<List<Integer>> listMonoid = Monoids.list();
      Function<Integer, List<Integer>> singletonList = List::of;

      assertThat(foldable.foldMap(listMonoid, singletonList, validKind))
          .containsExactly(DEFAULT_VALID_VALUE);
      assertThat(foldable.foldMap(listMonoid, singletonList, invalid)).isEmpty();
    }

    @Test
    @DisplayName("foldMap() with set monoid")
    void foldMapWithSetMonoid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);
      Monoid<Set<Integer>> setMonoid = Monoids.set();
      Function<Integer, Set<Integer>> singletonSet = Set::of;

      assertThat(foldable.foldMap(setMonoid, singletonSet, validKind))
          .containsExactly(DEFAULT_VALID_VALUE);
      assertThat(foldable.foldMap(setMonoid, singletonSet, invalid)).isEmpty();
    }

    @Test
    @DisplayName("foldMap() with a single element is monoid-independent")
    void foldMapSingleElementIsMonoidIndependent() {
      Function<Integer, Integer> mapper = i -> i + 10;

      Integer addResult = foldable.foldMap(Monoids.integerAddition(), mapper, validKind);
      Integer multResult = foldable.foldMap(Monoids.integerMultiplication(), mapper, validKind);

      // With a single element neither monoid combines anything, so both equal the mapped value.
      assertThat(addResult).isEqualTo(DEFAULT_VALID_VALUE + 10);
      assertThat(multResult).isEqualTo(DEFAULT_VALID_VALUE + 10);
      assertThat(addResult).isEqualTo(multResult);
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("foldMap() with complex transformations")
    void foldMapWithComplexTransformations() {
      Function<Integer, String> complexFunction =
          i -> {
            if (i < 0) return "negative,";
            if (i == 0) return "zero,";
            return "positive:" + i + ",";
          };

      String result = foldable.foldMap(validMonoid, complexFunction, validKind);
      assertThat(result).isEqualTo("positive:" + DEFAULT_VALID_VALUE + ",");
    }

    @Test
    @DisplayName("foldMap() does not run the function on Invalid values")
    void foldMapDoesNotRunFunctionOnInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);

      // Invalid short-circuits, so a function that would throw is never invoked.
      Function<Integer, String> throwingFunc =
          _ -> {
            throw new AssertionError("function must not run on an Invalid");
          };

      String result = foldable.foldMap(validMonoid, throwingFunc, invalid);
      assertThat(result).isEqualTo(validMonoid.empty());
    }
  }

  @Nested
  @DisplayName("Type Safety Tests")
  class TypeSafetyTests {

    @Test
    @DisplayName("foldMap() with different error types")
    void foldMapWithDifferentErrorTypes() {
      Foldable<ValidatedKind.Witness<Integer>> intErrorFoldable = ValidatedTraverse.instance();

      Kind<ValidatedKind.Witness<Integer>, Integer> valid = VALIDATED.widen(Validated.valid(100));
      Kind<ValidatedKind.Witness<Integer>, Integer> invalid =
          VALIDATED.widen(Validated.invalid(500));

      Monoid<Integer> intMonoid = Monoids.integerAddition();

      assertThat(intErrorFoldable.foldMap(intMonoid, i -> i * 2, valid)).isEqualTo(200);
      assertThat(intErrorFoldable.foldMap(intMonoid, i -> i * 2, invalid)).isEqualTo(0);
    }
  }
}
