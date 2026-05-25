// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.io.IOException;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.TryAssert}. */
@DisplayName("TryAssert showcase")
class TryAssertExample {

  @Test
  @DisplayName("Success is asserted with isSuccess() and hasValue()")
  void successCase() {
    Try<Integer> result = Try.success(7);

    assertThatTry(result).isSuccess().hasValue(7);
  }

  @Test
  @DisplayName("Failure is matched on exception type with hasExceptionOfType()")
  void failureType() {
    Try<String> result = Try.failure(new IOException("disk full"));

    assertThatTry(result).isFailure().hasExceptionOfType(IOException.class);
  }

  @Test
  @DisplayName("hasExceptionSatisfying() lets you assert on message and cause")
  void failureSatisfying() {
    Try<String> result =
        Try.failure(new IllegalArgumentException("expected positive", new ArithmeticException()));

    assertThatTry(result)
        .isFailure()
        .hasExceptionSatisfying(
            ex -> {
              assertThat(ex).hasMessage("expected positive");
              assertThat(ex.getCause()).isInstanceOf(ArithmeticException.class);
            });
  }

  @Test
  @DisplayName("hasValueSatisfying() runs further AssertJ checks on the success value")
  void valueSatisfying() {
    Try<String> result = Try.of(() -> "abc".toUpperCase());

    assertThatTry(result).isSuccess().hasValueSatisfying(s -> assertThat(s).isEqualTo("ABC"));
  }

  @Test
  @DisplayName("Accepts Kind<TryKind.Witness, T> directly without manual narrowing")
  void acceptsKindDirectly() {
    Kind<TryKind.Witness, Integer> kind = TRY.widen(Try.success(11));

    assertThatTry(kind).isSuccess().hasValue(11);
  }
}
