// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.reader.ReaderKindHelper.*;
import static org.higherkindedj.hkt.util.ErrorHandling.INVALID_KIND_TYPE_TEMPLATE;
import static org.higherkindedj.hkt.util.ErrorHandling.NULL_KIND_TEMPLATE;

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
  @DisplayName("READER.widen()")
  class WrapTests {
    @Test
    void widen_shouldReturnHolderForReader() {
      var kind = READER.widen(baseReader);
      assertThat(kind).isInstanceOf(ReaderHolder.class);
      // Unwrap to verify
      assertThat(READER.narrow(kind)).isSameAs(baseReader);
    }

    @Test
    void widen_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> READER.widen(null))
          .withMessageContaining("Input Reader cannot be null");
    }
  }

  @Nested
  @DisplayName("READER.narrow()")
  class UnwrapTests {
    @Test
    void narrow_shouldReturnOriginalReader() {
      var kind = READER.widen(baseReader);
      assertThat(READER.narrow(kind)).isSameAs(baseReader);
    }

    // Dummy Kind implementation that is not ReaderHolder
    record DummyReaderKind<R, A>() implements ReaderKind<R, A> {}

    @Test
    void narrow_shouldThrowForNullInput() {
      assertThatThrownBy(() -> READER.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(NULL_KIND_TEMPLATE.formatted(Reader.class.getSimpleName()));
    }

    @Test
    void narrow_shouldThrowForUnknownKindType() {
      ReaderKind<Env, String> unknownKind = new DummyReaderKind<>();
      assertThatThrownBy(() -> READER.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              INVALID_KIND_TYPE_TEMPLATE.formatted(
                  Reader.class.getSimpleName(), DummyReaderKind.class.getName()));
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {
    @Test
    void reader_shouldWrapFunction() {
      Function<Env, Integer> f = env -> env.value().hashCode();
      Kind<ReaderKind.Witness<Env>, Integer> kind = READER.reader(f);
      assertThat(READER.narrow(kind).run(testEnv)).isEqualTo(testEnv.value().hashCode());
    }

    @Test
    void constant_shouldWrapConstant() {
      Kind<ReaderKind.Witness<Env>, String> kind = READER.constant("hello");
      assertThat(READER.narrow(kind).run(testEnv)).isEqualTo("hello");
      assertThat(READER.narrow(kind).run(new Env("other"))).isEqualTo("hello");
    }

    @Test
    void ask_shouldWrapAsk() {
      Kind<ReaderKind.Witness<Env>, Env> kind = READER.ask();
      assertThat(READER.narrow(kind).run(testEnv)).isSameAs(testEnv);
    }
  }

  @Nested
  @DisplayName("runReader()")
  class RunReaderTests {
    @Test
    void runReader_shouldExecuteWrappedReader() {
      Kind<ReaderKind.Witness<Env>, Integer> kind = READER.widen(lenReader);
      assertThat(READER.runReader(kind, testEnv)).isEqualTo(testEnv.value().length());
    }

    @Test
    void runReader_shouldThrowIfKindIsINull() {
      assertThatThrownBy(() -> READER.runReader(null, testEnv))
          .isInstanceOf(NullPointerException.class); // Propagates unwrap exception
    }
  }
}
