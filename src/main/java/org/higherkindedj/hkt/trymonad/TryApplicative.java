package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Applicative} interface for {@link Try}, using {@link TryKind.Witness}. It
 * extends {@link TryFunctor}.
 *
 * @see Try
 * @see TryKind.Witness
 * @see TryFunctor
 */
public class TryApplicative extends TryFunctor implements Applicative<TryKind.Witness> {

  /**
   * Lifts a value into a successful {@code Try} context, represented as {@code
   * Kind<TryKind.Witness, A>}.
   *
   * @param <A> The type of the value.
   * @param value The value to lift. Can be {@code null}.
   * @return A {@code Kind<TryKind.Witness, A>} representing {@code Try.success(value)}.
   */
  @Override
  public <A> @NonNull Kind<TryKind.Witness, A> of(@Nullable A value) {
    return wrap(Try.success(value));
  }

  /**
   * Applies a function wrapped in a {@code Kind<TryKind.Witness, Function<A, B>>} to a value
   * wrapped in a {@code Kind<TryKind.Witness, A>}.
   *
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @param ff The {@code Kind<TryKind.Witness, Function<A, B>>} containing the function.
   * @param fa The {@code Kind<TryKind.Witness, A>} containing the value.
   * @return A new {@code Kind<TryKind.Witness, B>} resulting from the application. If {@code ff} or
   *     {@code fa} is a {@link Try.Failure}, or if applying the function in {@code ff} to the value
   *     in {@code fa} (if both are {@link Try.Success}) results in an exception, then a {@link
   *     Try.Failure} is returned.
   */
  @Override
  public <A, B> @NonNull Kind<TryKind.Witness, B> ap(
      @NonNull Kind<TryKind.Witness, Function<A, B>> ff, @NonNull Kind<TryKind.Witness, A> fa) {
    Try<Function<A, B>> tryF = unwrap(ff);
    Try<A> tryA = unwrap(fa);

    Try<B> resultTry =
        tryF.fold(f -> tryA.fold(a -> Try.of(() -> f.apply(a)), Try::failure), Try::failure);
    return wrap(resultTry);
  }
}
