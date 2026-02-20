// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Applicative} type class for {@link VStream}, using {@link
 * VStreamKind.Witness} as the higher-kinded type witness.
 *
 * <p>This implementation provides the ability to lift pure values into single-element VStreams and
 * to apply streams of functions to streams of values using Cartesian product semantics.
 *
 * <p><b>Applicative Semantics:</b> The {@link #ap} operation uses Cartesian product semantics,
 * consistent with list-like monads. Given a stream of functions {@code [f1, f2]} and a stream of
 * values {@code [a1, a2, a3]}, the result is {@code [f1(a1), f1(a2), f1(a3), f2(a1), f2(a2),
 * f2(a3)]}.
 *
 * <p><b>Applicative Laws:</b>
 *
 * <ul>
 *   <li><b>Identity:</b> {@code ap(of(id), fa) == fa}
 *   <li><b>Homomorphism:</b> {@code ap(of(f), of(x)) == of(f(x))}
 *   <li><b>Interchange:</b> {@code ap(ff, of(y)) == ap(of(f -> f(y)), ff)}
 * </ul>
 *
 * @see Applicative
 * @see VStreamFunctor
 * @see VStream
 * @see VStreamKind
 * @see VStreamKind.Witness
 * @see VStreamKindHelper
 */
public class VStreamApplicative extends VStreamFunctor implements Applicative<VStreamKind.Witness> {

  private static final Class<VStreamApplicative> VSTREAM_APPLICATIVE_CLASS =
      VStreamApplicative.class;

  /** Singleton instance of {@code VStreamApplicative}. */
  public static final VStreamApplicative INSTANCE = new VStreamApplicative();

  /** Protected constructor to enforce the singleton pattern while allowing subclassing. */
  protected VStreamApplicative() {
    super();
  }

  /**
   * Lifts a pure value into a single-element VStream.
   *
   * <p>The resulting stream produces the given value on the first pull, then completes.
   *
   * @param <A> The type of the value.
   * @param value The value to lift into the VStream context. Can be {@code null}.
   * @return A {@code Kind<VStreamKind.Witness, A>} representing a single-element VStream. Never
   *     null.
   */
  @Override
  public <A> Kind<VStreamKind.Witness, A> of(A value) {
    return VSTREAM.widen(VStream.of(value));
  }

  /**
   * Applies a VStream of functions to a VStream of values using Cartesian product semantics.
   *
   * <p>Each function in the function stream is applied to every value in the value stream. This
   * produces a result stream containing all combinations of function applications. The operation
   * remains lazy; no elements are produced until a terminal operation is invoked.
   *
   * @param <A> The input type of the functions.
   * @param <B> The output type of the functions.
   * @param ff The {@code Kind<VStreamKind.Witness, Function<A, B>>} containing the functions. Must
   *     not be null.
   * @param fa The {@code Kind<VStreamKind.Witness, A>} containing the argument values. Must not be
   *     null.
   * @return A {@code Kind<VStreamKind.Witness, B>} representing the Cartesian product of function
   *     applications. Never null.
   * @throws NullPointerException if {@code ff} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped.
   */
  @Override
  public <A, B> Kind<VStreamKind.Witness, B> ap(
      Kind<VStreamKind.Witness, ? extends Function<A, B>> ff, Kind<VStreamKind.Witness, A> fa) {

    Validation.kind().validateAp(ff, fa, VSTREAM_APPLICATIVE_CLASS);

    VStream<? extends Function<A, B>> fStream = VSTREAM.narrow(ff);
    VStream<A> aStream = VSTREAM.narrow(fa);

    // Cartesian product: each function applied to each value
    VStream<B> result = fStream.flatMap(f -> aStream.map(f));
    return VSTREAM.widen(result);
  }
}
