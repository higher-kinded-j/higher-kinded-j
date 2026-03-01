// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.List;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.NaturalTransformation;
import org.higherkindedj.hkt.effect.VStreamTransformations;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.stream.StreamKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link VStreamTransformations} natural transformations.
 *
 * <p>Verifies element preservation, order preservation, round-trip consistency, and correct
 * handling of empty collections.
 */
@DisplayName("VStreamTransformations Test Suite")
class VStreamTransformationsTest {

  @Nested
  @DisplayName("streamToVStream")
  class StreamToVStreamTests {

    @Test
    @DisplayName("preserves elements and order")
    void preservesElementsAndOrder() {
      NaturalTransformation<StreamKind.Witness, VStreamKind.Witness> nt =
          VStreamTransformations.streamToVStream();

      Kind<StreamKind.Witness, String> streamKind = STREAM.widen(Stream.of("a", "b", "c"));
      Kind<VStreamKind.Witness, String> result = nt.apply(streamKind);

      VStream<String> vstream = VSTREAM.narrow(result);
      assertThat(vstream.toList().run()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("handles empty stream")
    void handlesEmptyStream() {
      NaturalTransformation<StreamKind.Witness, VStreamKind.Witness> nt =
          VStreamTransformations.streamToVStream();

      Kind<StreamKind.Witness, Integer> streamKind = STREAM.widen(Stream.empty());
      Kind<VStreamKind.Witness, Integer> result = nt.apply(streamKind);

      VStream<Integer> vstream = VSTREAM.narrow(result);
      assertThat(vstream.toList().run()).isEmpty();
    }

    @Test
    @DisplayName("handles single element")
    void handlesSingleElement() {
      NaturalTransformation<StreamKind.Witness, VStreamKind.Witness> nt =
          VStreamTransformations.streamToVStream();

      Kind<StreamKind.Witness, Integer> streamKind = STREAM.widen(Stream.of(42));
      Kind<VStreamKind.Witness, Integer> result = nt.apply(streamKind);

      VStream<Integer> vstream = VSTREAM.narrow(result);
      assertThat(vstream.toList().run()).containsExactly(42);
    }
  }

  @Nested
  @DisplayName("vstreamToStream")
  class VStreamToStreamTests {

    @Test
    @DisplayName("preserves elements and order")
    void preservesElementsAndOrder() {
      NaturalTransformation<VStreamKind.Witness, StreamKind.Witness> nt =
          VStreamTransformations.vstreamToStream();

      Kind<VStreamKind.Witness, String> vstreamKind = VSTREAM.widen(VStream.of("x", "y", "z"));
      Kind<StreamKind.Witness, String> result = nt.apply(vstreamKind);

      Stream<String> stream = STREAM.narrow(result);
      assertThat(stream.toList()).containsExactly("x", "y", "z");
    }

    @Test
    @DisplayName("handles empty VStream")
    void handlesEmptyVStream() {
      NaturalTransformation<VStreamKind.Witness, StreamKind.Witness> nt =
          VStreamTransformations.vstreamToStream();

      Kind<VStreamKind.Witness, Integer> vstreamKind = VSTREAM.widen(VStream.empty());
      Kind<StreamKind.Witness, Integer> result = nt.apply(vstreamKind);

      Stream<Integer> stream = STREAM.narrow(result);
      assertThat(stream.toList()).isEmpty();
    }
  }

  @Nested
  @DisplayName("listToVStream")
  class ListToVStreamTests {

    @Test
    @DisplayName("preserves elements and order")
    void preservesElementsAndOrder() {
      NaturalTransformation<ListKind.Witness, VStreamKind.Witness> nt =
          VStreamTransformations.listToVStream();

      Kind<ListKind.Witness, String> listKind = LIST.widen(List.of("p", "q", "r"));
      Kind<VStreamKind.Witness, String> result = nt.apply(listKind);

      VStream<String> vstream = VSTREAM.narrow(result);
      assertThat(vstream.toList().run()).containsExactly("p", "q", "r");
    }

    @Test
    @DisplayName("handles empty list")
    void handlesEmptyList() {
      NaturalTransformation<ListKind.Witness, VStreamKind.Witness> nt =
          VStreamTransformations.listToVStream();

      Kind<ListKind.Witness, Integer> listKind = LIST.widen(List.of());
      Kind<VStreamKind.Witness, Integer> result = nt.apply(listKind);

      VStream<Integer> vstream = VSTREAM.narrow(result);
      assertThat(vstream.toList().run()).isEmpty();
    }
  }

  @Nested
  @DisplayName("vstreamToList")
  class VStreamToListTests {

    @Test
    @DisplayName("preserves elements and order")
    void preservesElementsAndOrder() {
      NaturalTransformation<VStreamKind.Witness, ListKind.Witness> nt =
          VStreamTransformations.vstreamToList();

      Kind<VStreamKind.Witness, String> vstreamKind = VSTREAM.widen(VStream.of("a", "b", "c"));
      Kind<ListKind.Witness, String> result = nt.apply(vstreamKind);

      List<String> list = LIST.narrow(result);
      assertThat(list).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("handles empty VStream")
    void handlesEmptyVStream() {
      NaturalTransformation<VStreamKind.Witness, ListKind.Witness> nt =
          VStreamTransformations.vstreamToList();

      Kind<VStreamKind.Witness, Integer> vstreamKind = VSTREAM.widen(VStream.empty());
      Kind<ListKind.Witness, Integer> result = nt.apply(vstreamKind);

      List<Integer> list = LIST.narrow(result);
      assertThat(list).isEmpty();
    }
  }

  @Nested
  @DisplayName("Round-trip Consistency")
  class RoundTripTests {

    @Test
    @DisplayName("Stream -> VStream -> Stream preserves elements")
    void streamRoundTrip() {
      NaturalTransformation<StreamKind.Witness, VStreamKind.Witness> toVStream =
          VStreamTransformations.streamToVStream();
      NaturalTransformation<VStreamKind.Witness, StreamKind.Witness> toStream =
          VStreamTransformations.vstreamToStream();

      Kind<StreamKind.Witness, Integer> original = STREAM.widen(Stream.of(1, 2, 3, 4, 5));
      Kind<VStreamKind.Witness, Integer> intermediate = toVStream.apply(original);
      Kind<StreamKind.Witness, Integer> result = toStream.apply(intermediate);

      assertThat(STREAM.narrow(result).toList()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("List -> VStream -> List preserves elements")
    void listRoundTrip() {
      NaturalTransformation<ListKind.Witness, VStreamKind.Witness> toVStream =
          VStreamTransformations.listToVStream();
      NaturalTransformation<VStreamKind.Witness, ListKind.Witness> toList =
          VStreamTransformations.vstreamToList();

      Kind<ListKind.Witness, String> original = LIST.widen(List.of("hello", "world"));
      Kind<VStreamKind.Witness, String> intermediate = toVStream.apply(original);
      Kind<ListKind.Witness, String> result = toList.apply(intermediate);

      assertThat(LIST.narrow(result)).containsExactly("hello", "world");
    }

    @Test
    @DisplayName("empty round-trip preserves emptiness")
    void emptyRoundTrip() {
      NaturalTransformation<ListKind.Witness, VStreamKind.Witness> toVStream =
          VStreamTransformations.listToVStream();
      NaturalTransformation<VStreamKind.Witness, ListKind.Witness> toList =
          VStreamTransformations.vstreamToList();

      Kind<ListKind.Witness, String> original = LIST.widen(List.of());
      Kind<VStreamKind.Witness, String> intermediate = toVStream.apply(original);
      Kind<ListKind.Witness, String> result = toList.apply(intermediate);

      assertThat(LIST.narrow(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Composition")
  class CompositionTests {

    @Test
    @DisplayName("andThen composes transformations correctly")
    void andThenComposesTransformations() {
      NaturalTransformation<StreamKind.Witness, VStreamKind.Witness> streamToVStream =
          VStreamTransformations.streamToVStream();
      NaturalTransformation<VStreamKind.Witness, ListKind.Witness> vstreamToList =
          VStreamTransformations.vstreamToList();

      NaturalTransformation<StreamKind.Witness, ListKind.Witness> composed =
          streamToVStream.andThen(vstreamToList);

      Kind<StreamKind.Witness, String> input = STREAM.widen(Stream.of("a", "b"));
      Kind<ListKind.Witness, String> result = composed.apply(input);

      assertThat(LIST.narrow(result)).containsExactly("a", "b");
    }
  }
}
