// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vstream;

import static org.higherkindedj.hkt.instances.Witnesses.vstream;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.higherkindedj.example.book.foundations.OrderPage;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/vstream.html">VStream</a> page: an order
 * feed read a page at a time, each page fetched only when the stream reaches it; the first paid
 * orders taken from it; a courier's quote for each paid order, with a flat rate in place of one
 * that fails; the orders read before the feed goes down; which thread runs a pull; and the instance
 * over {@code Kind}. The page {@code {{#include}}}s the anchored regions, {@code VStreamBookTest}
 * holds the claims the page makes, and the book's output gate runs {@link #main} against the
 * comments in the regions.
 */
public final class VStreamBook {

  /** Six orders: four paid, one new and one shipped. */
  static final List<Order> ORDERS =
      List.of(
          order(1, "Ada", OrderStatus.PAID),
          order(2, "Grace", OrderStatus.NEW),
          order(3, "Alan", OrderStatus.PAID),
          order(4, "Edsger", OrderStatus.PAID),
          order(5, "Barbara", OrderStatus.SHIPPED),
          order(6, "Ken", OrderStatus.PAID));

  /** The rate to charge when a courier cannot quote. */
  static final BigDecimal FLAT_RATE = new BigDecimal("9.99");

  private static Order order(int number, String name, OrderStatus status) {
    return new Order(
        UUID.fromString("00000000-0000-0000-0000-00000000000" + number),
        new Customer(name, new EmailAddress(name.toLowerCase(Locale.ROOT) + "@example.com")),
        List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))),
        Instant.parse("2026-11-03T09:00:00Z"),
        Currency.getInstance("GBP"),
        status);
  }

  // ANCHOR: services
  // A feed serves orders a page at a time, and a courier quotes for delivering an order.
  // Each call blocks until its service answers, and may throw an IOException.
  interface OrderFeed {
    OrderPage page(int number) throws IOException;
  }

  interface Courier {
    BigDecimal quote(Order order) throws IOException;
  }

  // ANCHOR_END: services

  /**
   * A feed serving its orders two to a page, which records each page it is asked for. A page past
   * the last is empty, and {@code failingPage}, if not zero, fails.
   */
  static final class PagedFeed implements OrderFeed {
    private final List<Order> orders;
    private final int failingPage;
    private final List<Integer> fetched = new CopyOnWriteArrayList<>();

    PagedFeed(List<Order> orders) {
      this(orders, 0);
    }

    PagedFeed(List<Order> orders, int failingPage) {
      this.orders = orders;
      this.failingPage = failingPage;
    }

    @Override
    public OrderPage page(int number) throws IOException {
      fetched.add(number);
      if (number == failingPage) {
        throw new IOException("the order feed is down");
      }
      int from = Math.min((number - 1) * 2, orders.size());
      return new OrderPage(number, orders.subList(from, Math.min(from + 2, orders.size())));
    }

    /** The pages asked for so far, in order. */
    List<Integer> fetched() {
      return List.copyOf(fetched);
    }
  }

  /** Quotes 4.99 for every order. */
  static final Courier COURIER = order -> new BigDecimal("4.99");

  /** Quotes as {@link #COURIER} does, except for Edsger, whose address it does not serve. */
  static final Courier PATCHY_COURIER =
      order -> {
        if (order.customer().name().equals("Edsger")) {
          throw new IOException("no courier serves Edsger's address");
        }
        return new BigDecimal("4.99");
      };

  private VStreamBook() {}

  // ANCHOR: orders
  // Every order in the feed. A page is fetched only when the stream reaches it, and the empty
  // page past the last ends the stream
  static VStream<Order> orders(OrderFeed feed) {
    VStream<OrderPage> pages =
        VStream.unfold(
            1,
            number ->
                VTask.of(
                    () -> {
                      OrderPage page = feed.page(number);
                      return page.orders().isEmpty()
                          ? Optional.empty()
                          : Optional.of(new VStream.Seed<>(page, number + 1));
                    }));
    return pages.flatMap(page -> VStream.fromList(page.orders()));
  }

  static boolean paid(Order order) {
    return order.status() == OrderStatus.PAID;
  }

  static String customer(Order order) {
    return order.customer().name();
  }

  // ANCHOR_END: orders

  // ANCHOR: quotes
  // A quote for each paid order, asking the courier only when the stream reaches the order
  static VStream<BigDecimal> shippingQuotes(VStream<Order> orders, Courier courier) {
    return orders.filter(VStreamBook::paid).mapTask(order -> VTask.of(() -> courier.quote(order)));
  }

  // ANCHOR_END: quotes

  public static void main(String[] args) {
    // ANCHOR: first_paid
    PagedFeed feed = new PagedFeed(ORDERS);
    VStream<String> paidCustomers =
        orders(feed).filter(VStreamBook::paid).map(VStreamBook::customer);
    List<Integer> fetchedBeforeRunning = feed.fetched();
    // []
    List<String> firstTwo = paidCustomers.take(2).toList().run();
    // [Ada, Alan]
    List<Integer> fetchedForTwo = feed.fetched();
    // [1, 2]
    List<String> everyPaid = paidCustomers.toList().run();
    // [Ada, Alan, Edsger, Ken]
    List<Integer> fetchedForAll = feed.fetched();
    // [1, 2, 1, 2, 3, 4]
    // ANCHOR_END: first_paid
    System.out.println(fetchedBeforeRunning);
    System.out.println(firstTwo);
    System.out.println(fetchedForTwo);
    System.out.println(everyPaid);
    System.out.println(fetchedForAll);

    // ANCHOR: quotes_outcome
    BigDecimal shipping =
        shippingQuotes(orders(new PagedFeed(ORDERS)), COURIER)
            .fold(BigDecimal.ZERO, BigDecimal::add)
            .run();
    // 19.96
    // ANCHOR_END: quotes_outcome
    System.out.println(shipping);

    // ANCHOR: threads
    VStream<Boolean> onAVirtualThread = VStream.of(1).map(_ -> Thread.currentThread().isVirtual());

    List<Boolean> fromRun = onAVirtualThread.toList().run();
    // [false]
    List<Boolean> fromRunAsync = onAVirtualThread.runAsync().join();
    // [true]
    // ANCHOR_END: threads
    System.out.println(fromRun);
    System.out.println(fromRunAsync);

    // ANCHOR: failures
    VStream<BigDecimal> patchy = shippingQuotes(orders(new PagedFeed(ORDERS)), PATCHY_COURIER);

    Try<List<BigDecimal>> held = patchy.toList().runSafe();
    // Failure(java.io.IOException: no courier serves Edsger's address)
    List<BigDecimal> withFlatRate = patchy.recover(error -> FLAT_RATE).toList().run();
    // [4.99, 4.99, 9.99, 4.99]
    // ANCHOR_END: failures
    System.out.println(held);
    System.out.println(withFlatRate);

    // ANCHOR: outage
    VStream<String> downAtPageTwo = orders(new PagedFeed(ORDERS, 2)).map(VStreamBook::customer);

    List<String> readBeforeTheOutage =
        downAtPageTwo.recoverWith(error -> VStream.empty()).toList().run();
    // [Ada, Grace]
    // ANCHOR_END: outage
    System.out.println(readBeforeTheOutage);

    // ANCHOR: kind
    Monad<VStreamKind.Witness> streams = Instances.monad(vstream());

    Kind<VStreamKind.Witness, String> customers = VSTREAM.widen(VStream.of("Ada", "Alan"));
    Kind<VStreamKind.Witness, String> greetings =
        streams.flatMap(name -> streams.of("Dear " + name), customers);
    List<String> letters = VSTREAM.narrow(greetings).toList().run();
    // [Dear Ada, Dear Alan]
    // ANCHOR_END: kind
    System.out.println(letters);
  }
}
