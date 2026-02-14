// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.reader.Reader;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 07: Real World Applications
 *
 * <p>In this final tutorial, we'll apply everything you've learned to solve real-world problems.
 *
 * <p>You'll build: 1. A complete validation pipeline 2. A data processing workflow 3. A
 * configuration-based application with Reader
 */
public class Tutorial07_RealWorld_Solution {

  /**
   * Exercise 1: Building a validation pipeline
   *
   * <p>Real applications need to validate user input thoroughly.
   *
   * <p>Task: Build a registration validator that checks multiple fields
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

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight().username()).isEqualTo("alice");
  }

  /**
   * Exercise 2: Data processing pipeline
   *
   * <p>Process a stream of data with transformations and error handling.
   *
   * <p>Task: Parse, validate, and transform a list of user records
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
   * Exercise 3: Reader for dependency injection
   *
   * <p>Reader allows you to thread configuration through your application without passing it
   * manually to every function.
   *
   * <p>Task: Build a configuration-based greeting service
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
   * Exercise 4: Combining multiple effects
   *
   * <p>Real applications often need to combine different effects - Maybe + Either, List + Either,
   * etc.
   *
   * <p>Task: Look up users and validate their data
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

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight().name()).isEqualTo("Alice");

    // Solution: Handle missing user by providing a user ID that doesn't exist
    Maybe<User> maybeMissing = findUser.apply("user999");
    Either<String, User> missing =
        maybeMissing.isJust() ? Either.right(maybeMissing.get()) : Either.left("User not found");

    assertThat(missing.isLeft()).isTrue();
    assertThat(missing.getLeft()).isEqualTo("User not found");
  }

  /**
   * Exercise 5: Batch operations with error handling
   *
   * <p>Process multiple items and handle individual failures gracefully.
   *
   * <p>Task: Process a batch of operations and partition successes from failures
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
   * Exercise 6: Building a mini workflow
   *
   * <p>Combine everything: validation, transformation, error handling, and dependency injection.
   *
   * <p>Task: Build an order processing workflow
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
