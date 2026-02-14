// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeTraversals Utility Class Tests")
class MaybeTraversalsTest {

  @Nested
  @DisplayName("just() - Traversal for Just case")
  class JustTraversal {
    private final Traversal<Maybe<String>, String> traversal = MaybeTraversals.just();

    @Test
    @DisplayName("should modify value in Just")
    void shouldModifyJust() {
      Maybe<String> maybe = Maybe.just("hello");
      Maybe<String> result = Traversals.modify(traversal, String::toUpperCase, maybe);
      assertThat(result.isJust()).isTrue();
      assertThat(result.get()).isEqualTo("HELLO");
    }

    @Test
    @DisplayName("should not modify Nothing")
    void shouldNotModifyNothing() {
      Maybe<String> maybe = Maybe.nothing();
      Maybe<String> result = Traversals.modify(traversal, String::toUpperCase, maybe);
      assertThat(result.isNothing()).isTrue();
    }

    @Test
    @DisplayName("should extract value from Just")
    void shouldExtractFromJust() {
      Maybe<String> maybe = Maybe.just("hello");
      List<String> result = Traversals.getAll(traversal, maybe);
      assertThat(result).containsExactly("hello");
    }

    @Test
    @DisplayName("should return empty list for Nothing")
    void shouldReturnEmptyForNothing() {
      Maybe<String> maybe = Maybe.nothing();
      List<String> result = Traversals.getAll(traversal, maybe);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should work with numeric transformations")
    void shouldWorkWithNumericTransformations() {
      Maybe<Integer> maybe = Maybe.just(42);
      Traversal<Maybe<Integer>, Integer> intTraversal = MaybeTraversals.just();
      Maybe<Integer> result = Traversals.modify(intTraversal, i -> i * 2, maybe);
      assertThat(result.get()).isEqualTo(84);
    }
  }

  @Nested
  @DisplayName("justPrism() - Prism for Just case")
  class JustPrismMethod {
    private final Prism<Maybe<String>, String> prism = MaybeTraversals.justPrism();

    @Test
    @DisplayName("should extract value from Just")
    void shouldExtractFromJust() {
      Maybe<String> maybe = Maybe.just("hello");
      Optional<String> result = prism.getOptional(maybe);
      assertThat(result).isPresent().contains("hello");
    }

    @Test
    @DisplayName("should return empty for Nothing")
    void shouldReturnEmptyForNothing() {
      Maybe<String> maybe = Maybe.nothing();
      Optional<String> result = prism.getOptional(maybe);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should build Just from value")
    void shouldBuild() {
      Maybe<String> result = prism.build("world");
      assertThat(result.isJust()).isTrue();
      assertThat(result.get()).isEqualTo("world");
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {
    @Test
    @DisplayName("should compose with list traversal")
    void shouldComposeWithListTraversal() {
      List<Maybe<String>> maybes =
          List.of(Maybe.just("hello"), Maybe.nothing(), Maybe.just("world"));
      Traversal<List<Maybe<String>>, String> allPresent =
          Traversals.<Maybe<String>>forList().andThen(MaybeTraversals.just());

      List<String> values = Traversals.getAll(allPresent, maybes);
      assertThat(values).containsExactly("hello", "world");
    }

    @Test
    @DisplayName("should modify all present values in list")
    void shouldModifyAllPresent() {
      List<Maybe<String>> maybes =
          List.of(Maybe.just("hello"), Maybe.nothing(), Maybe.just("world"));
      Traversal<List<Maybe<String>>, String> allPresent =
          Traversals.<Maybe<String>>forList().andThen(MaybeTraversals.just());

      List<Maybe<String>> result = Traversals.modify(allPresent, String::toUpperCase, maybes);
      assertThat(result).containsExactly(Maybe.just("HELLO"), Maybe.nothing(), Maybe.just("WORLD"));
    }

    @Test
    @DisplayName("should work with nested Maybe structures")
    void shouldWorkWithNestedMaybes() {
      Maybe<Maybe<String>> nested = Maybe.just(Maybe.just("inner"));
      Traversal<Maybe<Maybe<String>>, Maybe<String>> outer = MaybeTraversals.just();
      Traversal<Maybe<Maybe<String>>, String> deep = outer.andThen(MaybeTraversals.just());

      List<String> result = Traversals.getAll(deep, nested);
      assertThat(result).containsExactly("inner");
    }
  }
}
