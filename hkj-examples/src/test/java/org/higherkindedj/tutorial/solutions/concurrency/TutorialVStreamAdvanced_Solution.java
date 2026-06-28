// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.VStreamAssert.assertThatVStream;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.NaturalTransformation;
import org.higherkindedj.hkt.effect.VStreamTransformations;
import org.higherkindedj.hkt.effect.context.VStreamContext;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vstream.VStreamReactive;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.extensions.StreamTraversal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solution for TutorialVStreamAdvanced — teaching-solution format.
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
@DisplayName("Solution: VStream Advanced Features")
public class TutorialVStreamAdvanced_Solution {

  // ===========================================================================
  // Part 1: Resource-Safe Streaming with bracket
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Resource-Safe Streaming")
  class ResourceSafeStreaming {

    /**
     * Why this is idiomatic: {@code VStream.bracket(acquire, use, release)} is the canonical
     * resource-safe pattern. Resource opens on subscription and closes on completion or failure.
     *
     * <p>Alternative: a try-with-resources block. Same semantics for synchronous code; bracket
     * integrates with the lazy stream so it works for both finite and infinite consumption.
     *
     * <p>Common wrong attempt: skip the release lambda. Resources leak; bracket always wants both
     * halves of the contract.
     */
    @Test
    @DisplayName("Exercise 1: Basic bracket usage")
    void exercise1_basicBracket() {
      AtomicBoolean resourceOpen = new AtomicBoolean(false);

      // SOLUTION: VStream.bracket acquires, uses, and releases a resource
      List<String> result =
          VStream.<String, String>bracket(
                  VTask.of(
                      () -> {
                        resourceOpen.set(true);
                        return "handle";
                      }),
                  handle -> VStream.of("item1", "item2"),
                  handle -> VTask.exec(() -> resourceOpen.set(false)))
              .toList()
              .run();

      assertThat(result).containsExactly("item1", "item2");
      assertThat(resourceOpen).isFalse();
    }

    /**
     * Why this is idiomatic: bracket's release runs even when the consumer takes only part of the
     * stream. The contract is "release once the stream is no longer needed", not "release at end of
     * source".
     *
     * <p>Alternative: manual cleanup after each take. Brittle; bracket centralises the lifetime.
     *
     * <p>Common wrong attempt: assume {@code take(n)} implies "release later". The release fires as
     * soon as the consumer stops; the test asserts this behaviour.
     */
    @Test
    @DisplayName("Exercise 2: Release on partial consumption")
    void exercise2_releaseOnPartialConsumption() {
      AtomicBoolean released = new AtomicBoolean(false);

      // SOLUTION: bracket release runs even with take(2)
      List<Integer> result =
          VStream.<Integer, Integer>bracket(
                  VTask.succeed(0),
                  _ -> VStream.of(1, 2, 3, 4, 5),
                  _ -> VTask.exec(() -> released.set(true)))
              .take(2)
              .toList()
              .run();

      assertThat(result).hasSize(2);
      assertThat(released).isTrue();
    }

    /**
     * Why this is idiomatic: {@code onFinalize(task)} attaches a cleanup action that runs once the
     * stream is fully consumed. Useful for logging or metrics.
     *
     * <p>Alternative: wrap the consumer in {@code try/finally}. Equivalent for synchronous code;
     * {@code onFinalize} stays inside the stream API.
     *
     * <p>Common wrong attempt: assume {@code onFinalize} runs eagerly. The action runs at
     * completion; lazy until then.
     */
    @Test
    @DisplayName("Exercise 3: onFinalize for cleanup")
    void exercise3_onFinalize() {
      AtomicReference<String> log = new AtomicReference<>("");

      // SOLUTION: onFinalize attaches a cleanup action
      List<String> result =
          VStream.of("a", "b", "c").onFinalize(VTask.exec(() -> log.set("done"))).toList().run();

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
     * Why this is idiomatic: {@code StreamTraversal.forVStream()} is the lazy- traversal cousin of
     * {@code Traversals.forList} but for VStreams. Stream the focus instead of materialising a
     * list.
     *
     * <p>Alternative: collect to list, traverse, rebuild. Equivalent answer; the stream traversal
     * stays lazy.
     *
     * <p>Common wrong attempt: pair {@code StreamTraversal} with a non-stream source. The traversal
     * is type-specific; pick the matching factory.
     */
    @Test
    @DisplayName("Exercise 4: StreamTraversal.forVStream()")
    void exercise4_vstreamTraversal() {
      VStream<Integer> source = VStream.of(10, 20, 30);

      // SOLUTION: StreamTraversal.forVStream() creates a lazy traversal
      StreamTraversal<VStream<Integer>, Integer> st = StreamTraversal.forVStream();
      List<Integer> result = st.stream(source).toList().run();

      assertThat(result).containsExactly(10, 20, 30);
    }

    /**
     * Why this is idiomatic: {@code StreamTraversal.forList().modify(fn, source)} applies a
     * function across every element and rebuilds the list lazily.
     *
     * <p>Alternative: stream-map and {@code toList()}. Same answer; the stream traversal stays
     * composable with other optic combinators.
     *
     * <p>Common wrong attempt: use a regular {@code Traversal} when the source may be huge. The
     * lazy stream traversal handles size better.
     */
    @Test
    @DisplayName("Exercise 5: StreamTraversal modify")
    void exercise5_streamTraversalModify() {
      List<Integer> source = List.of(1, 2, 3, 4);

      // SOLUTION: StreamTraversal.forList().modify() transforms all elements
      StreamTraversal<List<Integer>, Integer> st = StreamTraversal.forList();
      List<Integer> result = st.modify(x -> x * 10, source);

      assertThat(result).containsExactly(10, 20, 30, 40);
    }

    /**
     * Why this is idiomatic: {@code StreamTraversal.andThen(lens)} composes a lazy traversal with a
     * lens — every element seen through the lens. The result is another stream traversal.
     *
     * <p>Alternative: a custom traversal that includes the lens projection. Equivalent; the {@code
     * andThen} keeps the pieces named.
     *
     * <p>Common wrong attempt: forget that the lens is per-element. The lens focuses inside each
     * element, not over the whole list.
     */
    @Test
    @DisplayName("Exercise 6: StreamTraversal + Lens composition")
    void exercise6_streamTraversalWithLens() {
      record User(String name, int age) {}

      Lens<User, String> nameLens = Lens.of(User::name, (u, n) -> new User(n, u.age()));
      List<User> users = List.of(new User("alice", 30), new User("bob", 25));

      // SOLUTION: andThen(lens) composes StreamTraversal with Lens
      StreamTraversal<List<User>, User> listST = StreamTraversal.forList();
      StreamTraversal<List<User>, String> namesST = listST.andThen(nameLens);
      List<String> result = namesST.stream(users).toList().run();

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
     * Why this is idiomatic: bridge VStream into and out of {@code Flow.Publisher} so reactive
     * consumers (Reactor, RxJava) can integrate. The round-trip preserves elements.
     *
     * <p>Alternative: use a reactive library directly. The bridge keeps the higher-kinded-j layer
     * for upstream code while presenting a {@code Flow.Publisher} surface to downstream.
     *
     * <p>Common wrong attempt: forget the buffer size on {@code fromPublisher}. Pick a sensible
     * bound based on consumer rate; too small backs up, too large risks memory pressure.
     */
    @Test
    @DisplayName("Exercise 7: VStream round-trip via Publisher")
    void exercise7_publisherRoundTrip() {
      VStream<Integer> original = VStream.of(1, 2, 3);

      // SOLUTION: toPublisher + fromPublisher for round-trip
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(original);
      VStream<Integer> roundTripped = VStreamReactive.fromPublisher(publisher, 16);
      assertThatVStream(roundTripped).producesElements(1, 2, 3);
    }
  }

  // ===========================================================================
  // Part 4: Natural Transformations
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Natural Transformations")
  class NaturalTransformationExercises {

    /**
     * Why this is idiomatic: a {@code NaturalTransformation<ListKind.Witness, VStreamKind.Witness>}
     * converts {@code Kind} values from one functor to another. The widen/narrow round-trip stays
     * at the {@code Kind} level.
     *
     * <p>Alternative: write a dedicated converter method. Same outcome; the natural-transformation
     * form is the type-class abstraction.
     *
     * <p>Common wrong attempt: skip widen and try to convert concrete values directly. The natural
     * transformation is at the {@code Kind} layer; widen first.
     */
    @Test
    @DisplayName("Exercise 8: List -> VStream transformation")
    void exercise8_listToVStream() {
      List<String> source = List.of("x", "y", "z");

      // SOLUTION: Use NaturalTransformation with Kind widen/narrow
      NaturalTransformation<ListKind.Witness, VStreamKind.Witness> nt =
          VStreamTransformations.listToVStream();
      Kind<ListKind.Witness, String> listKind = LIST.widen(source);
      Kind<VStreamKind.Witness, String> vstreamKind = nt.apply(listKind);
      VStream<String> vstream = VSTREAM.narrow(vstreamKind);
      assertThatVStream(vstream).producesElements("x", "y", "z");
    }
  }

  // ===========================================================================
  // Part 5: VStreamContext (Layer 2 API)
  // ===========================================================================

  @Nested
  @DisplayName("Part 5: VStreamContext")
  class VStreamContextExercises {

    /**
     * Why this is idiomatic: {@code VStreamContext} is a Layer-2 facade — same pipeline as VStream
     * but with no widen/narrow. Use it when callers want a cleaner surface.
     *
     * <p>Alternative: drop to {@code VStream} directly. Equivalent runtime; the Context layer is
     * for ergonomics.
     *
     * <p>Common wrong attempt: mix Layer-1 ({@code VStream.toList().run()}) with Layer-2 ({@code
     * VStreamContext.toList()}) in the same pipeline. Pick one style per pipeline.
     */
    @Test
    @DisplayName("Exercise 9: VStreamContext pipeline")
    void exercise9_contextPipeline() {
      // SOLUTION: VStreamContext provides clean, HKT-free API
      List<String> result =
          VStreamContext.range(1, 20)
              .filter(n -> n % 2 == 0)
              .map(n -> "Even: " + n)
              .take(3)
              .toList();

      assertThat(result).containsExactly("Even: 2", "Even: 4", "Even: 6");
    }

    /**
     * Why this is idiomatic: {@code VStreamContext.via(fn)} is the Layer-2 form of {@code flatMap}.
     * Each element expands to a sub-context; results concatenate.
     *
     * <p>Alternative: pull the underlying VStream and call {@code flatMap}. Equivalent; {@code via}
     * stays inside the Context API.
     *
     * <p>Common wrong attempt: return a {@code List} from the lambda. {@code via} needs a {@code
     * VStreamContext}; lift with {@code VStreamContext.fromList}.
     */
    @Test
    @DisplayName("Exercise 10: VStreamContext flatMap")
    void exercise10_contextFlatMap() {
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3));

      // SOLUTION: via() chains dependent computations
      List<Integer> result = ctx.via(x -> VStreamContext.fromList(List.of(x, x * 10))).toList();

      assertThat(result).containsExactly(1, 10, 2, 20, 3, 30);
    }

    /**
     * Why this is idiomatic: {@code VStreamContext.fold(seed, combiner)} reduces with an identity
     * and an associative operator. Returns the value directly (no wrapping VTask in Layer-2).
     *
     * <p>Alternative: collect to list and sum. Same answer; the fold avoids the intermediate list.
     *
     * <p>Common wrong attempt: forget the seed for empty contexts. The fold returns the seed for
     * empty inputs; pick zero for sum, one for product.
     */
    @Test
    @DisplayName("Exercise 11: VStreamContext fold")
    void exercise11_contextFold() {
      // SOLUTION: fold() reduces elements with an identity and operator
      Integer result = VStreamContext.fromList(List.of(1, 2, 3, 4, 5)).fold(0, Integer::sum);

      assertThat(result).isEqualTo(15);
    }

    /**
     * Why this is idiomatic: a bracketed VStream wrapped in {@code VStreamContext} gives the
     * Layer-2 ergonomics with the resource-safety of bracket. The release fires when the consumer
     * finishes.
     *
     * <p>Alternative: build a {@code VStreamContext} that calls bracket internally. Same outcome;
     * the wrapping form keeps both layers visible.
     *
     * <p>Common wrong attempt: miss the {@code VStreamContext.of(bracketed)} lift. The bracketed
     * VStream needs to enter the Context layer; otherwise downstream Context combinators do not
     * apply.
     */
    @Test
    @DisplayName("Exercise 12: bracket + VStreamContext")
    void exercise12_bracketWithContext() {
      AtomicBoolean released = new AtomicBoolean(false);

      // SOLUTION: Wrap a bracketed VStream in VStreamContext
      VStream<Integer> bracketed =
          VStream.<Integer, Integer>bracket(
              VTask.succeed(0),
              _ -> VStream.of(10, 20, 30),
              _ -> VTask.exec(() -> released.set(true)));

      List<Integer> result = VStreamContext.of(bracketed).map(x -> x + 1).toList();

      assertThat(result).containsExactly(11, 21, 31);
      assertThat(released).isTrue();
    }
  }
}
