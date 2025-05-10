package org.higherkindedj.hkt.optional;

import org.higherkindedj.hkt.Kind;

/**
 * Kind interface marker for the Optional type in Higher-Kinded-J. Represents Optional as a type
 * constructor 'F' in {@code Kind<F, A>}. The witness type F is OptionalKind.Witness.
 *
 * @param <A> The type of the value potentially held by the Optional.
 */
public interface OptionalKind<A> extends Kind<OptionalKind.Witness, A> {

  /**
   * The phantom type marker for the Optional type constructor. This is used as the 'F' in {@code
   * Kind<F, A>}.
   */
  final class Witness {
    private Witness() {} // Private constructor to prevent instantiation
  }
}
