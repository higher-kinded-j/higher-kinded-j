// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.effect.pathsource;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/** The {@code Kind} of {@link Outcome}, and its witness. */
public interface OutcomeKind<A> extends Kind<OutcomeKind.Witness, A> {

  /** The witness: one type parameter, so a {@code MonadError} can range over it. */
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}
