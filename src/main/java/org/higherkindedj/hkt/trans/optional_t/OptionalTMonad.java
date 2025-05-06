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
 * Implements the {@link MonadError} interface for the {@link OptionalTKind} monad transformer. The
 * error type {@code E} is fixed to {@link Void}, as {@code OptionalT} inherently represents failure
 * as an absence of a value (similar to {@code Optional.empty()}).
 *
 * <p>This class requires a {@link Monad} instance for the outer monad {@code F} to operate. It uses
 * {@link OptionalTKindHelper} to convert between the {@code Kind} representation ({@code
 * OptionalTKind<F, ?>}) and the concrete {@link OptionalT} type.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code IO<?>}).
 */
public class OptionalTMonad<F> implements MonadError<OptionalTKind<F, ?>, Void> {

  private final @NonNull Monad<F> outerMonad;

  /**
   * Constructs an {@code OptionalTMonad} instance.
   *
   * @param outerMonad The {@link Monad} instance for the outer monad {@code F}. Must not be null.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public OptionalTMonad(@NonNull Monad<F> outerMonad) {
    this.outerMonad =
        Objects.requireNonNull(
            outerMonad, "Outer Monad instance cannot be null for OptionalTMonad");
  }

  /**
   * Lifts a value {@code a} into the {@code OptionalTKind<F, ?>} context. Uses {@link
   * Optional#ofNullable(Object)} to handle potential nulls, resulting in {@code
   * F<Optional.of(value)>} or {@code F<Optional.empty()>}.
   *
   * @param <A> The type of the value.
   * @param value The value to lift. Can be null.
   * @return A {@code Kind<OptionalTKind<F, ?>, A>} representing the lifted value.
   */
  @Override
  public <A> @NonNull Kind<OptionalTKind<F, ?>, A> of(@Nullable A value) {
    // Lift using Optional.ofNullable then outerMonad.of
    Kind<F, Optional<A>> lifted = outerMonad.of(Optional.ofNullable(value));
    // Wrap the concrete OptionalT using the helper
    return OptionalTKindHelper.wrap(OptionalT.fromKind(lifted));
  }

  /**
   * Maps a function {@code f} over the value within an {@code OptionalTKind<F, A>}. If the wrapped
   * {@code Kind<F, Optional<A>>} contains {@code Optional.of(a)}, the function is applied. If it
   * contains {@code Optional.empty()}, the result is {@code F<Optional.empty()>}. The
   * transformation is applied within the context of the outer monad {@code F}.
   *
   * @param <A> The original type of the value.
   * @param <B> The new type of the value after applying the function.
   * @param f The function to apply. Must not be null. Must not return null (as Optional.map
   *     requires).
   * @param fa The {@code Kind<OptionalTKind<F, ?>, A>} to map over. Must not be null.
   * @return A new {@code Kind<OptionalTKind<F, ?>, B>} with the function applied.
   */
  @Override
  public <A, B> @NonNull Kind<OptionalTKind<F, ?>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<OptionalTKind<F, ?>, A> fa) {
    Objects.requireNonNull(f, "Function f cannot be null for map");
    Objects.requireNonNull(fa, "Kind fa cannot be null for map");
    // Unwrap the Kind to get the concrete OptionalT
    OptionalT<F, A> optionalT = OptionalTKindHelper.unwrap(fa);
    // F<Optional<A>> map (optA -> optA.map(f))
    // Optional.map handles the empty case.
    Kind<F, Optional<B>> newValue = outerMonad.map(opt -> opt.map(f), optionalT.value());
    // Wrap the result OptionalT using the helper
    return OptionalTKindHelper.wrap(OptionalT.fromKind(newValue));
  }

  /**
   * Applies a function wrapped in {@code Kind<OptionalTKind<F, ?>, Function<A, B>>} to a value
   * wrapped in {@code Kind<OptionalTKind<F, ?>, A>}.
   *
   * <p>The behavior is as follows:
   *
   * <ul>
   *   <li>If both the function and value are present (i.e., {@code F<Optional.of(Function)>} and
   *       {@code F<Optional.of(Value)>}), the function is applied, resulting in {@code
   *       F<Optional.of(Result)>}.
   *   <li>If either the function or value is {@code empty} (i.e., {@code F<Optional.empty()>}), the
   *       result is {@code F<Optional.empty()>}.
   *   <li>This logic is handled by {@code flatMap} and {@code map} on the inner {@link Optional}
   *       and the outer monad {@code F}.
   * </ul>
   *
   * @param <A> The type of the input value.
   * @param <B> The type of the result value.
   * @param ff The wrapped function. Must not be null.
   * @param fa The wrapped value. Must not be null.
   * @return A new {@code Kind<OptionalTKind<F, ?>, B>} representing the application.
   */
  @Override
  public <A, B> @NonNull Kind<OptionalTKind<F, ?>, B> ap(
      @NonNull Kind<OptionalTKind<F, ?>, Function<A, B>> ff,
      @NonNull Kind<OptionalTKind<F, ?>, A> fa) {
    Objects.requireNonNull(ff, "Kind ff cannot be null for ap");
    Objects.requireNonNull(fa, "Kind fa cannot be null for ap");
    // Unwrap the Kinds
    OptionalT<F, Function<A, B>> funcT = OptionalTKindHelper.unwrap(ff);
    OptionalT<F, A> valT = OptionalTKindHelper.unwrap(fa);

    // Logic: F<Optional<Function>> flatMap ( optF -> F<Optional<Value>> map (optV ->
    // optF.flatMap(optV::map)) )
    Kind<F, Optional<B>> resultValue =
        outerMonad.flatMap( // F<Optional<Function<A,B>>>
            optF -> // Optional<Function<A,B>>
            outerMonad.map( // F<Optional<A>>
                    optA -> // Optional<A>
                    optF.flatMap( // Optional<Function<A,B>> to Optional<B>
                            optA::map), // f -> optA.map(f) which is Optional<A> -> Optional<B>
                    valT.value()),
            funcT.value());
    // Wrap the result OptionalT
    return OptionalTKindHelper.wrap(OptionalT.fromKind(resultValue));
  }

  /**
   * Applies a function {@code f} that returns a {@code Kind<OptionalTKind<F, ?>, B>} to the value
   * within a {@code Kind<OptionalTKind<F, ?>, A>}, and flattens the result.
   *
   * <p>If the input {@code ma} contains {@code F<Optional.of(a)>}, {@code f(a)} is invoked. The
   * resulting {@code Kind<OptionalTKind<F, ?>, B>} (which internally is {@code F<Optional<B>>})
   * becomes the result. If {@code ma} contains {@code F<Optional.empty()>}, or if the inner {@code
   * Optional} is {@code empty}, the result is {@code F<Optional.empty()>} within the {@code
   * OptionalTKind} context.
   *
   * @param <A> The original type of the value.
   * @param <B> The type of the value in the resulting {@code Kind}.
   * @param f The function to apply, returning a new {@code Kind}. Must not be null.
   * @param ma The {@code Kind<OptionalTKind<F, ?>, A>} to transform. Must not be null.
   * @return A new {@code Kind<OptionalTKind<F, ?>, B>}.
   */
  @Override
  public <A, B> @NonNull Kind<OptionalTKind<F, ?>, B> flatMap(
      @NonNull Function<A, Kind<OptionalTKind<F, ?>, B>> f,
      @NonNull Kind<OptionalTKind<F, ?>, A> ma) {
    Objects.requireNonNull(f, "Function f cannot be null for flatMap");
    Objects.requireNonNull(ma, "Kind ma cannot be null for flatMap");
    // Unwrap the input Kind
    OptionalT<F, A> optionalT = OptionalTKindHelper.unwrap(ma);

    // Logic: F<Optional<A>> flatMap ( optA -> optA.map(a ->
    // f(a).unwrap().value()).orElse(F<Optional.empty>) )
    Kind<F, Optional<B>> newValue =
        outerMonad.flatMap( // Operating on F<Optional<A>>
            optA -> // optA is Optional<A>
            optA.map( // If Optional.of(a), apply inner function
                        a -> {
                          // Apply f: A -> Kind<OptionalTKind<F, ?>, B>
                          Kind<OptionalTKind<F, ?>, B> resultKind = f.apply(a);
                          // Unwrap the result Kind to get OptionalT<F, B>
                          OptionalT<F, B> resultT = OptionalTKindHelper.unwrap(resultKind);
                          // Extract the inner F<Optional<B>>
                          return resultT.value(); // This is Kind<F, Optional<B>>
                        })
                    // If optA was empty, map returns empty. We need F<Optional.empty> in this case.
                    .orElse(
                        outerMonad.of(
                            Optional
                                .empty())), // If Optional<A> is empty, result is F<Optional.empty>
            optionalT.value() // The initial Kind<F, Optional<A>>
            );
    // Wrap the final OptionalT
    return OptionalTKindHelper.wrap(OptionalT.fromKind(newValue));
  }

  // --- MonadError Methods (Error Type E = Void) ---

  /**
   * Raises an error in the {@code OptionalTKind<F, ?>} context. For {@code OptionalT}, an error is
   * represented by the {@code empty} state, so this method returns an {@code OptionalTKind}
   * wrapping {@code F<Optional.empty()>}. The provided {@code error} of type {@link Void} is
   * ignored.
   *
   * @param <A> The type parameter for the resulting {@code Kind}, though it will be empty.
   * @param error The error value ({@code null} for {@link Void}).
   * @return A {@code Kind<OptionalTKind<F, ?>, A>} representing {@code F<Optional.empty()>}.
   */
  @Override
  public <A> @NonNull Kind<OptionalTKind<F, ?>, A> raiseError(@Nullable Void error) {
    // The error state for OptionalT is F<Optional.empty()>
    // Wrap the concrete OptionalT using the helper
    return OptionalTKindHelper.wrap(OptionalT.none(outerMonad));
  }

  /**
   * Handles an error (represented by {@code empty}) in the {@code Kind<OptionalTKind<F, ?>, A>}. If
   * the input {@code ma} represents {@code F<Optional.empty()>}, the {@code handler} function is
   * applied. The {@link Void} parameter to the handler will be {@code null}. If {@code ma}
   * represents {@code F<Optional.of(a)>}, it is returned unchanged. This operation is performed
   * within the context of the outer monad {@code F}.
   *
   * @param <A> The type of the value.
   * @param ma The {@code Kind<OptionalTKind<F, ?>, A>} to handle. Must not be null.
   * @param handler The function to apply if {@code ma} represents {@code F<Optional.empty()>}. It
   *     takes a {@link Void} (which will be null) and returns a new {@code Kind<OptionalTKind<F,
   *     ?>, A>}. Must not be null.
   * @return A {@code Kind<OptionalTKind<F, ?>, A>}, either the original or the result of the
   *     handler.
   */
  @Override
  public <A> @NonNull Kind<OptionalTKind<F, ?>, A> handleErrorWith(
      @NonNull Kind<OptionalTKind<F, ?>, A> ma,
      @NonNull Function<Void, Kind<OptionalTKind<F, ?>, A>> handler) {
    Objects.requireNonNull(ma, "Kind ma cannot be null for handleErrorWith");
    Objects.requireNonNull(handler, "Function handler cannot be null for handleErrorWith");
    // Unwrap the input Kind
    OptionalT<F, A> optionalT = OptionalTKindHelper.unwrap(ma);

    // Logic: F<Optional<A>> flatMap( optA -> optA.isPresent() ? F<Optional.of(a)> : handler() ->
    // F<Optional<A>> )
    Kind<F, Optional<A>> handledValue =
        outerMonad.flatMap( // Operating on F<Optional<A>>
            optA -> { // optA is Optional<A>
              if (optA.isPresent()) {
                // Value is present, lift it back into F: F<Optional.of(a)>
                return outerMonad.of(optA);
              } else {
                // Value is empty, apply the handler.
                // handler(null) returns Kind<OptionalTKind<F, ?>, A>
                Kind<OptionalTKind<F, ?>, A> resultKind = handler.apply(null);
                // Unwrap the handler's result Kind to get OptionalT<F, A>
                OptionalT<F, A> resultT = OptionalTKindHelper.unwrap(resultKind);
                // Return the F<Optional<A>> from the handler's result.
                return resultT.value();
              }
            },
            optionalT.value() // The initial Kind<F, Optional<A>>
            );
    // Wrap the final OptionalT
    return OptionalTKindHelper.wrap(OptionalT.fromKind(handledValue));
  }
}
