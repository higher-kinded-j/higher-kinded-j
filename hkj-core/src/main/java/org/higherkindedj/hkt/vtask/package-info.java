// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
/**
 * Provides components for the {@code VTask} effect type and its simulation as a Higher-Kinded Type.
 * {@code VTask} represents lazy computations that execute on Java virtual threads, providing
 * lightweight concurrency with structured cancellation.
 *
 * <p>{@code VTask<A>} directly implements {@link org.higherkindedj.hkt.vtask.VTaskKind}, allowing
 * it to participate in the HKT simulation without requiring wrapper types. This means that {@code
 * widen} and {@code narrow} operations are simple type-safe casts rather than object wrapping.
 *
 * <p>Key characteristics of VTask:
 *
 * <ul>
 *   <li><b>Laziness:</b> Effects are not executed upon creation of a {@code VTask} value, but only
 *       when explicitly run via {@link org.higherkindedj.hkt.vtask.VTask#run()}.
 *   <li><b>Virtual Threads:</b> Computations execute on virtual threads, enabling high-concurrency
 *       scenarios with minimal overhead.
 *   <li><b>Structured Concurrency:</b> Uses Java 25's {@code StructuredTaskScope} for proper
 *       cancellation semantics.
 *   <li><b>Composability:</b> Operations can be chained using {@code map}, {@code flatMap}, and
 *       other combinators to build complex effectful workflows.
 * </ul>
 *
 * <p>Includes the {@link org.higherkindedj.hkt.vtask.VTask} type, {@link
 * org.higherkindedj.hkt.vtask.VTaskKindHelper}, {@link org.higherkindedj.hkt.vtask.VTaskMonad}, and
 * parallel combinators in {@link org.higherkindedj.hkt.vtask.Par}.
 *
 * @see org.higherkindedj.hkt.vtask.VTask
 * @see org.higherkindedj.hkt.vtask.VTaskKind
 * @see org.higherkindedj.hkt.vtask.Par
 */
@NullMarked
package org.higherkindedj.hkt.vtask;

import org.jspecify.annotations.NullMarked;
