package org.higherkindedj.hkt.either;

import org.higherkindedj.hkt.Kind;

/**
 * Kind interface marker for the {@code Either<L, R>} type in the Higher-Kinded-J. Represents {@code
 * Either<L, ?>} as a type constructor 'F' in {@code Kind<F, A>}, where 'L' is fixed and 'A'
 * corresponds to the 'R' (Right) type parameter. This structure facilitates defining Functor/Monad
 * instances biased towards the Right value.
 *
 * @param <L> The fixed Left type for this Kind instance.
 * @param <R> The Right type (variable type 'A' in {@code Kind<F, A>}).
 */
public interface EitherKind<L, R> extends Kind<EitherKind<L, ?>, R> {
  // No methods needed, purely a marker for the type system.
  // The Witness type F is EitherKind<L, ?>
  // The Value type A is R

}
