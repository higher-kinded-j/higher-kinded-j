// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.higherkindedj.hkt.util.validation.Operation.MAP;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Functor} type class for {@link VTask}, using {@link VTaskKind.Witness} as
 * the higher-kinded type witness.
 *
 * <p>This implementation provides the ability to transform the result of a VTask computation using
 * a pure function, while maintaining the lazy evaluation semantics and virtual thread execution of
 * VTask.
 *
 * @see Functor
 * @see VTask
 * @see VTaskKind
 * @see VTaskKind.Witness
 * @see VTaskKindHelper
 */
public class VTaskFunctor implements Functor<VTaskKind.Witness> {

  private static final Class<VTaskFunctor> VTASK_FUNCTOR_CLASS = VTaskFunctor.class;

  /** Singleton instance of {@code VTaskFunctor}. */
  public static final VTaskFunctor INSTANCE = new VTaskFunctor();

  /** Protected constructor to enforce the singleton pattern while allowing subclassing. */
  protected VTaskFunctor() {}

  /**
   * Applies a function to the result of a VTask computation, creating a new VTask computation that
   * will apply the function when executed.
   *
   * <p>This operation maintains the lazy evaluation semantics of VTask - the function is not
   * applied until the resulting VTask is executed with {@code run()}.
   *
   * @param <A> The type of the result of the input VTask computation.
   * @param <B> The type of the result after applying the function.
   * @param f The function to apply to the VTask result. Must not be null.
   * @param fa The {@code Kind<VTaskKind.Witness, A>} representing the VTask computation to
   *     transform. Must not be null.
   * @return A {@code Kind<VTaskKind.Witness, B>} representing the transformed VTask computation.
   *     Never null.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<VTaskKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<VTaskKind.Witness, A> fa) {

    Validation.function().requireMapper(f, "f", VTASK_FUNCTOR_CLASS, MAP);
    Validation.kind().requireNonNull(fa, VTASK_FUNCTOR_CLASS, MAP);

    VTask<A> vtaskA = VTASK.narrow(fa);
    VTask<B> vtaskB = vtaskA.map(f);
    return VTASK.widen(vtaskB);
  }
}
