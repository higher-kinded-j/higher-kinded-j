// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.monaderror;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/monad_error.html">MonadError</a> page:
 * {@code refuseShipped}, one method that raises an error in any container with an error type, run
 * with {@code Either}'s, {@code Try}'s and {@code Maybe}'s instances, then recovery from a live
 * store to an archive. The page {@code {{#include}}}s the anchored regions, {@code
 * MonadErrorBookTest} holds what the page says each instance does, and the book's output gate runs
 * {@link #main} against the comments in the regions.
 */
public final class MonadErrorBook {

  /** An order the live store holds, not yet paid for. */
  static final Order ORDER = order("00000000-0000-0000-0000-000000000001", OrderStatus.NEW);

  /** An order that has shipped, so it can no longer change. */
  static final Order SHIPPED = order("00000000-0000-0000-0000-000000000002", OrderStatus.SHIPPED);

  /** An order only the archive holds. */
  static final Order ARCHIVED = order("00000000-0000-0000-0000-000000000003", OrderStatus.SHIPPED);

  /** An id no store holds. */
  static final UUID MISSING = UUID.fromString("00000000-0000-0000-0000-000000000009");

  private MonadErrorBook() {}

  // ANCHOR: generic
  // One method for any container F whose errors are E: refuse an order that has shipped
  public static <F extends WitnessArity<TypeArity.Unary>, E> Kind<F, Order> refuseShipped(
      MonadError<F, E> monadError, Kind<F, Order> found, E shipped) {
    return monadError.flatMap(
        order ->
            order.status() == OrderStatus.SHIPPED
                ? monadError.raiseError(shipped)
                : monadError.of(order),
        found);
  }

  // ANCHOR_END: generic

  /** A store that holds one order, gives an error for any other id, and counts its reads. */
  static final class Store {
    private final Order held;
    private final String missing;
    private int reads;

    Store(Order held, String missing) {
      this.held = held;
      this.missing = missing;
    }

    Kind<EitherKind.Witness<String>, Order> find(UUID id) {
      reads++;
      return EITHER.widen(held.id().equals(id) ? Either.right(held) : Either.left(missing));
    }

    int reads() {
      return reads;
    }
  }

  static Order order(String id, OrderStatus status) {
    return new Order(
        UUID.fromString(id),
        new Customer("Ada", new EmailAddress("ada@example.com")),
        List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))),
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        status);
  }

  public static void main(String[] args) {
    // ANCHOR: instances
    MonadError<EitherKind.Witness<String>, String> eitherMonadError =
        Instances.monadError(either());
    Either<String, Order> refused =
        EITHER.narrow(
            refuseShipped(
                eitherMonadError, EITHER.widen(Either.right(SHIPPED)), "order has shipped"));
    // Left(order has shipped)

    MonadError<TryKind.Witness, Throwable> tryMonadError = Instances.monadError(try_());
    Try<Order> failed =
        TRY.narrow(
            refuseShipped(
                tryMonadError,
                TRY.widen(Try.success(SHIPPED)),
                new IllegalStateException("order has shipped")));
    // Failure(java.lang.IllegalStateException: order has shipped)

    MonadError<MaybeKind.Witness, Unit> maybeMonadError = Instances.monadError(maybe());
    Maybe<Order> nothing =
        MAYBE.narrow(
            refuseShipped(maybeMonadError, MAYBE.widen(Maybe.just(SHIPPED)), Unit.INSTANCE));
    // Nothing
    // ANCHOR_END: instances

    // ANCHOR: recover
    Store live = new Store(ORDER, "not in the live store");
    Store archive = new Store(ARCHIVED, "not in the archive");

    Either<String, Order> fromArchive =
        EITHER.narrow(
            eitherMonadError.handleErrorWith(
                live.find(ARCHIVED.id()), _ -> archive.find(ARCHIVED.id())));
    // Right(Order[id=00000000-0000-0000-0000-000000000003, ...])

    Either<String, Order> nowhere =
        EITHER.narrow(
            eitherMonadError.handleErrorWith(live.find(MISSING), _ -> archive.find(MISSING)));
    // Left(not in the archive)

    Either<String, String> report =
        EITHER.narrow(
            eitherMonadError.handleError(
                eitherMonadError.map(order -> "found", live.find(MISSING)),
                error -> "no order: " + error));
    // Right(no order: not in the live store)
    // ANCHOR_END: recover

    // ANCHOR: fallbacks
    Store readFirst = new Store(ARCHIVED, "not in the archive");
    Kind<EitherKind.Witness<String>, Order> withFallback =
        eitherMonadError.recoverWith(live.find(ORDER.id()), readFirst.find(ORDER.id()));
    int archiveReadsWithRecoverWith = readFirst.reads();
    // 1

    Store readOnFailure = new Store(ARCHIVED, "not in the archive");
    Kind<EitherKind.Witness<String>, Order> withHandler =
        eitherMonadError.handleErrorWith(
            live.find(ORDER.id()), _ -> readOnFailure.find(ORDER.id()));
    int archiveReadsWithAHandler = readOnFailure.reads();
    // 0
    // ANCHOR_END: fallbacks

    System.out.println(refused);
    System.out.println(failed);
    System.out.println(nothing);
    System.out.println(fromArchive);
    System.out.println(nowhere);
    System.out.println(report);
    System.out.println(archiveReadsWithRecoverWith);
    System.out.println(archiveReadsWithAHandler);
  }
}
