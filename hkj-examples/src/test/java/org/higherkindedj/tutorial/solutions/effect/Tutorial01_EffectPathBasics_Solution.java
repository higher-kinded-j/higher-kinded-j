// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.either.Either;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Solution for Tutorial 01: Effect Path Basics — teaching-solution format. */
@DisplayName("Tutorial 01 Solution: Effect Path Basics")
public class Tutorial01_EffectPathBasics_Solution {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ─── Exercise 1 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: each factory expresses a single intent. {@code Path.just} = "we know the
   * value"; {@code Path.nothing} = "no value, by design"; {@code Path.maybe} = "we have a nullable,
   * do the right thing".
   *
   * <p>Alternative: {@code Path.maybe(value)} works for the present case too, since non-null inputs
   * become Just. {@code Path.just} is preferred when we already know the value is non-null — it
   * documents intent and avoids the runtime null check.
   *
   * <p>Common wrong attempt: building a {@code Maybe} first and then wrapping with {@code
   * Path.maybe(maybe)}. Works, but adds a layer that the dedicated factory removes.
   */
  @Test
  @DisplayName("Exercise 1: build MaybePath with just / nothing / maybe")
  void exercise1_creatingMaybePath() {
    MaybePath<String> present = Path.just("hello");
    assertThat(present.run().isJust()).isTrue();
    assertThat(present.getOrElse("default")).isEqualTo("hello");

    MaybePath<String> absent = Path.nothing();
    assertThat(absent.run().isNothing()).isTrue();
    assertThat(absent.getOrElse("default")).isEqualTo("default");

    String nullable = null;
    MaybePath<String> fromNullable = Path.maybe(nullable);
    assertThat(fromNullable.run().isNothing()).isTrue();

    MaybePath<String> fromNonNull = Path.maybe("world");
    assertThat(fromNonNull.run().isJust()).isTrue();
  }

  // ─── Exercise 2 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code Path.right} / {@code Path.left} mirror {@code Either.right} /
   * {@code Either.left} — same names, same shape, lifted into the Path API.
   *
   * <p>Alternative: {@code Path.either(Either.right(42))} works but is roundabout when we are
   * starting from a literal value.
   *
   * <p>Common wrong attempt: type-witness syntax like {@code Path.<String, Integer>right(42)}
   * everywhere. Inference usually does the right thing; only reach for explicit witnesses when the
   * compiler complains.
   */
  @Test
  @DisplayName("Exercise 2: build EitherPath with right / left / either")
  void exercise2_creatingEitherPath() {
    EitherPath<String, Integer> success = Path.right(42);
    assertThat(success.run().isRight()).isTrue();
    assertThat(success.run().getRight()).isEqualTo(42);

    EitherPath<String, Integer> failure = Path.left("Invalid input");
    assertThat(failure.run().isLeft()).isTrue();
    assertThat(failure.run().getLeft()).isEqualTo("Invalid input");

    Either<String, Integer> either = Either.right(100);
    EitherPath<String, Integer> fromEither = Path.either(either);
    assertThat(fromEither.run().getRight()).isEqualTo(100);
  }

  // ─── Exercise 3 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code Path.tryOf} captures the throw and the success in one type;
   * {@code Path.io} defers the side effect, so we can build pipelines without running them.
   *
   * <p>Alternative: pre-build a {@code Try} or {@code IO} and lift it. Same effect; the dedicated
   * factory is one line shorter and reads better.
   *
   * <p>Common wrong attempt: calling {@code Path.io(...).unsafeRun()} eagerly inside the pipeline.
   * Defeats the purpose of IO — we lose the ability to compose, retry, or observe the effect before
   * running it. Reach for {@code unsafeRun()} only at the boundary of the program.
   */
  @Test
  @DisplayName("Exercise 3: TryPath captures throw; IOPath defers a side effect")
  void exercise3_tryPathAndIOPath() {
    TryPath<Integer> successTry = Path.tryOf(() -> Integer.parseInt("42"));
    assertThat(successTry.run().isSuccess()).isTrue();
    assertThat(successTry.getOrElse(-1)).isEqualTo(42);

    TryPath<Integer> failureTry = Path.tryOf(() -> Integer.parseInt("not a number"));
    assertThat(failureTry.run().isFailure()).isTrue();

    var counter = new int[] {0};
    IOPath<Integer> ioPath =
        Path.io(
            () -> {
              counter[0]++;
              return counter[0];
            });

    assertThat(counter[0]).isEqualTo(0);

    Integer result = ioPath.unsafeRun();
    assertThat(counter[0]).isEqualTo(1);
    assertThat(result).isEqualTo(1);
  }

  // ─── Exercise 4 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code map} is the Functor capability spelled into the Path API. Same
   * shape as {@link java.util.Optional#map} or {@link java.util.stream.Stream#map} — transform the
   * inside, leave the structure alone.
   *
   * <p>Alternative: {@code path.via(s -> Path.just(s.toUpperCase()))}. Works, but reaches for Monad
   * when Functor would do.
   *
   * <p>Common wrong attempt: passing a function that returns a Path to {@code .map}. The result is
   * nested ({@code MaybePath<MaybePath<String>>}). The diagnostic exercise covers this.
   */
  @Test
  @DisplayName("Exercise 4: map transforms success values; errors pass through")
  void exercise4_transformingWithMap() {
    MaybePath<String> name = Path.just("alice");
    MaybePath<String> upperName = name.map(String::toUpperCase);
    assertThat(upperName.getOrElse("")).isEqualTo("ALICE");

    MaybePath<String> absent = Path.<String>nothing().map(String::toUpperCase);
    assertThat(absent.run().isNothing()).isTrue();

    EitherPath<String, Integer> success = Path.right(10);
    EitherPath<String, Integer> doubled = success.map(n -> n * 2);
    assertThat(doubled.run().getRight()).isEqualTo(20);

    EitherPath<String, Integer> failure = Path.<String, Integer>left("error").map(n -> n * 2);
    assertThat(failure.run().getLeft()).isEqualTo("error");
  }

  // ─── Exercise 5 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: each step receives the previous result and decides what happens next.
   * The chain reads top-to-bottom in the order data flows; failures short-circuit automatically.
   *
   * <p>Alternative: {@code .flatMap(...)} (the same operation under a different name). {@code via}
   * reads more naturally in Java, but the two are aliases.
   *
   * <p>Common wrong attempt: assigning intermediate results to mutable locals and then using {@code
   * if (intermediate.isLeft())} to bail out manually. Works; reintroduces every problem the
   * abstraction was designed to remove.
   */
  @Test
  @DisplayName("Exercise 5: via chains parse → validate → divide and short-circuits on failure")
  void exercise5_chainingWithVia() {
    Function<String, EitherPath<String, Integer>> parseNumber =
        s -> {
          try {
            return Path.right(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            return Path.left("Not a number: " + s);
          }
        };

    Function<Integer, EitherPath<String, Integer>> validatePositive =
        n -> n > 0 ? Path.right(n) : Path.left("Must be positive");

    Function<Integer, EitherPath<String, Double>> divideHundredBy =
        n -> n != 0 ? Path.right(100.0 / n) : Path.left("Division by zero");

    EitherPath<String, String> input = Path.right("25");

    EitherPath<String, Double> result =
        input.via(parseNumber).via(validatePositive).via(divideHundredBy);

    assertThat(result.run().isRight()).isTrue();
    assertThat(result.run().getRight()).isEqualTo(4.0);

    EitherPath<String, String> invalidInput = Path.right("not-a-number");
    EitherPath<String, Double> failedResult =
        invalidInput.via(parseNumber).via(validatePositive).via(divideHundredBy);

    assertThat(failedResult.run().isLeft()).isTrue();
    assertThat(failedResult.run().getLeft()).isEqualTo("Not a number: not-a-number");
  }

  // ─── Exercise 6 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: each operation has a single responsibility. {@code recover} swaps the
   * error for a value; {@code recoverWith} swaps for a new computation; {@code orElse} keeps the
   * error around but replaces the value; {@code mapError} adapts the error type without recovering.
   *
   * <p>Alternative: {@code path.recoverWith(err -> Path.right(default))} can stand in for {@code
   * recover}; lighter weight when the recovery returns a constant, heavier when it does not.
   *
   * <p>Common wrong attempt: using {@code recover} to translate errors. {@code recover} produces a
   * success; if we want to keep the error path, reach for {@code mapError} instead.
   */
  @Test
  @DisplayName("Exercise 6: recover / recoverWith / orElse / mapError")
  void exercise6_errorRecovery() {
    EitherPath<String, Integer> failure = Path.left("Not found");

    EitherPath<String, Integer> recovered = failure.recover(err -> -1);
    assertThat(recovered.run().isRight()).isTrue();
    assertThat(recovered.run().getRight()).isEqualTo(-1);

    EitherPath<String, Integer> recoveredWith = failure.recoverWith(err -> Path.right(0));
    assertThat(recoveredWith.run().getRight()).isEqualTo(0);

    EitherPath<String, Integer> primary = Path.left("Primary failed");
    EitherPath<String, Integer> fallback = Path.right(42);

    EitherPath<String, Integer> withFallback = primary.orElse(() -> fallback);
    assertThat(withFallback.run().getRight()).isEqualTo(42);

    EitherPath<String, Integer> stringError = Path.left("error");
    EitherPath<Integer, Integer> intError = stringError.mapError(String::length);
    assertThat(intError.run().getLeft()).isEqualTo(5);
  }

  // ─── Exercise 7 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code zipWith} signals that the inputs do not depend on each other.
   * That is information the type system can use (e.g. to run the inputs concurrently in a future
   * version); using {@code via} would obscure it.
   *
   * <p>Alternative: nested {@code via} calls. Same answer for {@link MaybePath}; misleading name
   * for inputs that do not depend on each other.
   *
   * <p>Common wrong attempt: trying to use {@code zipWith} when inputs are dependent. If we need
   * the second input to be a function of the first, that is what {@code via} is for.
   */
  @Test
  @DisplayName("Exercise 7: zipWith combines independent paths")
  void exercise7_combiningPaths() {
    record User(String name, int age) {}

    MaybePath<String> namePath = Path.just("Alice");
    MaybePath<Integer> agePath = Path.just(30);

    MaybePath<User> userPath = namePath.zipWith(agePath, User::new);

    User user = userPath.getOrElse(new User("unknown", 0));
    assertThat(user.name()).isEqualTo("Alice");
    assertThat(user.age()).isEqualTo(30);

    MaybePath<String> absentName = Path.nothing();
    MaybePath<User> partialUser = absentName.zipWith(agePath, User::new);
    assertThat(partialUser.run().isNothing()).isTrue();

    EitherPath<String, String> firstName = Path.right("Alice");
    EitherPath<String, String> lastName = Path.right("Smith");

    EitherPath<String, String> fullName = firstName.zipWith(lastName, (f, l) -> f + " " + l);
    assertThat(fullName.run().getRight()).isEqualTo("Alice Smith");
  }

  // ─── Exercise 8 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the workflow reads as a sequence of business steps — find, validate,
   * extract, transform — without a single try/catch or null check. Errors short-circuit; we choose
   * where to recover.
   *
   * <p>Alternative: split the chain across local variables when each step is heavy. Same chain,
   * easier to debug.
   *
   * <p>Common wrong attempt: forgetting that {@code recover} can be applied at any point in the
   * chain. We do not have to recover at the end; recovering earlier gives the rest of the chain a
   * value to work with.
   */
  @Test
  @DisplayName("Exercise 8: realistic find → validate → extract → transform workflow")
  void exercise8_realWorldWorkflow() {
    record User(String id, String name, String email) {}

    Function<String, EitherPath<String, User>> findUser =
        id -> {
          if (id.equals("u1")) {
            return Path.right(new User("u1", "Alice", "alice@example.com"));
          } else if (id.equals("u2")) {
            return Path.right(new User("u2", "Bob", ""));
          }
          return Path.left("User not found: " + id);
        };

    Function<User, EitherPath<String, User>> validateEmail =
        user ->
            user.email().isEmpty()
                ? Path.left("Email required for user: " + user.name())
                : Path.right(user);

    EitherPath<String, String> workflow =
        Path.<String, String>right("u1")
            .via(findUser)
            .via(validateEmail)
            .map(User::email)
            .map(String::toUpperCase);

    String result = workflow.run().fold(e -> "Error: " + e, email -> email);
    assertThat(result).isEqualTo("ALICE@EXAMPLE.COM");

    EitherPath<String, String> invalidWorkflow =
        Path.<String, String>right("u2").via(findUser).via(validateEmail).map(User::email);
    assertThat(invalidWorkflow.run().isLeft()).isTrue();
    assertThat(invalidWorkflow.run().getLeft()).contains("Email required");

    EitherPath<String, String> recoveredWorkflow =
        Path.<String, String>right("u999")
            .via(findUser)
            .recover(err -> new User("default", "Guest", "guest@example.com"))
            .map(User::email);
    assertThat(recoveredWorkflow.run().getRight()).isEqualTo("guest@example.com");
  }

  // ─── Diagnostic ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the function returns a Path, so {@code via} is the right call — it
   * flattens the chain and produces a single {@code EitherPath<String, Integer>}.
   *
   * <p>Alternative: {@code input.flatMap(parseNumber)} — exactly the same operation under the
   * Java-style alias.
   *
   * <p>Common wrong attempt: {@code input.map(parseNumber)}. Returns {@code EitherPath<String,
   * EitherPath<String, Integer>>}: still a valid type, no compile error, every downstream {@code
   * .map} now operates on the wrong shape. Watch for this in code review.
   */
  @Test
  @DisplayName("Diagnostic: via for Path-returning functions, map for plain ones")
  void diagnostic_viaVsMap() {
    Function<String, EitherPath<String, Integer>> parseNumber =
        s -> {
          try {
            return Path.right(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            return Path.left("Not a number: " + s);
          }
        };

    EitherPath<String, String> input = Path.right("42");

    EitherPath<String, Integer> flattened = input.via(parseNumber);

    assertThat(flattened.run().isRight()).isTrue();
    assertThat(flattened.run().getRight()).isEqualTo(42);
  }
}
