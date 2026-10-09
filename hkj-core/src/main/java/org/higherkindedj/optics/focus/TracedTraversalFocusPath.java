// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A traced implementation of {@link TraversalPath} that invokes an observer during get operations.
 *
 * <p>This class wraps an underlying TraversalPath and adds tracing behaviour to the {@link
 * #getAll(Object)} method, and to the queries {@code preview}, {@code count}, {@code isEmpty},
 * {@code exists}, {@code all} and {@code find}, which read through it so the observer sees every
 * focus. Those queries give an untraced path's answers, but collect every focus first rather than
 * stopping at their answer. The observer is not invoked during modify operations or {@code
 * foldMap}.
 *
 * @param <S> the source type
 * @param <A> the focused type
 */
@NullMarked
record TracedTraversalFocusPath<S extends @Nullable Object, A extends @Nullable Object>(
    TraversalPath<S, A> underlying, BiConsumer<S, List<A>> observer)
    implements TraversalPath<S, A> {

  TracedTraversalFocusPath {
    Objects.requireNonNull(underlying, "underlying must not be null");
    Objects.requireNonNull(observer, "observer must not be null");
  }

  @Override
  public List<String> segments() {
    return underlying.segments();
  }

  @Override
  public List<A> getAll(S source) {
    List<A> result = underlying.getAll(source);
    observer.accept(source, result);
    return result;
  }

  @Override
  public Optional<@NonNull A> preview(S source) {
    return observed().preview(source);
  }

  @Override
  public int count(S source) {
    return observed().length(source);
  }

  @Override
  public boolean isEmpty(S source) {
    return observed().isEmpty(source);
  }

  @Override
  public boolean exists(Predicate<A> predicate, S source) {
    return observed().exists(predicate, source);
  }

  @Override
  public boolean all(Predicate<A> predicate, S source) {
    return observed().all(predicate, source);
  }

  @Override
  public Optional<@NonNull A> find(Predicate<A> predicate, S source) {
    return observed().find(predicate, source);
  }

  /**
   * A fold over the foci {@link #getAll} hands the observer, so a query sees what a read sees.
   *
   * @return a {@link Fold} that reads through this path's traced {@code getAll}
   */
  private Fold<S, A> observed() {
    return new Fold<>() {
      @Override
      public <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source) {
        M result = monoid.empty();
        for (A a : TracedTraversalFocusPath.this.getAll(source)) {
          result = monoid.combine(result, f.apply(a));
        }
        return result;
      }
    };
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
  public <B extends @Nullable Object> TraversalPath<S, B> via(Lens<A, B> lens) {
    return underlying.via(lens);
  }

  @Override
  public <B extends @Nullable Object> TraversalPath<S, B> via(Prism<A, B> prism) {
    return underlying.via(prism);
  }

  @Override
  public <B extends @Nullable Object> TraversalPath<S, B> via(Affine<A, B> affine) {
    return underlying.via(affine);
  }

  @Override
  public <B extends @Nullable Object> TraversalPath<S, B> via(Traversal<A, B> traversal) {
    return underlying.via(traversal);
  }

  @Override
  public <B extends @Nullable Object> TraversalPath<S, B> via(Iso<A, B> iso) {
    return underlying.via(iso);
  }

  @Override
  public Traversal<S, A> toTraversal() {
    return underlying.toTraversal();
  }
}
