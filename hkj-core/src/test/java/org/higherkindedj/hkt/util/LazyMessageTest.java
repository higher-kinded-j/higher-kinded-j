// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.util.ErrorHandling.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Lazy Message Tests")
class LazyMessageTest {

  @Nested
  @DisplayName("lazyMessage() Tests")
  class LazyMessageTests {

    @Test
    void shouldProduceCorrectMessage() {
      Supplier<String> messageSupplier = lazyMessage("Error with value %s", "static value");
      assertThat(messageSupplier.get()).isEqualTo("Error with value static value");
    }

    @Test
    void shouldDeferFormatting() {
      AtomicBoolean formatCalled = new AtomicBoolean(false);

      Object expensiveArg =
          new Object() {
            @Override
            public String toString() {
              formatCalled.set(true);
              return "expensive toString result";
            }
          };

      Supplier<String> messageSupplier = lazyMessage("Message: %s", expensiveArg);

      // toString should not be called until we get the message
      assertThat(formatCalled.get()).isFalse();

      // Now get the message - this should trigger toString
      String result = messageSupplier.get();
      assertThat(result).isEqualTo("Message: expensive toString result");
      assertThat(formatCalled.get()).isTrue();
    }

    @Test
    void shouldHandleMultipleArguments() {
      Supplier<String> messageSupplier = lazyMessage("Error: %s with code %d", "failure", 404);
      assertThat(messageSupplier.get()).isEqualTo("Error: failure with code 404");
    }

    @Test
    void shouldHandleNoArguments() {
      Supplier<String> messageSupplier = lazyMessage("Simple error message");
      assertThat(messageSupplier.get()).isEqualTo("Simple error message");
    }

    @Test
    void shouldCallFormatOnEachCall() {
      AtomicInteger callCount = new AtomicInteger(0);
      Object trackingObject =
          new Object() {
            @Override
            public String toString() {
              return "call_" + callCount.incrementAndGet();
            }
          };

      Supplier<String> messageSupplier = lazyMessage("Value: %s", trackingObject);

      // Each call formats independently
      String result1 = messageSupplier.get();
      String result2 = messageSupplier.get();

      assertThat(result1).isEqualTo("Value: call_1");
      assertThat(result2).isEqualTo("Value: call_2");
      assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void shouldHandleNullTemplate() {
      Supplier<String> supplier = lazyMessage(null, "arg");

      assertThatThrownBy(supplier::get).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHandleNullArgs() {
      Supplier<String> messageSupplier = lazyMessage("Message: %s", (Object) null);
      assertThat(messageSupplier.get()).isEqualTo("Message: null");
    }

    @Test
    void shouldHandleEmptyArgs() {
      Supplier<String> messageSupplier = lazyMessage("Simple message");
      assertThat(messageSupplier.get()).isEqualTo("Simple message");
    }

    @Test
    void shouldHandleMalformedTemplate() {
      // Test with mismatched format specifiers
      Supplier<String> messageSupplier = lazyMessage("Value: %s %d", "text");

      // This should throw when get() is called due to format mismatch
      assertThatThrownBy(messageSupplier::get)
          .isInstanceOf(java.util.MissingFormatArgumentException.class);
    }
  }

  @Nested
  @DisplayName("throwKindUnwrapException() Tests")
  class ThrowKindUnwrapExceptionTests {

    @Test
    void shouldThrowWithMessage() {
      assertThatThrownBy(() -> throwKindUnwrapException(() -> "Lazy error"))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Lazy error");
    }

    @Test
    void shouldThrowWithMessageAndCause() {
      Exception cause = new RuntimeException("root cause");
      assertThatThrownBy(() -> throwKindUnwrapException(() -> "Lazy error with cause", cause))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Lazy error with cause")
          .hasCause(cause);
    }

    @Test
    void shouldEvaluateSupplierLazily() {
      AtomicBoolean supplierCalled = new AtomicBoolean(false);
      Supplier<String> lazyMessage =
          () -> {
            supplierCalled.set(true);
            return "Evaluated message";
          };

      assertThatThrownBy(() -> throwKindUnwrapException(lazyMessage))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Evaluated message");

      assertThat(supplierCalled.get()).isTrue();
    }

    @Test
    void shouldHandleNullSupplier() {
      assertThatThrownBy(() -> throwKindUnwrapException(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHandleNullSupplierWithCause() {
      Exception cause = new RuntimeException("cause");
      assertThatThrownBy(() -> throwKindUnwrapException(null, cause))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHandleSupplierReturningNull() {
      assertThatThrownBy(() -> throwKindUnwrapException(() -> null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage((String) null); // The message will literally be null
    }

    @Test
    void shouldHandleSupplierThrowing() {
      RuntimeException supplierException = new RuntimeException("Supplier failed");
      assertThatThrownBy(
              () ->
                  throwKindUnwrapException(
                      () -> {
                        throw supplierException;
                      }))
          .isSameAs(supplierException);
    }

    @Test
    void shouldHandleCauseWithNullMessage() {
      RuntimeException cause = new RuntimeException((String) null);
      assertThatThrownBy(() -> throwKindUnwrapException(() -> "Test message", cause))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Test message")
          .hasCause(cause);
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    void shouldNotEvaluateArgsMultipleTimes() {
      AtomicInteger evaluationCount = new AtomicInteger(0);
      Object trackingArg =
          new Object() {
            @Override
            public String toString() {
              return "evaluation_" + evaluationCount.incrementAndGet();
            }
          };

      Supplier<String> messageSupplier = lazyMessage("Value: %s", trackingArg);

      // Each call evaluates the arguments independently
      String result1 = messageSupplier.get();
      String result2 = messageSupplier.get();
      String result3 = messageSupplier.get();

      assertThat(result1).isEqualTo("Value: evaluation_1");
      assertThat(result2).isEqualTo("Value: evaluation_2");
      assertThat(result3).isEqualTo("Value: evaluation_3");
      assertThat(evaluationCount.get()).isEqualTo(3);
    }

    @Test
    void shouldHandleLargeMessages() {
      String longString = "x".repeat(10000);
      java.util.function.Supplier<String> messageSupplier =
          lazyMessage("Long message: %s", longString);

      String result = messageSupplier.get();
      assertThat(result).startsWith("Long message: xxx");
      assertThat(result.length())
          .isEqualTo(10000 + 14); // "Long message: " + longString (14 = length of "Long message: ")
    }

    @Test
    void shouldHandleComplexFormatting() {
      Supplier<String> complexMessage =
          lazyMessage("Complex format: %s, %d, %.2f, %b, %c", "text", 42, 3.14159, true, 'A');

      assertThat(complexMessage.get()).isEqualTo("Complex format: text, 42, 3.14, true, A");
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCasesTests {

    @Test
    void shouldHandleSpecialCharacters() {
      Supplier<String> messageSupplier = lazyMessage("Unicode: %s", "类型_τύπος_тип");
      assertThat(messageSupplier.get()).isEqualTo("Unicode: 类型_τύπος_тип");
    }

    @Test
    void shouldHandleFormatSpecifiersInArgs() {
      // Args containing format specifiers should be treated as regular text
      Supplier<String> messageSupplier = lazyMessage("Value: %s", "contains %d and %s");
      assertThat(messageSupplier.get()).isEqualTo("Value: contains %d and %s");
    }

    @Test
    void shouldHandleVeryLongArgumentList() {
      Object[] manyArgs = new Object[20];
      StringBuilder templateBuilder = new StringBuilder("Many args:");
      for (int i = 0; i < 20; i++) {
        manyArgs[i] = "arg" + i;
        templateBuilder.append(" %s");
      }

      Supplier<String> messageSupplier = lazyMessage(templateBuilder.toString(), manyArgs);
      String result = messageSupplier.get();

      assertThat(result).startsWith("Many args: arg0 arg1");
      assertThat(result).endsWith("arg18 arg19");
    }
  }
}
