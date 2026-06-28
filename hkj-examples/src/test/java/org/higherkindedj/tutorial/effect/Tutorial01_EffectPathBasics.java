// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;

import java.util.function.Function;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.either.Either;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 01: Effect Path Basics — the primary user-facing API.
 *
 * <p>Pain → Promise. Most Java codebases reach for a different shape per "kind of failure":
 *
 * <pre>
 *   User user = repo.findById(id);                  // null on absence
 *   if (user == null) return ErrorResponse.notFound();
 *
 *   try {                                            // exceptions on parse failure
 *     int port = Integer.parseInt(rawPort);
 *   } catch (NumberFormatException e) {
 *     return ErrorResponse.badRequest("not a number");
 *   }
 *
 *   CompletableFuture&lt;Settings&gt; future = config.load();      // future for async
 *   future.thenApply(...).exceptionally(...);                  // and a separate error model
 * </pre>
 *
 * <p>Four different APIs (null check, try/catch, CompletableFuture, custom Result) for what is
 * conceptually the same idea: "compute a value, possibly fail in some way". The {@link Path} family
 * unifies them. Every Path carries success and failure semantics in its type, and every Path
 * supports the same four operations: {@code map}, {@code via}, recovery, and {@code zipWith}.
 *
 * <p>Java idiom anchor.
 *
 * <ul>
 *   <li>{@link MaybePath}{@code <A>} → the {@code Optional<A>} pattern, made composable.
 *   <li>{@link EitherPath}{@code <E, A>} → the {@code Result<E, A>} we keep rolling by hand, with
 *       built-in chain and recover.
 *   <li>{@link TryPath}{@code <A>} → the try/catch ladder, captured in a value.
 *   <li>{@link IOPath}{@code <A>} → a deferred side-effect, like a {@code Supplier} that admits the
 *       kind of pipeline operations {@link java.util.stream.Stream} gives us.
 * </ul>
 *
 * <p>What we will do here:
 *
 * <ol>
 *   <li>Create paths of each shape from concrete values, nullables, and existing values.
 *   <li>Transform values with {@code map} (Functor) and observe that errors pass through unchanged.
 *   <li>Chain dependent steps with {@code via} (Monad) and watch the chain short-circuit.
 *   <li>Recover from errors with {@code recover}, {@code recoverWith}, {@code orElse}, {@code
 *       mapError}.
 *   <li>Combine independent paths with {@code zipWith} (Applicative).
 *   <li>Build one realistic end-to-end workflow, then practise the {@code via} vs {@code map}
 *       diagnostic.
 * </ol>
 *
 * <p>This is the one journey we should complete first if we want to be productive day-to-day. The
 * Foundations chapter explains <em>why</em> the operations behave the way they do; this journey
 * teaches the operations themselves.
 *
 * <p>For the chapter-level overview see <a
 * href="../../../../../../../../../hkj-book/src/effect/effect_path_overview.md">Effect Path
 * Overview</a>; for the production walkthrough see <a
 * href="../../../../../../../../../hkj-book/src/hkts/one_line_six_layers.md">One Line, Six
 * Layers</a>.
 */
@DisplayName("Tutorial 01: Effect Path Basics")
public class Tutorial01_EffectPathBasics {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Part 1: Creating Paths
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: Creating MaybePath.
   *
   * <p>{@link MaybePath} represents "a value that may or may not be present", the same idea as
   * {@link java.util.Optional}. Three factory methods: {@code Path.just(value)}, {@code
   * Path.nothing()}, and {@code Path.maybe(nullable)} (which converts {@code null} to {@code
   * Nothing}).
   *
   * <pre>
   *   // Nudge:    Three different ways to build a MaybePath.
   *   // Strategy: Path.just(...), Path.nothing(), Path.maybe(...).
   *   // Spoiler:  Path.just("hello") / Path.nothing() / Path.maybe(nullable)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: build MaybePath with just / nothing / maybe")
  void exercise1_creatingMaybePath() {
    MaybePath<String> present = answerRequired();

    assertThatMaybe(present.run()).isJust();
    assertThat(present.getOrElse("default")).isEqualTo("hello");

    MaybePath<String> absent = answerRequired();

    assertThatMaybe(absent.run()).isNothing();
    assertThat(absent.getOrElse("default")).isEqualTo("default");

    String nullable = null;
    MaybePath<String> fromNullable = answerRequired();

    assertThatMaybe(fromNullable.run()).isNothing();

    // Sanity: non-null values become Just.
    MaybePath<String> fromNonNull = Path.maybe("world");
    assertThatMaybe(fromNonNull.run()).isJust();
  }

  /**
   * Exercise 2: Creating EitherPath.
   *
   * <p>{@link EitherPath} is the workhorse for typed errors. {@code Path.right(value)} for success,
   * {@code Path.left(error)} for failure, {@code Path.either(existing)} to lift an existing {@link
   * Either} value.
   *
   * <pre>
   *   // Nudge:    Three factory methods, one per starting condition.
   *   // Strategy: Path.right(...), Path.left(...), Path.either(...).
   *   // Spoiler:  Path.right(42) / Path.left("Invalid input") / Path.either(either)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: build EitherPath with right / left / either")
  void exercise2_creatingEitherPath() {
    EitherPath<String, Integer> success = answerRequired();

    assertThatEither(success.run()).isRight().hasRight(42);

    EitherPath<String, Integer> failure = answerRequired();

    assertThatEither(failure.run()).isLeft().hasLeft("Invalid input");

    Either<String, Integer> either = Either.right(100);
    EitherPath<String, Integer> fromEither = answerRequired();

    assertThatEither(fromEither.run()).hasRight(100);
  }

  /**
   * Exercise 3: TryPath and IOPath.
   *
   * <p>{@link TryPath} captures a potentially throwing computation as a value: the result is {@code
   * Success} or {@code Failure}, never an in-flight exception. {@link IOPath} captures a
   * side-effecting computation and defers its execution until {@code unsafeRun()} is called — the
   * cornerstone of the "pure core, impure shell" pattern.
   *
   * <pre>
   *   // Nudge:    Path.tryOf wraps a Supplier that may throw; Path.io defers a side effect.
   *   // Strategy: Path.tryOf(() -&gt; Integer.parseInt("42"))
   *   //           Path.io(() -&gt; { counter[0]++; return counter[0]; })
   *   // Spoiler:  see hint above.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: TryPath captures throw; IOPath defers a side effect")
  void exercise3_tryPathAndIOPath() {
    TryPath<Integer> successTry = answerRequired();

    assertThatTry(successTry.run()).isSuccess();
    assertThat(successTry.getOrElse(-1)).isEqualTo(42);

    // Sanity: invalid input becomes Failure.
    TryPath<Integer> failureTry = Path.tryOf(() -> Integer.parseInt("not a number"));
    assertThatTry(failureTry.run()).isFailure();

    var counter = new int[] {0};
    IOPath<Integer> ioPath = answerRequired();

    // IO is lazy: the counter has not been touched yet.
    assertThat(counter[0]).isEqualTo(0);

    Integer result = ioPath.unsafeRun();
    assertThat(counter[0]).isEqualTo(1);
    assertThat(result).isEqualTo(1);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Part 2: Transforming and Chaining
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 4: Transforming with {@code map} (Functor).
   *
   * <p>{@code map} transforms the success value while preserving the effect structure. Errors
   * (Nothing, Left, Failure) pass through untouched.
   *
   * <pre>
   *   // Nudge:    map applies a Function&lt;A, B&gt; to the success channel.
   *   // Strategy: name.map(String::toUpperCase) / success.map(n -&gt; n * 2)
   *   // Spoiler:  name.map(String::toUpperCase) and success.map(n -&gt; n * 2)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: map transforms success values; errors pass through")
  void exercise4_transformingWithMap() {
    MaybePath<String> name = Path.just("alice");
    MaybePath<String> upperName = answerRequired();

    assertThat(upperName.getOrElse("")).isEqualTo("ALICE");

    // Sanity: map on Nothing is a no-op.
    MaybePath<String> absent = Path.<String>nothing().map(String::toUpperCase);
    assertThatMaybe(absent.run()).isNothing();

    EitherPath<String, Integer> success = Path.right(10);
    EitherPath<String, Integer> doubled = answerRequired();

    assertThatEither(doubled.run()).hasRight(20);

    // Sanity: map on Left is a no-op.
    EitherPath<String, Integer> failure = Path.<String, Integer>left("error").map(n -> n * 2);
    assertThatEither(failure.run()).hasLeft("error");
  }

  /**
   * Exercise 5: Chaining with {@code via} (Monad).
   *
   * <p>{@code via} (also called {@code flatMap}) chains computations where each step depends on the
   * previous result and may itself fail. If any step fails, the chain short-circuits and the
   * downstream steps are never invoked.
   *
   * <pre>
   *   // Nudge:    Three dependent steps -&gt; three via calls.
   *   // Strategy: input.via(parseNumber).via(validatePositive).via(divideHundredBy)
   *   // Spoiler:  exactly that.
   * </pre>
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

    EitherPath<String, Double> result = answerRequired();

    assertThatEither(result.run()).isRight().hasRight(4.0);

    // Sanity: bad input short-circuits at the first failing step.
    EitherPath<String, String> invalidInput = Path.right("not-a-number");
    EitherPath<String, Double> failedResult =
        invalidInput.via(parseNumber).via(validatePositive).via(divideHundredBy);

    assertThatEither(failedResult.run()).isLeft().hasLeft("Not a number: not-a-number");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Part 3: Error Recovery
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 6: Recovery — replace errors, swap to alternatives, transform error types.
   *
   * <p>Four operations cover the recovery surface:
   *
   * <ul>
   *   <li>{@code recover(err -> value)} — replace the error with a success value.
   *   <li>{@code recoverWith(err -> path)} — replace with another path computation.
   *   <li>{@code orElse(() -> path)} — provide an alternative path, error untouched.
   *   <li>{@code mapError(err -> e2)} — transform the error type without recovering.
   * </ul>
   *
   * <pre>
   *   // Nudge:    Each operation is "what to do with the Left".
   *   // Strategy: failure.recover(err -&gt; -1), failure.recoverWith(err -&gt; Path.right(0)),
   *   //           primary.orElse(() -&gt; fallback), stringError.mapError(String::length)
   *   // Spoiler:  see hint above.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6: recover / recoverWith / orElse / mapError")
  void exercise6_errorRecovery() {
    EitherPath<String, Integer> failure = Path.left("Not found");

    EitherPath<String, Integer> recovered = answerRequired();

    assertThatEither(recovered.run()).isRight().hasRight(-1);

    EitherPath<String, Integer> recoveredWith = answerRequired();

    assertThatEither(recoveredWith.run()).hasRight(0);

    EitherPath<String, Integer> primary = Path.left("Primary failed");
    EitherPath<String, Integer> fallback = Path.right(42);

    EitherPath<String, Integer> withFallback = answerRequired();

    assertThatEither(withFallback.run()).hasRight(42);

    EitherPath<String, Integer> stringError = Path.left("error");
    EitherPath<Integer, Integer> intError = answerRequired();

    assertThatEither(intError.run()).hasLeft(5);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Part 4: Combining and a real workflow
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 7: Combining independent paths with {@code zipWith} (Applicative).
   *
   * <p>{@code zipWith} runs two independent paths and combines the results with a function. If
   * either fails, the result fails. This is the Path-API spelling of {@code Applicative.map2} from
   * Tutorial 03 of the Foundations journey.
   *
   * <pre>
   *   // Nudge:    Combine name and age with the User constructor.
   *   // Strategy: namePath.zipWith(agePath, User::new)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 7: zipWith combines independent paths")
  void exercise7_combiningPaths() {
    record User(String name, int age) {}

    MaybePath<String> namePath = Path.just("Alice");
    MaybePath<Integer> agePath = Path.just(30);

    MaybePath<User> userPath = answerRequired();

    User user = userPath.getOrElse(new User("unknown", 0));
    assertThat(user.name()).isEqualTo("Alice");
    assertThat(user.age()).isEqualTo(30);

    // Sanity: if either input is Nothing, the result is Nothing.
    MaybePath<String> absentName = Path.nothing();
    MaybePath<User> partialUser = absentName.zipWith(agePath, User::new);
    assertThatMaybe(partialUser.run()).isNothing();

    EitherPath<String, String> firstName = Path.right("Alice");
    EitherPath<String, String> lastName = Path.right("Smith");

    EitherPath<String, String> fullName = answerRequired();

    assertThatEither(fullName.run()).hasRight("Alice Smith");
  }

  /**
   * Exercise 8: A realistic end-to-end workflow.
   *
   * <p>Combine {@code via} for dependent steps, {@code map} for transformations, and {@code
   * recover} for graceful degradation when the user is missing.
   *
   * <pre>
   *   // Nudge:    Start with a Path containing the id, then via through findUser and
   *   //           validateEmail, then map to extract and uppercase the email.
   *   // Strategy: Path.&lt;String, String&gt;right("u1").via(findUser).via(validateEmail)
   *   //                .map(User::email).map(String::toUpperCase)
   *   // Spoiler:  exactly that.
   * </pre>
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

    EitherPath<String, String> workflow = answerRequired();

    String result = workflow.run().fold(e -> "Error: " + e, email -> email);
    assertThat(result).isEqualTo("ALICE@EXAMPLE.COM");

    // Sanity: u2 has no email, so validation fails.
    EitherPath<String, String> invalidWorkflow =
        Path.<String, String>right("u2").via(findUser).via(validateEmail).map(User::email);

    assertThatEither(invalidWorkflow.run())
        .isLeft()
        .hasLeftSatisfying(error -> assertThat(error).contains("Email required"));

    // Sanity: missing user, recovered with a default.
    EitherPath<String, String> recoveredWorkflow =
        Path.<String, String>right("u999")
            .via(findUser)
            .recover(err -> new User("default", "Guest", "guest@example.com"))
            .map(User::email);

    assertThatEither(recoveredWorkflow.run()).hasRight("guest@example.com");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Diagnostic: Things People Get Wrong
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Diagnostic: {@code map} vs {@code via}.
   *
   * <p>{@code map} takes a function {@code A -> B}; {@code via} takes a function {@code A ->
   * Path<B>}. Mix them up and the type system catches it — but only sometimes. When the function
   * returns a compatible Path under {@code map}, we end up with a nested {@code EitherPath<E,
   * EitherPath<E, A>>} that compiles fine and silently hides values. This is the Effect Path
   * equivalent of the Tutorial 02 diagnostic in the Foundations journey.
   *
   * <p>Below, {@code parseNumber} returns an {@code EitherPath}. Calling it inside {@code .map}
   * compiles and produces nested paths; calling it inside {@code .via} flattens.
   *
   * <pre>
   *   // Nudge:    parseNumber returns a Path; we want a single Path, not nested ones.
   *   // Strategy: input.via(parseNumber)
   *   // Spoiler:  exactly that.
   * </pre>
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

    // Wrong: input.map(parseNumber) returns EitherPath&lt;String, EitherPath&lt;String,
    // Integer&gt;&gt;.
    // The outer assertions would still pass (the Right is the inner Path), but every downstream
    // .map / .via would now operate on the wrong shape.

    // TODO: replace answerRequired() with input.via(parseNumber).
    EitherPath<String, Integer> flattened = answerRequired();

    assertThatEither(flattened.run()).isRight().hasRight(42);

    // For comparison: the wrong call shape, kept as a comment because it produces nested paths
    // that the type system cannot tell us are wrong:
    // EitherPath&lt;String, EitherPath&lt;String, Integer&gt;&gt; nested = input.map(parseNumber);
  }

  /*
   * Where to next?
   *   • Tutorial 02 — Effect Path Advanced. ForPath comprehensions for readable multi-step
   *     workflows; Effect Contexts (ErrorContext, ConfigContext, MutableContext); the
   *     @GeneratePathBridge annotation pattern; Focus-Effect integration.
   *   • Foundations chapter — One Line, Six Layers. The chapter-level anchor that frames every
   *     Effect Path operation in the broader stack.
   */
}
