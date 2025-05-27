// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.unwrap;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.wrap;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Implements the {@link Functor} type class for {@link MaybeKind}.
 *
 * <p>This allows {@code Maybe} to be used in contexts that expect a {@link Functor} instance,
 * providing a way to apply a function to the value contained within a {@code MaybeKind} without
 * needing to explicitly unwrap and wrap it. The witness type for {@code Maybe} in the HKT system is
 * {@link MaybeKind.Witness}.
 *
 * @see Functor
 * @see MaybeKind
 * @see MaybeKind.Witness
 */
public class MaybeFunctor implements Functor<MaybeKind.Witness> {

  /**
   * Applies a function to the value contained within a {@link MaybeKind} if it is a {@link Just},
   * and returns a new {@code MaybeKind} containing the result. If the input {@code MaybeKind} is
   * {@link Nothing}, or if the function {@code f} applied to the value results in {@code null},
   * then {@code Nothing} (wrapped as a {@code MaybeKind}) is returned.
   *
   * <p>This method leverages {@link Maybe#map(Function)} for its core logic, using {@link
   * MaybeKindHelper#unwrap(Kind)} to access the underlying {@link Maybe} instance and {@link
   * MaybeKindHelper#wrap(Maybe)} to package the result.
   *
   * @param <A> The type of the value in the input {@code MaybeKind}.
   * @param <B> The type of the value in the resulting {@code MaybeKind} after applying the
   *     function.
   * @param f The function to apply to the value if present. This function takes a value of type
   *     {@code A} and may return a {@code @Nullable B}. Must not be {@code null}.
   * @param ma The input {@code MaybeKind<A>} instance, which is a {@link Kind} representing a
   *     {@link Maybe}. Must not be {@code null}.
   * @return A new {@code @NonNull MaybeKind<B>} containing the result of applying the function
   *     {@code f} if {@code ma} contained a value and {@code f} produced a non-null result. Returns
   *     a {@code MaybeKind} representing {@code Nothing} if {@code ma} was {@code Nothing} or if
   *     {@code f} returned {@code null}.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} cannot be unwrapped.
   */
  @Override
  public <A, B> @NonNull MaybeKind<B> map(
      @NonNull Function<A, @Nullable B> f, @NonNull Kind<MaybeKind.Witness, A> ma) {
    // 1. Unwrap the Kind<MaybeKind.Witness, A> to get the concrete Maybe<A>.
    Maybe<A> maybeA = unwrap(ma);
    // 2. Apply the function using Maybe's own map method.
    //    Maybe.map handles the case where 'f' might return null by producing a Nothing.
    Maybe<B> resultMaybe = maybeA.map(f);
    // 3. Wrap the resulting Maybe<B> back into MaybeKind<B>.
    return wrap(resultMaybe);
  }
}
