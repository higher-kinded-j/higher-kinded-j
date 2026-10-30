// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.Objects;
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
 * Monoid<Integer> integerAddition = Monoid.of(0, Integer::sum);
 * integerAddition.combineAll(List.of(1, 2, 3)); // 6
 * }</pre>
 *
 * <p>{@link Monoids} holds ready-made instances, this one among them.
 *
 * @param <A> The type of the values.
 * @see Semigroup
 * @see Monoids
 */
@NullMarked
public interface Monoid<A> extends Semigroup<A> {

  /**
   * Returns a Monoid with the given empty value and combine operation.
   *
   * <p>The operation can be a lambda, a method reference, or an existing {@link Semigroup}, which
   * this lifts to a Monoid by giving it an empty value:
   *
   * <pre>{@code
   * Monoid<Duration> elapsed = Monoid.of(Duration.ZERO, Duration::plus);
   * Monoid<Money> total = Monoid.of(Money.ZERO, moneyAddition); // a Semigroup<Money>
   * }</pre>
   *
   * <p>A {@code BinaryOperator<A>} passes as {@code operator::apply}.
   *
   * <p>{@code of} does not check the laws: {@code empty} must leave any value unchanged when
   * combined with it, on either side, and {@code combine} must be associative.
   *
   * @param <A> The type of the values.
   * @param empty The empty value (non-null).
   * @param combine The associative operation (non-null).
   * @return A Monoid whose {@code empty()} returns {@code empty} and whose {@code combine} calls
   *     {@code combine} (non-null).
   * @throws NullPointerException if {@code empty} or {@code combine} is null.
   */
  static <A> Monoid<A> of(final A empty, final Semigroup<A> combine) {
    Objects.requireNonNull(empty, "empty must not be null");
    Objects.requireNonNull(combine, "combine must not be null");
    return new Monoid<>() {
      @Override
      public A empty() {
        return empty;
      }

      @Override
      public A combine(final A a1, final A a2) {
        return combine.combine(a1, a2);
      }
    };
  }

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
   * <p>An instance may test a looser equality: {@link Monoids#bigDecimalAddition()} counts zero at
   * any scale as empty.
   *
   * @param value The value to test (non-null).
   * @return {@code true} if the value equals the empty value, {@code false} otherwise.
   */
  default boolean isEmpty(final A value) {
    return empty().equals(value);
  }
}
