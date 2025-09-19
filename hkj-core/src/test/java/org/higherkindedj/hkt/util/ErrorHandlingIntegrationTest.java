// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.util.ErrorHandling.*;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ErrorHandling Integration Tests")
public class ErrorHandlingIntegrationTest {

  // Test Kind implementations
  private interface MyKindWitness {}

  private interface AnotherWitness {}

  private record MyKind<A>(A value) implements Kind<MyKindWitness, A> {}

  private record AnotherKind<A>(A value) implements Kind<MyKindWitness, A> {}

  private record DifferentWitnessKind<A>(A value) implements Kind<AnotherWitness, A> {}

  @Nested
  @DisplayName("Complete Workflow Integration")
  class CompleteWorkflowIntegrationTests {

    @Test
    void shouldHandleValidKindWorkflow() {
      // Create a Kind
      MyKind<String> original = new MyKind<>("test value");

      // Validate it's not null
      Kind<MyKindWitness, String> validated = requireNonNullKind(original, "test kind");

      // Narrow it using type check
      MyKind<String> narrowed = narrowKindWithTypeCheck(validated, MyKind.class, "MyKind");

      // Verify the result
      assertThat(narrowed).isSameAs(original);
      assertThat(narrowed.value()).isEqualTo("test value");
    }

    @Test
    void shouldHandleInvalidKindWorkflow() {
      // Create invalid Kind
      AnotherKind<String> wrongKind = new AnotherKind<>("wrong");

      // Validate it's not null (should succeed)
      Kind<MyKindWitness, String> validated = requireNonNullKind(wrongKind, "test kind");

      // Try to narrow it (should fail)
      assertThatThrownBy(() -> narrowKindWithTypeCheck(validated, MyKind.class, "MyKind"))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind instance is not a MyKind");
    }

    @Test
    void shouldProvideConsistentErrorMessages() {
      // Test that all error handling utilities use consistent message formats

      // Test null Kind error
      assertThatThrownBy(() -> narrowKind(null, "TestType", k -> "result"))
          .hasMessage("Cannot narrow null Kind for TestType");

      assertThatThrownBy(() -> narrowKindWithTypeCheck(null, MyKind.class, "TestType"))
          .hasMessage("Cannot narrow null Kind for TestType");

      // Test widen error
      assertThatThrownBy(() -> requireNonNullForWiden(null, "TestType"))
          .hasMessage("Input TestType cannot be null for widen");

      // Test holder error
      assertThatThrownBy(() -> requireNonNullForHolder(null, "TestType"))
          .hasMessage("TestTypeHolder contained null TestType instance");
    }

    @Test
    void shouldWorkWithComplexScenarios() {
      // Test a complex scenario combining multiple utilities
      List<String> data = List.of("item1", "item2");

      // Validate collection
      List<String> validatedData = requireNonEmptyCollection(data, "inputData");

      // Validate range
      int size = requireInRange(validatedData.size(), 1, 10, "dataSize");

      // Create and validate Kind
      MyKind<Integer> kind = new MyKind<>(size);
      Kind<MyKindWitness, Integer> validatedKind = requireNonNullKind(kind, "sizeKind");
      MyKind<Integer> narrowedKind = narrowKindWithTypeCheck(validatedKind, MyKind.class, "MyKind");

      assertThat(narrowedKind.value()).isEqualTo(2);
    }

    @Test
    void shouldHandleComplexValidationScenarios() {
      String[] inputArray = {"a", "b", "c"};

      validateAll(
          Validation.requireNonNull(inputArray, "input array cannot be null"),
          Validation.require(inputArray.length > 0, "input array cannot be empty"),
          Validation.require(inputArray.length <= 10, "input array too large"),
          () -> requireNonEmptyArray(inputArray, "inputArray") // Custom validation
          );

      // If we get here, all validations passed
      assertThat(inputArray).hasSize(3);
    }

    @Test
    void shouldHandleNestedExceptionWrapping() {
      RuntimeException root = new RuntimeException("Root cause");
      IllegalStateException middle = new IllegalStateException("Middle layer", root);

      // Wrap multiple times
      KindUnwrapException firstWrap = wrapAsKindUnwrapException(middle, "First context");
      KindUnwrapException secondWrap = wrapAsKindUnwrapException(firstWrap, "Second context");

      assertThat(secondWrap.getMessage()).isEqualTo("Second context: First context: Middle layer");
      assertThat(secondWrap.getCause()).isSameAs(firstWrap);
      assertThat(secondWrap.getCause().getCause()).isSameAs(middle);
      assertThat(secondWrap.getCause().getCause().getCause()).isSameAs(root);
    }

    @Test
    void shouldWorkWithDifferentKindTypes() {
      // Test with different witness types
      MyKind<String> myKind = new MyKind<>("my");
      DifferentWitnessKind<String> differentKind = new DifferentWitnessKind<>("different");

      // These should work with their respective witness types
      Kind<MyKindWitness, String> myValidated = requireNonNullKind(myKind, "myKind");
      Kind<AnotherWitness, String> differentValidated =
          requireNonNullKind(differentKind, "differentKind");

      MyKind<String> myNarrowed = narrowKindWithTypeCheck(myValidated, MyKind.class, "MyKind");
      DifferentWitnessKind<String> differentNarrowed =
          narrowKindWithTypeCheck(
              differentValidated, DifferentWitnessKind.class, "DifferentWitnessKind");

      assertThat(myNarrowed).isSameAs(myKind);
      assertThat(differentNarrowed).isSameAs(differentKind);
    }
  }

  @Nested
  @DisplayName("Domain Specific Helpers Integration")
  class DomainSpecificHelpersIntegrationTests {

    @Test
    void shouldValidateOuterMonad() {
      Monad<Id.Witness> monad = IdMonad.instance();
      assertThat(requireValidOuterMonad(monad, "MyTransformer")).isSameAs(monad);
    }

    @Test
    void shouldThrowForNullOuterMonad() {
      assertThatThrownBy(() -> requireValidOuterMonad(null, "MyTransformer"))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Outer Monad cannot be null for MyTransformer");
    }

    @Test
    void shouldWorkWithDifferentTransformerNames() {
      Monad<Id.Witness> monad = IdMonad.instance();
      assertThat(requireValidOuterMonad(monad, "EitherT")).isSameAs(monad);
      assertThat(requireValidOuterMonad(monad, "StateT")).isSameAs(monad);
      assertThat(requireValidOuterMonad(monad, "ReaderT")).isSameAs(monad);
    }

    @Test
    void shouldHandleNullTransformerName() {
      Monad<Id.Witness> monad = IdMonad.instance();
      assertThat(requireValidOuterMonad(monad, null)).isSameAs(monad);

      assertThatThrownBy(() -> requireValidOuterMonad(null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Outer Monad cannot be null for null");
    }

    @Test
    void shouldValidateMatchingWitness() {
      requireMatchingWitness(String.class, String.class, "myOperation");
    }

    @Test
    void shouldSucceedWithSameInstance() {
      Class<String> stringClass = String.class;
      requireMatchingWitness(stringClass, stringClass, "myOperation");
    }

    @Test
    void shouldThrowForMismatchedWitness() {
      assertThatThrownBy(() -> requireMatchingWitness(String.class, Integer.class, "myOperation"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Witness type mismatch in myOperation: expected String, got Integer");
    }

    @Test
    void shouldThrowForNullWitnessTypes() {
      assertThatThrownBy(() -> requireMatchingWitness(null, String.class, "myOperation"))
          .isInstanceOf(NullPointerException.class);

      assertThatThrownBy(() -> requireMatchingWitness(String.class, null, "myOperation"))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldWorkWithDifferentClassTypes() {
      requireMatchingWitness(List.class, List.class, "listOperation");

      assertThatThrownBy(
              () -> requireMatchingWitness(List.class, java.util.ArrayList.class, "listOperation"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Witness type mismatch in listOperation: expected List, got ArrayList");
    }

    @Test
    void shouldHandleNullOperationName() {
      requireMatchingWitness(String.class, String.class, null);

      assertThatThrownBy(() -> requireMatchingWitness(String.class, Integer.class, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Witness type mismatch in null: expected String, got Integer");
    }

    @Test
    void shouldHandlePrimitiveTypes() {
      requireMatchingWitness(int.class, int.class, "primitiveOperation");

      assertThatThrownBy(
              () -> requireMatchingWitness(int.class, Integer.class, "primitiveOperation"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Witness type mismatch in primitiveOperation: expected int, got Integer");
    }
  }

  @Nested
  @DisplayName("Error Message Constants Integration")
  class ErrorMessageConstantsIntegrationTests {

    @Test
    void constantsShouldMatchExpectedValues() {
      assertThat(NULL_KIND_TEMPLATE).isEqualTo("Cannot narrow null Kind for %s");
      assertThat(INVALID_KIND_TYPE_TEMPLATE).isEqualTo("Kind instance is not a %s: %s");
      assertThat(NULL_WIDEN_INPUT_TEMPLATE).isEqualTo("Input %s cannot be null for widen");
      assertThat(NULL_HOLDER_STATE_TEMPLATE).isEqualTo("%s contained null %s instance");
      assertThat(NULL_FUNCTION_MSG).isEqualTo("%s cannot be null");
      assertThat(NULL_KIND_ARG_MSG).isEqualTo("Kind argument cannot be null");
    }

    @Test
    void constantsShouldFormatCorrectly() {
      assertThat(String.format(NULL_KIND_TEMPLATE, "MyType"))
          .isEqualTo("Cannot narrow null Kind for MyType");
      assertThat(String.format(INVALID_KIND_TYPE_TEMPLATE, "MyType", "ActualClass"))
          .isEqualTo("Kind instance is not a MyType: ActualClass");
      assertThat(String.format(NULL_WIDEN_INPUT_TEMPLATE, "Writer"))
          .isEqualTo("Input Writer cannot be null for widen");
      assertThat(String.format(NULL_HOLDER_STATE_TEMPLATE, "MyTypeHolder", "MyType"))
          .isEqualTo("MyTypeHolder contained null MyType instance");
      assertThat(String.format(NULL_FUNCTION_MSG, "mapping function"))
          .isEqualTo("mapping function cannot be null");
    }

    @Test
    void constantsShouldHandleNullArguments() {
      assertThat(String.format(NULL_KIND_TEMPLATE, (Object) null))
          .isEqualTo("Cannot narrow null Kind for null");
      assertThat(String.format(INVALID_KIND_TYPE_TEMPLATE, null, "ActualClass"))
          .isEqualTo("Kind instance is not a null: ActualClass");
      assertThat(String.format(NULL_WIDEN_INPUT_TEMPLATE, (Object) null))
          .isEqualTo("Input null cannot be null for widen");
      assertThat(String.format(NULL_HOLDER_STATE_TEMPLATE, null, "MyType"))
          .isEqualTo("null contained null MyType instance");
      assertThat(String.format(NULL_FUNCTION_MSG, (Object) null)).isEqualTo("null cannot be null");
    }

    @Test
    void constantsShouldBeNonNull() {
      assertThat(NULL_KIND_TEMPLATE).isNotNull();
      assertThat(INVALID_KIND_TYPE_TEMPLATE).isNotNull();
      assertThat(NULL_WIDEN_INPUT_TEMPLATE).isNotNull();
      assertThat(NULL_HOLDER_STATE_TEMPLATE).isNotNull();
      assertThat(NULL_FUNCTION_MSG).isNotNull();
      assertThat(NULL_KIND_ARG_MSG).isNotNull();
    }
  }

  @Nested
  @DisplayName("Performance Integration Tests")
  class PerformanceIntegrationTests {

    @Test
    void shouldHandleComplexOperationsEfficiently() {
      // Create a complex validation scenario
      for (int i = 0; i < 100; i++) {
        String value = "test" + i;

        // Multiple validation steps
        requireNonNullForWiden(value, "testValue");
        requireCondition(value.length() > 0, "Value cannot be empty");
        requireInRange(value.length(), 1, 20, "valueLength");

        // Create and validate Kind
        MyKind<String> kind = new MyKind<>(value);
        Kind<MyKindWitness, String> validated = requireNonNullKind(kind, "testKind");
        MyKind<String> narrowed = narrowKindWithTypeCheck(validated, MyKind.class, "MyKind");

        assertThat(narrowed.value()).isEqualTo(value);
      }
    }

    @Test
    void shouldHandleLargeValidationSets() {
      // Create a large number of validations
      java.util.List<Validation> validations = new java.util.ArrayList<>();

      for (int i = 0; i < 1000; i++) {
        final int index = i;
        validations.add(Validation.require(true, "validation" + index));
      }

      // This should complete efficiently
      validateAll(validations.toArray(new Validation[0]));
    }

    @Test
    void shouldHandleLargeDataStructures() {
      // Create large data structures and validate them
      java.util.List<String> largeList = new java.util.ArrayList<>();
      for (int i = 0; i < 10000; i++) {
        largeList.add("item" + i);
      }

      List<String> validated = requireNonEmptyCollection(largeList, "largeList");
      int size = requireInRange(validated.size(), 1, 20000, "listSize");

      assertThat(size).isEqualTo(10000);
      assertThat(validated).hasSize(10000);
    }
  }

  @Nested
  @DisplayName("Comprehensive Coverage Tests")
  class ComprehensiveCoverageTests {

    @Test
    void shouldExerciseAllPublicMethods() {
      // Test all narrowKind variants
      MyKind<String> kind = new MyKind<>("test");
      narrowKind(kind, "Test", k -> (MyKind<String>) k);
      narrowKindWithTypeCheck(kind, MyKind.class, "Test");

      // Test all validation methods
      requireNonNullForWiden(kind, "Test");
      requireNonNullForHolder(kind, "Test");
      Function<Object, String> testFunction = Object::toString;
      requireNonNullFunction(testFunction, "testFunction");
      requireNonNullKind(kind, "testKind");
      requireNonEmptyCollection(List.of("test"), "testCollection");
      requireNonEmptyArray(new String[] {"test"}, "testArray");
      requireCondition(true, "test condition");
      requireInRange(5, 0, 10, "testRange");

      // Test lazy message utilities
      java.util.function.Supplier<String> lazyMsg = lazyMessage("Test: %s", "value");
      assertThat(lazyMsg.get()).isEqualTo("Test: value");

      // Test validation combinators
      validateAll(Validation.require(true, "test"));

      // Test exception utilities
      RuntimeException original = new RuntimeException("test");
      KindUnwrapException wrapped = wrapWithContext(original, "context", KindUnwrapException::new);
      assertThat(wrapped).isNotNull();

      KindUnwrapException directWrap = wrapAsKindUnwrapException(original, "context");
      assertThat(directWrap).isNotNull();

      // Test domain-specific helpers
      Monad<Id.Witness> monad = IdMonad.instance();
      requireValidOuterMonad(monad, "TestTransformer");
      requireMatchingWitness(String.class, String.class, "testOp");
    }

    @Test
    void shouldExerciseAllErrorPaths() {
      // narrowKind errors
      assertThatThrownBy(() -> narrowKind(null, "Test", k -> k))
          .isInstanceOf(KindUnwrapException.class);

      // narrowKindWithTypeCheck errors
      assertThatThrownBy(
              () -> narrowKindWithTypeCheck(new AnotherKind<>("test"), MyKind.class, "Test"))
          .isInstanceOf(KindUnwrapException.class);

      // Validation errors
      assertThatThrownBy(() -> requireNonNullForWiden(null, "Test"))
          .isInstanceOf(NullPointerException.class);

      assertThatThrownBy(() -> requireNonEmptyCollection(Collections.emptyList(), "Test"))
          .isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(() -> requireCondition(false, "Test failed"))
          .isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(() -> requireInRange(-1, 0, 10, "Test"))
          .isInstanceOf(IllegalArgumentException.class);

      // Validation combinator errors
      assertThatThrownBy(() -> validateAll(Validation.require(false, "Test error")))
          .isInstanceOf(IllegalArgumentException.class);

      // Domain-specific errors
      assertThatThrownBy(() -> requireValidOuterMonad(null, "Test"))
          .isInstanceOf(NullPointerException.class);

      assertThatThrownBy(() -> requireMatchingWitness(String.class, Integer.class, "Test"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
