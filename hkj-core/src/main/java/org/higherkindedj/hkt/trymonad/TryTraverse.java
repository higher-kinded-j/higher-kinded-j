// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * The Traverse and Foldable instance for {@link Try}.
 *
 * <p>Traversal and folding operations are performed on the 'Success' value. If the instance is
 * 'Failure', these operations short-circuit or return an empty/identity value.
 */
public enum TryTraverse implements Traverse<TryKind.Witness> {
  INSTANCE;

  private static Class<TryTraverse> TRY_TRAVERSE_CLASS = TryTraverse.class;

  /**
   * Maps a function over the value contained within a {@code TryKind} context, if a value is
   * present (i.e., if it's a {@link Try.Success}).
   *
   * @param <A> The type of the value in the input {@code Try}.
   * @param <B> The type of the value in the output {@code Try} after applying the function.
   * @param f The non-null function to apply to the value inside the {@code Try} if present.
   * @param fa The non-null {@code Kind<TryKind.Witness, A>} representing the {@code Try<A>} whose
   *     value is to be transformed.
   * @return A non-null {@code Kind<TryKind.Witness, B>} representing the transformed {@code
   *     Try<B>}.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped
   *     to a valid {@code Try} representation.
   */
  @Override
  public <A, B> Kind<TryKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<TryKind.Witness, A> fa) {

    Validation.function().requireMapper(f, "f", TRY_TRAVERSE_CLASS, MAP);
    Validation.kind().requireNonNull(fa, TRY_TRAVERSE_CLASS, MAP);

    return TRY.widen(TRY.narrow(fa).map(f));
  }

  /**
   * Traverses a {@code Try} structure, transforming it from {@code Try<A>} to {@code G<Try<B>>}
   * using an applicative functor {@code G}.
   *
   * <p>This operation allows sequencing effects: if the {@code Try} contains a success value, the
   * function {@code f} is applied to produce a {@code G<B>}, which is then wrapped in {@code Try}
   * within the {@code G} context. If the {@code Try} is a failure, the result is a {@code G}
   * containing the failure.
   *
   * @param <G> The witness type of the target applicative functor.
   * @param <A> The type of the value in the source {@code Try}.
   * @param <B> The type of the value in the target applicative context.
   * @param applicative The {@link Applicative} instance for the target context {@code G}. Must not
   *     be null.
   * @param f The transformation function that takes a value of type {@code A} and produces a {@code
   *     Kind<G, B>}. Must not be null.
   * @param ta The {@code Kind<TryKind.Witness, A>} to traverse. Must not be null.
   * @return A {@code Kind<G, Kind<TryKind.Witness, B>>} representing the traversed structure. Never
   *     null.
   * @throws NullPointerException if {@code applicative}, {@code f}, or {@code ta} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ta} cannot be unwrapped
   *     to a valid {@code Try} representation.
   */
  @Override
  public <G, A, B> Kind<G, Kind<TryKind.Witness, B>> traverse(
      Applicative<G> applicative,
      Function<? super A, ? extends Kind<G, ? extends B>> f,
      Kind<TryKind.Witness, A> ta) {

    Validation.function().validateTraverse(applicative, f, ta, TRY_TRAVERSE_CLASS);

    return TRY.narrow(ta)
        .fold(
            // Success case: Apply the effectful function and wrap the result in a Success.
            successValue -> applicative.map(b -> TRY.widen(Try.success(b)), f.apply(successValue)),

            // Failure case: Lift the Failure directly into the applicative context.
            cause -> applicative.of(TRY.widen(Try.failure(cause))));
  }

  /**
   * Folds a {@code Try} by mapping its success value (if present) through a function and combining
   * the result using a {@link Monoid}.
   *
   * <p>This operation enables reduction of {@code Try} values into a monoid type {@code M}:
   *
   * <ul>
   *   <li><b>Success value</b>: Applies {@code f} to the value and returns the result
   *   <li><b>Failure</b>: Returns the monoid's identity element ({@code monoid.empty()})
   * </ul>
   *
   * @param <A> The type of the value in the {@code Try}.
   * @param <M> The monoid type used for combining results.
   * @param monoid The {@link Monoid} instance defining the combination operation and identity
   *     element. Must not be null.
   * @param f The function to apply to the {@code Try}'s success value (if present) to produce a
   *     value of type {@code M}. Must not be null.
   * @param fa The {@code Kind<TryKind.Witness, A>} to fold. Must not be null.
   * @return A value of type {@code M}: either {@code f(value)} if the try contains a success value,
   *     or {@code monoid.empty()} if the try is a failure. Never null.
   * @throws NullPointerException if {@code monoid}, {@code f}, or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped
   *     to a valid {@code Try} representation.
   */
  @Override
  public <A, M> M foldMap(
      Monoid<M> monoid, Function<? super A, ? extends M> f, Kind<TryKind.Witness, A> fa) {

    Validation.function().validateFoldMap(monoid, f, fa, TRY_TRAVERSE_CLASS);

    // If the Try is a Success, apply the function `f` to the value.
    // If it's a Failure, return the identity element of the Monoid.
    return TRY.narrow(fa).fold(f, cause -> monoid.empty());
  }
}
