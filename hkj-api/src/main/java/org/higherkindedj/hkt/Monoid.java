// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import org.jspecify.annotations.NullMarked;

/**
 * A Monoid is a Semigroup that also has an "identity" or "empty" element.
 *
 * <p>In addition to the associative law from Semigroup, a Monoid must satisfy the identity laws:
 *
 * <ul>
 *   <li><b>Left identity:</b> {@code combine(empty(), a)} is equivalent to {@code a}.
 *   <li><b>Right identity:</b> {@code combine(a, empty())} is equivalent to {@code a}.
 * </ul>
 *
 * <p><b>Example:</b> A Monoid for {@code Integer} with addition:
 *
 * <pre>{@code
 * Monoid<Integer> integerAddition = new Monoid<>() {
 *  public Integer combine(Integer i1, Integer i2) {
 *    return i1 + i2;
 *  }
 *  public Integer empty() {
 *    return 0;
 *  }
 * };
 * }</pre>
 *
 * @param <A> The type of the values.
 * @see Semigroup
 */
@NullMarked
public interface Monoid<A> extends Semigroup<A> {

  /**
   * Returns the identity or "empty" value for this Monoid.
   *
   * @return The empty value (non-null).
   */
  A empty();
}
