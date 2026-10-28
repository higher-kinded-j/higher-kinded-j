// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.monaderror;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.monaderror.MonadErrorBook.ARCHIVED;
import static org.higherkindedj.example.book.foundations.monaderror.MonadErrorBook.MISSING;
import static org.higherkindedj.example.book.foundations.monaderror.MonadErrorBook.ORDER;
import static org.higherkindedj.example.book.foundations.monaderror.MonadErrorBook.SHIPPED;
import static org.higherkindedj.example.book.foundations.monaderror.MonadErrorBook.refuseShipped;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.instances.Witnesses.vtask;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.maybe_t.MaybeTKindHelper.MAYBE_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.example.book.foundations.monaderror.MonadErrorBook.Store;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe_t.MaybeT;
import org.higherkindedj.hkt.maybe_t.MaybeTKind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional_t.OptionalT;
import org.higherkindedj.hkt.optional_t.OptionalTKind;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the MonadError page makes about what each instance's error is. */
@DisplayName("MonadError: the instance decides what an error is")
class MonadErrorBookTest {

  private static final MonadError<EitherKind.Witness<String>, String> EITHER_ERRORS =
      Instances.monadError(either());
  private static final MonadError<TryKind.Witness, Throwable> TRY_ERRORS =
      Instances.monadError(try_());
  private static final MonadError<MaybeKind.Witness, Unit> MAYBE_ERRORS =
      Instances.monadError(maybe());
  private static final MonadError<OptionalKind.Witness, Unit> OPTIONAL_ERRORS =
      Instances.monadError(optional());
  private static final MonadError<ValidatedKind.Witness<List<String>>, List<String>>
      VALIDATED_ERRORS = Instances.validated(Semigroups.list());
  private static final MonadError<CompletableFutureKind.Witness, Throwable> FUTURE_ERRORS =
      Instances.monadError(completableFuture());
  private static final MonadError<VTaskKind.Witness, Throwable> VTASK_ERRORS =
      Instances.monadError(vtask());
  private static final MonadError<EitherTKind.Witness<OptionalKind.Witness, String>, String>
      EITHER_T_ERRORS = Instances.eitherT(Instances.monad(optional()));
  private static final MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Unit> MAYBE_T_ERRORS =
      Instances.maybeT(Instances.monad(optional()));
  private static final MonadError<OptionalTKind.Witness<OptionalKind.Witness>, Unit>
      OPTIONAL_T_ERRORS = Instances.optionalT(Instances.monad(optional()));

  @Test
  @DisplayName("refuseShipped passes an unshipped order on, and raises the error for a shipped one")
  void refuseShippedRaisesOnlyForAShippedOrder() {
    assertThat(
            EITHER.narrow(
                refuseShipped(
                    EITHER_ERRORS, EITHER.widen(Either.right(ORDER)), "order has shipped")))
        .isEqualTo(Either.right(ORDER));
    assertThat(
            EITHER.narrow(
                refuseShipped(
                    EITHER_ERRORS, EITHER.widen(Either.right(SHIPPED)), "order has shipped")))
        .isEqualTo(Either.left("order has shipped"));
  }

  @Test
  @DisplayName("Either raises a Left, and runs the handler on it")
  void eitherRaisesALeftAndHandlesIt() {
    List<String> seen = new ArrayList<>();
    Kind<EitherKind.Witness<String>, Order> raised = EITHER_ERRORS.raiseError("order not found");
    Kind<EitherKind.Witness<String>, Order> handled =
        EITHER_ERRORS.handleErrorWith(
            raised,
            error -> {
              seen.add(error);
              return EITHER.widen(Either.right(ORDER));
            });

    assertThat(EITHER.narrow(raised)).isEqualTo(Either.left("order not found"));
    assertThat(EITHER.narrow(handled)).isEqualTo(Either.right(ORDER));
    assertThat(seen).containsExactly("order not found");
  }

  @Test
  @DisplayName("Validated raises an Invalid, and runs the handler on it")
  void validatedRaisesAnInvalidAndHandlesIt() {
    List<String> seen = new ArrayList<>();
    Kind<ValidatedKind.Witness<List<String>>, Order> raised =
        VALIDATED_ERRORS.raiseError(List.of("order not found"));
    Kind<ValidatedKind.Witness<List<String>>, Order> handled =
        VALIDATED_ERRORS.handleErrorWith(
            raised,
            errors -> {
              seen.addAll(errors);
              return VALIDATED.widen(Validated.valid(ORDER));
            });

    assertThat(VALIDATED.narrow(raised)).isEqualTo(Validated.invalid(List.of("order not found")));
    assertThat(VALIDATED.narrow(handled)).isEqualTo(Validated.valid(ORDER));
    assertThat(seen).containsExactly("order not found");
  }

  @Test
  @DisplayName("Try raises a Failure, and an exception the handler throws becomes a Failure")
  void tryRaisesAFailureAndCatchesTheHandlersException() {
    List<Throwable> seen = new ArrayList<>();
    IllegalStateException missing = new IllegalStateException("order not found");
    IllegalStateException archiveDown = new IllegalStateException("archive unavailable");
    Kind<TryKind.Witness, Order> raised = TRY_ERRORS.raiseError(missing);
    Kind<TryKind.Witness, Order> handled =
        TRY_ERRORS.handleErrorWith(
            raised,
            error -> {
              seen.add(error);
              throw archiveDown;
            });

    assertThat(TRY.narrow(raised))
        .isInstanceOfSatisfying(
            Try.Failure.class, failure -> assertThat(failure.cause()).isSameAs(missing));
    assertThat(TRY.narrow(handled))
        .isInstanceOfSatisfying(
            Try.Failure.class, failure -> assertThat(failure.cause()).isSameAs(archiveDown));
    assertThat(seen).singleElement().isSameAs(missing);
  }

  @Test
  @DisplayName("Maybe and Optional raise an empty value, and hand the handler Unit")
  void maybeAndOptionalRaiseAnEmptyValueAndHandleWithUnit() {
    List<Unit> seen = new ArrayList<>();
    Kind<MaybeKind.Witness, Order> nothing = MAYBE_ERRORS.raiseError(Unit.INSTANCE);
    Kind<OptionalKind.Witness, Order> empty = OPTIONAL_ERRORS.raiseError(Unit.INSTANCE);
    Kind<MaybeKind.Witness, Order> just =
        MAYBE_ERRORS.handleErrorWith(
            nothing,
            unit -> {
              seen.add(unit);
              return MAYBE.widen(Maybe.just(ORDER));
            });
    Kind<OptionalKind.Witness, Order> present =
        OPTIONAL_ERRORS.handleErrorWith(
            empty,
            unit -> {
              seen.add(unit);
              return OPTIONAL.widen(Optional.of(ORDER));
            });

    assertThat(MAYBE.narrow(nothing)).isEqualTo(Maybe.nothing());
    assertThat(OPTIONAL.narrow(empty)).isEmpty();
    assertThat(MAYBE.narrow(just)).isEqualTo(Maybe.just(ORDER));
    assertThat(OPTIONAL.narrow(present)).contains(ORDER);
    assertThat(seen).containsExactly(Unit.INSTANCE, Unit.INSTANCE);
  }

  @Test
  @DisplayName(
      "CompletableFuture raises a failed future, and hands the handler the cause unwrapped")
  void completableFutureHandsTheHandlerTheUnwrappedCause() {
    List<Throwable> seen = new ArrayList<>();
    IllegalStateException declined = new IllegalStateException("card declined");
    Kind<CompletableFutureKind.Witness, Order> raised = FUTURE_ERRORS.raiseError(declined);
    // a later stage fails with a CompletionException that wraps the cause
    Kind<CompletableFutureKind.Witness, Order> later =
        FUTURE.widen(FUTURE.narrow(raised).thenApply(order -> order));
    Kind<CompletableFutureKind.Witness, Order> handled =
        FUTURE_ERRORS.handleErrorWith(
            later,
            error -> {
              seen.add(error);
              return FUTURE.widen(CompletableFuture.completedFuture(ORDER));
            });

    assertThat(FUTURE.narrow(raised)).isCompletedExceptionally();
    assertThat(FUTURE.narrow(handled).join()).isEqualTo(ORDER);
    assertThat(seen).singleElement().isSameAs(declined);
  }

  @Test
  @DisplayName("VTask raises a task that fails when run, and runs the handler only as it runs")
  void vtaskRunsTheHandlerOnlyAsItRuns() {
    List<Throwable> seen = new ArrayList<>();
    IllegalStateException declined = new IllegalStateException("card declined");
    Kind<VTaskKind.Witness, Order> raised = VTASK_ERRORS.raiseError(declined);
    Kind<VTaskKind.Witness, Order> handled =
        VTASK_ERRORS.handleErrorWith(
            raised,
            error -> {
              seen.add(error);
              return VTASK_ERRORS.of(ORDER);
            });

    assertThat(seen).isEmpty();
    assertThat(VTASK.narrow(handled).run()).isEqualTo(ORDER);
    assertThat(seen).singleElement().isSameAs(declined);
    assertThatThrownBy(() -> VTASK.narrow(raised).run()).isSameAs(declined);
  }

  @Test
  @DisplayName("Every raiseError refuses a null error")
  void everyRaiseErrorRefusesANullError() {
    assertThatThrownBy(() -> EITHER_ERRORS.raiseError(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> TRY_ERRORS.raiseError(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> MAYBE_ERRORS.raiseError(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> OPTIONAL_ERRORS.raiseError(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> VALIDATED_ERRORS.raiseError(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> FUTURE_ERRORS.raiseError(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> VTASK_ERRORS.raiseError(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("recoverWith reads its fallback first; handleErrorWith reads it only on a failure")
  void recoverWithBuildsItsFallbackFirst() {
    Store live = new Store(ORDER, "not in the live store");
    Store eagerArchive = new Store(ARCHIVED, "not in the archive");
    Store lazyArchive = new Store(ARCHIVED, "not in the archive");

    Kind<EitherKind.Witness<String>, Order> eager =
        EITHER_ERRORS.recoverWith(live.find(ORDER.id()), eagerArchive.find(ORDER.id()));
    Kind<EitherKind.Witness<String>, Order> lazyFound =
        EITHER_ERRORS.handleErrorWith(live.find(ORDER.id()), error -> lazyArchive.find(ORDER.id()));

    assertThat(EITHER.narrow(eager)).isEqualTo(Either.right(ORDER));
    assertThat(EITHER.narrow(lazyFound)).isEqualTo(Either.right(ORDER));
    assertThat(eagerArchive.reads()).isEqualTo(1);
    assertThat(lazyArchive.reads()).isZero();

    Kind<EitherKind.Witness<String>, Order> lazyMissing =
        EITHER_ERRORS.handleErrorWith(
            live.find(ARCHIVED.id()), error -> lazyArchive.find(ARCHIVED.id()));
    assertThat(EITHER.narrow(lazyMissing)).isEqualTo(Either.right(ARCHIVED));
    assertThat(lazyArchive.reads()).isEqualTo(1);
  }

  @Test
  @DisplayName("A recovery chain runs each handler only when what it wraps has failed")
  void aRecoveryChainRunsEachHandlerOnlyOnAFailure() {
    Store live = new Store(ORDER, "not in the live store");
    Store archive = new Store(ARCHIVED, "not in the archive");

    Kind<EitherKind.Witness<String>, Order> fromLive =
        EITHER_ERRORS.handleErrorWith(live.find(ORDER.id()), error -> archive.find(ORDER.id()));
    Kind<EitherKind.Witness<String>, Order> nowhere =
        EITHER_ERRORS.handleErrorWith(live.find(MISSING), error -> archive.find(MISSING));

    assertThat(EITHER.narrow(fromLive)).isEqualTo(Either.right(ORDER));
    assertThat(EITHER.narrow(nowhere)).isEqualTo(Either.left("not in the archive"));
    assertThat(archive.reads()).isEqualTo(1);
  }

  @Test
  @DisplayName("recoverWith refuses a null fallback on every instance")
  void recoverWithRefusesANullFallback() {
    assertThatThrownBy(
            () -> EITHER_ERRORS.recoverWith(EITHER_ERRORS.raiseError("order not found"), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> TRY_ERRORS.recoverWith(TRY_ERRORS.raiseError(new IllegalStateException()), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> MAYBE_ERRORS.recoverWith(MAYBE_ERRORS.raiseError(Unit.INSTANCE), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> OPTIONAL_ERRORS.recoverWith(OPTIONAL_ERRORS.raiseError(Unit.INSTANCE), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                VALIDATED_ERRORS.recoverWith(
                    VALIDATED_ERRORS.raiseError(List.of("order not found")), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                FUTURE_ERRORS.recoverWith(
                    FUTURE_ERRORS.raiseError(new IllegalStateException()), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                VTASK_ERRORS.recoverWith(
                    VTASK_ERRORS.raiseError(new IllegalStateException()), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("recover leaves a null value to of: Maybe and Optional empty, a future holds it")
  void recoverLeavesANullValueToOf() {
    assertThat(
            MAYBE.narrow(MAYBE_ERRORS.<Order>recover(MAYBE_ERRORS.raiseError(Unit.INSTANCE), null)))
        .isEqualTo(Maybe.nothing());
    assertThat(
            OPTIONAL.narrow(
                OPTIONAL_ERRORS.<Order>recover(OPTIONAL_ERRORS.raiseError(Unit.INSTANCE), null)))
        .isEmpty();
    assertThat(
            FUTURE
                .narrow(
                    FUTURE_ERRORS.<Order>recover(
                        FUTURE_ERRORS.raiseError(new IllegalStateException()), null))
                .join())
        .isNull();
  }

  @Test
  @DisplayName("recover refuses a null value on Either, Try and VTask, and on an invalid Validated")
  void recoverRefusesANullValueWhereASuccessHoldsOne() {
    assertThatThrownBy(
            () -> EITHER_ERRORS.<Order>recover(EITHER_ERRORS.raiseError("order not found"), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                TRY_ERRORS.<Order>recover(TRY_ERRORS.raiseError(new IllegalStateException()), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                VTASK_ERRORS.<Order>recover(
                    VTASK_ERRORS.raiseError(new IllegalStateException()), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                VALIDATED_ERRORS.<Order>recover(
                    VALIDATED_ERRORS.raiseError(List.of("order not found")), null))
        .isInstanceOf(NullPointerException.class);
    assertThat(
            VALIDATED.narrow(
                VALIDATED_ERRORS.recover(
                    VALIDATED.widen(Validated.<List<String>, Order>valid(ORDER)), null)))
        .isEqualTo(Validated.valid(ORDER));
  }

  @Test
  @DisplayName(
      "handleErrorWith leaves a success alone, and never runs the handler, on each instance")
  void handleErrorWithLeavesASuccessAlone() {
    List<String> handled = new ArrayList<>();

    assertThat(
            EITHER.narrow(
                EITHER_ERRORS.handleErrorWith(
                    EITHER_ERRORS.of(ORDER), error -> handle(handled, EITHER_ERRORS))))
        .isEqualTo(Either.right(ORDER));
    assertThat(
            VALIDATED.narrow(
                VALIDATED_ERRORS.handleErrorWith(
                    VALIDATED_ERRORS.of(ORDER), errors -> handle(handled, VALIDATED_ERRORS))))
        .isEqualTo(Validated.valid(ORDER));
    assertThat(
            TRY.narrow(
                TRY_ERRORS.handleErrorWith(
                    TRY_ERRORS.of(ORDER), error -> handle(handled, TRY_ERRORS))))
        .isEqualTo(Try.success(ORDER));
    assertThat(
            MAYBE.narrow(
                MAYBE_ERRORS.handleErrorWith(
                    MAYBE_ERRORS.of(ORDER), unit -> handle(handled, MAYBE_ERRORS))))
        .isEqualTo(Maybe.just(ORDER));
    assertThat(
            OPTIONAL.narrow(
                OPTIONAL_ERRORS.handleErrorWith(
                    OPTIONAL_ERRORS.of(ORDER), unit -> handle(handled, OPTIONAL_ERRORS))))
        .contains(ORDER);
    assertThat(
            FUTURE
                .narrow(
                    FUTURE_ERRORS.handleErrorWith(
                        FUTURE_ERRORS.of(ORDER), error -> handle(handled, FUTURE_ERRORS)))
                .join())
        .isEqualTo(ORDER);
    assertThat(
            VTASK
                .narrow(
                    VTASK_ERRORS.handleErrorWith(
                        VTASK_ERRORS.of(ORDER), error -> handle(handled, VTASK_ERRORS)))
                .run())
        .isEqualTo(ORDER);
    assertThat(handled).isEmpty();
  }

  @Test
  @DisplayName("The transformers' MonadError refuses a null error and a null fallback")
  void theTransformersRefuseANullErrorAndANullFallback() {
    assertThatThrownBy(() -> EITHER_T_ERRORS.raiseError(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> MAYBE_T_ERRORS.raiseError(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> OPTIONAL_T_ERRORS.raiseError(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> EITHER_T_ERRORS.recoverWith(EITHER_T_ERRORS.raiseError("order not found"), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> MAYBE_T_ERRORS.recoverWith(MAYBE_T_ERRORS.raiseError(Unit.INSTANCE), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> OPTIONAL_T_ERRORS.recoverWith(OPTIONAL_T_ERRORS.raiseError(Unit.INSTANCE), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("recover leaves a null value to of on MaybeT and OptionalT, and EitherT refuses it")
  void theTransformersRecoverANullValueAsTheirOfDoes() {
    MaybeT<OptionalKind.Witness, Order> maybeT =
        MAYBE_T.narrow(
            MAYBE_T_ERRORS.<Order>recover(MAYBE_T_ERRORS.raiseError(Unit.INSTANCE), null));
    OptionalT<OptionalKind.Witness, Order> optionalT =
        OPTIONAL_T.narrow(
            OPTIONAL_T_ERRORS.<Order>recover(OPTIONAL_T_ERRORS.raiseError(Unit.INSTANCE), null));

    assertThat(OPTIONAL.narrow(maybeT.value())).contains(Maybe.nothing());
    assertThat(OPTIONAL.narrow(optionalT.value())).contains(Optional.empty());
    assertThatThrownBy(
            () ->
                EITHER_T_ERRORS.<Order>recover(EITHER_T_ERRORS.raiseError("order not found"), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Either, Try, VTask and EitherT refuse a null recover value even on a success")
  void recoverRefusesANullValueAtTheCall() {
    assertThatThrownBy(() -> EITHER_ERRORS.<Order>recover(EITHER_ERRORS.of(ORDER), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> TRY_ERRORS.<Order>recover(TRY_ERRORS.of(ORDER), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> VTASK_ERRORS.<Order>recover(VTASK_ERRORS.of(ORDER), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> EITHER_T_ERRORS.<Order>recover(EITHER_T_ERRORS.of(ORDER), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName(
      "recover and recoverWith refuse a null source, and recoverWith a null fallback early")
  void theShortcutsRefuseANullSource() {
    assertThatThrownBy(() -> EITHER_ERRORS.recoverWith(null, EITHER_ERRORS.of(ORDER)))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> MAYBE_ERRORS.recover(null, ORDER))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> VALIDATED_ERRORS.recover(null, ORDER))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> MAYBE_ERRORS.recoverWith(MAYBE_ERRORS.of(ORDER), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> VALIDATED_ERRORS.recoverWith(VALIDATED_ERRORS.of(ORDER), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("IO has no MonadError, so the unchecked lookup fails when it casts")
  void ioHasNoMonadError() {
    assertThatThrownBy(() -> Instances.monadError(io())).isInstanceOf(ClassCastException.class);
  }

  private static <F extends WitnessArity<TypeArity.Unary>> Kind<F, Order> handle(
      List<String> handled, MonadError<F, ?> monadError) {
    handled.add("handled");
    return monadError.of(ORDER);
  }
}
