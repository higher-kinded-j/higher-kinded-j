// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StreamKindHelper Tests")
class StreamKindHelperTest {

  @Nested
  @DisplayName("widen()")
  class WidenTests {

    @Test
    void widen_shouldReturnStreamHolderForValidStream() {
      Stream<String> stream = Stream.of("a", "b");
      Kind<StreamKind.Witness, String> kind = STREAM.widen(stream);
      assertThat(kind).isInstanceOf(StreamKindHelper.StreamHolder.class);
      // Verify content by narrowing and collecting (consumes the stream)
      assertThat(STREAM.narrow(kind).collect(Collectors.toList())).containsExactly("a", "b");
    }

    @Test
    void widen_shouldReturnStreamHolderForEmptyStream() {
      Stream<Integer> emptyStream = Stream.empty();
      Kind<StreamKind.Witness, Integer> kind = STREAM.widen(emptyStream);
      assertThat(kind).isInstanceOf(StreamKindHelper.StreamHolder.class);
      assertThat(STREAM.narrow(kind).collect(Collectors.toList())).isEmpty();
    }

    @Test
    void widen_shouldThrowForNullInput() {
      assertThatThrownBy(() -> STREAM.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Input Stream cannot be null");
    }
  }

  @Nested
  @DisplayName("narrow()")
  class NarrowTests {

    @Test
    void narrow_shouldReturnOriginalStream() {
      Stream<Double> originalStream = Stream.of(1.0, 2.5);
      Kind<StreamKind.Witness, Double> kind = STREAM.widen(originalStream);
      Stream<Double> narrowed = STREAM.narrow(kind);
      // The narrowed stream should be the same instance (though we can't verify with isSameAs
      // after consuming)
      assertThat(narrowed.collect(Collectors.toList())).containsExactly(1.0, 2.5);
    }

    @Test
    void narrow_shouldRespectStreamSingleUseSemantics() {
      Stream<Integer> stream = Stream.of(1, 2, 3);
      Kind<StreamKind.Witness, Integer> kind = STREAM.widen(stream);
      Stream<Integer> narrowed = STREAM.narrow(kind);

      // First use - OK
      assertThat(narrowed.collect(Collectors.toList())).containsExactly(1, 2, 3);

      // Second use - should throw IllegalStateException
      assertThatThrownBy(() -> narrowed.count()).isInstanceOf(IllegalStateException.class);
    }

    // Dummy Kind for testing invalid type unwrap
    record DummyStreamKind<A>() implements Kind<StreamKind.Witness, A> {}

    @Test
    void narrow_shouldThrowForUnknownKindType() {
      Kind<StreamKind.Witness, String> unknownKind = new DummyStreamKind<>();
      assertThatThrownBy(() -> STREAM.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              "Kind instance cannot be narrowed to " + Stream.class.getSimpleName());
    }
  }

  @Nested
  @DisplayName("narrowOr()")
  class NarrowOrTests {
    @Test
    void narrowOr_shouldReturnValueWhenPresent() {
      Stream<String> stream = Stream.of("a", "b");
      Kind<StreamKind.Witness, String> kind = STREAM.widen(stream);
      Stream<String> defaultStream = Stream.of("default");
      Stream<String> result = STREAM.narrowOr(kind, defaultStream);
      assertThat(result.collect(Collectors.toList())).containsExactly("a", "b");
    }

    @Test
    void narrowOr_shouldReturnDefaultWhenKindIsNull() {
      Stream<String> defaultStream = Stream.of("default");
      Stream<String> result = STREAM.narrowOr(null, defaultStream);
      assertThat(result.collect(Collectors.toList())).containsExactly("default");
    }

    @Test
    void narrowOr_shouldThrowNPEWhenDefaultIsNull() {
      Stream<String> stream = Stream.of("a", "b");
      Kind<StreamKind.Witness, String> kind = STREAM.widen(stream);
      assertThatThrownBy(() -> STREAM.narrowOr(kind, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Input Stream cannot be null for widen");
    }
  }

  @Nested
  @DisplayName("of()")
  class OfTests {
    @Test
    void of_shouldCreateStreamKind() {
      Stream<Integer> stream = Stream.of(1, 2, 3);
      StreamKind<Integer> kind = STREAM.of(stream);
      assertThat(kind).isInstanceOf(StreamKindHelper.StreamHolder.class);
      assertThat(STREAM.narrow(kind).collect(Collectors.toList())).containsExactly(1, 2, 3);
    }
  }
}
