// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.trampoline.Trampoline;

/**
 * Free monad for creating Domain-Specific Languages (DSLs).
 *
 * <p>The Free monad allows you to build programs as data structures that can be interpreted in
 * different ways. It's particularly useful for:
 *
 * <ul>
 *   <li>Creating DSLs with deferred execution
 *   <li>Separating program description from interpretation
 *   <li>Testing by providing mock interpreters
 *   <li>Optimizing programs before execution
 * </ul>
 *
 * <p>The implementation uses three constructors for stack-safe execution:
 *
 * <ul>
 *   <li>{@code Pure} - A completed computation with a value
 *   <li>{@code Suspend} - A suspended computation in the functor F
 *   <li>{@code FlatMapped} - A sequenced computation (flatMap optimization)
 * </ul>
 *
 * @param <F> The functor type representing the instruction set
 * @param <A> The result type
 */
public sealed interface Free<F, A> permits Free.Pure, Free.Suspend, Free.FlatMapped {

  /**
   * Terminal case representing a pure value.
   *
   * @param <F> The functor type
   * @param <A> The value type
   */
  record Pure<F, A>(A value) implements Free<F, A> {}

  /**
   * Suspended computation wrapping a single instruction in F.
   *
   * @param <F> The functor type
   * @param <A> The result type
   */
  record Suspend<F, A>(Kind<F, Free<F, A>> computation) implements Free<F, A> {}

  /**
   * Chained computation representing flatMap. This constructor enables stack-safe execution of
   * deeply nested flatMaps.
   *
   * @param <F> The functor type
   * @param <X> The intermediate result type
   * @param <A> The final result type
   */
  record FlatMapped<F, X, A>(Free<F, X> sub, Function<X, Free<F, A>> continuation)
      implements Free<F, A> {}

  /**
   * Creates a Free monad from a pure value.
   *
   * @param value The value to lift
   * @param <F> The functor type
   * @param <A> The value type
   * @return A Free monad containing the value
   */
  static <F, A> Free<F, A> pure(A value) {
    return new Pure<>(value);
  }

  /**
   * Creates a Free monad from a suspended computation.
   *
   * @param computation The computation to suspend
   * @param <F> The functor type
   * @param <A> The result type
   * @return A Free monad suspending the computation
   */
  static <F, A> Free<F, A> suspend(Kind<F, Free<F, A>> computation) {
    return new Suspend<>(computation);
  }

  /**
   * Lifts a single instruction in F into the Free monad.
   *
   * @param fa The instruction to lift
   * @param functor The functor instance for F
   * @param <F> The functor type
   * @param <A> The result type
   * @return A Free monad containing the lifted instruction
   */
  static <F, A> Free<F, A> liftF(Kind<F, A> fa, Functor<F> functor) {
    return new Suspend<>(functor.map(Free::pure, fa));
  }

  /**
   * Maps a function over the result of this Free monad.
   *
   * @param f The function to apply
   * @param <B> The result type
   * @return A new Free monad with the function applied
   */
  default <B> Free<F, B> map(Function<? super A, ? extends B> f) {
    return flatMap(a -> pure(f.apply(a)));
  }

  /**
   * Sequences this Free monad with a function that produces another Free monad.
   *
   * @param f The function to sequence with
   * @param <B> The result type
   * @return A new Free monad representing the sequence
   */
  default <B> Free<F, B> flatMap(Function<A, Free<F, B>> f) {
    return new FlatMapped<>(this, f);
  }

  /**
   * Interprets this Free monad into a target monad M using a natural transformation. This is a
   * stack-safe interpreter that uses the {@link Trampoline} monad internally to ensure stack safety
   * during Free structure traversal.
   *
   * <p>By leveraging Higher-Kinded-J's own Trampoline implementation, this method demonstrates the
   * composability and practical utility of the library's abstractions whilst ensuring stack-safe
   * execution for deeply nested Free structures.
   *
   * @param transform The natural transformation from F to M
   * @param monad The monad instance for M
   * @param <M> The target monad type
   * @return The interpreted result in monad M
   */
  default <M> Kind<M, A> foldMap(Function<Kind<F, ?>, Kind<M, ?>> transform, Monad<M> monad) {
    return interpretFree(this, transform, monad).run();
  }

  /**
   * Internal helper that interprets a Free monad using Trampoline for stack-safe traversal.
   *
   * @param free The Free monad to interpret
   * @param transform The natural transformation from F to M
   * @param monad The monad instance for M
   * @param <F> The functor type
   * @param <M> The target monad type
   * @param <A> The result type
   * @return A Trampoline that produces the interpreted result in monad M
   */
  private static <F, M, A> Trampoline<Kind<M, A>> interpretFree(
      Free<F, A> free, Function<Kind<F, ?>, Kind<M, ?>> transform, Monad<M> monad) {

    return switch (free) {
      case Pure<F, A> pure ->
          // Terminal case: lift the pure value into the target monad
          Trampoline.done(monad.of(pure.value()));

      case Suspend<F, A> suspend -> {
        // Transform the suspended computation and recursively interpret
        @SuppressWarnings("unchecked")
        Kind<M, Free<F, A>> transformed =
            (Kind<M, Free<F, A>>) transform.apply(suspend.computation());

        // Use Trampoline.defer to ensure stack safety for nested interpretations
        yield Trampoline.done(
            monad.flatMap(
                innerFree -> interpretFree(innerFree, transform, monad).run(), transformed));
      }

      case FlatMapped<F, ?, A> flatMapped -> {
        // Handle FlatMapped by deferring the interpretation
        @SuppressWarnings("unchecked")
        FlatMapped<F, Object, A> fm = (FlatMapped<F, Object, A>) flatMapped;

        yield Trampoline.defer(
            () ->
                interpretFree(fm.sub(), transform, monad)
                    .map(
                        kindOfX ->
                            monad.flatMap(
                                x -> {
                                  Free<F, A> next = fm.continuation().apply(x);
                                  return interpretFree(next, transform, monad).run();
                                },
                                kindOfX)));
      }
    };
  }
}
