// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.each.VStreamTraversals;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial: VStream Optics Integration
 *
 * <p>These are the reference solutions for Tutorial17_VStreamOptics.
 */
@DisplayName("Tutorial Solution: VStream Optics Integration")
public class Tutorial17_VStreamOptics_Solution {

  // ===========================================================================
  // Part 1: VStream Traversal Basics
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: VStream Traversal Basics")
  class VStreamTraversalBasics {

    @Test
    @DisplayName("Exercise 1: Get all elements with VStream traversal")
    void getAllWithTraversal() {
      VStream<String> stream = VStream.fromList(List.of("alpha", "beta", "gamma"));

      Traversal<VStream<String>, String> traversal = VStreamTraversals.forVStream();
      List<String> result = Traversals.getAll(traversal, stream);

      assertThat(result).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    @DisplayName("Exercise 2: Modify all elements with VStream traversal")
    void modifyWithTraversal() {
      VStream<String> stream = VStream.fromList(List.of("hello", "world"));

      Traversal<VStream<String>, String> traversal = VStreamTraversals.forVStream();
      VStream<String> result = Traversals.modify(traversal, String::toUpperCase, stream);

      assertThat(result.toList().run()).containsExactly("HELLO", "WORLD");
    }
  }

  // ===========================================================================
  // Part 2: Each Instance
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: VStream Each Instance")
  class VStreamEachInstance {

    @Test
    @DisplayName("Exercise 3: Use VStream Each to get all elements")
    void useVStreamEach() {
      VStream<Integer> numbers = VStream.fromList(List.of(1, 2, 3, 4, 5));

      Each<VStream<Integer>, Integer> vstreamEach = EachInstances.vstreamEach();
      List<Integer> result = Traversals.getAll(vstreamEach.each(), numbers);

      assertThat(result).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("Exercise 4: Check VStream indexed support")
    void checkIndexedSupport() {
      Each<VStream<String>, String> vstreamEach = EachInstances.vstreamEach();

      boolean result = vstreamEach.supportsIndexed();

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

    @Test
    @DisplayName("Exercise 5: FocusDSL each() with vstreamEach")
    void focusDSLWithVStream() {
      Inventory inventory =
          new Inventory(VStream.fromList(List.of("Widget", "Gadget", "Doohickey")));

      Each<VStream<String>, String> vstreamEach = EachInstances.vstreamEach();
      TraversalPath<Inventory, String> allItems = FocusPath.of(ITEMS_LENS).each(vstreamEach);
      List<String> result = allItems.getAll(inventory);

      assertThat(result).containsExactly("Widget", "Gadget", "Doohickey");
    }

    @Test
    @DisplayName("Exercise 6: Modify VStream elements via FocusDSL")
    void modifyViaFocusDSL() {
      Inventory inventory = new Inventory(VStream.fromList(List.of("alpha", "beta")));
      Each<VStream<String>, String> vstreamEach = EachInstances.vstreamEach();
      TraversalPath<Inventory, String> allItems = FocusPath.of(ITEMS_LENS).each(vstreamEach);

      Inventory result = allItems.modifyAll(String::toUpperCase, inventory);

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

    @Test
    @DisplayName("Exercise 7: toVStreamPath() bridge")
    void toVStreamPathBridge() {
      Traversal<List<Integer>, Integer> listTraversal = Traversals.forList();
      TraversalPath<List<Integer>, Integer> path = TraversalPath.of(listTraversal);
      List<Integer> source = List.of(1, 2, 3, 4, 5, 6);

      List<Integer> result =
          path.toVStreamPath(source).filter(n -> n % 2 == 0).map(n -> n * 10).toList().unsafeRun();

      assertThat(result).containsExactly(20, 40, 60);
    }

    @Test
    @DisplayName("Exercise 8: VStreamPath.fromEach() factory")
    void fromEachFactory() {
      Each<List<String>, String> listEach = EachInstances.listEach();
      List<String> names = List.of("Alice", "Bob", "Charlie");

      List<Integer> result =
          VStreamPath.fromEach(names, listEach).map(String::length).toList().unsafeRun();

      assertThat(result).containsExactly(5, 3, 7);
    }
  }
}
