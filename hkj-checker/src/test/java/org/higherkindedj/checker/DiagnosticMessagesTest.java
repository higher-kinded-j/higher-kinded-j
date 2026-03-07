// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DiagnosticMessages")
class DiagnosticMessagesTest {

  @Nested
  @DisplayName("pathTypeMismatch")
  class PathTypeMismatch {

    @Test
    @DisplayName("includes method name, expected type, and actual type")
    void pathTypeMismatch_containsAllFields() {
      String message = DiagnosticMessages.pathTypeMismatch("via", "MaybePath", "IOPath");

      assertThat(message)
          .contains("via()")
          .contains("MaybePath")
          .contains("IOPath")
          .contains("Path type mismatch");
    }

    @Test
    @DisplayName("mentions conversion methods")
    void pathTypeMismatch_mentionsConversion() {
      String message = DiagnosticMessages.pathTypeMismatch("via", "MaybePath", "IOPath");

      assertThat(message).contains("conversion methods");
    }

    @Test
    @DisplayName("includes same-type chaining rule")
    void pathTypeMismatch_includesChainRule() {
      String message = DiagnosticMessages.pathTypeMismatch("zipWith", "MaybePath", "EitherPath");

      assertThat(message).contains("Each Path type can only chain with the same type");
    }

    @Test
    @DisplayName("suggests specific conversion when target has a known conversion method")
    void pathTypeMismatch_suggestsSpecificConversion() {
      String message = DiagnosticMessages.pathTypeMismatch("via", "EitherPath", "MaybePath");

      // MaybePath -> EitherPath has a known conversion: toEitherPath()
      assertThat(message).contains("toEitherPath()");
    }

    @Test
    @DisplayName("works for all checked methods")
    void pathTypeMismatch_allMethods() {
      for (String method : new String[] {"via", "then", "zipWith", "zipWith3", "recoverWith",
          "orElse"}) {
        String message = DiagnosticMessages.pathTypeMismatch(method, "MaybePath", "IOPath");
        assertThat(message)
            .as("Message for method %s should be non-empty", method)
            .isNotEmpty()
            .contains(method + "()");
      }
    }

    @Test
    @DisplayName("produces non-empty message for any input")
    void pathTypeMismatch_neverEmpty() {
      String message = DiagnosticMessages.pathTypeMismatch("custom", "TypeA", "TypeB");
      assertThat(message).isNotEmpty();
    }
  }
}
