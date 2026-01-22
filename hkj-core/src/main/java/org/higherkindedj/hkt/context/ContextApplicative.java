// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Applicative} interface for the {@link Context} type, using {@link
 * ContextKind.Witness} as the higher-kinded type witness.
 *
 * <p>A {@code ContextApplicative} provides the ability to lift values into the {@link Context}
 * context (via {@link #of(Object)}) and to apply a {@code Context} holding a function to a {@code
 * Context} holding a value (via {@link #ap(Kind, Kind)}). The {@code R} type parameter of this
 * class specifies the fixed scoped value type for which this applicative instance operates.
 *
 * @param <R> The fixed scoped value type of the {@link Context} for which this applicative instance
 *     is defined.
 * @see Context
 * @see ContextKind
 * @see ContextKind.Witness
 * @see Applicative
 * @see ContextFunctor
 * @see ContextKindHelper
 */
public class ContextApplicative<R> extends ContextFunctor<R>
    implements Applicative<ContextKind.Witness<R>> {

  private static final Class<ContextApplicative> CONTEXT_APPLICATIVE_CLASS =
      ContextApplicative.class;

  private static final ContextApplicative<?> INSTANCE = new ContextApplicative<>();

  /** Protected constructor to allow subclassing while enforcing singleton-per-type pattern. */
  protected ContextApplicative() {}

  /**
   * Returns the singleton instance of {@code ContextApplicative} for a given scoped value type
   * {@code R}.
   *
   * @param <R> The scoped value type.
   * @return The singleton {@code ContextApplicative<R>} instance.
   */
  @SuppressWarnings("unchecked")
  public static <R> ContextApplicative<R> instance() {
    return (ContextApplicative<R>) INSTANCE;
  }

  /**
   * Lifts a pure value {@code value} into the {@link Context} context. The resulting {@code
   * Context<R, A>} will produce the given value without reading from any scoped value.
   *
   * @param value The value to lift into the Context. May be null if {@code A} is a nullable type.
   * @param <A> The type of the lifted value.
   * @return A {@code Kind<ContextKind.Witness<R>, A>} representing a {@code Context<R, A>} that
   *     yields the given value. Never null.
   */
  @Override
  public <A> Kind<ContextKind.Witness<R>, A> of(@Nullable A value) {
    return CONTEXT.succeed(value);
  }

  /**
   * Applies a function contained within a {@code Kind<ContextKind.Witness<R>, Function<A, B>>} to a
   * value contained within a {@code Kind<ContextKind.Witness<R>, A>}.
   *
   * <p>Both underlying {@code Context} instances are run in sequence. The function extracted from
   * the first context is then applied to the value extracted from the second context.
   *
   * @param ff The higher-kinded representation of a {@code Context<R, Function<A, B>>}. Must not be
   *     null.
   * @param fa The higher-kinded representation of a {@code Context<R, A>}. Must not be null.
   * @param <A> The type of the value to which the function is applied.
   * @param <B> The result type of the function application.
   * @return A new {@code Kind<ContextKind.Witness<R>, B>} representing the {@code Context<R, B>}
   *     that results from applying the function. Never null.
   * @throws NullPointerException if {@code ff} or {@code fa} is null, or if the function extracted
   *     from the Context is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped to valid Context representations.
   */
  @Override
  public <A, B> Kind<ContextKind.Witness<R>, B> ap(
      Kind<ContextKind.Witness<R>, ? extends Function<A, B>> ff,
      Kind<ContextKind.Witness<R>, A> fa) {

    Validation.kind().validateAp(ff, fa, CONTEXT_APPLICATIVE_CLASS);

    Context<R, ? extends Function<A, B>> contextF = CONTEXT.narrow(ff);
    Context<R, A> contextA = CONTEXT.narrow(fa);

    Context<R, B> contextB =
        contextF.flatMap(
            func -> {
              if (func == null) {
                throw new NullPointerException("Function extracted from Context for 'ap' was null");
              }
              return contextA.map(func);
            });

    return CONTEXT.widen(contextB);
  }
}
