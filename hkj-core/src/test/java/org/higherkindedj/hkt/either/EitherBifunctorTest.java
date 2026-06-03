// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

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

/** Test suite for {@link EitherBifunctor}. */
@DisplayName("EitherBifunctor")
class EitherBifunctorTest {

  private EitherBifunctor bifunctor;

  private final BiPredicate<Kind2<EitherKind2.Witness, ?, ?>, Kind2<EitherKind2.Witness, ?, ?>>
      equalityChecker = (k1, k2) -> EITHER.narrow2(k1).equals(EITHER.narrow2(k2));

  @BeforeEach
  void setUp() {
    bifunctor = EitherBifunctor.INSTANCE;
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("kind2Fixtures")
    void identity(String label, Kind2<EitherKind2.Witness, String, Integer> fab) {
      BifunctorLaws.assertIdentity(bifunctor, fab, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("kind2Fixtures")
    void composition(String label, Kind2<EitherKind2.Witness, String, Integer> fab) {
      Function<String, Integer> f1 = String::length;
      Function<Integer, String> f2 = i -> "#" + i;
      Function<Integer, String> g1 = n -> "Value:" + n;
      Function<String, String> g2 = s -> s + "!";
      BifunctorLaws.assertComposition(bifunctor, fab, f1, f2, g1, g2, equalityChecker);
    }

    @ParameterizedTest(name = "first-consistency holds on {0}")
    @MethodSource("kind2Fixtures")
    void firstConsistency(String label, Kind2<EitherKind2.Witness, String, Integer> fab) {
      Function<String, String> f = s -> s + "!";
      BifunctorLaws.assertFirstConsistency(bifunctor, fab, f, equalityChecker);
    }

    @ParameterizedTest(name = "second-consistency holds on {0}")
    @MethodSource("kind2Fixtures")
    void secondConsistency(String label, Kind2<EitherKind2.Witness, String, Integer> fab) {
      Function<Integer, String> g = n -> "Value:" + n;
      BifunctorLaws.assertSecondConsistency(bifunctor, fab, g, equalityChecker);
    }

    /** Both inhabitants, so {@code bimap}/{@code first}/{@code second} exercise each channel. */
    static Stream<Arguments> kind2Fixtures() {
      return Stream.of(
          Arguments.of("Left(\"error\")", EITHER.widen2(Either.<String, Integer>left("error"))),
          Arguments.of("Right(42)", EITHER.widen2(Either.<String, Integer>right(42))));
    }
  }

  @Test
  @DisplayName("Bifunctor contract — operations, validations & exceptions (laws verified above)")
  void bifunctorContract() {
    Kind2<EitherKind2.Witness, String, Integer> left = EITHER.widen2(Either.left("error"));
    Kind2<EitherKind2.Witness, String, Integer> right = EITHER.widen2(Either.right(42));

    TypeClassContract.<EitherKind2.Witness>bifunctor(EitherBifunctor.class)
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

    @Test
    @DisplayName("bimap() transforms both Left and Right")
    void bimapTransformsBothChannels() {
      Kind2<EitherKind2.Witness, String, Integer> leftEither = EITHER.widen2(Either.left("error"));
      Kind2<EitherKind2.Witness, String, Integer> rightEither = EITHER.widen2(Either.right(42));

      Function<String, Integer> leftMapper = String::length;
      Function<Integer, String> rightMapper = n -> "Value:" + n;

      Either<Integer, String> transformedLeft =
          EITHER.narrow2(bifunctor.bimap(leftMapper, rightMapper, leftEither));
      Either<Integer, String> transformedRight =
          EITHER.narrow2(bifunctor.bimap(leftMapper, rightMapper, rightEither));

      assertThat(transformedLeft).isEqualTo(Either.left(5)); // "error".length() == 5
      assertThat(transformedRight).isEqualTo(Either.right("Value:42"));
    }

    @Test
    @DisplayName("first() transforms only the Left channel")
    void firstTransformsOnlyLeftChannel() {
      Kind2<EitherKind2.Witness, String, Integer> leftEither = EITHER.widen2(Either.left("hello"));
      Kind2<EitherKind2.Witness, String, Integer> rightEither = EITHER.widen2(Either.right(42));

      Function<String, Integer> leftMapper = String::length;

      assertThat(EITHER.narrow2(bifunctor.first(leftMapper, leftEither))).isEqualTo(Either.left(5));
      assertThat(EITHER.narrow2(bifunctor.first(leftMapper, rightEither)))
          .isEqualTo(Either.right(42)); // Unchanged
    }

    @Test
    @DisplayName("second() transforms only the Right channel")
    void secondTransformsOnlyRightChannel() {
      Kind2<EitherKind2.Witness, String, Integer> leftEither = EITHER.widen2(Either.left("error"));
      Kind2<EitherKind2.Witness, String, Integer> rightEither = EITHER.widen2(Either.right(42));

      Function<Integer, String> rightMapper = n -> "Value:" + n;

      assertThat(EITHER.narrow2(bifunctor.second(rightMapper, leftEither)))
          .isEqualTo(Either.left("error")); // Unchanged
      assertThat(EITHER.narrow2(bifunctor.second(rightMapper, rightEither)))
          .isEqualTo(Either.right("Value:42"));
    }
  }
}
