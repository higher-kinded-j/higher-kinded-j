// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.free.test.IdentityKindHelper.IDENTITY;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.eitherf.EitherF;
import org.higherkindedj.hkt.eitherf.EitherFFunctor;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.eitherf.EitherFKindHelper;
import org.higherkindedj.hkt.eitherf.Interpreters;
import org.higherkindedj.hkt.free.test.Identity;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityKindHelper;
import org.higherkindedj.hkt.free.test.IdentityMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Free.translate Test Suite")
class FreeTranslateTest {

  private final IdentityMonad identityMonad = IdentityMonad.INSTANCE;

  @Nested
  @DisplayName("translate preserves structure")
  class TranslatePreservesStructure {

    @Test
    @DisplayName("translate Pure preserves value")
    void translatePurePreservesValue() {
      Free<IdentityKind.Witness, String> pure = Free.pure("hello");

      // Translate from Identity to EitherF<Identity, Maybe>
      Natural<IdentityKind.Witness, EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>>
          nat =
              new Natural<>() {
                @Override
                public <A>
                    Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, A> apply(
                        Kind<IdentityKind.Witness, A> fa) {
                  return EitherFKindHelper.EITHERF.widen(EitherF.left(fa));
                }
              };

      EitherFFunctor<IdentityKind.Witness, MaybeKind.Witness> functor =
          new EitherFFunctor<>(identityMonad, MaybeMonad.INSTANCE);

      Free<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, String> translated =
          Free.translate(pure, nat, functor);

      assertThat(translated).isInstanceOf(Free.Pure.class);
      assertThat(((Free.Pure<?, String>) translated).value()).isEqualTo("hello");
    }

    @Test
    @DisplayName("translate liftF preserves computation")
    void translateLiftFPreservesComputation() {
      // Create a simple program: liftF an Identity instruction, then map
      Kind<IdentityKind.Witness, Integer> instruction = IDENTITY.widen(new Identity<>(42));
      Free<IdentityKind.Witness, Integer> program = Free.liftF(instruction, identityMonad);

      // Translate Identity -> EitherF<Identity, Maybe> via Left injection
      Natural<IdentityKind.Witness, EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>>
          nat =
              new Natural<>() {
                @Override
                public <A>
                    Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, A> apply(
                        Kind<IdentityKind.Witness, A> fa) {
                  return EitherFKindHelper.EITHERF.widen(EitherF.left(fa));
                }
              };

      EitherFFunctor<IdentityKind.Witness, MaybeKind.Witness> functor =
          new EitherFFunctor<>(identityMonad, MaybeMonad.INSTANCE);

      Free<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, Integer> translated =
          Free.translate(program, nat, functor);

      // Interpret the translated program
      Natural<IdentityKind.Witness, IdentityKind.Witness> identityInterp = Natural.identity();
      Natural<MaybeKind.Witness, IdentityKind.Witness> maybeInterp =
          new Natural<>() {
            @Override
            public <A> Kind<IdentityKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
              Maybe<A> maybe = MaybeKindHelper.MAYBE.narrow(fa);
              return IdentityKindHelper.IDENTITY.widen(new Identity<>(maybe.get()));
            }
          };

      Natural<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, IdentityKind.Witness>
          combinedInterp = Interpreters.combine(identityInterp, maybeInterp);

      Kind<IdentityKind.Witness, Integer> result =
          translated.foldMap(combinedInterp, identityMonad);
      Identity<Integer> id = IDENTITY.narrow(result);
      assertThat(id.value()).isEqualTo(42);
    }

    @Test
    @DisplayName("translate preserves flatMap chains")
    void translatePreservesFlatMapChains() {
      // Build a program with flatMap
      Kind<IdentityKind.Witness, Integer> instruction = IDENTITY.widen(new Identity<>(10));
      Free<IdentityKind.Witness, Integer> program =
          Free.liftF(instruction, identityMonad)
              .flatMap(x -> Free.pure(x * 2))
              .flatMap(x -> Free.pure(x + 1));

      // Translate and interpret
      Natural<IdentityKind.Witness, EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>>
          nat =
              new Natural<>() {
                @Override
                public <A>
                    Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, A> apply(
                        Kind<IdentityKind.Witness, A> fa) {
                  return EitherFKindHelper.EITHERF.widen(EitherF.left(fa));
                }
              };

      EitherFFunctor<IdentityKind.Witness, MaybeKind.Witness> functor =
          new EitherFFunctor<>(identityMonad, MaybeMonad.INSTANCE);

      Free<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, Integer> translated =
          Free.translate(program, nat, functor);

      Natural<IdentityKind.Witness, IdentityKind.Witness> identityInterp = Natural.identity();
      Natural<MaybeKind.Witness, IdentityKind.Witness> maybeInterp =
          new Natural<>() {
            @Override
            public <A> Kind<IdentityKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
              Maybe<A> maybe = MaybeKindHelper.MAYBE.narrow(fa);
              return IdentityKindHelper.IDENTITY.widen(new Identity<>(maybe.get()));
            }
          };

      Natural<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, IdentityKind.Witness>
          combinedInterp = Interpreters.combine(identityInterp, maybeInterp);

      Kind<IdentityKind.Witness, Integer> result =
          translated.foldMap(combinedInterp, identityMonad);
      Identity<Integer> id = IDENTITY.narrow(result);
      assertThat(id.value()).isEqualTo(21); // 10 * 2 + 1
    }

    @Test
    @DisplayName("translate preserves HandleError structure")
    void translatePreservesHandleError() {
      Free<IdentityKind.Witness, String> program =
          Free.<IdentityKind.Witness, String>pure("test")
              .handleError(RuntimeException.class, e -> Free.pure("recovered"));

      Free<IdentityKind.Witness, String> translated =
          Free.translate(program, Natural.identity(), identityMonad);

      FreeAssert.assertThatFree(translated).isHandleError();

      Kind<IdentityKind.Witness, String> result =
          translated.foldMap(Natural.identity(), identityMonad);
      assertThat(IDENTITY.<String>narrow(result).value()).isEqualTo("test");
    }
  }

  @Nested
  @DisplayName("Stack Safety")
  class StackSafety {

    @Test
    @DisplayName("translate handles deeply nested flatMap chains without stack overflow")
    void translateHandlesDeeplyNestedFlatMapChains() {
      // Build a left-associated flatMap chain deep enough to overflow without trampolining
      Free<IdentityKind.Witness, Integer> program = Free.pure(0);
      for (int i = 0; i < 10_000; i++) {
        program = program.flatMap(x -> Free.pure(x + 1));
      }

      Free<IdentityKind.Witness, Integer> translated =
          Free.translate(program, Natural.identity(), identityMonad);

      Kind<IdentityKind.Witness, Integer> result =
          translated.foldMap(Natural.identity(), identityMonad);
      Identity<Integer> id = IDENTITY.narrow(result);
      assertThat(id.value()).isEqualTo(10_000);
    }
  }

  @Nested
  @DisplayName("Null Validation")
  class NullValidation {

    @Test
    @DisplayName("translate rejects null program")
    void translateRejectsNullProgram() {
      assertThatThrownBy(() -> Free.translate(null, Natural.identity(), identityMonad))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("translate rejects null natural transformation")
    void translateRejectsNullNat() {
      Free<IdentityKind.Witness, String> program = Free.pure("test");
      assertThatThrownBy(() -> Free.translate(program, null, identityMonad))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("translate rejects null functor")
    void translateRejectsNullFunctor() {
      Free<IdentityKind.Witness, String> program = Free.pure("test");
      assertThatThrownBy(() -> Free.translate(program, Natural.identity(), null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
