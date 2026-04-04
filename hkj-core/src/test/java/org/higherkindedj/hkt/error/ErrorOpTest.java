// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.eitherf.EitherFFunctor;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityMonad;
import org.higherkindedj.hkt.inject.Inject;
import org.higherkindedj.hkt.inject.InjectInstances;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.trymonad.TryMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ErrorOp Test Suite")
class ErrorOpTest {

  @Nested
  @DisplayName("ErrorOp construction")
  class Construction {

    @Test
    @DisplayName("Raise creates an error operation")
    void raiseCreatesErrorOp() {
      ErrorOp<String, Integer> op = new ErrorOp.Raise<>("not found");
      assertThat(op).isInstanceOf(ErrorOp.Raise.class);
      assertThat(((ErrorOp.Raise<String, Integer>) op).error()).isEqualTo("not found");
    }

    @Test
    @DisplayName("Raise rejects null error")
    void raiseRejectsNull() {
      assertThatThrownBy(() -> new ErrorOp.Raise<>(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("ErrorOps smart constructors")
  class SmartConstructors {

    @Test
    @DisplayName("raise() lifts error into Free")
    void raiseLiftsFreeProgram() {
      Free<ErrorOpKind.Witness<String>, Integer> program = ErrorOps.raise("error");
      assertThat(program).isNotNull();
    }

    @Test
    @DisplayName("raise() rejects null error")
    void raiseRejectsNull() {
      assertThatThrownBy(() -> ErrorOps.<String, Integer>raise(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("ErrorOp interpretation via MonadError")
  class Interpretation {

    @Test
    @DisplayName("Raise interpreted into Try produces Failure")
    void raiseInterpretedIntoTryProducesFailure() {
      Free<ErrorOpKind.Witness<RuntimeException>, String> program =
          ErrorOps.raise(new RuntimeException("test error"));

      // Interpreter: ErrorOp -> Try (raises error via TryMonad.raiseError)
      Natural<ErrorOpKind.Witness<RuntimeException>, TryKind.Witness> interpreter =
          new Natural<>() {
            @Override
            public <A> Kind<TryKind.Witness, A> apply(
                Kind<ErrorOpKind.Witness<RuntimeException>, A> fa) {
              ErrorOp<RuntimeException, A> op = ErrorOpKindHelper.ERROR_OP.narrow(fa);
              return switch (op) {
                case ErrorOp.Raise<RuntimeException, A> raise ->
                    TryMonad.INSTANCE.raiseError(raise.error());
              };
            }
          };

      Kind<TryKind.Witness, String> result = program.foldMap(interpreter, TryMonad.INSTANCE);

      Try<String> tryResult = TRY.narrow(result);
      assertThat(tryResult.isFailure()).isTrue();
    }
  }

  @Nested
  @DisplayName("ErrorOpKindHelper")
  class KindHelperTests {

    @Test
    @DisplayName("widen then narrow round-trips")
    void widenNarrowRoundTrips() {
      ErrorOp<String, Integer> original = new ErrorOp.Raise<>("error");
      Kind<ErrorOpKind.Witness<String>, Integer> kind = ErrorOpKindHelper.ERROR_OP.widen(original);
      ErrorOp<String, Integer> roundTripped = ErrorOpKindHelper.ERROR_OP.narrow(kind);
      assertThat(roundTripped).isSameAs(original);
    }

    @Test
    @DisplayName("widen(null) throws NullPointerException")
    void widenNullThrows() {
      assertThatThrownBy(() -> ErrorOpKindHelper.ERROR_OP.widen(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("narrow(null) throws NullPointerException")
    void narrowNullThrows() {
      assertThatThrownBy(() -> ErrorOpKindHelper.ERROR_OP.<String, Integer>narrow(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("ErrorOpFunctor")
  class FunctorTests {

    @Test
    @DisplayName("map on Raise preserves error (cast-through)")
    void mapPreservesError() {
      ErrorOpFunctor<String> functor = ErrorOpFunctor.instance();
      Kind<ErrorOpKind.Witness<String>, Integer> kind =
          ErrorOpKindHelper.ERROR_OP.widen(new ErrorOp.Raise<>("error"));

      Kind<ErrorOpKind.Witness<String>, String> mapped = functor.map(Object::toString, kind);

      ErrorOp<String, String> result = ErrorOpKindHelper.ERROR_OP.narrow(mapped);
      assertThat(result).isInstanceOf(ErrorOp.Raise.class);
      assertThat(((ErrorOp.Raise<String, String>) result).error()).isEqualTo("error");
    }

    @Test
    @DisplayName("map rejects null function")
    void mapRejectsNullFunction() {
      ErrorOpFunctor<String> functor = ErrorOpFunctor.instance();
      Kind<ErrorOpKind.Witness<String>, Integer> kind =
          ErrorOpKindHelper.ERROR_OP.widen(new ErrorOp.Raise<>("error"));

      assertThatThrownBy(() -> functor.map(null, kind)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map rejects null Kind argument")
    void mapRejectsNullKind() {
      ErrorOpFunctor<String> functor = ErrorOpFunctor.instance();

      assertThatThrownBy(() -> functor.map(Object::toString, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("ErrorOps.Bound")
  class BoundTests {

    @Test
    @DisplayName("boundTo creates Bound instance")
    void boundToCreatesBound() {
      Inject<
              ErrorOpKind.Witness<String>,
              EitherFKind.Witness<ErrorOpKind.Witness<String>, IdentityKind.Witness>>
          inject = InjectInstances.injectLeft();

      var functor = new EitherFFunctor<>(ErrorOpFunctor.<String>instance(), IdentityMonad.INSTANCE);

      ErrorOps.Bound<String, EitherFKind.Witness<ErrorOpKind.Witness<String>, IdentityKind.Witness>>
          bound = ErrorOps.boundTo(inject, functor);

      assertThat(bound).isNotNull();
    }

    @Test
    @DisplayName("Bound.raise translates error into combined effect type")
    void boundRaiseTranslates() {
      Inject<
              ErrorOpKind.Witness<String>,
              EitherFKind.Witness<ErrorOpKind.Witness<String>, IdentityKind.Witness>>
          inject = InjectInstances.injectLeft();

      var functor = new EitherFFunctor<>(ErrorOpFunctor.<String>instance(), IdentityMonad.INSTANCE);

      var bound =
          ErrorOps
              .<String, EitherFKind.Witness<ErrorOpKind.Witness<String>, IdentityKind.Witness>>
                  boundTo(inject, functor);

      Free<EitherFKind.Witness<ErrorOpKind.Witness<String>, IdentityKind.Witness>, Integer>
          program = bound.raise("not found");

      assertThat(program).isNotNull();
    }
  }
}
