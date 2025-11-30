// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.optics;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A Traversal focuses on zero or more values within a structure.
 *
 * <p>Traversals represent "has-many" relationships: a Department <em>has many</em> Employees. They
 * generalise lenses to work with collections.
 *
 * @param <S> the type of the whole structure
 * @param <A> the type of each focused element
 */
public record Traversal<S, A>(
    Function<S, List<A>> getAll, BiFunction<Function<A, A>, S, S> modify) {

  /** Create a traversal from getAll and modify functions. */
  public static <S, A> Traversal<S, A> of(
      Function<S, List<A>> getAll, BiFunction<Function<A, A>, S, S> modify) {
    return new Traversal<>(getAll, modify);
  }

  /** Create a traversal over list elements. */
  public static <A> Traversal<List<A>, A> list() {
    return Traversal.of(list -> list, (f, list) -> list.stream().map(f).toList());
  }

  /** Get all focused values. */
  public List<A> getAll(S whole) {
    return getAll.apply(whole);
  }

  /** Modify all focused values using a function. */
  public S modify(Function<A, A> f, S whole) {
    return modify.apply(f, whole);
  }

  /** Set all focused values to the same value. */
  public S set(A value, S whole) {
    return modify(__ -> value, whole);
  }

  /**
   * Fold all focused values into a single result.
   *
   * @param combiner how to combine two results
   * @param identity the identity element
   * @param mapper transform each focused value before combining
   * @return the folded result
   */
  public <R> R foldMap(BinaryOperator<R> combiner, R identity, Function<A, R> mapper, S whole) {
    return getAll(whole).stream().map(mapper).reduce(identity, combiner);
  }

  /**
   * Filter this traversal to only focus on elements matching the predicate.
   *
   * @param predicate the filter condition
   * @return a filtered traversal
   */
  public Traversal<S, A> filtered(Predicate<A> predicate) {
    return Traversal.of(
        s -> getAll(s).stream().filter(predicate).toList(),
        (f, s) -> modify.apply(a -> predicate.test(a) ? f.apply(a) : a, s));
  }

  /**
   * Compose with a lens.
   *
   * @param lens a lens from A to B
   * @return a traversal from S to B
   */
  public <B> Traversal<S, B> andThen(Lens<A, B> lens) {
    return Traversal.of(
        s -> getAll(s).stream().map(lens::get).toList(),
        (f, s) -> modify(a -> lens.modify(f, a), s));
  }

  /**
   * Compose with a prism.
   *
   * @param prism a prism from A to B
   * @return a traversal from S to B
   */
  public <B> Traversal<S, B> andThen(Prism<A, B> prism) {
    return Traversal.of(
        s -> getAll(s).stream().flatMap(a -> prism.getOptional(a).stream()).toList(),
        (f, s) -> modify(a -> prism.modify(f, a), s));
  }

  /**
   * Compose with another traversal.
   *
   * @param other a traversal from A to B
   * @return a traversal from S to B
   */
  public <B> Traversal<S, B> andThen(Traversal<A, B> other) {
    return Traversal.of(
        s -> getAll(s).stream().flatMap(a -> other.getAll(a).stream()).toList(),
        (f, s) -> modify(a -> other.modify(f, a), s));
  }

  /**
   * Count the focused elements.
   *
   * @return the number of focused elements
   */
  public int count(S whole) {
    return getAll(whole).size();
  }
}
