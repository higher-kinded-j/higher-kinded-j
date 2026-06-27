// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.TraverseLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("EitherOrBothTraverse")
class EitherOrBothTraverseTest extends EitherOrBothTestBase {

  private final Traverse<EitherOrBothKind.Witness<String>> traverse =
      EitherOrBothTraverse.instance();
  private final Foldable<EitherOrBothKind.Witness<String>> foldable =
      EitherOrBothTraverse.instance();

  private final Applicative<MaybeKind.Witness> maybeApplicative = Instances.monadError(maybe());
  private final Function<Integer, Kind<MaybeKind.Witness, String>> intToJust =
      i -> MAYBE.widen(Maybe.just("v" + i));

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.eitherorboth.EitherOrBothLawFixtures#kinds")
    void identity(String label, Kind<EitherOrBothKind.Witness<String>, Integer> fa) {
      TraverseLaws.assertIdentity(traverse, fa, equalityChecker);
    }
  }

  @Test
  @DisplayName("Traverse contract — operations, validations & exceptions (laws verified above)")
  void traverseContract() {
    TypeClassContract.<EitherOrBothKind.Witness<String>>traverse(EitherOrBothTraverse.class)
        .<Integer>instance(traverse)
        .<String>withKind(validKind)
        .withMapper(validMapper)
        .withApplicative(maybeApplicative, intToJust)
        .withFoldable(Monoids.string(), Object::toString)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("traverse")
  class TraverseMethod {

    @Test
    void traverseLeftLiftsUnchanged() {
      Kind<MaybeKind.Witness, Kind<EitherOrBothKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, intToJust, leftK("e"));
      Maybe<Kind<EitherOrBothKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(EITHER_OR_BOTH.narrow(maybe.get())).isEqualTo(EitherOrBoth.left("e"));
    }

    @Test
    void traverseRightMapsTheValue() {
      Kind<MaybeKind.Witness, Kind<EitherOrBothKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, intToJust, rightK(5));
      Maybe<Kind<EitherOrBothKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(EITHER_OR_BOTH.narrow(maybe.get())).isEqualTo(EitherOrBoth.right("v5"));
    }

    @Test
    void traverseBothKeepsWarningsAndMapsTheValue() {
      Kind<MaybeKind.Witness, Kind<EitherOrBothKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, intToJust, bothK("w", 5));
      Maybe<Kind<EitherOrBothKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(EITHER_OR_BOTH.narrow(maybe.get())).isEqualTo(EitherOrBoth.both("w", "v5"));
    }

    @Test
    void traverseShortCircuitsWhenTheRightEffectFails() {
      Function<Integer, Kind<MaybeKind.Witness, String>> failing =
          _ -> MAYBE.widen(Maybe.nothing());
      assertThat(
              MAYBE.narrow(traverse.traverse(maybeApplicative, failing, bothK("w", 5))).isNothing())
          .isTrue();
    }
  }

  @Nested
  @DisplayName("foldMap")
  class FoldMapMethod {

    @Test
    void foldMapLeftIsEmpty() {
      assertThat(foldable.foldMap(Monoids.string(), Object::toString, leftK("e"))).isEmpty();
    }

    @Test
    void foldMapRightContributesTheValue() {
      assertThat(foldable.foldMap(Monoids.string(), i -> "i" + i, rightK(5))).isEqualTo("i5");
    }

    @Test
    void foldMapBothContributesOnlyTheRight() {
      assertThat(foldable.foldMap(Monoids.string(), i -> "i" + i, bothK("w", 5))).isEqualTo("i5");
    }
  }
}
