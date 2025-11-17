// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
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
 */
public class FilteredTraversalExample {

  // Domain models for a SaaS platform
  public record User(String name, boolean active, int score, SubscriptionTier tier) {
    User grantBonus() {
      return new User(name, active, score + 100, tier);
    }
  }

  public record Invoice(String id, double amount, boolean overdue) {}

  public record Customer(String name, List<Invoice> invoices) {}

  public record Order(List<Item> items) {}

  public record Item(String name, int price, String category) {}

  public enum SubscriptionTier {
    FREE,
    BASIC,
    PREMIUM,
    ENTERPRISE
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
  }

  private static void demonstrateBasicFiltering() {
    System.out.println("--- Basic Filtering with filtered() ---");

    Traversal<List<User>, User> allUsers = Traversals.forList();
    Traversal<List<User>, User> activeUsers = allUsers.filtered(User::active);

    List<User> users =
        List.of(
            new User("Alice", true, 100, SubscriptionTier.PREMIUM),
            new User("Bob", false, 200, SubscriptionTier.FREE),
            new User("Charlie", true, 150, SubscriptionTier.BASIC));

    System.out.println("All users: " + users);

    // Grant bonus ONLY to active users
    List<User> result = Traversals.modify(activeUsers, User::grantBonus, users);

    System.out.println("After granting bonus to active users:");
    for (User user : result) {
      System.out.println(
          "  " + user.name() + ": score=" + user.score() + " (active=" + user.active() + ")");
    }
    // Alice: 200 (bonus applied), Bob: 200 (unchanged), Charlie: 250 (bonus applied)
    System.out.println();
  }

  private static void demonstratePreservedVsExcluded() {
    System.out.println("--- Semantics: Preserved vs Excluded ---");

    Traversal<List<User>, User> activeUsers = Traversals.<User>forList().filtered(User::active);

    List<User> users =
        List.of(
            new User("Alice", true, 100, SubscriptionTier.PREMIUM),
            new User("Bob", false, 200, SubscriptionTier.FREE),
            new User("Charlie", true, 150, SubscriptionTier.BASIC));

    // MODIFY: Non-matching elements PRESERVED
    List<User> modified = Traversals.modify(activeUsers, User::grantBonus, users);
    System.out.println("After modify (structure PRESERVED):");
    System.out.println("  Size: " + modified.size() + " (same as original)");
    System.out.println("  Bob still in list: " + modified.get(1).name());

    // QUERY: Non-matching elements EXCLUDED
    List<User> gotten = Traversals.getAll(activeUsers, users);
    System.out.println("After getAll (non-matching EXCLUDED):");
    System.out.println("  Size: " + gotten.size() + " (only active users)");
    System.out.println("  Names: " + gotten.stream().map(User::name).toList());
    System.out.println();
  }

  private static void demonstrateFilteredComposition() {
    System.out.println("--- Composing Filtered Traversals ---");

    Traversal<List<User>, User> allUsers = Traversals.forList();
    Lens<User, String> nameLens =
        Lens.of(User::name, (u, n) -> new User(n, u.active(), u.score(), u.tier()));

    // Compose: list -> filtered active users -> name
    Traversal<List<User>, String> activeUserNames =
        allUsers.filtered(User::active).andThen(nameLens.asTraversal());

    List<User> users =
        List.of(
            new User("alice", true, 100, SubscriptionTier.PREMIUM),
            new User("bob", false, 200, SubscriptionTier.FREE),
            new User("charlie", true, 150, SubscriptionTier.BASIC));

    // Get only active user names
    List<String> names = Traversals.getAll(activeUserNames, users);
    System.out.println("Active user names: " + names);

    // Uppercase only active user names
    List<User> result = Traversals.modify(activeUserNames, String::toUpperCase, users);
    System.out.println("After uppercasing active user names:");
    for (User user : result) {
      System.out.println("  " + user.name() + " (active=" + user.active() + ")");
    }
    // ALICE (active), bob (inactive, unchanged), CHARLIE (active)
    System.out.println();
  }

  private static void demonstrateChainedFilters() {
    System.out.println("--- Chaining Multiple Filters ---");

    Traversal<List<User>, User> allUsers = Traversals.forList();

    // Chain: active AND high score AND premium/enterprise tier
    Traversal<List<User>, User> targetUsers =
        allUsers
            .filtered(User::active)
            .filtered(user -> user.score() > 120)
            .filtered(
                user ->
                    user.tier() == SubscriptionTier.PREMIUM
                        || user.tier() == SubscriptionTier.ENTERPRISE);

    List<User> users =
        List.of(
            new User("Alice", true, 100, SubscriptionTier.PREMIUM), // active but low score
            new User("Bob", false, 200, SubscriptionTier.ENTERPRISE), // high score but inactive
            new User(
                "Charlie", true, 150, SubscriptionTier.BASIC), // active, high score, wrong tier
            new User("Diana", true, 180, SubscriptionTier.PREMIUM), // matches all criteria
            new User("Eve", true, 200, SubscriptionTier.ENTERPRISE) // matches all criteria
            );

    List<User> result = Traversals.getAll(targetUsers, users);
    System.out.println("Users matching all criteria (active, score>120, premium/enterprise):");
    for (User user : result) {
      System.out.println(
          "  " + user.name() + " (score=" + user.score() + ", tier=" + user.tier() + ")");
    }
    System.out.println();
  }

  private static void demonstrateStaticCombinator() {
    System.out.println("--- Static Combinator: Traversals.filtered() ---");

    // Create reusable filter
    Traversal<User, User> activeFilter = Traversals.filtered(User::active);

    User activeUser = new User("Alice", true, 100, SubscriptionTier.PREMIUM);
    User inactiveUser = new User("Bob", false, 200, SubscriptionTier.FREE);

    // Standalone usage
    User result1 = Traversals.modify(activeFilter, User::grantBonus, activeUser);
    System.out.println("Active user after bonus: " + result1.score() + " (was 100)");

    User result2 = Traversals.modify(activeFilter, User::grantBonus, inactiveUser);
    System.out.println("Inactive user after bonus: " + result2.score() + " (unchanged)");

    // Compose into pipeline
    Lens<User, String> nameLens =
        Lens.of(User::name, (u, n) -> new User(n, u.active(), u.score(), u.tier()));

    Traversal<List<User>, String> activeUserNames =
        Traversals.<User>forList()
            .andThen(Traversals.filtered(User::active)) // Static combinator
            .andThen(nameLens.asTraversal());

    List<User> users =
        List.of(
            new User("alice", true, 100, SubscriptionTier.PREMIUM),
            new User("bob", false, 200, SubscriptionTier.FREE));

    List<String> names = Traversals.getAll(activeUserNames, users);
    System.out.println("Active user names via static combinator: " + names);
    System.out.println();
  }

  private static void demonstrateFilterByNested() {
    System.out.println("--- Advanced: filterBy() for Nested Queries ---");

    Traversal<List<Customer>, Customer> allCustomers = Traversals.forList();
    Fold<Customer, Invoice> customerInvoices = Fold.of(Customer::invoices);

    // Filter customers who have ANY overdue invoice
    Traversal<List<Customer>, Customer> customersWithOverdue =
        allCustomers.filterBy(customerInvoices, Invoice::overdue);

    List<Customer> customers =
        List.of(
            new Customer(
                "Alice",
                List.of(
                    new Invoice("INV-001", 100.0, false), new Invoice("INV-002", 200.0, false))),
            new Customer(
                "Bob",
                List.of(
                    new Invoice("INV-003", 150.0, true), // OVERDUE!
                    new Invoice("INV-004", 50.0, false))),
            new Customer("Charlie", List.of(new Invoice("INV-005", 300.0, false))));

    List<Customer> withOverdue = Traversals.getAll(customersWithOverdue, customers);
    System.out.println("Customers with overdue invoices:");
    for (Customer customer : withOverdue) {
      System.out.println("  " + customer.name());
    }
    // Only Bob

    // Example: Orders with expensive items
    Traversal<List<Order>, Order> allOrders = Traversals.forList();
    Fold<Order, Item> orderItems = Fold.of(Order::items);

    Traversal<List<Order>, Order> ordersWithExpensive =
        allOrders.filterBy(orderItems, item -> item.price() > 500);

    List<Order> orders =
        List.of(
            new Order(List.of(new Item("Apple", 50, "Food"), new Item("Banana", 30, "Food"))),
            new Order(List.of(new Item("Laptop", 1000, "Electronics"))),
            new Order(List.of(new Item("Book", 20, "Books"))));

    List<Order> expensive = Traversals.getAll(ordersWithExpensive, orders);
    System.out.println("Orders with items over $500: " + expensive.size() + " order(s)");
    System.out.println();
  }

  private static void demonstrateFoldFiltering() {
    System.out.println("--- Fold Filtering for Read-Only Queries ---");

    Fold<Order, Item> itemsFold = Fold.of(Order::items);
    Fold<Order, Item> expensiveItems = itemsFold.filtered(item -> item.price() > 100);

    Order order =
        new Order(
            List.of(
                new Item("Apple", 50, "Food"),
                new Item("Laptop", 1000, "Electronics"),
                new Item("Book", 25, "Books"),
                new Item("Phone", 800, "Electronics")));

    System.out.println("Order items: " + order.items().stream().map(Item::name).toList());

    // Query only expensive items
    List<Item> expensive = expensiveItems.getAll(order);
    System.out.println("Expensive items (>$100): " + expensive.stream().map(Item::name).toList());

    int count = expensiveItems.length(order);
    System.out.println("Count of expensive items: " + count);

    int totalExpensive = expensiveItems.foldMap(Monoids.integerAddition(), Item::price, order);
    System.out.println("Total value of expensive items: $" + totalExpensive);

    boolean allElectronics =
        expensiveItems.all(item -> item.category().equals("Electronics"), order);
    System.out.println("All expensive items are Electronics: " + allElectronics);

    System.out.println("\n=== Filtered optics enable declarative, composable data operations ===");
  }
}
