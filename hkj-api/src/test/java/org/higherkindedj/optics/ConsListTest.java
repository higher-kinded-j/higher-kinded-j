// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ConsList")
class ConsListTest {

  @Nested
  @DisplayName("toList")
  class ToListTests {

    @Test
    @DisplayName("gives the elements front to back")
    void keepsOrder() {
      ConsList<String> list =
          new ConsList.Cons<>("a", new ConsList.Cons<>("b", new ConsList.Nil<>()));

      assertThat(list.toList()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("keeps a null element")
    void keepsNull() {
      ConsList<String> list =
          new ConsList.Cons<>(null, new ConsList.Cons<>("b", new ConsList.Nil<>()));

      assertThat(list.toList()).containsExactly(null, "b");
    }

    @Test
    @DisplayName("gives an unmodifiable list")
    void isUnmodifiable() {
      ConsList<String> list = new ConsList.Cons<>("a", new ConsList.Nil<>());

      assertThat(list.toList()).isUnmodifiable();
    }

    @Test
    @DisplayName("gives an empty list for Nil")
    void emptyForNil() {
      assertThat(new ConsList.Nil<String>().toList()).isEmpty();
    }

    @Test
    @DisplayName("keeps the elements of two lists that share a tail apart")
    void sharedTailStaysSeparate() {
      ConsList<String> tail = new ConsList.Cons<>("c", new ConsList.Nil<>());
      ConsList<String> first = new ConsList.Cons<>("a", tail);
      ConsList<String> second = new ConsList.Cons<>("b", tail);

      assertThat(first.toList()).containsExactly("a", "c");
      assertThat(second.toList()).containsExactly("b", "c");
    }
  }
}
