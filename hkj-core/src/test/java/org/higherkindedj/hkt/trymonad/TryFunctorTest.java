// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;

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

@DisplayName("TryFunctor")
class TryFunctorTest extends TryTestBase {

  private TryFunctor functor;

  @BeforeEach
  void setUp() {
    functor = new TryFunctor();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.trymonad.TryLawFixtures#kinds")
    void identity(String label, Kind<TryKind.Witness, String> fa) {
      FunctorLaws.assertIdentity(functor, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.trymonad.TryLawFixtures#kinds")
    void composition(String label, Kind<TryKind.Witness, String> fa) {
      FunctorLaws.assertComposition(functor, fa, validMapper, secondMapper, equalityChecker);
    }
  }

  /**
   * {@link Category#EXCEPTIONS} is omitted: the generic contract asserts that {@code map}
   * <em>propagates</em> a thrown mapper exception, but {@code Try} instead captures it as a {@link
   * Try.Failure}. That capture behaviour is exercised by {@link
   * Operations#mapWithThrowingMapperBecomesFailure()}.
   */
  @Test
  @DisplayName(
      "Functor contract — operations & validations (laws verified above; Try captures the mapper"
          + " exception, verified below)")
  void functorContract() {
    TypeClassContract.<TryKind.Witness>functor(TryFunctor.class)
        .<String>instance(functor)
        .<Integer>withKind(validKind)
        .withMapper(validMapper)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
  }

  @Nested
  @DisplayName("Operations")
  class Operations {

    @Test
    void mapOnSuccessAppliesFunction() {
      Kind<TryKind.Witness, Integer> result = functor.map(validMapper, validKind);
      assertThatTry(result).isSuccess().hasValue(DEFAULT_SUCCESS_VALUE.length());
    }

    @Test
    void mapOnFailurePassesThrough() {
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);
      Kind<TryKind.Witness, Integer> result = functor.map(validMapper, failure);
      assertThatTry(result).isFailure().hasException(DEFAULT_TEST_EXCEPTION);
    }

    @Test
    void mapWithThrowingMapperBecomesFailure() {
      RuntimeException expected = new RuntimeException("mapper boom");
      Function<String, Integer> throwing =
          _ -> {
            throw expected;
          };
      Kind<TryKind.Witness, Integer> result = functor.map(throwing, validKind);
      assertThatTry(result).isFailure().hasException(expected);
    }
  }

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    void mapWithComplexBranchingTransformation() {
      Function<String, Integer> complex =
          s -> {
            if (s.isEmpty()) return -1;
            if (s.length() < 5) return s.length();
            return 100 + s.length();
          };
      Kind<TryKind.Witness, Integer> result = functor.map(complex, validKind);
      assertThatTry(result).isSuccess().hasValue(100 + DEFAULT_SUCCESS_VALUE.length());
    }
  }
}
