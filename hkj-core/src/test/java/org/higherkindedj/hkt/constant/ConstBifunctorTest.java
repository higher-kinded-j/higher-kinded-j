// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.constant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for {@link ConstBifunctor}. */
@DisplayName("ConstBifunctor Complete Test Suite")
class ConstBifunctorTest {

  private ConstBifunctor bifunctor;

  @BeforeEach
  void setUp() {
    bifunctor = ConstBifunctor.INSTANCE;
  }

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

    @Test
    @DisplayName("Run complete Bifunctor test pattern")
    void runCompleteBifunctorTestPattern() {
      Kind2<ConstKind2.Witness, String, Integer> validConst = CONST.widen2(new Const<>("hello"));
      Function<String, Integer> firstMapper = String::length;
      Function<Integer, String> secondMapper = n -> "Value:" + n;
      Function<Integer, String> compositionFirstMapper = i -> "#" + i;
      Function<String, String> compositionSecondMapper = s -> s + "!";
      BiPredicate<Kind2<ConstKind2.Witness, ?, ?>, Kind2<ConstKind2.Witness, ?, ?>>
          equalityChecker = (k1, k2) -> CONST.narrow2(k1).equals(CONST.narrow2(k2));

      TypeClassTest.<ConstKind2.Witness>bifunctor(ConstBifunctor.class)
          .<String, Integer>instance(bifunctor)
          .withKind2(validConst)
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
    @DisplayName("bimap() transforms only the constant value (first parameter)")
    void bimapTransformsOnlyConstantValue() {
      Kind2<ConstKind2.Witness, String, Integer> const_ = CONST.widen2(new Const<>("hello"));

      Const<Integer, String> result =
          CONST.narrow2(bifunctor.bimap(String::length, n -> "Value:" + n, const_));

      assertThat(result.value()).isEqualTo(5);
    }

    @Test
    @DisplayName("first() transforms the constant value")
    void firstTransformsConstantValue() {
      Kind2<ConstKind2.Witness, String, Integer> const_ = CONST.widen2(new Const<>("hello"));

      Const<Integer, Integer> result = CONST.narrow2(bifunctor.first(String::length, const_));

      assertThat(result.value()).isEqualTo(5);
    }

    @Test
    @DisplayName("second() leaves the constant value unchanged")
    void secondLeavesConstantValueUnchanged() {
      Kind2<ConstKind2.Witness, String, Integer> const_ = CONST.widen2(new Const<>("hello"));

      Const<String, String> result = CONST.narrow2(bifunctor.second(n -> "Value:" + n, const_));

      assertThat(result.value()).isEqualTo("hello");
    }

    @Test
    @DisplayName("second() with different phantom type preserves constant value")
    void secondWithDifferentPhantomTypePreservesConstantValue() {
      Kind2<ConstKind2.Witness, Integer, String> const_ = CONST.widen2(new Const<>(42));

      // Note: mapper must not dereference input since Const applies it to null for exception propagation
      Const<Integer, Double> result = CONST.narrow2(bifunctor.second(s -> 3.14, const_));

      assertThat(result.value()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Bifunctor Law Tests")
  class BifunctorLawTests {

    private final BiPredicate<Kind2<ConstKind2.Witness, ?, ?>, Kind2<ConstKind2.Witness, ?, ?>>
        equalityChecker = (k1, k2) -> CONST.narrow2(k1).equals(CONST.narrow2(k2));

    @Test
    @DisplayName("Identity Law: bimap(id, id, fab) == fab")
    void identityLaw() {
      Kind2<ConstKind2.Witness, String, Integer> const_ = CONST.widen2(new Const<>("hello"));

      Kind2<ConstKind2.Witness, String, Integer> result =
          bifunctor.bimap(Function.identity(), Function.identity(), const_);

      assertThat(equalityChecker.test(result, const_)).as("Identity law should hold").isTrue();
    }

    @Test
    @DisplayName("Composition Law")
    void compositionLaw() {
      Kind2<ConstKind2.Witness, String, Integer> const_ = CONST.widen2(new Const<>("hello"));

      Function<String, Integer> f1 = String::length;
      Function<Integer, String> f2 = i -> "#" + i;
      Function<Integer, String> g1 = n -> "Value:" + n;
      Function<String, String> g2 = s -> s + "!";

      // Left side
      Kind2<ConstKind2.Witness, String, String> leftSide =
          bifunctor.bimap(s -> f2.apply(f1.apply(s)), i -> g2.apply(g1.apply(i)), const_);

      // Right side
      Kind2<ConstKind2.Witness, Integer, String> intermediate = bifunctor.bimap(f1, g1, const_);
      Kind2<ConstKind2.Witness, String, String> rightSide = bifunctor.bimap(f2, g2, intermediate);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("Composition law should hold")
          .isTrue();
    }
  }

  @Nested
  @DisplayName("Const-specific Property Tests")
  class ConstSpecificPropertyTests {

    @Test
    @DisplayName("Multiple second() calls preserve the constant value")
    void multipleSecondCallsPreserveConstantValue() {
      Const<String, Integer> original = new Const<>("constant");
      Kind2<ConstKind2.Witness, String, Integer> kind = CONST.widen2(original);

      Kind2<ConstKind2.Witness, String, String> result1 = bifunctor.second(i -> "transform1", kind);
      Kind2<ConstKind2.Witness, String, Double> result2 =
          bifunctor.second(s -> 3.14, result1);
      Kind2<ConstKind2.Witness, String, Boolean> result3 =
          bifunctor.second(d -> true, result2);

      Const<String, Boolean> finalResult = CONST.narrow2(result3);
      assertThat(finalResult.value()).isEqualTo("constant");
    }

    @Test
    @DisplayName("first() changes value, second() does not")
    void firstChangesValueSecondDoesNot() {
      Const<String, Integer> original = new Const<>("hello");
      Kind2<ConstKind2.Witness, String, Integer> kind = CONST.widen2(original);

      // Apply first() to transform the constant value
      Kind2<ConstKind2.Witness, Integer, Integer> afterFirst =
          bifunctor.first(String::length, kind);
      Const<Integer, Integer> afterFirstNarrowed = CONST.narrow2(afterFirst);
      assertThat(afterFirstNarrowed.value()).isEqualTo(5);

      // Apply second() to change phantom type only
      Kind2<ConstKind2.Witness, Integer, String> afterSecond =
          bifunctor.second(i -> "ignored", afterFirst);
      Const<Integer, String> afterSecondNarrowed = CONST.narrow2(afterSecond);
      assertThat(afterSecondNarrowed.value()).isEqualTo(5); // Still 5, not changed
    }
  }
}
