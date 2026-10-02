// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.effect.pathsource;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * The {@code Kind} of {@link Traced}, and its witness: the marker class the book's Custom Paths
 * page names in {@code @PathSource}.
 */
// ANCHOR: traced_kind
public interface TracedKind<A> extends Kind<TracedKind.Witness, A> {

  /** The witness: one type parameter, so a {@code Monad} can range over it. */
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}
// ANCHOR_END: traced_kind
