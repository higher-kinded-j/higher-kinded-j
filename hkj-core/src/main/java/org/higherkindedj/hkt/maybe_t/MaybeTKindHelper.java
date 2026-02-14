// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link MaybeTConverterOps} for widen/narrow operations for {@link MaybeT}
 * types.
 *
 * <p>Access these operations via the singleton {@code MAYBE_T}. For example: {@code
 * MaybeTKindHelper.MAYBE_T.widen(myMaybeTInstance);}
 */
public enum MaybeTKindHelper implements MaybeTConverterOps {
  MAYBE_T;

  private static final Class<MaybeT> MAYBE_T_CLASS = MaybeT.class;

  /**
   * Widens a concrete {@link MaybeT MaybeT&lt;F, A&gt;} instance into its {@link Kind}
   * representation, {@code Kind<MaybeTKind.Witness<F>, A>}. Implements {@link
   * MaybeTConverterOps#widen}.
   *
   * <p>Since {@link MaybeT} directly implements {@link MaybeTKind} (which extends {@code
   * Kind<MaybeTKind.Witness<F>, A>}), this method effectively performs a safe cast.
   *
   * @param <F> The witness type of the outer monad in {@code MaybeT}.
   * @param <A> The type of the value potentially held by the inner {@code Maybe}.
   * @param maybeT The concrete {@link MaybeT} instance to widen. Must not be null.
   * @return The {@code Kind} representation of the {@code maybeT}.
   * @throws NullPointerException if {@code maybeT} is null.
   */
  @Override
  public <F extends WitnessArity<TypeArity.Unary>, A> Kind<MaybeTKind.Witness<F>, A> widen(
      MaybeT<F, A> maybeT) {
    Validation.kind().requireForWiden(maybeT, MAYBE_T_CLASS);
    return maybeT;
  }

  /**
   * Narrows a {@code Kind<MaybeTKind.Witness<F>, A>} back to its concrete {@link MaybeT
   * MaybeT&lt;F, A&gt;} type. Implements {@link MaybeTConverterOps#narrow}.
   *
   * @param <F> The witness type of the outer monad in {@code MaybeT}.
   * @param <A> The type of the value potentially held by the inner {@code Maybe}.
   * @param kind The {@code Kind<MaybeTKind.Witness<F>, A>} to narrow. Can be null.
   * @return The unwrapped, non-null {@link MaybeT MaybeT&lt;F, A&gt;} instance.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is null or not a
   *     valid {@link MaybeT} instance.
   */
  @Override
  public <F extends WitnessArity<TypeArity.Unary>, A> MaybeT<F, A> narrow(
      @Nullable Kind<MaybeTKind.Witness<F>, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, MAYBE_T_CLASS);
  }
}
