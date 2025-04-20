package org.simulation.hkt;

import java.util.function.Function;

/**
 * Represents a Monad that can explicitly handle errors of type E.
 * Extends Monad<F>.
 *
 * @param <F> The witness type for the Monad (e.g., EitherKind<E, ?>).
 * @param <E> The type of the error value.
 */
public interface MonadError<F, E> extends Monad<F> {

    /**
     * Lifts an error value 'e' into the monadic context F.
     * For Either<E, A>, this would be Kind(Left(e)).
     * For Maybe<A>, this would be Kind(Nothing) (E might be Void).
     *
     * @param error The error value to lift.
     * @param <A>   The phantom type parameter of the value (since this represents an error state).
     * @return The error wrapped in the context F.
     */
    <A> Kind<F, A> raiseError(E error);

    /**
     * Handles an error within the monadic context.
     * If 'ma' represents a success value, it's returned unchanged.
     * If 'ma' represents an error 'e', the 'handler' function is applied to 'e'
     * to potentially recover with a new monadic value.
     *
     * @param ma      The monadic value potentially containing an error.
     * @param handler A function that takes an error 'e' and returns a new monadic value,
     * potentially recovering from the error.
     * @param <A>     The type of the value within the monad.
     * @return The original monadic value if it was successful, or the result of the handler
     * if it contained an error.
     */
    <A> Kind<F, A> handleErrorWith(Kind<F, A> ma, Function<E, Kind<F, A>> handler);

    /**
     * A simpler version of handleErrorWith where the handler returns a pure value 'a'
     * which is then lifted into the context using 'of'.
     * Recovers from any error with a default value 'a'.
     *
     * @param ma      The monadic value potentially containing an error.
     * @param handler A function that takes an error 'e' and returns a pure value 'a'.
     * @param <A>     The type of the value within the monad.
     * @return The original monadic value if successful, or the result of the handler lifted
     * into the monad if it contained an error.
     */
    default <A> Kind<F, A> handleError(Kind<F, A> ma, Function<E, A> handler) {
        // Default implementation using handleErrorWith and 'of'
        return handleErrorWith(ma, error -> of(handler.apply(error)));
    }

    /**
     * Recovers from an error with a default monadic value 'fallback'.
     * If 'ma' contains an error, 'fallback' is returned, otherwise 'ma' is returned.
     *
     * @param ma       The monadic value potentially containing an error.
     * @param fallback The default monadic value to use in case of error.
     * @param <A>      The type of the value within the monad.
     * @return 'ma' if successful, 'fallback' otherwise.
     */
    default <A> Kind<F, A> recoverWith(Kind<F, A> ma, Kind<F, A> fallback) {
        return handleErrorWith(ma, error -> fallback);
    }

    /**
     * Recovers from an error with a default pure value 'a'.
     * If 'ma' contains an error, 'of(a)' is returned, otherwise 'ma' is returned.
     *
     * @param ma       The monadic value potentially containing an error.
     * @param value    The default pure value to use in case of error.
     * @param <A>      The type of the value within the monad.
     * @return 'ma' if successful, 'of(value)' otherwise.
     */
    default <A> Kind<F, A> recover(Kind<F, A> ma, A value) {
        return handleError(ma, error -> value);
    }
}
