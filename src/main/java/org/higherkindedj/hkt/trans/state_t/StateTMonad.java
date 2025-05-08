package org.higherkindedj.hkt.trans.state_t;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError; // Import MonadError
import org.higherkindedj.hkt.state.StateTuple;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Monad instance for the StateT monad transformer.
 *
 * <p>Requires a Monad instance for the underlying monad F.
 *
 * @param <S> The state type.
 * @param <F> The higher-kinded type witness for the underlying monad.
 */
public final class StateTMonad<S, F> implements Monad<StateTKind.Witness<S, F>> {

  private final Monad<F> monadF;
  // Store monadF also as MonadError if it implements it, for raiseError access.
  private final @Nullable MonadError<F, ?> monadErrorF; // Wildcard for error type

  // Private constructor, use factory method
  private StateTMonad(Monad<F> monadF) {
    this.monadF = Objects.requireNonNull(monadF, "Underlying Monad<F> cannot be null");
    // Check if it's also a MonadError
    if (monadF instanceof MonadError) {
      // Store it with a wildcard for the error type, as we don't know it here.
      // We only know the error type is Void when F is OptionalMonad.
      this.monadErrorF = (MonadError<F, ?>) monadF;
    } else {
      this.monadErrorF = null;
    }
  }

  /**
   * Factory method to create a Monad instance for StateT.
   *
   * @param monadF The Monad instance for the underlying monad F.
   * @param <S> The state type.
   * @param <F> The higher-kinded type witness for F.
   * @return A Monad<StateTKind.Witness<S, F>> instance.
   */
  public static <S, F> StateTMonad<S, F> instance(Monad<F> monadF) {
    return new StateTMonad<>(monadF);
  }

  /**
   * Implements the 'of' method required by Applicative (which Monad extends). Lifts a pure value
   * 'a' into the StateT monad. The state remains unchanged.
   *
   * @param a The pure value to lift.
   * @param <A> The value type.
   * @return A StateT instance representing the pure value.
   */
  @Override
  public <A> Kind<StateTKind.Witness<S, F>, A> of(@Nullable A a) {
    // The run function takes state 's' and returns F<StateTuple(s, a)>
    Function<S, Kind<F, StateTuple<S, A>>> runFn = s -> monadF.of(StateTuple.of(s, a));
    return StateT.<S, F, A>create(runFn, monadF);
  }

  @Override
  public <A, B> Kind<StateTKind.Witness<S, F>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<StateTKind.Witness<S, F>, A> fa) {
    StateT<S, F, A> stateT = StateTKind.narrow(fa);
    // Define the new run function:
    // s -> F.map(stateTuple -> StateTuple(stateTuple.state, f(stateTuple.value)),
    // stateT.runStateT(s))
    Function<S, Kind<F, StateTuple<S, B>>> newRunFn =
        s ->
            monadF.map(
                // Input type for map's function is StateTuple<S, A>
                // Output type is StateTuple<S, B>
                stateTuple -> StateTuple.of(stateTuple.state(), f.apply(stateTuple.value())),
                stateT.runStateT(s) // Input Kind is Kind<F, StateTuple<S, A>>
                );
    return StateT.<S, F, B>create(newRunFn, monadF);
  }

  /**
   * Applies a function wrapped in StateT to a value wrapped in StateT. It sequences the state
   * transformations. If the function extracted from the first StateT is null, the result uses
   * monadF's empty value (via raiseError).
   */
  @Override
  public <A, B> Kind<StateTKind.Witness<S, F>, B> ap(
      @NonNull Kind<StateTKind.Witness<S, F>, Function<A, B>> ff,
      @NonNull Kind<StateTKind.Witness<S, F>, A> fa) {
    StateT<S, F, Function<A, B>> stateTf = StateTKind.narrow(ff);
    StateT<S, F, A> stateTa = StateTKind.narrow(fa);

    Function<S, Kind<F, StateTuple<S, B>>> newRunFn =
        s0 ->
            // Run the function StateT first
            monadF.<StateTuple<S, Function<A, B>>, StateTuple<S, B>>flatMap(
                // Input: tupleF is StateTuple<S, Function<A, B>>
                // Output: Kind<F, StateTuple<S, B>>
                tupleF -> {
                  Function<A, B> function = tupleF.value();
                  S s1 = tupleF.state();

                  // *** FIX V6: Check function earlier and use raiseError WITH explicit type arg
                  // ***
                  if (function == null) {
                    // If function is null, the result of this flatMap step should be empty.
                    if (this.monadErrorF == null) {
                      throw new IllegalStateException(
                          "MonadError<F> instance not available, cannot produce empty value for"
                              + " null function in ap.");
                    }
                    // Explicitly provide the type <StateTuple<S, B>> to raiseError
                    // Pass null for the error type (assuming Void for OptionalMonad)
                    // We need to cast the monadErrorF back to the specific error type (Void)
                    // to pass null correctly. This is still a bit unsafe if F isn't OptionalMonad.
                    try {
                      @SuppressWarnings("unchecked") // Cast MonadError<F, ?> to MonadError<F, Void>
                      MonadError<F, Void> specificMonadError =
                          (MonadError<F, Void>) this.monadErrorF;
                      return specificMonadError.<StateTuple<S, B>>raiseError(null);
                    } catch (ClassCastException cce) {
                      throw new IllegalStateException(
                          "Underlying MonadError<F> does not have Void error type as expected.",
                          cce);
                    }
                  }

                  // Function is not null, proceed to run stateTa
                  Kind<F, StateTuple<S, A>> resultA = stateTa.runStateT(s1);

                  // Map over the result of stateTa
                  return monadF.<StateTuple<S, A>, StateTuple<S, B>>map(
                      tupleA -> {
                        // We know 'function' is not null here
                        @Nullable B appliedValue = function.apply(tupleA.value());
                        S s2 = tupleA.state();
                        // Rely on underlying map (OptionalFunctor.map) to handle if appliedValue is
                        // null
                        return StateTuple.of(s2, appliedValue);
                      },
                      resultA);
                },
                // Initial run of the function StateT
                stateTf.runStateT(s0) // Kind<F, StateTuple<S, Function<A, B>>>
                );

    return StateT.<S, F, B>create(newRunFn, monadF);
  }

  @Override
  public <A, B> @NonNull Kind<StateTKind.Witness<S, F>, B> flatMap(
      @NonNull Function<A, Kind<StateTKind.Witness<S, F>, B>> f,
      @NonNull Kind<StateTKind.Witness<S, F>, A> fa) {
    StateT<S, F, A> stateTa = StateTKind.narrow(fa);

    Function<S, Kind<F, StateTuple<S, B>>> newRunFn =
        s0 ->
            monadF.<StateTuple<S, A>, StateTuple<S, B>>flatMap(
                // Argument 1: Function<A1, Kind<F, B1>>
                // Input: tupleA is StateTuple<S, A>
                // Output: Kind<F, StateTuple<S, B>> (which is Kind<F, B1>)
                tupleA -> {
                  // Apply the function f to the value from the first StateT
                  Kind<StateTKind.Witness<S, F>, B> kindB = f.apply(tupleA.value());
                  // Narrow it back to a concrete StateT
                  StateT<S, F, B> stateTb = StateTKind.narrow(kindB);
                  // Run the resulting StateT with the state from the first StateT
                  return stateTb.runStateT(tupleA.state());
                },
                // Argument 2: Kind<F, A1>
                // Initial run of the first StateT
                stateTa.runStateT(s0) // Kind<F, StateTuple<S, A>>
                );

    return StateT.<S, F, B>create(newRunFn, monadF);
  }

  // Remove the private raiseError helper method as it's now called directly
}
