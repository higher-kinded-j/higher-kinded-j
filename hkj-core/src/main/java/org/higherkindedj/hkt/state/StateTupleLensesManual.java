// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import org.higherkindedj.optics.Lens;

/**
 * A container for static methods that create lenses for {@link StateTuple}. This class is manually
 * created to avoid cyclic dependencies between the core and processor modules.
 */
public final class StateTupleLensesManual {

  private StateTupleLensesManual() {
    // private constructor for utility class
  }

  /** Creates a Lens that focuses on the 'value' component of a StateTuple. */
  public static <S, A> Lens<StateTuple<S, A>, A> value() {
    return Lens.of(
        StateTuple::value, (tuple, newValue) -> new StateTuple<>(newValue, tuple.state()));
  }

  /** Creates a Lens that focuses on the 'state' component of a StateTuple. */
  public static <S, A> Lens<StateTuple<S, A>, S> state() {
    return Lens.of(
        StateTuple::state, (tuple, newState) -> new StateTuple<>(tuple.value(), newState));
  }
}
