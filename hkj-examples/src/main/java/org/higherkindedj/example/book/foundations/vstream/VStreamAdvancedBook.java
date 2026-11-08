// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vstream;

import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.ORDERS;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.vstream;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamTransformations;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.context.VStreamContext;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vstream.VStreamReactive;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.extensions.StreamTraversal;
import org.higherkindedj.optics.util.Traversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/vstream_advanced.html">Advanced
 * Features</a> page: a {@code StreamTraversal} over an order's prices and over the statuses in an
 * order feed, its conversion to a {@code Traversal}, a stream handed to a {@code Flow.Publisher}
 * and read back, the {@code Natural}s between {@code VStream} and {@code List}, {@code Path.from},
 * and a {@code VStreamContext}. The page {@code {{#include}}}s the anchored regions, {@code
 * VStreamAdvancedBookTest} holds the claims the page makes, and the book's output gate runs {@link
 * #main} against the comments in the regions.
 */
public final class VStreamAdvancedBook {

  private VStreamAdvancedBook() {}

  // ANCHOR: prices
  // A lamp, and four bulbs
  static final List<LineItem> LINES =
      List.of(
          new LineItem("LAMP", 1, new BigDecimal("40.00")),
          new LineItem("BULB", 4, new BigDecimal("2.50")));

  // Every price in an order's lines
  static final StreamTraversal<List<LineItem>, BigDecimal> PRICES =
      StreamTraversal.<LineItem>forList().andThen(LineItemLenses.price());

  static BigDecimal withTenPercent(BigDecimal price) {
    return price.multiply(new BigDecimal("1.10")).setScale(2, RoundingMode.HALF_UP);
  }

  // ANCHOR_END: prices

  // ANCHOR: statuses
  // The status of every order a stream gives
  static final StreamTraversal<VStream<Order>, OrderStatus> STATUSES =
      StreamTraversal.<Order>forVStream().andThen(OrderLenses.status());

  // A new order counts as paid, here
  static OrderStatus markPaid(OrderStatus status) {
    return status == OrderStatus.NEW ? OrderStatus.PAID : status;
  }

  // ANCHOR_END: statuses

  public static void main(String[] args) {
    // ANCHOR: prices_run
    List<BigDecimal> prices = PRICES.stream(LINES).toList().run();
    // [40.00, 2.50]
    List<LineItem> raised = PRICES.modify(VStreamAdvancedBook::withTenPercent, LINES);
    BigDecimal lampPrice = raised.getFirst().price();
    // 44.00
    // ANCHOR_END: prices_run
    System.out.println(prices);
    System.out.println(lampPrice);

    // ANCHOR: statuses_run
    VStreamBook.PagedFeed feed = new VStreamBook.PagedFeed(ORDERS);
    VStream<Order> allPaid =
        STATUSES.modify(VStreamAdvancedBook::markPaid, VStreamBook.orders(feed));

    List<OrderStatus> firstTwo = allPaid.take(2).map(Order::status).toList().run();
    // [PAID, PAID]
    List<Integer> pagesFetched = feed.fetched();
    // [1]
    // ANCHOR_END: statuses_run
    System.out.println(firstTwo);
    System.out.println(pagesFetched);

    // ANCHOR: to_traversal
    Traversal<List<LineItem>, BigDecimal> asTraversal = PRICES.toTraversal();
    BigDecimal unitPrices =
        Traversals.getAll(asTraversal, LINES).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    // 42.50
    // ANCHOR_END: to_traversal
    System.out.println(unitPrices);

    // ANCHOR: publisher
    VStream<String> customers = VStream.fromList(ORDERS).map(VStreamBook::customer);

    Flow.Publisher<String> publisher = VStreamReactive.toPublisher(customers);
    VStream<String> readBack = VStreamReactive.fromPublisher(publisher, 16);

    List<String> firstThree = readBack.take(3).toList().run();
    // [Ada, Grace, Alan]
    // ANCHOR_END: publisher
    System.out.println(firstThree);

    // ANCHOR: naturals
    Natural<ListKind.Witness, VStreamKind.Witness> listToVStream =
        VStreamTransformations.listToVStream();
    Natural<VStreamKind.Witness, ListKind.Witness> vstreamToList =
        VStreamTransformations.vstreamToList();

    GenericPath<ListKind.Witness, Order> asList =
        GenericPath.of(LIST.widen(ORDERS), Instances.monad(list()));
    GenericPath<VStreamKind.Witness, Order> asStream =
        asList.mapK(listToVStream, Instances.monad(vstream()));

    long paid = VSTREAM.narrow(asStream.runKind()).filter(VStreamBook::paid).count().run();
    // 4
    List<Order> roundTrip =
        LIST.narrow(listToVStream.andThen(vstreamToList).apply(LIST.widen(ORDERS)));
    int orders = roundTrip.size();
    // 6
    // ANCHOR_END: naturals
    System.out.println(paid);
    System.out.println(orders);

    // ANCHOR: path_from
    Optional<Chainable<?, Order>> found =
        Path.from(VSTREAM.widen(VStream.fromList(ORDERS)), VStreamKind.Witness.class);
    boolean hasPath = found.isPresent();
    // true
    // ANCHOR_END: path_from
    System.out.println(hasPath);

    // ANCHOR: context
    List<String> firstPaid =
        VStreamContext.fromList(ORDERS)
            .filter(VStreamBook::paid)
            .map(VStreamBook::customer)
            .take(3)
            .toList();
    // [Ada, Alan, Edsger]
    long shipped =
        VStreamContext.fromList(ORDERS)
            .filter(order -> order.status() == OrderStatus.SHIPPED)
            .count();
    // 1
    // ANCHOR_END: context
    System.out.println(firstPaid);
    System.out.println(shipped);
  }
}
