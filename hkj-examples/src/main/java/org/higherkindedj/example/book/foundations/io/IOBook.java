// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.io;

import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.time.TimeSource;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/io_monad.html">IO</a> page: a consignment
 * stamped with the time from a {@code TimeSource}, a program that dispatches and records it, run
 * twice to show that a run is not cached, a failure that propagates until an {@code IOPath} catches
 * it, and the instance over {@code Kind}. The page {@code {{#include}}}s the anchored regions,
 * {@code IOBookTest} holds the claims the page makes, and the book's output gate runs {@link #main}
 * against the comments in the regions.
 */
public final class IOBook {

  /** The instant the fixed time source reads. */
  static final Instant NINE_AM = Instant.parse("2026-10-31T09:00:00Z");

  /** An order that is ready to ship. */
  static final Order ORDER =
      new Order(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          new Customer("Ada", new EmailAddress("ada@example.com")),
          List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))),
          Instant.parse("2026-10-30T15:00:00Z"),
          Currency.getInstance("GBP"),
          OrderStatus.PAID);

  /** Where the order is going. */
  static final Address LEEDS = new Address("1 High Street", "Leeds", "LS1 4AP");

  private IOBook() {}

  // ANCHOR: dispatch
  // The time is read when the IO runs, not when dispatch is called
  static IO<Consignment> dispatch(TimeSource time, Order order, Address to) {
    return time.now()
        .map(now -> new Consignment(order.id(), to, new ConsignmentState.Dispatched(now)));
  }

  // ANCHOR_END: dispatch

  // ANCHOR: program
  static IO<Consignment> record(List<Consignment> log, Consignment consignment) {
    return IO.delay(
        () -> {
          log.add(consignment);
          return consignment;
        });
  }

  static IO<Consignment> dispatchAndRecord(
      TimeSource time, List<Consignment> log, Order order, Address to) {
    return dispatch(time, order, to).flatMap(consignment -> record(log, consignment));
  }

  // ANCHOR_END: program

  // ANCHOR: courier
  static IO<Consignment> bookCourier(Consignment consignment) {
    return IO.delay(
        () -> {
          throw new IllegalStateException("the courier is closed");
        });
  }

  static IOPath<Either<String, Consignment>> dispatchSafely(
      TimeSource time, Order order, Address to) {
    return Path.ioPath(dispatch(time, order, to))
        .via(consignment -> Path.ioPath(bookCourier(consignment)))
        .catching(error -> "booking failed: " + error.getMessage());
  }

  // ANCHOR_END: courier

  public static void main(String[] args) {
    // ANCHOR: dispatch_outcome
    TimeSource time = TimeSource.fixed(NINE_AM);
    IO<Consignment> described = dispatch(time, ORDER, LEEDS);

    Consignment dispatched = described.unsafeRunSync();
    // Consignment[orderId=00000000-0000-0000-0000-000000000001,
    //   to=Address[street=1 High Street, city=Leeds, postcode=LS1 4AP],
    //   state=Dispatched[at=2026-10-31T09:00:00Z]]
    // ANCHOR_END: dispatch_outcome
    System.out.println(dispatched);

    // ANCHOR: runs
    List<Consignment> log = new ArrayList<>();
    IO<Consignment> program = dispatchAndRecord(time, log, ORDER, LEEDS);

    int beforeRunning = log.size();
    // 0
    program.unsafeRunSync();
    program.unsafeRunSync();
    int afterTwoRuns = log.size();
    // 2
    // ANCHOR_END: runs
    System.out.println(beforeRunning);
    System.out.println(afterTwoRuns);

    // ANCHOR: path_outcome
    Either<String, Consignment> refused = dispatchSafely(time, ORDER, LEEDS).unsafeRun();
    // Left(booking failed: the courier is closed)
    // ANCHOR_END: path_outcome
    System.out.println(refused);

    // ANCHOR: kind
    Monad<IOKind.Witness> effects = Instances.monad(io());

    Kind<IOKind.Witness, Consignment> stamped = IO_OP.widen(dispatch(time, ORDER, LEEDS));
    Kind<IOKind.Witness, String> label =
        effects.map(consignment -> "to " + consignment.to().city(), stamped);

    String ran = IO_OP.unsafeRunSync(label);
    // ANCHOR_END: kind
    System.out.println(ran);
  }
}
