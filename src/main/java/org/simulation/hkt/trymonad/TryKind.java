package org.simulation.hkt.trymonad;

import org.simulation.hkt.Kind;

/**
 * Kind interface marker for the Try<T> type in the HKT simulation.
 * Represents Try as a type constructor 'F' in Kind<F, A>.
 *
 * @param <T> The type of the value potentially held by the Try (in case of Success).
 */
public interface TryKind<T> extends Kind<TryKind<?>, T> {
  // Witness type F = TryKind<?>
  // Value type A = T
}