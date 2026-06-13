// Copyright (c) 2025 - 2026 Magnus Smith
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
 * <p>As part of the HKT pattern, {@link Maybe} extends {@link MaybeKind}, so this type is already a
 * {@code Kind<MaybeKind.Witness, T>} and can be used with typeclasses expecting it.
 */
// Value must be NonNull because Maybe.just requires it
record Just<T>(T value) implements Maybe<T> {
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
    Validation.function().require(mapper, "mapper", MAP);
    // Use fromNullable to handle cases where the mapper might return null
    return Maybe.fromNullable(mapper.apply(value)); // Result of apply is Nullable
  }

  @Override
  public <U> Maybe<U> flatMap(Function<? super T, ? extends Maybe<? extends U>> mapper) {
    Validation.function().require(mapper, "mapper", FLAT_MAP);

    Maybe<? extends U> result = mapper.apply(value);
    Validation.function().requireNonNullResult(result, "mapper", FLAT_MAP);

    return covary(result);
  }

  /**
   * Reinterprets a {@code Maybe<? extends U>} as a {@code Maybe<U>}.
   *
   * <p>Safe: {@code Maybe} is sealed and immutable, so a value produced as {@code Maybe<? extends
   * U>} can be observed as {@code Maybe<U>} without risk — there is no operation through which the
   * narrowed element type could be written back.
   */
  @SuppressWarnings("unchecked") // sealed + immutable: covariant reinterpretation is unobservable
  private static <U> Maybe<U> covary(Maybe<? extends U> m) {
    return (Maybe<U>) m;
  }

  @Override
  public String toString() {
    return "Just(" + value + ")";
  }
}
