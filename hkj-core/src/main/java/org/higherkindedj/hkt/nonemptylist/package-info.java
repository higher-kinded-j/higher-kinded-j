// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Provides {@link org.higherkindedj.hkt.nonemptylist.NonEmptyList}, an immutable list that is
 * guaranteed to hold at least one element.
 *
 * <p>Because non-emptiness is encoded in the type, operations that are partial on an ordinary
 * {@link java.util.List} — {@code head}, {@code last}, {@code reduce}, {@code min}, {@code max} —
 * are <em>total</em> here: they always return a value and never throw. This makes {@code
 * NonEmptyList} the natural carrier for any result that must contain at least one element, most
 * notably the error channel of accumulating validation, where an <em>invalid</em> result always has
 * one or more errors.
 *
 * <p>There is deliberately no empty {@code NonEmptyList} and therefore no {@code Monoid} instance —
 * the absence is the point.
 *
 * <p>{@code NonEmptyList} also participates in the Higher-Kinded-J {@code Kind} simulation: it
 * implements {@link org.higherkindedj.hkt.nonemptylist.NonEmptyListKind} directly (with the nested
 * {@code Witness}), widen/narrow are provided by {@link
 * org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper#NON_EMPTY_LIST}, and the {@link
 * org.higherkindedj.hkt.nonemptylist.NonEmptyListFunctor}, {@link
 * org.higherkindedj.hkt.nonemptylist.NonEmptyListMonad} (a {@code Monad}, deliberately not a {@code
 * MonadZero}), and {@link org.higherkindedj.hkt.nonemptylist.NonEmptyListTraverse} type-class
 * instances are available. The canonical {@code Monad} is also reachable via {@code
 * Witnesses.nonEmptyList()}.
 */
@NullMarked
package org.higherkindedj.hkt.nonemptylist;

import org.jspecify.annotations.NullMarked;
