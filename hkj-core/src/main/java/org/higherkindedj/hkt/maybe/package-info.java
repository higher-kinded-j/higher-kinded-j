/**
 * Provides components for the {@code Maybe} type and its simulation as a Higher-Kinded Type. {@code
 * Maybe} is a custom type similar to Optional, representing a value that might be absent.
 *
 * <p>{@link org.higherkindedj.hkt.maybe.Just} and {@link org.higherkindedj.hkt.maybe.Nothing}
 * directly implement {@link org.higherkindedj.hkt.maybe.MaybeKind}, allowing them to participate in
 * the HKT simulation without requiring wrapper types. This means that {@code widen} and {@code
 * narrow} operations are simple type-safe casts rather than object wrapping.
 *
 * <p>Includes {@link org.higherkindedj.hkt.maybe.Maybe}, {@link
 * org.higherkindedj.hkt.maybe.MaybeKind}, {@link org.higherkindedj.hkt.maybe.MaybeMonad}, and
 * helpers.
 */
@NullMarked
package org.higherkindedj.hkt.maybe;

import org.jspecify.annotations.NullMarked;
