// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Prism;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A final utility class providing common {@link Prism} instances for standard types.
 *
 * <p>This class contains factory methods for creating prisms that work with common Java types like
 * {@link Optional}, {@link Either}, collections, and type hierarchies.
 */
@NullMarked
public final class Prisms {
  /** Private constructor to prevent instantiation. */
  private Prisms() {}

  /**
   * Creates a prism for {@link Optional} that focuses on the value when present.
   *
   * <p>This prism matches when the {@code Optional} contains a value and extracts it. Building from
   * a value wraps it in {@code Optional.of()}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<Optional<String>, String> somePrism = Prisms.some();
   *
   * Optional<String> present = Optional.of("hello");
   * Optional<String> result = somePrism.getOptional(present);  // Optional.of("hello")
   *
   * Optional<String> empty = Optional.empty();
   * Optional<String> noMatch = somePrism.getOptional(empty);  // Optional.empty()
   *
   * Optional<String> built = somePrism.build("world");  // Optional.of("world")
   * }</pre>
   *
   * @param <A> The type of the value inside the {@code Optional}.
   * @return A prism focusing on present values in an {@code Optional}.
   */
  public static <A> Prism<Optional<A>, A> some() {
    return Prism.of(Function.identity(), Optional::of);
  }

  /**
   * Creates a prism for {@link Either} that focuses on the {@link Either.Left} case.
   *
   * <p>This prism matches when the {@code Either} is a {@code Left} and extracts its value.
   * Building from a value wraps it in {@code Either.left()}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<Either<String, Integer>, String> leftPrism = Prisms.left();
   *
   * Either<String, Integer> leftValue = Either.left("error");
   * Optional<String> result = leftPrism.getOptional(leftValue);  // Optional.of("error")
   *
   * Either<String, Integer> rightValue = Either.right(42);
   * Optional<String> noMatch = leftPrism.getOptional(rightValue);  // Optional.empty()
   *
   * Either<String, Integer> built = leftPrism.build("failure");  // Either.left("failure")
   * }</pre>
   *
   * @param <L> The type of the {@code Left} value.
   * @param <R> The type of the {@code Right} value.
   * @return A prism focusing on the {@code Left} case of an {@code Either}.
   */
  public static <L, R> Prism<Either<L, R>, L> left() {
    return Prism.of(
        either -> either.isLeft() ? Optional.of(either.getLeft()) : Optional.empty(), Either::left);
  }

  /**
   * Creates a prism for {@link Either} that focuses on the {@link Either.Right} case.
   *
   * <p>This prism matches when the {@code Either} is a {@code Right} and extracts its value.
   * Building from a value wraps it in {@code Either.right()}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<Either<String, Integer>, Integer> rightPrism = Prisms.right();
   *
   * Either<String, Integer> rightValue = Either.right(42);
   * Optional<Integer> result = rightPrism.getOptional(rightValue);  // Optional.of(42)
   *
   * Either<String, Integer> leftValue = Either.left("error");
   * Optional<Integer> noMatch = rightPrism.getOptional(leftValue);  // Optional.empty()
   *
   * Either<String, Integer> built = rightPrism.build(100);  // Either.right(100)
   * }</pre>
   *
   * @param <L> The type of the {@code Left} value.
   * @param <R> The type of the {@code Right} value.
   * @return A prism focusing on the {@code Right} case of an {@code Either}.
   */
  public static <L, R> Prism<Either<L, R>, R> right() {
    return Prism.of(
        either -> either.isRight() ? Optional.of(either.getRight()) : Optional.empty(),
        Either::right);
  }

  /**
   * Creates a prism that only matches a specific value.
   *
   * <p>This prism is useful for pattern matching on constant values. It matches when the source is
   * equal to the expected value (using {@link Objects#equals}), and focuses on {@link Unit} since
   * there's no additional information to extract. Building always returns the expected value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<String, Unit> helloPrism = Prisms.only("hello");
   *
   * String matching = "hello";
   * Optional<Unit> result = helloPrism.getOptional(matching);  // Optional.of(Unit.INSTANCE)
   *
   * String notMatching = "world";
   * Optional<Unit> noMatch = helloPrism.getOptional(notMatching);  // Optional.empty()
   *
   * String built = helloPrism.build(Unit.INSTANCE);  // "hello"
   *
   * // Can be used for conditional logic
   * if (helloPrism.matches("hello")) {
   *   // Handle the specific case
   * }
   * }</pre>
   *
   * @param expected The expected value to match against.
   * @param <A> The type of the value.
   * @return A prism that matches only the specified value.
   */
  public static <A> Prism<A, Unit> only(A expected) {
    return Prism.of(
        actual -> Objects.equals(actual, expected) ? Optional.of(Unit.INSTANCE) : Optional.empty(),
        unit -> expected);
  }

  /**
   * Creates a prism that matches non-null values.
   *
   * <p>This prism filters out {@code null} values, focusing on non-null instances. It's useful for
   * safely working with potentially nullable data structures.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<String, String> notNullPrism = Prisms.notNull();
   *
   * String value = "hello";
   * Optional<String> result = notNullPrism.getOptional(value);  // Optional.of("hello")
   *
   * String nullValue = null;
   * Optional<String> noMatch = notNullPrism.getOptional(nullValue);  // Optional.empty()
   *
   * String built = notNullPrism.build("world");  // "world"
   * }</pre>
   *
   * @param <A> The type of the value.
   * @return A prism that matches non-null values.
   */
  public static <A> Prism<@Nullable A, A> notNull() {
    return Prism.of(Optional::ofNullable, a -> a);
  }

  /**
   * Creates a prism for safe type casting using {@code instanceof} checks.
   *
   * <p>This prism is useful for working with type hierarchies and polymorphic structures. It
   * matches when the source is an instance of the target class and performs a safe cast. Building
   * is an identity operation (the value is already of the target type).
   *
   * <p><b>Warning:</b> The build operation for this prism is limited—it can only build instances
   * that are already of type {@code A}. This is inherent to type-based prisms.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Working with Number hierarchy
   * Prism<Number, Integer> integerPrism = Prisms.instanceOf(Integer.class);
   *
   * Number intValue = Integer.valueOf(42);
   * Optional<Integer> result = integerPrism.getOptional(intValue);  // Optional.of(42)
   *
   * Number doubleValue = Double.valueOf(3.14);
   * Optional<Integer> noMatch = integerPrism.getOptional(doubleValue);  // Optional.empty()
   *
   * // Composing with other optics for type-safe navigation
   * sealed interface JsonValue permits JsonString, JsonNumber {}
   * record JsonString(String value) implements JsonValue {}
   * record JsonNumber(int value) implements JsonValue {}
   *
   * Prism<JsonValue, JsonString> stringPrism = Prisms.instanceOf(JsonString.class);
   * Lens<JsonString, String> valueLens = JsonStringLenses.value();
   *
   * Traversal<JsonValue, String> jsonStringValue =
   *     stringPrism.asTraversal().andThen(valueLens.asTraversal());
   * }</pre>
   *
   * @param targetClass The class to match against.
   * @param <S> The source type (supertype).
   * @param <A> The target type (subtype).
   * @return A prism for safe instanceof-based casting.
   */
  public static <S, A extends S> Prism<S, A> instanceOf(Class<A> targetClass) {
    return Prism.of(
        source ->
            targetClass.isInstance(source)
                ? Optional.of(targetClass.cast(source))
                : Optional.empty(),
        a -> a);
  }

  /**
   * Creates a prism for the first element of a {@link List}.
   *
   * <p>This prism matches non-empty lists and focuses on the first element. It does not modify the
   * list when building—it simply creates a singleton list with the given value.
   *
   * <p><b>Note:</b> The build operation creates a new list containing only the single element,
   * discarding any other elements that might have been in the original list.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<List<String>, String> headPrism = Prisms.listHead();
   *
   * List<String> list = List.of("first", "second", "third");
   * Optional<String> result = headPrism.getOptional(list);  // Optional.of("first")
   *
   * List<String> empty = List.of();
   * Optional<String> noMatch = headPrism.getOptional(empty);  // Optional.empty()
   *
   * List<String> built = headPrism.build("new");  // List.of("new")
   *
   * // Useful for modifying the first element
   * List<String> modified = headPrism.modify(String::toUpperCase, list);
   * // Returns List.of("FIRST") - note: other elements are lost due to build
   * }</pre>
   *
   * @param <A> The element type of the list.
   * @return A prism focusing on the first element of a list.
   */
  public static <A> Prism<List<A>, A> listHead() {
    return Prism.of(list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)), List::of);
  }

  /**
   * Creates a prism for accessing an element at a specific index in a {@link List}.
   *
   * <p>This prism matches when the list has an element at the specified index and focuses on that
   * element. The build operation is not supported for indexed prisms (it throws {@code
   * UnsupportedOperationException}) since there's no meaningful way to construct a complete list
   * from a single indexed element.
   *
   * <p><b>Usage:</b> This prism is primarily useful for reading and conditionally modifying
   * elements at specific positions. Avoid using the {@code build} method directly.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<List<String>, String> secondPrism = Prisms.listAt(1);
   *
   * List<String> list = List.of("first", "second", "third");
   * Optional<String> result = secondPrism.getOptional(list);  // Optional.of("second")
   *
   * List<String> short = List.of("only");
   * Optional<String> noMatch = secondPrism.getOptional(short);  // Optional.empty()
   *
   * // Checking if index exists
   * boolean hasSecond = secondPrism.matches(list);  // true
   * }</pre>
   *
   * @param index The zero-based index to focus on.
   * @param <A> The element type of the list.
   * @return A prism focusing on the element at the specified index.
   * @throws UnsupportedOperationException if {@code build} is called.
   */
  public static <A> Prism<List<A>, A> listAt(int index) {
    return Prism.of(
        list ->
            (index >= 0 && index < list.size()) ? Optional.of(list.get(index)) : Optional.empty(),
        a -> {
          throw new UnsupportedOperationException(
              "Cannot build a list from an indexed element. Use Lens or Traversal for list"
                  + " modification.");
        });
  }

  /**
   * Creates a prism for the last element of a {@link List}.
   *
   * <p>This prism matches non-empty lists and focuses on the last element. It does not modify the
   * list when building—it simply creates a singleton list with the given value.
   *
   * <p><b>Note:</b> The build operation creates a new list containing only the single element,
   * discarding any other elements that might have been in the original list.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Prism<List<String>, String> lastPrism = Prisms.listLast();
   *
   * List<String> list = List.of("first", "second", "third");
   * Optional<String> result = lastPrism.getOptional(list);  // Optional.of("third")
   *
   * List<String> empty = List.of();
   * Optional<String> noMatch = lastPrism.getOptional(empty);  // Optional.empty()
   *
   * List<String> built = lastPrism.build("new");  // List.of("new")
   * }</pre>
   *
   * @param <A> The element type of the list.
   * @return A prism focusing on the last element of a list.
   */
  public static <A> Prism<List<A>, A> listLast() {
    return Prism.of(
        list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(list.size() - 1)),
        List::of);
  }
}
