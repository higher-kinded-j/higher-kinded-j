// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.assertions.OptionalKindAssert.assertThatOptionalKind;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
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

@DisplayName("OptionalApplicative")
class OptionalApplicativeTest extends OptionalTestBase {

  private Applicative<OptionalKind.Witness> applicative;

  @BeforeEach
  void setUpApplicative() {
    applicative = Instances.monadError(optional());
    validateApplicativeFixtures();
  }

  // No separate Applicative contract smoke: this instance is the Optional MonadError, so its
  // map/ap/map2 null-argument validation and exception propagation are already covered by the
  // contract in OptionalMonadTest. A dedicated Applicative contract would only duplicate it.

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.optional.OptionalLawFixtures#kinds")
    void identity(String label, Kind<OptionalKind.Witness, Integer> v) {
      ApplicativeLaws.assertIdentity(applicative, v, equalityChecker);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.optional.OptionalLawFixtures#values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(applicative, value, validMapper, equalityChecker);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.optional.OptionalLawFixtures#values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(applicative, validFunctionKind, value, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.optional.OptionalLawFixtures#kinds")
    void composition(String label, Kind<OptionalKind.Witness, Integer> w) {
      Kind<OptionalKind.Witness, Function<String, String>> u =
          OPTIONAL.widen(Optional.of(s -> "u(" + s + ")"));
      Kind<OptionalKind.Witness, Function<Integer, String>> v =
          OPTIONAL.widen(Optional.of(i -> "v" + i));
      ApplicativeLaws.assertComposition(applicative, u, v, w, equalityChecker);
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("ap() applies function to value - both present")
    void apAppliesFunctionToValue() {
      Kind<OptionalKind.Witness, Function<Integer, String>> funcKind =
          applicative.of(i -> "value:" + i);
      Kind<OptionalKind.Witness, Integer> valueKind = applicative.of(DEFAULT_PRESENT_VALUE);

      var result = applicative.ap(funcKind, valueKind);

      assertThatOptionalKind(result).isPresent().contains("value:" + DEFAULT_PRESENT_VALUE);
    }

    @Test
    @DisplayName("ap() returns empty if function is empty")
    void apReturnsEmptyIfFunctionIsEmpty() {
      Kind<OptionalKind.Witness, Function<Integer, String>> funcKind = emptyOptional();
      var result = applicative.ap(funcKind, applicative.of(DEFAULT_PRESENT_VALUE));
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("ap() returns empty if value is empty")
    void apReturnsEmptyIfValueIsEmpty() {
      Kind<OptionalKind.Witness, Function<Integer, String>> funcKind =
          applicative.of(i -> "value:" + i);
      Kind<OptionalKind.Witness, Integer> valueKind = emptyOptional();
      var result = applicative.ap(funcKind, valueKind);
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("map2() combines two present values")
    void map2CombinesTwoPresentValues() {
      var r1 = applicative.of(10);
      var r2 = applicative.of("test");
      var result = applicative.map2(r1, r2, (i, s) -> s + ":" + i);
      assertThatOptionalKind(result).isPresent().contains("test:10");
    }

    @Test
    @DisplayName("map2() returns empty if either value is empty")
    void map2ReturnsEmptyIfEitherIsEmpty() {
      var present = applicative.of(10);
      Kind<OptionalKind.Witness, Integer> empty = emptyOptional();
      assertThatOptionalKind(applicative.map2(empty, present, (a, b) -> a + ":" + b)).isEmpty();
      assertThatOptionalKind(applicative.map2(present, empty, (a, b) -> a + ":" + b)).isEmpty();
    }

    @Test
    @DisplayName("map3() combines three present values")
    void map3CombinesThreePresentValues() {
      var r1 = applicative.of(1);
      var r2 = applicative.of("test");
      var r3 = applicative.of(3.14);
      Function3<Integer, String, Double, String> combiner =
          (i, s, d) -> String.format("%s:%d:%.2f", s, i, d);
      assertThatOptionalKind(applicative.map3(r1, r2, r3, combiner))
          .isPresent()
          .contains("test:1:3.14");
    }

    @Test
    @DisplayName("map4() combines four present values")
    void map4CombinesFourPresentValues() {
      var r1 = applicative.of(1);
      var r2 = applicative.of("test");
      var r3 = applicative.of(3.14);
      var r4 = applicative.of(true);
      Function4<Integer, String, Double, Boolean, String> combiner =
          (i, s, d, b) -> String.format("%s:%d:%.2f:%b", s, i, d, b);
      assertThatOptionalKind(applicative.map4(r1, r2, r3, r4, combiner))
          .isPresent()
          .contains("test:1:3.14:true");
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("mapN operations short-circuit on first empty")
    void mapNShortCircuitsOnFirstEmpty() {
      Kind<OptionalKind.Witness, Integer> e1 = emptyOptional();
      Kind<OptionalKind.Witness, String> e2 = emptyOptional();
      Kind<OptionalKind.Witness, Double> e3 = emptyOptional();
      Function3<Integer, String, Double, String> combiner = (_, _, _) -> "result";
      assertThatOptionalKind(applicative.map3(e1, e2, e3, combiner)).isEmpty();
    }

    @Test
    @DisplayName("Complex nested applicative operations")
    void complexNestedApplicativeOperations() {
      Kind<OptionalKind.Witness, Function<Integer, Function<String, String>>> nestedFunc =
          applicative.of(i -> s -> s + ":" + i);
      var partialFunc = applicative.ap(nestedFunc, applicative.of(DEFAULT_PRESENT_VALUE));
      var result = applicative.ap(partialFunc, applicative.of("test"));
      assertThatOptionalKind(result).isPresent().contains("test:" + DEFAULT_PRESENT_VALUE);
    }

    @Test
    @DisplayName("of() with null creates empty")
    void ofWithNullCreatesEmpty() {
      assertThatOptionalKind(applicative.of(null)).isEmpty();
    }
  }
}
