// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.IdAssert.assertThatId;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link IdAssert}. */
@DisplayName("IdAssert showcase")
class IdAssertExample {

  @Test
  @DisplayName("hasValue() unwraps an Id Kind and compares to the expected value")
  void hasValueChain() {
    Kind<IdKind.Witness, Integer> id = ID.widen(Id.of(42));
    assertThatId(id).hasValue(42);
  }

  @Test
  @DisplayName("satisfies() exposes the unwrapped value to a delegated AssertJ block")
  void satisfiesDelegate() {
    Kind<IdKind.Witness, Integer> id = ID.widen(Id.of(42));
    assertThatId(id).satisfies(value -> assertThat(value).isGreaterThan(40).isLessThan(50));
  }

  @Test
  @DisplayName("isEqualToId() compares two Id Kinds by inner value equality")
  void equality() {
    Kind<IdKind.Witness, Integer> a = ID.widen(Id.of(42));
    Kind<IdKind.Witness, Integer> b = ID.widen(Id.of(42));
    assertThatId(a).isEqualToId(b);
  }
}
