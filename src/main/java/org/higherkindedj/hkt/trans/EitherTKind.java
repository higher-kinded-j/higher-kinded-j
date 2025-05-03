package org.higherkindedj.hkt.trans;

import org.higherkindedj.hkt.Kind;

/**
 * Kind interface marker for the {@code EitherT<F, L, R>} monad transformer. Represents {@code EitherT<F, L, ?>} -> as
 * a type constructor 'G' in {@code Kind<G, A>}, where 'F' and 'L' are fixed, and 'A' corresponds to the 'R'
 * (Right) type parameter. The wrapped value is of type {@code Kind<F, Either<L, R>>}.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code CompletableFutureKind<?>}).
 * @param <L> The fixed Left type for the inner Either.
 * @param <R> The Right type (variable type 'A' in {@code Kind<G, A>}).
 */
public interface EitherTKind<F, L, R> extends Kind<EitherTKind<F, L, ?>, R> {
  // Witness type G = EitherTKind<F, L, ?>
  // Value type A = R
}
