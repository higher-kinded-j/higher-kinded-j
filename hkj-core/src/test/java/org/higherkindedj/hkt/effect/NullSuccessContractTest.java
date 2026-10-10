// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.context.ErrorContext;
import org.higherkindedj.hkt.effect.context.VTaskContext;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.resilience.Saga;
import org.higherkindedj.hkt.resilience.SagaError;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPath;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A {@code Right} or a {@code Success} always holds a value, whether a caller passes it or a
 * caller's function builds it. An eager path refuses a null where the call is written; a deferred
 * path fails with it when it runs; and {@code Try}, which captures what goes wrong in its
 * functions, captures a null result as a {@code Failure}. A computation that may still give a null,
 * such as a {@code VTask}, meets the rule where it becomes a {@code Try} or an {@code Either}.
 *
 * <p>The factories and the functions of {@code Either} and {@code Try} themselves are pinned beside
 * their other behaviour, in {@code EitherTest}, {@code TryTest} and the type-class tests.
 */
@DisplayName("A success, or a function that builds one, must not be null")
class NullSuccessContractTest {

  record Box(@Nullable String label) {}

  private static final FocusPath<Box, @Nullable String> LABEL =
      FocusPath.of(Lens.of(Box::label, (box, label) -> new Box(label)));

  private static void failsWith(Try<?> result, String message) {
    assertThatTry(result)
        .isFailure()
        .hasExceptionSatisfying(
            e -> assertThat(e).isInstanceOf(NullPointerException.class).hasMessage(message));
  }

  @Nested
  @DisplayName("Eager paths throw where the call is written")
  class EagerPaths {

    @Test
    @DisplayName("a VResultPath and a recover fallback refuse a null success")
    void factoriesRefuseANullSuccess() {
      MonadError<EitherKind.Witness<String>, String> eitherMonad = Instances.monadError(either());

      assertThatNullPointerException()
          .isThrownBy(() -> Path.vresultRight(null))
          .withMessage("Either.right value cannot be null");
      assertThatNullPointerException()
          .isThrownBy(() -> eitherMonad.recover(EITHER.widen(Either.right(1)), null))
          .withMessage("EitherMonad.recover value cannot be null");
    }

    @Test
    @DisplayName("EitherPath names the function that returned null")
    void eitherPathNamesTheFunction() {
      EitherPath<String, Integer> right = Path.right(1);
      EitherPath<String, Integer> left = Path.left("e");

      assertThatNullPointerException()
          .isThrownBy(() -> right.zipWith(right, (a, b) -> null))
          .withMessage("combiner must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> right.zipWith3(right, right, (a, b, c) -> null))
          .withMessage("combiner must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> left.recover(e -> null))
          .withMessage("recovery must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> right.bimap(e -> e, a -> null))
          .withMessage("successMapper must not return null");
    }

    @Test
    @DisplayName("a strict path's focus names the form that says what a null focus becomes")
    void aStrictPathsFocusNamesTheRoute() {
      Box unlabelled = new Box(null);

      assertThatNullPointerException()
          .isThrownBy(() -> Path.<String, Box>right(unlabelled).focus(LABEL))
          .withMessage(
              "the focus is null: use focus(path.nullable(), errorIfAbsent) to give a Left for it");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.<String, Box>valid(unlabelled, Semigroups.first()).focus(LABEL))
          .withMessage(
              "the focus is null: use focus(path.nullable(), errorIfAbsent) to give an Invalid"
                  + " for it");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.<String, Box>right(unlabelled, Semigroups.first()).focus(LABEL))
          .withMessage(
              "the focus is null: use focus(path.nullable(), errorIfAbsent) to give a Left for it");
      failsWith(
          Path.success(unlabelled).focus(LABEL).run(),
          "the focus is null: use focus(path.nullable(), exceptionIfAbsent) to give a Failure for"
              + " it");
    }
  }

  @Nested
  @DisplayName("Try captures a null result as a Failure")
  class TryCapturesANullResult {

    @Test
    @DisplayName("TryPath names the function that returned null")
    void tryPathNamesTheFunction() {
      TryPath<Integer> success = Path.success(1);

      failsWith(Path.tryOf(() -> null).run(), "supplier must not return null");
      failsWith(success.map(a -> null).run(), "mapper must not return null");
      failsWith(success.zipWith(success, (a, b) -> null).run(), "combiner must not return null");
      failsWith(
          success.zipWith3(success, success, (a, b, c) -> null).run(),
          "combiner must not return null");
      failsWith(
          Path.<Integer>failure(new IllegalStateException()).recover(e -> null).run(),
          "recovery must not return null");
    }
  }

  @Nested
  @DisplayName("Deferred paths fail when they run")
  class DeferredPaths {

    @Test
    @DisplayName("VResultPath names the function that returned null")
    void vresultPathNamesTheFunction() {
      VResultPath<String, Integer> right = Path.vresultRight(1);
      VResultPath<String, Integer> zipped = right.zipWith(right, (a, b) -> null);
      VResultPath<String, Integer> zipped3 = right.zipWith3(right, right, (a, b, c) -> null);
      VResultPath<String, Integer> bimapped = right.bimap(e -> e, a -> null);

      assertThatNullPointerException()
          .isThrownBy(() -> zipped.run().run())
          .withMessage("combiner must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> zipped3.run().run())
          .withMessage("combiner must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> bimapped.run().run())
          .withMessage("successMapper must not return null");
    }

    @Test
    @DisplayName("ErrorContext and Scope name what returned null")
    void errorContextAndScopeNameTheSource() {
      ErrorContext<IOKind.Witness, String, Integer> computed =
          ErrorContext.io(() -> null, Throwable::getMessage);
      ErrorContext<IOKind.Witness, String, Integer> recovered =
          ErrorContext.<String, Integer>failure("e").recover(e -> null);

      assertThatEither(computed.runIO().unsafeRun()).hasLeft("computation must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> recovered.runIO().unsafeRun())
          .withMessage("recovery must not return null");
      assertThatEither(Scope.<String>anySucceed().fork(VTask.succeed(null)).joinEither().run())
          .hasLeftSatisfying(
              e ->
                  assertThat(e)
                      .isInstanceOf(NullPointerException.class)
                      .hasMessage("the scope's result is null, and a Right always holds a value"));
    }
  }

  @Nested
  @DisplayName("A computation's null result, where a success must hold a value")
  class AComputationsNullResult {

    private static final String SUCCESS_HOLDS = " and a Success always holds a value";

    @Test
    @DisplayName("a capturing conversion gives a Failure, or the mapped error, naming its source")
    void aCapturingConversionGivesAFailure() {
      IOPath<String> io = Path.io(() -> null);
      VTaskPath<String> task = Path.vtask(() -> null);

      failsWith(VTask.<String>succeed(null).runSafe(), "the task returned null," + SUCCESS_HOLDS);
      failsWith(task.runSafe(), "the computation returned null," + SUCCESS_HOLDS);
      failsWith(task.toTryPath().run(), "the computation returned null," + SUCCESS_HOLDS);
      failsWith(task.asTry().unsafeRun(), "the computation returned null," + SUCCESS_HOLDS);
      failsWith(io.runSafe(), "the computation returned null," + SUCCESS_HOLDS);
      failsWith(io.asTry().unsafeRun(), "the computation returned null," + SUCCESS_HOLDS);
      failsWith(
          Path.<String>lazyNow(null).toTryPath().run(), "the lazy value is null," + SUCCESS_HOLDS);
      failsWith(
          Path.future(CompletableFuture.<String>completedFuture(null)).toTryPath().run(),
          "the future completed with null," + SUCCESS_HOLDS);
      failsWith(
          Scope.<String>anySucceed().fork(VTask.succeed(null)).joinSafe().run(),
          "the scope's result is null," + SUCCESS_HOLDS);
      assertThatEither(io.catching(Throwable::getMessage).unsafeRun())
          .hasLeft("the computation returned null, and a Right always holds a value");
      assertThatEither(task.catching(Throwable::getMessage).unsafeRun())
          .hasLeft("the computation returned null, and a Right always holds a value");
    }

    @Test
    @DisplayName("a conversion that captures nothing throws, naming the route to use")
    void aPlainConversionThrows() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> Path.future(CompletableFuture.<String>completedFuture(null)).toEitherPath())
          .withMessage(
              "the future completed with null: use toTryPath(), which gives a Failure for it and"
                  + " keeps a failed future's exception");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.<String, String>writerPure(null, Monoids.string()).toEitherPath())
          .withMessage(
              "the value is null: use toMaybePath().toEitherPath(error) to give a Left for it");
    }

    @Test
    @DisplayName("a saga whose result is null is compensated and gives a Left")
    void aSagaWhoseResultIsNullIsCompensated() {
      AtomicBoolean compensated = new AtomicBoolean();
      Saga<String> saga = Saga.of(VTask.succeed(null), (String s) -> compensated.set(true));

      assertThatEither(saga.runSafe().run())
          .hasLeftSatisfying(
              (SagaError error) -> {
                assertThat(error.failedStep()).isEqualTo("result");
                assertThat(error.originalError())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageStartingWith(
                        "the saga's result is null, and a Right always holds a value");
              });
      assertThat(compensated).isTrue();
    }

    @Test
    @DisplayName("a run that needs no Try hands a null result back")
    void aRunThatNeedsNoTryHandsANullBack() {
      AtomicBoolean secondRan = new AtomicBoolean();
      VTaskPath<String> first = Path.vtask(() -> null);
      VTaskPath<String> second =
          Path.vtask(
              () -> {
                secondRan.set(true);
                return "second";
              });
      VTaskContext<String> context = VTaskContext.of(() -> null);

      assertThat(PathOps.firstVTaskSuccess(NonEmptyList.of(first, second)).unsafeRun()).isNull();
      assertThat(secondRan).isFalse();
      assertThat(context.runOrElse("fallback")).isNull();
      assertThat(context.runOrElseGet(e -> "fallback")).isNull();
    }
  }
}
