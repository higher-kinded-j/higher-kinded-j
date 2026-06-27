// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("NonEmptyListKindHelper Tests")
class NonEmptyListKindHelperTest {

  @Nested
  @DisplayName("widen()")
  class WidenTests {

    @Test
    @DisplayName("widen returns the NonEmptyList itself (it implements its Kind directly)")
    void widenReturnsTheNonEmptyList() {
      NonEmptyList<String> nel = NonEmptyList.of("a", "b");
      Kind<NonEmptyListKind.Witness, String> kind = NON_EMPTY_LIST.widen(nel);
      assertThat(kind).isInstanceOf(NonEmptyList.class).isSameAs(nel);
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void widenThrowsForNullInput() {
      assertThatThrownBy(() -> NON_EMPTY_LIST.widen(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("narrow()")
  class NarrowTests {

    @Test
    @DisplayName("narrow returns the original NonEmptyList")
    void narrowReturnsTheOriginal() {
      NonEmptyList<Double> original = NonEmptyList.of(1.0, 2.5);
      Kind<NonEmptyListKind.Witness, Double> kind = NON_EMPTY_LIST.widen(original);
      assertThat(NON_EMPTY_LIST.narrow(kind)).isSameAs(original);
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void narrowThrowsForNullInput() {
      assertThatThrownBy(() -> NON_EMPTY_LIST.narrow(null)).isInstanceOf(KindUnwrapException.class);
    }

    /** A foreign Kind that is not a NonEmptyList, used to verify the type check. */
    record DummyKind<A>() implements Kind<NonEmptyListKind.Witness, A> {}

    @Test
    @DisplayName("narrow throws for an unknown Kind type")
    void narrowThrowsForUnknownKindType() {
      Kind<NonEmptyListKind.Witness, String> unknown = new DummyKind<>();
      assertThatThrownBy(() -> NON_EMPTY_LIST.narrow(unknown))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("NonEmptyList");
    }
  }
}
