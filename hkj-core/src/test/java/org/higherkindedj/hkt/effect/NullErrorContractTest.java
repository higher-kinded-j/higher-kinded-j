// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.context.ErrorContext;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.resilience.Bulkhead;
import org.higherkindedj.hkt.resilience.BulkheadConfig;
import org.higherkindedj.hkt.resilience.BulkheadFullException;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.CircuitOpenException;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * An error must not be null, whether a caller passes it or a caller's function builds it. An eager
 * path refuses a null where the call is written; a deferred path fails with it when it runs.
 */
@DisplayName("An error, or a function that builds one, must not be null")
class NullErrorContractTest {

  private static final Duration SHORT = Duration.ofMillis(50);

  private static final AffinePath<Optional<String>, String> SOME =
      AffinePath.of(FocusPaths.optionalSome());

  @Nested
  @DisplayName("Eager paths throw where the call is written")
  class EagerPaths {

    @Test
    @DisplayName("factories refuse a null error")
    void factoriesRefuseANullError() {
      MonadError<EitherKind.Witness<String>, String> monad = Instances.monadError(either());

      assertThatNullPointerException()
          .isThrownBy(() -> ErrorContext.<String, Integer>failure(null))
          .withMessage("error must not be null");
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  GenericPath.<EitherKind.Witness<String>, String, Integer>raiseError(null, monad))
          .withMessage("error must not be null");
    }

    @Test
    @DisplayName("an Either holding a null error cannot enter a path")
    void aLeftNullCannotEnterAPath() {
      Either<String, Integer> leftNull = Either.left(null);

      assertThatNullPointerException()
          .isThrownBy(() -> Path.either(leftNull))
          .withMessage("either must not hold a null error");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vresultEither(leftNull))
          .withMessage("either must not hold a null error");
      assertThatNullPointerException()
          .isThrownBy(() -> ErrorContext.fromEither(leftNull))
          .withMessage("either must not hold a null error");
      assertThat(Path.either(Either.<String, Integer>right(null)).run().isRight()).isTrue();
    }

    @Test
    @DisplayName("a function that supplies an exception names itself when it returns null")
    void exceptionSuppliersNameThemselves() {
      EitherPath<String, Integer> left = Path.left("e");
      ValidationPath<String, Integer> invalid = Path.invalid("e", Semigroups.string());
      EitherOrBothPath<String, Integer> eobLeft = Path.left("e", Semigroups.string());
      MaybePath<Integer> nothing = Path.nothing();

      assertThatNullPointerException()
          .isThrownBy(() -> left.toTryPath(e -> null))
          .withMessage("errorToException must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> invalid.toTryPath(e -> null))
          .withMessage("errorToException must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> eobLeft.toTryPath(e -> null))
          .withMessage("errorToException must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> nothing.toTryPath(() -> null))
          .withMessage("exceptionSupplier must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> nothing.toIdPath(() -> null))
          .withMessage("exceptionSupplier must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> SOME.toTryPath(Optional.empty(), () -> null))
          .withMessage("exceptionIfAbsent must not return null");
    }

    @Test
    @DisplayName("EitherPath.mapError and bimap")
    void eitherPathMapErrorAndBimap() {
      EitherPath<String, Integer> left = Path.left("e");

      assertThatNullPointerException()
          .isThrownBy(() -> left.mapError(e -> (String) null))
          .withMessage("mapper must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> left.bimap(e -> (String) null, a -> a))
          .withMessage("errorMapper must not return null");
    }

    @Test
    @DisplayName("mapErrorWith on ValidationPath and EitherOrBothPath")
    void mapErrorWith() {
      ValidationPath<String, Integer> invalid = Path.invalid("e", Semigroups.string());
      EitherOrBothPath<String, Integer> left = Path.left("e", Semigroups.string());
      EitherOrBothPath<String, Integer> both = Path.both("w", 1, Semigroups.string());

      assertThatNullPointerException()
          .isThrownBy(() -> invalid.mapErrorWith(e -> (String) null, Semigroups.string()))
          .withMessage("mapper must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> left.mapErrorWith(e -> (String) null, Semigroups.string()))
          .withMessage("mapper must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> both.mapErrorWith(e -> (String) null, Semigroups.string()))
          .withMessage("mapper must not return null");
    }

    @Test
    @DisplayName("GenericPath.mapError")
    void genericPathMapError() {
      MonadError<EitherKind.Witness<String>, String> monad = Instances.monadError(either());
      GenericPath<EitherKind.Witness<String>, Integer> failed = GenericPath.raiseError("e", monad);

      assertThatNullPointerException()
          .isThrownBy(() -> failed.<String, String>mapError(e -> null, monad))
          .withMessage("mapper must not return null");
    }

    @Test
    @DisplayName("EitherPath.withTimeout")
    void eitherPathWithTimeout() {
      CountDownLatch never = new CountDownLatch(1);
      try {
        assertThatNullPointerException()
            .isThrownBy(
                () ->
                    EitherPath.<String, Integer>withTimeout(
                        () -> {
                          await(never);
                          return Path.right(1);
                        },
                        SHORT,
                        () -> null))
            .withMessage("onTimeout must not return null");
      } finally {
        never.countDown(); // withTimeout does not interrupt the losing computation
      }
    }

    @Test
    @DisplayName("EitherPath.withCircuitBreaker")
    void eitherPathWithCircuitBreaker() {
      CircuitBreaker open = CircuitBreaker.withDefaults();
      open.tripOpen();

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  EitherPath.<String, Integer>withCircuitBreaker(
                      () -> Path.right(1), open, rejected -> null))
          .withMessage("onOpen must not return null")
          .withCauseInstanceOf(CircuitOpenException.class);
    }

    @Test
    @DisplayName("TryPath conversions keep the failure they mapped")
    void tryPathConversionsKeepTheFailure() {
      IllegalStateException failure = new IllegalStateException();
      TryPath<Integer> failed = Path.failure(failure);

      assertThatNullPointerException()
          .isThrownBy(() -> failed.toEitherPath(Throwable::getMessage))
          .withMessage("exceptionToError must not return null")
          .withCause(failure);
    }

    @Test
    @DisplayName("TryPath.focus holds a null exception as a Failure")
    void tryPathFocus() {
      TryPath<String> focused = Path.success(Optional.<String>empty()).focus(SOME, () -> null);

      assertThatTry(focused.run())
          .isFailure()
          .hasExceptionOfType(NullPointerException.class)
          .hasExceptionSatisfying(
              e -> assertThat(e).hasMessage("exceptionIfAbsent must not return null"));
    }
  }

  @Nested
  @DisplayName("Deferred paths fail when they run")
  class DeferredPaths {

    @Test
    @DisplayName("VResultPath.mapError and bimap")
    void vresultPathMapErrorAndBimap() {
      VResultPath<String, Integer> left = Path.vresultLeft("e");
      VResultPath<String, Integer> mapped = left.mapError(e -> (String) null);
      VResultPath<String, Integer> bimapped = left.bimap(e -> (String) null, a -> a);

      assertThatNullPointerException()
          .isThrownBy(() -> mapped.run().run())
          .withMessage("mapper must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> bimapped.run().run())
          .withMessage("errorMapper must not return null");
    }

    @Test
    @DisplayName("VResultPath.withTimeout")
    void vresultPathWithTimeout() {
      CountDownLatch never = new CountDownLatch(1);
      VResultPath<String, Integer> stuck =
          VResultPath.fromVTask(
              VTask.of(
                  () -> {
                    never.await();
                    return Either.right(1);
                  }));
      try {
        assertThatNullPointerException()
            .isThrownBy(() -> stuck.withTimeout(SHORT, () -> null).run().run())
            .withMessage("onTimeout must not return null");
      } finally {
        never.countDown(); // withTimeout does not interrupt the losing computation
      }
    }

    @Test
    @DisplayName("VResultPath.withCircuitBreaker")
    void vresultPathWithCircuitBreaker() {
      CircuitBreaker open = CircuitBreaker.withDefaults();
      open.tripOpen();
      VResultPath<String, Integer> guarded =
          Path.<String, Integer>vresultRight(1).withCircuitBreaker(open, rejected -> null);

      assertThatNullPointerException()
          .isThrownBy(() -> guarded.run().run())
          .withMessage("onOpen must not return null")
          .withCauseInstanceOf(CircuitOpenException.class);
    }

    @Test
    @DisplayName("withBulkhead on EitherPath and VResultPath")
    void withBulkhead() throws InterruptedException {
      // A short waitTimeout keeps each rejection quick
      Bulkhead bulkhead =
          Bulkhead.create(BulkheadConfig.builder().maxConcurrent(1).waitTimeout(SHORT).build());
      CountDownLatch holding = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      Thread holder =
          Thread.ofVirtual()
              .start(
                  () ->
                      EitherPath.withBulkhead(
                          () -> {
                            holding.countDown();
                            await(release);
                            return Path.<String, Integer>right(0);
                          },
                          bulkhead));
      try {
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();
        VResultPath<String, Integer> guarded =
            Path.<String, Integer>vresultRight(1).withBulkhead(bulkhead, full -> null);

        assertThatNullPointerException()
            .isThrownBy(
                () ->
                    EitherPath.<String, Integer>withBulkhead(
                        () -> Path.right(1), bulkhead, full -> null))
            .withMessage("onFull must not return null")
            .withCauseInstanceOf(BulkheadFullException.class);
        assertThatNullPointerException()
            .isThrownBy(() -> guarded.run().run())
            .withMessage("onFull must not return null")
            .withCauseInstanceOf(BulkheadFullException.class);
      } finally {
        release.countDown();
        holder.join();
      }
    }

    @Test
    @DisplayName("catching on IOPath and VTaskPath keeps the failure it mapped")
    void catching() {
      IllegalStateException failure = new IllegalStateException();
      IOPath<Either<String, Integer>> io =
          Path.<Integer>io(
                  () -> {
                    throw failure;
                  })
              .catching(t -> null);
      VTaskPath<Either<String, Integer>> vtask =
          Path.<Integer>vtaskFail(failure).catching(t -> null);

      assertThatNullPointerException()
          .isThrownBy(io::unsafeRun)
          .withMessage("exceptionMapper must not return null")
          .withCause(failure);
      assertThatNullPointerException()
          .isThrownBy(vtask::unsafeRun)
          .withMessage("exceptionMapper must not return null")
          .withCause(failure);
    }

    @Test
    @DisplayName("ErrorContext.io and mapError")
    void errorContext() {
      IllegalStateException failure = new IllegalStateException();
      ErrorContext<IOKind.Witness, String, Integer> caught =
          ErrorContext.io(
              () -> {
                throw failure;
              },
              t -> null);
      ErrorContext<IOKind.Witness, String, Integer> mapped =
          ErrorContext.<String, Integer>failure("e").mapError(e -> (String) null);

      assertThatNullPointerException()
          .isThrownBy(() -> caught.runIO().unsafeRun())
          .withMessage("errorMapper must not return null")
          .withCause(failure);
      assertThatNullPointerException()
          .isThrownBy(() -> mapped.runIO().unsafeRun())
          .withMessage("mapper must not return null");
    }

    @Test
    @DisplayName("focus on IOPath and VTaskPath names a null exception")
    void focus() {
      IOPath<String> io = Path.ioPure(Optional.<String>empty()).focus(SOME, () -> null);
      VTaskPath<String> vtask = Path.vtaskPure(Optional.<String>empty()).focus(SOME, () -> null);

      assertThatNullPointerException()
          .isThrownBy(io::unsafeRun)
          .withMessage("exceptionIfAbsent must not return null");
      assertThatNullPointerException()
          .isThrownBy(vtask::unsafeRun)
          .withMessage("exceptionIfAbsent must not return null");
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
