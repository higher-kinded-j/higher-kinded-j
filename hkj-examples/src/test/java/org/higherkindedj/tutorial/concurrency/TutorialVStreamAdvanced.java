// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: VStream Advanced Features - Resources, StreamTraversal, Reactive Interop
 *
 * <p>Learn to use resource-safe streaming with bracket and onFinalize, lazy optics with
 * StreamTraversal, reactive interop with Flow.Publisher, natural transformations between stream
 * types, and the VStreamContext Layer 2 API.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>bracket: acquire a resource, produce a stream, guarantee release
 *   <li>onFinalize: attach cleanup actions to any stream
 *   <li>StreamTraversal: lazy element access and modification through optics
 *   <li>VStreamReactive: bridge between VStream and Flow.Publisher
 *   <li>VStreamTransformations: natural transformations between stream types
 *   <li>VStreamContext: Layer 2 API hiding HKT complexity
 * </ul>
 *
 * <p>Prerequisites: TutorialVStream, TutorialVStreamParallel
 *
 * <p>Estimated time: 15-20 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial: VStream Advanced Features")
public class TutorialVStreamAdvanced {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: Resource-Safe Streaming with bracket
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Resource-Safe Streaming")
  class ResourceSafeStreaming {

    /**
     * Exercise 1: Basic bracket usage
     *
     * <p>VStream.bracket(acquire, use, release) creates a resource-safe stream. The resource is
     * acquired lazily on first pull, used to produce a stream, and released when the stream
     * completes, errors, or is partially consumed.
     *
     * <p>Task: Use VStream.bracket to create a stream that acquires a resource (setting the
     * AtomicBoolean to true), produces items from it, and releases it (setting the AtomicBoolean
     * back to false).
     *
     * <p>Hint: VStream.bracket(VTask.of(() -> { ... }), resource -> VStream.of(...), resource ->
     * VTask.exec(() -> { ... }))
     */
    @Test
    @DisplayName("Exercise 1: Basic bracket usage")
    void exercise1_basicBracket() {
      AtomicBoolean resourceOpen = new AtomicBoolean(false);

      // TODO: Use VStream.bracket to:
      // - Acquire: set resourceOpen to true, return "handle"
      // - Use: produce VStream.of("item1", "item2")
      // - Release: set resourceOpen back to false
      List<String> result = answerRequired();

      assertThat(result).containsExactly("item1", "item2");
      assertThat(resourceOpen).isFalse(); // Resource was released
    }

    /**
     * Exercise 2: Verify resource release on partial consumption
     *
     * <p>Even when only part of the stream is consumed (via take, headOption, etc.), the bracket
     * release function still runs. This guarantees cleanup regardless of how much of the stream the
     * consumer reads.
     *
     * <p>Task: Create a bracketed stream of 5 elements, take only 2, and verify the resource was
     * released.
     */
    @Test
    @DisplayName("Exercise 2: Release on partial consumption")
    void exercise2_releaseOnPartialConsumption() {
      AtomicBoolean released = new AtomicBoolean(false);

      // TODO: Create a bracketed stream of 5 integers, take only 2, collect to list
      // Hint: VStream.bracket(...).take(2).toList().run()
      List<Integer> result = answerRequired();

      assertThat(result).hasSize(2);
      assertThat(released).isTrue();
    }

    /**
     * Exercise 3: onFinalize for cleanup actions
     *
     * <p>VStream.onFinalize(VTask) attaches a cleanup action to any stream. The finaliser runs
     * exactly once when the stream completes or errors.
     *
     * <p>Task: Attach an onFinalize action that records "done" in the log, then collect the stream.
     */
    @Test
    @DisplayName("Exercise 3: onFinalize for cleanup")
    void exercise3_onFinalize() {
      AtomicReference<String> log = new AtomicReference<>("");

      // TODO: Use VStream.of("a", "b", "c").onFinalize(...) to record "done" in log
      // Hint: .onFinalize(VTask.exec(() -> log.set("done")))
      List<String> result = answerRequired();

      assertThat(result).containsExactly("a", "b", "c");
      assertThat(log.get()).isEqualTo("done");
    }
  }

  // ===========================================================================
  // Part 2: StreamTraversal (Lazy Optics)
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: StreamTraversal")
  class StreamTraversalExercises {

    /**
     * Exercise 4: Create a StreamTraversal for VStream
     *
     * <p>StreamTraversal.forVStream() creates a StreamTraversal that lazily streams all elements of
     * a VStream. Unlike standard Traversal which materialises via Applicative, StreamTraversal
     * preserves laziness.
     *
     * <p>Task: Use StreamTraversal.forVStream() to stream elements from a VStream.
     */
    @Test
    @DisplayName("Exercise 4: StreamTraversal.forVStream()")
    void exercise4_vstreamTraversal() {
      // Given
      // VStream<Integer> source = VStream.of(10, 20, 30);

      // TODO: Create a StreamTraversal and use stream() to get elements
      // Hint: StreamTraversal.forVStream().stream(source).toList().run()
      List<Integer> result = answerRequired();

      assertThat(result).containsExactly(10, 20, 30);
    }

    /**
     * Exercise 5: Modify all elements via StreamTraversal
     *
     * <p>StreamTraversal.modify(f, source) applies a pure function to all focused elements.
     *
     * <p>Task: Use StreamTraversal.forList().modify() to multiply all list elements by 10.
     */
    @Test
    @DisplayName("Exercise 5: StreamTraversal modify")
    void exercise5_streamTraversalModify() {
      // Given
      // List<Integer> source = List.of(1, 2, 3, 4);

      // TODO: Use StreamTraversal.forList().modify(x -> x * 10, source)
      List<Integer> result = answerRequired();

      assertThat(result).containsExactly(10, 20, 30, 40);
    }

    /**
     * Exercise 6: Compose StreamTraversal with Lens
     *
     * <p>StreamTraversal.andThen(Lens) composes a stream traversal with a lens, allowing you to
     * focus into a field of each element.
     *
     * <p>Task: Compose a list StreamTraversal with a Lens to extract all names.
     */
    @Test
    @DisplayName("Exercise 6: StreamTraversal + Lens composition")
    void exercise6_streamTraversalWithLens() {
      record User(String name, int age) {}

      // Given
      // Lens<User, String> nameLens = Lens.of(User::name, (n, u) -> new User(n, u.age()));
      // List<User> users = List.of(new User("alice", 30), new User("bob", 25));

      // TODO: Compose StreamTraversal.forList().andThen(nameLens) and stream the names
      // Hint: listST.andThen(nameLens).stream(users).toList().run()
      List<String> result = answerRequired();

      assertThat(result).containsExactly("alice", "bob");
    }
  }

  // ===========================================================================
  // Part 3: Reactive Interop (Flow.Publisher)
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Reactive Interop")
  class ReactiveInterop {

    /**
     * Exercise 7: Convert VStream to Flow.Publisher and back
     *
     * <p>VStreamReactive.toPublisher(stream) converts a VStream to a Flow.Publisher.
     * VStreamReactive.fromPublisher(publisher, bufferSize) converts a Publisher back to a VStream.
     * This enables interop with reactive frameworks.
     *
     * <p>Task: Convert a VStream to a Publisher and back, verifying elements are preserved.
     */
    @Test
    @DisplayName("Exercise 7: VStream round-trip via Publisher")
    void exercise7_publisherRoundTrip() {
      // Given
      // VStream<Integer> original = VStream.of(1, 2, 3);

      // TODO: Convert to publisher, then back to VStream, then collect
      // Hint: VStreamReactive.toPublisher(original) then VStreamReactive.fromPublisher(pub, 16)
      List<Integer> result = answerRequired();

      assertThat(result).containsExactly(1, 2, 3);
    }
  }

  // ===========================================================================
  // Part 4: Natural Transformations
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Natural Transformations")
  class NaturalTransformationExercises {

    /**
     * Exercise 8: Transform List to VStream via natural transformation
     *
     * <p>VStreamTransformations provides natural transformations between stream types.
     * listToVStream() creates a transformation from ListKind to VStreamKind.
     *
     * <p>Task: Use VStreamTransformations.listToVStream() to transform a List into a VStream.
     *
     * <p>Hint: Use LIST.widen() to create a ListKind, apply the transformation, then
     * VSTREAM.narrow() and collect.
     */
    @Test
    @DisplayName("Exercise 8: List -> VStream transformation")
    void exercise8_listToVStream() {
      // Given
      // List<String> source = List.of("x", "y", "z");

      // TODO: Use VStreamTransformations.listToVStream() to transform and collect
      List<String> result = answerRequired();

      assertThat(result).containsExactly("x", "y", "z");
    }
  }

  // ===========================================================================
  // Part 5: VStreamContext (Layer 2 API)
  // ===========================================================================

  @Nested
  @DisplayName("Part 5: VStreamContext")
  class VStreamContextExercises {

    /**
     * Exercise 9: Build a pipeline with VStreamContext
     *
     * <p>VStreamContext provides a clean API without HKT complexity. It supports map, filter, take,
     * flatMap, and blocking terminal operations like toList().
     *
     * <p>Task: Create a pipeline that filters even numbers from a range, maps them to strings, and
     * takes the first 3.
     */
    @Test
    @DisplayName("Exercise 9: VStreamContext pipeline")
    void exercise9_contextPipeline() {
      // TODO: Use VStreamContext.range(1, 20).filter(...).map(...).take(3).toList()
      List<String> result = answerRequired();

      assertThat(result).containsExactly("Even: 2", "Even: 4", "Even: 6");
    }

    /**
     * Exercise 10: VStreamContext flatMap
     *
     * <p>VStreamContext.via() (or flatMap()) chains dependent computations, similar to VStream's
     * flatMap.
     *
     * <p>Task: Use via() to expand each number into [n, n*10].
     */
    @Test
    @DisplayName("Exercise 10: VStreamContext flatMap")
    void exercise10_contextFlatMap() {
      // Given
      // VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3));

      // TODO: Use .via(x -> VStreamContext.fromList(List.of(x, x * 10))).toList()
      List<Integer> result = answerRequired();

      assertThat(result).containsExactly(1, 10, 2, 20, 3, 30);
    }

    /**
     * Exercise 11: VStreamContext terminal operations
     *
     * <p>VStreamContext provides blocking terminal operations: fold, exists, forAll, count,
     * headOption, find.
     *
     * <p>Task: Use fold to sum a list of integers.
     */
    @Test
    @DisplayName("Exercise 11: VStreamContext fold")
    void exercise11_contextFold() {
      // TODO: Use VStreamContext.fromList(List.of(1, 2, 3, 4, 5)).fold(0, Integer::sum)
      Integer result = answerRequired();

      assertThat(result).isEqualTo(15);
    }

    /**
     * Exercise 12: Combine bracket with VStreamContext
     *
     * <p>VStreamContext.of() can wrap any VStream, including resource-safe streams created with
     * bracket. This combines resource safety with the clean Layer 2 API.
     *
     * <p>Task: Create a bracketed stream, wrap it in VStreamContext, and process it.
     */
    @Test
    @DisplayName("Exercise 12: bracket + VStreamContext")
    void exercise12_bracketWithContext() {
      AtomicBoolean released = new AtomicBoolean(false);

      // TODO: Create a VStream.bracket that produces VStream.of(10, 20, 30),
      // wrap in VStreamContext.of(), map(x -> x + 1), and toList()
      // The release should set released to true
      List<Integer> result = answerRequired();

      assertThat(result).containsExactly(11, 21, 31);
      assertThat(released).isTrue();
    }
  }

  // ===========================================================================
  // Congratulations! 🎉
  //
  // You have learned:
  // - Resource-safe streaming with bracket and onFinalize
  // - Lazy optics with StreamTraversal
  // - Reactive interop with Flow.Publisher
  // - Natural transformations between stream types
  // - The VStreamContext Layer 2 API
  //
  // These advanced features integrate VStream with the wider Higher-Kinded-J
  // ecosystem and enable production-grade streaming patterns.
  // ===========================================================================
}
