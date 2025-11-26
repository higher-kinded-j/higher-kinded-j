/**
 * Provides components for the {@code Writer} monad and its simulation as a Higher-Kinded Type. The
 * {@code Writer} monad is used for computations that produce a value along with some accumulated
 * output (e.g., logs). Includes the {@link org.higherkindedj.hkt.writer.Writer} type, {@link
 * org.higherkindedj.hkt.writer.WriterKind}, {@link org.higherkindedj.hkt.writer.WriterMonad}, and
 * helper utilities. Requires a {@link org.higherkindedj.hkt.Monoid} for the output type.
 */
@NullMarked
package org.higherkindedj.hkt.writer;

import org.jspecify.annotations.NullMarked;
