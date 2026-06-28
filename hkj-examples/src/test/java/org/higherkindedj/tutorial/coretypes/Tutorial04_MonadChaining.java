// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.ListAssert.assertThatList;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
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

/**
 * Tutorial 04: Monad — chaining dependent computations.
 *
 * <p>Pain → Promise. When step N+1 needs the result of step N, imperative Java threads results
 * through if/null/exception ladders:
 *
 * <pre>
 *   String raw = request.getParameter("count");
 *   if (raw == null) return Response.badRequest("missing count");
 *   int count;
 *   try { count = Integer.parseInt(raw); } catch (NumberFormatException e) {
 *     return Response.badRequest("not a number");
 *   }
 *   if (count &lt;= 0) return Response.badRequest("must be positive");
 *   double per = 100.0 / count;
 *   return Response.ok("each share is " + per);
 * </pre>
 *
 * <p>{@link org.higherkindedj.hkt.Monad Monad} captures the same shape with a single combinator,
 * {@code flatMap}. Each step returns an {@code F<B>}; {@code flatMap} chains them together and
 * short-circuits on the first failure, so the call site has no early returns and no try/catch:
 *
 * <pre>
 *   parse(raw)
 *     .flatMap(this::validatePositive)
 *     .flatMap(this::divideHundredBy)
 *     .map(per -&gt; "each share is " + per);
 * </pre>
 *
 * <p>Java idiom anchor.
 *
 * <ul>
 *   <li>{@link java.util.Optional#flatMap} is the {@code Optional} version of this exact capability
 *       — chain steps that may return {@code Optional.empty()}.
 *   <li>{@link java.util.concurrent.CompletableFuture#thenCompose} is the future version — chain
 *       steps that produce another {@code CompletableFuture}.
 *   <li>The Java 21+ {@code Result<T>} pattern people roll by hand in their own codebases is trying
 *       to recreate {@link Either} + {@code flatMap}.
 * </ul>
 *
 * <p>What we will do here:
 *
 * <ol>
 *   <li>Use {@link Either#flatMap} for a single dependent step.
 *   <li>Chain three dependent steps and observe the natural top-to-bottom reading order.
 *   <li>See that an error at any step short-circuits the rest of the chain.
 *   <li>Practise the {@code map} vs {@code flatMap} decision (the diagnostic from Tutorial 02).
 *   <li>Repeat the pattern for {@link Maybe} (a different container, same shape).
 *   <li>Use {@code flatMap} on {@link List} to compute a Cartesian product.
 *   <li>Combine two dependent lookups using nested {@code flatMap} (one row of the Cartesian).
 * </ol>
 *
 * <p>For the typeclass deep-dive see <a
 * href="../../../../../../../../../hkj-book/src/functional/monad.md">Monad</a> in the Foundations
 * chapter.
 */
@DisplayName("Tutorial 04: Monad Chaining")
public class Tutorial04_MonadChaining {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 1: A single flatMap
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: Basic {@code flatMap} on {@link Either}.
   *
   * <p>{@code flatMap} is "{@code map}, then flatten". The function we pass returns a wrapped
   * value, and {@code flatMap} unwraps it so we don't get nested {@code Either<E, Either<E, A>>}.
   *
   * <pre>
   *   // Nudge:    parse() returns an Either; flatMap is the right tool when the function returns
   *   //           a wrapped value.
   *   // Strategy: input.flatMap(parse).
   *   // Spoiler:  input.flatMap(parse)
   * </pre>
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

    Either<String, Integer> result = answerRequired();

    assertThatEither(result).isRight().hasRight(42);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 2: Three steps in one chain
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 2: Chaining dependent steps.
   *
   * <p>Each step receives the result of the previous one and decides what happens next. The chain
   * reads top-to-bottom in the order the data flows.
   *
   * <pre>
   *   // Nudge:    Three steps -&gt; three flatMap calls.
   *   // Strategy: input.flatMap(parse).flatMap(validatePositive).flatMap(divideHundredBy).
   *   // Spoiler:  input.flatMap(parse).flatMap(validatePositive).flatMap(divideHundredBy)
   * </pre>
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

    Either<String, Double> result = answerRequired();

    assertThatEither(result).isRight().hasRight(20.0);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 3: Short-circuit on first error
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 3: Early termination.
   *
   * <p>If any step in the chain returns {@code Left} (or {@code Nothing}, or any other "failure"
   * shape), every subsequent step is skipped. The function we pass to {@code flatMap} is never
   * invoked. This is the same control flow as {@code if (e.isError()) return e;} in the imperative
   * version, but it is automatic.
   *
   * <p>This exercise is partly pre-filled to show the chain shape.
   *
   * <pre>
   *   // Nudge:    Same chain as exercise 2; the input is invalid so validation fails.
   *   // Strategy: input.flatMap(parse).flatMap(validatePositive).
   *   // Spoiler:  already filled in below.
   * </pre>
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

    Either<String, String> input = Either.right("-5"); // will fail validation

    Either<String, Integer> result = input.flatMap(parse).flatMap(validatePositive);

    assertThatEither(result).isLeft().hasLeft("Must be positive");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 4: map vs flatMap — the canonical decision
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 4: The {@code map} vs {@code flatMap} decision.
   *
   * <p>Rule of thumb:
   *
   * <ul>
   *   <li>If the function returns a plain value, use {@code map}.
   *   <li>If the function returns a wrapped value, use {@code flatMap}.
   * </ul>
   *
   * <p>The {@code validate} function below returns {@code Either<String, Integer>}, so the right
   * call is {@code flatMap}.
   *
   * <pre>
   *   // Nudge:    validate returns Either, so we want flatMap (otherwise the result is nested).
   *   // Strategy: value.flatMap(validate).
   *   // Spoiler:  value.flatMap(validate)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: pick flatMap when the function returns Either")
  void exercise4_flatMapVsMap() {
    Function<Integer, Either<String, Integer>> validate =
        n -> n > 0 ? Either.right(n * 2) : Either.left("Must be positive");

    Either<String, Integer> value = Either.right(5);

    Either<String, Integer> result = answerRequired();

    assertThatEither(result).hasRight(10);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 5: Same shape on Maybe
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 5: {@code flatMap} on {@link Maybe}.
   *
   * <p>The Monad capability is uniform across containers. {@code Maybe.flatMap} chains operations
   * that may return {@code Nothing}; the chain short-circuits the first time we hit absence.
   *
   * <pre>
   *   // Nudge:    Two flatMap calls: lookup then read field.
   *   // Strategy: userId.flatMap(findUser).flatMap(User::email).
   *   // Spoiler:  userId.flatMap(findUser).flatMap(User::email)
   * </pre>
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

    Maybe<String> email = answerRequired();

    assertThatMaybe(email).isJust().hasValue("alice@example.com");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 6: List flatMap = Cartesian product
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 6: {@code flatMap} on {@link List}.
   *
   * <p>For lists, {@code flatMap} produces a Cartesian-like product: for each element of the outer
   * list, run a function that produces an inner list, and concatenate. This is the same shape as
   * {@code Stream.flatMap}.
   *
   * <pre>
   *   // Nudge:    The outer flatMap iterates numbers1; the inner map iterates numbers2.
   *   // Strategy: monad.flatMap(n1 -&gt; monad.map(n2 -&gt; n1 + "-" + n2, LIST.widen(numbers2)),
   *   //                         numbers1)
   *   // Spoiler:  see the solution.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6: ListMonad.flatMap is a Cartesian product")
  void exercise6_flatMapWithList() {
    MonadZero<ListKind.Witness> monad = Instances.monadZero(list());
    Kind<ListKind.Witness, Integer> numbers1 = LIST.widen(List.of(1, 2));
    List<Integer> numbers2 = List.of(10, 20);

    Kind<ListKind.Witness, String> pairs = answerRequired();

    assertThatList(pairs).containsExactly("1-10", "1-20", "2-10", "2-20");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 7: Combining two dependent lookups
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 7: Two dependent lookups, combined.
   *
   * <p>Once we have the user id, the age and city lookups are independent of each other but both
   * depend on the id. The outer step is monadic (depends on id); the inner combination is
   * Applicative ({@code map2}).
   *
   * <pre>
   *   // Nudge:    Inside the outer flatMap, age and city are independent — use map2.
   *   // Strategy: app.map2(EITHER.widen(age), EITHER.widen(city),
   *   //                    (a, c) -&gt; "Age: " + a + ", City: " + c)
   *   // Spoiler:  EITHER.narrow(...)
   * </pre>
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
              return answerRequired();
            });

    assertThatEither(result).hasRight("Age: 30, City: New York");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Diagnostic: Things People Get Wrong
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Diagnostic: don't use {@code flatMap} when the steps are independent.
   *
   * <p>{@code flatMap} expresses dependency. If the steps do not actually depend on each other,
   * using {@code flatMap} forces a sequential mental model and (in the fail-fast case) loses any
   * possibility of accumulating errors or running steps concurrently.
   *
   * <p>Below, two field validations are each independent of the other; combining them with nested
   * {@code flatMap} works, but {@code map2} on the {@code EitherMonad} typeclass instance says the
   * same thing more directly. (And on {@link org.higherkindedj.hkt.validated.Validated}, only the
   * Applicative form accumulates errors — the Monad form does not.)
   *
   * <p>Task: write the body using {@code app.map2} on {@code Instances.monadError(either())}.
   *
   * <pre>
   *   // Nudge:    Same shape as Tutorial 03 exercise 2, here applied to a Pair.
   *   // Strategy: EITHER.narrow(app.map2(EITHER.widen(name), EITHER.widen(age), Pair::new))
   *   // Spoiler:  see the solution file.
   * </pre>
   */
  @Test
  @DisplayName("Diagnostic: prefer Applicative when steps are independent")
  void diagnostic_avoidUnnecessaryFlatMap() {
    record Pair(String name, int age) {}
    Either<String, String> name = Either.right("Alice");
    Either<String, Integer> age = Either.right(30);
    MonadError<EitherKind.Witness<String>, String> app = Instances.monadError(either());

    Either<String, Pair> result = answerRequired();

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(
            pair -> {
              assertThat(pair.name()).isEqualTo("Alice");
              assertThat(pair.age()).isEqualTo(30);
            });
  }

  /*
   * Where to next?
   *   • Tutorial 05 — MonadError. Once we can chain dependent steps, the next question is how to
   *     model and recover from typed errors.
   *   • Foundations chapter — Monad. Includes the "monad as programmable semicolon" framing and
   *     the comparison with Optional.flatMap and CompletableFuture.thenCompose.
   */
}
