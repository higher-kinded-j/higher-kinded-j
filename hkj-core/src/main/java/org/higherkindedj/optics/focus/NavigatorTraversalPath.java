// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.ListPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.NonDetPath;
import org.higherkindedj.hkt.effect.StreamPath;
import org.higherkindedj.hkt.effect.VStreamPath;
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
 * The base of every navigator {@code @GenerateFocus} generates over a {@link TraversalPath}.
 *
 * <p>A navigator is the path it wraps, with a navigation method for each field of the record it
 * focuses on. Every path method is final here and forwards to that path, so a navigator reads,
 * writes, composes and prints exactly as the path does, and a subclass can only add methods. A
 * navigator equals another navigator of the same class over an equal path.
 *
 * <p>Each method here that takes no arguments is a name a field of a record the navigator reaches
 * cannot take, since a navigation method of that name would clash with it. Adding one changes what
 * such records may declare, and leaves a navigator into a record with a field of that name without
 * its {@code TraversalForm}, which a hop from a wider path hands on.
 *
 * <p>This class exists for generated code. Write a {@link TraversalPath} by composing the paths the
 * generated companions publish, or with {@link TraversalPath#of(Traversal)}.
 *
 * @param <S> the source type
 * @param <A> the focused type
 * @since 0.5.0
 */
@NullMarked
public abstract non-sealed class NavigatorTraversalPath<
        S extends @Nullable Object, A extends @Nullable Object>
    implements TraversalPath<S, A> {

  private final TraversalPath<S, A> path;

  /**
   * Wraps a path.
   *
   * @param path the path this navigator is
   */
  protected NavigatorTraversalPath(TraversalPath<S, A> path) {
    this.path = Objects.requireNonNull(path, "path must not be null");
  }

  /**
   * The path this navigator wraps, without its navigation methods.
   *
   * @return the wrapped path
   */
  public final TraversalPath<S, A> toPath() {
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
  public final List<A> getAll(S source) {
    return path.getAll(source);
  }

  @Override
  public final Optional<@NonNull A> preview(S source) {
    return path.preview(source);
  }

  @Override
  public final S setAll(A value, S source) {
    return path.setAll(value, source);
  }

  @Override
  public final S modifyAll(Function<A, A> f, S source) {
    return path.modifyAll(f, source);
  }

  @Override
  public final int count(S source) {
    return path.count(source);
  }

  @Override
  public final boolean isEmpty(S source) {
    return path.isEmpty(source);
  }

  @Override
  public final boolean exists(Predicate<A> predicate, S source) {
    return path.exists(predicate, source);
  }

  @Override
  public final boolean all(Predicate<A> predicate, S source) {
    return path.all(predicate, source);
  }

  @Override
  public final Optional<@NonNull A> find(Predicate<A> predicate, S source) {
    return path.find(predicate, source);
  }

  @Override
  public final TraversalPath<S, A> filter(Predicate<A> predicate) {
    return path.filter(predicate);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> via(Lens<A, B> lens) {
    return path.via(lens);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> via(FocusPath<A, B> other) {
    return path.via(other);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> via(AffinePath<A, B> other) {
    return path.via(other);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> via(TraversalPath<A, B> other) {
    return path.via(other);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> via(Prism<A, B> prism) {
    return path.via(prism);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> via(Affine<A, B> affine) {
    return path.via(affine);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> via(Traversal<A, B> traversal) {
    return path.via(traversal);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> via(Iso<A, B> iso) {
    return path.via(iso);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> then(Lens<A, B> lens) {
    return path.then(lens);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> then(FocusPath<A, B> other) {
    return path.then(other);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> then(Prism<A, B> prism) {
    return path.then(prism);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> then(Affine<A, B> affine) {
    return path.then(affine);
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> then(AffinePath<A, B> other) {
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

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> then(Iso<A, B> iso) {
    return path.then(iso);
  }

  @Override
  public final S modifyWhen(Predicate<A> condition, Function<A, A> f, S source) {
    return path.modifyWhen(condition, f, source);
  }

  @Override
  public final <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
      Function<A, Kind<F, A>> f, S source, Applicative<F> applicative) {
    return path.modifyF(f, source, applicative);
  }

  @Override
  public final <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source) {
    return path.foldMap(monoid, f, source);
  }

  @Override
  public final @NonNull A fold(Monoid<@NonNull A> monoid, S source) {
    return path.fold(monoid, source);
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
  public final <E extends @Nullable Object> TraversalPath<S, E> at(int index) {
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
  public final <K, V extends @Nullable Object> TraversalPath<S, V> atKey(K key) {
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
  public final <E> TraversalPath<S, E> some() {
    return path.some();
  }

  @Override
  public final <B extends @Nullable Object> TraversalPath<S, B> some(Affine<A, B> affine) {
    return path.some(affine);
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
  public final <E extends @Nullable Object> TraversalPath<S, Pair<E, List<E>>> cons() {
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
  public final <E extends @Nullable Object> TraversalPath<S, Pair<E, List<E>>> headTail() {
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
  public final <E extends @Nullable Object> TraversalPath<S, Pair<List<E>, E>> snoc() {
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
  public final <E extends @Nullable Object> TraversalPath<S, Pair<List<E>, E>> initLast() {
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
  public final <E extends @Nullable Object> TraversalPath<S, E> head() {
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
  public final <E extends @Nullable Object> TraversalPath<S, E> last() {
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
  public final <E extends @Nullable Object> TraversalPath<S, List<E>> tail() {
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
  public final <E extends @Nullable Object> TraversalPath<S, List<E>> init() {
    return path.init();
  }

  @Override
  public final AffinePath<S, A> headOption() {
    return path.headOption();
  }

  @Override
  public final Traversal<S, A> toTraversal() {
    return path.toTraversal();
  }

  @Override
  public final Fold<S, A> asFold() {
    return path.asFold();
  }

  @Override
  public final TraversalPath<S, A> traced(BiConsumer<S, List<A>> observer) {
    return path.traced(observer);
  }

  @Override
  public final <B> VTaskPath<List<B>> traverseWith(Function<A, VTaskPath<B>> f, S source) {
    return path.traverseWith(f, source);
  }

  @Override
  public final ListPath<@NonNull A> toListPath(S source) {
    return path.toListPath(source);
  }

  @Override
  public final NonDetPath<@NonNull A> toNonDetPath(S source) {
    return path.toNonDetPath(source);
  }

  @Override
  public final StreamPath<@NonNull A> toStreamPath(S source) {
    return path.toStreamPath(source);
  }

  @Override
  public final VStreamPath<@NonNull A> toVStreamPath(S source) {
    return path.toVStreamPath(source);
  }

  @Override
  public final MaybePath<@NonNull A> toMaybePath(S source) {
    return path.toMaybePath(source);
  }

  /**
   * Whether another object is a navigator of the same class wrapping an equal path.
   *
   * @param other the object to compare
   * @return true when it is
   */
  @Override
  public final boolean equals(@Nullable Object other) {
    return other instanceof NavigatorTraversalPath<?, ?> that
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
