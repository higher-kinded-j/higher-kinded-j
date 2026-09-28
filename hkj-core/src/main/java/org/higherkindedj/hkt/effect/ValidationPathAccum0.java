// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.function.Supplier;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.Validated;

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

  /**
   * Completes an assembly of no fields: with nothing to fail, the result is {@code f}'s value.
   *
   * <p>{@code f} runs whatever it is handed, so an exception it throws escapes the assembly.
   *
   * @param f supplies the assembled value; must not be null
   * @param <X> the error type, fixed by where the result is used
   * @param <R> the assembled type
   * @return {@code f}'s value, as a valid result
   * @throws NullPointerException if {@code f} is null
   */
  public <X, R> ValidationPath<NonEmptyList<X>, R> apply(Supplier<? extends R> f) {
    Objects.requireNonNull(f, "f must not be null");
    return Path.validatedNel(Validated.validNel(f.get()));
  }
}
