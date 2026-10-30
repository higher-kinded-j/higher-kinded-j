// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.foroptics;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentLenses;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.example.book.optics.cast.OrderTraversals;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForIndexed;
import org.higherkindedj.hkt.expression.ForTraversal;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/for_optics.html">For with Optics</a>
 * page: a chain that reads a consignment's state with {@code focus} and keeps the dispatched ones
 * with {@code match}, an email seen through an {@code Iso}, and an order's lines edited with {@code
 * ForTraversal} and {@code ForIndexed}. The page {@code {{#include}}}s the anchored regions, {@code
 * ForOpticsBookTest} holds the claims the page makes, and the book's output gate runs {@link #main}
 * against the comments in the regions.
 */
public final class ForOpticsBook {

  private ForOpticsBook() {}

  /** An order of two lines: one lamp, and four bulbs. */
  static final Order ORDER =
      new Order(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          new Customer("Ada", new EmailAddress("ada@example.com")),
          List.of(
              new LineItem("LAMP", 1, new BigDecimal("40.00")),
              new LineItem("BULB", 4, new BigDecimal("2.50"))),
          Instant.parse("2026-10-01T09:00:00Z"),
          Currency.getInstance("GBP"),
          OrderStatus.NEW);

  /** Three consignments: one dispatched, one waiting, one sent back. */
  static final List<Consignment> CONSIGNMENTS =
      List.of(
          new Consignment(
              UUID.fromString("00000000-0000-0000-0000-000000000001"),
              new Address("1 High Street", "Leeds", "LS1 1AA"),
              new ConsignmentState.Dispatched(Instant.parse("2026-10-02T08:00:00Z"))),
          new Consignment(
              UUID.fromString("00000000-0000-0000-0000-000000000002"),
              new Address("2 Mill Lane", "York", "YO1 7HH"),
              new ConsignmentState.Pending()),
          new Consignment(
              UUID.fromString("00000000-0000-0000-0000-000000000003"),
              new Address("3 Quay Road", "Hull", "HU1 1AB"),
              new ConsignmentState.Returned("damaged")));

  // ANCHOR: email_text
  // An EmailAddress holds one String, so the two convert both ways without loss
  public static final Iso<EmailAddress, String> EMAIL_TEXT =
      Iso.of(EmailAddress::value, EmailAddress::new);

  // ANCHOR_END: email_text

  // ANCHOR: helpers
  // Prices are rounded to pence, and prices lists each line's SKU and price for printing
  static BigDecimal tenPercentOff(BigDecimal price) {
    return price.multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.HALF_UP);
  }

  static BigDecimal halfPrice(BigDecimal price) {
    return price.divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
  }

  static List<String> prices(List<LineItem> lines) {
    return lines.stream().map(line -> line.sku() + " " + line.price()).toList();
  }

  // ANCHOR_END: helpers

  public static void main(String[] args) {
    // ANCHOR: steps
    MonadZero<ListKind.Witness> listMonad = Instances.monadZero(list());

    List<String> dispatched =
        LIST.narrow(
            For.from(listMonad, LIST.widen(CONSIGNMENTS))
                .focus(ConsignmentLenses.state())
                .match(t -> ConsignmentStatePrisms.dispatched().getOptional(t._2()))
                .yield((consignment, state, sent) -> consignment.to().city() + " " + sent.at()));
    // [Leeds 2026-10-02T08:00:00Z]
    // ANCHOR_END: steps

    // ANCHOR: through
    MonadZero<MaybeKind.Witness> maybeMonad = Instances.monadZero(maybe());

    Maybe<String> domain =
        MAYBE.narrow(
            For.from(maybeMonad, MAYBE.just(ORDER.customer().email()))
                .through(EMAIL_TEXT)
                .yield((email, text) -> text.substring(text.indexOf('@') + 1)));
    // Just(example.com)
    // ANCHOR_END: through

    // ANCHOR: for_traversal
    Monad<IdKind.Witness> idMonad = Instances.monad(id());

    Order discounted =
        ID.unwrap(
            ForTraversal.over(OrderTraversals.lines(), ORDER, idMonad)
                .filter(line -> line.quantity() >= 2)
                .modify(LineItemLenses.price(), ForOpticsBook::tenPercentOff)
                .run());
    List<String> discountedPrices = prices(discounted.lines());
    // [LAMP 40.00, BULB 2.25]

    List<LineItem> bulk =
        ID.unwrap(
            ForTraversal.over(OrderTraversals.lines(), ORDER, idMonad)
                .filter(line -> line.quantity() >= 2)
                .toList());
    // [LineItem[sku=BULB, quantity=4, price=2.50]]
    // ANCHOR_END: for_traversal

    // ANCHOR: plain
    // filtered keeps the lines with a quantity of two or more, and andThen reaches each price
    Order viaOptics =
        Traversals.modify(
            OrderTraversals.lines()
                .filtered(line -> line.quantity() >= 2)
                .andThen(LineItemLenses.price()),
            ForOpticsBook::tenPercentOff,
            ORDER);
    List<String> viaOpticsPrices = prices(viaOptics.lines());
    // [LAMP 40.00, BULB 2.25]
    // ANCHOR_END: plain

    // ANCHOR: for_indexed
    List<String> slip =
        ID
            .unwrap(
                ForIndexed.overIndexed(IndexedTraversals.forList(), ORDER.lines(), idMonad)
                    .toIndexedList())
            .stream()
            .map(ForOpticsBook::slipLine)
            .toList();
    // [1. 1 x LAMP, 2. 4 x BULB]

    List<LineItem> secondHalfPrice =
        ID.unwrap(
            ForIndexed.overIndexed(IndexedTraversals.forList(), ORDER.lines(), idMonad)
                .filterIndex(index -> index % 2 == 1)
                .modify(LineItemLenses.price(), (index, price) -> halfPrice(price))
                .run());
    List<String> halfPrices = prices(secondHalfPrice);
    // [LAMP 40.00, BULB 1.25]
    // ANCHOR_END: for_indexed

    System.out.println(dispatched);
    System.out.println(domain);
    System.out.println(discountedPrices);
    System.out.println(bulk);
    System.out.println(viaOpticsPrices);
    System.out.println(slip);
    System.out.println(halfPrices);
  }

  // ANCHOR: slip_line
  // A packing slip numbers its lines from 1; the traversal's index counts from 0
  static String slipLine(Pair<Integer, LineItem> indexed) {
    LineItem line = indexed.second();
    return (indexed.first() + 1) + ". " + line.quantity() + " x " + line.sku();
  }
  // ANCHOR_END: slip_line
}
