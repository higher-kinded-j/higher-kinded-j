// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for Path lifting and wrapping methods.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>EitherPath: liftIO(), liftFuture(), deferIO()
 *   <li>MaybePath: liftIO(), liftFuture(), deferIO()
 *   <li>IOPath: catching(), asMaybe(), asTry()
 * </ul>
 */
@DisplayName("Path Lifting and Wrapping Methods")
class PathLiftingTest {

  private static final String TEST_VALUE = "test";
  private static final String TEST_ERROR = "error";
  private static final Integer TEST_INT = 42;

  /**
   * Helper method to throw checked exceptions from contexts that don't allow them. Uses type
   * erasure to bypass the compiler's checked exception checking.
   */
  @SuppressWarnings("unchecked")
  private static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T {
    throw (T) t;
  }

  @Nested
  @DisplayName("EitherPath Lifting Methods")
  class EitherPathLiftingTests {

    @Nested
    @DisplayName("liftIO()")
    class LiftIOTests {

      @Test
      @DisplayName("lifts Right value into IO context")
      void liftsRightIntoIO() {
        EitherPath<String, Integer> path = Path.right(TEST_INT);

        IOPath<Either<String, Integer>> lifted = path.liftIO();

        Either<String, Integer> result = lifted.unsafeRun();
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEqualTo(TEST_INT);
      }

      @Test
      @DisplayName("lifts Left value into IO context")
      void liftsLeftIntoIO() {
        EitherPath<String, Integer> path = Path.left(TEST_ERROR);

        IOPath<Either<String, Integer>> lifted = path.liftIO();

        Either<String, Integer> result = lifted.unsafeRun();
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo(TEST_ERROR);
      }

      @Test
      @DisplayName("lifts null Right value into IO context")
      void liftsNullRightIntoIO() {
        EitherPath<String, String> path = Path.right(null);

        IOPath<Either<String, String>> lifted = path.liftIO();

        Either<String, String> result = lifted.unsafeRun();
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isNull();
      }

      @Test
      @DisplayName("returns same Either value when run multiple times")
      void returnsSameValueOnMultipleRuns() {
        EitherPath<String, Integer> path = Path.right(TEST_INT);
        IOPath<Either<String, Integer>> lifted = path.liftIO();

        Either<String, Integer> result1 = lifted.unsafeRun();
        Either<String, Integer> result2 = lifted.unsafeRun();

        assertThat(result1).isEqualTo(result2);
      }
    }

    @Nested
    @DisplayName("liftFuture()")
    class LiftFutureTests {

      @Test
      @DisplayName("lifts Right value into completed future")
      void liftsRightIntoCompletedFuture() {
        EitherPath<String, Integer> path = Path.right(TEST_INT);

        CompletableFuturePath<Either<String, Integer>> lifted = path.liftFuture();

        Either<String, Integer> result = lifted.run().join();
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEqualTo(TEST_INT);
      }

      @Test
      @DisplayName("lifts Left value into completed future")
      void liftsLeftIntoCompletedFuture() {
        EitherPath<String, Integer> path = Path.left(TEST_ERROR);

        CompletableFuturePath<Either<String, Integer>> lifted = path.liftFuture();

        Either<String, Integer> result = lifted.run().join();
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo(TEST_ERROR);
      }

      @Test
      @DisplayName("returns already completed future")
      void returnsAlreadyCompletedFuture() {
        EitherPath<String, Integer> path = Path.right(TEST_INT);

        CompletableFuturePath<Either<String, Integer>> lifted = path.liftFuture();

        assertThat(lifted.run().isDone()).isTrue();
      }
    }

    @Nested
    @DisplayName("deferIO()")
    class DeferIOTests {

      @Test
      @DisplayName("defers computation until run")
      void defersComputationUntilRun() {
        AtomicBoolean called = new AtomicBoolean(false);

        IOPath<Either<String, Integer>> deferred =
            EitherPath.deferIO(
                () -> {
                  called.set(true);
                  return Either.right(TEST_INT);
                });

        assertThat(called.get()).isFalse();
        deferred.unsafeRun();
        assertThat(called.get()).isTrue();
      }

      @Test
      @DisplayName("produces Right value when supplier returns Right")
      void producesRightValue() {
        IOPath<Either<String, Integer>> deferred = EitherPath.deferIO(() -> Either.right(TEST_INT));

        Either<String, Integer> result = deferred.unsafeRun();
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEqualTo(TEST_INT);
      }

      @Test
      @DisplayName("produces Left value when supplier returns Left")
      void producesLeftValue() {
        IOPath<Either<String, Integer>> deferred =
            EitherPath.deferIO(() -> Either.left(TEST_ERROR));

        Either<String, Integer> result = deferred.unsafeRun();
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo(TEST_ERROR);
      }

      @Test
      @DisplayName("validates non-null supplier")
      void validatesNonNullSupplier() {
        assertThatNullPointerException()
            .isThrownBy(() -> EitherPath.deferIO(null))
            .withMessageContaining("supplier must not be null");
      }

      @Test
      @DisplayName("re-evaluates supplier on each run")
      void reEvaluatesOnEachRun() {
        AtomicBoolean toggle = new AtomicBoolean(false);

        IOPath<Either<String, Boolean>> deferred =
            EitherPath.deferIO(
                () -> {
                  boolean current = toggle.get();
                  toggle.set(!current);
                  return Either.right(current);
                });

        Either<String, Boolean> result1 = deferred.unsafeRun();
        Either<String, Boolean> result2 = deferred.unsafeRun();

        assertThat(result1.getRight()).isFalse();
        assertThat(result2.getRight()).isTrue();
      }
    }
  }

  @Nested
  @DisplayName("MaybePath Lifting Methods")
  class MaybePathLiftingTests {

    @Nested
    @DisplayName("liftIO()")
    class LiftIOTests {

      @Test
      @DisplayName("lifts Just value into IO context")
      void liftsJustIntoIO() {
        MaybePath<Integer> path = Path.just(TEST_INT);

        IOPath<Maybe<Integer>> lifted = path.liftIO();

        Maybe<Integer> result = lifted.unsafeRun();
        assertThat(result.isJust()).isTrue();
        assertThat(result.get()).isEqualTo(TEST_INT);
      }

      @Test
      @DisplayName("lifts Nothing into IO context")
      void liftsNothingIntoIO() {
        MaybePath<Integer> path = Path.nothing();

        IOPath<Maybe<Integer>> lifted = path.liftIO();

        Maybe<Integer> result = lifted.unsafeRun();
        assertThat(result.isNothing()).isTrue();
      }

      @Test
      @DisplayName("returns same Maybe value when run multiple times")
      void returnsSameValueOnMultipleRuns() {
        MaybePath<Integer> path = Path.just(TEST_INT);
        IOPath<Maybe<Integer>> lifted = path.liftIO();

        Maybe<Integer> result1 = lifted.unsafeRun();
        Maybe<Integer> result2 = lifted.unsafeRun();

        assertThat(result1).isEqualTo(result2);
      }
    }

    @Nested
    @DisplayName("liftFuture()")
    class LiftFutureTests {

      @Test
      @DisplayName("lifts Just value into completed future")
      void liftsJustIntoCompletedFuture() {
        MaybePath<Integer> path = Path.just(TEST_INT);

        CompletableFuturePath<Maybe<Integer>> lifted = path.liftFuture();

        Maybe<Integer> result = lifted.run().join();
        assertThat(result.isJust()).isTrue();
        assertThat(result.get()).isEqualTo(TEST_INT);
      }

      @Test
      @DisplayName("lifts Nothing into completed future")
      void liftsNothingIntoCompletedFuture() {
        MaybePath<Integer> path = Path.nothing();

        CompletableFuturePath<Maybe<Integer>> lifted = path.liftFuture();

        Maybe<Integer> result = lifted.run().join();
        assertThat(result.isNothing()).isTrue();
      }

      @Test
      @DisplayName("returns already completed future")
      void returnsAlreadyCompletedFuture() {
        MaybePath<Integer> path = Path.just(TEST_INT);

        CompletableFuturePath<Maybe<Integer>> lifted = path.liftFuture();

        assertThat(lifted.run().isDone()).isTrue();
      }
    }

    @Nested
    @DisplayName("deferIO()")
    class DeferIOTests {

      @Test
      @DisplayName("defers computation until run")
      void defersComputationUntilRun() {
        AtomicBoolean called = new AtomicBoolean(false);

        IOPath<Maybe<Integer>> deferred =
            MaybePath.deferIO(
                () -> {
                  called.set(true);
                  return Maybe.just(TEST_INT);
                });

        assertThat(called.get()).isFalse();
        deferred.unsafeRun();
        assertThat(called.get()).isTrue();
      }

      @Test
      @DisplayName("produces Just value when supplier returns Just")
      void producesJustValue() {
        IOPath<Maybe<Integer>> deferred = MaybePath.deferIO(() -> Maybe.just(TEST_INT));

        Maybe<Integer> result = deferred.unsafeRun();
        assertThat(result.isJust()).isTrue();
        assertThat(result.get()).isEqualTo(TEST_INT);
      }

      @Test
      @DisplayName("produces Nothing when supplier returns Nothing")
      void producesNothingValue() {
        IOPath<Maybe<Integer>> deferred = MaybePath.deferIO(Maybe::nothing);

        Maybe<Integer> result = deferred.unsafeRun();
        assertThat(result.isNothing()).isTrue();
      }

      @Test
      @DisplayName("validates non-null supplier")
      void validatesNonNullSupplier() {
        assertThatNullPointerException()
            .isThrownBy(() -> MaybePath.deferIO(null))
            .withMessageContaining("supplier must not be null");
      }

      @Test
      @DisplayName("re-evaluates supplier on each run")
      void reEvaluatesOnEachRun() {
        AtomicBoolean toggle = new AtomicBoolean(false);

        IOPath<Maybe<Boolean>> deferred =
            MaybePath.deferIO(
                () -> {
                  boolean current = toggle.get();
                  toggle.set(!current);
                  return Maybe.just(current);
                });

        Maybe<Boolean> result1 = deferred.unsafeRun();
        Maybe<Boolean> result2 = deferred.unsafeRun();

        assertThat(result1.get()).isFalse();
        assertThat(result2.get()).isTrue();
      }
    }
  }

  @Nested
  @DisplayName("IOPath Effect Wrapping Methods")
  class IOPathWrappingTests {

    @Nested
    @DisplayName("catching()")
    class CatchingTests {

      @Test
      @DisplayName("wraps successful computation in Right")
      void wrapsSuccessInRight() {
        IOPath<Integer> io = Path.io(() -> TEST_INT);

        IOPath<Either<String, Integer>> caught = io.catching(Throwable::getMessage);

        Either<String, Integer> result = caught.unsafeRun();
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEqualTo(TEST_INT);
      }

      @Test
      @DisplayName("wraps exception in Left with mapped error")
      void wrapsExceptionInLeft() {
        IOPath<Integer> io =
            Path.io(
                () -> {
                  throw new RuntimeException("boom");
                });

        IOPath<Either<String, Integer>> caught = io.catching(Throwable::getMessage);

        Either<String, Integer> result = caught.unsafeRun();
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo("boom");
      }

      @Test
      @DisplayName("catches checked exceptions")
      void catchesCheckedExceptions() {
        IOPath<Integer> io = Path.io(() -> sneakyThrow(new Exception("checked")));

        IOPath<Either<String, Integer>> caught = io.catching(Throwable::getMessage);

        Either<String, Integer> result = caught.unsafeRun();
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo("checked");
      }

      @Test
      @DisplayName("catches errors")
      void catchesErrors() {
        IOPath<Integer> io =
            Path.io(
                () -> {
                  throw new OutOfMemoryError("test oom");
                });

        IOPath<Either<String, Integer>> caught = io.catching(Throwable::getMessage);

        Either<String, Integer> result = caught.unsafeRun();
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo("test oom");
      }

      @Test
      @DisplayName("validates non-null exception mapper")
      void validatesNonNullMapper() {
        IOPath<Integer> io = Path.io(() -> TEST_INT);

        assertThatNullPointerException()
            .isThrownBy(() -> io.catching(null))
            .withMessageContaining("exceptionMapper must not be null");
      }

      @Test
      @DisplayName("defers execution until run")
      void defersExecution() {
        AtomicBoolean called = new AtomicBoolean(false);

        IOPath<Either<String, Integer>> caught =
            Path.io(
                    () -> {
                      called.set(true);
                      return TEST_INT;
                    })
                .catching(Throwable::getMessage);

        assertThat(called.get()).isFalse();
        caught.unsafeRun();
        assertThat(called.get()).isTrue();
      }

      @Test
      @DisplayName("allows custom error type mapping")
      void allowsCustomErrorTypeMapping() {
        record ApiError(String code, String message) {}

        IOPath<Integer> io =
            Path.io(
                () -> {
                  throw new IllegalArgumentException("invalid input");
                });

        IOPath<Either<ApiError, Integer>> caught =
            io.catching(t -> new ApiError("INVALID", t.getMessage()));

        Either<ApiError, Integer> result = caught.unsafeRun();
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft().code()).isEqualTo("INVALID");
        assertThat(result.getLeft().message()).isEqualTo("invalid input");
      }
    }

    @Nested
    @DisplayName("asMaybe()")
    class AsMaybeTests {

      @Test
      @DisplayName("wraps success in Just")
      void wrapsSuccessInJust() {
        IOPath<Integer> io = Path.io(() -> TEST_INT);

        IOPath<Maybe<Integer>> maybe = io.asMaybe();

        Maybe<Integer> result = maybe.unsafeRun();
        assertThat(result.isJust()).isTrue();
        assertThat(result.get()).isEqualTo(TEST_INT);
      }

      @Test
      @DisplayName("wraps exception in Nothing")
      void wrapsExceptionInNothing() {
        IOPath<Integer> io =
            Path.io(
                () -> {
                  throw new RuntimeException("error");
                });

        IOPath<Maybe<Integer>> maybe = io.asMaybe();

        Maybe<Integer> result = maybe.unsafeRun();
        assertThat(result.isNothing()).isTrue();
      }

      @Test
      @DisplayName("wraps checked exception in Nothing")
      void wrapsCheckedExceptionInNothing() {
        IOPath<Integer> io = Path.io(() -> sneakyThrow(new Exception("checked")));

        IOPath<Maybe<Integer>> maybe = io.asMaybe();

        Maybe<Integer> result = maybe.unsafeRun();
        assertThat(result.isNothing()).isTrue();
      }

      @Test
      @DisplayName("wraps error in Nothing")
      void wrapsErrorInNothing() {
        IOPath<Integer> io =
            Path.io(
                () -> {
                  throw new OutOfMemoryError("oom");
                });

        IOPath<Maybe<Integer>> maybe = io.asMaybe();

        Maybe<Integer> result = maybe.unsafeRun();
        assertThat(result.isNothing()).isTrue();
      }

      @Test
      @DisplayName("defers execution until run")
      void defersExecution() {
        AtomicBoolean called = new AtomicBoolean(false);

        IOPath<Maybe<Integer>> maybe =
            Path.io(
                    () -> {
                      called.set(true);
                      return TEST_INT;
                    })
                .asMaybe();

        assertThat(called.get()).isFalse();
        maybe.unsafeRun();
        assertThat(called.get()).isTrue();
      }

      @Test
      @DisplayName("treats null value as Nothing since Maybe.just rejects null")
      void treatsNullAsNothing() {
        IOPath<String> io = Path.io(() -> null);

        IOPath<Maybe<String>> maybe = io.asMaybe();

        Maybe<String> result = maybe.unsafeRun();
        // Maybe.just(null) throws NullPointerException, which asMaybe() catches
        // and converts to Nothing
        assertThat(result.isNothing()).isTrue();
      }
    }

    @Nested
    @DisplayName("asTry()")
    class AsTryTests {

      @Test
      @DisplayName("wraps success in Success")
      void wrapsSuccessInSuccess() {
        IOPath<Integer> io = Path.io(() -> TEST_INT);

        IOPath<Try<Integer>> tryPath = io.asTry();

        Try<Integer> result = tryPath.unsafeRun();
        assertThat(result.isSuccess()).isTrue();
        assertThat(((Try.Success<Integer>) result).value()).isEqualTo(TEST_INT);
      }

      @Test
      @DisplayName("wraps exception in Failure")
      void wrapsExceptionInFailure() {
        RuntimeException ex = new RuntimeException("error");
        IOPath<Integer> io =
            Path.io(
                () -> {
                  throw ex;
                });

        IOPath<Try<Integer>> tryPath = io.asTry();

        Try<Integer> result = tryPath.unsafeRun();
        assertThat(result.isFailure()).isTrue();
        assertThat(((Try.Failure<Integer>) result).cause()).isEqualTo(ex);
      }

      @Test
      @DisplayName("wraps checked exception in Failure")
      void wrapsCheckedExceptionInFailure() {
        Exception ex = new Exception("checked");
        IOPath<Integer> io = Path.io(() -> sneakyThrow(ex));

        IOPath<Try<Integer>> tryPath = io.asTry();

        Try<Integer> result = tryPath.unsafeRun();
        assertThat(result.isFailure()).isTrue();
        assertThat(((Try.Failure<Integer>) result).cause()).isEqualTo(ex);
      }

      @Test
      @DisplayName("wraps error in Failure")
      void wrapsErrorInFailure() {
        Error err = new OutOfMemoryError("oom");
        IOPath<Integer> io =
            Path.io(
                () -> {
                  throw err;
                });

        IOPath<Try<Integer>> tryPath = io.asTry();

        Try<Integer> result = tryPath.unsafeRun();
        assertThat(result.isFailure()).isTrue();
        assertThat(((Try.Failure<Integer>) result).cause()).isEqualTo(err);
      }

      @Test
      @DisplayName("defers execution until run")
      void defersExecution() {
        AtomicBoolean called = new AtomicBoolean(false);

        IOPath<Try<Integer>> tryPath =
            Path.io(
                    () -> {
                      called.set(true);
                      return TEST_INT;
                    })
                .asTry();

        assertThat(called.get()).isFalse();
        tryPath.unsafeRun();
        assertThat(called.get()).isTrue();
      }

      @Test
      @DisplayName("differs from toTryPath() by not executing immediately")
      void differsFromToTryPath() {
        AtomicBoolean called = new AtomicBoolean(false);

        IOPath<Integer> io =
            Path.io(
                () -> {
                  called.set(true);
                  return TEST_INT;
                });

        // asTry() does NOT execute
        IOPath<Try<Integer>> asTryResult = io.asTry();
        assertThat(called.get()).isFalse();

        // Now run it
        asTryResult.unsafeRun();
        assertThat(called.get()).isTrue();
      }

      @Test
      @DisplayName("can be run multiple times")
      void canBeRunMultipleTimes() {
        AtomicBoolean toggle = new AtomicBoolean(false);

        IOPath<Try<Boolean>> tryPath =
            Path.io(
                    () -> {
                      boolean current = toggle.get();
                      toggle.set(!current);
                      return current;
                    })
                .asTry();

        Try<Boolean> result1 = tryPath.unsafeRun();
        Try<Boolean> result2 = tryPath.unsafeRun();

        assertThat(((Try.Success<Boolean>) result1).value()).isFalse();
        assertThat(((Try.Success<Boolean>) result2).value()).isTrue();
      }
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("liftIO and catching compose correctly")
    void liftIOAndCatchingCompose() {
      // Create an EitherPath
      EitherPath<String, Integer> eitherPath = Path.right(TEST_INT);

      // Lift to IO, then compose with another IO operation that might fail
      IOPath<Either<String, Integer>> result =
          eitherPath
              .liftIO()
              .map(
                  either ->
                      either.flatMap(
                          n -> {
                            if (n > 100) {
                              return Either.left("Too large");
                            }
                            return Either.right(n * 2);
                          }));

      Either<String, Integer> finalResult = result.unsafeRun();
      assertThat(finalResult.isRight()).isTrue();
      assertThat(finalResult.getRight()).isEqualTo(84);
    }

    @Test
    @DisplayName("deferIO with catching provides full error handling")
    void deferIOWithCatching() {
      IOPath<Either<String, Integer>> computation =
          EitherPath.deferIO(
              () -> {
                // This could be a real computation that returns Either
                return Either.right(TEST_INT);
              });

      // Further compose with catching for any unexpected errors
      IOPath<Either<String, Either<String, Integer>>> safe =
          computation.catching(Throwable::getMessage);

      Either<String, Either<String, Integer>> result = safe.unsafeRun();
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight().isRight()).isTrue();
      assertThat(result.getRight().getRight()).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("MaybePath liftIO composes with IOPath operations")
    void maybePathLiftIOComposesWithIOPath() {
      MaybePath<Integer> maybePath = Path.just(10);

      IOPath<Maybe<Integer>> result = maybePath.liftIO().map(maybe -> maybe.map(n -> n * 2));

      Maybe<Integer> finalResult = result.unsafeRun();
      assertThat(finalResult.isJust()).isTrue();
      assertThat(finalResult.get()).isEqualTo(20);
    }
  }
}
