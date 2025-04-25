package org.simulation.hkt.typeclass;

import org.jspecify.annotations.NonNull;

/**
 * Represents a Monoid type class. A Monoid provides an associative binary operation 'combine' and
 * an identity element 'empty'.
 *
 * @param <A> The type for which the Monoid is defined.
 */
public interface Monoid<A> {
  /**
   * The identity element for the combine operation. combine(empty(), x) == x combine(x, empty()) ==
   * x
   */
  @NonNull A empty(); // Usually non-null, e.g., "", 0, Collections.emptyList()

  /** An associative binary operation. combine(x, combine(y, z)) == combine(combine(x, y), z) */
  @NonNull A combine(@NonNull A x, @NonNull A y);
}
