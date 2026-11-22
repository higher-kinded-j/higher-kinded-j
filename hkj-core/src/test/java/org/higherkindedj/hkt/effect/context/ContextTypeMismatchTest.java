// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ContextTypeMismatch: the message a context throws when via() gets another context")
class ContextTypeMismatchTest {

  private static final String FLAT_MAP = ", or chain with flatMap(), which javac checks.";

  @Test
  @DisplayName("The message names via(), both context types and the expected context's factories")
  void wordsTheMismatch() {
    assertThat(ContextTypeMismatch.via(ConfigContext.class, ErrorContext.success(1)))
        .hasMessage(
            "Type mismatch in via(): expected ConfigContext but function returned ErrorContext."
                + " Each context type can only chain with the same type."
                + " Build the step with ConfigContext's factory methods"
                + FLAT_MAP);
  }

  @Test
  @DisplayName(
      "An optional context returned where an ErrorContext is expected names toErrorContext")
  void namesToErrorContext() {
    assertThat(ContextTypeMismatch.via(ErrorContext.class, OptionalContext.some(1)))
        .hasMessageEndingWith(" Convert the step with toErrorContext" + FLAT_MAP);
    assertThat(ContextTypeMismatch.via(ErrorContext.class, JavaOptionalContext.some(1)))
        .hasMessageEndingWith(" Convert the step with toErrorContext" + FLAT_MAP);
  }

  @Test
  @DisplayName(
      "A JavaOptionalContext returned where an OptionalContext is expected names its conversion")
  void namesToOptionalContext() {
    assertThat(ContextTypeMismatch.via(OptionalContext.class, JavaOptionalContext.some(1)))
        .hasMessageEndingWith(" Convert the step with toOptionalContext" + FLAT_MAP);
  }

  @Test
  @DisplayName("A pair with no conversion between them names the expected context's factories")
  void namesFactoriesWithoutConversion() {
    assertThat(ContextTypeMismatch.via(ErrorContext.class, ConfigContext.pure(1)))
        .hasMessageEndingWith(" Build the step with ErrorContext's factory methods" + FLAT_MAP);
    assertThat(ContextTypeMismatch.via(OptionalContext.class, ErrorContext.success(1)))
        .hasMessageEndingWith(" Build the step with OptionalContext's factory methods" + FLAT_MAP);
    assertThat(ContextTypeMismatch.via(MutableContext.class, OptionalContext.some(1)))
        .hasMessageEndingWith(" Build the step with MutableContext's factory methods" + FLAT_MAP);
  }
}
