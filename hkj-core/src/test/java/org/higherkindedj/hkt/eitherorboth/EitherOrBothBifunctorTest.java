// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.laws.BifunctorLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("EitherOrBothBifunctor")
class EitherOrBothBifunctorTest {

  private EitherOrBothBifunctor bifunctor;

  private final BiPredicate<
          Kind2<EitherOrBothKind2.Witness, ?, ?>, Kind2<EitherOrBothKind2.Witness, ?, ?>>
      equalityChecker = (k1, k2) -> EITHER_OR_BOTH.narrow2(k1).equals(EITHER_OR_BOTH.narrow2(k2));

  @BeforeEach
  void setUp() {
    bifunctor = EitherOrBothBifunctor.INSTANCE;
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    /** {@code Left}, {@code Right} and {@code Both} so every channel of each case is exercised. */
    static Stream<Arguments> kind2Fixtures() {
      return Stream.of(
          Arguments.of("Left(e)", EITHER_OR_BOTH.widen2(EitherOrBoth.<String, Integer>left("e"))),
          Arguments.of("Right(42)", EITHER_OR_BOTH.widen2(EitherOrBoth.<String, Integer>right(42))),
          Arguments.of(
              "Both(w,7)", EITHER_OR_BOTH.widen2(EitherOrBoth.<String, Integer>both("w", 7))));
    }

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("kind2Fixtures")
    void identity(String label, Kind2<EitherOrBothKind2.Witness, String, Integer> fab) {
      BifunctorLaws.assertIdentity(bifunctor, fab, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("kind2Fixtures")
    void composition(String label, Kind2<EitherOrBothKind2.Witness, String, Integer> fab) {
      Function<String, Integer> f1 = String::length;
      Function<Integer, String> f2 = i -> "#" + i;
      Function<Integer, String> g1 = n -> "Value:" + n;
      Function<String, String> g2 = s -> s + "!";
      BifunctorLaws.assertComposition(bifunctor, fab, f1, f2, g1, g2, equalityChecker);
    }

    @ParameterizedTest(name = "first-consistency holds on {0}")
    @MethodSource("kind2Fixtures")
    void firstConsistency(String label, Kind2<EitherOrBothKind2.Witness, String, Integer> fab) {
      Function<String, String> f = s -> s + "!";
      BifunctorLaws.assertFirstConsistency(bifunctor, fab, f, equalityChecker);
    }

    @ParameterizedTest(name = "second-consistency holds on {0}")
    @MethodSource("kind2Fixtures")
    void secondConsistency(String label, Kind2<EitherOrBothKind2.Witness, String, Integer> fab) {
      Function<Integer, String> g = n -> "Value:" + n;
      BifunctorLaws.assertSecondConsistency(bifunctor, fab, g, equalityChecker);
    }
  }

  @Test
  @DisplayName("Bifunctor contract — operations, validations & exceptions (laws verified above)")
  void bifunctorContract() {
    Kind2<EitherOrBothKind2.Witness, String, Integer> left =
        EITHER_OR_BOTH.widen2(EitherOrBoth.left("e"));
    Kind2<EitherOrBothKind2.Witness, String, Integer> right =
        EITHER_OR_BOTH.widen2(EitherOrBoth.right(42));

    TypeClassContract.<EitherOrBothKind2.Witness>bifunctor(EitherOrBothBifunctor.class)
        .<String, Integer>instance(bifunctor)
        .withKind2(right)
        .withFirstMapper(String::length)
        .withSecondMapper(n -> "Value:" + n)
        .withFirstExceptionKind(left)
        .withSecondExceptionKind(right)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operations")
  class Operations {

    private final Function<String, Integer> leftMapper = String::length;
    private final Function<Integer, String> rightMapper = n -> "Value:" + n;

    @Test
    @DisplayName("bimap transforms both channels of a Both")
    void bimapTransformsBoth() {
      Kind2<EitherOrBothKind2.Witness, String, Integer> both =
          EITHER_OR_BOTH.widen2(EitherOrBoth.both("error", 7));
      assertThat(EITHER_OR_BOTH.narrow2(bifunctor.bimap(leftMapper, rightMapper, both)))
          .isEqualTo(EitherOrBoth.both(5, "Value:7"));
    }

    @Test
    @DisplayName("first transforms only the left channel")
    void firstTransformsOnlyLeft() {
      Kind2<EitherOrBothKind2.Witness, String, Integer> both =
          EITHER_OR_BOTH.widen2(EitherOrBoth.both("hello", 7));
      assertThat(EITHER_OR_BOTH.narrow2(bifunctor.first(leftMapper, both)))
          .isEqualTo(EitherOrBoth.both(5, 7));
    }

    @Test
    @DisplayName("second transforms only the right channel")
    void secondTransformsOnlyRight() {
      Kind2<EitherOrBothKind2.Witness, String, Integer> both =
          EITHER_OR_BOTH.widen2(EitherOrBoth.both("w", 7));
      assertThat(EITHER_OR_BOTH.narrow2(bifunctor.second(rightMapper, both)))
          .isEqualTo(EitherOrBoth.both("w", "Value:7"));
    }
  }
}
