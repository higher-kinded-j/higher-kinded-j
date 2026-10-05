// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A minimal, package-private immutable list that {@link Setter#forList()} and {@link
 * Setter#forMapValues()} sequence their effects into.
 *
 * <p>The effect {@code modifyF} builds may call each combining function more than once: a lazy
 * effect such as {@code IO} each time it runs, a non-deterministic one such as {@code List} once
 * per branch. A mutable list lifted once with {@code of} would be shared by all of those calls.
 * Each call here makes a new cell instead, in constant time, so the calls share a tail without
 * seeing each other's elements. It is the hkj-api counterpart of the cons list that {@code
 * ListTraverse} uses (cf. {@code org.higherkindedj.hkt.util.FList}). Unlike {@code List.copyOf}, it
 * keeps a null element.
 *
 * @param <A> The element type.
 */
@NullMarked
sealed interface ConsList<A extends @Nullable Object> {

  /**
   * The empty list.
   *
   * @param <A> The element type.
   */
  record Nil<A extends @Nullable Object>() implements ConsList<A> {}

  /**
   * An element in front of the rest of the list.
   *
   * @param head The first element.
   * @param tail The rest of the list.
   * @param <A> The element type.
   */
  record Cons<A extends @Nullable Object>(A head, ConsList<A> tail) implements ConsList<A> {}

  /**
   * Sequences the effects into one effect of their results, in order.
   *
   * <p>It folds from the right, prepending each result, and converts the list once at the end. Each
   * effect is the first operand of its combining function, so an applicative that keeps the first
   * failure, such as {@code Either}, reports the earliest one.
   *
   * @param effects The effects to sequence.
   * @param app The applicative that combines them.
   * @param <F> The effect's witness type.
   * @param <A> The result type of each effect.
   * @return One effect whose result lists every effect's result in order, as {@link #toList()}
   *     gives it.
   */
  static <F extends WitnessArity<TypeArity.Unary>, A extends @Nullable Object>
      Kind<F, List<A>> sequence(List<Kind<F, A>> effects, Applicative<F> app) {
    Kind<F, ConsList<A>> acc = app.of(new Nil<>());
    for (Kind<F, A> effect : effects.reversed()) {
      acc = app.map2(effect, acc, (head, tail) -> new Cons<>(head, tail));
    }
    return app.map(ConsList::toList, acc);
  }

  /**
   * The elements front to back, as an unmodifiable list that keeps a null element.
   *
   * @return The elements in order.
   */
  default List<A> toList() {
    List<A> elements = new ArrayList<>();
    ConsList<A> rest = this;
    while (rest instanceof Cons<A>(A head, ConsList<A> tail)) {
      elements.add(head);
      rest = tail;
    }
    return Collections.unmodifiableList(elements);
  }
}
