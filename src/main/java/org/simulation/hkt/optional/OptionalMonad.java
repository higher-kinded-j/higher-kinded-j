package org.simulation.hkt.optional;

import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;
import org.simulation.hkt.MonadError;

import java.util.Optional;
import java.util.function.Function;
import static org.simulation.hkt.optional.OptionalKindHelper.*;

/**
 * Monad and MonadError implementation for OptionalKind.
 * The error type E is Void, representing the Optional.empty state.
 */
public class OptionalMonad extends OptionalFunctor implements MonadError<OptionalKind<?>, Void> {

  @Override
  public <A> OptionalKind<A> of(A value) {
    // Lifts a value into Optional context. Use ofNullable for safety.
    return wrap(Optional.ofNullable(value));
  }

  @Override
  public <A, B> OptionalKind<B> flatMap(Function<A, Kind<OptionalKind<?>, B>> f, Kind<OptionalKind<?>, A> ma) {
    Optional<A> optA = unwrap(ma);

    Optional<B> resultOpt = optA.flatMap(a -> {
      Kind<OptionalKind<?>, B> kindB = f.apply(a);
      return unwrap(kindB);
    });

    return wrap(resultOpt);
  }


  @Override
  public <A, B> Kind<OptionalKind<?>, B> ap(Kind<OptionalKind<?>, Function<A, B>> ff, Kind<OptionalKind<?>, A> fa) {
    Optional<Function<A, B>> optF = unwrap(ff);
    Optional<A> optA = unwrap(fa);

    // If function Optional is present AND value Optional is present, apply function
    // Otherwise, return empty. Optional's flatMap/map handles this nicely.
    Optional<B> resultOpt = optF.flatMap(optA::map); // flatMap on function, map on value

    return wrap(resultOpt);
  }

  // --- MonadError Methods ---

  /**
   * Lifts the error state (Optional.empty) into the Optional context.
   * The input 'error' (Void) is ignored.
   *
   * @param error The error value (Void, ignored).
   * @param <A>   The phantom type parameter of the value.
   * @return An OptionalKind representing Optional.empty.
   */
  @Override
  public <A> Kind<OptionalKind<?>, A> raiseError(Void error) {
    // The error state for Optional is always Optional.empty
    return wrap(Optional.empty()); //
  }

  /**
   * Handles the error state (Optional.empty) within the Optional context.
   * If 'ma' contains a value, it's returned unchanged.
   * If 'ma' is empty, the 'handler' function is applied (with null input as error is Void).
   *
   * @param ma      The OptionalKind value.
   * @param handler Function Void -> Kind<OptionalKind<?>, A> to handle the empty state.
   * @param <A>     The type of the value within the Optional.
   * @return Original Kind if present, or result of handler if empty.
   */
  @Override
  public <A> Kind<OptionalKind<?>, A> handleErrorWith(Kind<OptionalKind<?>, A> ma, Function<Void, Kind<OptionalKind<?>, A>> handler) {
    Optional<A> optional = unwrap(ma); //

    if (optional.isEmpty()) {
      // Apply the handler (passing null because the error type is Void)
      return handler.apply(null);
    } else {
      // It's present, return the original Kind
      return ma;
    }
  }

}
