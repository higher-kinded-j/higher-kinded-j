// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.maybe_t.MaybeTKindHelper.MAYBE_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.util.ErrorHandling.*;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeTKindHelper Tests")
class MaybeTKindHelperTest {

  private Monad<OptionalKind.Witness> optionalOuterMonad;
  private Monad<IOKind.Witness> ioOuterMonad;

  @BeforeEach
  void setUp() {
    optionalOuterMonad = OptionalMonad.INSTANCE;
    ioOuterMonad = IOMonad.INSTANCE;
  }

  private <A extends @NonNull Object> MaybeT<OptionalKind.Witness, A> createConcreteMaybeTSomeOpt(
      @NonNull A value) {
    return MaybeT.just(optionalOuterMonad, value);
  }

  private <A> MaybeT<OptionalKind.Witness, A> createConcreteMaybeTNothingOpt() {
    return MaybeT.nothing(optionalOuterMonad);
  }

  private <A> MaybeT<OptionalKind.Witness, A> createConcreteMaybeTOuterEmptyOpt() {
    Kind<OptionalKind.Witness, Maybe<A>> emptyOuter = OPTIONAL.widen(Optional.empty());
    return MaybeT.fromKind(emptyOuter);
  }

  private <A extends @NonNull Object> MaybeT<IOKind.Witness, A> createConcreteMaybeTSomeIO(
      @NonNull A value) {
    return MaybeT.just(ioOuterMonad, value);
  }

  @Nested
  @DisplayName("wrap() tests")
  class WrapTests {
    @Test
    @DisplayName(
        "should wrap a non-null MaybeT (Some) into a Kind<MaybeTKind.Witness<F>, A> (Outer"
            + " Optional)")
    void widen_nonNullMaybeTSome_OptionalOuter_shouldReturnMaybeTKind() {
      MaybeT<OptionalKind.Witness, String> concreteMaybeT = createConcreteMaybeTSomeOpt("test");
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> wrapped =
          MAYBE_T.widen(concreteMaybeT);

      assertThat(wrapped).isNotNull().isInstanceOf(MaybeT.class);
      // Unwrap should still yield the same instance
      assertThat(MAYBE_T.narrow(wrapped)).isSameAs(concreteMaybeT);
    }

    @Test
    @DisplayName(
        "should wrap a non-null MaybeT (Some) into a Kind<MaybeTKind.Witness<F>, A> (Outer IO)")
    void widen_nonNullMaybeTSome_IOOuter_shouldReturnMaybeTKind() {
      MaybeT<IOKind.Witness, String> concreteMaybeT = createConcreteMaybeTSomeIO("testIO");
      Kind<MaybeTKind.Witness<IOKind.Witness>, String> wrapped = MAYBE_T.widen(concreteMaybeT);
      assertThat(wrapped).isNotNull().isInstanceOf(MaybeT.class);
      assertThat(MAYBE_T.narrow(wrapped)).isSameAs(concreteMaybeT);
    }

    @Test
    @DisplayName("should throw NullPointerException when wrapping null")
    void widen_nullMaybeT_shouldThrowNullPointerException() {
      assertThatThrownBy(() -> MAYBE_T.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage(NULL_WIDEN_INPUT_TEMPLATE.formatted(MaybeT.class.getSimpleName()));
    }
  }

  @Nested
  @DisplayName("unwrap() tests")
  class UnwrapTests {
    @Test
    @DisplayName(
        "should unwrap a valid Kind<MaybeTKind.Witness<F>, A> (Some) to the original MaybeT"
            + " instance (Outer Optional)")
    void narrow_validKindSome_OptionalOuter_shouldReturnMaybeT() {
      MaybeT<OptionalKind.Witness, String> originalMaybeT = createConcreteMaybeTSomeOpt("hello");
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> wrappedKind =
          MAYBE_T.widen(originalMaybeT);

      MaybeT<OptionalKind.Witness, String> unwrappedMaybeT = MAYBE_T.narrow(wrappedKind);

      assertThat(unwrappedMaybeT).isSameAs(originalMaybeT);
    }

    @Test
    @DisplayName(
        "should unwrap a valid Kind<MaybeTKind.Witness<F>, A> (Nothing) to the original MaybeT"
            + " instance (Outer Optional)")
    void narrow_validKindNothing_OptionalOuter_shouldReturnMaybeT() {
      MaybeT<OptionalKind.Witness, String> originalMaybeT = createConcreteMaybeTNothingOpt();
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> wrappedKind =
          MAYBE_T.widen(originalMaybeT);
      MaybeT<OptionalKind.Witness, String> unwrappedMaybeT = MAYBE_T.narrow(wrappedKind);
      assertThat(unwrappedMaybeT).isSameAs(originalMaybeT);
    }

    @Test
    @DisplayName(
        "should unwrap a valid Kind<MaybeTKind.Witness<F>, A> (OuterEmpty) to original MaybeT"
            + " instance (Outer Optional)")
    void narrow_validKindOuterEmpty_OptionalOuter_shouldReturnMaybeT() {
      MaybeT<OptionalKind.Witness, String> originalMaybeT = createConcreteMaybeTOuterEmptyOpt();
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> wrappedKind =
          MAYBE_T.widen(originalMaybeT);
      MaybeT<OptionalKind.Witness, String> unwrappedMaybeT = MAYBE_T.narrow(wrappedKind);
      assertThat(unwrappedMaybeT).isSameAs(originalMaybeT);
    }

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping null")
    void narrow_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> MAYBE_T.<OptionalKind.Witness, String>narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(NULL_KIND_TEMPLATE.formatted("MaybeT"));
    }

    private static class OtherKindWitness<F_Witness> {}

    private static class OtherKind<F_Witness, A> implements Kind<OtherKindWitness<F_Witness>, A> {}

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping an incorrect Kind type")
    void narrow_incorrectKindType_shouldThrowKindUnwrapException() {
      OtherKind<OptionalKind.Witness, String> incorrectKind = new OtherKind<>();

      @SuppressWarnings({"unchecked", "rawtypes"})
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> kindToTest =
          (Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>) (Kind) incorrectKind;

      assertThatThrownBy(() -> MAYBE_T.narrow(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              INVALID_KIND_TYPE_TEMPLATE.formatted("MaybeT", OtherKind.class.getName()));
    }

    @Test
    @DisplayName("unwrap should correctly infer types (Outer Optional)")
    void narrow_typeInference_OptionalOuter() {
      MaybeT<OptionalKind.Witness, Integer> concrete = MaybeT.just(optionalOuterMonad, 123);
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> wrapped = MAYBE_T.widen(concrete);

      MaybeT<OptionalKind.Witness, Integer> unwrapped = MAYBE_T.narrow(wrapped);
      Optional<Maybe<Integer>> result = OPTIONAL.narrow(unwrapped.value());
      assertThat(result).isPresent().contains(Maybe.just(123));
    }

    @Test
    @DisplayName("unwrap should correctly infer types (Outer IO)")
    void narrow_typeInference_IOOuter() {
      MaybeT<IOKind.Witness, Boolean> concreteBool = MaybeT.just(ioOuterMonad, true);
      Kind<MaybeTKind.Witness<IOKind.Witness>, Boolean> wrappedBool = MAYBE_T.widen(concreteBool);
      MaybeT<IOKind.Witness, Boolean> unwrappedBool = MAYBE_T.narrow(wrappedBool);
      Maybe<Boolean> resultIO = IO_OP.unsafeRunSync(unwrappedBool.value());
      assertThat(resultIO).isEqualTo(Maybe.just(true));
    }
  }
}
