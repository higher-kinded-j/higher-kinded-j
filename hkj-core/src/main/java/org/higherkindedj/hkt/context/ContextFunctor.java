// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Functor} interface for the {@link Context} type, using {@link
 * ContextKind.Witness} as the higher-kinded type witness.
 *
 * <p>A {@code ContextFunctor} allows mapping a function over the result value {@code A} of a {@code
 * Context<R, A>}, while keeping the scoped value type {@code R} fixed. The {@code R} type parameter
 * of this class specifies the fixed scoped value type for which this functor instance operates.
 *
 * @param <R> The fixed scoped value type of the {@link Context} for which this functor instance is
 *     defined.
 * @see Context
 * @see ContextKind
 * @see ContextKind.Witness
 * @see Functor
 * @see ContextKindHelper
 */
public class ContextFunctor<R> implements Functor<ContextKind.Witness<R>> {

  private static final Class<ContextFunctor> CONTEXT_FUNCTOR_CLASS = ContextFunctor.class;

  private static final ContextFunctor<?> INSTANCE = new ContextFunctor<>();

  /** Protected constructor to allow subclassing while enforcing singleton-per-type pattern. */
  protected ContextFunctor() {}

  /**
   * Returns the singleton instance of {@code ContextFunctor} for a given scoped value type {@code
   * R}.
   *
   * @param <R> The scoped value type.
   * @return The singleton {@code ContextFunctor<R>} instance.
   */
  @SuppressWarnings("unchecked")
  public static <R> ContextFunctor<R> instance() {
    return (ContextFunctor<R>) INSTANCE;
  }

  /**
   * Maps a function {@code f} over the value {@code A} contained within a {@code
   * Kind<ContextKind.Witness<R>, A>}.
   *
   * <p>This operation transforms a {@code Context<R, A>} into a {@code Context<R, B>} by applying
   * the function {@code f} to the result of the original context, without altering the required
   * scoped value type {@code R}.
   *
   * @param f The function to map over the context's result. Must not be null.
   * @param fa The higher-kinded representation of a {@code Context<R, A>}. Must not be null.
   * @param <A> The original result type of the Context.
   * @param <B> The new result type after applying the function {@code f}.
   * @return A new {@code Kind<ContextKind.Witness<R>, B>} representing the transformed {@code
   *     Context<R, B>}. Never null.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped
   *     to a valid Context representation.
   */
  @Override
  public <A, B> Kind<ContextKind.Witness<R>, B> map(
      Function<? super A, ? extends B> f, Kind<ContextKind.Witness<R>, A> fa) {

    Validation.function().requireMapper(f, "f", CONTEXT_FUNCTOR_CLASS, MAP);
    Validation.kind().requireNonNull(fa, CONTEXT_FUNCTOR_CLASS, MAP);

    Context<R, A> contextA = CONTEXT.narrow(fa);
    Context<R, B> contextB = contextA.map(f);
    return CONTEXT.widen(contextB);
  }
}
