// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.data;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;

/**
 * Common test functions for reuse across test suites.
 *
 * <p>Provides standard functions that are commonly needed in tests, reducing boilerplate and
 * ensuring consistency across test suites.
 *
 * <h2>Categories:</h2>
 *
 * <ul>
 *   <li>Basic transformations (toString, append, multiply, etc.)
 *   <li>Exception throwing functions
 *   <li>Null-returning functions
 *   <li>Traverse-specific functions
 *   <li>Predicate functions
 *   <li>Combining functions (BiFunction, etc.)
 * </ul>
 */
public final class TestFunctions {

  private TestFunctions() {
    throw new AssertionError("TestFunctions is a utility class");
  }

  // =============================================================================
  // Basic Transformation Functions
  // =============================================================================

  /** Converts Integer to String using toString() */
  public static final Function<Integer, String> INT_TO_STRING = Object::toString;

  /** Appends "_test" suffix to strings */
  public static final Function<String, String> APPEND_SUFFIX = s -> s + "_test";

  /** Multiplies integers by 2 */
  public static final Function<Integer, Integer> MULTIPLY_BY_2 = i -> i * 2;

  /** Returns string length */
  public static final Function<String, Integer> STRING_LENGTH = String::length;

  /** Converts to uppercase */
  public static final Function<String, String> TO_UPPERCASE = String::toUpperCase;

  /** Converts to lowercase */
  public static final Function<String, String> TO_LOWERCASE = String::toLowerCase;

  /** Squares an integer */
  public static final Function<Integer, Integer> SQUARE = i -> i * i;

  /** Increments an integer by 1 */
  public static final Function<Integer, Integer> INCREMENT = i -> i + 1;

  /** Decrements an integer by 1 */
  public static final Function<Integer, Integer> DECREMENT = i -> i - 1;

  /** Negates an integer */
  public static final Function<Integer, Integer> NEGATE = i -> -i;

  /** Returns absolute value */
  public static final Function<Integer, Integer> ABS = Math::abs;

  /** Identity function */
  public static final Function<Object, Object> IDENTITY = x -> x;

  // =============================================================================
  // Exception Throwing Functions
  // =============================================================================

  /**
   * Creates a function that throws the given exception.
   *
   * @param exception The exception to throw
   * @param <A> The input type
   * @param <B> The output type
   * @return A function that always throws the given exception
   */
  public static <A, B> Function<A, B> throwingFunction(RuntimeException exception) {
    return a -> {
      throw exception;
    };
  }

  /**
   * Creates a function that throws an exception with a message based on input.
   *
   * @param messagePrefix Prefix for the exception message
   * @param <A> The input type
   * @param <B> The output type
   * @return A function that throws with a custom message
   */
  public static <A, B> Function<A, B> throwingFunctionWithMessage(String messagePrefix) {
    return a -> {
      throw new RuntimeException(messagePrefix + ": " + a);
    };
  }

  /**
   * Creates a function that returns null (for testing null handling).
   *
   * @param <A> The input type
   * @param <B> The output type
   * @return A function that always returns null
   */
  public static <A, B> Function<A, B> nullReturningFunction() {
    return a -> null;
  }

  // =============================================================================
  // Traverse-Specific Functions
  // =============================================================================

  /**
   * Creates a traverse function that wraps values in Maybe Just.
   *
   * @param <A> The input type
   * @return A function that creates Maybe.just(toString(input))
   */
  public static <A> Function<A, Kind<MaybeKind.Witness, String>> wrapInMaybeJust() {
    return a -> MaybeKindHelper.MAYBE.widen(Maybe.just(a.toString()));
  }

  /**
   * Creates a traverse function that conditionally wraps values in Maybe.
   *
   * @param condition The condition to test values against
   * @param <A> The input type
   * @return A function that creates Maybe.just if condition is true, Maybe.nothing otherwise
   */
  public static <A> Function<A, Kind<MaybeKind.Witness, String>> conditionalMaybe(
      Predicate<A> condition) {
    return a -> {
      if (condition.test(a)) {
        return MaybeKindHelper.MAYBE.widen(Maybe.just(a.toString()));
      } else {
        return MaybeKindHelper.MAYBE.widen(Maybe.nothing());
      }
    };
  }

  /**
   * Creates a function that always returns Maybe.nothing (for testing failure cases).
   *
   * @param <A> The input type
   * @return A function that always returns Maybe.nothing
   */
  public static <A> Function<A, Kind<MaybeKind.Witness, String>> alwaysNothing() {
    return a -> MaybeKindHelper.MAYBE.widen(Maybe.nothing());
  }

  /**
   * Creates a traverse function that wraps in Maybe based on value.
   *
   * @param <A> The input type
   * @return A function that wraps non-null values in Maybe.just
   */
  public static <A> Function<A, Kind<MaybeKind.Witness, A>> wrapNonNull() {
    return a ->
        a != null
            ? MaybeKindHelper.MAYBE.widen(Maybe.just(a))
            : MaybeKindHelper.MAYBE.widen(Maybe.nothing());
  }

  // =============================================================================
  // Predicate Functions
  // =============================================================================

  /** Tests if integer is positive */
  public static final Predicate<Integer> IS_POSITIVE = i -> i > 0;

  /** Tests if integer is negative */
  public static final Predicate<Integer> IS_NEGATIVE = i -> i < 0;

  /** Tests if integer is zero */
  public static final Predicate<Integer> IS_ZERO = i -> i == 0;

  /** Tests if integer is even */
  public static final Predicate<Integer> IS_EVEN = i -> i % 2 == 0;

  /** Tests if integer is odd */
  public static final Predicate<Integer> IS_ODD = i -> i % 2 != 0;

  /** Tests if string is not empty */
  public static final Predicate<String> NOT_EMPTY = s -> s != null && !s.isEmpty();

  /** Tests if string is not blank */
  public static final Predicate<String> NOT_BLANK = s -> s != null && !s.isBlank();

  /** Tests if string contains only digits */
  public static final Predicate<String> IS_NUMERIC = s -> s != null && s.matches("\\d+");

  /** Tests if string contains only letters */
  public static final Predicate<String> IS_ALPHABETIC = s -> s != null && s.matches("[a-zA-Z]+");

  /** Always returns true */
  public static final Predicate<Object> ALWAYS_TRUE = x -> true;

  /** Always returns false */
  public static final Predicate<Object> ALWAYS_FALSE = x -> false;

  // =============================================================================
  // Combining Functions (BiFunction, etc.)
  // =============================================================================

  /** Concatenates two strings */
  public static final BiFunction<String, String, String> CONCAT = (a, b) -> a + b;

  /** Concatenates two strings with separator */
  public static BiFunction<String, String, String> concatWithSeparator(String separator) {
    return (a, b) -> a + separator + b;
  }

  /** Adds two integers */
  public static final BiFunction<Integer, Integer, Integer> ADD = (a, b) -> a + b;

  /** Multiplies two integers */
  public static final BiFunction<Integer, Integer, Integer> MULTIPLY = (a, b) -> a * b;

  /** Returns the maximum of two integers */
  public static final BiFunction<Integer, Integer, Integer> MAX = Math::max;

  /** Returns the minimum of two integers */
  public static final BiFunction<Integer, Integer, Integer> MIN = Math::min;

  /** Formats two values as "a: b" */
  public static final BiFunction<Object, Object, String> FORMAT_PAIR = (a, b) -> a + ": " + b;

  // =============================================================================
  // FlatMap Helper Functions
  // =============================================================================

  /**
   * Creates a flatMap function for a given monad.
   *
   * @param monad The monad to use
   * @param mapper The mapping function
   * @param <F> The monad witness type
   * @param <A> The input type
   * @param <B> The output type
   * @return A flatMap function
   */
  public static <F, A, B> Function<A, Kind<F, B>> flatMapFunction(
      org.higherkindedj.hkt.Monad<F> monad, Function<A, B> mapper) {
    return a -> monad.of(mapper.apply(a));
  }

  /**
   * Creates a flatMap function that conditionally returns a value.
   *
   * @param monad The monad to use
   * @param condition The condition to test
   * @param ifTrue Function to apply if condition is true
   * @param ifFalse Function to apply if condition is false
   * @param <F> The monad witness type
   * @param <A> The input type
   * @param <B> The output type
   * @return A conditional flatMap function
   */
  public static <F, A, B> Function<A, Kind<F, B>> conditionalFlatMap(
      org.higherkindedj.hkt.Monad<F> monad,
      Predicate<A> condition,
      Function<A, B> ifTrue,
      Function<A, B> ifFalse) {
    return a -> condition.test(a) ? monad.of(ifTrue.apply(a)) : monad.of(ifFalse.apply(a));
  }

  // =============================================================================
  // Composition Helpers
  // =============================================================================

  /**
   * Composes two functions.
   *
   * @param f First function
   * @param g Second function
   * @param <A> Input type
   * @param <B> Intermediate type
   * @param <C> Output type
   * @return Composed function g(f(x))
   */
  public static <A, B, C> Function<A, C> compose(Function<A, B> f, Function<B, C> g) {
    return a -> g.apply(f.apply(a));
  }

  /**
   * Creates a function that applies a transformation multiple times.
   *
   * @param function The function to apply
   * @param times Number of times to apply
   * @param <A> The type
   * @return A function that applies the transformation multiple times
   */
  public static <A> Function<A, A> applyNTimes(Function<A, A> function, int times) {
    return a -> {
      A result = a;
      for (int i = 0; i < times; i++) {
        result = function.apply(result);
      }
      return result;
    };
  }

  // =============================================================================
  // Value Generation Functions
  // =============================================================================

  /**
   * Creates a constant function that always returns the same value.
   *
   * @param constant The constant value to return
   * @param <A> The input type
   * @param <B> The output type
   * @return A function that always returns the constant
   */
  public static <A, B> Function<A, B> constant(B constant) {
    return a -> constant;
  }

  /**
   * Creates a function that returns a formatted string.
   *
   * @param format The format string
   * @param <A> The input type
   * @return A function that formats the input
   */
  public static <A> Function<A, String> formatter(String format) {
    return format::formatted;
  }

  /**
   * Creates a function that adds a prefix and suffix.
   *
   * @param prefix The prefix to add
   * @param suffix The suffix to add
   * @return A function that wraps strings
   */
  public static Function<String, String> wrapper(String prefix, String suffix) {
    return s -> prefix + s + suffix;
  }
}
