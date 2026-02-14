// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NullMarked;

/**
 * Functor instance for {@link OpticOp}.
 *
 * <p>This enables OpticOp to be used with the Free monad. Since OpticOp instructions are immutable
 * descriptions of operations, the Functor implementation doesn't actually modify the instruction
 * itself - the mapping happens during interpretation.
 */
@NullMarked
public enum OpticOpFunctor implements Functor<OpticOpKind.Witness> {
  /** Singleton instance. */
  INSTANCE;

  @Override
  @SuppressWarnings("unchecked")
  public <A, B> Kind<OpticOpKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<OpticOpKind.Witness, A> fa) {
    // OpticOp instructions are immutable descriptions
    // The actual mapping happens during interpretation
    // So we just preserve the structure here
    return (Kind<OpticOpKind.Witness, B>) fa;
  }
}
