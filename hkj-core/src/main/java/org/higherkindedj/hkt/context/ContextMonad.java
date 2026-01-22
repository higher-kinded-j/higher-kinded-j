// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;
import static org.higherkindedj.hkt.util.validation.Operation.FLAT_MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Monad} interface for the {@link Context} type.
 *
 * <p>This class allows {@link Context Context<R, A>} to be used as a monad. The {@code Context}
 * monad allows sequencing of operations that read from {@link ScopedValue} bindings in the current
 * thread scope.
 *
 * <p>The higher-kinded type (HKT) witness for {@code Context<R, ?>} is {@link ContextKind.Witness
 * Witness<R>}. This means that when using {@code ContextMonad} with generic HKT abstractions, a
 * {@code Context<R, A>} is represented as {@code Kind<ContextKind.Witness<R>, A>}.
 *
 * <p>This class is a singleton for any given scoped value type {@code R}, managed via the static
 * {@link #instance()} factory method.
 *
 * @param <R> The type of the scoped value. This type is fixed for a given instance of {@code
 *     ContextMonad}.
 * @see Context
 * @see ContextKind
 * @see ContextKind.Witness
 * @see ContextKindHelper
 * @see Monad
 * @see ContextApplicative
 */
public class ContextMonad<R> extends ContextApplicative<R>
    implements Monad<ContextKind.Witness<R>> {

  private static final Class<ContextMonad> CONTEXT_MONAD_CLASS = ContextMonad.class;

  private static final ContextMonad<?> INSTANCE = new ContextMonad<>();

  /** Protected constructor to enforce singleton-per-type pattern. */
  protected ContextMonad() {}

  /**
   * Returns the singleton instance of {@code ContextMonad} for a given scoped value type {@code R}.
   *
   * @param <R> The scoped value type.
   * @return The singleton {@code ContextMonad<R>} instance.
   */
  @SuppressWarnings("unchecked")
  public static <R> ContextMonad<R> instance() {
    return (ContextMonad<R>) INSTANCE;
  }

  /**
   * Sequentially composes two {@link Context} actions, passing the result of the first {@code
   * Context} ({@code ma}) into a function {@code f} that produces the second {@code Context}.
   *
   * <p>The {@code flatMap} operation allows dependent computations: the computation of the second
   * {@code Context} can depend on the result of the first.
   *
   * @param <A> The value type of the initial Context (represented by {@code ma}).
   * @param <B> The value type of the Context produced by the function {@code f}.
   * @param f A function that takes a value of type {@code A} (from the first Context) and returns a
   *     {@code Kind<ContextKind.Witness<R>, B>} (which is a wrapped {@code Context<R, B>}). Must
   *     not be null.
   * @param ma A {@code Kind<ContextKind.Witness<R>, A>} representing the initial {@code Context<R,
   *     A>}. Must not be null.
   * @return A new {@code Kind<ContextKind.Witness<R>, B>} representing the composed {@code
   *     Context<R, B>}. Never null. The resulting Context, when run, will first run {@code ma} to
   *     get {@code a}, then apply {@code f} to {@code a} to get a new Context, and then run that
   *     new Context.
   * @throws NullPointerException if {@code f} or {@code ma} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} is not a valid
   *     Context representation.
   */
  @Override
  public <A, B> Kind<ContextKind.Witness<R>, B> flatMap(
      Function<? super A, ? extends Kind<ContextKind.Witness<R>, B>> f,
      Kind<ContextKind.Witness<R>, A> ma) {

    Validation.function().validateFlatMap(f, ma, CONTEXT_MONAD_CLASS);

    Context<R, A> contextA = CONTEXT.narrow(ma);

    Context<R, B> contextB =
        contextA.flatMap(
            a -> {
              Kind<ContextKind.Witness<R>, B> kindB = f.apply(a);
              Validation.function()
                  .requireNonNullResult(kindB, "f", CONTEXT_MONAD_CLASS, FLAT_MAP, Kind.class);
              return CONTEXT.narrow(kindB);
            });

    return CONTEXT.widen(contextB);
  }
}
