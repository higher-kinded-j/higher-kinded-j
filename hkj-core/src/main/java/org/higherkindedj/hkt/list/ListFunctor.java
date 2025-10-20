// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Functor} type class for {@link java.util.List}, using {@link
 * ListKind.Witness} as the higher-kinded type witness.
 *
 * <p>A {@link Functor} provides the ability to apply a function to a value inside a context (in
 * this case, a {@code List}) without needing to explicitly extract the value. The {@link
 * #map(Function, Kind)} operation transforms a {@code List<A>} into a {@code List<B>} by applying a
 * function {@code A -> B} to each element of the list.
 *
 * @see Functor
 * @see List
 * @see ListKind
 * @see ListKindHelper
 * @see ListMonad
 */
class ListFunctor implements Functor<ListKind.Witness> {

  /**
   * Singleton instance of {@code ListFunctor}. Consider accessing Functor operations via {@link
   * ListMonad#INSTANCE}.
   */
  public static final ListFunctor INSTANCE = new ListFunctor();

  /**
   * Package-private constructor to allow instantiation within the package, primarily for {@link
   * ListMonad#INSTANCE}.
   */
  ListFunctor() {
    // Constructor for package-level access or for singleton.
  }

  /**
   * Applies a function to each element of a list wrapped in a {@link Kind}.
   *
   * <p>If the input list ({@code fa}) is {@code ListKind(List<A>)}, this method applies the
   * function {@code f: A -> B} to each element of the list, producing a new {@code
   * ListKind(List<B>)}.
   *
   * <p>This operation adheres to the Functor laws:
   *
   * <ol>
   *   <li>Identity: {@code map(x -> x, fa)} is equivalent to {@code fa}.
   *   <li>Composition: {@code map(g.compose(f), fa)} is equivalent to {@code map(g, map(f, fa))}.
   * </ol>
   *
   * @param <A> The type of the elements in the input list.
   * @param <B> The type of the elements in the output list after applying the function.
   * @param f The non-null function to apply to each element of the list.
   * @param fa The non-null {@code Kind<ListKind.Witness, A>} (which is a {@code ListKind<A>})
   *     containing the input list.
   * @return A new non-null {@code Kind<ListKind.Witness, B>} containing a list with the results of
   *     applying the function {@code f} to each element of the input list.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} is not a valid {@code
   *     ListKind} representation.
   */
  @Override
  public <A, B> Kind<ListKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<ListKind.Witness, A> fa) {

    Validation.function().requireMapper(f, "f", ListFunctor.class, MAP);
    Validation.kind().requireNonNull(fa, ListFunctor.class, MAP);

    List<A> listA = LIST.narrow(fa);
    List<B> listB = new ArrayList<>(listA.size());
    for (A a : listA) {
      listB.add(f.apply(a));
    }
    return LIST.widen(listB);
  }
}
