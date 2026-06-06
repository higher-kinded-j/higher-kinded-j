// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.assertj.core.api.Assertions.assertThat;

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

@DisplayName("IdTraverse — Foldable")
class IdFoldableTest extends IdTestBase {

  private Foldable<IdKind.Witness> foldable;
  private Kind<IdKind.Witness, Integer> idKind;
  private Monoid<String> validMonoid;
  private Function<Integer, String> validFoldMapFunction;

  @BeforeEach
  void setUpFoldable() {
    foldable = IdTraverse.INSTANCE;
    idKind = validKind;
    validMonoid = Monoids.string();
    validFoldMapFunction = TestFunctions.INT_TO_STRING;
    validateRequiredFixtures();
  }

  @Test
  @DisplayName("Foldable contract — operations, validations & exceptions (Foldable has no laws)")
  void foldableContract() {
    TypeClassContract.<IdKind.Witness>foldable(IdTraverse.class)
        .<Integer>instance(foldable)
        .withKind(idKind)
        .withOperations(validMonoid, validFoldMapFunction)
        .verify();
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("foldMap() applies the function to the single value")
    void foldMapAppliesFunction() {
      String result = foldable.foldMap(Monoids.string(), i -> "Value:" + i, idKind);
      assertThat(result).isEqualTo("Value:" + DEFAULT_VALUE);
    }

    @Test
    @DisplayName("foldMap() with different monoids")
    void foldMapWithDifferentMonoids() {
      Monoid<Integer> intAddition = Monoids.integerAddition();
      assertThat(foldable.foldMap(intAddition, i -> i * 2, idKind)).isEqualTo(DEFAULT_VALUE * 2);

      Monoid<Integer> intMultiplication = Monoids.integerMultiplication();
      assertThat(foldable.foldMap(intMultiplication, i -> i, idKind)).isEqualTo(DEFAULT_VALUE);

      Monoid<Boolean> andMonoid = Monoids.booleanAnd();
      assertThat(foldable.foldMap(andMonoid, i -> i > 0, idKind)).isTrue();
    }
  }

  @Nested
  @DisplayName("Exactly-One Semantics Tests")
  class ExactlyOneSemanticsTests {

    @Test
    @DisplayName("Id always folds exactly one element")
    void idAlwaysFoldsExactlyOneElement() {
      // Id has no empty case (unlike Maybe's Nothing), so foldMap always processes one element.
      Monoid<Integer> counting = Monoids.integerAddition();
      assertThat(foldable.foldMap(counting, _ -> 1, idKind)).isEqualTo(1);
      assertThat(foldable.foldMap(counting, _ -> 1, idOf("hello"))).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Null Handling Tests")
  class NullHandlingTests {

    @Test
    @DisplayName("foldMap() handles a null value")
    @SuppressWarnings({"DataFlowIssue", "ConstantValue"}) // Id may legitimately hold a null value
    void foldMapHandlesNullValue() {
      Kind<IdKind.Witness, Integer> nullKind = idOf(null);
      String result =
          foldable.foldMap(Monoids.string(), i -> i == null ? "null" : i.toString(), nullKind);
      assertThat(result).isEqualTo("null");
    }
  }
}
