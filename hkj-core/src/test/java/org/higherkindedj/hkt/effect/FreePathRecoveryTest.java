// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.free.test.IdentityKindHelper.IDENTITY;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.FreeAssert;
import org.higherkindedj.hkt.free.test.Identity;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityMonad;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.trymonad.TryMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FreePath Error Recovery Test Suite")
class FreePathRecoveryTest {

  private final IdentityMonad identityMonad = IdentityMonad.INSTANCE;

  @Nested
  @DisplayName("handleError")
  class HandleErrorTests {

    @Test
    @DisplayName("handleError wraps underlying Free with HandleError")
    void handleErrorWrapsWithHandleError() {
      FreePath<IdentityKind.Witness, String> path = FreePath.pure("hello", identityMonad);

      FreePath<IdentityKind.Witness, String> withRecovery =
          path.handleError(RuntimeException.class, e -> FreePath.pure("recovered", identityMonad));

      FreeAssert.assertThatFree(withRecovery.toFree()).isHandleError();
    }

    @Test
    @DisplayName("handleError on successful value passes through")
    void handleErrorOnSuccessPassesThrough() {
      FreePath<IdentityKind.Witness, String> path = FreePath.pure("hello", identityMonad);

      FreePath<IdentityKind.Witness, String> withRecovery =
          path.handleError(RuntimeException.class, e -> FreePath.pure("recovered", identityMonad));

      Kind<IdentityKind.Witness, String> result =
          withRecovery.toFree().foldMap(Natural.identity(), identityMonad);
      assertThat(IDENTITY.<String>narrow(result).value()).isEqualTo("hello");
    }

    @Test
    @DisplayName("handleError rejects null errorType")
    void handleErrorRejectsNullErrorType() {
      FreePath<IdentityKind.Witness, String> path = FreePath.pure("hello", identityMonad);

      assertThatThrownBy(
              () -> path.handleError(null, e -> FreePath.pure("recovered", identityMonad)))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("handleError rejects null handler")
    void handleErrorRejectsNullHandler() {
      FreePath<IdentityKind.Witness, String> path = FreePath.pure("hello", identityMonad);

      assertThatThrownBy(() -> path.handleError(RuntimeException.class, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("recover")
  class RecoverTests {

    @Test
    @DisplayName("recover wraps underlying Free with HandleError<Throwable>")
    void recoverWrapsWithHandleError() {
      FreePath<IdentityKind.Witness, String> path = FreePath.pure("hello", identityMonad);

      FreePath<IdentityKind.Witness, String> withRecovery = path.recover(e -> "fallback");

      FreeAssert.assertThatFree(withRecovery.toFree()).isHandleError();
    }

    @Test
    @DisplayName("recover on successful value passes through")
    void recoverOnSuccessPassesThrough() {
      FreePath<IdentityKind.Witness, String> path = FreePath.pure("hello", identityMonad);

      FreePath<IdentityKind.Witness, String> withRecovery = path.recover(e -> "fallback");

      Kind<IdentityKind.Witness, String> result =
          withRecovery.toFree().foldMap(Natural.identity(), identityMonad);
      assertThat(IDENTITY.<String>narrow(result).value()).isEqualTo("hello");
    }
  }

  @Nested
  @DisplayName("orElse")
  class OrElseTests {

    @Test
    @DisplayName("orElse wraps underlying Free with HandleError")
    void orElseWrapsWithHandleError() {
      FreePath<IdentityKind.Witness, String> path = FreePath.pure("hello", identityMonad);

      FreePath<IdentityKind.Witness, String> withFallback =
          path.orElse(() -> FreePath.pure("fallback", identityMonad));

      FreeAssert.assertThatFree(withFallback.toFree()).isHandleError();
    }

    @Test
    @DisplayName("orElse on successful value passes through")
    void orElseOnSuccessPassesThrough() {
      FreePath<IdentityKind.Witness, String> path = FreePath.pure("hello", identityMonad);

      FreePath<IdentityKind.Witness, String> withFallback =
          path.orElse(() -> FreePath.pure("fallback", identityMonad));

      Kind<IdentityKind.Witness, String> result =
          withFallback.toFree().foldMap(Natural.identity(), identityMonad);
      assertThat(IDENTITY.<String>narrow(result).value()).isEqualTo("hello");
    }

    @Test
    @DisplayName("orElse rejects null supplier")
    void orElseRejectsNull() {
      FreePath<IdentityKind.Witness, String> path = FreePath.pure("hello", identityMonad);

      assertThatThrownBy(() -> path.orElse(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Actual error recovery via MonadError (Try)")
  class MonadErrorRecovery {

    private final TryMonad tryMonad = TryMonad.INSTANCE;

    /** Interpreter that always fails. */
    private final Natural<IdentityKind.Witness, TryKind.Witness> failingInterp =
        new Natural<>() {
          @Override
          public <A> Kind<TryKind.Witness, A> apply(Kind<IdentityKind.Witness, A> fa) {
            return tryMonad.raiseError(new RuntimeException("service down"));
          }
        };

    @Test
    @DisplayName("handleError recovers from actual error via FreePath")
    void handleErrorRecoversFromActualError() {
      FreePath<IdentityKind.Witness, String> path =
          FreePath.liftF(IDENTITY.widen(new Identity<>("request")), identityMonad);

      FreePath<IdentityKind.Witness, String> withRecovery =
          path.handleError(
              Throwable.class, e -> FreePath.pure("fallback: " + e.getMessage(), identityMonad));

      Kind<TryKind.Witness, String> result = withRecovery.toFree().foldMap(failingInterp, tryMonad);

      Try<String> tryResult = TRY.narrow(result);
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse(null)).isEqualTo("fallback: service down");
    }

    @Test
    @DisplayName("recover maps error to pure value via FreePath")
    void recoverMapsErrorToPureValue() {
      FreePath<IdentityKind.Witness, String> path =
          FreePath.liftF(IDENTITY.widen(new Identity<>("request")), identityMonad);

      FreePath<IdentityKind.Witness, String> withRecovery =
          path.recover(e -> "default: " + e.getMessage());

      Kind<TryKind.Witness, String> result = withRecovery.toFree().foldMap(failingInterp, tryMonad);

      Try<String> tryResult = TRY.narrow(result);
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse(null)).isEqualTo("default: service down");
    }

    @Test
    @DisplayName("orElse uses fallback program on error via FreePath")
    void orElseUsesFallbackOnError() {
      FreePath<IdentityKind.Witness, String> path =
          FreePath.liftF(IDENTITY.widen(new Identity<>("request")), identityMonad);

      FreePath<IdentityKind.Witness, String> withFallback =
          path.orElse(() -> FreePath.pure("fallback value", identityMonad));

      Kind<TryKind.Witness, String> result = withFallback.toFree().foldMap(failingInterp, tryMonad);

      Try<String> tryResult = TRY.narrow(result);
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse(null)).isEqualTo("fallback value");
    }

    @Test
    @DisplayName("recover rejects null handler")
    void recoverRejectsNullHandler() {
      FreePath<IdentityKind.Witness, String> path = FreePath.pure("hello", identityMonad);

      assertThatThrownBy(() -> path.recover(null)).isInstanceOf(NullPointerException.class);
    }
  }
}
