// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Implementation of {@link AffinePath} backed by an {@link Affine}.
 *
 * <p>This class is package-private and should be created via {@link AffinePath#of(Affine)}.
 *
 * @param <S> the source type
 * @param <A> the focused type
 */
@NullMarked
record AffineFocusPath<S extends @Nullable Object, A extends @Nullable Object>(
    Affine<S, A> affine, List<String> segments) implements AffinePath<S, A> {

  AffineFocusPath {
    Objects.requireNonNull(affine, "affine must not be null");
    Objects.requireNonNull(segments, "segments must not be null");
    segments = List.copyOf(segments);
  }

  @Override
  public Optional<@NonNull A> getOptional(S source) {
    return affine.getOptional(source);
  }

  @Override
  public S set(A value, S source) {
    return affine.set(value, source);
  }

  // ===== Composition Methods =====

  @Override
  public <B extends @Nullable Object> AffinePath<S, B> via(Lens<A, B> lens) {
    return new AffineFocusPath<>(affine.andThen(lens), segments);
  }

  @Override
  public <B extends @Nullable Object> AffinePath<S, B> via(Prism<A, B> prism) {
    return new AffineFocusPath<>(affine.andThen(prism), segments);
  }

  @Override
  public <B extends @Nullable Object> AffinePath<S, B> via(Affine<A, B> other) {
    return new AffineFocusPath<>(affine.andThen(other), segments);
  }

  @Override
  public <B extends @Nullable Object> AffinePath<S, B> via(Iso<A, B> iso) {
    return new AffineFocusPath<>(affine.andThen(iso), segments);
  }

  @Override
  public <B extends @Nullable Object> TraversalPath<S, B> via(Traversal<A, B> traversal) {
    return new TraversalFocusPath<>(affine.andThen(traversal), segments);
  }

  // ===== Conversion =====

  @Override
  public Affine<S, A> toAffine() {
    return affine;
  }
}
