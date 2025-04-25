package org.simulation.hkt.writer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.exception.KindUnwrapException;
import org.simulation.hkt.typeclass.Monoid;
import org.simulation.hkt.typeclass.StringMonoid;
import org.simulation.hkt.writer.Writer;
import org.simulation.hkt.writer.WriterKind;
import org.simulation.hkt.writer.WriterKindHelper;
import org.simulation.hkt.writer.WriterMonad;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.simulation.hkt.writer.WriterKindHelper.*;

@DisplayName("WriterKindHelper Tests")
class WriterKindHelperTest {

  private Monoid<String> stringMonoid;
  private Writer<String, Integer> baseWriter;
  private Writer<String, Void> tellWriter;

  @BeforeEach
  void setUp() {
    stringMonoid = new StringMonoid();
    baseWriter = Writer.create("Log;", 10);
    tellWriter = Writer.tell("Tell;");
  }

  @Nested
  @DisplayName("wrap()")
  class WrapTests {
    @Test
    void wrap_shouldReturnHolderForWriter() {
      Kind<WriterKind<String, ?>, Integer> kind = wrap(baseWriter);
      assertThat(kind).isInstanceOf(WriterHolder.class);
      // Unwrap to verify
      assertThat(unwrap(kind)).isSameAs(baseWriter);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> wrap(null))
          .withMessageContaining("Input Writer cannot be null");
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {
    @Test
    void unwrap_shouldReturnOriginalWriter() {
      Kind<WriterKind<String, ?>, Integer> kind = wrap(baseWriter);
      assertThat(unwrap(kind)).isSameAs(baseWriter);
    }

    // Dummy Kind implementation
    record DummyWriterKind<W, A>() implements Kind<WriterKind<W, ?>, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      Kind<WriterKind<String, ?>, Integer> unknownKind = new DummyWriterKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyWriterKind.class.getName());
    }

    @Test
    void unwrap_shouldThrowForHolderWithNullWriter() {
      WriterHolder<String, Double> holderWithNull = new WriterHolder<>(null);
      // Cast needed for test setup
      @SuppressWarnings("unchecked")
      Kind<WriterKind<String, ?>, Double> kind = (Kind<WriterKind<String, ?>, Double>)(Kind<?,?>) holderWithNull;

      assertThatThrownBy(() -> unwrap(kind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_HOLDER_STATE_MSG);
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {
    @Test
    void value_shouldWrapValueWithEmptyLog() {
      Kind<WriterKind<String, ?>, Integer> kind = WriterKindHelper.value(stringMonoid, 50);
      Writer<String, Integer> w = unwrap(kind);
      assertThat(w.log()).isEqualTo(stringMonoid.empty());
      assertThat(w.value()).isEqualTo(50);
    }

    @Test
    void tell_shouldWrapLogWithNullValue() {
      Kind<WriterKind<String, ?>, Void> kind = WriterKindHelper.tell(stringMonoid, "LogMsg");
      Writer<String, Void> w = unwrap(kind);
      assertThat(w.log()).isEqualTo("LogMsg");
      assertThat(w.value()).isNull();
    }
  }

  @Nested
  @DisplayName("Run/Exec Helpers")
  class RunExecTests {
    @Test
    void runWriter_shouldReturnOriginalWriter() {
      Kind<WriterKind<String, ?>, Integer> kind = wrap(baseWriter);
      assertThat(runWriter(kind)).isSameAs(baseWriter);
    }

    @Test
    void run_shouldReturnValue() {
      Kind<WriterKind<String, ?>, Integer> kind = wrap(baseWriter);
      assertThat(run(kind)).isEqualTo(10);

      Kind<WriterKind<String, ?>, Void> tellKind = wrap(tellWriter);
      assertThat(run(tellKind)).isNull();
    }

    @Test
    void exec_shouldReturnLog() {
      Kind<WriterKind<String, ?>, Integer> kind = wrap(baseWriter);
      assertThat(exec(kind)).isEqualTo("Log;");

      Kind<WriterKind<String, ?>, Void> tellKind = wrap(tellWriter);
      assertThat(exec(tellKind)).isEqualTo("Tell;");
    }
  }

  @Nested
  @DisplayName("Private Constructor")
  class PrivateConstructorTest {
    @Test
    @DisplayName("should throw UnsupportedOperationException when invoked via reflection")
    void constructor_shouldThrowException() throws NoSuchMethodException {
      Constructor<WriterKindHelper> constructor = WriterKindHelper.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause()
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}
