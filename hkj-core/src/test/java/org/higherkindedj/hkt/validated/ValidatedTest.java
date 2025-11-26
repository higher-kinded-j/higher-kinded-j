// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.validated.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Validated Complete Test Suite")
class ValidatedTest extends ValidatedTestBase {

  private ValidatedMonad<String> monad;
  private Semigroup<String> semigroup;

  // Instance fields for test data
  private Validated<String, Integer> validInstance;
  private Validated<String, Integer> invalidInstance;
  private static final String DEFAULT_STRING_VALUE = "test-string";

  @BeforeEach
  void setUpValidated() {
    semigroup = createDefaultSemigroup();
    monad = ValidatedMonad.instance(semigroup);

    // Initialise instance fields
    validInstance = Validated.valid(DEFAULT_VALID_VALUE);
    invalidInstance = Validated.invalid(DEFAULT_ERROR);
  }

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      TypeClassTest.<ValidatedKind.Witness<String>>monad(ValidatedMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(ValidatedMonad.class)
          .withApFrom(ValidatedMonad.class)
          .withFlatMapFrom(ValidatedMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Functor test pattern")
    void runCompleteFunctorTestPattern() {
      TypeClassTest.<ValidatedKind.Witness<String>>functor(ValidatedMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Validated core type tests")
    void runCompleteValidatedCoreTypeTests() {
      CoreTypeTest.<String, Integer>validated(Validated.class)
          .withInvalid(invalidInstance)
          .withValid(validInstance)
          .withMappers(validMapper)
          .configureValidation()
          .withValidatedInheritanceValidation()
          .withMapFrom(ValidatedMonad.class)
          .withFlatMapFrom(ValidatedMonad.class)
          .withIfValidFrom(Invalid.class)
          .withIfInvalidFrom(Invalid.class)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Validated Selective core type tests - operations only")
    void runCompleteValidatedSelectiveCoreTypeTestsOperationsOnly() {
      // Create Choice instances for Selective testing - use non-null values
      Choice<Integer, String> choiceLeft = Selective.left(testValue);
      Choice<Integer, String> choiceRight = Selective.right(DEFAULT_STRING_VALUE);

      Validated<String, Choice<Integer, String>> validatedChoiceLeft = Validated.valid(choiceLeft);
      Validated<String, Choice<Integer, String>> validatedChoiceRight =
          Validated.valid(choiceRight);
      Validated<String, Boolean> validatedTrue = Validated.valid(true);
      Validated<String, Boolean> validatedFalse = Validated.valid(false);

      CoreTypeTest.<String, Integer>validated(Validated.class)
          .withInvalid(invalidInstance)
          .withValid(validInstance)
          .withMappers(validMapper)
          .withSelectiveOperations(
              validatedChoiceLeft, validatedChoiceRight, validatedTrue, validatedFalse)
          .withHandlers(i -> "selected:" + i, i -> "left:" + i, s -> "right:" + s)
          .configureValidation()
          .useSelectiveInheritanceValidation()
          .withSelectFrom(ValidatedSelective.class)
          .withBranchFrom(ValidatedSelective.class)
          .withWhenSFrom(ValidatedSelective.class)
          .withIfSFrom(ValidatedSelective.class)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Validated Selective core type tests - with validation")
    void runCompleteValidatedSelectiveCoreTypeTestsWithValidation() {
      // Create Choice instances for Selective testing - use non-null values
      Choice<Integer, String> choiceLeft = Selective.left(testValue);
      Choice<Integer, String> choiceRight = Selective.right(DEFAULT_STRING_VALUE);

      Validated<String, Choice<Integer, String>> validatedChoiceLeft = Validated.valid(choiceLeft);
      Validated<String, Choice<Integer, String>> validatedChoiceRight =
          Validated.valid(choiceRight);
      Validated<String, Boolean> validatedTrue = Validated.valid(true);
      Validated<String, Boolean> validatedFalse = Validated.valid(false);

      CoreTypeTest.<String, Integer>validated(Validated.class)
          .withInvalid(invalidInstance)
          .withValid(validInstance)
          .withMappers(validMapper)
          .withSelectiveOperations(
              validatedChoiceLeft, validatedChoiceRight, validatedTrue, validatedFalse)
          .withHandlers(i -> "selected:" + i, i -> "left:" + i, s -> "right:" + s)
          .configureValidation()
          .useSelectiveInheritanceValidation()
          .withSelectFrom(ValidatedSelective.class)
          .withBranchFrom(ValidatedSelective.class)
          .withWhenSFrom(ValidatedSelective.class)
          .withIfSFrom(ValidatedSelective.class)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Individual Type Class Components")
  class IndividualTypeClassComponents {

    @Test
    @DisplayName("Test Functor operations only")
    void testFunctorOperationsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>functor(ValidatedMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .testOperations();
    }

    @Test
    @DisplayName("Test Functor validations only")
    void testFunctorValidationsOnly() {
      CoreTypeTest.<String, Integer>validated(Validated.class)
          .withInvalid(invalidInstance)
          .withValid(validInstance)
          .withMappers(validMapper)
          .configureValidation()
          .withValidatedInheritanceValidation()
          .withMapFrom(ValidatedMonad.class)
          .withFlatMapFrom(ValidatedMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test Functor exception propagation only")
    void testFunctorExceptionPropagationOnly() {
      RuntimeException testException = new RuntimeException("Test exception: functor test");
      Function<Integer, String> throwingMapper =
          i -> {
            throw testException;
          };

      Kind<ValidatedKind.Witness<String>, Integer> validKindInstance =
          VALIDATED.widen(validInstance);
      assertThatThrownBy(() -> monad.map(throwingMapper, validKindInstance))
          .isSameAs(testException);

      Kind<ValidatedKind.Witness<String>, Integer> invalidKindInstance =
          VALIDATED.widen(invalidInstance);
      assertThatCode(() -> monad.map(throwingMapper, invalidKindInstance))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Test Functor laws only")
    void testFunctorLawsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>functor(ValidatedMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
    }

    @Test
    @DisplayName("Test Monad operations only")
    void testMonadOperationsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>monad(ValidatedMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test Monad validations only with full hierarchy")
    void testMonadValidationsOnly() {
      CoreTypeTest.<String, Integer>validated(Validated.class)
          .withInvalid(invalidInstance)
          .withValid(validInstance)
          .withMappers(validMapper)
          .configureValidation()
          .withValidatedInheritanceValidation()
          .withMapFrom(ValidatedMonad.class)
          .withFlatMapFrom(ValidatedMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test Monad exception propagation only")
    void testMonadExceptionPropagationOnly() {
      RuntimeException testException = new RuntimeException("Test exception: monad test");
      Function<Integer, Kind<ValidatedKind.Witness<String>, String>> throwingFlatMapper =
          i -> {
            throw testException;
          };

      Kind<ValidatedKind.Witness<String>, Integer> validKindInstance =
          VALIDATED.widen(validInstance);
      assertThatThrownBy(() -> monad.flatMap(throwingFlatMapper, validKindInstance))
          .isSameAs(testException);

      Kind<ValidatedKind.Witness<String>, Integer> invalidKindInstance =
          VALIDATED.widen(invalidInstance);
      assertThatCode(() -> monad.flatMap(throwingFlatMapper, invalidKindInstance))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Test Monad laws only")
    void testMonadLawsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>monad(ValidatedMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
    }

    @Test
    @DisplayName("Test Selective operations only")
    void testSelectiveOperationsOnly() {
      // Create Choice instances for Selective testing - use non-null values
      Choice<Integer, String> choiceLeft = Selective.left(testValue);
      Choice<Integer, String> choiceRight = Selective.right(DEFAULT_STRING_VALUE);

      Validated<String, Choice<Integer, String>> validatedChoiceLeft = Validated.valid(choiceLeft);
      Validated<String, Choice<Integer, String>> validatedChoiceRight =
          Validated.valid(choiceRight);
      Validated<String, Boolean> validatedTrue = Validated.valid(true);
      Validated<String, Boolean> validatedFalse = Validated.valid(false);

      CoreTypeTest.<String, Integer>validated(Validated.class)
          .withInvalid(invalidInstance)
          .withValid(validInstance)
          .withMappers(validMapper)
          .withSelectiveOperations(
              validatedChoiceLeft, validatedChoiceRight, validatedTrue, validatedFalse)
          .withHandlers(i -> "selected:" + i, i -> "left:" + i, s -> "right:" + s)
          .testOperations();
    }

    @Test
    @DisplayName("Test Selective validations only")
    void testSelectiveValidationsOnly() {
      // Create Choice instances for Selective testing - use non-null values
      Choice<Integer, String> choiceLeft = Selective.left(testValue);
      Choice<Integer, String> choiceRight = Selective.right(DEFAULT_STRING_VALUE);

      Validated<String, Choice<Integer, String>> validatedChoiceLeft = Validated.valid(choiceLeft);
      Validated<String, Choice<Integer, String>> validatedChoiceRight =
          Validated.valid(choiceRight);
      Validated<String, Boolean> validatedTrue = Validated.valid(true);
      Validated<String, Boolean> validatedFalse = Validated.valid(false);

      CoreTypeTest.<String, Integer>validated(Validated.class)
          .withInvalid(invalidInstance)
          .withValid(validInstance)
          .withMappers(validMapper)
          .withSelectiveOperations(
              validatedChoiceLeft, validatedChoiceRight, validatedTrue, validatedFalse)
          .withHandlers(i -> "selected:" + i, i -> "left:" + i, s -> "right:" + s)
          .configureValidation()
          .useSelectiveInheritanceValidation()
          .withSelectFrom(ValidatedSelective.class)
          .withBranchFrom(ValidatedSelective.class)
          .withWhenSFrom(ValidatedSelective.class)
          .withIfSFrom(ValidatedSelective.class)
          .testValidations();
    }
  }

  @Nested
  @DisplayName("Factory Methods - Complete Coverage")
  class FactoryMethodTests {

    @Test
    @DisplayName("valid() creates correct Valid instances with all value types")
    void validCreatesCorrectInstances() {
      assertThatValidated(validInstance)
          .isValid()
          .hasValue(DEFAULT_VALID_VALUE)
          .hasValueOfType(Integer.class);

      List<String> list = List.of("a", "b", "c");
      Validated<String, List<String>> listValid = Validated.valid(list);
      assertThatValidated(listValid)
          .isValid()
          .hasValueSatisfying(l -> l == list, "value is same list");

      Validated<String, Boolean> boolValid = Validated.valid(true);
      assertThatValidated(boolValid).isValid().hasValue(true);
    }

    @Test
    @DisplayName("invalid() creates correct Invalid instances with all error types")
    void invalidCreatesCorrectInstances() {
      assertThatValidated(invalidInstance)
          .isInvalid()
          .hasError(DEFAULT_ERROR)
          .hasErrorOfType(String.class);

      Exception exception = new RuntimeException("test");
      Validated<Exception, String> exceptionInvalid = Validated.invalid(exception);
      assertThatValidated(exceptionInvalid).isInvalid().hasError(exception);

      Validated<String, Integer> emptyInvalid = Validated.invalid("");
      assertThatValidated(emptyInvalid).isInvalid().hasError("");
    }

    @Test
    @DisplayName("valid() validates non-null requirement")
    void validValidatesNonNull() {
      assertThatThrownBy(() -> Validated.<String, Integer>valid(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("value")
          .hasMessageContaining("Validated");
    }

    @Test
    @DisplayName("invalid() validates non-null requirement")
    void invalidValidatesNonNull() {
      assertThatThrownBy(() -> Validated.<String, Integer>invalid(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("error")
          .hasMessageContaining("Validated");
    }

    @Test
    @DisplayName("Factory methods type inference works correctly")
    void factoryMethodsTypeInference() {
      var stringInvalid = Validated.invalid("error");
      var intValid = Validated.valid(42);

      Validated<String, Object> invalidAssignment = stringInvalid;
      Validated<Object, Integer> validAssignment = intValid;

      assertThatValidated(invalidAssignment).isInvalid().hasError("error");
      assertThatValidated(validAssignment).isValid().hasValue(42);
    }
  }

  @Nested
  @DisplayName("ValidateThat Static Methods")
  class ValidateThatStaticMethods {

    @Test
    @DisplayName("validateThat returns Valid when condition is true")
    void validateThatReturnsValidWhenConditionIsTrue() {
      Validated<String, Unit> result = Validated.validateThat(true, DEFAULT_ERROR);

      assertThatValidated(result).isValid().hasValue(Unit.INSTANCE).hasValueOfType(Unit.class);
    }

    @Test
    @DisplayName("validateThat returns Invalid when condition is false")
    void validateThatReturnsInvalidWhenConditionIsFalse() {
      Validated<String, Unit> result = Validated.validateThat(false, DEFAULT_ERROR);

      assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR).hasErrorOfType(String.class);
    }

    @Test
    @DisplayName("validateThat rejects null error")
    void validateThatRejectsNullError() {
      assertThatThrownBy(() -> Validated.validateThat(false, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("errorSupplier cannot be null");
    }

    @Test
    @DisplayName("validateThat with supplier returns Valid when condition is true")
    void validateThatWithSupplierReturnsValidWhenConditionIsTrue() {
      Supplier<String> errorSupplier =
          () -> {
            throw new AssertionError("Should not be called");
          };

      Validated<String, Unit> result = Validated.validateThat(true, errorSupplier);

      assertThatValidated(result).isValid().hasValue(Unit.INSTANCE);
    }

    @Test
    @DisplayName("validateThat with supplier returns Invalid when condition is false")
    void validateThatWithSupplierReturnsInvalidWhenConditionIsFalse() {
      Supplier<String> errorSupplier = () -> "lazy-error";

      Validated<String, Unit> result = Validated.validateThat(false, errorSupplier);

      assertThatValidated(result).isInvalid().hasError("lazy-error");
    }

    @Test
    @DisplayName("validateThat with supplier rejects null supplier")
    void validateThatWithSupplierRejectsNullSupplier() {
      assertThatThrownBy(() -> Validated.validateThat(false, (Supplier<String>) null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("errorSupplier cannot be null");
    }

    @Test
    @DisplayName("validateThat with supplier evaluates error lazily")
    void validateThatWithSupplierEvaluatesErrorLazily() {
      AtomicInteger counter = new AtomicInteger(0);

      Supplier<String> errorSupplier =
          () -> {
            counter.incrementAndGet();
            return "error";
          };

      Validated.validateThat(true, errorSupplier);
      assertThat(counter.get()).isEqualTo(0);

      Validated.validateThat(false, errorSupplier);
      assertThat(counter.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("AsUnit Method Tests")
  class AsUnitMethodTests {

    @Test
    @DisplayName("asUnit converts Valid to Valid Unit")
    void asUnitConvertsValidToValidUnit() {
      Validated<String, Integer> valid = Validated.valid(DEFAULT_VALID_VALUE);

      Validated<String, Unit> result = valid.asUnit();

      assertThatValidated(result).isValid().hasValue(Unit.INSTANCE).hasValueOfType(Unit.class);
    }

    @Test
    @DisplayName("asUnit preserves Invalid")
    void asUnitPreservesInvalid() {
      Validated<String, Integer> invalid = Validated.invalid(DEFAULT_ERROR);

      Validated<String, Unit> result = invalid.asUnit();

      assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR).hasErrorOfType(String.class);
    }

    @Test
    @DisplayName("asUnit is idempotent")
    void asUnitIsIdempotent() {
      Validated<String, Integer> valid = Validated.valid(DEFAULT_VALID_VALUE);

      Validated<String, Unit> result1 = valid.asUnit();
      Validated<String, Unit> result2 = result1.asUnit();

      assertThatValidated(result1).isEqualTo(result2);
    }
  }

  @Nested
  @DisplayName("Fold Operations")
  class FoldOperations {

    @Test
    @DisplayName("fold applies valid mapper on Valid")
    void foldAppliesValidMapperOnValid() {
      Validated<String, Integer> valid = Validated.valid(DEFAULT_VALID_VALUE);

      String result = valid.fold(error -> "Error: " + error, value -> "Value: " + value);

      assertThat(result).isEqualTo("Value: 42");
    }

    @Test
    @DisplayName("fold applies invalid mapper on Invalid")
    void foldAppliesInvalidMapperOnInvalid() {
      Validated<String, Integer> invalid = Validated.invalid(DEFAULT_ERROR);

      String result = invalid.fold(error -> "Error: " + error, value -> "Value: " + value);

      assertThat(result).isEqualTo("Error: " + DEFAULT_ERROR);
    }

    @Test
    @DisplayName("fold validates invalid mapper is non-null")
    void foldValidatesInvalidMapperIsNonNull() {
      Validated<String, Integer> valid = Validated.valid(DEFAULT_VALID_VALUE);

      assertThatThrownBy(() -> valid.fold(null, v -> "valid"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("invalidMapper")
          .hasMessageContaining("Validated")
          .hasMessageContaining("fold");
    }

    @Test
    @DisplayName("fold validates valid mapper is non-null")
    void foldValidatesValidMapperIsNonNull() {
      Validated<String, Integer> valid = Validated.valid(DEFAULT_VALID_VALUE);

      assertThatThrownBy(() -> valid.fold(e -> "invalid", null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("validMapper")
          .hasMessageContaining("Validated")
          .hasMessageContaining("fold");
    }
  }

  @Nested
  @DisplayName("toEither Conversion Tests")
  class ToEitherConversionTests {

    @Test
    @DisplayName("Valid toEither produces Right")
    void validToEitherProducesRight() {
      Validated<String, Integer> valid = Validated.valid(DEFAULT_VALID_VALUE);

      Either<String, Integer> either = valid.toEither();

      assertThat(either.isRight()).isTrue();
      assertThat(either.isLeft()).isFalse();
      assertThat(either.getRight()).isEqualTo(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("Invalid toEither produces Left")
    void invalidToEitherProducesLeft() {
      Validated<String, Integer> invalid = Validated.invalid(DEFAULT_ERROR);

      Either<String, Integer> either = invalid.toEither();

      assertThat(either.isLeft()).isTrue();
      assertThat(either.isRight()).isFalse();
      assertThat(either.getLeft()).isEqualTo(DEFAULT_ERROR);
    }

    @Test
    @DisplayName("Valid toEither roundtrip preserves value")
    void validToEitherRoundtripPreservesValue() {
      Validated<String, Integer> original = Validated.valid(DEFAULT_VALID_VALUE);
      Either<String, Integer> either = original.toEither();

      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo(original.get());
    }

    @Test
    @DisplayName("Invalid toEither roundtrip preserves error")
    void invalidToEitherRoundtripPreservesError() {
      Validated<String, Integer> original = Validated.invalid(DEFAULT_ERROR);
      Either<String, Integer> either = original.toEither();

      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(original.getError());
    }
  }

  @Nested
  @DisplayName("ToString and Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("toString produces readable output for Valid")
    void toStringProducesReadableOutputForValid() {
      Validated<String, Integer> valid = Validated.valid(DEFAULT_VALID_VALUE);
      assertThat(valid.toString()).isEqualTo("Valid(42)");
    }

    @Test
    @DisplayName("toString produces readable output for Invalid")
    void toStringProducesReadableOutputForInvalid() {
      Validated<String, Integer> invalid = Validated.invalid(DEFAULT_ERROR);
      assertThat(invalid.toString()).isEqualTo("Invalid(" + DEFAULT_ERROR + ")");
    }

    @Test
    @DisplayName("equals compares Valid values correctly")
    void equalsComparesValidValuesCorrectly() {
      Validated<String, Integer> valid1 = Validated.valid(DEFAULT_VALID_VALUE);
      Validated<String, Integer> valid2 = Validated.valid(DEFAULT_VALID_VALUE);
      Validated<String, Integer> valid3 = Validated.valid(ALTERNATIVE_VALID_VALUE);

      assertThatValidated(valid1).isEqualTo(valid2);
      assertThatValidated(valid1).isNotEqualTo(valid3);
    }

    @Test
    @DisplayName("equals compares Invalid values correctly")
    void equalsComparesInvalidValuesCorrectly() {
      Validated<String, Integer> invalid1 = Validated.invalid(DEFAULT_ERROR);
      Validated<String, Integer> invalid2 = Validated.invalid(DEFAULT_ERROR);
      Validated<String, Integer> invalid3 = Validated.invalid(ALTERNATIVE_ERROR);

      assertThatValidated(invalid1).isEqualTo(invalid2);
      assertThatValidated(invalid1).isNotEqualTo(invalid3);
    }

    @Test
    @DisplayName("Valid and Invalid are never equal")
    void validAndInvalidAreNeverEqual() {
      Validated<String, Integer> valid = Validated.valid(DEFAULT_VALID_VALUE);
      Validated<String, Integer> invalid = Validated.invalid(DEFAULT_ERROR);

      assertThatValidated(valid).isNotEqualTo(invalid);
    }

    @Test
    @DisplayName("hashCode is consistent for Valid")
    void hashCodeIsConsistentForValid() {
      Validated<String, Integer> valid1 = Validated.valid(DEFAULT_VALID_VALUE);
      Validated<String, Integer> valid2 = Validated.valid(DEFAULT_VALID_VALUE);

      assertThat(valid1.hashCode()).isEqualTo(valid2.hashCode());
    }

    @Test
    @DisplayName("hashCode is consistent for Invalid")
    void hashCodeIsConsistentForInvalid() {
      Validated<String, Integer> invalid1 = Validated.invalid(DEFAULT_ERROR);
      Validated<String, Integer> invalid2 = Validated.invalid(DEFAULT_ERROR);

      assertThat(invalid1.hashCode()).isEqualTo(invalid2.hashCode());
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("chain multiple Valid operations")
    void chainMultipleValidOperations() {
      Validated<String, Integer> result =
          Validated.<String, Integer>valid(10)
              .map(n -> n * 2)
              .flatMap(n -> Validated.<String, Integer>valid(n + 5))
              .map(n -> n * 3);

      assertThatValidated(result).isValid().hasValue(75);
    }

    @Test
    @DisplayName("chain operations short-circuits on Invalid")
    void chainOperationsShortCircuitsOnInvalid() {
      Validated<String, Integer> result =
          Validated.<String, Integer>valid(10)
              .map(n -> n * 2)
              .flatMap(n -> Validated.<String, Integer>invalid("computation failed"))
              .map(n -> n * 3);

      assertThatValidated(result).isInvalid().hasError("computation failed");
    }

    @Test
    @DisplayName("combining Valid values with map2")
    void combiningValidValuesWithMap2() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = validKind(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = validKind(20);

      Kind<ValidatedKind.Witness<String>, Integer> result = monad.map2(v1, v2, (a, b) -> a + b);

      Validated<String, Integer> validated = narrowToValidated(result);
      assertThatValidated(validated).isValid().hasValue(30);
    }

    @Test
    @DisplayName("combining Invalid values accumulates errors")
    void combiningInvalidValuesAccumulatesErrors() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = invalidKind("error1");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = invalidKind("error2");

      Kind<ValidatedKind.Witness<String>, Integer> result = monad.map2(v1, v2, (a, b) -> a + b);

      Validated<String, Integer> validated = narrowToValidated(result);
      assertThatValidated(validated).isInvalid().hasError("error1, error2");
    }

    @Test
    @DisplayName("validation pipeline with multiple checks")
    void validationPipelineWithMultipleChecks() {
      int age = 25;
      String name = "John";

      Validated<String, Unit> ageCheck = Validated.validateThat(age >= 18, "Must be 18 or older");
      Validated<String, Unit> nameCheck = Validated.validateThat(!name.isEmpty(), "Name required");

      Kind<ValidatedKind.Witness<String>, Unit> ageKind = VALIDATED.widen(ageCheck);
      Kind<ValidatedKind.Witness<String>, Unit> nameKind = VALIDATED.widen(nameCheck);

      Kind<ValidatedKind.Witness<String>, Unit> allValid =
          monad.map2(ageKind, nameKind, (u1, u2) -> Unit.INSTANCE);

      Validated<String, Unit> result = VALIDATED.narrow(allValid);
      assertThatValidated(result).isValid().hasValue(Unit.INSTANCE);
    }

    @Test
    @DisplayName("validation pipeline accumulates all errors")
    void validationPipelineAccumulatesAllErrors() {
      int age = 15;
      String name = "";

      Validated<String, Unit> ageCheck = Validated.validateThat(age >= 18, "Must be 18 or older");
      Validated<String, Unit> nameCheck = Validated.validateThat(!name.isEmpty(), "Name required");

      Kind<ValidatedKind.Witness<String>, Unit> ageKind = VALIDATED.widen(ageCheck);
      Kind<ValidatedKind.Witness<String>, Unit> nameKind = VALIDATED.widen(nameCheck);

      Kind<ValidatedKind.Witness<String>, Unit> allValid =
          monad.map2(ageKind, nameKind, (u1, u2) -> Unit.INSTANCE);

      Validated<String, Unit> result = VALIDATED.narrow(allValid);
      assertThatValidated(result)
          .isInvalid()
          .hasErrorSatisfying(
              error -> error.contains("Must be 18 or older") && error.contains("Name required"),
              "contains both validation errors");
    }
  }

  @Nested
  @DisplayName("Type Variance Tests")
  class TypeVarianceTests {

    @Test
    @DisplayName("map changes value type")
    void mapChangesValueType() {
      Validated<String, Integer> intValid = Validated.valid(42);
      Validated<String, String> stringValid = intValid.map(Object::toString);

      assertThatValidated(stringValid).isValid().hasValue("42").hasValueOfType(String.class);
    }

    @Test
    @DisplayName("flatMap changes value type")
    void flatMapChangesValueType() {
      Validated<String, Integer> intValid = Validated.valid(42);
      Validated<String, Boolean> boolValid = intValid.flatMap(n -> Validated.valid(n > 40));

      assertThatValidated(boolValid).isValid().hasValue(true).hasValueOfType(Boolean.class);
    }

    @Test
    @DisplayName("error type remains consistent through transformations")
    void errorTypeRemainsConsistentThroughTransformations() {
      Validated<String, Integer> start = Validated.invalid("initial error");

      Validated<String, String> afterMap = start.map(Object::toString);
      assertThatValidated(afterMap)
          .isInvalid()
          .hasError("initial error")
          .hasErrorOfType(String.class);

      Validated<String, Boolean> afterFlatMap = afterMap.flatMap(s -> Validated.valid(s.isEmpty()));
      assertThatValidated(afterFlatMap)
          .isInvalid()
          .hasError("initial error")
          .hasErrorOfType(String.class);
    }

    @Test
    @DisplayName("different error types with different Validated instances")
    void differentErrorTypesWithDifferentValidatedInstances() {
      Validated<Integer, String> intError = Validated.invalid(404);
      Validated<Boolean, String> boolError = Validated.invalid(true);

      assertThatValidated(intError).isInvalid().hasError(404).hasErrorOfType(Integer.class);

      assertThatValidated(boolError).isInvalid().hasError(true).hasErrorOfType(Boolean.class);
    }
  }

  @Nested
  @DisplayName("Traverse Tests")
  class TraverseTests {

    private ValidatedTraverse<String> traverse;
    private Monoid<Integer> intMonoid;

    @BeforeEach
    void setUpTraverse() {
      traverse = ValidatedTraverse.instance();
      intMonoid = Monoids.integerAddition();
    }

    @Test
    @DisplayName("Run complete Traverse test pattern")
    void runCompleteTraverseTestPattern() {
      TypeClassTest.<ValidatedKind.Witness<String>>traverse(ValidatedTraverse.class)
          .<Integer>instance(traverse)
          .<String>withKind(validKind)
          .withOperations(validMapper)
          .withApplicative(monad, validFlatMapper)
          .withFoldableOperations(intMonoid, i -> i)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Advanced Usage Patterns")
  class AdvancedUsagePatterns {

    @Test
    @DisplayName("Validated as functor maintains structure")
    void validatedAsFunctorMaintainsStructure() {
      Validated<String, Integer> start = Validated.valid(5);

      Validated<String, Double> result = start.map(i -> i * 2.0).map(d -> d + 0.5).map(Math::sqrt);

      assertThatValidated(result)
          .isValid()
          .hasValueSatisfying(
              value -> Math.abs(value - Math.sqrt(10.5)) < 0.001, "value is square root of 10.5");
    }

    @Test
    @DisplayName("Validated for parallel validation")
    void validatedForParallelValidation() {
      Function<String, Validated<String, Unit>> validateLength =
          s -> Validated.validateThat(s.length() >= 3, "Name must be at least 3 characters");

      Function<String, Validated<String, Unit>> validateNotEmpty =
          s -> Validated.validateThat(!s.isEmpty(), "Name cannot be empty");

      Function<String, Validated<String, Unit>> validateAlpha =
          s ->
              Validated.validateThat(
                  s.chars().allMatch(Character::isLetter), "Name must contain only letters");

      String validName = "John";
      Validated<String, Unit> v1 = validateLength.apply(validName);
      Validated<String, Unit> v2 = validateNotEmpty.apply(validName);
      Validated<String, Unit> v3 = validateAlpha.apply(validName);

      Kind<ValidatedKind.Witness<String>, Unit> k1 = VALIDATED.widen(v1);
      Kind<ValidatedKind.Witness<String>, Unit> k2 = VALIDATED.widen(v2);
      Kind<ValidatedKind.Witness<String>, Unit> k3 = VALIDATED.widen(v3);

      Kind<ValidatedKind.Witness<String>, Unit> allValid =
          monad.map2(monad.map2(k1, k2, (u1, u2) -> Unit.INSTANCE), k3, (u1, u2) -> Unit.INSTANCE);

      Validated<String, Unit> result = VALIDATED.narrow(allValid);
      assertThatValidated(result).isValid();

      String invalidName = "A1";
      Validated<String, Unit> iv1 = validateLength.apply(invalidName);
      Validated<String, Unit> iv2 = validateNotEmpty.apply(invalidName);
      Validated<String, Unit> iv3 = validateAlpha.apply(invalidName);

      Kind<ValidatedKind.Witness<String>, Unit> ik1 = VALIDATED.widen(iv1);
      Kind<ValidatedKind.Witness<String>, Unit> ik2 = VALIDATED.widen(iv2);
      Kind<ValidatedKind.Witness<String>, Unit> ik3 = VALIDATED.widen(iv3);

      Kind<ValidatedKind.Witness<String>, Unit> allInvalid =
          monad.map2(
              monad.map2(ik1, ik2, (u1, u2) -> Unit.INSTANCE), ik3, (u1, u2) -> Unit.INSTANCE);

      Validated<String, Unit> invalidResult = VALIDATED.narrow(allInvalid);
      assertThatValidated(invalidResult)
          .isInvalid()
          .hasErrorSatisfying(
              error -> error.contains("at least 3 characters") && error.contains("only letters"),
              "accumulates multiple errors");
    }

    @Test
    @DisplayName("Validated pattern matching with switch expressions")
    void validatedPatternMatchingWithSwitch() {
      Function<Validated<String, Integer>, String> processValidated =
          validated ->
              switch (validated) {
                case Invalid<String, Integer>(var error) -> "Error: " + error;
                case Valid<String, Integer>(var value) -> "Success: " + value;
              };

      assertThat(processValidated.apply(invalidInstance)).isEqualTo("Error: " + DEFAULT_ERROR);
      assertThat(processValidated.apply(validInstance))
          .isEqualTo("Success: " + DEFAULT_VALID_VALUE);

      Validated<Validated<String, Integer>, Boolean> nested =
          Validated.invalid(Validated.valid(42));
      String nestedResult =
          switch (nested) {
            case Invalid<Validated<String, Integer>, Boolean>(var innerValidated) ->
                switch (innerValidated) {
                  case Invalid<String, Integer>(var error) -> "Nested error: " + error;
                  case Valid<String, Integer>(var value) -> "Nested value: " + value;
                };
            case Valid<Validated<String, Integer>, Boolean>(var bool) -> "Boolean: " + bool;
          };
      assertThat(nestedResult).isEqualTo("Nested value: 42");
    }
  }

  @Nested
  @DisplayName("Performance and Memory Characteristics")
  class PerformanceAndMemoryTests {

    @Test
    @DisplayName("Validated operations complete in reasonable time")
    void validatedOperationsCompleteInReasonableTime() {
      Validated<String, Integer> test = Validated.valid(DEFAULT_VALID_VALUE);

      // Verify operations complete without hanging (generous timeout)
      // Use JMH benchmarks in hkj-benchmarks module for precise performance measurement
      assertTimeoutPreemptively(
          Duration.ofSeconds(2),
          () -> {
            for (int i = 0; i < 100_000; i++) {
              test.map(x -> x + 1).flatMap(x -> Validated.valid(x * 2)).isValid();
            }
          },
          "Validated operations should complete within reasonable time");
    }

    @Test
    @DisplayName("Invalid instances are reused efficiently")
    void invalidInstancesAreReusedEfficiently() {
      Validated<String, Integer> invalid = Validated.invalid("error");

      Validated<String, String> mapped = invalid.map(Object::toString);
      assertThat(mapped).isSameAs(invalid);

      Validated<String, Boolean> multiMapped =
          invalid.map(Object::toString).map(String::length).map(len -> len > 0);
      assertThat(multiMapped).isSameAs(invalid);

      Validated<String, String> flatMapped = invalid.flatMap(x -> Validated.valid("not reached"));
      assertThat(flatMapped).isSameAs(invalid);
    }

    @Test
    @DisplayName("Memory usage is reasonable for large chains")
    void memoryUsageIsReasonableForLargeChains() {
      Validated<String, Integer> start = Validated.valid(1);

      Validated<String, Integer> result = start;
      for (int i = 0; i < 1000; i++) {
        final int increment = i;
        result = result.map(x -> x + increment);
      }

      assertThatValidated(result).isValid().hasValue(1 + (999 * 1000) / 2);

      Validated<String, Integer> invalidStart = Validated.invalid("error");
      Validated<String, Integer> invalidResult = invalidStart;
      for (int i = 0; i < 1000; i++) {
        int finalI = i;
        invalidResult = invalidResult.map(x -> x + finalI);
      }

      assertThat(invalidResult).isSameAs(invalidStart);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Boundary Conditions")
  class EdgeCasesTests {

    @Test
    @DisplayName("Validated handles extreme values correctly")
    void validatedHandlesExtremeValuesCorrectly() {
      String largeString = "x".repeat(10000);
      Validated<String, String> largeValid = Validated.valid(largeString);
      assertThatValidated(largeValid.map(String::length)).isValid().hasValue(10000);

      Validated<String, Integer> maxInt = Validated.valid(Integer.MAX_VALUE);
      Validated<String, Long> promoted = maxInt.map(i -> i.longValue() + 1);
      assertThatValidated(promoted).isValid().hasValue((long) Integer.MAX_VALUE + 1);

      Validated<String, Validated<String, Validated<String, Integer>>> tripleNested =
          Validated.valid(Validated.valid(Validated.valid(42)));

      Validated<String, Integer> flattened =
          tripleNested.flatMap(inner -> inner.flatMap(innerInner -> innerInner));
      assertThatValidated(flattened).isValid().hasValue(42);
    }

    @Test
    @DisplayName("Validated operations are stack-safe for deep recursion")
    void validatedOperationsAreStackSafe() {
      Validated<String, Integer> start = Validated.valid(0);

      Validated<String, Integer> result = start;
      for (int i = 0; i < 10000; i++) {
        result = result.map(x -> x + 1);
      }

      assertThatValidated(result).isValid().hasValue(10000);

      Validated<String, Integer> flatMapResult = start;
      for (int i = 0; i < 1000; i++) {
        flatMapResult = flatMapResult.flatMap(x -> Validated.valid(x + 1));
      }

      assertThatValidated(flatMapResult).isValid().hasValue(1000);
    }

    @Test
    @DisplayName("Validated maintains referential transparency")
    void validatedMaintainsReferentialTransparency() {
      Validated<String, Integer> validated = Validated.valid(DEFAULT_VALID_VALUE);

      Validated<String, String> result1 = validated.map(i -> "value:" + i);
      Validated<String, String> result2 = validated.map(i -> "value:" + i);

      assertThat(result1).isEqualTo(result2);
      assertThat(result1.get()).isEqualTo(result2.get());

      Validated<String, String> flatMapResult1 =
          validated.flatMap(i -> Validated.valid("flat:" + i));
      Validated<String, String> flatMapResult2 =
          validated.flatMap(i -> Validated.valid("flat:" + i));

      assertThat(flatMapResult1).isEqualTo(flatMapResult2);
    }
  }
}
