package org.simulation.hkt.lazy;

import static org.assertj.core.api.Assertions.*;
import static org.simulation.hkt.lazy.LazyKindHelper.*; // Import static methods from helper

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.exception.KindUnwrapException;

@DisplayName("LazyKindHelper Tests")
class LazyKindHelperTest {

  // Counter for tracking evaluations
  private final AtomicInteger counter = new AtomicInteger(0);

  // Simple Lazy computations for testing
  private final Supplier<String> computation =
      () -> {
        counter.incrementAndGet();
        return "Computed";
      };
  private final Lazy<String> lazyDefer = Lazy.defer(computation);
  private final Lazy<String> lazyNow = Lazy.now("Now");
  private final RuntimeException testException = new RuntimeException("Lazy Computation Failed");
  private final Lazy<String> failingLazy =
      Lazy.defer(
          () -> {
            counter.incrementAndGet();
            throw testException;
          });

  @Nested
  @DisplayName("wrap()")
  class WrapTests {
    @Test
    void wrap_shouldReturnHolderForDeferredLazy() {
      Kind<LazyKind<?>, String> kind = wrap(lazyDefer);
      assertThat(kind).isInstanceOf(LazyHolder.class);
      assertThat(unwrap(kind)).isSameAs(lazyDefer);
    }

    @Test
    void wrap_shouldReturnHolderForNowLazy() {
      Kind<LazyKind<?>, String> kind = wrap(lazyNow);
      assertThat(kind).isInstanceOf(LazyHolder.class);
      assertThat(unwrap(kind)).isSameAs(lazyNow);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> wrap(null))
          .withMessageContaining("Input Lazy cannot be null");
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {
    @Test
    void unwrap_shouldReturnOriginalLazy() {
      Kind<LazyKind<?>, String> kindDefer = wrap(lazyDefer);
      assertThat(unwrap(kindDefer)).isSameAs(lazyDefer);

      Kind<LazyKind<?>, String> kindNow = wrap(lazyNow);
      assertThat(unwrap(kindNow)).isSameAs(lazyNow);
    }

    // Dummy Kind implementation that is not LazyHolder
    record DummyLazyKind<A>() implements Kind<LazyKind<?>, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      Kind<LazyKind<?>, String> unknownKind = new DummyLazyKind<>();
      assertThatThrownBy(() -> unwrap(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_KIND_TYPE_MSG + DummyLazyKind.class.getName());
    }

    @Test
    void unwrap_shouldThrowForHolderWithNullLazy() {
      LazyHolder<Double> holderWithNull = new LazyHolder<>(null);
      // Cast needed for test setup
      @SuppressWarnings("unchecked")
      Kind<LazyKind<?>, Double> kind = (Kind<LazyKind<?>, Double>) (Kind<?, ?>) holderWithNull;

      assertThatThrownBy(() -> unwrap(kind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(INVALID_HOLDER_STATE_MSG);
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {
    @Test
    void defer_shouldWrapDeferredComputation() {
      counter.set(0);
      Supplier<Integer> supplier =
          () -> {
            counter.incrementAndGet();
            return 123;
          };
      Kind<LazyKind<?>, Integer> kind = LazyKindHelper.defer(supplier);

      // Should not have evaluated yet
      assertThat(counter.get()).isZero();

      // Unwrap and check type
      Lazy<Integer> lazy = unwrap(kind);
      assertThat(lazy).isNotNull();

      // Force evaluation
      assertThat(lazy.force()).isEqualTo(123);
      assertThat(counter.get()).isEqualTo(1);

      // Force again - should be memoized
      assertThat(lazy.force()).isEqualTo(123);
      assertThat(counter.get()).isEqualTo(1); // Counter should not increment again
    }

    @Test
    void defer_shouldThrowNPEForNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> LazyKindHelper.defer(null))
          .withMessageContaining("computation"); // Message from Lazy.defer check
    }

    @Test
    void now_shouldWrapAlreadyEvaluatedValue() {
      counter.set(0);
      Kind<LazyKind<?>, String> kind = LazyKindHelper.now("Precomputed");

      // Should not evaluate anything
      assertThat(counter.get()).isZero();

      // Unwrap and check type
      Lazy<String> lazy = unwrap(kind);
      assertThat(lazy).isNotNull();

      // Force evaluation - should return immediately
      assertThat(lazy.force()).isEqualTo("Precomputed");
      assertThat(counter.get()).isZero(); // Still zero

      // Force again
      assertThat(lazy.force()).isEqualTo("Precomputed");
      assertThat(counter.get()).isZero();
    }

    @Test
    void now_shouldWrapNullValue() {
      Kind<LazyKind<?>, String> kind = LazyKindHelper.now(null);
      Lazy<String> lazy = unwrap(kind);
      assertThat(lazy.force()).isNull();
    }
  }

  @Nested
  @DisplayName("force()")
  class ForceTests {
    @Test
    void force_shouldEvaluateDeferredKindAndMemoize() {
      counter.set(0);
      Kind<LazyKind<?>, String> kind = wrap(lazyDefer); // Uses computation

      assertThat(counter.get()).isZero();
      assertThat(force(kind)).isEqualTo("Computed"); // First force evaluates
      assertThat(counter.get()).isEqualTo(1);

      assertThat(force(kind)).isEqualTo("Computed"); // Second force uses memoized value
      assertThat(counter.get()).isEqualTo(1); // Counter remains 1
    }

    @Test
    void force_shouldReturnValueForNowKind() {
      counter.set(0);
      Kind<LazyKind<?>, String> kind = wrap(lazyNow); // Already evaluated

      assertThat(counter.get()).isZero();
      assertThat(force(kind)).isEqualTo("Now");
      assertThat(counter.get()).isZero(); // No computation needed

      assertThat(force(kind)).isEqualTo("Now");
      assertThat(counter.get()).isZero();
    }

    @Test
    void force_shouldPropagateExceptionFromLazy() {
      counter.set(0);
      Kind<LazyKind<?>, String> kind = wrap(failingLazy);

      assertThat(counter.get()).isZero();
      assertThatThrownBy(() -> force(kind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException);
      assertThat(counter.get()).isEqualTo(1); // Computation ran once and failed

      // Forcing again should re-throw the cached exception without recomputing
      counter.set(0); // Reset counter to ensure no re-computation
      assertThatThrownBy(() -> force(kind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException);
      assertThat(counter.get()).isZero(); // Should not have recomputed
    }

    @Test
    void force_shouldThrowIfKindIsInvalid() {
      assertThatThrownBy(() -> force(null))
          .isInstanceOf(KindUnwrapException.class); // Propagates unwrap exception
    }
  }

  @Nested
  @DisplayName("Private Constructor")
  class PrivateConstructorTest {
    @Test
    @DisplayName("should throw UnsupportedOperationException when invoked via reflection")
    void constructor_shouldThrowException() throws NoSuchMethodException {
      Constructor<LazyKindHelper> constructor = LazyKindHelper.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause()
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}
