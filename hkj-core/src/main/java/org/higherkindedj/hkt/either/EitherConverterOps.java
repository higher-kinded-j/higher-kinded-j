// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to Either types and their Kind
 * representations. The methods are generic to handle the left (L) and right (R) types.
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, offering
 * these operations as instance methods.
 */
public interface EitherConverterOps {

  /**
   * Widens a concrete {@link Either Either&lt;L, R&gt;} instance into its higher-kinded
   * representation, {@code Kind<EitherKind.Witness<L>, R>}.
   *
   * @param <L> The type of the "Left" value of the {@code Either}.
   * @param <R> The type of the "Right" value of the {@code Either}.
   * @param either The non-null, concrete {@code Either<L, R>} instance to widen.
   * @return A non-null {@code Kind<EitherKind.Witness<L>, R>} representing the wrapped {@code
   *     Either}.
   * @throws NullPointerException if {@code either} is {@code null}.
   */
  <L, R> Kind<EitherKind.Witness<L>, R> widen(Either<L, R> either);

  /**
   * Narrows a {@code Kind<EitherKind.Witness<L>, R>} back to its concrete {@code Either<L, R>}
   * type.
   *
   * @param <L> The type of the "Left" value of the target {@code Either}.
   * @param <R> The type of the "Right" value of the target {@code Either}.
   * @param kind The {@code Kind<EitherKind.Witness<L>, R>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@code Either<L, R>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null} or not a representation
   *     of an {@code Either<L,R>}.
   */
  <L, R> Either<L, R> narrow(@Nullable Kind<EitherKind.Witness<L>, R> kind);

  /**
   * Widens a concrete {@link Either Either&lt;L, R&gt;} instance into its Kind2 representation,
   * {@code Kind2<EitherKind2.Witness, L, R>}.
   *
   * <p>This is used for bifunctor operations where both the left and right types can vary.
   *
   * @param <L> The type of the "Left" value of the {@code Either}.
   * @param <R> The type of the "Right" value of the {@code Either}.
   * @param either The non-null, concrete {@code Either<L, R>} instance to widen.
   * @return A non-null {@code Kind2<EitherKind2.Witness, L, R>} representing the wrapped {@code
   *     Either}.
   * @throws NullPointerException if {@code either} is {@code null}.
   */
  <L, R> Kind2<EitherKind2.Witness, L, R> widen2(Either<L, R> either);

  /**
   * Narrows a {@code Kind2<EitherKind2.Witness, L, R>} back to its concrete {@code Either<L, R>}
   * type.
   *
   * @param <L> The type of the "Left" value of the target {@code Either}.
   * @param <R> The type of the "Right" value of the target {@code Either}.
   * @param kind The {@code Kind2<EitherKind2.Witness, L, R>} instance to narrow. May be {@code
   *     null}.
   * @return The underlying, non-null {@code Either<L, R>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null} or not a representation
   *     of an {@code Either<L,R>}.
   */
  <L, R> Either<L, R> narrow2(@Nullable Kind2<EitherKind2.Witness, L, R> kind);
}
