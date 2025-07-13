// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;

/**
 * Enum implementing {@link ValidatedConverterOps} for widen/narrow operations, and providing
 * additional factory instance methods (valid, invalid) for {@link Validated} types.
 *
 * <p>Access these operations via the singleton {@code VALIDATED}. For example: {@code
 * ValidatedKindHelper.VALIDATED.widen(myValidatedValue);} Or, with static import: {@code import
 * static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED; VALIDATED.widen(...);}
 */
public enum ValidatedKindHelper implements ValidatedConverterOps {
  VALIDATED;

  /**
   * Widens a {@link Validated} to its {@link Kind} representation. Implements {@link
   * ValidatedConverterOps#widen}. The {@code @SuppressWarnings("unchecked")} is necessary because
   * Java's type system doesn't fully capture that {@code Validated<E, A>} is inherently a {@code
   * Kind<ValidatedKind.Witness<E>, A>} in this HKT emulation. This cast is fundamental to the HKT
   * pattern for {@code Validated} in this library.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <E, A> @NonNull Kind<ValidatedKind.Witness<E>, A> widen(
      @NonNull Validated<E, A> validated) {
    return (Kind<ValidatedKind.Witness<E>, A>) validated;
  }

  /**
   * Narrows a {@link Kind} representation to a {@link Validated}. Implements {@link
   * ValidatedConverterOps#narrow}. The {@code @SuppressWarnings("unchecked")} is for the explicit
   * cast from a Kind back to a more specific {@code Validated<E, A>}. Callers must ensure the Kind
   * instance is indeed of the correct underlying Validated type.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <E, A> @NonNull Validated<E, A> narrow(@NonNull Kind<ValidatedKind.Witness<E>, A> kind) {
    return (Validated<E, A>) kind;
  }

  /**
   * Creates a {@code Kind<ValidatedKind.Witness<E>, A>} representing a {@code Valid(value)}. This
   * is a convenience factory method specific to this helper enum.
   *
   * @param value The valid value. Must not be null.
   * @param <E> The error type (associated with the Witness).
   * @param <A> The value type.
   * @return A {@code Kind} representing a valid instance.
   */
  public <E, A> @NonNull Kind<ValidatedKind.Witness<E>, A> valid(@NonNull A value) {
    return this.widen(Validated.valid(value));
  }

  /**
   * Creates a {@code Kind<ValidatedKind.Witness<E>, A>} representing an {@code Invalid(error)}.
   * This is a convenience factory method specific to this helper enum.
   *
   * @param error The error value. Must not be null.
   * @param <E> The error type.
   * @param <A> The value type (associated with the Witness).
   * @return A {@code Kind} representing an invalid instance.
   */
  public <E, A> @NonNull Kind<ValidatedKind.Witness<E>, A> invalid(@NonNull E error) {
    return this.widen(Validated.invalid(error));
  }
}
