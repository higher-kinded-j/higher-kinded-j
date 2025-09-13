// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.*;
import static org.higherkindedj.hkt.util.ErrorHandling.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LazyKindHelper Tests (ThrowableSupplier)")
class LazyKindHelperTest {

  // Simple IO action for testing - now using ThrowableSupplier
  private final ThrowableSupplier<String> baseComputation =
      () -> "Result"; // Simple lambda still works
  private final Lazy<String> baseLazy = Lazy.defer(baseComputation);

  private final RuntimeException testException = new RuntimeException("Lazy Failure");
  private final ThrowableSupplier<String> failingComputation =
      () -> {
        throw testException;
      };
  private final Lazy<String> failingLazy = Lazy.defer(failingComputation);

  // Supplier throwing a checked exception
  private final ThrowableSupplier<String> checkedExceptionComputation =
      () -> {
        throw new IOException("Checked IO Failure");
      };
  private final Lazy<String> checkedFailingLazy = Lazy.defer(checkedExceptionComputation);

  @Nested
  @DisplayName("wrap()")
  class WrapTests {
    @Test
    void widen_shouldReturnHolderForLazy() {
      Kind<LazyKind.Witness, String> kind = LAZY.widen(baseLazy);
      assertThat(kind).isInstanceOf(LazyKindHelper.LazyHolder.class);
      assertThat(LAZY.narrow(kind)).isSameAs(baseLazy);
    }

    @Test
    void widen_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> LAZY.widen(null))
          .withMessageContaining(NULL_WIDEN_INPUT_TEMPLATE.formatted("Lazy"));
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {
    @Test
    void narrow_shouldReturnOriginalLazy() {
      Kind<LazyKind.Witness, String> kind = LAZY.widen(baseLazy);
      assertThat(LAZY.narrow(kind)).isSameAs(baseLazy);
    }

    record DummyOtherKind<A>() implements Kind<LazyKind.Witness, A> {}

    @Test
    void narrow_shouldThrowForNullInput() {
      assertThatThrownBy(() -> LAZY.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(NULL_KIND_TEMPLATE.formatted("LazyHolder"));
    }

    @Test
    void narrow_shouldThrowForUnknownKindType() {
      Kind<LazyKind.Witness, String> unknownKind = new DummyOtherKind<>();
      assertThatThrownBy(() -> LAZY.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              INVALID_KIND_TYPE_TEMPLATE.formatted("LazyHolder", DummyOtherKind.class.getName()));
    }
  }

  @Nested
  @DisplayName("Helper Factories")
  class HelperFactoriesTests {
    @Test
    // Add throws Throwable because LAZY.force() is called indirectly via unwrap().LAZY.force()
    void defer_shouldWrapSupplier() throws Throwable {
      AtomicInteger counter = new AtomicInteger(0);
      ThrowableSupplier<Integer> supplier =
          () -> {
            counter.incrementAndGet();
            return 42;
          };
      Kind<LazyKind.Witness, Integer> kind = LAZY.defer(supplier);

      assertThat(counter.get()).isZero();

      Lazy<Integer> lazy = LAZY.narrow(kind);
      assertThat(lazy.force()).isEqualTo(42);
      assertThat(counter.get()).isEqualTo(1);

      assertThat(lazy.force()).isEqualTo(42);
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void defer_shouldThrowNPEForNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> LAZY.defer(null))
          .withMessageContaining("computation"); // Message from Lazy.defer check
    }

    @Test
    void now_shouldWrapAlreadyEvaluatedValue() throws Throwable {
      AtomicInteger counter = new AtomicInteger(0);
      Kind<LazyKind.Witness, String> kind = LAZY.now("Precomputed");

      assertThat(counter.get()).isZero();
      Lazy<String> lazy = LAZY.narrow(kind);
      assertThat(lazy.force()).isEqualTo("Precomputed");
      assertThat(counter.get()).isZero();
    }

    @Test
    void now_shouldWrapNullValue() throws Throwable {
      Kind<LazyKind.Witness, String> kind = LAZY.now(null);
      Lazy<String> lazy = LAZY.narrow(kind);
      assertThat(lazy.force()).isNull();
    }
  }

  @Nested
  @DisplayName("force()")
  class ForceTests {
    @Test
    void force_shouldExecuteWrappedLazy() throws Throwable {
      AtomicInteger counter = new AtomicInteger(0);
      Kind<LazyKind.Witness, String> kind =
          LazyKindHelper.LAZY.defer( // Use ThrowableSupplier implicitly
              () -> {
                counter.incrementAndGet();
                return "Executed";
              });

      assertThat(counter.get()).isZero();
      assertThat(LAZY.force(kind)).isEqualTo("Executed");
      assertThat(counter.get()).isEqualTo(1);

      assertThat(LAZY.force(kind)).isEqualTo("Executed");
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void force_shouldPropagateRuntimeExceptionFromLazy() {
      Kind<LazyKind.Witness, String> failingKind = LAZY.widen(failingLazy);
      assertThatThrownBy(() -> LAZY.force(failingKind))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(testException);
    }

    @Test
    void force_shouldPropagateCheckedExceptionFromLazy() {
      Kind<LazyKind.Witness, String> checkedFailingKind = LAZY.widen(checkedFailingLazy);
      Throwable thrown = catchThrowable(() -> LAZY.force(checkedFailingKind));
      assertThat(thrown).isInstanceOf(IOException.class).hasMessage("Checked IO Failure");
    }

    @Test
    void force_shouldThrowKindUnwrapExceptionIfKindIsInvalid() {
      assertThatThrownBy(() -> LAZY.force(null)).isInstanceOf(KindUnwrapException.class);
    }
  }
}
