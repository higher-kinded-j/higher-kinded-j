// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.higherkindedj.hkt.context.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: Context Basics - Working with ScopedValue
 *
 * <p>Learn to work with Context, Higher-Kinded-J's effect type for reading from Java's ScopedValue
 * API. Context provides a functional approach to thread-scoped values that automatically propagate
 * to child virtual threads.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>Context reads from ScopedValue - think of it as the "Reader monad" for thread-local context
 *   <li>Use ask() to read a value, asks() to read and transform
 *   <li>Use map() and flatMap() for composition
 *   <li>Values must be bound before run() is called
 * </ul>
 *
 * <p>Requirements: Java 25+ (ScopedValue is finalised)
 *
 * <p>Estimated time: 25-30 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 01: Context Basics")
public class Tutorial01_ContextBasics {

  // Define ScopedValues for use in exercises
  private static final ScopedValue<String> USER_NAME = ScopedValue.newInstance();
  private static final ScopedValue<Integer> MAX_ITEMS = ScopedValue.newInstance();
  private static final ScopedValue<Boolean> DEBUG_MODE = ScopedValue.newInstance();

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: Creating Contexts with ask() and succeed()
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating Contexts")
  class CreatingContexts {

    /**
     * Exercise 1: Create a Context that reads from a ScopedValue
     *
     * <p>Context.ask(key) creates a Context that reads the value from the specified ScopedValue.
     * When run() is called, it retrieves the currently bound value.
     *
     * <p>Task: Create a Context that reads from USER_NAME
     */
    @Test
    @DisplayName("Exercise 1: Read a ScopedValue with ask()")
    void exercise1_readWithAsk() throws Exception {
      // TODO: Replace answerRequired() with Context.ask(USER_NAME)
      Context<String, String> getUserName = answerRequired();

      // Run within a scope binding
      String result = ScopedValue.where(USER_NAME, "Alice").call(() -> getUserName.run());

      assertThat(result).isEqualTo("Alice");
    }

    /**
     * Exercise 2: Create a Context that succeeds with a value
     *
     * <p>Context.succeed(value) creates a Context that immediately returns the given value without
     * reading from any ScopedValue. This is the "pure" operation for Context.
     *
     * <p>Task: Create a Context that succeeds with the value 42
     */
    @Test
    @DisplayName("Exercise 2: Create a pure value with succeed()")
    void exercise2_pureWithSucceed() {
      // TODO: Replace answerRequired() with Context.succeed(42)
      Context<String, Integer> pureValue = answerRequired();

      // No scope binding needed - succeed doesn't read from ScopedValue
      Integer result = pureValue.run();

      assertThat(result).isEqualTo(42);
    }

    /**
     * Exercise 3: Understand what happens when ScopedValue is unbound
     *
     * <p>When you run a Context that reads from an unbound ScopedValue, it throws
     * NoSuchElementException. This is fail-fast behaviour that helps catch configuration errors.
     *
     * <p>Task: Verify that reading an unbound value throws NoSuchElementException
     */
    @Test
    @DisplayName("Exercise 3: Unbound ScopedValue throws exception")
    void exercise3_unboundThrows() {
      Context<String, String> getUserName = Context.ask(USER_NAME);

      // TODO: Replace answerRequired() with NoSuchElementException.class
      // This verifies that running without a binding throws the expected exception
      assertThatThrownBy(() -> getUserName.run()).isInstanceOf(answerRequired());
    }
  }

  // ===========================================================================
  // Part 2: Reading and Transforming with asks()
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Reading and Transforming")
  class ReadingAndTransforming {

    /**
     * Exercise 4: Read and transform in one step with asks()
     *
     * <p>Context.asks(key, function) reads from the ScopedValue and applies a transformation
     * function in one step. This is a convenient combination of ask() and map().
     *
     * <p>Task: Create a Context that reads USER_NAME and transforms it to uppercase
     */
    @Test
    @DisplayName("Exercise 4: Read and transform with asks()")
    void exercise4_readAndTransform() throws Exception {
      // TODO: Replace answerRequired() with:
      // Context.asks(USER_NAME, name -> name.toUpperCase())
      // or Context.asks(USER_NAME, String::toUpperCase)
      Context<String, String> getUpperName = answerRequired();

      String result = ScopedValue.where(USER_NAME, "bob").call(() -> getUpperName.run());

      assertThat(result).isEqualTo("BOB");
    }

    /**
     * Exercise 5: Transform the result type with asks()
     *
     * <p>The transformation function can change the result type. You can go from
     * Context<ScopedType, ScopedType> to Context<ScopedType, AnyOtherType>.
     *
     * <p>Task: Create a Context that reads USER_NAME and returns its length
     */
    @Test
    @DisplayName("Exercise 5: Change result type with asks()")
    void exercise5_changeType() throws Exception {
      // TODO: Replace answerRequired() with:
      // Context.asks(USER_NAME, name -> name.length())
      // or Context.asks(USER_NAME, String::length)
      Context<String, Integer> getNameLength = answerRequired();

      Integer result = ScopedValue.where(USER_NAME, "Charlie").call(() -> getNameLength.run());

      assertThat(result).isEqualTo(7); // "Charlie" has 7 characters
    }

    /**
     * Exercise 6: Create a formatted message using asks()
     *
     * <p>Task: Create a Context that reads MAX_ITEMS and returns a message "Limit: N items"
     */
    @Test
    @DisplayName("Exercise 6: Format a message with asks()")
    void exercise6_formatMessage() throws Exception {
      // TODO: Replace answerRequired() with:
      // Context.asks(MAX_ITEMS, n -> "Limit: " + n + " items")
      Context<Integer, String> getLimitMessage = answerRequired();

      String result = ScopedValue.where(MAX_ITEMS, 100).call(() -> getLimitMessage.run());

      assertThat(result).isEqualTo("Limit: 100 items");
    }
  }

  // ===========================================================================
  // Part 3: Transforming Contexts with map()
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Transforming with map()")
  class TransformingWithMap {

    /**
     * Exercise 7: Transform an existing Context with map()
     *
     * <p>map() transforms the result of a Context without changing what it reads. The
     * transformation is applied after the value is retrieved.
     *
     * <p>Task: Take a Context<String, String> and map it to Context<String, Integer>
     */
    @Test
    @DisplayName("Exercise 7: Transform with map()")
    void exercise7_mapTransform() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // TODO: Replace answerRequired() with getName.map(String::length)
      // or getName.map(name -> name.length())
      Context<String, Integer> getLength = answerRequired();

      Integer result = ScopedValue.where(USER_NAME, "Diana").call(() -> getLength.run());

      assertThat(result).isEqualTo(5);
    }

    /**
     * Exercise 8: Chain multiple map() calls
     *
     * <p>You can chain multiple map() calls to build up complex transformations step by step.
     *
     * <p>Task: Read USER_NAME, trim it, convert to uppercase, and wrap in brackets
     */
    @Test
    @DisplayName("Exercise 8: Chain multiple maps")
    void exercise8_chainMaps() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // TODO: Replace answerRequired() with:
      // getName.map(String::trim).map(String::toUpperCase).map(s -> "[" + s + "]")
      Context<String, String> formatted = answerRequired();

      String result = ScopedValue.where(USER_NAME, "  eve  ").call(() -> formatted.run());

      assertThat(result).isEqualTo("[EVE]");
    }

    /**
     * Exercise 9: Map a boolean context
     *
     * <p>Task: Read DEBUG_MODE and map it to a log level string ("DEBUG" or "INFO")
     */
    @Test
    @DisplayName("Exercise 9: Map boolean to string")
    void exercise9_mapBoolean() throws Exception {
      Context<Boolean, Boolean> isDebug = Context.ask(DEBUG_MODE);

      // TODO: Replace answerRequired() with:
      // isDebug.map(debug -> debug ? "DEBUG" : "INFO")
      Context<Boolean, String> getLogLevel = answerRequired();

      String debugLevel = ScopedValue.where(DEBUG_MODE, true).call(() -> getLogLevel.run());
      String infoLevel = ScopedValue.where(DEBUG_MODE, false).call(() -> getLogLevel.run());

      assertThat(debugLevel).isEqualTo("DEBUG");
      assertThat(infoLevel).isEqualTo("INFO");
    }
  }

  // ===========================================================================
  // Part 4: Running Contexts within Scopes
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Running in Scopes")
  class RunningInScopes {

    /**
     * Exercise 10: Bind multiple ScopedValues
     *
     * <p>You can chain .where() calls to bind multiple ScopedValues for a scope.
     *
     * <p>Task: Run a context that reads USER_NAME within a scope that binds both USER_NAME and
     * MAX_ITEMS
     */
    @Test
    @DisplayName("Exercise 10: Bind multiple values")
    void exercise10_multipleBindings() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);
      Context<Integer, Integer> getMax = Context.ask(MAX_ITEMS);

      // TODO: Replace the answerRequired() values in the where() calls
      String userName =
          ScopedValue.where(USER_NAME, answerRequired()) // Hint: "Frank"
              .where(MAX_ITEMS, 50)
              .call(() -> getName.run());

      Integer maxItems =
          ScopedValue.where(USER_NAME, "Frank")
              .where(MAX_ITEMS, answerRequired()) // Hint: 50
              .call(() -> getMax.run());

      assertThat(userName).isEqualTo("Frank");
      assertThat(maxItems).isEqualTo(50);
    }

    /**
     * Exercise 11: Use run() vs call()
     *
     * <p>ScopedValue.where().run() is for Runnable (no return value). ScopedValue.where().call() is
     * for Callable (with return value).
     *
     * <p>Task: Use .run() to execute a side effect within a scope
     */
    @Test
    @DisplayName("Exercise 11: Using run() for side effects")
    void exercise11_runForSideEffects() {
      Context<String, String> getName = Context.ask(USER_NAME);
      StringBuilder log = new StringBuilder();

      // TODO: Replace answerRequired() with the binding value "Grace"
      ScopedValue.where(USER_NAME, answerRequired())
          .run(
              () -> {
                String name = getName.run();
                log.append("Processing: ").append(name);
              });

      assertThat(log.toString()).isEqualTo("Processing: Grace");
    }
  }

  // ===========================================================================
  // Bonus: Putting It All Together
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Complete Examples")
  class BonusExamples {

    /**
     * This test demonstrates a complete Context workflow:
     *
     * <ol>
     *   <li>Define ScopedValues for configuration
     *   <li>Create Contexts that read and transform
     *   <li>Run within properly bound scopes
     * </ol>
     */
    @Test
    @DisplayName("Complete workflow example")
    void completeWorkflowExample() throws Exception {
      // Create contexts for different configuration values
      Context<String, String> greeting = Context.asks(USER_NAME, name -> "Hello, " + name + "!");

      Context<Integer, String> itemsMessage =
          Context.asks(MAX_ITEMS, n -> "You can add up to " + n + " items.");

      Context<Boolean, String> modeMessage =
          Context.asks(DEBUG_MODE, debug -> debug ? "[DEBUG MODE ENABLED]" : "");

      // Run in a fully configured scope
      String result =
          ScopedValue.where(USER_NAME, "Tutorial User")
              .where(MAX_ITEMS, 25)
              .where(DEBUG_MODE, true)
              .call(
                  () -> {
                    String greet = greeting.run();
                    String items = itemsMessage.run();
                    String mode = modeMessage.run();
                    return String.join("\n", greet, items, mode);
                  });

      assertThat(result)
          .contains("Hello, Tutorial User!")
          .contains("You can add up to 25 items.")
          .contains("[DEBUG MODE ENABLED]");
    }
  }

  /**
   * Congratulations! You've completed Tutorial 01: Context Basics
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How to create Contexts with ask() and succeed()
   *   <li>✓ How to read and transform with asks()
   *   <li>✓ How to transform existing Contexts with map()
   *   <li>✓ How to bind ScopedValues and run Contexts within scopes
   *   <li>✓ What happens when a ScopedValue is not bound
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>Context is a functional wrapper around ScopedValue access
   *   <li>ask() reads the raw value; asks() reads and transforms
   *   <li>map() transforms the result without changing what is read
   *   <li>ScopedValues must be bound before run() is called
   * </ul>
   *
   * <p>Next: Tutorial 02 - Context Composition with flatMap and type classes
   */
}
