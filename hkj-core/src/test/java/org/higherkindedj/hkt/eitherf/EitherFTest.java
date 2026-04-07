// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.eitherf.EitherFAssert.assertThatEitherF;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.test.Identity;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityKindHelper;
import org.higherkindedj.hkt.free.test.IdentityMonad;
import org.higherkindedj.hkt.inject.Inject;
import org.higherkindedj.hkt.inject.InjectInstances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherF Test Suite")
class EitherFTest {

  private Kind<IdentityKind.Witness, String> identityOp(String value) {
    return IdentityKindHelper.IDENTITY.widen(new Identity<>(value));
  }

  private Kind<MaybeKind.Witness, String> maybeOp(String value) {
    return MaybeKindHelper.MAYBE.widen(Maybe.just(value));
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    @DisplayName("left() creates a Left")
    void leftCreatesLeft() {
      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> result =
          EitherF.left(identityOp("hello"));

      assertThatEitherF(result).isLeft();
    }

    @Test
    @DisplayName("right() creates a Right")
    void rightCreatesRight() {
      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> result =
          EitherF.right(maybeOp("world"));

      assertThatEitherF(result).isRight();
    }

    @Test
    @DisplayName("left() with null throws NullPointerException")
    void leftWithNullThrows() {
      assertThatThrownBy(() -> EitherF.left(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("right() with null throws NullPointerException")
    void rightWithNullThrows() {
      assertThatThrownBy(() -> EitherF.right(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Pattern Matching")
  class PatternMatching {

    @Test
    @DisplayName("Left matches Left pattern")
    void leftMatchesLeftPattern() {
      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherF.left(identityOp("test"));

      boolean isLeft =
          switch (eitherF) {
            case EitherF.Left<?, ?, ?> _ -> true;
            case EitherF.Right<?, ?, ?> _ -> false;
          };

      assertThat(isLeft).isTrue();
    }

    @Test
    @DisplayName("Right matches Right pattern")
    void rightMatchesRightPattern() {
      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherF.right(maybeOp("test"));

      boolean isRight =
          switch (eitherF) {
            case EitherF.Left<?, ?, ?> _ -> false;
            case EitherF.Right<?, ?, ?> _ -> true;
          };

      assertThat(isRight).isTrue();
    }
  }

  @Nested
  @DisplayName("Fold")
  class Fold {

    @Test
    @DisplayName("fold() on Left applies onLeft function")
    void foldOnLeftAppliesOnLeft() {
      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherF.left(identityOp("hello"));

      String result = eitherF.fold(left -> "left:" + left, right -> "right:" + right);

      assertThat(result).startsWith("left:");
    }

    @Test
    @DisplayName("fold() on Right applies onRight function")
    void foldOnRightAppliesOnRight() {
      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherF.right(maybeOp("world"));

      String result = eitherF.fold(left -> "left:" + left, right -> "right:" + right);

      assertThat(result).startsWith("right:");
    }

    @Test
    @DisplayName("fold() with null onLeft throws NullPointerException")
    void foldWithNullOnLeftThrows() {
      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherF.left(identityOp("test"));

      assertThatThrownBy(() -> eitherF.fold(null, right -> "right"))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("fold() with null onRight throws NullPointerException")
    void foldWithNullOnRightThrows() {
      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherF.left(identityOp("test"));

      assertThatThrownBy(() -> eitherF.fold(left -> "left", null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("EitherFAssert")
  class AssertTests {

    @Test
    @DisplayName("hasLeftSatisfying verifies Left value")
    void hasLeftSatisfyingVerifiesValue() {
      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherF.left(identityOp("hello"));

      assertThatEitherF(eitherF)
          .isLeft()
          .hasLeftSatisfying(
              kind -> {
                Identity<String> id = IdentityKindHelper.IDENTITY.narrow(kind);
                assertThat(id.value()).isEqualTo("hello");
              });
    }

    @Test
    @DisplayName("hasRightSatisfying verifies Right value")
    void hasRightSatisfyingVerifiesValue() {
      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherF.right(maybeOp("world"));

      assertThatEitherF(eitherF)
          .isRight()
          .hasRightSatisfying(
              kind -> {
                Maybe<String> maybe = MaybeKindHelper.MAYBE.narrow(kind);
                assertThat(maybe.isJust()).isTrue();
              });
    }

    @Test
    @DisplayName("isLeft() fails on Right")
    void isLeftFailsOnRight() {
      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherF.right(maybeOp("world"));

      assertThatThrownBy(() -> assertThatEitherF(eitherF).isLeft())
          .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("isRight() fails on Left")
    void isRightFailsOnLeft() {
      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherF.left(identityOp("hello"));

      assertThatThrownBy(() -> assertThatEitherF(eitherF).isRight())
          .isInstanceOf(AssertionError.class);
    }
  }

  @Nested
  @DisplayName("EitherFKindHelper")
  class KindHelperTests {

    @Test
    @DisplayName("widen(null) throws NullPointerException")
    void widenNullThrows() {
      assertThatThrownBy(() -> EitherFKindHelper.EITHERF.widen(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("narrow(null) throws NullPointerException")
    void narrowNullThrows() {
      assertThatThrownBy(
              () ->
                  EitherFKindHelper.EITHERF.<IdentityKind.Witness, MaybeKind.Witness, String>narrow(
                      null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("widen then narrow round-trips for Left")
    void widenNarrowRoundTripsLeft() {
      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> original =
          EitherF.left(identityOp("test"));
      var kind = EitherFKindHelper.EITHERF.widen(original);
      var roundTripped = EitherFKindHelper.EITHERF.narrow(kind);
      assertThat(roundTripped).isSameAs(original);
    }

    @Test
    @DisplayName("widen then narrow round-trips for Right")
    void widenNarrowRoundTripsRight() {
      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> original =
          EitherF.right(maybeOp("test"));
      var kind = EitherFKindHelper.EITHERF.widen(original);
      var roundTripped = EitherFKindHelper.EITHERF.narrow(kind);
      assertThat(roundTripped).isSameAs(original);
    }
  }

  @Nested
  @DisplayName("Integration: Inject + translate + Interpreters + foldMap")
  class IntegrationTests {

    @Test
    @DisplayName("End-to-end: two-effect program via Inject, translate, combine, foldMap")
    void endToEndTwoEffectProgram() {
      // Build a program in Identity
      var identityMonad = IdentityMonad.INSTANCE;
      Kind<IdentityKind.Witness, Integer> instruction =
          IdentityKindHelper.IDENTITY.widen(new Identity<>(10));
      Free<IdentityKind.Witness, Integer> program =
          Free.liftF(instruction, identityMonad).flatMap(x -> Free.pure(x * 3));

      // Translate into combined effect type via Inject
      Inject<IdentityKind.Witness, EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>>
          inject = InjectInstances.injectLeft();

      EitherFFunctor<IdentityKind.Witness, MaybeKind.Witness> functor =
          EitherFFunctor.of(identityMonad, MaybeMonad.INSTANCE);

      Natural<IdentityKind.Witness, EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>>
          nat = inject::inject;

      Free<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, Integer> translated =
          Free.translate(program, nat, functor);

      // Combine interpreters
      Natural<IdentityKind.Witness, IdentityKind.Witness> identityInterp = Natural.identity();
      Natural<MaybeKind.Witness, IdentityKind.Witness> maybeInterp =
          new Natural<>() {
            @Override
            public <A> Kind<IdentityKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
              return IdentityKindHelper.IDENTITY.widen(
                  new Identity<>(MaybeKindHelper.MAYBE.<A>narrow(fa).get()));
            }
          };

      var combined = Interpreters.combine(identityInterp, maybeInterp);

      // Interpret via foldMap
      var result = translated.foldMap(combined, identityMonad);
      Identity<Integer> id = IdentityKindHelper.IDENTITY.narrow(result);
      assertThat(id.value()).isEqualTo(30); // 10 * 3
    }
  }
}
