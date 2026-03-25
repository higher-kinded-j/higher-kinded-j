// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.free.FreeKindHelper.FREE;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FreeKindHelper Tests")
class FreeKindHelperTest {

  @Nested
  @DisplayName("widen/narrow round-trip")
  class RoundTripTests {

    @Test
    @DisplayName("narrow(widen(free)) should return the original Free instance")
    void narrowWidenRoundTrip() {
      Free<MaybeKind.Witness, String> pure = Free.pure("hello");
      Kind<FreeKind.Witness<MaybeKind.Witness>, String> widened = FREE.widen(pure);
      Free<MaybeKind.Witness, String> narrowed = FREE.narrow(widened);
      assertThat(narrowed).isSameAs(pure);
    }
  }

  @Nested
  @DisplayName("narrow() validation")
  class NarrowValidationTests {

    @Test
    @DisplayName("narrow(null) should throw KindUnwrapException")
    void narrow_null_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> FREE.<MaybeKind.Witness, String>narrow(null))
          .isInstanceOf(KindUnwrapException.class);
    }

    @Test
    @DisplayName("narrow(wrong type) should throw KindUnwrapException")
    @SuppressWarnings("unchecked")
    void narrow_wrongType_shouldThrowKindUnwrapException() {
      // Create a Kind with the wrong runtime type
      Kind<FreeKind.Witness<MaybeKind.Witness>, String> fakeKind =
          (Kind<FreeKind.Witness<MaybeKind.Witness>, String>) (Kind<?, ?>) new FakeKind<>();
      assertThatThrownBy(() -> FREE.narrow(fakeKind)).isInstanceOf(KindUnwrapException.class);
    }
  }

  /** A fake Kind implementation used to test type checking in narrow(). */
  private static final class FakeKind<F extends WitnessArity<?>, A> implements Kind<F, A> {}
}
