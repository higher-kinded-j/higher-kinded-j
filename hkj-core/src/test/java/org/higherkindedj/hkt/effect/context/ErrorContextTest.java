// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.io.IOKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for ErrorContext.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Factory methods: io(), ioEither(), success(), failure(), fromEither()
 *   <li>Chainable operations: map(), via(), flatMap()
 *   <li>Recovery operations: recover(), recoverWith(), orElse(), mapError()
 *   <li>Execution methods: runIO(), runIOOrThrow(), runIOOrElse(), runIOOrElseGet()
 *   <li>Escape hatch: toEitherT()
 * </ul>
 */
@DisplayName("ErrorContext")
class ErrorContextTest {

  private static final String TEST_ERROR = "test error";
  private static final Integer TEST_VALUE = 42;

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodTests {

    @Test
    @DisplayName("io() catches exceptions and maps to error type")
    void ioCatchesExceptions() {
      ErrorContext<IOKind.Witness, String, Integer> ctx =
          ErrorContext.io(
              () -> {
                throw new RuntimeException("boom");
              },
              Throwable::getMessage);

      Either<String, Integer> result = ctx.runIO().unsafeRun();
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("boom");
    }

    @Test
    @DisplayName("io() wraps successful computation")
    void ioWrapsSuccess() {
      ErrorContext<IOKind.Witness, String, Integer> ctx =
          ErrorContext.io(() -> TEST_VALUE, Throwable::getMessage);

      Either<String, Integer> result = ctx.runIO().unsafeRun();
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("io() defers computation until run")
    void ioDefersComputation() {
      AtomicBoolean called = new AtomicBoolean(false);

      ErrorContext<IOKind.Witness, String, Integer> ctx =
          ErrorContext.io(
              () -> {
                called.set(true);
                return TEST_VALUE;
              },
              Throwable::getMessage);

      assertThat(called.get()).isFalse();
      ctx.runIO().unsafeRun();
      assertThat(called.get()).isTrue();
    }

    @Test
    @DisplayName("io() validates non-null computation")
    void ioValidatesComputation() {
      assertThatNullPointerException()
          .isThrownBy(() -> ErrorContext.io(null, Throwable::getMessage))
          .withMessageContaining("computation must not be null");
    }

    @Test
    @DisplayName("io() validates non-null errorMapper")
    void ioValidatesErrorMapper() {
      assertThatNullPointerException()
          .isThrownBy(() -> ErrorContext.io(() -> TEST_VALUE, null))
          .withMessageContaining("errorMapper must not be null");
    }

    @Test
    @DisplayName("ioEither() wraps computation returning Either")
    void ioEitherWrapsComputation() {
      ErrorContext<IOKind.Witness, String, Integer> ctx =
          ErrorContext.ioEither(() -> Either.right(TEST_VALUE));

      Either<String, Integer> result = ctx.runIO().unsafeRun();
      assertThat(result.getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("ioEither() defers computation")
    void ioEitherDefersComputation() {
      AtomicBoolean called = new AtomicBoolean(false);

      ErrorContext<IOKind.Witness, String, Integer> ctx =
          ErrorContext.ioEither(
              () -> {
                called.set(true);
                return Either.right(TEST_VALUE);
              });

      assertThat(called.get()).isFalse();
      ctx.runIO().unsafeRun();
      assertThat(called.get()).isTrue();
    }

    @Test
    @DisplayName("success() creates Right context")
    void successCreatesRight() {
      ErrorContext<IOKind.Witness, String, Integer> ctx = ErrorContext.success(TEST_VALUE);

      Either<String, Integer> result = ctx.runIO().unsafeRun();
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("success() allows null value")
    void successAllowsNull() {
      ErrorContext<IOKind.Witness, String, String> ctx = ErrorContext.success(null);

      Either<String, String> result = ctx.runIO().unsafeRun();
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isNull();
    }

    @Test
    @DisplayName("failure() creates Left context")
    void failureCreatesLeft() {
      ErrorContext<IOKind.Witness, String, Integer> ctx = ErrorContext.failure(TEST_ERROR);

      Either<String, Integer> result = ctx.runIO().unsafeRun();
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("fromEither() wraps Right")
    void fromEitherWrapsRight() {
      ErrorContext<IOKind.Witness, String, Integer> ctx =
          ErrorContext.fromEither(Either.right(TEST_VALUE));

      Either<String, Integer> result = ctx.runIO().unsafeRun();
      assertThat(result.getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("fromEither() wraps Left")
    void fromEitherWrapsLeft() {
      ErrorContext<IOKind.Witness, String, Integer> ctx =
          ErrorContext.fromEither(Either.left(TEST_ERROR));

      Either<String, Integer> result = ctx.runIO().unsafeRun();
      assertThat(result.getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("fromEither() validates non-null")
    void fromEitherValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> ErrorContext.fromEither(null))
          .withMessageContaining("either must not be null");
    }
  }

  @Nested
  @DisplayName("Chainable Operations")
  class ChainableOperationsTests {

    @Test
    @DisplayName("map() transforms success value")
    void mapTransformsSuccess() {
      ErrorContext<IOKind.Witness, String, Integer> ctx = ErrorContext.success(21);

      ErrorContext<IOKind.Witness, String, Integer> result = ctx.map(x -> x * 2);

      assertThat(result.runIO().unsafeRun().getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("map() preserves error")
    void mapPreservesError() {
      ErrorContext<IOKind.Witness, String, Integer> ctx = ErrorContext.failure(TEST_ERROR);

      ErrorContext<IOKind.Witness, String, Integer> result = ctx.map(x -> x * 2);

      assertThat(result.runIO().unsafeRun().getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("map() validates non-null mapper")
    void mapValidatesMapper() {
      ErrorContext<IOKind.Witness, String, Integer> ctx = ErrorContext.success(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() chains successful contexts")
    void viaChainsSuccess() {
      ErrorContext<IOKind.Witness, String, Integer> result =
          ErrorContext.<String, Integer>success(10)
              .via(x -> ErrorContext.success(x + 5))
              .via(x -> ErrorContext.success(x * 2));

      assertThat(result.runIO().unsafeRun().getRight()).isEqualTo(30);
    }

    @Test
    @DisplayName("via() short-circuits on error")
    void viaShortCircuits() {
      AtomicBoolean called = new AtomicBoolean(false);

      ErrorContext<IOKind.Witness, String, Integer> result =
          ErrorContext.<String, Integer>failure("early error")
              .via(
                  x -> {
                    called.set(true);
                    return ErrorContext.success(x * 2);
                  });

      assertThat(result.runIO().unsafeRun().isLeft()).isTrue();
      assertThat(called.get()).isFalse();
    }

    @Test
    @DisplayName("via() validates non-null function")
    void viaValidatesFunction() {
      ErrorContext<IOKind.Witness, String, Integer> ctx = ErrorContext.success(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.via(null))
          .withMessageContaining("fn must not be null");
    }

    @Test
    @DisplayName("via() throws when function returns wrong context type")
    void viaThrowsOnWrongContextType() {
      ErrorContext<IOKind.Witness, String, Integer> ctx = ErrorContext.success(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> ctx.via(x -> OptionalContext.some(x.toString())).runIO().unsafeRun())
          .withMessageContaining("via function must return an ErrorContext");
    }

    @Test
    @DisplayName("flatMap() chains contexts")
    void flatMapChainsContexts() {
      ErrorContext<IOKind.Witness, String, Integer> result =
          ErrorContext.<String, Integer>success(10).flatMap(x -> ErrorContext.success(x * 2));

      assertThat(result.runIO().unsafeRun().getRight()).isEqualTo(20);
    }

    @Test
    @DisplayName("then() sequences independent computation")
    void thenSequences() {
      AtomicBoolean firstCalled = new AtomicBoolean(false);
      AtomicBoolean secondCalled = new AtomicBoolean(false);

      ErrorContext<IOKind.Witness, String, String> result =
          ErrorContext.<String, Integer>io(
                  () -> {
                    firstCalled.set(true);
                    return 1;
                  },
                  Throwable::getMessage)
              .then(
                  () ->
                      ErrorContext.io(
                          () -> {
                            secondCalled.set(true);
                            return "done";
                          },
                          Throwable::getMessage));

      // Before running, neither should be called
      assertThat(firstCalled.get()).isFalse();
      assertThat(secondCalled.get()).isFalse();

      // After running
      result.runIO().unsafeRun();
      assertThat(firstCalled.get()).isTrue();
      assertThat(secondCalled.get()).isTrue();
    }
  }

  @Nested
  @DisplayName("Recovery Operations")
  class RecoveryOperationsTests {

    @Test
    @DisplayName("recover() provides fallback for errors")
    void recoverProvidesFallback() {
      ErrorContext<IOKind.Witness, String, Integer> ctx =
          ErrorContext.<String, Integer>failure(TEST_ERROR).recover(err -> TEST_VALUE);

      assertThat(ctx.runIO().unsafeRun().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recover() passes through success")
    void recoverPassesThroughSuccess() {
      ErrorContext<IOKind.Witness, String, Integer> ctx =
          ErrorContext.<String, Integer>success(100).recover(err -> TEST_VALUE);

      assertThat(ctx.runIO().unsafeRun().getRight()).isEqualTo(100);
    }

    @Test
    @DisplayName("recover() validates non-null function")
    void recoverValidatesFunction() {
      ErrorContext<IOKind.Witness, String, Integer> ctx = ErrorContext.failure(TEST_ERROR);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.recover(null))
          .withMessageContaining("recovery must not be null");
    }

    @Test
    @DisplayName("recoverWith() provides fallback context for errors")
    void recoverWithProvidesFallback() {
      ErrorContext<IOKind.Witness, String, Integer> ctx =
          ErrorContext.<String, Integer>failure(TEST_ERROR)
              .recoverWith(err -> ErrorContext.success(TEST_VALUE));

      assertThat(ctx.runIO().unsafeRun().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recoverWith() can return another failure")
    void recoverWithCanFail() {
      ErrorContext<IOKind.Witness, String, Integer> ctx =
          ErrorContext.<String, Integer>failure("first error")
              .recoverWith(err -> ErrorContext.failure("second error"));

      assertThat(ctx.runIO().unsafeRun().getLeft()).isEqualTo("second error");
    }

    @Test
    @DisplayName("orElse() provides alternative context")
    void orElseProvidesFallback() {
      ErrorContext<IOKind.Witness, String, Integer> ctx =
          ErrorContext.<String, Integer>failure(TEST_ERROR)
              .orElse(() -> ErrorContext.success(TEST_VALUE));

      assertThat(ctx.runIO().unsafeRun().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("mapError() transforms error type")
    void mapErrorTransformsError() {
      ErrorContext<IOKind.Witness, String, Integer> ctx = ErrorContext.failure("error");

      ErrorContext<IOKind.Witness, Integer, Integer> mapped = ctx.mapError(String::length);

      assertThat(mapped.runIO().unsafeRun().getLeft()).isEqualTo(5);
    }

    @Test
    @DisplayName("mapError() preserves success")
    void mapErrorPreservesSuccess() {
      ErrorContext<IOKind.Witness, String, Integer> ctx = ErrorContext.success(TEST_VALUE);

      ErrorContext<IOKind.Witness, Integer, Integer> mapped = ctx.mapError(String::length);

      assertThat(mapped.runIO().unsafeRun().getRight()).isEqualTo(TEST_VALUE);
    }
  }

  @Nested
  @DisplayName("Execution Methods")
  class ExecutionMethodsTests {

    @Test
    @DisplayName("runIO() returns IOPath with Either")
    void runIOReturnsIOPath() {
      ErrorContext<IOKind.Witness, String, Integer> ctx = ErrorContext.success(TEST_VALUE);

      var ioPath = ctx.runIO();

      assertThat(ioPath).isNotNull();
      assertThat(ioPath.unsafeRun().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("runIOOrThrow() returns value on success")
    void runIOOrThrowReturnsValue() {
      Integer result = ErrorContext.<String, Integer>success(TEST_VALUE).runIOOrThrow();

      assertThat(result).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("runIOOrThrow() throws on error")
    void runIOOrThrowThrows() {
      assertThatThrownBy(() -> ErrorContext.<String, Integer>failure(TEST_ERROR).runIOOrThrow())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining(TEST_ERROR);
    }

    @Test
    @DisplayName("runIOOrElse() returns value on success")
    void runIOOrElseReturnsValueOnSuccess() {
      Integer result = ErrorContext.<String, Integer>success(TEST_VALUE).runIOOrElse(0);

      assertThat(result).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("runIOOrElse() returns default on error")
    void runIOOrElseReturnsDefaultOnError() {
      Integer result = ErrorContext.<String, Integer>failure(TEST_ERROR).runIOOrElse(0);

      assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("runIOOrElseGet() applies error handler")
    void runIOOrElseGetAppliesHandler() {
      Integer result =
          ErrorContext.<String, Integer>failure("12345").runIOOrElseGet(String::length);

      assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("runIOOrElseGet() returns value on success")
    void runIOOrElseGetReturnsValueOnSuccess() {
      Integer result =
          ErrorContext.<String, Integer>success(TEST_VALUE).runIOOrElseGet(String::length);

      assertThat(result).isEqualTo(TEST_VALUE);
    }
  }

  @Nested
  @DisplayName("Escape Hatch")
  class EscapeHatchTests {

    @Test
    @DisplayName("toEitherT() returns underlying transformer")
    void toEitherTReturnsTransformer() {
      ErrorContext<IOKind.Witness, String, Integer> ctx = ErrorContext.success(TEST_VALUE);

      var eitherT = ctx.toEitherT();

      assertThat(eitherT).isNotNull();
    }

    @Test
    @DisplayName("underlying() returns Kind")
    void underlyingReturnsKind() {
      ErrorContext<IOKind.Witness, String, Integer> ctx = ErrorContext.success(TEST_VALUE);

      var underlying = ctx.underlying();

      assertThat(underlying).isNotNull();
    }
  }

  @Nested
  @DisplayName("toString()")
  class ToStringTests {

    @Test
    @DisplayName("toString() contains ErrorContext")
    void toStringContainsClassName() {
      ErrorContext<IOKind.Witness, String, Integer> ctx = ErrorContext.success(TEST_VALUE);

      assertThat(ctx.toString()).contains("ErrorContext");
    }
  }
}
