// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.extensions;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StreamTraversal}.
 *
 * <p>Verifies lazy streaming, pure and effectful modification, composition with Lens and other
 * StreamTraversals, and conversion to/from standard Traversal.
 */
@DisplayName("StreamTraversal Test Suite")
class StreamTraversalTest {

  @Nested
  @DisplayName("ForVStream")
  class ForVStreamTests {

    @Test
    @DisplayName("stream() returns the source VStream unchanged")
    void streamReturnsSourceUnchanged() {
      StreamTraversal<VStream<Integer>, Integer> st = StreamTraversal.forVStream();
      VStream<Integer> source = VStream.of(1, 2, 3);

      VStream<Integer> result = st.stream(source);

      assertThat(result.toList().run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("modify() transforms all elements lazily")
    void modifyTransformsAllElements() {
      StreamTraversal<VStream<Integer>, Integer> st = StreamTraversal.forVStream();
      VStream<Integer> source = VStream.of(1, 2, 3);

      VStream<Integer> result = st.modify(x -> x * 2, source);

      assertThat(result.toList().run()).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("modifyVTask() applies effectful function")
    void modifyVTaskAppliesEffectfulFunction() {
      StreamTraversal<VStream<String>, String> st = StreamTraversal.forVStream();
      VStream<String> source = VStream.of("hello", "world");

      VTask<VStream<String>> result = st.modifyVTask(s -> VTask.of(s::toUpperCase), source);

      assertThat(result.run().toList().run()).containsExactly("HELLO", "WORLD");
    }

    @Test
    @DisplayName("stream() on empty VStream produces empty stream")
    void streamOnEmptyProducesEmpty() {
      StreamTraversal<VStream<Integer>, Integer> st = StreamTraversal.forVStream();
      VStream<Integer> source = VStream.empty();

      VStream<Integer> result = st.stream(source);

      assertThat(result.toList().run()).isEmpty();
    }
  }

  @Nested
  @DisplayName("ForList")
  class ForListTests {

    @Test
    @DisplayName("stream() returns lazy VStream of list elements")
    void streamReturnsLazyVStreamOfListElements() {
      StreamTraversal<List<String>, String> st = StreamTraversal.forList();
      List<String> source = List.of("a", "b", "c");

      VStream<String> result = st.stream(source);

      assertThat(result.toList().run()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("modify() transforms all elements in list")
    void modifyTransformsAllListElements() {
      StreamTraversal<List<Integer>, Integer> st = StreamTraversal.forList();
      List<Integer> source = List.of(1, 2, 3);

      List<Integer> result = st.modify(x -> x * 10, source);

      assertThat(result).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("modifyVTask() applies effectful function to list")
    void modifyVTaskAppliesEffectfulFunctionToList() {
      StreamTraversal<List<String>, String> st = StreamTraversal.forList();
      List<String> source = List.of("hello", "world");

      List<String> result = st.modifyVTask(s -> VTask.of(s::toUpperCase), source).run();

      assertThat(result).containsExactly("HELLO", "WORLD");
    }

    @Test
    @DisplayName("round-trip: stream -> collect -> equals original")
    void roundTripPreservesElements() {
      StreamTraversal<List<Integer>, Integer> st = StreamTraversal.forList();
      List<Integer> source = List.of(10, 20, 30);

      List<Integer> result = st.stream(source).toList().run();

      assertThat(result).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("modify() on empty list produces empty list")
    void modifyOnEmptyListProducesEmpty() {
      StreamTraversal<List<Integer>, Integer> st = StreamTraversal.forList();
      List<Integer> source = List.of();

      List<Integer> result = st.modify(x -> x * 2, source);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composition")
  class CompositionTests {

    @Test
    @DisplayName("andThen(StreamTraversal) composes two StreamTraversals")
    void andThenComposesStreamTraversals() {
      StreamTraversal<VStream<List<Integer>>, List<Integer>> outer = StreamTraversal.forVStream();
      StreamTraversal<List<Integer>, Integer> inner = StreamTraversal.forList();

      StreamTraversal<VStream<List<Integer>>, Integer> composed = outer.andThen(inner);

      VStream<List<Integer>> source = VStream.of(List.of(1, 2), List.of(3, 4));

      VStream<Integer> result = composed.stream(source);

      assertThat(result.toList().run()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("andThen(StreamTraversal) modify works through composition")
    void andThenModifyWorks() {
      StreamTraversal<List<List<Integer>>, List<Integer>> outer = StreamTraversal.forList();
      StreamTraversal<List<Integer>, Integer> inner = StreamTraversal.forList();

      StreamTraversal<List<List<Integer>>, Integer> composed = outer.andThen(inner);

      List<List<Integer>> source = List.of(List.of(1, 2), List.of(3, 4));
      List<List<Integer>> result = composed.modify(x -> x * 10, source);

      assertThat(result).containsExactly(List.of(10, 20), List.of(30, 40));
    }

    @Test
    @DisplayName("andThen(StreamTraversal) modifyVTask works through composition")
    void andThenModifyVTaskWorks() {
      StreamTraversal<List<List<Integer>>, List<Integer>> outer = StreamTraversal.forList();
      StreamTraversal<List<Integer>, Integer> inner = StreamTraversal.forList();

      StreamTraversal<List<List<Integer>>, Integer> composed = outer.andThen(inner);

      List<List<Integer>> source = List.of(List.of(1, 2), List.of(3, 4));
      List<List<Integer>> result = composed.modifyVTask(x -> VTask.succeed(x * 10), source).run();

      assertThat(result).containsExactly(List.of(10, 20), List.of(30, 40));
    }

    @Test
    @DisplayName("andThen(Lens) composes StreamTraversal with Lens")
    void andThenLensComposition() {
      record NamedValue(String name, int value) {}

      Lens<NamedValue, String> nameLens =
          Lens.of(NamedValue::name, (nv, n) -> new NamedValue(n, nv.value()));

      StreamTraversal<List<NamedValue>, NamedValue> listST = StreamTraversal.forList();
      StreamTraversal<List<NamedValue>, String> composed = listST.andThen(nameLens);

      List<NamedValue> source = List.of(new NamedValue("alice", 1), new NamedValue("bob", 2));

      VStream<String> names = composed.stream(source);
      assertThat(names.toList().run()).containsExactly("alice", "bob");
    }

    @Test
    @DisplayName("andThen(Lens) modify works through composition")
    void andThenLensModifyWorks() {
      record Item(String label, int count) {}

      Lens<Item, String> labelLens = Lens.of(Item::label, (i, l) -> new Item(l, i.count()));

      StreamTraversal<List<Item>, Item> listST = StreamTraversal.forList();
      StreamTraversal<List<Item>, String> composed = listST.andThen(labelLens);

      List<Item> source = List.of(new Item("a", 1), new Item("b", 2));
      List<Item> result = composed.modify(String::toUpperCase, source);

      assertThat(result).extracting(Item::label).containsExactly("A", "B");
      assertThat(result).extracting(Item::count).containsExactly(1, 2);
    }

    @Test
    @DisplayName("andThen(Lens) modifyVTask works through composition")
    void andThenLensModifyVTaskWorks() {
      record Item(String label, int count) {}

      Lens<Item, String> labelLens = Lens.of(Item::label, (i, l) -> new Item(l, i.count()));

      StreamTraversal<List<Item>, Item> listST = StreamTraversal.forList();
      StreamTraversal<List<Item>, String> composed = listST.andThen(labelLens);

      List<Item> source = List.of(new Item("a", 1), new Item("b", 2));
      List<Item> result = composed.modifyVTask(s -> VTask.succeed(s.toUpperCase()), source).run();

      assertThat(result).extracting(Item::label).containsExactly("A", "B");
      assertThat(result).extracting(Item::count).containsExactly(1, 2);
    }
  }

  @Nested
  @DisplayName("Conversion")
  class ConversionTests {

    @Test
    @DisplayName("toTraversal() produces valid standard Traversal")
    void toTraversalProducesValidTraversal() {
      StreamTraversal<List<Integer>, Integer> st = StreamTraversal.forList();
      Traversal<List<Integer>, Integer> traversal = st.toTraversal();

      assertThat(traversal).isNotNull();
    }

    @Test
    @DisplayName("toTraversal() modifyF exercises the materialisation path")
    void toTraversalModifyFWorks() {
      StreamTraversal<List<Integer>, Integer> st = StreamTraversal.forList();
      Traversal<List<Integer>, Integer> traversal = st.toTraversal();

      List<Integer> source = List.of(1, 2, 3);
      List<Integer> result =
          org.higherkindedj.optics.util.Traversals.modify(traversal, x -> x * 10, source);

      assertThat(result).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("toTraversal() getAll exercises the materialisation path")
    void toTraversalGetAllWorks() {
      StreamTraversal<List<Integer>, Integer> st = StreamTraversal.forList();
      Traversal<List<Integer>, Integer> traversal = st.toTraversal();

      List<Integer> source = List.of(10, 20, 30);
      List<Integer> result = org.higherkindedj.optics.util.Traversals.getAll(traversal, source);

      assertThat(result).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("fromTraversal() creates StreamTraversal from existing Traversal")
    void fromTraversalCreatesStreamTraversal() {
      Traversal<VStream<String>, String> vstreamTraversal =
          org.higherkindedj.optics.each.VStreamTraversals.forVStream();

      StreamTraversal<VStream<String>, String> st = StreamTraversal.fromTraversal(vstreamTraversal);

      VStream<String> source = VStream.of("hello", "world");
      VStream<String> elements = st.stream(source);

      assertThat(elements.toList().run()).containsExactly("hello", "world");
    }

    @Test
    @DisplayName("fromTraversal() modify works correctly")
    void fromTraversalModifyWorks() {
      Traversal<VStream<Integer>, Integer> vstreamTraversal =
          org.higherkindedj.optics.each.VStreamTraversals.forVStream();

      StreamTraversal<VStream<Integer>, Integer> st =
          StreamTraversal.fromTraversal(vstreamTraversal);

      VStream<Integer> source = VStream.of(1, 2, 3);
      VStream<Integer> result = st.modify(x -> x + 100, source);

      assertThat(result.toList().run()).containsExactly(101, 102, 103);
    }

    @Test
    @DisplayName("fromTraversal() modifyVTask applies effectful function via traversal")
    void fromTraversalModifyVTaskWorks() {
      Traversal<VStream<String>, String> vstreamTraversal =
          org.higherkindedj.optics.each.VStreamTraversals.forVStream();

      StreamTraversal<VStream<String>, String> st = StreamTraversal.fromTraversal(vstreamTraversal);

      VStream<String> source = VStream.of("hello", "world");
      VStream<String> result = st.modifyVTask(s -> VTask.of(s::toUpperCase), source).run();

      assertThat(result.toList().run()).containsExactly("HELLO", "WORLD");
    }
  }

  @Nested
  @DisplayName("Laziness")
  class LazinessTests {

    @Test
    @DisplayName("stream() does not materialise elements eagerly")
    void streamDoesNotMaterialiseEagerly() {
      AtomicInteger pullCount = new AtomicInteger(0);

      StreamTraversal<VStream<Integer>, Integer> st = StreamTraversal.forVStream();
      VStream<Integer> source = VStream.of(1, 2, 3, 4, 5).peek(x -> pullCount.incrementAndGet());

      VStream<Integer> streamed = st.stream(source);

      // Nothing materialised yet
      assertThat(pullCount.get()).isEqualTo(0);

      // Pull just 2 elements
      streamed.take(2).toList().run();
      assertThat(pullCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("take(n) from streamed traversal pulls only n elements")
    void takeFromStreamedTraversalPullsOnlyN() {
      AtomicInteger pullCount = new AtomicInteger(0);

      StreamTraversal<VStream<Integer>, Integer> st = StreamTraversal.forVStream();
      VStream<Integer> source = VStream.generate(() -> pullCount.incrementAndGet());

      VStream<Integer> streamed = st.stream(source);
      List<Integer> result = streamed.take(3).toList().run();

      assertThat(result).containsExactly(1, 2, 3);
      assertThat(pullCount.get()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Null Validation")
  class NullValidationTests {

    @Test
    @DisplayName("andThen(StreamTraversal) validates non-null")
    void andThenStreamTraversalValidatesNonNull() {
      StreamTraversal<List<Integer>, Integer> st = StreamTraversal.forList();

      assertThatNullPointerException()
          .isThrownBy(() -> st.andThen((StreamTraversal<Integer, Object>) null))
          .withMessageContaining("other must not be null");
    }

    @Test
    @DisplayName("andThen(Lens) validates non-null")
    void andThenLensValidatesNonNull() {
      StreamTraversal<List<Integer>, Integer> st = StreamTraversal.forList();

      assertThatNullPointerException()
          .isThrownBy(() -> st.andThen((Lens<Integer, Object>) null))
          .withMessageContaining("lens must not be null");
    }

    @Test
    @DisplayName("fromTraversal() validates non-null")
    void fromTraversalValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> StreamTraversal.fromTraversal(null))
          .withMessageContaining("traversal must not be null");
    }
  }
}
