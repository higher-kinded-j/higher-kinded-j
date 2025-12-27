// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.higherkindedj.hkt.free.FreeKindHelper.FREE;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Functor} type class for {@link FreeKind}.
 *
 * <p>This allows {@code Free} to be used in contexts that expect a {@link Functor} instance,
 * providing a way to apply a function to the value that will eventually be computed by the Free
 * monad.
 *
 * @param <F> The functor type over which Free is constructed
 * @see Functor
 * @see FreeKind
 */
public class FreeFunctor<F extends WitnessArity<?>> implements Functor<FreeKind.Witness<F>> {

  private static final Class<FreeFunctor> FREE_FUNCTOR_CLASS = FreeFunctor.class;

  /**
   * Creates a new FreeFunctor instance. Note: Unlike some other functors in this codebase, Free
   * requires a type parameter F, so we cannot use a singleton instance.
   */
  protected FreeFunctor() {}

  /**
   * Applies a function to the eventual result of a Free computation.
   *
   * <p>This operation is stack-safe and does not execute the Free program.
   *
   * @param <A> The type of the value in the input Free
   * @param <B> The type of the value in the resulting Free
   * @param f The function to apply to the value. Must not be null.
   * @param fa The input FreeKind. Must not be null.
   * @return A new FreeKind with the function applied
   * @throws NullPointerException if f or fa is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if fa cannot be unwrapped
   */
  @Override
  public <A, B> Kind<FreeKind.Witness<F>, B> map(
      Function<? super A, ? extends @Nullable B> f, Kind<FreeKind.Witness<F>, A> fa) {
    Validation.function().requireMapper(f, "f", FREE_FUNCTOR_CLASS, MAP);
    Validation.kind().requireNonNull(fa, FREE_FUNCTOR_CLASS, MAP);

    Free<F, A> freeA = FREE.narrow(fa);
    Free<F, B> resultFree = freeA.map(f);
    return FREE.widen(resultFree);
  }
}
