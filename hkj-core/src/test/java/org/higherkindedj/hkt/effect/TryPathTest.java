// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for TryPath.
 *
 * <p>Tests cover factory methods, Composable/Combinable/Chainable/Recoverable operations, utility
 * methods, and object methods.
 */
@DisplayName("TryPath<A> Complete Test Suite")
class TryPathTest {

  private static final String TEST_VALUE = "test";
  private static final Integer TEST_INT = 42;
  private static final RuntimeException TEST_EXCEPTION = new RuntimeException("test error");

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.success() creates TryPath with success value")
    void pathSuccessCreatesTryPathWithSuccessValue() {
      TryPath<Integer> path = Path.success(TEST_INT);

      assertThat(path.run().isSuccess()).isTrue();
      assertThat(path.getOrElse(null)).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("Path.failure() creates TryPath with failure")
    void pathFailureCreatesTryPathWithFailure() {
      TryPath<Integer> path = Path.failure(TEST_EXCEPTION);

      assertThat(path.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("Path.tryOf() creates TryPath from successful computation")
    void pathTryOfCreatesSuccessFromSuccessfulComputation() {
      TryPath<Integer> path = Path.tryOf(() -> 10 + 32);

      assertThat(path.run().isSuccess()).isTrue();
      assertThat(path.getOrElse(null)).isEqualTo(42);
    }

    @Test
    @DisplayName("Path.tryOf() creates TryPath from failing computation")
    void pathTryOfCreatesFailureFromFailingComputation() {
      TryPath<Integer> path =
          Path.tryOf(
              () -> {
                throw new RuntimeException("file not found");
              });

      assertThat(path.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("Path.tryPath() creates TryPath from Try")
    void pathTryPathCreatesTryPathFromTry() {
      Try<Integer> success = Try.success(TEST_INT);
      Try<Integer> failure = Try.failure(TEST_EXCEPTION);

      TryPath<Integer> successPath = Path.tryPath(success);
      TryPath<Integer> failurePath = Path.tryPath(failure);

      assertThat(successPath.run().isSuccess()).isTrue();
      assertThat(successPath.getOrElse(null)).isEqualTo(TEST_INT);
      assertThat(failurePath.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("Path.success() accepts null value")
    void pathSuccessAcceptsNull() {
      // Try.success allows null values
      TryPath<String> path = Path.success(null);
      assertThat(path.run().isSuccess()).isTrue();
      assertThat(path.getOrElse("default")).isNull();
    }

    @Test
    @DisplayName("Path.failure() validates non-null")
    void pathFailureValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.failure(null))
          .withMessageContaining("exception must not be null");
    }
  }

  @Nested
  @DisplayName("Run and Getter Methods")
  class RunAndGetterMethodsTests {

    @Test
    @DisplayName("run() returns underlying Try")
    void runReturnsUnderlyingTry() {
      TryPath<Integer> successPath = Path.success(TEST_INT);
      TryPath<Integer> failurePath = Path.failure(TEST_EXCEPTION);

      assertThat(successPath.run()).isInstanceOf(Try.class);
      assertThat(successPath.getOrElse(null)).isEqualTo(TEST_INT);
      assertThat(failurePath.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("getOrElse() returns value for Success")
    void getOrElseReturnsValueForSuccess() {
      TryPath<Integer> path = Path.success(TEST_INT);

      assertThat(path.getOrElse(0)).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("getOrElse() returns default for Failure")
    void getOrElseReturnsDefaultForFailure() {
      TryPath<Integer> path = Path.failure(TEST_EXCEPTION);

      assertThat(path.getOrElse(0)).isEqualTo(0);
    }

    @Test
    @DisplayName("getOrElseGet() returns value for Success without calling supplier")
    void getOrElseGetReturnsValueForSuccessWithoutCallingSupplier() {
      TryPath<Integer> path = Path.success(TEST_INT);
      AtomicBoolean called = new AtomicBoolean(false);

      Integer result =
          path.getOrElseGet(
              () -> {
                called.set(true);
                return 0;
              });

      assertThat(result).isEqualTo(TEST_INT);
      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("getOrElseGet() calls supplier for Failure")
    void getOrElseGetCallsSupplierForFailure() {
      TryPath<Integer> path = Path.failure(TEST_EXCEPTION);
      AtomicBoolean called = new AtomicBoolean(false);

      Integer result =
          path.getOrElseGet(
              () -> {
                called.set(true);
                return 0;
              });

      assertThat(result).isEqualTo(0);
      assertThat(called).isTrue();
    }

    @Test
    @DisplayName("fold() applies appropriate function")
    void foldAppliesAppropriateFunction() {
      TryPath<Integer> successPath = Path.success(TEST_INT);
      TryPath<Integer> failurePath = Path.failure(TEST_EXCEPTION);

      String successResult =
          successPath.fold(value -> "value: " + value, ex -> "error: " + ex.getMessage());

      String failureResult =
          failurePath.fold(value -> "value: " + value, ex -> "error: " + ex.getMessage());

      assertThat(successResult).isEqualTo("value: 42");
      assertThat(failureResult).isEqualTo("error: test error");
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value for Success")
    void mapTransformsValueForSuccess() {
      TryPath<String> path = Path.success(TEST_VALUE);

      TryPath<Integer> result = path.map(String::length);

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.getOrElse(null)).isEqualTo(TEST_VALUE.length());
    }

    @Test
    @DisplayName("map() preserves Failure")
    void mapPreservesFailure() {
      TryPath<String> path = Path.failure(TEST_EXCEPTION);

      TryPath<Integer> result = path.map(String::length);

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      TryPath<String> path = Path.success(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      TryPath<String> path = Path.success("hello");

      TryPath<String> result =
          path.map(String::toUpperCase).map(s -> s + "!").map(s -> s.repeat(2));

      assertThat(result.getOrElse(null)).isEqualTo("HELLO!HELLO!");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      TryPath<String> path = Path.success(TEST_VALUE);
      AtomicBoolean called = new AtomicBoolean(false);

      TryPath<String> result = path.peek(v -> called.set(true));

      assertThat(result.getOrElse(null)).isEqualTo(TEST_VALUE);
      assertThat(called).isTrue();
    }

    @Test
    @DisplayName("peek() does not call consumer for Failure")
    void peekDoesNotCallConsumerForFailure() {
      TryPath<String> path = Path.failure(TEST_EXCEPTION);
      AtomicBoolean called = new AtomicBoolean(false);

      path.peek(v -> called.set(true));

      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("peekFailure() observes exception without modifying")
    void peekFailureObservesExceptionWithoutModifying() {
      TryPath<String> path = Path.failure(TEST_EXCEPTION);
      AtomicBoolean called = new AtomicBoolean(false);

      TryPath<String> result = path.peekFailure(ex -> called.set(true));

      assertThat(result.run().isFailure()).isTrue();
      assertThat(called).isTrue();
    }

    @Test
    @DisplayName("peekFailure() does not call consumer for Success")
    void peekFailureDoesNotCallConsumerForSuccess() {
      TryPath<String> path = Path.success(TEST_VALUE);
      AtomicBoolean called = new AtomicBoolean(false);

      path.peekFailure(ex -> called.set(true));

      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("peek() ignores exception thrown by consumer")
    void peekIgnoresExceptionThrownByConsumer() {
      TryPath<String> path = Path.success(TEST_VALUE);

      TryPath<String> result =
          path.peek(
              v -> {
                throw new RuntimeException("consumer threw");
              });

      // Path should remain unchanged even if consumer throws
      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.getOrElse(null)).isEqualTo(TEST_VALUE);
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains dependent computations for Success")
    void viaChainsComputationsForSuccess() {
      TryPath<String> path = Path.success("hello");

      TryPath<Integer> result = path.via(s -> Path.success(s.length()));

      assertThat(result.getOrElse(null)).isEqualTo(5);
    }

    @Test
    @DisplayName("via() preserves Failure")
    void viaPreservesFailure() {
      TryPath<String> path = Path.failure(TEST_EXCEPTION);

      TryPath<Integer> result = path.via(s -> Path.success(s.length()));

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("via() short-circuits on Failure result")
    void viaShortCircuitsOnFailure() {
      TryPath<String> path = Path.success("hello");
      RuntimeException chainException = new RuntimeException("chain failed");

      TryPath<Integer> result =
          path.via(s -> Path.<Integer>failure(chainException)).via(i -> Path.success(i * 2));

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("via() captures exceptions from mapper")
    void viaCapturesExceptionsFromMapper() {
      TryPath<String> path = Path.success("hello");

      TryPath<Integer> result =
          path.via(
              s -> {
                throw new RuntimeException("mapper failed");
              });

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      TryPath<String> path = Path.success(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() returns failure when mapper returns wrong type")
    void viaReturnsFailureWhenMapperReturnsWrongType() {
      TryPath<String> path = Path.success(TEST_VALUE);

      // via() catches the exception and returns it as a failure
      TryPath<Integer> result = path.via(s -> Path.just(s.length()));

      assertThat(result.run().isFailure()).isTrue();
      result
          .run()
          .fold(
              value -> null,
              ex -> {
                assertThat(ex).isInstanceOf(IllegalArgumentException.class);
                assertThat(ex.getMessage()).contains("via mapper must return TryPath");
                return null;
              });
    }

    @Test
    @DisplayName("then() sequences computations discarding value")
    void thenSequencesComputationsDiscardingValue() {
      TryPath<String> path = Path.success("hello");
      AtomicBoolean firstExecuted = new AtomicBoolean(false);

      TryPath<Integer> result =
          path.peek(v -> firstExecuted.set(true)).then(() -> Path.success(42));

      assertThat(result.getOrElse(null)).isEqualTo(42);
      assertThat(firstExecuted).isTrue();
    }

    @Test
    @DisplayName("then() preserves Failure")
    void thenPreservesFailure() {
      TryPath<String> path = Path.failure(TEST_EXCEPTION);

      TryPath<Integer> result = path.then(() -> Path.success(42));

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("then() captures exceptions from supplier")
    void thenCapturesExceptionsFromSupplier() {
      TryPath<String> path = Path.success("hello");

      TryPath<Integer> result =
          path.then(
              () -> {
                throw new RuntimeException("supplier failed");
              });

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("then() throws when supplier returns wrong type")
    void thenThrowsWhenSupplierReturnsWrongType() {
      TryPath<String> path = Path.success(TEST_VALUE);

      // then() catches the exception and returns it as a failure
      TryPath<Integer> result = path.then(() -> Path.just(42));

      assertThat(result.run().isFailure()).isTrue();
      result
          .run()
          .fold(
              value -> null,
              ex -> {
                assertThat(ex).isInstanceOf(IllegalArgumentException.class);
                assertThat(ex.getMessage()).contains("then supplier must return TryPath");
                return null;
              });
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines two Success values")
    void zipWithCombinesTwoSuccessValues() {
      TryPath<String> first = Path.success("hello");
      TryPath<Integer> second = Path.success(3);

      TryPath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.getOrElse(null)).isEqualTo("hellohellohello");
    }

    @Test
    @DisplayName("zipWith() returns Failure if first is Failure")
    void zipWithReturnsFailureIfFirstIsFailure() {
      TryPath<String> first = Path.failure(TEST_EXCEPTION);
      TryPath<Integer> second = Path.success(3);

      TryPath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("zipWith() returns Failure if second is Failure")
    void zipWithReturnsFailureIfSecondIsFailure() {
      TryPath<String> first = Path.success("hello");
      RuntimeException secondException = new RuntimeException("second failed");
      TryPath<Integer> second = Path.failure(secondException);

      TryPath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("zipWith() captures exceptions from combiner")
    void zipWithCapturesExceptionsFromCombiner() {
      TryPath<String> first = Path.success("hello");
      TryPath<Integer> second = Path.success(3);

      TryPath<String> result =
          first.zipWith(
              second,
              (s, n) -> {
                throw new RuntimeException("combiner failed");
              });

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      TryPath<String> path = Path.success(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(Path.success("x"), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith3() combines three Success values")
    void zipWith3CombinesThreeSuccessValues() {
      TryPath<String> first = Path.success("hello");
      TryPath<String> second = Path.success(" ");
      TryPath<String> third = Path.success("world");

      TryPath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.getOrElse(null)).isEqualTo("hello world");
    }

    @Test
    @DisplayName("zipWith() throws when given non-TryPath")
    void zipWithThrowsWhenGivenNonTryPath() {
      TryPath<String> path = Path.success(TEST_VALUE);
      MaybePath<Integer> maybePath = Path.just(TEST_INT);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(maybePath, (s, i) -> s + i))
          .withMessageContaining("Cannot zipWith non-TryPath");
    }

    @Test
    @DisplayName("zipWith3() returns Failure if first is Failure")
    void zipWith3ReturnsFailureIfFirstIsFailure() {
      TryPath<String> first = Path.failure(TEST_EXCEPTION);
      TryPath<String> second = Path.success(" ");
      TryPath<String> third = Path.success("world");

      TryPath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("zipWith3() returns Failure if second is Failure")
    void zipWith3ReturnsFailureIfSecondIsFailure() {
      TryPath<String> first = Path.success("hello");
      TryPath<String> second = Path.failure(TEST_EXCEPTION);
      TryPath<String> third = Path.success("world");

      TryPath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("zipWith3() returns Failure if third is Failure")
    void zipWith3ReturnsFailureIfThirdIsFailure() {
      TryPath<String> first = Path.success("hello");
      TryPath<String> second = Path.success(" ");
      TryPath<String> third = Path.failure(TEST_EXCEPTION);

      TryPath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().isFailure()).isTrue();
    }
  }

  @Nested
  @DisplayName("Recoverable Operations")
  class RecoverableOperationsTests {

    @Test
    @DisplayName("recover() provides fallback for Failure")
    void recoverProvidesFallbackForFailure() {
      TryPath<String> path = Path.failure(TEST_EXCEPTION);

      TryPath<String> result = path.recover(ex -> "recovered: " + ex.getMessage());

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.getOrElse(null)).isEqualTo("recovered: test error");
    }

    @Test
    @DisplayName("recover() preserves Success value")
    void recoverPreservesSuccessValue() {
      TryPath<String> path = Path.success(TEST_VALUE);

      TryPath<String> result = path.recover(ex -> "recovered");

      assertThat(result.getOrElse(null)).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recoverWith() provides fallback path for Failure")
    void recoverWithProvidesFallbackPathForFailure() {
      TryPath<String> path = Path.failure(TEST_EXCEPTION);

      TryPath<String> result = path.recoverWith(ex -> Path.success("fallback"));

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.getOrElse(null)).isEqualTo("fallback");
    }

    @Test
    @DisplayName("recoverWith() preserves Success value")
    void recoverWithPreservesSuccessValue() {
      TryPath<String> path = Path.success(TEST_VALUE);

      TryPath<String> result = path.recoverWith(ex -> Path.success("fallback"));

      assertThat(result.getOrElse(null)).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("orElse() provides alternative path for Failure")
    void orElseAlternativeForFailure() {
      TryPath<String> path = Path.failure(TEST_EXCEPTION);

      TryPath<String> result = path.orElse(() -> Path.success("alternative"));

      assertThat(result.getOrElse(null)).isEqualTo("alternative");
    }

    @Test
    @DisplayName("orElse() preserves Success value")
    void orElsePreservesSuccessValue() {
      TryPath<String> path = Path.success(TEST_VALUE);
      AtomicBoolean called = new AtomicBoolean(false);

      TryPath<String> result =
          path.orElse(
              () -> {
                called.set(true);
                return Path.success("alternative");
              });

      assertThat(result.getOrElse(null)).isEqualTo(TEST_VALUE);
      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("mapException() transforms exception type")
    void mapExceptionTransformsExceptionType() {
      TryPath<String> path = Path.failure(TEST_EXCEPTION);

      TryPath<String> result =
          path.mapException(ex -> new IllegalStateException("wrapped: " + ex.getMessage()));

      assertThat(result.run().isFailure()).isTrue();
      result
          .run()
          .fold(
              value -> null,
              ex -> {
                assertThat(ex).isInstanceOf(IllegalStateException.class);
                assertThat(ex.getMessage()).contains("wrapped");
                return null;
              });
    }

    @Test
    @DisplayName("mapException() preserves Success value")
    void mapExceptionPreservesSuccessValue() {
      TryPath<String> path = Path.success(TEST_VALUE);

      TryPath<String> result = path.mapException(ex -> new IllegalStateException("wrapped"));

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.getOrElse(null)).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recoverWith() throws when recovery returns wrong type")
    @SuppressWarnings("unchecked")
    void recoverWithThrowsWhenRecoveryReturnsWrongType() {
      TryPath<String> path = Path.failure(TEST_EXCEPTION);

      // Use raw types to bypass compile-time type checking and test runtime validation
      assertThatIllegalArgumentException()
          .isThrownBy(
              () -> {
                var rawPath = (TryPath) path;
                rawPath.recoverWith(ex -> Path.just("fallback"));
              })
          .withMessageContaining("recovery must return TryPath");
    }

    @Test
    @DisplayName("orElse() throws when alternative returns wrong type")
    @SuppressWarnings("unchecked")
    void orElseThrowsWhenAlternativeReturnsWrongType() {
      TryPath<String> path = Path.failure(TEST_EXCEPTION);

      // Use raw types to bypass compile-time type checking and test runtime validation
      assertThatIllegalArgumentException()
          .isThrownBy(
              () -> {
                var rawPath = (TryPath) path;
                rawPath.orElse(() -> Path.just("alternative"));
              })
          .withMessageContaining("alternative must return TryPath");
    }

    @Test
    @DisplayName("mapError() returns path cast to new error type")
    void mapErrorReturnsCastPath() {
      TryPath<String> successPath = Path.success(TEST_VALUE);
      TryPath<String> failurePath = Path.failure(TEST_EXCEPTION);

      // mapError for TryPath just casts - the error type is fixed as Throwable
      var successResult = successPath.mapError(ex -> "mapped error");
      var failureResult = failurePath.mapError(ex -> "mapped error");

      // Both should still be TryPath instances
      assertThat(successResult).isInstanceOf(TryPath.class);
      assertThat(failureResult).isInstanceOf(TryPath.class);
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toMaybePath() converts Success to Just")
    void toMaybePathConvertsSuccessToJust() {
      TryPath<String> path = Path.success(TEST_VALUE);

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.getOrElse(null)).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toMaybePath() converts Failure to Nothing")
    void toMaybePathConvertsFailureToNothing() {
      TryPath<String> path = Path.failure(TEST_EXCEPTION);

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("toEitherPath() converts Success to Right")
    void toEitherPathConvertsSuccessToRight() {
      TryPath<String> path = Path.success(TEST_VALUE);

      EitherPath<String, String> result = path.toEitherPath(Throwable::getMessage);

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toEitherPath() converts Failure to Left")
    void toEitherPathConvertsFailureToLeft() {
      TryPath<String> path = Path.failure(TEST_EXCEPTION);

      EitherPath<String, String> result = path.toEitherPath(Throwable::getMessage);

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo("test error");
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("equals() works correctly for Success")
    void equalsWorksCorrectlyForSuccess() {
      TryPath<Integer> path1 = Path.success(TEST_INT);
      TryPath<Integer> path2 = Path.success(TEST_INT);
      TryPath<Integer> path3 = Path.success(99);

      assertThat(path1).isEqualTo(path2);
      assertThat(path1).isNotEqualTo(path3);
    }

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsReturnsTrueForSameInstance() {
      TryPath<Integer> path = Path.success(TEST_INT);
      assertThat(path.equals(path)).isTrue();
    }

    @Test
    @DisplayName("equals() returns false for non-TryPath")
    void equalsReturnsFalseForNonTryPath() {
      TryPath<Integer> path = Path.success(TEST_INT);
      assertThat(path.equals("not a TryPath")).isFalse();
      assertThat(path.equals(null)).isFalse();
      assertThat(path.equals(Path.just(TEST_INT))).isFalse();
    }

    @Test
    @DisplayName("hashCode() is consistent with equals for Success")
    void hashCodeIsConsistentWithEqualsForSuccess() {
      TryPath<Integer> path1 = Path.success(TEST_INT);
      TryPath<Integer> path2 = Path.success(TEST_INT);

      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      TryPath<Integer> successPath = Path.success(TEST_INT);
      TryPath<Integer> failurePath = Path.failure(TEST_EXCEPTION);

      assertThat(successPath.toString()).contains("TryPath");
      assertThat(successPath.toString()).contains("Success");
      assertThat(failurePath.toString()).contains("TryPath");
      assertThat(failurePath.toString()).contains("Failure");
    }
  }

  @Nested
  @DisplayName("Complex Chaining Patterns")
  class ComplexChainingPatternsTests {

    @Test
    @DisplayName("Exception handling pattern")
    void exceptionHandlingPattern() {
      Function<String, TryPath<Integer>> parseInteger = s -> Path.tryOf(() -> Integer.parseInt(s));

      Function<Integer, TryPath<Double>> divide =
          i ->
              Path.tryOf(
                  () -> {
                    if (i == 0) throw new ArithmeticException("Division by zero");
                    return 100.0 / i;
                  });

      TryPath<Double> success = Path.success("10").via(parseInteger).via(divide);

      assertThat(success.getOrElse(null)).isEqualTo(10.0);

      TryPath<Double> parseFailure = Path.success("not-a-number").via(parseInteger).via(divide);

      assertThat(parseFailure.run().isFailure()).isTrue();

      TryPath<Double> divisionFailure = Path.success("0").via(parseInteger).via(divide);

      assertThat(divisionFailure.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("Combining multiple sources")
    void combiningMultipleSources() {
      TryPath<String> firstName = Path.success("John");
      TryPath<String> lastName = Path.success("Doe");
      TryPath<Integer> age = Path.success(30);

      TryPath<String> fullRecord =
          firstName.zipWith3(
              lastName, age, (first, last, a) -> String.format("%s %s, age %d", first, last, a));

      assertThat(fullRecord.getOrElse(null)).isEqualTo("John Doe, age 30");
    }

    @Test
    @DisplayName("Recovery with fallback chain")
    void recoveryWithFallbackChain() {
      TryPath<String> primary = Path.failure(new RuntimeException("primary failed"));
      TryPath<String> secondary = Path.failure(new RuntimeException("secondary failed"));
      TryPath<String> tertiary = Path.success("fallback");

      TryPath<String> result = primary.orElse(() -> secondary).orElse(() -> tertiary);

      assertThat(result.getOrElse(null)).isEqualTo("fallback");
    }

    @Test
    @DisplayName("File operations simulation")
    void fileOperationsSimulation() {
      Function<String, TryPath<String>> readFile =
          filename ->
              Path.tryOf(
                  () -> {
                    if (filename.endsWith(".txt")) {
                      return "file content of " + filename;
                    }
                    throw new RuntimeException("Unsupported file format");
                  });

      Function<String, TryPath<Integer>> countWords =
          content -> Path.tryOf(() -> content.split("\\s+").length);

      TryPath<Integer> success = Path.success("test.txt").via(readFile).via(countWords);

      assertThat(success.run().isSuccess()).isTrue();

      TryPath<Integer> failure = Path.success("test.json").via(readFile).via(countWords);

      assertThat(failure.run().isFailure()).isTrue();
    }
  }
}
