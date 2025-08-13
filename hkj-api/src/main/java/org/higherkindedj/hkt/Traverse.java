// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.Function;
import org.jspecify.annotations.NonNull;

/**
 * The Traverse type class represents data structures that can be traversed from left to right,
 * performing an Applicative (or Monadic) effect for each element and collecting the results.
 *
 * <p>A Traverse instance must satisfy the following laws:
 *
 * <ol>
 *   <li><b>Naturality:</b> {@code t.compose(traverse(AP1, ta, f)) == traverse(AP2, ta,
 *       t.compose(f))} for any applicative transformation t.
 *   <li><b>Identity:</b> {@code traverse(Id.applicative(), ta, a -> Id.of(a)) == Id.of(ta)}
 *       (traversing with the Identity applicative and identity function is an identity).
 *   <li><b>Composition:</b> {@code traverse(ComposeAP.applicative(AP1, AP2), ta, fabc) ==
 *       AP1.map(traverse(AP2, ta, a -> fabc.apply(a).value().apply(a)), traverse(AP1, _, a -> a))}
 *       // This law is more complex and involves composing applicatives. // A simpler way to think
 *       about it: // traverse F (traverse G ...) is equivalent to traverse (F composed with G) ...
 * </ol>
 *
 * <p>Where {@code AP1}, {@code AP2} are {@link Applicative} instances, {@code ta} is a value of
 * type {@code Kind<T, A>}, and {@code f} is a function {@code A -> Kind<G, B>}. The {@code Id}
 * applicative is the identity applicative.
 *
 * @param <T> The type constructor of the traversable data structure (e.g., {@code
 *     ListKind.Witness}).
 */
public interface Traverse<T> extends Functor<T>, Foldable<T> {

  /**
   * Traverses this structure, applying a function {@code f} to each element {@code A} that results
   * in an Applicative effect {@code Kind<G, B>}. All the effects are combined using the
   * capabilities of the {@code Applicative<G>}.
   *
   * <p>Example: Given a list of user IDs {@code List<String>} and a function {@code String ->
   * IO<User>}, {@code traverse} can produce an {@code IO<List<User>>}. If any call to fetch a user
   * fails (e.g., if {@code G} is {@code EitherT<IO, Error, ?>}), the entire traversal might
   * short-circuit, depending on the {@code Applicative<G>} behavior.
   *
   * @param applicative The {@link Applicative} instance for the effect type {@code G}.
   * @param ta The traversable structure {@code Kind<T, A>} (e.g., a list of {@code A}s).
   * @param f A function from {@code A} to {@code Kind<G, B>}, producing an effectful value.
   * @param <G> The type constructor of the applicative effect (e.g., {@code IO.Witness}, {@code
   *     Option.Witness}).
   * @param <A> The type of elements in the input structure.
   * @param <B> The type of elements in the output structure, wrapped in the effect {@code G}.
   * @return An applicative effect {@code Kind<G, Kind<T, B>>}, which is the structure {@code T} of
   *     results {@code B}, all wrapped in the effect {@code G}. (e.g. {@code IO<List<B>>}, {@code
   *     Option<List<B>>})
   */
  <G, A, B> Kind<G, Kind<T, B>> traverse(
      @NonNull Applicative<G> applicative,
      @NonNull Kind<T, A> ta,
      @NonNull Function<? super A, ? extends Kind<G, ? extends B>> f);

  /**
   * Sequences a structure of applicative effects {@code Kind<T, Kind<G, A>>} into an applicative
   * effect of a structure {@code Kind<G, Kind<T, A>>}.
   *
   * <p>This is a specialized version of {@link #traverse(Applicative, Kind, Function)}, where the
   * function {@code f} is the identity function.
   *
   * <p>Example: Given a {@code List<IO<A>>}, {@code sequenceA} can turn it into an {@code
   * IO<List<A>>}.
   *
   * @param applicative The {@link Applicative} instance for the effect type {@code G}.
   * @param tga The traversable structure where each element is an applicative effect {@code Kind<T,
   *     Kind<G, A>>}.
   * @param <G> The type constructor of the applicative effect.
   * @param <A> The type of elements wrapped in the effect {@code G} within the structure {@code T}.
   * @return An applicative effect {@code Kind<G, Kind<T, A>>}.
   */
  default <G, A> Kind<G, Kind<T, A>> sequenceA(
      @NonNull Applicative<G> applicative, @NonNull Kind<T, Kind<G, A>> tga) {
    // Implementation using traverse with identity function
    // The cast for '? extends A' to 'A' is generally safe here due to how sequence is used.
    // The function f is A -> Kind<G, A>, where A is Kind<G,A> from tga.
    // So it becomes Kind<G,A> -> Kind<G, Kind<G,A>> which is not what we want.
    // The A in traverse's f: A -> Kind<G,B> is the inner A of Kind<T,A>
    // Here, A is Kind<G,A_val> for tga :: Kind<T, Kind<G,A_val>>.
    // So f is (Kind<G, A_val> element) -> (Kind<G, A_val> element)
    // B becomes A_val.
    // The result of traverse is Kind<G, Kind<T, A_val>>
    return traverse(applicative, tga, (Kind<G, A> ga) -> ga);
  }
}
