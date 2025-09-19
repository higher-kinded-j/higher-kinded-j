// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.util.ErrorHandling.*;

import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Domain Specific Helpers Tests")
class DomainSpecificHelpersTest {

  @Nested
  @DisplayName("requireValidOuterMonad() Tests")
  class RequireValidOuterMonadTests {

    @Test
    void shouldReturnSameMonadForValid() {
      Monad<Id.Witness> monad = IdMonad.instance();
      assertThat(requireValidOuterMonad(monad, "MyTransformer")).isSameAs(monad);
    }

    @Test
    void shouldThrowForNullMonad() {
      assertThatThrownBy(() -> requireValidOuterMonad(null, "MyTransformer"))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Outer Monad cannot be null for MyTransformer");
    }

    @Test
    void shouldWorkWithDifferentTransformerNames() {
      Monad<Id.Witness> monad = IdMonad.instance();

      // Test various transformer names
      assertThat(requireValidOuterMonad(monad, "EitherT")).isSameAs(monad);
      assertThat(requireValidOuterMonad(monad, "StateT")).isSameAs(monad);
      assertThat(requireValidOuterMonad(monad, "ReaderT")).isSameAs(monad);
      assertThat(requireValidOuterMonad(monad, "WriterT")).isSameAs(monad);
      assertThat(requireValidOuterMonad(monad, "MaybeT")).isSameAs(monad);
      assertThat(requireValidOuterMonad(monad, "IOT")).isSameAs(monad);
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
    void shouldHandleEmptyTransformerName() {
      Monad<Id.Witness> monad = IdMonad.instance();
      assertThat(requireValidOuterMonad(monad, "")).isSameAs(monad);

      assertThatThrownBy(() -> requireValidOuterMonad(null, ""))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Outer Monad cannot be null for ");
    }

    @Test
    void shouldHandleSpecialCharactersInTransformerName() {
      Monad<Id.Witness> monad = IdMonad.instance();

      // Test with special characters
      assertThat(requireValidOuterMonad(monad, "Custom-Transformer")).isSameAs(monad);
      assertThat(requireValidOuterMonad(monad, "Transformer_With_Underscores")).isSameAs(monad);
      assertThat(requireValidOuterMonad(monad, "Transformer With Spaces")).isSameAs(monad);
      assertThat(requireValidOuterMonad(monad, "Transformer123")).isSameAs(monad);
    }

    @Test
    void shouldHandleVeryLongTransformerNames() {
      Monad<Id.Witness> monad = IdMonad.instance();
      String longName = "VeryLongTransformerName".repeat(100);

      assertThat(requireValidOuterMonad(monad, longName)).isSameAs(monad);

      assertThatThrownBy(() -> requireValidOuterMonad(null, longName))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Outer Monad cannot be null for " + longName);
    }
  }

  @Nested
  @DisplayName("requireMatchingWitness() Tests")
  class RequireMatchingWitnessTests {

    @Test
    void shouldSucceedForSameClass() {
      requireMatchingWitness(String.class, String.class, "myOperation");
    }

    @Test
    void shouldSucceedWithSameClassInstance() {
      Class<String> stringClass = String.class;
      requireMatchingWitness(stringClass, stringClass, "myOperation");
    }

    @Test
    void shouldThrowForDifferentClasses() {
      assertThatThrownBy(() -> requireMatchingWitness(String.class, Integer.class, "myOperation"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Witness type mismatch in myOperation: expected String, got Integer");
    }

    @Test
    void shouldThrowForNullExpected() {
      assertThatThrownBy(() -> requireMatchingWitness(null, String.class, "myOperation"))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowForNullActual() {
      assertThatThrownBy(() -> requireMatchingWitness(String.class, null, "myOperation"))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldWorkWithDifferentClassTypes() {
      // Test with interface classes
      requireMatchingWitness(java.util.List.class, java.util.List.class, "listOperation");
      requireMatchingWitness(java.util.Map.class, java.util.Map.class, "mapOperation");

      // Should fail for different interfaces
      assertThatThrownBy(
              () -> requireMatchingWitness(java.util.List.class, java.util.Map.class, "operation"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Witness type mismatch in operation: expected List, got Map");
    }

    @Test
    void shouldWorkWithImplementationClasses() {
      // Test with concrete implementation classes
      requireMatchingWitness(java.util.ArrayList.class, java.util.ArrayList.class, "arrayListOp");

      // Should fail for interface vs implementation
      assertThatThrownBy(
              () ->
                  requireMatchingWitness(
                      java.util.List.class, java.util.ArrayList.class, "listOperation"))
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
    void shouldHandleEmptyOperationName() {
      requireMatchingWitness(String.class, String.class, "");

      assertThatThrownBy(() -> requireMatchingWitness(String.class, Integer.class, ""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Witness type mismatch in : expected String, got Integer");
    }

    @Test
    void shouldHandlePrimitiveTypes() {
      // Test with primitive type classes
      requireMatchingWitness(int.class, int.class, "primitiveOperation");
      requireMatchingWitness(boolean.class, boolean.class, "booleanOperation");
      requireMatchingWitness(double.class, double.class, "doubleOperation");

      // Should fail for primitive vs wrapper
      assertThatThrownBy(
              () -> requireMatchingWitness(int.class, Integer.class, "primitiveOperation"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Witness type mismatch in primitiveOperation: expected int, got Integer");
    }

    @Test
    void shouldHandleWrapperTypes() {
      // Test with wrapper type classes
      requireMatchingWitness(Integer.class, Integer.class, "wrapperOperation");
      requireMatchingWitness(Boolean.class, Boolean.class, "booleanWrapperOp");
      requireMatchingWitness(Double.class, Double.class, "doubleWrapperOp");

      // Should fail for wrapper vs primitive
      assertThatThrownBy(() -> requireMatchingWitness(Integer.class, int.class, "wrapperOperation"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Witness type mismatch in wrapperOperation: expected Integer, got int");
    }

    @Test
    void shouldHandleArrayTypes() {
      // Test with array types
      requireMatchingWitness(String[].class, String[].class, "stringArrayOp");
      requireMatchingWitness(int[].class, int[].class, "intArrayOp");

      // Should fail for different array types
      assertThatThrownBy(
              () -> requireMatchingWitness(String[].class, Integer[].class, "arrayOperation"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Witness type mismatch in arrayOperation: expected String[], got Integer[]");
    }

    @Test
    void shouldHandleGenericTypes() {
      // Test with generic types (raw)
      @SuppressWarnings("rawtypes")
      Class<java.util.List> rawListClass = java.util.List.class;

      requireMatchingWitness(rawListClass, rawListClass, "genericOperation");

      // Note: Generic type parameters are erased at runtime, so List<String>.class ==
      // List<Integer>.class
      // This test verifies the basic class matching behavior
    }

    @Test
    void shouldHandleComplexOperationNames() {
      requireMatchingWitness(String.class, String.class, "complex-operation_with.dots");

      assertThatThrownBy(
              () ->
                  requireMatchingWitness(
                      String.class, Integer.class, "complex-operation_with.dots"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "Witness type mismatch in complex-operation_with.dots: expected String, got Integer");
    }

    @Test
    void shouldHandleVeryLongOperationNames() {
      String longOperationName = "VeryLongOperationName".repeat(100);
      requireMatchingWitness(String.class, String.class, longOperationName);

      assertThatThrownBy(
              () -> requireMatchingWitness(String.class, Integer.class, longOperationName))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "Witness type mismatch in " + longOperationName + ": expected String, got Integer");
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    void shouldWorkTogetherInComplexScenario() {
      // Test using both domain-specific helpers together
      Monad<Id.Witness> monad = IdMonad.instance();

      // Validate outer monad
      Monad<Id.Witness> validatedMonad = requireValidOuterMonad(monad, "ComplexTransformer");
      assertThat(validatedMonad).isSameAs(monad);

      // Validate witness types match
      requireMatchingWitness(Id.Witness.class, Id.Witness.class, "witnessValidation");

      // Should work without throwing any exceptions
    }

    @Test
    void shouldFailAppropriatelyInComplexScenario() {
      // Test failure scenarios when used together
      assertThatThrownBy(
              () -> {
                Monad<Id.Witness> validatedMonad =
                    requireValidOuterMonad(null, "ComplexTransformer");
                requireMatchingWitness(String.class, Integer.class, "witnessValidation");
              })
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHandleMultipleValidations() {
      Monad<Id.Witness> monad = IdMonad.instance();

      // Multiple validations should all pass
      requireValidOuterMonad(monad, "FirstTransformer");
      requireValidOuterMonad(monad, "SecondTransformer");
      requireValidOuterMonad(monad, "ThirdTransformer");

      requireMatchingWitness(String.class, String.class, "firstOperation");
      requireMatchingWitness(Integer.class, Integer.class, "secondOperation");
      requireMatchingWitness(Boolean.class, Boolean.class, "thirdOperation");
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Conditions")
  class EdgeCasesTests {

    @Test
    void shouldHandleRepeatedValidations() {
      Monad<Id.Witness> monad = IdMonad.instance();

      // Same validation repeated multiple times should work
      for (int i = 0; i < 100; i++) {
        requireValidOuterMonad(monad, "RepeatedTransformer" + i);
        requireMatchingWitness(String.class, String.class, "repeatedOperation" + i);
      }
    }

    @Test
    void shouldHandleUnicodeInNames() {
      Monad<Id.Witness> monad = IdMonad.instance();

      // Test with Unicode characters
      requireValidOuterMonad(monad, "类型Transformer");
      requireValidOuterMonad(monad, "τύποςTransformer");
      requireValidOuterMonad(monad, "типTransformer");

      requireMatchingWitness(String.class, String.class, "类型Operation");
      requireMatchingWitness(String.class, String.class, "τύποςOperation");
      requireMatchingWitness(String.class, String.class, "типOperation");
    }

    @Test
    void shouldHandleSpecialClassTypes() {
      // Test with Object class
      requireMatchingWitness(Object.class, Object.class, "objectOperation");

      // Test with Void class
      requireMatchingWitness(Void.class, Void.class, "voidOperation");

      // Test with Class class itself
      requireMatchingWitness(Class.class, Class.class, "classOperation");
    }
  }
}
