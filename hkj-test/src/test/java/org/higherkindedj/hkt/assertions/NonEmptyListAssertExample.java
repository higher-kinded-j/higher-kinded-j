// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.NonEmptyListAssert.assertThatNonEmptyList;
import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;

import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.nonemptylist.NonEmptyListKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.NonEmptyListAssert}. */
@DisplayName("NonEmptyListAssert showcase")
class NonEmptyListAssertExample {

  @Test
  @DisplayName("hasHead(), hasLast() and hasSize() read the ends and the length")
  void headLastAndSize() {
    NonEmptyList<String> errors = NonEmptyList.of("name", "email", "age");

    assertThatNonEmptyList(errors).hasSize(3).hasHead("name").hasLast("age");
  }

  @Test
  @DisplayName("containsExactly() asserts the elements in order")
  void containsExactly() {
    NonEmptyList<Integer> values = NonEmptyList.of(1, 2, 3);

    assertThatNonEmptyList(values).containsExactly(1, 2, 3);
  }

  @Test
  @DisplayName("allMatch() asserts every element satisfies a predicate")
  void allMatch() {
    NonEmptyList<String> paths = NonEmptyList.of("order.email", "order.age");

    assertThatNonEmptyList(paths).allMatch(path -> path.startsWith("order"));
  }

  @Test
  @DisplayName("satisfies() hands the list to a further AssertJ check")
  void satisfies() {
    NonEmptyList<Integer> values = NonEmptyList.of(1, 2, 3);

    assertThatNonEmptyList(values)
        .satisfies(list -> Assertions.assertThat(list.toJavaList()).isSorted());
  }

  @Test
  @DisplayName("Accepts Kind<NonEmptyListKind.Witness, T> directly without manual narrowing")
  void acceptsKindDirectly() {
    Kind<NonEmptyListKind.Witness, Integer> kind = NON_EMPTY_LIST.widen(NonEmptyList.of(7, 8, 9));

    assertThatNonEmptyList(kind).hasHead(7).hasLast(9).hasSize(3);
  }
}
