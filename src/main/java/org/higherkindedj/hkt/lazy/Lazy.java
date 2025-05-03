package org.higherkindedj.hkt.lazy;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents a lazy computation that evaluates a {@code ThrowableSupplier<A>} only once and caches the
 * result or exception.
 *
 * @param <A> The type of the value produced.
 */
public final class Lazy<A> {

  private transient volatile boolean evaluated = false;
  private @Nullable A value;
  private @Nullable Throwable exception; // Field to store exception if computation fails
  // Use the new interface that allows throwing Throwable
  private final @NonNull ThrowableSupplier<? extends A> computation;

  // Constructor accepts ThrowableSupplier
  private Lazy(@NonNull ThrowableSupplier<? extends A> computation) {
    this.computation = Objects.requireNonNull(computation, "Lazy computation cannot be null");
  }

  /**
   * Creates a Lazy instance that will evaluate the given ThrowableSupplier when forced.
   *
   * @param computation The ThrowableSupplier to evaluate lazily. (NonNull)
   * @param <A> The value type.
   * @return A new Lazy instance. (NonNull)
   */
  public static <A> @NonNull Lazy<A> defer(@NonNull ThrowableSupplier<? extends A> computation) {
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
    // The supplier here won't throw, so using a lambda is fine.
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
  public @Nullable A force() throws Throwable { // Declare throws Throwable
    if (!evaluated) {
      synchronized (this) {
        if (!evaluated) {
          try {
            // computation.get() can now throw any Throwable directly
            this.value = computation.get();
            this.exception = null;
          } catch (Throwable t) {
            this.exception = t; // Store the caught throwable
            this.value = null;
          } finally {
            this.evaluated = true;
          }
        }
      }
    }

    // After evaluation attempt (or if already evaluated)
    if (this.exception != null) {
      // Re-throw the cached exception. No need to wrap anymore.
      throw this.exception; // Directly throw the cached Throwable (Line 87)
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
  public <B> @NonNull Lazy<B> map(@NonNull Function<? super A, ? extends B> f) {
    Objects.requireNonNull(f, "mapper function cannot be null");
    // Defer the mapping: force this Lazy (may throw), then apply f
    // Use the new ThrowableSupplier for defer
    return Lazy.defer(
        () -> {
          // force() now throws Throwable. It needs to be caught if map should
          // return a failed Lazy instead of throwing immediately when the
          // *mapped* Lazy is forced. However, standard Functor map typically
          // propagates the structure's failure. Here, forcing the mapped
          // Lazy will execute this lambda, which calls this.force(), propagating
          // the original exception if `this` fails. Exceptions from `f.apply`
          // are caught by the outer Lazy.defer mechanism.
          @Nullable A forcedValue = this.force(); // Call force directly
          // If force() succeeded (didn't throw), apply the function
          return f.apply(forcedValue);
        });
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
  public <B> @NonNull Lazy<B> flatMap(
      @NonNull Function<? super A, ? extends @NonNull Lazy<? extends B>> f) {
    Objects.requireNonNull(f, "flatMap mapper function cannot be null");
    // Defer the flatMap: force this Lazy, apply f, then force the next Lazy.
    // Use the new ThrowableSupplier for defer
    return Lazy.defer(
        () -> {
          // force() throws Throwable. Exceptions will propagate naturally
          // when this deferred computation is forced.
          @Nullable A forcedValue = this.force(); // Force the outer Lazy
          Lazy<? extends B> nextLazy = f.apply(forcedValue); // Apply the function
          Objects.requireNonNull(nextLazy, "flatMap function returned null Lazy");
          // Force the inner lazy, which also throws Throwable
          return nextLazy.force(); // Force the inner Lazy
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
