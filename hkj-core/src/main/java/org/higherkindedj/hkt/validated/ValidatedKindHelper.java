// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

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

  private static final Class<Validated> VALIDATED_CLASS = Validated.class;

  /**
   * Widens a {@link Validated} to its {@link Kind} representation. Implements {@link
   * ValidatedConverterOps#widen}.
   *
   * <p>The {@code @SuppressWarnings("unchecked")} is necessary because Java's type system doesn't
   * fully capture that {@code Validated<E, A>} is inherently a {@code
   * Kind<ValidatedKind.Witness<E>, A>} in this HKT emulation. This cast is fundamental to the HKT
   * pattern for {@code Validated} in this library.
   *
   * <p>Note: Unlike other KindHelpers, Validated doesn't use a holder because Valid and Invalid
   * already implement ValidatedKind directly.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <E, A> Kind<ValidatedKind.Witness<E>, A> widen(Validated<E, A> validated) {
    Validation.kind().requireForWiden(validated, VALIDATED_CLASS);
    return (Kind<ValidatedKind.Witness<E>, A>) validated;
  }

  /**
   * Narrows a {@link Kind} representation to a {@link Validated}. Implements {@link
   * ValidatedConverterOps#narrow}.
   *
   * <p>The {@code @SuppressWarnings("unchecked")} is for the explicit cast from a Kind back to a
   * more specific {@code Validated<E, A>}. This uses type checking to ensure the Kind instance is
   * indeed of the correct underlying Validated type.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <E, A> Validated<E, A> narrow(@Nullable Kind<ValidatedKind.Witness<E>, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, VALIDATED_CLASS);
  }

  /**
   * Creates a {@code Kind<ValidatedKind.Witness<E>, A>} representing a {@code Valid(value)}. This
   * is a convenience factory method specific to this helper enum.
   *
   * @param value The valid value. Must not be null.
   * @param <E> The error type (associated with the Witness).
   * @param <A> The value type.
   * @return A {@code Kind} representing a valid instance.
   * @throws NullPointerException if {@code value} is null.
   */
  public <E, A> Kind<ValidatedKind.Witness<E>, A> valid(A value) {
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
   * @throws NullPointerException if {@code error} is null.
   */
  public <E, A> Kind<ValidatedKind.Witness<E>, A> invalid(E error) {
    return this.widen(Validated.invalid(error));
  }

  /**
   * Widens a {@link Validated} to its {@link Kind2} representation. Implements {@link
   * ValidatedConverterOps#widen2}.
   *
   * <p>The {@code @SuppressWarnings("unchecked")} is necessary because Java's type system doesn't
   * fully capture that {@code Validated<E, A>} is inherently a {@code Kind2<ValidatedKind2.Witness,
   * E, A>} in this HKT emulation. This cast is fundamental to the HKT pattern for {@code Validated}
   * in this library.
   *
   * <p>Note: Like the Kind version, Validated doesn't use a holder because Valid and Invalid
   * already implement ValidatedKind2 directly.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <E, A> Kind2<ValidatedKind2.Witness, E, A> widen2(Validated<E, A> validated) {
    Validation.kind().requireForWiden(validated, VALIDATED_CLASS);
    return (Kind2<ValidatedKind2.Witness, E, A>) validated;
  }

  /**
   * Narrows a {@link Kind2} representation to a {@link Validated}. Implements {@link
   * ValidatedConverterOps#narrow2}.
   *
   * <p>The {@code @SuppressWarnings("unchecked")} is for the explicit cast from a Kind2 back to a
   * more specific {@code Validated<E, A>}. This uses type checking to ensure the Kind2 instance is
   * indeed of the correct underlying Validated type.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <E, A> Validated<E, A> narrow2(@Nullable Kind2<ValidatedKind2.Witness, E, A> kind) {
    if (kind == null) {
      throw new org.higherkindedj.hkt.exception.KindUnwrapException(
          "Cannot narrow null Kind2 for Validated");
    }
    if (!(kind instanceof Validated<?, ?>)) {
      throw new org.higherkindedj.hkt.exception.KindUnwrapException(
          "Kind2 instance cannot be narrowed to Validated (received: "
              + kind.getClass().getSimpleName()
              + ")");
    }
    return (Validated<E, A>) kind;
  }
}
