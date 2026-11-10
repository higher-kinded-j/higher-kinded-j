// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vstream;

import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.ORDERS;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.customer;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.orders;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.vstream;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.vstream.VStreamBook.PagedFeed;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vstream.VStreamTraverse;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/vstream_hkt.html">VStream HKT and Type
 * Classes</a> page: one generic function used with a stream, a list and a {@code Maybe}; what
 * {@code ap}, {@code orElse}, {@code foldMap} and {@code traverse} do with a stream of orders. The
 * page {@code {{#include}}}s the anchored regions, {@code VStreamHktBookTest} holds the claims the
 * page makes and checks the laws, and the book's output gate runs {@link #main} against the
 * comments in the regions.
 */
public final class VStreamHktBook {

  /** Postcodes the courier holds, by customer, with none for Grace or Barbara. */
  static final Map<String, String> POSTCODES =
      Map.of("Ada", "N1 9GU", "Alan", "M13 9PL", "Edsger", "CB2 1TN", "Ken", "EH8 9YL");

  private VStreamHktBook() {}

  // ANCHOR: customers
  // Written once, for any Functor: the customer of each order
  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> customers(
      Functor<F> functor, Kind<F, Order> orders) {
    return functor.map(VStreamBook::customer, orders);
  }

  // ANCHOR_END: customers

  // ANCHOR: order_value
  // What an order is worth: each line's price times its quantity
  static BigDecimal value(Order order) {
    return order.lines().stream()
        .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  // ANCHOR_END: order_value

  public static void main(String[] args) {
    // ANCHOR: customers_used
    Kind<VStreamKind.Witness, String> streamed =
        customers(Instances.functor(vstream()), VSTREAM.widen(orders(new PagedFeed(ORDERS))));
    List<String> fromTheFeed = VSTREAM.narrow(streamed).toList().run();
    // [Ada, Grace, Alan, Edsger, Barbara, Ken]
    List<String> fromAList = LIST.narrow(customers(Instances.functor(list()), LIST.widen(ORDERS)));
    // [Ada, Grace, Alan, Edsger, Barbara, Ken]
    Maybe<String> fromAMaybe =
        MAYBE.narrow(
            customers(Instances.functor(maybe()), MAYBE.widen(Maybe.just(ORDERS.getFirst()))));
    // Just(Ada)
    // ANCHOR_END: customers_used
    System.out.println(fromTheFeed);
    System.out.println(fromAList);
    System.out.println(fromAMaybe);

    // ANCHOR: ap
    PagedFeed feed = new PagedFeed(ORDERS);
    Kind<VStreamKind.Witness, String> firstTwoPaid =
        VSTREAM.widen(orders(feed).filter(VStreamBook::paid).map(VStreamBook::customer).take(2));
    Kind<VStreamKind.Witness, Function<String, String>> services =
        VSTREAM.widen(
            VStream.<Function<String, String>>of(
                name -> "standard for " + name, name -> "express for " + name));

    Applicative<VStreamKind.Witness> applicative = Instances.applicative(vstream());
    List<String> offers = VSTREAM.narrow(applicative.ap(services, firstTwoPaid)).toList().run();
    // [standard for Ada, standard for Alan, express for Ada, express for Alan]
    List<Integer> fetched = feed.fetched();
    // [1, 2]
    // ANCHOR_END: ap
    System.out.println(offers);
    System.out.println(fetched);

    // ANCHOR: or_else
    Alternative<VStreamKind.Witness> alternative = Instances.alternative(vstream());
    Kind<VStreamKind.Witness, String> london = VSTREAM.widen(VStream.of("Ada", "Alan"));
    Kind<VStreamKind.Witness, String> edinburgh = VSTREAM.widen(VStream.of("Edsger", "Ken"));

    List<String> both = VSTREAM.narrow(alternative.orElse(london, () -> edinburgh)).toList().run();
    // [Ada, Alan, Edsger, Ken]
    List<String> londonOnly =
        VSTREAM.narrow(alternative.orElse(london, () -> alternative.empty())).toList().run();
    // [Ada, Alan]
    // ANCHOR_END: or_else
    System.out.println(both);
    System.out.println(londonOnly);

    // ANCHOR: fold_map
    Traverse<VStreamKind.Witness> traverse = VStreamTraverse.INSTANCE;
    Kind<VStreamKind.Witness, Order> paidOrders =
        VSTREAM.widen(orders(new PagedFeed(ORDERS)).filter(VStreamBook::paid));

    BigDecimal paidValue =
        traverse.foldMap(Monoids.bigDecimalAddition(), VStreamHktBook::value, paidOrders);
    // 160.00
    // ANCHOR_END: fold_map
    System.out.println(paidValue);

    // ANCHOR: traverse
    Function<Order, Kind<MaybeKind.Witness, String>> postcode =
        order -> MAYBE.widen(Maybe.fromNullable(POSTCODES.get(customer(order))));

    Kind<MaybeKind.Witness, Kind<VStreamKind.Witness, String>> forThePaid =
        traverse.traverse(Instances.applicative(maybe()), postcode, paidOrders);
    Maybe<List<String>> labels =
        MAYBE.narrow(forThePaid).map(stream -> VSTREAM.narrow(stream).toList().run());
    // Just([N1 9GU, M13 9PL, CB2 1TN, EH8 9YL])

    Kind<VStreamKind.Witness, Order> everyOrder = VSTREAM.widen(orders(new PagedFeed(ORDERS)));
    Maybe<Kind<VStreamKind.Witness, String>> forEveryOrder =
        MAYBE.narrow(traverse.traverse(Instances.applicative(maybe()), postcode, everyOrder));
    // Nothing
    // ANCHOR_END: traverse
    System.out.println(labels);
    System.out.println(forEveryOrder);
  }
}
