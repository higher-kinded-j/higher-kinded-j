// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.cast;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/** Sample values of the Optics chapter's cast, shared by the lane pages' tests. */
public final class CastFixtures {

  private CastFixtures() {}

  public static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  public static final Instant PLACED_AT = Instant.parse("2026-10-01T09:00:00Z");

  public static final Currency GBP = Currency.getInstance("GBP");

  public static final Customer ADA = new Customer("Ada", new EmailAddress("ada@example.com"));

  public static final LineItem LAMP = new LineItem("LAMP", 1, new BigDecimal("40.00"));

  public static final LineItem BULB = new LineItem("BULB", 4, new BigDecimal("2.50"));

  /** Ada's order of a lamp and four bulbs. */
  public static final Order ORDER = order(List.of(LAMP, BULB));

  /** Ada's order with the given lines. */
  public static Order order(List<LineItem> lines) {
    return new Order(ORDER_ID, ADA, lines, PLACED_AT, GBP, OrderStatus.NEW);
  }

  /** A line of one item at the given price. */
  public static LineItem line(String sku, String price) {
    return new LineItem(sku, 1, new BigDecimal(price));
  }

  public static final Address HOME = new Address("1 Long Street", "London", "n1 1aa");

  /** A consignment of Ada's order in the given state. */
  public static Consignment consignment(ConsignmentState state) {
    return new Consignment(ORDER_ID, HOME, state);
  }
}
