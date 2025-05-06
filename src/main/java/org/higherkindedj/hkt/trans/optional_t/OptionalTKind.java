package org.higherkindedj.hkt.trans.optional_t;

import org.higherkindedj.hkt.Kind;

/**
 * Kind interface marker for the {@code OptionalT<F, A>} monad transformer. Represents {@code
 * OptionalT<F, ?>} as a type constructor 'G' in {@code Kind<G, A>}. The wrapped value is of type
 * {@code Kind<F, Optional<A>>}.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code CompletableFutureKind<?>}).
 * @param <A> The type of the value potentially held by the inner Optional.
 */
public interface OptionalTKind<F, A> extends Kind<OptionalTKind<F, ?>, A> {
  // Witness type G = OptionalTKind<F, ?>
  // Value type A = A (from Optional<A>)
}
