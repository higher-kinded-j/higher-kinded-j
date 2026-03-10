// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.writer_t.WriterTKindHelper.WRITER_T;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WriterTKindHelper Test Suite")
class WriterTKindHelperTest {

  @Nested
  @DisplayName("Widen Tests")
  class WidenTests {

    @Test
    @DisplayName("widen should convert WriterT to Kind")
    void widen_shouldConvertToKind() {
      var innerKind = IdKindHelper.ID.widen(new Id<>(Pair.of(42, "log")));
      var writerT = new WriterT<IdKind.Witness, String, Integer>(innerKind);
      Kind<WriterTKind.Witness<IdKind.Witness, String>, Integer> kind = WRITER_T.widen(writerT);
      assertThat(kind).isNotNull();
      assertThat(kind).isSameAs(writerT);
    }

    @Test
    @DisplayName("widen should reject null")
    void widen_shouldRejectNull() {
      assertThatThrownBy(() -> WRITER_T.widen(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Narrow Tests")
  class NarrowTests {

    @Test
    @DisplayName("narrow should convert Kind back to WriterT")
    void narrow_shouldConvertToWriterT() {
      var innerKind = IdKindHelper.ID.widen(new Id<>(Pair.of(42, "log")));
      var writerT = new WriterT<IdKind.Witness, String, Integer>(innerKind);
      Kind<WriterTKind.Witness<IdKind.Witness, String>, Integer> kind = WRITER_T.widen(writerT);
      WriterT<IdKind.Witness, String, Integer> narrowed = WRITER_T.narrow(kind);
      assertThat(narrowed).isSameAs(writerT);
    }

    @Test
    @DisplayName("narrow should reject null")
    void narrow_shouldRejectNull() {
      assertThatThrownBy(() -> WRITER_T.<IdKind.Witness, String, Integer>narrow(null))
          .isInstanceOf(KindUnwrapException.class);
    }
  }

  @Nested
  @DisplayName("Round-trip Tests")
  class RoundTripTests {

    @Test
    @DisplayName("widen then narrow should return same instance")
    void roundTrip() {
      var innerKind = IdKindHelper.ID.widen(new Id<>(Pair.of("value", "output")));
      var writerT = new WriterT<IdKind.Witness, String, String>(innerKind);
      var roundTripped = WRITER_T.narrow(WRITER_T.widen(writerT));
      assertThat(roundTripped).isSameAs(writerT);
    }
  }
}
