// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.Map;
import java.util.function.Function;
import org.higherkindedj.hkt.Monoid;

/**
 * A **Getter** is a read-only optic that focuses on exactly one element. Think of it as a
 * functional "accessor" that provides a composable way to extract values from structures.
 *
 * <p>A Getter is the right tool for:
 *
 * <ul>
 *   <li>Extracting a single computed or derived value from a structure
 *   <li>Wrapping pure functions in optic form for composition
 *   <li>Creating a read-only view of a field without modification capability
 * </ul>
 *
 * <p>Every {@link Lens} can be viewed as a Getter (using {@link Lens#asFold()} and extracting via
 * {@code get}), but not every Getter can be a Lens since Getters don't provide a way to set values.
 *
 * <p>A Getter extends {@link Fold} because it always focuses on exactly one element, making it a
 * special case of Fold with cardinality 1.
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * record Person(String firstName, String lastName) {}
 *
 * // Create a getter for the full name (computed value)
 * Getter<Person, String> fullName = Getter.of(p -> p.firstName() + " " + p.lastName());
 *
 * Person person = new Person("John", "Doe");
 * String name = fullName.get(person); // "John Doe"
 *
 * // Compose getters
 * Getter<String, Integer> length = Getter.of(String::length);
 * Getter<Person, Integer> nameLength = fullName.andThen(length);
 * int len = nameLength.get(person); // 8
 * }</pre>
 *
 * @param <S> The source type (the structure being queried).
 * @param <A> The target type (the value being extracted).
 */
public interface Getter<S, A> extends Fold<S, A> {

  /**
   * Gets the focused value from the source structure.
   *
   * <p>This is the core operation of a Getter. Unlike {@link Lens#get}, this operation is
   * inherently read-only and doesn't imply any ability to set the value back.
   *
   * @param source The source structure.
   * @return The focused value.
   */
  A get(S source);

  /**
   * {@inheritDoc}
   *
   * <p>For a Getter, this applies the function to the single focused element and returns the
   * result. Since a Getter always focuses on exactly one element, the monoid's {@code combine} is
   * never called.
   */
  @Override
  default <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source) {
    return f.apply(get(source));
  }

  /**
   * Composes this {@code Getter<S, A>} with another {@code Getter<A, B>} to create a new {@code
   * Getter<S, B>}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Getter<Person, Address> addressGetter = ...;
   * Getter<Address, String> cityGetter = ...;
   * Getter<Person, String> personCity = addressGetter.andThen(cityGetter);
   * }</pre>
   *
   * @param other The Getter to compose with.
   * @param <B> The type of the final focused value.
   * @return A new composed Getter.
   */
  default <B> Getter<S, B> andThen(Getter<A, B> other) {
    Getter<S, A> self = this;
    return new Getter<>() {
      @Override
      public B get(S source) {
        return other.get(self.get(source));
      }
    };
  }

  /**
   * Converts this Getter to a {@link Fold}.
   *
   * <p>This is always valid since a Getter is a Fold that focuses on exactly one element.
   *
   * @return A Fold view of this Getter.
   */
  default Fold<S, A> asFold() {
    return this;
  }

  /**
   * Creates a Getter from a function.
   *
   * <p>This is the primary factory method for creating Getters. The provided function defines how
   * to extract the focused value from the source structure.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Getter<String, Integer> stringLength = Getter.of(String::length);
   * Getter<Person, String> firstName = Getter.of(Person::firstName);
   * }</pre>
   *
   * @param getter The function that extracts the focused value.
   * @param <S> The source type.
   * @param <A> The target type.
   * @return A new Getter.
   */
  static <S, A> Getter<S, A> of(Function<S, A> getter) {
    return getter::apply;
  }

  /**
   * Creates a Getter from a function.
   *
   * <p>This is an alias for {@link #of(Function)} that provides a more descriptive name,
   * emphasizing the "extraction" aspect of a Getter.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Getter<List<String>, Integer> listSize = Getter.to(List::size);
   * }</pre>
   *
   * @param getter The function that extracts the focused value.
   * @param <S> The source type.
   * @param <A> The target type.
   * @return A new Getter.
   */
  static <S, A> Getter<S, A> to(Function<S, A> getter) {
    return of(getter);
  }

  /**
   * Creates a Getter that always returns the same constant value, ignoring the source.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Getter<String, Integer> always42 = Getter.constant(42);
   * always42.get("anything"); // 42
   * }</pre>
   *
   * @param value The constant value to return.
   * @param <S> The source type (ignored).
   * @param <A> The target type.
   * @return A Getter that always returns the given value.
   */
  static <S, A> Getter<S, A> constant(A value) {
    return source -> value;
  }

  /**
   * Creates a Getter that returns the source itself (identity getter).
   *
   * <p>Example:
   *
   * <pre>{@code
   * Getter<String, String> id = Getter.identity();
   * id.get("hello"); // "hello"
   * }</pre>
   *
   * @param <S> The source and target type.
   * @return An identity Getter.
   */
  static <S> Getter<S, S> identity() {
    return source -> source;
  }

  /**
   * Creates a Getter for the first element of a pair.
   *
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @return A Getter that extracts the first element.
   */
  static <A, B> Getter<Map.Entry<A, B>, A> first() {
    return Map.Entry::getKey;
  }

  /**
   * Creates a Getter for the second element of a pair.
   *
   * @param <A> The type of the first element.
   * @param <B> The type of the second element.
   * @return A Getter that extracts the second element.
   */
  static <A, B> Getter<Map.Entry<A, B>, B> second() {
    return Map.Entry::getValue;
  }
}
