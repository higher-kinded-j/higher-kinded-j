// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

// ANCHOR: complete_example

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.example.book.optics.cast.OrderTraversals;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.Traversals;

/**
 * Comprehensive example demonstrating Fold optics for read-only querying and data extraction.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Basic query operations: getAll, preview, find, exists, all, isEmpty, length
 *   <li>Composing folds for deep queries across nested structures
 *   <li>Monoid-based aggregation for calculating sums, checking conditions, etc.
 *   <li>Real-world analytics on a customer's order history
 * </ul>
 *
 * <p>Fold is a read-only optic designed specifically for querying without modification, making code
 * intent clear and preventing accidental mutations.
 */
public class FoldUsageExample {

  // A customer's past orders, beside the chapter's Order
  @GenerateLenses
  @GenerateFolds
  public record OrderHistory(List<Order> orders) {}

  // An order of the customer's, placed on the same day, in pounds
  private static Order order(String id, Customer customer, LineItem... lines) {
    return new Order(
        UUID.fromString(id),
        customer,
        List.of(lines),
        Instant.parse("2026-10-01T09:00:00Z"),
        Currency.getInstance("GBP"),
        OrderStatus.NEW);
  }

  // A line's total: its price times its quantity
  private static BigDecimal lineTotal(LineItem line) {
    return line.price().multiply(BigDecimal.valueOf(line.quantity()));
  }

  public static void main(String[] args) {
    // Create sample data: two of Ada's orders
    Customer ada = new Customer("Ada", new EmailAddress("ada@example.com"));

    var order1 =
        order(
            "00000000-0000-0000-0000-000000000001",
            ada,
            new LineItem("LAPTOP", 1, new BigDecimal("999.99")),
            new LineItem("MOUSE", 2, new BigDecimal("12.50")),
            new LineItem("DESK", 1, new BigDecimal("350.00")));

    var order2 =
        order(
            "00000000-0000-0000-0000-000000000002",
            ada,
            new LineItem("KEYBOARD", 1, new BigDecimal("75.00")),
            new LineItem("MONITOR", 1, new BigDecimal("450.00")),
            new LineItem("CHAIR", 1, new BigDecimal("200.00")));

    var history = new OrderHistory(List.of(order1, order2));

    System.out.println("=== FOLD USAGE EXAMPLE ===\n");

    // --- SCENARIO 1: Basic Query Operations ---
    System.out.println("--- Scenario 1: Basic Query Operations ---");
    Fold<Order, LineItem> linesFold = Fold.of(Order::lines);

    List<LineItem> allLines = linesFold.getAll(order1);
    System.out.println("All lines: " + allLines.size() + " line items");

    Optional<LineItem> firstLine = linesFold.preview(order1);
    System.out.println("First line: " + firstLine.map(LineItem::sku).orElse("none"));

    int count = linesFold.length(order1);
    System.out.println("Line count: " + count);

    boolean isEmpty = linesFold.isEmpty(order1);
    System.out.println("Is empty: " + isEmpty + "\n");

    // --- SCENARIO 2: Conditional Queries ---
    System.out.println("--- Scenario 2: Conditional Queries ---");

    boolean hasMultiUnit = linesFold.exists(line -> line.quantity() > 1, order1);
    System.out.println("Has a line for more than one unit: " + hasMultiUnit);

    boolean allSingleUnits = linesFold.all(line -> line.quantity() == 1, order1);
    System.out.println("All lines for a single unit: " + allSingleUnits);

    Optional<LineItem> expensiveLine =
        linesFold.find(line -> line.price().compareTo(new BigDecimal("500")) > 0, order1);
    System.out.println(
        "First line over £500: " + expensiveLine.map(LineItem::sku).orElse("none") + "\n");

    // --- SCENARIO 3: Composition ---
    System.out.println("--- Scenario 3: Composed Folds ---");

    Fold<OrderHistory, LineItem> allHistoryLines = OrderHistoryFolds.orders().andThen(linesFold);

    List<LineItem> linesFromHistory = allHistoryLines.getAll(history);
    System.out.println("Total lines across all orders: " + linesFromHistory.size());

    Fold<OrderHistory, String> allSkus = allHistoryLines.andThen(LineItemLenses.sku().asFold());

    System.out.println("Every SKU: " + allSkus.getAll(history) + "\n");

    // --- SCENARIO 4: Monoid Aggregation ---
    System.out.println("--- Scenario 4: Monoid-Based Aggregation ---");

    // Monoids has no BigDecimal sum, so money sums with a monoid of its own
    Monoid<BigDecimal> sumMonoid =
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

    BigDecimal orderTotal = linesFold.foldMap(sumMonoid, FoldUsageExample::lineTotal, order1);
    System.out.println("Order 1 total: £" + orderTotal);

    BigDecimal historyTotal =
        allHistoryLines.foldMap(sumMonoid, FoldUsageExample::lineTotal, history);
    System.out.println("All orders total: £" + historyTotal);

    // Standard monoids from the Monoids utility class: Boolean AND for checking conditions
    Monoid<Boolean> andMonoid = Monoids.booleanAnd();

    boolean allAffordable =
        linesFold.foldMap(
            andMonoid, line -> line.price().compareTo(new BigDecimal("1000")) < 0, order1);
    System.out.println("All lines under £1000: " + allAffordable);

    // Boolean OR monoid for checking any condition
    Monoid<Boolean> orMonoid = Monoids.booleanOr();

    boolean hasOverFourHundred =
        allHistoryLines.foldMap(
            orMonoid, line -> line.price().compareTo(new BigDecimal("400")) > 0, history);
    System.out.println("Has a line over £400: " + hasOverFourHundred + "\n");

    // --- SCENARIO 5: Analytics ---
    System.out.println("--- Scenario 5: Real-World Analytics ---");

    // Most expensive line
    Optional<LineItem> mostExpensive =
        allHistoryLines.getAll(history).stream().max(Comparator.comparing(LineItem::price));
    System.out.println(
        "Most expensive line: "
            + mostExpensive.map(line -> line.sku() + " (£" + line.price() + ")").orElse("none"));

    // Average line total
    List<LineItem> everyLine = allHistoryLines.getAll(history);
    BigDecimal averageLine =
        everyLine.isEmpty()
            ? BigDecimal.ZERO
            : historyTotal.divide(BigDecimal.valueOf(everyLine.size()), 2, RoundingMode.HALF_EVEN);
    System.out.println("Average line total: £" + averageLine);

    // Count the lines priced over £100
    long overHundredCount =
        allHistoryLines.getAll(history).stream()
            .filter(line -> line.price().compareTo(new BigDecimal("100")) > 0)
            .count();
    System.out.println("Lines priced over £100: " + overHundredCount + "\n");

    // --- SCENARIO 6: Traversal-Derived Folds ---
    System.out.println("--- Scenario 6: Traversal-Derived Folds via asFold() ---");

    // Build a Traversal for every line across all orders, then convert to Fold
    Traversal<OrderHistory, LineItem> allLinesTraversal =
        OrderHistoryLenses.orders()
            .andThen(Traversals.<Order>forList())
            .andThen(OrderTraversals.lines());

    // Convert to Fold: the same query power as the folds above
    Fold<OrderHistory, LineItem> traversalDerivedFold = allLinesTraversal.asFold();

    // These produce the same results as the folds above
    List<LineItem> allLines2 = traversalDerivedFold.getAll(history);
    System.out.println("Lines via traversal-derived fold: " + allLines2.size());

    BigDecimal total =
        traversalDerivedFold.foldMap(sumMonoid, FoldUsageExample::lineTotal, history);
    System.out.println("Total via traversal-derived fold: £" + total);

    // Filter the traversal, then convert to Fold for targeted queries
    Fold<OrderHistory, LineItem> overHundredFold =
        allLinesTraversal
            .filtered(line -> line.price().compareTo(new BigDecimal("100")) > 0)
            .asFold();

    int overHundredCount2 = overHundredFold.length(history);
    BigDecimal overHundredTotal =
        overHundredFold.foldMap(sumMonoid, FoldUsageExample::lineTotal, history);
    System.out.println("Lines priced over £100: " + overHundredCount2);
    System.out.println("Total of lines priced over £100: £" + overHundredTotal);

    System.out.println("\n=== END OF EXAMPLE ===");
  }
}
// ANCHOR_END: complete_example
