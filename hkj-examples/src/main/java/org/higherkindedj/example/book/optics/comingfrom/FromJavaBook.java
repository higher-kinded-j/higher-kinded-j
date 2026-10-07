// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.comingfrom;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentState.Dispatched;
import org.higherkindedj.example.book.optics.cast.ConsignmentState.Pending;
import org.higherkindedj.example.book.optics.cast.ConsignmentState.Returned;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.example.book.optics.cast.ReturnedFocus;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/from_java.html">Coming from Lombok, Streams
 * and Switch</a> page. The page {@code {{#include}}}s the anchored regions, and {@code
 * FromJavaBookTest} holds the claims the page makes about this code and its tables.
 *
 * <p>The records here are the "before" half of each before-and-after pair, so they carry the
 * withers Lombok's {@code @With} would generate. Their components are the chapter cast's.
 */
public final class FromJavaBook {

  private FromJavaBook() {}

  static Order normaliseEmailByWithers(Order order) {
    // ANCHOR: lombok_before
    // Lombok's @With: a wither per record, each nested inside the next, and the path read twice
    Order normalised =
        order.withCustomer(
            order
                .customer()
                .withEmail(
                    new EmailAddress(
                        order.customer().email().value().strip().toLowerCase(Locale.ROOT))));
    // ANCHOR_END: lombok_before
    return normalised;
  }

  static Order normaliseEmailByFocus(Order order) {
    // ANCHOR: lombok_after
    // One generated path, three records deep: it reads the email and rebuilds all three records
    Order normalised =
        OrderFocus.customer()
            .email()
            .value()
            .modify(email -> email.strip().toLowerCase(Locale.ROOT), order);
    // ANCHOR_END: lombok_after
    return normalised;
  }

  static Order discountBulkByStream(Order order) {
    // ANCHOR: streams_before
    // filter would drop the lamp from the order, so the test moves inside map
    Order discounted =
        order.withLines(
            order.lines().stream()
                .map(
                    line ->
                        line.quantity() >= 4
                            ? line.withPrice(line.price().multiply(new BigDecimal("0.9")))
                            : line)
                .toList());
    // ANCHOR_END: streams_before
    return discounted;
  }

  static Order discountBulkByFocus(Order order) {
    // ANCHOR: streams_after
    // A filtered path: the lines it leaves out stay in the order, unchanged
    Order discounted =
        OrderFocus.lines()
            .filter(line -> line.quantity() >= 4)
            .via(LineItemFocus.price())
            .modifyAll(price -> price.multiply(new BigDecimal("0.9")), order);
    // ANCHOR_END: streams_after
    return discounted;
  }

  static Consignment tidyReasonBySwitch(Consignment consignment) {
    // ANCHOR: switch_before
    // A sealed switch: change one case, and pass the others through
    Consignment tidied =
        switch (consignment.state()) {
          case Returned returned -> consignment.withState(new Returned(returned.reason().strip()));
          case Pending _, Dispatched _ -> consignment;
        };
    // ANCHOR_END: switch_before
    return tidied;
  }

  static Consignment tidyReasonByPrism(Consignment consignment) {
    // ANCHOR: switch_after
    // The prism picks the Returned case, and any other state passes through
    Consignment tidied =
        ConsignmentFocus.state()
            .via(ConsignmentStatePrisms.returned())
            .via(ReturnedFocus.reason())
            .modify(String::strip, consignment);
    // ANCHOR_END: switch_after
    return tidied;
  }
}

// Each record carries the withers this page's "before" code calls, as Lombok's @With would
// generate them, and the cast's lens and Focus annotations.

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Order(
    UUID id,
    Customer customer,
    List<LineItem> lines,
    Instant placedAt,
    Currency currency,
    OrderStatus status) {
  Order withCustomer(Customer customer) {
    return new Order(id, customer, lines, placedAt, currency, status);
  }

  Order withLines(List<LineItem> lines) {
    return new Order(id, customer, lines, placedAt, currency, status);
  }
}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Customer(String name, EmailAddress email) {
  Customer withEmail(EmailAddress email) {
    return new Customer(name, email);
  }
}

@GenerateLenses
@GenerateFocus
record LineItem(String sku, Integer quantity, BigDecimal price) {
  LineItem withPrice(BigDecimal price) {
    return new LineItem(sku, quantity, price);
  }
}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Consignment(UUID orderId, Address to, ConsignmentState state) {
  Consignment withState(ConsignmentState state) {
    return new Consignment(orderId, to, state);
  }
}

/** A supporting type named for its role beside the cast: a catalogue's prices, keyed by SKU. */
@GenerateLenses
@GenerateFocus
record Catalogue(String name, Map<String, BigDecimal> prices) {}
