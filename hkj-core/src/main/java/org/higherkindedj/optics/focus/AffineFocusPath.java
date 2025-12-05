// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import java.util.Objects;
import java.util.Optional;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NullMarked;

/**
 * Implementation of {@link AffinePath} backed by an {@link Affine}.
 *
 * <p>This class is package-private and should be created via {@link AffinePath#of(Affine)}.
 *
 * @param <S> the source type
 * @param <A> the focused type
 */
@NullMarked
record AffineFocusPath<S, A>(Affine<S, A> affine) implements AffinePath<S, A> {

  AffineFocusPath {
    Objects.requireNonNull(affine, "affine must not be null");
  }

  @Override
  public Optional<A> getOptional(S source) {
    return affine.getOptional(source);
  }

  @Override
  public S set(A value, S source) {
    return affine.set(value, source);
  }

  // ===== Composition Methods =====

  @Override
  public <B> AffinePath<S, B> via(Lens<A, B> lens) {
    return new AffineFocusPath<>(affine.andThen(lens));
  }

  @Override
  public <B> AffinePath<S, B> via(Prism<A, B> prism) {
    return new AffineFocusPath<>(affine.andThen(prism));
  }

  @Override
  public <B> AffinePath<S, B> via(Affine<A, B> other) {
    return new AffineFocusPath<>(affine.andThen(other));
  }

  @Override
  public <B> AffinePath<S, B> via(Iso<A, B> iso) {
    return new AffineFocusPath<>(affine.andThen(iso));
  }

  @Override
  public <B> TraversalPath<S, B> via(Traversal<A, B> traversal) {
    return new TraversalFocusPath<>(affine.andThen(traversal));
  }

  // ===== Conversion =====

  @Override
  public Affine<S, A> toAffine() {
    return affine;
  }
}
