// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import static org.higherkindedj.hkt.trampoline.TrampolineKindHelper.TRAMPOLINE;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Monad implementation for {@link TrampolineKind}. Provides {@link org.higherkindedj.hkt.Functor},
 * {@link org.higherkindedj.hkt.Applicative}, and {@link Monad} operations for the {@link
 * Trampoline} type within the Higher-Kinded-J system.
 *
 * <p>All monadic operations maintain stack safety, allowing arbitrarily deep chains of computations
 * without risking {@link StackOverflowError}.
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * TrampolineMonad monad = TrampolineMonad.INSTANCE;
 *
 * // Create trampolined computations
 * Kind<TrampolineKind.Witness, Integer> t1 = monad.of(5);
 * Kind<TrampolineKind.Witness, Integer> t2 = monad.flatMap(
 *     x -> monad.of(x * 2),
 *     t1
 * );
 *
 * // Extract the result
 * Integer result = TRAMPOLINE.narrow(t2).run(); // 10
 * }</pre>
 *
 * @see Monad
 * @see TrampolineKind
 * @see Trampoline
 */
public class TrampolineMonad extends TrampolineFunctor implements Monad<TrampolineKind.Witness> {

  /** Singleton instance of {@code TrampolineMonad}. */
  public static final TrampolineMonad INSTANCE = new TrampolineMonad();

  private static final Class<TrampolineMonad> TRAMPOLINE_MONAD_CLASS = TrampolineMonad.class;

  /** Private constructor to enforce the singleton pattern. */
  protected TrampolineMonad() {
    super();
  }

  /**
   * Lifts a value into the {@link Trampoline} context, creating a completed computation.
   *
   * <p>This creates a {@link Trampoline.Done} instance that immediately completes with the given
   * value.
   *
   * @param <A> The type of the value.
   * @param value The value to lift. Can be {@code null} if {@code A} is a nullable type.
   * @return A {@code Kind} representing a completed {@link Trampoline} with the given value. Never
   *     {@code null}.
   */
  @Override
  public <A> Kind<TrampolineKind.Witness, A> of(@Nullable A value) {
    return TRAMPOLINE.widen(Trampoline.done(value));
  }

  /**
   * Sequentially composes two {@link Trampoline} computations in a stack-safe manner.
   *
   * <p>The first computation is evaluated (when {@link Trampoline#run()} is eventually called), its
   * result is passed to the function {@code f}, which produces a new computation that is then also
   * evaluated. The entire chain remains stack-safe regardless of depth.
   *
   * @param <A> The type of the value produced by the input {@code Trampoline}.
   * @param <B> The type of the value produced by the resulting {@code Trampoline}.
   * @param f The function to apply to the result of the first computation. Must not be {@code null}
   *     and must not return {@code null}.
   * @param ma The {@code Trampoline} to transform. Must not be {@code null}.
   * @return The result of the computation chain, as a {@code Kind}. Never {@code null}.
   * @throws NullPointerException if {@code f} or {@code ma} is {@code null}, or if {@code f}
   *     returns {@code null}.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<TrampolineKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<TrampolineKind.Witness, B>> f,
      Kind<TrampolineKind.Witness, A> ma) {

    Validation.function().requireFlatMapper(f, "f", TRAMPOLINE_MONAD_CLASS, FLAT_MAP);
    Validation.kind().requireNonNull(ma, TRAMPOLINE_MONAD_CLASS, FLAT_MAP);

    Trampoline<A> trampolineA = TRAMPOLINE.narrow(ma);

    Trampoline<B> resultTrampoline =
        trampolineA.flatMap(
            a -> {
              Kind<TrampolineKind.Witness, B> kindB = f.apply(a);
              Validation.function()
                  .requireNonNullResult(
                      kindB, "f", TRAMPOLINE_MONAD_CLASS, FLAT_MAP, Trampoline.class);
              return TRAMPOLINE.narrow(kindB);
            });

    return TRAMPOLINE.widen(resultTrampoline);
  }

  /**
   * Applies a function wrapped in a {@link Trampoline} to a value wrapped in a {@link Trampoline},
   * maintaining stack safety.
   *
   * <p>When evaluated, this will first evaluate the function trampoline, then the argument
   * trampoline, and finally apply the function to the value.
   *
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @param ff The {@code Trampoline} containing the function. Must not be {@code null}.
   * @param fa The {@code Trampoline} containing the value. Must not be {@code null}.
   * @return The result of applying the function to the value, wrapped in a {@code Trampoline}.
   *     Never {@code null}.
   * @throws NullPointerException if {@code ff} or {@code fa} is {@code null}.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped.
   */
  @Override
  public <A, B> Kind<TrampolineKind.Witness, B> ap(
      Kind<TrampolineKind.Witness, ? extends Function<A, B>> ff,
      Kind<TrampolineKind.Witness, A> fa) {

    Validation.kind().requireNonNull(ff, TRAMPOLINE_MONAD_CLASS, AP, "function");
    Validation.kind().requireNonNull(fa, TRAMPOLINE_MONAD_CLASS, AP, "argument");

    Trampoline<? extends Function<A, B>> trampolineF = TRAMPOLINE.narrow(ff);
    Trampoline<A> trampolineA = TRAMPOLINE.narrow(fa);

    // Implement ap using flatMap for consistency
    Trampoline<B> resultTrampoline = trampolineF.flatMap(f -> trampolineA.map(f));
    return TRAMPOLINE.widen(resultTrampoline);
  }
}
