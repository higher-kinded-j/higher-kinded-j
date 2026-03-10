// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Provides the Writer monad transformer ({@code WriterT}) and its supporting types.
 *
 * <p>The {@code WriterT<F, W, A>} monad transformer adds output accumulation capabilities to an
 * underlying monad {@code F}. The output type {@code W} must form a {@link
 * org.higherkindedj.hkt.Monoid} for combining outputs during monadic composition.
 *
 * <p>Key types:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.writer_t.WriterT} - The transformer record wrapping {@code
 *       Kind<F, Pair<A, W>>}.
 *   <li>{@link org.higherkindedj.hkt.writer_t.WriterTKind} - HKT witness interface.
 *   <li>{@link org.higherkindedj.hkt.writer_t.WriterTKindHelper} - Widen/narrow operations.
 *   <li>{@link org.higherkindedj.hkt.writer_t.WriterTMonad} - Monad and MonadWriter instance.
 * </ul>
 *
 * @see org.higherkindedj.hkt.MonadWriter
 * @see org.higherkindedj.hkt.Monoid
 * @see org.higherkindedj.hkt.Pair
 */
package org.higherkindedj.hkt.writer_t;
