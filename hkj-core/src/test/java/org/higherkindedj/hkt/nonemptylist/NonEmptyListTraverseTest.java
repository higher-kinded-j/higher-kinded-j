// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.NonEmptyListAssert.assertThatNonEmptyList;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.TraverseLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("NonEmptyListTraverse")
class NonEmptyListTraverseTest extends NonEmptyListTestBase {

  private final Traverse<NonEmptyListKind.Witness> traverse = NonEmptyListTraverse.INSTANCE;
  private final Foldable<NonEmptyListKind.Witness> foldable = NonEmptyListTraverse.INSTANCE;

  // The applicative threaded through traverse is the real Maybe, matching every other
  // *TraverseTest in the codebase.
  private final Applicative<MaybeKind.Witness> maybeApplicative = Instances.monadError(maybe());

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.nonemptylist.NonEmptyListLawFixtures#kinds")
    void identity(String label, Kind<NonEmptyListKind.Witness, Integer> fa) {
      TraverseLaws.assertIdentity(traverse, fa, equalityChecker);
    }
  }

  @Test
  @DisplayName("Traverse contract — operations, validations & exceptions (laws verified above)")
  void traverseContract() {
    TypeClassContract.<NonEmptyListKind.Witness>traverse(NonEmptyListTraverse.class)
        .<Integer>instance(traverse)
        .<String>withKind(validKind)
        .withMapper(validMapper)
        .withApplicative(maybeApplicative, i -> MAYBE.widen(Maybe.just("v" + i)))
        .withFoldable(Monoids.string(), Object::toString)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("map method")
  class MapTests {

    @Test
    void mapAppliesFunctionToEveryElement() {
      assertThatNonEmptyList(traverse.map(x -> x * 2, nelOf(1, 2, 3))).containsExactly(2, 4, 6);
    }

    @Test
    void mapOnSingleElement() {
      assertThatNonEmptyList(traverse.map(x -> x + 1, singleNel(5))).containsExactly(6);
    }
  }

  @Nested
  @DisplayName("traverse method")
  class TraverseTests {

    private final Function<Integer, Kind<MaybeKind.Witness, String>> intToJustString =
        i -> MAYBE.widen(Maybe.just("v" + i));

    @Test
    void traverseSingleElementCollectsTheEffect() {
      Kind<MaybeKind.Witness, Kind<NonEmptyListKind.Witness, String>> result =
          traverse.traverse(maybeApplicative, intToJustString, singleNel(5));

      Maybe<Kind<NonEmptyListKind.Witness, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThatNonEmptyList(maybe.get()).containsExactly("v5");
    }

    @Test
    void traverseCollectsEveryEffectHeadFirst() {
      Kind<MaybeKind.Witness, Kind<NonEmptyListKind.Witness, String>> result =
          traverse.traverse(maybeApplicative, intToJustString, nelOf(1, 2, 3));

      Maybe<Kind<NonEmptyListKind.Witness, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThatNonEmptyList(maybe.get()).containsExactly("v1", "v2", "v3");
    }

    @Test
    void traverseShortCircuitsWhenAnyElementFails() {
      Function<Integer, Kind<MaybeKind.Witness, Integer>> f =
          i -> (i == 2) ? MAYBE.widen(Maybe.nothing()) : MAYBE.widen(Maybe.just(i));

      Kind<MaybeKind.Witness, Kind<NonEmptyListKind.Witness, Integer>> result =
          traverse.traverse(maybeApplicative, f, nelOf(1, 2, 3));

      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("traverse accumulates head-first with an accumulating applicative")
    void traverseAccumulatesInOrder() {
      Applicative<ValidatedKind.Witness<String>> validatedApp =
          ValidatedMonad.instance(Semigroups.string(","));

      // head (1) and a tail element (3) fail; 2 is valid.
      Function<Integer, Kind<ValidatedKind.Witness<String>, Integer>> validate =
          n ->
              n == 2
                  ? VALIDATED.widen(Validated.<String, Integer>valid(n))
                  : VALIDATED.widen(Validated.<String, Integer>invalid("e" + n));

      Kind<ValidatedKind.Witness<String>, Kind<NonEmptyListKind.Witness, Integer>> result =
          traverse.traverse(validatedApp, validate, nelOf(1, 2, 3));

      Validated<String, Kind<NonEmptyListKind.Witness, Integer>> validated =
          VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("e1,e3"); // element order preserved end-to-end
    }
  }

  @Nested
  @DisplayName("foldMap method")
  class FoldMapTests {

    @Test
    void foldMapSumsElementsFromTheHead() {
      Monoid<Integer> sum = Monoids.integerAddition();
      assertThat(foldable.foldMap(sum, Function.identity(), nelOf(1, 2, 3, 4))).isEqualTo(10);
    }

    @Test
    void foldMapConcatenatesMappedElements() {
      Monoid<String> string = Monoids.string();
      assertThat(foldable.foldMap(string, i -> "i" + i, nelOf(1, 2, 3))).isEqualTo("i1i2i3");
    }

    @Test
    void foldMapOnSingleElement() {
      assertThat(foldable.foldMap(Monoids.integerAddition(), Function.identity(), singleNel(9)))
          .isEqualTo(9);
    }
  }
}
