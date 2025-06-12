// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional_t;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
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

  /** Error message for attempting to narrow a null Kind. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot narrow null Kind for OptionalT";

  /** Error message for attempting to narrow a Kind of an unexpected type. */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an OptionalT: ";

  public static final String INVALID_KIND_TYPE_NULL_MSG =
      "Input OptionalT cannot be null for widen";

  /**
   * Widens a concrete {@link OptionalT OptionalT&lt;F, A&gt;} instance into its {@link Kind}
   * representation, {@code Kind<OptionalTKind.Witness<F>, A>}. Implements {@link
   * OptionalTConverterOps#widen}.
   *
   * <p>Since {@link OptionalT} directly implements {@link OptionalTKind} (which extends {@code
   * Kind<OptionalTKind.Witness<F>, A>}), this method effectively performs a safe cast.
   *
   * @param <F> The witness type of the outer monad in {@code OptionalT}.
   * @param <A> The type of the value potentially held by the inner {@link java.util.Optional}.
   * @param optionalT The concrete {@link OptionalT} instance to widen. Must not be null.
   * @return The {@code Kind} representation of the {@code optionalT}.
   * @throws NullPointerException if {@code optionalT} is null.
   */
  @Override
  public <F, A> @NonNull Kind<OptionalTKind.Witness<F>, A> widen(
      @NonNull OptionalT<F, A> optionalT) {
    Objects.requireNonNull(optionalT, INVALID_KIND_TYPE_NULL_MSG);
    // optionalT is already an OptionalTKind<F, A>, which is a Kind<OptionalTKind.Witness<F>, A>.
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
   * @throws KindUnwrapException if {@code kind} is null or not a valid {@link OptionalT} instance.
   */
  @Override
  public <F, A> @NonNull OptionalT<F, A> narrow(@Nullable Kind<OptionalTKind.Witness<F>, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(OptionalTKindHelper.INVALID_KIND_NULL_MSG);
      // Since OptionalT<F,A> implements OptionalTKind<F,A>,
      // which extends Kind<OptionalTKind.Witness<F>,A>
      case OptionalT<F, A> directOptionalT -> directOptionalT;
      default ->
          throw new KindUnwrapException(
              OptionalTKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }
}
