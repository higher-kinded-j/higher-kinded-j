// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.OptionalKindAssert.assertThatOptionalKind;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
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

@DisplayName("OptionalFunctor")
class OptionalFunctorTest extends OptionalTestBase {

  private OptionalFunctor functor;

  @BeforeEach
  void setUp() {
    functor = new OptionalFunctor();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("fixtures")
    void identity(String label, Kind<OptionalKind.Witness, Integer> fa) {
      FunctorLaws.assertIdentity(functor, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("fixtures")
    void composition(String label, Kind<OptionalKind.Witness, Integer> fa) {
      FunctorLaws.assertComposition(functor, fa, validMapper, secondMapper, equalityChecker);
    }

    static Stream<Arguments> fixtures() {
      return Stream.of(
          Arguments.of("present(0)", OPTIONAL.widen(Optional.of(0))),
          Arguments.of("present(42)", OPTIONAL.widen(Optional.of(42))),
          Arguments.of("present(-1)", OPTIONAL.widen(Optional.of(-1))),
          Arguments.of("empty", OPTIONAL.<Integer>widen(Optional.empty())));
    }
  }

  @Nested
  @DisplayName("Operations")
  class Operations {

    @Test
    void mapOnPresentAppliesFunction() {
      Kind<OptionalKind.Witness, String> result = functor.map(validMapper, validKind);
      assertThatOptionalKind(result).isPresent().contains(String.valueOf(DEFAULT_PRESENT_VALUE));
    }

    @Test
    void mapOnEmptyReturnsEmpty() {
      Kind<OptionalKind.Witness, String> result = functor.map(validMapper, emptyOptional());
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    void mapWithNullReturningMapperYieldsEmpty() {
      Kind<OptionalKind.Witness, String> result = functor.map(i -> null, validKind);
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    void mapTransformsToDifferentType() {
      Function<Integer, Double> toDouble = i -> i * 1.5;
      Kind<OptionalKind.Witness, Double> result = functor.map(toDouble, validKind);
      assertThatOptionalKind(result).isPresent().contains(DEFAULT_PRESENT_VALUE * 1.5);
    }
  }

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    void mapPreservesEmptyThroughChains() {
      Kind<OptionalKind.Witness, Integer> empty = emptyOptional();
      Function<Integer, Integer> doubleFunc = i -> i * 2;
      Function<Integer, String> stringFunc = i -> "Value: " + i;
      Kind<OptionalKind.Witness, String> result =
          functor.map(stringFunc, functor.map(doubleFunc, empty));
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    void mapWithComplexBranchingTransformation() {
      Function<Integer, String> complex =
          i -> {
            if (i < 0) return "negative";
            if (i == 0) return "zero";
            return "positive:" + i;
          };
      Kind<OptionalKind.Witness, String> result = functor.map(complex, validKind);
      assertThatOptionalKind(result).isPresent().contains("positive:" + DEFAULT_PRESENT_VALUE);
    }

    @Test
    void mapWithConditionalNullMapperYieldsEmpty() {
      Function<Integer, String> conditional = i -> i > 100 ? "large" : null;
      Kind<OptionalKind.Witness, String> result = functor.map(conditional, validKind);
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    void mapIsReferentiallyTransparent() {
      Function<Integer, String> mapper = i -> "Value: " + i;
      Kind<OptionalKind.Witness, String> r1 = functor.map(mapper, validKind);
      Kind<OptionalKind.Witness, String> r2 = functor.map(mapper, validKind);
      assertThat(narrowToOptional(r1)).isEqualTo(narrowToOptional(r2));
    }
  }
}
