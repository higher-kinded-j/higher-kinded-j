// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Represents a lazy computation that evaluates a {@code ThrowableSupplier<A>} only once and caches
 * the result or exception.
 *
 * @param <A> The type of the value produced.
 */
public final class Lazy<A> {

  private transient volatile boolean evaluated = false;
  private @Nullable A value;
  private @Nullable Throwable exception;
  private final ThrowableSupplier<? extends A> computation;

  private Lazy(ThrowableSupplier<? extends A> computation) {
    this.computation = Objects.requireNonNull(computation, "Lazy computation cannot be null");
  }

  /**
   * Creates a Lazy instance that will evaluate the given ThrowableSupplier when forced.
   *
   * @param computation The ThrowableSupplier to evaluate lazily. (NonNull)
   * @param <A> The value type.
   * @return A new Lazy instance. (NonNull)
   */
  public static <A> Lazy<A> defer(ThrowableSupplier<? extends A> computation) {
    return new Lazy<>(computation);
  }

  /**
   * Creates a Lazy instance already holding a computed value (strict).
   *
   * @param value The already computed value. (Nullable)
   * @param <A> The value type.
   * @return A new Lazy instance holding the pre-computed value. (NonNull)
   */
  public static <A> Lazy<A> now(@Nullable A value) {
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
   * @return The computed value. (Nullable depends on A)
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
   * @param f The mapping function. (NonNull)
   * @param <B> The result type of the mapping function.
   * @return A new Lazy computation for the mapped value. (NonNull)
   */
  public <B> Lazy<B> map(Function<? super A, ? extends B> f) {
    Objects.requireNonNull(f, "mapper function cannot be null");
    return Lazy.defer(() -> f.apply(this.force()));
  }

  /**
   * Creates a new Lazy computation by applying a Lazy-returning function to the result of this Lazy
   * computation, maintaining laziness. The mapping function itself should not throw checked
   * exceptions unless they are caught or wrapped. Exceptions from force() calls are propagated.
   *
   * @param f The function returning a new Lazy computation. (NonNull, returns NonNull Lazy)
   * @param <B> The value type of the returned Lazy computation.
   * @return A new Lazy computation representing the sequenced operation. (NonNull)
   */
  public <B> Lazy<B> flatMap(Function<? super A, ? extends Lazy<? extends B>> f) {
    Objects.requireNonNull(f, "flatMap mapper function cannot be null");
    return Lazy.defer(
        () -> {
          Lazy<? extends B> nextLazy = f.apply(this.force());
          Objects.requireNonNull(nextLazy, "flatMap function returned null Lazy");
          return nextLazy.force();
        });
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
