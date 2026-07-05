// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * A composable update: a function from a value to a new value of the same type.
 *
 * <p>{@code Update<S>} is a named, reusable transformation you apply to many sources and compose
 * with other updates. It extends {@link UnaryOperator}, so it drops into any API that accepts a
 * {@code Function<S, S>} (such as {@code Stream.map} or {@code Optional.map}) for free:
 *
 * <pre>{@code
 * Update<Order> normalise     = order -> ...;
 * Update<Order> applyDiscount = order -> ...;
 *
 * Order patched = normalise.andThen(applyDiscount).apply(order);
 * }</pre>
 *
 * <p>Updates form a {@link Monoid} under left-to-right composition, available via {@link
 * Monoids#update()}: the identity element is {@link #identity()}, and {@code combine(f, g)} applies
 * {@code f} first, then {@code g}. This is what lets any number of updates be folded into one.
 *
 * <p>In functional-programming literature this type is known as {@code Endo} (an endomorphism); it
 * is named {@code Update} here for clarity.
 *
 * @param <S> the type of the value being updated
 * @see Monoids#update()
 */
@FunctionalInterface
public interface Update<S> extends UnaryOperator<S> {

  /**
   * Returns the do-nothing update, which returns its input unchanged.
   *
   * <p>This is the identity element of the {@link Monoids#update()} monoid.
   *
   * @param <S> the type of the value being updated
   * @return the identity update (non-null)
   */
  static <S> Update<S> identity() {
    return s -> s;
  }

  /**
   * Returns a composed update that applies this update first and then {@code after}.
   *
   * <p>This is {@link UnaryOperator#andThen} narrowed so the result remains an {@code Update<S>}
   * and stays composable. Note that overload selection is static: the argument must itself be typed
   * as {@code Update<S>} — composing with a plain {@code UnaryOperator} or {@code Function} selects
   * the inherited {@link java.util.function.Function#andThen} and yields a {@code Function}, not an
   * {@code Update}. Wrap such a function first ({@code fn::apply} as an {@code Update<S>}) to stay
   * in the type.
   *
   * @param after the update to apply after this one; must not be null
   * @return the composed update (non-null)
   * @throws NullPointerException if {@code after} is null
   */
  default Update<S> andThen(final Update<S> after) {
    Objects.requireNonNull(after, "after must not be null");
    return s -> after.apply(this.apply(s));
  }
}
