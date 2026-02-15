// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.higherkindedj.hkt.free.FreeKindHelper.FREE;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Monad implementation for {@link FreeKind}.
 *
 * <p>Provides Functor, Applicative, and Monad operations for the Free type. The Free monad allows
 * building programs as data structures that can be interpreted in different ways.
 *
 * @param <F> The functor type representing the instruction set
 */
public class FreeMonad<F extends WitnessArity<?>> extends FreeFunctor<F>
    implements Monad<FreeKind.Witness<F>> {

  private static final Class<FreeMonad> FREE_MONAD_CLASS = FreeMonad.class;

  /**
   * Creates a new FreeMonad instance. Note: Unlike some other monads in this codebase, Free
   * requires a type parameter F, so we cannot use a singleton instance.
   */
  public FreeMonad() {
    super();
  }

  /**
   * Lifts a pure value into the Free monad context.
   *
   * @param <A> The type of the value
   * @param value The value to lift, can be null
   * @return A Kind representing Pure(value)
   */
  @Override
  public <A> Kind<FreeKind.Witness<F>, A> of(@Nullable A value) {
    return FREE.widen(Free.pure(value));
  }

  /**
   * Sequentially composes two Free computations.
   *
   * <p>This operation is stack-safe due to the use of the FlatMapped constructor, which defers the
   * actual computation until interpretation time.
   *
   * @param <A> The type of the value in the input Free
   * @param <B> The type of the value in the resulting Free
   * @param f The function to apply to the value. Must not be null.
   * @param ma The Free to transform. Must not be null.
   * @return The result of the computation chain
   * @throws NullPointerException if f or ma is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if ma cannot be unwrapped
   */
  @Override
  public <A, B> Kind<FreeKind.Witness<F>, B> flatMap(
      Function<? super A, ? extends Kind<FreeKind.Witness<F>, B>> f,
      Kind<FreeKind.Witness<F>, A> ma) {
    Validation.function().validateFlatMap(f, ma, FREE_MONAD_CLASS);

    Free<F, A> freeA = FREE.narrow(ma);

    Free<F, B> resultFree =
        freeA.flatMap(
            a -> {
              Kind<FreeKind.Witness<F>, B> kindB = f.apply(a);
              Validation.function()
                  .requireNonNullResult(kindB, "f", FREE_MONAD_CLASS, FLAT_MAP, Free.class);
              return FREE.narrow(kindB);
            });

    return FREE.widen(resultFree);
  }

  /**
   * Applies a function wrapped in a Free to a value wrapped in a Free.
   *
   * <p>This is implemented using flatMap and map, maintaining stack safety.
   *
   * @param <A> The input type of the function
   * @param <B> The output type of the function
   * @param ff The Free containing the function. Must not be null.
   * @param fa The Free containing the value. Must not be null.
   * @return The result of applying the function
   * @throws NullPointerException if ff or fa is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if ff or fa cannot be unwrapped
   */
  @Override
  public <A, B> Kind<FreeKind.Witness<F>, B> ap(
      Kind<FreeKind.Witness<F>, ? extends Function<A, B>> ff, Kind<FreeKind.Witness<F>, A> fa) {
    Validation.kind().validateAp(ff, fa, FREE_MONAD_CLASS);

    Free<F, ? extends Function<A, B>> freeF = FREE.narrow(ff);
    Free<F, A> freeA = FREE.narrow(fa);

    // Use flatMap and map for stack-safe implementation
    Free<F, B> resultFree = freeF.flatMap(func -> freeA.map(func));
    return FREE.widen(resultFree);
  }
}
