// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.stream;

import static org.higherkindedj.hkt.instances.Witnesses.stream;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.StreamPath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.stream.StreamKind;
import org.higherkindedj.hkt.stream.StreamOps;
import org.higherkindedj.hkt.stream.StreamTraverse;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/stream_monad.html">Stream</a> page: invoice
 * numbers drawn from an endless {@code Stream}, an order's lines read through {@code flatMap},
 * numbered with {@code StreamOps}, the same on a {@code StreamPath}, and a fold over {@code Kind}.
 * The page {@code {{#include}}}s the anchored regions, {@code StreamBookTest} holds the claims the
 * page makes, and the book's output gate runs {@link #main} against the comments in the regions.
 */
public final class StreamBook {

  // ANCHOR: order_lines
  // The lines of two orders: a lamp and four bulbs, then a shade
  static final List<List<LineItem>> ORDER_LINES =
      List.of(
          List.of(
              new LineItem("LAMP", 1, new BigDecimal("40.00")),
              new LineItem("BULB", 4, new BigDecimal("2.50"))),
          List.of(new LineItem("SHADE", 1, new BigDecimal("15.00"))));

  // ANCHOR_END: order_lines

  private StreamBook() {}

  // ANCHOR: invoice_numbers
  // Invoice numbers from 1001 on, without end: none is made until the stream is read
  static Kind<StreamKind.Witness, Integer> invoiceNumbers() {
    return STREAM.widen(Stream.iterate(1001, n -> n + 1));
  }

  // ANCHOR_END: invoice_numbers

  public static void main(String[] args) {
    // ANCHOR: invoices
    MonadZero<StreamKind.Witness> streams = Instances.monadZero(stream());

    Kind<StreamKind.Witness, String> invoices = streams.map(n -> "INV-" + n, invoiceNumbers());
    List<String> firstThree = StreamOps.toList(StreamOps.take(3, invoices));
    // [INV-1001, INV-1002, INV-1003]
    // ANCHOR_END: invoices
    System.out.println(firstThree);

    // ANCHOR: lines
    Kind<StreamKind.Witness, List<LineItem>> orders = STREAM.widen(ORDER_LINES.stream());

    Kind<StreamKind.Witness, String> skus =
        streams.flatMap(lines -> STREAM.widen(lines.stream().map(LineItem::sku)), orders);
    List<String> picked = StreamOps.toList(skus);
    // [LAMP, BULB, SHADE]
    // ANCHOR_END: lines
    System.out.println(picked);

    // ANCHOR: numbered
    Kind<StreamKind.Witness, String> numbered =
        streams.map(
            pair -> (pair._1() + 1) + ". " + pair._2(),
            StreamOps.zipWithIndex(StreamOps.fromArray("LAMP", "BULB", "SHADE")));
    List<String> pickList = StreamOps.toList(numbered);
    // [1. LAMP, 2. BULB, 3. SHADE]
    // ANCHOR_END: numbered
    System.out.println(pickList);

    // ANCHOR: path
    StreamPath<String> nextInvoices =
        Path.streamIterate(1001, n -> n + 1).take(3).map(n -> "INV-" + n);

    List<String> printed = nextInvoices.toList();
    List<String> printedAgain = nextInvoices.toList();
    boolean same = printed.equals(printedAgain);
    // true
    // ANCHOR_END: path
    System.out.println(same);

    // ANCHOR: kind
    Kind<StreamKind.Witness, Integer> quantities =
        STREAM.widen(ORDER_LINES.stream().flatMap(List::stream).map(LineItem::quantity));

    int items = StreamTraverse.INSTANCE.foldMap(Monoids.integerAddition(), q -> q, quantities);
    // 6
    // ANCHOR_END: kind
    System.out.println(items);
  }
}
