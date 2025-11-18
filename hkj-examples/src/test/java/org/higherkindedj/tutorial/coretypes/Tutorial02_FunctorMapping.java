// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherFunctor;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.list.ListFunctor;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListOf;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 02: Functor - Transforming Values in Context
 *
 * <p>A Functor is a typeclass that provides the map operation, allowing you to transform values
 * inside a context (like Either, List, Maybe) without changing the context itself.
 *
 * <p>Key Concepts: - map: applies a function to the value(s) inside a context - Preserves
 * structure: List of 3 items stays a list of 3 items - Composable: multiple maps can be chained
 *
 * <p>Functor Laws: 1. Identity: map(x => x) == identity 2. Composition: map(f).map(g) == map(x =>
 * g(f(x)))
 */
public class Tutorial02_FunctorMapping {

  /**
   * Exercise 1: Basic map on Either
   *
   * <p>Either represents a value that can be either Left (error) or Right (success). Mapping only
   * affects the Right side.
   *
   * <p>Task: Use map to convert an Integer to a String
   */
  @Test
  void exercise1_mapEither() {
    Either<String, Integer> either = Either.right(42);

    // TODO: Replace ___ with code that maps the integer to a string
    // Hint: Use either.map(...) with a function that converts Integer to String
    Either<String, String> result = ___;

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).isEqualTo("42");
  }

  /**
   * Exercise 2: Map on Left (error case)
   *
   * <p>When Either is Left, map does nothing - the error passes through unchanged.
   *
   * <p>Task: Observe that mapping over a Left has no effect
   */
  @Test
  void exercise2_mapDoesNotAffectLeft() {
    Either<String, Integer> error = Either.left("Error occurred");

    // TODO: Replace ___ with code that attempts to map the error case
    // The map will not be applied since this is a Left
    Either<String, String> result = error.map(___);

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Error occurred");
  }

  /**
   * Exercise 3: Map on List
   *
   * <p>Mapping over a List applies the function to every element.
   *
   * <p>Task: Double each number in the list
   */
  @Test
  void exercise3_mapList() {
    ListOf<Integer> numbers = ListOf.of(1, 2, 3, 4, 5);

    // TODO: Replace ___ with code that doubles each number
    // Hint: Use numbers.map(...) with a function that multiplies by 2
    ListOf<Integer> doubled = ___;

    assertThat(doubled.toJavaList()).containsExactly(2, 4, 6, 8, 10);
  }

  /**
   * Exercise 4: Chaining map operations
   *
   * <p>Multiple map calls can be chained together. Each map transforms the result of the previous
   * one.
   *
   * <p>Task: Chain multiple transformations together
   */
  @Test
  void exercise4_chainingMaps() {
    Either<String, Integer> value = Either.right(10);

    // TODO: Replace ___ with chained map operations that:
    // 1. Multiply by 2 (10 -> 20)
    // 2. Add 5 (20 -> 25)
    // 3. Convert to String (25 -> "25")
    Either<String, String> result = ___;

    assertThat(result.getRight()).isEqualTo("25");
  }

  /**
   * Exercise 5: Using Functor typeclass
   *
   * <p>Instead of calling .map() directly, we can use the Functor typeclass instance. This is
   * useful for generic programming.
   *
   * <p>Task: Use EitherFunctor to map over a Kind
   */
  @Test
  void exercise5_functorTypeclass() {
    EitherFunctor<String> functor = EitherFunctor.instance();
    Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(Either.right(100));

    // TODO: Replace ___ with code that uses functor.map() to transform the value
    // Hint: functor.map(function, kind)
    Kind<EitherKind.Witness<String>, String> mapped = functor.map(___, kind);

    Either<String, String> result = EITHER.narrow(mapped);
    assertThat(result.getRight()).isEqualTo("Value: 100");
  }

  /**
   * Exercise 6: Map with method references
   *
   * <p>Java's method references work great with map!
   *
   * <p>Task: Use method references to transform strings
   */
  @Test
  void exercise6_methodReferences() {
    ListOf<String> words = ListOf.of("hello", "world", "java");

    // TODO: Replace ___ with a method reference that converts each string to uppercase
    // Hint: String::toUpperCase
    ListOf<String> uppercase = words.map(___);

    assertThat(uppercase.toJavaList()).containsExactly("HELLO", "WORLD", "JAVA");
  }

  /**
   * Congratulations! You've completed Tutorial 02: Functor Mapping
   *
   * <p>You now understand: ✓ How to use map to transform values in context ✓ That map preserves
   * the structure (Left stays Left, 3 items stay 3 items) ✓ How to chain multiple map operations ✓
   * How to use Functor typeclass instances ✓ Using method references with map
   *
   * <p>Next: Tutorial 03 - Applicative Combining
   */
}
