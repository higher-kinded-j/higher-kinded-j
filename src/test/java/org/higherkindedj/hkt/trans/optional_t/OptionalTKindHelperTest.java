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

  private Monad<IOKind.Witness> outerMonad;

  @BeforeEach
  void setUp() {
    outerMonad = new IOMonad();
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
    Kind<IOKind.Witness, Optional<A>> failingKind = IOKindHelper.wrap(failingIO);
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
    @DisplayName("should wrap a non-null OptionalT (Some) into a Kind<OptionalTKind.Witness<F>,A>")
    void wrap_nonNullOptionalTSome_shouldReturnOptionalTKind() {
      OptionalT<IOKind.Witness, String> concreteOptionalT = createConcreteOptionalTSome("test");
      Kind<OptionalTKind.Witness<IOKind.Witness>, String> wrapped =
          OptionalTKindHelper.wrap(concreteOptionalT);

      assertThat(wrapped).isNotNull().isInstanceOf(OptionalT.class);
      assertThat(OptionalTKindHelper.<IOKind.Witness, String>unwrap(wrapped))
          .isSameAs(concreteOptionalT);
    }

    @Test
    @DisplayName("should wrap a non-null OptionalT (None) into a Kind<OptionalTKind.Witness<F>,A>")
    void wrap_nonNullOptionalTNone_shouldReturnOptionalTKind() {
      OptionalT<IOKind.Witness, String> concreteOptionalT = createConcreteOptionalTNone();
      Kind<OptionalTKind.Witness<IOKind.Witness>, String> wrapped =
          OptionalTKindHelper.wrap(concreteOptionalT);

      // Assert it's an instance of OptionalT
      assertThat(wrapped).isNotNull().isInstanceOf(OptionalT.class);
      assertThat(OptionalTKindHelper.<IOKind.Witness, String>unwrap(wrapped))
          .isSameAs(concreteOptionalT);
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
    @DisplayName("should unwrap a valid Kind (Some) to the original OptionalT instance")
    void unwrap_validKindSome_shouldReturnOptionalT() {
      OptionalT<IOKind.Witness, Integer> originalOptionalT = createConcreteOptionalTSome(456);
      Kind<OptionalTKind.Witness<IOKind.Witness>, Integer> wrappedKind =
          OptionalTKindHelper.wrap(originalOptionalT);

      OptionalT<IOKind.Witness, Integer> unwrappedOptionalT =
          OptionalTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedOptionalT).isNotNull().isSameAs(originalOptionalT);
      assertThat(IOKindHelper.unsafeRunSync(unwrappedOptionalT.value())).contains(456);
    }

    @Test
    @DisplayName("should unwrap a valid Kind (None) to the original OptionalT instance")
    void unwrap_validKindNone_shouldReturnOptionalT() {
      OptionalT<IOKind.Witness, String> originalOptionalT = createConcreteOptionalTNone();
      Kind<OptionalTKind.Witness<IOKind.Witness>, String> wrappedKind =
          OptionalTKindHelper.wrap(originalOptionalT);
      OptionalT<IOKind.Witness, String> unwrappedOptionalT =
          OptionalTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedOptionalT).isSameAs(originalOptionalT);
      assertThat(IOKindHelper.unsafeRunSync(unwrappedOptionalT.value())).isEmpty();
    }

    @Test
    @DisplayName("should unwrap a valid Kind (OuterError) to the original OptionalT instance")
    void unwrap_validKindOuterError_shouldReturnOptionalT() {
      OptionalT<IOKind.Witness, Double> originalOptionalT = createConcreteOptionalTOuterError();
      Kind<OptionalTKind.Witness<IOKind.Witness>, Double> wrappedKind =
          OptionalTKindHelper.wrap(originalOptionalT);
      OptionalT<IOKind.Witness, Double> unwrappedOptionalT =
          OptionalTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedOptionalT).isSameAs(originalOptionalT);
      assertThatThrownBy(() -> IOKindHelper.unsafeRunSync(unwrappedOptionalT.value()))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Outer IO failed");
    }

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping null")
    void unwrap_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> OptionalTKindHelper.<IOKind.Witness, String>unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(OptionalTKindHelper.INVALID_KIND_NULL_MSG);
    }

    // Dummy Kind for testing invalid type unwrap
    private static class OtherKindWitness<F_Witness> {}

    private static class OtherKind<F_Witness, A> implements Kind<OtherKindWitness<F_Witness>, A> {}

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping an incorrect Kind type")
    void unwrap_incorrectKindType_shouldThrowKindUnwrapException() {
      OtherKind<IOKind.Witness, String> incorrectKind = new OtherKind<>();

      @SuppressWarnings({"unchecked", "rawtypes"})
      Kind<OptionalTKind.Witness<IOKind.Witness>, String> kindToTest =
          (Kind<OptionalTKind.Witness<IOKind.Witness>, String>) (Kind) incorrectKind;

      assertThatThrownBy(() -> OptionalTKindHelper.unwrap(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageStartingWith(OptionalTKindHelper.INVALID_KIND_TYPE_MSG)
          .hasMessageContaining(OtherKind.class.getName());
    }

    @Test
    @DisplayName("unwrap should correctly infer types for F and A")
    void unwrap_typeInference() {
      OptionalT<IOKind.Witness, Integer> concreteInt = createConcreteOptionalTSome(789);
      Kind<OptionalTKind.Witness<IOKind.Witness>, Integer> wrappedInt =
          OptionalTKindHelper.wrap(concreteInt);
      OptionalT<IOKind.Witness, Integer> unwrappedInt = OptionalTKindHelper.unwrap(wrappedInt);
      assertThat(IOKindHelper.unsafeRunSync(unwrappedInt.value())).contains(789);

      OptionalT<IOKind.Witness, Boolean> concreteBool = createConcreteOptionalTSome(true);
      Kind<OptionalTKind.Witness<IOKind.Witness>, Boolean> wrappedBool =
          OptionalTKindHelper.wrap(concreteBool);
      OptionalT<IOKind.Witness, Boolean> unwrappedBool = OptionalTKindHelper.unwrap(wrappedBool);
      assertThat(IOKindHelper.unsafeRunSync(unwrappedBool.value())).contains(true);
    }
  }
}
