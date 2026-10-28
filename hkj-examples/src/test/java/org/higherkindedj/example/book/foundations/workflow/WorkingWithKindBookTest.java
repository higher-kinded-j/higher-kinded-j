// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Working with Kind page makes about its generic methods. */
@DisplayName("Working with Kind: one method over MonadError")
class WorkingWithKindBookTest {

  private static final Order PLACED =
      WorkingWithKindBook.order("00000000-0000-0000-0000-000000000001", OrderStatus.NEW);
  private static final Order SHIPPED =
      WorkingWithKindBook.order("00000000-0000-0000-0000-000000000002", OrderStatus.SHIPPED);

  private static final MonadError<MaybeKind.Witness, Unit> MAYBE_ERRORS =
      Instances.monadError(maybe());
  private static final MonadError<EitherKind.Witness<String>, String> EITHER_ERRORS =
      Instances.monadError(either());

  @Test
  @DisplayName("on a Maybe, a placed order gives its total and a shipped one gives Nothing")
  void onAMaybe() {
    Maybe<BigDecimal> placed =
        MAYBE.narrow(
            WorkingWithKindBook.totalUnlessShipped(
                MAYBE_ERRORS, MAYBE.widen(Maybe.just(PLACED)), Unit.INSTANCE));
    Maybe<BigDecimal> shipped =
        MAYBE.narrow(
            WorkingWithKindBook.totalUnlessShipped(
                MAYBE_ERRORS, MAYBE.widen(Maybe.just(SHIPPED)), Unit.INSTANCE));

    assertThat(placed.isJust()).isTrue();
    assertThat(placed.get()).isEqualByComparingTo("50.00");
    assertThat(shipped).isEqualTo(Maybe.nothing());
  }

  @Test
  @DisplayName("on an Either, the same method gives the total, or a Left carrying the error given")
  void onAnEither() {
    Either<String, BigDecimal> placed =
        EITHER.narrow(
            WorkingWithKindBook.totalUnlessShipped(
                EITHER_ERRORS, EITHER.widen(Either.right(PLACED)), "order already shipped"));
    Either<String, BigDecimal> shipped =
        EITHER.narrow(
            WorkingWithKindBook.totalUnlessShipped(
                EITHER_ERRORS, EITHER.widen(Either.right(SHIPPED)), "order already shipped"));

    assertThat(placed.isRight()).isTrue();
    assertThat(placed.getRight()).isEqualByComparingTo("50.00");
    assertThat(shipped).isEqualTo(Either.left("order already shipped"));
  }

  @Test
  @DisplayName("on a Nothing, flatMap never runs its function, and the Nothing comes back")
  void nothingSkipsTheFunction() {
    AtomicInteger calls = new AtomicInteger();
    Kind<MaybeKind.Witness, Order> none = MAYBE.widen(Maybe.nothing());

    Kind<MaybeKind.Witness, Order> result =
        MAYBE_ERRORS.flatMap(
            o -> {
              calls.incrementAndGet();
              return MAYBE_ERRORS.of(o);
            },
            none);

    assertThat(calls).hasValue(0);
    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    assertThat(
            MAYBE.narrow(WorkingWithKindBook.totalUnlessShipped(MAYBE_ERRORS, none, Unit.INSTANCE)))
        .isEqualTo(Maybe.nothing());
  }

  @Test
  @DisplayName("totalOrZero recovers a refusal through the instance, on any container")
  void recoverThroughTheInstance() {
    Maybe<BigDecimal> maybeZero =
        MAYBE.narrow(
            WorkingWithKindBook.totalOrZero(
                MAYBE_ERRORS, MAYBE.widen(Maybe.just(SHIPPED)), Unit.INSTANCE));
    Either<String, BigDecimal> eitherZero =
        EITHER.narrow(
            WorkingWithKindBook.totalOrZero(
                EITHER_ERRORS, EITHER.widen(Either.right(SHIPPED)), "order already shipped"));
    Maybe<BigDecimal> placed =
        MAYBE.narrow(
            WorkingWithKindBook.totalOrZero(
                MAYBE_ERRORS, MAYBE.widen(Maybe.just(PLACED)), Unit.INSTANCE));

    assertThat(maybeZero).isEqualTo(Maybe.just(BigDecimal.ZERO));
    assertThat(eitherZero).isEqualTo(Either.right(BigDecimal.ZERO));
    assertThat(placed.get()).isEqualByComparingTo("50.00");
  }

  @Test
  @DisplayName("widening Optional or List allocates a holder, and Maybe or Either does not")
  void wideningAllocatesOnlyForJdkTypes() {
    Optional<String> optional = Optional.of("x");
    List<String> list = List.of("x");
    Maybe<String> maybe = Maybe.just("x");
    Either<String, String> either = Either.right("x");

    assertThat((Object) OPTIONAL.widen(optional)).isNotSameAs(optional);
    assertThat((Object) LIST.widen(list)).isNotSameAs(list);
    assertThat(MAYBE.widen(maybe)).isSameAs(maybe);
    assertThat(EITHER.widen(either)).isSameAs(either);
  }
}
