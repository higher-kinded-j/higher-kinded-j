// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.NonEmptyListAssert.assertThatNonEmptyList;
import static org.higherkindedj.hkt.instances.Witnesses.nonEmptyList;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("NonEmptyListMonad")
class NonEmptyListMonadTest extends NonEmptyListTestBase {

  private Monad<NonEmptyListKind.Witness> monad;

  @BeforeEach
  void setUpMonad() {
    monad = Instances.monad(nonEmptyList());
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.nonemptylist.NonEmptyListLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(monad, value, testFunction, equalityChecker);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.nonemptylist.NonEmptyListLawFixtures#kinds")
    void rightIdentity(String label, Kind<NonEmptyListKind.Witness, Integer> ma) {
      MonadLaws.assertRightIdentity(monad, ma, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.nonemptylist.NonEmptyListLawFixtures#kinds")
    void associativity(String label, Kind<NonEmptyListKind.Witness, Integer> ma) {
      MonadLaws.assertAssociativity(monad, ma, testFunction, chainFunction, equalityChecker);
    }
  }

  @Test
  @DisplayName("Monad contract — operations, validations & exceptions (laws verified above)")
  void monadContract() {
    TypeClassContract.<NonEmptyListKind.Witness>monad(NonEmptyListMonad.class)
        .<Integer>instance(monad)
        .<String>withKind(validKind)
        .withMonadOperations(
            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("of() wraps a value in a singleton")
    void ofWrapsValueInSingleton() {
      assertThatNonEmptyList(monad.of(DEFAULT_VALUE)).hasSize(1).containsExactly(DEFAULT_VALUE);
    }

    @Test
    @DisplayName("map() applies the function to each element")
    void mapAppliesToEachElement() {
      assertThatNonEmptyList(monad.map(x -> x * 2, nelOf(1, 2, 3))).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("ap() applies every function to every value (Cartesian product)")
    void apAppliesEveryFunctionToEveryValue() {
      Kind<NonEmptyListKind.Witness, Function<Integer, String>> funcs =
          nelOf(x -> "N" + x, x -> "X" + (x * 2));
      assertThatNonEmptyList(monad.ap(funcs, nelOf(1, 2))).containsExactly("N1", "N2", "X2", "X4");
    }

    @Test
    @DisplayName("flatMap() applies the function and flattens, staying non-empty")
    void flatMapFlattens() {
      Function<Integer, Kind<NonEmptyListKind.Witness, String>> f = x -> nelOf("v" + x, "v" + x);
      assertThatNonEmptyList(monad.flatMap(f, nelOf(1, 2))).containsExactly("v1", "v1", "v2", "v2");
    }

    @Test
    @DisplayName("flatMap() chains correctly")
    void flatMapChains() {
      Kind<NonEmptyListKind.Witness, Integer> initial = nelOf(1, 2);
      Kind<NonEmptyListKind.Witness, Integer> step1 = monad.flatMap(x -> nelOf(x, x + 10), initial);
      Kind<NonEmptyListKind.Witness, String> result = monad.flatMap(y -> singleNel("N" + y), step1);
      assertThatNonEmptyList(result).containsExactly("N1", "N11", "N2", "N12");
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("flatMap preserves order")
    void flatMapPreservesOrder() {
      Function<Integer, Kind<NonEmptyListKind.Witness, Integer>> f = x -> nelOf(x, x * 10);
      assertThatNonEmptyList(monad.flatMap(f, nelOf(3, 1, 2))).containsExactly(3, 30, 1, 10, 2, 20);
    }

    @Test
    @DisplayName("flatMap with a function returning a null Kind fails appropriately")
    @SuppressWarnings("DataFlowIssue") // function deliberately returns null to verify rejection
    void flatMapWithNullResultFails() {
      Kind<NonEmptyListKind.Witness, Integer> ma = singleNel(1);
      Function<Integer, Kind<NonEmptyListKind.Witness, String>> nullReturning = _ -> null;
      assertThatThrownBy(() -> monad.flatMap(nullReturning, ma))
          .isInstanceOf(KindUnwrapException.class);
    }
  }
}
