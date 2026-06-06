// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.assertThat;

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

@DisplayName("OptionalTraverse — Foldable")
class OptionalFoldableTest extends OptionalTestBase {

  private Foldable<OptionalKind.Witness> foldable;
  private Kind<OptionalKind.Witness, Integer> presentKind;
  private Kind<OptionalKind.Witness, Integer> emptyKind;
  private Monoid<String> validMonoid;
  private Function<Integer, String> validFoldMapFunction;

  @BeforeEach
  void setUpFoldable() {
    foldable = OptionalTraverse.INSTANCE;
    presentKind = validKind;
    emptyKind = emptyOptional();
    validMonoid = Monoids.string();
    validFoldMapFunction = TestFunctions.INT_TO_STRING;
    validateRequiredFixtures();
  }

  @Test
  @DisplayName("Foldable contract — operations, validations & exceptions (Foldable has no laws)")
  void foldableContract() {
    TypeClassContract.<OptionalKind.Witness>foldable(OptionalTraverse.class)
        .<Integer>instance(foldable)
        .withKind(presentKind)
        .withOperations(validMonoid, validFoldMapFunction)
        .verify();
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("foldMap() on a present value applies the function")
    void foldMapOnPresentAppliesFunction() {
      String result = foldable.foldMap(Monoids.string(), i -> "Value:" + i, presentKind);
      assertThat(result).isEqualTo("Value:" + DEFAULT_PRESENT_VALUE);
    }

    @Test
    @DisplayName("foldMap() on empty returns monoid empty")
    void foldMapOnEmptyReturnsEmpty() {
      Monoid<String> stringMonoid = Monoids.string();
      String result = foldable.foldMap(stringMonoid, i -> "Value:" + i, emptyKind);
      assertThat(result).isEqualTo(stringMonoid.empty());
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("foldMap() with different monoids")
    void foldMapWithDifferentMonoids() {
      Monoid<Integer> intAddition = Monoids.integerAddition();
      assertThat(foldable.foldMap(intAddition, i -> i * 2, presentKind))
          .isEqualTo(DEFAULT_PRESENT_VALUE * 2);

      Monoid<Integer> intMultiplication = Monoids.integerMultiplication();
      assertThat(foldable.foldMap(intMultiplication, i -> i, presentKind))
          .isEqualTo(DEFAULT_PRESENT_VALUE);

      Monoid<Boolean> andMonoid = Monoids.booleanAnd();
      assertThat(foldable.foldMap(andMonoid, i -> i > 0, presentKind)).isTrue();

      Monoid<Boolean> orMonoid = Monoids.booleanOr();
      assertThat(foldable.foldMap(orMonoid, i -> i < 0, presentKind)).isFalse();
    }
  }

  @Nested
  @DisplayName("Monoid Properties Tests")
  class MonoidPropertiesTests {

    @Test
    @DisplayName("foldMap() respects monoid identity")
    void foldMapRespectsMonoidIdentity() {
      Monoid<String> stringMonoid = Monoids.string();
      String emptyResult = foldable.foldMap(stringMonoid, TestFunctions.INT_TO_STRING, emptyKind);
      assertThat(emptyResult).isEqualTo(stringMonoid.empty());
    }

    @Test
    @DisplayName("foldMap() with list monoid")
    void foldMapWithListMonoid() {
      Monoid<List<Integer>> listMonoid = Monoids.list();
      Function<Integer, List<Integer>> singletonList = List::of;

      assertThat(foldable.foldMap(listMonoid, singletonList, presentKind))
          .containsExactly(DEFAULT_PRESENT_VALUE);
      assertThat(foldable.foldMap(listMonoid, singletonList, emptyKind)).isEmpty();
    }

    @Test
    @DisplayName("foldMap() with set monoid")
    void foldMapWithSetMonoid() {
      Monoid<Set<Integer>> setMonoid = Monoids.set();
      Function<Integer, Set<Integer>> singletonSet = Set::of;

      assertThat(foldable.foldMap(setMonoid, singletonSet, presentKind))
          .containsExactly(DEFAULT_PRESENT_VALUE);
      assertThat(foldable.foldMap(setMonoid, singletonSet, emptyKind)).isEmpty();
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
      String result = foldable.foldMap(Monoids.string(), complexFunction, presentKind);
      assertThat(result).isEqualTo("positive:" + DEFAULT_PRESENT_VALUE + ",");
    }

    @Test
    @DisplayName("foldMap() with nested structures")
    void foldMapWithNestedStructures() {
      Kind<OptionalKind.Witness, List<Integer>> listPresent = presentOf(List.of(1, 2, 3));
      Function<List<Integer>, Integer> sumFunction =
          list -> list.stream().mapToInt(Integer::intValue).sum();
      assertThat(foldable.foldMap(Monoids.integerAddition(), sumFunction, listPresent))
          .isEqualTo(6);
    }
  }

  @Nested
  @DisplayName("Short-Circuit Tests")
  class ShortCircuitTests {

    @Test
    @DisplayName("foldMap() does not run the function on empty")
    void foldMapDoesNotRunFunctionOnEmpty() {
      Function<Integer, String> throwingFunc =
          _ -> {
            throw new AssertionError("function must not run on empty");
          };
      String result = foldable.foldMap(validMonoid, throwingFunc, emptyKind);
      assertThat(result).isEqualTo(validMonoid.empty());
    }
  }
}
