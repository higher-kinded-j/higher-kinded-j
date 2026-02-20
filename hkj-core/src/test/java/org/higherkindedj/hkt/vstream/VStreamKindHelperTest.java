// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VStreamKindHelper Test Suite")
class VStreamKindHelperTest {

  @Nested
  @DisplayName("widen() Operations")
  class WidenOperations {

    @Test
    @DisplayName("widen() returns same VStream instance")
    void widenReturnsSameVStreamInstance() {
      VStream<Integer> original = VStream.of(1, 2, 3);

      Kind<VStreamKind.Witness, Integer> kind = VSTREAM.widen(original);

      assertThat(kind).isSameAs(original);
    }

    @Test
    @DisplayName("widen() throws for null input")
    void widenThrowsForNullInput() {
      assertThatThrownBy(() -> VSTREAM.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("VStream cannot be null");
    }

    @Test
    @DisplayName("widen() preserves VStream laziness")
    void widenPreservesVStreamLaziness() {
      AtomicInteger counter = new AtomicInteger(0);
      VStream<Integer> stream =
          VStream.generate(
              () -> {
                counter.incrementAndGet();
                return 42;
              });

      Kind<VStreamKind.Witness, Integer> kind = VSTREAM.widen(stream);

      // Should not execute during widening
      assertThat(counter.get()).isZero();

      // Execute take(1) and verify laziness
      List<Integer> result = VSTREAM.narrow(kind).take(1).toList().run();
      assertThat(result).containsExactly(42);
      assertThat(counter.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("narrow() Operations")
  class NarrowOperations {

    @Test
    @DisplayName("narrow() returns original VStream")
    void narrowReturnsOriginalVStream() {
      VStream<Integer> original = VStream.of(1, 2, 3);
      Kind<VStreamKind.Witness, Integer> kind = VSTREAM.widen(original);

      VStream<Integer> narrowed = VSTREAM.narrow(kind);

      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("narrow() throws for null input")
    void narrowThrowsForNullInput() {
      assertThatThrownBy(() -> VSTREAM.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind for VStream");
    }

    @Test
    @DisplayName("narrow() throws for unknown Kind type")
    void narrowThrowsForUnknownKindType() {
      // Create a dummy VStreamKind that is not a VStream
      VStreamKind<String> unknownKind = new VStreamKind<String>() {};

      assertThatThrownBy(() -> VSTREAM.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind instance cannot be narrowed to VStream");
    }

    @Test
    @DisplayName("narrow() preserves VStream identity")
    void narrowPreservesVStreamIdentity() {
      VStream<String> original = VStream.of("hello", "world");
      Kind<VStreamKind.Witness, String> widened = VSTREAM.widen(original);
      VStream<String> narrowed = VSTREAM.narrow(widened);

      assertThat(narrowed).isSameAs(original);
      assertThat(narrowed.toList().run()).containsExactly("hello", "world");
    }
  }

  @Nested
  @DisplayName("Round-trip Tests")
  class RoundTripTests {

    @Test
    @DisplayName("widen then narrow preserves identity")
    void widenThenNarrowPreservesIdentity() {
      VStream<Integer> original = VStream.of(1, 2, 3);

      Kind<VStreamKind.Witness, Integer> widened = VSTREAM.widen(original);
      VStream<Integer> narrowed = VSTREAM.narrow(widened);

      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("multiple widen/narrow cycles preserve identity")
    void multipleWidenNarrowCyclesPreserveIdentity() {
      VStream<Integer> original = VStream.of(42);

      Kind<VStreamKind.Witness, Integer> kind = VSTREAM.widen(original);
      for (int i = 0; i < 5; i++) {
        VStream<Integer> narrowed = VSTREAM.narrow(kind);
        kind = VSTREAM.widen(narrowed);
      }

      assertThat(VSTREAM.narrow(kind)).isSameAs(original);
    }

    @Test
    @DisplayName("round-trip preserves stream content")
    void roundTripPreservesStreamContent() {
      VStream<String> original = VStream.fromList(List.of("a", "b", "c"));

      Kind<VStreamKind.Witness, String> widened = VSTREAM.widen(original);
      VStream<String> narrowed = VSTREAM.narrow(widened);

      assertThat(narrowed.toList().run()).containsExactly("a", "b", "c");
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("handles empty VStream")
    void handlesEmptyVStream() {
      VStream<Integer> empty = VStream.empty();

      Kind<VStreamKind.Witness, Integer> kind = VSTREAM.widen(empty);
      VStream<Integer> narrowed = VSTREAM.narrow(kind);

      assertThat(narrowed.toList().run()).isEmpty();
    }

    @Test
    @DisplayName("handles single-element VStream")
    void handlesSingleElementVStream() {
      VStream<Integer> single = VStream.of(42);

      Kind<VStreamKind.Witness, Integer> kind = VSTREAM.widen(single);
      VStream<Integer> narrowed = VSTREAM.narrow(kind);

      assertThat(narrowed.toList().run()).containsExactly(42);
    }

    @Test
    @DisplayName("handles generic types")
    void handlesGenericTypes() {
      VStream<List<Integer>> stream = VStream.of(List.of(1, 2), List.of(3, 4));

      Kind<VStreamKind.Witness, List<Integer>> kind = VSTREAM.widen(stream);
      VStream<List<Integer>> narrowed = VSTREAM.narrow(kind);

      assertThat(narrowed.toList().run()).containsExactly(List.of(1, 2), List.of(3, 4));
    }
  }
}
