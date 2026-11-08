// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vstream;

import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.FLAT_RATE;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.ORDERS;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.PATCHY_COURIER;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.customer;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.orders;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.higherkindedj.example.book.foundations.vstream.VStreamBook.Courier;
import org.higherkindedj.example.book.foundations.vstream.VStreamBook.PagedFeed;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamPar;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/vstream_parallel.html">VStream Parallel
 * Operations</a> page: a courier's quote for each paid order, asked for several orders at once, in
 * order and as each quote arrives; orders batched for a warehouse that packs several at a time; two
 * feeds merged; and a failed quote, recovered after the stream and inside the task. The page {@code
 * {{#include}}}s the anchored regions, {@code VStreamParBookTest} holds the claims the page makes,
 * and the book's output gate runs {@link #main} against the comments in the regions.
 */
public final class VStreamParBook {

  // ANCHOR: zoned
  // A courier whose quote depends on where the customer lives
  static final Map<String, BigDecimal> RATES =
      Map.of(
          "Ada", new BigDecimal("4.99"),
          "Alan", new BigDecimal("6.49"),
          "Edsger", new BigDecimal("3.99"),
          "Ken", new BigDecimal("5.49"));

  static final Courier ZONED_COURIER = order -> RATES.get(customer(order));

  // ANCHOR_END: zoned

  // ANCHOR: warehouse
  // A warehouse packs several orders in one call, which blocks until it answers
  interface Warehouse {
    List<String> pack(List<Order> orders) throws IOException;
  }

  // ANCHOR_END: warehouse

  /** Packs each order in a box labelled with its customer. */
  static final Warehouse WAREHOUSE =
      batch -> batch.stream().map(order -> "box for " + customer(order)).toList();

  private VStreamParBook() {}

  // ANCHOR: paid
  static VStream<Order> paidOrders() {
    return orders(new PagedFeed(ORDERS)).filter(VStreamBook::paid);
  }

  // ANCHOR_END: paid

  public static void main(String[] args) {
    // ANCHOR: in_order
    // At most four quotes asked for at once, and given in the orders' order
    VStream<BigDecimal> quotes =
        VStreamPar.parEvalMap(paidOrders(), 4, order -> VTask.of(() -> ZONED_COURIER.quote(order)));

    List<BigDecimal> inOrder = quotes.toList().run();
    // [4.99, 6.49, 3.99, 5.49]
    // ANCHOR_END: in_order
    System.out.println(inOrder);

    // ANCHOR: as_they_arrive
    // Each quote given as soon as the courier answers it
    VStream<String> arriving =
        VStreamPar.parEvalMapUnordered(
            paidOrders(),
            4,
            order -> VTask.of(() -> customer(order) + " " + ZONED_COURIER.quote(order)));

    List<String> asTheyArrived = arriving.toList().run();
    List<String> sorted = asTheyArrived.stream().sorted().toList();
    // [Ada 4.99, Alan 6.49, Edsger 3.99, Ken 5.49]
    // ANCHOR_END: as_they_arrive
    System.out.println(sorted);

    // ANCHOR: chunks
    List<List<String>> pairs =
        paidOrders()
            .chunk(2)
            .map(batch -> batch.map(VStreamBook::customer).toJavaList())
            .toList()
            .run();
    // [[Ada, Alan], [Edsger, Ken]]
    List<List<String>> byStatus =
        orders(new PagedFeed(ORDERS))
            .chunkWhile((earlier, later) -> earlier.status() == later.status())
            .map(batch -> batch.map(VStreamBook::customer).toJavaList())
            .toList()
            .run();
    // [[Ada], [Grace], [Alan, Edsger], [Barbara], [Ken]]
    // ANCHOR_END: chunks
    System.out.println(pairs);
    System.out.println(byStatus);

    // ANCHOR: packed
    // Two orders to a call, and two calls at once
    VStream<List<String>> boxes =
        VStreamPar.parEvalMap(
            paidOrders().chunk(2), 2, batch -> VTask.of(() -> WAREHOUSE.pack(batch.toJavaList())));

    List<List<String>> packed = boxes.toList().run();
    // [[box for Ada, box for Alan], [box for Edsger, box for Ken]]
    List<String> labels =
        paidOrders()
            .mapChunked(2, batch -> batch.map(order -> "label for " + customer(order)).toJavaList())
            .toList()
            .run();
    // [label for Ada, label for Alan, label for Edsger, label for Ken]
    // ANCHOR_END: packed
    System.out.println(packed);
    System.out.println(labels);

    // ANCHOR: merged
    VStream<String> london = orders(new PagedFeed(ORDERS.subList(0, 3))).map(VStreamBook::customer);
    VStream<String> edinburgh =
        orders(new PagedFeed(ORDERS.subList(3, 6))).map(VStreamBook::customer);

    List<String> everyCustomer = VStreamPar.merge(london, edinburgh).toList().run();
    List<String> alphabetical = everyCustomer.stream().sorted().toList();
    // [Ada, Alan, Barbara, Edsger, Grace, Ken]
    // ANCHOR_END: merged
    System.out.println(alphabetical);

    // ANCHOR: failures
    VStream<BigDecimal> patchy =
        VStreamPar.parEvalMap(
            paidOrders(), 4, order -> VTask.of(() -> PATCHY_COURIER.quote(order)));

    Try<List<BigDecimal>> held = patchy.toList().runSafe();
    // Failure(java.io.IOException: no courier serves Edsger's address)
    List<BigDecimal> recoveredAfter = patchy.recover(error -> FLAT_RATE).toList().run();
    // [9.99]
    List<BigDecimal> recoveredInside =
        VStreamPar.parEvalMap(
                paidOrders(),
                4,
                order -> VTask.of(() -> PATCHY_COURIER.quote(order)).recover(error -> FLAT_RATE))
            .toList()
            .run();
    // [4.99, 4.99, 9.99, 4.99]
    // ANCHOR_END: failures
    System.out.println(held);
    System.out.println(recoveredAfter);
    System.out.println(recoveredInside);

    // ANCHOR: path
    List<BigDecimal> viaPath =
        Path.vstream(paidOrders())
            .parEvalMap(4, order -> VTask.of(() -> ZONED_COURIER.quote(order)))
            .toList()
            .unsafeRun();
    // [4.99, 6.49, 3.99, 5.49]
    // ANCHOR_END: path
    System.out.println(viaPath);
  }
}
