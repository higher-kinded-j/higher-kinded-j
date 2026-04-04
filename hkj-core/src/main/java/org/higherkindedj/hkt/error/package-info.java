/**
 * Provides the {@link org.higherkindedj.hkt.error.ErrorOp} effect algebra for raising business
 * errors within Free monad programs.
 *
 * <p>{@code ErrorOp<E, A>} is a library-provided effect that composes via {@link
 * org.higherkindedj.hkt.eitherf.EitherF} like any other effect algebra. Programs that need to
 * explicitly raise typed business errors include it in their effect set. Programs that only need to
 * recover from interpreter failures (e.g. gateway timeouts) do NOT need ErrorOp.
 */
@NullMarked
package org.higherkindedj.hkt.error;

import org.jspecify.annotations.NullMarked;
