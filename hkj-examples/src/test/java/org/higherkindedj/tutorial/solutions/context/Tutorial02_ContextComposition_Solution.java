// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.context;

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

/** Solutions for Tutorial 02: Context Composition */
@DisplayName("Tutorial 02: Context Composition - Solutions")
public class Tutorial02_ContextComposition_Solution {

  private static final ScopedValue<String> USER_NAME = ScopedValue.newInstance();
  private static final ScopedValue<Integer> USER_ID = ScopedValue.newInstance();

  @Nested
  @DisplayName("Part 1: Chaining with flatMap")
  class ChainingWithFlatMap {

    @Test
    @DisplayName("Exercise 1: Basic flatMap chaining")
    void exercise1_basicFlatMap() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Use flatMap to chain computations
      Context<String, String> greeting =
          getName.flatMap(name -> Context.succeed("Hello, " + name + "!"));

      String result = ScopedValue.where(USER_NAME, "Alice").call(() -> greeting.run());
      assertThat(result).isEqualTo("Hello, Alice!");
    }

    @Test
    @DisplayName("Exercise 2: Using flatMap for dependent computation")
    void exercise2_flatMapDependent() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Use flatMap to transform the result type
      Context<String, Integer> getNameLength =
          getName.flatMap(name -> Context.succeed(name.length()));

      Integer result = ScopedValue.where(USER_NAME, "Bob").call(() -> getNameLength.run());
      assertThat(result).isEqualTo(3);
    }

    @Test
    @DisplayName("Exercise 3: Multiple flatMap chain")
    void exercise3_multipleFlatMaps() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Chain multiple flatMap calls
      Context<String, String> formatted =
          getName
              .flatMap(name -> Context.succeed("Hello, " + name))
              .flatMap(greeting -> Context.succeed("[" + greeting + "]"));

      String result = ScopedValue.where(USER_NAME, "Charlie").call(() -> formatted.run());
      assertThat(result).isEqualTo("[Hello, Charlie]");
    }

    @Test
    @DisplayName("Exercise 4: map vs flatMap")
    void exercise4_mapVsFlatMap() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Use map for simple transformations
      Context<String, String> upperName = getName.map(String::toUpperCase);

      // SOLUTION: Use flatMap when returning a Context
      Context<String, String> userLabel = getName.flatMap(name -> Context.succeed("User: " + name));

      String upper = ScopedValue.where(USER_NAME, "diana").call(() -> upperName.run());
      String label = ScopedValue.where(USER_NAME, "diana").call(() -> userLabel.run());

      assertThat(upper).isEqualTo("DIANA");
      assertThat(label).isEqualTo("User: diana");
    }
  }

  @Nested
  @DisplayName("Part 2: Context.fail and Error Propagation")
  class FailAndErrorPropagation {

    @Test
    @DisplayName("Exercise 5: Create a failing Context")
    void exercise5_failingContext() {
      // SOLUTION: Use Context.fail() with an exception
      Context<String, String> failing = Context.fail(new IllegalArgumentException("Invalid input"));

      try {
        failing.run();
        assertThat(false).as("Should have thrown").isTrue();
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).isEqualTo("Invalid input");
      }
    }

    @Test
    @DisplayName("Exercise 6: Conditional failure")
    void exercise6_conditionalFailure() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Use flatMap for conditional logic
      Context<String, String> validateAndGreet =
          getName.flatMap(
              name -> {
                if (name.isEmpty()) {
                  return Context.fail(new IllegalArgumentException("Name cannot be empty"));
                }
                return Context.succeed("Hello, " + name);
              });

      String valid = ScopedValue.where(USER_NAME, "Eve").call(() -> validateAndGreet.run());
      assertThat(valid).isEqualTo("Hello, Eve");

      try {
        ScopedValue.where(USER_NAME, "").call(() -> validateAndGreet.run());
        assertThat(false).as("Should have thrown").isTrue();
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).isEqualTo("Name cannot be empty");
      }
    }
  }

  @Nested
  @DisplayName("Part 3: Using asUnit()")
  class UsingAsUnit {

    @Test
    @DisplayName("Exercise 7: Discard result with asUnit()")
    void exercise7_asUnit() throws Exception {
      Context<String, String> getName = Context.ask(USER_NAME);

      // SOLUTION: Use asUnit() to discard the result
      Context<String, Unit> validateName =
          getName
              .flatMap(
                  name -> {
                    if (name.length() < 3) {
                      return Context.fail(new IllegalArgumentException("Name too short"));
                    }
                    return Context.succeed(name);
                  })
              .asUnit();

      Unit result = ScopedValue.where(USER_NAME, "Frank").call(() -> validateName.run());
      assertThat(result).isEqualTo(Unit.INSTANCE);

      try {
        ScopedValue.where(USER_NAME, "Jo").call(() -> validateName.run());
        assertThat(false).as("Should have thrown").isTrue();
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).isEqualTo("Name too short");
      }
    }
  }

  @Nested
  @DisplayName("Part 4: Type Class Integration")
  class TypeClassIntegration {

    @Test
    @DisplayName("Exercise 8: Using Monad.of()")
    void exercise8_monadOf() {
      ContextMonad<String> monad = ContextMonad.instance();

      // SOLUTION: Use monad.of() to lift a value (Applicative's pure operation)
      Kind<ContextKind.Witness<String>, Integer> pureKind = monad.of(42);

      Context<String, Integer> context = ContextKindHelper.CONTEXT.narrow(pureKind);
      Integer result = context.run();

      assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("Exercise 9: Using Functor.map() through monad")
    void exercise9_functorMap() throws Exception {
      ContextMonad<String> monad = ContextMonad.instance();

      Context<String, String> getName = Context.ask(USER_NAME);
      Kind<ContextKind.Witness<String>, String> nameKind = ContextKindHelper.CONTEXT.widen(getName);

      // SOLUTION: Use monad.map() with the Kind
      Kind<ContextKind.Witness<String>, Integer> lengthKind = monad.map(String::length, nameKind);

      Context<String, Integer> lengthContext = ContextKindHelper.CONTEXT.narrow(lengthKind);
      Integer result = ScopedValue.where(USER_NAME, "Grace").call(() -> lengthContext.run());

      assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("Exercise 10: Using Monad.flatMap()")
    void exercise10_monadFlatMap() throws Exception {
      ContextMonad<String> monad = ContextMonad.instance();

      Context<String, String> getName = Context.ask(USER_NAME);
      Kind<ContextKind.Witness<String>, String> nameKind = ContextKindHelper.CONTEXT.widen(getName);

      // SOLUTION: Use monad.flatMap() with widened result
      Kind<ContextKind.Witness<String>, String> greetingKind =
          monad.flatMap(
              name -> ContextKindHelper.CONTEXT.widen(Context.succeed("Hello, " + name)), nameKind);

      Context<String, String> greetingContext = ContextKindHelper.CONTEXT.narrow(greetingKind);
      String result = ScopedValue.where(USER_NAME, "Henry").call(() -> greetingContext.run());

      assertThat(result).isEqualTo("Hello, Henry");
    }
  }

  @Nested
  @DisplayName("Bonus: Real-World Patterns")
  class RealWorldPatterns {

    @Test
    @DisplayName("Complete composition workflow")
    void completeCompositionWorkflow() throws Exception {
      Context<Integer, Integer> getUserId = Context.ask(USER_ID);

      Context<Integer, Integer> validateId =
          getUserId.flatMap(
              id -> {
                if (id <= 0) {
                  return Context.fail(new IllegalArgumentException("Invalid user ID"));
                }
                return Context.succeed(id);
              });

      Context<Integer, String> lookupUser = validateId.flatMap(id -> Context.succeed("User" + id));

      Context<Integer, String> welcomeMessage =
          lookupUser.map(name -> "Welcome back, " + name + "!");

      String result = ScopedValue.where(USER_ID, 42).call(() -> welcomeMessage.run());

      assertThat(result).isEqualTo("Welcome back, User42!");

      try {
        ScopedValue.where(USER_ID, -1).call(() -> welcomeMessage.run());
        assertThat(false).as("Should have thrown").isTrue();
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).isEqualTo("Invalid user ID");
      }
    }
  }
}
