package org.simulation.hkt.reader;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents a computation that depends on a read-only environment R to produce a value A.
 * Essentially a wrapper around Function<R, A>.
 *
 * @param <R> The type of the environment (read-only context).
 * @param <A> The type of the value produced.
 */
@FunctionalInterface
public interface Reader<R, A> {

  /**
   * Runs the computation with the given environment.
   *
   * @param r The environment value. (@NonNull typically, but depends on R)
   * @return The computed value. (@Nullable depends on A)
   */
  @Nullable A run(@NonNull R r); // Input R NonNull, Output A Nullable

  /** Creates a Reader from a function R -> A. */
  static <R, A> @NonNull Reader<R, A> of(@NonNull Function<R, A> runFunction) {
    Objects.requireNonNull(runFunction, "runFunction cannot be null");
    // Use lambda expression which inherently implements the functional interface
    return runFunction::apply;
  }

  /** Maps the result of this Reader using function f. map(f) is equivalent to r -> f(run(r)). */
  default <B> @NonNull Reader<R, B> map(@NonNull Function<? super A, ? extends B> f) {
    Objects.requireNonNull(f, "mapper function cannot be null");
    // Compose the functions: first run this reader, then apply f
    return (R r) -> f.apply(this.run(r));
  }

  /**
   * Composes this Reader with another function that returns a Reader. flatMap(f) is equivalent to r
   * -> f(run(r)).run(r).
   */
  default <B> @NonNull Reader<R, B> flatMap(
      @NonNull Function<? super A, ? extends Reader<R, ? extends B>> f) {
    Objects.requireNonNull(f, "flatMap mapper function cannot be null");
    return (R r) -> {
      // Run the first reader to get A
      @Nullable A a = this.run(r);
      // Apply f to get the second reader Reader<R, ? extends B>
      Reader<R, ? extends B> readerB = f.apply(a);
      Objects.requireNonNull(readerB, "flatMap function returned null Reader");
      // Run the second reader with the *same* environment r
      return readerB.run(r);
    };
  }

  /** Creates a Reader that ignores the environment and always returns the given value. */
  static <R, A> @NonNull Reader<R, A> constant(@Nullable A value) {
    return r -> value;
  }

  /** Creates a Reader that simply returns the environment itself. */
  static <R> @NonNull Reader<R, R> ask() {
    return r -> r;
  }
}
