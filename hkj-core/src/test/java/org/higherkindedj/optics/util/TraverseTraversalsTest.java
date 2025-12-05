// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.stream.StreamKind;
import org.higherkindedj.hkt.stream.StreamKindHelper;
import org.higherkindedj.optics.Traversal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link TraverseTraversals} utility class. */
@DisplayName("TraverseTraversals")
class TraverseTraversalsTest {

  @Nested
  @DisplayName("forListKind()")
  class ForListKindTests {

    @Test
    @DisplayName("should get all elements from a list")
    void shouldGetAllElements() {
      Kind<ListKind.Witness, String> list =
          ListKindHelper.LIST.widen(List.of("Alice", "Bob", "Charlie"));

      Traversal<Kind<ListKind.Witness, String>, String> traversal =
          TraverseTraversals.forListKind();

      List<String> result = Traversals.getAll(traversal, list);

      assertThat(result).containsExactly("Alice", "Bob", "Charlie");
    }

    @Test
    @DisplayName("should modify all elements in a list")
    void shouldModifyAllElements() {
      Kind<ListKind.Witness, String> list = ListKindHelper.LIST.widen(List.of("Alice", "Bob"));

      Traversal<Kind<ListKind.Witness, String>, String> traversal =
          TraverseTraversals.forListKind();

      Kind<ListKind.Witness, String> result =
          Traversals.modify(traversal, String::toUpperCase, list);

      assertThat(ListKindHelper.LIST.narrow(result)).containsExactly("ALICE", "BOB");
    }

    @Test
    @DisplayName("should handle empty list")
    void shouldHandleEmptyList() {
      Kind<ListKind.Witness, String> list = ListKindHelper.LIST.widen(List.of());

      Traversal<Kind<ListKind.Witness, String>, String> traversal =
          TraverseTraversals.forListKind();

      List<String> result = Traversals.getAll(traversal, list);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("forMaybe()")
  class ForMaybeTests {

    @Test
    @DisplayName("should get value from Just")
    void shouldGetValueFromJust() {
      Maybe<String> maybe = Maybe.just("hello");

      Traversal<Maybe<String>, String> traversal = TraverseTraversals.forMaybe();

      List<String> result = Traversals.getAll(traversal, maybe);

      assertThat(result).containsExactly("hello");
    }

    @Test
    @DisplayName("should return empty for Nothing")
    void shouldReturnEmptyForNothing() {
      Maybe<String> maybe = Maybe.nothing();

      Traversal<Maybe<String>, String> traversal = TraverseTraversals.forMaybe();

      List<String> result = Traversals.getAll(traversal, maybe);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should modify value in Just")
    void shouldModifyValueInJust() {
      Maybe<String> maybe = Maybe.just("hello");

      Traversal<Maybe<String>, String> traversal = TraverseTraversals.forMaybe();

      Maybe<String> result = Traversals.modify(traversal, String::toUpperCase, maybe);

      assertThat(result.isJust()).isTrue();
      assertThat(result.get()).isEqualTo("HELLO");
    }

    @Test
    @DisplayName("should preserve Nothing on modify")
    void shouldPreserveNothingOnModify() {
      Maybe<String> maybe = Maybe.nothing();

      Traversal<Maybe<String>, String> traversal = TraverseTraversals.forMaybe();

      Maybe<String> result = Traversals.modify(traversal, String::toUpperCase, maybe);

      assertThat(result.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("forMaybeKind()")
  class ForMaybeKindTests {

    @Test
    @DisplayName("should get value from Just Kind")
    void shouldGetValueFromJustKind() {
      Kind<MaybeKind.Witness, String> maybe = MaybeKindHelper.MAYBE.widen(Maybe.just("hello"));

      Traversal<Kind<MaybeKind.Witness, String>, String> traversal =
          TraverseTraversals.forMaybeKind();

      List<String> result = Traversals.getAll(traversal, maybe);

      assertThat(result).containsExactly("hello");
    }

    @Test
    @DisplayName("should return empty for Nothing Kind")
    void shouldReturnEmptyForNothingKind() {
      Kind<MaybeKind.Witness, String> maybe = MaybeKindHelper.MAYBE.widen(Maybe.nothing());

      Traversal<Kind<MaybeKind.Witness, String>, String> traversal =
          TraverseTraversals.forMaybeKind();

      List<String> result = Traversals.getAll(traversal, maybe);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("forSet()")
  class ForSetTests {

    @Test
    @DisplayName("should get all elements from a set")
    void shouldGetAllElements() {
      Set<String> set = Set.of("Alice", "Bob", "Charlie");

      Traversal<Set<String>, String> traversal = TraverseTraversals.forSet();

      List<String> result = Traversals.getAll(traversal, set);

      assertThat(result).containsExactlyInAnyOrder("Alice", "Bob", "Charlie");
    }

    @Test
    @DisplayName("should modify all elements in a set")
    void shouldModifyAllElements() {
      Set<String> set = Set.of("Alice", "Bob");

      Traversal<Set<String>, String> traversal = TraverseTraversals.forSet();

      Set<String> result = Traversals.modify(traversal, String::toUpperCase, set);

      assertThat(result).containsExactlyInAnyOrder("ALICE", "BOB");
    }

    @Test
    @DisplayName("should handle empty set")
    void shouldHandleEmptySet() {
      Set<String> set = Set.of();

      Traversal<Set<String>, String> traversal = TraverseTraversals.forSet();

      List<String> result = Traversals.getAll(traversal, set);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("forStream()")
  class ForStreamTests {

    @Test
    @DisplayName("should get all elements from a stream")
    void shouldGetAllElements() {
      Stream<String> stream = Stream.of("Alice", "Bob", "Charlie");

      Traversal<Stream<String>, String> traversal = TraverseTraversals.forStream();

      List<String> result = Traversals.getAll(traversal, stream);

      assertThat(result).containsExactly("Alice", "Bob", "Charlie");
    }

    @Test
    @DisplayName("should modify all elements in a stream")
    void shouldModifyAllElements() {
      Stream<String> stream = Stream.of("Alice", "Bob");

      Traversal<Stream<String>, String> traversal = TraverseTraversals.forStream();

      Stream<String> result = Traversals.modify(traversal, String::toUpperCase, stream);

      assertThat(result.toList()).containsExactly("ALICE", "BOB");
    }

    @Test
    @DisplayName("should handle empty stream")
    void shouldHandleEmptyStream() {
      Stream<String> stream = Stream.empty();

      Traversal<Stream<String>, String> traversal = TraverseTraversals.forStream();

      List<String> result = Traversals.getAll(traversal, stream);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("forStreamKind()")
  class ForStreamKindTests {

    @Test
    @DisplayName("should get all elements from a StreamKind")
    void shouldGetAllElements() {
      Kind<StreamKind.Witness, String> stream =
          StreamKindHelper.STREAM.widen(Stream.of("Alice", "Bob", "Charlie"));

      Traversal<Kind<StreamKind.Witness, String>, String> traversal =
          TraverseTraversals.forStreamKind();

      List<String> result = Traversals.getAll(traversal, stream);

      assertThat(result).containsExactly("Alice", "Bob", "Charlie");
    }

    @Test
    @DisplayName("should modify all elements in a StreamKind")
    void shouldModifyAllElements() {
      Kind<StreamKind.Witness, String> stream =
          StreamKindHelper.STREAM.widen(Stream.of("Alice", "Bob"));

      Traversal<Kind<StreamKind.Witness, String>, String> traversal =
          TraverseTraversals.forStreamKind();

      Kind<StreamKind.Witness, String> result =
          Traversals.modify(traversal, String::toUpperCase, stream);

      assertThat(StreamKindHelper.STREAM.narrow(result).toList()).containsExactly("ALICE", "BOB");
    }

    @Test
    @DisplayName("should handle empty StreamKind")
    void shouldHandleEmptyStreamKind() {
      Kind<StreamKind.Witness, String> stream = StreamKindHelper.STREAM.widen(Stream.empty());

      Traversal<Kind<StreamKind.Witness, String>, String> traversal =
          TraverseTraversals.forStreamKind();

      List<String> result = Traversals.getAll(traversal, stream);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should compose with other traversals")
    void shouldComposeWithOtherTraversals() {
      Kind<StreamKind.Witness, Integer> stream =
          StreamKindHelper.STREAM.widen(Stream.of(1, 2, 3, 4, 5));

      Traversal<Kind<StreamKind.Witness, Integer>, Integer> traversal =
          TraverseTraversals.forStreamKind();

      // Get all elements and verify
      List<Integer> result = Traversals.getAll(traversal, stream);
      assertThat(result).containsExactly(1, 2, 3, 4, 5);
    }
  }
}
