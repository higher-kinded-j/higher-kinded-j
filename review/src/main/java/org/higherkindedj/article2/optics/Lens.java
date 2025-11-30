// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.optics;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A Lens focuses on exactly one value within a larger structure.
 *
 * <p>Lenses represent "has-a" relationships: an Employee <em>has an</em> Address; an Address
 * <em>has a</em> street. The focused value is guaranteed to exist.
 *
 * <p>This implementation demonstrates the concepts from Article 2. With higher-kinded-j, you would
 * use {@code @GenerateLenses} to auto-generate these.
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
   * Compose this lens with another lens to focus deeper.
   *
   * @param other a lens from A to B
   * @return a lens from S to B
   */
  public <B> Lens<S, B> andThen(Lens<A, B> other) {
    return Lens.of(s -> other.get(this.get(s)), (b, s) -> this.set(other.set(b, this.get(s)), s));
  }

  /**
   * Compose this lens with a prism to create an Optional (affine traversal).
   *
   * @param prism a prism from A to B
   * @return an Optional from S to B
   */
  public <B> Optionall<S, B> andThen(Prism<A, B> prism) {
    return Optionall.of(
        s -> prism.getOptional(this.get(s)),
        (b, s) -> this.modify(a -> prism.modify(__ -> b, a), s));
  }

  /**
   * Compose this lens with a traversal.
   *
   * @param traversal a traversal from A to B
   * @return a traversal from S to B
   */
  public <B> Traversal<S, B> andThen(Traversal<A, B> traversal) {
    return Traversal.of(
        s -> traversal.getAll(this.get(s)), (f, s) -> this.modify(a -> traversal.modify(f, a), s));
  }
}
