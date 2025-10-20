// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
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

  private static final Class<MaybeFunctor> MAYBE_FUNCTOR_CLASS = MaybeFunctor.class;

  public static final MaybeFunctor INSTANCE = new MaybeFunctor();

  protected MaybeFunctor() {}

  /**
   * Applies a function to the value contained within a {@link MaybeKind} if it is a {@link Just},
   * and returns a new {@code MaybeKind} containing the result. If the input {@code MaybeKind} is
   * {@link Nothing}, or if the function {@code f} applied to the value results in {@code null},
   * then {@code Nothing} (wrapped as a {@code MaybeKind}) is returned.
   *
   * <p>This method leverages {@link MaybeKindHelper#narrow(org.higherkindedj.hkt.Kind)
   * MaybeKindHelper.MAYBE.narrow(Kind)} to access the underlying {@link Maybe} instance and {@link
   * MaybeKindHelper#widen(org.higherkindedj.hkt.maybe.Maybe) MaybeKindHelper.MAYBE.widen(Maybe)} to
   * package the result.
   *
   * @param <A> The type of the value in the input {@code MaybeKind}.
   * @param <B> The type of the value in the resulting {@code MaybeKind} after applying the
   *     function.
   * @param f The function to apply to the value if present. This function takes a value of type
   *     {@code A} and may return a {@code @Nullable B}. Must not be {@code null}.
   * @param fa The input {@code MaybeKind<A>} instance, which is a {@link Kind} representing a
   *     {@link Maybe}. Must not be {@code null}.
   * @return A new {@code MaybeKind<B>} containing the result of applying the function {@code f} if
   *     {@code ma} contained a value and {@code f} produced a non-null result. Returns a {@code
   *     MaybeKind} representing {@code Nothing} if {@code ma} was {@code Nothing} or if {@code f}
   *     returned {@code null}. Never null.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} cannot be unwrapped
   *     to a valid {@code Maybe} representation.
   */
  @Override
  public <A, B> Kind<MaybeKind.Witness, B> map(
      Function<? super A, ? extends @Nullable B> f, Kind<MaybeKind.Witness, A> fa) {

    Validation.function().requireMapper(f, "f", MAYBE_FUNCTOR_CLASS, MAP);
    Validation.kind().requireNonNull(fa, MAYBE_FUNCTOR_CLASS, MAP);

    Maybe<A> maybeA = MAYBE.narrow(fa);
    Maybe<B> resultMaybe = maybeA.map(f);
    return MAYBE.widen(resultMaybe);
  }
}
