// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.resource;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.Resource;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/vtask_resource.html">Resource
 * Management</a> page: a booking session with a courier, opened before the booking and closed after
 * it however the booking ends; two sessions held at once and closed in reverse; one {@code
 * Resource} used twice, nested; finalisers; a rollback that runs only on a failure; and a session
 * held across a {@code Scope}. The page {@code {{#include}}}s the anchored regions, {@code
 * ResourceBookTest} holds the claims the page makes, and the book's output gate runs {@link #main}
 * against the comments in the regions.
 */
public final class ResourceBook {

  /** An order worth 92.50 in pounds. */
  static final Order ORDER =
      new Order(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          new Customer("Ada", new EmailAddress("ada@example.com")),
          List.of(
              new LineItem("LAMP", 2, new BigDecimal("40.00")),
              new LineItem("SHADE", 1, new BigDecimal("12.50"))),
          Instant.parse("2026-10-30T15:00:00Z"),
          Currency.getInstance("GBP"),
          OrderStatus.PAID);

  private ResourceBook() {}

  // ANCHOR: session
  // A booking session with the courier, which logs what happens to it
  record Session(String id, List<String> log) implements AutoCloseable {
    String book(Order order) {
      log.add("book on " + id);
      return "booked on " + id;
    }

    @Override
    public void close() {
      log.add("close " + id);
    }
  }

  // Each use opens a session of its own
  static Resource<Session> sessions(List<String> log) {
    AtomicInteger opened = new AtomicInteger();
    return Resource.fromAutoCloseable(
        () -> {
          Session session = new Session("session-" + opened.incrementAndGet(), log);
          log.add("open " + session.id());
          return session;
        });
  }

  // ANCHOR_END: session

  public static void main(String[] args) {
    // ANCHOR: use
    List<String> log = new CopyOnWriteArrayList<>();
    Resource<Session> session = sessions(log);
    VTask<String> booking = session.use(s -> VTask.of(() -> s.book(ORDER)));

    int openedBeforeRunning = log.size();
    // 0
    booking.run();
    List<String> whatRan = List.copyOf(log);
    // [open session-1, book on session-1, close session-1]
    // ANCHOR_END: use
    System.out.println(openedBeforeRunning);
    System.out.println(whatRan);

    // ANCHOR: failure
    log.clear();
    Try<String> refused =
        session.use(s -> VTask.<String>fail(new IOException("the courier refused"))).runSafe();
    // Failure(java.io.IOException: the courier refused)
    List<String> closedAnyway = List.copyOf(log);
    // [open session-2, close session-2]
    // ANCHOR_END: failure
    System.out.println(refused);
    System.out.println(closedAnyway);

    // ANCHOR: compose
    log.clear();
    Resource<Par.Tuple2<Session, Session>> twoSessions = session.and(session);

    twoSessions.useSync(both -> both.first().book(ORDER) + ", " + both.second().book(ORDER)).run();
    List<String> inReverse = List.copyOf(log);
    // [open session-3, open session-4, book on session-3, book on session-4, close session-4,
    //   close session-3]

    // A shipping label printed on a session, voided after use
    log.clear();
    Resource<String> label =
        session.flatMap(s -> Resource.make(() -> s.id() + "-label", l -> log.add("void " + l)));

    label.useSync(String::length).run();
    List<String> labelFirst = List.copyOf(log);
    // [open session-5, void session-5-label, close session-5]
    // ANCHOR_END: compose
    System.out.println(inReverse);
    System.out.println(labelFirst);

    // ANCHOR: many_uses
    log.clear();
    Resource<String> sessionIds = session.map(Session::id);

    List<String> both =
        sessionIds
            .use(outer -> sessionIds.use(inner -> VTask.succeed(List.of(outer, inner))))
            .run();
    // [session-6, session-7]
    List<String> innermostFirst = List.copyOf(log);
    // [open session-6, open session-7, close session-7, close session-6]
    // ANCHOR_END: many_uses
    System.out.println(both);
    System.out.println(innermostFirst);

    // ANCHOR: finalisers
    List<String> steps = new ArrayList<>();
    Resource<String> handle =
        Resource.make(() -> "handle", _ -> steps.add("release"))
            .withFinalizer(() -> steps.add("log the release"))
            .withFinalizer(() -> steps.add("record metrics"));

    handle.useSync(String::length).run();
    List<String> ranInOrder = List.copyOf(steps);
    // [release, log the release, record metrics]
    // ANCHOR_END: finalisers
    System.out.println(ranInOrder);

    // ANCHOR: on_failure
    List<String> ledger = new ArrayList<>();
    AtomicInteger begun = new AtomicInteger();
    Resource<String> transaction =
        Resource.make(() -> "tx-" + begun.incrementAndGet(), tx -> ledger.add("close " + tx))
            .onFailure(tx -> ledger.add("rollback " + tx));

    transaction.useSync(String::length).run();
    transaction.use(_ -> VTask.fail(new IllegalStateException("insert failed"))).runSafe();
    List<String> whatRanEachTime = List.copyOf(ledger);
    // [close tx-1, rollback tx-2, close tx-2]
    // ANCHOR_END: on_failure
    System.out.println(whatRanEachTime);

    // ANCHOR: with_scope
    log.clear();
    VTask<List<String>> bookTwice =
        session.use(
            s ->
                Scope.<String>allSucceed()
                    .fork(VTask.of(() -> s.book(ORDER)))
                    .fork(VTask.of(() -> s.book(ORDER)))
                    .join());

    List<String> bookings = bookTwice.run();
    // [booked on session-8, booked on session-8]
    List<String> closedAfterBoth = List.copyOf(log);
    // [open session-8, book on session-8, book on session-8, close session-8]
    // ANCHOR_END: with_scope
    System.out.println(bookings);
    System.out.println(closedAfterBoth);
  }
}
