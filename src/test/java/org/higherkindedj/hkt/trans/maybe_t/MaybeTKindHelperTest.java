package org.higherkindedj.hkt.trans.maybe_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
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
    optionalOuterMonad = new OptionalMonad();
    ioOuterMonad = new IOMonad();
  }

  private <A extends @NonNull Object> MaybeT<OptionalKind.Witness, A> createConcreteMaybeTSomeOpt(
      @NonNull A value) {
    return MaybeT.just(optionalOuterMonad, value);
  }

  private <A> MaybeT<OptionalKind.Witness, A> createConcreteMaybeTNothingOpt() {
    return MaybeT.nothing(optionalOuterMonad);
  }

  private <A> MaybeT<OptionalKind.Witness, A> createConcreteMaybeTOuterEmptyOpt() {
    Kind<OptionalKind.Witness, Maybe<A>> emptyOuter = OptionalKindHelper.wrap(Optional.empty());
    return MaybeT.fromKind(emptyOuter);
  }

  private <A extends @NonNull Object> MaybeT<IOKind.Witness, A> createConcreteMaybeTSomeIO(
      @NonNull A value) {
    return MaybeT.just(ioOuterMonad, value);
  }

  @Test
  @DisplayName("private constructor should prevent instantiation")
  void privateConstructor_shouldThrowException() {
    assertThatThrownBy(
            () -> {
              Constructor<MaybeTKindHelper> constructor =
                  MaybeTKindHelper.class.getDeclaredConstructor();
              constructor.setAccessible(true);
              try {
                constructor.newInstance();
              } catch (InvocationTargetException e) {
                throw e.getCause(); // Throw the actual cause
              }
            })
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("This is a utility class and cannot be instantiated");
  }

  @Nested
  @DisplayName("wrap() tests")
  class WrapTests {
    @Test
    @DisplayName(
        "should wrap a non-null MaybeT (Some) into a Kind<MaybeTKind.Witness<F>, A> (Outer"
            + " Optional)")
    void wrap_nonNullMaybeTSome_OptionalOuter_shouldReturnMaybeTKind() {
      MaybeT<OptionalKind.Witness, String> concreteMaybeT = createConcreteMaybeTSomeOpt("test");
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> wrapped =
          MaybeTKindHelper.wrap(concreteMaybeT);

      assertThat(wrapped).isNotNull().isInstanceOf(MaybeT.class);
      // Unwrap should still yield the same instance
      assertThat(MaybeTKindHelper.<OptionalKind.Witness, String>unwrap(wrapped))
          .isSameAs(concreteMaybeT);
    }

    @Test
    @DisplayName(
        "should wrap a non-null MaybeT (Some) into a Kind<MaybeTKind.Witness<F>, A> (Outer IO)")
    void wrap_nonNullMaybeTSome_IOOuter_shouldReturnMaybeTKind() {
      MaybeT<IOKind.Witness, String> concreteMaybeT = createConcreteMaybeTSomeIO("testIO");
      Kind<MaybeTKind.Witness<IOKind.Witness>, String> wrapped =
          MaybeTKindHelper.wrap(concreteMaybeT);
      assertThat(wrapped).isNotNull().isInstanceOf(MaybeT.class);
      assertThat(MaybeTKindHelper.<IOKind.Witness, String>unwrap(wrapped)).isSameAs(concreteMaybeT);
    }

    @Test
    @DisplayName("should throw NullPointerException when wrapping null")
    void wrap_nullMaybeT_shouldThrowNullPointerException() {
      assertThatThrownBy(() -> MaybeTKindHelper.wrap(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Input MaybeT cannot be null for wrap");
    }
  }

  @Nested
  @DisplayName("unwrap() tests")
  class UnwrapTests {
    @Test
    @DisplayName(
        "should unwrap a valid Kind<MaybeTKind.Witness<F>, A> (Some) to the original MaybeT"
            + " instance (Outer Optional)")
    void unwrap_validKindSome_OptionalOuter_shouldReturnMaybeT() {
      MaybeT<OptionalKind.Witness, String> originalMaybeT = createConcreteMaybeTSomeOpt("hello");
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> wrappedKind =
          MaybeTKindHelper.wrap(originalMaybeT);

      MaybeT<OptionalKind.Witness, String> unwrappedMaybeT = MaybeTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedMaybeT).isSameAs(originalMaybeT);
    }

    @Test
    @DisplayName(
        "should unwrap a valid Kind<MaybeTKind.Witness<F>, A> (Nothing) to the original MaybeT"
            + " instance (Outer Optional)")
    void unwrap_validKindNothing_OptionalOuter_shouldReturnMaybeT() {
      MaybeT<OptionalKind.Witness, String> originalMaybeT = createConcreteMaybeTNothingOpt();
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> wrappedKind =
          MaybeTKindHelper.wrap(originalMaybeT);
      MaybeT<OptionalKind.Witness, String> unwrappedMaybeT = MaybeTKindHelper.unwrap(wrappedKind);
      assertThat(unwrappedMaybeT).isSameAs(originalMaybeT);
    }

    @Test
    @DisplayName(
        "should unwrap a valid Kind<MaybeTKind.Witness<F>, A> (OuterEmpty) to original MaybeT"
            + " instance (Outer Optional)")
    void unwrap_validKindOuterEmpty_OptionalOuter_shouldReturnMaybeT() {
      MaybeT<OptionalKind.Witness, String> originalMaybeT = createConcreteMaybeTOuterEmptyOpt();
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> wrappedKind =
          MaybeTKindHelper.wrap(originalMaybeT);
      MaybeT<OptionalKind.Witness, String> unwrappedMaybeT = MaybeTKindHelper.unwrap(wrappedKind);
      assertThat(unwrappedMaybeT).isSameAs(originalMaybeT);
    }

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping null")
    void unwrap_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> MaybeTKindHelper.<OptionalKind.Witness, String>unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(MaybeTKindHelper.INVALID_KIND_NULL_MSG);
    }

    private static class OtherKindWitness<F_Witness> {}

    private static class OtherKind<F_Witness, A> implements Kind<OtherKindWitness<F_Witness>, A> {}

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping an incorrect Kind type")
    void unwrap_incorrectKindType_shouldThrowKindUnwrapException() {
      OtherKind<OptionalKind.Witness, String> incorrectKind = new OtherKind<>();

      @SuppressWarnings({"unchecked", "rawtypes"})
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> kindToTest =
          (Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>) (Kind) incorrectKind;

      assertThatThrownBy(() -> MaybeTKindHelper.unwrap(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageStartingWith(MaybeTKindHelper.INVALID_KIND_TYPE_MSG)
          .hasMessageContaining(OtherKind.class.getName());
    }

    @Test
    @DisplayName("unwrap should correctly infer types (Outer Optional)")
    void unwrap_typeInference_OptionalOuter() {
      MaybeT<OptionalKind.Witness, Integer> concrete = MaybeT.just(optionalOuterMonad, 123);
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> wrapped =
          MaybeTKindHelper.wrap(concrete);

      MaybeT<OptionalKind.Witness, Integer> unwrapped = MaybeTKindHelper.unwrap(wrapped);
      Optional<Maybe<Integer>> result = OptionalKindHelper.unwrap(unwrapped.value());
      assertThat(result).isPresent().contains(Maybe.just(123));
    }

    @Test
    @DisplayName("unwrap should correctly infer types (Outer IO)")
    void unwrap_typeInference_IOOuter() {
      MaybeT<IOKind.Witness, Boolean> concreteBool = MaybeT.just(ioOuterMonad, true);
      Kind<MaybeTKind.Witness<IOKind.Witness>, Boolean> wrappedBool =
          MaybeTKindHelper.wrap(concreteBool);
      MaybeT<IOKind.Witness, Boolean> unwrappedBool = MaybeTKindHelper.unwrap(wrappedBool);
      Maybe<Boolean> resultIO = IOKindHelper.unsafeRunSync(unwrappedBool.value());
      assertThat(resultIO).isEqualTo(Maybe.just(true));
    }
  }
}
