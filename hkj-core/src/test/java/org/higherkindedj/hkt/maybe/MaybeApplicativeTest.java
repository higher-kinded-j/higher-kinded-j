// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("MaybeApplicative")
class MaybeApplicativeTest extends MaybeTestBase {

  private MonadError<MaybeKind.Witness, Unit> applicative;
  private Applicative<MaybeKind.Witness> applicativeTyped;

  @BeforeEach
  void setUpApplicative() {
    applicative = Instances.monadError(maybe());
    applicativeTyped = applicative;
    validateApplicativeFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("fixtures")
    void identity(String label, Kind<MaybeKind.Witness, Integer> v) {
      ApplicativeLaws.assertIdentity(applicativeTyped, v, equalityChecker);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(applicativeTyped, value, validMapper, equalityChecker);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(
          applicativeTyped, validFunctionKind, value, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("fixtures")
    void composition(String label, Kind<MaybeKind.Witness, Integer> w) {
      Kind<MaybeKind.Witness, Function<String, String>> u =
          MAYBE.widen(Maybe.just(s -> "u(" + s + ")"));
      Kind<MaybeKind.Witness, Function<Integer, String>> v = MAYBE.widen(Maybe.just(i -> "v" + i));
      ApplicativeLaws.assertComposition(applicativeTyped, u, v, w, equalityChecker);
    }

    static Stream<Arguments> fixtures() {
      return Stream.of(
          Arguments.of("Just(0)", MAYBE.widen(Maybe.just(0))),
          Arguments.of("Just(42)", MAYBE.widen(Maybe.just(42))),
          Arguments.of("Just(-1)", MAYBE.widen(Maybe.just(-1))),
          Arguments.of("Nothing", MAYBE.<Integer>widen(Maybe.nothing())));
    }

    static Stream<Arguments> values() {
      return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
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

      Kind<MaybeKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      Maybe<String> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("value:" + DEFAULT_JUST_VALUE);
    }

    @Test
    @DisplayName("ap() returns Nothing if function is Nothing")
    void apReturnsNothingIfFunctionIsNothing() {
      Kind<MaybeKind.Witness, Function<Integer, String>> funcKind = nothingKind();
      Kind<MaybeKind.Witness, Integer> valueKind = applicative.of(DEFAULT_JUST_VALUE);

      Kind<MaybeKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      Maybe<String> maybe = narrowToMaybe(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("ap() returns Nothing if value is Nothing")
    void apReturnsNothingIfValueIsNothing() {
      Kind<MaybeKind.Witness, Function<Integer, String>> funcKind =
          applicative.of(i -> "value:" + i);
      Kind<MaybeKind.Witness, Integer> valueKind = nothingKind();

      Kind<MaybeKind.Witness, String> result = applicative.ap(funcKind, valueKind);

      Maybe<String> maybe = narrowToMaybe(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("map2() combines two Just values")
    void map2CombinesTwoJustValues() {
      Kind<MaybeKind.Witness, Integer> r1 = applicative.of(10);
      Kind<MaybeKind.Witness, String> r2 = applicative.of("test");

      BiFunction<Integer, String, String> combiner = (i, s) -> s + ":" + i;
      Kind<MaybeKind.Witness, String> result = applicative.map2(r1, r2, combiner);

      Maybe<String> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("test:10");
    }

    @Test
    @DisplayName("map2() returns Nothing if either value is Nothing")
    void map2ReturnsNothingIfEitherIsNothing() {
      Kind<MaybeKind.Witness, Integer> just = applicative.of(10);
      Kind<MaybeKind.Witness, Integer> nothing = nothingKind();

      BiFunction<Integer, Integer, String> combiner = (a, b) -> a + ":" + b;

      Maybe<String> result1 = narrowToMaybe(applicative.map2(nothing, just, combiner));
      assertThat(result1.isNothing()).isTrue();

      Maybe<String> result2 = narrowToMaybe(applicative.map2(just, nothing, combiner));
      assertThat(result2.isNothing()).isTrue();
    }

    @Test
    @DisplayName("map3() combines three Just values")
    void map3CombinesThreeJustValues() {
      Kind<MaybeKind.Witness, Integer> r1 = applicative.of(1);
      Kind<MaybeKind.Witness, String> r2 = applicative.of("test");
      Kind<MaybeKind.Witness, Double> r3 = applicative.of(3.14);

      Function3<Integer, String, Double, String> combiner =
          (i, s, d) -> String.format("%s:%d:%.2f", s, i, d);

      Kind<MaybeKind.Witness, String> result = applicative.map3(r1, r2, r3, combiner);

      Maybe<String> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("test:1:3.14");
    }

    @Test
    @DisplayName("map4() combines four Just values")
    void map4CombinesFourJustValues() {
      Kind<MaybeKind.Witness, Integer> r1 = applicative.of(1);
      Kind<MaybeKind.Witness, String> r2 = applicative.of("test");
      Kind<MaybeKind.Witness, Double> r3 = applicative.of(3.14);
      Kind<MaybeKind.Witness, Boolean> r4 = applicative.of(true);

      Function4<Integer, String, Double, Boolean, String> combiner =
          (i, s, d, b) -> String.format("%s:%d:%.2f:%b", s, i, d, b);

      Kind<MaybeKind.Witness, String> result = applicative.map4(r1, r2, r3, r4, combiner);

      Maybe<String> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("test:1:3.14:true");
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

      Function3<Integer, String, Double, String> combiner = (i, s, d) -> "result";

      Kind<MaybeKind.Witness, String> result = applicative.map3(n1, n2, n3, combiner);

      Maybe<String> maybe = narrowToMaybe(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("Complex nested applicative operations")
    void complexNestedApplicativeOperations() {
      Kind<MaybeKind.Witness, Function<Integer, Function<String, String>>> nestedFunc =
          applicative.of(i -> s -> s + ":" + i);
      Kind<MaybeKind.Witness, Integer> intKind = applicative.of(DEFAULT_JUST_VALUE);
      Kind<MaybeKind.Witness, String> stringKind = applicative.of("test");

      Kind<MaybeKind.Witness, Function<String, String>> partialFunc =
          applicative.ap(nestedFunc, intKind);
      Kind<MaybeKind.Witness, String> result = applicative.ap(partialFunc, stringKind);

      Maybe<String> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("test:" + DEFAULT_JUST_VALUE);
    }

    @Test
    @DisplayName("of() with null creates Nothing")
    void ofWithNullCreatesNothing() {
      Kind<MaybeKind.Witness, String> result = applicative.of(null);

      Maybe<String> maybe = narrowToMaybe(result);
      assertThat(maybe.isNothing()).isTrue();
    }
  }
}
