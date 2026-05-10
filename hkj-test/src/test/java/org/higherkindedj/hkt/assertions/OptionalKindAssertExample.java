// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.OptionalKindAssert.assertThatOptionalKind;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link OptionalKindAssert}. */
@DisplayName("OptionalKindAssert showcase")
class OptionalKindAssertExample {

  @Test
  @DisplayName("isPresent().contains() chains over a Kind<OptionalKind.Witness, T>")
  void presentChain() {
    Kind<OptionalKind.Witness, Integer> opt = OPTIONAL.widen(Optional.of(42));
    assertThatOptionalKind(opt).isPresent().contains(42);
  }

  @Test
  @DisplayName("isEmpty() asserts an empty Optional Kind")
  void emptyChain() {
    Kind<OptionalKind.Witness, String> empty = OPTIONAL.widen(Optional.empty());
    assertThatOptionalKind(empty).isEmpty();
  }

  @Test
  @DisplayName("satisfies() defers to a delegated AssertJ block on the held value")
  void satisfiesDelegate() {
    Kind<OptionalKind.Witness, Integer> opt = OPTIONAL.widen(Optional.of(42));
    assertThatOptionalKind(opt).isPresent().satisfies(value -> assertThat(value).isGreaterThan(40));
  }
}
