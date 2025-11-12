// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for {@link EitherBifunctor}. */
@DisplayName("EitherBifunctor Complete Test Suite")
class EitherBifunctorTest {

  private EitherBifunctor bifunctor;

  @BeforeEach
  void setUp() {
    bifunctor = EitherBifunctor.INSTANCE;
  }

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

    @Test
    @DisplayName("Run complete Bifunctor test pattern")
    void runCompleteBifunctorTestPattern() {
      Kind2<EitherKind2.Witness, String, Integer> validEither =
          EITHER.widen2(Either.right(42));
      Kind2<EitherKind2.Witness, String, Integer> leftEither =
          EITHER.widen2(Either.left("error"));
      Kind2<EitherKind2.Witness, String, Integer> rightEither =
          EITHER.widen2(Either.right(42));
      Function<String, Integer> firstMapper = String::length;
      Function<Integer, String> secondMapper = n -> "Value:" + n;
      Function<Integer, String> compositionFirstMapper = i -> "#" + i;
      Function<String, String> compositionSecondMapper = s -> s + "!";
      BiPredicate<Kind2<EitherKind2.Witness, ?, ?>, Kind2<EitherKind2.Witness, ?, ?>>
          equalityChecker = (k1, k2) -> EITHER.narrow2(k1).equals(EITHER.narrow2(k2));

      TypeClassTest.<EitherKind2.Witness>bifunctor(EitherBifunctor.class)
          .<String, Integer>instance(bifunctor)
          .withKind2(validEither)
          .withFirstMapper(firstMapper)
          .withSecondMapper(secondMapper)
          .withCompositionFirstMapper(compositionFirstMapper)
          .withCompositionSecondMapper(compositionSecondMapper)
          .withEqualityChecker(equalityChecker)
          .withFirstExceptionKind(leftEither)
          .withSecondExceptionKind(rightEither)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      Kind2<EitherKind2.Witness, String, Integer> validEither =
          EITHER.widen2(Either.right(42));

      TypeClassTest.<EitherKind2.Witness>bifunctor(EitherBifunctor.class)
          .<String, Integer>instance(bifunctor)
          .withKind2(validEither)
          .withFirstMapper(String::length)
          .withSecondMapper(n -> "Value:" + n)
          .selectTests()
          .onlyOperations()
          .test();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      Kind2<EitherKind2.Witness, String, Integer> validEither =
          EITHER.widen2(Either.right(42));
      BiPredicate<Kind2<EitherKind2.Witness, ?, ?>, Kind2<EitherKind2.Witness, ?, ?>>
          equalityChecker = (k1, k2) -> EITHER.narrow2(k1).equals(EITHER.narrow2(k2));

      TypeClassTest.<EitherKind2.Witness>bifunctor(EitherBifunctor.class)
          .<String, Integer>instance(bifunctor)
          .withKind2(validEither)
          .withFirstMapper(String::length)
          .withSecondMapper(n -> "Value:" + n)
          .withCompositionFirstMapper(i -> "#" + i)
          .withCompositionSecondMapper(s -> s + "!")
          .withEqualityChecker(equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
    }
  }

  @Nested
  @DisplayName("Bifunctor Operation Tests")
  class BifunctorOperationTests {

    @Test
    @DisplayName("bimap() transforms both Left and Right")
    void bimapTransformsBothChannels() {
      Kind2<EitherKind2.Witness, String, Integer> leftEither =
          EITHER.widen2(Either.left("error"));
      Kind2<EitherKind2.Witness, String, Integer> rightEither =
          EITHER.widen2(Either.right(42));

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
    @DisplayName("first() transforms only Left channel")
    void firstTransformsOnlyLeftChannel() {
      Kind2<EitherKind2.Witness, String, Integer> leftEither =
          EITHER.widen2(Either.left("hello"));
      Kind2<EitherKind2.Witness, String, Integer> rightEither =
          EITHER.widen2(Either.right(42));

      Function<String, Integer> leftMapper = String::length;

      Either<Integer, Integer> transformedLeft =
          EITHER.narrow2(bifunctor.first(leftMapper, leftEither));
      Either<Integer, Integer> transformedRight =
          EITHER.narrow2(bifunctor.first(leftMapper, rightEither));

      assertThat(transformedLeft).isEqualTo(Either.left(5));
      assertThat(transformedRight).isEqualTo(Either.right(42)); // Unchanged
    }

    @Test
    @DisplayName("second() transforms only Right channel")
    void secondTransformsOnlyRightChannel() {
      Kind2<EitherKind2.Witness, String, Integer> leftEither =
          EITHER.widen2(Either.left("error"));
      Kind2<EitherKind2.Witness, String, Integer> rightEither =
          EITHER.widen2(Either.right(42));

      Function<Integer, String> rightMapper = n -> "Value:" + n;

      Either<String, String> transformedLeft =
          EITHER.narrow2(bifunctor.second(rightMapper, leftEither));
      Either<String, String> transformedRight =
          EITHER.narrow2(bifunctor.second(rightMapper, rightEither));

      assertThat(transformedLeft).isEqualTo(Either.left("error")); // Unchanged
      assertThat(transformedRight).isEqualTo(Either.right("Value:42"));
    }
  }

  @Nested
  @DisplayName("Bifunctor Law Tests")
  class BifunctorLawTests {

    private final BiPredicate<Kind2<EitherKind2.Witness, ?, ?>, Kind2<EitherKind2.Witness, ?, ?>>
        equalityChecker = (k1, k2) -> EITHER.narrow2(k1).equals(EITHER.narrow2(k2));

    @Test
    @DisplayName("Identity Law: bimap(id, id, fab) == fab (Left)")
    void identityLawLeft() {
      Kind2<EitherKind2.Witness, String, Integer> leftEither =
          EITHER.widen2(Either.left("error"));

      Kind2<EitherKind2.Witness, String, Integer> result =
          bifunctor.bimap(Function.identity(), Function.identity(), leftEither);

      assertThat(equalityChecker.test(result, leftEither))
          .as("Identity law should hold for Left")
          .isTrue();
    }

    @Test
    @DisplayName("Identity Law: bimap(id, id, fab) == fab (Right)")
    void identityLawRight() {
      Kind2<EitherKind2.Witness, String, Integer> rightEither =
          EITHER.widen2(Either.right(42));

      Kind2<EitherKind2.Witness, String, Integer> result =
          bifunctor.bimap(Function.identity(), Function.identity(), rightEither);

      assertThat(equalityChecker.test(result, rightEither))
          .as("Identity law should hold for Right")
          .isTrue();
    }

    @Test
    @DisplayName("Composition Law: bimap(f2∘f1, g2∘g1, fab) == bimap(f2, g2, bimap(f1, g1, fab))")
    void compositionLaw() {
      Kind2<EitherKind2.Witness, String, Integer> rightEither =
          EITHER.widen2(Either.right(42));

      Function<String, Integer> f1 = String::length;
      Function<Integer, String> f2 = i -> "#" + i;
      Function<Integer, String> g1 = n -> "Value:" + n;
      Function<String, String> g2 = s -> s + "!";

      // Left side: bimap(f2∘f1, g2∘g1, fab)
      Function<String, String> composedF = s -> f2.apply(f1.apply(s));
      Function<Integer, String> composedG = i -> g2.apply(g1.apply(i));
      Kind2<EitherKind2.Witness, String, String> leftSide =
          bifunctor.bimap(composedF, composedG, rightEither);

      // Right side: bimap(f2, g2, bimap(f1, g1, fab))
      Kind2<EitherKind2.Witness, Integer, String> intermediate =
          bifunctor.bimap(f1, g1, rightEither);
      Kind2<EitherKind2.Witness, String, String> rightSide =
          bifunctor.bimap(f2, g2, intermediate);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("Composition law should hold")
          .isTrue();
    }
  }
}
