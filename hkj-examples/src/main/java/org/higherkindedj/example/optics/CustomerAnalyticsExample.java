// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

/**
 * A comprehensive real-world example demonstrating filtered optics for customer analytics.
 *
 * <p>This example models a SaaS platform's customer management system and showcases how filtered
 * optics enable complex business logic through composable, declarative operations:
 *
 * <ul>
 *   <li><b>Customer segmentation</b>: Target specific customer groups based on spending thresholds
 *   <li><b>Nested filtering</b>: Find customers with specific purchase patterns using filterBy
 *   <li><b>Aggregation queries</b>: Calculate metrics like total spending, average order value
 *   <li><b>Bulk updates</b>: Apply promotions or status changes to filtered subsets
 * </ul>
 *
 * <p>Key patterns demonstrated:
 *
 * <ul>
 *   <li>Composing filtered traversals with lenses for targeted modifications
 *   <li>Using filterBy to query nested structures (customers with overdue invoices)
 *   <li>Chaining multiple filters to express complex business rules
 *   <li>Fold filtering for read-only analytics queries
 *   <li>Monoid-based aggregation for type-safe calculations
 * </ul>
 */
public class CustomerAnalyticsExample {

  // Domain models for a SaaS customer management system
  public record Customer(String name, List<Purchase> purchases, boolean vip, double totalSpending) {
    Customer markAsVip() {
      return new Customer(name, purchases, true, totalSpending);
    }

    Customer applyDiscount(double percentage) {
      double newSpending = totalSpending * (1 - percentage / 100);
      return new Customer(name, purchases, vip, newSpending);
    }
  }

  public record Purchase(String productName, double amount, String category, boolean premium) {}

  public record Invoice(String id, double amount, boolean overdue, String customerId) {}

  public record AnalyticsReport(
      int totalCustomers,
      int vipCustomers,
      double totalRevenue,
      double averageSpending,
      List<String> highValueCustomers) {}

  public static void main(String[] args) {
    System.out.println("=== Customer Analytics with Filtered Optics ===\n");

    List<Customer> customers = createSampleCustomers();

    demonstrateVipSegmentation(customers);
    demonstratePremiumBuyerAnalysis(customers);
    demonstrateComplexFiltering(customers);
    demonstrateFilterByWithNestedQueries(customers);
    demonstrateAggregationWithFilters(customers);
    demonstrateBulkUpdates(customers);
  }

  private static List<Customer> createSampleCustomers() {
    return List.of(
        new Customer(
            "Alice",
            List.of(
                new Purchase("Laptop", 1200.0, "Electronics", true),
                new Purchase("Headphones", 150.0, "Electronics", false)),
            false,
            1350.0),
        new Customer(
            "Bob",
            List.of(
                new Purchase("Book", 25.0, "Books", false),
                new Purchase("Coffee Maker", 80.0, "Appliances", false)),
            false,
            105.0),
        new Customer(
            "Charlie",
            List.of(
                new Purchase("Phone", 900.0, "Electronics", true),
                new Purchase("Tablet", 600.0, "Electronics", true),
                new Purchase("Case", 30.0, "Accessories", false)),
            true,
            1530.0),
        new Customer(
            "Diana", List.of(new Purchase("Desk", 400.0, "Furniture", false)), false, 400.0),
        new Customer(
            "Eve",
            List.of(
                new Purchase("Monitor", 500.0, "Electronics", true),
                new Purchase("Keyboard", 120.0, "Electronics", false),
                new Purchase("Mouse", 60.0, "Electronics", false)),
            false,
            680.0));
  }

  private static void demonstrateVipSegmentation(List<Customer> customers) {
    System.out.println("--- VIP Customer Segmentation ---");

    Traversal<List<Customer>, Customer> allCustomers = Traversals.forList();

    // Mark high spenders (>$1000) as VIP
    Traversal<List<Customer>, Customer> highSpenders =
        allCustomers.filtered(c -> c.totalSpending() > 1000);

    List<Customer> result = Traversals.modify(highSpenders, Customer::markAsVip, customers);

    System.out.println("After marking high spenders as VIP:");
    for (Customer customer : result) {
      System.out.println(
          "  "
              + customer.name()
              + ": $"
              + customer.totalSpending()
              + " (VIP="
              + customer.vip()
              + ")");
    }
    // Alice: VIP=true (>$1000), Bob: VIP=false, Charlie: VIP=true (already was + >$1000)
    // Diana: VIP=false, Eve: VIP=false
    System.out.println();
  }

  private static void demonstratePremiumBuyerAnalysis(List<Customer> customers) {
    System.out.println("--- Premium Buyer Analysis ---");

    Traversal<List<Customer>, Customer> allCustomers = Traversals.forList();
    Fold<Customer, Purchase> customerPurchases = Fold.of(Customer::purchases);

    // Find customers with ANY premium purchase
    Traversal<List<Customer>, Customer> premiumBuyers =
        allCustomers.filterBy(customerPurchases, Purchase::premium);

    List<Customer> premiumCustomers = Traversals.getAll(premiumBuyers, customers);

    System.out.println("Customers with premium purchases:");
    for (Customer customer : premiumCustomers) {
      List<String> premiumItems =
          customer.purchases().stream()
              .filter(Purchase::premium)
              .map(Purchase::productName)
              .toList();
      System.out.println("  " + customer.name() + ": " + premiumItems);
    }
    // Alice (Laptop), Charlie (Phone, Tablet), Eve (Monitor)
    System.out.println();
  }

  private static void demonstrateComplexFiltering(List<Customer> customers) {
    System.out.println("--- Complex Business Rules with Chained Filters ---");

    Traversal<List<Customer>, Customer> allCustomers = Traversals.forList();

    // Find customers who: have >$500 spending AND are not VIP AND have multiple purchases
    Traversal<List<Customer>, Customer> targetCustomers =
        allCustomers
            .filtered(c -> c.totalSpending() > 500)
            .filtered(c -> !c.vip())
            .filtered(c -> c.purchases().size() > 1);

    List<Customer> targets = Traversals.getAll(targetCustomers, customers);

    System.out.println("Non-VIP customers with >$500 spending and multiple purchases:");
    for (Customer customer : targets) {
      System.out.println(
          "  "
              + customer.name()
              + ": $"
              + customer.totalSpending()
              + " ("
              + customer.purchases().size()
              + " purchases)");
    }
    // Alice: $1350 (2 purchases), Eve: $680 (3 purchases)
    System.out.println();
  }

  private static void demonstrateFilterByWithNestedQueries(List<Customer> customers) {
    System.out.println("--- FilterBy: Nested Structure Queries ---");

    Traversal<List<Customer>, Customer> allCustomers = Traversals.forList();
    Fold<Customer, Purchase> customerPurchases = Fold.of(Customer::purchases);

    // Find customers with electronics purchases over $500
    Traversal<List<Customer>, Customer> bigElectronicsBuyers =
        allCustomers.filterBy(
            customerPurchases, p -> p.category().equals("Electronics") && p.amount() > 500);

    List<Customer> result = Traversals.getAll(bigElectronicsBuyers, customers);

    System.out.println("Customers with electronics purchases over $500:");
    for (Customer customer : result) {
      List<String> expensiveElectronics =
          customer.purchases().stream()
              .filter(p -> p.category().equals("Electronics") && p.amount() > 500)
              .map(p -> p.productName() + " ($" + p.amount() + ")")
              .toList();
      System.out.println("  " + customer.name() + ": " + expensiveElectronics);
    }
    // Alice (Laptop $1200), Charlie (Phone $900, Tablet $600)
    System.out.println();
  }

  private static void demonstrateAggregationWithFilters(List<Customer> customers) {
    System.out.println("--- Aggregation with Fold Filtering ---");

    Fold<List<Customer>, Customer> allCustomersFold = Fold.of(c -> c);

    // Calculate total spending of VIP customers
    Fold<List<Customer>, Customer> vipCustomersFold = allCustomersFold.filtered(Customer::vip);

    double vipRevenue =
        vipCustomersFold.foldMap(Monoids.doubleAddition(), Customer::totalSpending, customers);
    int vipCount = vipCustomersFold.foldMap(Monoids.integerAddition(), c -> 1, customers);

    System.out.println("VIP Customer Metrics:");
    System.out.println("  Total VIP customers: " + vipCount);
    System.out.println("  Total VIP revenue: $" + vipRevenue);

    // Calculate spending from customers with >3 purchases
    Fold<List<Customer>, Customer> frequentBuyers =
        allCustomersFold.filtered(c -> c.purchases().size() >= 3);

    double frequentBuyerRevenue =
        frequentBuyers.foldMap(Monoids.doubleAddition(), Customer::totalSpending, customers);
    int frequentBuyerCount = frequentBuyers.foldMap(Monoids.integerAddition(), c -> 1, customers);

    System.out.println("\nFrequent Buyer Metrics (3+ purchases):");
    System.out.println("  Count: " + frequentBuyerCount);
    System.out.println("  Total revenue: $" + frequentBuyerRevenue);
    if (frequentBuyerCount > 0) {
      System.out.println("  Average spending: $" + (frequentBuyerRevenue / frequentBuyerCount));
    }

    // Nested fold: sum of all electronics purchases
    Fold<Customer, Purchase> purchasesFold = Fold.of(Customer::purchases);
    Fold<Customer, Purchase> electronicsPurchases =
        purchasesFold.filtered(p -> p.category().equals("Electronics"));

    double totalElectronicsSpending = 0;
    for (Customer customer : customers) {
      totalElectronicsSpending +=
          electronicsPurchases.foldMap(Monoids.doubleAddition(), Purchase::amount, customer);
    }

    System.out.println("\nElectronics Category Metrics:");
    System.out.println("  Total electronics spending: $" + totalElectronicsSpending);
    System.out.println();
  }

  private static void demonstrateBulkUpdates(List<Customer> customers) {
    System.out.println("--- Bulk Updates with Filtered Traversals ---");

    Traversal<List<Customer>, Customer> allCustomers = Traversals.forList();
    Lens<Customer, Double> spendingLens =
        Lens.of(
            Customer::totalSpending, (c, s) -> new Customer(c.name(), c.purchases(), c.vip(), s));

    // Apply 10% discount to high spenders (>$1000)
    Traversal<List<Customer>, Double> highSpenderSpending =
        allCustomers.filtered(c -> c.totalSpending() > 1000).andThen(spendingLens.asTraversal());

    List<Customer> afterDiscount =
        Traversals.modify(highSpenderSpending, spending -> spending * 0.9, customers);

    System.out.println("After applying 10% discount to high spenders:");
    for (int i = 0; i < customers.size(); i++) {
      Customer before = customers.get(i);
      Customer after = afterDiscount.get(i);
      if (before.totalSpending() != after.totalSpending()) {
        System.out.println(
            "  "
                + after.name()
                + ": $"
                + before.totalSpending()
                + " -> $"
                + after.totalSpending()
                + " (DISCOUNTED)");
      } else {
        System.out.println("  " + after.name() + ": $" + after.totalSpending() + " (unchanged)");
      }
    }

    // Apply loyalty bonus to frequent buyers (3+ purchases)
    Traversal<List<Customer>, Customer> frequentBuyers =
        allCustomers.filtered(c -> c.purchases().size() >= 3);

    List<Customer> afterBonus =
        Traversals.modify(frequentBuyers, c -> c.applyDiscount(5), customers);

    System.out.println("\nAfter applying 5% loyalty bonus to frequent buyers (3+ purchases):");
    for (int i = 0; i < customers.size(); i++) {
      Customer before = customers.get(i);
      Customer after = afterBonus.get(i);
      if (before.totalSpending() != after.totalSpending()) {
        System.out.println(
            "  "
                + after.name()
                + ": $"
                + before.totalSpending()
                + " -> $"
                + after.totalSpending()
                + " (BONUS APPLIED)");
      } else {
        System.out.println("  " + after.name() + ": $" + after.totalSpending() + " (unchanged)");
      }
    }

    System.out.println("\n=== Filtered optics enable declarative, type-safe business logic ===");
  }
}
