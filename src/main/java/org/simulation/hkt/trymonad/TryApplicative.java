package org.simulation.hkt.trymonad;

import org.simulation.hkt.Applicative;
import org.simulation.hkt.Kind;
import java.util.function.Function;
import static org.simulation.hkt.trymonad.TryKindHelper.*;

public class TryApplicative extends TryFunctor implements Applicative<TryKind<?>> {

  @Override
  public <A> Kind<TryKind<?>, A> of(A value) {
    // Lifts a pure value into a Success context.
    return wrap(Try.success(value));
    // Note: Try.of(() -> value) could also be used if you wanted to
    // potentially catch issues even during simple lifting, but standard 'of'
    // usually just wraps a known good value.
  }

  @Override
  public <A, B> Kind<TryKind<?>, B> ap(Kind<TryKind<?>, Function<A, B>> ff, Kind<TryKind<?>, A> fa) {
    Try<Function<A, B>> tryF = unwrap(ff);
    Try<A> tryA = unwrap(fa);

    // Use fold for pattern matching on the function Try
    Try<B> resultTry = tryF.fold(
        // Case 1: Function is Success(f)
        f -> tryA.fold(
            // Case 1a: Value is Success(a) -> Apply f(a) within a Try
            a -> Try.of(() -> f.apply(a)), // Use Try.of to catch exceptions from f.apply(a)
            // Case 1b: Value is Failure(e) -> Propagate value's failure
            failureA -> Try.failure(failureA) // Need type hint for failure
        ),
        // Case 2: Function is Failure(e) -> Propagate function's failure
        failureF -> Try.failure(failureF) // Need type hint for failure
    );

    return wrap(resultTry);
  }
}