// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Defines conversion operations (widen and narrow) specific to {@link EitherOrBoth} types and their
 * Kind representations. The methods are generic over the left ({@code L}) and right ({@code R})
 * types.
 *
 * <p>This interface is intended to be implemented by a service provider, such as an enum, exposing
 * these operations as instance methods.
 */
public interface EitherOrBothConverterOps {

  /**
   * Widens a concrete {@code EitherOrBoth<L, R>} into its higher-kinded representation, {@code
   * Kind<EitherOrBothKind.Witness<L>, R>}.
   *
   * @param <L> the left type
   * @param <R> the right type
   * @param eitherOrBoth the non-null instance to widen
   * @return a non-null {@code Kind<EitherOrBothKind.Witness<L>, R>}
   * @throws NullPointerException if {@code eitherOrBoth} is null
   */
  <L, R> Kind<EitherOrBothKind.Witness<L>, R> widen(EitherOrBoth<L, R> eitherOrBoth);

  /**
   * Narrows a {@code Kind<EitherOrBothKind.Witness<L>, R>} back to a concrete {@code
   * EitherOrBoth<L, R>}.
   *
   * @param <L> the left type
   * @param <R> the right type
   * @param kind the instance to narrow; may be null
   * @return the underlying, non-null {@code EitherOrBoth<L, R>}
   * @throws KindUnwrapException if {@code kind} is null or not an {@code EitherOrBoth}
   */
  <L, R> EitherOrBoth<L, R> narrow(@Nullable Kind<EitherOrBothKind.Witness<L>, R> kind);

  /**
   * Widens a concrete {@code EitherOrBoth<L, R>} into its Kind2 representation, {@code
   * Kind2<EitherOrBothKind2.Witness, L, R>}, for bifunctor operations.
   *
   * @param <L> the left type
   * @param <R> the right type
   * @param eitherOrBoth the non-null instance to widen
   * @return a non-null {@code Kind2<EitherOrBothKind2.Witness, L, R>}
   * @throws NullPointerException if {@code eitherOrBoth} is null
   */
  <L, R> Kind2<EitherOrBothKind2.Witness, L, R> widen2(EitherOrBoth<L, R> eitherOrBoth);

  /**
   * Narrows a {@code Kind2<EitherOrBothKind2.Witness, L, R>} back to a concrete {@code
   * EitherOrBoth<L, R>}.
   *
   * @param <L> the left type
   * @param <R> the right type
   * @param kind the instance to narrow; may be null
   * @return the underlying, non-null {@code EitherOrBoth<L, R>}
   * @throws KindUnwrapException if {@code kind} is null or not an {@code EitherOrBoth}
   */
  <L, R> EitherOrBoth<L, R> narrow2(@Nullable Kind2<EitherOrBothKind2.Witness, L, R> kind);
}
