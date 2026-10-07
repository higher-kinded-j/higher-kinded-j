// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.filtered;

// ANCHOR: imports
import java.util.List;
import org.higherkindedj.hkt.Monoids;
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

  public record Item(String name, int price, String category, boolean premium) {}

  public record Order(String id, List<Item> items, double total) {}

  public record Customer(String name, List<Order> orders, boolean vip) {}

  // Reusable optics
  private static final Fold<Customer, Order> CUSTOMER_ORDERS = Fold.of(Customer::orders);
  private static final Fold<Order, Item> ORDER_ITEMS = Fold.of(Order::items);
  private static final Fold<Customer, Item> ALL_CUSTOMER_ITEMS =
      CUSTOMER_ORDERS.andThen(ORDER_ITEMS);

  public static void main(String[] args) {
    List<Customer> customers = createSampleData();

    System.out.println("=== CUSTOMER ANALYTICS WITH FILTERED OPTICS ===\n");

    // --- Analysis 1: High-Value Customer Identification ---
    System.out.println("--- Analysis 1: High-Value Customers ---");

    Traversal<List<Customer>, Customer> allCustomers = Traversals.forList();
    Fold<Customer, Double> orderTotals = CUSTOMER_ORDERS.andThen(Getter.of(Order::total).asFold());

    // Customers with any order over £500
    Traversal<List<Customer>, Customer> bigSpenders =
        allCustomers.filterBy(orderTotals, total -> total > 500);

    List<Customer> highValue = Traversals.getAll(bigSpenders, customers);
    System.out.println(
        "Customers with orders over £500: " + highValue.stream().map(Customer::name).toList());

    // --- Analysis 2: Premium Product Buyers ---
    System.out.println("\n--- Analysis 2: Premium Product Buyers ---");

    Fold<Customer, Item> premiumItems = ALL_CUSTOMER_ITEMS.filtered(Item::premium);

    for (Customer customer : customers) {
      int premiumCount = premiumItems.length(customer);
      if (premiumCount > 0) {
        double premiumSpend =
            premiumItems.foldMap(Monoids.doubleAddition(), item -> (double) item.price(), customer);
        System.out.printf(
            "%s: %d premium items, £%.2f total%n", customer.name(), premiumCount, premiumSpend);
      }
    }

    // --- Analysis 3: Category-Specific Queries ---
    System.out.println("\n--- Analysis 3: Electronics Spending ---");

    Fold<Customer, Item> electronicsItems =
        ALL_CUSTOMER_ITEMS.filtered(item -> "Electronics".equals(item.category()));

    for (Customer customer : customers) {
      double electronicsSpend =
          electronicsItems.foldMap(
              Monoids.doubleAddition(), item -> (double) item.price(), customer);
      if (electronicsSpend > 0) {
        System.out.printf("%s spent £%.2f on Electronics%n", customer.name(), electronicsSpend);
      }
    }

    // --- Analysis 4: Mark VIP Customers ---
    System.out.println("\n--- Analysis 4: Auto-Mark VIP Customers ---");

    // Customers who bought premium items AND have any order over £300
    Traversal<List<Customer>, Customer> potentialVIPs =
        allCustomers
            .filterBy(ALL_CUSTOMER_ITEMS, Item::premium) // Has premium items
            .filterBy(orderTotals, total -> total > 300); // Has high-value orders

    Lens<Customer, Boolean> vipLens =
        Lens.of(Customer::vip, (c, v) -> new Customer(c.name(), c.orders(), v));

    List<Customer> updatedCustomers =
        Traversals.modify(potentialVIPs.andThen(vipLens), _ -> true, customers);

    for (Customer c : updatedCustomers) {
      if (c.vip()) {
        System.out.println(c.name() + " is now VIP");
      }
    }

    // --- Analysis 5: Aggregated Statistics ---
    System.out.println("\n--- Analysis 5: Platform Statistics ---");

    Fold<List<Customer>, Customer> customerFold = Fold.of(list -> list);
    Fold<List<Customer>, Item> allItems = customerFold.andThen(ALL_CUSTOMER_ITEMS);

    Fold<List<Customer>, Item> expensiveItems = allItems.filtered(i -> i.price() > 100);
    Fold<List<Customer>, Item> cheapItems = allItems.filtered(i -> i.price() <= 100);

    int totalExpensive = expensiveItems.length(customers);
    int totalCheap = cheapItems.length(customers);
    double expensiveRevenue =
        expensiveItems.foldMap(Monoids.doubleAddition(), i -> (double) i.price(), customers);

    System.out.printf(
        "Expensive items (>£100): %d items, £%.2f revenue%n", totalExpensive, expensiveRevenue);
    System.out.printf("Budget items (≤£100): %d items%n", totalCheap);

    System.out.println("\n=== END OF ANALYTICS ===");
  }

  private static List<Customer> createSampleData() {
    return List.of(
        new Customer(
            "Alice",
            List.of(
                new Order(
                    "A1",
                    List.of(
                        new Item("Laptop", 999, "Electronics", true),
                        new Item("Mouse", 25, "Electronics", false)),
                    1024.0),
                new Order("A2", List.of(new Item("Desk", 350, "Furniture", false)), 350.0)),
            false),
        new Customer(
            "Bob",
            List.of(
                new Order(
                    "B1",
                    List.of(
                        new Item("Book", 20, "Books", false),
                        new Item("Pen", 5, "Stationery", false)),
                    25.0)),
            false),
        new Customer(
            "Charlie",
            List.of(
                new Order(
                    "C1",
                    List.of(
                        new Item("Phone", 800, "Electronics", true),
                        new Item("Case", 50, "Accessories", false)),
                    850.0),
                new Order("C2", List.of(new Item("Headphones", 250, "Electronics", true)), 250.0)),
            false));
  }
}
// ANCHOR_END: customer_analytics
