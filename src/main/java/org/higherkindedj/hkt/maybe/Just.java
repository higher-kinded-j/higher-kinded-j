// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Concrete implementation of Maybe representing the presence of a value. */
// Value must be NonNull because Maybe.just requires it
record Just<T extends @NonNull Object>(@NonNull T value) implements Maybe<T> {
  // Constructor implicitly checks value is non-null via Maybe.just factory

  @Override
  public boolean isJust() {
    return true;
  }

  @Override
  public boolean isNothing() {
    return false;
  }

  @Override
  public @NonNull T get() {
    return value;
  } // Value is guaranteed non-null

  @Override
  public @NonNull T orElse(@NonNull T other) {
    return value;
  }

  @Override
  public @NonNull T orElseGet(@NonNull Supplier<? extends @NonNull T> other) {
    return value;
  }

  @Override
  public @NonNull <U> Maybe<U> map(@NonNull Function<? super T, ? extends @Nullable U> mapper) {
    Objects.requireNonNull(mapper, "mapper function cannot be null");
    // Use fromNullable to handle cases where the mapper might return null
    return Maybe.fromNullable(mapper.apply(value)); // Result of apply is Nullable
  }

  @Override
  public @NonNull <U> Maybe<U> flatMap(
      @NonNull Function<? super T, ? extends @NonNull Maybe<? extends U>> mapper) {
    Objects.requireNonNull(mapper, "mapper function cannot be null");
    Maybe<? extends U> result =
        mapper.apply(value); // apply expects NonNull T, returns NonNull Maybe
    // The flatMap function itself must not return null
    Objects.requireNonNull(result, "flatMap mapper returned null Maybe");
    // Cast needed because of <? extends U> - unavoidable Java type system limitation
    @SuppressWarnings("unchecked")
    Maybe<U> typedResult = (Maybe<U>) result;
    return typedResult;
  }

  @Override
  public @NonNull String toString() {
    return "Just(" + value + ")";
  }
}
