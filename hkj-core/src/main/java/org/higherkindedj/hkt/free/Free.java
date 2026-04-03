// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import java.util.Objects;
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
      Free<F, X> sub, Function<? super X, ? extends Free<F, A>> continuation)
      implements Free<F, A> {}

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
  default <B> Free<F, B> flatMap(Function<? super A, ? extends Free<F, B>> f) {
    return new FlatMapped<>(this, f);
  }

  /**
   * Translates a Free program from functor F to functor G using a natural transformation, requiring
   * a Functor instance for G to map over suspended computations.
   *
   * <p>This rebuilds the Free structure, replacing every {@code Suspend} node's instruction from F
   * with the corresponding instruction in G. The program's structure (Pure, FlatMapped chains) is
   * preserved.
   *
   * <p>The implementation is stack-safe: {@code FlatMapped} nodes are not recursively traversed
   * during translation. Instead, translation of continuations is deferred into new {@code
   * FlatMapped} nodes, so the work happens lazily during subsequent {@code foldMap} interpretation.
   *
   * <p>This is the primary mechanism for lifting single-effect programs into combined-effect
   * programs via {@link org.higherkindedj.hkt.inject.Inject}:
   *
   * <pre>{@code
   * Free<ConsoleOpKind.Witness, String> consoleProgram = ...;
   * Inject<ConsoleOpKind.Witness, AppEffects> inject = ...;
   * Functor<AppEffects> functor = ...;
   * Free<AppEffects, String> combined = Free.translate(consoleProgram, inject::inject, functor);
   * }</pre>
   *
   * @param program The Free program to translate. Must not be null.
   * @param nat The natural transformation from F to G. Must not be null.
   * @param functorG The Functor instance for the target type G. Must not be null.
   * @param <F> The source functor type
   * @param <G> The target functor type
   * @param <A> The result type
   * @return A new Free program in functor G with the same structure
   */
  @SuppressWarnings("unchecked")
  static <F extends WitnessArity<?>, G extends WitnessArity<TypeArity.Unary>, A>
      Free<G, A> translate(Free<F, A> program, Natural<F, G> nat, Functor<G> functorG) {
    Objects.requireNonNull(program, "program must not be null");
    Objects.requireNonNull(nat, "nat must not be null");
    Objects.requireNonNull(functorG, "functorG must not be null");
    return translateTrampoline(program, nat, functorG).run();
  }

  /**
   * Stack-safe translate implementation using {@link Trampoline}.
   *
   * <p>Uses {@code Trampoline.defer} to avoid stack overflow on deeply nested or left-associated
   * {@code FlatMapped} chains. The {@code FlatMapped} case defers sub-program translation through
   * the trampoline, ensuring left-associated chains do not consume stack frames proportional to
   * depth.
   *
   * <p>The {@code Suspend} case calls {@code translateTrampoline(...).run()} inside {@code
   * functorG.map}, which is not fully stack-safe for hypothetical deeply nested {@code Suspend}
   * chains with a strict functor. In practice this is not reachable: {@code Free.liftF} always
   * wraps a {@code Pure} inside the {@code Suspend}, so nested {@code Suspend} without intervening
   * {@code FlatMapped} nodes does not occur through the public API. Deep program chains use {@code
   * FlatMapped}, which is fully trampolined.
   */
  @SuppressWarnings("unchecked")
  private static <F extends WitnessArity<?>, G extends WitnessArity<TypeArity.Unary>, A>
      Trampoline<Free<G, A>> translateTrampoline(
          Free<F, A> program, Natural<F, G> nat, Functor<G> functorG) {
    return switch (program) {
      case Pure<F, A> p -> Trampoline.done(new Pure<G, A>(p.value()));
      case Suspend<F, A> s -> {
        Kind<G, Free<F, A>> translated = nat.apply(s.computation());
        Kind<G, Free<G, A>> fullyTranslated =
            functorG.map(inner -> translateTrampoline(inner, nat, functorG).run(), translated);
        yield Trampoline.done(new Suspend<>(fullyTranslated));
      }
      case FlatMapped<F, ?, A> fm -> {
        var rawFm = (FlatMapped<F, Object, A>) fm;
        yield Trampoline.defer(
            () ->
                translateTrampoline(rawFm.sub(), nat, functorG)
                    .map(
                        translatedSub ->
                            new FlatMapped<>(
                                translatedSub,
                                x ->
                                    translateTrampoline(
                                            rawFm.continuation().apply(x), nat, functorG)
                                        .run())));
      }
    };
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

        // Try to extract the inner Free eagerly (works for strict monads like Identity).
        // For strict monads, monad.map evaluates immediately, allowing us to defer
        // the recursive interpretation through the Trampoline for stack safety.
        // For lazy monads (IO, State), the monad's own laziness provides stack safety.
        @SuppressWarnings("unchecked")
        Free<F, A>[] innerFreeRef = (Free<F, A>[]) new Free[1];
        boolean[] eager = {false};
        monad.map(
            innerFree -> {
              innerFreeRef[0] = innerFree;
              eager[0] = true;
              return innerFree;
            },
            transformed);

        if (eager[0]) {
          // Strict monad: defer interpretation through Trampoline for stack safety
          Free<F, A> innerFree = innerFreeRef[0];
          yield Trampoline.defer(() -> interpretFree(innerFree, transform, monad));
        } else {
          // Lazy monad: use monad.flatMap (the monad's laziness provides stack safety)
          yield Trampoline.done(
              monad.flatMap(
                  innerFree -> interpretFree(innerFree, transform, monad).run(), transformed));
        }
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
        Kind<M, Free<F, A>> transformed = transform.apply(suspend.computation());

        // Try to extract the inner Free eagerly (works for strict monads).
        @SuppressWarnings("unchecked")
        Free<F, A>[] innerFreeRef = (Free<F, A>[]) new Free[1];
        boolean[] eager = {false};
        monad.map(
            innerFree -> {
              innerFreeRef[0] = innerFree;
              eager[0] = true;
              return innerFree;
            },
            transformed);

        if (eager[0]) {
          Free<F, A> innerFree = innerFreeRef[0];
          yield Trampoline.defer(() -> interpretFreeNatural(innerFree, transform, monad));
        } else {
          yield Trampoline.done(
              monad.flatMap(
                  innerFree -> interpretFreeNatural(innerFree, transform, monad).run(),
                  transformed));
        }
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
