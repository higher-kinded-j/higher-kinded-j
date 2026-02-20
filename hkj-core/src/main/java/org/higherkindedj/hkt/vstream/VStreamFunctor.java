// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.higherkindedj.hkt.util.validation.Operation.MAP;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Functor} type class for {@link VStream}, using {@link VStreamKind.Witness}
 * as the higher-kinded type witness.
 *
 * <p>This implementation provides the ability to transform elements of a VStream using a pure
 * function, while maintaining the lazy, pull-based evaluation semantics. No elements are produced
 * until a terminal operation is invoked on the resulting stream.
 *
 * <p><b>Functor Laws:</b>
 *
 * <ul>
 *   <li><b>Identity:</b> {@code map(id, fa) == fa}
 *   <li><b>Composition:</b> {@code map(g.compose(f), fa) == map(g, map(f, fa))}
 * </ul>
 *
 * @see Functor
 * @see VStream
 * @see VStreamKind
 * @see VStreamKind.Witness
 * @see VStreamKindHelper
 */
public class VStreamFunctor implements Functor<VStreamKind.Witness> {

  private static final Class<VStreamFunctor> VSTREAM_FUNCTOR_CLASS = VStreamFunctor.class;

  /** Singleton instance of {@code VStreamFunctor}. */
  public static final VStreamFunctor INSTANCE = new VStreamFunctor();

  /** Protected constructor to enforce the singleton pattern while allowing subclassing. */
  protected VStreamFunctor() {}

  /**
   * Applies a function to each element of a VStream, creating a new lazy VStream.
   *
   * <p>This operation maintains the lazy evaluation semantics of VStream. The function is not
   * applied until elements are pulled from the resulting stream via a terminal operation.
   *
   * @param <A> The type of elements in the input VStream.
   * @param <B> The type of elements in the output VStream after applying the function.
   * @param f The function to apply to each element. Must not be null.
   * @param fa The {@code Kind<VStreamKind.Witness, A>} representing the VStream to transform. Must
   *     not be null.
   * @return A {@code Kind<VStreamKind.Witness, B>} representing the transformed VStream. Never
   *     null.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<VStreamKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<VStreamKind.Witness, A> fa) {

    Validation.function().requireMapper(f, "f", VSTREAM_FUNCTOR_CLASS, MAP);
    Validation.kind().requireNonNull(fa, VSTREAM_FUNCTOR_CLASS, MAP);

    VStream<A> stream = VSTREAM.narrow(fa);
    VStream<B> mapped = stream.map(f);
    return VSTREAM.widen(mapped);
  }
}
