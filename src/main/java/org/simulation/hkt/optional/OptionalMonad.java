package org.simulation.hkt.optional;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
  public <A> @NonNull OptionalKind<A> of(@Nullable A value) { // Value can be null
    // Lifts a value into Optional context. Use ofNullable for safety.
    return wrap(Optional.ofNullable(value));
  }

  @Override
  public <A, B> @NonNull OptionalKind<B> flatMap(@NonNull Function<A, Kind<OptionalKind<?>, B>> f, @NonNull Kind<OptionalKind<?>, A> ma) {
    Optional<A> optA = unwrap(ma); // Handles null/invalid ma

    Optional<B> resultOpt = optA.flatMap(a -> { // Optional.flatMap handles null `a` case (though unwrap likely returns empty)
      Kind<OptionalKind<?>, B> kindB = f.apply(a); // f is NonNull
      return unwrap(kindB); // unwrap returns NonNull Optional
    });

    return wrap(resultOpt); // wrap requires NonNull Optional
  }


  @Override
  public <A, B> @NonNull Kind<OptionalKind<?>, B> ap(@NonNull Kind<OptionalKind<?>, Function<A, B>> ff, @NonNull Kind<OptionalKind<?>, A> fa) {
    Optional<Function<A, B>> optF = unwrap(ff); // Handles null/invalid ff
    Optional<A> optA = unwrap(fa); // Handles null/invalid fa

    // If function Optional is present AND value Optional is present, apply function
    // Otherwise, return empty. Optional's flatMap/map handles this nicely.
    Optional<B> resultOpt = optF.flatMap(optA::map); // flatMap on function, map on value

    return wrap(resultOpt); // wrap requires NonNull Optional
  }

  // --- MonadError Methods ---

  /**
   * Lifts the error state (Optional.empty) into the Optional context.
   * The input 'error' (Void) is ignored.
   *
   * @param error The error value (Void, Nullable).
   * @param <A>   The phantom type parameter of the value.
   * @return An OptionalKind representing Optional.empty. (NonNull)
   */
  @Override
  public <A> @NonNull Kind<OptionalKind<?>, A> raiseError(@Nullable Void error) {
    // The error state for Optional is always Optional.empty
    return wrap(Optional.empty()); // wrap requires NonNull Optional
  }

  /**
   * Handles the error state (Optional.empty) within the Optional context.
   * If 'ma' contains a value, it's returned unchanged.
   * If 'ma' is empty, the 'handler' function is applied (with null input as error is Void).
   *
   * @param ma      The OptionalKind value. (NonNull)
   * @param handler Function Void -> Kind<OptionalKind<?>, A> to handle the empty state. (NonNull)
   * @param <A>     The type of the value within the Optional.
   * @return Original Kind if present, or result of handler if empty. (NonNull)
   */
  @Override
  public <A> @NonNull Kind<OptionalKind<?>, A> handleErrorWith(@NonNull Kind<OptionalKind<?>, A> ma, @NonNull Function<Void, Kind<OptionalKind<?>, A>> handler) {
    Optional<A> optional = unwrap(ma); // Handles null/invalid ma

    if (optional.isEmpty()) {
      // Apply the handler (passing null because the error type is Void)
      return handler.apply(null); // Handler must return NonNull Kind
    } else {
      // It's present, return the original Kind
      return ma; // ma is NonNull
    }
  }
}