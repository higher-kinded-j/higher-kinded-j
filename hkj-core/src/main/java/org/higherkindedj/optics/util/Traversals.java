// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdentityMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.optics.Traversal;

/** A final utility class providing static helper methods for working with {@link Traversal}s. */
public final class Traversals {
  /** Private constructor to prevent instantiation. */
  private Traversals() {}

  /**
   * Modifies all targets of a {@link Traversal} using a pure, non-effectful function.
   *
   * <p>This is a convenience method that wraps the function in the {@link Id} monad, which
   * represents a direct, synchronous computation, and then immediately unwraps the result.
   *
   * @param traversal The {@code Traversal} to use.
   * @param f A pure function to apply to each focused part.
   * @param source The source structure.
   * @param <S> The type of the source structure.
   * @param <A> The type of the focused parts.
   * @return A new, updated source structure.
   */
  public static <S, A> S modify(Traversal<S, A> traversal, Function<A, A> f, S source) {
    Function<A, Kind<Id.Witness, A>> fId = a -> Id.of(f.apply(a));
    Kind<Id.Witness, S> resultInId = traversal.modifyF(fId, source, IdentityMonad.instance());
    return IdKindHelper.ID.narrow(resultInId).value();
  }

  /**
   * Extracts all targets of a {@link Traversal} from a source structure into a {@link List}.
   *
   * <p>This method traverses the structure, collecting each focused part into a list. It uses the
   * {@link Id} monad internally as a trivial context for the traversal.
   *
   * @param traversal The {@code Traversal} to use.
   * @param source The source structure.
   * @param <S> The type of the source structure.
   * @param <A> The type of the focused parts.
   * @return A {@code List} containing all the focused parts, in the order they were traversed.
   */
  public static <S, A> List<A> getAll(final Traversal<S, A> traversal, final S source) {
    final List<A> results = new ArrayList<>();
    traversal.modifyF(
        a -> {
          results.add(a);
          return Id.of(a); // Return original value in an Id context
        },
        source,
        IdentityMonad.instance());
    return results;
  }

  /**
   * Creates a {@code Traversal} that focuses on every element within a {@link List}.
   *
   * <p>This is a canonical traversal for the {@code List} data type, allowing an effectful function
   * to be applied to each of its elements.
   *
   * @param <A> The element type of the list.
   * @return A {@code Traversal} for the elements of a list.
   */
  public static <A> Traversal<List<A>, A> forList() {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, List<A>> modifyF(
          Function<A, Kind<F, A>> f, List<A> source, Applicative<F> applicative) {
        Kind<F, Kind<ListKind.Witness, A>> traversed =
            ListTraverse.INSTANCE.traverse(applicative, ListKindHelper.LIST.widen(source), f);
        return applicative.map(ListKindHelper.LIST::narrow, traversed);
      }
    };
  }

  /**
   * Creates a {@code Traversal} that focuses on a specific value within a {@code Map} by its key.
   *
   * <p>If the key exists in the map, the traversal focuses on its corresponding value. If the key
   * does not exist, the traversal focuses on zero elements, and any modification will have no
   * effect.
   *
   * @param key The key to focus on in the map.
   * @param <K> The type of the map's keys.
   * @param <V> The type of the map's values.
   * @return A {@code Traversal} for a map value.
   */
  public static <K, V> Traversal<Map<K, V>, V> forMap(K key) {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, Map<K, V>> modifyF(
          Function<V, Kind<F, V>> f, Map<K, V> source, Applicative<F> applicative) {
        V currentValue = source.get(key);
        if (currentValue == null) {
          // If the key doesn't exist, do nothing.
          return applicative.of(source);
        }

        // Apply the function to the existing value to get the new value in context.
        Kind<F, V> newValueF = f.apply(currentValue);

        // Map the result back into the map structure.
        return applicative.map(
            newValue -> {
              // Create a new map to preserve immutability.
              Map<K, V> newMap = new HashMap<>(source);
              newMap.put(key, newValue);
              return newMap;
            },
            newValueF);
      }
    };
  }

  /**
   * Applies an effectful function to each element of a {@link List} and collects the results in a
   * single effect.
   *
   * <p>This is a direct application of the {@code traverse} operation for {@code List}, provided
   * here as a static helper for convenience. It "flips" a {@code List<A>} and a function {@code A
   * -> F<B>} into a single {@code F<List<B>>}.
   *
   * @param list The source list to traverse.
   * @param f The effectful function to apply to each element.
   * @param applicative The {@code Applicative} instance for the effect {@code F}.
   * @param <F> The higher-kinded type witness of the applicative effect.
   * @param <A> The element type of the source list.
   * @param <B> The element type of the resulting list.
   * @return A {@code Kind<F, List<B>>}, representing the collected results within the applicative
   *     context.
   */
  public static <F, A, B> Kind<F, List<B>> traverseList(
      final List<A> list, final Function<A, Kind<F, B>> f, final Applicative<F> applicative) {

    final List<Kind<F, B>> listOfEffects = list.stream().map(f).collect(Collectors.toList());
    final Kind<ListKind.Witness, Kind<F, B>> effectsAsKind =
        ListKindHelper.LIST.widen(listOfEffects);
    final var effectOfKindList = ListTraverse.INSTANCE.sequenceA(applicative, effectsAsKind);

    return applicative.map(ListKindHelper.LIST::narrow, effectOfKindList);
  }
}
