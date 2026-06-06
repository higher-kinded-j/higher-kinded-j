// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.higherkindedj.hkt.assertions.OptionalKindAssert.assertThatOptionalKind;

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
    @MethodSource("org.higherkindedj.hkt.optional.OptionalLawFixtures#kinds")
    void identity(String label, Kind<OptionalKind.Witness, Integer> fa) {
      FunctorLaws.assertIdentity(functor, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.optional.OptionalLawFixtures#kinds")
    void composition(String label, Kind<OptionalKind.Witness, Integer> fa) {
      FunctorLaws.assertComposition(functor, fa, validMapper, secondMapper, equalityChecker);
    }
  }

  @Test
  @DisplayName("Functor contract — operations, validations & exceptions (laws verified above)")
  void functorContract() {
    TypeClassContract.<OptionalKind.Witness>functor(OptionalFunctor.class)
        .<Integer>instance(functor)
        .<String>withKind(validKind)
        .withMapper(validMapper)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
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
      Kind<OptionalKind.Witness, String> result = functor.map(_ -> null, validKind);
      assertThatOptionalKind(result).isEmpty();
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
      Kind<OptionalKind.Witness, String> result = functor.map(complex, validKind);
      assertThatOptionalKind(result).isPresent().contains("positive:" + DEFAULT_PRESENT_VALUE);
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // conditional null return exercises Optional.map's handling
    void mapWithConditionalNullMapperYieldsEmpty() {
      Function<Integer, String> conditional = i -> i > 100 ? "large" : null;
      Kind<OptionalKind.Witness, String> result = functor.map(conditional, validKind);
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    void mapWithThrowingMapperOnEmptyDoesNotRun() {
      Function<Integer, String> throwing =
          _ -> {
            throw new AssertionError("mapper must not run on empty");
          };
      assertThatCode(() -> functor.map(throwing, emptyOptional())).doesNotThrowAnyException();
    }
  }
}
