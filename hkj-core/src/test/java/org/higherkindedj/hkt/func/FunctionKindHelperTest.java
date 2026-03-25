// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.func;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.func.FunctionKindHelper.FUNCTION;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FunctionKindHelper Tests")
class FunctionKindHelperTest {

  @Nested
  @DisplayName("widen/narrow round-trip")
  class RoundTripTests {

    @Test
    @DisplayName("narrow(widen(fn)) should return a FunctionKind wrapping the original function")
    void narrowWidenRoundTrip() {
      Function<String, Integer> fn = String::length;
      Kind2<FunctionKind.Witness, String, Integer> widened = FUNCTION.widen(fn);
      FunctionKind<String, Integer> narrowed = FUNCTION.narrow(widened);
      assertThat(narrowed.getFunction().apply("hello")).isEqualTo(5);
    }
  }

  @Nested
  @DisplayName("narrow() validation")
  class NarrowValidationTests {

    @Test
    @DisplayName("narrow(null) should throw NullPointerException")
    void narrow_null_shouldThrowNullPointerException() {
      assertThatNullPointerException()
          .isThrownBy(() -> FUNCTION.<String, Integer>narrow(null))
          .withMessageContaining("Cannot narrow null Kind2 to FunctionKind");
    }

    @Test
    @DisplayName("narrow(wrong type) should throw IllegalArgumentException")
    @SuppressWarnings("unchecked")
    void narrow_wrongType_shouldThrowIllegalArgumentException() {
      Kind2<FunctionKind.Witness, String, Integer> fakeKind =
          (Kind2<FunctionKind.Witness, String, Integer>) (Kind2<?, ?, ?>) new FakeKind2<>();
      assertThatThrownBy(() -> FUNCTION.narrow(fakeKind))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Expected FunctionKind but got");
    }
  }

  @Nested
  @DisplayName("getFunction()")
  class GetFunctionTests {

    @Test
    @DisplayName("getFunction should extract the underlying function")
    void getFunction_shouldExtractUnderlyingFunction() {
      Function<String, Integer> fn = String::length;
      Kind2<FunctionKind.Witness, String, Integer> widened = FUNCTION.widen(fn);
      Function<String, Integer> extracted = FUNCTION.getFunction(widened);
      assertThat(extracted.apply("test")).isEqualTo(4);
    }
  }

  /** A fake Kind2 implementation used to test type checking in narrow(). */
  private static final class FakeKind2<W extends WitnessArity<TypeArity.Binary>, A, B>
      implements Kind2<W, A, B> {}
}
