// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.optics.Lens;

/**
 * Provides a fluent builder for state-threaded comprehensions using {@link Lens} optics.
 *
 * <p>This class enables declarative state management within monadic workflows, where state updates
 * are expressed through lenses rather than manual record copying. The state is automatically
 * threaded through each step, and lens operations provide type-safe, composable updates.
 *
 * <h3>Key Features</h3>
 *
 * <ul>
 *   <li><b>State threading:</b> Automatically carry state through each computation step
 *   <li><b>Lens-based updates:</b> Update state fields using lenses for type safety
 *   <li><b>Monadic composition:</b> Combine effectful operations with state management
 *   <li><b>Declarative syntax:</b> Clean, readable workflow definitions
 * </ul>
 *
 * <h3>Example Usage</h3>
 *
 * <pre>{@code
 * record WorkflowContext(String orderId, boolean validated, String confirmationId) {}
 *
 * Lens<WorkflowContext, Boolean> validatedLens =
 *     Lens.of(WorkflowContext::validated, (ctx, v) -> new WorkflowContext(ctx.orderId(), v, ctx.confirmationId()));
 * Lens<WorkflowContext, String> confirmationIdLens =
 *     Lens.of(WorkflowContext::confirmationId, (ctx, id) -> new WorkflowContext(ctx.orderId(), ctx.validated(), id));
 *
 * Kind<EitherT.Witness, WorkflowContext> result =
 *     ForState.withState(eitherTMonad, eitherTMonad.of(new WorkflowContext("ORD-123", false, null)))
 *         .update(validatedLens, true)
 *         .fromThen(ctx -> validateOrder(ctx.orderId()), validatedLens)
 *         .fromThen(ctx -> processPayment(ctx), confirmationIdLens)
 *         .yield();
 * }</pre>
 *
 * @see Lens
 * @see Monad
 */
public final class ForState {

  private ForState() {} // Static access only

  /**
   * Starts a state-threaded comprehension with an initial state wrapped in a monad.
   *
   * @param monad The {@link Monad} instance for the effect context.
   * @param initialState The initial state wrapped in the monad.
   * @param <M> The witness type for the monad context.
   * @param <S> The type of the state.
   * @return A {@link Steps} builder for chaining operations.
   * @throws NullPointerException if any argument is null.
   */
  public static <M, S> Steps<M, S> withState(Monad<M> monad, Kind<M, S> initialState) {
    Objects.requireNonNull(monad, "monad must not be null");
    Objects.requireNonNull(initialState, "initialState must not be null");
    return new ForStateStepsImpl<>(monad, initialState);
  }

  /**
   * A builder interface for chaining state-threaded operations.
   *
   * @param <M> The witness type for the monad context.
   * @param <S> The type of the state.
   */
  public interface Steps<M, S> {

    /**
     * Performs a monadic operation using the current state, keeping the state unchanged.
     *
     * @param f A function that takes the current state and returns a monadic computation.
     * @param <A> The type of the computation result.
     * @return A new builder with the operation queued.
     * @throws NullPointerException if {@code f} is null.
     */
    <A> Steps<M, S> from(Function<S, Kind<M, A>> f);

    /**
     * Performs a monadic operation and uses the result to update state via a lens.
     *
     * <p>This is the primary way to update state in a workflow: perform an operation that produces
     * a value, then store that value in the state using a lens.
     *
     * @param f A function that takes the current state and returns a monadic computation.
     * @param lens The lens to use for updating the state with the result.
     * @param <A> The type of the computation result (must match the lens focus type).
     * @return A new builder with the operation and update queued.
     * @throws NullPointerException if any argument is null.
     */
    <A> Steps<M, S> fromThen(Function<S, Kind<M, A>> f, Lens<S, A> lens);

    /**
     * Updates a field in the state using a lens and a pure function.
     *
     * @param lens The lens focusing on the field to update.
     * @param modifier A function to compute the new value from the current value.
     * @param <A> The type of the field.
     * @return A new builder with the update queued.
     * @throws NullPointerException if any argument is null.
     */
    <A> Steps<M, S> modify(Lens<S, A> lens, Function<A, A> modifier);

    /**
     * Sets a field in the state using a lens.
     *
     * @param lens The lens focusing on the field to set.
     * @param value The new value for the field.
     * @param <A> The type of the field.
     * @return A new builder with the update queued.
     * @throws NullPointerException if {@code lens} is null.
     */
    <A> Steps<M, S> update(Lens<S, A> lens, A value);

    /**
     * Completes the comprehension and returns the final state.
     *
     * @return The final state wrapped in the monad context.
     */
    Kind<M, S> yield();

    /**
     * Completes the comprehension by applying a projection to the final state.
     *
     * @param f A function to transform the final state into a result.
     * @param <R> The type of the result.
     * @return The projected result wrapped in the monad context.
     * @throws NullPointerException if {@code f} is null.
     */
    <R> Kind<M, R> yield(Function<S, R> f);
  }

  /** Implementation of the state steps builder. */
  private static final class ForStateStepsImpl<M, S> implements Steps<M, S> {
    private final Monad<M> monad;
    private final Kind<M, S> state;

    ForStateStepsImpl(Monad<M> monad, Kind<M, S> state) {
      this.monad = monad;
      this.state = state;
    }

    @Override
    public <A> Steps<M, S> from(Function<S, Kind<M, A>> f) {
      Objects.requireNonNull(f, "function must not be null");
      // Perform the operation but discard the result, keeping state unchanged
      Kind<M, S> newState = monad.flatMap(s -> monad.map(ignored -> s, f.apply(s)), state);
      return new ForStateStepsImpl<>(monad, newState);
    }

    @Override
    public <A> Steps<M, S> fromThen(Function<S, Kind<M, A>> f, Lens<S, A> lens) {
      Objects.requireNonNull(f, "function must not be null");
      Objects.requireNonNull(lens, "lens must not be null");
      // Perform the operation and update state using the lens
      Kind<M, S> newState =
          monad.flatMap(s -> monad.map(result -> lens.set(result, s), f.apply(s)), state);
      return new ForStateStepsImpl<>(monad, newState);
    }

    @Override
    public <A> Steps<M, S> modify(Lens<S, A> lens, Function<A, A> modifier) {
      Objects.requireNonNull(lens, "lens must not be null");
      Objects.requireNonNull(modifier, "modifier must not be null");
      Kind<M, S> newState = monad.map(s -> lens.modify(modifier, s), state);
      return new ForStateStepsImpl<>(monad, newState);
    }

    @Override
    public <A> Steps<M, S> update(Lens<S, A> lens, A value) {
      Objects.requireNonNull(lens, "lens must not be null");
      Kind<M, S> newState = monad.map(s -> lens.set(value, s), state);
      return new ForStateStepsImpl<>(monad, newState);
    }

    @Override
    public Kind<M, S> yield() {
      return state;
    }

    @Override
    public <R> Kind<M, R> yield(Function<S, R> f) {
      Objects.requireNonNull(f, "projection function must not be null");
      return monad.map(f, state);
    }
  }
}
