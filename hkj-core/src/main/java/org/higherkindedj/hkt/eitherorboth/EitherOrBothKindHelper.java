// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link EitherOrBothConverterOps} for widen/narrow operations on {@link
 * EitherOrBoth}.
 *
 * <p>Access these operations via the singleton {@code EITHER_OR_BOTH}. For example: {@code
 * EitherOrBothKindHelper.EITHER_OR_BOTH.widen(EitherOrBoth.right("value"));} Or, with static
 * import: {@code import static
 * org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;
 * EITHER_OR_BOTH.widen(...);}
 */
public enum EitherOrBothKindHelper implements EitherOrBothConverterOps {
  EITHER_OR_BOTH;

  private static final Class<EitherOrBoth> EITHER_OR_BOTH_CLASS = EitherOrBoth.class;

  /**
   * Widens a concrete {@code EitherOrBoth<L, R>} into its HKT representation, {@code
   * Kind<EitherOrBothKind.Witness<L>, R>}.
   *
   * <p>Since {@code EitherOrBoth} extends {@code EitherOrBothKind}, this is a cast-free upcast.
   *
   * @param <L> the left type
   * @param <R> the right type
   * @param eitherOrBoth the instance to widen; must be non-null
   * @return the {@code Kind} representation of {@code eitherOrBoth}
   * @throws NullPointerException if {@code eitherOrBoth} is null
   */
  @Override
  public <L, R> Kind<EitherOrBothKind.Witness<L>, R> widen(EitherOrBoth<L, R> eitherOrBoth) {
    Validation.kind().requireForWiden(eitherOrBoth, EITHER_OR_BOTH_CLASS);
    return eitherOrBoth;
  }

  /**
   * Narrows a {@code Kind<EitherOrBothKind.Witness<L>, R>} back to a concrete {@code
   * EitherOrBoth<L, R>}.
   *
   * <p>Since the {@code Left}/{@code Right}/{@code Both} records directly implement {@code
   * EitherOrBothKind}, this is a direct type check and cast with no holder to unwrap.
   *
   * @param <L> the left type
   * @param <R> the right type
   * @param kind the {@code Kind} to narrow; may be null
   * @return the underlying, non-null {@code EitherOrBoth<L, R>}
   * @throws KindUnwrapException if {@code kind} is null or not an {@code EitherOrBoth}
   */
  @Override
  @SuppressWarnings("unchecked") // raw Class token; runtime-checked via Class.isInstance
  public <L, R> EitherOrBoth<L, R> narrow(@Nullable Kind<EitherOrBothKind.Witness<L>, R> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, EITHER_OR_BOTH_CLASS);
  }

  /**
   * Widens a concrete {@code EitherOrBoth<L, R>} into its Kind2 representation, {@code
   * Kind2<EitherOrBothKind2.Witness, L, R>}.
   *
   * <p>Since {@code EitherOrBoth} extends {@code EitherOrBothKind2}, this is a cast-free upcast.
   *
   * @param <L> the left type
   * @param <R> the right type
   * @param eitherOrBoth the instance to widen; must be non-null
   * @return the {@code Kind2} representation of {@code eitherOrBoth}
   * @throws NullPointerException if {@code eitherOrBoth} is null
   */
  @Override
  public <L, R> Kind2<EitherOrBothKind2.Witness, L, R> widen2(EitherOrBoth<L, R> eitherOrBoth) {
    Validation.kind().requireForWiden(eitherOrBoth, EITHER_OR_BOTH_CLASS);
    return eitherOrBoth;
  }

  /**
   * Narrows a {@code Kind2<EitherOrBothKind2.Witness, L, R>} back to a concrete {@code
   * EitherOrBoth<L, R>}.
   *
   * @param <L> the left type
   * @param <R> the right type
   * @param kind the {@code Kind2} to narrow; may be null
   * @return the underlying, non-null {@code EitherOrBoth<L, R>}
   * @throws KindUnwrapException if {@code kind} is null or not an {@code EitherOrBoth}
   */
  @Override
  @SuppressWarnings("unchecked") // runtime-checked via the instanceof guard above
  public <L, R> EitherOrBoth<L, R> narrow2(@Nullable Kind2<EitherOrBothKind2.Witness, L, R> kind) {
    if (kind == null) {
      throw new KindUnwrapException("Cannot narrow null Kind2 for EitherOrBoth");
    }
    if (!(kind instanceof EitherOrBoth<?, ?>)) {
      throw new KindUnwrapException(
          "Kind2 instance cannot be narrowed to EitherOrBoth (received: "
              + kind.getClass().getSimpleName()
              + ")");
    }
    return (EitherOrBoth<L, R>) kind;
  }
}
