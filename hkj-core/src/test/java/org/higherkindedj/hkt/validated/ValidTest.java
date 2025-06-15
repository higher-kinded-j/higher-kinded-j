// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.*;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Valid<E, A> Tests")
class ValidTest {

  private final Valid<String, Integer> validInstance = new Valid<>(123);
  private final Valid<String, String> validStringInstance = new Valid<>("Test");

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {
    @Test
    @DisplayName("should initialize with a non-null value")
    void shouldInitializeWithANonNullValue() {
      assertThatCode(() -> new Valid<>(100)).doesNotThrowAnyException();
      Valid<String, Integer> v = new Valid<>(100);
      assertThat(v.value()).isEqualTo(100);
    }

    @Test
    @DisplayName("should throw NullPointerException if value is null")
    void shouldThrowIfValueIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> new Valid<>(null))
          .withMessage(Validated.VALID_VALUE_CANNOT_BE_NULL_MSG);
    }
  }

  @Nested
  @DisplayName("State Checking Methods")
  class StateCheckingMethods {
    @Test
    @DisplayName("isValid should return true")
    void isValidShouldReturnTrue() {
      assertThat(validInstance.isValid()).isTrue();
    }

    @Test
    @DisplayName("isInvalid should return false")
    void isInvalidShouldReturnFalse() {
      assertThat(validInstance.isInvalid()).isFalse();
    }
  }

  @Nested
  @DisplayName("Value Retrieval Methods")
  class ValueRetrievalMethods {
    @Test
    @DisplayName("get should return the value")
    void getShouldReturnTheValue() {
      assertThat(validInstance.get()).isEqualTo(123);
    }

    @Test
    @DisplayName("getError should throw NoSuchElementException")
    void getErrorShouldThrowNoSuchElementException() {
      assertThatThrownBy(validInstance::getError)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessage(Valid.CANNOT_GET_ERROR_FROM_VALID_INSTANCE_MSG);
    }
  }

  @Nested
  @DisplayName("orElse Methods")
  class OrElseMethods {
    @Test
    @DisplayName("orElse should return the original value")
    void orElseShouldReturnTheOriginalValue() {
      assertThat(validInstance.orElse(200)).isEqualTo(123);
    }

    @Test
    @DisplayName("orElse should throw NullPointerException if other is null (eager check)")
    void orElseShouldThrowNullPointerExceptionIfOtherIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> validInstance.orElse(null))
          .withMessage(Valid.OR_ELSE_CANNOT_BE_NULL_MSG);
    }

    @Test
    @DisplayName("orElseGet should return the original value and not call supplier")
    void orElseGetShouldReturnTheOriginalValueAndNotCallSupplier() {
      AtomicBoolean supplierCalled = new AtomicBoolean(false);
      Supplier<Integer> supplier =
          () -> {
            supplierCalled.set(true);
            return 200;
          };
      assertThat(validInstance.orElseGet(supplier)).isEqualTo(123);
      assertThat(supplierCalled.get()).isFalse();
    }

    @Test
    @DisplayName("orElseGet should throw NullPointerException if supplier is null (eager check)")
    void orElseGetShouldThrowNullPointerExceptionIfSupplierIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> validInstance.orElseGet(null))
          .withMessage(Valid.OR_ELSE_GET_CANNOT_BE_NULL_MSG);
    }

    @Test
    @DisplayName("orElseThrow should return the original value and not call supplier")
    void orElseThrowShouldReturnTheOriginalValueAndNotCallSupplier() {
      AtomicBoolean supplierCalled = new AtomicBoolean(false);
      Supplier<RuntimeException> exceptionSupplier =
          () -> {
            supplierCalled.set(true);
            return new RuntimeException("Should not be thrown");
          };
      assertThat(validInstance.orElseThrow(exceptionSupplier)).isEqualTo(123);
      assertThat(supplierCalled.get()).isFalse();
    }

    @Test
    @DisplayName("orElseThrow should throw NullPointerException if supplier is null (eager check)")
    void orElseThrowShouldThrowNullPointerExceptionIfSupplierIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> validInstance.orElseThrow(null))
          .withMessage(Valid.OR_ELSE_THROW_CANNOT_BE_NULL_MSG);
    }
  }

  @Nested
  @DisplayName("Side Effect Methods")
  class SideEffectMethods {
    @Test
    @DisplayName("ifValid should perform action with the value")
    void ifValidShouldPerformActionWithTheValue() {
      AtomicReference<Integer> store = new AtomicReference<>();
      Consumer<Integer> consumer = store::set;
      validInstance.ifValid(consumer);
      assertThat(store.get()).isEqualTo(123);
    }

    @Test
    @DisplayName("ifValid should throw NullPointerException if consumer is null")
    void ifValidShouldThrowNullPointerExceptionIfConsumerIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> validInstance.ifValid(null))
          .withMessage(Valid.IF_VALID_CANNOT_BE_NULL_MSG);
    }

    @Test
    @DisplayName("ifInvalid should not perform action")
    void ifInvalidShouldNotPerformAction() {
      Consumer<String> consumer = err -> fail("Consumer should not be called for Valid");
      validInstance.ifInvalid(consumer);
      // No assertion needed if fail() is not called
    }

    @Test
    @DisplayName("ifInvalid should throw NullPointerException if consumer is null (eager check)")
    void ifInvalidShouldThrowNullPointerExceptionIfConsumerIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> validInstance.ifInvalid(null))
          .withMessage(Valid.IF_INVALID_CANNOT_BE_NULL_MSG);
    }
  }

  @Nested
  @DisplayName("Transformation Methods (map, flatMap, ap)")
  class TransformationMethods {
    private final Function<Integer, String> toStringFunc = Object::toString;
    private final Function<Integer, Validated<String, String>> toValidatedStringFunc =
        val -> Validated.valid("Mapped: " + val);

    // --- map ---
    @Test
    @DisplayName("map should transform the value and return a new Valid")
    void mapShouldTransformTheValueAndReturnANewValid() {
      Validated<String, String> mapped = validInstance.map(toStringFunc);
      assertThat(mapped.isValid()).isTrue();
      assertThat(mapped.get()).isEqualTo("123");
    }

    @Test
    @DisplayName("map should throw NullPointerException if function is null")
    void mapShouldThrowIfFunctionIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> validInstance.map(null))
          .withMessage(Valid.MAP_FN_CANNOT_BE_NULL_MSG);
    }

    @Test
    @DisplayName("map should throw NullPointerException if function returns null")
    void mapShouldThrowIfFunctionReturnsNull() {
      Function<Integer, String> nullReturningFunc = val -> null;
      assertThatNullPointerException()
          .isThrownBy(() -> validInstance.map(nullReturningFunc))
          .withMessage(Valid.MAP_FN_RETURNED_NULL_MSG);
    }

    // --- flatMap ---
    @Test
    @DisplayName("flatMap should apply function and return its Validated result")
    void flatMapShouldApplyFunctionAndReturnItsValidatedResult() {
      Validated<String, String> flatMapped = validInstance.flatMap(toValidatedStringFunc);
      assertThat(flatMapped.isValid()).isTrue();
      assertThat(flatMapped.get()).isEqualTo("Mapped: 123");
    }

    @Test
    @DisplayName("flatMap should propagate Invalid from the applied function")
    void flatMapShouldPropagateInvalidFromTheAppliedFunction() {
      Validated<String, String> errorResult = Validated.invalid("flatMap error");
      Function<Integer, Validated<String, String>> funcReturningInvalid = val -> errorResult;
      Validated<String, String> result = validInstance.flatMap(funcReturningInvalid);
      assertThat(result.isInvalid()).isTrue();
      assertThat(result.getError()).isEqualTo("flatMap error");
    }

    @Test
    @DisplayName("flatMap should throw NullPointerException if function is null")
    void flatMapShouldThrowIfFunctionIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> validInstance.flatMap(null))
          .withMessage(Valid.FLATMAP_FN_CANNOT_BE_NULL_MSG);
    }

    @Test
    @DisplayName("flatMap should throw NullPointerException if function returns null Validated")
    void flatMapShouldThrowIfFunctionReturnsNullValidated() {
      Function<Integer, Validated<String, String>> nullReturningFunc = val -> null;
      assertThatNullPointerException()
          .isThrownBy(() -> validInstance.flatMap(nullReturningFunc))
          .withMessage(Valid.FLATMAP_FN_RETURNED_NULL_MSG);
    }

    // --- ap ---
    @Test
    @DisplayName("ap with Valid function should apply function to value")
    void apWithValidFunctionShouldApplyFunctionToValue() {
      Validated<String, Function<Integer, String>> fnValidatedOriginal =
          Validated.valid(toStringFunc);
      // Explicitly map to the type expected by ap
      Validated<String, Function<? super Integer, ? extends String>> fnValidatedForAp =
          fnValidatedOriginal.map(f -> (Function<? super Integer, ? extends String>) f);

      Validated<String, String> result = validInstance.ap(fnValidatedForAp);
      assertThat(result.isValid()).isTrue();
      assertThat(result.get()).isEqualTo("123");
    }

    @Test
    @DisplayName("ap with Invalid function should propagate the function's error")
    void apWithInvalidFunctionShouldPropagateTheFunctionsError() {
      Validated<String, Function<Integer, String>> fnValidatedOriginal =
          Validated.invalid("Function Error");
      // Explicitly map to the type expected by ap
      Validated<String, Function<? super Integer, ? extends String>> fnValidatedForAp =
          fnValidatedOriginal.map(f -> (Function<? super Integer, ? extends String>) f);

      Validated<String, String> result = validInstance.ap(fnValidatedForAp);
      assertThat(result.isInvalid()).isTrue();
      assertThat(result.getError()).isEqualTo("Function Error");
    }

    @Test
    @DisplayName("ap should throw NullPointerException if fnValidated is null")
    void apShouldThrowIfFnValidatedIsNull() {
      // No need to map a null Validated, the null check is for fnValidated itself
      assertThatNullPointerException()
          .isThrownBy(() -> validInstance.ap(null))
          .withMessage(Valid.AP_FN_CANNOT_BE_NULL);
    }

    @Test
    @DisplayName("Creating Validated.valid(null function) should throw NPE from factory")
    void creatingValidatedValidWithNullFunctionShouldThrowNPE() {
      Function<Integer, String> nullFunction = null;
      assertThatNullPointerException()
          .isThrownBy(() -> Validated.valid(nullFunction))
          .withMessage(Validated.VALID_VALUE_CANNOT_BE_NULL_MSG);
    }

    @Test
    @DisplayName("ap should throw NullPointerException if Valid(function) returns null via map")
    void apShouldThrowIfValidFunctionReturnsNull() {
      Function<Integer, String> nullReturningFunc = val -> null;
      Validated<String, Function<Integer, String>> fnValidatedOriginal =
          Validated.valid(nullReturningFunc);
      // Explicitly map to the type expected by ap
      Validated<String, Function<? super Integer, ? extends String>> fnValidatedForAp =
          fnValidatedOriginal.map(f -> (Function<? super Integer, ? extends String>) f);

      // This triggers the null check for the result of f.apply(value) within this.map(f) inside ap
      assertThatNullPointerException()
          .isThrownBy(() -> validInstance.ap(fnValidatedForAp))
          .withMessage(Valid.MAP_FN_RETURNED_NULL_MSG);
    }
  }

  @Nested
  @DisplayName("toString Method")
  class ToStringMethod {
    @Test
    @DisplayName("toString should return correct format")
    void toStringShouldReturnCorrectFormat() {
      assertThat(validInstance.toString()).isEqualTo("Valid(123)");
      assertThat(validStringInstance.toString()).isEqualTo("Valid(Test)");
    }
  }

  @Nested
  @DisplayName("Record Properties")
  class RecordProperties {
    @Test
    @DisplayName("should have correct value via record accessor")
    void shouldHaveCorrectValueViaRecordAccessor() {
      assertThat(validInstance.value()).isEqualTo(123);
    }

    @Test
    @DisplayName("equals and hashCode should work as expected for records")
    void equalsAndHashCodeShouldWorkAsExpectedForRecords() {
      Valid<String, Integer> sameValidInstance = new Valid<>(123);
      Valid<String, Integer> differentValidInstance = new Valid<>(404);
      // For Valid, the error type E is phantom and does not affect equality if the value is the
      // same.
      Valid<Integer, Integer> differentErrorTypeValidSameValue = new Valid<>(123);

      assertThat(validInstance).isEqualTo(sameValidInstance);
      assertThat(validInstance).hasSameHashCodeAs(sameValidInstance);

      assertThat(validInstance).isNotEqualTo(differentValidInstance);
      assertThat(validInstance.hashCode()).isNotEqualTo(differentValidInstance.hashCode());

      // Standard record equality means if the component (value) is the same, they are equal,
      // regardless of erased generic type E.
      assertThat(validInstance).isEqualTo(differentErrorTypeValidSameValue);
      assertThat(validInstance.hashCode()).isEqualTo(differentErrorTypeValidSameValue.hashCode());
    }
  }
}
