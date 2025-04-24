package org.simulation.hkt;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.function.Function;

/**
 * Represents a Monad that can explicitly handle errors of type E.
 * Extends Monad<F>.
 *
 * @param <F> The witness type for the Monad (e.g., EitherKind<E, ?>).
 * @param <E> The type of the error value. Nullability depends on context (e.g., Throwable is NonNull, Void is Nullable).
 */
public interface MonadError<F, E> extends Monad<F> {

    /**
     * Lifts an error value 'e' into the monadic context F.
     * For Either<E, A>, this would be Kind(Left(e)).
     * For Maybe<A>, this would be Kind(Nothing) (E might be Void).
     *
     * @param error The error value to lift.
     * @param <A>   The phantom type parameter of the value (since this represents an error state).
     * @return The error wrapped in the context F. Guaranteed non-null.
     */
    <A> @NonNull Kind<F, A> raiseError(@Nullable E error); // Error type E might be nullable (e.g., Void)

    /**
     * Handles an error within the monadic context.
     * If 'ma' represents a success value, it's returned unchanged.
     * If 'ma' represents an error 'e', the 'handler' function is applied to 'e'
     * to potentially recover with a new monadic value.
     *
     * @param ma      The monadic value potentially containing an error. Assumed non-null.
     * @param handler A function that takes an error 'e' and returns a new monadic value,
     * potentially recovering from the error. Assumed non-null.
     * @param <A>     The type of the value within the monad.
     * @return The original monadic value if it was successful, or the result of the handler
     * if it contained an error. Guaranteed non-null.
     */
    <A> @NonNull Kind<F, A> handleErrorWith(@NonNull Kind<F, A> ma, @NonNull Function<E, Kind<F, A>> handler);

    /**
     * A simpler version of handleErrorWith where the handler returns a pure value 'a'
     * which is then lifted into the context using 'of'.
     * Recovers from any error with a default value 'a'.
     *
     * @param ma      The monadic value potentially containing an error. Assumed non-null.
     * @param handler A function that takes an error 'e' and returns a pure value 'a'. Assumed non-null.
     * @param <A>     The type of the value within the monad.
     * @return The original monadic value if successful, or the result of the handler lifted
     * into the monad if it contained an error. Guaranteed non-null.
     */
    default <A> @NonNull Kind<F, A> handleError(@NonNull Kind<F, A> ma, @NonNull Function<E, A> handler) {
        // Default implementation using handleErrorWith and 'of'
        return handleErrorWith(ma, error -> of(handler.apply(error)));
    }

    /**
     * Recovers from an error with a default monadic value 'fallback'.
     * If 'ma' contains an error, 'fallback' is returned, otherwise 'ma' is returned.
     *
     * @param ma       The monadic value potentially containing an error. Assumed non-null.
     * @param fallback The default monadic value to use in case of error. Assumed non-null.
     * @param <A>      The type of the value within the monad.
     * @return 'ma' if successful, 'fallback' otherwise. Guaranteed non-null.
     */
    default <A> @NonNull Kind<F, A> recoverWith(@NonNull Kind<F, A> ma, @NonNull Kind<F, A> fallback) {
        return handleErrorWith(ma, error -> fallback);
    }

    /**
     * Recovers from an error with a default pure value 'a'.
     * If 'ma' contains an error, 'of(a)' is returned, otherwise 'ma' is returned.
     *
     * @param ma       The monadic value potentially containing an error. Assumed non-null.
     * @param value    The default pure value to use in case of error. Nullability depends on `of`.
     * @param <A>      The type of the value within the monad.
     * @return 'ma' if successful, 'of(value)' otherwise. Guaranteed non-null.
     */
    default <A> @NonNull Kind<F, A> recover(@NonNull Kind<F, A> ma, @Nullable A value) { // Value nullability matches `of`
        return handleError(ma, error -> value);
    }
}