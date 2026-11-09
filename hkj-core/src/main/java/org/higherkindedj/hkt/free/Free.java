// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.higherkindedj.hkt.util.validation.Operation.CONSTRUCTION;
import static org.higherkindedj.hkt.util.validation.Operation.FOLD_MAP;
import static org.higherkindedj.hkt.util.validation.Operation.FROM_KIND;
import static org.higherkindedj.hkt.util.validation.Operation.PURE;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.trampoline.Trampoline;
import org.higherkindedj.hkt.util.validation.Validation;

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
 *   <li>Optimising programs before execution
 * </ul>
 *
 * <p>The implementation has five constructors:
 *
 * <ul>
 *   <li>{@code Pure} - A completed computation with a value
 *   <li>{@code Suspend} - A suspended computation in the functor F
 *   <li>{@code FlatMapped} - A sequenced computation (flatMap optimisation)
 *   <li>{@code HandleError} - A program with a handler for a failure of an error type
 *   <li>{@code Ap} - Independent instructions combined applicatively
 * </ul>
 *
 * <p>As part of the HKT simulation, {@code Free} extends {@link FreeKind}, so every {@code Free} is
 * already a {@code Kind<FreeKind.Witness<F>, A>}. Widening via {@link FreeKindHelper} is therefore
 * a cast-free, allocation-free upcast (no wrapper object), and a new {@code Free} case that forgot
 * the relationship would be a compile error.
 *
 * @param <F> The functor type representing the instruction set
 * @param <A> The result type
 */
public sealed interface Free<F extends WitnessArity<TypeArity.Unary>, A> extends FreeKind<F, A>
    permits Free.Pure, Free.Suspend, Free.FlatMapped, Free.HandleError, Free.Ap {

  /**
   * Terminal case representing a pure value.
   *
   * @param value The value. Must not be null.
   * @param <F> The functor type
   * @param <A> The value type
   */
  record Pure<F extends WitnessArity<TypeArity.Unary>, A>(A value) implements Free<F, A> {
    public Pure {
      Validation.coreType().requireValue(value, Free.class, CONSTRUCTION);
    }
  }

  /**
   * Suspended computation wrapping a single instruction in F.
   *
   * @param <F> The functor type
   * @param <A> The result type
   */
  record Suspend<F extends WitnessArity<TypeArity.Unary>, A>(Kind<F, Free<F, A>> computation)
      implements Free<F, A> {}

  /**
   * Chained computation representing flatMap. This constructor enables stack-safe execution of
   * deeply nested flatMaps.
   *
   * @param <F> The functor type
   * @param <X> The intermediate result type
   * @param <A> The final result type
   */
  record FlatMapped<F extends WitnessArity<TypeArity.Unary>, X, A>(
      Free<F, X> sub, Function<? super X, ? extends Free<F, A>> continuation)
      implements Free<F, A> {}

  /**
   * Error recovery wrapper. Wraps a sub-program with a recovery strategy, mirroring try/catch
   * semantics. During interpretation, if the target monad is a {@link MonadError}, the handler is
   * invoked on failure. If the target monad is not a {@code MonadError}, the handler is ignored and
   * the inner program is interpreted normally.
   *
   * @param program The sub-program to protect
   * @param handler The recovery function, applied to the error if one occurs
   * @param errorType The class of the error type for type-safe dispatch
   * @param <F> The functor type
   * @param <E> The error type
   * @param <A> The result type
   */
  record HandleError<F extends WitnessArity<TypeArity.Unary>, E, A>(
      Free<F, A> program, Function<? super E, ? extends Free<F, A>> handler, Class<E> errorType)
      implements Free<F, A> {
    public HandleError {
      Validation.function().require(program, "program", CONSTRUCTION);
      Validation.function().require(handler, "handler", CONSTRUCTION);
      Validation.function().require(errorType, "errorType", CONSTRUCTION);
    }
  }

  /**
   * Applicative sub-expression wrapper. Wraps a {@link FreeAp} expression within a {@code Free}
   * program, preserving the applicative structure for interpreters that can exploit parallelism or
   * for static analysis.
   *
   * <p>During interpretation, the {@code FreeAp} is interpreted using the target monad's {@link
   * Applicative} instance (all monads are applicatives). For monads like IO or VTask that override
   * {@code map2} with concurrent execution, this yields true parallelism. For sequential monads
   * (Id, State), {@code map2} defaults to sequential evaluation.
   *
   * @param applicative The applicative sub-expression
   * @param <F> The functor type
   * @param <A> The result type
   */
  record Ap<F extends WitnessArity<TypeArity.Unary>, A>(FreeAp<F, A> applicative)
      implements Free<F, A> {
    public Ap {
      Validation.function().require(applicative, "applicative", CONSTRUCTION);
    }
  }

  /**
   * Creates a Free monad from a pure value.
   *
   * @param value The value to lift. Must not be null.
   * @param <F> The functor type
   * @param <A> The value type
   * @return A Free monad containing the value
   * @throws NullPointerException if value is null
   */
  static <F extends WitnessArity<TypeArity.Unary>, A> Free<F, A> pure(A value) {
    Validation.coreType().requireValue(value, Free.class, PURE);
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
  static <F extends WitnessArity<TypeArity.Unary>, A> Free<F, A> suspend(
      Kind<F, Free<F, A>> computation) {
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
   * @param f The function to apply. Must not return null; a null result fails the program with a
   *     NullPointerException when it is interpreted.
   * @param <B> The result type
   * @return A new Free monad with the function applied
   */
  default <B> Free<F, B> map(Function<? super A, ? extends B> f) {
    return flatMap(a -> pure(Objects.requireNonNull(f.apply(a), "f must not return null")));
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
   * Wraps this Free program with typed error recovery. During interpretation, if the target monad
   * is a {@link MonadError}, the handler is invoked when an error of the specified type occurs. If
   * the target monad is not a {@code MonadError}, the handler is silently ignored.
   *
   * @param errorType The class of errors to catch
   * @param handler The recovery function
   * @param <E> The error type
   * @return A new Free program with error recovery
   */
  default <E> Free<F, A> handleError(
      Class<E> errorType, Function<? super E, ? extends Free<F, A>> handler) {
    return new HandleError<>(this, handler, errorType);
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
  static <F extends WitnessArity<TypeArity.Unary>, G extends WitnessArity<TypeArity.Unary>, A>
      Free<G, A> translate(Free<F, A> program, Natural<F, G> nat, Functor<G> functorG) {
    Validation.function().require(program, "program", FROM_KIND);
    Validation.function().require(nat, "nat", FROM_KIND);
    Validation.function().require(functorG, "functorG", FROM_KIND);
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
  private static <
          F extends WitnessArity<TypeArity.Unary>, G extends WitnessArity<TypeArity.Unary>, A>
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
      case HandleError<F, ?, A> he -> {
        var rawHe = (HandleError<F, Object, A>) he;
        yield translateTrampoline(rawHe.program(), nat, functorG)
            .map(
                translatedProg ->
                    new HandleError<>(
                        translatedProg,
                        e -> translateTrampoline(rawHe.handler().apply(e), nat, functorG).run(),
                        rawHe.errorType()));
      }
      case Ap<F, A> ap -> {
        FreeAp<G, A> translated = translateFreeAp(ap.applicative(), nat);
        yield Trampoline.done(new Ap<>(translated));
      }
    };
  }

  /** Translates a FreeAp structure from functor F to functor G by transforming each Lift node. */
  @SuppressWarnings("unchecked")
  private static <
          F extends WitnessArity<TypeArity.Unary>, G extends WitnessArity<TypeArity.Unary>, A>
      FreeAp<G, A> translateFreeAp(FreeAp<F, A> freeAp, Natural<F, G> nat) {
    return switch (freeAp) {
      case FreeAp.Pure<F, A> p -> new FreeAp.Pure<G, A>(p.value());
      case FreeAp.Lift<F, A> l -> new FreeAp.Lift<>(nat.apply(l.fa()));
      case FreeAp.Ap<F, ?, A> ap -> {
        var rawAp = (FreeAp.Ap<F, Object, A>) ap;
        FreeAp<G, Function<Object, A>> translatedFf = translateFreeAp(rawAp.ff(), nat);
        FreeAp<G, Object> translatedFa = translateFreeAp(rawAp.fa(), nat);
        yield new FreeAp.Ap<>(translatedFf, translatedFa);
      }
    };
  }

  /**
   * Interprets this Free monad into a target monad M using a type-safe {@link Natural}
   * transformation. This is the preferred method for interpreting Free monads as it provides
   * compile-time type safety for the transformation.
   *
   * <p>The interpreter walks the program in a loop, holding the {@code flatMap} continuations still
   * to run on a stack of its own.
   *
   * <p>Each instruction's whole value reaches the target monad: a {@code Writer}'s log, a {@code
   * List}'s other elements and an {@code EitherOrBoth}'s warnings are all kept. The interpreter
   * walks the program in a loop, with a stack of its own for continuations and {@code handleError}
   * handlers, so a deep program runs into {@code Id}, {@code Either}, {@code Writer}, {@code
   * EitherOrBoth}, {@code State} or {@code IO} without overflowing. Two things still deepen the
   * stack: an instruction whose value holds several results, such as a {@code List} of two, runs
   * the rest of the program once for each, and a target whose own {@code flatMap} nests, such as
   * {@code Reader}, nests one call for each instruction when it runs.
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
   * @throws NullPointerException if {@code transform} or {@code monad} is null
   * @see Natural
   */
  default <M extends WitnessArity<TypeArity.Unary>> Kind<M, A> foldMap(
      Natural<F, M> transform, Monad<M> monad) {
    Validation.function().require(transform, "transform", FOLD_MAP);
    Validation.function().require(monad, "monad", FOLD_MAP);
    return FreeInterpreter.foldMap(this, transform, monad);
  }

  /**
   * Interprets this Free monad into a target monad M using a raw function transformation.
   *
   * <p>This method is provided for backwards compatibility and convenience when a full {@link
   * Natural} transformation is not needed. For type-safe interpretation, prefer {@link
   * #foldMap(Natural, Monad)}.
   *
   * <p>It interprets as {@link #foldMap(Natural, Monad)} does.
   *
   * <p>Each instruction's whole value reaches the target monad: a {@code Writer}'s log, a {@code
   * List}'s other elements and an {@code EitherOrBoth}'s warnings are all kept. The interpreter
   * walks the program in a loop, with a stack of its own for continuations and {@code handleError}
   * handlers, so a deep program runs into {@code Id}, {@code Either}, {@code Writer}, {@code
   * EitherOrBoth}, {@code State} or {@code IO} without overflowing. Two things still deepen the
   * stack: an instruction whose value holds several results, such as a {@code List} of two, runs
   * the rest of the program once for each, and a target whose own {@code flatMap} nests, such as
   * {@code Reader}, nests one call for each instruction when it runs.
   *
   * @param transform The transformation function from F to M (natural transformation as function)
   * @param monad The monad instance for M
   * @param <M> The target monad type
   * @return The interpreted result in monad M
   * @throws NullPointerException if {@code transform} or {@code monad} is null
   * @see #foldMap(Natural, Monad)
   */
  default <M extends WitnessArity<TypeArity.Unary>> Kind<M, A> foldMap(
      Function<Kind<F, ?>, Kind<M, ?>> transform, Monad<M> monad) {
    Validation.function().require(transform, "transform", FOLD_MAP);
    Validation.function().require(monad, "monad", FOLD_MAP);
    // Adapt the raw function to a Natural and interpret through the single Natural-based path.
    return FreeInterpreter.foldMap(this, asNatural(transform), monad);
  }

  /**
   * Adapts a raw {@code Function<Kind<F, ?>, Kind<M, ?>>} into a {@link Natural} so the
   * function-based {@link #foldMap(Function, Monad)} can reuse the single type-safe interpreter.
   *
   * <p>This is the one place where the function overload's erased per-element correspondence is
   * reasserted: the function promises (by contract) to map {@code Kind<F, B>} to {@code Kind<M, B>}
   * for every {@code B}, which the {@code Natural} signature makes explicit. The unchecked cast is
   * confined here rather than scattered across a duplicate interpreter.
   *
   * @param transform the raw function transformation
   * @param <F> the source functor witness
   * @param <M> the target monad witness
   * @return a {@code Natural<F, M>} backed by {@code transform}
   */
  private static <F extends WitnessArity<TypeArity.Unary>, M extends WitnessArity<TypeArity.Unary>>
      Natural<F, M> asNatural(Function<Kind<F, ?>, Kind<M, ?>> transform) {
    return new Natural<>() {
      @Override
      @SuppressWarnings("unchecked") // function contract: Kind<F, B> maps to Kind<M, B> for every B
      public <B> Kind<M, B> apply(Kind<F, B> fa) {
        return (Kind<M, B>) transform.apply(fa);
      }
    };
  }
}
