// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.ContextKind;
import org.higherkindedj.hkt.context.ContextKindHelper;
import org.higherkindedj.hkt.context.ContextMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: Context Composition - flatMap and Type Classes
 *
 * <p>Learn to compose Context computations using flatMap and understand how Context integrates with
 * Higher-Kinded-J's type class hierarchy (Functor, Applicative, Monad).
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>flatMap chains dependent Context computations
 *   <li>Context implements Functor, Applicative, and Monad type classes
 *   <li>asUnit() discards the result and returns Unit
 *   <li>Context.fail() creates failing computations for validation
 * </ul>
 *
 * <p>Requirements: Java 25+ (ScopedValue is finalised)
 *
 * <p>Estimated time: 25-30 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 02: Context Composition")
public class Tutorial02_ContextComposition {

  private static final ScopedValue<String> USER_NAME = ScopedValue.newInstance();
  private static final ScopedValue<Integer> USER_ID = ScopedValue.newInstance();
  private static final ScopedValue<String> CONFIG_PATH = ScopedValue.newInstance();

  /** Helper method for incomplete exercises. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: Chaining with flatMap
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Chaining with flatMap")
  class ChainingWithFlatMap {

    /**
     * Exercise 1: Chain two Context computations with flatMap
     *
     * <p>flatMap takes the result of one Context and uses it to create another Context. This is
     * essential when the next computation depends on the previous result.
     *
     * <p>Task: Read USER_NAME and use it to create a greeting message
     */
    @Test
    @DisplayName("Exercise 1: Basic flatMap chaining")
    void exercise1_basicFlatMap() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // TODO: Replace answerRequired() with:
      // getName.flatMap(name -> Context.succeed("Hello, " + name + "!"))
      Context<String, String> greeting = answerRequired();

      String result = ScopedValue.where(USER_NAME, "Alice").call(() -> greeting.run());

      assertThat(result).isEqualTo("Hello, Alice!");
    }

    /**
     * Exercise 2: Use flatMap to transform the result type
     *
     * <p>flatMap allows chaining when your transformation returns a Context. This is useful when
     * the next step depends on the previous result.
     *
     * <p>Task: Use flatMap to get the name length
     */
    @Test
    @DisplayName("Exercise 2: Using flatMap for dependent computation")
    void exercise2_flatMapDependent() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // TODO: Replace answerRequired() with:
      // getName.flatMap(name -> Context.succeed(name.length()))
      Context<String, Integer> getNameLength = answerRequired();

      Integer result = ScopedValue.where(USER_NAME, "Bob").call(() -> getNameLength.run());

      assertThat(result).isEqualTo(3);
    }

    /**
     * Exercise 3: Chain multiple flatMap calls
     *
     * <p>You can chain multiple flatMap calls to build complex pipelines where each step depends on
     * the previous.
     *
     * <p>Task: Read name, then create a greeting, then format it with brackets
     */
    @Test
    @DisplayName("Exercise 3: Multiple flatMap chain")
    void exercise3_multipleFlatMaps() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // TODO: Replace answerRequired() with:
      // getName
      //   .flatMap(name -> Context.succeed("Hello, " + name))
      //   .flatMap(greeting -> Context.succeed("[" + greeting + "]"))
      Context<String, String> formatted = answerRequired();

      String result = ScopedValue.where(USER_NAME, "Charlie").call(() -> formatted.run());

      assertThat(result).isEqualTo("[Hello, Charlie]");
    }

    /**
     * Exercise 4: Understand the difference between map and flatMap
     *
     * <p>map: takes a function A -> B flatMap: takes a function A -> Context<R, B>
     *
     * <p>Use flatMap when your transformation needs to return another Context. Use map when your
     * transformation is a simple value transformation.
     *
     * <p>Task: Complete both transformations correctly
     */
    @Test
    @DisplayName("Exercise 4: map vs flatMap")
    void exercise4_mapVsFlatMap() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // This uses map because toUpperCase returns String, not Context
      // TODO: Replace answerRequired() with getName.map(String::toUpperCase)
      Context<String, String> upperName = answerRequired();

      // This uses flatMap because we want to return a Context
      // TODO: Replace answerRequired() with:
      // getName.flatMap(name -> Context.succeed("User: " + name))
      Context<String, String> userLabel = answerRequired();

      String upper = ScopedValue.where(USER_NAME, "diana").call(() -> upperName.run());
      String label = ScopedValue.where(USER_NAME, "diana").call(() -> userLabel.run());

      assertThat(upper).isEqualTo("DIANA");
      assertThat(label).isEqualTo("User: diana");
    }
  }

  // ===========================================================================
  // Part 2: Context.fail and Error Propagation
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Context.fail and Error Propagation")
  class FailAndErrorPropagation {

    /**
     * Exercise 5: Create a failing Context
     *
     * <p>Context.fail(error) creates a Context that fails immediately when run. This is useful for
     * validation or error conditions.
     *
     * <p>Task: Create a Context that fails with an IllegalArgumentException
     */
    @Test
    @DisplayName("Exercise 5: Create a failing Context")
    void exercise5_failingContext() {
      // TODO: Replace answerRequired() with:
      // Context.fail(new IllegalArgumentException("Invalid input"))
      Context<String, String> failing = answerRequired();

      try {
        failing.run();
        assertThat(false).as("Should have thrown").isTrue();
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).isEqualTo("Invalid input");
      }
    }

    /**
     * Exercise 6: Conditional failure with flatMap
     *
     * <p>Use flatMap to conditionally succeed or fail based on a value.
     *
     * <p>Task: Read USER_NAME and fail if it's empty, otherwise succeed with a greeting
     */
    @Test
    @DisplayName("Exercise 6: Conditional failure")
    void exercise6_conditionalFailure() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // TODO: Replace answerRequired() with:
      // getName.flatMap(name -> {
      //   if (name.isEmpty()) {
      //     return Context.fail(new IllegalArgumentException("Name cannot be empty"));
      //   }
      //   return Context.succeed("Hello, " + name);
      // })
      Context<String, String> validateAndGreet = answerRequired();

      // Test with valid name
      String valid = ScopedValue.where(USER_NAME, "Eve").call(() -> validateAndGreet.run());
      assertThat(valid).isEqualTo("Hello, Eve");

      // Test with empty name
      try {
        ScopedValue.where(USER_NAME, "").call(() -> validateAndGreet.run());
        assertThat(false).as("Should have thrown").isTrue();
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).isEqualTo("Name cannot be empty");
      }
    }
  }

  // ===========================================================================
  // Part 3: Using asUnit()
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Using asUnit()")
  class UsingAsUnit {

    /**
     * Exercise 7: Discard result with asUnit()
     *
     * <p>asUnit() transforms Context<R, A> to Context<R, Unit>, discarding the result. This is
     * useful when you only care about the side effect or validation, not the value.
     *
     * <p>Task: Create a validation context that returns Unit on success
     */
    @Test
    @DisplayName("Exercise 7: Discard result with asUnit()")
    void exercise7_asUnit() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // TODO: Replace answerRequired() with:
      // getName.flatMap(name -> {
      //   if (name.length() < 3) {
      //     return Context.fail(new IllegalArgumentException("Name too short"));
      //   }
      //   return Context.succeed(name);
      // }).asUnit()
      Context<String, Unit> validateName = answerRequired();

      // Valid name returns Unit
      Unit result = ScopedValue.where(USER_NAME, "Frank").call(() -> validateName.run());
      assertThat(result).isEqualTo(Unit.INSTANCE);

      // Invalid name throws
      try {
        ScopedValue.where(USER_NAME, "Jo").call(() -> validateName.run());
        assertThat(false).as("Should have thrown").isTrue();
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).isEqualTo("Name too short");
      }
    }
  }

  // ===========================================================================
  // Part 4: Type Class Integration
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Type Class Integration")
  class TypeClassIntegration {

    /**
     * Exercise 8: Use ContextMonad.of()
     *
     * <p>ContextMonad provides the Monad type class instance for Context. The of() method (from
     * Applicative) lifts a value into a Context, equivalent to Context.succeed().
     *
     * <p>Task: Use the monad's of() method to create a Context
     */
    @Test
    @DisplayName("Exercise 8: Using Monad.of()")
    void exercise8_monadOf() {
      ContextMonad<String> monad = ContextMonad.instance();

      // TODO: Replace answerRequired() with monad.of(42)
      Kind<ContextKind.Witness<String>, Integer> pureKind = answerRequired();

      // Narrow back to Context to run
      Context<String, Integer> context = ContextKindHelper.CONTEXT.narrow(pureKind);
      Integer result = context.run();

      assertThat(result).isEqualTo(42);
    }

    /**
     * Exercise 9: Use Functor.map() through the monad
     *
     * <p>Since Monad extends Functor, you can use the monad's map() method to transform values.
     *
     * <p>Task: Use monad.map() to transform a Context
     */
    @Test
    @DisplayName("Exercise 9: Using Functor.map() through monad")
    void exercise9_functorMap() throws Exception {
      ContextMonad<String> monad = ContextMonad.instance();

      Context<String, String> getName = Context.ask(USER_NAME);
      Kind<ContextKind.Witness<String>, String> nameKind = ContextKindHelper.CONTEXT.widen(getName);

      // TODO: Replace answerRequired() with:
      // monad.map(String::length, nameKind)
      Kind<ContextKind.Witness<String>, Integer> lengthKind = answerRequired();

      Context<String, Integer> lengthContext = ContextKindHelper.CONTEXT.narrow(lengthKind);
      Integer result = ScopedValue.where(USER_NAME, "Grace").call(() -> lengthContext.run());

      assertThat(result).isEqualTo(5);
    }

    /**
     * Exercise 10: Use Monad.flatMap() through the monad
     *
     * <p>The monad's flatMap() method takes arguments in a different order than the instance
     * method: flatMap(function, kind) vs kind.flatMap(function).
     *
     * <p>Task: Use monad.flatMap() to chain computations
     */
    @Test
    @DisplayName("Exercise 10: Using Monad.flatMap()")
    void exercise10_monadFlatMap() throws Exception {
      ContextMonad<String> monad = ContextMonad.instance();

      Context<String, String> getName = Context.ask(USER_NAME);
      Kind<ContextKind.Witness<String>, String> nameKind = ContextKindHelper.CONTEXT.widen(getName);

      // TODO: Replace answerRequired() with:
      // monad.flatMap(
      //   name -> ContextKindHelper.CONTEXT.widen(Context.succeed("Hello, " + name)),
      //   nameKind)
      Kind<ContextKind.Witness<String>, String> greetingKind = answerRequired();

      Context<String, String> greetingContext = ContextKindHelper.CONTEXT.narrow(greetingKind);
      String result = ScopedValue.where(USER_NAME, "Henry").call(() -> greetingContext.run());

      assertThat(result).isEqualTo("Hello, Henry");
    }
  }

  // ===========================================================================
  // Bonus: Real-World Patterns
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Real-World Patterns")
  class RealWorldPatterns {

    /**
     * This test demonstrates a complete workflow using Context composition:
     *
     * <ol>
     *   <li>Read user ID from context
     *   <li>Validate the ID
     *   <li>Look up the username (simulated)
     *   <li>Format a welcome message
     * </ol>
     */
    @Test
    @DisplayName("Complete composition workflow")
    void completeCompositionWorkflow() throws Exception {
      // Step 1: Read user ID
      Context<Integer, Integer> getUserId = Context.ask(USER_ID);

      // Step 2: Validate (ID must be positive)
      Context<Integer, Integer> validateId =
          getUserId.flatMap(
              id -> {
                if (id <= 0) {
                  return Context.fail(new IllegalArgumentException("Invalid user ID"));
                }
                return Context.succeed(id);
              });

      // Step 3: "Look up" username (simulated based on ID)
      Context<Integer, String> lookupUser = validateId.flatMap(id -> Context.succeed("User" + id));

      // Step 4: Format welcome message
      Context<Integer, String> welcomeMessage =
          lookupUser.map(name -> "Welcome back, " + name + "!");

      // Run the complete pipeline
      String result = ScopedValue.where(USER_ID, 42).call(() -> welcomeMessage.run());

      assertThat(result).isEqualTo("Welcome back, User42!");

      // Test with invalid ID
      try {
        ScopedValue.where(USER_ID, -1).call(() -> welcomeMessage.run());
        assertThat(false).as("Should have thrown").isTrue();
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).isEqualTo("Invalid user ID");
      }
    }
  }

  /**
   * Congratulations! You've completed Tutorial 02: Context Composition
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How to chain computations with flatMap()
   *   <li>✓ The difference between map() and flatMap()
   *   <li>✓ How to create failing Contexts with fail()
   *   <li>✓ How to discard results with asUnit()
   *   <li>✓ How Context integrates with the Monad type class
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>flatMap is for chaining when the next step returns a Context
   *   <li>map is for simple transformations that don't return Context
   *   <li>Context.fail() enables validation and error handling
   *   <li>ContextMonad provides access to the type class interface
   * </ul>
   *
   * <p>Next: Tutorial 03 - RequestContext Patterns for distributed tracing
   */
}
