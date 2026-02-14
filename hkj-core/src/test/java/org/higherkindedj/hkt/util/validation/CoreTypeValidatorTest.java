// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.util.validation.CoreTypeValidator.CORE_TYPE_VALIDATOR;
import static org.higherkindedj.hkt.util.validation.Operation.LEFT;
import static org.higherkindedj.hkt.util.validation.Operation.RIGHT;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CoreTypeValidator")
class CoreTypeValidatorTest {

  private static final class TestType {}

  @Nested
  @DisplayName("requireValue")
  class RequireValue {

    @Test
    @DisplayName("should return non-null value")
    void shouldReturnNonNullValue() {
      var value = "test-value";
      var result = CORE_TYPE_VALIDATOR.requireValue(value, TestType.class, LEFT);

      assertThat(result).isEqualTo(value);
    }

    @Test
    @DisplayName("should throw NullPointerException when value is null")
    void shouldThrowWhenValueIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> CORE_TYPE_VALIDATOR.requireValue(null, TestType.class, LEFT))
          .withMessage("TestType.left value cannot be null");
    }

    @Test
    @DisplayName("should include type class name in error message")
    void shouldIncludeTypeClassNameInErrorMessage() {
      assertThatNullPointerException()
          .isThrownBy(() -> CORE_TYPE_VALIDATOR.requireValue(null, TestType.class, RIGHT))
          .withMessageContaining("TestType");
    }

    @Test
    @DisplayName("should include operation in error message")
    void shouldIncludeOperationInErrorMessage() {
      assertThatNullPointerException()
          .isThrownBy(() -> CORE_TYPE_VALIDATOR.requireValue(null, TestType.class, LEFT))
          .withMessageContaining("left");
    }
  }

  @Nested
  @DisplayName("requireValue with custom value name")
  class RequireValueWithCustomName {

    @Test
    @DisplayName("should return non-null value")
    void shouldReturnNonNullValue() {
      var value = "test-value";
      var result = CORE_TYPE_VALIDATOR.requireValue(value, "customValue", TestType.class, LEFT);

      assertThat(result).isEqualTo(value);
    }

    @Test
    @DisplayName("should throw NullPointerException when value is null")
    void shouldThrowWhenValueIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> CORE_TYPE_VALIDATOR.requireValue(null, "customValue", TestType.class, LEFT))
          .withMessage("TestType.left customValue cannot be null");
    }

    @Test
    @DisplayName("should include custom value name in error message")
    void shouldIncludeCustomValueNameInErrorMessage() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> CORE_TYPE_VALIDATOR.requireValue(null, "myValue", TestType.class, RIGHT))
          .withMessageContaining("myValue");
    }

    @Test
    @DisplayName("should include type class and operation in error message")
    void shouldIncludeTypeClassAndOperationInErrorMessage() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> CORE_TYPE_VALIDATOR.requireValue(null, "customValue", TestType.class, LEFT))
          .withMessageContaining("TestType.left");
    }
  }

  @Nested
  @DisplayName("requireError")
  class RequireError {

    @Test
    @DisplayName("should return non-null error")
    void shouldReturnNonNullError() {
      var error = new RuntimeException("test error");
      var result = CORE_TYPE_VALIDATOR.requireError(error, TestType.class, LEFT);

      assertThat(result).isEqualTo(error);
    }

    @Test
    @DisplayName("should throw NullPointerException when error is null")
    void shouldThrowWhenErrorIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> CORE_TYPE_VALIDATOR.requireError(null, TestType.class, LEFT))
          .withMessage("TestType.left error cannot be null");
    }

    @Test
    @DisplayName("should include type class name in error message")
    void shouldIncludeTypeClassNameInErrorMessage() {
      assertThatNullPointerException()
          .isThrownBy(() -> CORE_TYPE_VALIDATOR.requireError(null, TestType.class, RIGHT))
          .withMessageContaining("TestType");
    }

    @Test
    @DisplayName("should include operation in error message")
    void shouldIncludeOperationInErrorMessage() {
      assertThatNullPointerException()
          .isThrownBy(() -> CORE_TYPE_VALIDATOR.requireError(null, TestType.class, LEFT))
          .withMessageContaining("left");
    }

    @Test
    @DisplayName("should work with different error types")
    void shouldWorkWithDifferentErrorTypes() {
      var illegalStateError = new IllegalStateException("state error");
      var result = CORE_TYPE_VALIDATOR.requireError(illegalStateError, TestType.class, RIGHT);

      assertThat(result).isEqualTo(illegalStateError);
    }
  }
}
