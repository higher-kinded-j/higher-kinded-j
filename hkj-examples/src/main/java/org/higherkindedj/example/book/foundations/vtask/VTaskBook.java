// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vtask;

import static org.higherkindedj.hkt.instances.Witnesses.vtask;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/vtask_monad.html">VTask</a> page: an order
 * priced in euros from two lookups that run at the same time on a {@code VTaskPath}, with a cached
 * rate when the rate service fails; a collection refused as a typed error on a {@code VResultPath};
 * which thread runs a task; quotes from three couriers at once through {@code Par}; and the
 * instance over {@code Kind}. The page {@code {{#include}}}s the anchored regions, {@code
 * VTaskBookTest} holds the claims the page makes, and the book's output gate runs {@link #main}
 * against the comments in the regions.
 */
public final class VTaskBook {

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

  /** The same order once it has shipped. */
  static final Order SHIPPED =
      new Order(
          ORDER.id(),
          ORDER.customer(),
          ORDER.lines(),
          ORDER.placedAt(),
          ORDER.currency(),
          OrderStatus.SHIPPED);

  /** The rate to use when the rate service cannot answer. */
  static final BigDecimal CACHED_RATE = new BigDecimal("1.15");

  // ANCHOR: services
  // Two remote services: each call blocks until it answers, and may throw an IOException
  interface Rates {
    BigDecimal toEuros() throws IOException;
  }

  interface Courier {
    BigDecimal quote(Order order) throws IOException;
  }

  // ANCHOR_END: services

  /** How many quotes {@link #COURIER} has given. */
  static final AtomicInteger QUOTES = new AtomicInteger();

  /** Stands in for the rate service when it answers. */
  static final Rates LIVE_RATES = () -> new BigDecimal("1.17");

  /** Stands in for the rate service when it is down. */
  static final Rates RATES_DOWN =
      () -> {
        throw new IOException("the rate service is down");
      };

  /** Stands in for the courier, counting the quotes it gives. */
  static final Courier COURIER =
      order -> {
        QUOTES.incrementAndGet();
        return new BigDecimal("7.99");
      };

  /** Three couriers, each quoting its own price. */
  static final List<Courier> COURIERS =
      List.of(
          order -> new BigDecimal("7.99"),
          order -> new BigDecimal("6.50"),
          order -> new BigDecimal("9.20"));

  // ANCHOR: values
  // Nothing to return: give Unit.INSTANCE, as VTask.exec does
  static final VTask<Unit> LOGGED = VTask.exec(() -> System.out.println("priced " + ORDER.id()));

  // A value that may be missing: say so with a Maybe
  static final VTask<Maybe<String>> NICKNAME =
      VTask.of(() -> Maybe.fromNullable(System.getenv("NICKNAME")));

  // ANCHOR_END: values

  private VTaskBook() {}

  static BigDecimal subtotal(Order order) {
    return order.lines().stream()
        .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  static BigDecimal inEuros(Order order, BigDecimal rate, BigDecimal shipping) {
    return subtotal(order).add(shipping).multiply(rate).setScale(2, RoundingMode.HALF_UP);
  }

  // ANCHOR: path
  static VTaskPath<BigDecimal> priceInEuros(Order order, Rates rates, Courier courier) {
    VTaskPath<BigDecimal> rate = Path.vtask(rates::toEuros).handleError(error -> CACHED_RATE);
    VTaskPath<BigDecimal> shipping = Path.vtask(() -> courier.quote(order));

    // The two lookups run at the same time, each on a virtual thread
    return rate.parZipWith(shipping, (r, s) -> inEuros(order, r, s));
  }

  // ANCHOR_END: path

  // ANCHOR: vresult
  // The courier refuses a collection for an order that has already shipped
  static VResultPath<OrderError, String> bookCollection(Order order) {
    return Path.vresultDefer(
        () ->
            order.status() == OrderStatus.SHIPPED
                ? Either.left(OrderError.alreadyShipped(order.id()))
                : Either.right("collection booked for " + order.customer().name()));
  }

  static VResultPath<OrderError, String> confirmCollection(Order order) {
    return bookCollection(order)
        .via(booking -> Path.<OrderError, String>vresultRight(booking + ", confirmed"));
  }

  // ANCHOR_END: vresult

  public static void main(String[] args) {
    // ANCHOR: path_outcome
    VTaskPath<BigDecimal> price = priceInEuros(ORDER, LIVE_RATES, COURIER);
    int quotesBeforeRunning = QUOTES.get();
    // 0
    BigDecimal euros = price.unsafeRun();
    // 117.57
    BigDecimal withCachedRate = priceInEuros(ORDER, RATES_DOWN, COURIER).unsafeRun();
    // 115.56
    // ANCHOR_END: path_outcome
    System.out.println(quotesBeforeRunning);
    System.out.println(euros);
    System.out.println(withCachedRate);

    // ANCHOR: vresult_outcome
    Either<OrderError, String> booked = confirmCollection(ORDER).run().run();
    // Right(collection booked for Ada, confirmed)
    Either<OrderError, String> refused = confirmCollection(SHIPPED).run().run();
    // Left(AlreadyShipped[orderId=00000000-0000-0000-0000-000000000001])
    // ANCHOR_END: vresult_outcome
    System.out.println(booked);
    System.out.println(refused);

    // ANCHOR: threads
    VTask<Boolean> onAVirtualThread = VTask.of(() -> Thread.currentThread().isVirtual());

    boolean fromRun = onAVirtualThread.run();
    // false
    boolean fromRunAsync = onAVirtualThread.runAsync().join();
    // true
    // ANCHOR_END: threads
    System.out.println(fromRun);
    System.out.println(fromRunAsync);

    // ANCHOR: failures
    VTask<BigDecimal> rateWhileDown = VTask.of(RATES_DOWN::toEuros);

    Try<BigDecimal> held = rateWhileDown.runSafe();
    // Failure(java.io.IOException: the rate service is down)
    // ANCHOR_END: failures
    System.out.println(held);

    // ANCHOR: par
    VTask<BigDecimal> cheapest =
        Par.traverse(COURIERS, courier -> VTask.of(() -> courier.quote(ORDER)))
            .map(Collections::min);

    BigDecimal best = cheapest.run();
    // 6.50
    // ANCHOR_END: par
    System.out.println(best);

    // ANCHOR: kind
    MonadError<VTaskKind.Witness, Throwable> tasks = Instances.monadError(vtask());

    Kind<VTaskKind.Witness, BigDecimal> rate =
        tasks.handleErrorWith(
            VTASK.widen(VTask.of(RATES_DOWN::toEuros)), error -> tasks.of(CACHED_RATE));
    BigDecimal fallback = VTASK.run(rate);
    // 1.15
    // ANCHOR_END: kind
    System.out.println(fallback);
  }
}
