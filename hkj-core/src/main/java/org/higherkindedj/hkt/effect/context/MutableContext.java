// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateT;
import org.higherkindedj.hkt.state_t.StateTKind;
import org.higherkindedj.hkt.state_t.StateTMonad;

/**
 * Effect context for stateful computations using {@link StateT}.
 *
 * <p>MutableContext wraps {@link StateT} with a user-friendly API, hiding the complexity of
 * higher-kinded types while preserving the full capability of the transformer. It provides a clean
 * way to thread mutable state through a computation.
 *
 * <h2>Factory Methods</h2>
 *
 * <ul>
 *   <li>{@link #io(Function)} - Create from a state transformation function
 *   <li>{@link #pure(Object)} - Lift a value without changing state
 *   <li>{@link #get()} - Get the current state
 *   <li>{@link #put(Object)} - Set a new state
 *   <li>{@link #modify(UnaryOperator)} - Modify the current state
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * record Counter(int count) {}
 *
 * MutableContext<IOKind.Witness, Counter, Integer> workflow =
 *     MutableContext.<Counter>get()
 *         .map(Counter::count)
 *         .flatMap(count -> MutableContext.<Counter, Integer>io(
 *             s -> StateTuple.of(new Counter(s.count() + 1), count)));
 *
 * StateTuple<Counter, Integer> result = workflow
 *     .runWith(new Counter(0))
 *     .unsafeRun();
 * // result.value() == 0, result.state().count() == 1
 * }</pre>
 *
 * @param <F> the underlying effect type witness (e.g., {@code IOKind.Witness})
 * @param <S> the state type
 * @param <A> the value type
 */
public final class MutableContext<F, S, A> implements EffectContext<F, A> {

  private final StateT<S, F, A> transformer;
  private final Monad<F> outerMonad;
  private final StateTMonad<S, F> stateTMonad;

  private MutableContext(StateT<S, F, A> transformer, Monad<F> outerMonad) {
    this.transformer = Objects.requireNonNull(transformer, "transformer must not be null");
    this.outerMonad = Objects.requireNonNull(outerMonad, "outerMonad must not be null");
    this.stateTMonad = StateTMonad.instance(outerMonad);
  }

  // --- Factory Methods for IO-based contexts ---

  /**
   * Creates a MutableContext from a state transformation function.
   *
   * <p>The function takes the current state and returns a tuple of (new state, value).
   *
   * @param computation the state transformation function; must not be null
   * @param <S> the state type
   * @param <A> the value type
   * @return a new MutableContext wrapping the computation
   * @throws NullPointerException if computation is null
   */
  public static <S, A> MutableContext<IOKind.Witness, S, A> io(
      Function<S, StateTuple<S, A>> computation) {
    Objects.requireNonNull(computation, "computation must not be null");

    StateT<S, IOKind.Witness, A> transformer =
        StateT.create(s -> IO_OP.widen(IO.delay(() -> computation.apply(s))), IOMonad.INSTANCE);
    return new MutableContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates a MutableContext containing the given value without changing state.
   *
   * @param value the value to contain
   * @param <S> the state type
   * @param <A> the value type
   * @return a new MutableContext containing the value
   */
  public static <S, A> MutableContext<IOKind.Witness, S, A> pure(A value) {
    StateT<S, IOKind.Witness, A> transformer =
        StateT.create(s -> IO_OP.widen(IO.delay(() -> StateTuple.of(s, value))), IOMonad.INSTANCE);
    return new MutableContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates a MutableContext that returns the current state as the value.
   *
   * @param <S> the state type
   * @return a new MutableContext that yields the current state
   */
  public static <S> MutableContext<IOKind.Witness, S, S> get() {
    StateT<S, IOKind.Witness, S> transformer =
        StateT.create(s -> IO_OP.widen(IO.delay(() -> StateTuple.of(s, s))), IOMonad.INSTANCE);
    return new MutableContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates a MutableContext that sets a new state.
   *
   * @param state the new state to set
   * @param <S> the state type
   * @return a new MutableContext that sets the state and returns Unit
   */
  public static <S> MutableContext<IOKind.Witness, S, Unit> put(S state) {
    StateT<S, IOKind.Witness, Unit> transformer =
        StateT.create(
            ignored -> IO_OP.widen(IO.delay(() -> StateTuple.of(state, Unit.INSTANCE))),
            IOMonad.INSTANCE);
    return new MutableContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates a MutableContext that modifies the current state.
   *
   * @param modifier the function to modify the state; must not be null
   * @param <S> the state type
   * @return a new MutableContext that modifies the state and returns Unit
   * @throws NullPointerException if modifier is null
   */
  public static <S> MutableContext<IOKind.Witness, S, Unit> modify(UnaryOperator<S> modifier) {
    Objects.requireNonNull(modifier, "modifier must not be null");
    StateT<S, IOKind.Witness, Unit> transformer =
        StateT.create(
            s -> IO_OP.widen(IO.delay(() -> StateTuple.of(modifier.apply(s), Unit.INSTANCE))),
            IOMonad.INSTANCE);
    return new MutableContext<>(transformer, IOMonad.INSTANCE);
  }

  // --- Chainable Operations ---

  @Override
  @SuppressWarnings("unchecked")
  public <B> MutableContext<F, S, B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    Kind<StateTKind.Witness<S, F>, B> result = stateTMonad.map(mapper, transformer);
    return new MutableContext<>(StateTKind.narrow(result), outerMonad);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <B> MutableContext<F, S, B> via(Function<? super A, ? extends EffectContext<F, B>> fn) {
    Objects.requireNonNull(fn, "fn must not be null");

    Kind<StateTKind.Witness<S, F>, B> result =
        stateTMonad.flatMap(
            a -> {
              EffectContext<F, B> next = fn.apply(a);
              Objects.requireNonNull(next, "fn must not return null");
              if (!(next instanceof MutableContext<?, ?, ?> nextCtx)) {
                throw new IllegalArgumentException(
                    "via function must return a MutableContext, got: " + next.getClass().getName());
              }
              @SuppressWarnings("unchecked")
              MutableContext<F, S, B> typedNext = (MutableContext<F, S, B>) nextCtx;
              return typedNext.transformer;
            },
            transformer);

    return new MutableContext<>(StateTKind.narrow(result), outerMonad);
  }

  /**
   * Chains a dependent computation using MutableContext-specific typing.
   *
   * <p>This is a convenience method that preserves the state type in the signature.
   *
   * @param fn the function to apply, returning a new MutableContext; must not be null
   * @param <B> the type of the value in the returned context
   * @return the context returned by the function
   * @throws NullPointerException if fn is null or returns null
   */
  public <B> MutableContext<F, S, B> flatMap(
      Function<? super A, ? extends MutableContext<F, S, B>> fn) {
    Objects.requireNonNull(fn, "fn must not be null");

    Kind<StateTKind.Witness<S, F>, B> result =
        stateTMonad.flatMap(
            a -> {
              MutableContext<F, S, B> next = fn.apply(a);
              Objects.requireNonNull(next, "fn must not return null");
              return next.transformer;
            },
            transformer);

    return new MutableContext<>(StateTKind.narrow(result), outerMonad);
  }

  /**
   * Sequences an independent computation, discarding this context's value.
   *
   * <p>This is useful for sequencing effects where only the final result matters.
   *
   * @param supplier provides the next context; must not be null
   * @param <B> the type of the value in the returned context
   * @return the context from the supplier
   * @throws NullPointerException if supplier is null or returns null
   */
  public <B> MutableContext<F, S, B> then(Supplier<? extends MutableContext<F, S, B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return flatMap(ignored -> supplier.get());
  }

  // --- Execution Methods (IO-specific) ---

  /**
   * Runs the stateful computation with the given initial state.
   *
   * <p>Returns both the final value and final state.
   *
   * @param initialState the initial state
   * @return an IOPath that will produce the state tuple when run
   * @throws ClassCastException if F is not IOKind.Witness
   */
  @SuppressWarnings("unchecked")
  public IOPath<StateTuple<S, A>> runWith(S initialState) {
    Kind<F, StateTuple<S, A>> result = transformer.runStateT(initialState);
    IO<StateTuple<S, A>> io = IO_OP.narrow((Kind<IOKind.Witness, StateTuple<S, A>>) result);
    return Path.ioPath(io);
  }

  /**
   * Runs the stateful computation and returns only the final value.
   *
   * @param initialState the initial state
   * @return an IOPath that will produce the value when run
   * @throws ClassCastException if F is not IOKind.Witness
   */
  @SuppressWarnings("unchecked")
  public IOPath<A> evalWith(S initialState) {
    Kind<F, A> result = transformer.evalStateT(initialState);
    IO<A> io = IO_OP.narrow((Kind<IOKind.Witness, A>) result);
    return Path.ioPath(io);
  }

  /**
   * Runs the stateful computation and returns only the final state.
   *
   * @param initialState the initial state
   * @return an IOPath that will produce the final state when run
   * @throws ClassCastException if F is not IOKind.Witness
   */
  @SuppressWarnings("unchecked")
  public IOPath<S> execWith(S initialState) {
    Kind<F, S> result = transformer.execStateT(initialState);
    IO<S> io = IO_OP.narrow((Kind<IOKind.Witness, S>) result);
    return Path.ioPath(io);
  }

  // --- Escape Hatch ---

  /**
   * Returns the underlying StateT transformer.
   *
   * <p>This is an escape hatch to Layer 3 (raw transformers) for users who need full control over
   * the transformer operations.
   *
   * @return the underlying StateT transformer
   */
  public StateT<S, F, A> toStateT() {
    return transformer;
  }

  @Override
  public Kind<?, A> underlying() {
    return transformer;
  }

  @Override
  public String toString() {
    return "MutableContext(" + transformer + ")";
  }
}
