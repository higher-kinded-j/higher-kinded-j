// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

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
    @MethodSource("fixtures")
    void identity(String label, Kind<TryKind.Witness, String> fa) {
      FunctorLaws.assertIdentity(functor, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("fixtures")
    void composition(String label, Kind<TryKind.Witness, String> fa) {
      FunctorLaws.assertComposition(functor, fa, validMapper, secondMapper, equalityChecker);
    }

    static Stream<Arguments> fixtures() {
      RuntimeException ex = new RuntimeException("test");
      return Stream.of(
          Arguments.of("Success(\"\")", TRY.widen(Try.success(""))),
          Arguments.of("Success(\"hello\")", TRY.widen(Try.success("hello"))),
          Arguments.of("Success(\"abcdef\")", TRY.widen(Try.success("abcdef"))),
          Arguments.of("Failure(ex)", TRY.<String>widen(Try.failure(ex))));
    }
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
          s -> {
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
