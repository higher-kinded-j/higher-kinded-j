package org.higherkindedj.hkt.trans.maybe_t;

import org.higherkindedj.hkt.Kind;

/**
 * Kind interface marker for the MaybeT<F, A> monad transformer. Represents MaybeT<F, ?> as a type
 * constructor 'G' in Kind<G, A>. The wrapped value is of type Kind<F, Maybe<A>>.
 *
 * @param <F> The witness type of the outer monad.
 * @param <A> The type of the value potentially held by the inner Maybe.
 */
public interface MaybeTKind<F, A> extends Kind<MaybeTKind<F, ?>, A> {
  // Witness type G = MaybeTKind<F, ?>
  // Value type A = A (from Maybe<A>)
}
