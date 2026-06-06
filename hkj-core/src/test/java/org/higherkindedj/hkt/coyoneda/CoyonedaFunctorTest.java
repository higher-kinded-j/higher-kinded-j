// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.coyoneda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.coyoneda.CoyonedaKindHelper.COYONEDA;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.maybe.MaybeFunctor;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test suite for {@link CoyonedaFunctor}.
 *
 * <p>Verifies the Functor operations and laws; the laws are driven by the shipped {@link
 * FunctorLaws} over {@link CoyonedaLawFixtures}.
 */
@DisplayName("CoyonedaFunctor Tests")
class CoyonedaFunctorTest {

  private static final MaybeFunctor MAYBE_FUNCTOR = MaybeFunctor.INSTANCE;

  private CoyonedaFunctor<MaybeKind.Witness> functor;
  private Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer> validKind;
  private Function<Integer, String> mapper;

  @BeforeEach
  void setUp() {
    functor = new CoyonedaFunctor<>();
    validKind = COYONEDA.lift(MAYBE.just(42));
    mapper = x -> "value:" + x;
  }

  /**
   * Coyoneda fuses maps — the mapper is only applied at {@code lower()} time — so {@link
   * Category#EXCEPTIONS} is omitted (a thrown mapper does not surface until the value is lowered).
   * The Functor laws are verified in the {@code FunctorLawTests} block below.
   */
  @Test
  @DisplayName("Functor contract — operations & validations")
  void functorContract() {
    TypeClassContract.<CoyonedaKind.Witness<MaybeKind.Witness>>functor(CoyonedaFunctor.class)
        .<Integer>instance(functor)
        .<String>withKind(validKind)
        .withMapper(mapper)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
  }

  @Nested
  @DisplayName("Basic Operations")
  class BasicOperationsTests {

    @Test
    @DisplayName("map transforms value correctly")
    void mapTransformsValue() {
      Kind<CoyonedaKind.Witness<MaybeKind.Witness>, String> result = functor.map(mapper, validKind);

      Coyoneda<MaybeKind.Witness, String> coyo = COYONEDA.narrow(result);
      Kind<MaybeKind.Witness, String> lowered = coyo.lower(MAYBE_FUNCTOR);

      assertThat(MAYBE.narrow(lowered).get()).isEqualTo("value:42");
    }

    @Test
    @DisplayName("map preserves Nothing")
    void mapPreservesNothing() {
      Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer> nothingKind =
          COYONEDA.lift(MAYBE.nothing());

      Kind<CoyonedaKind.Witness<MaybeKind.Witness>, String> result =
          functor.map(Object::toString, nothingKind);

      Coyoneda<MaybeKind.Witness, String> coyo = COYONEDA.narrow(result);
      Kind<MaybeKind.Witness, String> lowered = coyo.lower(MAYBE_FUNCTOR);

      assertThat(MAYBE.narrow(lowered).isNothing()).isTrue();
    }

    @Test
    @DisplayName("multiple maps are fused")
    void multipleMapsAreFused() {
      Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer> result =
          functor.map(x -> x * 2, functor.map(x -> x + 1, validKind));

      Coyoneda<MaybeKind.Witness, Integer> coyo = COYONEDA.narrow(result);
      Kind<MaybeKind.Witness, Integer> lowered = coyo.lower(MAYBE_FUNCTOR);

      // (42 + 1) * 2 = 86
      assertThat(MAYBE.narrow(lowered).get()).isEqualTo(86);
    }
  }

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLawTests {
    final Function<Integer, String> f = Object::toString;
    final Function<String, String> g = s -> s + "!";

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.coyoneda.CoyonedaLawFixtures#kinds")
    void identity(String label, Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer> fa) {
      FunctorLaws.assertIdentity(functor, fa, CoyonedaLawFixtures.EQ);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.coyoneda.CoyonedaLawFixtures#kinds")
    void composition(String label, Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer> fa) {
      FunctorLaws.assertComposition(functor, fa, f, g, CoyonedaLawFixtures.EQ);
    }
  }
}
