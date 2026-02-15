// Copyright (c) 2025 - 2026 Magnus Smith
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

  /**
   * Combines all elements in the given iterable using this Monoid's combine operation.
   *
   * <p>If the iterable is empty, returns the empty value.
   *
   * @param elements The iterable of elements to combine (non-null).
   * @return The combined value (non-null).
   */
  default A combineAll(final Iterable<A> elements) {
    A result = empty();
    for (A element : elements) {
      result = combine(result, element);
    }
    return result;
  }

  /**
   * Combines a value with itself {@code n} times using this Monoid's combine operation.
   *
   * <p>If {@code n} is 0, returns the empty value. If {@code n} is 1, returns the value itself.
   *
   * @param value The value to combine (non-null).
   * @param n The number of times to combine (must be non-negative).
   * @return The combined value (non-null).
   * @throws IllegalArgumentException if {@code n} is negative.
   */
  default A combineN(final A value, final int n) {
    if (n < 0) {
      throw new IllegalArgumentException("n must be non-negative, but was: " + n);
    }

    A result = empty();
    A current = value;
    int k = n;

    while (k > 0) {
      if ((k & 1) == 1) { // if k is odd
        result = combine(result, current);
      }
      current = combine(current, current);
      k >>= 1; // k = k / 2
    }
    return result;
  }

  /**
   * Tests whether the given value is equal to the empty value of this Monoid.
   *
   * @param value The value to test (non-null).
   * @return {@code true} if the value equals the empty value, {@code false} otherwise.
   */
  default boolean isEmpty(final A value) {
    return empty().equals(value);
  }
}
