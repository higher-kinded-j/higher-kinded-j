// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.context.ConfigContext;
import org.higherkindedj.hkt.effect.context.ErrorContext;
import org.higherkindedj.hkt.effect.context.MutableContext;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 02: Effect Path Advanced - ForPath, Contexts, and Integration
 *
 * <p>This tutorial covers advanced Effect Path usage including:
 *
 * <ul>
 *   <li>ForPath comprehensions for readable multi-step workflows
 *   <li>Effect Contexts (ErrorContext, ConfigContext, MutableContext)
 *   <li>@GeneratePathBridge annotations for service integration
 *   <li>Focus-Effect integration for optics within effect contexts
 * </ul>
 *
 * <p>Prerequisites: Complete Tutorial 01 - Effect Path Basics
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
public class Tutorial02_EffectPathAdvanced_Solution {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Part 1: ForPath Comprehensions
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: ForPath Basics
   *
   * <p>ForPath provides for-comprehension style syntax for chaining paths. It is syntactic sugar
   * over via/flatMap that improves readability for multi-step workflows.
   *
   * <p>Key methods:
   *
   * <ul>
   *   <li>{@code from(path)} - Start with a path or chain another path computation
   *   <li>{@code let(f)} - Bind a pure value without creating a new path
   *   <li>{@code yield(f)} - Produce the final result from bound values
   * </ul>
   *
   * <p>Task: Use ForPath to chain operations
   */
  @Test
  void exercise1_forPathBasics() {
    // ForPath.from starts a comprehension
    // SOLUTION: Use ForPath.from to chain paths and yield the sum
    MaybePath<Integer> result =
        ForPath.from(Path.just(10)).from(n -> Path.just(n * 2)).yield((a, b) -> a + b);

    assertThat(result.getOrElse(0)).isEqualTo(30); // 10 + 20

    // let binds pure values without creating a new path
    MaybePath<String> withLet =
        ForPath.from(Path.just("hello"))
            .let(String::length)
            .yield((str, len) -> str + " has " + len + " chars");

    assertThat(withLet.getOrElse("")).isEqualTo("hello has 5 chars");

    // Short-circuiting: Nothing stops the chain
    MaybePath<Integer> shortCircuit =
        ForPath.from(Path.just(5))
            .<Integer>from(a -> Path.nothing()) // stops here
            .from(t -> Path.just(100)) // never executed
            .yield((a, b, c) -> a + b + c);

    assertThat(shortCircuit.run().isNothing()).isTrue();
  }

  /**
   * Exercise 2: ForPath with EitherPath
   *
   * <p>ForPath works with EitherPath for workflows with typed errors. Any step that returns Left
   * short-circuits the entire comprehension.
   *
   * <p>Task: Build a validation workflow with ForPath
   */
  @Test
  void exercise2_forPathWithEither() {
    Function<String, EitherPath<String, Integer>> parseAge =
        s -> {
          try {
            return Path.right(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            return Path.left("Invalid age format");
          }
        };

    Function<Integer, EitherPath<String, Integer>> validateRange =
        age -> (age >= 0 && age <= 150) ? Path.right(age) : Path.left("Age out of range");

    // SOLUTION: Chain validation steps with ForPath
    // Note: After the first from(), subsequent from() receives a Tuple2 containing all previous
    // values
    EitherPath<String, String> workflow =
        ForPath.from(Path.<String, String>right("25"))
            .from(parseAge)
            .from(t -> validateRange.apply(t._2())) // Extract parsed value from tuple
            .yield((input, parsed, validated) -> "Valid age: " + validated);

    assertThat(workflow.run().getRight()).isEqualTo("Valid age: 25");

    // Test failure case - invalid format
    EitherPath<String, String> invalid =
        ForPath.from(Path.<String, String>right("abc"))
            .from(parseAge)
            .from(t -> validateRange.apply(t._2()))
            .yield((input, parsed, validated) -> "Valid age: " + validated);

    assertThat(invalid.run().isLeft()).isTrue();
    assertThat(invalid.run().getLeft()).isEqualTo("Invalid age format");

    // Test failure case - out of range
    EitherPath<String, String> outOfRange =
        ForPath.from(Path.<String, String>right("200"))
            .from(parseAge)
            .from(t -> validateRange.apply(t._2()))
            .yield((input, parsed, validated) -> "Valid age: " + validated);

    assertThat(outOfRange.run().isLeft()).isTrue();
    assertThat(outOfRange.run().getLeft()).isEqualTo("Age out of range");
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Part 2: Effect Contexts
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 3: ErrorContext
   *
   * <p>ErrorContext wraps EitherT monad transformer in a user-friendly API. It provides typed error
   * handling with deferred IO execution.
   *
   * <p>Key methods:
   *
   * <ul>
   *   <li>{@code ErrorContext.success(value)} - Create a successful context
   *   <li>{@code ErrorContext.failure(error)} - Create a failed context
   *   <li>{@code map}, {@code via} - Transform and chain computations
   *   <li>{@code recover}, {@code mapError} - Error handling
   *   <li>{@code runIO().unsafeRun()} - Execute and extract the result
   * </ul>
   *
   * <p>Task: Use ErrorContext for typed error workflows
   */
  @Test
  void exercise3_errorContext() {
    // Create success and failure contexts
    // SOLUTION: Use ErrorContext.success and ErrorContext.failure
    ErrorContext<?, String, Integer> success = ErrorContext.success(42);

    // SOLUTION: Use ErrorContext.failure
    ErrorContext<?, String, Integer> failure = ErrorContext.failure("Something went wrong");

    // Run and extract
    Either<String, Integer> successResult = success.runIO().unsafeRun();
    assertThat(successResult.getRight()).isEqualTo(42);

    Either<String, Integer> failureResult = failure.runIO().unsafeRun();
    assertThat(failureResult.getLeft()).isEqualTo("Something went wrong");

    // Chain with via
    ErrorContext<?, String, String> workflow =
        ErrorContext.<String, Integer>success(10)
            .via(n -> n > 5 ? ErrorContext.success("big: " + n) : ErrorContext.failure("too small"))
            .map(String::toUpperCase);

    Either<String, String> workflowResult = workflow.runIO().unsafeRun();
    assertThat(workflowResult.getRight()).isEqualTo("BIG: 10");

    // Recovery
    Either<String, Integer> recovered = failure.recover(err -> -1).runIO().unsafeRun();
    assertThat(recovered.getRight()).isEqualTo(-1);
  }

  /**
   * Exercise 4: ConfigContext
   *
   * <p>ConfigContext wraps ReaderT for dependency injection patterns. It allows computations to
   * access configuration without passing it explicitly through every function.
   *
   * <p>Key methods:
   *
   * <ul>
   *   <li>{@code ConfigContext.ask()} - Retrieve the entire configuration
   *   <li>{@code ConfigContext.pure(value)} - Lift a pure value
   *   <li>{@code ConfigContext.io(f)} - Create a config-dependent computation
   *   <li>{@code runWithSync(config)} - Run with a specific configuration
   *   <li>{@code local(f)} - Modify the configuration for a sub-computation
   * </ul>
   *
   * <p>Task: Use ConfigContext for configuration-dependent workflows
   */
  @Test
  void exercise4_configContext() {
    record AppConfig(String apiUrl, int timeout, boolean debug) {}

    AppConfig config = new AppConfig("https://api.example.com", 30, true);

    // ask retrieves the entire config
    // SOLUTION: Use ConfigContext.ask()
    ConfigContext<?, AppConfig, AppConfig> getConfig = ConfigContext.ask();

    AppConfig result = getConfig.runWithSync(config);
    assertThat(result.apiUrl()).isEqualTo("https://api.example.com");

    // Extract a specific field using ask().map()
    ConfigContext<?, AppConfig, String> getUrl =
        ConfigContext.<AppConfig>ask().map(AppConfig::apiUrl);
    assertThat(getUrl.runWithSync(config)).isEqualTo("https://api.example.com");

    // Chain config-dependent operations
    // SOLUTION: Use ask(), via(), and io() to build a config-dependent workflow
    ConfigContext<?, AppConfig, String> workflow =
        ConfigContext.<AppConfig>ask()
            .via(cfg -> ConfigContext.pure(cfg.apiUrl() + "/users"))
            .via(
                endpoint ->
                    ConfigContext.<AppConfig, String>io(
                        cfg -> endpoint + "?timeout=" + cfg.timeout()));

    String url = workflow.runWithSync(config);
    assertThat(url).isEqualTo("https://api.example.com/users?timeout=30");

    // local modifies the config for a sub-computation
    ConfigContext<?, AppConfig, Integer> doubled =
        ConfigContext.<AppConfig, Integer>io(cfg -> cfg.timeout())
            .local(cfg -> new AppConfig(cfg.apiUrl(), cfg.timeout() * 2, cfg.debug()));

    Integer doubledTimeout = doubled.runWithSync(config);
    assertThat(doubledTimeout).isEqualTo(60);
  }

  /**
   * Exercise 5: MutableContext
   *
   * <p>MutableContext wraps StateT for stateful computations. It provides get, put, and modify
   * operations for managing state in a pure, functional way.
   *
   * <p>Key methods:
   *
   * <ul>
   *   <li>{@code MutableContext.get()} - Retrieve current state
   *   <li>{@code MutableContext.put(s)} - Replace state entirely
   *   <li>{@code MutableContext.modify(f)} - Transform state
   *   <li>{@code evalWith(initial)} - Run and return just the value
   *   <li>{@code execWith(initial)} - Run and return just the final state
   *   <li>{@code runWith(initial)} - Run and return both state and value
   * </ul>
   *
   * <p>Task: Use MutableContext for stateful workflows
   */
  @Test
  void exercise5_mutableContext() {
    record Counter(int count) {}

    Counter initial = new Counter(0);

    // get retrieves current state
    Counter current = MutableContext.<Counter>get().evalWith(initial).unsafeRun();
    assertThat(current.count()).isEqualTo(0);

    // modify transforms state
    // SOLUTION: Use MutableContext.modify() to add 10 to the count
    MutableContext<?, Counter, Unit> addTen =
        MutableContext.<Counter>modify(c -> new Counter(c.count() + 10));

    Counter modified = addTen.execWith(initial).unsafeRun();
    assertThat(modified.count()).isEqualTo(10);

    // Chain stateful operations using then()
    MutableContext<?, Counter, String> workflow =
        MutableContext.<Counter>modify(c -> new Counter(c.count() + 1))
            .then(() -> MutableContext.modify(c -> new Counter(c.count() + 2)))
            .then(() -> MutableContext.<Counter>get().map(c -> "Final: " + c.count()));

    StateTuple<Counter, String> resultTuple = workflow.runWith(initial).unsafeRun();
    assertThat(resultTuple.value()).isEqualTo("Final: 3");
    assertThat(resultTuple.state().count()).isEqualTo(3);

    // put replaces state entirely
    Counter replaced = MutableContext.<Counter>put(new Counter(100)).execWith(initial).unsafeRun();
    assertThat(replaced.count()).isEqualTo(100);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Part 3: Annotations and Integration
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 6: @GeneratePathBridge Annotation Pattern
   *
   * <p>The @GeneratePathBridge annotation generates Path-returning wrapper methods for service
   * interfaces. Methods annotated with @PathVia are automatically wrapped in the appropriate Path
   * type.
   *
   * <p>In production code, you annotate your service interfaces:
   *
   * <pre>{@code
   * @GeneratePathBridge
   * public interface UserService {
   *     @PathVia
   *     Optional<User> findById(Long id);
   *
   *     @PathVia
   *     Either<Error, User> createUser(CreateUserRequest request);
   * }
   * }</pre>
   *
   * <p>The annotation processor generates UserServicePaths with methods returning:
   *
   * <ul>
   *   <li>{@code OptionalPath<User> findById(Long id)}
   *   <li>{@code EitherPath<Error, User> createUser(CreateUserRequest request)}
   * </ul>
   *
   * <p>Task: Understand the pattern by implementing it manually
   */
  @Test
  void exercise6_generatePathBridgePattern() {
    record User(String id, String name) {}

    // Original service method returning Optional
    Function<String, Optional<User>> findById =
        id -> id.equals("u1") ? Optional.of(new User("u1", "Alice")) : Optional.empty();

    // What @PathVia generates: wrap the result in a Path
    // SOLUTION: Wrap the Optional result in Path.optional()
    Function<String, OptionalPath<User>> findByIdPath = id -> Path.optional(findById.apply(id));

    // Now we can use fluent path operations
    OptionalPath<String> userName =
        findByIdPath.apply("u1").map(User::name).map(String::toUpperCase);

    assertThat(userName.run()).contains("ALICE");

    // Absent case returns empty Optional
    OptionalPath<String> notFound = findByIdPath.apply("u999").map(User::name);

    assertThat(notFound.run()).isEmpty();

    // Example with Either-returning service method
    Function<String, Either<String, User>> createUser =
        name ->
            name.isBlank()
                ? Either.left("Name cannot be blank")
                : Either.right(new User("new-id", name));

    // What @PathVia generates for Either
    Function<String, EitherPath<String, User>> createUserPath =
        name -> Path.either(createUser.apply(name));

    EitherPath<String, String> createResult =
        createUserPath.apply("Bob").map(User::name).map(name -> "Created: " + name);

    assertThat(createResult.run().getRight()).isEqualTo("Created: Bob");
  }

  /**
   * Exercise 7: Focus-Effect Integration
   *
   * <p>Effect Paths integrate with the Focus DSL through the focus() method. This allows
   * optic-based navigation within effect contexts, combining the power of both APIs.
   *
   * <p>Key concepts:
   *
   * <ul>
   *   <li>FocusPath wraps a Lens for total access to nested fields
   *   <li>The focus() method composes Effect Paths with Focus Paths
   *   <li>Errors propagate correctly through the composition
   * </ul>
   *
   * <p>Task: Use focus() to navigate within Effect Paths
   *
   * <p>See Tutorial 14 (Optics: Focus-Effect Bridge) for comprehensive coverage.
   */
  @Test
  void exercise7_focusEffectIntegration() {
    record Address(String city, String country) {}

    record User(String name, Address address) {}

    /*
     * ========================================================================
     * IMPORTANT: Manual Implementation (For Educational Purposes Only)
     * ========================================================================
     *
     * This exercise manually creates lenses to demonstrate Focus-Effect
     * integration. In real projects, ALWAYS use annotations:
     *
     *   @GenerateLenses
     *   record User(String name, Address address) {}
     *
     *   // Then use: UserLenses.address(), AddressLenses.city()
     *
     * Manual implementations are error-prone and verbose. The annotation
     * processor generates optimised, tested code automatically.
     */

    Lens<User, Address> addressLens = Lens.of(User::address, (u, a) -> new User(u.name(), a));

    Lens<Address, String> cityLens = Lens.of(Address::city, (a, c) -> new Address(c, a.country()));

    FocusPath<User, Address> addressPath = FocusPath.of(addressLens);
    FocusPath<Address, String> cityPath = FocusPath.of(cityLens);

    User alice = new User("Alice", new Address("London", "UK"));

    // Start with EitherPath, use focus() to navigate
    EitherPath<String, User> userPath = Path.right(alice);

    // SOLUTION: Use focus() to navigate through address to city
    EitherPath<String, String> cityEffectPath = userPath.focus(addressPath).focus(cityPath);

    assertThat(cityEffectPath.run().getRight()).isEqualTo("London");

    // focus() preserves errors - Left passes through unchanged
    EitherPath<String, User> errorPath = Path.left("User not found");
    EitherPath<String, String> cityFromError = errorPath.focus(addressPath).focus(cityPath);

    assertThat(cityFromError.run().isLeft()).isTrue();
    assertThat(cityFromError.run().getLeft()).isEqualTo("User not found");

    // Using ForPath with focus() for more complex navigation
    MaybePath<String> complexResult =
        ForPath.from(Path.just(alice))
            .focus(addressPath)
            .focus(t -> t._2().city())
            .yield((u, address, city) -> u.name() + " lives in " + city);

    assertThat(complexResult.getOrElse("")).isEqualTo("Alice lives in London");
  }

  /**
   * Congratulations! You have completed Tutorial 02: Effect Path Advanced
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How to use ForPath comprehensions for readable multi-step workflows
   *   <li>✓ How ForPath short-circuits on Nothing or Left values
   *   <li>✓ How to use ErrorContext for typed error handling
   *   <li>✓ How to use ConfigContext for dependency injection patterns
   *   <li>✓ How to use MutableContext for stateful computations
   *   <li>✓ How @GeneratePathBridge generates Path-returning service wrappers
   *   <li>✓ How to integrate Focus DSL with Effect Paths for optic navigation
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>ForPath improves readability for complex effect chains
   *   <li>Effect Contexts wrap monad transformers in user-friendly APIs
   *   <li>Annotations reduce boilerplate for service integration
   *   <li>Focus-Effect integration combines optics with effect handling
   * </ul>
   *
   * <p>═══════════════════════════════════════════════════════════════════════ <b>Next Steps:
   * Optics for Data Manipulation</b>
   * ═══════════════════════════════════════════════════════════════════════
   *
   * <p>Now that you've mastered Effect Paths for computation sequencing, explore the <b>Optics</b>
   * module for powerful data manipulation:
   *
   * <p><b>Recommended Optics Tutorials:</b>
   *
   * <ul>
   *   <li><b>Tutorial 05</b>: Traversal Basics - Focus on multiple elements
   *   <li><b>Tutorial 12</b>: Focus DSL - Type-safe path navigation
   *   <li><b>Tutorial 13</b>: Advanced Focus DSL - Type class integration
   *   <li><b>Tutorial 14</b>: Focus-Effect Bridge - Combine optics with effects
   * </ul>
   *
   * <p><b>Each Typeclass for Container Traversal:</b>
   *
   * <p>The {@code Each} typeclass provides canonical traversals for containers. Instead of manually
   * creating traversals, use {@code EachInstances} for Java types (List, Map, Optional, arrays,
   * String) or {@code EachExtensions} for HKT types (Maybe, Either, Try):
   *
   * <pre>{@code
   * // Traverse all map values with custom Each instance
   * TraversalPath<Config, Setting> allSettings =
   *     FocusPath.of(settingsLens).each(EachInstances.mapValuesEach());
   *
   * // Traverse Maybe values from HKT types
   * TraversalPath<Wrapper, Value> maybeValues =
   *     FocusPath.of(maybeLens).each(EachExtensions.maybeEach());
   * }</pre>
   *
   * <p>See {@code EachInstancesExample.java} for comprehensive examples.
   */
}
