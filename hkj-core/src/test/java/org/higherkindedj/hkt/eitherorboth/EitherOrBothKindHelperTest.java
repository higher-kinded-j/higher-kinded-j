// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherOrBothKindHelper")
class EitherOrBothKindHelperTest {

  /** A foreign {@code Kind} that is not an {@code EitherOrBoth}, used to verify the type check. */
  record DummyKind<A>() implements Kind<EitherOrBothKind.Witness<String>, A> {}

  /** A foreign {@code Kind2} that is not an {@code EitherOrBoth}, used to verify the type check. */
  record DummyKind2<A, B>() implements Kind2<EitherOrBothKind2.Witness, A, B> {}

  @Nested
  @DisplayName("widen / narrow (Kind)")
  class KindRoundTrip {

    @Test
    void widenReturnsTheValueItself() {
      EitherOrBoth<String, Integer> eob = EitherOrBoth.both("w", 1);
      Kind<EitherOrBothKind.Witness<String>, Integer> kind = EITHER_OR_BOTH.widen(eob);
      assertThat(kind).isInstanceOf(EitherOrBoth.class).isSameAs(eob);
    }

    @Test
    void narrowReturnsTheOriginal() {
      EitherOrBoth<String, Integer> eob = EitherOrBoth.right(7);
      assertThat(EITHER_OR_BOTH.narrow(EITHER_OR_BOTH.widen(eob))).isSameAs(eob);
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // null passed deliberately to verify rejection
    void widenRejectsNull() {
      Assertions.assertThatNullPointerException().isThrownBy(() -> EITHER_OR_BOTH.widen(null));
    }

    @Test
    void narrowRejectsNull() {
      assertThatThrownBy(() -> EITHER_OR_BOTH.narrow(null)).isInstanceOf(KindUnwrapException.class);
    }

    @Test
    void narrowRejectsForeignKind() {
      Kind<EitherOrBothKind.Witness<String>, Integer> foreign = new DummyKind<>();
      assertThatThrownBy(() -> EITHER_OR_BOTH.narrow(foreign))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("EitherOrBoth");
    }
  }

  @Nested
  @DisplayName("widen2 / narrow2 (Kind2)")
  class Kind2RoundTrip {

    @Test
    void widen2ReturnsTheValueItself() {
      EitherOrBoth<String, Integer> eob = EitherOrBoth.left("e");
      Kind2<EitherOrBothKind2.Witness, String, Integer> kind = EITHER_OR_BOTH.widen2(eob);
      assertThat(kind).isInstanceOf(EitherOrBoth.class).isSameAs(eob);
    }

    @Test
    void narrow2ReturnsTheOriginal() {
      EitherOrBoth<String, Integer> eob = EitherOrBoth.both("w", 3);
      assertThat(EITHER_OR_BOTH.narrow2(EITHER_OR_BOTH.widen2(eob))).isSameAs(eob);
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // null passed deliberately to verify rejection
    void widen2RejectsNull() {
      Assertions.assertThatNullPointerException().isThrownBy(() -> EITHER_OR_BOTH.widen2(null));
    }

    @Test
    void narrow2RejectsNull() {
      assertThatThrownBy(() -> EITHER_OR_BOTH.narrow2(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("null Kind2");
    }

    @Test
    void narrow2RejectsForeignKind2() {
      Kind2<EitherOrBothKind2.Witness, String, Integer> foreign = new DummyKind2<>();
      assertThatThrownBy(() -> EITHER_OR_BOTH.narrow2(foreign))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("EitherOrBoth");
    }
  }
}
