// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * {@link Bifunctor} instance for {@link Either}.
 *
 * <p>This instance enables transformation of both the left (error) and right (success) channels of
 * an {@link Either} independently. Both type parameters are covariant.
 *
 * <p>Examples:
 *
 * <pre>{@code
 * Either<String, Integer> either = Either.right(42);
 * Either<Integer, String> transformed = EitherBifunctor.INSTANCE.bimap(
 *     String::length,     // Transform left (not applied here)
 *     n -> "Value: " + n, // Transform right: 42 -> "Value: 42"
 *     EITHER.widen2(either)
 * );
 * }</pre>
 */
@NullMarked
public class EitherBifunctor implements Bifunctor<EitherKind2.Witness> {

  /** Singleton instance of the EitherBifunctor. */
  public static final EitherBifunctor INSTANCE = new EitherBifunctor();

  private EitherBifunctor() {}

  @Override
  public <A, B, C, D> Kind2<EitherKind2.Witness, C, D> bimap(
      Function<? super A, ? extends C> f,
      Function<? super B, ? extends D> g,
      Kind2<EitherKind2.Witness, A, B> fab) {

    Validation.function().requireMapper(f, "f", EitherBifunctor.class, BIMAP);
    Validation.function().requireMapper(g, "g", EitherBifunctor.class, BIMAP);
    Objects.requireNonNull(fab, "Kind for EitherBifunctor.bimap cannot be null");

    Either<A, B> either = EITHER.narrow2(fab);
    Either<C, D> result = either.bimap(f, g);
    return EITHER.widen2(result);
  }

  @Override
  public <A, B, C> Kind2<EitherKind2.Witness, C, B> first(
      Function<? super A, ? extends C> f, Kind2<EitherKind2.Witness, A, B> fab) {

    Validation.function().requireMapper(f, "f", EitherBifunctor.class, FIRST);
    Objects.requireNonNull(fab, "Kind for EitherBifunctor.first cannot be null");

    Either<A, B> either = EITHER.narrow2(fab);
    Either<C, B> result = either.mapLeft(f);
    return EITHER.widen2(result);
  }

  @Override
  public <A, B, D> Kind2<EitherKind2.Witness, A, D> second(
      Function<? super B, ? extends D> g, Kind2<EitherKind2.Witness, A, B> fab) {

    Validation.function().requireMapper(g, "g", EitherBifunctor.class, SECOND);
    Objects.requireNonNull(fab, "Kind for EitherBifunctor.second cannot be null");

    Either<A, B> either = EITHER.narrow2(fab);
    Either<A, D> result = either.mapRight(g);
    return EITHER.widen2(result);
  }
}
