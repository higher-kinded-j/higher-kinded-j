// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
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
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

/**
 * A comprehensive real-world example demonstrating filtered optics for customer analytics.
 *
 * <p>This example analyses the order service's customers, on the Optics chapter's cast, and
 * showcases how filtered optics enable complex business logic through composable, declarative
 * operations:
 *
 * <ul>
 *   <li><b>Customer segmentation</b>: Target specific customer groups based on spending thresholds
 *   <li><b>Nested filtering</b>: Find customers with specific purchase patterns using filterBy
 *   <li><b>Aggregation queries</b>: Calculate metrics like total spending, average spending
 *   <li><b>Bulk updates</b>: Apply discounts or status changes to filtered subsets
 * </ul>
 *
 * <p>Key patterns demonstrated:
 *
 * <ul>
 *   <li>Composing filtered traversals with lenses for targeted modifications
 *   <li>Using filterBy to query nested structures (customers with big electronics lines)
 *   <li>Chaining multiple filters to express complex business rules
 *   <li>Fold filtering for read-only analytics queries
 *   <li>Monoid-based aggregation for type-safe calculations
 * </ul>
 */
public class CustomerAnalyticsExample {

  // A customer's orders, kept beside the cast's Customer with a VIP mark
  public record CustomerHistory(Customer customer, List<Order> orders, boolean vip) {}

  // What the catalogue knows that a line does not: each SKU's category, and which are premium
  public record SkuCatalogue(Map<String, String> categories, Set<String> premiumSkus) {
    boolean premium(LineItem line) {
      return premiumSkus.contains(line.sku());
    }

    String category(LineItem line) {
      return categories.getOrDefault(line.sku(), "Uncategorised");
    }
  }

  private static final SkuCatalogue CATALOGUE =
      new SkuCatalogue(
          Map.ofEntries(
              Map.entry("LAPTOP", "Electronics"),
              Map.entry("HEADPHONES", "Electronics"),
              Map.entry("BOOK", "Books"),
              Map.entry("COFFEE-MAKER", "Appliances"),
              Map.entry("PHONE", "Electronics"),
              Map.entry("TABLET", "Electronics"),
              Map.entry("CASE", "Accessories"),
              Map.entry("DESK", "Furniture"),
              Map.entry("MONITOR", "Electronics"),
              Map.entry("KEYBOARD", "Electronics"),
              Map.entry("MOUSE", "Electronics")),
          Set.of("LAPTOP", "PHONE", "TABLET", "MONITOR"));

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

  // Reusable optics
  private static final Fold<CustomerHistory, Order> HISTORY_ORDERS =
      Fold.of(CustomerHistory::orders);
  private static final Fold<CustomerHistory, LineItem> HISTORY_LINES =
      HISTORY_ORDERS.andThen(Fold.of(Order::lines));

  private static final Lens<CustomerHistory, Boolean> VIP =
      Lens.of(CustomerHistory::vip, (h, vip) -> new CustomerHistory(h.customer(), h.orders(), vip));
  private static final Lens<CustomerHistory, List<Order>> ORDERS =
      Lens.of(
          CustomerHistory::orders,
          (h, orders) -> new CustomerHistory(h.customer(), orders, h.vip()));

  // The prices on a customer's open orders: those not yet paid
  private static final Traversal<CustomerHistory, BigDecimal> OPEN_ORDER_PRICES =
      ORDERS
          .andThen(Traversals.<Order>forList())
          .filtered(order -> order.status() == OrderStatus.NEW)
          .andThen(OrderTraversals.lines())
          .andThen(LineItemLenses.price());

  private static final BigDecimal HIGH_SPEND = new BigDecimal("1000");

  // A line's total: its price times its quantity
  private static BigDecimal lineTotal(LineItem line) {
    return line.price().multiply(BigDecimal.valueOf(line.quantity()));
  }

  // What a customer has spent across all their orders
  private static BigDecimal spend(CustomerHistory history) {
    return HISTORY_LINES.foldMap(MONEY, CustomerAnalyticsExample::lineTotal, history);
  }

  // A percentage off a price, to the penny
  private static UnaryOperator<BigDecimal> percentOff(int percent) {
    BigDecimal factor = BigDecimal.valueOf(100 - percent, 2);
    return price -> price.multiply(factor).setScale(2, RoundingMode.HALF_EVEN);
  }

  public static void main(String[] args) {
    System.out.println("=== Customer Analytics with Filtered Optics ===\n");

    List<CustomerHistory> histories = createSampleHistories();

    demonstrateVipSegmentation(histories);
    demonstratePremiumBuyerAnalysis(histories);
    demonstrateComplexFiltering(histories);
    demonstrateFilterByWithNestedQueries(histories);
    demonstrateAggregationWithFilters(histories);
    demonstrateCombinedFoldExtraction(histories);
    demonstrateBulkUpdates(histories);
  }

  private static final Instant PLACED_AT = Instant.parse("2026-10-01T09:00:00Z");

  private static final Currency GBP = Currency.getInstance("GBP");

  // Order number n, placed on the same day as the others, in pounds
  private static Order order(int number, Customer customer, OrderStatus status, LineItem... lines) {
    return new Order(new UUID(0, number), customer, List.of(lines), PLACED_AT, GBP, status);
  }

  private static LineItem line(String sku, String price) {
    return new LineItem(sku, 1, new BigDecimal(price));
  }

  private static Customer customer(String name) {
    return new Customer(name, new EmailAddress(name.toLowerCase() + "@example.com"));
  }

  private static List<CustomerHistory> createSampleHistories() {
    Customer alice = customer("Alice");
    Customer bob = customer("Bob");
    Customer charlie = customer("Charlie");
    Customer diana = customer("Diana");
    Customer eve = customer("Eve");
    return List.of(
        new CustomerHistory(
            alice,
            List.of(
                order(1, alice, OrderStatus.PAID, line("LAPTOP", "1200.00")),
                order(2, alice, OrderStatus.NEW, line("HEADPHONES", "150.00"))),
            false),
        new CustomerHistory(
            bob,
            List.of(
                order(
                    3,
                    bob,
                    OrderStatus.PAID,
                    line("BOOK", "25.00"),
                    line("COFFEE-MAKER", "80.00"))),
            false),
        new CustomerHistory(
            charlie,
            List.of(
                order(4, charlie, OrderStatus.SHIPPED, line("PHONE", "900.00")),
                order(5, charlie, OrderStatus.PAID, line("TABLET", "600.00")),
                order(6, charlie, OrderStatus.NEW, line("CASE", "30.00"))),
            true),
        new CustomerHistory(
            diana, List.of(order(7, diana, OrderStatus.NEW, line("DESK", "400.00"))), false),
        new CustomerHistory(
            eve,
            List.of(
                order(
                    8,
                    eve,
                    OrderStatus.NEW,
                    line("MONITOR", "500.00"),
                    line("KEYBOARD", "120.00"),
                    line("MOUSE", "60.00"))),
            false));
  }

  private static void demonstrateVipSegmentation(List<CustomerHistory> histories) {
    System.out.println("--- VIP Customer Segmentation ---");

    Traversal<List<CustomerHistory>, CustomerHistory> allHistories = Traversals.forList();

    // Mark high spenders (>£1000) as VIP
    Traversal<List<CustomerHistory>, Boolean> highSpenderVip =
        allHistories.filtered(h -> spend(h).compareTo(HIGH_SPEND) > 0).andThen(VIP);

    List<CustomerHistory> result = Traversals.modify(highSpenderVip, _ -> true, histories);

    System.out.println("After marking high spenders as VIP:");
    for (CustomerHistory history : result) {
      System.out.println(
          "  "
              + history.customer().name()
              + ": £"
              + spend(history)
              + " (VIP="
              + history.vip()
              + ")");
    }
    // Alice: VIP=true (>£1000), Bob: VIP=false, Charlie: VIP=true (already was + >£1000)
    // Diana: VIP=false, Eve: VIP=false
    System.out.println();
  }

  private static void demonstratePremiumBuyerAnalysis(List<CustomerHistory> histories) {
    System.out.println("--- Premium Buyer Analysis ---");

    Traversal<List<CustomerHistory>, CustomerHistory> allHistories = Traversals.forList();

    // Find customers with ANY premium line
    Traversal<List<CustomerHistory>, CustomerHistory> premiumBuyers =
        allHistories.filterBy(HISTORY_LINES, CATALOGUE::premium);

    // The SKUs of a customer's premium lines
    Fold<CustomerHistory, String> premiumSkus =
        HISTORY_LINES.filtered(CATALOGUE::premium).andThen(LineItemLenses.sku().asFold());

    List<CustomerHistory> premiumCustomers = Traversals.getAll(premiumBuyers, histories);

    System.out.println("Customers with premium lines:");
    for (CustomerHistory history : premiumCustomers) {
      System.out.println("  " + history.customer().name() + ": " + premiumSkus.getAll(history));
    }
    // Alice (LAPTOP), Charlie (PHONE, TABLET), Eve (MONITOR)
    System.out.println();
  }

  private static void demonstrateComplexFiltering(List<CustomerHistory> histories) {
    System.out.println("--- Complex Business Rules with Chained Filters ---");

    Traversal<List<CustomerHistory>, CustomerHistory> allHistories = Traversals.forList();

    // Find customers who: have >£500 spending AND are not VIP AND have more than one line
    Traversal<List<CustomerHistory>, CustomerHistory> targetCustomers =
        allHistories
            .filtered(h -> spend(h).compareTo(new BigDecimal("500")) > 0)
            .filtered(h -> !h.vip())
            .filtered(h -> HISTORY_LINES.length(h) > 1);

    List<CustomerHistory> targets = Traversals.getAll(targetCustomers, histories);

    System.out.println("Non-VIP customers with >£500 spending and more than one line:");
    for (CustomerHistory history : targets) {
      System.out.println(
          "  "
              + history.customer().name()
              + ": £"
              + spend(history)
              + " ("
              + HISTORY_LINES.length(history)
              + " lines)");
    }
    // Alice: £1350.00 (2 lines), Eve: £680.00 (3 lines)
    System.out.println();
  }

  private static void demonstrateFilterByWithNestedQueries(List<CustomerHistory> histories) {
    System.out.println("--- FilterBy: Nested Structure Queries ---");

    Traversal<List<CustomerHistory>, CustomerHistory> allHistories = Traversals.forList();

    // Find customers with electronics lines over £500
    Predicate<LineItem> bigElectronics =
        line ->
            "Electronics".equals(CATALOGUE.category(line))
                && line.price().compareTo(new BigDecimal("500")) > 0;
    Traversal<List<CustomerHistory>, CustomerHistory> bigElectronicsBuyers =
        allHistories.filterBy(HISTORY_LINES, bigElectronics);

    Fold<CustomerHistory, LineItem> bigElectronicsLines = HISTORY_LINES.filtered(bigElectronics);

    List<CustomerHistory> result = Traversals.getAll(bigElectronicsBuyers, histories);

    System.out.println("Customers with electronics lines over £500:");
    for (CustomerHistory history : result) {
      List<String> expensiveElectronics =
          bigElectronicsLines.getAll(history).stream()
              .map(line -> line.sku() + " (£" + line.price() + ")")
              .toList();
      System.out.println("  " + history.customer().name() + ": " + expensiveElectronics);
    }
    // Alice (LAPTOP £1200.00), Charlie (PHONE £900.00, TABLET £600.00)
    System.out.println();
  }

  private static void demonstrateAggregationWithFilters(List<CustomerHistory> histories) {
    System.out.println("--- Aggregation with Fold Filtering ---");

    Fold<List<CustomerHistory>, CustomerHistory> allHistoriesFold = Fold.of(h -> h);

    // Calculate total spending of VIP customers
    Fold<List<CustomerHistory>, CustomerHistory> vipFold =
        allHistoriesFold.filtered(CustomerHistory::vip);

    BigDecimal vipRevenue = vipFold.foldMap(MONEY, CustomerAnalyticsExample::spend, histories);
    int vipCount = vipFold.foldMap(Monoids.integerAddition(), h -> 1, histories);

    System.out.println("VIP Customer Metrics:");
    System.out.println("  Total VIP customers: " + vipCount);
    System.out.println("  Total VIP revenue: £" + vipRevenue);

    // Calculate spending from customers with 3 or more lines
    Fold<List<CustomerHistory>, CustomerHistory> frequentBuyers =
        allHistoriesFold.filtered(h -> HISTORY_LINES.length(h) >= 3);

    BigDecimal frequentBuyerRevenue =
        frequentBuyers.foldMap(MONEY, CustomerAnalyticsExample::spend, histories);
    int frequentBuyerCount = frequentBuyers.foldMap(Monoids.integerAddition(), h -> 1, histories);

    System.out.println("\nFrequent Buyer Metrics (3+ lines):");
    System.out.println("  Count: " + frequentBuyerCount);
    System.out.println("  Total revenue: £" + frequentBuyerRevenue);
    if (frequentBuyerCount > 0) {
      BigDecimal average =
          frequentBuyerRevenue.divide(
              BigDecimal.valueOf(frequentBuyerCount), 2, RoundingMode.HALF_EVEN);
      System.out.println("  Average spending: £" + average);
    }

    // Nested fold: sum of all electronics lines
    Fold<CustomerHistory, LineItem> electronicsLines =
        HISTORY_LINES.filtered(line -> "Electronics".equals(CATALOGUE.category(line)));

    BigDecimal totalElectronicsSpending = BigDecimal.ZERO;
    for (CustomerHistory history : histories) {
      totalElectronicsSpending =
          totalElectronicsSpending.add(
              electronicsLines.foldMap(MONEY, CustomerAnalyticsExample::lineTotal, history));
    }

    System.out.println("\nElectronics Category Metrics:");
    System.out.println("  Total electronics spending: £" + totalElectronicsSpending);
    System.out.println();
  }

  private static void demonstrateCombinedFoldExtraction(List<CustomerHistory> histories) {
    System.out.println("--- Combined Fold Extraction with Fold.plus() ---");

    Fold<List<CustomerHistory>, CustomerHistory> allHistoriesFold = Fold.of(h -> h);

    // Extract customer names
    Fold<List<CustomerHistory>, String> namesFold =
        allHistoriesFold.andThen(Fold.of(h -> List.of(h.customer().name())));

    // Extract every SKU from every customer's orders
    Fold<List<CustomerHistory>, String> skusFold =
        allHistoriesFold.andThen(HISTORY_LINES).andThen(Fold.of(line -> List.of(line.sku())));

    // Combine both: get all customer names AND all SKUs in one pass
    Fold<List<CustomerHistory>, String> allNamesFold = namesFold.plus(skusFold);

    System.out.println("All names (customers + SKUs): " + allNamesFold.getAll(histories));
    System.out.println("Total name count: " + allNamesFold.length(histories));

    // Use Fold.sum() for multiple metric extractions
    Fold<List<CustomerHistory>, BigDecimal> spendingFold =
        allHistoriesFold.andThen(Fold.of(h -> List.of(spend(h))));

    Fold<List<CustomerHistory>, BigDecimal> lineAmountsFold =
        allHistoriesFold.andThen(HISTORY_LINES).andThen(Fold.of(line -> List.of(lineTotal(line))));

    // Get all monetary values across the entire dataset
    Fold<List<CustomerHistory>, BigDecimal> allMonetaryValues =
        Fold.sum(spendingFold, lineAmountsFold);

    BigDecimal totalMonetary = allMonetaryValues.foldMap(MONEY, amount -> amount, histories);
    System.out.println("Total monetary values (spending + line amounts): £" + totalMonetary);
    System.out.println();
  }

  private static void demonstrateBulkUpdates(List<CustomerHistory> histories) {
    System.out.println("--- Bulk Updates with Filtered Traversals ---");

    Traversal<List<CustomerHistory>, CustomerHistory> allHistories = Traversals.forList();

    // Apply a 10% discount to the open orders of high spenders (>£1000)
    Traversal<List<CustomerHistory>, BigDecimal> highSpenderOpenPrices =
        allHistories.filtered(h -> spend(h).compareTo(HIGH_SPEND) > 0).andThen(OPEN_ORDER_PRICES);

    List<CustomerHistory> afterDiscount =
        Traversals.modify(highSpenderOpenPrices, percentOff(10), histories);

    System.out.println("After applying a 10% discount to high spenders' open orders:");
    printChanges(histories, afterDiscount, "DISCOUNTED");

    // Apply a 5% loyalty discount to the open orders of frequent buyers (3+ lines)
    Traversal<List<CustomerHistory>, BigDecimal> frequentBuyerOpenPrices =
        allHistories.filtered(h -> HISTORY_LINES.length(h) >= 3).andThen(OPEN_ORDER_PRICES);

    List<CustomerHistory> afterBonus =
        Traversals.modify(frequentBuyerOpenPrices, percentOff(5), histories);

    System.out.println("\nAfter applying a 5% loyalty discount to frequent buyers (3+ lines):");
    printChanges(histories, afterBonus, "BONUS APPLIED");

    System.out.println("\n=== Filtered optics enable declarative, type-safe business logic ===");
  }

  private static void printChanges(
      List<CustomerHistory> before, List<CustomerHistory> after, String label) {
    for (int i = 0; i < before.size(); i++) {
      BigDecimal was = spend(before.get(i));
      BigDecimal now = spend(after.get(i));
      String name = after.get(i).customer().name();
      if (was.compareTo(now) != 0) {
        System.out.println("  " + name + ": £" + was + " -> £" + now + " (" + label + ")");
      } else {
        System.out.println("  " + name + ": £" + now + " (unchanged)");
      }
    }
  }
}
