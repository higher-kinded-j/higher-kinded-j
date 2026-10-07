// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.structures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.higherkindedj.optics.util.TupleTraversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/common_data_structure_traversals.html">Common
 * Data Structure Traversals</a> page. The page {@code {{#include}}}s the anchored regions, and
 * {@code StructuresBookTest} holds the claims the page makes about this code.
 */
public final class StructuresBook {

  private StructuresBook() {}

  /** A present port moved on, an empty one left alone, and the present one read as a list. */
  record OptionalResults(
      Optional<Integer> offsetPort, Optional<Integer> stillEmpty, List<Integer> values) {}

  static OptionalResults optionalTraversal() {
    // ANCHOR: optional
    // Create an Optional traversal
    Traversal<Optional<Integer>, Integer> optTraversal = Traversals.forOptional();

    // Modify the value if present
    Optional<Integer> maybePort = Optional.of(8080);
    Optional<Integer> offsetPort = Traversals.modify(optTraversal, p -> p + 1000, maybePort);
    // Result: Optional[9080]

    // Empty Optional remains empty
    Optional<Integer> empty = Optional.empty();
    Optional<Integer> stillEmpty = Traversals.modify(optTraversal, p -> p + 1000, empty);
    // Result: Optional.empty

    // Extract value as a list
    List<Integer> values = Traversals.getAll(optTraversal, maybePort);
    // Result: [8080]  (or [] for empty)
    // ANCHOR_END: optional
    return new OptionalResults(offsetPort, stillEmpty, values);
  }

  /** The page's price list, and the stream-and-collect version of a 10% increase over it. */
  record StreamIncrease(Map<String, BigDecimal> prices, Map<String, BigDecimal> inflated) {}

  static StreamIncrease streamIncrease() {
    // ANCHOR: map_stream
    // a TreeMap rather than Map.of, so the prices iterate in a fixed key order
    Map<String, BigDecimal> prices =
        new TreeMap<>(
            Map.of(
                "widget", new BigDecimal("10.00"),
                "gadget", new BigDecimal("25.00"),
                "gizmo", new BigDecimal("15.00")));

    // Traditional: Stream + collect, for a 10% price increase kept to whole pence
    Map<String, BigDecimal> inflated =
        prices.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e ->
                        e.getValue()
                            .multiply(new BigDecimal("1.1"))
                            .setScale(2, RoundingMode.HALF_EVEN)));
    // ANCHOR_END: map_stream
    return new StreamIncrease(prices, inflated);
  }

  /** A charge added to every price, every price read, and a discount on the expensive ones. */
  record MapResults(
      Map<String, BigDecimal> inflated,
      List<BigDecimal> allPrices,
      Map<String, BigDecimal> discounted) {}

  static MapResults mapValues(Map<String, BigDecimal> prices) {
    // ANCHOR: map_values
    // Create a Map values traversal
    Traversal<Map<String, BigDecimal>, BigDecimal> priceTraversal = Traversals.forMapValues();

    // Add a flat 1.50 handling charge to every value
    Map<String, BigDecimal> inflated =
        Traversals.modify(priceTraversal, price -> price.add(new BigDecimal("1.50")), prices);
    // Result: {gadget=26.50, gizmo=16.50, widget=11.50}

    // Extract all values
    List<BigDecimal> allPrices = Traversals.getAll(priceTraversal, prices);
    // Result: [25.00, 15.00, 10.00]

    // Compose with filtered for conditional updates
    // (filtered optics are covered properly in the Precision and Filtering group)
    Traversal<Map<String, BigDecimal>, BigDecimal> expensiveItems =
        priceTraversal.filtered(price -> price.compareTo(new BigDecimal("20.00")) > 0);

    // 10% discount on expensive items only, kept to whole pence
    Map<String, BigDecimal> discounted =
        Traversals.modify(
            expensiveItems,
            price -> price.multiply(new BigDecimal("0.9")).setScale(2, RoundingMode.HALF_EVEN),
            prices);
    // Result: {gadget=22.50, gizmo=15.00, widget=10.00}
    // ANCHOR_END: map_values
    return new MapResults(inflated, allPrices, discounted);
  }

  /** A pair of numbers doubled and read, and a pair of names capitalised. */
  record TupleResults(
      Tuple2<Integer, Integer> doubled, List<Integer> values, Tuple2<String, String> capitalised) {}

  static TupleResults bothElements() {
    // ANCHOR: tuple
    // Create a tuple traversal (when both elements are same type)
    Traversal<Tuple2<Integer, Integer>, Integer> bothInts = TupleTraversals.both();

    // Double both elements
    Tuple2<Integer, Integer> range = new Tuple2<>(10, 20);
    Tuple2<Integer, Integer> doubled = Traversals.modify(bothInts, x -> x * 2, range);
    // Result: Tuple2[_1=20, _2=40]

    // Extract both elements
    List<Integer> values = Traversals.getAll(bothInts, range);
    // Result: [10, 20]

    // Works with any shared type
    Traversal<Tuple2<String, String>, String> bothStrings = TupleTraversals.both();
    Tuple2<String, String> names = new Tuple2<>("alice", "bob");
    Tuple2<String, String> capitalised =
        Traversals.modify(
            bothStrings, s -> s.substring(0, 1).toUpperCase() + s.substring(1), names);
    // Result: Tuple2[_1=Alice, _2=Bob]
    // ANCHOR_END: tuple
    return new TupleResults(doubled, values, capitalised);
  }
}
