// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.constant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.laws.BifunctorLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test suite for {@link ConstBifunctor}.
 *
 * <p>{@code Const} is a product-type bifunctor: {@code bimap}/{@code first} apply the first
 * function to the held constant value, while the second function only changes the phantom type and
 * is never invoked. Because the second mapper is never applied it cannot throw, so the contract
 * omits {@link Category#EXCEPTIONS}. The laws are driven by the shipped {@link BifunctorLaws} over
 * {@link ConstLawFixtures}.
 */
@DisplayName("ConstBifunctor Tests")
class ConstBifunctorTest {

  private ConstBifunctor bifunctor;

  @BeforeEach
  void setUp() {
    bifunctor = ConstBifunctor.INSTANCE;
  }

  /**
   * {@code Const}'s second mapper is phantom (never applied), so a thrown second mapper cannot
   * surface — {@link Category#EXCEPTIONS} is omitted. The Bifunctor laws are verified in the {@code
   * Laws} block below.
   */
  @Test
  @DisplayName("Bifunctor contract — operations & validations")
  void bifunctorContract() {
    Kind2<ConstKind2.Witness, String, Integer> validConst = CONST.widen2(new Const<>("hello"));

    TypeClassContract.<ConstKind2.Witness>bifunctor(ConstBifunctor.class)
        .<String, Integer>instance(bifunctor)
        .withKind2(validConst)
        .withFirstMapper(String::length)
        .withSecondMapper(n -> "Value:" + n)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
  }

  @Nested
  @DisplayName("Bifunctor Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.constant.ConstLawFixtures#kind2s")
    void identity(String label, Kind2<ConstKind2.Witness, String, Integer> fab) {
      BifunctorLaws.assertIdentity(bifunctor, fab, ConstLawFixtures.BIFUNCTOR_EQ);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.constant.ConstLawFixtures#kind2s")
    void composition(String label, Kind2<ConstKind2.Witness, String, Integer> fab) {
      Function<String, Integer> f1 = String::length;
      Function<Integer, String> f2 = i -> "#" + i;
      Function<Integer, String> g1 = n -> "Value:" + n;
      Function<String, String> g2 = s -> s + "!";
      BifunctorLaws.assertComposition(
          bifunctor, fab, f1, f2, g1, g2, ConstLawFixtures.BIFUNCTOR_EQ);
    }

    @ParameterizedTest(name = "first-consistency holds on {0}")
    @MethodSource("org.higherkindedj.hkt.constant.ConstLawFixtures#kind2s")
    void firstConsistency(String label, Kind2<ConstKind2.Witness, String, Integer> fab) {
      Function<String, Integer> f = String::length;
      BifunctorLaws.assertFirstConsistency(bifunctor, fab, f, ConstLawFixtures.BIFUNCTOR_EQ);
    }

    @ParameterizedTest(name = "second-consistency holds on {0}")
    @MethodSource("org.higherkindedj.hkt.constant.ConstLawFixtures#kind2s")
    void secondConsistency(String label, Kind2<ConstKind2.Witness, String, Integer> fab) {
      Function<Integer, String> g = n -> "Value:" + n;
      BifunctorLaws.assertSecondConsistency(bifunctor, fab, g, ConstLawFixtures.BIFUNCTOR_EQ);
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

      // The mapper is validated for non-null but never invoked (phantom type)
      Const<Integer, Double> result = CONST.narrow2(bifunctor.second(_ -> 3.14, const_));

      assertThat(result.value()).isEqualTo(42);
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

      Kind2<ConstKind2.Witness, String, String> result1 = bifunctor.second(_ -> "transform1", kind);
      Kind2<ConstKind2.Witness, String, Double> result2 = bifunctor.second(_ -> 3.14, result1);
      Kind2<ConstKind2.Witness, String, Boolean> result3 = bifunctor.second(_ -> true, result2);

      Const<String, Boolean> finalResult = CONST.narrow2(result3);
      assertThat(finalResult.value()).isEqualTo("constant");
    }

    @Test
    @DisplayName("Const.mapSecond should not NPE when mapper cannot handle null (audit issue #1)")
    void mapSecondShouldNotNpeWithNonNullMapper() {
      Const<String, Integer> c = new Const<>("hello");
      // This mapper does arithmetic on its input — calling it with null would NPE
      Const<String, Double> result = c.mapSecond(i -> i * 2.0);
      assertThat(result.value()).isEqualTo("hello");
    }

    @Test
    @DisplayName(
        "Const.bimap should not NPE when second mapper cannot handle null (audit issue #1)")
    void bimapShouldNotNpeWithNonNullSecondMapper() {
      Const<String, Integer> c = new Const<>("hello");
      Const<Integer, String> result = c.bimap(String::length, i -> "Value: " + (i + 1));
      assertThat(result.value()).isEqualTo(5);
    }

    @Test
    @DisplayName(
        "ConstBifunctor.second should not NPE when mapper cannot handle null (audit issue #1)")
    void constBifunctorSecondShouldNotNpe() {
      var const_ = CONST.widen2(new Const<String, Integer>("hello"));
      // mapper that dereferences its input — would NPE if called with null
      var result = ConstBifunctor.INSTANCE.second(Object::toString, const_);
      Const<String, String> narrowed = CONST.narrow2(result);
      assertThat(narrowed.value()).isEqualTo("hello");
    }

    @Test
    @DisplayName("Const.mapSecond should still validate non-null mapper parameter (audit issue #1)")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void mapSecondShouldStillRejectNullMapper() {
      Const<String, Integer> c = new Const<>("hello");
      assertThatNullPointerException().isThrownBy(() -> c.mapSecond(null));
    }

    @Test
    @DisplayName("Const.bimap should still validate non-null mapper parameters (audit issue #1)")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void bimapShouldStillRejectNullMappers() {
      Const<String, Integer> c = new Const<>("hello");
      assertThatNullPointerException().isThrownBy(() -> c.bimap(null, i -> i));
      assertThatNullPointerException().isThrownBy(() -> c.bimap(s -> s, null));
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
          bifunctor.second(_ -> "ignored", afterFirst);
      Const<Integer, String> afterSecondNarrowed = CONST.narrow2(afterSecond);
      assertThat(afterSecondNarrowed.value()).isEqualTo(5); // Still 5, not changed
    }
  }
}
