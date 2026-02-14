// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.Optional;
import org.higherkindedj.hkt.effect.context.ConfigContext;
import org.higherkindedj.hkt.effect.context.ErrorContext;
import org.higherkindedj.hkt.effect.context.JavaOptionalContext;
import org.higherkindedj.hkt.effect.context.MutableContext;
import org.higherkindedj.hkt.effect.context.OptionalContext;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.state.StateTuple;

/**
 * Demonstrates the Effect Context API for user-friendly monad transformer usage.
 *
 * <p>Effect Contexts provide a Layer 2 abstraction over monad transformers, hiding the complexity
 * of higher-kinded types while preserving full monadic composition capabilities.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>{@link ErrorContext} - Typed error handling (wraps EitherT)
 *   <li>{@link OptionalContext} - Optional values with Maybe (wraps MaybeT)
 *   <li>{@link JavaOptionalContext} - Optional values with java.util.Optional (wraps OptionalT)
 *   <li>{@link ConfigContext} - Dependency injection (wraps ReaderT)
 *   <li>{@link MutableContext} - Stateful computations (wraps StateT)
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.EffectContextExample}
 */
public class EffectContextExample {

  public static void main(String[] args) {
    System.out.println("=== Effect Context API Examples ===\n");

    errorContextExample();
    optionalContextExample();
    javaOptionalContextExample();
    configContextExample();
    mutableContextExample();
    combinedWorkflowExample();
  }

  // ========== ErrorContext Examples ==========

  private static void errorContextExample() {
    System.out.println("--- ErrorContext: Typed Error Handling ---");

    // Creating success and failure contexts
    ErrorContext<?, String, Integer> success = ErrorContext.success(42);
    ErrorContext<?, String, Integer> failure = ErrorContext.failure("Invalid input");

    // Transforming with map
    Either<String, Integer> doubled = success.map(x -> x * 2).runIO().unsafeRun();
    System.out.println("Doubled success: " + doubled.getRight()); // 84

    // Chaining with via (flatMap)
    ErrorContext<?, String, String> workflow =
        ErrorContext.<String, Integer>success(10)
            .via(x -> x > 5 ? ErrorContext.success("big: " + x) : ErrorContext.failure("too small"))
            .map(String::toUpperCase);

    Either<String, String> result = workflow.runIO().unsafeRun();
    System.out.println("Workflow result: " + result.getRight()); // BIG: 10

    // Error recovery
    Either<String, Integer> recovered = failure.recover(err -> -1).runIO().unsafeRun();
    System.out.println("Recovered from error: " + recovered.getRight()); // -1

    // Error transformation
    Either<Integer, Integer> mappedError = failure.mapError(String::length).runIO().unsafeRun();
    System.out.println("Error mapped to length: " + mappedError.getLeft()); // 13

    System.out.println();
  }

  // ========== OptionalContext Examples ==========

  private static void optionalContextExample() {
    System.out.println("--- OptionalContext: Optional Values (Maybe) ---");

    // Creating contexts
    OptionalContext<?, Integer> some = OptionalContext.some(42);
    OptionalContext<?, Integer> none = OptionalContext.none();

    // Transforming with map
    Maybe<Integer> doubled = some.map(x -> x * 2).runIO().unsafeRun();
    System.out.println("Doubled some: " + doubled.get()); // 84

    // Chaining - short-circuits on none
    OptionalContext<?, String> workflow =
        OptionalContext.<Integer>some(5)
            .via(x -> x > 0 ? OptionalContext.some("positive: " + x) : OptionalContext.none())
            .map(String::toUpperCase);

    Maybe<String> result = workflow.runIO().unsafeRun();
    System.out.println("Workflow result: " + result.get()); // POSITIVE: 5

    // Recovery
    Maybe<Integer> recovered = none.orElseValue(99).runIO().unsafeRun();
    System.out.println("Recovered from none: " + recovered.get()); // 99

    // Alternative with orElse
    Maybe<Integer> alternative =
        OptionalContext.<Integer>none().orElse(() -> OptionalContext.some(100)).runIO().unsafeRun();
    System.out.println("Alternative value: " + alternative.get()); // 100

    System.out.println();
  }

  // ========== JavaOptionalContext Examples ==========

  private static void javaOptionalContextExample() {
    System.out.println("--- JavaOptionalContext: Optional Values (java.util.Optional) ---");

    // Creating contexts - works with Java's Optional
    JavaOptionalContext<?, Integer> some = JavaOptionalContext.some(42);
    JavaOptionalContext<?, Integer> none = JavaOptionalContext.none();
    JavaOptionalContext<?, String> fromOptional =
        JavaOptionalContext.fromOptional(Optional.of("hello"));

    // Transforming with map
    Optional<Integer> doubled = some.map(x -> x * 2).runIO().unsafeRun();
    System.out.println("Doubled some: " + doubled.get()); // 84

    // Chaining computations
    Optional<String> result =
        JavaOptionalContext.<Integer>some(10)
            .via(x -> JavaOptionalContext.some("value: " + x))
            .map(String::toUpperCase)
            .runIO()
            .unsafeRun();
    System.out.println("Chained result: " + result.get()); // VALUE: 10

    // Convenience methods
    Integer orElse = none.runIOOrElse(0);
    System.out.println("Run or else: " + orElse); // 0

    // Convert to ErrorContext
    Either<String, Integer> asError = none.toErrorContext("not found").runIO().unsafeRun();
    System.out.println("Converted to error: " + asError.getLeft()); // not found

    System.out.println();
  }

  // ========== ConfigContext Examples ==========

  /** Sample configuration record for demonstrating ConfigContext. */
  record AppConfig(String apiUrl, int timeout, boolean debug) {}

  private static void configContextExample() {
    System.out.println("--- ConfigContext: Dependency Injection ---");

    AppConfig config = new AppConfig("https://api.example.com", 30, true);

    // Reading configuration
    ConfigContext<?, AppConfig, String> getUrl =
        ConfigContext.<AppConfig>ask().map(AppConfig::apiUrl);
    String url = getUrl.runWithSync(config);
    System.out.println("API URL: " + url); // https://api.example.com

    // Composing config-dependent operations
    ConfigContext<?, AppConfig, String> workflow =
        ConfigContext.<AppConfig>ask()
            .via(cfg -> ConfigContext.pure(cfg.apiUrl() + "/users"))
            .via(
                endpoint ->
                    ConfigContext.<AppConfig, String>io(
                        cfg -> endpoint + "?timeout=" + cfg.timeout()));

    String result = workflow.runWithSync(config);
    System.out.println("Built URL: " + result); // https://api.example.com/users?timeout=30

    // Local modification of config
    ConfigContext<?, AppConfig, Integer> doubled =
        ConfigContext.<AppConfig, Integer>io(cfg -> cfg.timeout())
            .local(cfg -> new AppConfig(cfg.apiUrl(), cfg.timeout() * 2, cfg.debug()));

    Integer doubledTimeout = doubled.runWithSync(config);
    System.out.println("Doubled timeout: " + doubledTimeout); // 60

    System.out.println();
  }

  // ========== MutableContext Examples ==========

  /** Counter state for demonstrating MutableContext. */
  record Counter(int count) {}

  private static void mutableContextExample() {
    System.out.println("--- MutableContext: Stateful Computations ---");

    Counter initial = new Counter(0);

    // Get current state
    Counter current = MutableContext.<Counter>get().evalWith(initial).unsafeRun();
    System.out.println("Initial count: " + current.count()); // 0

    // Modify state
    Counter modified =
        MutableContext.<Counter>modify(c -> new Counter(c.count() + 10))
            .execWith(initial)
            .unsafeRun();
    System.out.println("After adding 10: " + modified.count()); // 10

    // Chain stateful operations
    MutableContext<?, Counter, String> workflow =
        MutableContext.<Counter>modify(c -> new Counter(c.count() + 1))
            .then(() -> MutableContext.modify(c -> new Counter(c.count() + 2)))
            .then(() -> MutableContext.modify(c -> new Counter(c.count() + 3)))
            .then(() -> MutableContext.<Counter>get().map(c -> "Final count: " + c.count()));

    StateTuple<Counter, String> result = workflow.runWith(initial).unsafeRun();
    System.out.println(result.value()); // Final count: 6
    System.out.println("Final state: " + result.state().count()); // 6

    // Put replaces state entirely
    Counter replaced = MutableContext.<Counter>put(new Counter(100)).execWith(initial).unsafeRun();
    System.out.println("After put(100): " + replaced.count()); // 100

    System.out.println();
  }

  // ========== Combined Workflow Example ==========

  /** User record for combined workflow example. */
  record User(String id, String name, String email) {}

  /** Database configuration for combined workflow. */
  record DbConfig(String connectionUrl, int maxRetries) {}

  private static void combinedWorkflowExample() {
    System.out.println("--- Combined Workflow: Real-World Pattern ---");

    // Simulate a service that looks up users
    // Using ConfigContext for dependency injection

    DbConfig dbConfig = new DbConfig("jdbc:postgresql://localhost/mydb", 3);

    // Simulate finding a user (would normally be a database call)
    ConfigContext<?, DbConfig, User> findUser =
        ConfigContext.<DbConfig>ask()
            .via(
                cfg -> {
                  // In reality, this would query the database
                  System.out.println("  Connecting to: " + cfg.connectionUrl());
                  return ConfigContext.pure(new User("u123", "Alice", "alice@example.com"));
                });

    // Transform the user
    ConfigContext<?, DbConfig, String> workflow =
        findUser.map(User::email).map(String::toUpperCase);

    String result = workflow.runWithSync(dbConfig);
    System.out.println("User email: " + result); // ALICE@EXAMPLE.COM

    // ErrorContext for validation
    ErrorContext<?, String, User> validateUser =
        ErrorContext.<String, User>success(new User("u123", "Bob", "bob@example.com"))
            .via(
                user ->
                    user.email().contains("@")
                        ? ErrorContext.success(user)
                        : ErrorContext.failure("Invalid email"));

    Either<String, User> validated = validateUser.runIO().unsafeRun();
    System.out.println("Validated user: " + validated.getRight().name()); // Bob

    System.out.println();
    System.out.println("=== Effect Context Examples Complete ===");
  }
}
