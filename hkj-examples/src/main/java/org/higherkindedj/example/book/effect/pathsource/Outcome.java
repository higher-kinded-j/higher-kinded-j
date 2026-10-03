// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.effect.pathsource;

import org.higherkindedj.hkt.effect.annotation.PathSource;

/**
 * A result that carries a {@link Problem} when it fails: the effect for which the book's Custom
 * Paths page generates a recovering {@code OutcomePath}.
 */
// ANCHOR: outcome
@PathSource(
    witness = OutcomeKind.Witness.class,
    errorType = Problem.class,
    capability = PathSource.Capability.RECOVERABLE)
public sealed interface Outcome<A> extends OutcomeKind<A> {

  record Ok<A>(A value) implements Outcome<A> {}

  record Failed<A>(Problem problem) implements Outcome<A> {}
}
// ANCHOR_END: outcome
