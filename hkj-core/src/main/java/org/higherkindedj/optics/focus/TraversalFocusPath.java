// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.jspecify.annotations.NullMarked;

/**
 * Implementation of {@link TraversalPath} backed by a {@link Traversal}.
 *
 * <p>This class is package-private and should be created via {@link TraversalPath#of(Traversal)}.
 *
 * @param <S> the source type
 * @param <A> the focused type
 */
@NullMarked
record TraversalFocusPath<S, A>(Traversal<S, A> traversal) implements TraversalPath<S, A> {

  TraversalFocusPath {
    Objects.requireNonNull(traversal, "traversal must not be null");
  }

  @Override
  public List<A> getAll(S source) {
    return Traversals.getAll(traversal, source);
  }

  @Override
  public S setAll(A value, S source) {
    return Traversals.modify(traversal, _ -> value, source);
  }

  @Override
  public S modifyAll(Function<A, A> f, S source) {
    return Traversals.modify(traversal, f, source);
  }

  @Override
  public TraversalPath<S, A> filter(Predicate<A> predicate) {
    return new TraversalFocusPath<>(traversal.filtered(predicate));
  }

  // ===== Composition Methods =====

  @Override
  public <B> TraversalPath<S, B> via(Lens<A, B> lens) {
    return new TraversalFocusPath<>(traversal.andThen(lens));
  }

  @Override
  public <B> TraversalPath<S, B> via(Prism<A, B> prism) {
    return new TraversalFocusPath<>(traversal.andThen(prism));
  }

  @Override
  public <B> TraversalPath<S, B> via(Affine<A, B> affine) {
    // Traversal >>> Affine requires going through traversal composition
    return new TraversalFocusPath<>(traversal.andThen(affine.asTraversal()));
  }

  @Override
  public <B> TraversalPath<S, B> via(Traversal<A, B> other) {
    return new TraversalFocusPath<>(traversal.andThen(other));
  }

  @Override
  public <B> TraversalPath<S, B> via(Iso<A, B> iso) {
    // Compose via lens view of the iso
    Lens<A, B> lensView = Lens.of(iso::get, (a, b) -> iso.reverseGet(b));
    return new TraversalFocusPath<>(traversal.andThen(lensView));
  }

  // ===== Conversion =====

  @Override
  public Traversal<S, A> toTraversal() {
    return traversal;
  }
}
