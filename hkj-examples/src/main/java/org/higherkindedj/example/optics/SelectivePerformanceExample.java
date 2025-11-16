// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.id.IdSelective;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;

/**
 * Demonstrates performance characteristics of Selective vs Applicative approaches. Shows operation
 * counting to illustrate when Selective provides benefits.
 */
public class SelectivePerformanceExample {

  @GenerateLenses
  public record Item(String id, boolean needsProcessing, int value) {}

  @GenerateTraversals
  public record Batch(String name, List<Item> items) {}

  private static final AtomicInteger operationCount = new AtomicInteger(0);

  public static void main(String[] args) {
    System.out.println("=== SELECTIVE PERFORMANCE COMPARISON ===\n");

    shortCircuitComparison();
    conditionalProcessingComparison();
    branchingComparison();
  }

  /**
   * Example 1: Short-circuit processing Selective can stop early, Applicative must process all
   * elements.
   */
  private static void shortCircuitComparison() {
    System.out.println("--- Example 1: Short-Circuit Processing ---");

    List<Item> items =
        List.of(
            new Item("ITEM-001", true, 10),
            new Item("ITEM-002", true, 20),
            new Item("ITEM-003", false, 30), // First item that doesn't need processing
            new Item("ITEM-004", true, 40),
            new Item("ITEM-005", true, 50));

    // Standard Applicative approach - processes all items
    System.out.println("Applicative approach (processes all items):");
    operationCount.set(0);

    Kind<IdKind.Witness, List<Item>> applicativeResult =
        Traversals.traverseList(
            items,
            item -> {
              operationCount.incrementAndGet();
              System.out.println("  Processing: " + item.id());
              return Id.of(item);
            },
            IdMonad.instance());

    System.out.println("Total operations: " + operationCount.get());

    // Selective approach - can stop early
    System.out.println("\nSelective approach (stops when condition met):");
    operationCount.set(0);

    Kind<IdKind.Witness, List<Item>> selectiveResult =
        Traversals.traverseListUntil(
            items,
            item -> !item.needsProcessing(), // Stop condition
            item -> {
              operationCount.incrementAndGet();
              System.out.println("  Processing: " + item.id());
              return Id.of(item);
            },
            IdSelective.instance());

    System.out.println("Total operations: " + operationCount.get());
    System.out.println("Performance gain: " + (5 - operationCount.get()) + " operations saved\n");
  }

  /** Example 2: Conditional processing Only process items that match criteria. */
  private static void conditionalProcessingComparison() {
    System.out.println("--- Example 2: Conditional Processing ---");

    Batch batch =
        new Batch(
            "Mixed Batch",
            List.of(
                new Item("ITEM-001", true, 10),
                new Item("ITEM-002", false, 20),
                new Item("ITEM-003", true, 30),
                new Item("ITEM-004", false, 40),
                new Item("ITEM-005", true, 50)));

    Traversal<Batch, Item> itemsTraversal = BatchTraversals.items();

    // Without selective - must process all items
    System.out.println("Without selective filtering:");
    operationCount.set(0);

    Kind<IdKind.Witness, Batch> allProcessed =
        itemsTraversal.modifyF(
            item -> {
              operationCount.incrementAndGet();
              if (item.needsProcessing()) {
                System.out.println("  Processing: " + item.id());
                return Id.of(new Item(item.id(), item.needsProcessing(), item.value() * 2));
              } else {
                System.out.println("  Skipping: " + item.id() + " (but still touched)");
                return Id.of(item);
              }
            },
            batch,
            IdMonad.instance());

    System.out.println("Total items touched: " + operationCount.get());

    // With selective - only process matching items
    System.out.println("\nWith selective filtering:");
    operationCount.set(0);

    Kind<IdKind.Witness, Batch> selectiveProcessed =
        itemsTraversal.modifyWhen(
            Item::needsProcessing,
            item -> {
              operationCount.incrementAndGet();
              System.out.println("  Processing: " + item.id());
              return Id.of(new Item(item.id(), item.needsProcessing(), item.value() * 2));
            },
            batch,
            IdSelective.instance());

    System.out.println("Total items processed: " + operationCount.get());
    System.out.println(
        "Note: With selective, only matching items execute the expensive operation\n");
  }

  /** Example 3: Branching logic Show how selective makes branching more explicit. */
  private static void branchingComparison() {
    System.out.println("--- Example 3: Branching Logic ---");

    List<Item> items =
        List.of(
            new Item("SMALL-001", true, 5),
            new Item("LARGE-001", true, 500),
            new Item("SMALL-002", true, 8),
            new Item("LARGE-002", true, 750),
            new Item("SMALL-003", true, 3));

    // Standard approach - checks happen inside the function
    System.out.println("Standard approach (implicit branching):");
    operationCount.set(0);

    Traversals.traverseList(
        items,
        item -> {
          operationCount.incrementAndGet();
          if (item.value() > 100) {
            System.out.println("  Heavy processing for " + item.id());
          } else {
            System.out.println("  Light processing for " + item.id());
          }
          return Id.of(item);
        },
        IdMonad.instance());

    // Selective approach - branches are explicit
    System.out.println("\nSelective approach (explicit branching):");
    operationCount.set(0);

    Traversals.speculativeTraverseList(
        items,
        item -> item.value() > 100,
        item -> {
          operationCount.incrementAndGet();
          System.out.println("  Heavy processing for " + item.id());
          return Id.of(item);
        },
        item -> {
          operationCount.incrementAndGet();
          System.out.println("  Light processing for " + item.id());
          return Id.of(item);
        },
        IdSelective.instance());

    System.out.println("\nNote: With selective, branches are visible upfront,");
    System.out.println("enabling potential parallel execution in concurrent implementations.\n");
  }
}
