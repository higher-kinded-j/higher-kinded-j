// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.*;

import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Validated<E, A> Interface Tests")
class ValidatedTest {

  @Nested
  @DisplayName("Static Factory Methods")
  class StaticFactoryMethods {
    @Test
    @DisplayName("valid(value) should create a Valid instance for non-null value")
    void validShouldCreateAValidInstance() {
      Validated<String, Integer> validated = Validated.valid(123);
      assertThat(validated).isInstanceOf(Valid.class);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo(123);
    }

    @Test
    @DisplayName("valid(null) should throw NullPointerException")
    void validNullShouldThrowNullPointerException() {
      // Assuming the NPE originates from the Valid constructor's check
      assertThatNullPointerException()
          .isThrownBy(() -> Validated.valid(null))
          .withMessage("Validated.VALID_VALUE_CANNOT_BE_NULL_MSG");
    }

    @Test
    @DisplayName("invalid(error) should create an Invalid instance for non-null error")
    void invalidShouldCreateAnInvalidInstance() {
      Validated<String, Integer> validated = Validated.invalid("Test Error");
      assertThat(validated).isInstanceOf(Invalid.class);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("Test Error");
    }

    @Test
    @DisplayName("invalid(error) should throw NullPointerException if error is null")
    void invalidShouldThrowNullPointerExceptionIfErrorIsNull() {
      // Assuming the NPE originates from the Invalid constructor's check
      assertThatNullPointerException()
          .isThrownBy(() -> Validated.invalid(null))
          .withMessage("INVALID_ERROR_CANNOT_BE_NULL_MSG");
    }
  }

  @Nested
  @DisplayName("fold Method")
  class FoldMethod {
    // Validated.valid() ensures non-null value for validInstance
    private final Validated<String, Integer> validInstance = Validated.valid(100);
    private final Validated<String, Integer> invalidInstance = Validated.invalid("Error Occurred");

    private final Function<String, String> invalidMapper = error -> "Error: " + error;
    private final Function<Integer, String> validMapper = value -> "Value: " + value;

    @Test
    @DisplayName("fold on Valid should apply validMapper")
    void foldOnValidShouldApplyValidMapper() {
      String result = validInstance.fold(invalidMapper, validMapper);
      assertThat(result).isEqualTo("Value: 100");
    }

    @Test
    @DisplayName("fold on Invalid should apply invalidMapper")
    void foldOnInvalidShouldApplyInvalidMapper() {
      String result = invalidInstance.fold(invalidMapper, validMapper);
      assertThat(result).isEqualTo("Error: Error Occurred");
    }

    @Test
    @DisplayName("fold should throw NullPointerException if invalidMapper is null")
    void foldShouldThrowNullPointerExceptionIfInvalidMapperIsNull() {
      // Validated.java: Objects.requireNonNull(invalidMapper, "invalidMapper cannot be null");
      assertThatNullPointerException()
          .isThrownBy(() -> validInstance.fold(null, validMapper))
          .withMessage("invalidMapper cannot be null");
      assertThatNullPointerException()
          .isThrownBy(() -> invalidInstance.fold(null, validMapper))
          .withMessage("invalidMapper cannot be null");
    }

    @Test
    @DisplayName("fold should throw NullPointerException if validMapper is null")
    void foldShouldThrowNullPointerExceptionIfValidMapperIsNull() {
      // Validated.java: Objects.requireNonNull(validMapper, "validMapper cannot be null");
      assertThatNullPointerException()
          .isThrownBy(() -> validInstance.fold(invalidMapper, null))
          .withMessage("validMapper cannot be null");
      assertThatNullPointerException()
          .isThrownBy(() -> invalidInstance.fold(invalidMapper, null))
          .withMessage("validMapper cannot be null");
    }

    @Test
    @DisplayName("fold should propagate exception from validMapper")
    void foldShouldPropagateExceptionFromValidMapper() {
      RuntimeException ex = new RuntimeException("Valid mapper failed");
      Function<Integer, String> throwingValidMapper =
          v -> {
            throw ex;
          };
      assertThatThrownBy(() -> validInstance.fold(invalidMapper, throwingValidMapper)).isSameAs(ex);
    }

    @Test
    @DisplayName("fold should propagate exception from invalidMapper")
    void foldShouldPropagateExceptionFromInvalidMapper() {
      RuntimeException ex = new RuntimeException("Invalid mapper failed");
      Function<String, String> throwingInvalidMapper =
          e -> {
            throw ex;
          };
      assertThatThrownBy(() -> invalidInstance.fold(throwingInvalidMapper, validMapper))
          .isSameAs(ex);
    }
  }
}
