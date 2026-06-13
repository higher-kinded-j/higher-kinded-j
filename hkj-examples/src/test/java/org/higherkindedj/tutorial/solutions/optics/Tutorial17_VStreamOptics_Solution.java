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
 * Solution for Tutorial17 VStreamOptics — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
@DisplayName("Tutorial Solution: VStream Optics Integration")
public class Tutorial17_VStreamOptics_Solution {

  // ===========================================================================
  // Part 1: VStream Traversal Basics
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: VStream Traversal Basics")
  class VStreamTraversalBasics {

    /**
     * Why this is idiomatic: {@code VStreamTraversals.forVStream()} treats a virtual stream the
     * same way the list traversal treats a list. {@code getAll} forces the stream and collects
     * every element.
     *
     * <p>Alternative: {@code stream.toList().run()} and read directly. Same data; the traversal
     * stays composable for downstream lens composition.
     *
     * <p>Common wrong attempt: assume {@code VStream} can be re-traversed cheaply. Forcing it
     * materialises the elements; reuse the resulting list if the same data is needed twice.
     */
    @Test
    @DisplayName("Exercise 1: Get all elements with VStream traversal")
    void getAllWithTraversal() {
      VStream<String> stream = VStream.fromList(List.of("alpha", "beta", "gamma"));

      Traversal<VStream<String>, String> traversal = VStreamTraversals.forVStream();
      List<String> result = Traversals.getAll(traversal, stream);

      assertThat(result).containsExactly("alpha", "beta", "gamma");
    }

    /**
     * Why this is idiomatic: {@code Traversals.modify} on a {@code VStream} returns a fresh stream
     * — the original is untouched, the modify pipeline is described as data and forced when the
     * result is consumed.
     *
     * <p>Alternative: collect to a list, map, rebuild the stream. Same answer; the traversal
     * version preserves the streaming nature.
     *
     * <p>Common wrong attempt: assume the modify runs eagerly. {@code VStream} is lazy; the work
     * happens at the consumer.
     */
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

    /**
     * Why this is idiomatic: {@code EachInstances.vstreamEach()} is the canonical {@code Each}
     * instance for {@code VStream}. Combined with {@code each()} it produces a traversal that any
     * optic combinator understands.
     *
     * <p>Alternative: build a custom traversal directly with {@code
     * VStreamTraversals.forVStream()}. Equivalent; the {@code Each} instance is the Focus DSL's
     * lingua franca.
     *
     * <p>Common wrong attempt: confuse {@code listEach} with {@code vstreamEach}. The traversal
     * types differ; pick the instance that matches your container.
     */
    @Test
    @DisplayName("Exercise 3: Use VStream Each to get all elements")
    void useVStreamEach() {
      VStream<Integer> numbers = VStream.fromList(List.of(1, 2, 3, 4, 5));

      Each<VStream<Integer>, Integer> vstreamEach = EachInstances.vstreamEach();
      List<Integer> result = Traversals.getAll(vstreamEach.each(), numbers);

      assertThat(result).containsExactly(1, 2, 3, 4, 5);
    }

    /**
     * Why this is idiomatic: {@code VStream}'s {@code Each} reports {@code supportsIndexed = false}
     * because virtual streams have no random index access. Code that needs indexed traversal (e.g.
     * {@code modifyAtIndex}) checks this flag first.
     *
     * <p>Alternative: assume every container supports indexed traversal. False — only containers
     * like {@code List} and {@code Vector} do; the flag is the typed answer.
     *
     * <p>Common wrong attempt: try to obtain an indexed traversal from a vstream. It is not an
     * {@code EachIndexed}, so there is no {@code indexedTraversal()} to call; the compile-time
     * check is the polite version.
     */
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

    /**
     * Why this is idiomatic: {@code FocusPath.of(lens).each(vstreamEach)} produces a {@code
     * TraversalPath} that walks every element of the {@code VStream} field — the Focus DSL stays
     * uniform across containers.
     *
     * <p>Alternative: collect the VStream to a list and use {@code each()}. Same answer for the
     * read; loses the streaming when the data is large.
     *
     * <p>Common wrong attempt: pass a {@code listEach} to a vstream-shaped lens. The compiler
     * rejects the mismatch; double-check the {@code Each} instance.
     */
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

    /**
     * Why this is idiomatic: {@code modifyAll} on a vstream-backed traversal path uppercases every
     * item without forcing the stream until the result is read. The {@code Inventory} record is
     * rebuilt with a new stream.
     *
     * <p>Alternative: read the stream, modify, rewrap in a new {@code Inventory}. Same answer; the
     * path-driven version stays inside the optic vocabulary.
     *
     * <p>Common wrong attempt: assume modifying once exhausts the stream. The path produces a fresh
     * stream description; reading it returns the modified data.
     */
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

    /**
     * Why this is idiomatic: {@code path.toVStreamPath(source)} turns a list-shaped traversal into
     * a {@code VStream} pipeline — filter, map, then collect. The optic supplies the elements; the
     * stream provides lazy operators.
     *
     * <p>Alternative: stream over the list with {@code stream().filter(...).map(...)}. Same answer;
     * the {@code VStream} bridge enables structured-concurrency operators later in the pipeline.
     *
     * <p>Common wrong attempt: forget to call {@code unsafeRun} on the resulting list. {@code
     * VStreamPath} is lazy; nothing happens until the result is forced.
     */
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

    /**
     * Why this is idiomatic: {@code VStreamPath.fromEach(source, eachInstance)} is the direct
     * factory — no intermediate traversal, just an {@code Each} and a source. Cleaner when the path
     * is the simple "every element of this container" case.
     *
     * <p>Alternative: build the traversal explicitly and call {@code toVStreamPath}. Equivalent;
     * the factory removes one step.
     *
     * <p>Common wrong attempt: pass an {@code Each} whose source type does not match the data. The
     * compiler enforces the relationship; pick the matching instance.
     */
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
