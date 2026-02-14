// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;

/**
 * Higher-kinded type marker interface for {@link OpticOp}.
 *
 * <p>This interface allows {@code OpticOp} to be used with Higher-Kinded-J's type class system,
 * enabling it to work with {@link org.higherkindedj.hkt.free.Free} monad and other abstractions.
 *
 * @param <A> The result type
 */
@NullMarked
public interface OpticOpKind<A> extends Kind<OpticOpKind.Witness, A> {

  /** Witness type for OpticOp in the Kind encoding. */
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {
      throw new UnsupportedOperationException("Witness class");
    }
  }
}
