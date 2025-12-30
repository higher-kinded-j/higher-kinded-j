// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.optics;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A Prism focuses on one variant of a sum type.
 *
 * <p>Prisms represent "is-a" relationships: a Shape <em>might be a</em> Circle. Unlike lenses, the
 * focused value might not exist (the shape might be a Rectangle instead).
 *
 * <p>This implementation demonstrates the concepts from Article 2. With higher-kinded-j, you would
 * use {@code @GeneratePrisms} to auto-generate these.
 *
 * @param <S> the sum type (e.g., Shape)
 * @param <A> the specific variant (e.g., Circle)
 */
public record Prism<S, A>(Function<S, Optional<A>> getOptional, Function<A, S> build) {

  /** Create a prism from a partial getter and a builder. */
  public static <S, A> Prism<S, A> of(Function<S, Optional<A>> getOptional, Function<A, S> build) {
    return new Prism<>(getOptional, build);
  }

  /**
   * Create a prism using instanceof check and cast.
   *
   * @param clazz the class to match
   * @return a prism that matches instances of the given class
   */
  @SuppressWarnings("unchecked")
  public static <S, A extends S> Prism<S, A> fromClass(Class<A> clazz) {
    return Prism.of(s -> clazz.isInstance(s) ? Optional.of((A) s) : Optional.empty(), a -> a);
  }

  /** Try to extract the variant from the sum type. */
  public Optional<A> getOptional(S whole) {
    return getOptional.apply(whole);
  }

  /** Construct the sum type from the variant. Always succeeds. */
  public S build(A value) {
    return build.apply(value);
  }

  /** Check if this prism matches the given value. */
  public boolean matches(S whole) {
    return getOptional(whole).isPresent();
  }

  /** Modify the variant if it matches, otherwise return unchanged. */
  public S modify(Function<A, A> f, S whole) {
    return getOptional(whole).map(a -> build(f.apply(a))).orElse(whole);
  }

  /**
   * Compose this prism with a lens to create an Optional (affine traversal).
   *
   * @param lens a lens from A to B
   * @return an Optional from S to B
   */
  public <B> Optionall<S, B> andThen(Lens<A, B> lens) {
    return Optionall.of(
        s -> getOptional(s).map(lens::get), (b, s) -> modify(a -> lens.set(b, a), s));
  }

  /**
   * Compose this prism with another prism.
   *
   * @param other a prism from A to B
   * @return a prism from S to B
   */
  public <B> Prism<S, B> andThen(Prism<A, B> other) {
    return Prism.of(s -> getOptional(s).flatMap(other::getOptional), b -> build(other.build(b)));
  }

  /**
   * Compose this prism with a traversal.
   *
   * @param traversal a traversal from A to B
   * @return a traversal from S to B
   */
  public <B> Traversal<S, B> andThen(Traversal<A, B> traversal) {
    return Traversal.of(
        s -> getOptional(s).map(traversal::getAll).orElse(List.of()),
        (f, s) -> modify(a -> traversal.modify(f, a), s));
  }
}
