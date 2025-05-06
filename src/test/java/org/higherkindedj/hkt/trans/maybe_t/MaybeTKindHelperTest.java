package org.higherkindedj.hkt.trans.maybe_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
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

  private OptionalMonad outerMonad;

  @BeforeEach
  void setUp() {
    outerMonad = new OptionalMonad();
  }

  // Helper to create a concrete MaybeT<OptionalKind, String>
  private MaybeT<OptionalKind<?>, String> createConcreteMaybeT(@NonNull String value) {
    return MaybeT.just(outerMonad, value);
  }

  // Helper to create a concrete MaybeT<OptionalKind, String> with Nothing
  private MaybeT<OptionalKind<?>, String> createConcreteMaybeTNothing() {
    return MaybeT.nothing(outerMonad);
  }

  // Helper to create a concrete MaybeT<OptionalKind, String> with outer Optional.empty()
  private MaybeT<OptionalKind<?>, String> createConcreteMaybeTOuterEmpty() {
    Kind<OptionalKind<?>, Maybe<String>> emptyOuter = OptionalKindHelper.wrap(Optional.empty());
    return MaybeT.fromKind(emptyOuter);
  }

  @Test
  @DisplayName("private constructor should prevent instantiation")
  void privateConstructor_shouldThrowException() {
    // Test the private constructor to ensure it prevents instantiation (for coverage)
    assertThatThrownBy(
        () -> {
          Constructor<MaybeTKindHelper> constructor =
              MaybeTKindHelper.class.getDeclaredConstructor();
          constructor.setAccessible(true); // Allow access to private constructor
          try {
            constructor.newInstance(); // Attempt to instantiate
          } catch (InvocationTargetException e) {
            // The actual exception thrown by the constructor is wrapped in
            // InvocationTargetException
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
    @DisplayName("should wrap a non-null MaybeT into a MaybeTKind")
    void wrap_nonNullMaybeT_shouldReturnMaybeTKind() {
      MaybeT<OptionalKind<?>, String> concreteMaybeT = createConcreteMaybeT("test");
      MaybeTKind<OptionalKind<?>, String> wrapped = MaybeTKindHelper.wrap(concreteMaybeT);

      assertThat(wrapped).isNotNull();
      assertThat(wrapped).isInstanceOf(MaybeTKind.class);
      // Further check if it's the specific internal holder (optional, but good for confidence)
      assertThat(wrapped.getClass().getSimpleName()).isEqualTo("MaybeTHolder");
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
    @DisplayName("should unwrap a valid MaybeTKind to the original MaybeT instance")
    void unwrap_validKind_shouldReturnMaybeT() {
      MaybeT<OptionalKind<?>, String> originalMaybeT = createConcreteMaybeT("hello");
      MaybeTKind<OptionalKind<?>, String> wrappedKind = MaybeTKindHelper.wrap(originalMaybeT);

      MaybeT<OptionalKind<?>, String> unwrappedMaybeT = MaybeTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedMaybeT).isNotNull();
      assertThat(unwrappedMaybeT).isSameAs(originalMaybeT); // Check for object identity
      assertThat(unwrappedMaybeT.value())
          .isEqualTo(OptionalKindHelper.wrap(Optional.of(Maybe.just("hello"))));
    }

    @Test
    @DisplayName("should unwrap a valid MaybeTKind (Nothing) to the original MaybeT instance")
    void unwrap_validKindNothing_shouldReturnMaybeT() {
      MaybeT<OptionalKind<?>, String> originalMaybeT = createConcreteMaybeTNothing();
      MaybeTKind<OptionalKind<?>, String> wrappedKind = MaybeTKindHelper.wrap(originalMaybeT);
      MaybeT<OptionalKind<?>, String> unwrappedMaybeT = MaybeTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedMaybeT).isSameAs(originalMaybeT);
      assertThat(unwrappedMaybeT.value())
          .isEqualTo(OptionalKindHelper.wrap(Optional.of(Maybe.nothing())));
    }

    @Test
    @DisplayName("should unwrap a valid MaybeTKind (OuterEmpty) to the original MaybeT instance")
    void unwrap_validKindOuterEmpty_shouldReturnMaybeT() {
      MaybeT<OptionalKind<?>, String> originalMaybeT = createConcreteMaybeTOuterEmpty();
      MaybeTKind<OptionalKind<?>, String> wrappedKind = MaybeTKindHelper.wrap(originalMaybeT);
      MaybeT<OptionalKind<?>, String> unwrappedMaybeT = MaybeTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedMaybeT).isSameAs(originalMaybeT);
      assertThat(unwrappedMaybeT.value()).isEqualTo(OptionalKindHelper.wrap(Optional.empty()));
    }

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping null")
    void unwrap_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> MaybeTKindHelper.unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(MaybeTKindHelper.INVALID_KIND_NULL_MSG);
    }

    // Dummy Kind for testing invalid type unwrap
    private static class OtherKind<A> implements Kind<OtherKind<?>, A> {}

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping an incorrect Kind type")
    void unwrap_incorrectKindType_shouldThrowKindUnwrapException() {
      OtherKind<String> incorrectKind = new OtherKind<>();

      // This cast is unsafe but necessary for the test setup to pass an incorrect Kind.
      @SuppressWarnings({"unchecked", "rawtypes"})
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> kindToTest =
          (Kind<MaybeTKind<OptionalKind<?>, ?>, String>) (Kind) incorrectKind;

      assertThatThrownBy(() -> MaybeTKindHelper.unwrap(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageStartingWith(MaybeTKindHelper.INVALID_KIND_TYPE_MSG)
          .hasMessageContaining(OtherKind.class.getName());
    }

    @Test
    @DisplayName("unwrap should correctly infer types")
    void unwrap_typeInference() {
      MaybeT<OptionalKind<?>, Integer> concrete =
          MaybeT.just(new OptionalMonad(), 123);
      MaybeTKind<OptionalKind<?>, Integer> wrapped = MaybeTKindHelper.wrap(concrete);

      // Explicitly type the unwrap to ensure the generic types are correctly inferred
      MaybeT<OptionalKind<?>, Integer> unwrapped = MaybeTKindHelper.unwrap(wrapped);
      assertThat(unwrapped.value())
          .isEqualTo(OptionalKindHelper.wrap(Optional.of(Maybe.just(123))));

      // Test with a different inner type
      MaybeT<OptionalKind<?>, Boolean> concreteBool =
          MaybeT.just(new OptionalMonad(), true);
      MaybeTKind<OptionalKind<?>, Boolean> wrappedBool = MaybeTKindHelper.wrap(concreteBool);
      MaybeT<OptionalKind<?>, Boolean> unwrappedBool = MaybeTKindHelper.unwrap(wrappedBool);
      assertThat(unwrappedBool.value())
          .isEqualTo(OptionalKindHelper.wrap(Optional.of(Maybe.just(true))));
    }
  }
}
