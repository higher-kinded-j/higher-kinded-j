package org.simulation.hkt.trymonad;

import org.jspecify.annotations.NonNull;
import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;
import java.util.function.Function;
import static org.simulation.hkt.trymonad.TryKindHelper.*;

public class TryMonad extends TryApplicative implements Monad<TryKind<?>> {

  @Override
  public <A, B> @NonNull Kind<TryKind<?>, B> flatMap(@NonNull Function<A, Kind<TryKind<?>, B>> f, @NonNull Kind<TryKind<?>, A> ma) {
    Try<A> tryA = unwrap(ma); // Handles null/invalid ma

    // Use Try's flatMap, but need to adapt the function signature
    Try<B> resultTry = tryA.flatMap(a -> {
      // Our function f returns Kind<TryKind<?>, B>
      // The underlying Try.flatMap expects Function<A, Try<B>>
      // We need to apply f, then unwrap the result Kind, handling potential exceptions from f.apply itself
      try {
        Kind<TryKind<?>, B> kindB = f.apply(a); // f is NonNull
        // Explicitly return Failure<B> if unwrapping fails (e.g., kindB is null/invalid)
        // Note: unwrap already returns Try.Failure on error.
        return unwrap(kindB); // Returns NonNull Try
      } catch (Throwable t) {
        // Catch exceptions thrown by f.apply(a) itself
        return Try.failure(t); // Returns NonNull Failure
      }
    });

    return wrap(resultTry); // wrap requires NonNull resultTry
  }
}