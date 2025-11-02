// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.validated.ValidatedAssert.assertThatValidated;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Valid Complete Test Suite")
class ValidTest extends ValidatedTestBase {

  private Valid<String, Integer> validInstance;
  private Semigroup<String> semigroup;

  @BeforeEach
  void setUp() {
    validInstance = new Valid<>(DEFAULT_VALID_VALUE);
    semigroup = createDefaultSemigroup();
  }

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Valid validation tests")
    void runCompleteValidValidationTests() {
      Validated<String, Integer> anotherValid = Validated.valid(ALTERNATIVE_VALID_VALUE);

      CoreTypeTest.<String, Integer>validated(Validated.class)
          .withInvalid(validInstance)
          .withValid(anotherValid)
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
      Valid<String, Integer> valid = new Valid<>(DEFAULT_VALID_VALUE);

      assertThat(valid.value()).isEqualTo(DEFAULT_VALID_VALUE);
      assertThatValidated(valid)
          .isValid()
          .hasValue(DEFAULT_VALID_VALUE)
          .hasValueOfType(Integer.class);
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
      Validated<String, Integer> validated = Validated.valid(DEFAULT_VALID_VALUE);

      assertThatValidated(validated)
          .isValid()
          .hasValue(DEFAULT_VALID_VALUE)
          .hasValueOfType(Integer.class);
      assertThat(validated).isInstanceOf(Valid.class);
    }
  }

  @Nested
  @DisplayName("Query Operations")
  class QueryOperations {

    @Test
    @DisplayName("isValid returns true")
    void isValidReturnsTrue() {
      assertThat(validInstance.isValid()).isTrue();
      assertThatValidated(validInstance).isValid();
    }

    @Test
    @DisplayName("isInvalid returns false")
    void isInvalidReturnsFalse() {
      assertThat(validInstance.isInvalid()).isFalse();
    }

    @Test
    @DisplayName("get returns the encapsulated value")
    void getReturnsTheEncapsulatedValue() {
      assertThat(validInstance.get()).isEqualTo(DEFAULT_VALID_VALUE);
      assertThatValidated(validInstance).hasValue(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("getError throws NoSuchElementException")
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
    @DisplayName("orElse returns the original value")
    void orElseReturnsTheOriginalValue() {
      assertThat(validInstance.orElse(ALTERNATIVE_VALID_VALUE)).isEqualTo(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("orElse rejects null parameter")
    void orElseRejectsNullParameter() {
      assertThatThrownBy(() -> validInstance.orElse(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("orElse")
          .hasMessageContaining("other");
    }

    @Test
    @DisplayName("orElseGet returns the original value without invoking supplier")
    void orElseGetReturnsTheOriginalValueWithoutInvokingSupplier() {
      AtomicBoolean supplierInvoked = new AtomicBoolean(false);

      Integer result =
          validInstance.orElseGet(
              () -> {
                supplierInvoked.set(true);
                return ALTERNATIVE_VALID_VALUE;
              });

      assertThat(result).isEqualTo(DEFAULT_VALID_VALUE);
      assertThat(supplierInvoked).isFalse();
    }

    @Test
    @DisplayName("orElseGet validates supplier is non-null")
    void orElseGetValidatesSupplierIsNonNull() {
      assertThatThrownBy(() -> validInstance.orElseGet(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("otherSupplier")
          .hasMessageContaining("Valid")
          .hasMessageContaining("orElseGet");
    }

    @Test
    @DisplayName("orElseThrow returns the original value without invoking supplier")
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
    @DisplayName("orElseThrow validates supplier is non-null")
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
    @DisplayName("ifValid executes consumer with the value")
    void ifValidExecutesConsumerWithTheValue() {
      AtomicBoolean executed = new AtomicBoolean(false);

      validInstance.ifValid(
          v -> {
            assertThat(v).isEqualTo(DEFAULT_VALID_VALUE);
            executed.set(true);
          });

      assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("ifValid validates consumer is non-null")
    void ifValidValidatesConsumerIsNonNull() {
      assertThatThrownBy(() -> validInstance.ifValid(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("consumer")
          .hasMessageContaining("Valid")
          .hasMessageContaining("ifValid");
    }

    @Test
    @DisplayName("ifInvalid does not execute consumer")
    void ifInvalidDoesNotExecuteConsumer() {
      AtomicBoolean executed = new AtomicBoolean(false);

      validInstance.ifInvalid(e -> executed.set(true));

      assertThat(executed).isFalse();
    }

    @Test
    @DisplayName("ifInvalid validates consumer is non-null")
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
    @DisplayName("map transforms the value")
    void mapTransformsTheValue() {
      Validated<String, String> result = validInstance.map(Object::toString);

      assertThatValidated(result).isValid().hasValue("42").hasValueOfType(String.class);
    }

    @Test
    @DisplayName("map validates mapper is non-null")
    void mapValidatesMapperIsNonNull() {
      assertThatThrownBy(() -> validInstance.map(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Function fn for Valid.map cannot be null");
    }

    @Test
    @DisplayName("map validates mapper result is non-null")
    void mapValidatesMapperResultIsNonNull() {
      Function<Integer, String> nullReturningMapper = i -> null;

      assertThatThrownBy(() -> validInstance.map(nullReturningMapper))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Function fn in Valid.map returned null, which is not allowed");
    }

    @Test
    @DisplayName("flatMap chains computations")
    void flatMapChainsComputations() {
      Validated<String, String> result = validInstance.flatMap(i -> Validated.valid(i.toString()));

      assertThatValidated(result).isValid().hasValue("42");
    }

    @Test
    @DisplayName("flatMap can produce Invalid")
    void flatMapCanProduceInvalid() {
      Validated<String, String> result =
          validInstance.flatMap(i -> Validated.invalid("computed error"));

      assertThatValidated(result).isInvalid().hasError("computed error");
    }

    @Test
    @DisplayName("flatMap validates mapper is non-null")
    void flatMapValidatesMapperIsNonNull() {
      assertThatThrownBy(() -> validInstance.flatMap(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("fn")
          .hasMessageContaining("Valid")
          .hasMessageContaining("flatMap");
    }

    @Test
    @DisplayName("flatMap validates mapper result is non-null")
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
    @DisplayName("ap applies Valid function to Valid value")
    void apAppliesValidFunctionToValidValue() {
      Validated<String, Function<? super Integer, ? extends String>> fnValidated =
          Validated.valid(Object::toString);

      Validated<String, String> result = validInstance.ap(fnValidated, semigroup);

      assertThatValidated(result).isValid().hasValue("42");
    }

    @Test
    @DisplayName("ap propagates Invalid function")
    void apPropagatesInvalidFunction() {
      Validated<String, Function<? super Integer, ? extends String>> fnValidated =
          Validated.invalid("function error");

      Validated<String, String> result = validInstance.ap(fnValidated, semigroup);

      assertThatValidated(result).isInvalid().hasError("function error");
    }

    @Test
    @DisplayName("ap validates function is non-null")
    void apValidatesFunctionIsNonNull() {
      assertThatThrownBy(() -> validInstance.ap(null, semigroup))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("fnValidated")
          .hasMessageContaining("Validated")
          .hasMessageContaining("ap");
    }

    @Test
    @DisplayName("ap validates semigroup is non-null")
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
    @DisplayName("fold applies valid mapper")
    void foldAppliesValidMapper() {
      String result = validInstance.fold(error -> "Error: " + error, value -> "Value: " + value);

      assertThat(result).isEqualTo("Value: 42");
    }

    @Test
    @DisplayName("fold validates invalid mapper is non-null")
    void foldValidatesInvalidMapperIsNonNull() {
      assertThatThrownBy(() -> validInstance.fold(null, v -> "valid"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("invalidMapper")
          .hasMessageContaining("Validated")
          .hasMessageContaining("fold");
    }

    @Test
    @DisplayName("fold validates valid mapper is non-null")
    void foldValidatesValidMapperIsNonNull() {
      assertThatThrownBy(() -> validInstance.fold(e -> "invalid", null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("validMapper")
          .hasMessageContaining("Validated")
          .hasMessageContaining("fold");
    }
  }

  @Nested
  @DisplayName("Conversion Operations")
  class ConversionOperations {

    @Test
    @DisplayName("toEither converts Valid to Right")
    void toEitherConvertsValidToRight() {
      Either<String, Integer> either = validInstance.toEither();

      assertThat(either.isRight()).isTrue();
      assertThat(either.isLeft()).isFalse();
      assertThat(either.getRight()).isEqualTo(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("asUnit replaces value with Unit")
    void asUnitReplacesValueWithUnit() {
      Validated<String, Unit> result = validInstance.asUnit();

      assertThatValidated(result).isValid().hasValue(Unit.INSTANCE).hasValueOfType(Unit.class);
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("toString produces readable output")
    void toStringProducesReadableOutput() {
      assertThat(validInstance.toString()).isEqualTo("Valid(42)");
    }

    @Test
    @DisplayName("equals compares values correctly")
    void equalsComparesValuesCorrectly() {
      Valid<String, Integer> same = new Valid<>(DEFAULT_VALID_VALUE);
      Valid<String, Integer> different = new Valid<>(ALTERNATIVE_VALID_VALUE);

      assertThatValidated(validInstance).isEqualTo(same);
      assertThatValidated(validInstance).isNotEqualTo(different);
      assertThat(validInstance).isNotEqualTo(null);
      assertThat(validInstance).isNotEqualTo("not a Valid");
    }

    @Test
    @DisplayName("hashCode is consistent")
    void hashCodeIsConsistent() {
      Valid<String, Integer> same = new Valid<>(DEFAULT_VALID_VALUE);

      assertThat(validInstance.hashCode()).isEqualTo(same.hashCode());
    }

    @Test
    @DisplayName("record accessor returns value")
    void recordAccessorReturnsValue() {
      assertThat(validInstance.value()).isEqualTo(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("Valid with different value types")
    void validWithDifferentValueTypes() {
      Valid<String, String> stringValid = new Valid<>("hello");

      assertThatValidated(stringValid).isValid().hasValue("hello").hasValueOfType(String.class);
    }

    @Test
    @DisplayName("map type changes correctly")
    void mapTypeChangesCorrectly() {
      Validated<String, Boolean> boolResult = validInstance.map(i -> i > 0);

      assertThatValidated(boolResult).isValid().hasValue(true).hasValueOfType(Boolean.class);
    }

    @Test
    @DisplayName("flatMap type changes correctly")
    void flatMapTypeChangesCorrectly() {
      Validated<String, Boolean> boolResult = validInstance.flatMap(i -> Validated.valid(i > 0));

      assertThatValidated(boolResult).isValid().hasValue(true).hasValueOfType(Boolean.class);
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("chaining operations transforms value correctly")
    void chainingOperationsTransformsValueCorrectly() {
      Validated<String, String> result =
          validInstance
              .map(i -> i * 2)
              .flatMap(i -> Validated.valid(i.toString()))
              .map(s -> s + "!");

      assertThatValidated(result).isValid().hasValue("84!");
    }

    @Test
    @DisplayName("ap with multiple Valid values")
    void apWithMultipleValidValues() {
      Validated<String, Integer> val1 = Validated.valid(10);
      Validated<String, Integer> val2 = Validated.valid(20);

      Function<Integer, Function<Integer, Integer>> addFunc = a -> b -> a + b;

      Validated<String, Function<? super Integer, ? extends Integer>> fnValidated =
          val1.map(addFunc);

      Validated<String, Integer> result = val2.ap(fnValidated, semigroup);

      assertThatValidated(result).isValid().hasValue(30);
    }

    @Test
    @DisplayName("fold with type conversion")
    void foldWithTypeConversion() {
      record SuccessInfo(int value, String status) {}

      SuccessInfo result =
          validInstance.fold(
              error -> new SuccessInfo(0, "Error: " + error),
              value -> new SuccessInfo(value, "Success"));

      assertThat(result.value()).isEqualTo(DEFAULT_VALID_VALUE);
      assertThat(result.status()).isEqualTo("Success");
    }

    @Test
    @DisplayName("complex transformation pipeline")
    void complexTransformationPipeline() {
      Validated<String, String> result =
          validInstance
              .map(i -> i * 2)
              .flatMap(i -> i < 100 ? Validated.valid(i) : Validated.invalid("Too large"))
              .map(i -> i + 10)
              .flatMap(i -> Validated.valid("Result: " + i));

      assertThatValidated(result).isValid().hasValue("Result: 94");
    }
  }
}
