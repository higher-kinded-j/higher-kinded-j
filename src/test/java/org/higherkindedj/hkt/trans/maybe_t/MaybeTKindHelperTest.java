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
  private Monad<IOKind.Witness> ioOuterMonad; // Assuming IOKind uses IOKind<?> as its witness

  @BeforeEach
  void setUp() {
    optionalOuterMonad = new OptionalMonad(); // Correctly provides Monad<OptionalKind.Witness>
    ioOuterMonad = new IOMonad();
  }

  private <A extends @NonNull Object> MaybeT<OptionalKind.Witness, A> createConcreteMaybeTSomeOpt(
      @NonNull A value) {
    // MaybeT.just now correctly infers F as OptionalKind.Witness from optionalOuterMonad
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
    @DisplayName("should wrap a non-null MaybeT (Some) into a MaybeTKind (Outer Optional)")
    void wrap_nonNullMaybeTSome_OptionalOuter_shouldReturnMaybeTKind() {
      MaybeT<OptionalKind.Witness, String> concreteMaybeT = createConcreteMaybeTSomeOpt("test");
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> wrapped =
          MaybeTKindHelper.wrap(concreteMaybeT);

      assertThat(wrapped).isNotNull().isInstanceOf(MaybeTKindHelper.MaybeTHolder.class);
      assertThat(MaybeTKindHelper.<OptionalKind.Witness, String>unwrap(wrapped))
          .isSameAs(concreteMaybeT);
    }

    @Test
    @DisplayName("should wrap a non-null MaybeT (Some) into a MaybeTKind (Outer IO)")
    void wrap_nonNullMaybeTSome_IOOuter_shouldReturnMaybeTKind() {
      MaybeT<IOKind.Witness, String> concreteMaybeT = createConcreteMaybeTSomeIO("testIO");
      Kind<MaybeTKind<IOKind.Witness, ?>, String> wrapped = MaybeTKindHelper.wrap(concreteMaybeT);
      assertThat(wrapped).isNotNull().isInstanceOf(MaybeTKindHelper.MaybeTHolder.class);
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
        "should unwrap a valid MaybeTKind (Some) to the original MaybeT instance (Outer Optional)")
    void unwrap_validKindSome_OptionalOuter_shouldReturnMaybeT() {
      MaybeT<OptionalKind.Witness, String> originalMaybeT = createConcreteMaybeTSomeOpt("hello");
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> wrappedKind =
          MaybeTKindHelper.wrap(originalMaybeT);

      MaybeT<OptionalKind.Witness, String> unwrappedMaybeT = MaybeTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedMaybeT).isSameAs(originalMaybeT);
    }

    @Test
    @DisplayName(
        "should unwrap a valid MaybeTKind (Nothing) to the original MaybeT instance (Outer"
            + " Optional)")
    void unwrap_validKindNothing_OptionalOuter_shouldReturnMaybeT() {
      MaybeT<OptionalKind.Witness, String> originalMaybeT = createConcreteMaybeTNothingOpt();
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> wrappedKind =
          MaybeTKindHelper.wrap(originalMaybeT);
      MaybeT<OptionalKind.Witness, String> unwrappedMaybeT = MaybeTKindHelper.unwrap(wrappedKind);
      assertThat(unwrappedMaybeT).isSameAs(originalMaybeT);
    }

    @Test
    @DisplayName(
        "should unwrap a valid MaybeTKind (OuterEmpty) to original MaybeT instance (Outer"
            + " Optional)")
    void unwrap_validKindOuterEmpty_OptionalOuter_shouldReturnMaybeT() {
      MaybeT<OptionalKind.Witness, String> originalMaybeT = createConcreteMaybeTOuterEmptyOpt();
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> wrappedKind =
          MaybeTKindHelper.wrap(originalMaybeT);
      MaybeT<OptionalKind.Witness, String> unwrappedMaybeT = MaybeTKindHelper.unwrap(wrappedKind);
      assertThat(unwrappedMaybeT).isSameAs(originalMaybeT);
    }

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping null")
    void unwrap_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> MaybeTKindHelper.unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(MaybeTKindHelper.INVALID_KIND_NULL_MSG);
    }

    // Dummy Kind for testing invalid type unwrap
    private static class OtherKind<F_Witness, A> implements Kind<OtherKind<F_Witness, ?>, A> {}

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping an incorrect Kind type")
    void unwrap_incorrectKindType_shouldThrowKindUnwrapException() {
      OtherKind<OptionalKind.Witness, String> incorrectKind = new OtherKind<>();

      @SuppressWarnings({"unchecked", "rawtypes"}) // Suppress warnings for the forced cast
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> kindToTest =
          (Kind<MaybeTKind<OptionalKind.Witness, ?>, String>) (Kind) incorrectKind;

      assertThatThrownBy(() -> MaybeTKindHelper.unwrap(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageStartingWith(MaybeTKindHelper.INVALID_KIND_TYPE_MSG)
          .hasMessageContaining(OtherKind.class.getName());
    }

    @Test
    @DisplayName("unwrap should correctly infer types (Outer Optional)")
    void unwrap_typeInference_OptionalOuter() {
      // Use the correctly typed optionalOuterMonad field
      MaybeT<OptionalKind.Witness, Integer> concrete = MaybeT.just(optionalOuterMonad, 123);
      Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> wrapped = MaybeTKindHelper.wrap(concrete);

      MaybeT<OptionalKind.Witness, Integer> unwrapped = MaybeTKindHelper.unwrap(wrapped);
      // Verify content
      Optional<Maybe<Integer>> result = OptionalKindHelper.unwrap(unwrapped.value());
      assertThat(result).isPresent().contains(Maybe.just(123));
    }

    @Test
    @DisplayName("unwrap should correctly infer types (Outer IO)")
    void unwrap_typeInference_IOOuter() {
      MaybeT<IOKind.Witness, Boolean> concreteBool = MaybeT.just(ioOuterMonad, true);
      Kind<MaybeTKind<IOKind.Witness, ?>, Boolean> wrappedBool =
          MaybeTKindHelper.wrap(concreteBool);
      MaybeT<IOKind.Witness, Boolean> unwrappedBool = MaybeTKindHelper.unwrap(wrappedBool);
      // Verify content by running IO
      Maybe<Boolean> resultIO = IOKindHelper.unsafeRunSync(unwrappedBool.value());
      assertThat(resultIO).isEqualTo(Maybe.just(true));
    }
  }
}
