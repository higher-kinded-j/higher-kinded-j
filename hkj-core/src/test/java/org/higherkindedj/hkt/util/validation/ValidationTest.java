// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Validation")
class ValidationTest {

  @Test
  @DisplayName("should provide CoreTypeValidator instance")
  void shouldProvideCoreTypeValidator() {
    var validator = Validation.coreType();

    assertThat(validator).isNotNull();
    assertThat(validator).isEqualTo(CoreTypeValidator.CORE_TYPE_VALIDATOR);
  }

  @Test
  @DisplayName("should provide FunctionValidator instance")
  void shouldProvideFunctionValidator() {
    var validator = Validation.function();

    assertThat(validator).isNotNull();
    assertThat(validator).isEqualTo(FunctionValidator.FUNCTION_VALIDATOR);
  }

  @Test
  @DisplayName("should provide KindValidator instance")
  void shouldProvideKindValidator() {
    var validator = Validation.kind();

    assertThat(validator).isNotNull();
    assertThat(validator).isEqualTo(KindValidator.KIND_VALIDATOR);
  }

  @Test
  @DisplayName("should provide TransformerValidator instance")
  void shouldProvideTransformerValidator() {
    var validator = Validation.transformer();

    assertThat(validator).isNotNull();
    assertThat(validator).isEqualTo(TransformerValidator.TRANSFORMER_VALIDATOR);
  }

  @Test
  @DisplayName("should return same CoreTypeValidator instance on multiple calls")
  void shouldReturnSameCoreTypeValidatorInstance() {
    var validator1 = Validation.coreType();
    var validator2 = Validation.coreType();

    assertThat(validator1).isSameAs(validator2);
  }

  @Test
  @DisplayName("should return same FunctionValidator instance on multiple calls")
  void shouldReturnSameFunctionValidatorInstance() {
    var validator1 = Validation.function();
    var validator2 = Validation.function();

    assertThat(validator1).isSameAs(validator2);
  }

  @Test
  @DisplayName("should return same KindValidator instance on multiple calls")
  void shouldReturnSameKindValidatorInstance() {
    var validator1 = Validation.kind();
    var validator2 = Validation.kind();

    assertThat(validator1).isSameAs(validator2);
  }

  @Test
  @DisplayName("should return same TransformerValidator instance on multiple calls")
  void shouldReturnSameTransformerValidatorInstance() {
    var validator1 = Validation.transformer();
    var validator2 = Validation.transformer();

    assertThat(validator1).isSameAs(validator2);
  }
}
