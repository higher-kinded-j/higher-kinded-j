package org.simulation.hkt.trymonad;

import org.simulation.hkt.Kind;
import org.simulation.hkt.MonadError;
import java.util.function.Function;
import static org.simulation.hkt.trymonad.TryKindHelper.*;

/**
 * MonadError implementation for TryKind.
 * The error type E is Throwable.
 */
public class TryMonadError extends TryMonad implements MonadError<TryKind<?>, Throwable> {

  @Override
  public <A> Kind<TryKind<?>, A> raiseError(Throwable error) {
    // Create a Failure and wrap it in the Kind.
    return wrap(Try.failure(error));
  }

  @Override
  public <A> Kind<TryKind<?>, A> handleErrorWith(Kind<TryKind<?>, A> ma, Function<Throwable, Kind<TryKind<?>, A>> handler) {
    Try<A> tryA = unwrap(ma);

    // Use Try's recoverWith, adapting the handler function
    Try<A> resultTry = tryA.recoverWith(throwable -> {
      // Our handler returns Kind<TryKind<?>, A>
      // The underlying Try.recoverWith expects Function<Throwable, Try<A>>
      // We need to apply the handler and unwrap its result, handling potential exceptions from the handler itself.
      try {
        Kind<TryKind<?>, A> recoveryKind = handler.apply(throwable);
        // Explicitly return Failure<A> if unwrapping fails
        // Note: unwrap already returns Try.Failure on error.
        return unwrap(recoveryKind);
      } catch (Throwable t) {
        // Catch exceptions thrown by handler.apply(throwable) itself
        return Try.failure(t);
      }
    });

    return wrap(resultTry);
  }

}
