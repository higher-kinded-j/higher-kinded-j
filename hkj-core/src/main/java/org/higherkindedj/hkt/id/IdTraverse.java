// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * Implements the {@link Traverse} typeclass for {@link Id}, using {@link IdKind.Witness} as the
 * higher-kinded type witness.
 *
 * <p>The Identity monad's traverse is trivial since it always contains exactly one value. The
 * effectful function is applied to that single value, and the result is wrapped back in an {@code
 * Id}.
 *
 * <h2>Semantic Meaning</h2>
 *
 * <p>The Id Traverse has "exactly one" semantics, meaning:
 *
 * <ul>
 *   <li>There is always exactly one element to traverse
 *   <li>The effectful function is always applied once
 *   <li>The result is always a single-element structure
 * </ul>
 *
 * <p>This makes Id useful as a base case in generic programming, monad transformers, and when
 * representing computations with no additional effects.
 *
 * @see Id
 * @see IdKind
 * @see Traverse
 */
@NullMarked
public enum IdTraverse implements Traverse<IdKind.Witness> {
  /**
   * Singleton instance of {@code IdTraverse}. This instance can be used to access {@code Traverse}
   * and {@code Foldable} operations for Id.
   */
  INSTANCE;

  private static final Class<IdTraverse> ID_TRAVERSE_CLASS = IdTraverse.class;

  /**
   * Maps a function over the value inside an Id.
   *
   * <p>Since Id always contains exactly one value, this simply applies the function to that value
   * and wraps the result in a new Id.
   *
   * @param <A> The type of the value in the input Id.
   * @param <B> The type of the value in the output Id after applying the function.
   * @param f The non-null function to apply to the value inside the Id.
   * @param fa The non-null {@code Kind<IdKind.Witness, A>} representing the input Id.
   * @return A new non-null {@code Kind<IdKind.Witness, B>} containing an Id with the result of
   *     applying the function {@code f}.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<IdKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<IdKind.Witness, A> fa) {

    Validation.function().requireMapper(f, "f", ID_TRAVERSE_CLASS, MAP);
    Validation.kind().requireNonNull(fa, ID_TRAVERSE_CLASS, MAP);

    return ID.narrow(fa).map(f);
  }

  /**
   * Traverses the Id by applying an effectful function to its value.
   *
   * <p>Since Id always contains exactly one value, this applies the effectful function {@code f} to
   * that value and maps the result back into an Id within the applicative context.
   *
   * <p>This operation always succeeds because Id is never empty. The effect from {@code f} is
   * captured in the outer context {@code G}, and the resulting value is wrapped in an Id.
   *
   * @param <G> The higher-kinded type witness for the {@link Applicative} context.
   * @param <A> The type of the value in the input Id.
   * @param <B> The type of the value in the resulting Id, wrapped within the context {@code G}.
   * @param applicative The non-null {@link Applicative} instance for the effect type {@code G}.
   * @param f A non-null function from {@code A} to {@code Kind<G, ? extends B>}, producing an
   *     effectful value.
   * @param ta The non-null {@code Kind<IdKind.Witness, A>} (an Id of {@code A}s) to traverse.
   * @return A {@code Kind<G, Kind<IdKind.Witness, B>>}. This represents an Id containing the result
   *     (type {@code B}), with the entire resulting Id structure wrapped in the applicative context
   *     {@code G}. Never null.
   * @throws NullPointerException if {@code applicative}, {@code f}, or {@code ta} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ta} cannot be unwrapped.
   */
  @Override
  public <G extends WitnessArity<TypeArity.Unary>, A, B> Kind<G, Kind<IdKind.Witness, B>> traverse(
      Applicative<G> applicative,
      Function<? super A, ? extends Kind<G, ? extends B>> f,
      Kind<IdKind.Witness, A> ta) {

    Validation.function().validateTraverse(applicative, f, ta, ID_TRAVERSE_CLASS);

    Id<A> id = ID.narrow(ta);
    A value = id.value();

    // Apply the effectful function to the single value, then wrap result in Id
    return applicative.map(b -> Id.of(b), f.apply(value));
  }

  /**
   * Maps the Id's value to a {@link Monoid} and returns it.
   *
   * <p>Since Id always contains exactly one value, this simply applies the mapping function and
   * returns the result. There is no combining needed since there is only one element.
   *
   * @param <A> The type of the value in the Id.
   * @param <M> The Monoidal type to which the value is mapped.
   * @param monoid The {@code Monoid} used (not actually needed for Id since there's only one
   *     value). Must not be null.
   * @param f A function to map the value of type {@code A} to the Monoidal type {@code M}. Must not
   *     be null.
   * @param fa The {@code Kind<IdKind.Witness, A>} representing the Id to fold. Must not be null.
   * @return The result of applying {@code f} to the Id's value. Never null (if {@code f} doesn't
   *     return null).
   * @throws NullPointerException if {@code monoid}, {@code f}, or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped.
   */
  @Override
  public <A, M> M foldMap(
      Monoid<M> monoid, Function<? super A, ? extends M> f, Kind<IdKind.Witness, A> fa) {

    Validation.function().validateFoldMap(monoid, f, fa, ID_TRAVERSE_CLASS);

    Id<A> id = ID.narrow(fa);
    return f.apply(id.value());
  }
}
