// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.effect.capability.Recoverable;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.resilience.Bulkhead;
import org.higherkindedj.hkt.resilience.BulkheadFullException;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.CircuitOpenException;
import org.higherkindedj.hkt.resilience.Retry;
import org.higherkindedj.hkt.resilience.RetryExhaustedException;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.ScopeJoiner;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * A fluent path wrapper for asynchronous computations that may fail with a typed, domain error.
 *
 * <p>{@code VResultPath} represents the stacked shape {@code VTask<Either<E, A>>}: deferred work
 * that executes on a virtual thread and completes with either a success value ({@code Right}) or a
 * typed failure ({@code Left}). It reads as "an {@link EitherPath} that happens to be
 * asynchronous", providing the same vocabulary ({@code map}/{@code via} on the success channel,
 * {@code mapError}/{@code recover} on the error channel) without any {@code Kind} or transformer
 * ceremony surfacing.
 *
 * <p><b>Note on the name:</b> there is no {@code Result} type in higher-kinded-j — the carrier is
 * {@link Either}{@code <E, A>}. The name describes the shape: an asynchronous <i>result</i> that is
 * either a value or a typed failure.
 *
 * <h2>Use Cases</h2>
 *
 * <ul>
 *   <li>Remote calls returning a value or a typed domain error
 *   <li>Railway-style workflows over asynchronous steps
 *   <li>Typed-error recovery and enrichment across async boundaries
 *   <li>Keeping domain failures out of the exception channel
 * </ul>
 *
 * <h2>Creating VResultPath instances</h2>
 *
 * <pre>{@code
 * // Deferred async computation producing an Either
 * VResultPath<OrderError, Order> deferred = Path.vresultDefer(() -> orderService.load(orderId));
 *
 * // Already-computed success or failure
 * VResultPath<OrderError, Order> ok = Path.vresultRight(order);
 * VResultPath<OrderError, Order> failed = Path.vresultLeft(OrderError.notFound(orderId));
 *
 * // From an existing carrier or Either
 * VResultPath<OrderError, Order> fromTask = Path.vresult(someVTask);
 * VResultPath<OrderError, Order> fromEither = Path.vresultEither(someEither);
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <pre>{@code
 * VResultPath<OrderError, Receipt> receipt =
 *     validateAddress(request)                        // VResultPath<OrderError, Address>
 *         .via(addr -> lookupCustomer(request))       // short-circuits on the first Left
 *         .map(Customer::preferredPayment)
 *         .recoverWith(err -> retryOnce(request))     // typed-error-aware recovery
 *         .mapError(OrderError::enrich);              // transform the error channel
 *
 * // Nothing has executed yet; run at the boundary:
 * Either<OrderError, Receipt> result = receipt.run().run();
 * }</pre>
 *
 * <h2>Laziness</h2>
 *
 * <p>Like {@link VTaskPath}, all operations are lazy: they describe a computation but execute
 * nothing until the carrier returned by {@link #run()} is executed (via {@link VTask#run()}, {@link
 * VTask#runSafe()} or {@link VTask#runAsync()}). Running the carrier again re-executes the whole
 * pipeline.
 *
 * <h2>Composition</h2>
 *
 * <p>Combinators compose directly over the carrier — {@link VTask#map(Function)}/{@link
 * VTask#flatMap(Function)} with {@link Either#fold(Function, Function)} — following the Path-family
 * precedent ({@link CompletableFuturePath}, {@link EitherPath}) rather than delegating to the
 * {@code EitherT} transformer. The Functor and Monad laws for this composition are pinned by the
 * {@code VResultPathTest} law suite.
 *
 * @param <E> the type of the error (left value)
 * @param <A> the type of the success value (right value)
 * @see VTask
 * @see Either
 * @see EitherPath
 * @see VTaskPath
 * @see CompletableFuturePath
 */
public final class VResultPath<E, A> implements Recoverable<E, A> {

  private final VTask<Either<E, A>> task;

  /**
   * Creates a new VResultPath wrapping the given carrier.
   *
   * @param task the VTask carrier to wrap; must not be null
   */
  VResultPath(VTask<Either<E, A>> task) {
    this.task = Objects.requireNonNull(task, "task must not be null");
  }

  // ===== Factory Methods =====

  /**
   * Creates a VResultPath from an existing carrier.
   *
   * @param task the {@code VTask<Either<E, A>>} to wrap; must not be null
   * @param <E> the error type
   * @param <A> the success type
   * @return a VResultPath wrapping the carrier
   * @throws NullPointerException if task is null
   */
  public static <E, A> VResultPath<E, A> fromVTask(VTask<Either<E, A>> task) {
    return new VResultPath<>(task);
  }

  /**
   * Creates a VResultPath from an already-computed Either.
   *
   * <p>No side effects occur when the resulting path is run; the Either is simply produced.
   *
   * @param either the Either to lift; must not be null
   * @param <E> the error type
   * @param <A> the success type
   * @return a VResultPath that immediately produces the Either when run
   * @throws NullPointerException if either is null
   */
  public static <E, A> VResultPath<E, A> fromEither(Either<E, A> either) {
    Objects.requireNonNull(either, "either must not be null");
    return new VResultPath<>(VTask.succeed(either));
  }

  /**
   * Creates a successful VResultPath containing the given value.
   *
   * @param value the success value
   * @param <E> the phantom error type
   * @param <A> the success type
   * @return a VResultPath that immediately produces {@code Right(value)} when run
   */
  public static <E, A> VResultPath<E, A> pure(A value) {
    return new VResultPath<>(VTask.succeed(Either.right(value)));
  }

  /**
   * Creates a failed VResultPath containing the given typed error.
   *
   * @param error the error value; must not be null
   * @param <E> the error type
   * @param <A> the phantom success type
   * @return a VResultPath that immediately produces {@code Left(error)} when run
   * @throws NullPointerException if error is null
   */
  public static <E, A> VResultPath<E, A> raiseError(E error) {
    Objects.requireNonNull(error, "error must not be null");
    return new VResultPath<>(VTask.succeed(Either.left(error)));
  }

  /**
   * Creates a VResultPath from a deferred computation that produces an Either.
   *
   * <p>The supplier is not invoked until the path is run, mirroring {@link VTask} laziness.
   *
   * @param supplier the deferred computation; must not be null and must not return null
   * @param <E> the error type
   * @param <A> the success type
   * @return a VResultPath representing the deferred computation
   * @throws NullPointerException if supplier is null
   */
  public static <E, A> VResultPath<E, A> defer(Supplier<Either<E, A>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return new VResultPath<>(
        VTask.of(() -> Objects.requireNonNull(supplier.get(), "supplier must not return null")));
  }

  // ===== Terminal Operations =====

  /**
   * Returns the underlying carrier.
   *
   * <p>This is the boundary operation: execute the returned {@link VTask} (via {@link VTask#run()},
   * {@link VTask#runSafe()} or {@link VTask#runAsync()}) to obtain the {@code Either<E, A>}.
   *
   * @return the wrapped {@code VTask<Either<E, A>>}
   */
  public VTask<Either<E, A>> run() {
    return task;
  }

  /**
   * Folds both channels into a single value, staying within the lazy carrier.
   *
   * <p>The returned VTask is lazy; nothing executes until it is run.
   *
   * @param leftMapper the function to apply if this path produces an error; must not be null
   * @param rightMapper the function to apply if this path produces a value; must not be null
   * @param <B> the result type
   * @return a VTask producing the result of applying the appropriate function
   * @throws NullPointerException if either mapper is null
   */
  public <B> VTask<B> fold(
      Function<? super E, ? extends B> leftMapper, Function<? super A, ? extends B> rightMapper) {
    Objects.requireNonNull(leftMapper, "leftMapper must not be null");
    Objects.requireNonNull(rightMapper, "rightMapper must not be null");
    return task.map(either -> either.fold(leftMapper, rightMapper));
  }

  /**
   * Produces the success value, or the provided default if this path produces an error.
   *
   * <p>The returned VTask is lazy; nothing executes until it is run.
   *
   * @param defaultValue the value to produce if this path produces an error
   * @return a VTask producing the success value or the default
   */
  public VTask<A> getOrElse(A defaultValue) {
    return task.map(either -> either.fold(_ -> defaultValue, a -> a));
  }

  /**
   * Produces the success value, or invokes the supplier if this path produces an error.
   *
   * <p>The returned VTask is lazy; neither the pipeline nor the supplier executes until it is run.
   *
   * @param supplier provides the default value; must not be null
   * @return a VTask producing the success value or the supplier's result
   * @throws NullPointerException if supplier is null
   */
  public VTask<A> getOrElseGet(Supplier<? extends A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return task.map(either -> either.fold(_ -> supplier.get(), a -> a));
  }

  // ===== Composable implementation =====

  @Override
  public <B> VResultPath<E, B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new VResultPath<>(task.map(either -> either.map(mapper)));
  }

  @Override
  public VResultPath<E, A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    return new VResultPath<>(
        task.map(
            either -> {
              either.ifRight(consumer);
              return either;
            }));
  }

  /**
   * Observes the error value without modifying it (for debugging).
   *
   * <p>The consumer is invoked lazily, when the path is run and only if it produces an error.
   *
   * @param consumer the action to perform on the error; must not be null
   * @return a new VResultPath with the observation attached
   * @throws NullPointerException if consumer is null
   */
  public VResultPath<E, A> peekLeft(Consumer<? super E> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    return new VResultPath<>(
        task.map(
            either -> {
              either.ifLeft(consumer);
              return either;
            }));
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> VResultPath<E, C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof VResultPath<?, ?> otherPath)) {
      throw new IllegalArgumentException("Cannot zipWith non-VResultPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    VResultPath<E, B> typedOther = (VResultPath<E, B>) otherPath;

    return new VResultPath<>(
        task.flatMap(
            either ->
                either.fold(
                    e -> VTask.<Either<E, C>>succeed(Either.left(e)),
                    a ->
                        typedOther.task.map(
                            otherEither -> otherEither.map(b -> combiner.apply(a, b))))));
  }

  /**
   * Combines this path with two others using a ternary function.
   *
   * <p>The paths are sequenced left to right; the first {@code Left} short-circuits and the
   * remaining tasks are not executed.
   *
   * @param second the second path; must not be null
   * @param third the third path; must not be null
   * @param combiner the function to combine the values; must not be null
   * @param <B> the type of the second path's value
   * @param <C> the type of the third path's value
   * @param <D> the type of the combined result
   * @return a new path producing the combined result, or the first error encountered
   * @throws NullPointerException if second, third or combiner is null
   */
  public <B, C, D> VResultPath<E, D> zipWith3(
      VResultPath<E, B> second,
      VResultPath<E, C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    return new VResultPath<>(
        task.flatMap(
            firstEither ->
                firstEither.fold(
                    e -> VTask.<Either<E, D>>succeed(Either.left(e)),
                    a ->
                        second.task.flatMap(
                            secondEither ->
                                secondEither.fold(
                                    e -> VTask.<Either<E, D>>succeed(Either.left(e)),
                                    b ->
                                        third.task.map(
                                            thirdEither ->
                                                thirdEither.map(c -> combiner.apply(a, b, c))))))));
  }

  // ===== Chainable implementation =====

  @Override
  public <B> VResultPath<E, B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    VTask<Either<E, B>> composed =
        task.flatMap(
            either ->
                either.fold(
                    e -> VTask.<Either<E, B>>succeed(Either.left(e)),
                    a -> {
                      Chainable<B> result = mapper.apply(a);
                      Objects.requireNonNull(result, "mapper must not return null");

                      if (!(result instanceof VResultPath<?, ?> vResultPath)) {
                        throw new IllegalArgumentException(
                            "via mapper must return VResultPath, got: " + result.getClass());
                      }

                      @SuppressWarnings("unchecked")
                      VResultPath<E, B> typedResult = (VResultPath<E, B>) vResultPath;
                      return typedResult.task;
                    }));

    return new VResultPath<>(composed);
  }

  @Override
  public <B> VResultPath<E, B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    VTask<Either<E, B>> sequenced =
        task.flatMap(
            either ->
                either.fold(
                    e -> VTask.<Either<E, B>>succeed(Either.left(e)),
                    _ -> {
                      Chainable<B> result = supplier.get();
                      Objects.requireNonNull(result, "supplier must not return null");

                      if (!(result instanceof VResultPath<?, ?> vResultPath)) {
                        throw new IllegalArgumentException(
                            "then supplier must return VResultPath, got: " + result.getClass());
                      }

                      @SuppressWarnings("unchecked")
                      VResultPath<E, B> typedResult = (VResultPath<E, B>) vResultPath;
                      return typedResult.task;
                    }));

    return new VResultPath<>(sequenced);
  }

  // ===== Recoverable implementation =====

  @Override
  public VResultPath<E, A> recover(Function<? super E, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return new VResultPath<>(
        task.map(either -> either.fold(e -> Either.<E, A>right(recovery.apply(e)), _ -> either)));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Deviation note (#606): the issue sketched a {@code recoverWith(predicate, handler)}
   * overload; the single-argument family form ships instead - no Path-family member has a predicate
   * overload, and selective recovery reads naturally as {@code recoverWith(e -> pred.test(e) ?
   * handler.apply(e) : VResultPath.raiseError(e))}.
   */
  @Override
  public VResultPath<E, A> recoverWith(Function<? super E, ? extends Recoverable<E, A>> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");

    VTask<Either<E, A>> recovered =
        task.flatMap(
            either ->
                either.fold(
                    e -> {
                      Recoverable<E, A> result = recovery.apply(e);
                      Objects.requireNonNull(result, "recovery must not return null");

                      if (!(result instanceof VResultPath<?, ?> vResultPath)) {
                        throw new IllegalArgumentException(
                            "recoverWith must return VResultPath, got: " + result.getClass());
                      }

                      @SuppressWarnings("unchecked")
                      VResultPath<E, A> typedResult = (VResultPath<E, A>) vResultPath;
                      return typedResult.task;
                    },
                    _ -> VTask.succeed(either)));

    return new VResultPath<>(recovered);
  }

  @Override
  public VResultPath<E, A> orElse(Supplier<? extends Recoverable<E, A>> alternative) {
    Objects.requireNonNull(alternative, "alternative must not be null");
    return recoverWith(_ -> alternative.get());
  }

  @Override
  public <E2> VResultPath<E2, A> mapError(Function<? super E, ? extends E2> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new VResultPath<>(task.map(either -> either.mapLeft(mapper)));
  }

  /**
   * Transforms both the error and the success values simultaneously, returning a new {@code
   * VResultPath} whose error type and success type are both rebased.
   *
   * <p>A {@code Left(e)} becomes {@code Left(errorMapper.apply(e))} and a {@code Right(a)} becomes
   * {@code Right(successMapper.apply(a))}. Equivalent to {@code
   * mapError(errorMapper).map(successMapper)} but expressed in a single call.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * VResultPath<String, Integer> original = Path.vresultRight(42);
   *
   * VResultPath<Integer, String> transformed = original.bimap(
   *     String::length,       // Transform error
   *     n -> "Value: " + n);  // Transform success
   * }</pre>
   *
   * @param errorMapper the function applied to a {@code Left} value; must not be null
   * @param successMapper the function applied to a {@code Right} value; must not be null
   * @param <E2> the new error type
   * @param <A2> the new success type
   * @return a new {@code VResultPath<E2, A2>}
   * @throws NullPointerException if either mapper is null
   */
  public <E2, A2> VResultPath<E2, A2> bimap(
      Function<? super E, ? extends E2> errorMapper,
      Function<? super A, ? extends A2> successMapper) {
    Objects.requireNonNull(errorMapper, "errorMapper must not be null");
    Objects.requireNonNull(successMapper, "successMapper must not be null");
    return new VResultPath<>(task.map(either -> either.bimap(errorMapper, successMapper)));
  }

  // ===== Conversions =====

  /**
   * Converts to an EitherPath by executing the carrier (blocking).
   *
   * <p><b>Note:</b> this executes the underlying VTask on the calling thread. Typed failures are
   * preserved as {@code Left}; exceptions thrown by the task itself (the defect channel) propagate
   * as from {@link VTask#run()}.
   *
   * @return an EitherPath containing the produced Either
   */
  public EitherPath<E, A> toEitherPath() {
    Either<E, A> result =
        Objects.requireNonNull(task.run(), "VTask must not produce a null Either");
    return new EitherPath<>(result);
  }

  /**
   * Converts to a VTaskPath by collapsing the typed error into the VTask failure channel.
   *
   * <p>A {@code Right(a)} becomes a successful task producing {@code a}; a {@code Left(e)} becomes
   * a failed task carrying {@code errorToException.apply(e)}. The conversion is lazy; nothing
   * executes until the resulting path is run.
   *
   * @param errorToException converts the typed error to an exception; must not be null
   * @return a VTaskPath producing the success value or failing with the mapped exception
   * @throws NullPointerException if errorToException is null
   */
  public VTaskPath<A> toVTaskPath(Function<? super E, ? extends Throwable> errorToException) {
    Objects.requireNonNull(errorToException, "errorToException must not be null");
    return Path.vtaskPath(
        task.flatMap(
            either ->
                either.fold(e -> VTask.<A>fail(errorToException.apply(e)), a -> VTask.succeed(a))));
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof VResultPath<?, ?> other)) return false;
    return task.equals(other.task);
  }

  @Override
  public int hashCode() {
    return task.hashCode();
  }

  @Override
  public String toString() {
    return "VResultPath(" + PathToString.DEFERRED + ")";
  }

  // ==================== Outcome-aware structured concurrency (#606 slice 2) ====================
  // The issue sketches Scope.firstSuccess(...), but Scope lives in hkt.vtask and hkt.effect
  // depends on hkt.vtask - statics on Scope referencing VResultPath would create a package
  // cycle, so the combinators live here, implemented over the Scope/ScopeJoiner substrate.

  /**
   * Races the candidates: the first to succeed with a {@code Right} wins and the rest are cancelled
   * - a winning {@code Right} outranks everything, including defects raised by other candidates
   * (the {@code anySucceed} family precedent). Typed failures do not abort the race - they are
   * collected, and only if every candidate fails does the result carry {@code Left} of all their
   * errors, in candidate order. A candidate that throws is a defect: it fails the race only when no
   * candidate ever succeeds.
   *
   * @param candidates the racing paths; must not be null, empty, or contain nulls
   * @param <E> the typed error type
   * @param <A> the success type
   * @return the winning value, or every typed failure when nothing succeeds (non-null)
   * @throws NullPointerException if {@code candidates} or an element is null
   * @throws IllegalArgumentException if {@code candidates} is empty
   */
  public static <E, A> VResultPath<NonEmptyList<E>, A> firstSuccess(
      List<? extends VResultPath<E, A>> candidates) {
    Objects.requireNonNull(candidates, "candidates must not be null");
    if (candidates.isEmpty()) {
      throw new IllegalArgumentException("candidates must not be empty");
    }
    List<VTask<Either<E, A>>> tasks = new ArrayList<>(candidates.size());
    for (VResultPath<E, A> candidate : candidates) {
      tasks.add(Objects.requireNonNull(candidate, "candidates must not contain null").run());
    }
    Scope<Either<E, A>, Either<List<E>, A>> scope =
        Scope.withJoiner(ScopeJoiner.firstSuccessEither());
    for (VTask<Either<E, A>> task : tasks) {
      scope = scope.fork(task);
    }
    VTask<Either<NonEmptyList<E>, A>> joined =
        scope
            .join()
            .map(
                outcome ->
                    outcome.fold(
                        errors ->
                            Either.left(
                                NonEmptyList.of(
                                    errors.getFirst(), errors.subList(1, errors.size()))),
                        Either::right));
    return fromVTask(joined);
  }

  /**
   * Races the candidates with emptiness made unrepresentable - the {@link NonEmptyList} overload of
   * {@link #firstSuccess(List)}, preferred when at least one candidate is statically known (the
   * race-family precedent from #585).
   *
   * @param candidates the racing paths; must not be null or contain nulls
   * @param <E> the typed error type
   * @param <A> the success type
   * @return the winning value, or every typed failure when nothing succeeds (non-null)
   * @throws NullPointerException if {@code candidates} or an element is null
   */
  public static <E, A> VResultPath<NonEmptyList<E>, A> firstSuccess(
      NonEmptyList<VResultPath<E, A>> candidates) {
    Objects.requireNonNull(candidates, "candidates must not be null");
    return firstSuccess(candidates.toJavaList());
  }

  /**
   * Runs every task and succeeds only when all do, fail-fast: the first typed failure cancels the
   * remaining tasks and becomes the result. An empty list is vacuously successful.
   *
   * @param tasks the paths to run together; must not be null or contain nulls
   * @param <E> the typed error type
   * @param <A> the success type
   * @return every value in task order, or the first typed failure (non-null)
   * @throws NullPointerException if {@code tasks} or an element is null
   */
  public static <E, A> VResultPath<E, List<A>> allSucceed(List<? extends VResultPath<E, A>> tasks) {
    Objects.requireNonNull(tasks, "tasks must not be null");
    Scope<A, List<A>> scope = Scope.allSucceed();
    for (VResultPath<E, A> task : tasks) {
      Objects.requireNonNull(task, "tasks must not contain null");
      scope =
          scope.fork(
              task.run()
                  .flatMap(
                      either ->
                          either.fold(
                              error -> VTask.fail(new LeftCarrier(error)), VTask::succeed)));
    }
    VTask<Either<E, List<A>>> joined =
        scope
            .join()
            .<Either<E, List<A>>>map(Either::right)
            .recoverWith(
                failure ->
                    failure instanceof LeftCarrier carrier
                        ? VTask.succeed(Either.left(carrier.typedError()))
                        : VTask.fail(failure));
    return fromVTask(joined);
  }

  /**
   * Runs every task to completion and accumulates: all values when everything succeeds, otherwise
   * every typed failure (in task order). Nothing is cancelled - unlike {@link #allSucceed}, a
   * failure does not abort the others. An empty list is vacuously successful. A task that throws is
   * a defect and fails the whole operation.
   *
   * @param tasks the paths to run together; must not be null or contain nulls
   * @param <E> the typed error type
   * @param <A> the success type
   * @return every value, or every typed failure (non-null)
   * @throws NullPointerException if {@code tasks} or an element is null
   */
  public static <E, A> VResultPath<NonEmptyList<E>, List<A>> allSucceedAccumulating(
      List<? extends VResultPath<E, A>> tasks) {
    Objects.requireNonNull(tasks, "tasks must not be null");
    Scope<Either<E, A>, List<Either<E, A>>> scope = Scope.allSucceed();
    for (VResultPath<E, A> task : tasks) {
      scope = scope.fork(Objects.requireNonNull(task, "tasks must not contain null").run());
    }
    VTask<Either<NonEmptyList<E>, List<A>>> joined =
        scope
            .join()
            .map(
                outcomes -> {
                  List<E> errors = new ArrayList<>();
                  List<A> values = new ArrayList<>();
                  for (Either<E, A> outcome : outcomes) {
                    outcome.fold(
                        error -> {
                          errors.add(error);
                          return null;
                        },
                        value -> {
                          values.add(value);
                          return null;
                        });
                  }
                  return errors.isEmpty()
                      ? Either.<NonEmptyList<E>, List<A>>right(List.copyOf(values))
                      : Either.<NonEmptyList<E>, List<A>>left(
                          NonEmptyList.of(errors.getFirst(), errors.subList(1, errors.size())));
                });
    return fromVTask(joined);
  }

  /**
   * Bounds this path's execution: if it does not complete within {@code duration}, the result is
   * {@code Left} of the designated typed error - a timeout stays on the railway instead of becoming
   * a thrown {@code TimeoutException}.
   *
   * <p>Two caveats inherited from {@link VTask#timeout}: the losing computation is <em>not</em>
   * interrupted - it keeps running unobserved after the typed timeout is returned (bound its side
   * effects accordingly, e.g. with {@link #bracketOutcome}); and a {@code TimeoutException} thrown
   * by code <em>inside</em> the pipeline is indistinguishable from the budget expiring, so it is
   * also mapped to {@code onTimeout}.
   *
   * @param duration the time budget; must not be null
   * @param onTimeout supplies the typed error for the timeout case; must not be null
   * @return a time-bounded path (non-null)
   * @throws NullPointerException if either argument is null
   */
  public VResultPath<E, A> withTimeout(Duration duration, Supplier<? extends E> onTimeout) {
    Objects.requireNonNull(duration, "duration must not be null");
    Objects.requireNonNull(onTimeout, "onTimeout must not be null");
    return fromVTask(
        run()
            .timeout(duration)
            .recoverWith(
                failure ->
                    failure instanceof TimeoutException
                        ? VTask.succeed(Either.left(onTimeout.get()))
                        : VTask.fail(failure)));
  }

  /**
   * Returns a path that retries this computation on <em>thrown exceptions only</em>, per the
   * policy's predicate. A {@code Left} is a business decision, not a fault - it completes the path
   * as-is and is never retried; this overload is the pure railway default. To also retry selected
   * typed errors, use {@link #withRetry(Predicate, RetryPolicy)}.
   *
   * <p>Do not wrap a non-idempotent step (e.g. a payment): retry re-runs the whole computation.
   * Ordering note: the default policy predicate retries every exception, so chaining {@code
   * .withCircuitBreaker(cb).withRetry(policy)} will also retry {@link CircuitOpenException} against
   * an open circuit - exclude it via {@link RetryPolicy#retryIf} or chain the breaker after the
   * retry.
   *
   * @param policy the retry policy; must not be null
   * @return a new path with retry behaviour (non-null)
   * @throws NullPointerException if policy is null
   */
  public VResultPath<E, A> withRetry(RetryPolicy policy) {
    Objects.requireNonNull(policy, "policy must not be null");
    return fromVTask(Retry.retryTask(run(), policy));
  }

  /**
   * Returns a path with railway-aware retry: thrown exceptions retry per the policy's predicate,
   * and a {@code Left} retries only when {@code retryOn} selects it (e.g. {@code
   * SystemError::isTransient}). A {@code Left} the predicate does not select - a business error
   * such as "card declined" - completes the path immediately, never retried. On exhaustion while
   * retrying a selected {@code Left}, the path completes with the last {@code Left}, keeping errors
   * on the typed channel; exhaustion on a thrown exception fails with {@link
   * RetryExhaustedException}.
   *
   * <p>Do not wrap a non-idempotent step (e.g. a payment): retry re-runs the whole computation.
   * Ordering note: the default policy predicate retries every exception, so chaining {@code
   * .withCircuitBreaker(cb).withRetry(retryOn, policy)} will also retry {@link
   * CircuitOpenException} against an open circuit - exclude it via {@link RetryPolicy#retryIf} or
   * chain the breaker after the retry.
   *
   * @param retryOn selects which typed errors are transient enough to retry; must not be null
   * @param policy the retry policy; must not be null
   * @return a new path with railway-aware retry (non-null)
   * @throws NullPointerException if either argument is null
   */
  public VResultPath<E, A> withRetry(Predicate<? super E> retryOn, RetryPolicy policy) {
    Objects.requireNonNull(retryOn, "retryOn must not be null");
    Objects.requireNonNull(policy, "policy must not be null");
    return fromVTask(RailwayRetry.retryTaskEither(run(), retryOn, policy));
  }

  /**
   * Returns a path that executes through the circuit breaker. A {@code Left} is a successfully
   * computed value - it does <em>not</em> count as a breaker failure; defects (thrown exceptions)
   * trip the circuit. When the circuit is open the {@link CircuitOpenException} surfaces as a
   * defect; use {@link #withCircuitBreaker(CircuitBreaker, Function)} to keep the rejection on the
   * typed channel.
   *
   * <p>The breaker also applies its configured per-call budget ({@code
   * CircuitBreakerConfig.callTimeout()}, 10 seconds by default): a pipeline slower than that - even
   * one that would have produced a {@code Left} - counts as a breaker failure and surfaces as a
   * defect wrapping the checked {@code TimeoutException}.
   *
   * @param circuitBreaker the (shareable) breaker; must not be null
   * @return a new path protected by the breaker (non-null)
   * @throws NullPointerException if circuitBreaker is null
   */
  public VResultPath<E, A> withCircuitBreaker(CircuitBreaker circuitBreaker) {
    Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
    return fromVTask(circuitBreaker.protect(run()));
  }

  /**
   * Returns a path that executes through the circuit breaker, keeping an open-circuit rejection on
   * the typed channel: {@link CircuitOpenException} becomes {@code Left(onOpen.apply(e))}. A {@code
   * Left} is a successfully computed value and does not count as a breaker failure. The per-call
   * budget caveat on {@link #withCircuitBreaker(CircuitBreaker)} applies here too.
   *
   * <p>A {@code CircuitOpenException} raised by the pipeline <em>itself</em> (e.g. a nested breaker
   * inside it) is indistinguishable from this breaker's rejection and is likewise mapped through
   * {@code onOpen}.
   *
   * @param circuitBreaker the (shareable) breaker; must not be null
   * @param onOpen types the open-circuit rejection; must not be null
   * @return a new path protected by the breaker (non-null)
   * @throws NullPointerException if either argument is null
   */
  public VResultPath<E, A> withCircuitBreaker(
      CircuitBreaker circuitBreaker, Function<? super CircuitOpenException, ? extends E> onOpen) {
    Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
    Objects.requireNonNull(onOpen, "onOpen must not be null");
    return fromVTask(
        circuitBreaker
            .protect(run())
            .recoverWith(
                failure ->
                    failure instanceof CircuitOpenException open
                        ? VTask.succeed(Either.left(onOpen.apply(open)))
                        : VTask.fail(failure)));
  }

  /**
   * Returns a path that executes through the bulkhead, bounding how many callers run this
   * computation concurrently. A caller that finds no permit free <em>waits</em> up to the
   * bulkhead's configured {@code waitTimeout} (5 seconds by default) before {@link
   * BulkheadFullException} surfaces as a defect; use {@link #withBulkhead(Bulkhead, Function)} to
   * keep the rejection on the typed channel.
   *
   * @param bulkhead the (shareable) bulkhead; must not be null
   * @return a new path protected by the bulkhead (non-null)
   * @throws NullPointerException if bulkhead is null
   */
  public VResultPath<E, A> withBulkhead(Bulkhead bulkhead) {
    Objects.requireNonNull(bulkhead, "bulkhead must not be null");
    return fromVTask(bulkhead.protect(run()));
  }

  /**
   * Returns a path that executes through the bulkhead, keeping a rejected execution on the typed
   * channel: {@link BulkheadFullException} becomes {@code Left(onFull.apply(e))} - including when
   * the permit wait is interrupted (the interrupt flag stays set). A {@code BulkheadFullException}
   * raised by the pipeline <em>itself</em> is indistinguishable from this bulkhead's rejection and
   * is likewise mapped through {@code onFull}.
   *
   * @param bulkhead the (shareable) bulkhead; must not be null
   * @param onFull types the bulkhead rejection; must not be null
   * @return a new path protected by the bulkhead (non-null)
   * @throws NullPointerException if either argument is null
   */
  public VResultPath<E, A> withBulkhead(
      Bulkhead bulkhead, Function<? super BulkheadFullException, ? extends E> onFull) {
    Objects.requireNonNull(bulkhead, "bulkhead must not be null");
    Objects.requireNonNull(onFull, "onFull must not be null");
    return fromVTask(
        bulkhead
            .protect(run())
            .recoverWith(
                failure ->
                    failure instanceof BulkheadFullException full
                        ? VTask.succeed(Either.left(onFull.apply(full)))
                        : VTask.fail(failure)));
  }

  /**
   * The bracket pattern with an outcome-aware release: acquire a resource, use it, and ALWAYS
   * release it - with the release seeing the {@link Either} outcome, so compensation (confirm vs
   * refund vs plain cleanup) is decided from the result, not a side flag.
   *
   * <p>Policies: a typed failure from {@code acquire} skips {@code use} and {@code release}
   * (nothing was acquired). A defect thrown inside {@code use} - including one thrown while {@code
   * use} is still <em>constructing</em> its path, before any task runs - is first converted to the
   * typed channel through {@code onDefect}, so {@code release} always observes a real outcome and
   * the resource is never leaked. A defect thrown by {@code release} itself propagates as a defect
   * - broken cleanup is exceptional and must be visible, even at the cost of masking the primary
   * outcome.
   *
   * <p>Cancellation reaches {@code use} as an {@link InterruptedException}-style defect and is
   * therefore also typed through {@code onDefect} (release still runs - the {@code Resource}
   * guarantee). If the pipeline must distinguish cancellation from domain failure, map it to a
   * dedicated error in {@code onDefect}.
   *
   * @param acquire produces the resource; must not be null
   * @param use consumes the resource; must not be null
   * @param release always runs after {@code use}, observing the outcome; must not be null
   * @param onDefect types a defect thrown inside {@code use}; must not be null
   * @param <E> the typed error type
   * @param <A> the resource type
   * @param <B> the result type
   * @return the bracketed path (non-null)
   * @throws NullPointerException if any argument is null
   */
  public static <E, A, B> VResultPath<E, B> bracketOutcome(
      VResultPath<E, A> acquire,
      Function<? super A, ? extends VResultPath<E, B>> use,
      BiFunction<? super A, ? super Either<E, B>, ? extends VTask<?>> release,
      Function<? super Throwable, ? extends E> onDefect) {
    Objects.requireNonNull(acquire, "acquire must not be null");
    Objects.requireNonNull(use, "use must not be null");
    Objects.requireNonNull(release, "release must not be null");
    Objects.requireNonNull(onDefect, "onDefect must not be null");
    return fromVTask(
        acquire
            .run()
            .flatMap(
                acquired ->
                    acquired.fold(
                        error -> VTask.succeed(Either.left(error)),
                        resource -> {
                          VTask<Either<E, B>> useOutcome;
                          try {
                            useOutcome = use.apply(resource).run();
                          } catch (Throwable defect) {
                            // use threw while CONSTRUCTING its path (before any task ran);
                            // funnel it into the task channel so the recoverWith below types
                            // it and release still observes a real outcome - never a leak.
                            useOutcome = VTask.fail(defect);
                          }
                          return useOutcome
                              .recoverWith(
                                  defect -> VTask.succeed(Either.left(onDefect.apply(defect))))
                              .flatMap(
                                  outcome ->
                                      release.apply(resource, outcome).map(ignored -> outcome));
                        })));
  }

  /** Smuggles a typed error through the scope's failure channel for fail-fast joining. */
  private static final class LeftCarrier extends RuntimeException {
    private final transient Object error;

    LeftCarrier(Object error) {
      super(null, null, false, false);
      this.error = error;
    }

    @SuppressWarnings("unchecked") // sound: the carrier is created and consumed with the same E
    <E> E typedError() {
      return (E) error;
    }
  }
}
