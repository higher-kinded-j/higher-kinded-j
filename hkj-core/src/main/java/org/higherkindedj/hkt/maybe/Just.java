// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.util.validation.Operation.FLAT_MAP;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Concrete implementation of Maybe representing the presence of a value.
 *
 * <p>As part of the HKT pattern, this class implements {@link MaybeKind}, allowing it to be used
 * with typeclasses expecting {@code Kind<MaybeKind.Witness, T>}.
 */
// Value must be NonNull because Maybe.just requires it
record Just<T>(T value) implements Maybe<T>, MaybeKind<T> {
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
  public T get() {
    return value;
  } // Value is guaranteed non-null

  @Override
  public T orElse(T other) {
    return value;
  }

  @Override
  public T orElseGet(Supplier<? extends T> other) {
    return value;
  }

  @Override
  public <U> Maybe<U> map(Function<? super T, ? extends @Nullable U> mapper) {
    Validation.function().requireMapper(mapper, "mapper", Just.class, MAP);
    // Use fromNullable to handle cases where the mapper might return null
    return Maybe.fromNullable(mapper.apply(value)); // Result of apply is Nullable
  }

  @Override
  public <U> Maybe<U> flatMap(Function<? super T, ? extends Maybe<? extends U>> mapper) {
    Validation.function().requireFlatMapper(mapper, "mapper", Just.class, FLAT_MAP);

    Maybe<? extends U> result = mapper.apply(value);
    Validation.function().requireNonNullResult(result, "mapper", Just.class, FLAT_MAP, Maybe.class);

    // Cast needed because of <? extends U> - unavoidable Java type system limitation
    @SuppressWarnings("unchecked")
    Maybe<U> typedResult = (Maybe<U>) result;
    return typedResult;
  }

  @Override
  public String toString() {
    return "Just(" + value + ")";
  }
}
