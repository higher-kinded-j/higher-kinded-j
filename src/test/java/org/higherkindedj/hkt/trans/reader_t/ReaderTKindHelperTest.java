package org.higherkindedj.hkt.trans.reader_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReaderTKindHelper Tests (Outer: OptionalKind.Witness, Env: String)")
class ReaderTKindHelperTest {

  private Monad<OptionalKind.Witness> outerMonad;
  // Environment type R_ENV is String for these tests
  private final String testEnv = "testEnvironment";

  @BeforeEach
  void setUp() {
    outerMonad = new OptionalMonad();
  }

  // Helper to create a ReaderT for testing
  // F = OptionalKind.Witness, R_ENV = String
  private <A> ReaderT<OptionalKind.Witness, String, A> createReaderT(
      Function<String, Kind<OptionalKind.Witness, A>> runFn) {
    return ReaderT.of(runFn);
  }

  @Test
  @DisplayName("private constructor should prevent instantiation")
  void privateConstructor_shouldThrowException() {
    assertThatThrownBy(
            () -> {
              Constructor<ReaderTKindHelper> constructor =
                  ReaderTKindHelper.class.getDeclaredConstructor();
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
    @DisplayName("should wrap a non-null ReaderT into a Kind<ReaderTKind.Witness<F,R>,A>")
    void wrap_nonNullReaderT_shouldReturnReaderTKind() {
      ReaderT<OptionalKind.Witness, String, Integer> concreteReaderT =
          createReaderT(env -> OptionalKindHelper.wrap(Optional.of(env.length())));

      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> wrapped =
          ReaderTKindHelper.wrap(concreteReaderT);

      assertThat(wrapped).isNotNull().isInstanceOf(ReaderT.class);
      assertThat(ReaderTKindHelper.<OptionalKind.Witness, String, Integer>unwrap(wrapped))
          .isSameAs(concreteReaderT);
    }

    @Test
    @DisplayName("should throw NullPointerException when wrapping null")
    void wrap_nullReaderT_shouldThrowNullPointerException() {
      assertThatThrownBy(() -> ReaderTKindHelper.wrap(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Input ReaderT cannot be null for wrap");
    }
  }

  @Nested
  @DisplayName("unwrap() tests")
  class UnwrapTests {
    @Test
    @DisplayName("should unwrap a valid Kind to the original ReaderT instance")
    void unwrap_validKind_shouldReturnReaderT() {
      ReaderT<OptionalKind.Witness, String, Integer> originalReaderT =
          createReaderT(env -> OptionalKindHelper.wrap(Optional.of(env.hashCode())));
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> wrappedKind =
          ReaderTKindHelper.wrap(originalReaderT);

      ReaderT<OptionalKind.Witness, String, Integer> unwrappedReaderT =
          ReaderTKindHelper.unwrap(wrappedKind);

      assertThat(unwrappedReaderT).isSameAs(originalReaderT);
    }

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping null")
    void unwrap_nullKind_shouldThrowKindUnwrapException() {
      assertThatThrownBy(() -> ReaderTKindHelper.<OptionalKind.Witness, String, Object>unwrap(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage(ReaderTKindHelper.INVALID_KIND_NULL_MSG);
    }

    // Dummy Kind for testing invalid type unwrap
    // F is outer monad witness, R_ENV is environment type
    private static class OtherKindWitness<F_Witness, R_ENV_Witness> {}

    private static class OtherKind<F, R_ENV, A> implements Kind<OtherKindWitness<F, R_ENV>, A> {}

    @Test
    @DisplayName("should throw KindUnwrapException when unwrapping an incorrect Kind type")
    void unwrap_incorrectKindType_shouldThrowKindUnwrapException() {
      class SimpleIncorrectKind<A> implements Kind<SimpleIncorrectKind<?>, A> {}
      SimpleIncorrectKind<Integer> incorrectKind = new SimpleIncorrectKind<>();

      @SuppressWarnings({"unchecked", "rawtypes"})
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> kindToTest =
          (Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer>) (Kind) incorrectKind;

      assertThatThrownBy(() -> ReaderTKindHelper.unwrap(kindToTest))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageStartingWith(ReaderTKindHelper.INVALID_KIND_TYPE_MSG)
          .hasMessageContaining(SimpleIncorrectKind.class.getName());
    }
  }
}
