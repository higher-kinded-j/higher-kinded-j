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
 * Implements the {@link MonadError} interface for {@link OptionalT}. The witness for {@code
 * OptionalT<F, ?>} is {@link OptionalTKind.Witness Witness&lt;F&gt;}. The error type {@code E} is
 * fixed to {@link Void}, as {@code OptionalT} inherently represents failure as an absence of a
 * value (similar to {@code Optional.empty()}).
 *
 * <p>This class requires a {@link Monad} instance for the outer monad {@code F} to operate. It uses
 * {@link OptionalTKindHelper} to convert between the {@code Kind<OptionalTKind.Witness<F>, A>}
 * representation and the concrete {@link OptionalT} type.
 *
 * @param <F> The witness type of the outer monad (e.g., {@code IOKind.Witness}).
 */
public class OptionalTMonad<F> implements MonadError<OptionalTKind.Witness<F>, Void> {

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
   * Lifts a value {@code a} into the {@code Kind<OptionalTKind.Witness<F>, A>} context. Uses {@link
   * Optional#ofNullable(Object)} to handle potential nulls, resulting in {@code
   * F<Optional.of(value)>} or {@code F<Optional.empty()>}.
   *
   * @param <A> The type of the value.
   * @param value The value to lift. Can be null.
   * @return A {@code Kind<OptionalTKind.Witness<F>, A>} representing the lifted value.
   */
  @Override
  public <A> @NonNull Kind<OptionalTKind.Witness<F>, A> of(@Nullable A value) {
    Kind<F, Optional<A>> lifted = outerMonad.of(Optional.ofNullable(value));
    return OptionalTKindHelper.wrap(OptionalT.fromKind(lifted));
  }

  /**
   * Maps a function {@code f} over the value within a {@code Kind<OptionalTKind.Witness<F>, A>}. If
   * the wrapped {@code Kind<F, Optional<A>>} contains {@code Optional.of(a)}, the function is
   * applied. If it contains {@code Optional.empty()}, or if {@code f} returns null, the result is
   * {@code F<Optional.empty()>}. The transformation is applied within the context of the outer
   * monad {@code F}.
   *
   * @param <A> The original type of the value.
   * @param <B> The new type of the value after applying the function.
   * @param f The function to apply. Must not be null.
   * @param fa The {@code Kind<OptionalTKind.Witness<F>, A>} to map over. Must not be null.
   * @return A new {@code Kind<OptionalTKind.Witness<F>, B>} with the function applied.
   */
  @Override
  public <A, B> @NonNull Kind<OptionalTKind.Witness<F>, B> map(
      @NonNull Function<A, @Nullable B> f, @NonNull Kind<OptionalTKind.Witness<F>, A> fa) {
    Objects.requireNonNull(f, "Function f cannot be null for map");
    Objects.requireNonNull(fa, "Kind fa cannot be null for map");
    OptionalT<F, A> optionalT = OptionalTKindHelper.unwrap(fa);
    Kind<F, Optional<B>> newValue = outerMonad.map(opt -> opt.map(f), optionalT.value());
    return OptionalTKindHelper.wrap(OptionalT.fromKind(newValue));
  }

  /**
   * Applies a function wrapped in {@code Kind<OptionalTKind.Witness<F>, Function<A, B>>} to a value
   * wrapped in {@code Kind<OptionalTKind.Witness<F>, A>}.
   *
   * <p>The behavior is as follows:
   *
   * <ul>
   *   <li>If both the function and value are present (i.e., {@code F<Optional.of(Function)>} and
   *       {@code F<Optional.of(Value)>}), the function is applied. If the application results in a
   *       null, it becomes {@code F<Optional.empty()>}. Otherwise, {@code F<Optional.of(Result)>}.
   *   <li>If either the function or value is {@code empty} (i.e., {@code F<Optional.empty()>}), the
   *       result is {@code F<Optional.empty()>}.
   * </ul>
   *
   * @param <A> The type of the input value.
   * @param <B> The type of the result value.
   * @param ff The wrapped function. Must not be null.
   * @param fa The wrapped value. Must not be null.
   * @return A new {@code Kind<OptionalTKind.Witness<F>, B>} representing the application.
   */
  @Override
  public <A, B> @NonNull Kind<OptionalTKind.Witness<F>, B> ap(
      @NonNull Kind<OptionalTKind.Witness<F>, Function<A, @Nullable B>> ff,
      @NonNull Kind<OptionalTKind.Witness<F>, A> fa) {
    Objects.requireNonNull(ff, "Kind ff cannot be null for ap");
    Objects.requireNonNull(fa, "Kind fa cannot be null for ap");
    OptionalT<F, Function<A, @Nullable B>> funcT = OptionalTKindHelper.unwrap(ff);
    OptionalT<F, A> valT = OptionalTKindHelper.unwrap(fa);

    Kind<F, Optional<B>> resultValue =
        outerMonad.flatMap(
            optF ->
                outerMonad.map(
                    optA -> optF.flatMap(f -> optA.map(f)), // f itself can return null
                    valT.value()),
            funcT.value());
    return OptionalTKindHelper.wrap(OptionalT.fromKind(resultValue));
  }

  /**
   * Applies a function {@code f} that returns a {@code Kind<OptionalTKind.Witness<F>, B>} to the
   * value within a {@code Kind<OptionalTKind.Witness<F>, A>}, and flattens the result.
   *
   * <p>If the input {@code ma} contains {@code F<Optional.of(a)>}, {@code f(a)} is invoked. The
   * resulting {@code Kind<OptionalTKind.Witness<F>, B>} (which internally is {@code
   * F<Optional<B>>}) becomes the result. If {@code ma} contains {@code F<Optional.empty()>}, or if
   * the inner {@code Optional} is {@code empty}, the result is {@code F<Optional.empty()>}.
   *
   * @param <A> The original type of the value.
   * @param <B> The type of the value in the resulting {@code Kind}.
   * @param f The function to apply, returning a new {@code Kind}. Must not be null.
   * @param ma The {@code Kind<OptionalTKind.Witness<F>, A>} to transform. Must not be null.
   * @return A new {@code Kind<OptionalTKind.Witness<F>, B>}.
   */
  @Override
  public <A, B> @NonNull Kind<OptionalTKind.Witness<F>, B> flatMap(
      @NonNull Function<A, Kind<OptionalTKind.Witness<F>, B>> f,
      @NonNull Kind<OptionalTKind.Witness<F>, A> ma) {
    Objects.requireNonNull(f, "Function f cannot be null for flatMap");
    Objects.requireNonNull(ma, "Kind ma cannot be null for flatMap");
    OptionalT<F, A> optionalT = OptionalTKindHelper.unwrap(ma);

    Kind<F, Optional<B>> newValue =
        outerMonad.flatMap(
            optA ->
                optA.map(
                        a -> {
                          Kind<OptionalTKind.Witness<F>, B> resultKind = f.apply(a);
                          OptionalT<F, B> resultT = OptionalTKindHelper.unwrap(resultKind);
                          return resultT.value();
                        })
                    .orElseGet(() -> outerMonad.of(Optional.empty())),
            optionalT.value());
    return OptionalTKindHelper.wrap(OptionalT.fromKind(newValue));
  }

  // --- MonadError Methods (Error Type E = Void) ---

  /**
   * Raises an error in the {@code Kind<OptionalTKind.Witness<F>, A>} context. For {@code
   * OptionalT}, an error is represented by the {@code empty} state, so this method returns a {@code
   * Kind} wrapping {@code F<Optional.empty()>}. The provided {@code error} of type {@link Void} is
   * ignored.
   *
   * @param <A> The type parameter for the resulting {@code Kind}, though it will be empty.
   * @param error The error value ({@code null} for {@link Void}).
   * @return A {@code Kind<OptionalTKind.Witness<F>, A>} representing {@code F<Optional.empty()>}.
   */
  @Override
  public <A> @NonNull Kind<OptionalTKind.Witness<F>, A> raiseError(@Nullable Void error) {
    return OptionalTKindHelper.wrap(OptionalT.none(outerMonad));
  }

  /**
   * Handles an error (represented by {@code empty}) in the {@code Kind<OptionalTKind.Witness<F>,
   * A>}. If the input {@code ma} represents {@code F<Optional.empty()>}, the {@code handler}
   * function is applied. The {@link Void} parameter to the handler will be {@code null}. If {@code
   * ma} represents {@code F<Optional.of(a)>}, it is returned unchanged. This operation is performed
   * within the context of the outer monad {@code F}.
   *
   * @param <A> The type of the value.
   * @param ma The {@code Kind<OptionalTKind.Witness<F>, A>} to handle. Must not be null.
   * @param handler The function to apply if {@code ma} represents {@code F<Optional.empty()>}. It
   *     takes a {@link Void} (which will be null) and returns a new {@code
   *     Kind<OptionalTKind.Witness<F>, A>}. Must not be null.
   * @return A {@code Kind<OptionalTKind.Witness<F>, A>}, either the original or the result of the
   *     handler.
   */
  @Override
  public <A> @NonNull Kind<OptionalTKind.Witness<F>, A> handleErrorWith(
      @NonNull Kind<OptionalTKind.Witness<F>, A> ma,
      @NonNull Function<Void, Kind<OptionalTKind.Witness<F>, A>> handler) {
    Objects.requireNonNull(ma, "Kind ma cannot be null for handleErrorWith");
    Objects.requireNonNull(handler, "Function handler cannot be null for handleErrorWith");
    OptionalT<F, A> optionalT = OptionalTKindHelper.unwrap(ma);

    Kind<F, Optional<A>> handledValue =
        outerMonad.flatMap(
            optA -> {
              if (optA.isPresent()) {
                return outerMonad.of(optA);
              } else {
                Kind<OptionalTKind.Witness<F>, A> resultKind = handler.apply(null);
                OptionalT<F, A> resultT = OptionalTKindHelper.unwrap(resultKind);
                return resultT.value();
              }
            },
            optionalT.value());
    return OptionalTKindHelper.wrap(OptionalT.fromKind(handledValue));
  }
}
