// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.extensions;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Fold;
import org.jspecify.annotations.NullMarked;

/**
 * Extension utilities for {@link Fold} that integrate with higher-kinded-j core types.
 *
 * <p>This class provides {@link Maybe}-based alternatives to {@link Fold}'s {@link Optional}-
 * returning methods, ensuring consistency with higher-kinded-j's functional programming patterns.
 *
 * <h2>Why Maybe Instead of Optional?</h2>
 *
 * <p>While {@code Optional} is part of the Java standard library, {@code Maybe} provides better
 * integration with higher-kinded-j's type class system and functional patterns. Using {@code Maybe}
 * consistently across optics operations provides:
 *
 * <ul>
 *   <li>Seamless integration with other higher-kinded-j core types ({@code Either}, {@code
 *       Validated})
 *   <li>Participation in the HKT (Higher-Kinded Type) framework
 *   <li>More expressive method names ({@code isJust}/{@code isNothing} vs {@code isPresent}/ {@code
 *       isEmpty})
 *   <li>Consistent functional programming style throughout the codebase
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.optics.extensions.FoldExtensions.*;
 *
 * // Define a fold over a list of users
 * record User(String name, int age) {}
 * record Team(List<User> members) {}
 *
 * Fold<Team, User> membersFold = ...;
 * Team team = new Team(List.of(
 *     new User("Alice", 30),
 *     new User("Bob", 25),
 *     new User("Charlie", 35)
 * ));
 *
 * // Get first member as Maybe
 * Maybe<User> firstMember = previewMaybe(membersFold, team);
 * firstMember.ifJust(user -> System.out.println("First: " + user.name()));
 *
 * // Find member matching predicate
 * Maybe<User> adult = findMaybe(membersFold, user -> user.age() >= 30, team);
 * String name = adult.map(User::name).orElse("No adults found");
 *
 * // Get all members wrapped in Maybe (distinguishes empty from nothing)
 * Maybe<List<User>> allMembers = getAllMaybe(membersFold, team);
 * allMembers.ifJust(members -> System.out.println("Count: " + members.size()));
 * }</pre>
 *
 * <h2>Method Correspondence</h2>
 *
 * <table>
 *   <caption>Fold methods and their Maybe-based equivalents</caption>
 *   <thead>
 *     <tr>
 *       <th>Fold Method</th>
 *       <th>FoldExtensions Method</th>
 *       <th>Return Type</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>{@link Fold#preview(Object)}</td>
 *       <td>{@link #previewMaybe(Fold, Object)}</td>
 *       <td>{@code Maybe<A>}</td>
 *     </tr>
 *     <tr>
 *       <td>{@link Fold#find(Predicate, Object)}</td>
 *       <td>{@link #findMaybe(Fold, Predicate, Object)}</td>
 *       <td>{@code Maybe<A>}</td>
 *     </tr>
 *     <tr>
 *       <td>{@link Fold#getAll(Object)}</td>
 *       <td>{@link #getAllMaybe(Fold, Object)}</td>
 *       <td>{@code Maybe<List<A>>}</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * @see Fold
 * @see Maybe
 * @since 1.0
 */
@NullMarked
public final class FoldExtensions {

  private FoldExtensions() {
    throw new UnsupportedOperationException("Utility class - do not instantiate");
  }

  /**
   * Returns the first focused part as {@link Maybe}.
   *
   * <p>This is a {@code Maybe}-based alternative to {@link Fold#preview(Object)}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Maybe<User> firstUser = previewMaybe(userFold, team);
   * firstUser.ifJust(user -> System.out.println("Found: " + user));
   *
   * // Chain with other Maybe operations
   * Maybe<String> userName = previewMaybe(userFold, team)
   *     .map(User::name)
   *     .map(String::toUpperCase);
   * }</pre>
   *
   * @param fold The fold to use for focusing on parts of the structure
   * @param source The source structure to query
   * @param <S> The source type
   * @param <A> The focused value type
   * @return {@code Maybe.just(value)} if a focus exists, {@code Maybe.nothing()} otherwise
   */
  public static <S, A> Maybe<A> previewMaybe(Fold<S, A> fold, S source) {
    return Maybe.fromOptional(fold.preview(source));
  }

  /**
   * Finds the first focused part matching the given predicate as {@link Maybe}.
   *
   * <p>This is a {@code Maybe}-based alternative to {@link Fold#find(Predicate, Object)}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Maybe<Item> expensiveItem = findMaybe(
   *     itemFold,
   *     item -> item.price() > 100,
   *     order
   * );
   *
   * expensiveItem.fold(
   *     () -> System.out.println("No expensive items"),
   *     item -> applyDiscount(item)
   * );
   * }</pre>
   *
   * @param fold The fold to use for focusing on parts of the structure
   * @param predicate The predicate to test each focused part against
   * @param source The source structure to query
   * @param <S> The source type
   * @param <A> The focused value type
   * @return {@code Maybe.just(value)} if a matching focus exists, {@code Maybe.nothing()} otherwise
   */
  public static <S, A> Maybe<A> findMaybe(
      Fold<S, A> fold, Predicate<? super A> predicate, S source) {
    return Maybe.fromOptional(fold.find(predicate, source));
  }

  /**
   * Gets all focused parts wrapped in {@link Maybe}.
   *
   * <p>Returns {@code Maybe.just(list)} if the list is non-empty, {@code Maybe.nothing()} for empty
   * lists. This is useful when you want to distinguish between "no results" and "results exist".
   *
   * <p>This method differs from {@link Fold#getAll(Object)} by wrapping the result in {@code
   * Maybe}, allowing you to treat an empty result as a failure case that can be handled with {@code
   * Maybe}'s monadic operations.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Maybe<List<String>> names = getAllMaybe(nameFold, team);
   *
   * names.fold(
   *     () -> System.out.println("No names found"),
   *     list -> System.out.println("Found " + list.size() + " names")
   * );
   *
   * // Chain with Maybe operations
   * Maybe<Integer> totalLength = getAllMaybe(nameFold, team)
   *     .map(list -> list.stream()
   *         .mapToInt(String::length)
   *         .sum());
   * }</pre>
   *
   * <h3>When to Use This vs {@code getAll}</h3>
   *
   * <ul>
   *   <li>Use {@code getAllMaybe} when an empty result should be treated as a "nothing" case that
   *       can be chained with other {@code Maybe} operations
   *   <li>Use {@link Fold#getAll(Object)} when you need the list directly and will handle emptiness
   *       with {@code List.isEmpty()}
   * </ul>
   *
   * @param fold The fold to use for focusing on parts of the structure
   * @param source The source structure to query
   * @param <S> The source type
   * @param <A> The focused value type
   * @return {@code Maybe.just(list)} if the list is non-empty, {@code Maybe.nothing()} if the list
   *     is empty
   */
  public static <S, A> Maybe<List<A>> getAllMaybe(Fold<S, A> fold, S source) {
    List<A> all = fold.getAll(source);
    return all.isEmpty() ? Maybe.nothing() : Maybe.just(all);
  }
}
