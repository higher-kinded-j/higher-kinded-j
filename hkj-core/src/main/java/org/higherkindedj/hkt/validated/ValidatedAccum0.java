// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import java.util.Objects;
import java.util.function.Supplier;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;

/**
 * Entry stage of an accumulating {@code Validated} assembly, obtained from {@link
 * Validated#accumulate()}.
 *
 * <p>Generic in the error payload {@code X}, carried as {@code NonEmptyList<X>}; the first {@code
 * and(value)} fixes {@code X}. All errors accumulate, in field-declaration order.
 */
public final class ValidatedAccum0 {

  private static final ValidatedAccum0 INSTANCE = new ValidatedAccum0();

  private ValidatedAccum0() {}

  /**
   * The stateless entry stage.
   *
   * @return the shared instance
   */
  public static ValidatedAccum0 instance() {
    return INSTANCE;
  }

  /**
   * Adds the first validated field.
   *
   * @param value the validated field; must not be null
   * @param <X> the error payload type
   * @param <A> the field type
   * @return the arity-1 stage
   * @throws NullPointerException if {@code value} is null
   */
  public <X, A> ValidatedAccum1<X, A> and(Validated<NonEmptyList<X>, A> value) {
    Objects.requireNonNull(value, "value must not be null");
    return new ValidatedAccum1<>(value);
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
  public <X, R> Validated<NonEmptyList<X>, R> apply(Supplier<? extends R> f) {
    Objects.requireNonNull(f, "f must not be null");
    return Validated.validNel(f.get());
  }
}
