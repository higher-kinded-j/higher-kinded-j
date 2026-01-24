// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.higherkindedj.hkt.util.validation.Operation.*;

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
 *   <li><b>Memoization:</b> Once evaluated, the result (or exception) is cached for subsequent
 *       calls.
 *   <li><b>Thread-Safe:</b> Evaluation is synchronised to ensure only one execution occurs even in
 *       concurrent scenarios.
 *   <li><b>Exception Handling:</b> If the computation throws an exception, it is cached and
 *       re-thrown on each {@code force()} call.
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
   * @param computation The ThrowableSupplier to evaluate lazily. Must not be null.
   * @param <A> The value type.
   * @return A new Lazy instance. Never null.
   * @throws NullPointerException if computation is null.
   */
  public static <A> Lazy<A> defer(ThrowableSupplier<? extends A> computation) {
    Validation.function().requireFunction(computation, "computation", LAZY_CLASS, DEFER);
    return new Lazy<>(computation);
  }

  /**
   * Creates a Lazy instance already holding a computed value (strict evaluation).
   *
   * <p>Coverage note: The lambda {@code () -> value} is intentionally never invoked. We immediately
   * set {@code evaluated = true} and assign the value directly, bypassing lazy evaluation. The
   * lambda exists only to satisfy the constructor's requirement for a ThrowableSupplier.
   *
   * @param value The already computed value. Can be {@code null}.
   * @param <A> The value type.
   * @return A new Lazy instance holding the pre-computed value. Never null.
   */
  public static <A> Lazy<A> now(@Nullable A value) {
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
   * @return The computed value. Can be {@code null} depending on type {@code A}.
   * @throws Throwable if the lazy computation fails.
   */
  public @Nullable A force() throws Throwable {
    if (!evaluated) {
      synchronized (this) {
        if (!evaluated) {
          try {
            this.value = computation.get();
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
    if (this.exception != null) {
      throw this.exception;
    }
    return this.value;
  }

  /**
   * Creates a new Lazy computation by applying a function to the result of this Lazy computation,
   * maintaining laziness. The mapping function itself should not throw checked exceptions unless
   * they are caught or wrapped. Exceptions from the original Lazy's force() are propagated.
   *
   * @param f The mapping function. Must not be null.
   * @param <B> The result type of the mapping function.
   * @return A new Lazy computation for the mapped value. Never null.
   * @throws NullPointerException if f is null.
   */
  public <B> Lazy<B> map(Function<? super A, ? extends B> f) {
    Validation.function().requireMapper(f, "f", LAZY_CLASS, MAP);
    return Lazy.defer(() -> f.apply(this.force()));
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
    Validation.function().requireFlatMapper(f, "f", LAZY_CLASS, FLAT_MAP);
    return Lazy.defer(
        () -> {
          Lazy<? extends B> nextLazy = f.apply(this.force());
          Validation.function()
              .requireNonNullResult(nextLazy, "f", LAZY_CLASS, FLAT_MAP, LAZY_CLASS);
          return nextLazy.force();
        });
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
