// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.scope;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.resilience.Retry;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.ScopeJoiner;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/vtask_scope.html">Structured
 * Concurrency</a> page: quotes from three couriers forked into one {@code Scope} and joined in four
 * ways, an order's stock checked line by line with every shortfall kept, a timeout that cancels a
 * courier that never answers, a scope run again by a retry, and a {@code ScopeJoiner} used with a
 * {@code StructuredTaskScope} opened by hand. The page {@code {{#include}}}s the anchored regions,
 * {@code ScopeBookTest} holds the claims the page makes, and the book's output gate runs {@link
 * #main} against the comments in the regions.
 */
public final class ScopeBook {

  /** An order for two lamps and a shade. */
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

  // ANCHOR: services
  // A courier quotes for an order, and a warehouse counts its stock of a product. Each call
  // blocks until its service answers, and may throw an IOException.
  interface Courier {
    BigDecimal quote(Order order) throws IOException;
  }

  interface Warehouse {
    int inStock(String sku) throws IOException;
  }

  // ANCHOR_END: services

  /** Three couriers, each quoting its own price. */
  static final List<Courier> COURIERS =
      List.of(
          order -> new BigDecimal("7.99"),
          order -> new BigDecimal("6.50"),
          order -> new BigDecimal("9.20"));

  /** Stands in for a courier whose service is down. */
  static final Courier DOWN =
      order -> {
        throw new IOException("the courier is down");
      };

  /** Stands in for a courier that never answers, until it is cancelled. */
  static final Courier SILENT =
      order -> {
        try {
          Thread.sleep(Duration.ofHours(1));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("cancelled while waiting for the courier", e);
        }
        return new BigDecimal("5.00");
      };

  /** A warehouse short of both products. */
  static final Warehouse SHORT = Map.of("LAMP", 1, "SHADE", 0)::get;

  /** A warehouse with plenty of both. */
  static final Warehouse WELL_STOCKED = sku -> 10;

  private ScopeBook() {}

  // ANCHOR: quotes
  static VTask<BigDecimal> quoteFrom(Courier courier, Order order) {
    return VTask.of(() -> courier.quote(order));
  }

  static List<VTask<BigDecimal>> quotesFrom(List<Courier> couriers, Order order) {
    return couriers.stream().map(courier -> quoteFrom(courier, order)).toList();
  }

  // Every courier's quote, in the order they were forked
  static VTask<List<BigDecimal>> allQuotes(List<Courier> couriers, Order order) {
    return Scope.<BigDecimal>allSucceed().forkAll(quotesFrom(couriers, order)).join();
  }

  // The first courier to answer with a quote
  static VTask<BigDecimal> firstQuote(List<Courier> couriers, Order order) {
    return Scope.<BigDecimal>anySucceed().forkAll(quotesFrom(couriers, order)).join();
  }

  // Whichever courier answers first, with a quote or a failure
  static VTask<BigDecimal> firstAnswer(List<Courier> couriers, Order order) {
    return Scope.<BigDecimal>firstComplete().forkAll(quotesFrom(couriers, order)).join();
  }

  // ANCHOR_END: quotes

  // ANCHOR: stock
  static VTask<LineItem> checkStock(Warehouse warehouse, LineItem line) {
    return VTask.of(
        () -> {
          int inStock = warehouse.inStock(line.sku());
          if (inStock < line.quantity()) {
            throw new IllegalStateException(
                line.sku() + ": " + line.quantity() + " ordered, " + inStock + " in stock");
          }
          return line;
        });
  }

  // Every line is checked, and every shortfall kept
  static VTask<Validated<List<String>, List<LineItem>>> checkLines(
      Warehouse warehouse, Order order) {
    return Scope.<String, LineItem>accumulating(Throwable::getMessage)
        .forkAll(order.lines().stream().map(line -> checkStock(warehouse, line)).toList())
        .join();
  }

  // ANCHOR_END: stock

  // ANCHOR: by_hand
  static List<BigDecimal> twoQuotesByHand(
      ScopeJoiner<BigDecimal, List<BigDecimal>> policy, Order order) throws InterruptedException {
    try (var scope = StructuredTaskScope.open(policy.joiner())) {
      scope.fork(() -> COURIERS.get(0).quote(order));
      scope.fork(() -> COURIERS.get(1).quote(order));
      return scope.join();
    }
  }

  // ANCHOR_END: by_hand

  public static void main(String[] args) throws InterruptedException {
    // ANCHOR: quote_outcomes
    List<BigDecimal> quotes = allQuotes(COURIERS, ORDER).run();
    // [7.99, 6.50, 9.20]
    BigDecimal answered = firstQuote(List.of(DOWN, COURIERS.get(1), DOWN), ORDER).run();
    // 6.50
    // ANCHOR_END: quote_outcomes
    System.out.println(quotes);
    System.out.println(answered);

    // ANCHOR: stock_outcome
    Validated<List<String>, List<LineItem>> shortfalls = checkLines(SHORT, ORDER).run();
    // Invalid([LAMP: 2 ordered, 1 in stock, SHADE: 1 ordered, 0 in stock])
    boolean allInStock = checkLines(WELL_STOCKED, ORDER).run().isValid();
    // true
    // ANCHOR_END: stock_outcome
    System.out.println(shortfalls);
    System.out.println(allInStock);

    // ANCHOR: timeout
    Try<List<BigDecimal>> late =
        Scope.<BigDecimal>allSucceed()
            .timeout(Duration.ofMillis(50))
            .fork(quoteFrom(SILENT, ORDER))
            .joinSafe()
            .run();
    // Failure(java.util.concurrent.TimeoutException: Scope timed out after PT0.05S)
    // ANCHOR_END: timeout
    System.out.println(late);

    // ANCHOR: safe
    Either<Throwable, List<BigDecimal>> refused =
        Scope.<BigDecimal>allSucceed().fork(quoteFrom(DOWN, ORDER)).joinEither().run();
    // Left(java.io.IOException: the courier is down)
    // ANCHOR_END: safe
    System.out.println(refused);

    // ANCHOR: run_again
    AtomicInteger quoteCalls = new AtomicInteger();
    VTask<String> quote =
        VTask.of(
            () -> {
              if (quoteCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("quote service busy");
              }
              return "quote";
            });
    VTask<List<String>> prices =
        Scope.<String>allSucceed().fork(quote).fork(VTask.succeed("rates")).join();

    List<String> retried =
        Retry.retryTask(prices, RetryPolicy.fixed(3, Duration.ofMillis(100))).run();
    // [quote, rates]
    List<String> runAgain = prices.run();
    // [quote, rates]
    // ANCHOR_END: run_again
    System.out.println(retried);
    System.out.println(runAgain);

    // ANCHOR: joiner
    ScopeJoiner<BigDecimal, List<BigDecimal>> everyQuote = ScopeJoiner.allSucceed();

    // The same policy for a Scope, and for the StructuredTaskScope opened by hand
    List<BigDecimal> viaScope =
        Scope.withJoiner(everyQuote).forkAll(quotesFrom(COURIERS, ORDER)).join().run();
    // [7.99, 6.50, 9.20]
    List<BigDecimal> byHand = twoQuotesByHand(everyQuote, ORDER);
    // [7.99, 6.50]
    // ANCHOR_END: joiner
    System.out.println(viaScope);
    System.out.println(byHand);
  }
}
