// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalTraverse;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.trampoline.Trampoline;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A final utility class providing static helper methods for working with {@link Traversal}s. */
@NullMarked
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
  public static <S, A> @Nullable S modify(
      final Traversal<S, A> traversal, final Function<A, A> f, S source) {
    Function<A, Kind<IdKind.Witness, A>> fId = a -> Id.of(f.apply(a));
    Kind<IdKind.Witness, S> resultInId = traversal.modifyF(fId, source, IdMonad.instance());
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
        IdMonad.instance());
    return results;
  }

  /**
   * Creates an affine {@code Traversal} that focuses on the value only if it matches the given
   * predicate.
   *
   * <p>This is a static combinator that creates a {@code Traversal<A, A>} which focuses on zero or
   * one element (the input itself). It acts as an identity for matching values and a no-op for
   * non-matching values.
   *
   * <p>This combinator is particularly useful when composed with other traversals to add filtering
   * at any point in the composition chain.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Create an affine traversal that only focuses on active users
   * Traversal<User, User> activeUserFilter = Traversals.filtered(User::isActive);
   *
   * // Compose with a list traversal
   * Traversal<List<User>, User> activeUsersInList =
   *     Traversals.<User>forList().andThen(activeUserFilter);
   *
   * // Alternative: use directly with andThen
   * Traversal<List<User>, String> activeUserNames =
   *     Traversals.<User>forList()
   *         .andThen(Traversals.filtered(User::isActive))
   *         .andThen(userNameLens.asTraversal());
   *
   * // Can also be used standalone
   * User user = ...;
   * User result = Traversals.modify(activeUserFilter, User::grantBonus, user);
   * // If user is active, returns user with bonus; otherwise returns user unchanged
   * }</pre>
   *
   * @param predicate The predicate to filter by
   * @param <A> The type of the value to filter
   * @return An affine {@code Traversal} that focuses on the value only if it matches
   */
  public static <A> Traversal<A, A> filtered(final Predicate<? super A> predicate) {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, A> modifyF(
          final Function<A, Kind<F, A>> f, final A source, final Applicative<F> applicative) {
        return predicate.test(source) ? f.apply(source) : applicative.of(source);
      }
    };
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
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, List<A>> modifyF(
          final Function<A, Kind<F, A>> f, final List<A> source, final Applicative<F> applicative) {
        Kind<F, Kind<ListKind.Witness, A>> traversed =
            ListTraverse.INSTANCE.traverse(applicative, f, ListKindHelper.LIST.widen(source));
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
  public static <K, V> Traversal<Map<K, V>, V> forMap(final K key) {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Map<K, V>> modifyF(
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
   * Creates a {@code Traversal} that focuses on the value within an {@link Optional}.
   *
   * <p>This is an affine traversal with 0-1 cardinality. If the optional is empty, the traversal
   * focuses on zero elements and modifications have no effect. If the optional contains a value,
   * the traversal focuses on that single value.
   *
   * <p>This traversal is particularly useful for composing with other traversals to handle optional
   * nested structures.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Modify the value inside an optional
   * Traversal<Optional<String>, String> optTraversal = Traversals.forOptional();
   * Optional<String> result = Traversals.modify(
   *     optTraversal,
   *     String::toUpperCase,
   *     Optional.of("hello")
   * );
   * // result = Optional.of("HELLO")
   *
   * // Compose with other traversals
   * Traversal<List<Optional<User>>, User> userTraversal =
   *     Traversals.<Optional<User>>forList()
   *         .andThen(Traversals.forOptional());
   * }</pre>
   *
   * @param <A> The type of the value potentially contained in the optional.
   * @return A {@code Traversal} for optional values.
   */
  public static <A> Traversal<Optional<A>, A> forOptional() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Optional<A>> modifyF(
          final Function<A, Kind<F, A>> f,
          final Optional<A> source,
          final Applicative<F> applicative) {
        return traverseOptional(source, f, applicative);
      }
    };
  }

  /**
   * Creates a {@code Traversal} for all elements of a {@link Set}.
   *
   * <p>This is a canonical traversal for the {@code Set} data type, allowing an effectful function
   * to be applied to each of its elements. The resulting set preserves the uniqueness property of
   * sets; if the modification function produces duplicate values, only one will be retained.
   *
   * <p>The traversal uses a {@link LinkedHashSet} internally to preserve iteration order during
   * modification, though the final set may have a different order if duplicates are produced.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Modify all elements in a set
   * Traversal<Set<String>, String> setTraversal = Traversals.forSet();
   * Set<String> names = Set.of("alice", "bob", "charlie");
   * Set<String> upper = Traversals.modify(setTraversal, String::toUpperCase, names);
   * // upper = Set.of("ALICE", "BOB", "CHARLIE")
   *
   * // Get all elements
   * List<String> allNames = Traversals.getAll(setTraversal, names);
   * // allNames = ["alice", "bob", "charlie"] (order may vary)
   * }</pre>
   *
   * @param <A> The element type of the set.
   * @return A {@code Traversal} for the elements of a set.
   */
  public static <A> Traversal<Set<A>, A> forSet() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Set<A>> modifyF(
          final Function<A, Kind<F, A>> f, final Set<A> source, final Applicative<F> applicative) {
        return traverseSet(source, f, applicative);
      }
    };
  }

  /**
   * Creates a {@code Traversal} for all elements of an array.
   *
   * <p>This is a canonical traversal for array types, allowing an effectful function to be applied
   * to each element. The resulting array has the same length as the source array.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Modify all elements in an array
   * Traversal<String[], String> arrayTraversal = Traversals.forArray();
   * String[] names = {"alice", "bob", "charlie"};
   * String[] upper = Traversals.modify(arrayTraversal, String::toUpperCase, names);
   * // upper = ["ALICE", "BOB", "CHARLIE"]
   *
   * // Get all elements
   * List<String> allNames = Traversals.getAll(arrayTraversal, names);
   * // allNames = ["alice", "bob", "charlie"]
   * }</pre>
   *
   * @param <A> The element type of the array.
   * @return A {@code Traversal} for the elements of an array.
   */
  public static <A> Traversal<A[], A> forArray() {
    return new Traversal<>() {
      @Override
      @SuppressWarnings("unchecked")
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, A[]> modifyF(
          final Function<A, Kind<F, A>> f, final A[] source, final Applicative<F> applicative) {
        // Traverse to a list, then convert back to array using the source's component type
        Kind<F, List<A>> listResult = traverseArray(source, f, applicative);
        return applicative.map(
            list ->
                list.toArray(
                    (A[])
                        java.lang.reflect.Array.newInstance(
                            source.getClass().getComponentType(), list.size())),
            listResult);
      }
    };
  }

  /**
   * Creates a {@code Traversal} that focuses on all values within a {@link Map}, preserving the
   * keys.
   *
   * <p>This traversal applies an effectful function to each value in the map while keeping all keys
   * unchanged. The order of traversal follows the map's iteration order.
   *
   * <p>This is distinct from {@link #forMap(Object)} which focuses on a single key-value pair. This
   * traversal focuses on all values simultaneously.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Modify all values in a map
   * Map<String, Integer> ages = Map.of("Alice", 25, "Bob", 30);
   * Traversal<Map<String, Integer>, Integer> allValues = Traversals.forMapValues();
   *
   * Map<String, Integer> incremented = Traversals.modify(
   *     allValues,
   *     age -> age + 1,
   *     ages
   * );
   * // incremented = Map.of("Alice", 26, "Bob", 31)
   *
   * // Get all values
   * List<Integer> values = Traversals.getAll(allValues, ages);
   * // values = [25, 30] (order depends on map iteration order)
   * }</pre>
   *
   * @param <K> The type of the map's keys.
   * @param <V> The type of the map's values.
   * @return A {@code Traversal} for all map values.
   */
  public static <K, V> Traversal<Map<K, V>, V> forMapValues() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Map<K, V>> modifyF(
          final Function<V, Kind<F, V>> f,
          final Map<K, V> source,
          final Applicative<F> applicative) {
        return traverseMapValues(source, f, applicative);
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
  public static <F extends WitnessArity<TypeArity.Unary>, A, B> Kind<F, List<B>> traverseList(
      final List<A> list, final Function<A, Kind<F, B>> f, final Applicative<F> applicative) {

    final List<Kind<F, B>> listOfEffects = list.stream().map(f).collect(Collectors.toList());
    final Kind<ListKind.Witness, Kind<F, B>> effectsAsKind =
        ListKindHelper.LIST.widen(listOfEffects);
    final var effectOfKindList = ListTraverse.INSTANCE.sequenceA(applicative, effectsAsKind);

    return applicative.map(ListKindHelper.LIST::narrow, effectOfKindList);
  }

  /**
   * Applies an effectful function to the value in an {@link Optional} if present, collecting the
   * result in a single effect.
   *
   * <p>This is a direct application of the {@code traverse} operation for {@code Optional},
   * provided here as a static helper for convenience. It "flips" an {@code Optional<A>} and a
   * function {@code A -> F<B>} into a single {@code F<Optional<B>>}.
   *
   * <p>If the optional is empty, the result is {@code applicative.of(Optional.empty())}. If the
   * optional contains a value, the function is applied to produce {@code F<B>}, which is then
   * mapped to {@code F<Optional<B>>}.
   *
   * @param optional The source optional to traverse.
   * @param f The effectful function to apply to the value if present.
   * @param applicative The {@code Applicative} instance for the effect {@code F}.
   * @param <F> The higher-kinded type witness of the applicative effect.
   * @param <A> The element type of the source optional.
   * @param <B> The element type of the resulting optional.
   * @return A {@code Kind<F, Optional<B>>}, representing the result within the applicative context.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B>
      Kind<F, Optional<B>> traverseOptional(
          final Optional<A> optional,
          final Function<? super A, ? extends Kind<F, ? extends B>> f,
          final Applicative<F> applicative) {

    final Kind<OptionalKind.Witness, A> optionalKind = OptionalKindHelper.OPTIONAL.widen(optional);
    final Kind<F, Kind<OptionalKind.Witness, B>> traversed =
        OptionalTraverse.INSTANCE.traverse(applicative, f, optionalKind);

    return applicative.map(OptionalKindHelper.OPTIONAL::narrow, traversed);
  }

  /**
   * Applies an effectful function to each value in a {@link Map}, preserving the keys and
   * collecting the results in a single effect.
   *
   * <p>This helper traverses all values in the map, applying the effectful function {@code f} to
   * each value while keeping the keys unchanged. The result is a {@code F<Map<K, W>>} where all
   * effects have been sequenced.
   *
   * <p>If the map is empty, returns {@code applicative.of(emptyMap)}.
   *
   * @param map The source map to traverse.
   * @param f The effectful function to apply to each value.
   * @param applicative The {@code Applicative} instance for the effect {@code F}.
   * @param <F> The higher-kinded type witness of the applicative effect.
   * @param <K> The type of the map keys.
   * @param <V> The type of the source map values.
   * @param <W> The type of the resulting map values.
   * @return A {@code Kind<F, Map<K, W>>}, representing the transformed map within the applicative
   *     context.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, K, V, W>
      Kind<F, Map<K, W>> traverseMapValues(
          final Map<K, V> map,
          final Function<? super V, ? extends Kind<F, ? extends W>> f,
          final Applicative<F> applicative) {

    if (map.isEmpty()) {
      return applicative.of(new HashMap<>());
    }

    Kind<F, Map<K, W>> result = applicative.of(new HashMap<>(map.size()));
    for (Map.Entry<K, V> entry : map.entrySet()) {
      @SuppressWarnings("unchecked")
      final Kind<F, W> newFValue = (Kind<F, W>) f.apply(entry.getValue());
      final K key = entry.getKey();
      result =
          applicative.map2(
              result,
              newFValue,
              (m, w) -> {
                final Map<K, W> updated = new HashMap<>(m);
                updated.put(key, w);
                return updated;
              });
    }
    return result;
  }

  /**
   * Applies an effectful function to each element of a {@link Set} and collects the results in a
   * single effect.
   *
   * <p>This is a direct application of the {@code traverse} operation for {@code Set}, provided
   * here as a static helper for convenience. It "flips" a {@code Set<A>} and a function {@code A ->
   * F<B>} into a single {@code F<Set<B>>}.
   *
   * <p>The resulting set uses a {@link LinkedHashSet} to preserve iteration order. Note that if the
   * function produces duplicate values, the set will contain only unique elements.
   *
   * @param set The source set to traverse.
   * @param f The effectful function to apply to each element.
   * @param applicative The {@code Applicative} instance for the effect {@code F}.
   * @param <F> The higher-kinded type witness of the applicative effect.
   * @param <A> The element type of the source set.
   * @param <B> The element type of the resulting set.
   * @return A {@code Kind<F, Set<B>>}, representing the collected results within the applicative
   *     context.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B> Kind<F, Set<B>> traverseSet(
      final Set<A> set,
      final Function<? super A, ? extends Kind<F, ? extends B>> f,
      final Applicative<F> applicative) {

    if (set.isEmpty()) {
      return applicative.of(new LinkedHashSet<>());
    }

    // Convert to list, traverse efficiently, then convert back to set
    @SuppressWarnings("unchecked")
    final Function<A, Kind<F, B>> fCast = (Function<A, Kind<F, B>>) f;
    Kind<F, List<B>> listResult = traverseList(new ArrayList<>(set), fCast, applicative);
    return applicative.map(LinkedHashSet::new, listResult);
  }

  /**
   * Applies an effectful function to each element of an array and collects the results in a single
   * effect.
   *
   * <p>This is a direct application of the {@code traverse} operation for arrays, provided here as
   * a static helper for convenience. It "flips" an {@code A[]} and a function {@code A -> F<B>}
   * into a single {@code F<B[]>}.
   *
   * <p>The resulting array has the same length as the source array, with each element transformed
   * by the function.
   *
   * @param array The source array to traverse.
   * @param f The effectful function to apply to each element.
   * @param applicative The {@code Applicative} instance for the effect {@code F}.
   * @param <F> The higher-kinded type witness of the applicative effect.
   * @param <A> The element type of the source array.
   * @param <B> The element type of the resulting array.
   * @return A {@code Kind<F, List<B>>}, representing the collected results within the applicative
   *     context. Note: Returns a List because creating generic arrays at runtime is not type-safe
   *     in Java.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B> Kind<F, List<B>> traverseArray(
      final A[] array,
      final Function<? super A, ? extends Kind<F, ? extends B>> f,
      final Applicative<F> applicative) {

    if (array.length == 0) {
      return applicative.of(new ArrayList<>());
    }

    // Convert to list and traverse efficiently
    @SuppressWarnings("unchecked")
    final Function<A, Kind<F, B>> fCast = (Function<A, Kind<F, B>>) f;
    return traverseList(Arrays.asList(array), fCast, applicative);
  }

  /**
   * Applies an effectful function to both elements of a {@link Tuple2} where both elements are of
   * the same type, collecting the results in a single effect.
   *
   * <p>This helper traverses both positions in the tuple, applying the effectful function {@code f}
   * to each element. The result is {@code F<Tuple2<B, B>>} where both effects have been sequenced.
   *
   * @param tuple The source tuple to traverse.
   * @param f The effectful function to apply to each element.
   * @param applicative The {@code Applicative} instance for the effect {@code F}.
   * @param <F> The higher-kinded type witness of the applicative effect.
   * @param <A> The element type of the source tuple.
   * @param <B> The element type of the resulting tuple.
   * @return A {@code Kind<F, Tuple2<B, B>>}, representing the transformed tuple within the
   *     applicative context.
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B>
      Kind<F, Tuple2<B, B>> traverseTuple2Both(
          final Tuple2<A, A> tuple,
          final Function<? super A, ? extends Kind<F, ? extends B>> f,
          final Applicative<F> applicative) {

    @SuppressWarnings("unchecked")
    final Kind<F, B> first = (Kind<F, B>) f.apply(tuple._1());
    @SuppressWarnings("unchecked")
    final Kind<F, B> second = (Kind<F, B>) f.apply(tuple._2());

    return applicative.map2(first, second, Tuple2::new);
  }

  /**
   * Traverse a list with speculative execution for each element. Both branches are visible upfront,
   * allowing selective implementations to potentially execute them in parallel.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Try cache first, API as fallback - both can start immediately
   * List<UserId> ids = List.of(id1, id2, id3);
   * Kind<F, List<User>> users = Traversals.speculativeTraverseList(
   *   ids,
   *   id -> cacheHas(id),           // Predicate
   *   id -> fetchFromCache(id),     // Fast path
   *   id -> fetchFromAPI(id),       // Slow path
   *   selective
   * );
   * }</pre>
   *
   * @param list The list to traverse
   * @param predicate Determines which branch to take for each element
   * @param thenBranch Function to apply when predicate is true
   * @param elseBranch Function to apply when predicate is false
   * @param selective The Selective instance
   * @param <F> The effect type
   * @param <A> The element type of the input list
   * @param <B> The element type of the output list
   * @return The transformed list wrapped in the effect
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B>
      Kind<F, List<B>> speculativeTraverseList(
          final List<A> list,
          final Predicate<? super A> predicate,
          final Function<? super A, ? extends Kind<F, B>> thenBranch,
          final Function<? super A, ? extends Kind<F, B>> elseBranch,
          final Selective<F> selective) {
    // Wrap each element in a selective conditional
    final Function<A, Kind<F, B>> selectiveF =
        a ->
            selective.ifS(
                selective.of(predicate.test(a)), thenBranch.apply(a), elseBranch.apply(a));

    // Use the standard traverse implementation
    return traverseList(list, selectiveF, selective);
  }

  /**
   * Traverse a list, applying a function only to elements that match a predicate. Elements that
   * don't match are left unchanged.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Only validate emails that look valid
   * List<String> emails = List.of("valid@example.com", "invalid", "another@example.com");
   * Kind<F, List<String>> result = Traversals.traverseListIf(
   *   emails,
   *   email -> email.contains("@"),
   *   email -> validateEmailInDatabase(email),
   *   selective
   * );
   * }</pre>
   *
   * @param list The list to traverse
   * @param predicate Determines which elements to process
   * @param f Function to apply to matching elements
   * @param selective The Selective instance
   * @param <F> The effect type
   * @param <A> The element type
   * @return The transformed list wrapped in the effect
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> Kind<F, List<A>> traverseListIf(
      final List<A> list,
      final Predicate<? super A> predicate,
      final Function<? super A, ? extends Kind<F, A>> f,
      final Selective<F> selective) {
    final Function<A, Kind<F, A>> conditionalF =
        a -> {
          if (predicate.test(a)) {
            return f.apply(a);
          } else {
            return selective.of(a);
          }
        };

    return traverseList(list, conditionalF, selective);
  }

  /**
   * Traverse a list, stopping when a predicate is met. Elements after the stopping point are left
   * unchanged.
   *
   * <p>This implementation uses the State monad to track whether we've stopped, making it purely
   * functional, referentially transparent, and thread-safe.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Process items until we hit an error
   * List<Task> tasks = List.of(task1, task2, task3);
   * Kind<F, List<Task>> result = Traversals.traverseListUntil(
   *   tasks,
   *   task -> task.hasError(),
   *   task -> processTask(task),
   *   selective
   * );
   * }</pre>
   *
   * @param list The list to traverse
   * @param stopCondition Predicate that triggers stopping
   * @param f Function to apply to elements before stopping
   * @param selective The Selective instance
   * @param <F> The effect type
   * @param <A> The element type
   * @return The transformed list wrapped in the effect
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> Kind<F, List<A>> traverseListUntil(
      final List<A> list,
      final Predicate<? super A> stopCondition,
      final Function<? super A, ? extends Kind<F, A>> f,
      final Selective<F> selective) {

    // Create a stateful function that tracks whether we've stopped
    final Function<A, State<Boolean, Kind<F, A>>> statefulF =
        a ->
            State.<Boolean, Boolean>inspect(stopped -> stopped || stopCondition.test(a))
                .flatMap(
                    shouldStop -> {
                      if (shouldStop) {
                        return State.set(true).map(_ -> selective.of(a));
                      } else {
                        return State.pure(f.apply(a));
                      }
                    });

    // Map each element through the stateful function
    final List<State<Boolean, Kind<F, A>>> statefulComputations =
        list.stream().map(statefulF).collect(Collectors.toList());

    // Sequence the stateful computations
    final State<Boolean, List<Kind<F, A>>> sequencedState = sequenceStateList(statefulComputations);

    // Run the state computation (initial state: false = not stopped)
    final StateTuple<Boolean, List<Kind<F, A>>> result = sequencedState.run(false);

    // Now sequence the effects within F
    final List<Kind<F, A>> effectsList = result.value();
    return traverseList(effectsList, Function.identity(), selective);
  }

  /**
   * Sequences a list of State computations into a State of a list.
   *
   * <p>This implementation uses {@link Trampoline} for stack safety whilst maintaining functional
   * purity. The previous imperative loop implementation has been replaced with a tail-recursive
   * algorithm executed via Trampoline, preventing {@code StackOverflowError} with arbitrarily large
   * lists whilst preserving referential transparency.
   *
   * <p>The naive functional approach using {@code Stream.reduce} with {@code flatMap} creates
   * deeply nested closures that cause {@code StackOverflowError} with large lists (>1000 elements).
   * Trampoline eliminates this issue by converting recursion into iteration.
   *
   * @param states List of State computations to sequence
   * @param <S> The state type
   * @param <A> The value type
   * @return A State computation that produces a list of all values
   */
  static <S, A> State<S, List<A>> sequenceStateList(final List<State<S, A>> states) {
    return State.of(
        initialState -> {
          final List<A> resultList = new ArrayList<>(states.size());
          final Trampoline<StateTuple<S, List<A>>> trampoline =
              sequenceStateListTrampoline(states, 0, initialState, resultList);
          return trampoline.run();
        });
  }

  /**
   * Tail-recursive helper for sequencing State computations using Trampoline.
   *
   * <p>This method processes the list of State computations recursively, building up the result
   * list and threading state through. Each recursive call is wrapped in {@link Trampoline#defer} to
   * ensure stack safety.
   *
   * @param states The list of State computations to sequence
   * @param index The current index in the list
   * @param currentState The current state value
   * @param resultList The accumulated list of results (mutated for efficiency)
   * @param <S> The state type
   * @param <A> The value type
   * @return A Trampoline that will produce the final StateTuple when run
   */
  private static <S, A> Trampoline<StateTuple<S, List<A>>> sequenceStateListTrampoline(
      final List<State<S, A>> states,
      final int index,
      final S currentState,
      final List<A> resultList) {
    // Base case: processed all states
    if (index >= states.size()) {
      return Trampoline.done(new StateTuple<>(resultList, currentState));
    }

    // Recursive case: process next state
    final StateTuple<S, A> result = states.get(index).run(currentState);
    resultList.add(result.value());

    return Trampoline.defer(
        () -> sequenceStateListTrampoline(states, index + 1, result.state(), resultList));
  }

  /**
   * Converts a {@link Traversal} into a {@link Lens} focusing on a list of all traversed elements.
   *
   * <p>This powerful combinator allows you to extract all focused elements as a list, manipulate
   * that list using standard list operations (sorting, reversing, filtering, etc.), and write the
   * results back to the structure.
   *
   * <p>The getter extracts all focused elements into a mutable {@link List}. The setter distributes
   * the list elements back to the original positions. If the new list has fewer elements than the
   * original, the remaining positions retain their original values. If the new list has more
   * elements, the extra elements are ignored.
   *
   * <p>Example use cases:
   *
   * <ul>
   *   <li>Sorting focused elements
   *   <li>Reversing focused elements
   *   <li>Removing duplicates
   *   <li>Applying list algorithms to traversal focuses
   * </ul>
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Sort all names in a list of users
   * Traversal<List<User>, String> userNames = Traversals.<User>forList()
   *     .andThen(userNameLens.asTraversal());
   * Lens<List<User>, List<String>> namesLens = Traversals.partsOf(userNames);
   *
   * List<User> users = List.of(
   *     new User("Charlie", 30),
   *     new User("Alice", 25),
   *     new User("Bob", 35)
   * );
   *
   * // Get all names as a list
   * List<String> names = namesLens.get(users);  // ["Charlie", "Alice", "Bob"]
   *
   * // Sort the names
   * List<String> sorted = new ArrayList<>(names);
   * Collections.sort(sorted);
   *
   * // Set them back
   * List<User> result = namesLens.set(sorted, users);
   * // Result: [User("Alice", 30), User("Bob", 25), User("Charlie", 35)]
   * }</pre>
   *
   * @param traversal The traversal to convert into a lens
   * @param <S> The type of the source structure
   * @param <A> The type of the focused elements
   * @return A lens focusing on a list of all traversed elements
   */
  public static <S, A> Lens<S, List<A>> partsOf(final Traversal<S, A> traversal) {
    return Lens.of(
        // Getter: collect all elements into a mutable list
        source -> getAll(traversal, source),
        // Setter: distribute list elements back to original positions
        (source, newList) -> {
          final AtomicInteger index = new AtomicInteger(0);
          return modify(
              traversal,
              oldValue -> {
                int i = index.getAndIncrement();
                return i < newList.size() ? newList.get(i) : oldValue;
              },
              source);
        });
  }

  /**
   * Sorts the elements focused by a traversal using their natural ordering.
   *
   * <p>This is a convenience method that uses {@link #partsOf} to extract all focused elements,
   * sort them, and write them back.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<List<User>, Integer> userAges = Traversals.<User>forList()
   *     .andThen(userAgeLens.asTraversal());
   *
   * List<User> users = List.of(
   *     new User("Alice", 30),
   *     new User("Bob", 25),
   *     new User("Charlie", 35)
   * );
   *
   * List<User> sorted = Traversals.sorted(userAges, users);
   * // Result: [User("Alice", 25), User("Bob", 30), User("Charlie", 35)]
   * // Note: ages are sorted, not users themselves
   * }</pre>
   *
   * @param traversal The traversal focusing on elements to sort
   * @param source The source structure
   * @param <S> The type of the source structure
   * @param <A> The type of the focused elements (must be Comparable)
   * @return A new structure with focused elements sorted
   */
  public static <S, A extends Comparable<? super A>> @Nullable S sorted(
      final Traversal<S, A> traversal, final S source) {
    final Lens<S, List<A>> partsLens = partsOf(traversal);
    final List<A> parts = new ArrayList<>(partsLens.get(source));
    Collections.sort(parts);
    return partsLens.set(parts, source);
  }

  /**
   * Sorts the elements focused by a traversal using a custom comparator.
   *
   * <p>This is a convenience method that uses {@link #partsOf} to extract all focused elements,
   * sort them with the provided comparator, and write them back.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<List<User>, String> userNames = Traversals.<User>forList()
   *     .andThen(userNameLens.asTraversal());
   *
   * List<User> users = List.of(
   *     new User("charlie", 30),
   *     new User("Alice", 25),
   *     new User("bob", 35)
   * );
   *
   * // Sort names case-insensitively
   * List<User> sorted = Traversals.sorted(userNames, String.CASE_INSENSITIVE_ORDER, users);
   * // Result: [User("Alice", 30), User("bob", 25), User("charlie", 35)]
   * }</pre>
   *
   * @param traversal The traversal focusing on elements to sort
   * @param comparator The comparator to use for sorting
   * @param source The source structure
   * @param <S> The type of the source structure
   * @param <A> The type of the focused elements
   * @return A new structure with focused elements sorted
   */
  public static <S, A> @Nullable S sorted(
      final Traversal<S, A> traversal, final Comparator<? super A> comparator, final S source) {
    final Lens<S, List<A>> partsLens = partsOf(traversal);
    final List<A> parts = new ArrayList<>(partsLens.get(source));
    parts.sort(comparator);
    return partsLens.set(parts, source);
  }

  /**
   * Reverses the order of elements focused by a traversal.
   *
   * <p>This is a convenience method that uses {@link #partsOf} to extract all focused elements,
   * reverse them, and write them back.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<List<User>, String> userNames = Traversals.<User>forList()
   *     .andThen(userNameLens.asTraversal());
   *
   * List<User> users = List.of(
   *     new User("Alice", 25),
   *     new User("Bob", 30),
   *     new User("Charlie", 35)
   * );
   *
   * List<User> reversed = Traversals.reversed(userNames, users);
   * // Result: [User("Charlie", 25), User("Bob", 30), User("Alice", 35)]
   * // Note: names are reversed, not users themselves
   * }</pre>
   *
   * @param traversal The traversal focusing on elements to reverse
   * @param source The source structure
   * @param <S> The type of the source structure
   * @param <A> The type of the focused elements
   * @return A new structure with focused elements reversed
   */
  public static <S, A> @Nullable S reversed(final Traversal<S, A> traversal, final S source) {
    final Lens<S, List<A>> partsLens = partsOf(traversal);
    final List<A> parts = new ArrayList<>(partsLens.get(source));
    Collections.reverse(parts);
    return partsLens.set(parts, source);
  }

  /**
   * Removes duplicate elements from the focus of a traversal, preserving first occurrences.
   *
   * <p>This is a convenience method that uses {@link #partsOf} to extract all focused elements,
   * remove duplicates while preserving order, and write them back. Since the resulting list may
   * have fewer elements than the original, any positions beyond the deduplicated list retain their
   * original values.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<List<User>, String> userNames = Traversals.<User>forList()
   *     .andThen(userNameLens.asTraversal());
   *
   * List<User> users = List.of(
   *     new User("Alice", 25),
   *     new User("Bob", 30),
   *     new User("Alice", 35),
   *     new User("Charlie", 40)
   * );
   *
   * List<User> distinct = Traversals.distinct(userNames, users);
   * // Result: [User("Alice", 25), User("Bob", 30), User("Charlie", 35), User("Charlie", 40)]
   * // "Alice" appears only once, "Charlie" fills third position, fourth keeps original
   * }</pre>
   *
   * @param traversal The traversal focusing on elements to deduplicate
   * @param source The source structure
   * @param <S> The type of the source structure
   * @param <A> The type of the focused elements
   * @return A new structure with duplicate focused elements removed
   */
  public static <S, A> @Nullable S distinct(final Traversal<S, A> traversal, final S source) {
    final Lens<S, List<A>> partsLens = partsOf(traversal);
    final List<A> parts = partsLens.get(source);
    // Use LinkedHashSet to preserve order while removing duplicates
    final List<A> distinctParts = new ArrayList<>(new LinkedHashSet<>(parts));
    return partsLens.set(distinctParts, source);
  }
}
