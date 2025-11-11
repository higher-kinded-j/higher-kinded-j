// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import org.higherkindedj.hkt.Kind2;
import org.jspecify.annotations.NullMarked;

/**
 * Wrapper for {@link Either} to work with the {@link Kind2} system for bifunctor operations.
 *
 * <p>This representation treats {@link Either} as a type constructor with two type parameters, both
 * of which can vary. This enables bifunctor operations where both the left (error) and right
 * (success) types can be transformed independently.
 *
 * <p>This is distinct from {@link EitherKind}, which fixes the left type parameter for use with
 * {@link org.higherkindedj.hkt.Functor} and {@link org.higherkindedj.hkt.Monad} instances.
 *
 * @param <L> The type of the Left value (error/alternative channel)
 * @param <R> The type of the Right value (success channel)
 * @see Either
 * @see EitherKind
 * @see org.higherkindedj.hkt.Bifunctor
 */
@NullMarked
public final class EitherKind2<L, R> implements Kind2<EitherKind2.Witness, L, R> {

  /** Witness type for the Either type constructor when used as a bifunctor. */
  public static final class Witness {}

  private final Either<L, R> either;

  EitherKind2(Either<L, R> either) {
    this.either = either;
  }

  Either<L, R> getEither() {
    return either;
  }
}
