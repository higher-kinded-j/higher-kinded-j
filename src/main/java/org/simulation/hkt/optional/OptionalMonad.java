package org.simulation.hkt.optional;

import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;

import java.util.Optional;
import java.util.function.Function;
import static org.simulation.hkt.optional.OptionalKindHelper.*;

/**
 * Monad implementation for OptionalKind.
 */
public class OptionalMonad extends OptionalFunctor implements Monad<OptionalKind<?>> {

  // --- Monad Operations ---

  @Override
  public <A> OptionalKind<A> pure(A value) {
    // Lifts a value into Optional context. Use ofNullable for safety.
    return wrap(Optional.ofNullable(value));
  }

  @Override
  public <A, B> OptionalKind<B> flatMap(Function<A, Kind<OptionalKind<?>, B>> f, Kind<OptionalKind<?>, A> ma) {
    Optional<A> optA = unwrap(ma);

    // Optional.flatMap handles the unwrapping/wrapping of the result of f naturally
    Optional<B> resultOpt = optA.flatMap(a -> {
      Kind<OptionalKind<?>, B> kindB = f.apply(a); // f returns OptionalKind<B>
      return unwrap(kindB); // We need to unwrap kindB to get Optional<B> for Optional.flatMap
    });

    return wrap(resultOpt); // Wrap the final Optional<B>
  }


}
