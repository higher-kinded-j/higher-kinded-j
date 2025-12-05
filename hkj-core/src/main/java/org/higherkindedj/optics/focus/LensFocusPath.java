// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import java.util.Objects;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NullMarked;

/**
 * Implementation of {@link FocusPath} backed by a {@link Lens}.
 *
 * <p>This class is package-private and should be created via {@link FocusPath#of(Lens)}.
 *
 * @param <S> the source type
 * @param <A> the focused type
 */
@NullMarked
record LensFocusPath<S, A>(Lens<S, A> lens) implements FocusPath<S, A> {

  LensFocusPath {
    Objects.requireNonNull(lens, "lens must not be null");
  }

  @Override
  public A get(S source) {
    return lens.get(source);
  }

  @Override
  public S set(A value, S source) {
    return lens.set(value, source);
  }

  // ===== Composition Methods =====

  @Override
  public <B> FocusPath<S, B> via(Lens<A, B> other) {
    return new LensFocusPath<>(lens.andThen(other));
  }

  @Override
  public <B> FocusPath<S, B> via(Iso<A, B> iso) {
    return new LensFocusPath<>(lens.andThen(iso));
  }

  @Override
  public <B> AffinePath<S, B> via(Prism<A, B> prism) {
    return new AffineFocusPath<>(lens.andThen(prism));
  }

  @Override
  public <B> AffinePath<S, B> via(Affine<A, B> affine) {
    return new AffineFocusPath<>(lens.andThen(affine));
  }

  @Override
  public <B> TraversalPath<S, B> via(Traversal<A, B> traversal) {
    return new TraversalFocusPath<>(lens.andThen(traversal));
  }

  // ===== Conversion =====

  @Override
  public Lens<S, A> toLens() {
    return lens;
  }
}
