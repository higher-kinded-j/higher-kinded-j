package org.higherkindedj.hkt.state;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
// Assuming B can be @Nullable if the function f returns a nullable type
// However, State.map typically expects Function<A, B> where B is non-null if A is non-null by
// convention
// or if the State monad itself doesn't permit nulls in its value slot directly.
// Let's check State.map signature. If State.map takes Function<A,B> and A is @NonNull, B can be
// anything.
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Functor} type class for {@link StateKind}{@code <S, A>}, allowing the
 * transformation of the computed value {@code A} within a stateful computation, while leaving the
 * state transformation logic intact.
 *
 * <p>An instance of {@code StateFunctor} is specific to a particular state type {@code S}, which is
 * fixed when the functor is instantiated. The HKT marker (witness type) used is {@link
 * StateKind.Witness}.
 *
 * @param <S> The type of the state that this {@code StateFunctor} instance operates upon. This
 *     state type is fixed for the lifetime of the functor instance.
 * @see Functor
 * @see State
 * @see StateKind
 * @see StateKind.Witness
 * @see StateKindHelper
 */
public class StateFunctor<S> implements Functor<StateKind.Witness> {

  /**
   * Applies a function {@code f} to the computed value of a {@link State}{@code <S, A>}
   * computation, transforming it into a {@link State}{@code <S, B>} computation.
   *
   * <p>The {@code map} operation for {@code State} works by running the original state computation,
   * applying the function {@code f} to its resulting value, and then pairing this new value with
   * the final state from the original computation. The state transformation itself is preserved.
   *
   * <p>This method uses {@link StateKindHelper#unwrap(Kind)} to retrieve the underlying {@link
   * State}{@code <S, A>} and {@link State#map(Function)} to perform the transformation, finally
   * re-wrapping the result using {@link StateKindHelper#wrap(State)}.
   *
   * @param <A> The type of the value in the input {@code State} computation (and its {@link Kind}
   *     representation).
   * @param <B> The type of the value in the resulting {@code State} computation (and its {@link
   *     Kind} representation) after applying the function {@code f}.
   * @param f The non-null function to apply to the computed value of the {@code State} computation.
   *     It transforms a value of type {@code A} to a value of type {@code B}. If {@code B} can be
   *     {@code null}, then the {@code Function<A, @Nullable B>} signature should reflect that.
   *     Given {@code State.map(Function<A,B>)}, if {@code B} can be null, use {@code @Nullable B}.
   * @param fa The {@code Kind<StateKind.Witness, A>} which represents the input {@link State}{@code
   *     <S, A>} computation. Must not be {@code null}.
   * @return A new non-null {@code Kind<StateKind.Witness, B>} representing the transformed {@link
   *     State}{@code <S, B>} computation.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped.
   * @throws NullPointerException if {@code f} is {@code null}.
   */
  @Override
  public <A, B> @NonNull Kind<StateKind.Witness, B> map(
      // Assuming State.map's function can produce a @Nullable B if A can be mapped to null.
      // If State's value cannot be null, then B should be @NonNull or just B.
      // For now, let's assume f can produce @Nullable B to be general,
      // and State.map handles this appropriately (e.g. if State's value can be null).
      // If State<S,A> implies A is always non-null, then this should be Function<A, @NonNull B> or
      // Function<A,B>
      @NonNull Function<A, @Nullable B>
          f, // Changed B to @Nullable B for generality, assuming State.map handles it
      @NonNull Kind<StateKind.Witness, A> fa) {
    requireNonNull(f, "Mapping function cannot be null"); // Added explicit null check for f

    // 1. Unwrap the Kind to get the concrete State<S, A>.
    //    The type S is bound to this StateFunctor instance.
    State<S, A> stateA = StateKindHelper.unwrap(fa);

    // 2. Apply the function using State's own map method.
    State<S, B> stateB = stateA.map(f);

    // 3. Wrap the resulting State<S, B> back into a Kind.
    return StateKindHelper.wrap(stateB);
  }
}
