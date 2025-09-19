// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.util.ErrorHandling.*;

import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Exception Chain Tests")
class ExceptionChainTest {

  @Nested
  @DisplayName("wrapWithContext() Tests")
  class WrapWithContextTests {

    @Test
    void shouldWrapException() {
      RuntimeException original = new RuntimeException("Original error");
      KindUnwrapException wrapped = wrapWithContext(original, "Context", KindUnwrapException::new);

      assertThat(wrapped).hasMessage("Context: Original error").hasCause(original);
    }

    @Test
    void shouldHandleNullMessage() {
      RuntimeException original = new RuntimeException();
      KindUnwrapException wrapped = wrapWithContext(original, "Context", KindUnwrapException::new);

      assertThat(wrapped).hasMessage("Context: null").hasCause(original);
    }

    @Test
    void shouldWorkWithDifferentExceptionTypes() {
      IllegalStateException original = new IllegalStateException("State error");
      IllegalArgumentException wrapped =
          wrapWithContext(original, "Argument context", IllegalArgumentException::new);

      assertThat(wrapped).hasMessage("Argument context: State error").hasCause(original);
    }

    @Test
    void shouldHandleNullContext() {
      RuntimeException original = new RuntimeException("Original");
      KindUnwrapException wrapped = wrapWithContext(original, null, KindUnwrapException::new);
      assertThat(wrapped.getMessage()).isEqualTo("null: Original");
    }

    @Test
    void shouldThrowForNullWrapperConstructor() {
      RuntimeException original = new RuntimeException("Original");
      assertThatThrownBy(() -> wrapWithContext(original, "Context", null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowForNullOriginalException() {
      assertThatThrownBy(() -> wrapWithContext(null, "Context", KindUnwrapException::new))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHandleConstructorThrowing() {
      RuntimeException original = new RuntimeException("Original");

      assertThatThrownBy(
              () ->
                  wrapWithContext(
                      original,
                      "Context",
                      (msg, cause) -> {
                        throw new UnsupportedOperationException("Constructor failed");
                      }))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessage("Constructor failed");
    }

    @Test
    void shouldHandleExceptionWithNullMessage() {
      RuntimeException original = new RuntimeException((String) null);
      KindUnwrapException wrapped = wrapWithContext(original, "Context", KindUnwrapException::new);
      assertThat(wrapped.getMessage()).isEqualTo("Context: null");
    }

    @Test
    void shouldWorkWithDifferentWrapperTypes() {
      RuntimeException original = new RuntimeException("Original");

      // Wrap as IllegalArgumentException
      IllegalArgumentException argException =
          wrapWithContext(original, "Arg context", IllegalArgumentException::new);
      assertThat(argException.getMessage()).isEqualTo("Arg context: Original");
      assertThat(argException.getCause()).isSameAs(original);

      // Wrap as IllegalStateException
      IllegalStateException stateException =
          wrapWithContext(original, "State context", IllegalStateException::new);
      assertThat(stateException.getMessage()).isEqualTo("State context: Original");
      assertThat(stateException.getCause()).isSameAs(original);
    }
  }

  @Nested
  @DisplayName("wrapAsKindUnwrapException() Tests")
  class WrapAsKindUnwrapExceptionTests {

    @Test
    void shouldWrapException() {
      IllegalStateException original = new IllegalStateException("Original state");
      KindUnwrapException wrapped = wrapAsKindUnwrapException(original, "Wrapping context");
      assertThat(wrapped).hasMessage("Wrapping context: Original state").hasCause(original);
    }

    @Test
    void shouldHandleNestedExceptions() {
      Exception root = new Exception("Root cause");
      RuntimeException middle = new RuntimeException("Middle layer", root);
      KindUnwrapException wrapped = wrapAsKindUnwrapException(middle, "Top level");

      assertThat(wrapped).hasMessage("Top level: Middle layer").hasCause(middle);
      assertThat(wrapped.getCause().getCause()).isSameAs(root);
    }

    @Test
    void shouldThrowForNullOriginal() {
      assertThatThrownBy(() -> wrapAsKindUnwrapException(null, "Context"))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHandleNullContext() {
      RuntimeException original = new RuntimeException("Original");
      KindUnwrapException wrapped = wrapAsKindUnwrapException(original, null);
      assertThat(wrapped.getMessage()).isEqualTo("null: Original");
    }

    @Test
    void shouldHandleExceptionWithNullMessage() {
      RuntimeException original = new RuntimeException((String) null);
      KindUnwrapException wrapped = wrapAsKindUnwrapException(original, "Context");
      assertThat(wrapped.getMessage()).isEqualTo("Context: null");
    }

    @Test
    void shouldPreserveCauseChain() {
      Exception level1 = new Exception("Level 1");
      RuntimeException level2 = new RuntimeException("Level 2", level1);
      IllegalStateException level3 = new IllegalStateException("Level 3", level2);

      KindUnwrapException wrapped = wrapAsKindUnwrapException(level3, "Wrapped");

      assertThat(wrapped.getMessage()).isEqualTo("Wrapped: Level 3");
      assertThat(wrapped.getCause()).isSameAs(level3);
      assertThat(wrapped.getCause().getCause()).isSameAs(level2);
      assertThat(wrapped.getCause().getCause().getCause()).isSameAs(level1);
    }
  }

  @Nested
  @DisplayName("Exception Chain Integration Tests")
  class ExceptionChainIntegrationTests {

    @Test
    void shouldHandleMultipleWrapping() {
      RuntimeException root = new RuntimeException("Root cause");
      IllegalStateException middle = new IllegalStateException("Middle layer", root);

      // Wrap multiple times
      KindUnwrapException firstWrap = wrapAsKindUnwrapException(middle, "First context");
      KindUnwrapException secondWrap = wrapAsKindUnwrapException(firstWrap, "Second context");

      assertThat(secondWrap.getMessage()).isEqualTo("Second context: First context: Middle layer");
      assertThat(secondWrap.getCause()).isSameAs(firstWrap);
      assertThat(secondWrap.getCause().getCause()).isSameAs(middle);
      assertThat(secondWrap.getCause().getCause().getCause()).isSameAs(root);
    }

    @Test
    void shouldWorkWithDifferentExceptionTypes() {
      // Test various exception types as originals
      NullPointerException npe = new NullPointerException("NPE message");
      IllegalArgumentException iae = new IllegalArgumentException("IAE message");
      UnsupportedOperationException uoe = new UnsupportedOperationException("UOE message");

      KindUnwrapException wrappedNpe = wrapAsKindUnwrapException(npe, "NPE context");
      KindUnwrapException wrappedIae = wrapAsKindUnwrapException(iae, "IAE context");
      KindUnwrapException wrappedUoe = wrapAsKindUnwrapException(uoe, "UOE context");

      assertThat(wrappedNpe.getMessage()).isEqualTo("NPE context: NPE message");
      assertThat(wrappedIae.getMessage()).isEqualTo("IAE context: IAE message");
      assertThat(wrappedUoe.getMessage()).isEqualTo("UOE context: UOE message");
    }

    @Test
    void shouldMaintainStackTraces() {
      RuntimeException original = new RuntimeException("Original");
      KindUnwrapException wrapped = wrapAsKindUnwrapException(original, "Context");

      // The wrapped exception should have a stack trace
      assertThat(wrapped.getStackTrace()).isNotEmpty();

      // The original exception's stack trace should be preserved
      assertThat(wrapped.getCause().getStackTrace()).isNotEmpty();
      // Note: We don't check if they're the same object since stack traces may be copied
      assertThat(wrapped.getCause().getStackTrace()).isNotSameAs(wrapped.getStackTrace());
    }

    @Test
    void shouldHandleComplexWrappingChain() {
      // Create a complex wrapping scenario
      Exception original = new Exception("Database connection failed");

      // Wrap as repository exception
      RuntimeException repoException =
          wrapWithContext(original, "Repository layer", RuntimeException::new);

      // Wrap as service exception
      IllegalStateException serviceException =
          wrapWithContext(repoException, "Service layer", IllegalStateException::new);

      // Finally wrap as KindUnwrapException
      KindUnwrapException finalException =
          wrapAsKindUnwrapException(serviceException, "Controller layer");

      assertThat(finalException.getMessage())
          .isEqualTo(
              "Controller layer: Service layer: Repository layer: Database connection failed");

      // Verify the full chain
      Throwable current = finalException;
      assertThat(current).isInstanceOf(KindUnwrapException.class);

      current = current.getCause();
      assertThat(current).isInstanceOf(IllegalStateException.class);
      assertThat(current.getMessage())
          .isEqualTo("Service layer: Repository layer: Database connection failed");

      current = current.getCause();
      assertThat(current).isInstanceOf(RuntimeException.class);
      assertThat(current.getMessage()).isEqualTo("Repository layer: Database connection failed");

      current = current.getCause();
      assertThat(current).isInstanceOf(Exception.class);
      assertThat(current.getMessage()).isEqualTo("Database connection failed");
      assertThat(current).isSameAs(original);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCasesTests {

    @Test
    void shouldHandleVeryLongMessages() {
      String longMessage = "x".repeat(10000);
      RuntimeException original = new RuntimeException(longMessage);
      KindUnwrapException wrapped = wrapAsKindUnwrapException(original, "Context");

      assertThat(wrapped.getMessage()).startsWith("Context: xxx");
      assertThat(wrapped.getMessage().length())
          .isEqualTo(10000 + 9); // "Context: " + longMessage (9 = length of "Context: ")
    }

    @Test
    void shouldHandleSpecialCharactersInMessages() {
      RuntimeException original = new RuntimeException("Message with\nnewlines\tand\ttabs");
      KindUnwrapException wrapped = wrapAsKindUnwrapException(original, "Context");

      assertThat(wrapped.getMessage()).isEqualTo("Context: Message with\nnewlines\tand\ttabs");
    }

    @Test
    void shouldHandleUnicodeInMessages() {
      RuntimeException original = new RuntimeException("Unicode: 类型_τύπος_тип");
      KindUnwrapException wrapped = wrapAsKindUnwrapException(original, "Context");

      assertThat(wrapped.getMessage()).isEqualTo("Context: Unicode: 类型_τύπος_тип");
    }

    @Test
    void shouldHandleEmptyMessages() {
      RuntimeException original = new RuntimeException("");
      KindUnwrapException wrapped = wrapAsKindUnwrapException(original, "Context");

      assertThat(wrapped.getMessage()).isEqualTo("Context: ");
    }
  }
}
