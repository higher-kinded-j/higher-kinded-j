package org.higherkindedj.hkt.trans.either_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherTKindHelper Tests")
class EitherTKindHelperTest {

  private Monad<OptionalKind<?>> outerMonad; // Use Monad<F> for creating EitherT

  @BeforeEach
  void setUp() {
    outerMonad = new OptionalMonad(); // OptionalMonad implements Monad<OptionalKind<?>>
  }

  // Helper to create a concrete EitherT<OptionalKind, String, R> with a Right value
  private <R> EitherT<OptionalKind<?>, String, R> createConcreteEitherTRight(R value) {
    return EitherT.right(outerMonad, value);
  }

  // Helper to create a concrete EitherT<OptionalKind, String, R> with a Left value
  private <R> EitherT<OptionalKind<?>, String, R> createConcreteEitherTLeft(String error) {
    return EitherT.left(outerMonad, error);
  }

  // Helper to create a concrete EitherT<OptionalKind, String, R> with outer Optional.empty()
  private <R> EitherT<OptionalKind<?>, String, R> createConcreteEitherTOuterEmpty() {
    Kind<OptionalKind<?>, Either<String, R>> emptyOuter = OptionalKindHelper.wrap(Optional.empty());
    return EitherT.fromKind(emptyOuter);
  }

  @Test
  @DisplayName("private constructor should prevent instantiation")
  void privateConstructor_shouldThrowException() {
    assertThatThrownBy(
            () -> {
              // The helper class itself is generic, but the constructor is not.
              // We can use raw type for getDeclaredConstructor.
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
      EitherT<OptionalKind<?>, String, Integer> concreteEitherT = createConcreteEitherTRight(123);
      EitherTKind<OptionalKind<?>, String, Integer> wrapped =
          EitherTKindHelper.wrap(concreteEitherT);

      assertThat(wrapped).isNotNull();
      assertThat(wrapped).isInstanceOf(EitherTKind.class);
      assertThat(wrapped.getClass().getSimpleName()).isEqualTo("EitherTHolder");
    }

    @Test
    @DisplayName("should wrap a non-null EitherT (Left) into an EitherTKind")
    void wrap_nonNullEitherTLeft_shouldReturnEitherTKind() {
      EitherT<OptionalKind<?>, String, Integer> concreteEitherT =
          createConcreteEitherTLeft("Error");
      EitherTKind<OptionalKind<?>, String, Integer> wrapped =
          EitherTKindHelper.wrap(concreteEitherT);

      assertThat(wrapped).isNotNull();
      assertThat(wrapped).isInstanceOf(EitherTKind.class);
      assertThat(wrapped.getClass().getSimpleName()).isEqualTo("EitherTHolder");
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
      EitherT<OptionalKind<?>, String, Integer> originalEitherT = createConcreteEitherTRight(456);
      EitherTKind<OptionalKind<?>, String, Integer> wrappedKind =
          EitherTKindHelper.wrap(originalEitherT);

      EitherT<OptionalKind<?>, String, Integer> unwrappedEitherT =
          EitherTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedEitherT).isNotNull();
      assertThat(unwrappedEitherT).isSameAs(originalEitherT);
      assertThat(OptionalKindHelper.unwrap(unwrappedEitherT.value())).contains(Either.right(456));
    }

    @Test
    @DisplayName("should unwrap a valid EitherTKind (Left) to the original EitherT instance")
    void unwrap_validKindLeft_shouldReturnEitherT() {
      EitherT<OptionalKind<?>, String, Integer> originalEitherT =
          createConcreteEitherTLeft("TestError");
      EitherTKind<OptionalKind<?>, String, Integer> wrappedKind =
          EitherTKindHelper.wrap(originalEitherT);
      EitherT<OptionalKind<?>, String, Integer> unwrappedEitherT =
          EitherTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedEitherT).isSameAs(originalEitherT);
      assertThat(OptionalKindHelper.unwrap(unwrappedEitherT.value()))
          .contains(Either.left("TestError"));
    }

    @Test
    @DisplayName("should unwrap a valid EitherTKind (OuterEmpty) to the original EitherT instance")
    void unwrap_validKindOuterEmpty_shouldReturnEitherT() {
      EitherT<OptionalKind<?>, String, Double> originalEitherT = createConcreteEitherTOuterEmpty();
      EitherTKind<OptionalKind<?>, String, Double> wrappedKind =
          EitherTKindHelper.wrap(originalEitherT);
      EitherT<OptionalKind<?>, String, Double> unwrappedEitherT =
          EitherTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedEitherT).isSameAs(originalEitherT);
      assertThat(OptionalKindHelper.unwrap(unwrappedEitherT.value())).isEmpty();
    }

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping null")
    void unwrap_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> EitherTKindHelper.unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(EitherTKindHelper.INVALID_KIND_NULL_MSG);
    }

    // Dummy Kind for testing invalid type unwrap
    private static class OtherKind<F, L, R> implements Kind<OtherKind<F, L, ?>, R> {}

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping an incorrect Kind type")
    void unwrap_incorrectKindType_shouldThrowKindUnwrapException() {
      OtherKind<OptionalKind<?>, String, Integer> incorrectKind = new OtherKind<>();

      // This cast is unsafe but necessary for the test setup
      @SuppressWarnings({"unchecked", "rawtypes"})
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> kindToTest =
          (Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer>) (Kind) incorrectKind;

      assertThatThrownBy(() -> EitherTKindHelper.unwrap(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageStartingWith(EitherTKindHelper.INVALID_KIND_TYPE_MSG)
          .hasMessageContaining(OtherKind.class.getName());
    }

    @Test
    @DisplayName("unwrap should correctly infer types for F, L, and R")
    void unwrap_typeInference() {
      // Test with specific F (OptionalKind), L (String), R (Integer)
      EitherT<OptionalKind<?>, String, Integer> concreteInt =
          EitherT.right(new OptionalMonad(), 789);
      EitherTKind<OptionalKind<?>, String, Integer> wrappedInt =
          EitherTKindHelper.wrap(concreteInt);
      EitherT<OptionalKind<?>, String, Integer> unwrappedInt = EitherTKindHelper.unwrap(wrappedInt);
      assertThat(OptionalKindHelper.unwrap(unwrappedInt.value())).contains(Either.right(789));

      // Test with different R (Boolean)
      EitherT<OptionalKind<?>, String, Boolean> concreteBool =
          EitherT.right(new OptionalMonad(), true);
      EitherTKind<OptionalKind<?>, String, Boolean> wrappedBool =
          EitherTKindHelper.wrap(concreteBool);
      EitherT<OptionalKind<?>, String, Boolean> unwrappedBool =
          EitherTKindHelper.unwrap(wrappedBool);
      assertThat(OptionalKindHelper.unwrap(unwrappedBool.value())).contains(Either.right(true));

      // Test with different L (Integer) and R (String)
      EitherT<OptionalKind<?>, Integer, String> concreteLeftInt =
          EitherT.left(new OptionalMonad(), -1);
      EitherTKind<OptionalKind<?>, Integer, String> wrappedLeftInt =
          EitherTKindHelper.wrap(concreteLeftInt);

      // Explicit type arguments for unwrap to demonstrate/test inference target
      EitherT<OptionalKind<?>, Integer, String> unwrappedLeftInt =
          EitherTKindHelper.<OptionalKind<?>, Integer, String>unwrap(wrappedLeftInt);
      assertThat(OptionalKindHelper.unwrap(unwrappedLeftInt.value())).contains(Either.left(-1));
    }
  }
}
