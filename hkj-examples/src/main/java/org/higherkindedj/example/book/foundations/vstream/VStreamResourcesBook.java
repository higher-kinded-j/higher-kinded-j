// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vstream;

import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.COURIER;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.ORDERS;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.orders;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.shippingQuotes;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.higherkindedj.example.book.foundations.OrderPage;
import org.higherkindedj.example.book.foundations.vstream.VStreamBook.Courier;
import org.higherkindedj.example.book.foundations.vstream.VStreamBook.OrderFeed;
import org.higherkindedj.example.book.foundations.vstream.VStreamBook.PagedFeed;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/vstream_resources.html">Resource-Safe
 * Streaming</a> page: the order feed read over a connection that the stream opens when a run starts
 * and closes however the run ends; a run stopped early, a feed that fails, and a connection that
 * will not close; a courier session opened inside the connection; two feeds read one after another;
 * finalisers; and the same on a {@code VStreamPath}. The page {@code {{#include}}}s the anchored
 * regions, {@code VStreamResourcesBookTest} holds the claims the page makes, and the book's output
 * gate runs {@link #main} against the comments in the regions.
 */
public final class VStreamResourcesBook {

  // ANCHOR: services
  // A service opens a connection to the order feed, or a session with a courier. Each is
  // closed once the stream is done with it, and opening or closing may throw an IOException
  interface FeedService {
    FeedConnection open() throws IOException;
  }

  interface FeedConnection extends OrderFeed, Closeable {}

  interface CourierService {
    CourierSession open() throws IOException;
  }

  interface CourierSession extends Courier, Closeable {}

  // ANCHOR_END: services

  /**
   * A feed service whose connections serve a {@link PagedFeed} and write each open, page and close
   * to a log. One made with {@link #failingToClose} throws when a connection is closed.
   */
  static final class LoggedFeedService implements FeedService {
    private final List<String> log;
    private final PagedFeed feed;
    private final boolean closeFails;

    private LoggedFeedService(List<String> log, PagedFeed feed, boolean closeFails) {
      this.log = log;
      this.feed = feed;
      this.closeFails = closeFails;
    }

    /** Serves {@code feed}, logging to {@code log}. */
    LoggedFeedService(List<String> log, PagedFeed feed) {
      this(log, feed, false);
    }

    /** Serves every order in {@code ORDERS}, logging to {@code log}. */
    LoggedFeedService(List<String> log) {
      this(log, new PagedFeed(ORDERS));
    }

    /** Serves {@code feed}, logging to {@code log}, with connections that fail to close. */
    static LoggedFeedService failingToClose(List<String> log, PagedFeed feed) {
      return new LoggedFeedService(log, feed, true);
    }

    @Override
    public FeedConnection open() {
      log.add("feed opened");
      return new FeedConnection() {
        @Override
        public OrderPage page(int number) throws IOException {
          log.add("page " + number);
          return feed.page(number);
        }

        @Override
        public void close() throws IOException {
          log.add("feed closed");
          if (closeFails) {
            throw new IOException("the connection would not close");
          }
        }
      };
    }
  }

  /**
   * A courier service whose sessions quote 4.99, as {@link VStreamBook#COURIER} does, and write
   * each open and close to a log.
   */
  static final class LoggedCourierService implements CourierService {
    private final List<String> log;

    LoggedCourierService(List<String> log) {
      this.log = log;
    }

    @Override
    public CourierSession open() {
      log.add("courier opened");
      return new CourierSession() {
        @Override
        public BigDecimal quote(Order order) throws IOException {
          return COURIER.quote(order);
        }

        @Override
        public void close() {
          log.add("courier closed");
        }
      };
    }
  }

  private VStreamResourcesBook() {}

  // ANCHOR: orders_over
  // Every order, read over a connection that is opened when a run starts and closed however
  // the run ends
  static VStream<Order> ordersOver(FeedService service) {
    return VStream.bracket(
        VTask.of(service::open), VStreamBook::orders, VStreamResourcesBook::closing);
  }

  // close() can throw an IOException, so it runs in VTask.of; VTask.exec takes a Runnable,
  // which cannot throw one
  static VTask<Unit> closing(Closeable resource) {
    return VTask.of(
        () -> {
          resource.close();
          return Unit.INSTANCE;
        });
  }

  // ANCHOR_END: orders_over

  // ANCHOR: nested
  // A courier session opened inside the feed connection, so it is closed first
  static VStream<BigDecimal> quotesOver(FeedService feeds, CourierService couriers) {
    return VStream.bracket(
        VTask.of(feeds::open),
        connection ->
            VStream.bracket(
                VTask.of(couriers::open),
                session -> shippingQuotes(orders(connection), session),
                VStreamResourcesBook::closing),
        VStreamResourcesBook::closing);
  }

  // ANCHOR_END: nested

  public static void main(String[] args) {
    // ANCHOR: bracket_used
    List<String> log = new CopyOnWriteArrayList<>();
    VStream<String> paidCustomers =
        ordersOver(new LoggedFeedService(log)).filter(VStreamBook::paid).map(VStreamBook::customer);
    List<String> beforeRunning = List.copyOf(log);
    // []
    List<String> paid = paidCustomers.toList().run();
    // [Ada, Alan, Edsger, Ken]
    List<String> afterOneRun = List.copyOf(log);
    // [feed opened, page 1, page 2, page 3, page 4, feed closed]
    // ANCHOR_END: bracket_used
    System.out.println(beforeRunning);
    System.out.println(paid);
    System.out.println(afterOneRun);

    // ANCHOR: run_twice
    paidCustomers.toList().run();
    int opened = Collections.frequency(log, "feed opened");
    // 2
    int closed = Collections.frequency(log, "feed closed");
    // 2
    // ANCHOR_END: run_twice
    System.out.println(opened);
    System.out.println(closed);

    // ANCHOR: stopped_early
    List<String> earlyLog = new CopyOnWriteArrayList<>();
    List<String> firstTwo =
        ordersOver(new LoggedFeedService(earlyLog))
            .filter(VStreamBook::paid)
            .map(VStreamBook::customer)
            .take(2)
            .toList()
            .run();
    // [Ada, Alan]
    List<String> earlyRun = List.copyOf(earlyLog);
    // [feed opened, page 1, page 2, feed closed]
    // ANCHOR_END: stopped_early
    System.out.println(firstTwo);
    System.out.println(earlyRun);

    // ANCHOR: failures
    List<String> downLog = new CopyOnWriteArrayList<>();
    Try<List<Order>> feedDown =
        ordersOver(new LoggedFeedService(downLog, new PagedFeed(ORDERS, 2))).toList().runSafe();
    // Failure(java.io.IOException: the order feed is down)
    List<String> downRun = List.copyOf(downLog);
    // [feed opened, page 1, page 2, feed closed]

    Try<List<Order>> stuck =
        ordersOver(LoggedFeedService.failingToClose(downLog, new PagedFeed(ORDERS)))
            .toList()
            .runSafe();
    // Failure(java.io.IOException: the connection would not close)
    // ANCHOR_END: failures
    System.out.println(feedDown);
    System.out.println(downRun);
    System.out.println(stuck);

    // ANCHOR: nested_used
    List<String> nestedLog = new CopyOnWriteArrayList<>();
    List<BigDecimal> quotes =
        quotesOver(new LoggedFeedService(nestedLog), new LoggedCourierService(nestedLog))
            .toList()
            .run();
    // [4.99, 4.99, 4.99, 4.99]
    List<String> nestedRun = List.copyOf(nestedLog);
    // [feed opened, courier opened, page 1, page 2, page 3, page 4, courier closed, feed closed]
    // ANCHOR_END: nested_used
    System.out.println(quotes);
    System.out.println(nestedRun);

    // ANCHOR: one_after_another
    // Two feeds, each read over its own connection, joined outside both brackets
    List<String> twoFeedsLog = new CopyOnWriteArrayList<>();
    VStream<Order> london =
        ordersOver(new LoggedFeedService(twoFeedsLog, new PagedFeed(ORDERS.subList(0, 2))));
    VStream<Order> edinburgh =
        ordersOver(new LoggedFeedService(twoFeedsLog, new PagedFeed(ORDERS.subList(2, 4))));

    long both = london.concat(edinburgh).count().run();
    // 4
    List<String> twoFeedsRun = List.copyOf(twoFeedsLog);
    // [feed opened, page 1, page 2, feed closed, feed opened, page 1, page 2, feed closed]
    // ANCHOR_END: one_after_another
    System.out.println(both);
    System.out.println(twoFeedsRun);

    // ANCHOR: finalisers
    List<String> reports = new CopyOnWriteArrayList<>();
    VStream<String> customers =
        orders(new PagedFeed(ORDERS))
            .map(VStreamBook::customer)
            .onFinalize(VTask.exec(() -> reports.add("customers read")))
            .onFinalize(VTask.exec(() -> reports.add("report sent")));

    List<String> firstCustomer = customers.take(1).toList().run();
    // [Ada]
    List<String> afterTake = List.copyOf(reports);
    // [customers read, report sent]
    // ANCHOR_END: finalisers
    System.out.println(firstCustomer);
    System.out.println(afterTake);

    // ANCHOR: path
    List<String> pathLog = new CopyOnWriteArrayList<>();
    FeedService service = new LoggedFeedService(pathLog);
    VStreamPath<Order> viaPath =
        Path.vstreamBracket(
                VTask.of(service::open), VStreamBook::orders, VStreamResourcesBook::closing)
            .onFinalize(VTask.exec(() -> pathLog.add("orders read")));

    long count = viaPath.count().unsafeRun();
    // 6
    List<String> pathRun = List.copyOf(pathLog);
    // [feed opened, page 1, page 2, page 3, page 4, feed closed, orders read]
    // ANCHOR_END: path
    System.out.println(count);
    System.out.println(pathRun);
  }
}
