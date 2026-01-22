// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import org.higherkindedj.hkt.context.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 01: Context Basics
 *
 * <p>This file contains the completed solutions for all exercises. Compare your answers with these
 * solutions after attempting the tutorial.
 */
@DisplayName("Tutorial 01: Context Basics - Solutions")
public class Tutorial01_ContextBasics_Solution {

  private static final ScopedValue<String> USER_NAME = ScopedValue.newInstance();
  private static final ScopedValue<Integer> MAX_ITEMS = ScopedValue.newInstance();
  private static final ScopedValue<Boolean> DEBUG_MODE = ScopedValue.newInstance();

  @Nested
  @DisplayName("Part 1: Creating Contexts")
  class CreatingContexts {

    @Test
    @DisplayName("Exercise 1: Read a ScopedValue with ask()")
    void exercise1_readWithAsk() throws Exception {
      // SOLUTION: Use Context.ask() with the ScopedValue key
      Context<String, String> getUserName = Context.ask(USER_NAME);

      String result = ScopedValue.where(USER_NAME, "Alice").call(() -> getUserName.run());

      assertThat(result).isEqualTo("Alice");
    }

    @Test
    @DisplayName("Exercise 2: Create a pure value with succeed()")
    void exercise2_pureWithSucceed() {
      // SOLUTION: Use Context.succeed() to create a pure value
      Context<String, Integer> pureValue = Context.succeed(42);

      Integer result = pureValue.run();

      assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("Exercise 3: Unbound ScopedValue throws exception")
    void exercise3_unboundThrows() {
      Context<String, String> getUserName = Context.ask(USER_NAME);

      // SOLUTION: NoSuchElementException is thrown when ScopedValue is not bound
      assertThatThrownBy(() -> getUserName.run()).isInstanceOf(NoSuchElementException.class);
    }
  }

  @Nested
  @DisplayName("Part 2: Reading and Transforming")
  class ReadingAndTransforming {

    @Test
    @DisplayName("Exercise 4: Read and transform with asks()")
    void exercise4_readAndTransform() throws Exception {
      // SOLUTION: Use Context.asks() with a transformation function
      Context<String, String> getUpperName = Context.asks(USER_NAME, String::toUpperCase);

      String result = ScopedValue.where(USER_NAME, "bob").call(() -> getUpperName.run());

      assertThat(result).isEqualTo("BOB");
    }

    @Test
    @DisplayName("Exercise 5: Change result type with asks()")
    void exercise5_changeType() throws Exception {
      // SOLUTION: The transformation can change the result type
      Context<String, Integer> getNameLength = Context.asks(USER_NAME, String::length);

      Integer result = ScopedValue.where(USER_NAME, "Charlie").call(() -> getNameLength.run());

      assertThat(result).isEqualTo(7);
    }

    @Test
    @DisplayName("Exercise 6: Format a message with asks()")
    void exercise6_formatMessage() throws Exception {
      // SOLUTION: Use string concatenation in the transformation
      Context<Integer, String> getLimitMessage =
          Context.asks(MAX_ITEMS, n -> "Limit: " + n + " items");

      String result = ScopedValue.where(MAX_ITEMS, 100).call(() -> getLimitMessage.run());

      assertThat(result).isEqualTo("Limit: 100 items");
    }
  }

  @Nested
  @DisplayName("Part 3: Transforming with map()")
  class TransformingWithMap {

    @Test
    @DisplayName("Exercise 7: Transform with map()")
    void exercise7_mapTransform() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Use map() to transform the result
      Context<String, Integer> getLength = getName.map(String::length);

      Integer result = ScopedValue.where(USER_NAME, "Diana").call(() -> getLength.run());

      assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("Exercise 8: Chain multiple maps")
    void exercise8_chainMaps() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Chain multiple map() calls for step-by-step transformation
      Context<String, String> formatted =
          getName.map(String::trim).map(String::toUpperCase).map(s -> "[" + s + "]");

      String result = ScopedValue.where(USER_NAME, "  eve  ").call(() -> formatted.run());

      assertThat(result).isEqualTo("[EVE]");
    }

    @Test
    @DisplayName("Exercise 9: Map boolean to string")
    void exercise9_mapBoolean() throws Exception {
      Context<Boolean, Boolean> isDebug = Context.ask(DEBUG_MODE);

      // SOLUTION: Use conditional expression in map
      Context<Boolean, String> getLogLevel = isDebug.map(debug -> debug ? "DEBUG" : "INFO");

      String debugLevel = ScopedValue.where(DEBUG_MODE, true).call(() -> getLogLevel.run());
      String infoLevel = ScopedValue.where(DEBUG_MODE, false).call(() -> getLogLevel.run());

      assertThat(debugLevel).isEqualTo("DEBUG");
      assertThat(infoLevel).isEqualTo("INFO");
    }
  }

  @Nested
  @DisplayName("Part 4: Running in Scopes")
  class RunningInScopes {

    @Test
    @DisplayName("Exercise 10: Bind multiple values")
    void exercise10_multipleBindings() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);
      Context<Integer, Integer> getMax = Context.ask(MAX_ITEMS);

      // SOLUTION: Provide the correct values in where() calls
      String userName =
          ScopedValue.where(USER_NAME, "Frank").where(MAX_ITEMS, 50).call(() -> getName.run());

      Integer maxItems =
          ScopedValue.where(USER_NAME, "Frank").where(MAX_ITEMS, 50).call(() -> getMax.run());

      assertThat(userName).isEqualTo("Frank");
      assertThat(maxItems).isEqualTo(50);
    }

    @Test
    @DisplayName("Exercise 11: Using run() for side effects")
    void exercise11_runForSideEffects() {
      Context<String, String> getName = Context.ask(USER_NAME);
      StringBuilder log = new StringBuilder();

      // SOLUTION: Use "Grace" as the binding value
      ScopedValue.where(USER_NAME, "Grace")
          .run(
              () -> {
                String name = getName.run();
                log.append("Processing: ").append(name);
              });

      assertThat(log.toString()).isEqualTo("Processing: Grace");
    }
  }

  @Nested
  @DisplayName("Bonus: Complete Examples")
  class BonusExamples {

    @Test
    @DisplayName("Complete workflow example")
    void completeWorkflowExample() throws Exception {
      Context<String, String> greeting = Context.asks(USER_NAME, name -> "Hello, " + name + "!");

      Context<Integer, String> itemsMessage =
          Context.asks(MAX_ITEMS, n -> "You can add up to " + n + " items.");

      Context<Boolean, String> modeMessage =
          Context.asks(DEBUG_MODE, debug -> debug ? "[DEBUG MODE ENABLED]" : "");

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
}
