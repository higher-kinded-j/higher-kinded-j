// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe_t;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
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

  // Error Messages
  /** Error message for attempting to narrow a null Kind. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot narrow null Kind for MaybeT";

  /** Error message for attempting to narrow a Kind of an unexpected type. */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a MaybeT: ";

  public static final String INVALID_KIND_TYPE_NULL_MSG = "Input MaybeT cannot be null for widen";

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
  public <F, A> @NonNull Kind<MaybeTKind.Witness<F>, A> widen(@NonNull MaybeT<F, A> maybeT) {
    Objects.requireNonNull(maybeT, INVALID_KIND_TYPE_NULL_MSG);
    // maybeT is already a MaybeTKind<F, A>, which is a Kind<MaybeTKind.Witness<F>, A>.
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
   * @throws KindUnwrapException if {@code kind} is null or not a valid {@link MaybeT} instance.
   */
  @Override
  public <F, A> @NonNull MaybeT<F, A> narrow(@Nullable Kind<MaybeTKind.Witness<F>, A> kind) {
    if (kind == null) {
      throw new KindUnwrapException(MaybeTKindHelper.INVALID_KIND_NULL_MSG);
    }
    // Since MaybeT<F,A> implements MaybeTKind<F,A>, which extends Kind<MaybeTKind.Witness<F>,A>
    if (kind instanceof MaybeT) {
      return (MaybeT<F, A>) kind;
    }
    throw new KindUnwrapException(
        MaybeTKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
  }
}
