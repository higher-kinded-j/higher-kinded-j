// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerLenses;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

/**
 * A runnable example demonstrating filtered traversals for predicate-based optic composition.
 *
 * <p>This example showcases three approaches to filtering in optics:
 *
 * <ul>
 *   <li><b>Instance method</b>: {@code traversal.filtered(predicate)} - filters within an existing
 *       traversal
 *   <li><b>Static combinator</b>: {@code Traversals.filtered(predicate)} - creates a reusable
 *       affine traversal
 *   <li><b>Query-based filter</b>: {@code traversal.filterBy(fold, predicate)} - filters based on
 *       nested properties
 * </ul>
 *
 * <p>Key semantics demonstrated:
 *
 * <ul>
 *   <li>During <b>modification</b>: non-matching elements are <i>preserved unchanged</i> in the
 *       structure
 *   <li>During <b>queries</b> (like getAll): non-matching elements are <i>excluded</i> from results
 * </ul>
 *
 * <p>The examples run on the Optics chapter's order service: the cast's {@link Order}s of {@link
 * LineItem}s, placed by a {@link Customer}.
 */
public class FilteredTraversalExample {

  // A customer's orders, kept beside the cast's Customer with a VIP mark
  public record CustomerHistory(Customer customer, List<Order> orders, boolean vip) {}

  // Monoids has no BigDecimal sum, so the example writes its own
  private static final Monoid<BigDecimal> MONEY =
      new Monoid<>() {
        @Override
        public BigDecimal empty() {
          return BigDecimal.ZERO;
        }

        @Override
        public BigDecimal combine(BigDecimal a, BigDecimal b) {
          return a.add(b);
        }
      };

  // A line with a quantity of four or more is a bulk line
  private static boolean bulk(LineItem line) {
    return line.quantity() >= 4;
  }

  // Ten per cent off a line's price, to the penny
  private static LineItem tenPercentOff(LineItem line) {
    BigDecimal discounted =
        line.price().multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.HALF_EVEN);
    return new LineItem(line.sku(), line.quantity(), discounted);
  }

  // A line's total: its price times its quantity
  private static BigDecimal lineTotal(LineItem line) {
    return line.price().multiply(BigDecimal.valueOf(line.quantity()));
  }

  private static LineItem line(String sku, int quantity, String price) {
    return new LineItem(sku, quantity, new BigDecimal(price));
  }

  private static Customer customer(String name) {
    return new Customer(name, new EmailAddress(name.toLowerCase() + "@example.com"));
  }

  // Order number n, placed on the same day as the others, in pounds
  private static Order order(int number, Customer customer, OrderStatus status, LineItem... lines) {
    return new Order(
        new UUID(0, number),
        customer,
        List.of(lines),
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        status);
  }

  public static void main(String[] args) {
    System.out.println("=== Filtered Traversal Examples ===\n");

    demonstrateBasicFiltering();
    demonstratePreservedVsExcluded();
    demonstrateFilteredComposition();
    demonstrateChainedFilters();
    demonstrateStaticCombinator();
    demonstrateFilterByNested();
    demonstrateFoldFiltering();
    demonstrateFilteredAsFold();

    System.out.println("=== Filtered optics enable declarative, composable data operations ===");
  }

  private static List<LineItem> sampleLines() {
    return List.of(line("BULB", 4, "2.50"), line("LAMP", 1, "40.00"), line("FUSE", 10, "1.20"));
  }

  private static void demonstrateBasicFiltering() {
    System.out.println("--- Basic Filtering with filtered() ---");

    Traversal<List<LineItem>, LineItem> allLines = Traversals.forList();
    Traversal<List<LineItem>, LineItem> bulkLines =
        allLines.filtered(FilteredTraversalExample::bulk);

    List<LineItem> lines = sampleLines();

    System.out.println("All lines: " + lines);

    // Discount ONLY the bulk lines
    List<LineItem> result =
        Traversals.modify(bulkLines, FilteredTraversalExample::tenPercentOff, lines);

    System.out.println("After discounting the bulk lines:");
    for (LineItem line : result) {
      System.out.println(
          "  " + line.sku() + ": £" + line.price() + " (quantity=" + line.quantity() + ")");
    }
    // BULB: £2.25 (discounted), LAMP: £40.00 (unchanged), FUSE: £1.08 (discounted)
    System.out.println();
  }

  private static void demonstratePreservedVsExcluded() {
    System.out.println("--- Semantics: Preserved vs Excluded ---");

    Traversal<List<LineItem>, LineItem> bulkLines =
        Traversals.<LineItem>forList().filtered(FilteredTraversalExample::bulk);

    List<LineItem> lines = sampleLines();

    // MODIFY: Non-matching elements PRESERVED
    List<LineItem> modified =
        Traversals.modify(bulkLines, FilteredTraversalExample::tenPercentOff, lines);
    System.out.println("After modify (structure PRESERVED):");
    System.out.println("  Size: " + modified.size() + " (same as original)");
    System.out.println("  LAMP still in list: " + modified.get(1).sku());

    // QUERY: Non-matching elements EXCLUDED
    List<LineItem> gotten = Traversals.getAll(bulkLines, lines);
    System.out.println("After getAll (non-matching EXCLUDED):");
    System.out.println("  Size: " + gotten.size() + " (only bulk lines)");
    System.out.println("  SKUs: " + gotten.stream().map(LineItem::sku).toList());
    System.out.println();
  }

  private static void demonstrateFilteredComposition() {
    System.out.println("--- Composing Filtered Traversals ---");

    Traversal<List<LineItem>, LineItem> allLines = Traversals.forList();

    // Compose: list -> filtered bulk lines -> price
    Traversal<List<LineItem>, BigDecimal> bulkPrices =
        allLines.filtered(FilteredTraversalExample::bulk).andThen(LineItemLenses.price());

    List<LineItem> lines = sampleLines();

    // Get only the bulk lines' prices
    List<BigDecimal> prices = Traversals.getAll(bulkPrices, lines);
    System.out.println("Bulk line prices: " + prices);

    // Take £0.20 off only the bulk lines' prices
    List<LineItem> result =
        Traversals.modify(bulkPrices, price -> price.subtract(new BigDecimal("0.20")), lines);
    System.out.println("After taking £0.20 off the bulk prices:");
    for (LineItem line : result) {
      System.out.println("  " + line.sku() + ": £" + line.price());
    }
    // BULB: £2.30 (bulk), LAMP: £40.00 (single item, unchanged), FUSE: £1.00 (bulk)
    System.out.println();
  }

  private static void demonstrateChainedFilters() {
    System.out.println("--- Chaining Multiple Filters ---");

    Traversal<List<LineItem>, LineItem> allLines = Traversals.forList();

    // Chain: bulk AND under £5 AND a consumable (a bulb or a fuse)
    Traversal<List<LineItem>, LineItem> targetLines =
        allLines
            .filtered(FilteredTraversalExample::bulk)
            .filtered(line -> line.price().compareTo(new BigDecimal("5")) < 0)
            .filtered(line -> line.sku().equals("BULB") || line.sku().equals("FUSE"));

    List<LineItem> lines =
        List.of(
            line("LAMP", 1, "40.00"), // a single item
            line("CABLE", 6, "8.00"), // bulk, but over £5
            line("BULB", 4, "2.50"), // matches all criteria
            line("PLUG", 10, "1.50"), // bulk and under £5, but not a consumable
            line("FUSE", 10, "1.20") // matches all criteria
            );

    List<LineItem> result = Traversals.getAll(targetLines, lines);
    System.out.println("Lines matching all criteria (bulk, under £5, a bulb or a fuse):");
    for (LineItem line : result) {
      System.out.println(
          "  " + line.sku() + " (quantity=" + line.quantity() + ", price=£" + line.price() + ")");
    }
    System.out.println();
  }

  private static void demonstrateStaticCombinator() {
    System.out.println("--- Static Combinator: Traversals.filtered() ---");

    // Create reusable filter
    Traversal<LineItem, LineItem> bulkFilter = Traversals.filtered(FilteredTraversalExample::bulk);

    LineItem bulbs = line("BULB", 4, "2.50");
    LineItem lamp = line("LAMP", 1, "40.00");

    // Standalone usage
    LineItem result1 =
        Traversals.modify(bulkFilter, FilteredTraversalExample::tenPercentOff, bulbs);
    System.out.println("Bulk line after discount: £" + result1.price() + " (was £2.50)");

    LineItem result2 = Traversals.modify(bulkFilter, FilteredTraversalExample::tenPercentOff, lamp);
    System.out.println("Single item after discount: £" + result2.price() + " (unchanged)");

    // Compose into pipeline
    Traversal<List<LineItem>, String> bulkSkus =
        Traversals.<LineItem>forList()
            .andThen(Traversals.filtered(FilteredTraversalExample::bulk)) // Static combinator
            .andThen(LineItemLenses.sku());

    List<String> skus = Traversals.getAll(bulkSkus, List.of(bulbs, lamp));
    System.out.println("Bulk SKUs via static combinator: " + skus);
    System.out.println();
  }

  private static void demonstrateFilterByNested() {
    System.out.println("--- Advanced: filterBy() for Nested Queries ---");

    Traversal<List<CustomerHistory>, CustomerHistory> allHistories = Traversals.forList();
    Fold<CustomerHistory, Order> historyOrders = Fold.of(CustomerHistory::orders);

    // Filter customers who have ANY unpaid order
    Traversal<List<CustomerHistory>, CustomerHistory> withUnpaid =
        allHistories.filterBy(historyOrders, order -> order.status() == OrderStatus.NEW);

    Customer alice = customer("Alice");
    Customer bob = customer("Bob");
    Customer charlie = customer("Charlie");
    List<CustomerHistory> histories =
        List.of(
            new CustomerHistory(
                alice,
                List.of(
                    order(1, alice, OrderStatus.PAID, line("LAMP", 1, "40.00")),
                    order(2, alice, OrderStatus.SHIPPED, line("BULB", 4, "2.50"))),
                false),
            new CustomerHistory(
                bob,
                List.of(
                    order(3, bob, OrderStatus.NEW, line("DESK", 1, "350.00")), // UNPAID!
                    order(4, bob, OrderStatus.PAID, line("PEN", 10, "1.50"))),
                false),
            new CustomerHistory(
                charlie,
                List.of(order(5, charlie, OrderStatus.PAID, line("CHAIR", 1, "120.00"))),
                true));

    List<CustomerHistory> unpaid = Traversals.getAll(withUnpaid, histories);
    System.out.println("Customers with unpaid orders:");
    for (CustomerHistory history : unpaid) {
      System.out.println("  " + history.customer().name());
    }
    // Only Bob

    // Example: Orders with a line over £500
    Traversal<List<Order>, Order> allOrders = Traversals.forList();
    Fold<Order, LineItem> orderLines = Fold.of(Order::lines);

    Traversal<List<Order>, Order> bigTicketOrders =
        allOrders.filterBy(orderLines, line -> line.price().compareTo(new BigDecimal("500")) > 0);

    List<Order> orders =
        List.of(
            order(6, alice, OrderStatus.PAID, line("APPLE", 6, "0.50"), line("PEAR", 4, "0.60")),
            order(7, bob, OrderStatus.PAID, line("LAPTOP", 1, "1000.00")),
            order(8, charlie, OrderStatus.NEW, line("BOOK", 1, "20.00")));

    List<Order> bigTicket = Traversals.getAll(bigTicketOrders, orders);
    System.out.println("Orders with a line over £500: " + bigTicket.size() + " order(s)");
    System.out.println();
  }

  private static void demonstrateFoldFiltering() {
    System.out.println("--- Fold Filtering for Read-Only Queries ---");

    Fold<Order, LineItem> linesFold = Fold.of(Order::lines);
    Fold<Order, LineItem> expensiveLines =
        linesFold.filtered(line -> line.price().compareTo(new BigDecimal("100")) > 0);

    Order order =
        order(
            9,
            customer("Ada"),
            OrderStatus.NEW,
            line("APPLE", 6, "0.50"),
            line("LAPTOP", 1, "1000.00"),
            line("BOOK", 2, "25.00"),
            line("PHONE", 1, "800.00"));

    System.out.println("Order lines: " + order.lines().stream().map(LineItem::sku).toList());

    // Query only expensive lines
    List<LineItem> expensive = expensiveLines.getAll(order);
    System.out.println(
        "Expensive lines (>£100): " + expensive.stream().map(LineItem::sku).toList());

    int count = expensiveLines.length(order);
    System.out.println("Count of expensive lines: " + count);

    BigDecimal totalExpensive =
        expensiveLines.foldMap(MONEY, FilteredTraversalExample::lineTotal, order);
    System.out.println("Total value of expensive lines: £" + totalExpensive);

    boolean allSingleItems = expensiveLines.all(line -> line.quantity() == 1, order);
    System.out.println("All expensive lines are single items: " + allSingleItems);
    System.out.println();
  }

  private static void demonstrateFilteredAsFold() {
    System.out.println("--- Filtered Traversal to Fold: The Query Pipeline ---");

    Traversal<List<Order>, Order> allOrders = Traversals.forList();

    // Build a filtered traversal, then convert to Fold for read-only aggregation
    Fold<List<Order>, Order> paidFold =
        allOrders.filtered(o -> o.status() == OrderStatus.PAID).asFold();

    Customer alice = customer("Alice");
    Customer bob = customer("Bob");
    Customer charlie = customer("Charlie");
    Customer diana = customer("Diana");
    List<Order> orders =
        List.of(
            order(10, alice, OrderStatus.PAID, line("LAMP", 1, "40.00"), line("BULB", 4, "2.50")),
            order(11, bob, OrderStatus.NEW, line("DESK", 1, "350.00")),
            order(12, charlie, OrderStatus.PAID, line("CHAIR", 2, "120.00")),
            order(13, diana, OrderStatus.SHIPPED, line("PEN", 10, "1.50")));

    // Use the full Fold API on the filtered result
    Fold<List<Order>, String> paidCustomers =
        paidFold.andThen(OrderLenses.customer().asFold()).andThen(CustomerLenses.name().asFold());
    System.out.println("Paid orders' customers: " + paidCustomers.getAll(orders));
    System.out.println("Paid order count: " + paidFold.length(orders));

    BigDecimal paidRevenue =
        paidFold
            .andThen(Fold.of(Order::lines))
            .foldMap(MONEY, FilteredTraversalExample::lineTotal, orders);
    System.out.println("Total paid revenue: £" + paidRevenue);

    boolean allPaidHaveSeveralLines = paidFold.all(o -> o.lines().size() > 1, orders);
    System.out.println("All paid orders have several lines: " + allPaidHaveSeveralLines);

    // Chain: filtered traversal -> asFold -> andThen to extract a specific field
    Fold<List<Order>, String> paidSeveralLineCustomers =
        allOrders
            .filtered(o -> o.status() == OrderStatus.PAID)
            .filtered(o -> o.lines().size() > 1)
            .asFold()
            .andThen(OrderLenses.customer().asFold())
            .andThen(CustomerLenses.name().asFold());

    System.out.println(
        "Customers of paid orders with several lines: " + paidSeveralLineCustomers.getAll(orders));
    System.out.println();
  }
}
