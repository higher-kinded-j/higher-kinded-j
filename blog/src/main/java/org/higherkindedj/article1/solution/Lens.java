// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article1.solution;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A Lens focuses on exactly one value within a larger structure.
 *
 * <p>A lens provides two operations:
 *
 * <ul>
 *   <li>{@code get} - extract the focused value
 *   <li>{@code set} - return a new structure with the focused value replaced
 * </ul>
 *
 * <p>This implementation demonstrates the core optics concepts. With higher-kinded-j (when Maven
 * Central is accessible), you would use {@code @GenerateLenses} to auto-generate these.
 *
 * @param <S> the type of the whole structure
 * @param <A> the type of the focused part
 */
public record Lens<S, A>(Function<S, A> get, BiFunction<A, S, S> set) {

  /** Create a lens from getter and setter functions. */
  public static <S, A> Lens<S, A> of(Function<S, A> getter, BiFunction<A, S, S> setter) {
    return new Lens<>(getter, setter);
  }

  /** Get the focused value from the structure. */
  public A get(S whole) {
    return get.apply(whole);
  }

  /** Set a new value, returning an updated structure. */
  public S set(A newValue, S whole) {
    return set.apply(newValue, whole);
  }

  /** Modify the focused value using a function. */
  public S modify(Function<A, A> f, S whole) {
    return set(f.apply(get(whole)), whole);
  }

  /**
   * Compose this lens with another to focus deeper into a structure.
   *
   * <p>This is the key insight of optics: access paths compose.
   *
   * @param other a lens from A to B
   * @return a lens from S to B (going through A)
   */
  public <B> Lens<S, B> andThen(Lens<A, B> other) {
    return Lens.of(s -> other.get(this.get(s)), (b, s) -> this.set(other.set(b, this.get(s)), s));
  }
}
