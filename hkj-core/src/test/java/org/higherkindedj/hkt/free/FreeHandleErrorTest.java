// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.free.FreeAssert.assertThatFree;
import static org.higherkindedj.hkt.free.test.IdentityKindHelper.IDENTITY;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.test.Identity;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityKindHelper;
import org.higherkindedj.hkt.free.test.IdentityMonad;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.trymonad.TryMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Free.HandleError Test Suite")
class FreeHandleErrorTest {

  private final IdentityMonad identityMonad = IdentityMonad.INSTANCE;
  private final TryMonad tryMonad = TryMonad.INSTANCE;

  /** Natural transformation: Identity -> Try (wraps value in Success). */
  private final Natural<IdentityKind.Witness, TryKind.Witness> identityToTry =
      new Natural<>() {
        @Override
        public <A> Kind<TryKind.Witness, A> apply(Kind<IdentityKind.Witness, A> fa) {
          Identity<A> id = IdentityKindHelper.IDENTITY.narrow(fa);
          return TRY.widen(Try.success(id.value()));
        }
      };

  @Nested
  @DisplayName("HandleError constructor null validation")
  class ConstructorNullValidation {

    @Test
    @DisplayName("HandleError rejects null program")
    void rejectsNullProgram() {
      assertThatThrownBy(
              () -> new Free.HandleError<>(null, e -> Free.pure("fallback"), Throwable.class))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("HandleError rejects null handler")
    void rejectsNullHandler() {
      assertThatThrownBy(() -> new Free.HandleError<>(Free.pure("test"), null, Throwable.class))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("HandleError rejects null errorType")
    void rejectsNullErrorType() {
      assertThatThrownBy(
              () -> new Free.HandleError<>(Free.pure("test"), e -> Free.pure("fallback"), null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("HandleError structure")
  class StructureTests {

    @Test
    @DisplayName("handleError() creates HandleError variant")
    void handleErrorCreatesCorrectVariant() {
      Free<IdentityKind.Witness, String> program =
          Free.<IdentityKind.Witness, String>pure("test")
              .handleError(RuntimeException.class, e -> Free.pure("fallback"));

      assertThatFree(program).isHandleError();
    }

    @Test
    @DisplayName("Nested handleError wraps correctly")
    void nestedHandleErrorWraps() {
      Free<IdentityKind.Witness, String> program =
          Free.<IdentityKind.Witness, String>pure("inner")
              .handleError(IllegalArgumentException.class, e -> Free.pure("inner-recovery"))
              .handleError(RuntimeException.class, e -> Free.pure("outer-recovery"));

      assertThatFree(program).isHandleError();
    }
  }

  @Nested
  @DisplayName("HandleError with non-MonadError target (Id)")
  class NonMonadErrorTarget {

    @Test
    @DisplayName("HandleError is silently ignored when target is not MonadError")
    void handleErrorIgnoredForNonMonadError() {
      Free<IdentityKind.Witness, Integer> program =
          Free.<IdentityKind.Witness, Integer>pure(42)
              .handleError(Throwable.class, e -> Free.pure(-1));

      Kind<IdentityKind.Witness, Integer> result =
          program.foldMap(Natural.identity(), identityMonad);

      assertThat(IDENTITY.<Integer>narrow(result).value()).isEqualTo(42);
    }

    @Test
    @DisplayName("HandleError on pure value passes through with non-MonadError")
    void handleErrorOnPurePassesThrough() {
      Free<IdentityKind.Witness, String> program =
          Free.<IdentityKind.Witness, String>pure("hello")
              .handleError(RuntimeException.class, e -> Free.pure("recovered"));

      Kind<IdentityKind.Witness, String> result =
          program.foldMap(Natural.identity(), identityMonad);

      assertThat(IDENTITY.<String>narrow(result).value()).isEqualTo("hello");
    }
  }

  @Nested
  @DisplayName("HandleError with MonadError target (Try)")
  class MonadErrorTarget {

    @Test
    @DisplayName("HandleError on successful computation returns original value")
    void handleErrorOnSuccessReturnsOriginal() {
      Free<IdentityKind.Witness, Integer> program =
          Free.<IdentityKind.Witness, Integer>pure(42)
              .handleError(Exception.class, e -> Free.pure(-1));

      Kind<TryKind.Witness, Integer> result = program.foldMap(identityToTry, tryMonad);

      Try<Integer> tryResult = TRY.narrow(result);
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse(null)).isEqualTo(42);
    }

    @Test
    @DisplayName("HandleError recovers from matching error via MonadError")
    void handleErrorRecoversFromMatchingError() {
      // Build a program that raises an error during interpretation via TryMonad.raiseError
      // We use a Natural that converts the Identity instruction to a Failure
      Natural<IdentityKind.Witness, TryKind.Witness> failingInterp =
          new Natural<>() {
            @Override
            public <A> Kind<TryKind.Witness, A> apply(Kind<IdentityKind.Witness, A> fa) {
              return tryMonad.raiseError(new RuntimeException("boom"));
            }
          };

      Free<IdentityKind.Witness, String> program =
          Free.liftF(IDENTITY.widen(new Identity<>("will-fail")), identityMonad)
              .handleError(Throwable.class, e -> Free.pure("recovered: " + e.getMessage()));

      Kind<TryKind.Witness, String> result = program.foldMap(failingInterp, tryMonad);

      Try<String> tryResult = TRY.narrow(result);
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse(null)).isEqualTo("recovered: boom");
    }

    @Test
    @DisplayName("HandleError re-raises when error type does not match")
    void handleErrorReRaisesOnTypeMismatch() {
      Natural<IdentityKind.Witness, TryKind.Witness> failingInterp =
          new Natural<>() {
            @Override
            public <A> Kind<TryKind.Witness, A> apply(Kind<IdentityKind.Witness, A> fa) {
              return tryMonad.raiseError(new RuntimeException("boom"));
            }
          };

      // Handle only IllegalArgumentException, not RuntimeException
      Free<IdentityKind.Witness, String> program =
          Free.liftF(IDENTITY.widen(new Identity<>("will-fail")), identityMonad)
              .handleError(IllegalArgumentException.class, e -> Free.pure("should not reach"));

      Kind<TryKind.Witness, String> result = program.foldMap(failingInterp, tryMonad);

      Try<String> tryResult = TRY.narrow(result);
      assertThat(tryResult.isFailure()).isTrue();
    }

    @Test
    @DisplayName("HandleError matches subclass errors")
    void handleErrorMatchesSubclass() {
      Natural<IdentityKind.Witness, TryKind.Witness> failingInterp =
          new Natural<>() {
            @Override
            public <A> Kind<TryKind.Witness, A> apply(Kind<IdentityKind.Witness, A> fa) {
              return tryMonad.raiseError(new IllegalArgumentException("bad arg"));
            }
          };

      // Handle RuntimeException — IllegalArgumentException is a subclass
      Free<IdentityKind.Witness, String> program =
          Free.liftF(IDENTITY.widen(new Identity<>("will-fail")), identityMonad)
              .handleError(
                  RuntimeException.class,
                  e -> Free.pure("caught: " + e.getClass().getSimpleName()));

      Kind<TryKind.Witness, String> result = program.foldMap(failingInterp, tryMonad);

      Try<String> tryResult = TRY.narrow(result);
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse(null)).isEqualTo("caught: IllegalArgumentException");
    }
  }
}
