// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.OptionalKindAssert.assertThatOptionalKind;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.TraverseLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("OptionalTraverse")
class OptionalTraverseTest extends OptionalTestBase {

  private Traverse<OptionalKind.Witness> traverse;
  private Applicative<MaybeKind.Witness> maybeApplicative;
  private Kind<OptionalKind.Witness, Integer> presentKind;
  private Kind<OptionalKind.Witness, Integer> emptyKind;
  private Function<Integer, Kind<MaybeKind.Witness, String>> validTraverseFunction;
  private Monoid<String> validMonoid;
  private Function<Integer, String> validFoldMapFunction;

  @BeforeEach
  void setUpTraverse() {
    traverse = OptionalTraverse.INSTANCE;
    maybeApplicative = Instances.monadError(maybe());
    presentKind = validKind;
    emptyKind = emptyOptional();
    validTraverseFunction = i -> MAYBE.widen(Maybe.just("Traversed:" + i));
    validMonoid = Monoids.string();
    validFoldMapFunction = validMapper;

    validateRequiredFixtures();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.optional.OptionalLawFixtures#kinds")
    void identity(String label, Kind<OptionalKind.Witness, Integer> fa) {
      TraverseLaws.assertIdentity(traverse, fa, equalityChecker);
    }
  }

  @Test
  @DisplayName("Traverse contract — operations, validations & exceptions (laws verified above)")
  void traverseContract() {
    TypeClassContract.<OptionalKind.Witness>traverse(OptionalTraverse.class)
        .<Integer>instance(traverse)
        .<String>withKind(presentKind)
        .withMapper(validMapper)
        .withApplicative(maybeApplicative, validTraverseFunction)
        .withFoldable(validMonoid, validFoldMapFunction)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("traverse() on a present value with a successful function")
    void traversePresentSuccessful() {
      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, String>> result =
          traverse.traverse(maybeApplicative, validTraverseFunction, presentKind);

      Maybe<Kind<OptionalKind.Witness, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThatOptionalKind(maybe.get())
          .isPresent()
          .contains("Traversed:" + DEFAULT_PRESENT_VALUE);
    }

    @Test
    @DisplayName("traverse() on a present value with a failing function")
    void traversePresentFailing() {
      Function<Integer, Kind<MaybeKind.Witness, String>> failingFunc =
          _ -> MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, String>> result =
          traverse.traverse(maybeApplicative, failingFunc, presentKind);

      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("traverse() on empty lifts empty into the applicative context")
    void traverseEmptyLiftsEmpty() {
      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, String>> result =
          traverse.traverse(maybeApplicative, validTraverseFunction, emptyKind);

      Maybe<Kind<OptionalKind.Witness, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThatOptionalKind(maybe.get()).isEmpty();
    }

    @Test
    @DisplayName("foldMap() on a present value applies the function")
    void foldMapOnPresentAppliesFunction() {
      String result = traverse.foldMap(Monoids.string(), i -> "Value:" + i, presentKind);
      assertThat(result).isEqualTo("Value:" + DEFAULT_PRESENT_VALUE);
    }

    @Test
    @DisplayName("foldMap() on empty returns monoid empty")
    void foldMapOnEmptyReturnsEmpty() {
      Monoid<String> stringMonoid = Monoids.string();
      String result = traverse.foldMap(stringMonoid, i -> "Value:" + i, emptyKind);
      assertThat(result).isEqualTo(stringMonoid.empty());
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("map() applies the function to a present value")
    void mapAppliesFunctionToPresent() {
      Kind<OptionalKind.Witness, String> result = traverse.map(validMapper, presentKind);
      assertThatOptionalKind(result).isPresent().contains(DEFAULT_PRESENT_VALUE.toString());
    }

    @Test
    @DisplayName("map() preserves empty unchanged")
    void mapPreservesEmpty() {
      Kind<OptionalKind.Witness, String> result = traverse.map(validMapper, emptyKind);
      assertThatOptionalKind(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("traverse() with a conditional function")
    void traverseWithConditionalFunction() {
      Function<Integer, Kind<MaybeKind.Witness, String>> conditionalFunc =
          i -> i > 50 ? MAYBE.widen(Maybe.just(i.toString())) : MAYBE.widen(Maybe.nothing());

      // Should fail because DEFAULT_PRESENT_VALUE (42) <= 50
      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, String>> failResult =
          traverse.traverse(maybeApplicative, conditionalFunc, presentKind);
      assertThat(MAYBE.narrow(failResult).isNothing()).isTrue();

      // Should succeed with value > 50
      Kind<OptionalKind.Witness, Integer> bigPresent = presentOf(100);
      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, String>> successResult =
          traverse.traverse(maybeApplicative, conditionalFunc, bigPresent);

      Maybe<Kind<OptionalKind.Witness, String>> maybe = MAYBE.narrow(successResult);
      assertThat(maybe.isJust()).isTrue();
      assertThatOptionalKind(maybe.get()).isPresent().contains("100");
    }

    @Test
    @DisplayName("foldMap() with different monoids")
    void foldMapWithDifferentMonoids() {
      Monoid<Integer> intAddition = Monoids.integerAddition();
      assertThat(traverse.foldMap(intAddition, i -> i * 2, presentKind))
          .isEqualTo(DEFAULT_PRESENT_VALUE * 2);
      assertThat(traverse.foldMap(intAddition, i -> i * 2, emptyKind))
          .isEqualTo(intAddition.empty());

      Monoid<Boolean> andMonoid = Monoids.booleanAnd();
      assertThat(traverse.foldMap(andMonoid, i -> i > 0, presentKind)).isTrue();
    }

    @Test
    @DisplayName("sequenceA() turns present(Just<A>) into Just(present(A))")
    void sequencePresentJustToJustPresent() {
      Kind<MaybeKind.Witness, Integer> maybeKind = MAYBE.widen(Maybe.just(DEFAULT_PRESENT_VALUE));
      Kind<OptionalKind.Witness, Kind<MaybeKind.Witness, Integer>> input = presentOf(maybeKind);

      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      Maybe<Kind<OptionalKind.Witness, Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThatOptionalKind(maybe.get()).isPresent().contains(DEFAULT_PRESENT_VALUE);
    }

    @Test
    @DisplayName("sequenceA() turns present(Nothing) into Nothing")
    void sequencePresentNothingToNothing() {
      Kind<MaybeKind.Witness, Integer> maybeKind = MAYBE.widen(Maybe.nothing());
      Kind<OptionalKind.Witness, Kind<MaybeKind.Witness, Integer>> input = presentOf(maybeKind);

      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("sequenceA() preserves empty values")
    void sequenceEmptyPreservesEmpty() {
      Kind<OptionalKind.Witness, Kind<MaybeKind.Witness, Integer>> emptyInput = emptyOptional();

      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, Integer>> result =
          traverse.sequenceA(maybeApplicative, emptyInput);

      Maybe<Kind<OptionalKind.Witness, Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThatOptionalKind(maybe.get()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("traverse() integrates with map")
    void traverseIntegratesWithMap() {
      Kind<OptionalKind.Witness, String> mapped = traverse.map(i -> "mapped:" + i, presentKind);

      Function<String, Kind<MaybeKind.Witness, String>> traverseFunc =
          s -> MAYBE.widen(Maybe.just(s.toUpperCase()));

      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, String>> result =
          traverse.traverse(maybeApplicative, traverseFunc, mapped);

      Maybe<Kind<OptionalKind.Witness, String>> maybe = MAYBE.narrow(result);
      assertThatOptionalKind(maybe.get()).isPresent().contains("MAPPED:" + DEFAULT_PRESENT_VALUE);
    }

    @Test
    @DisplayName("traverse() integrates with foldMap")
    void traverseIntegratesWithFoldMap() {
      String folded = traverse.foldMap(Monoids.string(), i -> "fold:" + i, presentKind);
      Kind<OptionalKind.Witness, String> foldedKind = presentOf(folded);

      Function<String, Kind<MaybeKind.Witness, String>> traverseFunc =
          s -> MAYBE.widen(Maybe.just("traversed:" + s));

      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, String>> result =
          traverse.traverse(maybeApplicative, traverseFunc, foldedKind);

      Maybe<Kind<OptionalKind.Witness, String>> maybe = MAYBE.narrow(result);
      assertThatOptionalKind(maybe.get())
          .isPresent()
          .contains("traversed:fold:" + DEFAULT_PRESENT_VALUE);
    }
  }
}
