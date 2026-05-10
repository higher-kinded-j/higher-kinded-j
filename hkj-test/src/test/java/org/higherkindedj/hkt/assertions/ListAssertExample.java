// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ListAssert.assertThatList;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link ListAssert}. */
@DisplayName("ListAssert showcase")
class ListAssertExample {

  @Test
  @DisplayName("isNotEmpty().hasSize().containsExactly() chains over a Kind<ListKind.Witness, T>")
  void chainOnNonEmptyList() {
    Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(1, 2, 3));
    assertThatList(list).isNotEmpty().hasSize(3).containsExactly(1, 2, 3);
  }

  @Test
  @DisplayName("isEmpty() asserts a list narrowed from an empty Kind")
  void chainOnEmptyList() {
    Kind<ListKind.Witness, String> empty = LIST.widen(List.of());
    assertThatList(empty).isEmpty();
  }

  @Test
  @DisplayName("satisfies() defers to a delegated AssertJ block")
  void satisfiesDelegate() {
    Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(2, 4, 6));
    assertThatList(list).satisfies(elements -> assertThat(elements).allMatch(n -> n % 2 == 0));
  }
}
