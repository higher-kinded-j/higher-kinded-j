package org.simulation.hkt.maybe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.trymonad.TryKindHelper;


import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.*;
import static org.simulation.hkt.maybe.MaybeKindHelper.*;

@DisplayName("MaybeKindHelper Tests")
class MaybeKindHelperTest {

  // Helper method to add explicit null check in unwrap (if needed, currently handled by switch)
  // Modify unwrap if direct null check is preferred over relying on switch behavior for holder.maybe() == null
  private static <A> Maybe<A> unwrapWithExplicitNullCheck(Kind<MaybeKind<?>, A> kind) {
    return switch (kind) {
      case MaybeHolder<A> holder -> holder.maybe() != null ? holder.maybe() : Maybe.nothing();
      case null, default -> Maybe.nothing();
    };
  }

  @Test
  @DisplayName("Unwrap with Explicit Null Check (Alternative)")
  void unwrap_explicitNullCheckHandlesHolderWithNullMaybe() {
    MaybeHolder<Double> holderWithNull = new MaybeHolder<>(null);
    @SuppressWarnings("unchecked")
    Kind<MaybeKind<?>, Double> kind = holderWithNull;

    Maybe<Double> result = unwrapWithExplicitNullCheck(kind);
    assertThat(result).isNotNull();
    assertThat(result.isNothing()).isTrue();
  }

  @Nested
  @DisplayName("wrap()")
  class WrapTests {
    @Test
    void wrap_shouldReturnHolderForJust() {
      Maybe<String> just = Maybe.just("value");
      Kind<MaybeKind<?>, String> kind = wrap(just);

      assertThat(kind).isInstanceOf(MaybeHolder.class);
      assertThat(unwrap(kind)).isSameAs(just);
    }

    @Test
    void wrap_shouldReturnHolderForNothing() {
      Maybe<Integer> nothing = Maybe.nothing();
      Kind<MaybeKind<?>, Integer> kind = wrap(nothing);

      assertThat(kind).isInstanceOf(MaybeHolder.class);
      assertThat(unwrap(kind)).isSameAs(nothing);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      // Wrap requires a non-null Maybe instance
      assertThatNullPointerException().isThrownBy(() -> wrap(null));
    }
  }

  @Nested
  @DisplayName("just()")
  class JustHelperTests {
    @Test
    void just_shouldWrapJustValue() {
      String value = "test";
      Kind<MaybeKind<?>, String> kind = just(value);

      assertThat(kind).isInstanceOf(MaybeHolder.class);
      Maybe<String> maybe = unwrap(kind);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(value);
    }

    @Test
    void just_shouldThrowForNullInput() {
      // Maybe.just itself throws for null
      assertThatNullPointerException()
          .isThrownBy(() -> just(null))
          .withMessageContaining("Value for Just cannot be null");
    }
  }

  @Nested
  @DisplayName("nothing()")
  class NothingHelperTests {
    @Test
    void nothing_shouldWrapNothingValue() {
      Kind<MaybeKind<?>, Integer> kind = nothing();

      assertThat(kind).isInstanceOf(MaybeHolder.class);
      Maybe<Integer> maybe = unwrap(kind);
      assertThat(maybe.isNothing()).isTrue();
      assertThat(maybe).isSameAs(Maybe.nothing()); // Verify singleton
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {

    // --- Success Cases (implicitly tested by wrap/just/nothing tests) ---
    @Test
    void unwrap_shouldReturnOriginalJust() {
      Maybe<Integer> original = Maybe.just(123);
      Kind<MaybeKind<?>, Integer> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    @Test
    void unwrap_shouldReturnOriginalNothing() {
      Maybe<Integer> original = Maybe.nothing();
      Kind<MaybeKind<?>, Integer> kind = wrap(original);
      assertThat(unwrap(kind)).isSameAs(original);
    }

    // --- Robustness / Failure Cases ---

    @Test
    void unwrap_shouldReturnNothingForNullInput() {
      Maybe<String> result = unwrap(null);
      assertThat(result).isNotNull();
      assertThat(result.isNothing()).isTrue();
    }

    @Test
    void unwrap_shouldReturnNothingForUnknownKindType() {
      Kind<MaybeKind<?>, Integer> unknownKind = new DummyMaybeKind<>();
      Maybe<Integer> result = unwrap(unknownKind);
      assertThat(result).isNotNull();
      assertThat(result.isNothing()).isTrue();
    }

    @Test
    void unwrap_shouldReturnNothingForHolderWithNullMaybe() {
      // Test the specific case where the holder exists but its internal maybe is null
      MaybeHolder<Double> holderWithNull = new MaybeHolder<>(null);
      // Need to cast to satisfy the Kind type parameter in unwrap
      @SuppressWarnings("unchecked")
      Kind<MaybeKind<?>, Double> kind = holderWithNull;

      // The unwrap switch case should match the holder but return Maybe.nothing()
      // because holder.maybe() returns null, triggering the null check.
      Maybe<Double> result = unwrap(kind);
      assertThat(result).isNotNull();
      assertThat(result.isNothing()).isTrue();
    }

    // Dummy Kind implementation that is not MaybeHolder
    record DummyMaybeKind<A>() implements Kind<MaybeKind<?>, A> {
    }
  }

  @Nested
  @DisplayName("Private Constructor")
  class PrivateConstructorTest {

    @Test
    @DisplayName("should throw UnsupportedOperationException when invoked via reflection")
    void constructor_shouldThrowException() throws NoSuchMethodException {
      // Get the private constructor
      Constructor<MaybeKindHelper> constructor = MaybeKindHelper.class.getDeclaredConstructor();

      // Make it accessible
      constructor.setAccessible(true);

      // Assert that invoking the constructor throws the expected exception
      // InvocationTargetException wraps the actual exception thrown by the constructor
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause() // Get the wrapped UnsupportedOperationException
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }

}


