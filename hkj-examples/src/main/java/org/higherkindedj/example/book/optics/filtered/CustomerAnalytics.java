// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.filtered;

// ANCHOR: imports
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

// ANCHOR_END: imports

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/filtered_optics.html">Filtered Optics</a>
 * page, in its customer analytics dashboard. The page {@code {{#include}}}s the anchored region,
 * and shows what {@code main} prints from a golden file the book's output gate holds to it.
 */
// ANCHOR: customer_analytics
public class CustomerAnalytics {

  // The dashboard's view of one customer: their orders, and the VIP mark the dashboard sets
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
          Map.of(
              "LAPTOP", "Electronics",
              "MOUSE", "Electronics",
              "DESK", "Furniture",
              "BOOK", "Books",
              "PEN", "Stationery",
              "PHONE", "Electronics",
              "CASE", "Accessories",
              "HEADPHONES", "Electronics"),
          Set.of("LAPTOP", "PHONE", "HEADPHONES"));

  // Monoids has no BigDecimal sum, so the dashboard writes its own
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
  private static final Fold<Order, LineItem> ORDER_LINES = Fold.of(Order::lines);
  private static final Fold<CustomerHistory, LineItem> ALL_HISTORY_LINES =
      HISTORY_ORDERS.andThen(ORDER_LINES);

  // An order stores no total, so a getter computes one from its lines
  private static final Getter<Order, BigDecimal> ORDER_TOTAL =
      Getter.of(order -> ORDER_LINES.foldMap(MONEY, CustomerAnalytics::lineTotal, order));

  private static BigDecimal lineTotal(LineItem line) {
    return line.price().multiply(BigDecimal.valueOf(line.quantity()));
  }

  public static void main(String[] args) {
    List<CustomerHistory> histories = createSampleData();

    System.out.println("=== CUSTOMER ANALYTICS WITH FILTERED OPTICS ===\n");

    // --- Analysis 1: High-Value Customer Identification ---
    System.out.println("--- Analysis 1: High-Value Customers ---");

    Traversal<List<CustomerHistory>, CustomerHistory> allHistories = Traversals.forList();
    Fold<CustomerHistory, BigDecimal> orderTotals = HISTORY_ORDERS.andThen(ORDER_TOTAL.asFold());

    // Customers with any order over £500
    Traversal<List<CustomerHistory>, CustomerHistory> bigSpenders =
        allHistories.filterBy(orderTotals, total -> total.compareTo(new BigDecimal("500")) > 0);

    List<CustomerHistory> highValue = Traversals.getAll(bigSpenders, histories);
    System.out.println(
        "Customers with orders over £500: "
            + highValue.stream().map(h -> h.customer().name()).toList());

    // --- Analysis 2: Premium Product Buyers ---
    System.out.println("\n--- Analysis 2: Premium Product Buyers ---");

    Fold<CustomerHistory, LineItem> premiumLines = ALL_HISTORY_LINES.filtered(CATALOGUE::premium);

    for (CustomerHistory history : histories) {
      int premiumCount = premiumLines.length(history);
      if (premiumCount > 0) {
        BigDecimal premiumSpend =
            premiumLines.foldMap(MONEY, CustomerAnalytics::lineTotal, history);
        System.out.printf(
            "%s: %d premium items, £%.2f total%n",
            history.customer().name(), premiumCount, premiumSpend);
      }
    }

    // --- Analysis 3: Category-Specific Queries ---
    System.out.println("\n--- Analysis 3: Electronics Spending ---");

    Fold<CustomerHistory, LineItem> electronicsLines =
        ALL_HISTORY_LINES.filtered(line -> "Electronics".equals(CATALOGUE.category(line)));

    for (CustomerHistory history : histories) {
      BigDecimal electronicsSpend =
          electronicsLines.foldMap(MONEY, CustomerAnalytics::lineTotal, history);
      if (electronicsSpend.signum() > 0) {
        System.out.printf(
            "%s spent £%.2f on Electronics%n", history.customer().name(), electronicsSpend);
      }
    }

    // --- Analysis 4: Mark VIP Customers ---
    System.out.println("\n--- Analysis 4: Auto-Mark VIP Customers ---");

    // Customers who bought premium items AND have any order over £300
    Traversal<List<CustomerHistory>, CustomerHistory> potentialVIPs =
        allHistories
            .filterBy(ALL_HISTORY_LINES, CATALOGUE::premium) // Has premium items
            .filterBy(orderTotals, total -> total.compareTo(new BigDecimal("300")) > 0);

    Lens<CustomerHistory, Boolean> vipLens =
        Lens.of(CustomerHistory::vip, (h, v) -> new CustomerHistory(h.customer(), h.orders(), v));

    List<CustomerHistory> updatedHistories =
        Traversals.modify(potentialVIPs.andThen(vipLens), _ -> true, histories);

    for (CustomerHistory h : updatedHistories) {
      if (h.vip()) {
        System.out.println(h.customer().name() + " is now VIP");
      }
    }

    // --- Analysis 5: Aggregated Statistics ---
    System.out.println("\n--- Analysis 5: Platform Statistics ---");

    Fold<List<CustomerHistory>, CustomerHistory> historyFold = Fold.of(list -> list);
    Fold<List<CustomerHistory>, LineItem> allLines = historyFold.andThen(ALL_HISTORY_LINES);

    BigDecimal threshold = new BigDecimal("100");
    Fold<List<CustomerHistory>, LineItem> expensiveLines =
        allLines.filtered(line -> line.price().compareTo(threshold) > 0);
    Fold<List<CustomerHistory>, LineItem> cheapLines =
        allLines.filtered(line -> line.price().compareTo(threshold) <= 0);

    int totalExpensive = expensiveLines.length(histories);
    int totalCheap = cheapLines.length(histories);
    BigDecimal expensiveRevenue =
        expensiveLines.foldMap(MONEY, CustomerAnalytics::lineTotal, histories);

    System.out.printf(
        "Expensive items (>£100): %d items, £%.2f revenue%n", totalExpensive, expensiveRevenue);
    System.out.printf("Budget items (≤£100): %d items%n", totalCheap);

    System.out.println("\n=== END OF ANALYTICS ===");
  }

  private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private static final Instant PLACED_AT = Instant.parse("2026-10-01T09:00:00Z");

  private static final Currency GBP = Currency.getInstance("GBP");

  private static Order order(Customer customer, LineItem... lines) {
    return new Order(ORDER_ID, customer, List.of(lines), PLACED_AT, GBP, OrderStatus.PAID);
  }

  private static LineItem line(String sku, String price) {
    return new LineItem(sku, 1, new BigDecimal(price));
  }

  private static List<CustomerHistory> createSampleData() {
    Customer alice = new Customer("Alice", new EmailAddress("alice@example.com"));
    Customer bob = new Customer("Bob", new EmailAddress("bob@example.com"));
    Customer charlie = new Customer("Charlie", new EmailAddress("charlie@example.com"));
    return List.of(
        new CustomerHistory(
            alice,
            List.of(
                order(alice, line("LAPTOP", "999.00"), line("MOUSE", "25.00")),
                order(alice, line("DESK", "350.00"))),
            false),
        new CustomerHistory(
            bob, List.of(order(bob, line("BOOK", "20.00"), line("PEN", "5.00"))), false),
        new CustomerHistory(
            charlie,
            List.of(
                order(charlie, line("PHONE", "800.00"), line("CASE", "50.00")),
                order(charlie, line("HEADPHONES", "250.00"))),
            false));
  }
}
// ANCHOR_END: customer_analytics
