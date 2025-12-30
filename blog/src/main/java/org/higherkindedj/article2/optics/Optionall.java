// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.optics;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * An Optional (affine traversal) focuses on at most one value.
 *
 * <p>This is the result of composing a Lens with a Prism, or a Prism with a Lens. The focused value
 * might not exist.
 *
 * <p>Note: Named "Optionall" to avoid conflict with java.util.Optional.
 *
 * @param <S> the type of the whole structure
 * @param <A> the type of the focused part (if it exists)
 */
public record Optionall<S, A>(Function<S, Optional<A>> getOptional, BiFunction<A, S, S> set) {

  /** Create an Optional from getter and setter. */
  public static <S, A> Optionall<S, A> of(
      Function<S, Optional<A>> getOptional, BiFunction<A, S, S> set) {
    return new Optionall<>(getOptional, set);
  }

  /** Try to get the focused value. */
  public Optional<A> getOptional(S whole) {
    return getOptional.apply(whole);
  }

  /** Set a new value, returning updated structure. Only applies if the path exists. */
  public S set(A newValue, S whole) {
    return set.apply(newValue, whole);
  }

  /** Modify the focused value if it exists. */
  public S modify(Function<A, A> f, S whole) {
    return getOptional(whole).map(a -> set(f.apply(a), whole)).orElse(whole);
  }

  /**
   * Compose with a lens.
   *
   * @param lens a lens from A to B
   * @return an Optional from S to B
   */
  public <B> Optionall<S, B> andThen(Lens<A, B> lens) {
    return Optionall.of(
        s -> getOptional(s).map(lens::get), (b, s) -> modify(a -> lens.set(b, a), s));
  }

  /**
   * Convert to a traversal (focusing on zero or one element).
   *
   * @return a traversal that yields at most one element
   */
  public Traversal<S, A> asTraversal() {
    return Traversal.of(
        s -> getOptional(s).map(List::of).orElse(List.of()), (f, s) -> modify(f, s));
  }
}
