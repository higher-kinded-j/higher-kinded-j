// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free.test;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind interface for Identity.
 *
 * @param <A> The wrapped value type
 */
public interface IdentityKind<A> extends Kind<IdentityKind.Witness, A> {

  /** Witness type for Identity. */
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }
}
