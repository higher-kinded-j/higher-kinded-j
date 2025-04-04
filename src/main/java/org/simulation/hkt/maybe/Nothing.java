package org.simulation.hkt.maybe;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Concrete implementation of Maybe representing the absence of a value (singleton).
 */
final class Nothing<T> implements Maybe<T> {
  // Singleton instance
  private static final Nothing<?> INSTANCE = new Nothing<>();

  // Private constructor to enforce singleton pattern
  private Nothing() {}

  /** Factory method to get the singleton instance */
  @SuppressWarnings("unchecked")
  static <T> Nothing<T> instance() {
    return (Nothing<T>) INSTANCE;
  }

  @Override public boolean isJust() { return false; }
  @Override public boolean isNothing() { return true; }
  @Override public T get() { throw new NoSuchElementException("Cannot call get() on Nothing"); }
  @Override public T orElse(T other) { return other; }

  @Override public T orElseGet(Supplier<? extends T> other) {
    Objects.requireNonNull(other, "orElseGet supplier cannot be null");
    return other.get();
  }

  @Override public <U> Maybe<U> map(Function<? super T, ? extends U> mapper) {
    Objects.requireNonNull(mapper, "mapper function cannot be null");
    return instance(); // Mapping Nothing always results in Nothing
  }

  @Override public <U> Maybe<U> flatMap(Function<? super T, ? extends Maybe<? extends U>> mapper) {
    Objects.requireNonNull(mapper, "mapper function cannot be null");
    return instance(); // FlatMapping Nothing always results in Nothing
  }

  @Override public String toString() {
    return "Nothing";
  }

  // Optional: Ensure proper singleton behavior during serialization
  private Object readResolve() {
    return INSTANCE;
  }
}