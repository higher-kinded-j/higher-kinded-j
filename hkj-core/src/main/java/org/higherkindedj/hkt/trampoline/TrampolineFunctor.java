// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import static org.higherkindedj.hkt.trampoline.TrampolineKindHelper.TRAMPOLINE;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Functor} type class for {@link TrampolineKind}.
 *
 * <p>This allows {@code Trampoline} to be used in contexts that expect a {@link Functor} instance,
 * providing a way to apply a function to the value that will eventually be produced by a {@code
 * Trampoline} computation, without needing to explicitly unwrap and wrap it. The witness type for
 * {@code Trampoline} in the HKT system is {@link TrampolineKind.Witness}.
 *
 * <p>The mapping operation is stack-safe and deferred - the function is not applied until the
 * trampoline is actually evaluated via {@link Trampoline#run()}.
 *
 * @see Functor
 * @see TrampolineKind
 * @see TrampolineKind.Witness
 */
public class TrampolineFunctor implements Functor<TrampolineKind.Witness> {

  private static final Class<TrampolineFunctor> TRAMPOLINE_FUNCTOR_CLASS = TrampolineFunctor.class;

  /** Singleton instance of {@code TrampolineFunctor}. */
  public static final TrampolineFunctor INSTANCE = new TrampolineFunctor();

  /** Protected constructor to allow subclassing (e.g., by {@code TrampolineMonad}). */
  protected TrampolineFunctor() {}

  /**
   * Applies a function to the eventual result of a {@link Trampoline} computation, maintaining
   * stack safety.
   *
   * <p>This method leverages {@link TrampolineKindHelper#narrow(Kind)
   * TrampolineKindHelper.TRAMPOLINE.narrow(Kind)} to access the underlying {@link Trampoline}
   * instance and {@link TrampolineKindHelper#widen(Trampoline)
   * TrampolineKindHelper.TRAMPOLINE.widen(Trampoline)} to package the result.
   *
   * <p>The function {@code f} is not executed immediately - it will only be applied when the
   * resulting trampoline is evaluated via {@link Trampoline#run()}.
   *
   * @param <A> The type of the value produced by the input {@code Trampoline}.
   * @param <B> The type of the value produced by the resulting {@code Trampoline} after applying
   *     the function.
   * @param f The function to apply to the eventual result. Must not be {@code null}.
   * @param fa The input {@code TrampolineKind<A>} instance, which is a {@link Kind} representing a
   *     {@link Trampoline}. Must not be {@code null}.
   * @return A new {@code TrampolineKind<B>} that will produce the result of applying {@code f} to
   *     the result of {@code fa} when evaluated. Never {@code null}.
   * @throws NullPointerException if {@code f} or {@code fa} is {@code null}.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped
   *     to a valid {@code Trampoline} representation.
   */
  @Override
  public <A, B> Kind<TrampolineKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<TrampolineKind.Witness, A> fa) {

    Validation.function().requireMapper(f, "f", TRAMPOLINE_FUNCTOR_CLASS, MAP);
    Validation.kind().requireNonNull(fa, TRAMPOLINE_FUNCTOR_CLASS, MAP);

    Trampoline<A> trampolineA = TRAMPOLINE.narrow(fa);
    Trampoline<B> resultTrampoline = trampolineA.map(f);
    return TRAMPOLINE.widen(resultTrampoline);
  }
}
