// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.IdAssert.assertThatId;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for {@code IdMonad}, the identity Functor/Applicative/Monad.
 *
 * <p>{@code IdMonad} is the single class realising Functor, Applicative and Monad for {@code Id},
 * so — unlike the types with separate functor/applicative classes — all three algebras' laws and
 * the one contract live here rather than in three near-identical files.
 */
@DisplayName("IdMonad")
class IdMonadTest extends IdTestBase {

  private Monad<IdKind.Witness> monad;

  @BeforeEach
  void setUpMonad() {
    monad = Instances.monad(id());
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLawsBlock {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.id.IdLawFixtures#kinds")
    void identity(String label, Kind<IdKind.Witness, Integer> fa) {
      FunctorLaws.assertIdentity(monad, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.id.IdLawFixtures#kinds")
    void composition(String label, Kind<IdKind.Witness, Integer> fa) {
      FunctorLaws.assertComposition(monad, fa, validMapper, secondMapper, equalityChecker);
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLawsBlock {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.id.IdLawFixtures#kinds")
    void identity(String label, Kind<IdKind.Witness, Integer> v) {
      ApplicativeLaws.assertIdentity(monad, v, equalityChecker);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.id.IdLawFixtures#values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(monad, value, validMapper, equalityChecker);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.id.IdLawFixtures#values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(monad, validFunctionKind, value, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.id.IdLawFixtures#kinds")
    void composition(String label, Kind<IdKind.Witness, Integer> w) {
      Kind<IdKind.Witness, Function<String, String>> u = ID.widen(Id.of(s -> "u(" + s + ")"));
      Kind<IdKind.Witness, Function<Integer, String>> v = ID.widen(Id.of(i -> "v" + i));
      ApplicativeLaws.assertComposition(monad, u, v, w, equalityChecker);
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawsBlock {

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.id.IdLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(monad, value, testFunction, equalityChecker);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.id.IdLawFixtures#kinds")
    void rightIdentity(String label, Kind<IdKind.Witness, Integer> ma) {
      MonadLaws.assertRightIdentity(monad, ma, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.id.IdLawFixtures#kinds")
    void associativity(String label, Kind<IdKind.Witness, Integer> ma) {
      MonadLaws.assertAssociativity(monad, ma, testFunction, chainFunction, equalityChecker);
    }
  }

  @Test
  @DisplayName("Monad contract — operations, validations & exceptions (laws verified above)")
  void monadContract() {
    TypeClassContract.<IdKind.Witness>monad(IdMonad.class)
        .<Integer>instance(monad)
        .<String>withKind(validKind)
        .withMonadOperations(
            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("of() wraps a value in Id")
    void ofWrapsValueInId() {
      assertThatId(monad.of(DEFAULT_VALUE)).hasValue(DEFAULT_VALUE);
    }

    @Test
    @DisplayName("of() wraps a null value in Id")
    void ofWrapsNullValue() {
      assertThatId(monad.of(null)).hasNullValue();
    }

    @Test
    @DisplayName("map() applies the function to the value")
    void mapAppliesFunctionToValue() {
      assertThatId(monad.map(Object::toString, idOf(5))).hasValue("5");
    }

    @Test
    @DisplayName("ap() applies a wrapped function to a wrapped value")
    void apAppliesFunctionToValue() {
      Kind<IdKind.Witness, Function<Integer, String>> funcKind = monad.of(x -> "N" + x);
      assertThatId(monad.ap(funcKind, monad.of(10))).hasValue("N10");
    }

    @Test
    @DisplayName("flatMap() applies the function and unwraps the result")
    void flatMapAppliesFunctionAndUnwraps() {
      Function<Integer, Kind<IdKind.Witness, String>> func = i -> idOf("value:" + i);
      assertThatId(monad.flatMap(func, idOf(5))).hasValue("value:5");
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Id can hold a null value through map and flatMap")
    @SuppressWarnings({"DataFlowIssue", "ConstantValue"}) // Id may legitimately hold a null value
    void handlesNullValue() {
      Kind<IdKind.Witness, Integer> nullKind = idOf(null);

      Function<Integer, String> nullSafeMapper = i -> i == null ? "null" : i.toString();
      assertThatId(monad.map(nullSafeMapper, nullKind)).hasValue("null");

      Function<Integer, Kind<IdKind.Witness, String>> nullSafeFlatMapper =
          i -> monad.of(i == null ? "null" : i.toString());
      assertThatId(monad.flatMap(nullSafeFlatMapper, nullKind)).hasValue("null");
    }

    @Test
    @DisplayName("map() with the identity function preserves the value")
    void mapWithIdentityFunction() {
      Kind<IdKind.Witness, Integer> mapped = monad.map(i -> i, validKind);
      assertThat(extractValue(mapped)).isEqualTo(extractValue(validKind));
    }

    @Test
    @DisplayName("Chained flatMap operations compose")
    void chainedOperationsCompose() {
      Function<Integer, Kind<IdKind.Witness, Integer>> step1 = x -> idOf(x * 2);
      Function<Integer, Kind<IdKind.Witness, String>> step2 = y -> idOf("N" + y);
      Kind<IdKind.Witness, String> result = monad.flatMap(step2, monad.flatMap(step1, idOf(5)));
      assertThatId(result).hasValue("N10");
    }

    @Test
    @DisplayName("flatMap() propagates an exception thrown by the function")
    void flatMapPropagatesException() {
      RuntimeException boom = new RuntimeException("Test exception");
      Function<Integer, Kind<IdKind.Witness, String>> throwing =
          _ -> {
            throw boom;
          };
      assertThatThrownBy(() -> monad.flatMap(throwing, validKind)).isSameAs(boom);
    }

    @Test
    @DisplayName("ap() propagates an exception thrown by the function")
    void apPropagatesException() {
      RuntimeException boom = new RuntimeException("Test exception");
      Function<Integer, String> throwing =
          _ -> {
            throw boom;
          };
      Kind<IdKind.Witness, Function<Integer, String>> throwingKind = idOf(throwing);
      assertThatThrownBy(() -> monad.ap(throwingKind, validKind)).isSameAs(boom);
    }
  }
}
