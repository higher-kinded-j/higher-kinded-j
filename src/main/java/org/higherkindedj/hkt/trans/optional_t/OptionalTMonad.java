package org.higherkindedj.hkt.trans.optional_t;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * MonadError instance for the OptionalT transformer. Requires a Monad instance for the outer
 * context F. The error type E is Void, representing the Optional.empty() state.
 *
 * @param <F> The witness type of the outer Monad.
 */
public class OptionalTMonad<F> implements MonadError<OptionalTKind<F, ?>, Void> {

  private final @NonNull Monad<F> outerMonad;

  /**
   * Creates a MonadError instance for OptionalT.
   *
   * @param outerMonad The Monad instance for the outer context F. Must not be null.
   */
  public OptionalTMonad(@NonNull Monad<F> outerMonad) {
    this.outerMonad =
        Objects.requireNonNull(
            outerMonad, "Outer Monad instance cannot be null for OptionalTMonad");
  }

  // --- Applicative Methods ---

  /**
   * Lifts a pure value 'a' into the OptionalT context as {@code F<Optional.ofNullable(a)>}. Note:
   * This uses Optional.ofNullable, so null values become F<Optional.empty()>. To strictly enforce
   * non-null and lift as F<Optional.of(a)>, use {@code OptionalT.some(outerMonad, a)}.
   */
  @Override
  public <A> @NonNull Kind<OptionalTKind<F, ?>, A> of(@Nullable A value) {
    // Lift 'a' into Optional<A>, then lift that into F<Optional<A>>
    Kind<F, Optional<A>> lifted = outerMonad.of(Optional.ofNullable(value));
    return OptionalT.fromKind(lifted);
  }

  /**
   * Applies a function inside OptionalT to a value inside OptionalT. {@code F<Optional<A -> B>> ->
   * F<Optional<A>> -> F<Optional<B>>}
   */
  @Override
  public <A, B> @NonNull Kind<OptionalTKind<F, ?>, B> ap(
      @NonNull Kind<OptionalTKind<F, ?>, Function<A, B>> ff,
      @NonNull Kind<OptionalTKind<F, ?>, A> fa) {
    OptionalT<F, Function<A, B>> funcT = (OptionalT<F, Function<A, B>>) ff;
    OptionalT<F, A> valT = (OptionalT<F, A>) fa;

    // Use outer monad's flatMap/map (or ap if available) to combine
    // F<Optional<Function>> flatMap ( optF -> F<Optional<Value>> map (optV -> optF.flatMap(f ->
    // optV.map(f))) )
    Kind<F, Optional<B>> resultValue =
        outerMonad.flatMap(
            optF ->
                outerMonad.map(
                    optA ->
                        optF.flatMap(
                            optA::map), // Inner: Optional<A -> B> flatMap (Optional<A> map f)
                    valT.value()),
            funcT.value());

    return OptionalT.fromKind(resultValue);
  }

  // --- Functor Method ---

  /**
   * Maps a function over the value within the OptionalT context. {@code (A -> B) -> F<Optional<A>>
   * -> F<Optional<B>>}
   */
  @Override
  public <A, B> @NonNull Kind<OptionalTKind<F, ?>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<OptionalTKind<F, ?>, A> fa) {
    OptionalT<F, A> optionalT = (OptionalT<F, A>) fa;
    // Map the inner Optional using the outer monad's map
    // F<Optional<A>> map (optA -> optA.map(f))
    Kind<F, Optional<B>> newValue = outerMonad.map(opt -> opt.map(f), optionalT.value());
    return OptionalT.fromKind(newValue);
  }

  // --- Monad Method ---

  /**
   * Sequences operations within the OptionalT context. {@code (A -> F<Optional<B>>) ->
   * F<Optional<A>> -> F<Optional<B>>}
   */
  @Override
  public <A, B> @NonNull Kind<OptionalTKind<F, ?>, B> flatMap(
      @NonNull Function<A, Kind<OptionalTKind<F, ?>, B>> f,
      @NonNull Kind<OptionalTKind<F, ?>, A> ma) {
    OptionalT<F, A> optionalT = (OptionalT<F, A>) ma;

    Kind<F, Optional<B>> newValue =
        outerMonad.flatMap(
            optA ->
                optA.map(
                        a -> {
                          // Apply f which returns OptionalT<F, B>
                          OptionalT<F, B> resultT = (OptionalT<F, B>) f.apply(a);
                          // Extract the inner F<Optional<B>>
                          return resultT.value();
                        })
                    // If optA was empty, map returns empty Optional. We need F<Optional.empty()> in
                    // this case.
                    // If optA was present but f(a) resulted in F<Optional.empty>, we need that.
                    .orElse(
                        outerMonad.of(
                            Optional.empty())), // If optA is empty, result is F<Optional.empty>
            optionalT.value() // Apply flatMap to the initial F<Optional<A>>
            );
    return OptionalT.fromKind(newValue);
  }

  // --- MonadError Methods (Error Type E = Void) ---

  /**
   * Lifts the error state (Optional.empty) into the OptionalT context as {@code
   * F<Optional.empty()>}. The input 'error' (Void) is ignored.
   */
  @Override
  public <A> @NonNull Kind<OptionalTKind<F, ?>, A> raiseError(@Nullable Void error) {
    // The error state is F<Optional.empty()>
    return OptionalT.none(outerMonad);
  }

  /**
   * Handles the error state (empty Optional) within the OptionalT context. If the wrapped {@code
   * Kind<F, Optional<A>>} eventually results in a present Optional, it's returned unchanged. If it
   * results in an empty Optional, the 'handler' function {@code Void -> OptionalT<F, A>} is
   * applied.
   */
  @Override
  public <A> @NonNull Kind<OptionalTKind<F, ?>, A> handleErrorWith(
      @NonNull Kind<OptionalTKind<F, ?>, A> ma,
      @NonNull Function<Void, Kind<OptionalTKind<F, ?>, A>> handler) {
    OptionalT<F, A> optionalT = (OptionalT<F, A>) ma;

    // Use outerMonad's flatMap to check the inner Optional once F completes.
    Kind<F, Optional<A>> handledValue =
        outerMonad.flatMap(
            optA -> {
              if (optA.isPresent()) {
                // Value is present, lift it back: F<Optional.of(a)>
                return outerMonad.of(optA);
              } else {
                // Value is empty, apply the handler.
                // handler(null) returns Kind<OptionalTKind<F, ?>, A>
                OptionalT<F, A> resultT = (OptionalT<F, A>) handler.apply(null);
                // Return the F<Optional<A>> from the handler's result.
                return resultT.value();
              }
            },
            optionalT.value() // Apply flatMap to the original F<Optional<A>>
            );

    return OptionalT.fromKind(handledValue);
  }
}
