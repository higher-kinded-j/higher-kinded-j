// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("MaybeFunctor")
class MaybeFunctorTest extends MaybeTestBase {

  private MaybeFunctor functor;

  @BeforeEach
  void setUp() {
    functor = new MaybeFunctor();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("fixtures")
    void identity(String label, Kind<MaybeKind.Witness, Integer> fa) {
      FunctorLaws.assertIdentity(functor, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("fixtures")
    void composition(String label, Kind<MaybeKind.Witness, Integer> fa) {
      FunctorLaws.assertComposition(functor, fa, validMapper, secondMapper, equalityChecker);
    }

    static Stream<Arguments> fixtures() {
      return Stream.of(
          Arguments.of("Just(0)", MAYBE.widen(Maybe.just(0))),
          Arguments.of("Just(42)", MAYBE.widen(Maybe.just(42))),
          Arguments.of("Just(-1)", MAYBE.widen(Maybe.just(-1))),
          Arguments.of("Nothing", MAYBE.<Integer>widen(Maybe.nothing())));
    }
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
      Kind<MaybeKind.Witness, String> result = functor.map(i -> null, validKind);
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
  }
}
