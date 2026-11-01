// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.lazy;

import static org.higherkindedj.hkt.instances.Witnesses.lazy;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.LAZY;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.LazyPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.lazy.LazyKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/lazy_monad.html">Lazy</a> page: an order
 * view whose shipping quote is worked out only when it is first read, then kept; the same quote on
 * a {@code LazyPath}; and the instance over {@code Kind}. The page {@code {{#include}}}s the
 * anchored regions, {@code LazyBookTest} holds the claims the page makes, and the book's output
 * gate runs {@link #main} against the comments in the regions.
 */
public final class LazyBook {

  /** An order with three items across two lines. */
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

  private LazyBook() {}

  // ANCHOR: quote
  // Working out a shipping quote is slow, and most views never show it
  static BigDecimal quoteShipping(Order order, AtomicInteger quotesWorkedOut) {
    quotesWorkedOut.incrementAndGet();
    int items = order.lines().stream().mapToInt(LineItem::quantity).sum();
    return new BigDecimal("4.99")
        .add(new BigDecimal("1.50").multiply(BigDecimal.valueOf(items - 1)));
  }

  record OrderView(Order order, Lazy<BigDecimal> shipping) {}

  static OrderView view(Order order, AtomicInteger quotesWorkedOut) {
    return new OrderView(order, Lazy.defer(() -> quoteShipping(order, quotesWorkedOut)));
  }

  // ANCHOR_END: quote

  // ANCHOR: combine
  // The total needs the quote; flatMap joins them without working either out
  static Lazy<BigDecimal> totalWithShipping(Order order, Lazy<BigDecimal> shipping) {
    Lazy<BigDecimal> subtotal =
        Lazy.defer(
            () ->
                order.lines().stream()
                    .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
    return subtotal.flatMap(sum -> shipping.map(sum::add));
  }

  // ANCHOR_END: combine

  // ANCHOR: path
  static LazyPath<String> shippingLabel(Order order, AtomicInteger quotesWorkedOut) {
    return Path.lazyDefer(() -> quoteShipping(order, quotesWorkedOut))
        .map(cost -> "shipping " + cost);
  }

  // ANCHOR_END: path

  public static void main(String[] args) throws Throwable {
    // ANCHOR: outcomes
    AtomicInteger quotes = new AtomicInteger();
    OrderView opened = view(ORDER, quotes);

    int beforeReading = quotes.get();
    // 0
    BigDecimal shipping = opened.shipping().force();
    // 7.99
    BigDecimal readAgain = opened.shipping().force();
    // 7.99
    int afterTwoReads = quotes.get();
    // 1
    // ANCHOR_END: outcomes
    System.out.println(beforeReading);
    System.out.println(shipping);
    System.out.println(readAgain);
    System.out.println(afterTwoReads);

    // ANCHOR: combine_outcome
    AtomicInteger counted = new AtomicInteger();
    Lazy<BigDecimal> total = totalWithShipping(ORDER, view(ORDER, counted).shipping());
    int beforeForcing = counted.get();
    // 0
    BigDecimal forced = total.force();
    // 100.49
    // ANCHOR_END: combine_outcome
    System.out.println(beforeForcing);
    System.out.println(forced);

    // ANCHOR: path_outcome
    LazyPath<String> label = shippingLabel(ORDER, quotes);
    boolean workedOutYet = label.isEvaluated();
    // false
    String shown = label.get();
    // ANCHOR_END: path_outcome
    System.out.println(workedOutYet);

    // ANCHOR: kind
    Monad<LazyKind.Witness> deferred = Instances.monad(lazy());

    Kind<LazyKind.Witness, BigDecimal> quoted = LAZY.widen(view(ORDER, quotes).shipping());
    Kind<LazyKind.Witness, BigDecimal> withVat =
        deferred.map(
            cost -> cost.multiply(new BigDecimal("1.20")).setScale(2, RoundingMode.HALF_UP),
            quoted);

    BigDecimal grossShipping = LAZY.force(withVat);
    // 9.59
    // ANCHOR_END: kind
    System.out.println(grossShipping);
    System.out.println(shown);
  }
}
