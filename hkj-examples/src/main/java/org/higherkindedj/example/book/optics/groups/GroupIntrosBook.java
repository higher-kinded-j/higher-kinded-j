// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.groups;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.CustomerLenses;
import org.higherkindedj.example.book.optics.cast.EmailAddressLenses;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderTraversals;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;

/**
 * The code shown on the book's four On demand group introductions in the Optics chapter: <a
 * href="https://higher-kinded-j.github.io/latest/optics/ch1_intro.html">The Optic Types</a>, <a
 * href="https://higher-kinded-j.github.io/latest/optics/ch2_intro.html">Collections</a>, <a
 * href="https://higher-kinded-j.github.io/latest/optics/ch3_intro.html">Precision and Filtering</a>
 * and <a href="https://higher-kinded-j.github.io/latest/optics/ch4_intro.html">The Focus DSL in
 * Depth</a>. Each page {@code {{#include}}}s one anchored region, and {@code GroupIntrosBookTest}
 * holds the values its comments claim.
 *
 * <p>Three of the four work over the chapter's cast, from {@code
 * org.higherkindedj.example.book.optics.cast}; Collections keeps the league the Collections pages
 * share, declared here.
 */
public final class GroupIntrosBook {

  private GroupIntrosBook() {}

  /** The Optic Types' composed lens and its two updates, so the test can read each. */
  record EmailUpdates(Lens<Order, String> email, Order changed, Order shouted) {}

  static EmailUpdates opticTypes(Order order) {
    // ANCHOR: optic_types
    var email =
        OrderLenses.customer().andThen(CustomerLenses.email()).andThen(EmailAddressLenses.value());

    Order changed = email.set("ada@example.org", order);
    Order shouted = email.modify(String::toUpperCase, order);
    // email.get(changed) -> "ada@example.org"
    // email.get(shouted) -> "ADA@EXAMPLE.COM"
    // each record on the path is rebuilt for you and the lines are reused; order is untouched
    // ANCHOR_END: optic_types
    return new EmailUpdates(email, changed, shouted);
  }

  /** Collections' composed traversal and its bulk update, so the test can read each. */
  record Bonus(Traversal<League, Integer> everyScore, League bonus) {}

  static Bonus collections(League league) {
    // ANCHOR: collections
    var everyScore =
        LeagueTraversals.teams().andThen(TeamTraversals.players()).andThen(PlayerLenses.score());

    League bonus = Traversals.modify(everyScore, score -> score + 5, league);
    // Traversals.getAll(everyScore, league) -> [100, 90, 110, 120]
    // Traversals.getAll(everyScore, bonus)  -> [105, 95, 115, 125]
    // every team and player is rebuilt for you; league itself is untouched
    // ANCHOR_END: collections
    return new Bonus(everyScore, bonus);
  }

  /** Precision and Filtering's narrowed traversal and its update, so the test can read each. */
  record Discount(Traversal<Order, BigDecimal> pricey, Order discounted) {}

  static Discount precision(Order order) {
    // ANCHOR: precision
    var pricey =
        OrderTraversals.lines()
            .andThen(LineItemLenses.price())
            .filtered(price -> price.compareTo(new BigDecimal("10.00")) > 0);

    Order discounted =
        Traversals.modify(pricey, price -> price.subtract(new BigDecimal("5.00")), order);
    // Traversals.getAll(pricey, order)      -> [40.00]
    // Traversals.getAll(pricey, discounted) -> [35.00]
    // the £2.50 bulbs are untouched, and so is order itself
    // ANCHOR_END: precision
    return new Discount(pricey, discounted);
  }

  /** The Focus DSL in Depth's deep read and bulk update, so the test can read each. */
  record Reminder(String email, Order afterRise) {}

  static Reminder focusInDepth(Order order) {
    // ANCHOR: focus_in_depth
    // Order -> customer -> email -> value: chained hops, generated
    String email = OrderFocus.customer().email().value().get(order);
    // "ada@example.com"

    // Order -> lines[] -> price: every price, in one expression
    Order afterRise =
        OrderFocus.lines()
            .via(LineItemFocus.price())
            .modifyAll(price -> price.multiply(new BigDecimal("1.10")), order);
    // every price is 10% higher; order itself is untouched
    // ANCHOR_END: focus_in_depth
    return new Reminder(email, afterRise);
  }
}

// The league, its teams and their players, as the Collections pages share them.

@GenerateLenses
record Player(String name, int score) {}

@GenerateLenses
@GenerateTraversals
record Team(String name, List<Player> players) {}

@GenerateLenses
@GenerateTraversals
record League(String name, List<Team> teams) {}
