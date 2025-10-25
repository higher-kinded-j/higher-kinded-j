// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.validated.ValidatedAssert.assertThatValidated;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Valid Complete Test Suite")
class ValidTest {

  private Valid<String, Integer> validInstance;
  private Semigroup<String> semigroup;

  @BeforeEach
  void setUp() {
    validInstance = new Valid<>(42);
    semigroup = Semigroups.string(",");
  }

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Valid validation tests")
    void runCompleteValidValidationTests() {
      Validated<String, Integer> anotherValid = Validated.valid(24);

      CoreTypeTest.<String, Integer>validated(Validated.class)
          .withInvalid(validInstance) // Use Valid instance
          .withValid(anotherValid) // Use another Valid instance
          .withMappers(Object::toString)
          .configureValidation()
          .withValidatedInheritanceValidation()
          .withMapFrom(Valid.class)
          .withFlatMapFrom(Valid.class)
          .withIfValidFrom(Valid.class)
          .withIfInvalidFrom(Valid.class)
          .testValidations();
    }
  }

  @Nested
  @DisplayName("Construction Tests")
  class ConstructionTests {

    @Test
    @DisplayName("Valid creation succeeds with non-null value")
    void validCreationSucceedsWithNonNullValue() {
      Valid<String, Integer> valid = new Valid<>(42);
      assertThat(valid.value()).isEqualTo(42);
    }

    @Test
    @DisplayName("Valid creation rejects null value")
    void validCreationRejectsNullValue() {
      assertThatThrownBy(() -> new Valid<String, Integer>(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("value")
          .hasMessageContaining("Valid")
          .hasMessageContaining("construction");
    }

    @Test
    @DisplayName("Factory method creates Valid instance")
    void factoryMethodCreatesValidInstance() {
      Validated<String, Integer> validated = Validated.valid(42);

      assertThatValidated(validated).isValid().hasValue(42).hasValueOfType(Integer.class);
    }
  }

  @Nested
  @DisplayName("Query Operations")
  class QueryOperations {

    @Test
    @DisplayName("IsValid returns true")
    void isValidReturnsTrue() {
      assertThatValidated(validInstance).isValid();
    }

    @Test
    @DisplayName("IsInvalid returns false")
    void isInvalidReturnsFalse() {
      assertThat(validInstance.isInvalid()).isFalse();
    }

    @Test
    @DisplayName("Get returns the encapsulated value")
    void getReturnsTheEncapsulatedValue() {
      assertThatValidated(validInstance).hasValue(42);
    }

    @Test
    @DisplayName("GetError throws NoSuchElementException")
    void getErrorThrowsNoSuchElementException() {
      assertThatThrownBy(() -> validInstance.getError())
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot getError() from a Valid instance");
    }
  }

  @Nested
  @DisplayName("OrElse Operations")
  class OrElseOperations {

    @Test
    @DisplayName("OrElse returns the original value")
    void orElseReturnsTheOriginalValue() {
      assertThat(validInstance.orElse(100)).isEqualTo(42);
    }

    @Test
    @DisplayName("OrElse rejects null parameter")
    void orElseRejectsNullParameter() {
      assertThatThrownBy(() -> validInstance.orElse(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("orElse")
          .hasMessageContaining("other");
    }

    @Test
    @DisplayName("OrElseGet returns the original value without invoking supplier")
    void orElseGetReturnsTheOriginalValueWithoutInvokingSupplier() {
      AtomicBoolean supplierInvoked = new AtomicBoolean(false);

      Integer result =
          validInstance.orElseGet(
              () -> {
                supplierInvoked.set(true);
                return 100;
              });

      assertThat(result).isEqualTo(42);
      assertThat(supplierInvoked).isFalse();
    }

    @Test
    @DisplayName("OrElseGet validates supplier is non-null")
    void orElseGetValidatesSupplierIsNonNull() {
      assertThatThrownBy(() -> validInstance.orElseGet(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("otherSupplier")
          .hasMessageContaining("Valid")
          .hasMessageContaining("orElseGet");
    }

    @Test
    @DisplayName("OrElseThrow returns the original value without invoking supplier")
    void orElseThrowReturnsTheOriginalValueWithoutInvokingSupplier() {
      AtomicBoolean supplierInvoked = new AtomicBoolean(false);

      assertThatCode(
              () ->
                  validInstance.orElseThrow(
                      () -> {
                        supplierInvoked.set(true);
                        return new RuntimeException("Should not be thrown");
                      }))
          .doesNotThrowAnyException();

      assertThat(supplierInvoked).isFalse();
    }

    @Test
    @DisplayName("OrElseThrow validates supplier is non-null")
    void orElseThrowValidatesSupplierIsNonNull() {
      assertThatThrownBy(() -> validInstance.orElseThrow(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("exceptionSupplier")
          .hasMessageContaining("Valid")
          .hasMessageContaining("orElseThrow");
    }
  }

  @Nested
  @DisplayName("Side Effect Operations")
  class SideEffectOperations {

    @Test
    @DisplayName("IfValid executes consumer with the value")
    void ifValidExecutesConsumerWithTheValue() {
      AtomicBoolean executed = new AtomicBoolean(false);

      validInstance.ifValid(
          v -> {
            assertThat(v).isEqualTo(42);
            executed.set(true);
          });

      assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("IfValid validates consumer is non-null")
    void ifValidValidatesConsumerIsNonNull() {
      assertThatThrownBy(() -> validInstance.ifValid(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("consumer")
          .hasMessageContaining("Valid")
          .hasMessageContaining("ifValid");
    }

    @Test
    @DisplayName("IfInvalid does not execute consumer")
    void ifInvalidDoesNotExecuteConsumer() {
      AtomicBoolean executed = new AtomicBoolean(false);

      validInstance.ifInvalid(e -> executed.set(true));

      assertThat(executed).isFalse();
    }

    @Test
    @DisplayName("IfInvalid validates consumer is non-null")
    void ifInvalidValidatesConsumerIsNonNull() {
      assertThatThrownBy(() -> validInstance.ifInvalid(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("consumer")
          .hasMessageContaining("Valid")
          .hasMessageContaining("ifInvalid");
    }
  }

  @Nested
  @DisplayName("Transformation Operations")
  class TransformationOperations {

    @Test
    @DisplayName("Map transforms the value")
    void mapTransformsTheValue() {
      Validated<String, String> result = validInstance.map(Object::toString);

      assertThatValidated(result).isValid().hasValue("42").hasValueOfType(String.class);
    }

    @Test
    @DisplayName("Map validates mapper is non-null")
    void mapValidatesMapperIsNonNull() {
      assertThatThrownBy(() -> validInstance.map(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function fn for Valid.map cannot be null");
    }

    @Test
    @DisplayName("Map validates mapper result is non-null")
    void mapValidatesMapperResultIsNonNull() {
      Function<Integer, String> nullReturningMapper = i -> null;

      assertThatThrownBy(() -> validInstance.map(nullReturningMapper))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Function fn in Valid.map returned null, which is not allowed");
    }

    @Test
    @DisplayName("FlatMap chains computations")
    void flatMapChainsComputations() {
      Validated<String, String> result = validInstance.flatMap(i -> Validated.valid(i.toString()));

      assertThatValidated(result).isValid().hasValue("42");
    }

    @Test
    @DisplayName("FlatMap can produce Invalid")
    void flatMapCanProduceInvalid() {
      Validated<String, String> result =
          validInstance.flatMap(i -> Validated.invalid("computed error"));

      assertThatValidated(result).isInvalid().hasError("computed error");
    }

    @Test
    @DisplayName("FlatMap validates mapper is non-null")
    void flatMapValidatesMapperIsNonNull() {
      assertThatThrownBy(() -> validInstance.flatMap(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("fn")
          .hasMessageContaining("Valid")
          .hasMessageContaining("flatMap");
    }

    @Test
    @DisplayName("FlatMap validates mapper result is non-null")
    void flatMapValidatesMapperResultIsNonNull() {
      Function<Integer, Validated<String, String>> nullReturningMapper = i -> null;

      assertThatThrownBy(() -> validInstance.flatMap(nullReturningMapper))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              "Function fn in Valid.flatMap returned null when Validated expected, which is not"
                  + " allowed");
    }
  }

  @Nested
  @DisplayName("Ap Operations")
  class ApOperations {

    @Test
    @DisplayName("Ap applies Valid function to Valid value")
    void apAppliesValidFunctionToValidValue() {
      Validated<String, Function<? super Integer, ? extends String>> fnValidated =
          Validated.valid(Object::toString);

      Validated<String, String> result = validInstance.ap(fnValidated, semigroup);

      assertThatValidated(result).isValid().hasValue("42");
    }

    @Test
    @DisplayName("Ap propagates Invalid function")
    void apPropagatesInvalidFunction() {
      Validated<String, Function<? super Integer, ? extends String>> fnValidated =
          Validated.invalid("function error");

      Validated<String, String> result = validInstance.ap(fnValidated, semigroup);

      assertThatValidated(result).isInvalid().hasError("function error");
    }

    @Test
    @DisplayName("Ap validates function is non-null")
    void apValidatesFunctionIsNonNull() {
      assertThatThrownBy(() -> validInstance.ap(null, semigroup))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("fnValidated")
          .hasMessageContaining("Validated")
          .hasMessageContaining("ap");
    }

    @Test
    @DisplayName("Ap validates semigroup is non-null")
    void apValidatesSemigroupIsNonNull() {
      Validated<String, Function<? super Integer, ? extends String>> fnValidated =
          Validated.valid(Object::toString);

      assertThatThrownBy(() -> validInstance.ap(fnValidated, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("semigroup")
          .hasMessageContaining("Valid")
          .hasMessageContaining("ap");
    }
  }

  @Nested
  @DisplayName("Fold Operations")
  class FoldOperations {

    @Test
    @DisplayName("Fold applies valid mapper")
    void foldAppliesValidMapper() {
      String result = validInstance.fold(error -> "Error: " + error, value -> "Value: " + value);

      assertThat(result).isEqualTo("Value: 42");
    }

    @Test
    @DisplayName("Fold validates invalid mapper is non-null")
    void foldValidatesInvalidMapperIsNonNull() {
      assertThatThrownBy(() -> validInstance.fold(null, v -> "valid"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("invalidMapper")
          .hasMessageContaining("Validated")
          .hasMessageContaining("fold");
    }

    @Test
    @DisplayName("Fold validates valid mapper is non-null")
    void foldValidatesValidMapperIsNonNull() {
      assertThatThrownBy(() -> validInstance.fold(e -> "invalid", null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("validMapper")
          .hasMessageContaining("Validated")
          .hasMessageContaining("fold");
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("ToString produces readable output")
    void toStringProducesReadableOutput() {
      assertThat(validInstance.toString()).isEqualTo("Valid(42)");
    }

    @Test
    @DisplayName("Equals compares values correctly")
    void equalsComparesValuesCorrectly() {
      Valid<String, Integer> same = new Valid<>(42);
      Valid<String, Integer> different = new Valid<>(43);

      assertThatValidated(validInstance).isEqualTo(same);
      assertThatValidated(validInstance).isNotEqualTo(different);
      assertThat(validInstance).isNotEqualTo(null);
      assertThat(validInstance).isNotEqualTo("not a Valid");
    }

    @Test
    @DisplayName("HashCode is consistent")
    void hashCodeIsConsistent() {
      Valid<String, Integer> same = new Valid<>(42);

      assertThat(validInstance.hashCode()).isEqualTo(same.hashCode());
    }

    @Test
    @DisplayName("Record accessor returns value")
    void recordAccessorReturnsValue() {
      assertThat(validInstance.value()).isEqualTo(42);
    }
  }
}
