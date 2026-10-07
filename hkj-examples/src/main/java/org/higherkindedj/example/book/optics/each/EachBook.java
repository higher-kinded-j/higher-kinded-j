// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.each;

import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.EachIndexed;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/each_typeclass.html">Each</a> page, in its
 * Basic Usage section. The page {@code {{#include}}}s the anchored regions, and {@code
 * EachBookTest} holds the values their comments claim.
 */
public final class EachBook {

  private EachBook() {}

  /** What the page's three list operations return, so the test can read each. */
  record ListResults(List<String> all, List<String> upper, List<String> same) {}

  static ListResults traverseAll() {
    // ANCHOR: traverse_all
    Each<List<String>, String> listEach = EachInstances.listEach();
    Traversal<List<String>, String> traversal = listEach.each();

    List<String> names = List.of("alice", "bob", "charlie");

    // Get all elements
    List<String> all = Traversals.getAll(traversal, names);
    // Result: ["alice", "bob", "charlie"]

    // Modify all elements
    List<String> upper = Traversals.modify(traversal, String::toUpperCase, names);
    // Result: ["ALICE", "BOB", "CHARLIE"]

    // Set all elements to the same value (set is modify with a constant)
    List<String> same = Traversals.modify(traversal, _ -> "anonymous", names);
    // Result: ["anonymous", "anonymous", "anonymous"]
    // ANCHOR_END: traverse_all
    return new ListResults(all, upper, same);
  }

  static List<String> numbered() {
    // ANCHOR: indexed
    EachIndexed<Integer, List<String>, String> listEach = EachInstances.listEach();
    IndexedTraversal<Integer, List<String>, String> indexed = listEach.indexedTraversal();

    List<String> items = List.of("apple", "banana", "cherry");

    // Number each element
    List<String> numbered =
        IndexedTraversals.imodify(indexed, (index, value) -> (index + 1) + ". " + value, items);
    // Result: ["1. apple", "2. banana", "3. cherry"]
    // ANCHOR_END: indexed
    return numbered;
  }

  /** What the page's two map operations return, so the test can read each. */
  record MapResults(Map<String, Integer> adjusted, Map<String, String> labelled) {}

  static MapResults byKey() {
    // ANCHOR: map_key
    EachIndexed<String, Map<String, Integer>, Integer> mapEach = EachInstances.mapValuesEach();
    IndexedTraversal<String, Map<String, Integer>, Integer> indexed = mapEach.indexedTraversal();

    Map<String, Integer> scores = Map.of("alice", 100, "bob", 85, "charlie", 92);

    // Award a five-point bonus to one particular entrant, by key
    Map<String, Integer> adjusted =
        IndexedTraversals.imodify(
            indexed, (key, value) -> key.equals("bob") ? value + 5 : value, scores);
    // Result: {"alice": 100, "bob": 90, "charlie": 92}

    // A traversal cannot change the value type: imodify takes A -> A and returns the
    // same map type. To build a differently-typed map, extract the pairs and collect:
    Map<String, String> labelled =
        IndexedTraversals.toIndexedList(indexed, scores).stream()
            .collect(toMap(Pair::first, pair -> pair.first() + ": " + pair.second()));
    // ANCHOR_END: map_key
    return new MapResults(adjusted, labelled);
  }
}
