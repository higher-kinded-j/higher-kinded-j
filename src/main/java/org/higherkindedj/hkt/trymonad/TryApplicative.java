package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class TryApplicative extends TryFunctor implements Applicative<TryKind<?>> {

  @Override
  public <A> @NonNull Kind<TryKind<?>, A> of(@Nullable A value) {
    // Lifts a pure value into a Success context.
    return wrap(Try.success(value)); // Try.success allows null
    // Note: Try.of(() -> value) could also be used if you wanted to
    // potentially catch issues even during simple lifting, but standard 'of'
    // usually just wraps a known good value.
  }

  @Override
  public <A, B> @NonNull Kind<TryKind<?>, B> ap(
      @NonNull Kind<TryKind<?>, Function<A, B>> ff, @NonNull Kind<TryKind<?>, A> fa) {
    Try<Function<A, B>> tryF = unwrap(ff); // unwrap handles null/invalid ff
    Try<A> tryA = unwrap(fa); // unwrap handles null/invalid fa

    // Use fold for pattern matching on the function Try
    Try<B> resultTry =
        tryF.fold(
            // Case 1: Function is Success(f)
            f ->
                tryA.fold(
                    // Case 1a: Value is Success(a) -> Apply f(a) within a Try
                    a -> Try.of(() -> f.apply(a)), // Use Try.of to catch exceptions from f.apply(a)
                    // Case 1b: Value is Failure(e) -> Propagate value's failure
                    failureA -> Try.failure(failureA) // failureA is NonNull Throwable
                    ),
            // Case 2: Function is Failure(e) -> Propagate function's failure
            failureF -> Try.failure(failureF) // failureF is NonNull Throwable
            );

    return wrap(resultTry); // resultTry is NonNull
  }
}
