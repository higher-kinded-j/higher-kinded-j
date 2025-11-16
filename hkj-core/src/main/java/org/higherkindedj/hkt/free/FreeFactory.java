// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;

/**
 * A type-parameterised factory for creating {@link Free} monad instances with improved type
 * inference.
 *
 * <p>This factory solves the problem of Java's type inference not being able to determine the
 * functor type parameter {@code F} when chaining operations directly on {@code Free.pure()}. By
 * creating a factory instance parameterised on {@code F}, all subsequent operations can infer the
 * type automatically.
 *
 * <p><b>Problem solved:</b>
 *
 * <pre>{@code
 * // Without FreeFactory - requires explicit type parameters:
 * Free<IdKind.Witness, Integer> f = Free.<IdKind.Witness, Integer>pure(2).map(x -> x * 2);
 *
 * // With FreeFactory - type inference works:
 * FreeFactory<IdKind.Witness> factory = FreeFactory.of();
 * Free<IdKind.Witness, Integer> f = factory.pure(2).map(x -> x * 2);
 * }</pre>
 *
 * <p><b>Usage patterns:</b>
 *
 * <pre>{@code
 * // Pattern 1: Create factory once, reuse
 * FreeFactory<IdKind.Witness> FREE = FreeFactory.of();
 * Free<IdKind.Witness, Integer> program = FREE.pure(1)
 *     .map(x -> x + 1)
 *     .flatMap(x -> FREE.pure(x * 2));
 *
 * // Pattern 2: Create with monad instance (for documentation/clarity)
 * FreeFactory<IdKind.Witness> FREE = FreeFactory.withMonad(IdMonad.instance());
 *
 * // Pattern 3: Inline usage
 * Free<IdKind.Witness, Integer> f = FreeFactory.<IdKind.Witness>of().pure(42);
 * }</pre>
 *
 * @param <F> The functor type for the Free monad (e.g., {@code IdKind.Witness})
 * @see Free
 * @see Free#pure(Object)
 * @see Free#suspend(Kind)
 */
public final class FreeFactory<F> {

  /** Private constructor to enforce factory method usage. */
  private FreeFactory() {}

  /**
   * Creates a new {@link FreeFactory} instance for the specified functor type.
   *
   * <p>The functor type {@code F} is inferred from the usage context or can be explicitly
   * specified.
   *
   * @param <F> The functor type for the Free monad
   * @return A new {@link FreeFactory} instance. Never null.
   */
  public static <F> FreeFactory<F> of() {
    return new FreeFactory<>();
  }

  /**
   * Creates a new {@link FreeFactory} instance, associating it with a specific {@link Monad}
   * instance.
   *
   * <p>This factory method is useful for documentation and clarity, making explicit which monad the
   * Free programs will be interpreted into. The monad parameter is only used for type inference;
   * the actual interpretation happens via {@link Free#foldMap}.
   *
   * @param monad The {@link Monad} instance for the functor type. The monad itself is not stored,
   *     only used for type inference. Must not be null.
   * @param <F> The functor type for the Free monad (inferred from monad parameter)
   * @return A new {@link FreeFactory} instance. Never null.
   */
  public static <F> FreeFactory<F> withMonad(Monad<F> monad) {
    // The monad is used purely for type inference - we don't actually need to store it
    return new FreeFactory<>();
  }

  /**
   * Creates a pure Free monad wrapping the given value.
   *
   * <p>This is equivalent to {@link Free#pure(Object)} but with improved type inference. The
   * functor type {@code F} is automatically inferred from this factory instance.
   *
   * @param value The value to wrap. Can be null.
   * @param <A> The type of the value
   * @return A {@link Free} monad containing the pure value. Never null.
   */
  public <A> Free<F, A> pure(A value) {
    return Free.pure(value);
  }

  /**
   * Creates a Free monad from a suspended computation.
   *
   * <p>This is equivalent to {@link Free#suspend(Kind)} but with improved type inference. The
   * functor type {@code F} is automatically inferred from this factory instance.
   *
   * @param computation The computation to suspend, wrapped in the functor {@code F}. Must not be
   *     null.
   * @param <A> The result type of the Free monad
   * @return A {@link Free} monad suspending the computation. Never null.
   */
  public <A> Free<F, A> suspend(Kind<F, Free<F, A>> computation) {
    return Free.suspend(computation);
  }

  /**
   * Lifts a computation in the functor {@code F} into the Free monad.
   *
   * <p>This creates a suspended computation that, when interpreted, will execute the given
   * computation and return a pure value containing the result.
   *
   * <p>This is equivalent to {@link Free#liftF(Kind, Functor)} but with improved type inference for
   * the functor type parameter.
   *
   * @param fa The computation to lift, wrapped in the functor {@code F}. Must not be null.
   * @param functor The {@link Functor} instance for {@code F}. Must not be null.
   * @param <A> The result type
   * @return A {@link Free} monad that will execute the lifted computation. Never null.
   */
  public <A> Free<F, A> liftF(Kind<F, A> fa, Functor<F> functor) {
    return Free.liftF(fa, functor);
  }

  @Override
  public String toString() {
    return "FreeFactory";
  }
}
