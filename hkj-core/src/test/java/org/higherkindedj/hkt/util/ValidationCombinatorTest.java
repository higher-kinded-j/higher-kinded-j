// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.util.ErrorHandling.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Validation Combinator Tests")
class ValidationCombinatorTest {

  @Nested
  @DisplayName("validateAll() Tests")
  class ValidateAllTests {

    @Test
    void shouldSucceedWithNoFailures() {
      validateAll(
          Validation.require(true, "error1"), Validation.requireNonNull("not null", "error2"));
    }

    @Test
    void shouldSucceedWithEmptyValidations() {
      validateAll(); // No validations should succeed
    }

    @Test
    void shouldThrowWithOneFailure() {
      assertThatThrownBy(
              () ->
                  validateAll(
                      Validation.require(true, "error1"),
                      Validation.requireNonNull(null, "error2")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Validation failed: error2");
    }

    @Test
    void shouldThrowWithMultipleFailures() {
      assertThatThrownBy(
              () ->
                  validateAll(
                      Validation.require(false, "error1"),
                      Validation.requireNonNull(null, "error2"),
                      Validation.require(false, "error3")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Validation failed: error1; error2; error3");
    }

    @Test
    void shouldHandleNullValidations() {
      assertThatThrownBy(() -> validateAll((Validation[]) null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHandleNullValidationInArray() {
      assertThatThrownBy(() -> validateAll(Validation.require(true, "ok"), null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "Cannot invoke \"org.higherkindedj.hkt.util.ErrorHandling$Validation.validate()\""
                  + " because \"validation\" is null");
    }

    @Test
    void shouldHandleMixedExceptionTypes() {
      assertThatThrownBy(
              () ->
                  validateAll(
                      Validation.require(false, "arg error"),
                      Validation.requireNonNull(null, "null error")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Validation failed: arg error; null error");
    }

    @Test
    void shouldCollectAllErrors() {
      // Create custom validations that throw different exception types
      Validation custom1 =
          () -> {
            throw new RuntimeException("runtime error");
          };
      Validation custom2 =
          () -> {
            throw new IllegalStateException("state error");
          };
      Validation custom3 =
          () -> {
            throw new UnsupportedOperationException("unsupported error");
          };

      assertThatThrownBy(() -> validateAll(custom1, custom2, custom3))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Validation failed: runtime error; state error; unsupported error");
    }

    @Test
    void shouldHandleValidationThatThrowsNullMessage() {
      Validation customValidation =
          () -> {
            throw new RuntimeException((String) null);
          };

      assertThatThrownBy(() -> validateAll(customValidation))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Validation failed: null");
    }

    @Test
    void shouldNotShortCircuit() {
      AtomicBoolean secondValidationCalled = new AtomicBoolean(false);

      Validation firstValidation = Validation.require(false, "first error");
      Validation secondValidation =
          () -> {
            secondValidationCalled.set(true);
            throw new RuntimeException("second error");
          };

      assertThatThrownBy(() -> validateAll(firstValidation, secondValidation))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Validation failed: first error; second error");

      // Both validations should be called (no short-circuiting)
      assertThat(secondValidationCalled.get()).isTrue();
    }
  }

  @Nested
  @DisplayName("Validation.require() Tests")
  class ValidationRequireTests {

    @Test
    void shouldSucceedForTrue() throws Exception {
      Validation validation = Validation.require(true, "Should not throw");
      validation.validate(); // Should not throw
    }

    @Test
    void shouldThrowForFalse() {
      Validation validation = Validation.require(false, "Custom error");
      assertThatThrownBy(validation::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Custom error");
    }

    @Test
    void shouldHandleNullMessage() {
      Validation validation = Validation.require(false, null);
      assertThatThrownBy(validation::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage((String) null); // The message will literally be null
    }

    @Test
    void shouldHandleEmptyMessage() {
      Validation validation = Validation.require(false, "");
      assertThatThrownBy(validation::validate)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("");
    }
  }

  @Nested
  @DisplayName("Validation.requireNonNull() Tests")
  class ValidationRequireNonNullTests {

    @Test
    void shouldSucceedForNonNull() throws Exception {
      Validation validation = Validation.requireNonNull("not null", "error");
      validation.validate(); // Should not throw
    }

    @Test
    void shouldThrowForNull() {
      Validation validation = Validation.requireNonNull(null, "Custom null error");
      assertThatThrownBy(validation::validate)
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Custom null error");
    }

    @Test
    void shouldHandleNullMessage() {
      Validation validation = Validation.requireNonNull(null, null);
      assertThatThrownBy(validation::validate)
          .isInstanceOf(NullPointerException.class)
          .hasMessage((String) null); // The message will literally be null
    }

    @Test
    void shouldHandleEmptyMessage() {
      Validation validation = Validation.requireNonNull(null, "");
      assertThatThrownBy(validation::validate)
          .isInstanceOf(NullPointerException.class)
          .hasMessage("");
    }

    @Test
    void shouldSucceedForNonNullObjects() throws Exception {
      Object obj = new Object();
      Validation validation = Validation.requireNonNull(obj, "should not throw");
      validation.validate(); // Should not throw
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    void shouldHandleLargeNumberOfValidations() {
      List<Validation> validations = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
        final int index = i;
        if (i < 999) {
          validations.add(Validation.require(true, "validation" + index));
        } else {
          // Make the last one fail
          validations.add(Validation.require(false, "final failure"));
        }
      }

      assertThatThrownBy(() -> validateAll(validations.toArray(new Validation[0])))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("final failure");
    }

    @Test
    void shouldHandleLargeErrorMessages() {
      List<Validation> failingValidations = new ArrayList<>();
      StringBuilder expectedMessage = new StringBuilder("Validation failed: ");

      for (int i = 0; i < 100; i++) {
        final String errorMsg = "Error number " + i + " with some details";
        failingValidations.add(Validation.require(false, errorMsg));
        if (i > 0) expectedMessage.append("; ");
        expectedMessage.append(errorMsg);
      }

      assertThatThrownBy(() -> validateAll(failingValidations.toArray(new Validation[0])))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(expectedMessage.toString());
    }
  }

  @Nested
  @DisplayName("Custom Validation Tests")
  class CustomValidationTests {

    @Test
    void shouldWorkWithCustomValidation() throws Exception {
      // Custom validation that always succeeds
      Validation customValidation =
          () -> {
            // Do some custom validation logic
            if (System.currentTimeMillis() > 0) {
              return; // Success
            }
            throw new IllegalStateException("Time is broken");
          };

      customValidation.validate(); // Should not throw
    }

    @Test
    void shouldWorkWithComplexCustomValidation() {
      // Custom validation with complex logic
      Validation complexValidation =
          () -> {
            String value = "test";
            if (value == null) {
              throw new NullPointerException("Value cannot be null");
            }
            if (value.isEmpty()) {
              throw new IllegalArgumentException("Value cannot be empty");
            }
            if (value.length() > 100) {
              throw new IllegalArgumentException("Value too long");
            }
            // All checks passed
          };

      // This should NOT throw an exception since all validations pass
      try {
        validateAll(complexValidation);
        // If we reach here, no exception was thrown (which is expected)
      } catch (Exception e) {
        throw new AssertionError("Expected no exception, but got: " + e.getMessage(), e);
      }
    }

    @Test
    void shouldChainCustomValidationsWithBuiltIn() {
      String testValue = "valid";

      validateAll(
          Validation.requireNonNull(testValue, "value cannot be null"),
          Validation.require(!testValue.isEmpty(), "value cannot be empty"),
          Validation.require(testValue.length() <= 10, "value too long"),
          () -> {
            if (testValue.contains("invalid")) {
              throw new IllegalArgumentException("value contains invalid text");
            }
          });
    }

    @Test
    void shouldHandleValidationThatModifiesState() {
      AtomicBoolean stateModified = new AtomicBoolean(false);

      Validation stateModifyingValidation =
          () -> {
            stateModified.set(true);
            // Validation passes
          };

      validateAll(stateModifyingValidation);
      assertThat(stateModified.get()).isTrue();
    }
  }
}
