// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.writer;

import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.foundations.AuditEntry;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.WriterPath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterKind;

/**
 * The audit log shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/writer_monad.html">Writer</a> page: paying
 * for and shipping an order, each step adding an entry to the order's audit log, joined by the
 * log's {@code Monoid}; the same steps on a {@code WriterPath}; and the instance over {@code Kind}.
 * The page {@code {{#include}}}s the anchored regions, {@code WriterBookTest} holds the claims the
 * page makes, and the book's output gate runs {@link #main} against the comments in the regions.
 */
public final class WriterBook {

  // ANCHOR: audit
  // The empty log is an empty list, and two logs join into one list
  static final Monoid<List<AuditEntry>> AUDIT = Monoids.list();

  // ANCHOR_END: audit

  /** An order for one lamp, not yet paid for. */
  static final Order NEW_ORDER =
      new Order(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          new Customer("Ada", new EmailAddress("ada@example.com")),
          List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))),
          Instant.parse("2026-10-30T15:00:00Z"),
          Currency.getInstance("GBP"),
          OrderStatus.NEW);

  private WriterBook() {}

  // ANCHOR: steps
  // Each step returns the order it changed, with the entry it adds to the log
  static Writer<List<AuditEntry>, Order> pay(Order order) {
    return Writer.of(
        List.of(new AuditEntry(order.id(), "paid")),
        OrderLenses.status().set(OrderStatus.PAID, order));
  }

  static Writer<List<AuditEntry>, Order> ship(Order order) {
    return Writer.of(
        List.of(new AuditEntry(order.id(), "shipped")),
        OrderLenses.status().set(OrderStatus.SHIPPED, order));
  }

  // A step with only something to log is a tell, and gives Unit
  static Writer<List<AuditEntry>, Unit> emailCustomer(Order order) {
    return Writer.tell(List.of(new AuditEntry(order.id(), "customer emailed")));
  }

  // ANCHOR_END: steps

  // ANCHOR: fulfil
  // Writer's own flatMap is given the Monoid that joins the two logs
  static Writer<List<AuditEntry>, Order> fulfil(Order order) {
    return pay(order)
        .flatMap(AUDIT, WriterBook::ship)
        .flatMap(AUDIT, shipped -> emailCustomer(shipped).map(_ -> shipped));
  }

  // ANCHOR_END: fulfil

  // ANCHOR: path
  // A WriterPath holds the Monoid, so via is given only the next step
  static WriterPath<List<AuditEntry>, Order> fulfilOnPath(Order order) {
    return Path.writer(pay(order), AUDIT)
        .via(paid -> Path.writer(ship(paid), AUDIT))
        .via(shipped -> Path.writer(emailCustomer(shipped), AUDIT).map(_ -> shipped));
  }

  // ANCHOR_END: path

  public static void main(String[] args) {
    // ANCHOR: run
    Writer<List<AuditEntry>, Order> fulfilled = fulfil(NEW_ORDER);

    Order shipped = fulfilled.run();
    List<String> events = fulfilled.exec().stream().map(AuditEntry::event).toList();
    // [paid, shipped, customer emailed]
    // ANCHOR_END: run
    System.out.println(shipped.status());
    System.out.println(events);

    // ANCHOR: path_outcome
    WriterPath<List<AuditEntry>, Order> onPath = fulfilOnPath(NEW_ORDER);

    boolean sameAsWriter = onPath.run().equals(fulfil(NEW_ORDER));
    // true
    // ANCHOR_END: path_outcome
    System.out.println(sameAsWriter);

    // ANCHOR: kind
    Monad<WriterKind.Witness<List<AuditEntry>>> audited = Instances.writer(AUDIT);

    Kind<WriterKind.Witness<List<AuditEntry>>, Order> paid = WRITER.widen(pay(NEW_ORDER));
    Kind<WriterKind.Witness<List<AuditEntry>>, Order> thenShipped =
        audited.flatMap(order -> WRITER.widen(ship(order)), paid);

    int entries = WRITER.exec(thenShipped).size();
    // 2
    // ANCHOR_END: kind
    System.out.println(entries);
  }
}
