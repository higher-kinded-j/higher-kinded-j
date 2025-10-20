// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/** Concrete implementation of Maybe representing the absence of a value (singleton). */
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

  @Override
  public boolean isJust() {
    return false;
  }

  @Override
  public boolean isNothing() {
    return true;
  }

  // get() throws, return type annotation isn't critical but technically should match Maybe<T>
  @Override
  public T get() {
    throw new NoSuchElementException("Cannot call get() on Nothing");
  }

  @Override
  public T orElse(T other) {
    return other;
  }

  @Override
  public T orElseGet(Supplier<? extends T> other) {
    Validation.function().requireFunction(other, "otherSupplier", MAYBE_CLASS, OR_ELSE_GET);
    return other.get(); // Supplier must return NonNull T
  }

  @Override
  public <U> Maybe<U> map(Function<? super T, ? extends @Nullable U> mapper) {
    Validation.function().requireMapper(mapper, "mapper", MAYBE_CLASS, MAP);
    return instance(); // Mapping Nothing always results in Nothing
  }

  @Override
  public <U> Maybe<U> flatMap(Function<? super T, ? extends Maybe<? extends U>> mapper) {
    Validation.function().requireFlatMapper(mapper, "mapper", MAYBE_CLASS, FLAT_MAP);
    return instance(); // FlatMapping Nothing always results in Nothing
  }

  @Override
  public String toString() {
    return "Nothing";
  }
}
