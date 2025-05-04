package org.higherkindedj.hkt.trans.reader_t; // Create this package

import org.higherkindedj.hkt.Kind;

/**
 * Kind interface marker for the ReaderT<F, R, A> monad transformer. Represents ReaderT<F, R, ?> as
 * a type constructor 'G' in Kind<G, A>. The underlying structure conceptually represents R -> F<A>.
 *
 * @param <F> The witness type of the outer monad (e.g., OptionalKind<?>).
 * @param <R> The type of the environment (read-only context).
 * @param <A> The type of the value produced within the outer monad F.
 */
public interface ReaderTKind<F, R, A> extends Kind<ReaderTKind<F, R, ?>, A> {
  // Witness type G = ReaderTKind<F, R, ?>
  // Value type A = A
}
