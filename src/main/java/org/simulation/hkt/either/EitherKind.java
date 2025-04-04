package org.simulation.hkt.either;

import org.simulation.hkt.Kind;

/**
 * Kind interface marker for the Either<L, R> type in the HKT simulation.
 * Represents Either<L, -> as a type constructor 'F' in Kind<F, A>,
 * where 'L' is fixed and 'A' corresponds to the 'R' (Right) type parameter.
 * This structure facilitates defining Functor/Monad instances biased towards the Right value.
 *
 * @param <L> The fixed Left type for this Kind instance.
 * @param <R> The Right type (variable type 'A' in Kind<F, A>).
 */
public interface EitherKind<L, R> extends Kind<EitherKind<L, ?>, R> {
    // No methods needed, purely a marker for the type system simulation.
    // The Witness type F is EitherKind<L, ?>
    // The Value type A is R

}
