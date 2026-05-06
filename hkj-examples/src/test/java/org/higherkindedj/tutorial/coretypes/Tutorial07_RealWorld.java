// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.reader.Reader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 07: Real-World Applications.
 *
 * <p>Pain → Promise. Tutorials 01-06 introduced each type and capability in isolation. Production
 * code combines them: validate inputs, parse, look up users, thread configuration through a
 * pipeline. This tutorial is the end-to-end walkthrough that ties Functor, Applicative, Monad,
 * MonadError, Either, Maybe, Validated, and Reader together into three realistic scenarios.
 *
 * <p>You'll build: 1. A complete validation pipeline 2. A data processing workflow 3. A
 * configuration-based application with Reader
 */
public class Tutorial07_RealWorld {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /**
   * Exercise 1: Validation pipeline with nested flatMaps.
   *
   * <pre>
   *   // Nudge:    Each step receives the previous validated value through a tuple of locals.
   *   // Strategy: For each answerRequired() inside .apply(...), pass the field name from the
   *   //           Registration we want to validate (e.g. "alice", "alice@example.com").
   *   //           For the inner .map(age -&gt; answerRequired()), build the Registration record.
   *   // Spoiler:  validateUsername.apply("alice").flatMap(username -&gt;
   *   //              validateEmail.apply("alice@example.com").flatMap(email -&gt; ...
   *   //                  ... .map(age -&gt; new Registration(username, email, password, age))))
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: validation pipeline via nested flatMap")
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

    // TODO: Replace null with code that validates all fields and creates a Registration
    // Hint: Use flatMap to chain validations together
    Either<ValidationError, Registration> result =
        validateUsername
            .apply(answerRequired())
            .flatMap(
                username ->
                    validateEmail
                        .apply(answerRequired())
                        .flatMap(
                            email ->
                                validatePassword
                                    .apply(answerRequired())
                                    .flatMap(
                                        password ->
                                            validateAge
                                                .apply(answerRequired())
                                                .map(age -> answerRequired()))));

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight().username()).isEqualTo("alice");
  }

  /**
   * Exercise 2: Data processing pipeline.
   *
   * <pre>
   *   // Nudge:    Stream over rawData, map through processRecord, filter to Right, extract.
   *   // Strategy: rawData.stream().map(processRecord)
   *   //               .filter(Either::isRight).map(Either::getRight).toList()
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: stream of records through processRecord; keep successes")
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

    // TODO: Replace null with code that:
    // 1. Streams over the raw data to process each record
    // 2. Filters to keep only successful results (Either.isRight())
    // 3. Maps to extract the ProcessedData from successful Eithers
    // 4. Collects to a list
    // Hint:
    // rawData.stream().map(processRecord).filter(Either::isRight).map(Either::getRight).collect(Collectors.toList())
    List<ProcessedData> processed = answerRequired();

    assertThat(processed.size()).isEqualTo(3); // user4 should be filtered out
    assertThat(processed.getFirst().grade()).isEqualTo("A");
  }

  /**
   * Exercise 3: Reader for dependency injection.
   *
   * <pre>
   *   // Nudge:    The lambda inside .map receives the AppConfig; build the versioned greeting.
   *   // Strategy: greeting + " (v" + config.version() + ")"
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: Reader threads configuration through a workflow")
  void exercise3_readerForDependencyInjection() {
    record Config(String appName, String version, boolean debugMode) {}

    record User(String name, String email) {}

    // A Reader that gets the app name from config
    Reader<Config, String> getAppName = Reader.<Config>ask().map(config -> config.appName());

    // A Reader that formats a greeting
    Function<User, Reader<Config, String>> greetUser =
        user ->
            getAppName.map(appName -> String.format("Welcome to %s, %s!", appName, user.name()));

    // TODO: Replace null with code that creates a Reader that:
    // 1. Gets the app version from config
    // 2. Appends it to a greeting message
    Reader<Config, String> getVersionedGreeting =
        greetUser
            .apply(new User("Alice", "alice@example.com"))
            .flatMap(greeting -> Reader.<Config>ask().map(config -> answerRequired()));

    Config config = new Config("MyApp", "1.0.0", false);
    String result = getVersionedGreeting.run(config);

    assertThat(result).contains("Welcome to MyApp, Alice!");
    assertThat(result).contains("1.0.0");
  }

  /**
   * Exercise 4: Combining Maybe + Either.
   *
   * <pre>
   *   // Nudge:    The single answerRequired() in this exercise asks for an id that
   *   //           findUser will not find, e.g. "missing-id".
   *   // Strategy: findUser.apply("missing-id")
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: combine Maybe lookup with Either validation")
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

    // TODO: Replace null with code that:
    // 1. Looks up the user (Maybe)
    // 2. Converts Maybe to Either (use pattern: maybe.isJust() ? Either.right(maybe.get()) :
    // Either.left("error"))
    // 3. Validates the user (Either)
    Maybe<User> maybeUser = findUser.apply("user1");
    Either<String, User> eitherUser =
        maybeUser.isJust() ? Either.right(maybeUser.get()) : Either.left("User not found");
    Either<String, User> result = eitherUser.flatMap(validateUser);

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight().name()).isEqualTo("Alice");

    // TODO: Replace null with code that handles a missing user
    Maybe<User> maybeMissing = findUser.apply(answerRequired());
    Either<String, User> missing =
        maybeMissing.isJust() ? Either.right(maybeMissing.get()) : Either.left("User not found");

    assertThat(missing.isLeft()).isTrue();
    assertThat(missing.getLeft()).isEqualTo("User not found");
  }

  /**
   * Exercise 5: Batch operations partitioned by success / failure.
   *
   * <pre>
   *   // Nudge:    Stream over items, run processItem, collect to list.
   *   // Strategy: items.stream().map(processItem).collect(Collectors.toList())
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5: stream of items into a List<Either> for partitioning")
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

    // TODO: Replace null with code that:
    // 1. Streams over items to process each one (produces Stream<Either<String, String>>)
    // 2. Collects to a list
    // Hint: items.stream().map(processItem).collect(Collectors.toList())
    List<Either<String, String>> processed = answerRequired();

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
   * Exercise 6: Mini order workflow.
   *
   * <pre>
   *   // Nudge:    The .map lambda gets the validated RawOrder; build the ProcessedOrder by
   *   //           applying processOrder.apply(config) to it.
   *   // Strategy: processOrder.apply(config).apply(o)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6: validate then process an order using Reader-style config")
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

    // TODO: Replace null with code that:
    // 1. Validates the order
    // 2. Maps it through the processOrder function with config
    Either<String, ProcessedOrder> result = validateOrder.apply(order).map(o -> answerRequired());

    assertThat(result.isRight()).isTrue();
    ProcessedOrder processed = result.getRight();
    assertThat(processed.subtotal()).isEqualTo(100.0);
    assertThat(processed.total()).isEqualTo(118.0); // 100 + 8 (tax) + 10 (shipping)
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
