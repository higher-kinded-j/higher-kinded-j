// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: VStream Optics Integration
 *
 * <p>Learn how VStream integrates with the optics ecosystem (Each, Traversal, FocusDSL) and the
 * effect path bridges (toVStreamPath, fromEach).
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>VStreamTraversals.forVStream() creates a Traversal for VStream elements
 *   <li>EachInstances.vstreamEach() provides the Each type class instance
 *   <li>FocusDSL .each(vstreamEach) navigates into VStream fields
 *   <li>TraversalPath.toVStreamPath() bridges optics to VStreamPath
 *   <li>VStreamPath.fromEach() creates VStreamPath from any Each-traversable source
 * </ul>
 *
 * <p>Prerequisites: Familiarity with VStream basics and optics (Lens, Traversal, FocusDSL)
 *
 * <p>Estimated time: 15-20 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial: VStream Optics Integration")
public class Tutorial17_VStreamOptics {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: VStream Traversal Basics
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: VStream Traversal Basics")
  class VStreamTraversalBasics {

    /**
     * Exercise 1: Create a VStream traversal and get all elements
     *
     * <p>VStreamTraversals.forVStream() creates a Traversal<VStream<A>, A> that materialises the
     * stream and traverses its elements. Use Traversals.getAll() to extract the elements.
     *
     * <p>Task: Create a traversal for VStream<String>, then use it to get all elements.
     */
    @Test
    @DisplayName("Exercise 1: Get all elements with VStream traversal")
    void getAllWithTraversal() {
      VStream<String> stream = VStream.fromList(List.of("alpha", "beta", "gamma"));

      // TODO: Create a VStream traversal and get all elements
      // Hint: Use VStreamTraversals.forVStream() and Traversals.getAll()
      List<String> result = answerRequired();

      assertThat(result).containsExactly("alpha", "beta", "gamma");
    }

    /**
     * Exercise 2: Modify all elements with VStream traversal
     *
     * <p>Traversals.modify() applies a function to all traversed elements. The VStream traversal
     * materialises the stream, transforms, and reconstructs.
     *
     * <p>Task: Use the traversal to uppercase all strings in the VStream.
     */
    @Test
    @DisplayName("Exercise 2: Modify all elements with VStream traversal")
    void modifyWithTraversal() {
      VStream<String> stream = VStream.fromList(List.of("hello", "world"));

      // TODO: Modify all elements to uppercase using VStreamTraversals and Traversals.modify()
      VStream<String> result = answerRequired();

      assertThat(result.toList().run()).containsExactly("HELLO", "WORLD");
    }
  }

  // ===========================================================================
  // Part 2: Each Instance
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: VStream Each Instance")
  class VStreamEachInstance {

    /**
     * Exercise 3: Use the VStream Each instance
     *
     * <p>EachInstances.vstreamEach() provides an Each instance for VStream. Each.each() returns the
     * underlying traversal.
     *
     * <p>Task: Create a VStream Each instance and use it to get all elements.
     */
    @Test
    @DisplayName("Exercise 3: Use VStream Each to get all elements")
    void useVStreamEach() {
      VStream<Integer> numbers = VStream.fromList(List.of(1, 2, 3, 4, 5));

      // TODO: Create a VStream Each instance and use it to get all elements
      // Hint: EachInstances.vstreamEach() and Traversals.getAll()
      List<Integer> result = answerRequired();

      assertThat(result).containsExactly(1, 2, 3, 4, 5);
    }

    /**
     * Exercise 4: Verify VStream Each does not support indexing
     *
     * <p>VStream does not have natural positional indices, so supportsIndexed() returns false.
     *
     * <p>Task: Check whether VStream Each supports indexed traversal.
     */
    @Test
    @DisplayName("Exercise 4: Check VStream indexed support")
    void checkIndexedSupport() {
      Each<VStream<String>, String> vstreamEach = EachInstances.vstreamEach();

      // TODO: Call the appropriate method to check indexed support
      boolean result = answerRequired();

      assertThat(result).isFalse();
    }
  }

  // ===========================================================================
  // Part 3: FocusDSL Integration
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: FocusDSL Integration")
  class FocusDSLIntegration {

    record Inventory(VStream<String> items) {}

    static final Lens<Inventory, VStream<String>> ITEMS_LENS =
        Lens.of(Inventory::items, (inv, items) -> new Inventory(items));

    /**
     * Exercise 5: Navigate into VStream with FocusDSL
     *
     * <p>Use FocusPath.of(lens).each(vstreamEach) to create a TraversalPath that navigates through
     * a lens into a VStream field and traverses its elements.
     *
     * <p>Task: Create a TraversalPath from Inventory to all item strings.
     */
    @Test
    @DisplayName("Exercise 5: FocusDSL each() with vstreamEach")
    void focusDSLWithVStream() {
      Inventory inventory =
          new Inventory(VStream.fromList(List.of("Widget", "Gadget", "Doohickey")));

      // TODO: Create a TraversalPath<Inventory, String> using FocusDSL
      // Hint: FocusPath.of(ITEMS_LENS).each(EachInstances.vstreamEach())
      List<String> result = answerRequired();

      assertThat(result).containsExactly("Widget", "Gadget", "Doohickey");
    }

    /**
     * Exercise 6: Modify all VStream elements via FocusDSL
     *
     * <p>TraversalPath.modifyAll() modifies all focused elements.
     *
     * <p>Task: Use a TraversalPath to uppercase all item names in the Inventory.
     */
    @Test
    @DisplayName("Exercise 6: Modify VStream elements via FocusDSL")
    void modifyViaFocusDSL() {
      Inventory inventory = new Inventory(VStream.fromList(List.of("alpha", "beta")));
      Each<VStream<String>, String> vstreamEach = EachInstances.vstreamEach();
      TraversalPath<Inventory, String> allItems = FocusPath.of(ITEMS_LENS).each(vstreamEach);

      // TODO: Use allItems.modifyAll() to uppercase all items
      Inventory result = answerRequired();

      List<String> resultItems = allItems.getAll(result);
      assertThat(resultItems).containsExactly("ALPHA", "BETA");
    }
  }

  // ===========================================================================
  // Part 4: Effect Path Bridges
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Effect Path Bridges")
  class EffectPathBridges {

    /**
     * Exercise 7: Bridge from TraversalPath to VStreamPath
     *
     * <p>TraversalPath.toVStreamPath(source) extracts all focused values and wraps them in a
     * VStreamPath for lazy, virtual-thread stream processing.
     *
     * <p>Task: Convert a list traversal path to a VStreamPath and apply lazy operations.
     */
    @Test
    @DisplayName("Exercise 7: toVStreamPath() bridge")
    void toVStreamPathBridge() {
      Traversal<List<Integer>, Integer> listTraversal = Traversals.forList();
      TraversalPath<List<Integer>, Integer> path = TraversalPath.of(listTraversal);
      List<Integer> source = List.of(1, 2, 3, 4, 5, 6);

      // TODO: Use path.toVStreamPath(source) then filter evens and multiply by 10
      // Finally collect to list with .toList().unsafeRun()
      List<Integer> result = answerRequired();

      assertThat(result).containsExactly(20, 40, 60);
    }

    /**
     * Exercise 8: Create VStreamPath from Each
     *
     * <p>VStreamPath.fromEach(source, each) extracts all elements from a source via an Each
     * instance and wraps them in a VStreamPath.
     *
     * <p>Task: Create a VStreamPath from a List using listEach, then map and collect.
     */
    @Test
    @DisplayName("Exercise 8: VStreamPath.fromEach() factory")
    void fromEachFactory() {
      Each<List<String>, String> listEach = EachInstances.listEach();
      List<String> names = List.of("Alice", "Bob", "Charlie");

      // TODO: Use VStreamPath.fromEach() to create a VStreamPath from names
      // Then map each name to its length and collect to list
      List<Integer> result = answerRequired();

      assertThat(result).containsExactly(5, 3, 7);
    }
  }
}
