/**
 * Provides components for the {@code Either} type and its simulation as a Higher-Kinded Type.
 * {@code Either} represents a value of one of two possible types, typically used for error
 * handling.
 *
 * <p>{@link org.higherkindedj.hkt.either.Either.Left} and {@link
 * org.higherkindedj.hkt.either.Either.Right} directly implement {@link
 * org.higherkindedj.hkt.either.EitherKind} and {@link org.higherkindedj.hkt.either.EitherKind2},
 * allowing them to participate in the HKT simulation without requiring wrapper types. This means
 * that {@code widen} and {@code narrow} operations are simple type-safe casts rather than object
 * wrapping.
 *
 * <p>Includes {@link org.higherkindedj.hkt.either.Either}, {@link
 * org.higherkindedj.hkt.either.EitherKind}, {@link org.higherkindedj.hkt.either.EitherMonad}, and
 * helpers.
 */
@org.jspecify.annotations.NullMarked
package org.higherkindedj.hkt.either;
