// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.indexed;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.higherkindedj.optics.indexed.IndexedFold;
import org.higherkindedj.optics.indexed.IndexedLens;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/indexed_optics.html">Indexed Optics</a>
 * page, where each line item, map entry or field arrives with its index. The page {@code
 * {{#include}}}s the anchored regions, and {@code IndexedOpticsBookTest} holds the claims the page
 * makes about this code.
 *
 * <p>The records are nested here, as {@code OrderFulfilmentDashboard}'s are, so the two files can
 * each keep the names their page uses.
 */
public final class IndexedOpticsBook {

  private IndexedOpticsBook() {}

  /** An order line, as the page's data model declares it. */
  record LineItem(String productName, int quantity, BigDecimal price) {}

  /** A customer, as the page's data model declares it. */
  record Customer(String name, String email) {}

  /** The page's sample items, and each paired with its index. */
  record Positions(List<LineItem> items, List<Pair<Integer, LineItem>> indexedItems) {}

  /** What the fold example finds, and whether an even-positioned item is expensive. */
  record FoldQueries(Pair<Integer, LineItem> found, boolean hasExpensiveEven) {}

  /** The pairs a filter focuses, and the list its {@code imodify} returns. */
  record Filtered(List<Pair<Integer, LineItem>> focused, List<LineItem> result) {}

  /** The indexed lens's read, and the customer its {@code imodify} returns. */
  record LensRead(Pair<String, String> fieldInfo, Customer updated) {}

  static Positions positions(IndexedTraversal<Integer, List<LineItem>, LineItem> itemsWithIndex) {
    // ANCHOR: pairs
    List<LineItem> items =
        List.of(
            new LineItem("Laptop", 1, new BigDecimal("999.99")),
            new LineItem("Mouse", 2, new BigDecimal("24.99")),
            new LineItem("Keyboard", 1, new BigDecimal("79.99")));

    // Get list of (index, item) pairs - optic meets data
    List<Pair<Integer, LineItem>> indexedItems =
        IndexedTraversals.toIndexedList(itemsWithIndex, items);

    for (Pair<Integer, LineItem> pair : indexedItems) {
      int position = pair.first();
      LineItem item = pair.second();
      System.out.println("Position " + position + ": " + item.productName());
    }
    // Output:
    // Position 0: Laptop
    // Position 1: Mouse
    // Position 2: Keyboard
    // ANCHOR_END: pairs
    return new Positions(items, indexedItems);
  }

  static FoldQueries foldQueries(
      IndexedTraversal<Integer, List<LineItem>, LineItem> itemsWithIndex, List<LineItem> items) {
    // ANCHOR: fold
    // Convert to read-only indexed fold
    IndexedFold<Integer, List<LineItem>, LineItem> itemsFold = itemsWithIndex.asIndexedFold();

    // Find item at a specific position
    Pair<Integer, LineItem> found =
        itemsFold.findWithIndex((index, item) -> index == 1, items).orElseThrow();

    System.out.println("Item at index 1: " + found.second().productName());
    // Output: Item at index 1: Mouse

    // Check if any even-positioned item is expensive
    boolean hasExpensiveEven =
        itemsFold.existsWithIndex(
            (index, item) -> index % 2 == 0 && item.price().compareTo(new BigDecimal("500")) > 0,
            items);
    // ANCHOR_END: fold
    return new FoldQueries(found, hasExpensiveEven);
  }

  static List<LineItem> numbered(
      IndexedTraversal<Integer, List<LineItem>, LineItem> itemsWithIndex, List<LineItem> items) {
    // ANCHOR: numbering
    // Modify product names to include position numbers
    List<LineItem> numbered =
        IndexedTraversals.imodify(
            itemsWithIndex,
            (index, item) ->
                new LineItem(
                    "Item " + (index + 1) + ": " + item.productName(),
                    item.quantity(),
                    item.price()),
            items);

    for (LineItem item : numbered) {
      System.out.println(item.productName());
    }
    // Output:
    // Item 1: Laptop
    // Item 2: Mouse
    // Item 3: Keyboard
    // ANCHOR_END: numbering
    return numbered;
  }

  static Map<String, String> keyAware() {
    // ANCHOR: map_keys
    IndexedTraversal<String, Map<String, String>, String> metadataTraversal =
        IndexedTraversals.forMap();

    Map<String, String> metadata =
        Map.of(
            "priority", "express",
            "gift-wrap", "true",
            "delivery-note", "Leave at door");

    Map<String, String> processed =
        IndexedTraversals.imodify(
            metadataTraversal,
            (key, value) -> {
              // Add key prefix to all values for debugging
              return "[" + key + "] " + value;
            },
            metadata);

    // Each key now maps to its value with the key as a prefix:
    // "priority" → "[priority] express"
    // "gift-wrap" → "[gift-wrap] true"
    // "delivery-note" → "[delivery-note] Leave at door"
    // ANCHOR_END: map_keys
    return processed;
  }

  static Filtered evenPositions(
      IndexedTraversal<Integer, List<LineItem>, LineItem> itemsWithIndex, List<LineItem> items) {
    // ANCHOR: filter_index
    // Focus only on even-positioned items
    IndexedTraversal<Integer, List<LineItem>, LineItem> evenPositions =
        itemsWithIndex.filterIndex(index -> index % 2 == 0);

    List<Pair<Integer, LineItem>> evenItems = IndexedTraversals.toIndexedList(evenPositions, items);
    // Laptop at index 0 and Keyboard at index 2

    // Modify only even-positioned items
    List<LineItem> result =
        IndexedTraversals.imodify(
            evenPositions,
            (index, item) ->
                new LineItem(item.productName() + " [SALE]", item.quantity(), item.price()),
            items);
    // Laptop and Keyboard get "[SALE]" suffix, Mouse unchanged
    // ANCHOR_END: filter_index
    return new Filtered(evenItems, result);
  }

  static List<Pair<Integer, LineItem>> expensive(
      IndexedTraversal<Integer, List<LineItem>, LineItem> itemsWithIndex, List<LineItem> items) {
    // ANCHOR: filtered_with_index
    // Focus on expensive items, but still track their original positions
    IndexedTraversal<Integer, List<LineItem>, LineItem> expensiveItems =
        itemsWithIndex.filteredWithIndex(
            (index, item) -> item.price().compareTo(new BigDecimal("50")) > 0);

    List<Pair<Integer, LineItem>> expensive =
        IndexedTraversals.toIndexedList(expensiveItems, items);
    // Laptop at index 0 and Keyboard at index 2
    // Notice: indices are preserved (0 and 2), not renumbered
    // ANCHOR_END: filtered_with_index
    return expensive;
  }

  static List<Pair<String, String>> deliveryEntries(
      IndexedTraversal<String, Map<String, String>, String> metadataTraversal,
      Map<String, String> metadata) {
    // ANCHOR: filter_map
    // Focus on metadata keys starting with "delivery"
    IndexedTraversal<String, Map<String, String>, String> deliveryMetadata =
        metadataTraversal.filterIndex(key -> key.startsWith("delivery"));

    List<Pair<String, String>> deliveryEntries =
        IndexedTraversals.toIndexedList(deliveryMetadata, metadata);
    // [Pair[first=delivery-note, second=Leave at door]]
    // ANCHOR_END: filter_map
    return deliveryEntries;
  }

  static LensRead fieldTracking() {
    // ANCHOR: indexed_lens
    // Create an indexed lens for the customer email field
    IndexedLens<String, Customer, String> emailLens =
        IndexedLens.of(
            "email", // The index: field name
            Customer::email, // Getter
            (customer, newEmail) -> new Customer(customer.name(), newEmail)); // Setter

    Customer customer = new Customer("Alice", "alice@example.com");

    // Get both field name and value
    Pair<String, String> fieldInfo = emailLens.iget(customer);
    System.out.println("Field: " + fieldInfo.first());
    System.out.println("Value: " + fieldInfo.second());
    // Output:
    // Field: email
    // Value: alice@example.com

    // Modify with field name awareness
    Customer updated =
        emailLens.imodify(
            (fieldName, oldValue) -> {
              System.out.println("Updating field '" + fieldName + "' from " + oldValue);
              return "alice.smith@example.com";
            },
            customer);
    // Output: Updating field 'email' from alice@example.com
    // ANCHOR_END: indexed_lens
    return new LensRead(fieldInfo, updated);
  }

  static List<String> sequenceNumbers() {
    // ANCHOR: sequence_numbers
    // Generate a numbered list for display
    IndexedTraversal<Integer, List<String>, String> indexed = IndexedTraversals.forList();

    List<String> tasks = List.of("Review PR", "Update docs", "Run tests");

    List<String> numbered =
        IndexedTraversals.imodify(indexed, (i, task) -> (i + 1) + ". " + task, tasks);
    // [1. Review PR, 2. Update docs, 3. Run tests]
    // ANCHOR_END: sequence_numbers
    return numbered;
  }

  static List<String> keyValueDisplay() {
    // ANCHOR: map_display
    IndexedTraversal<String, Map<String, Integer>, Integer> mapIndexed = IndexedTraversals.forMap();

    // A TreeMap, so the entries come back in key order
    Map<String, Integer> scores = new TreeMap<>(Map.of("alice", 100, "bob", 85, "charlie", 92));

    // Create display strings incorporating both key and value
    List<String> results =
        IndexedTraversals.toIndexedList(mapIndexed, scores).stream()
            .map(pair -> pair.first() + " scored " + pair.second())
            .toList();
    // [alice scored 100, bob scored 85, charlie scored 92]
    // ANCHOR_END: map_display
    return results;
  }

  static List<String> oddPositions() {
    // ANCHOR: odd_positions
    IndexedTraversal<Integer, List<String>, String> indexed = IndexedTraversals.forList();

    List<String> values = List.of("a", "b", "c", "d", "e", "f");

    // Take only odd positions (1, 3, 5)
    IndexedTraversal<Integer, List<String>, String> oddPositions =
        indexed.filterIndex(i -> i % 2 == 1);

    List<String> odd = IndexedTraversals.getAll(oddPositions, values);
    // [b, d, f]
    // ANCHOR_END: odd_positions
    return odd;
  }
}
