package org.higherkindedj.hkt.trans.reader_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReaderTKindHelper Tests")
class ReaderTKindHelperTest {

  // Simple Environment for testing
  record Config(String setting) {}

  final Config testConfig = new Config("test-setting");

  // Outer Monad (F = OptionalKind<?>)
  private Monad<OptionalKind<?>> outerMonad;
  private ReaderT<OptionalKind<?>, Config, String> baseReaderT;

  @BeforeEach
  void setUp() {
    outerMonad = new OptionalMonad();
    // Create a base ReaderT for testing wrap/unwrap
    baseReaderT = ReaderT.of(cfg -> outerMonad.of("Value:" + cfg.setting()));
  }

  @Nested
  @DisplayName("wrap()")
  class WrapTests {
    @Test
    void wrap_shouldReturnHolderForReaderT() {
      Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> kind =
          ReaderTKindHelper.wrap(baseReaderT);
      assertThat(kind).isInstanceOf(ReaderTKindHelper.ReaderTHolder.class);
      // Unwrap to verify
      assertThat(ReaderTKindHelper.unwrap(kind)).isSameAs(baseReaderT);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      assertThatNullPointerException()
          .isThrownBy(() -> ReaderTKindHelper.wrap(null))
          .withMessageContaining("Input ReaderT cannot be null");
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {
    @Test
    void unwrap_shouldReturnOriginalReaderT() {
      Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> kind =
          ReaderTKindHelper.wrap(baseReaderT);
      assertThat(ReaderTKindHelper.unwrap(kind)).isSameAs(baseReaderT);
    }

    // Dummy Kind implementation that is not ReaderTHolder
    record DummyReaderTKind<F, R, A>() implements Kind<ReaderTKind<F, R, ?>, A> {}

    @Test
    void unwrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> ReaderTKindHelper.unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(ReaderTKindHelper.INVALID_KIND_NULL_MSG);
    }

    @Test
    void unwrap_shouldThrowForUnknownKindType() {
      // Use specific types for the dummy Kind if necessary
      Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> unknownKind = new DummyReaderTKind<>();
      assertThatThrownBy(() -> ReaderTKindHelper.unwrap(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(ReaderTKindHelper.INVALID_KIND_TYPE_MSG);
    }
  }

  // Optional: Explicit tests for the Holder record
  @Nested
  @DisplayName("ReaderTHolder Record Tests")
  class HolderRecordTests {
    // Create some ReaderT instances for comparison
    ReaderT<OptionalKind<?>, Config, Integer> rt1 =
        ReaderT.of(cfg -> outerMonad.of(cfg.setting().length()));
    ReaderT<OptionalKind<?>, Config, Integer> rt2 =
        ReaderT.of(cfg -> outerMonad.of(cfg.setting().length())); // Same logic, different instance
    ReaderT<OptionalKind<?>, Config, Integer> rt3 =
        ReaderT.of(
            cfg -> outerMonad.of(cfg.setting().hashCode())); // Different logic using existing field

    ReaderTKindHelper.ReaderTHolder<OptionalKind<?>, Config, Integer> h1a =
        new ReaderTKindHelper.ReaderTHolder<>(rt1);
    ReaderTKindHelper.ReaderTHolder<OptionalKind<?>, Config, Integer> h1b =
        new ReaderTKindHelper.ReaderTHolder<>(rt1); // Same inner ReaderT instance
    ReaderTKindHelper.ReaderTHolder<OptionalKind<?>, Config, Integer> h2 =
        new ReaderTKindHelper.ReaderTHolder<>(rt2); // Different inner ReaderT instance
    ReaderTKindHelper.ReaderTHolder<OptionalKind<?>, Config, Integer> h3 =
        new ReaderTKindHelper.ReaderTHolder<>(rt3); // Different function

    @Test
    void holderEqualsAndHashCode() {
      assertThat(h1a).isEqualTo(h1b); // Record equality checks fields (same rt1 instance)
      assertThat(h1a).hasSameHashCodeAs(h1b);

      // Equality of functional interfaces (rt1 vs rt2) is reference equality
      assertThat(h1a).isNotEqualTo(h2);
      assertThat(h1a).isNotEqualTo(h3);
      assertThat(h1a).isNotEqualTo(null);
      assertThat(h1a).isNotEqualTo(rt1); // Different type
    }

    @Test
    void holderToString() {
      // Check that toString includes the inner ReaderT's representation
      assertThat(h1a.toString()).startsWith("ReaderTHolder[readerT=ReaderT[run=");
      // Note: Comparing lambda toString is unreliable, just check structure
      // assertThat(h1a.toString()).contains(rt1.run().toString());
      assertThat(h1a.toString()).endsWith("]]");
    }
  }

  @Nested
  @DisplayName("Private Constructor")
  class PrivateConstructorTest {

    @Test
    @DisplayName("should throw UnsupportedOperationException when invoked via reflection")
    void constructor_shouldThrowException() throws NoSuchMethodException {
      Constructor<ReaderTKindHelper> constructor = ReaderTKindHelper.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause()
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}
