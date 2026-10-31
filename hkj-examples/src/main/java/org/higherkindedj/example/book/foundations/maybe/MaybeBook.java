// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.maybe;

import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/maybe_monad.html">Maybe</a> page: lookups
 * in a {@code Map}, whose {@code get} gives {@code null} for a missing key, chained from an order
 * to its consignment's address; the result read with {@code orElse} and given a reason with {@code
 * toEither}; the bridges to {@code Optional} and {@code Stream}; the same chain on a {@code
 * MaybePath}; and the instance over {@code Kind}. The page {@code {{#include}}}s the anchored
 * regions, {@code MaybeBookTest} holds the claims the page makes, and the book's output gate runs
 * {@link #main} against the comments in the regions.
 */
public final class MaybeBook {

  /** An order that has been placed and not yet dispatched. */
  static final Order PLACED = order("00000000-0000-0000-0000-000000000001", OrderStatus.NEW);

  /** An order that has shipped, so a consignment carries it. */
  static final Order SHIPPED = order("00000000-0000-0000-0000-000000000002", OrderStatus.SHIPPED);

  /** An order old enough to have moved to the archive. */
  static final Order ARCHIVED = order("00000000-0000-0000-0000-000000000003", OrderStatus.SHIPPED);

  /** An id no order has. */
  static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-000000000009");

  /** The orders on record, by id. */
  static final Map<UUID, Order> ORDERS = Map.of(PLACED.id(), PLACED, SHIPPED.id(), SHIPPED);

  /** Older orders, by id. */
  static final Map<UUID, Order> ARCHIVE = Map.of(ARCHIVED.id(), ARCHIVED);

  /** The consignments carrying orders that have shipped, by order id. */
  static final Map<UUID, Consignment> CONSIGNMENTS =
      Map.of(
          SHIPPED.id(),
          new Consignment(
              SHIPPED.id(),
              new Address("1 High Street", "Leeds", "LS1 4AP"),
              new ConsignmentState.Dispatched(Instant.parse("2026-10-02T09:00:00Z"))));

  private MaybeBook() {}

  // ANCHOR: find
  // Map.get gives null for a missing key, and fromNullable turns that null into Nothing
  static Maybe<Order> findOrder(UUID id) {
    return Maybe.fromNullable(ORDERS.get(id));
  }

  static Maybe<Consignment> findConsignment(Order order) {
    return Maybe.fromNullable(CONSIGNMENTS.get(order.id()));
  }

  static Maybe<Order> findArchived(UUID id) {
    return Maybe.fromNullable(ARCHIVE.get(id));
  }

  // ANCHOR_END: find

  // ANCHOR: chain
  static Maybe<Address> deliveryAddress(UUID id) {
    return findOrder(id).flatMap(MaybeBook::findConsignment).map(Consignment::to);
  }

  // ANCHOR_END: chain

  // ANCHOR: path
  static MaybePath<String> deliveryCity(UUID id) {
    return Path.maybe(findOrder(id))
        .via(order -> Path.maybe(findConsignment(order)))
        .map(consignment -> consignment.to().city());
  }

  // ANCHOR_END: path

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
    // ANCHOR: outcomes
    Maybe<Address> dispatched = deliveryAddress(SHIPPED.id());
    // Just(Address[street=1 High Street, city=Leeds, postcode=LS1 4AP])
    Maybe<Address> notDispatched = deliveryAddress(PLACED.id());
    // Nothing
    Maybe<Address> noSuchOrder = deliveryAddress(UNKNOWN);
    // Nothing
    // ANCHOR_END: outcomes
    System.out.println(dispatched);
    System.out.println(notDispatched);
    System.out.println(noSuchOrder);

    // ANCHOR: reading
    String city = deliveryAddress(PLACED.id()).map(Address::city).orElse("not dispatched yet");

    Either<OrderError, Order> withReason =
        findOrder(UNKNOWN).toEither(OrderError.notFound(UNKNOWN));
    // Left(NotFound[orderId=00000000-0000-0000-0000-000000000009])
    // ANCHOR_END: reading
    System.out.println(withReason);

    // ANCHOR: jdk
    Optional<Address> forJdkCode = deliveryAddress(SHIPPED.id()).toOptional();
    // Optional[Address[street=1 High Street, city=Leeds, postcode=LS1 4AP]]

    // Keep only the ids that find an order
    List<UUID> found =
        Stream.of(PLACED.id(), UNKNOWN, SHIPPED.id())
            .map(MaybeBook::findOrder)
            .flatMap(Maybe::stream)
            .map(Order::id)
            .toList();
    // [00000000-0000-0000-0000-000000000001, 00000000-0000-0000-0000-000000000002]
    // ANCHOR_END: jdk
    System.out.println(forJdkCode);
    System.out.println(found);

    // ANCHOR: path_outcome
    MaybePath<String> leeds = deliveryCity(SHIPPED.id());
    // MaybePath(Just(Leeds))
    String unknownCity = deliveryCity(UNKNOWN).getOrElse("unknown");
    // ANCHOR_END: path_outcome
    System.out.println(leeds);

    // ANCHOR: kind
    MonadError<MaybeKind.Witness, Unit> absence = Instances.monadError(maybe());

    Kind<MaybeKind.Witness, Order> lookedUp = MAYBE.widen(findOrder(ARCHIVED.id()));
    Kind<MaybeKind.Witness, Order> orArchived =
        absence.handleErrorWith(lookedUp, unit -> MAYBE.widen(findArchived(ARCHIVED.id())));

    Maybe<Order> fromArchive = MAYBE.narrow(orArchived);
    // Just(Order[id=00000000-0000-0000-0000-000000000003, ...])
    Maybe<Order> raised = MAYBE.narrow(absence.raiseError(Unit.INSTANCE));
    // Nothing
    // ANCHOR_END: kind
    System.out.println(fromArchive);
    System.out.println(raised);
    System.out.println(city + " " + unknownCity);
  }
}
