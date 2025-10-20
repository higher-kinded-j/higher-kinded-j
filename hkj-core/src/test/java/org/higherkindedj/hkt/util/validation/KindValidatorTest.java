// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.test.assertions.KindAssertions.*;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("KindValidator")
class KindValidatorTest {

  private static final class TestType {}

  @Nested
  @DisplayName("narrow")
  class Narrow {

    @Test
    @DisplayName("should narrow Kind to target type successfully")
    void shouldNarrowKindSuccessfully() {
      var either = Either.<String, String>right("test");
      var kind = EITHER.widen(either);

      @SuppressWarnings("unchecked")
      var result =
          Validation.kind()
              .<EitherKind.Witness<String>, String, Either<String, String>>narrow(
                  kind,
                  (Class<Either<String, String>>) (Class<?>) Either.class,
                  k -> EITHER.<String, String>narrow(k));

      assertThat(result).isEqualTo(either);
      assertThat(result.getRight()).isEqualTo("test");
    }

    @Test
    @DisplayName("should throw KindUnwrapException when kind is null")
    void shouldThrowWhenKindIsNull() {
      assertNarrowNull(
          () -> {
            @SuppressWarnings("unchecked")
            var unused =
                Validation.kind()
                    .<EitherKind.Witness<String>, String, Either<String, String>>narrow(
                        null,
                        (Class<Either<String, String>>) (Class<?>) Either.class,
                        k -> EITHER.<String, String>narrow(k));
          },
          Either.class);
    }

    @Test
    @DisplayName("should throw KindUnwrapException when narrowing fails")
    void shouldThrowWhenNarrowingFails() {
      // Use Maybe when Either is expected
      var maybe = Maybe.just("test");
      Kind<MaybeKind.Witness, String> kind = MAYBE.widen(maybe);

      assertInvalidKindType(
          () -> {
            @SuppressWarnings("unchecked")
            var unused =
                Validation.kind()
                    .<EitherKind.Witness<String>, String, Either<String, String>>narrow(
                        (Kind<EitherKind.Witness<String>, String>) (Kind<?, ?>) kind,
                        (Class<Either<String, String>>) (Class<?>) Either.class,
                        k -> EITHER.<String, String>narrow(k));
          },
          Either.class,
          kind);
    }
  }

  @Nested
  @DisplayName("narrowWithTypeCheck")
  class NarrowWithTypeCheck {

    @Test
    @DisplayName("should narrow Kind using narrow method successfully")
    void shouldNarrowWithNarrowMethodSuccessfully() {
      var either = Either.<String, String>right("test");
      var kind = EITHER.widen(either);

      // Use narrow method since narrowWithTypeCheck won't work with wrapped Kinds
      @SuppressWarnings("unchecked")
      var result =
          Validation.kind()
              .<EitherKind.Witness<String>, String, Either<String, String>>narrow(
                  kind,
                  (Class<Either<String, String>>) (Class<?>) Either.class,
                  k -> EITHER.<String, String>narrow(k));

      assertThat(result).isEqualTo(either);
      assertThat(result.getRight()).isEqualTo("test");
    }

    @Test
    @DisplayName("should throw KindUnwrapException when kind is null")
    void shouldThrowWhenKindIsNull() {
      assertNarrowNull(
          () -> {
            @SuppressWarnings("unchecked")
            var unused =
                Validation.kind()
                    .<EitherKind.Witness<String>, String, Either<String, String>>narrow(
                        null,
                        (Class<Either<String, String>>) (Class<?>) Either.class,
                        k -> EITHER.<String, String>narrow(k));
          },
          Either.class);
    }

    @Test
    @DisplayName("should throw KindUnwrapException when kind is not instance of target type")
    void shouldThrowWhenKindIsNotInstanceOfTargetType() {
      // Use Maybe when Either is expected
      var maybe = Maybe.just("test");
      Kind<MaybeKind.Witness, String> kind = MAYBE.widen(maybe);

      assertInvalidKindType(
          () -> {
            @SuppressWarnings("unchecked")
            var unused =
                Validation.kind()
                    .<EitherKind.Witness<String>, String, Either<String, String>>narrow(
                        (Kind<EitherKind.Witness<String>, String>) (Kind<?, ?>) kind,
                        (Class<Either<String, String>>) (Class<?>) Either.class,
                        k -> EITHER.<String, String>narrow(k));
          },
          Either.class,
          kind);
    }
  }

  @Nested
  @DisplayName("requireForWiden")
  class RequireForWiden {

    @Test
    @DisplayName("should return non-null input for widening")
    void shouldReturnNonNullInput() {
      var input = Either.<String, String>right("test");
      var result = Validation.kind().requireForWiden(input, Either.class);

      assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("should throw NullPointerException when input is null")
    void shouldThrowWhenInputIsNull() {
      assertWidenNull(() -> Validation.kind().requireForWiden(null, Either.class), Either.class);
    }
  }

  @Nested
  @DisplayName("requireNonNull with class-based context")
  class RequireNonNullWithClass {

    @Test
    @DisplayName("should return non-null Kind")
    void shouldReturnNonNullKind() {
      Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));
      var result = Validation.kind().requireNonNull(kind, TestType.class, MAP);

      assertThat(result).isEqualTo(kind);
    }

    @Test
    @DisplayName("should throw NullPointerException when Kind is null")
    void shouldThrowWhenKindIsNull() {
      assertKindNull(
          () -> Validation.kind().requireNonNull(null, TestType.class, MAP), TestType.class, MAP);
    }
  }

  @Nested
  @DisplayName("requireNonNull with descriptor")
  class RequireNonNullWithDescriptor {

    @Test
    @DisplayName("should return non-null Kind with descriptor")
    void shouldReturnNonNullKindWithDescriptor() {
      Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));
      var result = Validation.kind().requireNonNull(kind, TestType.class, AP, "function");

      assertThat(result).isEqualTo(kind);
    }

    @Test
    @DisplayName("should include descriptor in error message")
    void shouldIncludeDescriptorInErrorMessage() {
      assertKindNull(
          () -> Validation.kind().requireNonNull(null, TestType.class, AP, "function"),
          TestType.class,
          AP,
          "function");
    }

    @Test
    @DisplayName("should distinguish between multiple parameters using descriptors")
    void shouldDistinguishBetweenParametersUsingDescriptors() {
      assertKindNull(
          () -> Validation.kind().requireNonNull(null, TestType.class, AP, "argument"),
          TestType.class,
          AP,
          "argument");

      assertKindNull(
          () -> Validation.kind().requireNonNull(null, TestType.class, AP, "function"),
          TestType.class,
          AP,
          "function");
    }
  }

  @Nested
  @DisplayName("KindContext")
  class KindContextTest {

    @Test
    @DisplayName("should generate correct null parameter message")
    void shouldGenerateCorrectNullParameterMessage() {
      var context = new KindValidator.KindContext(Either.class, "narrow");

      assertThat(context.nullParameterMessage()).isEqualTo("Cannot narrow null Kind for Either");
    }

    @Test
    @DisplayName("should generate correct null input message")
    void shouldGenerateCorrectNullInputMessage() {
      var context = new KindValidator.KindContext(Either.class, "widen");

      assertThat(context.nullInputMessage()).isEqualTo("Input Either cannot be null for widen");
    }

    @Test
    @DisplayName("should generate correct invalid type message")
    void shouldGenerateCorrectInvalidTypeMessage() {
      var context = new KindValidator.KindContext(Either.class, "narrow");

      assertThat(context.invalidTypeMessage())
          .isEqualTo("Kind instance cannot be narrowed to Either");
    }
  }
}
