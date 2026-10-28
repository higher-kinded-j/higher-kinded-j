// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.effect.capability.Accumulating;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.effect.capability.Recoverable;

/**
 * Reaches a Path's run-time kind check, which typed code does not reach: each method widens a step
 * to any Path family with one unchecked cast, as a raw type or code compiled against an older
 * release does.
 */
public final class Mismatched {

  private Mismatched() {}

  @SuppressWarnings("unchecked") // the point: hand the Path a step of another family
  public static <A, P, B> Function<A, Chainable<P, B>> step(
      Function<A, ? extends Chainable<?, B>> step) {
    return (Function<A, Chainable<P, B>>) (Function<?, ?>) step;
  }

  @SuppressWarnings("unchecked") // the point: hand the Path a supplier of another family
  public static <P, B> Supplier<Chainable<P, B>> supplier(
      Supplier<? extends Chainable<?, B>> supplier) {
    return (Supplier<Chainable<P, B>>) (Supplier<?>) supplier;
  }

  @SuppressWarnings("unchecked") // the point: hand the Path another family to combine with
  public static <P, B> Combinable<P, B> other(Combinable<?, B> other) {
    return (Combinable<P, B>) other;
  }

  @SuppressWarnings("unchecked") // the point: accumulate with another family
  public static <P, E, B> Accumulating<P, E, B> accumulating(Accumulating<?, ?, B> other) {
    return (Accumulating<P, E, B>) other;
  }

  @SuppressWarnings("unchecked") // the point: recover into another family
  public static <E, P, A> Function<E, Recoverable<P, E, A>> recovery(
      Function<E, ? extends Recoverable<?, ?, A>> recovery) {
    return (Function<E, Recoverable<P, E, A>>) (Function<?, ?>) recovery;
  }

  @SuppressWarnings("unchecked") // the point: fall back to another family
  public static <P, E, A> Supplier<Recoverable<P, E, A>> alternative(
      Supplier<? extends Recoverable<?, ?, A>> alternative) {
    return (Supplier<Recoverable<P, E, A>>) (Supplier<?>) alternative;
  }
}
