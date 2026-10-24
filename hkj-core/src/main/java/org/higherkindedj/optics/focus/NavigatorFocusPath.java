// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.indexed.Pair;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The base of every navigator {@code @GenerateFocus} generates over a {@link FocusPath}.
 *
 * <p>A navigator is the path it wraps, with a navigation method for each field of the record it
 * focuses on. Every path method is final here and forwards to that path, so a navigator reads,
 * writes, composes and prints exactly as the path does, and a subclass can only add methods. A
 * navigator equals another navigator of the same class over an equal path.
 *
 * <p>Each method here that takes no arguments is a name a field of a record the navigator reaches
 * cannot take, since a navigation method of that name would clash with it. Adding one changes what
 * such records may declare.
 *
 * <p>This class exists for generated code. Write a {@link FocusPath} by composing the paths the
 * generated companions publish, or with {@link FocusPath#of(Lens)}.
 *
 * @param <S> the source type
 * @param <A> the focused type
 * @since 0.5.0
 */
@NullMarked
public abstract non-sealed class NavigatorFocusPath<
        S extends @Nullable Object, A extends @Nullable Object>
    implements FocusPath<S, A> {

  private final FocusPath<S, A> path;

  // Every FocusPath reads and writes through its lens, so the hot methods call it directly and cost
  // what the wrapped path's own do.
  private final Lens<S, A> lens;

  /**
   * Wraps a path.
   *
   * @param path the path this navigator is
   */
  protected NavigatorFocusPath(FocusPath<S, A> path) {
    this.path = Objects.requireNonNull(path, "path must not be null");
    this.lens = path.toLens();
  }

  /**
   * The path this navigator wraps, without its navigation methods.
   *
   * @return the wrapped path
   */
  public final FocusPath<S, A> toPath() {
    return path;
  }

  @Override
  public final List<String> segments() {
    return path.segments();
  }

  @Override
  public final String pathString() {
    return path.pathString();
  }

  @Override
  public final A get(S source) {
    return lens.get(source);
  }

  @Override
  public final S set(A value, S source) {
    return lens.set(value, source);
  }

  @Override
  public final S modify(Function<A, A> f, S source) {
    return lens.set(f.apply(lens.get(source)), source);
  }

  @Override
  public final <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
      Function<A, Kind<F, A>> f, S source, Functor<F> functor) {
    return path.modifyF(f, source, functor);
  }

  @Override
  public final <B extends @Nullable Object> FocusPath<S, B> via(Lens<A, B> lens) {
    return path.via(lens);
  }

  @Override
  public final <B extends @Nullable Object> FocusPath<S, B> via(FocusPath<A, B> other) {
    return path.via(other);
  }

  @Override
  public final <B extends @Nullable Object> AffinePath<S, B> via(AffinePath<A, B> other) {
    return path.via(other);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> via(TraversalPath<A, B> other) {
    return path.via(other);
  }

  @Override
  public final <B extends @Nullable Object> FocusPath<S, B> via(Iso<A, B> iso) {
    return path.via(iso);
  }

  @Override
  public final <B extends @Nullable Object> AffinePath<S, B> via(Prism<A, B> prism) {
    return path.via(prism);
  }

  @Override
  public final <B extends @Nullable Object> AffinePath<S, B> via(Affine<A, B> affine) {
    return path.via(affine);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> via(Traversal<A, B> traversal) {
    return path.via(traversal);
  }

  @Override
  public final <B extends @Nullable Object> FocusPath<S, B> then(Lens<A, B> lens) {
    return path.then(lens);
  }

  @Override
  public final <B extends @Nullable Object> FocusPath<S, B> then(FocusPath<A, B> other) {
    return path.then(other);
  }

  @Override
  public final <B extends @Nullable Object> FocusPath<S, B> then(Iso<A, B> iso) {
    return path.then(iso);
  }

  @Override
  public final <B extends @Nullable Object> AffinePath<S, B> then(Prism<A, B> prism) {
    return path.then(prism);
  }

  @Override
  public final <B extends @Nullable Object> AffinePath<S, B> then(Affine<A, B> affine) {
    return path.then(affine);
  }

  @Override
  public final <B extends @Nullable Object> AffinePath<S, B> then(AffinePath<A, B> other) {
    return path.then(other);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> then(Traversal<A, B> traversal) {
    return path.then(traversal);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> then(TraversalPath<A, B> other) {
    return path.then(other);
  }

  /**
   * @deprecated since 0.5.0, scheduled for removal in 0.6.0. Use {@code
   *     each(EachInstances.listEach())}, which reads its element type from the path. The {@code
   *     MigrateDeprecationsTo0_6_0} OpenRewrite recipe replaces it, or marks a call it cannot.
   */
  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public final <E extends @Nullable Object> TraversalPath<S, E> each() {
    return path.each();
  }

  @Override
  public final <E extends @Nullable Object> TraversalPath<S, E> each(Each<A, E> eachInstance) {
    return path.each(eachInstance);
  }

  /**
   * @deprecated since 0.5.0, scheduled for removal in 0.6.0. Use {@code
   *     via(FocusPaths.listAt(index))}, which reads its element type from the path. The {@code
   *     MigrateDeprecationsTo0_6_0} OpenRewrite recipe replaces it, or marks a call it cannot.
   */
  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public final <E extends @Nullable Object> AffinePath<S, E> at(int index) {
    return path.at(index);
  }

  /**
   * @deprecated since 0.5.0, scheduled for removal in 0.6.0. Use {@code
   *     via(FocusPaths.mapAt(key))}, which reads its element type from the path. The {@code
   *     MigrateDeprecationsTo0_6_0} OpenRewrite recipe replaces it, or marks a call it cannot.
   */
  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public final <K, V extends @Nullable Object> AffinePath<S, V> atKey(K key) {
    return path.atKey(key);
  }

  /**
   * @deprecated since 0.5.0, scheduled for removal in 0.6.0. Use {@code
   *     some(FocusPaths.optionalSome())}, which reads its element type from the path. The {@code
   *     MigrateDeprecationsTo0_6_0} OpenRewrite recipe replaces it, or marks a call it cannot.
   */
  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public final <E> AffinePath<S, E> some() {
    return path.some();
  }

  @Override
  public final <B extends @Nullable Object> AffinePath<S, B> some(Affine<A, B> affine) {
    return path.some(affine);
  }

  @Override
  public final AffinePath<S, @NonNull A> nullable() {
    return path.nullable();
  }

  /**
   * @deprecated since 0.5.0, scheduled for removal in 0.6.0. Use {@code
   *     each(EachInstances.fromTraverse(traverse))}, which reads its element type from the path.
   *     The {@code MigrateDeprecationsTo0_6_0} OpenRewrite recipe replaces it, or marks a call it
   *     cannot.
   */
  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public final <F extends WitnessArity<TypeArity.Unary>, E> TraversalPath<S, E> traverseOver(
      Traverse<F> traverse) {
    return path.traverseOver(traverse);
  }

  /**
   * @deprecated since 0.5.0, scheduled for removal in 0.6.0. Use {@code
   *     via(FocusPaths.listCons())}, which reads its element type from the path. The {@code
   *     MigrateDeprecationsTo0_6_0} OpenRewrite recipe replaces it, or marks a call it cannot.
   */
  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public final <E extends @Nullable Object> AffinePath<S, Pair<E, List<E>>> cons() {
    return path.cons();
  }

  /**
   * @deprecated since 0.5.0, scheduled for removal in 0.6.0. Use {@code
   *     via(FocusPaths.listHeadTail())}, which reads its element type from the path. The {@code
   *     MigrateDeprecationsTo0_6_0} OpenRewrite recipe replaces it, or marks a call it cannot.
   */
  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public final <E extends @Nullable Object> AffinePath<S, Pair<E, List<E>>> headTail() {
    return path.headTail();
  }

  /**
   * @deprecated since 0.5.0, scheduled for removal in 0.6.0. Use {@code
   *     via(FocusPaths.listSnoc())}, which reads its element type from the path. The {@code
   *     MigrateDeprecationsTo0_6_0} OpenRewrite recipe replaces it, or marks a call it cannot.
   */
  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public final <E extends @Nullable Object> AffinePath<S, Pair<List<E>, E>> snoc() {
    return path.snoc();
  }

  /**
   * @deprecated since 0.5.0, scheduled for removal in 0.6.0. Use {@code
   *     via(FocusPaths.listInitLast())}, which reads its element type from the path. The {@code
   *     MigrateDeprecationsTo0_6_0} OpenRewrite recipe replaces it, or marks a call it cannot.
   */
  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public final <E extends @Nullable Object> AffinePath<S, Pair<List<E>, E>> initLast() {
    return path.initLast();
  }

  /**
   * @deprecated since 0.5.0, scheduled for removal in 0.6.0. Use {@code
   *     via(FocusPaths.listHead())}, which reads its element type from the path. The {@code
   *     MigrateDeprecationsTo0_6_0} OpenRewrite recipe replaces it, or marks a call it cannot.
   */
  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public final <E extends @Nullable Object> AffinePath<S, E> head() {
    return path.head();
  }

  /**
   * @deprecated since 0.5.0, scheduled for removal in 0.6.0. Use {@code
   *     via(FocusPaths.listLast())}, which reads its element type from the path. The {@code
   *     MigrateDeprecationsTo0_6_0} OpenRewrite recipe replaces it, or marks a call it cannot.
   */
  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public final <E extends @Nullable Object> AffinePath<S, E> last() {
    return path.last();
  }

  /**
   * @deprecated since 0.5.0, scheduled for removal in 0.6.0. Use {@code
   *     via(FocusPaths.listTail())}, which reads its element type from the path. The {@code
   *     MigrateDeprecationsTo0_6_0} OpenRewrite recipe replaces it, or marks a call it cannot.
   */
  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public final <E extends @Nullable Object> AffinePath<S, List<E>> tail() {
    return path.tail();
  }

  /**
   * @deprecated since 0.5.0, scheduled for removal in 0.6.0. Use {@code
   *     via(FocusPaths.listInit())}, which reads its element type from the path. The {@code
   *     MigrateDeprecationsTo0_6_0} OpenRewrite recipe replaces it, or marks a call it cannot.
   */
  @Override
  @Deprecated(since = "0.5.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  public final <E extends @Nullable Object> AffinePath<S, List<E>> init() {
    return path.init();
  }

  @Override
  public final Lens<S, A> toLens() {
    return lens;
  }

  @Override
  public final AffinePath<S, A> asAffine() {
    return path.asAffine();
  }

  @Override
  public final TraversalPath<S, A> asTraversal() {
    return path.asTraversal();
  }

  @Override
  public final Fold<S, A> asFold() {
    return path.asFold();
  }

  @Override
  public final FocusPath<S, A> traced(BiConsumer<S, A> observer) {
    return path.traced(observer);
  }

  @Override
  public final <B> VTaskPath<B> traverseWith(Function<A, VTaskPath<B>> f, S source) {
    return path.traverseWith(f, source);
  }

  @Override
  public final MaybePath<@NonNull A> toMaybePath(S source) {
    return path.toMaybePath(source);
  }

  @Override
  public final <E> EitherPath<E, A> toEitherPath(S source) {
    return path.toEitherPath(source);
  }

  @Override
  public final <E> EitherPath<E, @NonNull A> toEitherPath(S source, E errorIfNull) {
    return path.toEitherPath(source, errorIfNull);
  }

  @Override
  public final <E> EitherPath<E, @NonNull A> toEitherPath(
      S source, Supplier<? extends E> errorSupplier) {
    return path.toEitherPath(source, errorSupplier);
  }

  @Override
  public final TryPath<A> toTryPath(S source) {
    return path.toTryPath(source);
  }

  @Override
  public final TryPath<@NonNull A> toTryPath(
      S source, Supplier<? extends Throwable> exceptionIfNull) {
    return path.toTryPath(source, exceptionIfNull);
  }

  @Override
  public final IdPath<A> toIdPath(S source) {
    return path.toIdPath(source);
  }

  /**
   * Whether another object is a navigator of the same class wrapping an equal path.
   *
   * @param other the object to compare
   * @return true when it is
   */
  @Override
  public final boolean equals(@Nullable Object other) {
    return other instanceof NavigatorFocusPath<?, ?> that
        && getClass() == that.getClass()
        && path.equals(that.path);
  }

  @Override
  public final int hashCode() {
    return path.hashCode();
  }

  @Override
  public final String toString() {
    return path.toString();
  }
}
