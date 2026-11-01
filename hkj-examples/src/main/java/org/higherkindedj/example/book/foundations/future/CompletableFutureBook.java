// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.future;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/cf_monad.html">CompletableFuture</a> page:
 * an order priced in euros from two lookups that run at the same time, joined through {@code
 * CompletableFuture}'s instance, with a cached rate when the rate service fails, and the same on a
 * {@code CompletableFuturePath}. The page {@code {{#include}}}s the anchored regions, {@code
 * CompletableFutureBookTest} holds the claims the page makes, and the book's output gate runs
 * {@link #main} against the comments in the regions.
 */
public final class CompletableFutureBook {

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

  /** The rate to use when the rate service cannot answer. */
  static final BigDecimal CACHED_RATE = new BigDecimal("1.15");

  private CompletableFutureBook() {}

  // ANCHOR: lookups
  // Each lookup starts on another thread as soon as it is called
  static CompletableFuture<BigDecimal> rateToEuros() {
    return CompletableFuture.supplyAsync(() -> new BigDecimal("1.17"));
  }

  static CompletableFuture<BigDecimal> shippingQuote(Order order) {
    return CompletableFuture.supplyAsync(() -> new BigDecimal("7.99"));
  }

  // A lookup whose service fails
  static CompletableFuture<BigDecimal> rateServiceDown() {
    return CompletableFuture.supplyAsync(
        () -> {
          throw new IllegalStateException("the rate service is down");
        });
  }

  static BigDecimal subtotal(Order order) {
    return order.lines().stream()
        .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  static BigDecimal inEuros(Order order, BigDecimal rate, BigDecimal shipping) {
    return subtotal(order).add(shipping).multiply(rate).setScale(2, RoundingMode.HALF_UP);
  }

  // ANCHOR_END: lookups

  // ANCHOR: path
  static CompletableFuturePath<BigDecimal> priceInEuros(
      Order order, CompletableFuture<BigDecimal> rate) {
    return Path.future(rate)
        .recover(error -> CACHED_RATE)
        .parZipWith(
            Path.future(shippingQuote(order)), (r, shipping) -> inEuros(order, r, shipping));
  }

  // ANCHOR_END: path

  public static void main(String[] args) {
    // ANCHOR: kind
    MonadError<CompletableFutureKind.Witness, Throwable> futures =
        Instances.monadError(completableFuture());

    // Both lookups are already running when map2 is called
    Kind<CompletableFutureKind.Witness, BigDecimal> price =
        futures.map2(
            FUTURE.widen(rateToEuros()),
            FUTURE.widen(shippingQuote(ORDER)),
            (rate, shipping) -> inEuros(ORDER, rate, shipping));
    BigDecimal euros = FUTURE.join(price);
    // 117.57

    Kind<CompletableFutureKind.Witness, BigDecimal> rate =
        futures.handleErrorWith(
            FUTURE.widen(rateServiceDown()),
            error -> FUTURE.widen(CompletableFuture.completedFuture(CACHED_RATE)));
    BigDecimal fallback = FUTURE.join(rate);
    // 1.15
    // ANCHOR_END: kind
    System.out.println(euros);
    System.out.println(fallback);

    // ANCHOR: path_outcome
    BigDecimal priced = priceInEuros(ORDER, rateToEuros()).join();
    // 117.57
    BigDecimal withCachedRate = priceInEuros(ORDER, rateServiceDown()).join();
    // 115.56
    // ANCHOR_END: path_outcome
    System.out.println(priced);
    System.out.println(withCachedRate);
  }
}
