// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import org.jspecify.annotations.NullMarked;

/**
 * A Semigroup is a type {@code A} that has an associative {@code combine} operation.
 *
 * <p>The combine operation must satisfy the following law:
 *
 * <ul>
 *   <li><b>Associativity:</b> {@code combine(a, combine(b, c))} is equivalent to {@code
 *       combine(combine(a, b), c)} for all values a, b, and c.
 * </ul>
 *
 * <p><b>Example:</b> A Semigroup for {@code String} with concatenation:
 *
 * <pre>{@code
 * Semigroup<String> stringSemigroup = (s1, s2) -> s1 + s2;
 * stringSemigroup.combine("hello", " world"); // "hello world"
 * }</pre>
 *
 * @param <A> The type of the values to be combined.
 * @see Monoid
 */
@NullMarked
public interface Semigroup<A> {

  /**
   * Combines two values of type {@code A} into one.
   *
   * @param a1 The first value (non-null).
   * @param a2 The second value (non-null).
   * @return The combined value (non-null).
   */
  A combine(A a1, A a2);
}
