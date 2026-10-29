// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.owntype;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/** The Kind view of a {@link java.util.Set}, which {@link SetKindHelper} holds in a record. */
// ANCHOR: set_kind
public interface SetKind<A> extends Kind<SetKind.Witness, A> {

  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}
// ANCHOR_END: set_kind
