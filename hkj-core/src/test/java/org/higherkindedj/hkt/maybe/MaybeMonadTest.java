// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("MaybeMonad")
class MaybeMonadTest extends MaybeTestBase {

  private MonadError<MaybeKind.Witness, Unit> monad;

  @BeforeEach
  void setUp() {
    monad = Instances.monadError(maybe());
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.maybe.MaybeLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(monad, value, testFunction, equalityChecker);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.maybe.MaybeLawFixtures#kinds")
    void rightIdentity(String label, Kind<MaybeKind.Witness, Integer> ma) {
      MonadLaws.assertRightIdentity(monad, ma, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.maybe.MaybeLawFixtures#kinds")
    void associativity(String label, Kind<MaybeKind.Witness, Integer> ma) {
      MonadLaws.assertAssociativity(monad, ma, testFunction, chainFunction, equalityChecker);
    }
  }

  @Test
  @DisplayName("Monad contract — operations, validations & exceptions (laws verified above)")
  void monadContract() {
    TypeClassContract.<MaybeKind.Witness>monad(MaybeMonad.class)
        .<Integer>instance(monad)
        .<String>withKind(validKind)
        .withMonadOperations(
            validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("flatMap operations")
  class FlatMapOperations {

    @Test
    void flatMapOnJustAppliesFunction() {
      Kind<MaybeKind.Witness, String> result = monad.flatMap(validFlatMapper, validKind);
      assertThatMaybe(result).isJust().hasValue("flat:" + DEFAULT_JUST_VALUE);
    }

    @Test
    void flatMapOnJustCanReturnNothing() {
      Function<Integer, Kind<MaybeKind.Witness, String>> nothingMapper = _ -> nothingKind();
      Kind<MaybeKind.Witness, String> result = monad.flatMap(nothingMapper, validKind);
      assertThatMaybe(result).isNothing();
    }

    @Test
    void flatMapOnNothingReturnsNothing() {
      Kind<MaybeKind.Witness, String> result = monad.flatMap(validFlatMapper, nothingKind());
      assertThatMaybe(result).isNothing();
    }
  }

  @Nested
  @DisplayName("of operations")
  class OfOperations {

    @Test
    void ofCreatesJustForNonNull() {
      Kind<MaybeKind.Witness, String> result = monad.of("success");
      assertThatMaybe(result).isJust().hasValue("success");
    }

    @Test
    void ofCreatesNothingForNull() {
      Kind<MaybeKind.Witness, String> result = monad.of(null);
      assertThatMaybe(result).isNothing();
    }
  }

  @Nested
  @DisplayName("ap operations")
  class ApOperations {

    @Test
    void apAppliesFunctionToValue() {
      Kind<MaybeKind.Witness, String> result = monad.ap(validFunctionKind, validKind);
      assertThatMaybe(result).isJust().hasValue(String.valueOf(DEFAULT_JUST_VALUE));
    }

    @Test
    void apReturnsNothingWhenFunctionIsNothing() {
      Kind<MaybeKind.Witness, Function<Integer, String>> nothingFn = nothingKind();
      Kind<MaybeKind.Witness, String> result = monad.ap(nothingFn, validKind);
      assertThatMaybe(result).isNothing();
    }

    @Test
    void apReturnsNothingWhenArgumentIsNothing() {
      Kind<MaybeKind.Witness, String> result = monad.ap(validFunctionKind, nothingKind());
      assertThatMaybe(result).isNothing();
    }
  }

  @Nested
  @DisplayName("map2 operations")
  class Map2Operations {

    @Test
    void map2CombinesTwoJustValues() {
      Kind<MaybeKind.Witness, String> result =
          monad.map2(validKind, validKind2, validCombiningFunction);
      assertThatMaybe(result)
          .isJust()
          .hasValue("Result:" + DEFAULT_JUST_VALUE + "," + ALTERNATIVE_JUST_VALUE);
    }

    @Test
    void map2ReturnsNothingIfFirstIsNothing() {
      Kind<MaybeKind.Witness, String> result =
          monad.map2(nothingKind(), validKind2, validCombiningFunction);
      assertThatMaybe(result).isNothing();
    }

    @Test
    void map2ReturnsNothingIfSecondIsNothing() {
      Kind<MaybeKind.Witness, String> result =
          monad.map2(validKind, nothingKind(), validCombiningFunction);
      assertThatMaybe(result).isNothing();
    }
  }

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    void deepFlatMapChaining() {
      Kind<MaybeKind.Witness, Integer> result = justKind(1);
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result = monad.flatMap(x -> monad.of(x + increment), result);
      }
      assertThatMaybe(result).isJust().hasValue(46);
    }

    @Test
    void flatMapWithEarlyNothingShortCircuits() {
      Kind<MaybeKind.Witness, Integer> result = justKind(1);
      for (int i = 0; i < 10; i++) {
        final int index = i;
        result = monad.flatMap(x -> index == 5 ? this.nothing() : monad.of(x + index), result);
      }
      assertThatMaybe(result).isNothing();
    }

    @Test
    void chainingMapAndFlatMap() {
      Kind<MaybeKind.Witness, Integer> start = justKind(10);
      Kind<MaybeKind.Witness, String> result =
          monad.flatMap(
              i -> monad.map(String::toUpperCase, monad.map(x -> "value:" + x, monad.of(i * 2))),
              start);
      assertThatMaybe(result).isJust().hasValue("VALUE:20");
    }

    @Test
    void nestedMaybeFlattens() {
      Kind<MaybeKind.Witness, Kind<MaybeKind.Witness, Integer>> nested =
          justKind(justKind(DEFAULT_JUST_VALUE));
      Kind<MaybeKind.Witness, Integer> flattened = monad.flatMap(inner -> inner, nested);
      assertThatMaybe(flattened).isJust().hasValue(DEFAULT_JUST_VALUE);
    }

    @Test
    void flatMapWithThrowingMapperOnNothingDoesNotRun() {
      Function<Integer, Kind<MaybeKind.Witness, String>> throwing =
          _ -> {
            throw new AssertionError("flatMap function must not run on Nothing");
          };
      assertThatCode(() -> monad.flatMap(throwing, this.nothing())).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "flatMap rejects null {0} parameter")
    @MethodSource("flatMapNullParameters")
    void flatMapRejectsNullParameter(
        String label,
        Kind<MaybeKind.Witness, Integer> kind,
        Function<Integer, Kind<MaybeKind.Witness, String>> function) {
      assertThatThrownBy(() -> monad.flatMap(function, kind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining(label);
    }

    static Stream<Arguments> flatMapNullParameters() {
      Kind<MaybeKind.Witness, Integer> okKind = MAYBE.widen(Maybe.just(42));
      Function<Integer, Kind<MaybeKind.Witness, String>> okFn =
          i -> MAYBE.widen(Maybe.just("flat:" + i));
      return Stream.of(Arguments.of("f for", okKind, null), Arguments.of("Kind", null, okFn));
    }

    private <A> Kind<MaybeKind.Witness, A> nothing() {
      return nothingKind();
    }
  }
}
