// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateTuple;
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
    Function<A, Kind<Id.Witness, A>> fId = a -> Id.of(f.apply(a));
    Kind<Id.Witness, S> resultInId = traversal.modifyF(fId, source, IdMonad.instance());
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
  public static <F, A, B> Kind<F, List<B>> speculativeTraverseList(
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
  public static <F, A> Kind<F, List<A>> traverseListIf(
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
  public static <F, A> Kind<F, List<A>> traverseListUntil(
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
   * Helper: sequences a list of State computations into a State of a list. This is analogous to
   * sequence for other monads.
   */
  private static <S, A> State<S, List<A>> sequenceStateList(final List<State<S, A>> states) {
    return states.stream()
        .reduce(
            State.pure(new ArrayList<A>()),
            (accState, elemState) ->
                accState.flatMap(
                    acc ->
                        elemState.map(
                            elem -> {
                              List<A> newList = new ArrayList<>(acc);
                              newList.add(elem);
                              return newList;
                            })),
            (s1, s2) ->
                s1.flatMap(
                    list1 ->
                        s2.map(
                            list2 -> {
                              List<A> combined = new ArrayList<>(list1);
                              combined.addAll(list2);
                              return combined;
                            })));
  }
}
