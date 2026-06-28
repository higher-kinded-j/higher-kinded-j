// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.reader.Reader;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial07 RealWorld — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
public class Tutorial07_RealWorld_Solution {

  /**
   * Why this is idiomatic: nested {@code flatMap} chains thread the validated values into the final
   * {@code Registration} constructor — each {@code flatMap} is "given the previous good value, run
   * the next check". The first {@code Left} short-circuits the rest.
   *
   * <p>Alternative: use {@code Validated} + a {@code Semigroup} (Tutorial 06 Exercise 5) to collect
   * every error at once. Pick that when the user wants the full report; pick this fail-fast {@code
   * Either} chain when the next check would not run anyway (e.g. once the username is invalid the
   * rest is moot).
   *
   * <p>Common wrong attempt: assembling the {@code Registration} with raw record arguments before
   * validating, then validating the whole record. That works but the constructor now accepts inputs
   * the system never wants to allow — push validation to the boundary where the inputs first
   * arrive.
   */
  @Test
  void exercise1_validationPipeline() {
    record Registration(String username, String email, String password, int age) {}

    record ValidationError(String field, String message) {}

    Function<String, Either<ValidationError, String>> validateUsername =
        username -> {
          if (username == null || username.length() < 3) {
            return Either.left(new ValidationError("username", "Must be at least 3 characters"));
          }
          return Either.right(username);
        };

    Function<String, Either<ValidationError, String>> validateEmail =
        email -> {
          if (email == null || !email.contains("@")) {
            return Either.left(new ValidationError("email", "Invalid email format"));
          }
          return Either.right(email);
        };

    Function<String, Either<ValidationError, String>> validatePassword =
        password -> {
          if (password == null || password.length() < 8) {
            return Either.left(new ValidationError("password", "Must be at least 8 characters"));
          }
          return Either.right(password);
        };

    Function<Integer, Either<ValidationError, Integer>> validateAge =
        age -> {
          if (age < 18) {
            return Either.left(new ValidationError("age", "Must be at least 18"));
          }
          return Either.right(age);
        };

    // Solution: Chain all validations together using flatMap
    Either<ValidationError, Registration> result =
        validateUsername
            .apply("alice")
            .flatMap(
                username ->
                    validateEmail
                        .apply("alice@example.com")
                        .flatMap(
                            email ->
                                validatePassword
                                    .apply("password123")
                                    .flatMap(
                                        password ->
                                            validateAge
                                                .apply(25)
                                                .map(
                                                    age ->
                                                        new Registration(
                                                            username, email, password, age)))));

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(reg -> assertThat(reg.username()).isEqualTo("alice"));
  }

  /**
   * Why this is idiomatic: each record is processed independently into an {@code Either}, then a
   * single stream pass keeps the right-handed values. Per-record failure does not abort the batch —
   * exactly the semantics a data-processing job usually wants.
   *
   * <p>Alternative: a {@code Validated}-based aggregation that returns every parse failure
   * alongside the successes. Use it when failures need surfacing rather than being dropped, e.g.
   * when the caller wants a "rejected rows" report.
   *
   * <p>Common wrong attempt: a {@code try/catch} inside the stream that returns {@code null} for
   * failed parses, then a {@code .filter(Objects::nonNull)}. The pipeline still works, but every
   * downstream call has to remember the implicit {@code null} contract; a typed {@code Either}
   * makes the failure mode visible in the signature.
   */
  @Test
  void exercise2_dataProcessingPipeline() {
    record RawData(String userId, String scoreStr) {}

    record ProcessedData(String userId, int score, String grade) {}

    List<RawData> rawData =
        List.of(
            new RawData("user1", "95"),
            new RawData("user2", "82"),
            new RawData("user3", "67"),
            new RawData("user4", "invalid"));

    Function<String, Either<String, Integer>> parseScore =
        str -> {
          try {
            return Either.right(Integer.parseInt(str));
          } catch (NumberFormatException e) {
            return Either.left("Invalid score");
          }
        };

    Function<Integer, String> calculateGrade =
        score -> {
          if (score >= 90) return "A";
          if (score >= 80) return "B";
          if (score >= 70) return "C";
          if (score >= 60) return "D";
          return "F";
        };

    Function<RawData, Either<String, ProcessedData>> processRecord =
        raw ->
            parseScore
                .apply(raw.scoreStr())
                .map(score -> new ProcessedData(raw.userId(), score, calculateGrade.apply(score)));

    // Solution: Stream, map to process, filter successes, extract values, collect
    List<ProcessedData> processed =
        rawData.stream()
            .map(processRecord)
            .filter(Either::isRight)
            .map(Either::getRight)
            .collect(Collectors.toList());

    assertThat(processed.size()).isEqualTo(3); // user4 should be filtered out
    assertThat(processed.getFirst().grade()).isEqualTo("A");
  }

  /**
   * Why this is idiomatic: {@code Reader.<Config>ask().map(...)} captures "I will need the config
   * later" without naming a config parameter on every helper. {@code flatMap} chains the
   * environment through the next step — the {@code Config} only appears once, at the call to {@code
   * run}.
   *
   * <p>Alternative: pass the {@code Config} explicitly through every method signature. Honest and
   * obvious for short call chains; gets noisy once five functions all need three different fields.
   * {@code Reader} is the right tool when the environment is wide and shared.
   *
   * <p>Common wrong attempt: store the config in a static or {@code ThreadLocal}. It works locally
   * but turns "what does this function depend on?" into a global hunt — the {@code Reader<Config,
   * A>} return type states the dependency in the signature.
   */
  @Test
  void exercise3_readerForDependencyInjection() {
    record Config(String appName, String version, boolean debugMode) {}

    record User(String name, String email) {}

    // A Reader that gets the app name from config
    Reader<Config, String> getAppName = Reader.<Config>ask().map(config -> config.appName());

    // A Reader that formats a greeting
    Function<User, Reader<Config, String>> greetUser =
        user ->
            getAppName.map(appName -> String.format("Welcome to %s, %s!", appName, user.name()));

    // Solution: Combine greeting with version information
    Reader<Config, String> getVersionedGreeting =
        greetUser
            .apply(new User("Alice", "alice@example.com"))
            .flatMap(
                greeting ->
                    Reader.<Config>ask().map(config -> greeting + " (v" + config.version() + ")"));

    Config config = new Config("MyApp", "1.0.0", false);
    String result = getVersionedGreeting.run(config);

    assertThat(result).contains("Welcome to MyApp, Alice!");
    assertThat(result).contains("1.0.0");
  }

  /**
   * Why this is idiomatic: lift the {@code Maybe} into an {@code Either} at the boundary, then
   * chain validation with {@code flatMap}. The seam — "missing user" — picks up its error string at
   * the conversion site, and the rest of the pipeline only deals with one effect.
   *
   * <p>Alternative: stack monad transformers (e.g. {@code MaybeT<EitherKind, ...>}) to keep both
   * effects nested without lifting. More powerful for deep stacks; here, a single conversion is the
   * smaller hammer.
   *
   * <p>Common wrong attempt: keep the {@code Maybe} and call {@code maybe.flatMap(validateUser)},
   * which fails to compile because {@code validateUser} returns {@code Either} not {@code Maybe}.
   * The fix is to choose one effect for the pipeline and lift the other to it.
   */
  @Test
  void exercise4_combiningEffects() {
    record User(String id, String name, int age) {}

    // Database lookup (might not find the user)
    Function<String, Maybe<User>> findUser =
        id -> {
          if (id.equals("user1")) {
            return Maybe.just(new User("user1", "Alice", 25));
          } else {
            return Maybe.nothing();
          }
        };

    // Validation (might fail)
    Function<User, Either<String, User>> validateUser =
        user -> {
          if (user.age() < 18) {
            return Either.left("User too young");
          }
          return Either.right(user);
        };

    // Solution: This code is already implemented in the tutorial file
    Maybe<User> maybeUser = findUser.apply("user1");
    Either<String, User> eitherUser =
        maybeUser.isJust() ? Either.right(maybeUser.get()) : Either.left("User not found");
    Either<String, User> result = eitherUser.flatMap(validateUser);

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(user -> assertThat(user.name()).isEqualTo("Alice"));

    // Solution: Handle missing user by providing a user ID that doesn't exist
    Maybe<User> maybeMissing = findUser.apply("user999");
    Either<String, User> missing =
        maybeMissing.isJust() ? Either.right(maybeMissing.get()) : Either.left("User not found");

    assertThatEither(missing).isLeft().hasLeft("User not found");
  }

  /**
   * Why this is idiomatic: keep the {@code Either<E, A>} per item and partition at the end — one
   * stream pass for the successes, one for the failures. Both lists end up materialised exactly
   * once, with no shared mutable state.
   *
   * <p>Alternative: {@code Collectors.partitioningBy(Either::isRight)} into a {@code Map<Boolean,
   * List<Either<...>>>}. Single pass, but the result still holds {@code Either} on each side and
   * the caller has to map each list back to plain values.
   *
   * <p>Common wrong attempt: a single {@code reduce} that mutates two {@code ArrayList}s. Works for
   * sequential streams; breaks under {@code .parallelStream()} where the accumulator is called
   * concurrently. The two-pass partition shown here is parallel-safe.
   */
  @Test
  void exercise5_batchOperations() {
    record Result(List<String> successes, List<String> failures) {}

    Function<Integer, Either<String, String>> processItem =
        n -> {
          if (n % 2 == 0) {
            return Either.right("Processed: " + n);
          } else {
            return Either.left("Failed: " + n);
          }
        };

    List<Integer> items = List.of(1, 2, 3, 4, 5, 6);

    // Solution: Stream over items, map through processItem, collect to list
    List<Either<String, String>> processed =
        items.stream().map(processItem).collect(Collectors.toList());

    List<String> successes =
        processed.stream()
            .filter(Either::isRight)
            .map(Either::getRight)
            .collect(Collectors.toList());
    List<String> failures =
        processed.stream().filter(Either::isLeft).map(Either::getLeft).collect(Collectors.toList());

    Result result = new Result(successes, failures);

    assertThat(result.successes().size()).isEqualTo(3);
    assertThat(result.failures().size()).isEqualTo(3);
  }

  /**
   * Why this is idiomatic: validate first (fail-fast on a bad order), then map the validated order
   * through the {@code Config}-aware pricer. Validation owns "is this allowed?", processing owns
   * "given it is allowed, what does it cost?", and the {@code map} keeps the order short-circuiting
   * on failure.
   *
   * <p>Alternative: {@code Reader<Config, Function<RawOrder, Either<String, ProcessedOrder>>>} —
   * let the {@code Reader} layer thread the config and have validation and processing share the
   * same environment. Cleaner once the workflow grows several config-dependent steps.
   *
   * <p>Common wrong attempt: capture the {@code config} inside the validator's closure so it can
   * read tax rates "for early rejection". Now validation depends on config and tests have to
   * construct a config to exercise validation rules — keep validation pure of business config when
   * you can.
   */
  @Test
  void exercise6_miniWorkflow() {
    record Config(double taxRate, double shippingCost) {}

    record RawOrder(String productId, int quantity, double price) {}

    record ProcessedOrder(String productId, int quantity, double subtotal, double total) {}

    Function<RawOrder, Either<String, RawOrder>> validateOrder =
        order -> {
          if (order.quantity() <= 0) {
            return Either.left("Invalid quantity");
          }
          if (order.price() <= 0) {
            return Either.left("Invalid price");
          }
          return Either.right(order);
        };

    Function<Config, Function<RawOrder, ProcessedOrder>> processOrder =
        config ->
            order -> {
              double subtotal = order.quantity() * order.price();
              double tax = subtotal * config.taxRate();
              double total = subtotal + tax + config.shippingCost();
              return new ProcessedOrder(order.productId(), order.quantity(), subtotal, total);
            };

    RawOrder order = new RawOrder("PROD-001", 2, 50.0);
    Config config = new Config(0.08, 10.0);

    // Solution: Validate order and then process it with config
    Either<String, ProcessedOrder> result =
        validateOrder.apply(order).map(o -> processOrder.apply(config).apply(o));

    assertThatEither(result)
        .isRight()
        .hasRightSatisfying(
            processed -> {
              assertThat(processed.subtotal()).isEqualTo(100.0);
              assertThat(processed.total()).isEqualTo(118.0); // 100 + 8 (tax) + 10 (shipping)
            });
  }

  /**
   * Congratulations! You've completed Tutorial 07: Real World Applications
   *
   * <p>You now understand: ✓ How to build complete validation pipelines ✓ How to process data with
   * error handling ✓ How to use Reader for dependency injection ✓ How to combine different effects
   * (Maybe + Either, List + Either) ✓ How to handle batch operations ✓ How to build real-world
   * workflows
   *
   * <p>You've completed the Core Types tutorial series! 🎉
   *
   * <p>Next: Start the Optics tutorial series to learn about Lenses, Prisms, and Traversals!
   */
}
