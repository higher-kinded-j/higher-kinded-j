// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
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
public sealed interface Free<F extends WitnessArity<?>, A>
    permits Free.Pure, Free.Suspend, Free.FlatMapped {

  /**
   * Terminal case representing a pure value.
   *
   * @param <F> The functor type
   * @param <A> The value type
   */
  record Pure<F extends WitnessArity<?>, A>(A value) implements Free<F, A> {}

  /**
   * Suspended computation wrapping a single instruction in F.
   *
   * @param <F> The functor type
   * @param <A> The result type
   */
  record Suspend<F extends WitnessArity<?>, A>(Kind<F, Free<F, A>> computation)
      implements Free<F, A> {}

  /**
   * Chained computation representing flatMap. This constructor enables stack-safe execution of
   * deeply nested flatMaps.
   *
   * @param <F> The functor type
   * @param <X> The intermediate result type
   * @param <A> The final result type
   */
  record FlatMapped<F extends WitnessArity<?>, X, A>(
      Free<F, X> sub, Function<X, Free<F, A>> continuation) implements Free<F, A> {}

  /**
   * Creates a Free monad from a pure value.
   *
   * @param value The value to lift
   * @param <F> The functor type
   * @param <A> The value type
   * @return A Free monad containing the value
   */
  static <F extends WitnessArity<?>, A> Free<F, A> pure(A value) {
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
  static <F extends WitnessArity<?>, A> Free<F, A> suspend(Kind<F, Free<F, A>> computation) {
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
  static <F extends WitnessArity<TypeArity.Unary>, A> Free<F, A> liftF(
      Kind<F, A> fa, Functor<F> functor) {
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
   * Interprets this Free monad into a target monad M using a type-safe {@link Natural}
   * transformation. This is the preferred method for interpreting Free monads as it provides
   * compile-time type safety for the transformation.
   *
   * <p>This is a stack-safe interpreter that uses the {@link Trampoline} monad internally to ensure
   * stack safety during Free structure traversal.
   *
   * <p>By leveraging Higher-Kinded-J's own Trampoline implementation, this method demonstrates the
   * composability and practical utility of the library's abstractions whilst ensuring stack-safe
   * execution for deeply nested Free structures.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * // Define a natural transformation from your DSL to IO
   * Natural<MyDSL.Witness, IO.Witness> interpreter = new Natural<>() {
   *   @Override
   *   public <A> Kind<IO.Witness, A> apply(Kind<MyDSL.Witness, A> fa) {
   *     return switch (MyDSLKindHelper.narrow(fa)) {
   *       case ReadOp<?> r -> IO.of(() -> readFromConsole());
   *       case WriteOp<?> w -> IO.of(() -> writeToConsole(w.message()));
   *     };
   *   }
   * };
   *
   * // Interpret the Free program
   * Free<MyDSL.Witness, String> program = ...;
   * IO<String> executable = program.foldMap(interpreter, ioMonad);
   * }</pre>
   *
   * @param transform The natural transformation from F to M. Must not be null.
   * @param monad The monad instance for M. Must not be null.
   * @param <M> The target monad type
   * @return The interpreted result in monad M
   * @see Natural
   */
  default <M extends WitnessArity<TypeArity.Unary>> Kind<M, A> foldMap(
      Natural<F, M> transform, Monad<M> monad) {
    return interpretFreeNatural(this, transform, monad).run();
  }

  /**
   * Interprets this Free monad into a target monad M using a raw function transformation.
   *
   * <p>This method is provided for backwards compatibility and convenience when a full {@link
   * Natural} transformation is not needed. For type-safe interpretation, prefer {@link
   * #foldMap(Natural, Monad)}.
   *
   * <p>This is a stack-safe interpreter that uses the {@link Trampoline} monad internally to ensure
   * stack safety during Free structure traversal.
   *
   * @param transform The transformation function from F to M (natural transformation as function)
   * @param monad The monad instance for M
   * @param <M> The target monad type
   * @return The interpreted result in monad M
   * @see #foldMap(Natural, Monad)
   */
  default <M extends WitnessArity<TypeArity.Unary>> Kind<M, A> foldMap(
      Function<Kind<F, ?>, Kind<M, ?>> transform, Monad<M> monad) {
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
  private static <F extends WitnessArity<?>, M extends WitnessArity<TypeArity.Unary>, A>
      Trampoline<Kind<M, A>> interpretFree(
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

  /**
   * Internal helper that interprets a Free monad using a type-safe {@link Natural} transformation
   * and Trampoline for stack-safe traversal.
   *
   * @param free The Free monad to interpret
   * @param transform The natural transformation from F to M
   * @param monad The monad instance for M
   * @param <F> The functor type
   * @param <M> The target monad type
   * @param <A> The result type
   * @return A Trampoline that produces the interpreted result in monad M
   */
  private static <F extends WitnessArity<?>, M extends WitnessArity<TypeArity.Unary>, A>
      Trampoline<Kind<M, A>> interpretFreeNatural(
          Free<F, A> free, Natural<F, M> transform, Monad<M> monad) {

    return switch (free) {
      case Pure<F, A> pure ->
          // Terminal case: lift the pure value into the target monad
          Trampoline.done(monad.of(pure.value()));

      case Suspend<F, A> suspend -> {
        // Transform the suspended computation using the type-safe Natural transformation
        // The Natural transformation properly handles the type: Kind<F, Free<F, A>> -> Kind<M,
        // Free<F, A>>
        Kind<M, Free<F, A>> transformed = transform.apply(suspend.computation());

        // Use Trampoline.defer to ensure stack safety for nested interpretations
        yield Trampoline.done(
            monad.flatMap(
                innerFree -> interpretFreeNatural(innerFree, transform, monad).run(), transformed));
      }

      case FlatMapped<F, ?, A> flatMapped -> {
        // Handle FlatMapped by deferring the interpretation
        @SuppressWarnings("unchecked")
        FlatMapped<F, Object, A> fm = (FlatMapped<F, Object, A>) flatMapped;

        yield Trampoline.defer(
            () ->
                interpretFreeNatural(fm.sub(), transform, monad)
                    .map(
                        kindOfX ->
                            monad.flatMap(
                                x -> {
                                  Free<F, A> next = fm.continuation().apply(x);
                                  return interpretFreeNatural(next, transform, monad).run();
                                },
                                kindOfX)));
      }
    };
  }
}
