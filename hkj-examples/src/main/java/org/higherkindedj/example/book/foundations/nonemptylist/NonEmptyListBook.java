// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.nonemptylist;

import static org.higherkindedj.hkt.instances.Witnesses.nonEmptyList;
import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.nonemptylist.NonEmptyListKind;
import org.higherkindedj.hkt.validated.Validated;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/nonemptylist_monad.html">NonEmptyList</a>
 * page: an error list that lets an invalid result hold no errors, then an order's lines held as a
 * {@code NonEmptyList}, totalled with {@code reduce} and searched with {@code max}, and the
 * instance over {@code Kind}. The page {@code {{#include}}}s the anchored regions, {@code
 * NonEmptyListBookTest} holds the claims the page makes, and the book's output gate runs {@link
 * #main} against the comments in the regions.
 */
public final class NonEmptyListBook {

  /** An order with two lines. */
  static final Order PLACED =
      order(
          List.of(
              new LineItem("LAMP", 2, new BigDecimal("40.00")),
              new LineItem("SHADE", 1, new BigDecimal("12.50"))));

  /** An order whose lines were all removed. */
  static final Order EMPTIED = order(List.of());

  private NonEmptyListBook() {}

  // ANCHOR: lines
  // An order with no lines gives Nothing, so the empty case is handled once, here
  static Maybe<NonEmptyList<LineItem>> lines(Order order) {
    return NonEmptyList.fromList(order.lines());
  }

  // ANCHOR_END: lines

  // ANCHOR: total
  static BigDecimal lineTotal(LineItem line) {
    return line.price().multiply(BigDecimal.valueOf(line.quantity()));
  }

  // reduce uses only the monoid's join: there is always a first line to start from
  static BigDecimal orderTotal(NonEmptyList<LineItem> lines) {
    return lines.map(NonEmptyListBook::lineTotal).reduce(Monoids.bigDecimalAddition());
  }

  static LineItem priciest(NonEmptyList<LineItem> lines) {
    return lines.max(Comparator.comparing(NonEmptyListBook::lineTotal));
  }

  // ANCHOR_END: total

  static Order order(List<LineItem> lines) {
    return new Order(
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        new Customer("Ada", new EmailAddress("ada@example.com")),
        lines,
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        OrderStatus.NEW);
  }

  public static void main(String[] args) {
    // ANCHOR: empty_errors
    // An invalid result should hold at least one error, but a List can be empty
    Validated<List<String>, Order> noReason = Validated.invalid(List.of());
    // Invalid([])
    // ANCHOR_END: empty_errors
    System.out.println(noReason);

    // ANCHOR: nel_errors
    Validated<NonEmptyList<String>, Order> refused = Validated.invalidNel("order has no lines");
    // Invalid(NonEmptyList[order has no lines])
    String firstReason = refused.getError().head();
    // ANCHOR_END: nel_errors
    System.out.println(refused);

    // ANCHOR: line_outcomes
    Maybe<NonEmptyList<LineItem>> placed = lines(PLACED);
    // Just(NonEmptyList[LineItem[sku=LAMP, quantity=2, price=40.00],
    //   LineItem[sku=SHADE, quantity=1, price=12.50]])
    Maybe<NonEmptyList<LineItem>> emptied = lines(EMPTIED);
    // Nothing
    // ANCHOR_END: line_outcomes
    System.out.println(placed);
    System.out.println(emptied);

    // ANCHOR: total_outcomes
    Maybe<BigDecimal> total = placed.map(NonEmptyListBook::orderTotal);
    // Just(92.50)
    Maybe<LineItem> first = placed.map(NonEmptyList::head);
    // Just(LineItem[sku=LAMP, quantity=2, price=40.00])
    Maybe<LineItem> priciest = placed.map(NonEmptyListBook::priciest);
    // Just(LineItem[sku=LAMP, quantity=2, price=40.00])
    // ANCHOR_END: total_outcomes
    System.out.println(total);
    System.out.println(first);
    System.out.println(priciest);

    // ANCHOR: construct
    NonEmptyList<String> skus = NonEmptyList.of("LAMP", "SHADE", "BULB");
    NonEmptyList<String> one = NonEmptyList.single("LAMP");
    NonEmptyList<String> joined = one.concat(NonEmptyList.of("SHADE")).append("BULB");
    // NonEmptyList[LAMP, SHADE, BULB]
    // ANCHOR_END: construct
    System.out.println(joined);

    // ANCHOR: kind
    Monad<NonEmptyListKind.Witness> nonEmpty = Instances.monad(nonEmptyList());

    Kind<NonEmptyListKind.Witness, Function<BigDecimal, BigDecimal>> adjustments =
        NON_EMPTY_LIST.widen(
            NonEmptyList.of(
                price -> price.add(new BigDecimal("4.99")),
                price -> price.subtract(new BigDecimal("5.00"))));
    Kind<NonEmptyListKind.Witness, BigDecimal> prices =
        NON_EMPTY_LIST.widen(NonEmptyList.of(new BigDecimal("40.00"), new BigDecimal("12.50")));

    // ap pairs every adjustment with every price, as List's does
    NonEmptyList<BigDecimal> everyPairing = NON_EMPTY_LIST.narrow(nonEmpty.ap(adjustments, prices));
    // NonEmptyList[44.99, 17.49, 35.00, 7.50]
    // ANCHOR_END: kind
    System.out.println(everyPairing);
    System.out.println(skus.size() + " " + firstReason);
  }
}
