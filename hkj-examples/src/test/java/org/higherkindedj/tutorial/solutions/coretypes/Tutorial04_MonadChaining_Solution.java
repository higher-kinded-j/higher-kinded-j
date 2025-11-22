// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 04: Monad - Chaining Dependent Computations
 *
 * <p>A Monad extends Applicative and provides flatMap (also called bind or >>=), which allows you
 * to chain computations where each step depends on the result of the previous step.
 *
 * <p>Key Concepts: - flatMap: chains dependent computations - Each step can decide what to do based
 * on previous results - Automatically handles the context (no manual unwrapping needed) -
 * flatMap2/flatMap3: combine multiple dependent values
 *
 * <p>Difference from Applicative: - Applicative: combines INDEPENDENT values (all inputs known
 * upfront) - Monad: chains DEPENDENT computations (next step depends on previous result)
 */
public class Tutorial04_MonadChaining_Solution {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /**
   * Exercise 1: Basic flatMap
   *
   * <p>flatMap is like map, but the function you provide returns a wrapped value (Either, Maybe,
   * etc.) instead of a plain value.
   *
   * <p>Task: Chain a parse operation with a validation
   */
  @Test
  void exercise1_basicFlatMap() {
    // Simulates parsing a string to an integer
    Function<String, Either<String, Integer>> parse =
        s -> {
          try {
            return Either.right(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            return Either.left("Not a number");
          }
        };

    Either<String, String> input = Either.right("42");

    // SOLUTION: Use flatMap to apply the parse function
    Either<String, Integer> result = input.flatMap(parse);

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).isEqualTo(42);
  }

  /**
   * Exercise 2: Chaining dependent operations
   *
   * <p>The power of flatMap: each step can depend on the result of the previous step.
   *
   * <p>Task: Parse a string, then validate it's positive, then divide 100 by it
   */
  @Test
  void exercise2_chainingDependentOperations() {
    Function<String, Either<String, Integer>> parse =
        s -> {
          try {
            return Either.right(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            return Either.left("Not a number");
          }
        };

    Function<Integer, Either<String, Integer>> validatePositive =
        n -> n > 0 ? Either.right(n) : Either.left("Must be positive");

    Function<Integer, Either<String, Double>> divideHundredBy =
        n -> n != 0 ? Either.right(100.0 / n) : Either.left("Cannot divide by zero");

    Either<String, String> input = Either.right("5");

    // SOLUTION: Chain all three operations using flatMap
    Either<String, Double> result =
        input.flatMap(parse).flatMap(validatePositive).flatMap(divideHundredBy);

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).isEqualTo(20.0);
  }

  /**
   * Exercise 3: Early termination on error
   *
   * <p>If any step in the chain returns Left/Nothing/etc., the rest of the chain is skipped.
   *
   * <p>Task: Observe that the chain stops at the first error
   */
  @Test
  void exercise3_earlyTermination() {
    Function<String, Either<String, Integer>> parse =
        s -> {
          try {
            return Either.right(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            return Either.left("Not a number");
          }
        };

    Function<Integer, Either<String, Integer>> validatePositive =
        n -> n > 0 ? Either.right(n) : Either.left("Must be positive");

    Either<String, String> input = Either.right("-5"); // Will fail validation

    // SOLUTION: The chain will stop at validatePositive because -5 is not positive
    Either<String, Integer> result = input.flatMap(parse).flatMap(validatePositive);

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Must be positive");
  }

  /**
   * Exercise 4: flatMap vs map
   *
   * <p>When should you use flatMap vs map? - Use map when your function returns a plain value - Use
   * flatMap when your function returns a wrapped value (Either, Maybe, etc.)
   *
   * <p>Task: Fix the compilation error by using the right method
   */
  @Test
  void exercise4_flatMapVsMap() {
    Function<Integer, Either<String, Integer>> validate =
        n -> n > 0 ? Either.right(n * 2) : Either.left("Must be positive");

    Either<String, Integer> value = Either.right(5);

    // This function returns Either, so we need flatMap, not map
    // SOLUTION: Use flatMap since validate returns Either
    Either<String, Integer> result = value.flatMap(validate);

    assertThat(result.getRight()).isEqualTo(10);
  }

  /**
   * Exercise 5: flatMap with Maybe
   *
   * <p>Maybe works the same way - flatMap chains operations that might return Nothing.
   *
   * <p>Task: Look up a user, then look up their email
   */
  @Test
  void exercise5_flatMapWithMaybe() {
    record User(String id, String name, Maybe<String> email) {}

    Function<String, Maybe<User>> findUser =
        id -> {
          if (id.equals("user1")) {
            return Maybe.just(new User("user1", "Alice", Maybe.just("alice@example.com")));
          } else {
            return Maybe.nothing();
          }
        };

    Maybe<String> userId = Maybe.just("user1");

    // SOLUTION: Chain two flatMap operations
    // 1. Find the user by ID
    // 2. Get their email from the user object
    Maybe<String> email = userId.flatMap(findUser).flatMap(user -> user.email());

    assertThat(email.isJust()).isTrue();
    assertThat(email.get()).isEqualTo("alice@example.com");
  }

  /**
   * Exercise 6: flatMap with List (Cartesian product)
   *
   * <p>flatMap on List creates a Cartesian product - it applies the function to each element and
   * flattens the results.
   *
   * <p>Task: Generate all pairs of numbers using ListMonad
   */
  @Test
  void exercise6_flatMapWithList() {
    ListMonad monad = ListMonad.INSTANCE;
    Kind<ListKind.Witness, Integer> numbers1 = LIST.widen(List.of(1, 2));
    List<Integer> numbers2 = List.of(10, 20);

    // SOLUTION: Use flatMap to create all combinations
    Kind<ListKind.Witness, String> pairs =
        monad.flatMap(n1 -> monad.map(n2 -> n1 + "-" + n2, LIST.widen(numbers2)), numbers1);

    assertThat(LIST.narrow(pairs)).containsExactly("1-10", "1-20", "2-10", "2-20");
  }

  /**
   * Exercise 7: flatMap2 for combining dependent values
   *
   * <p>flatMap2 is like map2, but for dependent computations.
   *
   * <p>Task: Combine two database lookups
   */
  @Test
  void exercise7_flatMap2() {
    Function<String, Either<String, Integer>> getUserAge = id -> Either.right(30);
    Function<String, Either<String, String>> getUserCity = id -> Either.right("New York");

    Either<String, String> userId = Either.right("user1");

    // SOLUTION: Use flatMap to get the user ID, then use map2 to combine age and city
    EitherMonad<String> applicative = EitherMonad.instance();
    Either<String, String> result =
        userId.flatMap(
            id -> {
              Either<String, Integer> age = getUserAge.apply(id);
              Either<String, String> city = getUserCity.apply(id);
              // Use map2 to combine age and city into a description string
              return EITHER.narrow(
                  applicative.map2(
                      EITHER.widen(age),
                      EITHER.widen(city),
                      (a, c) -> "Age: " + a + ", City: " + c));
            });

    assertThat(result.getRight()).isEqualTo("Age: 30, City: New York");
  }

  /**
   * Congratulations! You've completed Tutorial 04: Monad Chaining
   *
   * <p>You now understand: ✓ How to use flatMap to chain dependent computations ✓ The difference
   * between map (plain values) and flatMap (wrapped values) ✓ That flatMap short-circuits on errors
   * ✓ How flatMap works with different types (Either, Maybe, List) ✓ How to combine dependent
   * values with flatMap2
   *
   * <p>Next: Tutorial 05 - Monad Error Handling
   */
}
