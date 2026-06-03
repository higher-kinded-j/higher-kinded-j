// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("MaybeApplicative")
class MaybeApplicativeTest extends MaybeTestBase {

  private Applicative<MaybeKind.Witness> applicative;

  @BeforeEach
  void setUpApplicative() {
    applicative = Instances.monadError(maybe());
    validateApplicativeFixtures();
  }

  // No separate Applicative contract smoke: this instance is the Maybe MonadError, so its
  // map/ap/map2 null-argument validation and exception propagation are already covered by the
  // contract in MaybeMonadTest. A dedicated Applicative contract would only duplicate it.

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.maybe.MaybeLawFixtures#kinds")
    void identity(String label, Kind<MaybeKind.Witness, Integer> v) {
      ApplicativeLaws.assertIdentity(applicative, v, equalityChecker);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.maybe.MaybeLawFixtures#values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(applicative, value, validMapper, equalityChecker);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.maybe.MaybeLawFixtures#values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(applicative, validFunctionKind, value, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.maybe.MaybeLawFixtures#kinds")
    void composition(String label, Kind<MaybeKind.Witness, Integer> w) {
      Kind<MaybeKind.Witness, Function<String, String>> u =
          MAYBE.widen(Maybe.just(s -> "u(" + s + ")"));
      Kind<MaybeKind.Witness, Function<Integer, String>> v = MAYBE.widen(Maybe.just(i -> "v" + i));
      ApplicativeLaws.assertComposition(applicative, u, v, w, equalityChecker);
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("ap() applies function to value - both Just")
    void apAppliesFunctionToValue() {
      Kind<MaybeKind.Witness, Function<Integer, String>> funcKind =
          applicative.of(i -> "value:" + i);
      Kind<MaybeKind.Witness, Integer> valueKind = applicative.of(DEFAULT_JUST_VALUE);

      var result = applicative.ap(funcKind, valueKind);

      assertThatMaybe(result).isJust().hasValue("value:" + DEFAULT_JUST_VALUE);
    }

    @Test
    @DisplayName("ap() returns Nothing if function is Nothing")
    void apReturnsNothingIfFunctionIsNothing() {
      Kind<MaybeKind.Witness, Function<Integer, String>> funcKind = nothingKind();
      var result = applicative.ap(funcKind, applicative.of(DEFAULT_JUST_VALUE));
      assertThatMaybe(result).isNothing();
    }

    @Test
    @DisplayName("ap() returns Nothing if value is Nothing")
    void apReturnsNothingIfValueIsNothing() {
      Kind<MaybeKind.Witness, Function<Integer, String>> funcKind =
          applicative.of(i -> "value:" + i);
      Kind<MaybeKind.Witness, Integer> valueKind = nothingKind();
      var result = applicative.ap(funcKind, valueKind);
      assertThatMaybe(result).isNothing();
    }

    @Test
    @DisplayName("map2() combines two Just values")
    void map2CombinesTwoJustValues() {
      var r1 = applicative.of(10);
      var r2 = applicative.of("test");
      var result = applicative.map2(r1, r2, (i, s) -> s + ":" + i);
      assertThatMaybe(result).isJust().hasValue("test:10");
    }

    @Test
    @DisplayName("map2() returns Nothing if either value is Nothing")
    void map2ReturnsNothingIfEitherIsNothing() {
      var just = applicative.of(10);
      Kind<MaybeKind.Witness, Integer> nothing = nothingKind();
      assertThatMaybe(applicative.map2(nothing, just, (a, b) -> a + ":" + b)).isNothing();
      assertThatMaybe(applicative.map2(just, nothing, (a, b) -> a + ":" + b)).isNothing();
    }

    @Test
    @DisplayName("map3() combines three Just values")
    void map3CombinesThreeJustValues() {
      var r1 = applicative.of(1);
      var r2 = applicative.of("test");
      var r3 = applicative.of(3.14);
      Function3<Integer, String, Double, String> combiner =
          (i, s, d) -> String.format("%s:%d:%.2f", s, i, d);
      assertThatMaybe(applicative.map3(r1, r2, r3, combiner)).isJust().hasValue("test:1:3.14");
    }

    @Test
    @DisplayName("map4() combines four Just values")
    void map4CombinesFourJustValues() {
      var r1 = applicative.of(1);
      var r2 = applicative.of("test");
      var r3 = applicative.of(3.14);
      var r4 = applicative.of(true);
      Function4<Integer, String, Double, Boolean, String> combiner =
          (i, s, d, b) -> String.format("%s:%d:%.2f:%b", s, i, d, b);
      assertThatMaybe(applicative.map4(r1, r2, r3, r4, combiner))
          .isJust()
          .hasValue("test:1:3.14:true");
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("mapN operations short-circuit on first Nothing")
    void mapNShortCircuitsOnFirstNothing() {
      Kind<MaybeKind.Witness, Integer> n1 = nothingKind();
      Kind<MaybeKind.Witness, String> n2 = nothingKind();
      Kind<MaybeKind.Witness, Double> n3 = nothingKind();
      Function3<Integer, String, Double, String> combiner = (_, _, _) -> "result";
      assertThatMaybe(applicative.map3(n1, n2, n3, combiner)).isNothing();
    }

    @Test
    @DisplayName("Complex nested applicative operations")
    void complexNestedApplicativeOperations() {
      Kind<MaybeKind.Witness, Function<Integer, Function<String, String>>> nestedFunc =
          applicative.of(i -> s -> s + ":" + i);
      var partialFunc = applicative.ap(nestedFunc, applicative.of(DEFAULT_JUST_VALUE));
      var result = applicative.ap(partialFunc, applicative.of("test"));
      assertThatMaybe(result).isJust().hasValue("test:" + DEFAULT_JUST_VALUE);
    }

    @Test
    @DisplayName("of() with null creates Nothing")
    void ofWithNullCreatesNothing() {
      assertThatMaybe(applicative.of(null)).isNothing();
    }
  }
}
