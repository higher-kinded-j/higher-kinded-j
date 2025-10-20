// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link OptionalTConverterOps} for widen/narrow operations for {@link OptionalT}
 * types.
 *
 * <p>Access these operations via the singleton {@code OPTIONAL_T}. For example: {@code
 * OptionalTKindHelper.OPTIONAL_T.widen(myOptionalTInstance);}
 */
public enum OptionalTKindHelper implements OptionalTConverterOps {
  OPTIONAL_T;

  private static final Class<OptionalT> OPTIONAL_T_CLASS = OptionalT.class;

  /**
   * Widens a concrete {@link OptionalT OptionalT&lt;F, A&gt;} instance into its higher-kinded
   * representation, {@code Kind<OptionalTKind.Witness<F>, A>}. Implements {@link
   * OptionalTConverterOps#widen}.
   *
   * <p>Since {@link OptionalT} directly implements {@link OptionalTKind} (which extends {@code
   * Kind<OptionalTKind.Witness<F>, A>}), this method effectively performs a safe cast.
   *
   * @param <F> The witness type of the outer monad in {@code OptionalT}.
   * @param <A> The type of the value potentially held by the inner {@link java.util.Optional}.
   * @param optionalT The concrete {@link OptionalT} instance to widen. Must not be null.
   * @return The {@code Kind} representation.
   * @throws NullPointerException if {@code optionalT} is {@code null}.
   */
  @Override
  public <F, A> Kind<OptionalTKind.Witness<F>, A> widen(OptionalT<F, A> optionalT) {
    Validation.kind().requireForWiden(optionalT, OPTIONAL_T_CLASS);
    return optionalT;
  }

  /**
   * Narrows a {@code Kind<OptionalTKind.Witness<F>, A>} back to its concrete {@link OptionalT
   * OptionalT&lt;F, A&gt;} type. Implements {@link OptionalTConverterOps#narrow}.
   *
   * @param <F> The witness type of the outer monad in {@code OptionalT}.
   * @param <A> The type of the value potentially held by the inner {@link java.util.Optional}.
   * @param kind The {@code Kind<OptionalTKind.Witness<F>, A>} to narrow. Can be null.
   * @return The unwrapped, non-null {@link OptionalT OptionalT&lt;F, A&gt;} instance.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is null or not a
   *     valid {@link OptionalT} instance.
   */
  @Override
  public <F, A> OptionalT<F, A> narrow(@Nullable Kind<OptionalTKind.Witness<F>, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, OPTIONAL_T_CLASS);
  }
}
