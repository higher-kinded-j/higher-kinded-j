package org.higherkindedj.hkt.trans.either_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherTKindHelper Tests (F=OptionalKind.Witness)")
class EitherTKindHelperTest {

  private Monad<OptionalKind.Witness> outerMonad;

  @BeforeEach
  void setUp() {
    outerMonad = new OptionalMonad();
  }

  // Helper to create a concrete EitherT with OptionalKind.Witness as F
  private <L, R> EitherT<OptionalKind.Witness, L, R> createEitherT(Either<L, R> either) {
    return EitherT.fromEither(outerMonad, either);
  }

  @Test
  @DisplayName("private constructor should prevent instantiation")
  void privateConstructor_shouldThrowException() {
    assertThatThrownBy(
            () -> {
              @SuppressWarnings("rawtypes")
              Constructor<EitherTKindHelper> constructor =
                  EitherTKindHelper.class.getDeclaredConstructor();
              constructor.setAccessible(true);
              try {
                constructor.newInstance();
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
            })
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("This is a utility class and cannot be instantiated");
  }

  @Nested
  @DisplayName("wrap() tests")
  class WrapTests {
    @Test
    @DisplayName("should wrap a non-null EitherT (Right) into an EitherTKind")
    void wrap_nonNullEitherTRight_shouldReturnEitherTKind() {
      EitherT<OptionalKind.Witness, String, Integer> concreteEitherT =
          createEitherT(Either.right(123));
      Kind<EitherTKind<OptionalKind.Witness, String, ?>, Integer> wrapped =
          EitherTKindHelper.wrap(concreteEitherT);

      assertThat(wrapped).isNotNull().isInstanceOf(EitherTKindHelper.EitherTHolder.class);
      assertThat(EitherTKindHelper.<OptionalKind.Witness, String, Integer>unwrap(wrapped))
          .isSameAs(concreteEitherT);
    }

    @Test
    @DisplayName("should throw NullPointerException when wrapping null")
    void wrap_nullEitherT_shouldThrowNullPointerException() {
      assertThatThrownBy(() -> EitherTKindHelper.wrap(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Input EitherT cannot be null for wrap");
    }
  }

  @Nested
  @DisplayName("unwrap() tests")
  class UnwrapTests {
    @Test
    @DisplayName("should unwrap a valid EitherTKind (Right) to the original EitherT instance")
    void unwrap_validKindRight_shouldReturnEitherT() {
      EitherT<OptionalKind.Witness, String, Integer> originalEitherT =
          createEitherT(Either.right(456));
      Kind<EitherTKind<OptionalKind.Witness, String, ?>, Integer> wrappedKind =
          EitherTKindHelper.wrap(originalEitherT);

      EitherT<OptionalKind.Witness, String, Integer> unwrappedEitherT =
          EitherTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedEitherT).isSameAs(originalEitherT);
    }

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping null")
    void unwrap_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> EitherTKindHelper.unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(EitherTKindHelper.INVALID_KIND_NULL_MSG);
    }

    // Dummy Kind for testing invalid type unwrap
    private static class OtherKind<F_Witness, L, R>
        implements Kind<OtherKind<F_Witness, L, ?>, R> {}

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping an incorrect Kind type")
    void unwrap_incorrectKindType_shouldThrowKindUnwrapException() {
      OtherKind<OptionalKind.Witness, String, Integer> incorrectKind = new OtherKind<>();

      @SuppressWarnings({"unchecked", "rawtypes"})
      Kind<EitherTKind<OptionalKind.Witness, String, ?>, Integer> kindToTest =
          (Kind<EitherTKind<OptionalKind.Witness, String, ?>, Integer>) (Kind) incorrectKind;

      assertThatThrownBy(() -> EitherTKindHelper.unwrap(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageStartingWith(EitherTKindHelper.INVALID_KIND_TYPE_MSG)
          .hasMessageContaining(OtherKind.class.getName());
    }
  }
}
