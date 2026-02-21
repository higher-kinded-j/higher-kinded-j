// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;

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
 *   <li><b>Guards and filtering:</b> Short-circuit workflows via {@link MonadZero} with {@code
 *       when()} and {@code matchThen()}
 *   <li><b>Traversal support:</b> Bulk operations over collection fields within state
 *   <li><b>State zooming:</b> Temporarily narrow state scope via {@code zoom()} / {@code endZoom()}
 * </ul>
 *
 * <h3>Basic Example</h3>
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
 * <h3>Filterable Example (with MonadZero)</h3>
 *
 * <pre>{@code
 * Kind<MaybeKind.Witness, WorkflowContext> result =
 *     ForState.withState(maybeMonad, MAYBE.just(new WorkflowContext("ORD-123", false, null)))
 *         .update(validatedLens, true)
 *         .when(ctx -> ctx.validated())          // short-circuits to Nothing if false
 *         .fromThen(ctx -> processPayment(ctx), confirmationIdLens)
 *         .yield();
 * }</pre>
 *
 * @see Lens
 * @see Monad
 * @see MonadZero
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
  public static <M extends WitnessArity<TypeArity.Unary>, S> Steps<M, S> withState(
      Monad<M> monad, Kind<M, S> initialState) {
    Objects.requireNonNull(monad, "monad must not be null");
    Objects.requireNonNull(initialState, "initialState must not be null");
    return new ForStateStepsImpl<>(monad, initialState);
  }

  /**
   * Starts a state-threaded comprehension with filtering support via {@link MonadZero}.
   *
   * <p>When the monad supports a zero element (e.g., {@code Maybe}, {@code List}, {@code
   * Optional}), this overload returns {@link FilterableSteps} which adds {@code when()} for guards
   * and {@code matchThen()} for prism-based pattern matching that short-circuits on failure.
   *
   * @param monad The {@link MonadZero} instance for the effect context.
   * @param initialState The initial state wrapped in the monad.
   * @param <M> The witness type for the monad context.
   * @param <S> The type of the state.
   * @return A {@link FilterableSteps} builder for chaining operations with filtering support.
   * @throws NullPointerException if any argument is null.
   */
  public static <M extends WitnessArity<TypeArity.Unary>, S> FilterableSteps<M, S> withState(
      MonadZero<M> monad, Kind<M, S> initialState) {
    Objects.requireNonNull(monad, "monad must not be null");
    Objects.requireNonNull(initialState, "initialState must not be null");
    return new ForStateFilterableStepsImpl<>(monad, initialState);
  }

  /**
   * A builder interface for chaining state-threaded operations.
   *
   * @param <M> The witness type for the monad context.
   * @param <S> The type of the state.
   */
  public interface Steps<M extends WitnessArity<TypeArity.Unary>, S> {

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
     * Applies an effectful function to each element focused by a traversal within a state field,
     * updating the field with the results.
     *
     * <p>This combines a {@link Lens} (to access a collection field in the state) with a {@link
     * Traversal} (to iterate over elements) and an effectful function (to transform each element
     * within the monad context).
     *
     * @param collectionLens The lens focusing on the collection field in the state.
     * @param traversal The traversal over elements within the collection.
     * @param f The effectful function to apply to each element.
     * @param <C> The type of the collection field.
     * @param <A> The type of elements within the collection.
     * @return A new builder with the traversal operation queued.
     * @throws NullPointerException if any argument is null.
     */
    <C, A> Steps<M, S> traverse(
        Lens<S, C> collectionLens, Traversal<C, A> traversal, Function<A, Kind<M, A>> f);

    /**
     * Narrows the state scope to a sub-part of the state via a lens.
     *
     * <p>Operations on the returned {@link ZoomedSteps} operate on the sub-state type {@code T}
     * whilst internally composing through the zoom lens. Call {@link ZoomedSteps#endZoom()} to
     * return to the original state scope.
     *
     * @param zoomLens The lens focusing on the sub-state.
     * @param <T> The type of the sub-state.
     * @return A {@link ZoomedSteps} builder operating on the sub-state.
     * @throws NullPointerException if {@code zoomLens} is null.
     */
    <T> ZoomedSteps<M, S, T> zoom(Lens<S, T> zoomLens);

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

  /**
   * An extended builder interface that adds filtering and pattern matching capabilities.
   *
   * <p>This interface is returned when {@link ForState#withState(MonadZero, Kind)} is used with a
   * {@link MonadZero} instance. It extends {@link Steps} with:
   *
   * <ul>
   *   <li>{@link #when(Predicate)} for predicate-based guards
   *   <li>{@link #matchThen(Lens, Prism, Lens)} for prism-based pattern matching with state update
   *   <li>{@link #matchThen(Function, Lens)} for function-based extraction with state update
   * </ul>
   *
   * @param <M> The witness type for the monad context.
   * @param <S> The type of the state.
   */
  public interface FilterableSteps<M extends WitnessArity<TypeArity.Unary>, S> extends Steps<M, S> {

    /**
     * Filters the workflow based on a predicate applied to the current state.
     *
     * <p>If the predicate returns {@code false}, the computation short-circuits using the monad's
     * {@link MonadZero#zero()} element (e.g., {@code Nothing} for Maybe, empty list for List).
     *
     * @param predicate The predicate to test against the current state.
     * @return A new builder with the guard applied.
     * @throws NullPointerException if {@code predicate} is null.
     */
    FilterableSteps<M, S> when(Predicate<S> predicate);

    /**
     * Extracts a field via a source lens, matches it with a prism, and stores the result via a
     * target lens. Short-circuits via {@link MonadZero#zero()} when the prism does not match.
     *
     * <p>This is useful for conditional state transitions based on sum types:
     *
     * <pre>{@code
     * ForState.withState(maybeMonad, MAYBE.just(state))
     *     .matchThen(statusLens, confirmedPrism, confirmationIdLens)
     *     .yield();
     * }</pre>
     *
     * @param sourceLens The lens to extract the field to match against.
     * @param prism The prism to match the extracted value.
     * @param targetLens The lens to store the matched result in the state.
     * @param <X> The type of the extracted field (prism source).
     * @param <A> The type of the matched value (prism target), which must match the target lens
     *     focus type.
     * @return A new builder with the match-and-store operation queued.
     * @throws NullPointerException if any argument is null.
     */
    <X, A> FilterableSteps<M, S> matchThen(
        Lens<S, X> sourceLens, Prism<X, A> prism, Lens<S, A> targetLens);

    /**
     * Extracts a value from the state using a function, and if present, stores it via a lens.
     * Short-circuits via {@link MonadZero#zero()} when the function returns an empty {@link
     * Optional}.
     *
     * <p>This is a simpler variant of {@link #matchThen(Lens, Prism, Lens)} for cases where a
     * direct function extraction is more convenient than composing a lens and prism.
     *
     * @param extractor A function that extracts an optional value from the state.
     * @param targetLens The lens to store the extracted value in the state.
     * @param <A> The type of the extracted value.
     * @return A new builder with the extraction operation queued.
     * @throws NullPointerException if any argument is null.
     */
    <A> FilterableSteps<M, S> matchThen(Function<S, Optional<A>> extractor, Lens<S, A> targetLens);

    // Override return types to preserve FilterableSteps in the chain

    @Override
    <A> FilterableSteps<M, S> from(Function<S, Kind<M, A>> f);

    @Override
    <A> FilterableSteps<M, S> fromThen(Function<S, Kind<M, A>> f, Lens<S, A> lens);

    @Override
    <A> FilterableSteps<M, S> modify(Lens<S, A> lens, Function<A, A> modifier);

    @Override
    <A> FilterableSteps<M, S> update(Lens<S, A> lens, A value);

    @Override
    <C, A> FilterableSteps<M, S> traverse(
        Lens<S, C> collectionLens, Traversal<C, A> traversal, Function<A, Kind<M, A>> f);

    @Override
    <T> FilterableZoomedSteps<M, S, T> zoom(Lens<S, T> zoomLens);
  }

  /**
   * A builder interface for operations within a zoomed (narrowed) state scope.
   *
   * <p>Operations on this builder target the sub-state type {@code T} rather than the full state
   * {@code S}. Internally, all operations compose through the zoom lens to update the full state.
   *
   * @param <M> The witness type for the monad context.
   * @param <S> The type of the outer (full) state.
   * @param <T> The type of the inner (zoomed) state.
   */
  public interface ZoomedSteps<M extends WitnessArity<TypeArity.Unary>, S, T> {

    /**
     * Sets a field within the zoomed sub-state using a lens.
     *
     * @param lens The lens focusing on a field within the sub-state {@code T}.
     * @param value The new value for the field.
     * @param <A> The type of the field.
     * @return A new zoomed builder with the update queued.
     * @throws NullPointerException if {@code lens} is null.
     */
    <A> ZoomedSteps<M, S, T> update(Lens<T, A> lens, A value);

    /**
     * Transforms a field within the zoomed sub-state using a lens and a pure function.
     *
     * @param lens The lens focusing on a field within the sub-state {@code T}.
     * @param modifier A function to compute the new value from the current value.
     * @param <A> The type of the field.
     * @return A new zoomed builder with the update queued.
     * @throws NullPointerException if any argument is null.
     */
    <A> ZoomedSteps<M, S, T> modify(Lens<T, A> lens, Function<A, A> modifier);

    /**
     * Performs a monadic operation on the zoomed sub-state and stores the result via a lens.
     *
     * @param f A function that takes the sub-state and returns a monadic computation.
     * @param lens The lens to use for updating the sub-state with the result.
     * @param <A> The type of the computation result.
     * @return A new zoomed builder with the operation queued.
     * @throws NullPointerException if any argument is null.
     */
    <A> ZoomedSteps<M, S, T> fromThen(Function<T, Kind<M, A>> f, Lens<T, A> lens);

    /**
     * Returns to the outer state scope, ending the zoom.
     *
     * @return A {@link Steps} builder operating on the full state {@code S}.
     */
    Steps<M, S> endZoom();
  }

  /**
   * An extended zoomed builder interface that preserves {@link FilterableSteps} capabilities.
   *
   * <p>This interface is returned by {@link FilterableSteps#zoom(Lens)} and ensures that {@link
   * #endZoom()} returns a {@link FilterableSteps} instead of a base {@link Steps}, so that guards
   * ({@code when()}) and pattern matching ({@code matchThen()}) remain available after a zoom
   * block.
   *
   * @param <M> The witness type for the monad context.
   * @param <S> The type of the outer (full) state.
   * @param <T> The type of the inner (zoomed) state.
   */
  public interface FilterableZoomedSteps<M extends WitnessArity<TypeArity.Unary>, S, T>
      extends ZoomedSteps<M, S, T> {

    @Override
    <A> FilterableZoomedSteps<M, S, T> update(Lens<T, A> lens, A value);

    @Override
    <A> FilterableZoomedSteps<M, S, T> modify(Lens<T, A> lens, Function<A, A> modifier);

    @Override
    <A> FilterableZoomedSteps<M, S, T> fromThen(Function<T, Kind<M, A>> f, Lens<T, A> lens);

    /**
     * Returns to the outer state scope, ending the zoom and preserving filtering capabilities.
     *
     * @return A {@link FilterableSteps} builder operating on the full state {@code S}.
     */
    @Override
    FilterableSteps<M, S> endZoom();
  }

  /** Implementation of the state steps builder. */
  private static final class ForStateStepsImpl<M extends WitnessArity<TypeArity.Unary>, S>
      implements Steps<M, S> {
    private final Monad<M> monad;
    private final Kind<M, S> state;

    ForStateStepsImpl(Monad<M> monad, Kind<M, S> state) {
      this.monad = monad;
      this.state = state;
    }

    @Override
    public <A> Steps<M, S> from(Function<S, Kind<M, A>> f) {
      Objects.requireNonNull(f, "function must not be null");
      Kind<M, S> newState = monad.flatMap(s -> monad.map(ignored -> s, f.apply(s)), state);
      return new ForStateStepsImpl<>(monad, newState);
    }

    @Override
    public <A> Steps<M, S> fromThen(Function<S, Kind<M, A>> f, Lens<S, A> lens) {
      Objects.requireNonNull(f, "function must not be null");
      Objects.requireNonNull(lens, "lens must not be null");
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
    public <C, A> Steps<M, S> traverse(
        Lens<S, C> collectionLens, Traversal<C, A> traversal, Function<A, Kind<M, A>> f) {
      Objects.requireNonNull(collectionLens, "collectionLens must not be null");
      Objects.requireNonNull(traversal, "traversal must not be null");
      Objects.requireNonNull(f, "function must not be null");
      Kind<M, S> newState =
          monad.flatMap(
              s -> {
                C collection = collectionLens.get(s);
                Kind<M, C> updatedCollection = traversal.modifyF(f, collection, monad);
                return monad.map(
                    newCollection -> collectionLens.set(newCollection, s), updatedCollection);
              },
              state);
      return new ForStateStepsImpl<>(monad, newState);
    }

    @Override
    public <T> ZoomedSteps<M, S, T> zoom(Lens<S, T> zoomLens) {
      Objects.requireNonNull(zoomLens, "zoomLens must not be null");
      return new ForStateZoomedStepsImpl<>(monad, state, zoomLens);
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

  /** Implementation of the filterable state steps builder. */
  private static final class ForStateFilterableStepsImpl<M extends WitnessArity<TypeArity.Unary>, S>
      implements FilterableSteps<M, S> {
    private final MonadZero<M> monad;
    private final Kind<M, S> state;

    ForStateFilterableStepsImpl(MonadZero<M> monad, Kind<M, S> state) {
      this.monad = monad;
      this.state = state;
    }

    @Override
    public FilterableSteps<M, S> when(Predicate<S> predicate) {
      Objects.requireNonNull(predicate, "predicate must not be null");
      Kind<M, S> newState =
          monad.flatMap(s -> predicate.test(s) ? monad.of(s) : monad.zero(), state);
      return new ForStateFilterableStepsImpl<>(monad, newState);
    }

    @Override
    public <X, A> FilterableSteps<M, S> matchThen(
        Lens<S, X> sourceLens, Prism<X, A> prism, Lens<S, A> targetLens) {
      Objects.requireNonNull(sourceLens, "sourceLens must not be null");
      Objects.requireNonNull(prism, "prism must not be null");
      Objects.requireNonNull(targetLens, "targetLens must not be null");
      Kind<M, S> newState =
          monad.flatMap(
              s -> {
                X extracted = sourceLens.get(s);
                return prism
                    .getOptional(extracted)
                    .map(a -> monad.of(targetLens.set(a, s)))
                    .orElseGet(monad::zero);
              },
              state);
      return new ForStateFilterableStepsImpl<>(monad, newState);
    }

    @Override
    public <A> FilterableSteps<M, S> matchThen(
        Function<S, Optional<A>> extractor, Lens<S, A> targetLens) {
      Objects.requireNonNull(extractor, "extractor must not be null");
      Objects.requireNonNull(targetLens, "targetLens must not be null");
      Kind<M, S> newState =
          monad.flatMap(
              s ->
                  extractor
                      .apply(s)
                      .map(a -> monad.of(targetLens.set(a, s)))
                      .orElseGet(monad::zero),
              state);
      return new ForStateFilterableStepsImpl<>(monad, newState);
    }

    @Override
    public <A> FilterableSteps<M, S> from(Function<S, Kind<M, A>> f) {
      Objects.requireNonNull(f, "function must not be null");
      Kind<M, S> newState = monad.flatMap(s -> monad.map(ignored -> s, f.apply(s)), state);
      return new ForStateFilterableStepsImpl<>(monad, newState);
    }

    @Override
    public <A> FilterableSteps<M, S> fromThen(Function<S, Kind<M, A>> f, Lens<S, A> lens) {
      Objects.requireNonNull(f, "function must not be null");
      Objects.requireNonNull(lens, "lens must not be null");
      Kind<M, S> newState =
          monad.flatMap(s -> monad.map(result -> lens.set(result, s), f.apply(s)), state);
      return new ForStateFilterableStepsImpl<>(monad, newState);
    }

    @Override
    public <A> FilterableSteps<M, S> modify(Lens<S, A> lens, Function<A, A> modifier) {
      Objects.requireNonNull(lens, "lens must not be null");
      Objects.requireNonNull(modifier, "modifier must not be null");
      Kind<M, S> newState = monad.map(s -> lens.modify(modifier, s), state);
      return new ForStateFilterableStepsImpl<>(monad, newState);
    }

    @Override
    public <A> FilterableSteps<M, S> update(Lens<S, A> lens, A value) {
      Objects.requireNonNull(lens, "lens must not be null");
      Kind<M, S> newState = monad.map(s -> lens.set(value, s), state);
      return new ForStateFilterableStepsImpl<>(monad, newState);
    }

    @Override
    public <C, A> FilterableSteps<M, S> traverse(
        Lens<S, C> collectionLens, Traversal<C, A> traversal, Function<A, Kind<M, A>> f) {
      Objects.requireNonNull(collectionLens, "collectionLens must not be null");
      Objects.requireNonNull(traversal, "traversal must not be null");
      Objects.requireNonNull(f, "function must not be null");
      Kind<M, S> newState =
          monad.flatMap(
              s -> {
                C collection = collectionLens.get(s);
                Kind<M, C> updatedCollection = traversal.modifyF(f, collection, monad);
                return monad.map(
                    newCollection -> collectionLens.set(newCollection, s), updatedCollection);
              },
              state);
      return new ForStateFilterableStepsImpl<>(monad, newState);
    }

    @Override
    public <T> FilterableZoomedSteps<M, S, T> zoom(Lens<S, T> zoomLens) {
      Objects.requireNonNull(zoomLens, "zoomLens must not be null");
      return new ForStateFilterableZoomedStepsImpl<>(monad, state, zoomLens);
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

  /** Implementation of the zoomed state steps builder. */
  private static final class ForStateZoomedStepsImpl<M extends WitnessArity<TypeArity.Unary>, S, T>
      implements ZoomedSteps<M, S, T> {
    private final Monad<M> monad;
    private final Kind<M, S> state;
    private final Lens<S, T> zoomLens;

    ForStateZoomedStepsImpl(Monad<M> monad, Kind<M, S> state, Lens<S, T> zoomLens) {
      this.monad = monad;
      this.state = state;
      this.zoomLens = zoomLens;
    }

    @Override
    public <A> ZoomedSteps<M, S, T> update(Lens<T, A> lens, A value) {
      Objects.requireNonNull(lens, "lens must not be null");
      Lens<S, A> composed = zoomLens.andThen(lens);
      Kind<M, S> newState = monad.map(s -> composed.set(value, s), state);
      return new ForStateZoomedStepsImpl<>(monad, newState, zoomLens);
    }

    @Override
    public <A> ZoomedSteps<M, S, T> modify(Lens<T, A> lens, Function<A, A> modifier) {
      Objects.requireNonNull(lens, "lens must not be null");
      Objects.requireNonNull(modifier, "modifier must not be null");
      Lens<S, A> composed = zoomLens.andThen(lens);
      Kind<M, S> newState = monad.map(s -> composed.modify(modifier, s), state);
      return new ForStateZoomedStepsImpl<>(monad, newState, zoomLens);
    }

    @Override
    public <A> ZoomedSteps<M, S, T> fromThen(Function<T, Kind<M, A>> f, Lens<T, A> lens) {
      Objects.requireNonNull(f, "function must not be null");
      Objects.requireNonNull(lens, "lens must not be null");
      Lens<S, A> composedTarget = zoomLens.andThen(lens);
      Kind<M, S> newState =
          monad.flatMap(
              s -> {
                T subState = zoomLens.get(s);
                return monad.map(result -> composedTarget.set(result, s), f.apply(subState));
              },
              state);
      return new ForStateZoomedStepsImpl<>(monad, newState, zoomLens);
    }

    @Override
    public Steps<M, S> endZoom() {
      return new ForStateStepsImpl<>(monad, state);
    }
  }

  /** Implementation of the filterable zoomed state steps builder. */
  private static final class ForStateFilterableZoomedStepsImpl<
          M extends WitnessArity<TypeArity.Unary>, S, T>
      implements FilterableZoomedSteps<M, S, T> {
    private final MonadZero<M> monad;
    private final Kind<M, S> state;
    private final Lens<S, T> zoomLens;

    ForStateFilterableZoomedStepsImpl(MonadZero<M> monad, Kind<M, S> state, Lens<S, T> zoomLens) {
      this.monad = monad;
      this.state = state;
      this.zoomLens = zoomLens;
    }

    @Override
    public <A> FilterableZoomedSteps<M, S, T> update(Lens<T, A> lens, A value) {
      Objects.requireNonNull(lens, "lens must not be null");
      Lens<S, A> composed = zoomLens.andThen(lens);
      Kind<M, S> newState = monad.map(s -> composed.set(value, s), state);
      return new ForStateFilterableZoomedStepsImpl<>(monad, newState, zoomLens);
    }

    @Override
    public <A> FilterableZoomedSteps<M, S, T> modify(Lens<T, A> lens, Function<A, A> modifier) {
      Objects.requireNonNull(lens, "lens must not be null");
      Objects.requireNonNull(modifier, "modifier must not be null");
      Lens<S, A> composed = zoomLens.andThen(lens);
      Kind<M, S> newState = monad.map(s -> composed.modify(modifier, s), state);
      return new ForStateFilterableZoomedStepsImpl<>(monad, newState, zoomLens);
    }

    @Override
    public <A> FilterableZoomedSteps<M, S, T> fromThen(Function<T, Kind<M, A>> f, Lens<T, A> lens) {
      Objects.requireNonNull(f, "function must not be null");
      Objects.requireNonNull(lens, "lens must not be null");
      Lens<S, A> composedTarget = zoomLens.andThen(lens);
      Kind<M, S> newState =
          monad.flatMap(
              s -> {
                T subState = zoomLens.get(s);
                return monad.map(result -> composedTarget.set(result, s), f.apply(subState));
              },
              state);
      return new ForStateFilterableZoomedStepsImpl<>(monad, newState, zoomLens);
    }

    @Override
    public FilterableSteps<M, S> endZoom() {
      return new ForStateFilterableStepsImpl<>(monad, state);
    }
  }
}
