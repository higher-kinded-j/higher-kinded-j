// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.func;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;

/**
 * Wrapper for {@link Function} to work with the {@link Kind2} system. This demonstrates how
 * profunctors work with the most basic example.
 *
 * @param <A> Input type
 * @param <B> Output type
 */
@NullMarked
public final class FunctionKind<A, B> implements Kind2<FunctionKind.Witness, A, B> {

  /** Witness type for the Function type constructor. */
  public static final class Witness implements WitnessArity<TypeArity.Binary> {
    private Witness() {}
  }

  private final Function<A, B> function;

  FunctionKind(Function<A, B> function) {
    this.function = function;
  }

  Function<A, B> getFunction() {
    return function;
  }

  /** Applies the wrapped function. */
  public B apply(A a) {
    return function.apply(a);
  }
}
