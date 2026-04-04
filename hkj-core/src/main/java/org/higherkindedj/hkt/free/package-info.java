// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Free monad for creating Domain-Specific Languages (DSLs) with deferred interpretation.
 *
 * <p>The Free monad allows building programs as data structures that can be interpreted in
 * different ways. This is particularly useful for separating program description from execution,
 * enabling testing with mock interpreters, and supporting multiple interpretation strategies.
 *
 * <h2>Key Components</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.free.Free} — The Free monad sealed interface with Pure,
 *       Suspend, FlatMapped, HandleError, and Ap constructors
 *   <li>{@link org.higherkindedj.hkt.free.FreeKind} — HKT representation
 *   <li>{@link org.higherkindedj.hkt.free.FreeKindHelper} — Widen/narrow operations
 *   <li>{@link org.higherkindedj.hkt.free.FreeFunctor} — Functor instance
 *   <li>{@link org.higherkindedj.hkt.free.FreeMonad} — Monad instance
 * </ul>
 *
 * <h2>Core Operations</h2>
 *
 * <ul>
 *   <li>{@code Free.pure(value)} — Lift a pure value into the Free monad
 *   <li>{@code Free.liftF(fa, functor)} — Lift a single instruction into Free
 *   <li>{@code Free.foldMap(nat, monad)} — Interpret a Free program via natural transformation
 *   <li>{@code Free.translate(program, nat, functor)} — Transform between instruction sets
 *   <li>{@code Free.handleError(errorType, handler)} — Add error recovery to a sub-program
 * </ul>
 *
 * @see org.higherkindedj.hkt.free.Free
 * @see org.higherkindedj.hkt.free_ap.FreeAp
 * @see org.higherkindedj.hkt.effect.FreePath
 */
@NullMarked
package org.higherkindedj.hkt.free;

import org.jspecify.annotations.NullMarked;
