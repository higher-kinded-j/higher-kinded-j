// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_op;

import static org.higherkindedj.hkt.util.validation.Operation.CONSTRUCTION;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.util.validation.Validation;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.jspecify.annotations.NullMarked;

/**
 * Effect algebra for optics-parameterised state operations within Free monad programs.
 *
 * <p>{@code StateOp<S, A>} has two type parameters: {@code S} is the state type (fixed by the
 * user), and {@code A} is the HKT-varying result type. Each record carries a continuation function
 * {@code Function<RawResult, A>} that maps the operation's raw result to the HKT carrier type. This
 * continuation-passing style enables the {@link StateOpFunctor} to compose with the continuation
 * rather than using cast-through (which only works for short-circuiting operations like {@link
 * org.higherkindedj.hkt.error.ErrorOp}).
 *
 * <p>Unlike traditional state effects that offer coarse-grained {@code get}/{@code put}, StateOp
 * operations are parameterised by optics, enabling:
 *
 * <ul>
 *   <li><b>Fine-grained access</b> — each operation declares exactly which part of the state it
 *       accesses
 *   <li><b>Static analysis</b> — programs can be inspected to determine which state fields are
 *       accessed without execution
 *   <li><b>Composability</b> — optics compose naturally
 * </ul>
 *
 * @param <S> The state type (fixed for a given program)
 * @param <A> The result type (HKT-varying parameter)
 * @see StateOpKind
 * @see StateOps
 */
@NullMarked
public sealed interface StateOp<S, A>
    permits StateOp.View,
        StateOp.Over,
        StateOp.Assign,
        StateOp.Preview,
        StateOp.TraverseOver,
        StateOp.GetState {

  /**
   * Maps a function over the continuation, producing a new StateOp with a different result type.
   * This is used by {@link StateOpFunctor} to implement the Functor contract.
   *
   * @param f The mapping function
   * @param <B> The new result type
   * @return A new StateOp with the composed continuation
   */
  <B> StateOp<S, B> mapK(Function<? super A, ? extends B> f);

  /**
   * Interprets this operation against a state value, producing the result and (possibly updated)
   * state. Each record implements this with full type knowledge of its internal {@code R}
   * parameter, so interpreters never need raw-type pattern matching.
   *
   * @param state The current state
   * @return A tuple of the new state and the mapped result
   */
  StateTuple<S, A> interpretState(S state);

  /**
   * Reads a value through a {@link Getter}.
   *
   * @param <S> The state type
   * @param <R> The raw result type (getter's focus)
   * @param <A> The mapped result type (HKT carrier)
   */
  record View<S, R, A>(Getter<S, R> optic, Function<R, A> k) implements StateOp<S, A> {
    public View {
      Validation.function().require(optic, "optic", CONSTRUCTION);
      Validation.function().require(k, "k", CONSTRUCTION);
    }

    @Override
    public <B> StateOp<S, B> mapK(Function<? super A, ? extends B> f) {
      return new View<>(optic, k.andThen(f));
    }

    @Override
    public StateTuple<S, A> interpretState(S state) {
      return StateTuple.of(state, k.apply(optic.get(state)));
    }
  }

  /**
   * Modifies a focus through a {@link Lens} and returns the new value.
   *
   * @param <S> The state type
   * @param <R> The raw result type (lens's focus)
   * @param <A> The mapped result type (HKT carrier)
   */
  record Over<S, R, A>(Lens<S, R> optic, Function<R, R> f, Function<R, A> k)
      implements StateOp<S, A> {
    public Over {
      Validation.function().require(optic, "optic", CONSTRUCTION);
      Validation.function().require(f, "f", CONSTRUCTION);
      Validation.function().require(k, "k", CONSTRUCTION);
    }

    @Override
    public <B> StateOp<S, B> mapK(Function<? super A, ? extends B> fn) {
      return new Over<>(optic, f, k.andThen(fn));
    }

    @Override
    public StateTuple<S, A> interpretState(S state) {
      R newValue = f.apply(optic.get(state));
      S newState = optic.set(newValue, state);
      return StateTuple.of(newState, k.apply(newValue));
    }
  }

  /**
   * Sets a focus through a {@link Lens} to a fixed value and returns the new value.
   *
   * @param <S> The state type
   * @param <R> The raw result type (lens's focus)
   * @param <A> The mapped result type (HKT carrier)
   */
  record Assign<S, R, A>(Lens<S, R> optic, R value, Function<R, A> k) implements StateOp<S, A> {
    public Assign {
      Validation.function().require(optic, "optic", CONSTRUCTION);
      Validation.function().require(value, "value", CONSTRUCTION);
      Validation.function().require(k, "k", CONSTRUCTION);
    }

    @Override
    public <B> StateOp<S, B> mapK(Function<? super A, ? extends B> fn) {
      return new Assign<>(optic, value, k.andThen(fn));
    }

    @Override
    public StateTuple<S, A> interpretState(S state) {
      S newState = optic.set(value, state);
      return StateTuple.of(newState, k.apply(value));
    }
  }

  /**
   * Reads a value through a {@link Prism}, returning an {@link Optional}.
   *
   * @param <S> The state type
   * @param <R> The prism's focus type
   * @param <A> The mapped result type (HKT carrier)
   */
  record Preview<S, R, A>(Prism<S, R> optic, Function<Optional<R>, A> k) implements StateOp<S, A> {
    public Preview {
      Validation.function().require(optic, "optic", CONSTRUCTION);
      Validation.function().require(k, "k", CONSTRUCTION);
    }

    @Override
    public <B> StateOp<S, B> mapK(Function<? super A, ? extends B> fn) {
      return new Preview<>(optic, k.andThen(fn));
    }

    @Override
    public StateTuple<S, A> interpretState(S state) {
      return StateTuple.of(state, k.apply(optic.getOptional(state)));
    }
  }

  /**
   * Modifies all targets of a {@link Traversal} and returns the modified state.
   *
   * @param <S> The state type
   * @param <R> The traversal's element type
   * @param <A> The mapped result type (HKT carrier)
   */
  record TraverseOver<S, R, A>(Traversal<S, R> optic, Function<R, R> f, Function<S, A> k)
      implements StateOp<S, A> {
    public TraverseOver {
      Validation.function().require(optic, "optic", CONSTRUCTION);
      Validation.function().require(f, "f", CONSTRUCTION);
      Validation.function().require(k, "k", CONSTRUCTION);
    }

    @Override
    public <B> StateOp<S, B> mapK(Function<? super A, ? extends B> fn) {
      return new TraverseOver<>(optic, f, k.andThen(fn));
    }

    @Override
    public StateTuple<S, A> interpretState(S state) {
      S newState = Traversals.modify(optic, f, state);
      return StateTuple.of(newState, k.apply(newState));
    }
  }

  /**
   * Reads the entire state.
   *
   * @param <S> The state type
   * @param <A> The mapped result type (HKT carrier)
   */
  record GetState<S, A>(Function<S, A> k) implements StateOp<S, A> {
    public GetState {
      Validation.function().require(k, "k", CONSTRUCTION);
    }

    @Override
    public <B> StateOp<S, B> mapK(Function<? super A, ? extends B> fn) {
      return new GetState<>(k.andThen(fn));
    }

    @Override
    public StateTuple<S, A> interpretState(S state) {
      return StateTuple.of(state, k.apply(state));
    }
  }
}
