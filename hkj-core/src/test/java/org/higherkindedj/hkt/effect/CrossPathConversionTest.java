// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.NoSuchElementException;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for cross-path conversions.
 *
 * <p>Tests verify that conversions between path types preserve values correctly and handle
 * error/empty cases appropriately.
 */
@DisplayName("Cross-Path Conversion Tests")
class CrossPathConversionTest {

  private static final String TEST_VALUE = "test";
  private static final String TEST_ERROR = "error";
  private static final Semigroup<String> STRING_SEMIGROUP = (a, b) -> a + ", " + b;

  // ===== MaybePath Conversions =====

  @Nested
  @DisplayName("MaybePath Conversions")
  class MaybePathConversions {

    @Test
    @DisplayName("MaybePath(Just) → EitherPath(Right)")
    void maybeJustToEitherRight() {
      MaybePath<String> source = Path.just(TEST_VALUE);

      EitherPath<String, String> result = source.toEitherPath(TEST_ERROR);

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("MaybePath(Nothing) → EitherPath(Left)")
    void maybeNothingToEitherLeft() {
      MaybePath<String> source = Path.nothing();

      EitherPath<String, String> result = source.toEitherPath(TEST_ERROR);

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("MaybePath(Just) → TryPath(Success)")
    void maybeJustToTrySuccess() {
      MaybePath<String> source = Path.just(TEST_VALUE);

      TryPath<String> result = source.toTryPath(NoSuchElementException::new);

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.run().orElse(null)).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("MaybePath(Nothing) → TryPath(Failure)")
    void maybeNothingToTryFailure() {
      MaybePath<String> source = Path.nothing();

      TryPath<String> result = source.toTryPath(NoSuchElementException::new);

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("MaybePath(Just) → ValidationPath(Valid)")
    void maybeJustToValidationValid() {
      MaybePath<String> source = Path.just(TEST_VALUE);

      ValidationPath<String, String> result = source.toValidationPath(TEST_ERROR, STRING_SEMIGROUP);

      assertThat(result.run().isValid()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("MaybePath(Nothing) → ValidationPath(Invalid)")
    void maybeNothingToValidationInvalid() {
      MaybePath<String> source = Path.nothing();

      ValidationPath<String, String> result = source.toValidationPath(TEST_ERROR, STRING_SEMIGROUP);

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("MaybePath(Just) → OptionalPath(Present)")
    void maybeJustToOptionalPresent() {
      MaybePath<String> source = Path.just(TEST_VALUE);

      OptionalPath<String> result = source.toOptionalPath();

      assertThat(result.run()).isPresent();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("MaybePath(Nothing) → OptionalPath(Empty)")
    void maybeNothingToOptionalEmpty() {
      MaybePath<String> source = Path.nothing();

      OptionalPath<String> result = source.toOptionalPath();

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("MaybePath(Just) → IdPath")
    void maybeJustToIdPath() {
      MaybePath<String> source = Path.just(TEST_VALUE);

      IdPath<String> result = source.toIdPath(NoSuchElementException::new);

      assertThat(result.get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("MaybePath(Nothing) → IdPath throws")
    void maybeNothingToIdPathThrows() {
      MaybePath<String> source = Path.nothing();

      assertThatThrownBy(() -> source.toIdPath(NoSuchElementException::new))
          .isInstanceOf(NoSuchElementException.class);
    }
  }

  // ===== EitherPath Conversions =====

  @Nested
  @DisplayName("EitherPath Conversions")
  class EitherPathConversions {

    @Test
    @DisplayName("EitherPath(Right) → MaybePath(Just)")
    void eitherRightToMaybeJust() {
      EitherPath<String, String> source = Path.right(TEST_VALUE);

      MaybePath<String> result = source.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("EitherPath(Left) → MaybePath(Nothing)")
    void eitherLeftToMaybeNothing() {
      EitherPath<String, String> source = Path.left(TEST_ERROR);

      MaybePath<String> result = source.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("EitherPath(Right) → TryPath(Success)")
    void eitherRightToTrySuccess() {
      EitherPath<String, String> source = Path.right(TEST_VALUE);

      TryPath<String> result = source.toTryPath(RuntimeException::new);

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.run().orElse(null)).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("EitherPath(Left) → TryPath(Failure)")
    void eitherLeftToTryFailure() {
      EitherPath<String, String> source = Path.left(TEST_ERROR);

      TryPath<String> result = source.toTryPath(RuntimeException::new);

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("EitherPath(Right) → ValidationPath(Valid)")
    void eitherRightToValidationValid() {
      EitherPath<String, String> source = Path.right(TEST_VALUE);

      ValidationPath<String, String> result = source.toValidationPath(STRING_SEMIGROUP);

      assertThat(result.run().isValid()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("EitherPath(Left) → ValidationPath(Invalid)")
    void eitherLeftToValidationInvalid() {
      EitherPath<String, String> source = Path.left(TEST_ERROR);

      ValidationPath<String, String> result = source.toValidationPath(STRING_SEMIGROUP);

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("EitherPath(Right) → OptionalPath(Present)")
    void eitherRightToOptionalPresent() {
      EitherPath<String, String> source = Path.right(TEST_VALUE);

      OptionalPath<String> result = source.toOptionalPath();

      assertThat(result.run()).isPresent();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("EitherPath(Left) → OptionalPath(Empty)")
    void eitherLeftToOptionalEmpty() {
      EitherPath<String, String> source = Path.left(TEST_ERROR);

      OptionalPath<String> result = source.toOptionalPath();

      assertThat(result.run()).isEmpty();
    }
  }

  // ===== TryPath Conversions =====

  @Nested
  @DisplayName("TryPath Conversions")
  class TryPathConversions {

    @Test
    @DisplayName("TryPath(Success) → MaybePath(Just)")
    void trySuccessToMaybeJust() {
      TryPath<String> source = Path.success(TEST_VALUE);

      MaybePath<String> result = source.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("TryPath(Failure) → MaybePath(Nothing)")
    void tryFailureToMaybeNothing() {
      TryPath<String> source = Path.failure(new RuntimeException(TEST_ERROR));

      MaybePath<String> result = source.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("TryPath(Success) → EitherPath(Right)")
    void trySuccessToEitherRight() {
      TryPath<String> source = Path.success(TEST_VALUE);

      EitherPath<String, String> result = source.toEitherPath(Throwable::getMessage);

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("TryPath(Failure) → EitherPath(Left)")
    void tryFailureToEitherLeft() {
      TryPath<String> source = Path.failure(new RuntimeException(TEST_ERROR));

      EitherPath<String, String> result = source.toEitherPath(Throwable::getMessage);

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("TryPath(Success) → ValidationPath(Valid)")
    void trySuccessToValidationValid() {
      TryPath<String> source = Path.success(TEST_VALUE);

      ValidationPath<String, String> result =
          source.toValidationPath(Throwable::getMessage, STRING_SEMIGROUP);

      assertThat(result.run().isValid()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("TryPath(Failure) → ValidationPath(Invalid)")
    void tryFailureToValidationInvalid() {
      TryPath<String> source = Path.failure(new RuntimeException(TEST_ERROR));

      ValidationPath<String, String> result =
          source.toValidationPath(Throwable::getMessage, STRING_SEMIGROUP);

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("TryPath(Success) → OptionalPath(Present)")
    void trySuccessToOptionalPresent() {
      TryPath<String> source = Path.success(TEST_VALUE);

      OptionalPath<String> result = source.toOptionalPath();

      assertThat(result.run()).isPresent();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("TryPath(Failure) → OptionalPath(Empty)")
    void tryFailureToOptionalEmpty() {
      TryPath<String> source = Path.failure(new RuntimeException(TEST_ERROR));

      OptionalPath<String> result = source.toOptionalPath();

      assertThat(result.run()).isEmpty();
    }
  }

  // ===== ValidationPath Conversions =====

  @Nested
  @DisplayName("ValidationPath Conversions")
  class ValidationPathConversions {

    @Test
    @DisplayName("ValidationPath(Valid) → EitherPath(Right)")
    void validationValidToEitherRight() {
      ValidationPath<String, String> source = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      EitherPath<String, String> result = source.toEitherPath();

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("ValidationPath(Invalid) → EitherPath(Left)")
    void validationInvalidToEitherLeft() {
      ValidationPath<String, String> source = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      EitherPath<String, String> result = source.toEitherPath();

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("ValidationPath(Valid) → MaybePath(Just)")
    void validationValidToMaybeJust() {
      ValidationPath<String, String> source = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      MaybePath<String> result = source.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("ValidationPath(Invalid) → MaybePath(Nothing)")
    void validationInvalidToMaybeNothing() {
      ValidationPath<String, String> source = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      MaybePath<String> result = source.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("ValidationPath(Valid) → TryPath(Success)")
    void validationValidToTrySuccess() {
      ValidationPath<String, String> source = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      TryPath<String> result = source.toTryPath(RuntimeException::new);

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.run().orElse(null)).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("ValidationPath(Invalid) → TryPath(Failure)")
    void validationInvalidToTryFailure() {
      ValidationPath<String, String> source = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      TryPath<String> result = source.toTryPath(RuntimeException::new);

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("ValidationPath(Valid) → OptionalPath(Present)")
    void validationValidToOptionalPresent() {
      ValidationPath<String, String> source = Path.valid(TEST_VALUE, STRING_SEMIGROUP);

      OptionalPath<String> result = source.toOptionalPath();

      assertThat(result.run()).isPresent();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("ValidationPath(Invalid) → OptionalPath(Empty)")
    void validationInvalidToOptionalEmpty() {
      ValidationPath<String, String> source = Path.invalid(TEST_ERROR, STRING_SEMIGROUP);

      OptionalPath<String> result = source.toOptionalPath();

      assertThat(result.run()).isEmpty();
    }
  }

  // ===== OptionalPath Conversions =====

  @Nested
  @DisplayName("OptionalPath Conversions")
  class OptionalPathConversions {

    @Test
    @DisplayName("OptionalPath(Present) → MaybePath(Just)")
    void optionalPresentToMaybeJust() {
      OptionalPath<String> source = Path.present(TEST_VALUE);

      MaybePath<String> result = source.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("OptionalPath(Empty) → MaybePath(Nothing)")
    void optionalEmptyToMaybeNothing() {
      OptionalPath<String> source = Path.absent();

      MaybePath<String> result = source.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("OptionalPath(Present) → EitherPath(Right)")
    void optionalPresentToEitherRight() {
      OptionalPath<String> source = Path.present(TEST_VALUE);

      EitherPath<String, String> result = source.toEitherPath(TEST_ERROR);

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("OptionalPath(Empty) → EitherPath(Left)")
    void optionalEmptyToEitherLeft() {
      OptionalPath<String> source = Path.absent();

      EitherPath<String, String> result = source.toEitherPath(TEST_ERROR);

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("OptionalPath(Present) → ValidationPath(Valid)")
    void optionalPresentToValidationValid() {
      OptionalPath<String> source = Path.present(TEST_VALUE);

      ValidationPath<String, String> result = source.toValidationPath(TEST_ERROR, STRING_SEMIGROUP);

      assertThat(result.run().isValid()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("OptionalPath(Empty) → ValidationPath(Invalid)")
    void optionalEmptyToValidationInvalid() {
      OptionalPath<String> source = Path.absent();

      ValidationPath<String, String> result = source.toValidationPath(TEST_ERROR, STRING_SEMIGROUP);

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).isEqualTo(TEST_ERROR);
    }
  }

  // ===== IdPath Conversions =====

  @Nested
  @DisplayName("IdPath Conversions")
  class IdPathConversions {

    @Test
    @DisplayName("IdPath → MaybePath(Just) for non-null value")
    void idPathToMaybeJust() {
      IdPath<String> source = Path.id(TEST_VALUE);

      MaybePath<String> result = source.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("IdPath → MaybePath(Nothing) for null value")
    void idPathNullToMaybeNothing() {
      IdPath<String> source = Path.id(null);

      MaybePath<String> result = source.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("IdPath → EitherPath(Right)")
    void idPathToEitherRight() {
      IdPath<String> source = Path.id(TEST_VALUE);

      EitherPath<String, String> result = source.toEitherPath();

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }
  }

  // ===== IOPath Conversions =====

  @Nested
  @DisplayName("IOPath Conversions")
  class IOPathConversions {

    @Test
    @DisplayName("IOPath(success) → TryPath(Success)")
    void ioPathSuccessToTrySuccess() {
      IOPath<String> source = Path.io(() -> TEST_VALUE);

      TryPath<String> result = source.toTryPath();

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.run().orElse(null)).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("IOPath(throws) → TryPath(Failure)")
    void ioPathThrowsToTryFailure() {
      IOPath<String> source =
          Path.io(
              () -> {
                throw new RuntimeException(TEST_ERROR);
              });

      TryPath<String> result = source.toTryPath();

      assertThat(result.run().isFailure()).isTrue();
    }
  }

  // ===== GenericPath Conversions =====

  @Nested
  @DisplayName("GenericPath Conversions")
  class GenericPathConversions {

    private static final MaybeMonad MONAD = MaybeMonad.INSTANCE;

    @Test
    @DisplayName("GenericPath(Just) → MaybePath(Just)")
    void genericPathJustToMaybeJust() {
      GenericPath<MaybeKind.Witness, String> source = GenericPath.pure(TEST_VALUE, MONAD);

      MaybePath<String> result = source.toMaybePath(MAYBE::narrow);

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("GenericPath(Nothing) → MaybePath(Nothing)")
    void genericPathNothingToMaybeNothing() {
      GenericPath<MaybeKind.Witness, String> source =
          GenericPath.of(MAYBE.<String>nothing(), MONAD);

      MaybePath<String> result = source.toMaybePath(MAYBE::narrow);

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("GenericPath → EitherPath with custom narrower")
    void genericPathToEitherPath() {
      GenericPath<MaybeKind.Witness, String> source = GenericPath.pure(TEST_VALUE, MONAD);

      EitherPath<String, String> result =
          source.toEitherPath(
              kind -> {
                Maybe<String> maybe = MAYBE.narrow(kind);
                return maybe.isJust() ? Either.right(maybe.get()) : Either.left("No value");
              });

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }
  }

  // ===== Round-Trip Conversions =====

  @Nested
  @DisplayName("Round-Trip Conversions")
  class RoundTripConversions {

    @Test
    @DisplayName("MaybePath → EitherPath → MaybePath preserves value")
    void maybeToEitherToMaybe() {
      MaybePath<String> source = Path.just(TEST_VALUE);

      MaybePath<String> result = source.toEitherPath(TEST_ERROR).toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("EitherPath → ValidationPath → EitherPath preserves value")
    void eitherToValidationToEither() {
      EitherPath<String, String> source = Path.right(TEST_VALUE);

      EitherPath<String, String> result = source.toValidationPath(STRING_SEMIGROUP).toEitherPath();

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("EitherPath → ValidationPath → EitherPath preserves error")
    void eitherToValidationToEitherPreservesError() {
      EitherPath<String, String> source = Path.left(TEST_ERROR);

      EitherPath<String, String> result = source.toValidationPath(STRING_SEMIGROUP).toEitherPath();

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("MaybePath → OptionalPath → MaybePath preserves value")
    void maybeToOptionalToMaybe() {
      MaybePath<String> source = Path.just(TEST_VALUE);

      MaybePath<String> result = source.toOptionalPath().toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("OptionalPath → MaybePath → OptionalPath preserves value")
    void optionalToMaybeToOptional() {
      OptionalPath<String> source = Path.present(TEST_VALUE);

      OptionalPath<String> result = source.toMaybePath().toOptionalPath();

      assertThat(result.run()).isPresent();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("TryPath → EitherPath → TryPath preserves value")
    void tryToEitherToTry() {
      TryPath<String> source = Path.success(TEST_VALUE);

      TryPath<String> result =
          source.toEitherPath(Throwable::getMessage).toTryPath(RuntimeException::new);

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.run().orElse(null)).isEqualTo(TEST_VALUE);
    }
  }

  // ===== Chain Conversions =====

  @Nested
  @DisplayName("Chain Conversions")
  class ChainConversions {

    @Test
    @DisplayName("MaybePath → EitherPath → ValidationPath → TryPath")
    void maybeToEitherToValidationToTry() {
      MaybePath<String> source = Path.just(TEST_VALUE);

      TryPath<String> result =
          source
              .toEitherPath(TEST_ERROR)
              .toValidationPath(STRING_SEMIGROUP)
              .toTryPath(RuntimeException::new);

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.run().orElse(null)).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("OptionalPath → MaybePath → EitherPath → ValidationPath")
    void optionalToMaybeToEitherToValidation() {
      OptionalPath<String> source = Path.present(TEST_VALUE);

      ValidationPath<String, String> result =
          source.toMaybePath().toEitherPath(TEST_ERROR).toValidationPath(STRING_SEMIGROUP);

      assertThat(result.run().isValid()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("Error propagates through chain")
    void errorPropagatesThroughChain() {
      MaybePath<String> source = Path.nothing();

      TryPath<String> result =
          source
              .toEitherPath(TEST_ERROR)
              .toValidationPath(STRING_SEMIGROUP)
              .toTryPath(RuntimeException::new);

      assertThat(result.run().isFailure()).isTrue();
    }
  }
}
