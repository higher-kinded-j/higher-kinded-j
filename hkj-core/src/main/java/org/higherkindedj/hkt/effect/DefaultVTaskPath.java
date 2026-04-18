// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.effect.capability.Effectful;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.resilience.Bulkhead;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.Retry;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;

/**
 * Default implementation of {@link VTaskPath}.
 *
 * <p>This class wraps a {@link VTask} and delegates all operations to it, providing the fluent
 * Effect Path API.
 *
 * @param <A> the type of the value produced by the computation
 */
final class DefaultVTaskPath<A> implements VTaskPath<A> {

  private final VTask<A> value;

  /**
   * Creates a new DefaultVTaskPath wrapping the given VTask.
   *
   * @param value the VTask to wrap; must not be null
   */
  DefaultVTaskPath(VTask<A> value) {
    this.value = Objects.requireNonNull(value, "value must not be null");
  }

  @Override
  public VTask<A> run() {
    return value;
  }

  @Override
  public A unsafeRun() {
    // VTask.run() already wraps checked exceptions in VTaskExecutionException,
    // so it can only throw RuntimeException or Error — no additional wrapping needed.
    return value.run();
  }

  @Override
  public CompletableFuture<A> runAsync() {
    return value.runAsync();
  }

  // ===== Composable implementation =====

  @Override
  public <B> VTaskPath<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new DefaultVTaskPath<>(value.map(mapper));
  }

  @Override
  public VTaskPath<A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    return new DefaultVTaskPath<>(value.peek(consumer));
  }

  @Override
  public VTaskPath<Unit> asUnit() {
    return new DefaultVTaskPath<>(value.asUnit());
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> VTaskPath<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof VTaskPath<?> otherVTask)) {
      throw new IllegalArgumentException("Cannot zipWith non-VTaskPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    VTaskPath<B> typedOther = (VTaskPath<B>) otherVTask;

    return new DefaultVTaskPath<>(Par.map2(this.run(), typedOther.run(), combiner));
  }

  @Override
  public <B, C, D> VTaskPath<D> zipWith3(
      VTaskPath<B> second,
      VTaskPath<C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    // Adapt the combiner to match Par.map3's invariant signature
    Function3<A, B, C, D> adaptedCombiner = (a, b, c) -> combiner.apply(a, b, c);
    return new DefaultVTaskPath<>(Par.map3(this.run(), second.run(), third.run(), adaptedCombiner));
  }

  // ===== Chainable implementation =====

  @Override
  public <B> VTaskPath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    return new DefaultVTaskPath<>(
        VTask.delay(
            () -> {
              A a = this.unsafeRun();
              Chainable<B> result = mapper.apply(a);
              Objects.requireNonNull(result, "mapper must not return null");

              if (!(result instanceof VTaskPath<?> vtaskPath)) {
                throw new IllegalArgumentException(
                    "via mapper must return VTaskPath, got: " + result.getClass());
              }

              @SuppressWarnings("unchecked")
              VTaskPath<B> typedResult = (VTaskPath<B>) vtaskPath;
              return typedResult.unsafeRun();
            }));
  }

  @Override
  public <B> VTaskPath<B> flatMap(Function<? super A, ? extends Chainable<B>> mapper) {
    return via(mapper);
  }

  @Override
  public <B> VTaskPath<B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    return new DefaultVTaskPath<>(
        VTask.delay(
            () -> {
              // Execute this VTask for its side effects
              this.unsafeRun();

              Chainable<B> result = supplier.get();
              Objects.requireNonNull(result, "supplier must not return null");

              if (!(result instanceof VTaskPath<?> vtaskPath)) {
                throw new IllegalArgumentException(
                    "then supplier must return VTaskPath, got: " + result.getClass());
              }

              @SuppressWarnings("unchecked")
              VTaskPath<B> typedResult = (VTaskPath<B>) vtaskPath;
              return typedResult.unsafeRun();
            }));
  }

  // ===== Error handling =====

  @Override
  public VTaskPath<A> handleError(Function<? super Throwable, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return new DefaultVTaskPath<>(value.recover(recovery));
  }

  @Override
  public VTaskPath<A> handleErrorWith(
      Function<? super Throwable, ? extends Effectful<A>> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return new DefaultVTaskPath<>(
        value.recoverWith(
            t -> {
              Effectful<A> fallback = recovery.apply(t);
              Objects.requireNonNull(fallback, "recovery must not return null");
              // If the fallback is itself a VTaskPath, use its VTask directly so we
              // preserve virtual-thread execution semantics; otherwise adapt the
              // arbitrary Effectful<A> by delegating to unsafeRun inside a VTask.
              if (fallback instanceof VTaskPath<A> vtaskFallback) {
                return vtaskFallback.run();
              }
              return VTask.of(fallback::unsafeRun);
            }));
  }

  // ===== Timeout =====

  @Override
  public VTaskPath<A> timeout(Duration duration) {
    Objects.requireNonNull(duration, "duration must not be null");
    return new DefaultVTaskPath<>(value.timeout(duration));
  }

  // ===== Focus Bridge Methods =====

  @Override
  public <B> VTaskPath<B> focus(FocusPath<A, B> path) {
    Objects.requireNonNull(path, "path must not be null");
    return map(path::get);
  }

  @Override
  public <B> VTaskPath<B> focus(
      AffinePath<A, B> path, Supplier<? extends RuntimeException> exceptionIfAbsent) {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(exceptionIfAbsent, "exceptionIfAbsent must not be null");
    return via(
        a ->
            path.getOptional(a)
                .<VTaskPath<B>>map(Path::vtaskPure)
                .orElseGet(
                    () ->
                        Path.vtask(
                            () -> {
                              throw exceptionIfAbsent.get();
                            })));
  }

  // ===== Retry Operations =====

  @Override
  public VTaskPath<A> withRetry(RetryPolicy policy) {
    Objects.requireNonNull(policy, "policy must not be null");
    return new DefaultVTaskPath<>(Retry.retryTask(value, policy));
  }

  @Override
  public VTaskPath<A> retry(int maxAttempts, Duration initialDelay) {
    return withRetry(RetryPolicy.exponentialBackoffWithJitter(maxAttempts, initialDelay));
  }

  // ===== Resilience Operations =====

  @Override
  public VTaskPath<A> withCircuitBreaker(CircuitBreaker circuitBreaker) {
    Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
    return new DefaultVTaskPath<>(circuitBreaker.protect(value));
  }

  @Override
  public VTaskPath<A> withBulkhead(Bulkhead bulkhead) {
    Objects.requireNonNull(bulkhead, "bulkhead must not be null");
    return new DefaultVTaskPath<>(bulkhead.protect(value));
  }

  // ===== Effect Wrapping Methods =====

  @Override
  public <E> VTaskPath<Either<E, A>> catching(
      Function<? super Throwable, ? extends E> exceptionMapper) {
    Objects.requireNonNull(exceptionMapper, "exceptionMapper must not be null");
    return new DefaultVTaskPath<>(
        VTask.delay(
            () -> {
              try {
                return Either.right(this.unsafeRun());
              } catch (Throwable t) {
                return Either.left(exceptionMapper.apply(t));
              }
            }));
  }

  @Override
  public VTaskPath<Maybe<A>> asMaybe() {
    return new DefaultVTaskPath<>(
        VTask.delay(
            () -> {
              try {
                return Maybe.just(this.unsafeRun());
              } catch (Throwable t) {
                return Maybe.nothing();
              }
            }));
  }

  @Override
  public VTaskPath<Try<A>> asTry() {
    return new DefaultVTaskPath<>(VTask.delay(() -> this.runSafe()));
  }

  // ===== Error Transformation =====

  @Override
  public VTaskPath<A> mapError(Function<? super Throwable, ? extends Throwable> f) {
    Objects.requireNonNull(f, "f must not be null");
    return new DefaultVTaskPath<>(value.mapError(f));
  }

  // ===== Resource Safety =====

  @Override
  public VTaskPath<A> guarantee(Runnable finalizer) {
    Objects.requireNonNull(finalizer, "finalizer must not be null");
    return new DefaultVTaskPath<>(
        VTask.delay(
            () -> {
              try {
                return this.unsafeRun();
              } finally {
                finalizer.run();
              }
            }));
  }

  // ===== Parallel Combinators =====

  @Override
  public <B, C> VTaskPath<C> parZipWith(
      VTaskPath<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");
    // VTaskPath.zipWith already uses Par.map2 for parallel execution.
    return zipWith(other, combiner);
  }

  @Override
  public VTaskPath<A> race(VTaskPath<A> other) {
    Objects.requireNonNull(other, "other must not be null");
    return new DefaultVTaskPath<>(Par.race(List.of(this.run(), other.run())));
  }

  // ===== Conversion Methods =====

  @Override
  public TryPath<A> toTryPath() {
    return new TryPath<>(runSafe());
  }

  @Override
  public IOPath<A> toIOPath() {
    return new IOPath<>(IO.delay(this::unsafeRun));
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    // VTask equality is based on reference since VTask represents a computation
    return this == obj;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String toString() {
    return "VTaskPath(<deferred>)";
  }
}
