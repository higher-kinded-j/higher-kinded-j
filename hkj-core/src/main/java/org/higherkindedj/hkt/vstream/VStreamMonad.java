// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.higherkindedj.hkt.util.validation.Operation.FLAT_MAP;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Monad} type class for {@link VStream}, using {@link VStreamKind.Witness} as
 * the higher-kinded type witness.
 *
 * <p>This implementation provides the ability to sequence VStream computations where each element
 * can be substituted with a sub-stream that is then flattened into the result. This is the standard
 * monadic bind (flatMap) operation for streams.
 *
 * <p><b>Monad Laws:</b>
 *
 * <ul>
 *   <li><b>Left Identity:</b> {@code flatMap(of(a), f) == f(a)}
 *   <li><b>Right Identity:</b> {@code flatMap(m, of) == m}
 *   <li><b>Associativity:</b> {@code flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x),
 *       g))}
 * </ul>
 *
 * <p>This class is a stateless singleton, accessible via {@link #INSTANCE}.
 *
 * @see Monad
 * @see VStreamApplicative
 * @see VStream
 * @see VStreamKind
 * @see VStreamKind.Witness
 * @see VStreamKindHelper
 */
public class VStreamMonad extends VStreamApplicative implements Monad<VStreamKind.Witness> {

  private static final Class<VStreamMonad> VSTREAM_MONAD_CLASS = VStreamMonad.class;

  /** Singleton instance of {@code VStreamMonad}. */
  public static final VStreamMonad INSTANCE = new VStreamMonad();

  /** Protected constructor to enforce the singleton pattern while allowing subclassing. */
  protected VStreamMonad() {
    super();
  }

  /**
   * Sequentially composes VStream computations. Each element from the input stream is passed to
   * function {@code f}, which produces a sub-stream. All sub-streams are flattened into the result
   * stream.
   *
   * <p>This operation maintains lazy evaluation. No elements are produced until a terminal
   * operation is invoked on the resulting stream. The flatMap implementation is stack-safe for deep
   * chains.
   *
   * @param <A> The type of elements in the input stream {@code ma}.
   * @param <B> The type of elements in the sub-streams produced by {@code f}.
   * @param f A function that takes an element and returns a {@code Kind<VStreamKind.Witness, B>}
   *     representing the sub-stream. Must not be null.
   * @param ma A {@code Kind<VStreamKind.Witness, A>} representing the input stream. Must not be
   *     null.
   * @return A {@code Kind<VStreamKind.Witness, B>} representing the flattened result stream. Never
   *     null.
   * @throws NullPointerException if {@code f} or {@code ma} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the {@code Kind}
   *     returned by {@code f} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<VStreamKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<VStreamKind.Witness, B>> f,
      Kind<VStreamKind.Witness, A> ma) {

    Validation.function().validateFlatMap(f, ma, VSTREAM_MONAD_CLASS);

    VStream<A> stream = VSTREAM.narrow(ma);
    VStream<B> result =
        stream.flatMap(
            a -> {
              var kindB = f.apply(a);
              Validation.function()
                  .requireNonNullResult(kindB, "f", VSTREAM_MONAD_CLASS, FLAT_MAP, Kind.class);
              return VSTREAM.narrow(kindB);
            });
    return VSTREAM.widen(result);
  }
}
