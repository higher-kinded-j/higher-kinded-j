// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NullMarked;

/**
 * A traced implementation of {@link TraversalPath} that invokes an observer during get operations.
 *
 * <p>This class wraps an underlying TraversalPath and adds tracing behavior to the {@link
 * #getAll(Object)} method. The observer is only invoked during get operations, not during modify
 * operations.
 *
 * @param <S> the source type
 * @param <A> the focused type
 */
@NullMarked
record TracedTraversalFocusPath<S, A>(
    TraversalPath<S, A> underlying, BiConsumer<S, List<A>> observer)
    implements TraversalPath<S, A> {

  TracedTraversalFocusPath {
    Objects.requireNonNull(underlying, "underlying must not be null");
    Objects.requireNonNull(observer, "observer must not be null");
  }

  @Override
  public List<A> getAll(S source) {
    List<A> result = underlying.getAll(source);
    observer.accept(source, result);
    return result;
  }

  @Override
  public S setAll(A value, S source) {
    return underlying.setAll(value, source);
  }

  @Override
  public S modifyAll(Function<A, A> f, S source) {
    return underlying.modifyAll(f, source);
  }

  @Override
  public TraversalPath<S, A> filter(Predicate<A> predicate) {
    return underlying.filter(predicate);
  }

  @Override
  public <B> TraversalPath<S, B> via(Lens<A, B> lens) {
    return underlying.via(lens);
  }

  @Override
  public <B> TraversalPath<S, B> via(Prism<A, B> prism) {
    return underlying.via(prism);
  }

  @Override
  public <B> TraversalPath<S, B> via(Affine<A, B> affine) {
    return underlying.via(affine);
  }

  @Override
  public <B> TraversalPath<S, B> via(Traversal<A, B> traversal) {
    return underlying.via(traversal);
  }

  @Override
  public <B> TraversalPath<S, B> via(Iso<A, B> iso) {
    return underlying.via(iso);
  }

  @Override
  public Traversal<S, A> toTraversal() {
    return underlying.toTraversal();
  }
}
