// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Solution for Tutorial 04: Monad Chaining — teaching-solution format. */
@DisplayName("Tutorial 04 Solution: Monad Chaining")
public class Tutorial04_MonadChaining_Solution {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ─── Exercise 1 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code input.flatMap(parse)} reads as "given a Right input, run parse
   * and adopt its result". The function reference passes through directly.
   *
   * <p>Alternative: {@code input.flatMap(s -> parse.apply(s))}. Identical; the reference form is
   * shorter.
   *
   * <p>Common wrong attempt: {@code input.map(parse)}. Returns {@code Either<String, Either<String,
   * Integer>>} — the Tutorial 02 diagnostic in action. Use {@code flatMap} when the function
   * returns a wrapped value.
   */
  @Test
  @DisplayName("Exercise 1: flatMap unwraps the inner Either")
  void exercise1_basicFlatMap() {
    Function<String, Either<String, Integer>> parse =
        s -> {
          try {
            return Either.right(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            return Either.left("Not a number");
          }
        };

    Either<String, String> input = Either.right("42");

    Either<String, Integer> result = input.flatMap(parse);

    assertThatEither(result).isRight().hasRight(42);
  }

  // ─── Exercise 2 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: each step reads as one line; the chain order matches the data flow.
   *
   * <p>Alternative: assign intermediate results to {@code var} for readability when each step is
   * heavy. Same chain, broken across lines.
   *
   * <p>Common wrong attempt: capturing intermediate results into mutable locals and then threading
   * them through {@code if/else} branches manually. Works; reintroduces every problem the
   * abstraction was designed to remove.
   */
  @Test
  @DisplayName("Exercise 2: chain parse → validate → divide")
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

    Either<String, Double> result =
        input.flatMap(parse).flatMap(validatePositive).flatMap(divideHundredBy);

    assertThatEither(result).isRight().hasRight(20.0);
  }

  // ─── Exercise 3 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: short-circuiting is the whole point of the Monad capability — we get to
   * read the chain as if every step succeeded, and the type system handles the failure path.
   *
   * <p>Alternative: explicit {@code if (e.isLeft()) return e;} after each step. Five lines per step
   * instead of one.
   *
   * <p>Common wrong attempt: catching exceptions inside the lambda body and returning a fake {@code
   * Right}. The chain proceeds with bogus data; tests pass that should not.
   */
  @Test
  @DisplayName("Exercise 3: chain short-circuits at first error")
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

    Either<String, String> input = Either.right("-5");

    Either<String, Integer> result = input.flatMap(parse).flatMap(validatePositive);

    assertThatEither(result).isLeft().hasLeft("Must be positive");
  }

  // ─── Exercise 4 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: when the function returns a wrapped value, {@code flatMap} preserves the
   * structure. The compiler will catch the wrong choice — using {@code map} here gives a nested
   * type that the assertion would not match.
   *
   * <p>Alternative: rewrite {@code validate} to return the unwrapped value (an {@code int})
   * <em>and</em> a separate boolean. Loses information at the type level; not an improvement.
   *
   * <p>Common wrong attempt: {@code value.map(validate).flatMap(x -> x)}. The double-call
   * <em>does</em> flatten, but it is exactly what {@code flatMap} does in one call. Reach for the
   * canonical form.
   */
  @Test
  @DisplayName("Exercise 4: pick flatMap when the function returns Either")
  void exercise4_flatMapVsMap() {
    Function<Integer, Either<String, Integer>> validate =
        n -> n > 0 ? Either.right(n * 2) : Either.left("Must be positive");

    Either<String, Integer> value = Either.right(5);

    Either<String, Integer> result = value.flatMap(validate);

    assertThatEither(result).hasRight(10);
  }

  // ─── Exercise 5 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code User::email} as a method reference reads as "follow this
   * accessor". The chain has the same shape as the {@code Either} version one tutorial earlier.
   *
   * <p>Alternative: {@code userId.flatMap(findUser).flatMap(u -> u.email())}. Identical.
   *
   * <p>Common wrong attempt: calling {@code .get()} on the inner {@code Maybe<String>} field
   * directly. Bypasses the {@code Maybe} structure and throws if the email is absent — exactly the
   * failure mode {@code Maybe} was designed to prevent.
   */
  @Test
  @DisplayName("Exercise 5: flatMap on Maybe")
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

    Maybe<String> email = userId.flatMap(findUser).flatMap(User::email);

    assertThatMaybe(email).isJust().hasValue("alice@example.com");
  }

  // ─── Exercise 6 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the outer {@code flatMap} iterates the first list; the inner {@code map}
   * iterates the second; the result is the Cartesian product, flattened. This is the same pattern
   * that {@link java.util.stream.Stream#flatMap} expresses for streams.
   *
   * <p>Alternative: nested {@code for} loops with a result builder. Same answer; loses the Monad
   * abstraction and any algebraic reasoning that comes with it.
   *
   * <p>Common wrong attempt: trying to use {@code map2} from Tutorial 03 across two lists expecting
   * it to behave like {@code zip}. {@code map2} on the {@code List} Applicative is Cartesian by
   * design; the {@code zip} behaviour is a different typeclass instance.
   */
  @Test
  @DisplayName("Exercise 6: ListMonad.flatMap is a Cartesian product")
  void exercise6_flatMapWithList() {
    MonadZero<ListKind.Witness> monad = Instances.monadZero(list());
    Kind<ListKind.Witness, Integer> numbers1 = LIST.widen(List.of(1, 2));
    List<Integer> numbers2 = List.of(10, 20);

    Kind<ListKind.Witness, String> pairs =
        monad.flatMap(n1 -> monad.map(n2 -> n1 + "-" + n2, LIST.widen(numbers2)), numbers1);

    assertThat(LIST.narrow(pairs)).containsExactly("1-10", "1-20", "2-10", "2-20");
  }

  // ─── Exercise 7 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the outer step (looking up the user id) is monadic — we depend on it
   * before we can look up age and city. Once we have the id, age and city are independent of each
   * other, so the inner combination uses {@code map2} (Applicative). Using the right capability for
   * each layer keeps the code honest about what depends on what.
   *
   * <p>Alternative: nested {@code flatMap}s for the inner combination. Works; loses the
   * "independent inputs" signal at the type level.
   *
   * <p>Common wrong attempt: pulling all three lookups out into the surrounding scope and using
   * {@code flatMap} on the {@code userId} only as a guard. Equivalent for {@code Either}, but does
   * not generalise to typeclasses where the guard semantics matter.
   */
  @Test
  @DisplayName("Exercise 7: monadic outer step, applicative inner combination")
  void exercise7_flatMap2() {
    Function<String, Either<String, Integer>> getUserAge = id -> Either.right(30);
    Function<String, Either<String, String>> getUserCity = id -> Either.right("New York");
    MonadError<EitherKind.Witness<String>, String> app = Instances.monadError(either());

    Either<String, String> userId = Either.right("user1");

    Either<String, String> result =
        userId.flatMap(
            id -> {
              Either<String, Integer> age = getUserAge.apply(id);
              Either<String, String> city = getUserCity.apply(id);
              return EITHER.narrow(
                  app.map2(
                      EITHER.widen(age),
                      EITHER.widen(city),
                      (a, c) -> "Age: " + a + ", City: " + c));
            });

    assertThatEither(result).hasRight("Age: 30, City: New York");
  }

  // ─── Diagnostic ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: when both inputs are independent, {@code app.map2} is the call that
   * names what is happening. Switching to {@code Validated} later (to accumulate errors) is then a
   * one-line change.
   *
   * <p>Alternative: {@code name.flatMap(n -> age.map(a -> new Pair(n, a)))}. Same answer for {@code
   * Either}; misleading name (depends-on) for inputs that don't depend.
   *
   * <p>Common wrong attempt: using {@code flatMap} so habitually that we forget {@code Applicative}
   * exists. The cost is small for {@code Either}; the cost on {@code Validated} is the loss of
   * error accumulation, which is the whole reason {@code Validated} is in the library.
   */
  @Test
  @DisplayName("Diagnostic: prefer Applicative when steps are independent")
  void diagnostic_avoidUnnecessaryFlatMap() {
    record Pair(String name, int age) {}
    Either<String, String> name = Either.right("Alice");
    Either<String, Integer> age = Either.right(30);
    MonadError<EitherKind.Witness<String>, String> app = Instances.monadError(either());

    Either<String, Pair> result =
        EITHER.narrow(app.map2(EITHER.widen(name), EITHER.widen(age), Pair::new));

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(
            pair -> {
              assertThat(pair.name()).isEqualTo("Alice");
              assertThat(pair.age()).isEqualTo(30);
            });
  }
}
