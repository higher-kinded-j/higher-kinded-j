// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("MaybeFunctor")
class MaybeFunctorTest extends MaybeTestBase {

  private MaybeFunctor functor;

  @BeforeEach
  void setUp() {
    functor = MaybeFunctor.INSTANCE;
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.maybe.MaybeLawFixtures#kinds")
    void identity(String label, Kind<MaybeKind.Witness, Integer> fa) {
      FunctorLaws.assertIdentity(functor, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.maybe.MaybeLawFixtures#kinds")
    void composition(String label, Kind<MaybeKind.Witness, Integer> fa) {
      FunctorLaws.assertComposition(functor, fa, validMapper, secondMapper, equalityChecker);
    }
  }

  @Test
  @DisplayName("Functor contract — operations, validations & exceptions (laws verified above)")
  void functorContract() {
    TypeClassContract.<MaybeKind.Witness>functor(MaybeFunctor.class)
        .<Integer>instance(functor)
        .<String>withKind(validKind)
        .withMapper(validMapper)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operations")
  class Operations {

    @Test
    void mapOnJustAppliesFunction() {
      Kind<MaybeKind.Witness, String> result = functor.map(validMapper, validKind);
      assertThatMaybe(result).isJust().hasValue(String.valueOf(DEFAULT_JUST_VALUE));
    }

    @Test
    void mapOnNothingReturnsNothing() {
      Kind<MaybeKind.Witness, String> result = functor.map(validMapper, nothingKind());
      assertThatMaybe(result).isNothing();
    }

    @Test
    void mapWithNullReturningMapperYieldsNothing() {
      Kind<MaybeKind.Witness, String> result = functor.map(_ -> null, validKind);
      assertThatMaybe(result).isNothing();
    }
  }

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    void mapWithComplexBranchingTransformation() {
      Function<Integer, String> complex =
          i -> {
            if (i < 0) return "negative";
            if (i == 0) return "zero";
            return "positive:" + i;
          };
      Kind<MaybeKind.Witness, String> result = functor.map(complex, validKind);
      assertThatMaybe(result).isJust().hasValue("positive:" + DEFAULT_JUST_VALUE);
    }

    @Test
    void mapWithThrowingMapperOnNothingDoesNotRun() {
      Function<Integer, String> throwing =
          _ -> {
            throw new AssertionError("mapper must not run on Nothing");
          };
      assertThatCode(() -> functor.map(throwing, nothingKind())).doesNotThrowAnyException();
    }
  }
}
