package org.simulation.hkt.maybe;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Concrete implementation of Maybe representing the presence of a value.
 */
record Just<T>(T value) implements Maybe<T> {
  // Constructor implicitly checks value is non-null via Maybe.just factory

  @Override public boolean isJust() { return true; }
  @Override public boolean isNothing() { return false; }
  @Override public T get() { return value; } // Value is guaranteed non-null
  @Override public T orElse(T other) { return value; }
  @Override public T orElseGet(Supplier<? extends T> other) { return value; }

  @Override public <U> Maybe<U> map(Function<? super T, ? extends U> mapper) {
    Objects.requireNonNull(mapper, "mapper function cannot be null");
    // Use fromNullable to handle cases where the mapper might return null
    return Maybe.fromNullable(mapper.apply(value));
  }

  @Override public <U> Maybe<U> flatMap(Function<? super T, ? extends Maybe<? extends U>> mapper) {
    Objects.requireNonNull(mapper, "mapper function cannot be null");
    Maybe<? extends U> result = mapper.apply(value);
    // The flatMap function itself must not return null
    Objects.requireNonNull(result, "flatMap mapper returned null Maybe");
    // Cast needed because of <? extends U> - unavoidable Java type system limitation
    @SuppressWarnings("unchecked")
    Maybe<U> typedResult = (Maybe<U>) result;
    return typedResult;
  }

  @Override public String toString() {
    return "Just(" + value + ")";
  }
}