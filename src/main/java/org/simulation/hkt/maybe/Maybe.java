package org.simulation.hkt.maybe;

import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.Objects;


/**
 * A container object which may or may not contain a non-null value.
 * If a value is present, isJust() returns true.
 * If no value is present, the object is considered to be Nothing.
 * <p>
 * This is conceptually similar to Optional, but implemented independently.
 * Null values are not permitted within a Just; use Nothing to represent absence.
 *
 * @param <T> the type of value held by this Maybe
 */
public sealed interface Maybe<T> permits Just, Nothing {

  /**
   * Returns a Maybe describing the given non-null value.
   *
   * @param value the value to describe, which must be non-null
   * @param <T>   the type of the value
   * @return a Maybe with the value present
   * @throws NullPointerException if value is null
   */
  static <T> Maybe<T> just(T value) {
    // Explicitly check for null, as Just cannot hold null.
    Objects.requireNonNull(value, "Value for Just cannot be null");
    return new Just<>(value);
  }

  /**
   * Returns an empty Maybe instance. No value is present for this Maybe.
   *
   * @param <T> Type of the non-existent value
   * @return an empty Maybe
   */
  static <T> Maybe<T> nothing() {
    // Reuse the singleton instance for Nothing
    return Nothing.instance();
  }

  /**
   * Returns a Maybe describing the given value, if non-null, otherwise returns Nothing.
   *
   * @param value the possibly-null value to describe
   * @param <T>   the type of the value
   * @return a Maybe with the value present if the specified value is non-null, otherwise Nothing
   */
  static <T> Maybe<T> fromNullable(T value) {
    return value == null ? nothing() : just(value);
  }

  /**
   * Returns true if there is a value present, otherwise false.
   */
  boolean isJust();

  /**
   * Returns true if there is no value present, otherwise false.
   */
  boolean isNothing();

  /**
   * If a value is present, returns the value, otherwise throws NoSuchElementException.
   *
   * @return the non-null value held by this Maybe
   * @throws NoSuchElementException if there is no value present
   */
  T get() throws NoSuchElementException;

  /**
   * Returns the value if present, otherwise returns other.
   *
   * @param other the value to be returned if there is no value present.
   * @return the value, if present, otherwise other
   */
  T orElse(T other);


  /**
   * Returns the value if present, otherwise invokes other and returns the result of that invocation.
   *
   * @param other a Supplier whose result is returned if no value is present
   * @return the value if present otherwise the result of other.get()
   * @throws NullPointerException if value is not present and other is null
   */
  T orElseGet(Supplier<? extends T> other);

  /**
   * If a value is present, returns a Maybe describing (as if by fromNullable) the result
   * of applying the given mapping function to the value, otherwise returns Nothing.
   *
   * @param <U>    The type of the result of the mapping function
   * @param mapper the mapping function to apply to a value, if present
   * @return a Maybe describing the result of applying a mapping function to the value of this Maybe,
   * if a value is present, otherwise Nothing
   * @throws NullPointerException if the mapping function is null
   */
  <U> Maybe<U> map(Function<? super T, ? extends U> mapper);

  /**
   * If a value is present, returns the result of applying the given Maybe-bearing mapping function
   * to the value, otherwise returns Nothing. This method is similar to map(Function), but the mapping
   * function is one whose result is already a Maybe, and if invoked, flatMap does not wrap it within
   * an additional Maybe.
   *
   * @param <U>    The type parameter of the Maybe returned by the mapping function
   * @param mapper the mapping function to apply to a value, if present
   * @return the result of applying a Maybe-bearing mapping function to the value of this Maybe,
   * if a value is present, otherwise Nothing
   * @throws NullPointerException if the mapping function is null or returns a null Maybe
   */
  <U> Maybe<U> flatMap(Function<? super T, ? extends Maybe<? extends U>> mapper);
}




