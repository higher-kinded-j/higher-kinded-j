package org.higherkindedj.hkt.trans.optional_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.io.IOMonad;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OptionalTKindHelper Tests")
class OptionalTKindHelperTest {

  private Monad<IOKind<?>> outerMonad;

  @BeforeEach
  void setUp() {
    outerMonad = new IOMonad();
  }

  // Helper to create a concrete OptionalT<IOKind, A> with a present value
  private <A extends @NonNull Object> OptionalT<IOKind<?>, A> createConcreteOptionalTSome(
      @NonNull A value) {
    return OptionalT.some(outerMonad, value);
  }

  // Helper to create a concrete OptionalT<IOKind, A> with an empty value
  private <A> OptionalT<IOKind<?>, A> createConcreteOptionalTNone() {
    return OptionalT.none(outerMonad);
  }

  // Helper to create a concrete OptionalT<IOKind, A> with outer IO failure (for testing unwrap)
  // Note: This represents an error in F, not handled by OptionalT's MonadError
  private <A> OptionalT<IOKind<?>, A> createConcreteOptionalTOuterError() {
    RuntimeException ex = new RuntimeException("Outer IO failed");
    IO<Optional<A>> failingIO =
        () -> {
          throw ex;
        };
    Kind<IOKind<?>, Optional<A>> failingKind = IOKindHelper.wrap(failingIO);
    return OptionalT.fromKind(failingKind);
  }

  @Test
  @DisplayName("private constructor should prevent instantiation")
  void privateConstructor_shouldThrowException() {
    assertThatThrownBy(
            () -> {
              Constructor<OptionalTKindHelper> constructor =
                  OptionalTKindHelper.class.getDeclaredConstructor();
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
    @DisplayName("should wrap a non-null OptionalT (Some) into an OptionalTKind")
    void wrap_nonNullOptionalTSome_shouldReturnOptionalTKind() {
      OptionalT<IOKind<?>, String> concreteOptionalT = createConcreteOptionalTSome("test");
      OptionalTKind<IOKind<?>, String> wrapped = OptionalTKindHelper.wrap(concreteOptionalT);

      assertThat(wrapped).isNotNull();
      assertThat(wrapped).isInstanceOf(OptionalTKind.class);
      assertThat(wrapped.getClass().getSimpleName()).isEqualTo("OptionalTHolder");
    }

    @Test
    @DisplayName("should wrap a non-null OptionalT (None) into an OptionalTKind")
    void wrap_nonNullOptionalTNone_shouldReturnOptionalTKind() {
      OptionalT<IOKind<?>, String> concreteOptionalT = createConcreteOptionalTNone();
      OptionalTKind<IOKind<?>, String> wrapped = OptionalTKindHelper.wrap(concreteOptionalT);

      assertThat(wrapped).isNotNull();
      assertThat(wrapped).isInstanceOf(OptionalTKind.class);
      assertThat(wrapped.getClass().getSimpleName()).isEqualTo("OptionalTHolder");
    }

    @Test
    @DisplayName("should throw NullPointerException when wrapping null")
    void wrap_nullOptionalT_shouldThrowNullPointerException() {
      assertThatThrownBy(() -> OptionalTKindHelper.wrap(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Input OptionalT cannot be null for wrap");
    }
  }

  @Nested
  @DisplayName("unwrap() tests")
  class UnwrapTests {
    @Test
    @DisplayName("should unwrap a valid OptionalTKind (Some) to the original OptionalT instance")
    void unwrap_validKindSome_shouldReturnOptionalT() {
      OptionalT<IOKind<?>, Integer> originalOptionalT = createConcreteOptionalTSome(456);
      OptionalTKind<IOKind<?>, Integer> wrappedKind = OptionalTKindHelper.wrap(originalOptionalT);

      OptionalT<IOKind<?>, Integer> unwrappedOptionalT = OptionalTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedOptionalT).isNotNull();
      assertThat(unwrappedOptionalT).isSameAs(originalOptionalT);
      // Verify the inner IO contains the correct Optional
      assertThat(IOKindHelper.unsafeRunSync(unwrappedOptionalT.value())).contains(456);
    }

    @Test
    @DisplayName("should unwrap a valid OptionalTKind (None) to the original OptionalT instance")
    void unwrap_validKindNone_shouldReturnOptionalT() {
      OptionalT<IOKind<?>, String> originalOptionalT = createConcreteOptionalTNone();
      OptionalTKind<IOKind<?>, String> wrappedKind = OptionalTKindHelper.wrap(originalOptionalT);
      OptionalT<IOKind<?>, String> unwrappedOptionalT = OptionalTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedOptionalT).isSameAs(originalOptionalT);
      assertThat(IOKindHelper.unsafeRunSync(unwrappedOptionalT.value())).isEmpty();
    }

    @Test
    @DisplayName(
        "should unwrap a valid OptionalTKind (OuterError) to the original OptionalT instance")
    void unwrap_validKindOuterError_shouldReturnOptionalT() {
      OptionalT<IOKind<?>, Double> originalOptionalT = createConcreteOptionalTOuterError();
      OptionalTKind<IOKind<?>, Double> wrappedKind = OptionalTKindHelper.wrap(originalOptionalT);
      OptionalT<IOKind<?>, Double> unwrappedOptionalT = OptionalTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedOptionalT).isSameAs(originalOptionalT);
      // Verify that running the inner IO throws the expected exception
      assertThatThrownBy(() -> IOKindHelper.unsafeRunSync(unwrappedOptionalT.value()))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Outer IO failed");
    }

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping null")
    void unwrap_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> OptionalTKindHelper.unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(OptionalTKindHelper.INVALID_KIND_NULL_MSG);
    }

    // Dummy Kind for testing invalid type unwrap
    private static class OtherKind<F, A> implements Kind<OtherKind<F, ?>, A> {}

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping an incorrect Kind type")
    void unwrap_incorrectKindType_shouldThrowKindUnwrapException() {
      OtherKind<IOKind<?>, String> incorrectKind = new OtherKind<>();

      // This cast is unsafe but necessary for the test setup
      @SuppressWarnings({"unchecked", "rawtypes"})
      Kind<OptionalTKind<IOKind<?>, ?>, String> kindToTest =
          (Kind<OptionalTKind<IOKind<?>, ?>, String>) (Kind) incorrectKind;

      assertThatThrownBy(() -> OptionalTKindHelper.unwrap(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageStartingWith(OptionalTKindHelper.INVALID_KIND_TYPE_MSG)
          .hasMessageContaining(OtherKind.class.getName());
    }

    @Test
    @DisplayName("unwrap should correctly infer types for F and A")
    void unwrap_typeInference() {
      // Test with specific F (IOKind), A (Integer)
      OptionalT<IOKind<?>, Integer> concreteInt = createConcreteOptionalTSome(789);
      OptionalTKind<IOKind<?>, Integer> wrappedInt = OptionalTKindHelper.wrap(concreteInt);
      OptionalT<IOKind<?>, Integer> unwrappedInt = OptionalTKindHelper.unwrap(wrappedInt);
      assertThat(IOKindHelper.unsafeRunSync(unwrappedInt.value())).contains(789);

      // Test with different A (Boolean)
      OptionalT<IOKind<?>, Boolean> concreteBool = createConcreteOptionalTSome(true);
      OptionalTKind<IOKind<?>, Boolean> wrappedBool = OptionalTKindHelper.wrap(concreteBool);
      OptionalT<IOKind<?>, Boolean> unwrappedBool = OptionalTKindHelper.unwrap(wrappedBool);
      assertThat(IOKindHelper.unsafeRunSync(unwrappedBool.value())).contains(true);
    }
  }
}
