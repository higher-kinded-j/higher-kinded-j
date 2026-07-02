// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import java.util.Objects;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;

/**
 * Entry stage of a tolerant accumulating {@code EitherOrBoth} assembly, obtained from {@link
 * EitherOrBoth#accumulate()}.
 *
 * <p>Generic in the warning payload {@code X}, carried as {@code NonEmptyList<X>}. Warnings
 * accumulate ({@code Both}) while the value keeps flowing; any {@code Left} makes the whole
 * assembly {@code Left}, still with every warning collected, in field-declaration order.
 */
public final class EitherOrBothAccum0 {

  private static final EitherOrBothAccum0 INSTANCE = new EitherOrBothAccum0();

  private EitherOrBothAccum0() {}

  /**
   * The stateless entry stage.
   *
   * @return the shared instance
   */
  public static EitherOrBothAccum0 instance() {
    return INSTANCE;
  }

  /**
   * Adds the first field.
   *
   * @param value the field's {@code EitherOrBoth}; must not be null
   * @param <X> the warning payload type
   * @param <A> the field type
   * @return the arity-1 stage
   * @throws NullPointerException if {@code value} is null
   */
  public <X, A> EitherOrBothAccum1<X, A> and(EitherOrBoth<NonEmptyList<X>, A> value) {
    Objects.requireNonNull(value, "value must not be null");
    return new EitherOrBothAccum1<>(value);
  }
}
