// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Applicative} type class for {@link VTask}, using {@link VTaskKind.Witness}
 * as the higher-kinded type witness.
 *
 * <p>This implementation provides the ability to lift pure values into VTask computations and to
 * apply functions wrapped in VTask to values wrapped in VTask, while maintaining lazy evaluation
 * and virtual thread execution semantics.
 *
 * @see Applicative
 * @see VTaskFunctor
 * @see VTask
 * @see VTaskKind
 * @see VTaskKind.Witness
 * @see VTaskKindHelper
 */
public class VTaskApplicative extends VTaskFunctor implements Applicative<VTaskKind.Witness> {

  private static final Class<VTaskApplicative> VTASK_APPLICATIVE_CLASS = VTaskApplicative.class;

  /** Singleton instance of {@code VTaskApplicative}. */
  public static final VTaskApplicative INSTANCE = new VTaskApplicative();

  /** Protected constructor to enforce the singleton pattern while allowing subclassing. */
  protected VTaskApplicative() {
    super();
  }

  /**
   * Lifts a pure value into the VTask applicative context, creating a VTask computation that will
   * immediately return the given value when executed.
   *
   * <p>This operation delays the evaluation of the value until {@code run()} is called, maintaining
   * the lazy semantics of VTask. The lifted value can be {@code null}.
   *
   * @param <A> The type of the value.
   * @param value The value to lift into the VTask context. Can be {@code null}.
   * @return A {@code Kind<VTaskKind.Witness, A>} representing a VTask computation that returns
   *     {@code value}. Never null.
   */
  @Override
  public <A> Kind<VTaskKind.Witness, A> of(A value) {
    return VTASK.widen(VTask.succeed(value));
  }

  /**
   * Applies a VTask-wrapped function to a VTask-wrapped value, creating a new VTask computation.
   *
   * <p>This operation sequences the evaluation of both VTasks: when the resulting VTask is
   * executed, it first executes the function VTask, then the argument VTask, and finally applies
   * the function to the value.
   *
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @param ff The {@code Kind<VTaskKind.Witness, Function<A, B>>} containing the function. Must not
   *     be null.
   * @param fa The {@code Kind<VTaskKind.Witness, A>} containing the argument value. Must not be
   *     null.
   * @return A {@code Kind<VTaskKind.Witness, B>} representing the VTask computation that applies
   *     the function to the value. Never null.
   * @throws NullPointerException if {@code ff} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped.
   */
  @Override
  public <A, B> Kind<VTaskKind.Witness, B> ap(
      Kind<VTaskKind.Witness, ? extends Function<A, B>> ff, Kind<VTaskKind.Witness, A> fa) {

    Validation.kind().validateAp(ff, fa, VTASK_APPLICATIVE_CLASS);

    VTask<? extends Function<A, B>> vtaskF = VTASK.narrow(ff);
    VTask<A> vtaskA = VTASK.narrow(fa);

    // Use flatMap to properly sequence: run vtaskF, run vtaskA, apply function to value
    VTask<B> vtaskB = vtaskF.flatMap(func -> vtaskA.map(func));
    return VTASK.widen(vtaskB);
  }
}
