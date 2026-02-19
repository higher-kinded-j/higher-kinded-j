// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test suite for {@link ContextException}, covering all constructors, the {@code wrap()} factory,
 * and the {@code unwrap()} utility.
 */
@DisplayName("ContextException")
class ContextExceptionTest {

  @Nested
  @DisplayName("Constructors")
  class Constructors {

    @Test
    @DisplayName("message constructor should set message")
    void messageConstructor() {
      ContextException ex = new ContextException("something failed");

      assertThat(ex).hasMessage("something failed").hasNoCause();
    }

    @Test
    @DisplayName("cause constructor should set cause and derive message")
    void causeConstructor() {
      IOException cause = new IOException("disk full");
      ContextException ex = new ContextException(cause);

      assertThat(ex).hasCause(cause).hasMessageContaining("IOException");
    }

    @Test
    @DisplayName("message+cause constructor should set both")
    void messageAndCauseConstructor() {
      IOException cause = new IOException("disk full");
      ContextException ex = new ContextException("wrapped IO error", cause);

      assertThat(ex).hasMessage("wrapped IO error").hasCause(cause);
    }
  }

  @Nested
  @DisplayName("wrap()")
  class Wrap {

    @Test
    @DisplayName("wrap() should return RuntimeException as-is")
    void wrap_shouldReturnRuntimeExceptionAsIs() {
      RuntimeException re = new IllegalArgumentException("bad arg");

      RuntimeException result = ContextException.wrap(re);

      assertThat(result).isSameAs(re);
    }

    @Test
    @DisplayName("wrap() should return ContextException as-is")
    void wrap_shouldReturnContextExceptionAsIs() {
      ContextException ce = new ContextException("already wrapped");

      RuntimeException result = ContextException.wrap(ce);

      assertThat(result).isSameAs(ce);
    }

    @Test
    @DisplayName("wrap() should wrap checked exception in ContextException")
    void wrap_shouldWrapCheckedException() {
      IOException checked = new IOException("disk full");

      RuntimeException result = ContextException.wrap(checked);

      assertThat(result).isInstanceOf(ContextException.class).hasCause(checked);
    }

    @Test
    @DisplayName("wrap() should wrap plain Exception in ContextException")
    void wrap_shouldWrapPlainException() {
      Exception checked = new Exception("generic checked");

      RuntimeException result = ContextException.wrap(checked);

      assertThat(result).isInstanceOf(ContextException.class).hasCause(checked);
    }

    @Test
    @DisplayName("wrap() should rethrow Error directly")
    void wrap_shouldRethrowError() {
      OutOfMemoryError oom = new OutOfMemoryError("oom");

      assertThatThrownBy(() -> ContextException.wrap(oom)).isSameAs(oom);
    }

    @Test
    @DisplayName("wrap() should rethrow StackOverflowError directly")
    void wrap_shouldRethrowStackOverflowError() {
      StackOverflowError soe = new StackOverflowError("stack");

      assertThatThrownBy(() -> ContextException.wrap(soe)).isSameAs(soe);
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class Unwrap {

    @Test
    @DisplayName("unwrap() should return cause for ContextException with cause")
    void unwrap_shouldReturnCauseForContextException() {
      IOException cause = new IOException("disk full");
      ContextException ce = new ContextException(cause);

      Throwable result = ContextException.unwrap(ce);

      assertThat(result).isSameAs(cause);
    }

    @Test
    @DisplayName("unwrap() should return self for ContextException without cause")
    void unwrap_shouldReturnSelfForContextExceptionWithoutCause() {
      ContextException ce = new ContextException("no cause");

      Throwable result = ContextException.unwrap(ce);

      assertThat(result).isSameAs(ce);
    }

    @Test
    @DisplayName("unwrap() should return self for RuntimeException")
    void unwrap_shouldReturnSelfForRuntimeException() {
      RuntimeException re = new IllegalStateException("state");

      Throwable result = ContextException.unwrap(re);

      assertThat(result).isSameAs(re);
    }

    @Test
    @DisplayName("unwrap() should return self for checked exception")
    void unwrap_shouldReturnSelfForCheckedException() {
      IOException io = new IOException("io");

      Throwable result = ContextException.unwrap(io);

      assertThat(result).isSameAs(io);
    }

    @Test
    @DisplayName("unwrap() should return self for Error")
    void unwrap_shouldReturnSelfForError() {
      OutOfMemoryError oom = new OutOfMemoryError("oom");

      Throwable result = ContextException.unwrap(oom);

      assertThat(result).isSameAs(oom);
    }
  }

  @Nested
  @DisplayName("Integration with Context")
  class Integration {

    @Test
    @DisplayName("Context.fail with checked exception should be catchable as ContextException")
    void contextFailWithCheckedShouldBeCatchableAsContextException() {
      IOException checked = new IOException("disk full");
      Context<String, String> ctx = Context.fail(checked);

      assertThatThrownBy(ctx::run)
          .isInstanceOf(ContextException.class)
          .isInstanceOf(RuntimeException.class)
          .hasCause(checked);
    }

    @Test
    @DisplayName("Context.fail with RuntimeException should NOT wrap")
    void contextFailWithRuntimeShouldNotWrap() {
      IllegalArgumentException rte = new IllegalArgumentException("bad");
      Context<String, String> ctx = Context.fail(rte);

      assertThatThrownBy(ctx::run).isSameAs(rte);
    }

    @Test
    @DisplayName("Recovery from checked exception should see original cause, not wrapper")
    void recoveryShouldSeeOriginalCause() {
      IOException checked = new IOException("disk full");

      String result =
          Context.<String, String>fail(checked)
              .recover(
                  e -> {
                    assertThat(e).isSameAs(checked);
                    return "recovered from " + e.getClass().getSimpleName();
                  })
              .run();

      assertThat(result).isEqualTo("recovered from IOException");
    }

    @Test
    @DisplayName("mapError should see original cause and can return checked exception")
    void mapErrorShouldSeeOriginalAndCanReturnChecked() {
      IOException original = new IOException("original");
      Exception mapped = new Exception("mapped");

      Context<String, String> ctx =
          Context.<String, String>fail(original)
              .mapError(
                  e -> {
                    assertThat(e).isSameAs(original);
                    return mapped;
                  });

      assertThatThrownBy(ctx::run).isInstanceOf(ContextException.class).hasCause(mapped);
    }

    @Test
    @DisplayName("toVTask should work without compilation issues")
    void toVTaskShouldWork() throws Exception {
      Context<String, String> ctx = Context.succeed("hello");

      var task = ctx.toVTask();
      var result = task.runSafe();

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse("nope")).isEqualTo("hello");
    }

    @Test
    @DisplayName("toMaybe should work for failed context with checked exception")
    void toMaybeShouldWorkForCheckedFailure() {
      Context<String, String> ctx = Context.fail(new IOException("io"));

      var maybe = ctx.toMaybe();

      assertThat(maybe.isJust()).isFalse();
    }
  }
}
