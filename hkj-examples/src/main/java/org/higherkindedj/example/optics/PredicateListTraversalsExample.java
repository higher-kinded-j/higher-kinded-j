// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.ListTraversals;
import org.higherkindedj.optics.util.Traversals;

/**
 * Demonstrates predicate-based list traversals for conditional focusing on list portions.
 *
 * <p>Predicate-based traversals enable runtime-determined focusing where the number of elements in
 * focus depends on data conditions rather than fixed indices:
 *
 * <ul>
 *   <li>{@code takingWhile(Predicate)} - Focus on longest prefix where predicate holds
 *   <li>{@code droppingWhile(Predicate)} - Skip prefix whilst predicate holds
 *   <li>{@code element(int)} - Safe single-element access (affine traversal)
 * </ul>
 *
 * <p>Scenarios covered:
 *
 * <ul>
 *   <li>Time-series data processing with thresholds
 *   <li>Priority queue handling
 *   <li>Log file analysis (skipping headers, extracting sections)
 *   <li>Batch processing with conditional boundaries
 *   <li>Safe indexed access without exceptions
 * </ul>
 */
public class PredicateListTraversalsExample {

  // Domain models
  record Transaction(String id, LocalDateTime timestamp, double amount, String status) {
    @Override
    public String toString() {
      return String.format(
          "Transaction[%s, %s, $%.2f, %s]",
          id, timestamp.truncatedTo(ChronoUnit.SECONDS), amount, status);
    }
  }

  record LogEntry(LocalDateTime timestamp, String level, String message) {
    boolean isWarmup() {
      return message.contains("WARMUP") || message.contains("INIT");
    }

    @Override
    public String toString() {
      return String.format(
          "[%s] %s: %s", timestamp.truncatedTo(ChronoUnit.SECONDS), level, message);
    }
  }

  record Task(String id, int priority, String status) {
    @Override
    public String toString() {
      return String.format("Task[%s, P%d, %s]", id, priority, status);
    }
  }

  record Product(String sku, String name, double price, int stock) {
    @Override
    public String toString() {
      return String.format("%s (%s): $%.2f, stock=%d", name, sku, price, stock);
    }
  }

  public static void main(String[] args) {
    System.out.println("=== PREDICATE-BASED LIST TRAVERSALS EXAMPLE ===\n");

    demonstrateTakingWhile();
    demonstrateDroppingWhile();
    demonstrateElement();
    demonstrateComposedPredicateTraversals();
    demonstrateRealWorldScenarios();

    System.out.println("\n=== PREDICATE-BASED TRAVERSALS COMPLETE ===");
  }

  private static void demonstrateTakingWhile() {
    System.out.println("--- SCENARIO 1: takingWhile() - Prefix Processing ---\n");

    // Process products while price < $20
    List<Product> products =
        List.of(
            new Product("SKU001", "Widget", 10.0, 100),
            new Product("SKU002", "Gadget", 15.0, 50),
            new Product("SKU003", "Gizmo", 25.0, 75), // Stops here
            new Product("SKU004", "Thing", 12.0, 25) // Not included
            );

    Traversal<List<Product>, Product> affordablePrefix =
        ListTraversals.takingWhile(p -> p.price() < 20.0);

    System.out.println("Original products:");
    products.forEach(p -> System.out.println("  " + p));

    // Discount affordable prefix
    List<Product> discounted =
        Traversals.modify(
            affordablePrefix,
            p -> new Product(p.sku(), p.name(), p.price() * 0.9, p.stock()),
            products);

    System.out.println("\nAfter 10% discount on affordable prefix (price < $20):");
    discounted.forEach(p -> System.out.println("  " + p));

    // Extract the prefix
    List<Product> affordable = Traversals.getAll(affordablePrefix, products);
    System.out.println("\nAffordable prefix: " + affordable.size() + " products");
    affordable.forEach(p -> System.out.println("  " + p));

    // Sorted list example
    List<Integer> sorted = List.of(1, 2, 3, 4, 10, 11, 12);
    Traversal<List<Integer>, Integer> lessThan5 = ListTraversals.takingWhile(n -> n < 5);

    List<Integer> prefix = Traversals.getAll(lessThan5, sorted);
    System.out.println("\nNumbers less than 5 (prefix): " + prefix);
    System.out.println();
  }

  private static void demonstrateDroppingWhile() {
    System.out.println("--- SCENARIO 2: droppingWhile() - Skip Prefix ---\n");

    // Skip low-stock items
    List<Product> products =
        List.of(
            new Product("SKU001", "Widget", 10.0, 20),
            new Product("SKU002", "Gadget", 25.0, 30),
            new Product("SKU003", "Gizmo", 15.0, 75), // First well-stocked
            new Product("SKU004", "Thing", 12.0, 25)); // Included

    Traversal<List<Product>, Product> wellStocked =
        ListTraversals.droppingWhile(p -> p.stock() < 50);

    System.out.println("Original products:");
    products.forEach(p -> System.out.println("  " + p));

    // Restock well-stocked items (and everything after first well-stocked)
    List<Product> restocked =
        Traversals.modify(
            wellStocked, p -> new Product(p.sku(), p.name(), p.price(), p.stock() + 50), products);

    System.out.println("\nAfter restocking (skip stock < 50 prefix):");
    restocked.forEach(p -> System.out.println("  " + p));

    // Extract focused portion
    List<Product> focused = Traversals.getAll(wellStocked, products);
    System.out.println("\nFocused products (after skipping): " + focused.size());
    focused.forEach(p -> System.out.println("  " + p));

    // Log processing example
    List<String> logLines =
        List.of(
            "[CONFIG] Loading application.properties",
            "[CONFIG] Initialising database pool",
            "[INFO] Application started",
            "[WARN] High memory usage",
            "[ERROR] Connection timeout");

    Traversal<List<String>, String> runtimeLogs =
        ListTraversals.droppingWhile(line -> line.startsWith("[CONFIG]"));

    List<String> runtime = Traversals.getAll(runtimeLogs, logLines);
    System.out.println("\nRuntime logs (skipping config preamble):");
    runtime.forEach(line -> System.out.println("  " + line));
    System.out.println();
  }

  private static void demonstrateElement() {
    System.out.println("--- SCENARIO 3: element() - Safe Single Element Access ---\n");

    List<Product> products =
        List.of(
            new Product("SKU001", "Widget", 10.0, 100),
            new Product("SKU002", "Gadget", 25.0, 50),
            new Product("SKU003", "Gizmo", 15.0, 75));

    // Access third product
    Traversal<List<Product>, Product> thirdProduct = ListTraversals.element(2);

    System.out.println("All products:");
    for (int i = 0; i < products.size(); i++) {
      System.out.println("  [" + i + "] " + products.get(i));
    }

    // Modify only the third product
    List<Product> updated =
        Traversals.modify(
            thirdProduct,
            p -> new Product(p.sku(), p.name(), p.price() * 0.8, p.stock()),
            products);

    System.out.println("\nAfter 20% discount on element at index 2:");
    for (int i = 0; i < updated.size(); i++) {
      System.out.println("  [" + i + "] " + updated.get(i));
    }

    // Extract the element
    List<Product> element = Traversals.getAll(thirdProduct, products);
    System.out.println("\nExtracted element [2]: " + element);

    // Out of bounds: gracefully returns empty
    Traversal<List<Product>, Product> outOfBounds = ListTraversals.element(10);
    List<Product> empty = Traversals.getAll(outOfBounds, products);
    System.out.println("Element at index 10 (out of bounds): " + empty);

    // Compose with nested lists
    List<List<Integer>> nested =
        List.of(List.of(1, 2, 3), List.of(10, 20, 30), List.of(100, 200, 300));

    Traversal<List<List<Integer>>, List<Integer>> secondList = ListTraversals.element(1);
    Traversal<List<List<Integer>>, Integer> secondListFirstElement =
        secondList.andThen(ListTraversals.element(0));

    List<Integer> extracted = Traversals.getAll(secondListFirstElement, nested);
    System.out.println("\nNested list access [1][0]: " + extracted);
    System.out.println();
  }

  private static void demonstrateComposedPredicateTraversals() {
    System.out.println("--- SCENARIO 4: Composed Predicate Traversals ---\n");

    List<Product> products =
        List.of(
            new Product("SKU001", "Widget", 10.0, 0), // No stock
            new Product("SKU002", "Gadget", 15.0, 50),
            new Product("SKU003", "Gizmo", 25.0, 75),
            new Product("SKU004", "Thing", 12.0, 100),
            new Product("SKU005", "Doodad", 30.0, 25),
            new Product("SKU006", "Thingamajig", 8.0, 80));

    // Combine taking with filtering - take first 4, then filter by stock and price
    Traversal<List<Product>, Product> topAffordableInStock =
        ListTraversals.<Product>taking(4)
            .filtered(p -> p.stock() > 0)
            .filtered(p -> p.price() < 20.0);

    System.out.println("All products:");
    for (int i = 0; i < products.size(); i++) {
      System.out.println(String.format("  [%d] %s", i, products.get(i)));
    }

    List<Product> focused = Traversals.getAll(topAffordableInStock, products);
    System.out.println("\nFocused (take 4, where stock > 0 and price < $20):");
    focused.forEach(p -> System.out.println("  " + p));

    // Apply discount to focused products
    List<Product> discounted =
        Traversals.modify(
            topAffordableInStock,
            p -> new Product(p.sku(), p.name(), p.price() * 0.85, p.stock()),
            products);

    System.out.println("\nAfter 15% discount on focused products:");
    for (int i = 0; i < discounted.size(); i++) {
      System.out.println(String.format("  [%d] %s", i, discounted.get(i)));
    }
    System.out.println();
  }

  private static void demonstrateRealWorldScenarios() {
    System.out.println("--- SCENARIO 5: Real-World Use Cases ---\n");

    demonstrateTimeSeriesProcessing();
    demonstratePriorityQueueHandling();
    demonstrateBatchProcessing();
  }

  private static void demonstrateTimeSeriesProcessing() {
    System.out.println("Use Case 1: Time-Series Processing with Threshold");
    System.out.println("--------------------------------------------------");

    LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
    LocalDateTime now = LocalDateTime.now();

    List<Transaction> transactions =
        List.of(
            new Transaction("TX001", now.minusHours(48), 100.0, "COMPLETED"),
            new Transaction("TX002", now.minusHours(36), 200.0, "COMPLETED"),
            new Transaction("TX003", now.minusHours(12), 150.0, "PENDING"),
            new Transaction("TX004", now.minusHours(6), 300.0, "PENDING"),
            new Transaction("TX005", now.minusHours(1), 250.0, "PENDING"));

    Traversal<List<Transaction>, Transaction> beforeCutoff =
        ListTraversals.takingWhile(t -> t.timestamp().isBefore(cutoff));

    System.out.println("All transactions:");
    transactions.forEach(t -> System.out.println("  " + t));

    // Process old transactions
    List<Transaction> processed =
        Traversals.modify(
            beforeCutoff,
            t -> new Transaction(t.id(), t.timestamp(), t.amount(), "ARCHIVED"),
            transactions);

    System.out.println("\nAfter archiving transactions before cutoff:");
    processed.forEach(t -> System.out.println("  " + t));
    System.out.println();
  }

  private static void demonstratePriorityQueueHandling() {
    System.out.println("Use Case 2: Priority Queue Processing");
    System.out.println("--------------------------------------");

    List<Task> tasks =
        List.of(
            new Task("T1", 1, "PENDING"),
            new Task("T2", 1, "PENDING"),
            new Task("T3", 2, "PENDING"), // Priority changes here
            new Task("T4", 2, "PENDING"),
            new Task("T5", 3, "PENDING"));

    Traversal<List<Task>, Task> highPriority = ListTraversals.takingWhile(t -> t.priority() == 1);

    System.out.println("All tasks:");
    tasks.forEach(t -> System.out.println("  " + t));

    // Process high-priority tasks
    List<Task> processed =
        Traversals.modify(highPriority, t -> new Task(t.id(), t.priority(), "COMPLETED"), tasks);

    System.out.println("\nAfter processing priority 1 tasks:");
    processed.forEach(t -> System.out.println("  " + t));

    // Remaining tasks
    Traversal<List<Task>, Task> remaining = ListTraversals.droppingWhile(t -> t.priority() == 1);

    List<Task> remainingTasks = Traversals.getAll(remaining, tasks);
    System.out.println("\nRemaining tasks: " + remainingTasks.size());
    remainingTasks.forEach(t -> System.out.println("  " + t));
    System.out.println();
  }

  private static void demonstrateBatchProcessing() {
    System.out.println("Use Case 3: Batch Processing with Warmup Skip");
    System.out.println("----------------------------------------------");

    LocalDateTime now = LocalDateTime.now();
    List<LogEntry> logs =
        List.of(
            new LogEntry(now.minusMinutes(10), "INFO", "WARMUP: Initialising cache"),
            new LogEntry(now.minusMinutes(9), "INFO", "WARMUP: Loading data"),
            new LogEntry(now.minusMinutes(8), "INFO", "INIT: System ready"),
            new LogEntry(now.minusMinutes(7), "INFO", "Processing request 1"),
            new LogEntry(now.minusMinutes(6), "WARN", "High latency detected"),
            new LogEntry(now.minusMinutes(5), "ERROR", "Connection timeout"));

    Traversal<List<LogEntry>, LogEntry> steadyState =
        ListTraversals.droppingWhile(LogEntry::isWarmup);

    System.out.println("All log entries:");
    logs.forEach(e -> System.out.println("  " + e));

    // Process only steady-state logs
    List<LogEntry> steadyStateLogs = Traversals.getAll(steadyState, logs);

    System.out.println("\nSteady-state logs (skipping warmup):");
    steadyStateLogs.forEach(e -> System.out.println("  " + e));

    // To combine droppingWhile with taking, apply sequentially
    // First drop warmup, then take first N from the result
    Traversal<List<LogEntry>, LogEntry> takingFirst = ListTraversals.<LogEntry>taking(100);

    List<LogEntry> batchLogs = Traversals.getAll(takingFirst, steadyStateLogs);
    System.out.println("\nFirst batch (skip warmup, take 100): " + batchLogs.size() + " entries");
  }
}
