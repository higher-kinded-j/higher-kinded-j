// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;
import static org.higherkindedj.hkt.util.ErrorHandling.INVALID_KIND_TYPE_TEMPLATE;
import static org.higherkindedj.hkt.util.ErrorHandling.NULL_KIND_TEMPLATE;
import static org.higherkindedj.hkt.util.ErrorHandling.NULL_WIDEN_INPUT_TEMPLATE;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReaderTKindHelper Tests (Outer: OptionalKind.Witness, Env: String)")
class ReaderTKindHelperTest {

  // Environment type R_ENV is String for these tests
  private final String testEnv = "testEnvironment";

  // Helper to create a ReaderT for testing
  // F = OptionalKind.Witness, R_ENV = String
  private <A> ReaderT<OptionalKind.Witness, String, A> createReaderT(
      Function<String, Kind<OptionalKind.Witness, A>> runFn) {
    return ReaderT.of(runFn);
  }

  @Nested
  @DisplayName("READER_T.widen() tests")
  class WrapTests {
    @Test
    @DisplayName("should wrap a non-null ReaderT into a Kind<ReaderTKind.Witness<F,R>,A>")
    void widen_nonNullReaderT_shouldReturnReaderTKind() {
      ReaderT<OptionalKind.Witness, String, Integer> concreteReaderT =
          createReaderT(env -> OPTIONAL.widen(Optional.of(env.length())));

      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> wrapped =
          READER_T.widen(concreteReaderT);

      assertThat(wrapped).isNotNull().isInstanceOf(ReaderT.class);
      assertThat(READER_T.narrow(wrapped)).isSameAs(concreteReaderT);
    }

    @Test
    @DisplayName("should throw NullPointerException when wrapping null")
    void widen_nullReaderT_shouldThrowNullPointerException() {
      assertThatThrownBy(() -> READER_T.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage(NULL_WIDEN_INPUT_TEMPLATE.formatted("ReaderT"));
    }
  }

  @Nested
  @DisplayName("READER_T.narrow() tests")
  class UnwrapTests {
    @Test
    @DisplayName("should unwrap a valid Kind to the original ReaderT instance")
    void narrow_validKind_shouldReturnReaderT() {
      ReaderT<OptionalKind.Witness, String, Integer> originalReaderT =
          createReaderT(env -> OPTIONAL.widen(Optional.of(env.hashCode())));
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> wrappedKind =
          READER_T.widen(originalReaderT);

      ReaderT<OptionalKind.Witness, String, Integer> unwrappedReaderT =
          READER_T.narrow(wrappedKind);

      assertThat(unwrappedReaderT).isSameAs(originalReaderT);
    }

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping null")
    void narrow_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> READER_T.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(NULL_KIND_TEMPLATE.formatted("ReaderT"));
    }

    // Dummy Kind for testing invalid type unwrap
    // F is outer monad witness, R_ENV is environment type
    private static class OtherKindWitness<F_Witness, R_ENV_Witness> {}

    private static class OtherKind<F, R_ENV, A> implements Kind<OtherKindWitness<F, R_ENV>, A> {}

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping an incorrect Kind type")
    void narrow_incorrectKindType_shouldThrowKindUnwrapException() {
      class SimpleIncorrectKind<A> implements Kind<SimpleIncorrectKind<?>, A> {}
      SimpleIncorrectKind<Integer> incorrectKind = new SimpleIncorrectKind<>();

      @SuppressWarnings({"unchecked", "rawtypes"})
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> kindToTest =
          (Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer>) (Kind) incorrectKind;

      assertThatThrownBy(() -> READER_T.narrow(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageStartingWith(INVALID_KIND_TYPE_TEMPLATE.formatted("ReaderT", ""))
          .hasMessageContaining(SimpleIncorrectKind.class.getName());
    }
  }
}
