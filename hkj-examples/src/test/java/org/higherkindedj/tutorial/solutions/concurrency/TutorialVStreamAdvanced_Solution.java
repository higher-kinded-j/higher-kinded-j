// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Solutions for VStream Advanced Features Tutorial.
 *
 * <p>This file contains the working solutions for TutorialVStreamAdvanced. Compare your answers
 * against these solutions.
 */
@DisplayName("Solution: VStream Advanced Features")
public class TutorialVStreamAdvanced_Solution {

  // ===========================================================================
  // Part 1: Resource-Safe Streaming with bracket
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Resource-Safe Streaming")
  class ResourceSafeStreaming {

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

    @Test
    @DisplayName("Exercise 4: StreamTraversal.forVStream()")
    void exercise4_vstreamTraversal() {
      VStream<Integer> source = VStream.of(10, 20, 30);

      // SOLUTION: StreamTraversal.forVStream() creates a lazy traversal
      StreamTraversal<VStream<Integer>, Integer> st = StreamTraversal.forVStream();
      List<Integer> result = st.stream(source).toList().run();

      assertThat(result).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("Exercise 5: StreamTraversal modify")
    void exercise5_streamTraversalModify() {
      List<Integer> source = List.of(1, 2, 3, 4);

      // SOLUTION: StreamTraversal.forList().modify() transforms all elements
      StreamTraversal<List<Integer>, Integer> st = StreamTraversal.forList();
      List<Integer> result = st.modify(x -> x * 10, source);

      assertThat(result).containsExactly(10, 20, 30, 40);
    }

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

    @Test
    @DisplayName("Exercise 7: VStream round-trip via Publisher")
    void exercise7_publisherRoundTrip() {
      VStream<Integer> original = VStream.of(1, 2, 3);

      // SOLUTION: toPublisher + fromPublisher for round-trip
      Flow.Publisher<Integer> publisher = VStreamReactive.toPublisher(original);
      VStream<Integer> roundTripped = VStreamReactive.fromPublisher(publisher, 16);
      List<Integer> result = roundTripped.toList().run();

      assertThat(result).containsExactly(1, 2, 3);
    }
  }

  // ===========================================================================
  // Part 4: Natural Transformations
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Natural Transformations")
  class NaturalTransformationExercises {

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
      List<String> result = vstream.toList().run();

      assertThat(result).containsExactly("x", "y", "z");
    }
  }

  // ===========================================================================
  // Part 5: VStreamContext (Layer 2 API)
  // ===========================================================================

  @Nested
  @DisplayName("Part 5: VStreamContext")
  class VStreamContextExercises {

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

    @Test
    @DisplayName("Exercise 10: VStreamContext flatMap")
    void exercise10_contextFlatMap() {
      VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3));

      // SOLUTION: via() chains dependent computations
      List<Integer> result = ctx.via(x -> VStreamContext.fromList(List.of(x, x * 10))).toList();

      assertThat(result).containsExactly(1, 10, 2, 20, 3, 30);
    }

    @Test
    @DisplayName("Exercise 11: VStreamContext fold")
    void exercise11_contextFold() {
      // SOLUTION: fold() reduces elements with an identity and operator
      Integer result = VStreamContext.fromList(List.of(1, 2, 3, 4, 5)).fold(0, Integer::sum);

      assertThat(result).isEqualTo(15);
    }

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
