/**
 * Provides components for the {@code Validated} type and its simulation as a Higher-Kinded Type.
 * {@code Validated} is a data type similar to {@code Either} but is typically used in contexts
 * where errors can be accumulated (when used with an Applicative that supports it).
 *
 * <p>Includes the {@link org.higherkindedj.hkt.validated.Validated} interface, its implementations
 * {@link org.higherkindedj.hkt.validated.Valid} and {@link
 * org.higherkindedj.hkt.validated.Invalid}, the HKT wrapper {@link
 * org.higherkindedj.hkt.validated.ValidatedKind}, the {@link
 * org.higherkindedj.hkt.validated.ValidatedMonad} instance, and helper utilities.
 */
@org.jspecify.annotations.NullMarked
package org.higherkindedj.hkt.validated;
