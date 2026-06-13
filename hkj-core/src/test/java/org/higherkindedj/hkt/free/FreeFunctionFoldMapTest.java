// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.free.test.IdentityKindHelper.IDENTITY;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.test.Identity;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityMonad;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the function-based {@code foldMap(Function, Monad)} overload with HandleError and Ap.
 * Since the function overload now adapts to a {@code Natural} and runs through the single
 * interpreter, these exercise that adapter path; the direct Natural path is covered in
 * FreeHandleErrorTest and FreeApTest.
 */
@DisplayName("Free function-based foldMap: HandleError and Ap coverage")
class FreeFunctionFoldMapTest {

  private final IdentityMonad identityMonad = IdentityMonad.INSTANCE;
  private final MonadError<TryKind.Witness, Throwable> tryMonad = Instances.monadError(try_());

  /** Function-based identity transform. */
  private final Function<Kind<IdentityKind.Witness, ?>, Kind<IdentityKind.Witness, ?>>
      identityTransform = kind -> kind;

  @Nested
  @DisplayName("HandleError via function-based foldMap")
  class HandleErrorFunction {

    @Test
    @DisplayName("HandleError ignored for non-MonadError via function foldMap")
    void handleErrorIgnoredViaFunction() {
      Free<IdentityKind.Witness, Integer> program =
          Free.<IdentityKind.Witness, Integer>pure(42)
              .handleError(Throwable.class, _ -> Free.pure(-1));

      Kind<IdentityKind.Witness, Integer> result =
          program.foldMap(identityTransform, identityMonad);

      assertThat(IDENTITY.narrow(result).value()).isEqualTo(42);
    }

    @Test
    @DisplayName("HandleError recovers via function-based foldMap with MonadError")
    void handleErrorRecoversViaFunction() {
      Function<Kind<IdentityKind.Witness, ?>, Kind<TryKind.Witness, ?>> failingTransform =
          _ -> tryMonad.raiseError(new RuntimeException("boom"));

      Free<IdentityKind.Witness, String> program =
          Free.liftF(IDENTITY.widen(new Identity<>("will-fail")), identityMonad)
              .handleError(Throwable.class, e -> Free.pure("recovered: " + e.getMessage()));

      Kind<TryKind.Witness, String> result = program.foldMap(failingTransform, tryMonad);

      Try<String> tryResult = TRY.narrow(result);
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse(null)).isEqualTo("recovered: boom");
    }

    @Test
    @DisplayName("HandleError re-raises on type mismatch via function foldMap")
    void handleErrorReRaisesViaFunction() {
      Function<Kind<IdentityKind.Witness, ?>, Kind<TryKind.Witness, ?>> failingTransform =
          _ -> tryMonad.raiseError(new RuntimeException("boom"));

      Free<IdentityKind.Witness, String> program =
          Free.liftF(IDENTITY.widen(new Identity<>("will-fail")), identityMonad)
              .handleError(IllegalArgumentException.class, _ -> Free.pure("should not reach"));

      Kind<TryKind.Witness, String> result = program.foldMap(failingTransform, tryMonad);

      Try<String> tryResult = TRY.narrow(result);
      assertThat(tryResult.isFailure()).isTrue();
    }
  }

  @Nested
  @DisplayName("Ap via function-based foldMap")
  class ApFunction {

    @Test
    @DisplayName("Ap with map2 via function-based foldMap")
    void apMap2ViaFunction() {
      FreeAp<IdentityKind.Witness, Integer> left = FreeAp.lift(IDENTITY.widen(new Identity<>(10)));
      FreeAp<IdentityKind.Witness, Integer> right = FreeAp.lift(IDENTITY.widen(new Identity<>(20)));
      FreeAp<IdentityKind.Witness, Integer> combined = left.map2(right, Integer::sum);

      Free<IdentityKind.Witness, Integer> program = new Free.Ap<>(combined);

      Kind<IdentityKind.Witness, Integer> result =
          program.foldMap(identityTransform, identityMonad);

      assertThat(IDENTITY.narrow(result).value()).isEqualTo(30);
    }
  }

  @Nested
  @DisplayName("HandleError translate with actual handler invocation")
  class HandleErrorTranslate {

    @Test
    @DisplayName("Translated HandleError handler executes correctly during foldMap")
    void translatedHandlerExecutesDuringFoldMap() {
      // Build program with HandleError
      Free<IdentityKind.Witness, String> program =
          Free.liftF(IDENTITY.widen(new Identity<>("will-fail")), identityMonad)
              .handleError(Throwable.class, e -> Free.pure("recovered: " + e.getMessage()));

      // Translate (identity) — this exercises translateTrampoline HandleError case
      Free<IdentityKind.Witness, String> translated =
          Free.translate(program, Natural.identity(), identityMonad);

      // Now interpret with a failing interpreter — exercises the translated handler
      Natural<IdentityKind.Witness, TryKind.Witness> failingInterp =
          new Natural<>() {
            @Override
            public <A> Kind<TryKind.Witness, A> apply(Kind<IdentityKind.Witness, A> fa) {
              return tryMonad.raiseError(new RuntimeException("translated boom"));
            }
          };

      Kind<TryKind.Witness, String> result = translated.foldMap(failingInterp, tryMonad);

      Try<String> tryResult = TRY.narrow(result);
      assertThat(tryResult.isSuccess()).isTrue();
      assertThat(tryResult.orElse(null)).isEqualTo("recovered: translated boom");
    }
  }

  @Nested
  @DisplayName("foldMap argument validation")
  class ArgumentValidation {

    private final Free<IdentityKind.Witness, Integer> program = Free.pure(42);

    @Test
    @DisplayName("foldMap(Function) rejects a null transform")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void functionFoldMapRejectsNullTransform() {
      Function<Kind<IdentityKind.Witness, ?>, Kind<IdentityKind.Witness, ?>> nullTransform = null;
      assertThatThrownBy(() -> program.foldMap(nullTransform, identityMonad))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("foldMap(Function) rejects a null monad")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void functionFoldMapRejectsNullMonad() {
      assertThatThrownBy(() -> program.foldMap(identityTransform, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("foldMap(Natural) rejects a null transform")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void naturalFoldMapRejectsNullTransform() {
      Natural<IdentityKind.Witness, IdentityKind.Witness> nullTransform = null;
      assertThatThrownBy(() -> program.foldMap(nullTransform, identityMonad))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("foldMap(Natural) rejects a null monad")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void naturalFoldMapRejectsNullMonad() {
      Natural<IdentityKind.Witness, IdentityKind.Witness> nat = Natural.identity();
      assertThatThrownBy(() -> program.foldMap(nat, null)).isInstanceOf(NullPointerException.class);
    }
  }
}
