// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.owntype;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

// ANCHOR: kind
/** The Kind view of a {@link Result}: a Result whose container is named by {@link Witness}. */
public interface ResultKind<A> extends Kind<ResultKind.Witness, A> {

  /** Names the Result container. Nothing instantiates it; it appears only as a type argument. */
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}
// ANCHOR_END: kind
