// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.writer.WriterKindHelper.*;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.typeclass.StringMonoid;
import org.higherkindedj.hkt.unit.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WriterKindHelper Tests")
class WriterKindHelperTest {

  private Monoid<String> stringMonoid;
  private Writer<String, Integer> baseWriter;
  private Writer<String, Unit> tellWriter;

  @BeforeEach
  void setUp() {
    stringMonoid = new StringMonoid();
    baseWriter = new Writer<>("Log;", 10);
    tellWriter = Writer.tell("Tell;");
  }

  @Nested
  @DisplayName("WRITER.widen()")
  class WrapTests {
    @Test
    void widen_shouldReturnHolderForWriter() {
      Kind<WriterKind.Witness<String>, Integer> kind = WRITER.widen(baseWriter);
      assertThat(kind).isInstanceOf(WriterKindHelper.WriterHolder.class);
      // Unwrap to verify
      assertThat(WRITER.narrow(kind)).isSameAs(baseWriter);
    }

    @Test
    void widen_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> WRITER.widen(null))
          .withMessageContaining("Input Writer cannot be null for widen");
    }
  }

  @Nested
  @DisplayName("WRITER.narrow()")
  class UnwrapTests {
    @Test
    void narrow_shouldReturnOriginalWriter() {
      Kind<WriterKind.Witness<String>, Integer> kind = WRITER.widen(baseWriter);
      assertThat(WRITER.narrow(kind)).isSameAs(baseWriter);
    }

    // Dummy Kind implementation
    record DummyWriterKind<W, A>() implements Kind<WriterKind.Witness<W>, A> {}

    @Test
    void narrow_shouldThrowForNullInput() {
      assertThatThrownBy(() -> WRITER.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind for Writer");
    }

    @Test
    void narrow_shouldThrowForUnknownKindType() {
      Kind<WriterKind.Witness<String>, Integer> unknownKind = new DummyWriterKind<>();
      assertThatThrownBy(() -> WRITER.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              "Kind instance is not a Writer: " + DummyWriterKind.class.getName());
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {
    @Test
    void value_shouldWrapValueWithEmptyLog() {
      Kind<WriterKind.Witness<String>, Integer> kind = WRITER.value(stringMonoid, 50);
      Writer<String, Integer> w = WRITER.narrow(kind);
      assertThat(w.log()).isEqualTo(stringMonoid.empty());
      assertThat(w.value()).isEqualTo(50);
    }

    @Test
    void tell_shouldWrapLogWithUnitValue() {
      Kind<WriterKind.Witness<String>, Unit> kind = WRITER.tell("LogMsg");
      Writer<String, Unit> w = WRITER.narrow(kind);
      assertThat(w.log()).isEqualTo("LogMsg");
      assertThat(w.value()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    void tell_shouldThrowForNullLog() {
      assertThatNullPointerException()
          .isThrownBy(() -> WRITER.tell(null))
          .withMessageContaining("Log message for tell cannot be null");
    }

    @Test
    void value_shouldThrowForNullMonoid() {
      assertThatNullPointerException()
          .isThrownBy(() -> WRITER.value(null, 42))
          .withMessageContaining("Monoid");
    }
  }

  @Nested
  @DisplayName("Run/Exec Helpers")
  class RunExecTests {
    @Test
    void runWriter_shouldReturnOriginalWriter() {
      var kind = WRITER.widen(baseWriter);
      assertThat(WRITER.runWriter(kind)).isSameAs(baseWriter);
    }

    @Test
    void run_shouldReturnValue() {
      var kind = WRITER.widen(baseWriter);
      assertThat(WRITER.run(kind)).isEqualTo(10);

      var tellKind = WRITER.widen(tellWriter);
      assertThat(WRITER.run(tellKind)).isEqualTo(Unit.INSTANCE);
    }

    @Test
    void exec_shouldReturnLog() {
      var kind = WRITER.widen(baseWriter);
      assertThat(WRITER.exec(kind)).isEqualTo("Log;");

      var tellKind = WRITER.widen(tellWriter);
      assertThat(WRITER.exec(tellKind)).isEqualTo("Tell;");
    }

    @Test
    void runWriter_shouldThrowForNullKind() {
      assertThatThrownBy(() -> WRITER.runWriter(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind for Writer");
    }

    @Test
    void run_shouldThrowForNullKind() {
      assertThatThrownBy(() -> WRITER.run(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind for Writer");
    }

    @Test
    void exec_shouldThrowForNullKind() {
      assertThatThrownBy(() -> WRITER.exec(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind for Writer");
    }
  }
}
