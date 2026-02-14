// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Affine;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A final utility class providing common {@link Affine} instances and helper methods.
 *
 * <p>This class contains factory methods for creating affines that work with common Java types like
 * {@link Optional}, nullable fields, and collections with optional access patterns.
 *
 * <p>An Affine focuses on <b>zero or one</b> element within a structure. Unlike a Prism which can
 * {@code build} a new structure from scratch, an Affine can only {@code set} a value within an
 * existing structure. This makes Affine ideal for optional fields within product types.
 *
 * <h2>When to use Affine vs Prism</h2>
 *
 * <ul>
 *   <li><b>Affine:</b> Optional fields in records, nullable properties, conditional access
 *   <li><b>Prism:</b> Sum type variants (sealed interfaces), type-safe instanceof checks
 * </ul>
 */
@NullMarked
public final class Affines {
  /** Private constructor to prevent instantiation. */
  private Affines() {}

  /**
   * Creates an affine for accessing the value inside an {@link Optional} field.
   *
   * <p>This is one of the most common uses of Affine: accessing optional fields in records or
   * classes. The affine focuses on the contained value when present, and returns empty when the
   * Optional is empty.
   *
   * <p>Example:
   *
   * <pre>{@code
   * record User(String name, Optional<String> email) {}
   *
   * Affine<Optional<String>, String> someAffine = Affines.some();
   *
   * Optional<String> present = Optional.of("alice@example.com");
   * Optional<String> result = someAffine.getOptional(present);  // Optional.of("alice@example.com")
   *
   * Optional<String> empty = Optional.empty();
   * Optional<String> noMatch = someAffine.getOptional(empty);  // Optional.empty()
   *
   * // Setting always wraps in Optional.of()
   * Optional<String> updated = someAffine.set("new@example.com", present);
   * // Optional.of("new@example.com")
   * }</pre>
   *
   * @param <A> The type of the value inside the {@code Optional}.
   * @return An affine focusing on present values in an {@code Optional}.
   */
  public static <A> Affine<Optional<A>, A> some() {
    return Affine.of(Function.identity(), (opt, value) -> Optional.of(value));
  }

  /**
   * Creates an affine for accessing the value inside an {@link Optional} field, with removal
   * support.
   *
   * <p>This variant supports the {@link Affine#remove} operation, allowing you to clear an Optional
   * field to empty.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Affine<Optional<String>, String> someAffine = Affines.someWithRemove();
   *
   * Optional<String> present = Optional.of("alice@example.com");
   *
   * // Remove clears the Optional
   * Optional<String> cleared = someAffine.remove(present);  // Optional.empty()
   * }</pre>
   *
   * @param <A> The type of the value inside the {@code Optional}.
   * @return An affine focusing on present values in an {@code Optional}, with removal support.
   */
  public static <A> Affine<Optional<A>, A> someWithRemove() {
    return Affine.of(
        Function.identity(), (opt, value) -> Optional.of(value), opt -> Optional.empty());
  }

  /**
   * Creates an affine for accessing the value inside a {@link Maybe} when it is {@code Just}.
   *
   * <p>Similar to {@link #some()} but for the higher-kinded-j {@code Maybe} type.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Affine<Maybe<String>, String> justAffine = Affines.just();
   *
   * Maybe<String> present = Maybe.just("hello");
   * Optional<String> result = justAffine.getOptional(present);  // Optional.of("hello")
   *
   * Maybe<String> nothing = Maybe.nothing();
   * Optional<String> noMatch = justAffine.getOptional(nothing);  // Optional.empty()
   *
   * Maybe<String> updated = justAffine.set("world", present);  // Maybe.just("world")
   * }</pre>
   *
   * @param <A> The type of the value inside the {@code Maybe}.
   * @return An affine focusing on {@code Just} values in a {@code Maybe}.
   */
  public static <A> Affine<Maybe<A>, A> just() {
    return Affine.of(
        maybe -> maybe.isJust() ? Optional.of(maybe.get()) : Optional.empty(),
        (maybe, value) -> Maybe.just(value));
  }

  /**
   * Creates an affine for accessing nullable fields.
   *
   * <p>This is useful for working with legacy code or APIs that use null to represent absent
   * values. The affine wraps null checks in Optional semantics.
   *
   * <p><b>Warning:</b> The setter will set the value directly (including potentially null values
   * passed to it). Use with care.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // For a record with a nullable field (legacy pattern)
   * record LegacyUser(String name, @Nullable String nickname) {}
   *
   * // Create an affine using nullable() with appropriate getter/setter
   * Affine<LegacyUser, String> nicknameAffine = Affine.of(
   *     user -> Optional.ofNullable(user.nickname()),
   *     (user, nickname) -> new LegacyUser(user.name(), nickname)
   * );
   *
   * LegacyUser user = new LegacyUser("Alice", null);
   * Optional<String> result = nicknameAffine.getOptional(user);  // Optional.empty()
   *
   * LegacyUser updated = nicknameAffine.set("Ally", user);
   * // LegacyUser[name=Alice, nickname=Ally]
   * }</pre>
   *
   * @param <A> The type of the nullable value.
   * @return An affine that treats null as absent.
   */
  public static <A> Affine<@Nullable A, A> nullable() {
    return Affine.of(Optional::ofNullable, (ignored, value) -> value);
  }

  /**
   * Creates an affine for accessing the first element of a {@link List}.
   *
   * <p>This affine focuses on the first element when the list is non-empty. Setting replaces the
   * first element whilst preserving the rest of the list.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Affine<List<String>, String> headAffine = Affines.listHead();
   *
   * List<String> list = List.of("first", "second", "third");
   * Optional<String> result = headAffine.getOptional(list);  // Optional.of("first")
   *
   * List<String> empty = List.of();
   * Optional<String> noMatch = headAffine.getOptional(empty);  // Optional.empty()
   *
   * // Setting replaces first element, keeps rest
   * List<String> updated = headAffine.set("NEW", list);
   * // [NEW, second, third]
   * }</pre>
   *
   * @param <A> The element type of the list.
   * @return An affine focusing on the first element of a list.
   */
  public static <A> Affine<List<A>, A> listHead() {
    return Affine.of(
        list -> list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst()),
        (list, value) -> {
          if (list.isEmpty()) {
            return List.of(value);
          }
          List<A> result = new ArrayList<>(list);
          result.set(0, value);
          return List.copyOf(result);
        });
  }

  /**
   * Creates an affine for accessing the last element of a {@link List}.
   *
   * <p>This affine focuses on the last element when the list is non-empty. Setting replaces the
   * last element whilst preserving the rest of the list.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Affine<List<String>, String> lastAffine = Affines.listLast();
   *
   * List<String> list = List.of("first", "second", "third");
   * Optional<String> result = lastAffine.getOptional(list);  // Optional.of("third")
   *
   * // Setting replaces last element, keeps rest
   * List<String> updated = lastAffine.set("NEW", list);
   * // [first, second, NEW]
   * }</pre>
   *
   * @param <A> The element type of the list.
   * @return An affine focusing on the last element of a list.
   */
  public static <A> Affine<List<A>, A> listLast() {
    return Affine.of(
        list -> list.isEmpty() ? Optional.empty() : Optional.of(list.getLast()),
        (list, value) -> {
          if (list.isEmpty()) {
            return List.of(value);
          }
          List<A> result = new ArrayList<>(list);
          result.set(result.size() - 1, value);
          return List.copyOf(result);
        });
  }

  /**
   * Creates an affine for accessing an element at a specific index in a {@link List}.
   *
   * <p>This affine focuses on the element at the given index when it exists. Setting replaces that
   * element whilst preserving the rest of the list.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Affine<List<String>, String> secondAffine = Affines.listAt(1);
   *
   * List<String> list = List.of("first", "second", "third");
   * Optional<String> result = secondAffine.getOptional(list);  // Optional.of("second")
   *
   * List<String> shortList = List.of("only");
   * Optional<String> noMatch = secondAffine.getOptional(shortList);  // Optional.empty()
   *
   * // Setting replaces element at index, keeps rest
   * List<String> updated = secondAffine.set("NEW", list);
   * // [first, NEW, third]
   * }</pre>
   *
   * @param index The zero-based index to focus on.
   * @param <A> The element type of the list.
   * @return An affine focusing on the element at the specified index.
   */
  public static <A> Affine<List<A>, A> listAt(int index) {
    return Affine.of(
        list ->
            (index >= 0 && index < list.size()) ? Optional.of(list.get(index)) : Optional.empty(),
        (list, value) -> {
          if (index < 0 || index >= list.size()) {
            return list;
          }
          List<A> result = new ArrayList<>(list);
          result.set(index, value);
          return List.copyOf(result);
        });
  }

  // ===== Utility Methods =====

  /**
   * Extracts all focused values from a structure using an affine.
   *
   * <p>Since an affine focuses on zero or one element, this returns either an empty list or a
   * singleton list.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Affine<Optional<String>, String> someAffine = Affines.some();
   *
   * List<String> present = Affines.getAll(someAffine, Optional.of("hello"));  // [hello]
   * List<String> absent = Affines.getAll(someAffine, Optional.empty());       // []
   * }</pre>
   *
   * @param affine The affine to use for extraction.
   * @param source The source structure.
   * @param <S> The source type.
   * @param <A> The focus type.
   * @return A list containing zero or one element.
   */
  public static <S, A> List<A> getAll(Affine<S, A> affine, S source) {
    return affine.getOptional(source).map(List::of).orElse(List.of());
  }

  /**
   * Modifies the focused value using a function.
   *
   * <p>This is a static helper that delegates to {@link Affine#modify}. Provided for consistency
   * with {@link Traversals#modify}.
   *
   * @param affine The affine to use.
   * @param modifier The function to apply.
   * @param source The source structure.
   * @param <S> The source type.
   * @param <A> The focus type.
   * @return The modified structure.
   */
  public static <S, A> S modify(Affine<S, A> affine, Function<A, A> modifier, S source) {
    return affine.modify(modifier, source);
  }

  /**
   * Checks if the affine focuses on a value in the given structure.
   *
   * <p>This is a static helper that delegates to {@link Affine#matches}.
   *
   * @param affine The affine to use.
   * @param source The source structure.
   * @param <S> The source type.
   * @param <A> The focus type.
   * @return {@code true} if a value is present, {@code false} otherwise.
   */
  public static <S, A> boolean matches(Affine<S, A> affine, S source) {
    return affine.matches(source);
  }
}
