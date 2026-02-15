// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOMonad;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OptionalTKindHelper Tests")
class OptionalTKindHelperTest {

  private Monad<IOKind.Witness> outerMonad;

  @BeforeEach
  void setUp() {
    outerMonad = IOMonad.INSTANCE;
  }

  private <A extends @NonNull Object> OptionalT<IOKind.Witness, A> createConcreteOptionalTSome(
      @NonNull A value) {
    return OptionalT.some(outerMonad, value);
  }

  private <A> OptionalT<IOKind.Witness, A> createConcreteOptionalTNone() {
    return OptionalT.none(outerMonad);
  }

  private <A> OptionalT<IOKind.Witness, A> createConcreteOptionalTOuterError() {
    RuntimeException ex = new RuntimeException("Outer IO failed");
    IO<Optional<A>> failingIO =
        () -> {
          throw ex;
        };
    Kind<IOKind.Witness, Optional<A>> failingKind = IO_OP.widen(failingIO);
    return OptionalT.fromKind(failingKind);
  }

  @Nested
  @DisplayName("wrap() tests")
  class WrapTests {
    @Test
    @DisplayName("should wrap a non-null OptionalT (Some) into a Kind<OptionalTKind.Witness<F>,A>")
    void widen_nonNullOptionalTSome_shouldReturnOptionalTKind() {
      OptionalT<IOKind.Witness, String> concreteOptionalT = createConcreteOptionalTSome("test");
      Kind<OptionalTKind.Witness<IOKind.Witness>, String> wrapped =
          OPTIONAL_T.widen(concreteOptionalT);

      assertThat(wrapped).isNotNull().isInstanceOf(OptionalT.class);
      assertThat(OPTIONAL_T.narrow(wrapped)).isSameAs(concreteOptionalT);
    }

    @Test
    @DisplayName("should wrap a non-null OptionalT (None) into a Kind<OptionalTKind.Witness<F>,A>")
    void widen_nonNullOptionalTNone_shouldReturnOptionalTKind() {
      OptionalT<IOKind.Witness, String> concreteOptionalT = createConcreteOptionalTNone();
      Kind<OptionalTKind.Witness<IOKind.Witness>, String> wrapped =
          OPTIONAL_T.widen(concreteOptionalT);

      // Assert it's an instance of OptionalT
      assertThat(wrapped).isNotNull().isInstanceOf(OptionalT.class);
      assertThat(OPTIONAL_T.narrow(wrapped)).isSameAs(concreteOptionalT);
    }

    @Test
    @DisplayName("should throw NullPointerException when wrapping null")
    void widen_nullOptionalT_shouldThrowNullPointerException() {
      assertThatThrownBy(() -> OPTIONAL_T.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Input %s cannot be null for widen".formatted("OptionalT"));
    }
  }

  @Nested
  @DisplayName("unwrap() tests")
  class UnwrapTests {
    @Test
    @DisplayName("should unwrap a valid Kind (Some) to the original OptionalT instance")
    void narrow_validKindSome_shouldReturnOptionalT() {
      OptionalT<IOKind.Witness, Integer> originalOptionalT = createConcreteOptionalTSome(456);
      Kind<OptionalTKind.Witness<IOKind.Witness>, Integer> wrappedKind =
          OPTIONAL_T.widen(originalOptionalT);

      OptionalT<IOKind.Witness, Integer> unwrappedOptionalT = OPTIONAL_T.narrow(wrappedKind);

      assertThat(unwrappedOptionalT).isNotNull().isSameAs(originalOptionalT);
      assertThat(IO_OP.unsafeRunSync(unwrappedOptionalT.value())).contains(456);
    }

    @Test
    @DisplayName("should unwrap a valid Kind (None) to the original OptionalT instance")
    void narrow_validKindNone_shouldReturnOptionalT() {
      OptionalT<IOKind.Witness, String> originalOptionalT = createConcreteOptionalTNone();
      Kind<OptionalTKind.Witness<IOKind.Witness>, String> wrappedKind =
          OPTIONAL_T.widen(originalOptionalT);
      OptionalT<IOKind.Witness, String> unwrappedOptionalT = OPTIONAL_T.narrow(wrappedKind);

      assertThat(unwrappedOptionalT).isSameAs(originalOptionalT);
      assertThat(IO_OP.unsafeRunSync(unwrappedOptionalT.value())).isEmpty();
    }

    @Test
    @DisplayName("should unwrap a valid Kind (OuterError) to the original OptionalT instance")
    void narrow_validKindOuterError_shouldReturnOptionalT() {
      OptionalT<IOKind.Witness, Double> originalOptionalT = createConcreteOptionalTOuterError();
      Kind<OptionalTKind.Witness<IOKind.Witness>, Double> wrappedKind =
          OPTIONAL_T.widen(originalOptionalT);
      OptionalT<IOKind.Witness, Double> unwrappedOptionalT = OPTIONAL_T.narrow(wrappedKind);

      assertThat(unwrappedOptionalT).isSameAs(originalOptionalT);
      assertThatThrownBy(() -> IO_OP.unsafeRunSync(unwrappedOptionalT.value()))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Outer IO failed");
    }

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping null")
    void narrow_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> OPTIONAL_T.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Cannot narrow null Kind for %s".formatted("OptionalT"));
    }

    // Dummy Kind for testing invalid type unwrap
    private static class OtherKindWitness<F_Witness> implements WitnessArity<TypeArity.Unary> {}

    private static class OtherKind<F_Witness, A> implements Kind<OtherKindWitness<F_Witness>, A> {}

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping an incorrect Kind type")
    void narrow_incorrectKindType_shouldThrowKindUnwrapException() {
      OtherKind<IOKind.Witness, String> incorrectKind = new OtherKind<>();

      @SuppressWarnings({"unchecked", "rawtypes"})
      Kind<OptionalTKind.Witness<IOKind.Witness>, String> kindToTest =
          (Kind<OptionalTKind.Witness<IOKind.Witness>, String>) (Kind) incorrectKind;

      assertThatThrownBy(() -> OPTIONAL_T.narrow(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              "Kind instance cannot be narrowed to " + OptionalT.class.getSimpleName());
    }

    @Test
    @DisplayName("unwrap should correctly infer types for F and A")
    void narrow_typeInference() {
      OptionalT<IOKind.Witness, Integer> concreteInt = createConcreteOptionalTSome(789);
      Kind<OptionalTKind.Witness<IOKind.Witness>, Integer> wrappedInt =
          OPTIONAL_T.widen(concreteInt);
      OptionalT<IOKind.Witness, Integer> unwrappedInt = OPTIONAL_T.narrow(wrappedInt);
      assertThat(IO_OP.unsafeRunSync(unwrappedInt.value())).contains(789);

      OptionalT<IOKind.Witness, Boolean> concreteBool = createConcreteOptionalTSome(true);
      Kind<OptionalTKind.Witness<IOKind.Witness>, Boolean> wrappedBool =
          OPTIONAL_T.widen(concreteBool);
      OptionalT<IOKind.Witness, Boolean> unwrappedBool = OPTIONAL_T.narrow(wrappedBool);
      assertThat(IO_OP.unsafeRunSync(unwrappedBool.value())).contains(true);
    }
  }
}
