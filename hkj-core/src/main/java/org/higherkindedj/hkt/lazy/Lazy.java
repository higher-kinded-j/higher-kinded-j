// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Represents a lazy computation that evaluates a {@code ThrowableSupplier<A>} only once and caches
 * the result or exception.
 *
 * <p>Key characteristics:
 *
 * <ul>
 *   <li><b>Lazy Evaluation:</b> The computation is not executed until {@link #force()} is called.
 *   <li><b>Memoisation:</b> Once evaluated, the result (or exception) is cached for subsequent
 *       calls.
 *   <li><b>Thread-Safe:</b> Evaluation is synchronised to ensure only one execution occurs even in
 *       concurrent scenarios.
 *   <li><b>Exception Handling:</b> If the computation throws an exception, it is cached and
 *       re-thrown on each {@code force()} call.
 *   <li><b>Always a Value:</b> A Lazy never holds {@code null}. A computation or function that
 *       returns {@code null} fails with a {@link NullPointerException} naming it, cached like any
 *       other exception.
 * </ul>
 *
 * @param <A> The type of the value produced.
 */
public final class Lazy<A> {

  private static final Class<Lazy> LAZY_CLASS = Lazy.class;

  private transient volatile boolean evaluated = false;
  private volatile @Nullable A value;
  private volatile @Nullable Throwable exception;
  private final ThrowableSupplier<? extends A> computation;

  private Lazy(ThrowableSupplier<? extends A> computation) {
    this.computation = Validation.coreType().requireValue(computation, LAZY_CLASS, CONSTRUCTION);
  }

  /**
   * Creates a Lazy instance that will evaluate the given ThrowableSupplier when forced.
   *
   * @param computation The ThrowableSupplier to evaluate lazily. Must not be null, and must not
   *     return null; a null result fails the Lazy with a {@link NullPointerException} when forced.
   * @param <A> The value type.
   * @return A new Lazy instance. Never null.
   * @throws NullPointerException if computation is null.
   */
  public static <A> Lazy<A> defer(ThrowableSupplier<? extends A> computation) {
    Validation.function().require(computation, "computation", DEFER);
    return new Lazy<>(computation);
  }

  /**
   * Creates a Lazy instance already holding a computed value (strict evaluation).
   *
   * <p>Coverage note: The lambda {@code () -> value} is intentionally never invoked. We immediately
   * set {@code evaluated = true} and assign the value directly, bypassing lazy evaluation. The
   * lambda exists only to satisfy the constructor's requirement for a ThrowableSupplier.
   *
   * @param value The already computed value. Must not be null.
   * @param <A> The value type.
   * @return A new Lazy instance holding the pre-computed value. Never null.
   * @throws NullPointerException if value is null.
   */
  public static <A> Lazy<A> now(A value) {
    Validation.coreType().requireValue(value, LAZY_CLASS, NOW);
    // The lambda is never invoked - we set evaluated=true immediately below
    Lazy<A> lazy = new Lazy<>(() -> value);
    lazy.value = value;
    lazy.exception = null;
    lazy.evaluated = true;
    return lazy;
  }

  /**
   * Forces the evaluation of the computation if not already evaluated, and returns the result. This
   * evaluation happens only once. If the computation fails, the exception is cached and re-thrown
   * on subsequent calls.
   *
   * @return The computed value. Never null.
   * @throws Throwable if the lazy computation fails, including a {@link NullPointerException} when
   *     it returns null.
   */
  public A force() throws Throwable {
    if (!evaluated) {
      synchronized (this) {
        if (!evaluated) {
          try {
            if (computation instanceof FlatMapComputation<?, ?>) {
              this.value = evaluateFlatMapChain();
            } else {
              this.value =
                  Objects.requireNonNull(computation.get(), "computation must not return null");
            }
            this.exception = null;
          } catch (Throwable t) {
            this.exception = t;
            this.value = null;
          } finally {
            this.evaluated = true;
          }
        }
      }
    }
    Throwable failure = this.exception;
    if (failure != null) {
      throw failure;
    }
    // Evaluated without a failure, so the value is set
    return Objects.requireNonNull(this.value);
  }

  /**
   * Iteratively evaluates a chain of flatMap operations to avoid stack overflow.
   *
   * <p>Each Lazy in the chain caches its own value, or the failure that stops the chain, as the
   * chain reaches it, just as forcing it on its own would. A Lazy in the chain that is already
   * evaluated gives its cached value and is not run again.
   */
  @SuppressWarnings("unchecked") // each step's function takes the value of the Lazy before it
  private A evaluateFlatMapChain() throws Throwable {
    // The steps still to run, the next one on top
    Deque<ChainStep> steps = new ArrayDeque<>();
    try {
      Object result = unwind(this, steps).force();
      while (!steps.isEmpty()) {
        switch (steps.pop()) {
          case ChainStep.Apply(Lazy<?> owner, Function<Object, Lazy<?>> function) -> {
            // The Lazy the function returns gives the owner's value, to cache once it is known
            steps.push(new ChainStep.Cache(owner));
            Lazy<?> next = function.apply(result);
            Validation.function().requireNonNullResult(next, "f", FLAT_MAP);
            result = unwind(next, steps).force();
          }
          case ChainStep.Cache(Lazy<?> owner) -> {
            owner.cache(result, null);
            // Carry on with what the owner holds, which another thread may have cached first
            result = owner.force();
          }
        }
      }
      return (A) result;
    } catch (Throwable t) {
      // Every Lazy still waiting for its value depends on the step that failed
      for (ChainStep step : steps) {
        step.owner().cache(null, t);
      }
      throw t;
    }
  }

  /**
   * Pushes a step for each unevaluated flatMap Lazy from {@code lazy} down to the first that is
   * evaluated or is not a flatMap, and returns that one.
   */
  @SuppressWarnings("unchecked") // a flatMap's function takes the value of its source
  private static Lazy<?> unwind(Lazy<?> lazy, Deque<ChainStep> steps) {
    Lazy<?> current = lazy;
    while (!current.evaluated && current.computation instanceof FlatMapComputation<?, ?> chain) {
      steps.push(
          new ChainStep.Apply(
              current, (Function<Object, Lazy<?>>) (Function<?, ?>) chain.function()));
      current = chain.source();
    }
    return current;
  }

  /**
   * Caches what the flatMap chain worked out for this Lazy, a value or a failure, unless another
   * thread has evaluated it meanwhile.
   */
  @SuppressWarnings("unchecked") // the chain gives this Lazy the value of its own last step
  private synchronized void cache(@Nullable Object result, @Nullable Throwable failure) {
    if (!evaluated) {
      this.value = (A) result;
      this.exception = failure;
      this.evaluated = true;
    }
  }

  /** A step of a flatMap chain that {@link #evaluateFlatMapChain} has still to run. */
  private sealed interface ChainStep {

    /** The Lazy this step works out the value of. */
    Lazy<?> owner();

    /** Applies the owner's flatMap function to the value of the Lazy before it. */
    record Apply(Lazy<?> owner, Function<Object, Lazy<?>> function) implements ChainStep {}

    /** Caches the value just worked out in the owner. */
    record Cache(Lazy<?> owner) implements ChainStep {}
  }

  /**
   * Creates a new Lazy computation by applying a function to the result of this Lazy computation,
   * maintaining laziness. The mapping function itself should not throw checked exceptions unless
   * they are caught or wrapped. Exceptions from the original Lazy's force() are propagated.
   *
   * @param f The mapping function. Must not be null, and must not return null; a null result fails
   *     the Lazy with a {@link NullPointerException} when forced.
   * @param <B> The result type of the mapping function.
   * @return A new Lazy computation for the mapped value. Never null.
   * @throws NullPointerException if f is null.
   */
  public <B> Lazy<B> map(Function<? super A, ? extends B> f) {
    Validation.function().require(f, "f", MAP);
    return Lazy.defer(
        () -> Objects.requireNonNull(f.apply(this.force()), "f must not return null"));
  }

  /**
   * Creates a new Lazy computation by applying a Lazy-returning function to the result of this Lazy
   * computation, maintaining laziness. The mapping function itself should not throw checked
   * exceptions unless they are caught or wrapped. Exceptions from force() calls are propagated.
   *
   * @param f The function returning a new Lazy computation. Must not be null and must not return
   *     null.
   * @param <B> The value type of the returned Lazy computation.
   * @return A new Lazy computation representing the sequenced operation. Never null.
   * @throws NullPointerException if f is null or f returns null.
   */
  public <B> Lazy<B> flatMap(Function<? super A, ? extends Lazy<? extends B>> f) {
    Validation.function().require(f, "f", FLAT_MAP);
    return new Lazy<>(new FlatMapComputation<>(this, f));
  }

  /** Internal marker computation for flatMap chains, enabling iterative evaluation in force(). */
  private record FlatMapComputation<A, B>(
      Lazy<A> source, Function<? super A, ? extends Lazy<? extends B>> function)
      implements ThrowableSupplier<B> {
    @Override
    public B get() throws Throwable {
      // Direct evaluation fallback — not used when force() detects the chain
      Lazy<? extends B> nextLazy = function.apply(source.force());
      Validation.function().requireNonNullResult(nextLazy, "f", FLAT_MAP);
      return nextLazy.force();
    }
  }

  /**
   * Returns whether this Lazy computation has been evaluated.
   *
   * <p>A Lazy is considered evaluated after {@link #force()} has been called at least once,
   * regardless of whether the computation succeeded or threw an exception.
   *
   * @return {@code true} if the computation has been evaluated, {@code false} otherwise.
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  /**
   * Returns whether this Lazy computation has been evaluated and threw an exception.
   *
   * <p>A Lazy is considered failed only after evaluation has occurred and the underlying
   * computation produced a {@link Throwable}. An unevaluated Lazy is not considered failed.
   *
   * @return {@code true} if the computation has been evaluated and resulted in an exception; {@code
   *     false} otherwise (unevaluated, or evaluated successfully).
   */
  public boolean hasFailed() {
    return evaluated && exception != null;
  }

  @Override
  public String toString() {
    if (evaluated) {
      if (exception != null) {
        return "Lazy[failed: " + exception.getClass().getSimpleName() + "]";
      } else {
        return "Lazy[" + value + "]";
      }
    } else {
      return "Lazy[unevaluated...]";
    }
  }
}
