// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.assertj.core.api.Assertions.*;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for the {@link Context} type and all its record implementations.
 *
 * <p>Coverage includes factory methods, composition methods, conversion methods, and all sealed
 * subtypes: Ask, Pure, FlatMapped, Failed, Recovered, RecoveredWith, ErrorMapped.
 */
@DisplayName("Context<R, A> Complete Test Suite")
class ContextTest {

  private static final ScopedValue<String> STRING_KEY = ScopedValue.newInstance();
  private static final ScopedValue<Integer> INT_KEY = ScopedValue.newInstance();

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Nested
    @DisplayName("ask()")
    class AskTests {

      @Test
      @DisplayName("ask() should create a Context that reads from ScopedValue")
      void ask_shouldReadFromScopedValue() throws Exception {
        Context<String, String> ctx = Context.ask(STRING_KEY);

        String result = ScopedValue.where(STRING_KEY, "test-value").call(ctx::run);

        assertThat(result).isEqualTo("test-value");
      }

      @Test
      @DisplayName("ask() should throw NullPointerException for null key")
      void ask_shouldThrowForNullKey() {
        assertThatNullPointerException()
            .isThrownBy(() -> Context.ask(null))
            .withMessageContaining("key cannot be null");
      }

      @Test
      @DisplayName("ask() should throw NoSuchElementException when ScopedValue is not bound")
      void ask_shouldThrowWhenNotBound() {
        Context<String, String> ctx = Context.ask(STRING_KEY);

        assertThatThrownBy(ctx::run).isInstanceOf(NoSuchElementException.class);
      }
    }

    @Nested
    @DisplayName("asks()")
    class AsksTests {

      @Test
      @DisplayName("asks() should create a Context that reads and transforms ScopedValue")
      void asks_shouldReadAndTransform() throws Exception {
        Context<String, Integer> ctx = Context.asks(STRING_KEY, String::length);

        Integer result = ScopedValue.where(STRING_KEY, "hello").call(ctx::run);

        assertThat(result).isEqualTo(5);
      }

      @Test
      @DisplayName("asks() should throw NullPointerException for null key")
      void asks_shouldThrowForNullKey() {
        assertThatNullPointerException()
            .isThrownBy(() -> Context.asks(null, String::length))
            .withMessageContaining("key cannot be null");
      }

      @Test
      @DisplayName("asks() should throw NullPointerException for null function")
      void asks_shouldThrowForNullFunction() {
        assertThatNullPointerException()
            .isThrownBy(() -> Context.asks(STRING_KEY, null))
            .withMessageContaining("function cannot be null");
      }
    }

    @Nested
    @DisplayName("succeed()")
    class SucceedTests {

      @Test
      @DisplayName("succeed() should create a Context with pure value")
      void succeed_shouldCreatePureContext() {
        Context<String, Integer> ctx = Context.succeed(42);

        Integer result = ctx.run();

        assertThat(result).isEqualTo(42);
      }

      @Test
      @DisplayName("succeed() should allow null value")
      void succeed_shouldAllowNullValue() {
        Context<String, Integer> ctx = Context.succeed(null);

        Integer result = ctx.run();

        assertThat(result).isNull();
      }
    }

    @Nested
    @DisplayName("fail()")
    class FailTests {

      @Test
      @DisplayName("fail() should create a Context that throws on run")
      void fail_shouldThrowOnRun() {
        RuntimeException error = new RuntimeException("test error");
        Context<String, String> ctx = Context.fail(error);

        assertThatThrownBy(ctx::run).isSameAs(error);
      }

      @Test
      @DisplayName("fail() should throw NullPointerException for null error")
      void fail_shouldThrowForNullError() {
        assertThatNullPointerException()
            .isThrownBy(() -> Context.fail(null))
            .withMessageContaining("error cannot be null");
      }

      @Test
      @DisplayName("fail() should propagate checked exceptions")
      void fail_shouldPropagateCheckedException() {
        Exception checkedException = new Exception("checked exception");
        Context<String, String> ctx = Context.fail(checkedException);

        assertThatThrownBy(ctx::run).isSameAs(checkedException);
      }
    }
  }

  @Nested
  @DisplayName("Composition Methods")
  class CompositionMethods {

    @Nested
    @DisplayName("map()")
    class MapTests {

      @Test
      @DisplayName("map() should transform result value")
      void map_shouldTransformResult() throws Exception {
        Context<String, String> ctx = Context.ask(STRING_KEY);
        Context<String, Integer> mapped = ctx.map(String::length);

        Integer result = ScopedValue.where(STRING_KEY, "hello").call(mapped::run);

        assertThat(result).isEqualTo(5);
      }

      @Test
      @DisplayName("map() should throw NullPointerException for null function")
      void map_shouldThrowForNullFunction() {
        Context<String, String> ctx = Context.succeed("value");

        assertThatNullPointerException()
            .isThrownBy(() -> ctx.map(null))
            .withMessageContaining("function cannot be null");
      }

      @Test
      @DisplayName("map() should compose multiple transformations")
      void map_shouldComposeTransformations() throws Exception {
        Context<String, String> ctx =
            Context.<String>ask(STRING_KEY)
                .map(String::toUpperCase)
                .map(s -> "[" + s + "]");

        String result = ScopedValue.where(STRING_KEY, "test").call(ctx::run);

        assertThat(result).isEqualTo("[TEST]");
      }
    }

    @Nested
    @DisplayName("flatMap()")
    class FlatMapTests {

      @Test
      @DisplayName("flatMap() should sequence context computations")
      void flatMap_shouldSequenceComputations() throws Exception {
        Context<String, String> ctx =
            Context.<String>ask(STRING_KEY)
                .flatMap(s -> Context.succeed("Result: " + s));

        String result = ScopedValue.where(STRING_KEY, "input").call(ctx::run);

        assertThat(result).isEqualTo("Result: input");
      }

      @Test
      @DisplayName("flatMap() should throw NullPointerException for null function")
      void flatMap_shouldThrowForNullFunction() {
        Context<String, String> ctx = Context.succeed("value");

        assertThatNullPointerException()
            .isThrownBy(() -> ctx.flatMap(null))
            .withMessageContaining("function cannot be null");
      }

      @Test
      @DisplayName("flatMap() should throw if function returns null context")
      void flatMap_shouldThrowIfFunctionReturnsNull() {
        Context<String, String> ctx =
            Context.<String, String>succeed("value").flatMap(s -> null);

        assertThatNullPointerException()
            .isThrownBy(ctx::run)
            .withMessageContaining("flatMap function returned null context");
      }

      @Test
      @DisplayName("flatMap() should propagate errors from source")
      void flatMap_shouldPropagateSourceErrors() {
        RuntimeException error = new RuntimeException("source error");
        AtomicBoolean wasCalled = new AtomicBoolean(false);

        Context<String, String> ctx =
            Context.<String, String>fail(error)
                .flatMap(
                    s -> {
                      wasCalled.set(true);
                      return Context.succeed("should not reach");
                    });

        assertThatThrownBy(ctx::run).isSameAs(error);
        assertThat(wasCalled).isFalse();
      }

      @Test
      @DisplayName("flatMap() should propagate errors from next context")
      void flatMap_shouldPropagateNextErrors() {
        RuntimeException error = new RuntimeException("next error");

        Context<String, String> ctx =
            Context.<String, String>succeed("value").flatMap(s -> Context.fail(error));

        assertThatThrownBy(ctx::run).isSameAs(error);
      }
    }

    @Nested
    @DisplayName("recover()")
    class RecoverTests {

      @Test
      @DisplayName("recover() should recover from failure with value")
      void recover_shouldRecoverWithValue() {
        Context<String, String> ctx =
            Context.<String, String>fail(new RuntimeException("error"))
                .recover(e -> "recovered: " + e.getMessage());

        String result = ctx.run();

        assertThat(result).isEqualTo("recovered: error");
      }

      @Test
      @DisplayName("recover() should pass through success")
      void recover_shouldPassThroughSuccess() {
        Context<String, String> ctx =
            Context.<String, String>succeed("success").recover(e -> "recovered");

        String result = ctx.run();

        assertThat(result).isEqualTo("success");
      }

      @Test
      @DisplayName("recover() should throw NullPointerException for null function")
      void recover_shouldThrowForNullFunction() {
        Context<String, String> ctx = Context.succeed("value");

        assertThatNullPointerException()
            .isThrownBy(() -> ctx.recover(null))
            .withMessageContaining("recoveryFunction cannot be null");
      }
    }

    @Nested
    @DisplayName("recoverWith()")
    class RecoverWithTests {

      @Test
      @DisplayName("recoverWith() should recover from failure with context")
      void recoverWith_shouldRecoverWithContext() {
        Context<String, String> ctx =
            Context.<String, String>fail(new RuntimeException("error"))
                .recoverWith(e -> Context.succeed("recovered: " + e.getMessage()));

        String result = ctx.run();

        assertThat(result).isEqualTo("recovered: error");
      }

      @Test
      @DisplayName("recoverWith() should pass through success")
      void recoverWith_shouldPassThroughSuccess() {
        Context<String, String> ctx =
            Context.<String, String>succeed("success")
                .recoverWith(e -> Context.succeed("recovered"));

        String result = ctx.run();

        assertThat(result).isEqualTo("success");
      }

      @Test
      @DisplayName("recoverWith() should throw NullPointerException for null function")
      void recoverWith_shouldThrowForNullFunction() {
        Context<String, String> ctx = Context.succeed("value");

        assertThatNullPointerException()
            .isThrownBy(() -> ctx.recoverWith(null))
            .withMessageContaining("recoveryFunction cannot be null");
      }

      @Test
      @DisplayName("recoverWith() should throw if recovery returns null")
      void recoverWith_shouldThrowIfRecoveryReturnsNull() {
        Context<String, String> ctx =
            Context.<String, String>fail(new RuntimeException("error"))
                .recoverWith(e -> null);

        assertThatNullPointerException()
            .isThrownBy(ctx::run)
            .withMessageContaining("recovery context cannot be null");
      }
    }

    @Nested
    @DisplayName("mapError()")
    class MapErrorTests {

      @Test
      @DisplayName("mapError() should transform error")
      void mapError_shouldTransformError() {
        Context<String, String> ctx =
            Context.<String, String>fail(new RuntimeException("original"))
                .mapError(e -> new IllegalArgumentException("mapped: " + e.getMessage()));

        assertThatThrownBy(ctx::run)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mapped: original");
      }

      @Test
      @DisplayName("mapError() should pass through success")
      void mapError_shouldPassThroughSuccess() {
        Context<String, String> ctx =
            Context.<String, String>succeed("success")
                .mapError(e -> new IllegalArgumentException("should not be called"));

        String result = ctx.run();

        assertThat(result).isEqualTo("success");
      }

      @Test
      @DisplayName("mapError() should throw NullPointerException for null function")
      void mapError_shouldThrowForNullFunction() {
        Context<String, String> ctx = Context.succeed("value");

        assertThatNullPointerException()
            .isThrownBy(() -> ctx.mapError(null))
            .withMessageContaining("function cannot be null");
      }
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethods {

    @Nested
    @DisplayName("toVTask()")
    class ToVTaskTests {

      @Test
      @DisplayName("toVTask() should convert successful context to VTask")
      void toVTask_shouldConvertSuccessToVTask() throws Exception {
        Context<String, String> ctx = Context.ask(STRING_KEY);
        VTask<String> task = ctx.toVTask();

        String result =
            ScopedValue.where(STRING_KEY, "scoped-value")
                .call(() -> task.runSafe().orElse("error"));

        assertThat(result).isEqualTo("scoped-value");
      }

      @Test
      @DisplayName("toVTask() should convert failed context to failing VTask")
      void toVTask_shouldConvertFailureToVTask() {
        RuntimeException error = new RuntimeException("test error");
        Context<String, String> ctx = Context.fail(error);
        VTask<String> task = ctx.toVTask();

        var tryResult = task.runSafe();

        assertThat(tryResult.isFailure()).isTrue();
      }
    }

    @Nested
    @DisplayName("toMaybe()")
    class ToMaybeTests {

      @Test
      @DisplayName("toMaybe() should convert successful non-null result to Just")
      void toMaybe_shouldConvertSuccessToJust() throws Exception {
        Context<String, String> ctx = Context.ask(STRING_KEY);

        Maybe<String> result =
            ScopedValue.where(STRING_KEY, "value").call(ctx::toMaybe);

        assertThat(result.isJust()).isTrue();
        assertThat(result.orElse("fallback")).isEqualTo("value");
      }

      @Test
      @DisplayName("toMaybe() should convert null result to Nothing")
      void toMaybe_shouldConvertNullToNothing() {
        Context<String, String> ctx = Context.succeed(null);

        Maybe<String> result = ctx.toMaybe();

        assertThat(result.isJust()).isFalse();
      }

      @Test
      @DisplayName("toMaybe() should convert failure to Nothing")
      void toMaybe_shouldConvertFailureToNothing() {
        Context<String, String> ctx = Context.fail(new RuntimeException("error"));

        Maybe<String> result = ctx.toMaybe();

        assertThat(result.isJust()).isFalse();
      }
    }

    @Nested
    @DisplayName("asUnit()")
    class AsUnitTests {

      @Test
      @DisplayName("asUnit() should discard result and return Unit")
      void asUnit_shouldReturnUnit() throws Exception {
        Context<String, String> ctx = Context.ask(STRING_KEY);
        Context<String, Unit> unitCtx = ctx.asUnit();

        Unit result =
            ScopedValue.where(STRING_KEY, "value").call(unitCtx::run);

        assertThat(result).isEqualTo(Unit.INSTANCE);
      }

      @Test
      @DisplayName("asUnit() should preserve failure")
      void asUnit_shouldPreserveFailure() {
        RuntimeException error = new RuntimeException("error");
        Context<String, String> ctx = Context.fail(error);
        Context<String, Unit> unitCtx = ctx.asUnit();

        assertThatThrownBy(unitCtx::run).isSameAs(error);
      }
    }
  }

  @Nested
  @DisplayName("Record Implementations")
  class RecordImplementations {

    @Nested
    @DisplayName("Ask Record")
    class AskRecordTests {

      @Test
      @DisplayName("Ask record should have valid components")
      void ask_shouldHaveValidComponents() {
        Context.Ask<String, String> ask = new Context.Ask<>(STRING_KEY, s -> s.toUpperCase());

        assertThat(ask.key()).isSameAs(STRING_KEY);
        assertThat(ask.transform()).isNotNull();
      }

      @Test
      @DisplayName("Ask record should reject null key")
      void ask_shouldRejectNullKey() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Context.Ask<>(null, s -> s))
            .withMessageContaining("key cannot be null");
      }

      @Test
      @DisplayName("Ask record should reject null transform")
      void ask_shouldRejectNullTransform() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Context.Ask<>(STRING_KEY, null))
            .withMessageContaining("transform cannot be null");
      }
    }

    @Nested
    @DisplayName("Pure Record")
    class PureRecordTests {

      @Test
      @DisplayName("Pure record should hold value")
      void pure_shouldHoldValue() {
        Context.Pure<String, Integer> pure = new Context.Pure<>(42);

        assertThat(pure.value()).isEqualTo(42);
        assertThat(pure.run()).isEqualTo(42);
      }

      @Test
      @DisplayName("Pure record should allow null value")
      void pure_shouldAllowNullValue() {
        Context.Pure<String, Integer> pure = new Context.Pure<>(null);

        assertThat(pure.value()).isNull();
        assertThat(pure.run()).isNull();
      }
    }

    @Nested
    @DisplayName("FlatMapped Record")
    class FlatMappedRecordTests {

      @Test
      @DisplayName("FlatMapped record should compose computations")
      void flatMapped_shouldComposeComputations() throws Exception {
        Context<String, String> source = Context.ask(STRING_KEY);
        Context.FlatMapped<String, String, Integer> flatMapped =
            new Context.FlatMapped<>(source, s -> Context.succeed(s.length()));

        Integer result =
            ScopedValue.where(STRING_KEY, "hello").call(flatMapped::run);

        assertThat(result).isEqualTo(5);
      }

      @Test
      @DisplayName("FlatMapped record should reject null source")
      void flatMapped_shouldRejectNullSource() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Context.FlatMapped<>(null, s -> Context.succeed(s)))
            .withMessageContaining("source cannot be null");
      }

      @Test
      @DisplayName("FlatMapped record should reject null function")
      void flatMapped_shouldRejectNullFunction() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Context.FlatMapped<>(Context.succeed("x"), null))
            .withMessageContaining("function cannot be null");
      }
    }

    @Nested
    @DisplayName("Failed Record")
    class FailedRecordTests {

      @Test
      @DisplayName("Failed record should hold error")
      void failed_shouldHoldError() {
        RuntimeException error = new RuntimeException("test");
        Context.Failed<String, String> failed = new Context.Failed<>(error);

        assertThat(failed.error()).isSameAs(error);
      }

      @Test
      @DisplayName("Failed record run() should throw error")
      void failed_runShouldThrowError() {
        RuntimeException error = new RuntimeException("test error");
        Context.Failed<String, String> failed = new Context.Failed<>(error);

        assertThatThrownBy(failed::run).isSameAs(error);
      }

      @Test
      @DisplayName("Failed record run() should sneaky throw checked exception")
      void failed_runShouldSneakyThrowCheckedException() {
        Exception checkedException = new Exception("checked exception");
        Context.Failed<String, String> failed = new Context.Failed<>(checkedException);

        assertThatThrownBy(failed::run)
            .isSameAs(checkedException)
            .isInstanceOf(Exception.class)
            .hasMessage("checked exception");
      }

      @Test
      @DisplayName("Failed record should reject null error")
      void failed_shouldRejectNullError() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Context.Failed<>(null))
            .withMessageContaining("error cannot be null");
      }
    }

    @Nested
    @DisplayName("Recovered Record")
    class RecoveredRecordTests {

      @Test
      @DisplayName("Recovered record should recover from failure")
      void recovered_shouldRecoverFromFailure() {
        Context<String, String> source = Context.fail(new RuntimeException("error"));
        Context.Recovered<String, String> recovered =
            new Context.Recovered<>(source, e -> "recovered");

        String result = recovered.run();

        assertThat(result).isEqualTo("recovered");
      }

      @Test
      @DisplayName("Recovered record should reject null source")
      void recovered_shouldRejectNullSource() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Context.Recovered<>(null, e -> "x"))
            .withMessageContaining("source cannot be null");
      }

      @Test
      @DisplayName("Recovered record should reject null recovery function")
      void recovered_shouldRejectNullRecoveryFunction() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Context.Recovered<>(Context.succeed("x"), null))
            .withMessageContaining("recoveryFunction cannot be null");
      }
    }

    @Nested
    @DisplayName("RecoveredWith Record")
    class RecoveredWithRecordTests {

      @Test
      @DisplayName("RecoveredWith record should recover with context")
      void recoveredWith_shouldRecoverWithContext() {
        Context<String, String> source = Context.fail(new RuntimeException("error"));
        Context.RecoveredWith<String, String> recovered =
            new Context.RecoveredWith<>(source, e -> Context.succeed("recovered"));

        String result = recovered.run();

        assertThat(result).isEqualTo("recovered");
      }

      @Test
      @DisplayName("RecoveredWith record should reject null source")
      void recoveredWith_shouldRejectNullSource() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Context.RecoveredWith<>(null, e -> Context.succeed("x")))
            .withMessageContaining("source cannot be null");
      }

      @Test
      @DisplayName("RecoveredWith record should reject null recovery function")
      void recoveredWith_shouldRejectNullRecoveryFunction() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Context.RecoveredWith<>(Context.succeed("x"), null))
            .withMessageContaining("recoveryFunction cannot be null");
      }
    }

    @Nested
    @DisplayName("ErrorMapped Record")
    class ErrorMappedRecordTests {

      @Test
      @DisplayName("ErrorMapped record should transform error")
      void errorMapped_shouldTransformError() {
        Context<String, String> source = Context.fail(new RuntimeException("original"));
        Context.ErrorMapped<String, String> errorMapped =
            new Context.ErrorMapped<>(source, e -> new IllegalArgumentException("mapped"));

        assertThatThrownBy(errorMapped::run)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("mapped");
      }

      @Test
      @DisplayName("ErrorMapped record run() should sneaky throw mapped checked exception")
      void errorMapped_runShouldSneakyThrowMappedCheckedException() {
        Context<String, String> source = Context.fail(new RuntimeException("original"));
        Exception checkedException = new Exception("mapped checked exception");
        Context.ErrorMapped<String, String> errorMapped =
            new Context.ErrorMapped<>(source, e -> checkedException);

        assertThatThrownBy(errorMapped::run)
            .isSameAs(checkedException)
            .isInstanceOf(Exception.class)
            .hasMessage("mapped checked exception");
      }

      @Test
      @DisplayName("ErrorMapped record run() should pass through success")
      void errorMapped_runShouldPassThroughSuccess() {
        Context<String, String> source = Context.succeed("success value");
        Context.ErrorMapped<String, String> errorMapped =
            new Context.ErrorMapped<>(source, e -> new IllegalArgumentException("should not be called"));

        String result = errorMapped.run();

        assertThat(result).isEqualTo("success value");
      }

      @Test
      @DisplayName("ErrorMapped record should reject null source")
      void errorMapped_shouldRejectNullSource() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Context.ErrorMapped<>(null, e -> e))
            .withMessageContaining("source cannot be null");
      }

      @Test
      @DisplayName("ErrorMapped record should reject null error mapper")
      void errorMapped_shouldRejectNullErrorMapper() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Context.ErrorMapped<>(Context.succeed("x"), null))
            .withMessageContaining("errorMapper cannot be null");
      }
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {

    @Test
    @DisplayName("Left identity: succeed(a).flatMap(f) == f(a)")
    void leftIdentity() {
      String value = "test";

      Context<String, Integer> left =
          Context.<String, String>succeed(value).flatMap(s -> Context.succeed(s.length()));

      Context<String, Integer> right = Context.succeed(value.length());

      assertThat(left.run()).isEqualTo(right.run());
    }

    @Test
    @DisplayName("Right identity: m.flatMap(succeed) == m")
    void rightIdentity() throws Exception {
      Context<String, String> m = Context.ask(STRING_KEY);

      Context<String, String> left = m.flatMap(Context::succeed);

      String value = "test-value";
      String leftResult = ScopedValue.where(STRING_KEY, value).call(left::run);
      String rightResult = ScopedValue.where(STRING_KEY, value).call(m::run);

      assertThat(leftResult).isEqualTo(rightResult);
    }

    @Test
    @DisplayName("Associativity: m.flatMap(f).flatMap(g) == m.flatMap(a -> f(a).flatMap(g))")
    void associativity() throws Exception {
      Context<String, String> m = Context.ask(STRING_KEY);

      Context<String, Integer> left =
          m.flatMap(s -> Context.<String, String>succeed(s.toUpperCase()))
              .flatMap(s -> Context.succeed(s.length()));

      Context<String, Integer> right =
          m.flatMap(
              s ->
                  Context.<String, String>succeed(s.toUpperCase())
                      .flatMap(u -> Context.succeed(u.length())));

      String value = "test";
      Integer leftResult = ScopedValue.where(STRING_KEY, value).call(left::run);
      Integer rightResult = ScopedValue.where(STRING_KEY, value).call(right::run);

      assertThat(leftResult).isEqualTo(rightResult);
    }
  }

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {

    @Test
    @DisplayName("Identity: m.map(identity) == m")
    void identity() throws Exception {
      Context<String, String> m = Context.ask(STRING_KEY);
      Context<String, String> mapped = m.map(s -> s);

      String value = "test";
      String mResult = ScopedValue.where(STRING_KEY, value).call(m::run);
      String mappedResult = ScopedValue.where(STRING_KEY, value).call(mapped::run);

      assertThat(mappedResult).isEqualTo(mResult);
    }

    @Test
    @DisplayName("Composition: m.map(f).map(g) == m.map(f.andThen(g))")
    void composition() throws Exception {
      Context<String, String> m = Context.ask(STRING_KEY);

      Context<String, Integer> left = m.map(String::toUpperCase).map(String::length);
      Context<String, Integer> right =
          m.map(((java.util.function.Function<String, String>) String::toUpperCase).andThen(String::length));

      String value = "test";
      Integer leftResult = ScopedValue.where(STRING_KEY, value).call(left::run);
      Integer rightResult = ScopedValue.where(STRING_KEY, value).call(right::run);

      assertThat(leftResult).isEqualTo(rightResult);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Deeply nested flatMap chain should work")
    void deeplyNestedFlatMapChain() {
      AtomicInteger counter = new AtomicInteger(0);

      Context<String, Integer> ctx = Context.succeed(0);
      for (int i = 0; i < 100; i++) {
        ctx = ctx.flatMap(n -> {
          counter.incrementAndGet();
          return Context.succeed(n + 1);
        });
      }

      Integer result = ctx.run();

      assertThat(result).isEqualTo(100);
      assertThat(counter.get()).isEqualTo(100);
    }

    @Test
    @DisplayName("Recovery chain should work correctly")
    void recoveryChain() {
      Context<String, String> ctx =
          Context.<String, String>fail(new RuntimeException("error1"))
              .recover(e -> "recovered1")
              .<String>map(s -> { throw new RuntimeException("error2"); })
              .recover(e -> "recovered2");

      String result = ctx.run();

      assertThat(result).isEqualTo("recovered2");
    }
  }
}
