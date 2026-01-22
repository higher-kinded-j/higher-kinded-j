// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link ContextKindHelper}.
 *
 * <p>Coverage includes widen/narrow operations, factory methods, and edge cases.
 */
@DisplayName("ContextKindHelper Complete Test Suite")
class ContextKindHelperTest {

  private static final ScopedValue<String> STRING_KEY = ScopedValue.newInstance();
  private static final ScopedValue<Integer> INT_KEY = ScopedValue.newInstance();

  @Nested
  @DisplayName("widen() Operation")
  class WidenOperation {

    @Test
    @DisplayName("widen() should convert Context to Kind")
    void widen_shouldConvertContextToKind() {
      Context<String, Integer> ctx = Context.succeed(42);

      Kind<ContextKind.Witness<String>, Integer> kind = CONTEXT.widen(ctx);

      assertThat(kind).isNotNull();
      assertThat(kind).isInstanceOf(ContextKindHelper.ContextHolder.class);
    }

    @Test
    @DisplayName("widen() should throw NullPointerException for null context")
    void widen_shouldThrowForNullContext() {
      assertThatNullPointerException().isThrownBy(() -> CONTEXT.widen(null));
    }

    @Test
    @DisplayName("widen() should work with different Context types")
    void widen_shouldWorkWithDifferentContextTypes() {
      Kind<ContextKind.Witness<String>, String> askKind = CONTEXT.widen(Context.ask(STRING_KEY));
      Kind<ContextKind.Witness<String>, Integer> pureKind = CONTEXT.widen(Context.succeed(42));
      Kind<ContextKind.Witness<String>, String> failKind =
          CONTEXT.widen(Context.fail(new RuntimeException("error")));

      assertThat(askKind).isNotNull();
      assertThat(pureKind).isNotNull();
      assertThat(failKind).isNotNull();
    }
  }

  @Nested
  @DisplayName("narrow() Operation")
  class NarrowOperation {

    @Test
    @DisplayName("narrow() should convert Kind back to Context")
    void narrow_shouldConvertKindToContext() {
      Context<String, Integer> original = Context.succeed(42);
      Kind<ContextKind.Witness<String>, Integer> kind = CONTEXT.widen(original);

      Context<String, Integer> narrowed = CONTEXT.narrow(kind);

      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("narrow() should throw KindUnwrapException for null kind")
    void narrow_shouldThrowForNullKind() {
      assertThatThrownBy(() -> CONTEXT.narrow(null))
          .isInstanceOf(KindUnwrapException.class);
    }

    @Test
    @DisplayName("narrow() should throw KindUnwrapException for invalid kind type")
    void narrow_shouldThrowForInvalidKindType() {
      // Create a mock Kind that isn't a ContextHolder
      Kind<ContextKind.Witness<String>, Integer> invalidKind =
          new Kind<>() {}; // Anonymous invalid implementation

      assertThatThrownBy(() -> CONTEXT.narrow(invalidKind))
          .isInstanceOf(KindUnwrapException.class);
    }
  }

  @Nested
  @DisplayName("Round-trip")
  class RoundTrip {

    @Test
    @DisplayName("widen then narrow should return same Context")
    void widenThenNarrow_shouldReturnSameContext() {
      Context<String, Integer> original = Context.succeed(42);

      Context<String, Integer> roundTripped = CONTEXT.narrow(CONTEXT.widen(original));

      assertThat(roundTripped).isSameAs(original);
    }

    @Test
    @DisplayName("Round-trip should preserve all Context types")
    void roundTrip_shouldPreserveAllContextTypes() {
      Context<String, String> ask = Context.ask(STRING_KEY);
      Context<String, Integer> pure = Context.succeed(42);
      Context<String, String> failed = Context.fail(new RuntimeException("error"));

      assertThat(CONTEXT.narrow(CONTEXT.widen(ask))).isSameAs(ask);
      assertThat(CONTEXT.narrow(CONTEXT.widen(pure))).isSameAs(pure);
      assertThat(CONTEXT.narrow(CONTEXT.widen(failed))).isSameAs(failed);
    }
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    @DisplayName("ask() should create Kind with Ask context")
    void ask_shouldCreateKindWithAskContext() throws Exception {
      Kind<ContextKind.Witness<String>, String> kind = CONTEXT.ask(STRING_KEY);

      Context<String, String> ctx = CONTEXT.narrow(kind);
      String result = ScopedValue.where(STRING_KEY, "test-value").call(ctx::run);

      assertThat(result).isEqualTo("test-value");
    }

    @Test
    @DisplayName("ask() should throw NullPointerException for null key")
    void ask_shouldThrowForNullKey() {
      assertThatNullPointerException().isThrownBy(() -> CONTEXT.ask(null));
    }

    @Test
    @DisplayName("asks() should create Kind with transformed Ask context")
    void asks_shouldCreateKindWithTransformedAskContext() throws Exception {
      Kind<ContextKind.Witness<String>, Integer> kind =
          CONTEXT.asks(STRING_KEY, String::length);

      Context<String, Integer> ctx = CONTEXT.narrow(kind);
      Integer result = ScopedValue.where(STRING_KEY, "hello").call(ctx::run);

      assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("asks() should throw NullPointerException for null key")
    void asks_shouldThrowForNullKey() {
      assertThatNullPointerException().isThrownBy(() -> CONTEXT.asks(null, String::length));
    }

    @Test
    @DisplayName("asks() should throw NullPointerException for null function")
    void asks_shouldThrowForNullFunction() {
      assertThatNullPointerException().isThrownBy(() -> CONTEXT.asks(STRING_KEY, null));
    }

    @Test
    @DisplayName("succeed() should create Kind with Pure context")
    void succeed_shouldCreateKindWithPureContext() {
      Kind<ContextKind.Witness<String>, Integer> kind = CONTEXT.succeed(42);

      Context<String, Integer> ctx = CONTEXT.narrow(kind);
      Integer result = ctx.run();

      assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("succeed() should allow null value")
    void succeed_shouldAllowNullValue() {
      Kind<ContextKind.Witness<String>, Integer> kind = CONTEXT.succeed(null);

      Context<String, Integer> ctx = CONTEXT.narrow(kind);
      Integer result = ctx.run();

      assertThat(result).isNull();
    }

    @Test
    @DisplayName("fail() should create Kind with Failed context")
    void fail_shouldCreateKindWithFailedContext() {
      RuntimeException error = new RuntimeException("test error");
      Kind<ContextKind.Witness<String>, String> kind = CONTEXT.fail(error);

      Context<String, String> ctx = CONTEXT.narrow(kind);

      assertThatThrownBy(ctx::run).isSameAs(error);
    }

    @Test
    @DisplayName("fail() should throw NullPointerException for null error")
    void fail_shouldThrowForNullError() {
      assertThatNullPointerException().isThrownBy(() -> CONTEXT.fail(null));
    }
  }

  @Nested
  @DisplayName("runContext() Operation")
  class RunContextOperation {

    @Test
    @DisplayName("runContext() should run Context from Kind")
    void runContext_shouldRunContextFromKind() throws Exception {
      Kind<ContextKind.Witness<String>, String> kind = CONTEXT.ask(STRING_KEY);

      String result =
          ScopedValue.where(STRING_KEY, "scoped-value")
              .call(() -> CONTEXT.runContext(kind));

      assertThat(result).isEqualTo("scoped-value");
    }

    @Test
    @DisplayName("runContext() should throw NullPointerException for null kind")
    void runContext_shouldThrowForNullKind() {
      assertThatNullPointerException().isThrownBy(() -> CONTEXT.runContext(null));
    }

    @Test
    @DisplayName("runContext() should propagate failure")
    void runContext_shouldPropagateFailure() {
      RuntimeException error = new RuntimeException("test error");
      Kind<ContextKind.Witness<String>, String> kind = CONTEXT.fail(error);

      assertThatThrownBy(() -> CONTEXT.runContext(kind)).isSameAs(error);
    }

    @Test
    @DisplayName("runContext() should return null for null result")
    void runContext_shouldReturnNullForNullResult() {
      Kind<ContextKind.Witness<String>, String> kind = CONTEXT.succeed(null);

      String result = CONTEXT.runContext(kind);

      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("ContextHolder Record")
  class ContextHolderTests {

    @Test
    @DisplayName("ContextHolder should expose context via accessor")
    void contextHolder_shouldExposeContext() {
      Context<String, Integer> ctx = Context.succeed(42);
      ContextKindHelper.ContextHolder<String, Integer> holder =
          new ContextKindHelper.ContextHolder<>(ctx);

      assertThat(holder.context()).isSameAs(ctx);
    }

    @Test
    @DisplayName("ContextHolder should implement ContextKind")
    void contextHolder_shouldImplementContextKind() {
      Context<String, Integer> ctx = Context.succeed(42);
      ContextKindHelper.ContextHolder<String, Integer> holder =
          new ContextKindHelper.ContextHolder<>(ctx);

      assertThat(holder).isInstanceOf(ContextKind.class);
    }

    @Test
    @DisplayName("ContextHolder should throw for null context")
    void contextHolder_shouldThrowForNullContext() {
      assertThatThrownBy(() -> new ContextKindHelper.ContextHolder<>(null))
          .isInstanceOf(Exception.class);
    }
  }

  @Nested
  @DisplayName("Enum Singleton")
  class EnumSingleton {

    @Test
    @DisplayName("CONTEXT should be the only enum constant")
    void context_shouldBeOnlyEnumConstant() {
      ContextKindHelper[] values = ContextKindHelper.values();

      assertThat(values).hasSize(1);
      assertThat(values[0]).isEqualTo(CONTEXT);
    }

    @Test
    @DisplayName("CONTEXT should implement ContextConverterOps")
    void context_shouldImplementContextConverterOps() {
      assertThat(CONTEXT).isInstanceOf(ContextConverterOps.class);
    }
  }
}
