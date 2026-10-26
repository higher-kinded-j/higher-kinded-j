// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.context.ConfigContext;
import org.higherkindedj.hkt.effect.context.ErrorContext;
import org.higherkindedj.hkt.effect.context.MutableContext;
import org.higherkindedj.hkt.effect.context.VStreamContext;
import org.higherkindedj.hkt.effect.context.VTaskContext;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.resilience.Saga;
import org.higherkindedj.hkt.resilience.SagaError;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPath;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A {@code Right} or a {@code Success} always holds a value, and a computation always gives one,
 * whether a caller passes it or a caller's function builds it. An eager path refuses a null where
 * the call is written; a deferred path fails with it when it runs; and {@code Try}, which captures
 * what goes wrong in its functions, captures a null result as a {@code Failure}. Only a
 * hand-written {@code IO} or {@code VTask} lambda can still return null, and it meets the rule
 * where it is run.
 *
 * <p>The factories and the functions of the core types themselves are pinned beside their other
 * behaviour, in {@code EitherTest}, {@code TryTest}, {@code VTaskTest} and the type-class tests,
 * and each path's own factories in its path test.
 */
@DisplayName("A success, or a function that builds one, must not be null")
class NullSuccessContractTest {

  record Box(@Nullable String label) {}

  private static final FocusPath<Box, @Nullable String> LABEL =
      FocusPath.of(Lens.of(Box::label, (box, label) -> new Box(label)));

  /** A hand-written task, which only a nullness checker stops from returning null. */
  private static final VTask<String> RAW_TASK = () -> null;

  /** A hand-written IO, which only a nullness checker stops from returning null. */
  private static final IO<String> RAW_IO = () -> null;

  private static void failsInTheFuture(CompletableFuturePath<?> path, String message) {
    assertThatThrownBy(path::join)
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(NullPointerException.class)
        .hasRootCauseMessage(message);
  }

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

    @Test
    @DisplayName("a lazy path's factory refuses a null value")
    void aLazyPathsFactoryRefusesANullValue() {
      IdMonad id = IdMonad.instance();

      assertThatNullPointerException()
          .isThrownBy(() -> Path.ioPure(null))
          .withMessage("value must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vtaskPure(null))
          .withMessage("value must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.trampolineDone(null))
          .withMessage("value must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.freePure(null, id))
          .withMessage("value must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.freeApPure(null, id))
          .withMessage("value must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vstreamPure(null))
          .withMessage("value must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vstreamIterate(null, s -> s))
          .withMessage("seed must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.streamPure(null))
          .withMessage("value must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.streamIterate(null, s -> s))
          .withMessage("seed must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.stream(Stream.of("a", null)))
          .withMessage("stream must not contain null");
      assertThatNullPointerException()
          .isThrownBy(() -> StreamPath.of("a", null))
          .withMessage("elements must not contain null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.futureCompleted("a").completeOnTimeout(null, Duration.ZERO))
          .withMessage("defaultValue must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vstreamOf("a").fold(null, (a, b) -> a))
          .withMessage("identity must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vstreamOf("a").foldLeft(null, (acc, a) -> acc))
          .withMessage("identity must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> VTaskContext.pure(null))
          .withMessage("value must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> VStreamContext.pure(null))
          .withMessage("value must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> ConfigContext.pure(null))
          .withMessage("value must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> MutableContext.pure(null))
          .withMessage("value must not be null");
      assertThatNullPointerException()
          .isThrownBy(() -> VTaskContext.pure("a").runOrElse(null))
          .withMessage("defaultValue must not be null");
    }

    @Test
    @DisplayName("an eager lazy path names the function that returned null")
    void anEagerLazyPathNamesTheFunction() {
      WriterPath<String, String> logged = WriterPath.pure("a", Monoids.string());

      assertThatNullPointerException()
          .isThrownBy(() -> Path.id("a").map(a -> null))
          .withMessage("mapper must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.id("a").zipWith(Path.id("b"), (a, b) -> null))
          .withMessage("combiner must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.id("a").zipWith3(Path.id("b"), Path.id("c"), (a, b, c) -> null))
          .withMessage("combiner must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> logged.map(a -> null))
          .withMessage("mapper must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> logged.zipWith(logged, (a, b) -> null))
          .withMessage("combiner must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> logged.zipWith3(logged, logged, (a, b, c) -> null))
          .withMessage("combiner must not return null");
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
      assertThatEither(Scope.<String>anySucceed().fork(RAW_TASK).joinEither().run())
          .hasLeftSatisfying(
              e ->
                  assertThat(e)
                      .isInstanceOf(NullPointerException.class)
                      .hasMessage("the scope's result is null, and a Right always holds a value"));
    }

    @Test
    @DisplayName("a lazy path names the function that returned null")
    void aLazyPathNamesTheFunction() {
      IOPath<String> io = Path.ioPure("a");
      IOPath<String> failingIo =
          Path.io(
              () -> {
                throw new IllegalStateException();
              });
      VTaskPath<String> task = Path.vtaskPure("a");
      LazyPath<String> lazy = Path.lazyNow("a");
      ReaderPath<String, String> reader = Path.readerPure("a");
      WithStatePath<Integer, String> state = Path.statePure("a");
      VStreamPath<String> stream = Path.vstreamOf("a");

      assertThatNullPointerException()
          .isThrownBy(() -> Path.io(() -> null).unsafeRun())
          .withMessage("supplier must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> io.zipWith(io, (a, b) -> null).unsafeRun())
          .withMessage("combiner must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> io.parZipWith(io, (a, b) -> null).unsafeRun())
          .withMessage("combiner must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> failingIo.handleError(t -> null).unsafeRun())
          .withMessage("recovery must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> IOPath.bracket(() -> "r", r -> null, r -> {}).unsafeRun())
          .withMessage("use must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip3(io, io, io, (a, b, c) -> null).unsafeRun())
          .withMessage("combiner must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> task.zipWith(task, (a, b) -> null).unsafeRun())
          .withMessage("combiner must not return null");
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  Path.<String>vtaskFail(new IllegalStateException())
                      .handleError(t -> null)
                      .unsafeRun())
          .withMessage("recovery must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> LazyPath.defer(() -> null).get())
          .withMessage("supplier must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> lazy.zipWith(lazy, (a, b) -> null).get())
          .withMessage("combiner must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.<String, String>asks(r -> null).run("env"))
          .withMessage("f must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> reader.zipWith(reader, (a, b) -> null).run("env"))
          .withMessage("combiner must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> state.zipWith(state, (a, b) -> null).run(0))
          .withMessage("combiner must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> stream.zipWith(stream, (a, b) -> null).toList().unsafeRun())
          .withMessage("combiner must not return null");
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  Path.vstream(VStream.<String>fail(new IllegalStateException()))
                      .recover(t -> null)
                      .toList()
                      .unsafeRun())
          .withMessage("recovery must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.streamPure("a").map(a -> null).toList())
          .withMessage("mapper must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.streamFromList(Arrays.asList("a", null)).toList())
          .withMessage("list must not contain null");
      assertThatNullPointerException()
          .isThrownBy(() -> StreamPath.generate(() -> null).headOption())
          .withMessage("supplier must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> ConfigContext.<String, String>io(r -> null).runWithSync("config"))
          .withMessage("computation must not return null");
      assertThatNullPointerException()
          .isThrownBy(
              () -> VTaskContext.<String>fail(new IllegalStateException()).runOrElseGet(e -> null))
          .withMessage("errorHandler must not return null");
    }

    @Test
    @DisplayName("a lazy path's map and combiners name the function that returned null")
    void aLazyPathsMapAndCombinersNameTheFunction() {
      IdMonad id = IdMonad.instance();
      Natural<IdKind.Witness, IdKind.Witness> nat = Natural.identity();
      IOPath<String> io = Path.ioPure("a");
      VTaskPath<String> task = Path.vtaskPure("a");
      VStreamPath<String> stream = Path.vstreamOf("a");
      LazyPath<String> lazy = Path.lazyNow("a");
      ReaderPath<String, String> reader = Path.readerPure("a");
      WithStatePath<Integer, String> state = Path.statePure("a");
      TrampolinePath<String> trampoline = Path.trampolineDone("a");
      FreePath<IdKind.Witness, String> free = Path.freePure("a", id);
      FreeApPath<IdKind.Witness, String> freeAp = Path.freeApPure("a", id);
      StreamPath<String> letters = Path.streamPure("a");
      String mapper = "mapper must not return null";
      String combiner = "combiner must not return null";

      assertThatNullPointerException()
          .isThrownBy(() -> io.map(a -> null).unsafeRun())
          .withMessage(mapper);
      assertThatNullPointerException()
          .isThrownBy(() -> task.map(a -> null).unsafeRun())
          .withMessage(mapper);
      assertThatNullPointerException()
          .isThrownBy(() -> stream.map(a -> null).toList().unsafeRun())
          .withMessage(mapper);
      assertThatNullPointerException()
          .isThrownBy(() -> lazy.map(a -> null).get())
          .withMessage(mapper);
      assertThatNullPointerException()
          .isThrownBy(() -> reader.map(a -> null).run("env"))
          .withMessage(mapper);
      assertThatNullPointerException()
          .isThrownBy(() -> state.map(a -> null).run(0))
          .withMessage(mapper);
      assertThatNullPointerException()
          .isThrownBy(() -> trampoline.map(a -> null).run())
          .withMessage(mapper);
      assertThatNullPointerException()
          .isThrownBy(() -> free.map(a -> null).foldMap(nat, id))
          .withMessage(mapper);
      assertThatNullPointerException()
          .isThrownBy(() -> freeAp.map(a -> null).foldMapKind(nat, id))
          .withMessage(mapper);

      assertThatNullPointerException()
          .isThrownBy(() -> io.zipWith3(io, io, (a, b, c) -> null).unsafeRun())
          .withMessage(combiner);
      assertThatNullPointerException()
          .isThrownBy(() -> task.zipWith3(task, task, (a, b, c) -> null).unsafeRun())
          .withMessage(combiner);
      assertThatNullPointerException()
          .isThrownBy(() -> stream.zipWith3(stream, stream, (a, b, c) -> null).toList().unsafeRun())
          .withMessage(combiner);
      assertThatNullPointerException()
          .isThrownBy(() -> lazy.zipWith3(lazy, lazy, (a, b, c) -> null).get())
          .withMessage(combiner);
      assertThatNullPointerException()
          .isThrownBy(() -> reader.zipWith3(reader, reader, (a, b, c) -> null).run("env"))
          .withMessage(combiner);
      assertThatNullPointerException()
          .isThrownBy(() -> state.zipWith3(state, state, (a, b, c) -> null).run(0))
          .withMessage(combiner);
      assertThatNullPointerException()
          .isThrownBy(() -> freeAp.zipWith3(freeAp, freeAp, (a, b, c) -> null).foldMapKind(nat, id))
          .withMessage(combiner);
      assertThatNullPointerException()
          .isThrownBy(() -> trampoline.zipWith(trampoline, (a, b) -> null).run())
          .withMessage(combiner);
      assertThatNullPointerException()
          .isThrownBy(() -> trampoline.zipWith3(trampoline, trampoline, (a, b, c) -> null).run())
          .withMessage(combiner);
      assertThatNullPointerException()
          .isThrownBy(() -> free.zipWith(free, (a, b) -> null).foldMap(nat, id))
          .withMessage(combiner);
      assertThatNullPointerException()
          .isThrownBy(() -> free.zipWith3(free, free, (a, b, c) -> null).foldMap(nat, id))
          .withMessage(combiner);
      assertThatNullPointerException()
          .isThrownBy(() -> freeAp.zipWith(freeAp, (a, b) -> null).foldMapKind(nat, id))
          .withMessage(combiner);
      assertThatNullPointerException()
          .isThrownBy(() -> letters.zipWith(letters, (a, b) -> null).toList())
          .withMessage(combiner);
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip4(io, io, io, io, (a, b, c, d) -> null).unsafeRun())
          .withMessage(combiner);
    }

    @Test
    @DisplayName("a resource, a stream source and a deferred config name what returned null")
    void aResourceAStreamSourceAndADeferredConfigNameTheSource() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> IOPath.withResource(() -> (AutoCloseable) () -> {}, r -> null).unsafeRun())
          .withMessage("use must not return null");
      assertThatNullPointerException()
          .isThrownBy(() -> StreamPath.fromSupplier(() -> Stream.of("a", null)).toList())
          .withMessage("supplier's stream must not contain null");
      assertThatNullPointerException()
          .isThrownBy(() -> StreamPath.iterate("a", a -> null).take(2).toList())
          .withMessage("f must not return null");
      assertThatNullPointerException()
          .isThrownBy(
              () -> ConfigContext.<String, String>ioDeferred(r -> () -> null).runWithSync("config"))
          .withMessage("computation's supplier must not return null");
    }

    @Test
    @DisplayName("a future's path names the function that returned null")
    void aFuturesPathNamesTheFunction() {
      CompletableFuturePath<String> future = Path.futureCompleted("a");

      failsInTheFuture(future.map(a -> null), "mapper must not return null");
      failsInTheFuture(future.zipWith(future, (a, b) -> null), "combiner must not return null");
      failsInTheFuture(
          future.zipWith3(future, future, (a, b, c) -> null), "combiner must not return null");
      failsInTheFuture(
          Path.<String>futureFailed(new IllegalStateException()).recover(e -> null),
          "recovery must not return null");
      failsInTheFuture(Path.futureAsync(() -> null), "supplier must not return null");
    }

    @Test
    @DisplayName("a lazy path's focus names the form that says what a null focus becomes")
    void aLazyPathsFocusNamesTheRoute() {
      Box unlabelled = new Box(null);
      String failWithYourOwn =
          "the focus is null: use focus(path.nullable(), exceptionIfAbsent) to fail with an"
              + " exception of your own";

      assertThatNullPointerException()
          .isThrownBy(() -> Path.ioPure(unlabelled).focus(LABEL).unsafeRun())
          .withMessage(failWithYourOwn);
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vtaskPure(unlabelled).focus(LABEL).unsafeRun())
          .withMessage(failWithYourOwn);
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vstreamOf(unlabelled).focus(LABEL).toList().unsafeRun())
          .withMessage("the focus is null: use focus(path.nullable()) to leave it out");
      assertThatNullPointerException()
          .isThrownBy(() -> Path.id(unlabelled).focus(LABEL))
          .withMessage("the focus is null: use focus(path.nullable()) to give a Nothing for it");
    }
  }

  @Nested
  @DisplayName("A hand-written computation that returns null fails where it is run")
  class AHandWrittenComputationsNullResult {

    private static final String SUCCESS_HOLDS = " and a Success always holds a value";
    private static final String TASK_GIVES =
        "the task returned null, and a VTask always gives a value";
    private static final String IO_GIVES =
        "the IO returned null, and an IOPath always gives a value";

    @Test
    @DisplayName("a capturing conversion gives a Failure, or the mapped error, naming its source")
    void aCapturingConversionGivesAFailure() {
      IOPath<String> io = Path.ioPath(RAW_IO);
      VTaskPath<String> task = Path.vtaskPath(RAW_TASK);

      failsWith(RAW_TASK.runSafe(), "the task returned null," + SUCCESS_HOLDS);
      failsWith(task.runSafe(), TASK_GIVES);
      failsWith(task.toTryPath().run(), TASK_GIVES);
      failsWith(task.asTry().unsafeRun(), TASK_GIVES);
      failsWith(io.runSafe(), IO_GIVES);
      failsWith(io.asTry().unsafeRun(), IO_GIVES);
      failsWith(
          Scope.<String>anySucceed().fork(RAW_TASK).joinSafe().run(),
          "the scope's result is null," + SUCCESS_HOLDS);
      assertThatEither(io.catching(Throwable::getMessage).unsafeRun()).hasLeft(IO_GIVES);
      assertThatEither(task.catching(Throwable::getMessage).unsafeRun()).hasLeft(TASK_GIVES);
      assertThatNullPointerException().isThrownBy(RAW_TASK::run).withMessage(TASK_GIVES);
    }

    @Test
    @DisplayName("an IOPath refuses a null from an IO written by hand wherever it runs it")
    void anIOPathRefusesAHandWrittenNull() {
      IOPath<String> io = Path.ioPath(RAW_IO);

      assertThatNullPointerException().isThrownBy(io::unsafeRun).withMessage(IO_GIVES);
      assertThatNullPointerException()
          .isThrownBy(() -> io.zipWith(io, (a, b) -> a + b).unsafeRun())
          .withMessage(IO_GIVES);
      assertThatNullPointerException()
          .isThrownBy(() -> io.guarantee(() -> {}).unsafeRun())
          .withMessage(IO_GIVES);
      assertThat(io.asMaybe().unsafeRun().isNothing()).isTrue();
    }

    @Test
    @DisplayName("a future that completes with null fails the path where it enters")
    void aFutureThatCompletesWithNullFailsThePath() {
      CompletableFuturePath<String> path =
          Path.future(CompletableFuture.<String>completedFuture(null));
      String completedWithNull =
          "the future completed with null: map a future with nothing to return to Unit, with"
              + " thenApply(_ -> Unit.INSTANCE), before wrapping it";

      failsInTheFuture(path, completedWithNull);
      assertThatEither(path.toEitherPath().run())
          .hasLeftSatisfying(
              e ->
                  assertThat(e)
                      .isInstanceOf(NullPointerException.class)
                      .hasMessage(completedWithNull));
    }

    @Test
    @DisplayName(
        "a saga step written by hand that returns null fails, and the steps before it are compensated")
    void aSagaStepThatReturnsNullIsCompensated() {
      AtomicBoolean compensated = new AtomicBoolean();
      Saga<String> saga =
          Saga.of(VTask.succeed("booked"), (String s) -> compensated.set(true))
              .flatMap(s -> Saga.of(RAW_TASK, (String t) -> {}));

      assertThatEither(saga.runSafe().run())
          .hasLeftSatisfying(
              (SagaError error) ->
                  assertThat(error.originalError())
                      .isInstanceOf(NullPointerException.class)
                      .hasMessage(TASK_GIVES));
      assertThat(compensated).isTrue();
    }

    @Test
    @DisplayName("a run with a fallback treats a null result as a failure")
    void aRunWithAFallbackTreatsANullAsAFailure() {
      VTaskPath<String> second = Path.vtask(() -> "second");
      VTaskContext<String> context = VTaskContext.of(() -> null);

      assertThat(
              PathOps.firstVTaskSuccess(NonEmptyList.of(Path.vtask(() -> null), second))
                  .unsafeRun())
          .isEqualTo("second");
      assertThat(
              PathOps.firstVTaskSuccess(NonEmptyList.of(Path.vtaskPath(RAW_TASK), second))
                  .unsafeRun())
          .isEqualTo("second");
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  PathOps.firstVTaskSuccess(NonEmptyList.of(Path.vtaskPath(RAW_TASK))).unsafeRun())
          .withMessage(TASK_GIVES);
      assertThat(context.runOrElse("fallback")).isEqualTo("fallback");
      assertThat(context.runOrElseGet(e -> "fallback")).isEqualTo("fallback");
    }
  }
}
