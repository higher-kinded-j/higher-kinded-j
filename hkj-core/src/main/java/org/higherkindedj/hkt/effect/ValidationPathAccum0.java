// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;

/**
 * Entry stage of an accumulating {@code ValidationPath} assembly, obtained from {@link
 * Path#accumulate()}.
 *
 * <p>Generic in the error payload {@code X}, carried as {@code NonEmptyList<X>} with {@link
 * NonEmptyList#semigroup()} fixed for accumulation (the incoming path's own semigroup is normalised
 * away). All errors accumulate, in field-declaration order.
 */
public final class ValidationPathAccum0 {

  private static final ValidationPathAccum0 INSTANCE = new ValidationPathAccum0();

  private ValidationPathAccum0() {}

  /**
   * The stateless entry stage.
   *
   * @return the shared instance
   */
  public static ValidationPathAccum0 instance() {
    return INSTANCE;
  }

  /**
   * Adds the first validated field.
   *
   * @param value the validation path for the field; must not be null
   * @param <X> the error payload type
   * @param <A> the field type
   * @return the arity-1 stage
   * @throws NullPointerException if {@code value} is null
   */
  public <X, A> ValidationPathAccum1<X, A> and(ValidationPath<NonEmptyList<X>, A> value) {
    Objects.requireNonNull(value, "value must not be null");
    return new ValidationPathAccum1<>(Path.validatedNel(value.run()));
  }
}
