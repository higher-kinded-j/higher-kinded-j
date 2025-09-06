// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.func;

import static org.higherkindedj.hkt.func.FunctionKindHelper.FUNCTION;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.Profunctor;
import org.jspecify.annotations.NullMarked;

/**
 * {@link Profunctor} instance for {@link FunctionKind}.
 *
 * <p>This is the canonical example of a profunctor - functions are contravariant in their input and
 * covariant in their output.
 */
@NullMarked
public class FunctionProfunctor implements Profunctor<FunctionKind.Witness> {

  /** Singleton instance of the FunctionProfunctor. */
  public static final FunctionProfunctor INSTANCE = new FunctionProfunctor();

  private FunctionProfunctor() {}

  @Override
  public <A, B, C, D> Kind2<FunctionKind.Witness, C, D> dimap(
      Function<? super C, ? extends A> f,
      Function<? super B, ? extends D> g,
      Kind2<FunctionKind.Witness, A, B> pab) {

    Function<A, B> originalFunction = FUNCTION.getFunction(pab);

    // dimap for functions: (c -> a) -> (b -> d) -> (a -> b) -> (c -> d)
    // This is function composition: g ∘ originalFunction ∘ f
    // We need to handle the wildcard types carefully
    Function<C, D> newFunction =
        (C c) -> {
          A a = f.apply(c);
          B b = originalFunction.apply(a);
          return g.apply(b);
        };

    return FUNCTION.widen(newFunction);
  }
}
