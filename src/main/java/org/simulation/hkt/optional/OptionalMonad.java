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
    Optional<B> resultOpt = optF.flatMap(f -> optA.map(f)); // flatMap on function, map on value

    return wrap(resultOpt);
  }

}
