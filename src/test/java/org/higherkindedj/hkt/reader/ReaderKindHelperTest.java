package org.higherkindedj.hkt.reader;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.reader.ReaderKindHelper.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReaderKindHelper Tests")
class ReaderKindHelperTest {

  record Env(String value) {}

  final Env testEnv = new Env("test-env");
  final Reader<Env, String> baseReader = Env::value;
  final Reader<Env, Integer> lenReader = env -> env.value().length();

  @Nested
  @DisplayName("wrap()")
  class WrapTests {
    @Test
    void wrap_shouldReturnHolderForReader() {
      ReaderKind<Env, String> kind = wrap(baseReader);
      assertThat(kind).isInstanceOf(ReaderHolder.class);
      // Unwrap to verify
      assertThat(unwrap(kind)).isSameAs(baseReader);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> wrap(null))
          .withMessageContaining("Input Reader cannot be null");
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {
    @Test
    void unwrap_shouldReturnOriginalReader() {
      ReaderKind<Env, String> kind = wrap(baseReader);
      assertThat(unwrap(kind)).isSameAs(baseReader);
    }

    // Dummy Kind implementation that is not ReaderHolder
    record DummyReaderKind<R, A>() implements ReaderKind<R, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      ReaderKind<Env, String> unknownKind = new DummyReaderKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyReaderKind.class.getName());
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {
    @Test
    void reader_shouldWrapFunction() {
      Function<Env, Integer> f = env -> env.value().hashCode();
      Kind<ReaderKind.Witness<Env>, Integer> kind = reader(f);
      assertThat(unwrap(kind).run(testEnv)).isEqualTo(testEnv.value().hashCode());
    }

    @Test
    void constant_shouldWrapConstant() {
      Kind<ReaderKind.Witness<Env>, String> kind = constant("hello");
      assertThat(unwrap(kind).run(testEnv)).isEqualTo("hello");
      assertThat(unwrap(kind).run(new Env("other"))).isEqualTo("hello");
    }

    @Test
    void ask_shouldWrapAsk() {
      Kind<ReaderKind.Witness<Env>, Env> kind = ask();
      assertThat(unwrap(kind).run(testEnv)).isSameAs(testEnv);
    }
  }

  @Nested
  @DisplayName("runReader()")
  class RunReaderTests {
    @Test
    void runReader_shouldExecuteWrappedReader() {
      Kind<ReaderKind.Witness<Env>, Integer> kind = wrap(lenReader);
      assertThat(runReader(kind, testEnv)).isEqualTo(testEnv.value().length());
    }

    @Test
    void runReader_shouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> runReader(null, testEnv))
          .isInstanceOf(KindUnwrapException.class); // Propagates unwrap exception
    }
  }

  @Nested
  @DisplayName("Private Constructor")
  class PrivateConstructorTest {
    @Test
    @DisplayName("should throw UnsupportedOperationException when invoked via reflection")
    void constructor_shouldThrowException() throws NoSuchMethodException {
      Constructor<ReaderKindHelper> constructor = ReaderKindHelper.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause()
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}
