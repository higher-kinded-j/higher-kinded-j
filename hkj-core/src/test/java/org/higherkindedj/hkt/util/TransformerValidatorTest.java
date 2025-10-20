// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.util.validation.Operation.*;
import static org.higherkindedj.hkt.util.validation.TransformerValidator.TRANSFORMER_VALIDATOR;

import org.higherkindedj.hkt.Monad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TransformerValidator")
class TransformerValidatorTest {

  private static final class StateT {}

  private static final class OptionalT {}

  private static final Monad<?> TEST_MONAD = createTestMonad();

  private static Monad<?> createTestMonad() {
    // Simple test monad implementation
    return new Monad<>() {
      @Override
      public Object of(Object value) {
        return value;
      }

      @Override
      public Object ap(Object ff, Object fa) {
        return null;
      }

      @Override
      public Object map(java.util.function.Function f, Object fa) {
        return null;
      }

      @Override
      public Object flatMap(java.util.function.Function f, Object ma) {
        return null;
      }
    };
  }

  @Nested
  @DisplayName("requireOuterMonad")
  class RequireOuterMonad {

    @Test
    @DisplayName("should return non-null outer monad")
    void shouldReturnNonNullOuterMonad() {
      var result = TRANSFORMER_VALIDATOR.requireOuterMonad(TEST_MONAD, StateT.class, CONSTRUCTION);

      assertThat(result).isEqualTo(TEST_MONAD);
    }

    @Test
    @DisplayName("should throw NullPointerException when monad is null")
    void shouldThrowWhenMonadIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> TRANSFORMER_VALIDATOR.requireOuterMonad(null, StateT.class, CONSTRUCTION))
          .withMessage("Transformer Monad cannot be null for StateT construction");
    }

    @Test
    @DisplayName("should include transformer name in error message")
    void shouldIncludeTransformerNameInErrorMessage() {
      assertThatNullPointerException()
          .isThrownBy(() -> TRANSFORMER_VALIDATOR.requireOuterMonad(null, OptionalT.class, LIFT_F))
          .withMessageContaining("OptionalT");
    }

    @Test
    @DisplayName("should include operation in error message")
    void shouldIncludeOperationInErrorMessage() {
      assertThatNullPointerException()
          .isThrownBy(() -> TRANSFORMER_VALIDATOR.requireOuterMonad(null, StateT.class, LIFT_F))
          .withMessageContaining("liftF");
    }

    @Test
    @DisplayName("should throw NullPointerException when transformer class is null")
    void shouldThrowWhenTransformerClassIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> TRANSFORMER_VALIDATOR.requireOuterMonad(TEST_MONAD, null, CONSTRUCTION))
          .withMessage("transformerClass cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when method name is null")
    void shouldThrowWhenMethodNameIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> TRANSFORMER_VALIDATOR.requireOuterMonad(TEST_MONAD, StateT.class, null))
          .withMessage("methodName cannot be null");
    }
  }

  @Nested
  @DisplayName("requireTransformerComponent")
  class RequireTransformerComponent {

    @Test
    @DisplayName("should return non-null component")
    void shouldReturnNonNullComponent() {
      var component = "test-component";
      var result =
          TRANSFORMER_VALIDATOR.requireTransformerComponent(
              component, "inner Optional", OptionalT.class, FROM_OPTIONAL);

      assertThat(result).isEqualTo(component);
    }

    @Test
    @DisplayName("should throw NullPointerException when component is null")
    void shouldThrowWhenComponentIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  TRANSFORMER_VALIDATOR.requireTransformerComponent(
                      null, "inner Optional", OptionalT.class, FROM_OPTIONAL))
          .withMessage("inner Optional cannot be null for OptionalT.fromOptional");
    }

    @Test
    @DisplayName("should include component name in error message")
    void shouldIncludeComponentNameInErrorMessage() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  TRANSFORMER_VALIDATOR.requireTransformerComponent(
                      null, "state function", StateT.class, STATE_T))
          .withMessageContaining("state function");
    }

    @Test
    @DisplayName("should include transformer name in error message")
    void shouldIncludeTransformerNameInErrorMessage() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  TRANSFORMER_VALIDATOR.requireTransformerComponent(
                      null, "inner Optional", OptionalT.class, FROM_OPTIONAL))
          .withMessageContaining("OptionalT");
    }

    @Test
    @DisplayName("should include operation in error message")
    void shouldIncludeOperationInErrorMessage() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  TRANSFORMER_VALIDATOR.requireTransformerComponent(
                      null, "inner Optional", OptionalT.class, FROM_OPTIONAL))
          .withMessageContaining("fromOptional");
    }

    @Test
    @DisplayName("should throw NullPointerException when transformer class is null")
    void shouldThrowWhenTransformerClassIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  TRANSFORMER_VALIDATOR.requireTransformerComponent(
                      "component", "inner Optional", null, FROM_OPTIONAL))
          .withMessage("transformerClass cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when method name is null")
    void shouldThrowWhenMethodNameIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  TRANSFORMER_VALIDATOR.requireTransformerComponent(
                      "component", "inner Optional", OptionalT.class, null))
          .withMessage("methodName cannot be null");
    }

    @Test
    @DisplayName("should work with different component types")
    void shouldWorkWithDifferentComponentTypes() {
      var intComponent = 42;
      var result =
          TRANSFORMER_VALIDATOR.requireTransformerComponent(
              intComponent, "state value", StateT.class, STATE_T);

      assertThat(result).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("DomainContext")
  class DomainContextTest {

    @Test
    @DisplayName("should create transformer context")
    void shouldCreateTransformerContext() {
      var context = TransformerValidator.DomainContext.transformer("StateT.construction");

      assertThat(context.domainType()).isEqualTo("transformer");
      assertThat(context.objectName()).isEqualTo("StateT.construction");
    }

    @Test
    @DisplayName("should create witness context")
    void shouldCreateWitnessContext() {
      var context = TransformerValidator.DomainContext.witness("map");

      assertThat(context.domainType()).isEqualTo("witness");
      assertThat(context.objectName()).isEqualTo("map");
    }

    @Test
    @DisplayName("should generate correct null parameter message for transformer")
    void shouldGenerateCorrectNullParameterMessageForTransformer() {
      var context = new TransformerValidator.DomainContext("transformer", "StateT construction");

      assertThat(context.nullParameterMessage())
          .isEqualTo("Transformer  cannot be null for StateT construction");
    }

    @Test
    @DisplayName("should generate correct null parameter message for witness")
    void shouldGenerateCorrectNullParameterMessageForWitness() {
      var context = new TransformerValidator.DomainContext("witness", "map");

      assertThat(context.nullParameterMessage()).isEqualTo("Witness Monad cannot be null for map");
    }

    @Test
    @DisplayName("should capitalise domain type in message")
    void shouldCapitaliseDomainTypeInMessage() {
      var context = new TransformerValidator.DomainContext("transformer", "operation");

      assertThat(context.nullParameterMessage()).startsWith("Transformer");
    }

    @Test
    @DisplayName("should throw NullPointerException when domain type is null")
    void shouldThrowWhenDomainTypeIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> new TransformerValidator.DomainContext(null, "objectName"))
          .withMessage("domainType cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when object name is null")
    void shouldThrowWhenObjectNameIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> new TransformerValidator.DomainContext("transformer", null))
          .withMessage("objectName cannot be null");
    }
  }
}
