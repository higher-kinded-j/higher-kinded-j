package org.simulation.hkt.lazy;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents a lazy computation that evaluates a Supplier<A> only once and caches the result (or
 * exception).
 *
 * @param <A> The type of the value produced.
 */
public final class Lazy<A> {

  // Using Supplier<A> directly and synchronizing access for basic thread safety.
  private transient volatile boolean evaluated = false;
  private @Nullable A value; // Stores the computed value (can be null)
  private @Nullable Throwable exception; // Field to store exception if computation fails
  private final @NonNull Supplier<? extends A> computation;

  private Lazy(@NonNull Supplier<? extends A> computation) {
    this.computation = Objects.requireNonNull(computation, "Lazy computation cannot be null");
  }

  /**
   * Creates a Lazy instance that will evaluate the given Supplier when forced.
   *
   * @param computation The Supplier to evaluate lazily. (NonNull)
   * @param <A> The value type.
   * @return A new Lazy instance. (NonNull)
   */
  public static <A> @NonNull Lazy<A> defer(@NonNull Supplier<? extends A> computation) {
    return new Lazy<>(computation);
  }

  /**
   * Creates a Lazy instance already holding a computed value (strict).
   *
   * @param value The already computed value. (Nullable)
   * @param <A> The value type.
   * @return A new Lazy instance holding the pre-computed value. (NonNull)
   */
  public static <A> @NonNull Lazy<A> now(@Nullable A value) {
    // Create a Lazy that's already evaluated.
    Lazy<A> lazy = new Lazy<>(() -> value); // Supplier returns the known value
    lazy.value = value;
    lazy.exception = null; // Ensure exception is null for 'now'
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
  public @Nullable A force() {
    // Double-checked locking idiom (basic version) for memoization
    if (!evaluated) {
      synchronized (this) {
        if (!evaluated) {
          try {
            this.value = computation.get(); // Evaluate the supplier
            this.exception = null; // Clear exception field on success
          } catch (Throwable t) {
            this.exception = t; // Store the exception
            this.value = null; // Ensure value is null on failure
          } finally {
            // IMPORTANT: Mark as evaluated regardless of success or failure
            this.evaluated = true;
          }
        }
      }
    }

    // After evaluation attempt (or if already evaluated)
    if (this.exception != null) {
      // Re-throw the cached exception
      // Handle RuntimeException and Error directly
      if (this.exception instanceof RuntimeException re) throw re;
      if (this.exception instanceof Error err) throw err;
      // Wrap checked exceptions
      throw new RuntimeException(
          "Lazy computation failed with checked exception", this.exception);
    }
    // If no exception was cached, return the value (which might be null)
    return this.value;
  }

  /**
   * Creates a new Lazy computation by applying a function to the result of this Lazy computation,
   * maintaining laziness.
   *
   * @param f The mapping function. (NonNull)
   * @param <B> The result type of the mapping function.
   * @return A new Lazy computation for the mapped value. (NonNull)
   */
  public <B> @NonNull Lazy<B> map(@NonNull Function<? super A, ? extends B> f) {
    Objects.requireNonNull(f, "mapper function cannot be null");
    // Defer the mapping: force this Lazy, then apply f
    return Lazy.defer(() -> f.apply(this.force()));
  }

  /**
   * Creates a new Lazy computation by applying a Lazy-returning function to the result of this Lazy
   * computation, maintaining laziness.
   *
   * @param f The function returning a new Lazy computation. (NonNull, returns NonNull Lazy)
   * @param <B> The value type of the returned Lazy computation.
   * @return A new Lazy computation representing the sequenced operation. (NonNull)
   */
  public <B> @NonNull Lazy<B> flatMap(
      @NonNull Function<? super A, ? extends @NonNull Lazy<? extends B>> f) {
    Objects.requireNonNull(f, "flatMap mapper function cannot be null");
    // Defer the flatMap: force this Lazy, apply f to get the next Lazy, then force the next Lazy.
    return Lazy.defer(
        () -> {
          Lazy<? extends B> nextLazy = f.apply(this.force());
          Objects.requireNonNull(nextLazy, "flatMap function returned null Lazy");
          return nextLazy.force();
        });
  }

  @Override
  public String toString() {
    // Avoid forcing evaluation in toString, show status instead
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