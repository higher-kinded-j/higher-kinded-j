// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ErrorHandling Constructor Tests")
class ErrorHandlingConstructorTest {

  @Test
  @DisplayName("ErrorHandling constructor should be private and throw AssertionError")
  void errorHandlingConstructorShouldThrowAssertionError() {
    assertThatThrownBy(
            () -> {
              // Use reflection to access private constructor
              var constructor = ErrorHandling.class.getDeclaredConstructor();
              constructor.setAccessible(true);
              constructor.newInstance();
            })
        .hasCauseInstanceOf(AssertionError.class)
        .getCause()
        .hasMessage("ErrorHandling is a utility class and should not be instantiated");
  }

  @Test
  @DisplayName("TypeMatchers constructor should be private and throw AssertionError")
  void typeMatchersConstructorShouldThrowAssertionError() {
    assertThatThrownBy(
            () -> {
              // Use reflection to access private constructor
              var constructor = ErrorHandling.TypeMatchers.class.getDeclaredConstructor();
              constructor.setAccessible(true);
              constructor.newInstance();
            })
        .hasCauseInstanceOf(AssertionError.class)
        .getCause()
        .hasMessage("TypeMatchers is a utility class and should not be instantiated");
  }
}
