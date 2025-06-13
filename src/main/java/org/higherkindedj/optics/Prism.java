package org.higherkindedj.optics;

import java.util.Optional;

/**
 * A Prism focuses on a single case 'A' of a sum type 'S'.
 * It provides a way to optionally get the value if it's the right case,
 * and a way to construct the structure 'S' from a value 'A'.
 *
 * @param <S> The type of the sum type (e.g., Either<L, R>).
 * @param <A> The type of the focused case (e.g., R).
 */
public interface Prism<S, A> {

  /**
   * Optionally gets the part 'A' from the structure 'S' if it matches the focus.
   * Returns an empty Optional otherwise.
   */
  Optional<A> getOptional(S source);

  /**
   * Constructs the structure 'S' from a value of the focused part 'A'.
   * This is also known as 'reverseGet' or 'review'.
   */
  S build(A value);
}
