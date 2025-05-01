package org.higherkindedj.hkt.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for the custom KindUnwrapException class. */
@DisplayName("KindUnwrapException Tests")
class KindUnwrapExceptionTest {

  @Test
  @DisplayName("Constructor with message should set message correctly")
  void constructor_withMessage_setsMessage() {
    String testMessage = "Invalid Kind encountered";
    KindUnwrapException exception = new KindUnwrapException(testMessage);

    assertThat(exception.getMessage()).isEqualTo(testMessage);
    assertThat(exception.getCause()).isNull();
    // Verify it's an instance of the expected superclass
    assertThat(exception).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Constructor with message and cause should set both correctly")
  void constructor_withMessageAndCause_setsBoth() {
    String testMessage = "Failed during unwrapping";
    Throwable testCause = new NullPointerException("Internal null value");
    KindUnwrapException exception = new KindUnwrapException(testMessage, testCause);

    assertThat(exception.getMessage()).isEqualTo(testMessage);
    assertThat(exception.getCause()).isSameAs(testCause);
    // Verify it's an instance of the expected superclass
    assertThat(exception).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Constructor should handle null message")
  void constructor_withNullMessage_setsNullMessage() {
    KindUnwrapException exception = new KindUnwrapException(null);
    assertThat(exception.getMessage()).isNull();
    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Constructor should handle null message with cause")
  void constructor_withNullMessageAndCause_setsCauseAndNullMessage() {
    Throwable testCause = new RuntimeException("Root cause");
    KindUnwrapException exception = new KindUnwrapException(null, testCause);
    assertThat(exception.getMessage()).isNull();
    assertThat(exception.getCause()).isSameAs(testCause);
  }

  @Test
  @DisplayName("Constructor should handle null cause")
  void constructor_withMessageAndNullCause_setsMessageAndNullCause() {
    String testMessage = "Failure without specific cause";
    KindUnwrapException exception = new KindUnwrapException(testMessage, null);
    assertThat(exception.getMessage()).isEqualTo(testMessage);
    assertThat(exception.getCause()).isNull();
  }
}
