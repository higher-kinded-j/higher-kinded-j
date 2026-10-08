// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.indexed;

// ANCHOR: imports
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.higherkindedj.optics.indexed.IndexedFold;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;

// ANCHOR_END: imports

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/indexed_optics_advanced.html">Indexed
 * Optics: Advanced Patterns</a> page, in its order fulfilment dashboard. The page {@code
 * {{#include}}}s the anchored region, and shows what {@code main} prints from a golden file the
 * book's output gate holds to it.
 */
// ANCHOR: dashboard
public class OrderFulfilmentDashboard {

  public record LineItem(String productName, int quantity, double price) {}

  public record Order(String orderId, List<LineItem> items, Map<String, String> metadata) {}

  private static Map<String, String> metadataInOrder() {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("priority", "express");
    metadata.put("gift-wrap", "true");
    metadata.put("delivery-note", "Leave at door");
    return metadata;
  }

  public static void main(String[] args) {
    Order order =
        new Order(
            "ORD-12345",
            List.of(
                new LineItem("Laptop", 1, 999.99),
                new LineItem("Mouse", 2, 24.99),
                new LineItem("Keyboard", 1, 79.99),
                new LineItem("Monitor", 1, 299.99)),
            // Insertion order matters for the output, so put() in order:
            // wrapping Map.of would inherit its randomised iteration order.
            metadataInOrder());

    System.out.println("=== ORDER FULFILMENT DASHBOARD ===\n");

    // --- Task 1: Generate Packing Slip ---
    System.out.println("--- Packing Slip ---");
    generatePackingSlip(order);

    // --- Task 2: Apply Position-Based Discounts ---
    System.out.println("\n--- Position-Based Discounts ---");
    Order discounted = applyPositionDiscounts(order);
    System.out.printf("Original total: £%.2f%n", calculateTotal(order));
    System.out.printf("Discounted total: £%.2f%n", calculateTotal(discounted));

    // --- Task 3: Process Metadata with Key Awareness ---
    System.out.println("\n--- Metadata Processing ---");
    processMetadata(order);

    // --- Task 4: Identify High-Value Positions ---
    System.out.println("\n--- High-Value Items ---");
    identifyHighValuePositions(order);

    System.out.println("\n=== END OF DASHBOARD ===");
  }

  private static void generatePackingSlip(Order order) {
    IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed = IndexedTraversals.forList();

    List<Pair<Integer, LineItem>> indexedItems =
        IndexedTraversals.toIndexedList(itemsIndexed, order.items());

    System.out.println("Order: " + order.orderId());
    for (Pair<Integer, LineItem> pair : indexedItems) {
      int position = pair.first() + 1; // 1-based for display
      LineItem item = pair.second();
      System.out.printf(
          "  Item %d: %s (Qty: %d) - £%.2f%n",
          position, item.productName(), item.quantity(), item.price() * item.quantity());
    }
  }

  private static Order applyPositionDiscounts(Order order) {
    IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed = IndexedTraversals.forList();

    // Every 3rd item gets 15% off (indices 2, 5, 8...)
    List<LineItem> discounted =
        IndexedTraversals.imodify(
            itemsIndexed,
            (index, item) -> {
              if ((index + 1) % 3 == 0) {
                double newPrice = item.price() * 0.85;
                System.out.printf(
                    "  Position %d (%s): £%.2f → £%.2f (15%% off)%n",
                    index + 1, item.productName(), item.price(), newPrice);
                return new LineItem(item.productName(), item.quantity(), newPrice);
              }
              return item;
            },
            order.items());

    return new Order(order.orderId(), discounted, order.metadata());
  }

  private static void processMetadata(Order order) {
    IndexedTraversal<String, Map<String, String>, String> metadataIndexed =
        IndexedTraversals.forMap();

    IndexedFold<String, Map<String, String>, String> fold = metadataIndexed.asIndexedFold();

    List<Pair<String, String>> entries = fold.toIndexedList(order.metadata());

    for (Pair<String, String> entry : entries) {
      String key = entry.first();
      String value = entry.second();

      // Process based on key
      switch (key) {
        case "priority" -> System.out.println("  Shipping priority: " + value.toUpperCase());
        case "gift-wrap" ->
            System.out.println(
                "  Gift wrapping: " + (value.equals("true") ? "Required" : "Not required"));
        case "delivery-note" -> System.out.println("  Special instructions: " + value);
        default -> System.out.println("  " + key + ": " + value);
      }
    }
  }

  private static void identifyHighValuePositions(Order order) {
    IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed = IndexedTraversals.forList();

    // Filter to items over £100
    IndexedTraversal<Integer, List<LineItem>, LineItem> highValue =
        itemsIndexed.filteredWithIndex((index, item) -> item.price() > 100);

    List<Pair<Integer, LineItem>> expensive =
        IndexedTraversals.toIndexedList(highValue, order.items());

    System.out.println("  Items over £100 (require special handling):");
    for (Pair<Integer, LineItem> pair : expensive) {
      System.out.printf(
          "    Position %d: %s (£%.2f)%n",
          pair.first() + 1, pair.second().productName(), pair.second().price());
    }
  }

  private static double calculateTotal(Order order) {
    return order.items().stream().mapToDouble(item -> item.price() * item.quantity()).sum();
  }
}
// ANCHOR_END: dashboard
