// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.constant;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test suite for {@link ConstApplicative}.
 *
 * <p>Verifies the Applicative operations and laws. {@code Const<C, A>} is a phantom applicative:
 * the phantom {@code A} is ignored and {@code map}/{@code ap}/{@code map2} combine the held
 * monoidal value {@code C} rather than applying their functions. Because the functions are never
 * invoked, map/ap can never throw a function exception, so the contract omits {@link
 * Category#EXCEPTIONS}. The laws are driven by the shipped {@link ApplicativeLaws} over {@link
 * ConstLawFixtures}.
 */
@DisplayName("ConstApplicative Tests")
class ConstApplicativeTest extends ConstTestBase {

  private ConstApplicative<Integer> applicative;
  private Applicative<ConstKind.Witness<Integer>> applicativeTyped;

  @BeforeEach
  void setUpApplicative() {
    applicative = new ConstApplicative<>(sumMonoid);
    applicativeTyped = applicative;
    validateApplicativeFixtures();
  }

  /**
   * {@code Const} never applies its functions (the phantom {@code A} is ignored), so a thrown
   * mapper cannot surface — {@link Category#EXCEPTIONS} is omitted. The Applicative laws are
   * verified in the {@code Laws} block below.
   */
  @Test
  @DisplayName("Applicative contract — operations & validations")
  void applicativeContract() {
    TypeClassContract.<ConstKind.Witness<Integer>>applicative(ConstApplicative.class)
        .<String>instance(applicativeTyped)
        .<Integer>withKind(validKind)
        .withOperations(validKind2, validMapper, validFunctionKind, validCombiningFunction)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
  }

  @Nested
  @DisplayName("Applicative Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.constant.ConstLawFixtures#kinds")
    void identity(String label, Kind<ConstKind.Witness<Integer>, Integer> v) {
      ApplicativeLaws.assertIdentity(applicativeTyped, v, ConstLawFixtures.EQ);
    }

    @ParameterizedTest(name = "homomorphism holds on value \"{0}\"")
    @MethodSource("org.higherkindedj.hkt.constant.ConstLawFixtures#values")
    void homomorphism(String value) {
      ApplicativeLaws.assertHomomorphism(applicativeTyped, value, validMapper, ConstLawFixtures.EQ);
    }

    @ParameterizedTest(name = "interchange holds on value \"{0}\"")
    @MethodSource("org.higherkindedj.hkt.constant.ConstLawFixtures#values")
    void interchange(String value) {
      ApplicativeLaws.assertInterchange(
          applicativeTyped, validFunctionKind, value, ConstLawFixtures.EQ);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.constant.ConstLawFixtures#kinds")
    void composition(String label, Kind<ConstKind.Witness<Integer>, Integer> w) {
      // Functions are phantom in Const, so u/v simply carry accumulated values.
      Kind<ConstKind.Witness<Integer>, Function<String, Integer>> u = CONST.widen(new Const<>(7));
      Kind<ConstKind.Witness<Integer>, Function<Integer, String>> v = CONST.widen(new Const<>(3));
      ApplicativeLaws.assertComposition(applicativeTyped, u, v, w, ConstLawFixtures.EQ);
    }
  }

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("Constructor accepts valid monoid")
    void constructorAcceptsValidMonoid() {
      ConstApplicative<Integer> app = new ConstApplicative<>(sumMonoid);

      assertThat(app).isNotNull();
    }

    @Test
    @DisplayName("Constructor rejects null monoid")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void constructorRejectsNullMonoid() {
      assertThatNullPointerException()
          .isThrownBy(() -> new ConstApplicative<Integer>(null))
          .withMessage("Monoid cannot be null");
    }

    @Test
    @DisplayName("Constructor works with different monoid types")
    void constructorWorksWithDifferentMonoidTypes() {
      ConstApplicative<String> app = new ConstApplicative<>(stringMonoid);

      assertThat(app).isNotNull();
    }
  }

  @Nested
  @DisplayName("of() Tests")
  class OfTests {

    @Test
    @DisplayName("of() creates Const with monoid empty")
    void ofCreatesConstWithMonoidEmpty() {
      Kind<ConstKind.Witness<Integer>, String> result = applicative.of("ignored");

      Const<Integer, String> const_ = CONST.narrow(result);
      assertThat(const_.value()).isEqualTo(0); // sumMonoid empty is 0
    }

    @Test
    @DisplayName("of() ignores the input value")
    void ofIgnoresInputValue() {
      Kind<ConstKind.Witness<Integer>, String> result1 = applicative.of("value1");
      Kind<ConstKind.Witness<Integer>, String> result2 = applicative.of("value2");

      assertThat(CONST.narrow(result1).value()).isEqualTo(CONST.narrow(result2).value());
    }

    @Test
    @DisplayName("of() accepts null input")
    void ofAcceptsNullInput() {
      Kind<ConstKind.Witness<Integer>, String> result = applicative.of(null);

      Const<Integer, String> const_ = CONST.narrow(result);
      assertThat(const_.value()).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("map() Tests")
  class MapTests {

    @Test
    @DisplayName("map() preserves accumulated value")
    void mapPreservesAccumulatedValue() {
      Kind<ConstKind.Witness<Integer>, String> input = CONST.widen(new Const<>(42));
      Function<String, Integer> mapper = String::length;

      Kind<ConstKind.Witness<Integer>, Integer> result = applicative.map(mapper, input);

      Const<Integer, Integer> const_ = CONST.narrow(result);
      assertThat(const_.value()).isEqualTo(42);
    }

    @Test
    @DisplayName("map() changes phantom type parameter")
    void mapChangesPhantomTypeParameter() {
      Kind<ConstKind.Witness<Integer>, String> input = CONST.widen(new Const<>(100));
      Function<String, Boolean> mapper = _ -> true;

      Kind<ConstKind.Witness<Integer>, Boolean> result = applicative.map(mapper, input);

      Const<Integer, Boolean> const_ = CONST.narrow(result);
      assertThat(const_.value()).isEqualTo(100);
    }

    @Test
    @DisplayName("map() never applies the function")
    void mapNeverAppliesFunction() {
      Kind<ConstKind.Witness<Integer>, String> input = CONST.widen(new Const<>(7));
      // This function throws, but should never be called
      Function<String, Integer> throwingMapper =
          _ -> {
            throw new RuntimeException("Function should not be called");
          };

      assertThatCode(() -> applicative.map(throwingMapper, input)).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("ap() Tests")
  class ApTests {

    @Test
    @DisplayName("ap() combines accumulated values using monoid")
    void apCombinesAccumulatedValues() {
      Kind<ConstKind.Witness<Integer>, Function<String, Integer>> ff = CONST.widen(new Const<>(5));
      Kind<ConstKind.Witness<Integer>, String> fa = CONST.widen(new Const<>(10));

      Kind<ConstKind.Witness<Integer>, Integer> result = applicative.ap(ff, fa);

      Const<Integer, Integer> const_ = CONST.narrow(result);
      assertThat(const_.value()).isEqualTo(15); // 5 + 10
    }

    @Test
    @DisplayName("ap() never applies the phantom function")
    void apNeverAppliesPhantomFunction() {
      Kind<ConstKind.Witness<Integer>, Function<String, Integer>> ff = CONST.widen(new Const<>(3));
      Kind<ConstKind.Witness<Integer>, String> fa = CONST.widen(new Const<>(7));

      assertThatCode(() -> applicative.ap(ff, fa)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ap() works with zero values")
    void apWorksWithZeroValues() {
      Kind<ConstKind.Witness<Integer>, Function<String, Integer>> ff = CONST.widen(new Const<>(0));
      Kind<ConstKind.Witness<Integer>, String> fa = CONST.widen(new Const<>(0));

      Kind<ConstKind.Witness<Integer>, Integer> result = applicative.ap(ff, fa);

      Const<Integer, Integer> const_ = CONST.narrow(result);
      assertThat(const_.value()).isEqualTo(0);
    }

    @Test
    @DisplayName("ap() combines multiple times correctly")
    void apCombinesMultipleTimes() {
      Kind<ConstKind.Witness<Integer>, Function<String, Integer>> ff1 = CONST.widen(new Const<>(1));
      Kind<ConstKind.Witness<Integer>, String> fa1 = CONST.widen(new Const<>(2));

      Kind<ConstKind.Witness<Integer>, Integer> intermediate = applicative.ap(ff1, fa1);

      Kind<ConstKind.Witness<Integer>, Function<Integer, String>> ff2 = CONST.widen(new Const<>(3));

      // This demonstrates nested ap
      Kind<ConstKind.Witness<Integer>, String> final_ =
          applicative.ap(ff2, CONST.widen(new Const<>(4)));

      assertThat(CONST.narrow(intermediate).value()).isEqualTo(3); // 1 + 2
      assertThat(CONST.narrow(final_).value()).isEqualTo(7); // 3 + 4
    }
  }

  @Nested
  @DisplayName("map2() Tests")
  class Map2Tests {

    @Test
    @DisplayName("map2() combines two accumulated values")
    void map2CombinesTwoAccumulatedValues() {
      Kind<ConstKind.Witness<Integer>, String> fa = CONST.widen(new Const<>(10));
      Kind<ConstKind.Witness<Integer>, Boolean> fb = CONST.widen(new Const<>(20));
      BiFunction<String, Boolean, Integer> combiner = (_, _) -> 999; // Never called

      Kind<ConstKind.Witness<Integer>, Integer> result = applicative.map2(fa, fb, combiner);

      Const<Integer, Integer> const_ = CONST.narrow(result);
      assertThat(const_.value()).isEqualTo(30); // 10 + 20
    }

    @Test
    @DisplayName("map2() never applies the combining function")
    void map2NeverAppliesCombiningFunction() {
      Kind<ConstKind.Witness<Integer>, String> fa = CONST.widen(new Const<>(5));
      Kind<ConstKind.Witness<Integer>, Integer> fb = CONST.widen(new Const<>(15));
      BiFunction<String, Integer, Boolean> throwingCombiner =
          (_, _) -> {
            throw new RuntimeException("Function should not be called");
          };

      assertThatCode(() -> applicative.map2(fa, fb, throwingCombiner)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("map2() works with monoid identity")
    void map2WorksWithMonoidIdentity() {
      Kind<ConstKind.Witness<Integer>, String> fa = CONST.widen(new Const<>(0)); // identity
      Kind<ConstKind.Witness<Integer>, Integer> fb = CONST.widen(new Const<>(42));
      BiFunction<String, Integer, String> combiner = (_, _) -> "";

      Kind<ConstKind.Witness<Integer>, String> result = applicative.map2(fa, fb, combiner);

      Const<Integer, String> const_ = CONST.narrow(result);
      assertThat(const_.value()).isEqualTo(42); // 0 + 42 = 42
    }
  }

  @Nested
  @DisplayName("map3() and map4() Tests")
  class MapNTests {

    @Test
    @DisplayName("map3() combines three accumulated values")
    void map3CombinesThreeAccumulatedValues() {
      Kind<ConstKind.Witness<Integer>, String> r1 = CONST.widen(new Const<>(1));
      Kind<ConstKind.Witness<Integer>, Integer> r2 = CONST.widen(new Const<>(2));
      Kind<ConstKind.Witness<Integer>, Boolean> r3 = CONST.widen(new Const<>(3));

      Function3<String, Integer, Boolean, Double> combiner = (_, _, _) -> 0.0;

      Kind<ConstKind.Witness<Integer>, Double> result = applicative.map3(r1, r2, r3, combiner);

      Const<Integer, Double> const_ = CONST.narrow(result);
      assertThat(const_.value()).isEqualTo(6); // 1 + 2 + 3
    }

    @Test
    @DisplayName("map4() combines four accumulated values")
    void map4CombinesFourAccumulatedValues() {
      Kind<ConstKind.Witness<Integer>, String> r1 = CONST.widen(new Const<>(10));
      Kind<ConstKind.Witness<Integer>, Integer> r2 = CONST.widen(new Const<>(20));
      Kind<ConstKind.Witness<Integer>, Boolean> r3 = CONST.widen(new Const<>(30));
      Kind<ConstKind.Witness<Integer>, Double> r4 = CONST.widen(new Const<>(40));

      Function4<String, Integer, Boolean, Double, String> combiner = (_, _, _, _) -> "";

      Kind<ConstKind.Witness<Integer>, String> result = applicative.map4(r1, r2, r3, r4, combiner);

      Const<Integer, String> const_ = CONST.narrow(result);
      assertThat(const_.value()).isEqualTo(100); // 10 + 20 + 30 + 40
    }
  }

  @Nested
  @DisplayName("Edge Cases and Real-World Scenarios")
  class EdgeCasesAndRealWorldScenarios {

    @Test
    @DisplayName("Monoid combination is associative")
    void monoidCombinationIsAssociative() {
      Kind<ConstKind.Witness<Integer>, String> k1 = CONST.widen(new Const<>(1));
      Kind<ConstKind.Witness<Integer>, String> k2 = CONST.widen(new Const<>(2));
      Kind<ConstKind.Witness<Integer>, String> k3 = CONST.widen(new Const<>(3));

      BiFunction<String, String, String> combiner = (_, _) -> "";

      // ((k1 + k2) + k3)
      Kind<ConstKind.Witness<Integer>, String> left =
          applicative.map2(applicative.map2(k1, k2, combiner), k3, combiner);

      // (k1 + (k2 + k3))
      Kind<ConstKind.Witness<Integer>, String> right =
          applicative.map2(k1, applicative.map2(k2, k3, combiner), combiner);

      assertThat(CONST.narrow(left).value()).isEqualTo(CONST.narrow(right).value()).isEqualTo(6);
    }

    @Test
    @DisplayName("Use case: Sum aggregation with String concatenation monoid")
    void useCaseSumAggregation() {
      ConstApplicative<String> stringApp = new ConstApplicative<>(stringMonoid);

      Kind<ConstKind.Witness<String>, Integer> k1 = CONST.widen(new Const<>("Hello"));
      Kind<ConstKind.Witness<String>, Integer> k2 = CONST.widen(new Const<>(" "));
      Kind<ConstKind.Witness<String>, Integer> k3 = CONST.widen(new Const<>("World"));

      BiFunction<Integer, Integer, Integer> combiner = (_, _) -> 0;

      Kind<ConstKind.Witness<String>, Integer> result =
          stringApp.map2(stringApp.map2(k1, k2, combiner), k3, combiner);

      assertThat(CONST.narrow(result).value()).isEqualTo("Hello World");
    }

    @Test
    @DisplayName("Use case: Product accumulation")
    void useCaseProductAccumulation() {
      ConstApplicative<Integer> productApp = new ConstApplicative<>(productMonoid);

      Kind<ConstKind.Witness<Integer>, String> k1 = CONST.widen(new Const<>(2));
      Kind<ConstKind.Witness<Integer>, String> k2 = CONST.widen(new Const<>(3));
      Kind<ConstKind.Witness<Integer>, String> k3 = CONST.widen(new Const<>(4));

      BiFunction<String, String, String> combiner = (_, _) -> "";

      Kind<ConstKind.Witness<Integer>, String> result =
          productApp.map2(productApp.map2(k1, k2, combiner), k3, combiner);

      assertThat(CONST.narrow(result).value()).isEqualTo(24); // 2 * 3 * 4
    }

    @Test
    @DisplayName("Identity law: of followed by ap equals map")
    void identityLawOfApEqualsMap() {
      Kind<ConstKind.Witness<Integer>, String> value = CONST.widen(new Const<>(42));
      Function<String, Integer> func = String::length;

      // Left side: of(func) `ap` value
      Kind<ConstKind.Witness<Integer>, Function<String, Integer>> funcKind = applicative.of(func);
      Kind<ConstKind.Witness<Integer>, Integer> left = applicative.ap(funcKind, value);

      // Right side: map(func, value)
      Kind<ConstKind.Witness<Integer>, Integer> right = applicative.map(func, value);

      assertThat(CONST.narrow(left).value()).isEqualTo(CONST.narrow(right).value());
    }
  }
}
