// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Kind interface marker for the VStream type in Higher-Kinded-J. Represents VStream as a type
 * constructor 'F' in {@code Kind<F, A>}. The witness type F is VStreamKind.Witness.
 *
 * <p>{@code VStream} is a lazy, pull-based streaming abstraction that executes element production
 * on virtual threads via {@link org.higherkindedj.hkt.vtask.VTask}. This HKT encoding enables
 * VStream to participate in polymorphic, type-class-based programming alongside VTask, Maybe,
 * Either, and other HKT-encoded types.
 *
 * @param <A> The type of elements produced by the VStream.
 * @see org.higherkindedj.hkt.Kind
 * @see VStream
 * @see VStreamKindHelper
 */
public interface VStreamKind<A> extends Kind<VStreamKind.Witness, A> {
  /**
   * The phantom type marker for the VStream type constructor. This is used as the 'F' in {@code
   * Kind<F, A>}.
   */
  final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {} // Private constructor to prevent instantiation
  }
}
