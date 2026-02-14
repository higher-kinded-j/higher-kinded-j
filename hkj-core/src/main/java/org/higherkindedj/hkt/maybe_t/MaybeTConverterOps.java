// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to MaybeT types and their Kind
 * representations. The methods are generic to handle the outer monad (F) and value type (A).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface MaybeTConverterOps {

  /**
   * Widens a concrete {@link MaybeT MaybeT&lt;F, A&gt;} instance into its higher-kinded
   * representation, {@code Kind<MaybeTKind.Witness<F>, A>}.
   *
   * @param <F> The witness type of the outer monad in {@code MaybeT}.
   * @param <A> The type of the value potentially held by the inner {@code Maybe}.
   * @param maybeT The concrete {@link MaybeT} instance to widen. Must not be null.
   * @return The {@code Kind} representation.
   * @throws NullPointerException if {@code maybeT} is {@code null}.
   */
  <F extends WitnessArity<TypeArity.Unary>, A> Kind<MaybeTKind.Witness<F>, A> widen(
      MaybeT<F, A> maybeT);

  /**
   * Narrows a {@code Kind<MaybeTKind.Witness<F>, A>} back to its concrete {@link MaybeT
   * MaybeT&lt;F, A&gt;} type.
   *
   * @param <F> The witness type of the outer monad in {@code MaybeT}.
   * @param <A> The type of the value potentially held by the inner {@code Maybe}.
   * @param kind The {@code Kind<MaybeTKind.Witness<F>, A>} to narrow. Can be null.
   * @return The unwrapped, non-null {@link MaybeT MaybeT&lt;F, A&gt;} instance.
   * @throws KindUnwrapException if {@code kind} is null or not a valid {@link MaybeT} instance.
   */
  <F extends WitnessArity<TypeArity.Unary>, A> MaybeT<F, A> narrow(
      @Nullable Kind<MaybeTKind.Witness<F>, A> kind);
}
