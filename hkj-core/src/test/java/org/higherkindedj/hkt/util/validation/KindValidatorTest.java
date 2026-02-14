// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.test.assertions.KindAssertions.*;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("KindValidator - Enhanced Coverage")
class KindValidatorTest {

  private static final class TestType {}

  private static final class AnotherTestType {}

  @Nested
  @DisplayName("narrow - comprehensive scenarios")
  class NarrowComprehensive {

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

    @Test
    @DisplayName("should handle narrower function throwing exception")
    void shouldHandleNarrowerFunctionException() {
      var either = Either.<String, String>right("test");
      var kind = EITHER.widen(either);

      var expectedException = new RuntimeException("Narrower failed");

      assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(
              () -> {
                @SuppressWarnings("unchecked")
                var unused =
                    Validation.kind()
                        .<EitherKind.Witness<String>, String, Either<String, String>>narrow(
                            kind,
                            (Class<Either<String, String>>) (Class<?>) Either.class,
                            k -> {
                              throw expectedException;
                            });
              })
          .withCause(expectedException)
          .withMessageContaining("Kind instance cannot be narrowed to Either");
    }

    @Test
    @DisplayName("should preserve narrowing for different types")
    void shouldPreserveNarrowingForDifferentTypes() {
      var maybeInt = Maybe.just(42);
      var kindInt = MAYBE.widen(maybeInt);

      var resultInt =
          Validation.kind()
              .<MaybeKind.Witness, Integer, Maybe<Integer>>narrow(
                  kindInt,
                  (Class<Maybe<Integer>>) (Class<?>) Maybe.class,
                  k -> MAYBE.<Integer>narrow(k));

      assertThat(resultInt).isEqualTo(maybeInt);
      assertThat(resultInt.get()).isEqualTo(42);

      var maybeString = Maybe.just("hello");
      var kindString = MAYBE.widen(maybeString);

      var resultString =
          Validation.kind()
              .<MaybeKind.Witness, String, Maybe<String>>narrow(
                  kindString,
                  (Class<Maybe<String>>) (Class<?>) Maybe.class,
                  k -> MAYBE.<String>narrow(k));

      assertThat(resultString).isEqualTo(maybeString);
      assertThat(resultString.get()).isEqualTo("hello");
    }
  }

  @Nested
  @DisplayName("narrowWithTypeCheck - comprehensive scenarios")
  class NarrowWithTypeCheckComprehensive {

    @Test
    @DisplayName("should narrow using custom narrower successfully")
    void shouldNarrowWithCustomNarrowerSuccessfully() {
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
    @DisplayName("should throw KindUnwrapException for wrong type")
    void shouldThrowForWrongType() {
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
  @DisplayName("requireForWiden - comprehensive scenarios")
  class RequireForWidenComprehensive {

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

    @Test
    @DisplayName("should work with different input types")
    void shouldWorkWithDifferentInputTypes() {
      var maybe = Maybe.just(42);
      var result = Validation.kind().requireForWiden(maybe, Maybe.class);
      assertThat(result).isEqualTo(maybe);

      var either = Either.<String, Integer>right(100);
      var result2 = Validation.kind().requireForWiden(either, Either.class);
      assertThat(result2).isEqualTo(either);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "value", "another value"})
    @DisplayName("should validate multiple string values")
    void shouldValidateMultipleStringValues(String value) {
      var input = Either.<String, String>right(value);
      var result = Validation.kind().requireForWiden(input, Either.class);
      assertThat(result).isEqualTo(input);
    }
  }

  @Nested
  @DisplayName("requireNonNull - operation context only")
  class RequireNonNullOperationOnly {

    @Test
    @DisplayName("should return non-null Kind with operation context")
    void shouldReturnNonNullKindWithOperation() {
      Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));
      var result = Validation.kind().requireNonNull(kind, MAP);

      assertThat(result).isEqualTo(kind);
    }

    @Test
    @DisplayName("should throw NullPointerException when Kind is null")
    void shouldThrowWhenKindIsNull() {
      assertKindNull(() -> Validation.kind().requireNonNull(null, MAP), MAP);
    }

    @ParameterizedTest
    @EnumSource(
        value = Operation.class,
        names = {"MAP", "FLAT_MAP", "AP", "TRAVERSE", "FOLD_MAP"})
    @DisplayName("should validate across different operations")
    void shouldValidateAcrossDifferentOperations(Operation operation) {
      Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));
      var result = Validation.kind().requireNonNull(kind, operation);
      assertThat(result).isEqualTo(kind);
    }

    @ParameterizedTest
    @EnumSource(
        value = Operation.class,
        names = {"MAP", "FLAT_MAP", "AP", "TRAVERSE", "FOLD_MAP"})
    @DisplayName("should throw for null Kind across different operations")
    void shouldThrowForNullKindAcrossDifferentOperations(Operation operation) {
      assertKindNull(() -> Validation.kind().requireNonNull(null, operation), operation);
    }
  }

  @Nested
  @DisplayName("requireNonNull - with class context")
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

    @Test
    @DisplayName("should work with different context classes")
    void shouldWorkWithDifferentContextClasses() {
      Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));

      var result1 = Validation.kind().requireNonNull(kind, TestType.class, MAP);
      assertThat(result1).isEqualTo(kind);

      var result2 = Validation.kind().requireNonNull(kind, AnotherTestType.class, MAP);
      assertThat(result2).isEqualTo(kind);
    }
  }

  @Nested
  @DisplayName("requireNonNull - with descriptor")
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

    @ParameterizedTest
    @ValueSource(strings = {"first", "second", "source", "target", "function", "argument"})
    @DisplayName("should handle various descriptor names")
    void shouldHandleVariousDescriptorNames(String descriptor) {
      Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));
      var result = Validation.kind().requireNonNull(kind, TestType.class, MAP_2, descriptor);
      assertThat(result).isEqualTo(kind);
    }

    @Test
    @DisplayName("should work with null descriptor gracefully")
    void shouldWorkWithNullDescriptor() {
      Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));
      var result = Validation.kind().requireNonNull(kind, TestType.class, AP, null);
      assertThat(result).isEqualTo(kind);
    }
  }

  @Nested
  @DisplayName("requireNonNull - with operation and descriptor only - 100% Coverage")
  class RequireNonNullOperationAndDescriptor {

    @Nested
    @DisplayName("Branch 1: descriptor != null")
    class WithNonNullDescriptor {

      @Test
      @DisplayName("should return non-null Kind with operation and descriptor")
      void shouldReturnNonNullKindWithOperationAndDescriptor() {
        Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));
        var result = Validation.kind().requireNonNull(kind, AP, "function");

        assertThat(result).isEqualTo(kind);
        assertThat(result).isSameAs(kind); // Verify same instance returned
      }

      @Test
      @DisplayName("should throw NullPointerException with descriptor in message when kind is null")
      void shouldThrowWithDescriptorInMessage() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> Validation.kind().requireNonNull(null, AP, "function"))
            .withMessage("Kind for ap (function) cannot be null");
      }

      @ParameterizedTest
      @ValueSource(strings = {"function", "argument", "first", "second", "source", "target"})
      @DisplayName("should include descriptor in error message for various descriptors")
      void shouldIncludeDescriptorInErrorMessage(String descriptor) {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> Validation.kind().requireNonNull(null, MAP_2, descriptor))
            .withMessage("Kind for map2 (" + descriptor + ") cannot be null");
      }

      @ParameterizedTest
      @EnumSource(
          value = Operation.class,
          names = {"MAP", "FLAT_MAP", "AP", "TRAVERSE", "FOLD_MAP", "MAP_2"})
      @DisplayName("should handle different operations with non-null descriptor")
      void shouldHandleDifferentOperationsWithDescriptor(Operation operation) {
        Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));
        var result = Validation.kind().requireNonNull(kind, operation, "testDescriptor");

        assertThat(result).isEqualTo(kind);
        assertThat(result).isSameAs(kind);
      }

      @Test
      @DisplayName("should format message correctly with descriptor for AP operation")
      void shouldFormatMessageCorrectlyForApOperation() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> Validation.kind().requireNonNull(null, AP, "function"))
            .withMessageMatching("Kind for ap \\(function\\) cannot be null");
      }

      @Test
      @DisplayName("should handle empty string descriptor as non-null")
      void shouldHandleEmptyStringDescriptor() {
        Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));
        var result = Validation.kind().requireNonNull(kind, MAP, "");

        assertThat(result).isEqualTo(kind);

        // Verify empty descriptor still triggers non-null branch
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> Validation.kind().requireNonNull(null, MAP, ""))
            .withMessage("Kind for map () cannot be null");
      }

      @Test
      @DisplayName("should handle descriptor with special characters")
      void shouldHandleDescriptorWithSpecialCharacters() {
        var specialDescriptor = "func-tion_123!@#$%";

        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> Validation.kind().requireNonNull(null, AP, specialDescriptor))
            .withMessage("Kind for ap (" + specialDescriptor + ") cannot be null");
      }

      @Test
      @DisplayName("should handle very long descriptor")
      void shouldHandleVeryLongDescriptor() {
        var longDescriptor = "descriptor".repeat(100);
        Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));

        var result = Validation.kind().requireNonNull(kind, MAP, longDescriptor);
        assertThat(result).isEqualTo(kind);
      }
    }

    @Nested
    @DisplayName("Branch 2: descriptor == null")
    class WithNullDescriptor {

      @Test
      @DisplayName("should return non-null Kind when descriptor is null")
      void shouldReturnNonNullKindWithNullDescriptor() {
        Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));
        var result = Validation.kind().requireNonNull(kind, MAP, null);

        assertThat(result).isEqualTo(kind);
        assertThat(result).isSameAs(kind); // Verify same instance returned
      }

      @Test
      @DisplayName(
          "should throw NullPointerException without descriptor in message when kind is null and"
              + " descriptor is null")
      void shouldThrowWithoutDescriptorInMessage() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> Validation.kind().requireNonNull(null, MAP, null))
            .withMessage("Kind for map cannot be null");
      }

      @ParameterizedTest
      @EnumSource(
          value = Operation.class,
          names = {
            "MAP",
            "FLAT_MAP",
            "AP",
            "TRAVERSE",
            "FOLD_MAP",
            "MAP_2",
            "MAP_3",
            "MAP_4",
            "MAP_5"
          })
      @DisplayName("should handle different operations with null descriptor")
      void shouldHandleDifferentOperationsWithNullDescriptor(Operation operation) {
        Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));
        var result = Validation.kind().requireNonNull(kind, operation, null);

        assertThat(result).isEqualTo(kind);
        assertThat(result).isSameAs(kind);
      }

      @Test
      @DisplayName("should use operation.toString() when descriptor is null")
      void shouldUseOperationToStringWhenDescriptorIsNull() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> Validation.kind().requireNonNull(null, FLAT_MAP, null))
            .withMessage("Kind for flatMap cannot be null");
      }

      @Test
      @DisplayName("should format message correctly without descriptor for AP operation")
      void shouldFormatMessageCorrectlyForApOperationWithoutDescriptor() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> Validation.kind().requireNonNull(null, AP, null))
            .withMessageMatching("Kind for ap cannot be null");
      }

      @Test
      @DisplayName("should format message correctly without descriptor for MAP_2 operation")
      void shouldFormatMessageCorrectlyForMap2OperationWithoutDescriptor() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> Validation.kind().requireNonNull(null, MAP_2, null))
            .withMessage("Kind for map2 cannot be null");
      }

      @Test
      @DisplayName("should format message correctly without descriptor for TRAVERSE operation")
      void shouldFormatMessageCorrectlyForTraverseOperationWithoutDescriptor() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> Validation.kind().requireNonNull(null, TRAVERSE, null))
            .withMessage("Kind for traverse cannot be null");
      }
    }

    @Nested
    @DisplayName("Branch comparison and edge cases")
    class BranchComparison {

      @Test
      @DisplayName("should produce different messages for null vs non-null descriptor")
      void shouldProduceDifferentMessagesForNullVsNonNullDescriptor() {
        // With descriptor
        var exceptionWithDescriptor =
            catchNullPointerException(() -> Validation.kind().requireNonNull(null, AP, "function"));

        // Without descriptor (null)
        var exceptionWithoutDescriptor =
            catchNullPointerException(() -> Validation.kind().requireNonNull(null, AP, null));

        assertThat(exceptionWithDescriptor.getMessage())
            .isEqualTo("Kind for ap (function) cannot be null");

        assertThat(exceptionWithoutDescriptor.getMessage()).isEqualTo("Kind for ap cannot be null");

        // Messages should be different
        assertThat(exceptionWithDescriptor.getMessage())
            .isNotEqualTo(exceptionWithoutDescriptor.getMessage());
      }

      @Test
      @DisplayName("should return same instance for both branches when Kind is non-null")
      void shouldReturnSameInstanceForBothBranches() {
        Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));

        // With descriptor
        var resultWithDescriptor = Validation.kind().requireNonNull(kind, MAP, "test");

        // Without descriptor
        var resultWithoutDescriptor = Validation.kind().requireNonNull(kind, MAP, null);

        // Both should return the same instance
        assertThat(resultWithDescriptor).isSameAs(kind);
        assertThat(resultWithoutDescriptor).isSameAs(kind);
        assertThat(resultWithDescriptor).isSameAs(resultWithoutDescriptor);
      }

      @Test
      @DisplayName("should handle boundary between null and non-null descriptor")
      void shouldHandleBoundaryBetweenNullAndNonNullDescriptor() {
        Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));

        // Test the exact boundary: empty string is non-null, should use first branch
        var emptyResult = Validation.kind().requireNonNull(kind, MAP, "");
        assertThat(emptyResult).isSameAs(kind);

        var emptyException =
            catchNullPointerException(() -> Validation.kind().requireNonNull(null, MAP, ""));
        assertThat(emptyException.getMessage()).contains("()"); // Should have parentheses

        // null descriptor should use second branch
        var nullResult = Validation.kind().requireNonNull(kind, MAP, null);
        assertThat(nullResult).isSameAs(kind);

        var nullException =
            catchNullPointerException(() -> Validation.kind().requireNonNull(null, MAP, null));
        assertThat(nullException.getMessage()).doesNotContain("("); // Should NOT have parentheses
      }

      @ParameterizedTest
      @EnumSource(Operation.class)
      @DisplayName("should correctly branch for all operations with null descriptor")
      void shouldCorrectlyBranchForAllOperationsWithNullDescriptor(Operation operation) {
        var exception =
            catchNullPointerException(
                () -> Validation.kind().requireNonNull(null, operation, null));

        // Verify second branch: no parentheses in message
        assertThat(exception.getMessage())
            .startsWith("Kind for " + operation.toString())
            .doesNotContain("(")
            .doesNotContain(")");
      }

      @ParameterizedTest
      @EnumSource(Operation.class)
      @DisplayName("should correctly branch for all operations with non-null descriptor")
      void shouldCorrectlyBranchForAllOperationsWithNonNullDescriptor(Operation operation) {
        var exception =
            catchNullPointerException(
                () -> Validation.kind().requireNonNull(null, operation, "desc"));

        // Verify first branch: has parentheses in message
        assertThat(exception.getMessage())
            .startsWith("Kind for " + operation.toString())
            .contains("(desc)");
      }
    }

    @Nested
    @DisplayName("Integration with real-world scenarios")
    class RealWorldIntegration {

      @Test
      @DisplayName("should validate ap operation with function descriptor")
      void shouldValidateApOperationWithFunctionDescriptor() {
        Kind<EitherKind.Witness<String>, String> functionKind = EITHER.widen(Either.right("test"));

        var result = Validation.kind().requireNonNull(functionKind, AP, "function");
        assertThat(result).isSameAs(functionKind);
      }

      @Test
      @DisplayName("should validate ap operation with argument descriptor")
      void shouldValidateApOperationWithArgumentDescriptor() {
        Kind<EitherKind.Witness<String>, String> argumentKind = EITHER.widen(Either.right("test"));

        var result = Validation.kind().requireNonNull(argumentKind, AP, "argument");
        assertThat(result).isSameAs(argumentKind);
      }

      @Test
      @DisplayName("should validate map2 operation with first descriptor")
      void shouldValidateMap2OperationWithFirstDescriptor() {
        Kind<EitherKind.Witness<String>, String> firstKind = EITHER.widen(Either.right("test"));

        var result = Validation.kind().requireNonNull(firstKind, MAP_2, "first");
        assertThat(result).isSameAs(firstKind);
      }

      @Test
      @DisplayName("should validate map2 operation with second descriptor")
      void shouldValidateMap2OperationWithSecondDescriptor() {
        Kind<EitherKind.Witness<String>, String> secondKind = EITHER.widen(Either.right("test"));

        var result = Validation.kind().requireNonNull(secondKind, MAP_2, "second");
        assertThat(result).isSameAs(secondKind);
      }

      @Test
      @DisplayName("should validate simple operations without descriptor")
      void shouldValidateSimpleOperationsWithoutDescriptor() {
        Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));

        var mapResult = Validation.kind().requireNonNull(kind, MAP, null);
        assertThat(mapResult).isSameAs(kind);

        var flatMapResult = Validation.kind().requireNonNull(kind, FLAT_MAP, null);
        assertThat(flatMapResult).isSameAs(kind);

        var traverseResult = Validation.kind().requireNonNull(kind, TRAVERSE, null);
        assertThat(traverseResult).isSameAs(kind);
      }
    }

    // Helper method to catch NullPointerException for testing
    private NullPointerException catchNullPointerException(Runnable runnable) {
      try {
        runnable.run();
        throw new AssertionError("Expected NullPointerException but none was thrown");
      } catch (NullPointerException e) {
        return e;
      }
    }
  }

  @Nested
  @DisplayName("KindContext - comprehensive record validation")
  class KindContextComprehensive {

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

    @Test
    @DisplayName("should handle different target types")
    void shouldHandleDifferentTargetTypes() {
      var contextMaybe = new KindValidator.KindContext(Maybe.class, "narrow");
      assertThat(contextMaybe.nullParameterMessage()).contains("Maybe");

      var contextEither = new KindValidator.KindContext(Either.class, "narrow");
      assertThat(contextEither.nullParameterMessage()).contains("Either");
    }

    @Test
    @DisplayName("should handle different operations")
    void shouldHandleDifferentOperations() {
      var contextNarrow = new KindValidator.KindContext(Either.class, "narrow");
      assertThat(contextNarrow.nullParameterMessage()).contains("narrow");

      var contextWiden = new KindValidator.KindContext(Either.class, "widen");
      assertThat(contextWiden.nullInputMessage()).contains("widen");
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("should throw when target type is null")
    void shouldThrowWhenTargetTypeIsNull(Class<?> nullClass) {
      assertThatExceptionOfType(NullPointerException.class)
          .isThrownBy(() -> new KindValidator.KindContext(nullClass, "narrow"))
          .withMessage("targetType cannot be null");
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("should throw when operation is null")
    void shouldThrowWhenOperationIsNull(String nullOperation) {
      assertThatExceptionOfType(NullPointerException.class)
          .isThrownBy(() -> new KindValidator.KindContext(Either.class, nullOperation))
          .withMessage("operation cannot be null");
    }

    @Test
    @DisplayName("should be a record with proper equality")
    void shouldBeRecordWithProperEquality() {
      var context1 = new KindValidator.KindContext(Either.class, "narrow");
      var context2 = new KindValidator.KindContext(Either.class, "narrow");
      var context3 = new KindValidator.KindContext(Maybe.class, "narrow");

      assertThat(context1).isEqualTo(context2);
      assertThat(context1).isNotEqualTo(context3);
      assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
    }

    @Test
    @DisplayName("should have readable toString representation")
    void shouldHaveReadableToStringRepresentation() {
      var context = new KindValidator.KindContext(Either.class, "narrow");
      var stringRep = context.toString();

      assertThat(stringRep).contains("Either").contains("narrow");
    }
  }

  @Nested
  @DisplayName("Error message format validation")
  class ErrorMessageFormatValidation {

    @Test
    @DisplayName("narrow null should have consistent message format")
    void narrowNullShouldHaveConsistentMessageFormat() {
      assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(
              () -> {
                @SuppressWarnings("unchecked")
                var unused =
                    Validation.kind()
                        .<EitherKind.Witness<String>, String, Either<String, String>>narrow(
                            null,
                            (Class<Either<String, String>>) (Class<?>) Either.class,
                            k -> EITHER.<String, String>narrow(k));
              })
          .withMessageMatching("Cannot narrow null Kind for .*");
    }

    @Test
    @DisplayName("widen null should have consistent message format")
    void widenNullShouldHaveConsistentMessageFormat() {
      assertThatExceptionOfType(NullPointerException.class)
          .isThrownBy(() -> Validation.kind().requireForWiden(null, Either.class))
          .withMessageMatching("Input .* cannot be null for widen");
    }

    @Test
    @DisplayName("requireNonNull should have consistent message format")
    void requireNonNullShouldHaveConsistentMessageFormat() {
      assertThatExceptionOfType(NullPointerException.class)
          .isThrownBy(() -> Validation.kind().requireNonNull(null, TestType.class, MAP))
          .withMessageMatching("Kind for .* cannot be null");
    }

    @Test
    @DisplayName("requireNonNull with descriptor should include descriptor")
    void requireNonNullWithDescriptorShouldIncludeDescriptor() {
      assertThatExceptionOfType(NullPointerException.class)
          .isThrownBy(() -> Validation.kind().requireNonNull(null, TestType.class, AP, "function"))
          .withMessageMatching("Kind for .*\\(function\\) cannot be null");
    }
  }

  @Nested
  @DisplayName("Integration scenarios")
  class IntegrationScenarios {

    @Test
    @DisplayName("should validate complete narrow-widen cycle")
    void shouldValidateCompleteNarrowWidenCycle() {
      // Create original
      var original = Either.<String, Integer>right(42);

      // Validate for widen
      var validated = Validation.kind().requireForWiden(original, Either.class);
      assertThat(validated).isEqualTo(original);

      // Widen
      var widened = EITHER.widen(validated);

      // Validate non-null Kind
      var validatedKind = Validation.kind().requireNonNull(widened, TestType.class, MAP);
      assertThat(validatedKind).isEqualTo(widened);

      // Narrow
      @SuppressWarnings("unchecked")
      var narrowed =
          Validation.kind()
              .<EitherKind.Witness<String>, Integer, Either<String, Integer>>narrow(
                  validatedKind,
                  (Class<Either<String, Integer>>) (Class<?>) Either.class,
                  k -> EITHER.<String, Integer>narrow(k));

      assertThat(narrowed).isEqualTo(original);
      assertThat(narrowed.getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("should handle validation chain with multiple operations")
    void shouldHandleValidationChainWithMultipleOperations() {
      var either = Either.<String, String>right("test");

      // Validate for widen
      var step1 = Validation.kind().requireForWiden(either, Either.class);

      // Widen
      var kind = EITHER.widen(step1);

      // Validate for map
      var step2 = Validation.kind().requireNonNull(kind, TestType.class, MAP);

      // Validate for flatMap
      var step3 = Validation.kind().requireNonNull(step2, TestType.class, FLAT_MAP);

      // Validate for ap
      var step4 = Validation.kind().requireNonNull(step3, TestType.class, AP, "argument");

      assertThat(step4).isEqualTo(kind);
    }

    @Test
    @DisplayName("should fail fast at first validation error in chain")
    void shouldFailFastAtFirstValidationError() {
      assertThatExceptionOfType(NullPointerException.class)
          .isThrownBy(
              () -> {
                // This should fail immediately
                Validation.kind().requireForWiden(null, Either.class);

                // These should never execute
                throw new AssertionError("Should not reach here");
              });
    }
  }

  @Nested
  @DisplayName("Thread safety validation")
  class ThreadSafetyValidation {

    @Test
    @DisplayName("should handle concurrent validation requests")
    void shouldHandleConcurrentValidationRequests() throws InterruptedException {
      int threadCount = 10;
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch doneLatch = new CountDownLatch(threadCount);
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);

      try {
        IntStream.range(0, threadCount)
            .forEach(
                i -> {
                  executor.submit(
                      () -> {
                        try {
                          startLatch.await();

                          var either = Either.<String, Integer>right(i);
                          var validated = Validation.kind().requireForWiden(either, Either.class);
                          var kind = EITHER.widen(validated);
                          var validatedKind =
                              Validation.kind().requireNonNull(kind, TestType.class, MAP);

                          assertThat(validatedKind).isNotNull();

                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        } finally {
                          doneLatch.countDown();
                        }
                      });
                });

        startLatch.countDown();
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();

      } finally {
        executor.shutdown();
      }
    }

    @Test
    @DisplayName("should maintain thread safety for KindContext creation")
    void shouldMaintainThreadSafetyForKindContextCreation() {
      var futures =
          IntStream.range(0, 100)
              .mapToObj(
                  i ->
                      CompletableFuture.supplyAsync(
                          () -> new KindValidator.KindContext(Either.class, "narrow")))
              .toArray(CompletableFuture[]::new);

      CompletableFuture.allOf(futures).join();

      // All contexts should be equal
      var first = ((CompletableFuture<KindValidator.KindContext>) futures[0]).join();
      for (var future : futures) {
        @SuppressWarnings("unchecked")
        var context = ((CompletableFuture<KindValidator.KindContext>) future).join();
        assertThat(context).isEqualTo(first);
      }
    }
  }

  @Nested
  @DisplayName("Edge cases and boundary conditions")
  class EdgeCasesAndBoundaryConditions {

    @Test
    @DisplayName("should handle empty descriptor string")
    void shouldHandleEmptyDescriptorString() {
      Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));
      var result = Validation.kind().requireNonNull(kind, TestType.class, AP, "");
      assertThat(result).isEqualTo(kind);
    }

    @Test
    @DisplayName("should handle very long descriptor string")
    void shouldHandleVeryLongDescriptorString() {
      var longDescriptor = "a".repeat(1000);
      Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));
      var result = Validation.kind().requireNonNull(kind, TestType.class, AP, longDescriptor);
      assertThat(result).isEqualTo(kind);
    }

    @Test
    @DisplayName("should handle descriptor with special characters")
    void shouldHandleDescriptorWithSpecialCharacters() {
      var specialDescriptor = "func-tion_123!@#";
      Kind<EitherKind.Witness<String>, String> kind = EITHER.widen(Either.right("test"));
      var result = Validation.kind().requireNonNull(kind, TestType.class, AP, specialDescriptor);
      assertThat(result).isEqualTo(kind);
    }

    @Test
    @DisplayName("should handle nested generic types")
    void shouldHandleNestedGenericTypes() {
      var either = Either.<String, Maybe<Integer>>right(Maybe.just(42));
      var validated = Validation.kind().requireForWiden(either, Either.class);
      assertThat(validated).isEqualTo(either);
    }

    @Test
    @DisplayName("should maintain validation semantics under high load")
    void shouldMaintainValidationSemanticsUnderHighLoad() {
      IntStream.range(0, 1000)
          .parallel()
          .forEach(
              i -> {
                var either = Either.<String, Integer>right(i);
                var validated = Validation.kind().requireForWiden(either, Either.class);
                assertThat(validated.getRight()).isEqualTo(i);
              });
    }
  }

  @Nested
  @DisplayName("Real-world usage patterns")
  class RealWorldUsagePatterns {

    @Test
    @DisplayName("should validate monad operations chain")
    void shouldValidateMonadOperationsChain() {
      var either = Either.<String, Integer>right(42);
      var kind = EITHER.widen(either);

      // Validate for map
      var forMap = Validation.kind().requireNonNull(kind, TestType.class, MAP);
      assertThat(forMap).isEqualTo(kind);

      // Validate for flatMap
      var forFlatMap = Validation.kind().requireNonNull(kind, TestType.class, FLAT_MAP);
      assertThat(forFlatMap).isEqualTo(kind);
    }

    @Test
    @DisplayName("should validate applicative operations")
    void shouldValidateApplicativeOperations() {
      var kind = EITHER.widen(Either.<String, Integer>right(42));

      // Validate function Kind for ap
      var functionKind = Validation.kind().requireNonNull(kind, TestType.class, AP, "function");
      assertThat(functionKind).isEqualTo(kind);

      // Validate argument Kind for ap
      var argumentKind = Validation.kind().requireNonNull(kind, TestType.class, AP, "argument");
      assertThat(argumentKind).isEqualTo(kind);
    }

    @Test
    @DisplayName("should validate map2 operations")
    void shouldValidateMap2Operations() {
      var kind = EITHER.widen(Either.<String, Integer>right(42));

      // Validate first Kind for map2
      var firstKind = Validation.kind().requireNonNull(kind, TestType.class, MAP_2, "first");
      assertThat(firstKind).isEqualTo(kind);

      // Validate second Kind for map2
      var secondKind = Validation.kind().requireNonNull(kind, TestType.class, MAP_2, "second");
      assertThat(secondKind).isEqualTo(kind);
    }

    @Test
    @DisplayName("should validate traverse operations")
    void shouldValidateTraverseOperations() {
      var kind = EITHER.widen(Either.<String, Integer>right(42));

      var validated = Validation.kind().requireNonNull(kind, TestType.class, TRAVERSE);
      assertThat(validated).isEqualTo(kind);
    }
  }
}
