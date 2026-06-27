// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Semigroups;
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

@DisplayName("EitherOrBothMonad")
class EitherOrBothMonadTest extends EitherOrBothTestBase {

  private Monad<EitherOrBothKind.Witness<String>> monad;

  @BeforeEach
  void setUpMonad() {
    monad = Instances.eitherOrBoth(Semigroups.string());
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.eitherorboth.EitherOrBothLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(monad, value, testFunction, equalityChecker);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.eitherorboth.EitherOrBothLawFixtures#kinds")
    void rightIdentity(String label, Kind<EitherOrBothKind.Witness<String>, Integer> ma) {
      MonadLaws.assertRightIdentity(monad, ma, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.eitherorboth.EitherOrBothLawFixtures#kinds")
    void associativity(String label, Kind<EitherOrBothKind.Witness<String>, Integer> ma) {
      MonadLaws.assertAssociativity(monad, ma, testFunction, chainFunction, equalityChecker);
    }
  }

  @Test
  @DisplayName("Monad contract — operations, validations & exceptions (laws verified above)")
  void monadContract() {
    TypeClassContract.<EitherOrBothKind.Witness<String>>monad(EitherOrBothMonad.class)
        .<Integer>instance(monad)
        .<String>withKind(validKind)
        .withMonadOperations(
            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operations")
  class Operations {

    @Test
    void ofWrapsInRight() {
      assertThat(EITHER_OR_BOTH.narrow(monad.of(7))).isEqualTo(EitherOrBoth.right(7));
    }

    @Test
    void flatMapAccumulatesOnBoth() {
      Function<Integer, Kind<EitherOrBothKind.Witness<String>, String>> f =
          i -> bothK("x", "v" + i);
      assertThat(EITHER_OR_BOTH.narrow(monad.flatMap(f, bothK("w", 5))))
          .isEqualTo(EitherOrBoth.both("wx", "v5"));
    }

    @Test
    void flatMapShortCircuitsOnLeft() {
      Function<Integer, Kind<EitherOrBothKind.Witness<String>, String>> f = i -> rightK("v" + i);
      assertThat(EITHER_OR_BOTH.narrow(monad.flatMap(f, leftK("e"))))
          .isEqualTo(EitherOrBoth.left("e"));
    }

    @Test
    void flatMapWithNullResultFails() {
      Function<Integer, Kind<EitherOrBothKind.Witness<String>, String>> nullReturning = _ -> null;
      assertThatThrownBy(() -> monad.flatMap(nullReturning, rightK(1)))
          .isInstanceOf(KindUnwrapException.class);
    }
  }

  @Nested
  @DisplayName("ap — monad-consistent, NOT Validated-style")
  class Ap {

    private final Function<Integer, String> g = x -> "g" + x;

    @Test
    void apBothWithBothAccumulatesLeftToRight() {
      Kind<EitherOrBothKind.Witness<String>, Function<Integer, String>> ff = bothK("f", g);
      assertThat(EITHER_OR_BOTH.narrow(monad.ap(ff, bothK("a", 3))))
          .isEqualTo(EitherOrBoth.both("fa", "g3"));
    }

    @Test
    void apRightWithRightApplies() {
      Kind<EitherOrBothKind.Witness<String>, Function<Integer, String>> ff = rightK(g);
      assertThat(EITHER_OR_BOTH.narrow(monad.ap(ff, rightK(3))))
          .isEqualTo(EitherOrBoth.right("g3"));
    }

    @Test
    @DisplayName("Left function side short-circuits and drops the argument's left (vs Validated)")
    void apLeftShortCircuitsDroppingArgumentLeft() {
      Kind<EitherOrBothKind.Witness<String>, Function<Integer, String>> ff = leftK("f");
      // Validated-style accumulation would yield Left("fa"); the monad yields just Left("f").
      assertThat(EITHER_OR_BOTH.narrow(monad.ap(ff, bothK("a", 3))))
          .isEqualTo(EitherOrBoth.left("f"));
    }

    @Test
    void apBothWithLeftBecomesLeftAccumulated() {
      Kind<EitherOrBothKind.Witness<String>, Function<Integer, String>> ff = bothK("f", g);
      assertThat(EITHER_OR_BOTH.narrow(monad.ap(ff, leftK("a"))))
          .isEqualTo(EitherOrBoth.left("fa"));
    }

    @Test
    void apBothWithRightCarriesWarning() {
      Kind<EitherOrBothKind.Witness<String>, Function<Integer, String>> ff = bothK("f", g);
      assertThat(EITHER_OR_BOTH.narrow(monad.ap(ff, rightK(3))))
          .isEqualTo(EitherOrBoth.both("f", "g3"));
    }

    @Test
    void apRightWithBothCarriesArgumentWarning() {
      Kind<EitherOrBothKind.Witness<String>, Function<Integer, String>> ff = rightK(g);
      assertThat(EITHER_OR_BOTH.narrow(monad.ap(ff, bothK("a", 3))))
          .isEqualTo(EitherOrBoth.both("a", "g3"));
    }
  }
}
