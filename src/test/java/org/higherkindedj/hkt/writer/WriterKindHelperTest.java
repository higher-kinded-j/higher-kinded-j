package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.writer.WriterKindHelper.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.typeclass.Monoid;
import org.higherkindedj.hkt.typeclass.StringMonoid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
      Kind<WriterKind.Witness<String>, Integer> kind = wrap(baseWriter);
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
      WriterKind<String, Integer> kind = wrap(baseWriter);
      assertThat(unwrap(kind)).isSameAs(baseWriter);
    }

    // Dummy Kind implementation
    record DummyWriterKind<W, A>() implements Kind<WriterKind.Witness<W>, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      Kind<WriterKind.Witness<String>, Integer> unknownKind = new DummyWriterKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyWriterKind.class.getName());
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {
    @Test
    void value_shouldWrapValueWithEmptyLog() {
      Kind<WriterKind.Witness<String>, Integer> kind = WriterKindHelper.value(stringMonoid, 50);
      Writer<String, Integer> w = unwrap(kind);
      assertThat(w.log()).isEqualTo(stringMonoid.empty());
      assertThat(w.value()).isEqualTo(50);
    }

    @Test
    void tell_shouldWrapLogWithNullValue() {
      Kind<WriterKind.Witness<String>, Void> kind = WriterKindHelper.tell(stringMonoid, "LogMsg");
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
      var kind = wrap(baseWriter);
      assertThat(runWriter(kind)).isSameAs(baseWriter);
    }

    @Test
    void run_shouldReturnValue() {
      var kind = wrap(baseWriter);
      assertThat(run(kind)).isEqualTo(10);

      var tellKind = wrap(tellWriter);
      assertThat(run(tellKind)).isNull();
    }

    @Test
    void exec_shouldReturnLog() {
      var kind = wrap(baseWriter);
      assertThat(exec(kind)).isEqualTo("Log;");

      var tellKind = wrap(tellWriter);
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
