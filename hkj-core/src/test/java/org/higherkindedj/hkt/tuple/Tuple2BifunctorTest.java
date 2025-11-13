// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.tuple.Tuple2KindHelper.TUPLE2;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for {@link Tuple2Bifunctor}. */
@DisplayName("Tuple2Bifunctor Complete Test Suite")
class Tuple2BifunctorTest {

  private Tuple2Bifunctor bifunctor;

  @BeforeEach
  void setUp() {
    bifunctor = Tuple2Bifunctor.INSTANCE;
  }

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

    @Test
    @DisplayName("Run complete Bifunctor test pattern")
    void runCompleteBifunctorTestPattern() {
      Kind2<Tuple2Kind2.Witness, String, Integer> validTuple =
          TUPLE2.widen2(new Tuple2<>("hello", 42));
      Function<String, Integer> firstMapper = String::length;
      Function<Integer, String> secondMapper = n -> "Value:" + n;
      Function<Integer, String> compositionFirstMapper = i -> "#" + i;
      Function<String, String> compositionSecondMapper = s -> s + "!";
      BiPredicate<Kind2<Tuple2Kind2.Witness, ?, ?>, Kind2<Tuple2Kind2.Witness, ?, ?>>
          equalityChecker = (k1, k2) -> TUPLE2.narrow2(k1).equals(TUPLE2.narrow2(k2));

      TypeClassTest.<Tuple2Kind2.Witness>bifunctor(Tuple2Bifunctor.class)
          .<String, Integer>instance(bifunctor)
          .withKind2(validTuple)
          .withFirstMapper(firstMapper)
          .withSecondMapper(secondMapper)
          .withCompositionFirstMapper(compositionFirstMapper)
          .withCompositionSecondMapper(compositionSecondMapper)
          .withEqualityChecker(equalityChecker)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Bifunctor Operation Tests")
  class BifunctorOperationTests {

    @Test
    @DisplayName("bimap() transforms both elements")
    void bimapTransformsBothElements() {
      Kind2<Tuple2Kind2.Witness, String, Integer> tuple = TUPLE2.widen2(new Tuple2<>("hello", 42));

      Tuple2<Integer, String> result =
          TUPLE2.narrow2(bifunctor.bimap(String::length, n -> "Value:" + n, tuple));

      assertThat(result).isEqualTo(new Tuple2<>(5, "Value:42"));
    }

    @Test
    @DisplayName("first() transforms only first element")
    void firstTransformsOnlyFirstElement() {
      Kind2<Tuple2Kind2.Witness, String, Integer> tuple = TUPLE2.widen2(new Tuple2<>("hello", 42));

      Tuple2<Integer, Integer> result = TUPLE2.narrow2(bifunctor.first(String::length, tuple));

      assertThat(result).isEqualTo(new Tuple2<>(5, 42));
    }

    @Test
    @DisplayName("second() transforms only second element")
    void secondTransformsOnlySecondElement() {
      Kind2<Tuple2Kind2.Witness, String, Integer> tuple = TUPLE2.widen2(new Tuple2<>("hello", 42));

      Tuple2<String, String> result = TUPLE2.narrow2(bifunctor.second(n -> "Value:" + n, tuple));

      assertThat(result).isEqualTo(new Tuple2<>("hello", "Value:42"));
    }
  }

  @Nested
  @DisplayName("Bifunctor Law Tests")
  class BifunctorLawTests {

    private final BiPredicate<Kind2<Tuple2Kind2.Witness, ?, ?>, Kind2<Tuple2Kind2.Witness, ?, ?>>
        equalityChecker = (k1, k2) -> TUPLE2.narrow2(k1).equals(TUPLE2.narrow2(k2));

    @Test
    @DisplayName("Identity Law: bimap(id, id, fab) == fab")
    void identityLaw() {
      Kind2<Tuple2Kind2.Witness, String, Integer> tuple = TUPLE2.widen2(new Tuple2<>("hello", 42));

      Kind2<Tuple2Kind2.Witness, String, Integer> result =
          bifunctor.bimap(Function.identity(), Function.identity(), tuple);

      assertThat(equalityChecker.test(result, tuple)).as("Identity law should hold").isTrue();
    }

    @Test
    @DisplayName("Composition Law")
    void compositionLaw() {
      Kind2<Tuple2Kind2.Witness, String, Integer> tuple = TUPLE2.widen2(new Tuple2<>("hello", 42));

      Function<String, Integer> f1 = String::length;
      Function<Integer, String> f2 = i -> "#" + i;
      Function<Integer, String> g1 = n -> "Value:" + n;
      Function<String, String> g2 = s -> s + "!";

      // Left side
      Kind2<Tuple2Kind2.Witness, String, String> leftSide =
          bifunctor.bimap(s -> f2.apply(f1.apply(s)), i -> g2.apply(g1.apply(i)), tuple);

      // Right side
      Kind2<Tuple2Kind2.Witness, Integer, String> intermediate = bifunctor.bimap(f1, g1, tuple);
      Kind2<Tuple2Kind2.Witness, String, String> rightSide = bifunctor.bimap(f2, g2, intermediate);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("Composition law should hold")
          .isTrue();
    }
  }
}
