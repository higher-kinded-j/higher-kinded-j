// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trans.either_t;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to EitherT types and their Kind
 * representations. The methods are generic to handle the outer monad (F), left type (L), and right
 * type (R).
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface EitherTConverterOps {

  /**
   * Widens a concrete {@link EitherT EitherT&lt;F, L, R&gt;} instance into its higher-kinded
   * representation, {@code Kind<EitherTKind.Witness<F, L>, R>}.
   *
   * @param <F> The witness type of the outer monad in {@code EitherT}.
   * @param <L> The type of the 'left' value in the inner {@link
   *     org.higherkindedj.hkt.either.Either} of {@code EitherT}.
   * @param <R> The type of the 'right' value in the inner {@link
   *     org.higherkindedj.hkt.either.Either}.
   * @param eitherT The concrete {@link EitherT} instance to widen. Must not be null.
   * @return The {@code Kind} representation.
   * @throws NullPointerException if {@code eitherT} is {@code null}.
   */
  <F, L, R> @NonNull Kind<EitherTKind.Witness<F, L>, R> widen(@NonNull EitherT<F, L, R> eitherT);

  /**
   * Narrows a {@code Kind<EitherTKind.Witness<F, L>, R>} back to its concrete {@link EitherT
   * EitherT&lt;F, L, R&gt;} type.
   *
   * @param <F> The witness type of the outer monad in {@code EitherT}.
   * @param <L> The type of the 'left' value in the inner {@link
   *     org.higherkindedj.hkt.either.Either} of {@code EitherT}.
   * @param <R> The type of the 'right' value in the inner {@link
   *     org.higherkindedj.hkt.either.Either}.
   * @param kind The {@code Kind<EitherTKind.Witness<F, L>, R>} to narrow. Can be null.
   * @return The unwrapped, non-null {@link EitherT EitherT&lt;F, L, R&gt;} instance.
   * @throws KindUnwrapException if {@code kind} is null or not a valid {@link EitherT} instance.
   */
  <F, L, R> @NonNull EitherT<F, L, R> narrow(@Nullable Kind<EitherTKind.Witness<F, L>, R> kind);
}
