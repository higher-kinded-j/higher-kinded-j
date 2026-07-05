// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.typeclass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Update<S> - composable update function")
class UpdateTest {

  private static final Update<String> APPEND_A = s -> s + "a";
  private static final Update<String> APPEND_B = s -> s + "b";

  @Nested
  @DisplayName("identity()")
  class IdentityTests {

    @Test
    @DisplayName("should return its input unchanged")
    void shouldReturnInputUnchanged() {
      String value = "unchanged";

      assertThat(Update.<String>identity().apply(value)).isSameAs(value);
    }
  }

  @Nested
  @DisplayName("andThen(Update)")
  class AndThenTests {

    @Test
    @DisplayName("should apply this update first, then the after update")
    void shouldApplyLeftToRight() {
      assertThat(APPEND_A.andThen(APPEND_B).apply("")).isEqualTo("ab");
      assertThat(APPEND_B.andThen(APPEND_A).apply("")).isEqualTo("ba");
    }

    @Test
    @DisplayName("should stay an Update, so composition chains fluently")
    void shouldChainFluently() {
      Update<String> chained = APPEND_A.andThen(APPEND_B).andThen(APPEND_A);

      assertThat(chained.apply("")).isEqualTo("aba");
    }

    @Test
    @DisplayName("should compose with a plain UnaryOperator without wrapping, staying an Update")
    void shouldComposeWithPlainUnaryOperator() {
      UnaryOperator<String> trim = String::trim;

      Update<String> composed = APPEND_A.andThen(trim).andThen(APPEND_B);

      assertThat(composed.apply("  x ")).isEqualTo("x ab");
    }

    @Test
    @DisplayName("should reject a null after update")
    void shouldRejectNullAfter() {
      assertThatNullPointerException()
          .isThrownBy(() -> APPEND_A.andThen(null))
          .withMessage("after must not be null");
    }
  }

  @Nested
  @DisplayName("UnaryOperator interoperability")
  class InteropTests {

    @Test
    @DisplayName("should drop into Function-shaped APIs such as Stream.map")
    void shouldWorkAsUnaryOperator() {
      UnaryOperator<String> asOperator = APPEND_A;

      assertThat(Stream.of("x", "y").map(asOperator).toList()).containsExactly("xa", "ya");
    }
  }
}
