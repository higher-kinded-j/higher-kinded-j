/**
 * Provides components for the {@code IO} monad and its simulation as a Higher-Kinded Type. The
 * {@code IO} monad is used for encapsulating side-effecting computations.
 *
 * <p>{@code IO<A>} directly implements {@link org.higherkindedj.hkt.io.IOKind}, allowing it to
 * participate in the HKT simulation without requiring wrapper types. This means that {@code widen}
 * and {@code narrow} operations are simple type-safe casts rather than object wrapping.
 *
 * <p>Includes the {@link org.higherkindedj.hkt.io.IO} type, {@link
 * org.higherkindedj.hkt.io.IOKind}, {@link org.higherkindedj.hkt.io.IOMonad}, and helper utilities.
 */
@org.jspecify.annotations.NullMarked
package org.higherkindedj.hkt.io;
