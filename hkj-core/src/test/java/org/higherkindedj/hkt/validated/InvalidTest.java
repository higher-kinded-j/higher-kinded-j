// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Invalid<E, A> Tests")
class InvalidTest {

  private final Invalid<String, Integer> invalidInstance =
      new Invalid<>("Error: Something went wrong");
  private final Invalid<Integer, String> invalidWithDifferentErrorType = new Invalid<>(404);
  private final Semigroup<String> stringSemigroup = Semigroups.string(" & ");

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {
    @Test
    @DisplayName("should initialize with a non-null error")
    void shouldInitializeWithANonNullError() {
      assertThatCode(() -> new Invalid<>("Test Error")).doesNotThrowAnyException();
      Invalid<String, Integer> inv = new Invalid<>("Test Error");
      assertThat(inv.error()).isEqualTo("Test Error");
    }

    @Test
    @DisplayName("should throw NullPointerException if error is null")
    void shouldThrowNullPointerExceptionIfErrorIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> new Invalid<>(null))
          .withMessage(Validated.INVALID_ERROR_CANNOT_BE_NULL_MSG);
    }
  }

  @Nested
  @DisplayName("State Checking Methods")
  class StateCheckingMethods {
    @Test
    @DisplayName("isValid should return false")
    void isValidShouldReturnFalse() {
      assertThat(invalidInstance.isValid()).isFalse();
    }

    @Test
    @DisplayName("isInvalid should return true")
    void isInvalidShouldReturnTrue() {
      assertThat(invalidInstance.isInvalid()).isTrue();
    }
  }

  @Nested
  @DisplayName("Value Retrieval Methods")
  class ValueRetrievalMethods {
    @Test
    @DisplayName("get should throw NoSuchElementException")
    void getShouldThrowNoSuchElementException() {
      assertThatThrownBy(invalidInstance::get)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessage(
              Invalid.CANNOT_GET_FROM_INVALID_INSTANCE_PREFIX_MSG + invalidInstance.error());
    }

    @Test
    @DisplayName("getError should return the error")
    void getErrorShouldReturnTheError() {
      assertThat(invalidInstance.getError()).isEqualTo("Error: Something went wrong");
      assertThat(invalidWithDifferentErrorType.getError()).isEqualTo(404);
    }
  }

  @Nested
  @DisplayName("orElse Methods")
  class OrElseMethods {
    @Test
    @DisplayName("orElse should return the other value")
    void orElseShouldReturnTheOtherValue() {
      assertThat(invalidInstance.orElse(200)).isEqualTo(200);
    }

    @Test
    @DisplayName("orElse should throw NullPointerException if other is null")
    void orElseShouldThrowNullPointerExceptionIfOtherIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> invalidInstance.orElse(null))
          .withMessage(Invalid.OR_ELSE_OTHER_CANNOT_BE_NULL_MSG);
    }

    @Test
    @DisplayName("orElseGet should return value from supplier")
    void orElseGetShouldReturnValueFromSupplier() {
      Supplier<Integer> supplier = () -> 200;
      assertThat(invalidInstance.orElseGet(supplier)).isEqualTo(200);
    }

    @Test
    @DisplayName("orElseGet should throw NullPointerException if supplier is null")
    void orElseGetShouldThrowNullPointerExceptionIfSupplierIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> invalidInstance.orElseGet(null))
          .withMessage(Invalid.OR_ELSE_GET_SUPPLIER_CANNOT_BE_NULL_MSG);
    }

    @Test
    @DisplayName("orElseGet should throw NullPointerException if supplier returns null")
    void orElseGetShouldThrowNullPointerExceptionIfSupplierReturnsNull() {
      Supplier<Integer> nullReturningSupplier = () -> null;
      assertThatNullPointerException()
          .isThrownBy(() -> invalidInstance.orElseGet(nullReturningSupplier))
          .withMessage(Invalid.OR_ELSE_GET_SUPPLIER_RETURNED_NULL_MSG);
    }

    @Test
    @DisplayName("orElseThrow should throw exception from supplier")
    void orElseThrowShouldThrowExceptionFromSupplier() {
      class MyCustomException extends Throwable {
        MyCustomException(String message) {
          super(message);
        }
      }
      Supplier<MyCustomException> exceptionSupplier = () -> new MyCustomException("Custom Error");
      assertThatThrownBy(() -> invalidInstance.orElseThrow(exceptionSupplier))
          .isInstanceOf(MyCustomException.class)
          .hasMessage("Custom Error");
    }

    @Test
    @DisplayName("orElseThrow should throw NullPointerException if supplier is null")
    void orElseThrowShouldThrowNullPointerExceptionIfSupplierIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> invalidInstance.orElseThrow(null))
          .withMessage(Invalid.OR_ELSE_THROW_SUPPLIER_CANNOT_BE_NULL_MSG);
    }

    @Test
    @DisplayName("orElseThrow should throw NullPointerException if supplier produces null")
    void orElseThrowShouldThrowNullPointerExceptionIfSupplierProducesNull() {
      Supplier<RuntimeException> nullProducingSupplier = () -> null;
      assertThatNullPointerException()
          .isThrownBy(() -> invalidInstance.orElseThrow(nullProducingSupplier))
          .withMessage(Invalid.OR_ELSE_THROW_SUPPLIER_PRODUCED_NULL_MSG);
    }
  }

  @Nested
  @DisplayName("Side Effect Methods")
  class SideEffectMethods {
    @Test
    @DisplayName("ifValid should not perform action")
    void ifValidShouldNotPerformAction() {
      Consumer<Integer> consumer = val -> fail("Consumer should not be called for Invalid");
      invalidInstance.ifValid(consumer);
      // No assertion needed if fail() is not called
    }

    @Test
    @DisplayName("ifValid should throw NullPointerException if consumer is null")
    void ifValidShouldThrowNullPointerExceptionIfConsumerIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> invalidInstance.ifValid(null))
          .withMessage(Invalid.IF_VALID_CONSUMER_CANNOT_BE_NULL_MSG);
    }

    @Test
    @DisplayName("ifInvalid should perform action with the error")
    void ifInvalidShouldPerformActionWithTheError() {
      AtomicReference<String> store = new AtomicReference<>();
      Consumer<String> consumer = store::set;
      invalidInstance.ifInvalid(consumer);
      assertThat(store.get()).isEqualTo("Error: Something went wrong");
    }

    @Test
    @DisplayName("ifInvalid should throw NullPointerException if consumer is null")
    void ifInvalidShouldThrowNullPointerExceptionIfConsumerIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> invalidInstance.ifInvalid(null))
          .withMessage(Invalid.IF_INVALID_CONSUMER_CANNOT_BE_NULL_MSG);
    }
  }

  @Nested
  @DisplayName("Transformation Methods (map, flatMap, ap)")
  class TransformationMethods {
    @Test
    @DisplayName("map should return the same Invalid instance")
    void mapShouldReturnTheSameInvalidInstance() {
      Function<Integer, String> toStringFunc = Object::toString;
      Validated<String, String> mapped = invalidInstance.map(toStringFunc);
      assertThat(mapped).isSameAs(invalidInstance);
      assertThat(mapped.isInvalid()).isTrue();
      assertThat(mapped.getError()).isEqualTo("Error: Something went wrong");
    }

    @Test
    @DisplayName("map should throw NullPointerException if function is null")
    void mapShouldThrowNullPointerExceptionIfFunctionIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> invalidInstance.map(null))
          .withMessage(Invalid.MAP_FN_CANNOT_BE_NULL_MSG);
    }

    @Test
    @DisplayName("flatMap should return the same Invalid instance")
    void flatMapShouldReturnTheSameInvalidInstance() {
      Function<Integer, Validated<String, String>> toValidatedStringFunc =
          val -> Validated.valid("Val: " + val);
      Validated<String, String> flatMapped = invalidInstance.flatMap(toValidatedStringFunc);
      assertThat(flatMapped).isSameAs(invalidInstance);
      assertThat(flatMapped.isInvalid()).isTrue();
      assertThat(flatMapped.getError()).isEqualTo("Error: Something went wrong");
    }

    @Test
    @DisplayName("flatMap should throw NullPointerException if function is null")
    void flatMapShouldThrowNullPointerExceptionIfFunctionIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> invalidInstance.flatMap(null))
          .withMessage(Invalid.FLATMAP_FN_CANNOT_BE_NULL_MSG);
    }

    @Test
    @DisplayName("ap should return this Invalid instance if the function is Valid")
    void apShouldReturnThisInvalidIfFnValidatedIsValid() {
      Validated<String, Function<? super Integer, ? extends String>> fnValidated =
          Validated.valid(i -> "Applied: " + i);

      Validated<String, String> result = invalidInstance.ap(fnValidated, stringSemigroup);
      assertThat(result).isInstanceOf(Invalid.class);
      assertThat(result.getError()).isEqualTo(invalidInstance.error());
    }

    @Test
    @DisplayName("ap should combine errors if the function is also Invalid")
    void apShouldCombineErrorsIfFnValidatedIsAlsoInvalid() {
      Validated<String, Function<? super Integer, ? extends String>> fnValidated =
          Validated.invalid("Function Error");

      Validated<String, String> result = invalidInstance.ap(fnValidated, stringSemigroup);
      assertThat(result.isInvalid()).isTrue();
      assertThat(result.getError()).isEqualTo("Function Error & Error: Something went wrong");
    }

    @Test
    @DisplayName("ap should throw NullPointerException if fnValidated is null")
    void apShouldThrowNullPointerExceptionIfFnValidatedIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> invalidInstance.ap(null, stringSemigroup))
          .withMessage(Invalid.AP_FN_VALIDATED_CANNOT_BE_NULL_MSG);
    }

    @Test
    @DisplayName("ap should throw NullPointerException if semigroup is null")
    void apShouldThrowNullPointerExceptionIfSemigroupIsNull() {
      Validated<String, Function<? super Integer, ? extends String>> fnValidated =
          Validated.valid(i -> "Applied: " + i);
      assertThatNullPointerException()
          .isThrownBy(() -> invalidInstance.ap(fnValidated, null))
          .withMessage(Valid.SEMIGROUP_FOR_FOR_AP_CANNOT_BE_NULL_MSG);
    }
  }

  @Nested
  @DisplayName("toEither Method")
  class ToEitherMethod {
    @Test
    @DisplayName("toEither should return an Either.Left with the same error")
    void toEither_shouldReturnLeft() {
      Either<String, Integer> result = invalidInstance.toEither();

      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("Error: Something went wrong");
    }
  }

  @Nested
  @DisplayName("toString Method")
  class ToStringMethod {
    @Test
    @DisplayName("toString should return correct format")
    void toStringShouldReturnCorrectFormat() {
      assertThat(invalidInstance.toString()).isEqualTo("Invalid(Error: Something went wrong)");
      assertThat(invalidWithDifferentErrorType.toString()).isEqualTo("Invalid(404)");
    }
  }

  @Nested
  @DisplayName("Record Properties")
  class RecordProperties {
    @Test
    @DisplayName("should have correct error via record accessor")
    void shouldHaveCorrectErrorViaRecordAccessor() {
      assertThat(invalidInstance.error()).isEqualTo("Error: Something went wrong");
    }

    @Test
    @DisplayName("equals and hashCode should work as expected for records")
    void equalsAndHashCodeShouldWorkAsExpectedForRecords() {
      Invalid<String, Integer> sameInvalidInstance = new Invalid<>("Error: Something went wrong");
      Invalid<String, Integer> differentInvalidInstance = new Invalid<>("Another Error");
      Invalid<Integer, Integer> differentErrorTypeInvalid = new Invalid<>(500);
      String notAnInvalid = "not an invalid";

      // Equality
      assertThat(invalidInstance).isEqualTo(sameInvalidInstance);
      assertThat(invalidInstance).isNotEqualTo(differentInvalidInstance);
      assertThat(invalidInstance).isNotEqualTo(differentErrorTypeInvalid);
      assertThat(invalidInstance).isNotEqualTo(notAnInvalid);

      // HashCode
      assertThat(invalidInstance).hasSameHashCodeAs(sameInvalidInstance);
      assertThat(invalidInstance.hashCode()).isNotEqualTo(differentInvalidInstance.hashCode());
    }
  }
}
