// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalAssert.assertThatOptional;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OptionalSelective Tests")
class OptionalSelectiveTest extends OptionalTestBase {

  private final Selective<OptionalKind.Witness> selective = OptionalSelective.INSTANCE;

  @Nested
  @DisplayName("select() - Core selective operation")
  class SelectTests {

    @Test
    @DisplayName("select with Right choice should return the value directly, ignoring function")
    void select_withRightChoice_shouldReturnValue() {
      // Given: A Right choice with value "result"
      Choice<String, String> rightChoice = Selective.right("result");
      Kind<OptionalKind.Witness, Choice<String, String>> fab = presentOf(rightChoice);

      // Function should not be used
      Function<String, String> shouldNotBeCalled =
          s -> {
            throw new AssertionError("Function should not be called for Right choice");
          };
      Kind<OptionalKind.Witness, Function<String, String>> ff = presentOf(shouldNotBeCalled);

      // When
      Kind<OptionalKind.Witness, String> result = selective.select(fab, ff);

      // Then
      assertThatOptional(result).isPresent().contains("result");
    }

    @Test
    @DisplayName("select with Left choice and present function should apply function")
    void select_withLeftChoice_shouldApplyFunction() {
      // Given: A Left choice with value "input"
      Choice<String, String> leftChoice = Selective.left("input");
      Kind<OptionalKind.Witness, Choice<String, String>> fab = presentOf(leftChoice);

      // Function to apply
      Function<String, String> toUpperCase = String::toUpperCase;
      Kind<OptionalKind.Witness, Function<String, String>> ff = presentOf(toUpperCase);

      // When
      Kind<OptionalKind.Witness, String> result = selective.select(fab, ff);

      // Then
      assertThatOptional(result).isPresent().contains("INPUT");
    }

    @Test
    @DisplayName("select with empty choice should return empty")
    void select_withEmptyChoice_shouldReturnEmpty() {
      // Given: Empty choice
      Kind<OptionalKind.Witness, Choice<String, String>> fab = emptyOptional();

      // Function (won't be used)
      Function<String, String> func = String::toUpperCase;
      Kind<OptionalKind.Witness, Function<String, String>> ff = presentOf(func);

      // When
      Kind<OptionalKind.Witness, String> result = selective.select(fab, ff);

      // Then
      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("select with Left choice but empty function should return empty")
    void select_withLeftChoiceButEmptyFunction_shouldReturnEmpty() {
      // Given: A Left choice
      Choice<String, String> leftChoice = Selective.left("input");
      Kind<OptionalKind.Witness, Choice<String, String>> fab = presentOf(leftChoice);

      // Empty function
      Kind<OptionalKind.Witness, Function<String, String>> ff = emptyOptional();

      // When
      Kind<OptionalKind.Witness, String> result = selective.select(fab, ff);

      // Then
      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("select with function returning null should return empty")
    void select_withFunctionReturningNull_shouldReturnEmpty() {
      // Given: A Left choice
      Choice<String, String> leftChoice = Selective.left("input");
      Kind<OptionalKind.Witness, Choice<String, String>> fab = presentOf(leftChoice);

      // Function that returns null
      Function<String, String> returnsNull = s -> null;
      Kind<OptionalKind.Witness, Function<String, String>> ff = presentOf(returnsNull);

      // When
      Kind<OptionalKind.Witness, String> result = selective.select(fab, ff);

      // Then
      assertThatOptional(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("branch() - Two-way conditional choice")
  class BranchTests {

    @Test
    @DisplayName("branch with Left choice should use left handler")
    void branch_withLeftChoice_shouldUseLeftHandler() {
      // Given: A Left choice
      Choice<Integer, String> leftChoice = Selective.left(5);
      Kind<OptionalKind.Witness, Choice<Integer, String>> fab = presentOf(leftChoice);

      // Left handler: multiply by 2
      Function<Integer, String> leftHandler = i -> "Left: " + (i * 2);
      Kind<OptionalKind.Witness, Function<Integer, String>> fl = presentOf(leftHandler);

      // Right handler: should not be called
      Function<String, String> rightHandler =
          s -> {
            throw new AssertionError("Right handler should not be called");
          };
      Kind<OptionalKind.Witness, Function<String, String>> fr = presentOf(rightHandler);

      // When
      Kind<OptionalKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThatOptional(result).isPresent().contains("Left: 10");
    }

    @Test
    @DisplayName("branch with Right choice should use right handler")
    void branch_withRightChoice_shouldUseRightHandler() {
      // Given: A Right choice
      Choice<Integer, String> rightChoice = Selective.right("value");
      Kind<OptionalKind.Witness, Choice<Integer, String>> fab = presentOf(rightChoice);

      // Left handler: should not be called
      Function<Integer, String> leftHandler =
          i -> {
            throw new AssertionError("Left handler should not be called");
          };
      Kind<OptionalKind.Witness, Function<Integer, String>> fl = presentOf(leftHandler);

      // Right handler: convert to uppercase
      Function<String, String> rightHandler = s -> "Right: " + s.toUpperCase();
      Kind<OptionalKind.Witness, Function<String, String>> fr = presentOf(rightHandler);

      // When
      Kind<OptionalKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThatOptional(result).isPresent().contains("Right: VALUE");
    }

    @Test
    @DisplayName("branch with empty choice should return empty")
    void branch_withEmptyChoice_shouldReturnEmpty() {
      // Given: Empty choice
      Kind<OptionalKind.Witness, Choice<Integer, String>> fab = emptyOptional();

      Function<Integer, String> leftHandler = i -> "Left: " + i;
      Kind<OptionalKind.Witness, Function<Integer, String>> fl = presentOf(leftHandler);

      Function<String, String> rightHandler = s -> "Right: " + s;
      Kind<OptionalKind.Witness, Function<String, String>> fr = presentOf(rightHandler);

      // When
      Kind<OptionalKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("branch with Left choice but empty left handler should return empty")
    void branch_withLeftChoiceButEmptyLeftHandler_shouldReturnEmpty() {
      // Given: A Left choice
      Choice<Integer, String> leftChoice = Selective.left(5);
      Kind<OptionalKind.Witness, Choice<Integer, String>> fab = presentOf(leftChoice);

      // Empty left handler
      Kind<OptionalKind.Witness, Function<Integer, String>> fl = emptyOptional();

      Function<String, String> rightHandler = s -> "Right: " + s;
      Kind<OptionalKind.Witness, Function<String, String>> fr = presentOf(rightHandler);

      // When
      Kind<OptionalKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("branch with Right choice but empty right handler should return empty")
    void branch_withRightChoiceButEmptyRightHandler_shouldReturnEmpty() {
      // Given: A Right choice
      Choice<Integer, String> rightChoice = Selective.right("value");
      Kind<OptionalKind.Witness, Choice<Integer, String>> fab = presentOf(rightChoice);

      Function<Integer, String> leftHandler = i -> "Left: " + i;
      Kind<OptionalKind.Witness, Function<Integer, String>> fl = presentOf(leftHandler);

      // Empty right handler
      Kind<OptionalKind.Witness, Function<String, String>> fr = emptyOptional();

      // When
      Kind<OptionalKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThatOptional(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("whenS() - Conditional effect execution with Unit")
  class WhenSTests {

    @Test
    @DisplayName("whenS with true condition should execute effect")
    void whenS_withTrueCondition_shouldExecuteEffect() {
      // Given: Condition is true
      Kind<OptionalKind.Witness, Boolean> condition = presentOf(true);

      // Effect to execute
      Kind<OptionalKind.Witness, Unit> effect = presentOf(Unit.INSTANCE);

      // When
      Kind<OptionalKind.Witness, Unit> result = selective.whenS(condition, effect);

      // Then: Effect was executed, returns Unit
      assertThatOptional(result).isPresent().contains(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS with false condition should skip effect and return Unit")
    void whenS_withFalseCondition_shouldSkipEffectAndReturnUnit() {
      // Given: Condition is false
      Kind<OptionalKind.Witness, Boolean> condition = presentOf(false);

      // Effect (should not be executed)
      Kind<OptionalKind.Witness, Unit> effect = presentOf(Unit.INSTANCE);

      // When
      Kind<OptionalKind.Witness, Unit> result = selective.whenS(condition, effect);

      // Then: Effect was skipped, but returns Unit.INSTANCE (not empty)
      assertThatOptional(result).isPresent().contains(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS with empty condition should return empty (no condition to evaluate)")
    void whenS_withEmptyCondition_shouldReturnEmpty() {
      // Given: Condition is empty
      Kind<OptionalKind.Witness, Boolean> condition = emptyOptional();

      // Effect
      Kind<OptionalKind.Witness, Unit> effect = presentOf(Unit.INSTANCE);

      // When
      Kind<OptionalKind.Witness, Unit> result = selective.whenS(condition, effect);

      // Then: Returns empty (no condition to evaluate)
      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("whenS distinguishes between empty condition and false condition")
    void whenS_shouldDistinguishEmptyFromFalse() {
      // Given: False condition
      Kind<OptionalKind.Witness, Boolean> falseCondition = presentOf(false);

      // Empty condition
      Kind<OptionalKind.Witness, Boolean> emptyCondition = emptyOptional();

      Kind<OptionalKind.Witness, Unit> effect = presentOf(Unit.INSTANCE);

      // When
      Kind<OptionalKind.Witness, Unit> resultFalse = selective.whenS(falseCondition, effect);
      Kind<OptionalKind.Witness, Unit> resultEmpty = selective.whenS(emptyCondition, effect);

      // Then: False condition returns Unit.INSTANCE, empty condition returns empty
      assertThatOptional(resultFalse).isPresent().contains(Unit.INSTANCE);
      assertThatOptional(resultEmpty).isEmpty();
    }
  }

  @Nested
  @DisplayName("whenS_() - Convenience method with value-discarding")
  class WhenS_Tests {

    @Test
    @DisplayName("whenS_ with true condition should execute effect and discard result")
    void whenS__withTrueCondition_shouldExecuteEffectAndDiscardResult() {
      // Given: Condition is true
      Kind<OptionalKind.Witness, Boolean> condition = presentOf(true);

      // Effect that returns a String (will be discarded)
      Kind<OptionalKind.Witness, String> effect = presentOf("some result");

      // When
      Kind<OptionalKind.Witness, Unit> result = selective.whenS_(condition, effect);

      // Then: Effect was executed, result discarded, Unit returned
      assertThatOptional(result).isPresent().contains(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS_ with false condition should skip effect and return Unit")
    void whenS__withFalseCondition_shouldSkipEffect() {
      // Given: Condition is false
      Kind<OptionalKind.Witness, Boolean> condition = presentOf(false);

      // Effect
      Kind<OptionalKind.Witness, Integer> effect = presentOf(42);

      // When
      Kind<OptionalKind.Witness, Unit> result = selective.whenS_(condition, effect);

      // Then: Effect skipped, Unit.INSTANCE returned
      assertThatOptional(result).isPresent().contains(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS_ with empty condition should return empty")
    void whenS__withEmptyCondition_shouldReturnEmpty() {
      // Given: Empty condition
      Kind<OptionalKind.Witness, Boolean> condition = emptyOptional();

      // Effect
      Kind<OptionalKind.Witness, String> effect = presentOf("value");

      // When
      Kind<OptionalKind.Witness, Unit> result = selective.whenS_(condition, effect);

      // Then: Returns empty
      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("whenS_ should handle empty effect with true condition")
    void whenS__withEmptyEffectAndTrueCondition_shouldReturnEmpty() {
      // Given: True condition but empty effect
      Kind<OptionalKind.Witness, Boolean> condition = presentOf(true);
      Kind<OptionalKind.Witness, String> emptyEffect = emptyOptional();

      // When
      Kind<OptionalKind.Witness, Unit> result = selective.whenS_(condition, emptyEffect);

      // Then: Returns empty (effect itself was empty)
      assertThatOptional(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("ifS() - Ternary conditional operator")
  class IfSTests {

    @Test
    @DisplayName("ifS with true condition should return then-branch")
    void ifS_withTrueCondition_shouldReturnThenBranch() {
      // Given: Condition is true
      Kind<OptionalKind.Witness, Boolean> condition = presentOf(true);

      // Branches
      Kind<OptionalKind.Witness, String> thenBranch = presentOf("then");
      Kind<OptionalKind.Witness, String> elseBranch = presentOf("else");

      // When
      Kind<OptionalKind.Witness, String> result = selective.ifS(condition, thenBranch, elseBranch);

      // Then
      assertThatOptional(result).isPresent().contains("then");
    }

    @Test
    @DisplayName("ifS with false condition should return else-branch")
    void ifS_withFalseCondition_shouldReturnElseBranch() {
      // Given: Condition is false
      Kind<OptionalKind.Witness, Boolean> condition = presentOf(false);

      // Branches
      Kind<OptionalKind.Witness, String> thenBranch = presentOf("then");
      Kind<OptionalKind.Witness, String> elseBranch = presentOf("else");

      // When
      Kind<OptionalKind.Witness, String> result = selective.ifS(condition, thenBranch, elseBranch);

      // Then
      assertThatOptional(result).isPresent().contains("else");
    }

    @Test
    @DisplayName("ifS with empty condition should return empty")
    void ifS_withEmptyCondition_shouldReturnEmpty() {
      // Given: Empty condition
      Kind<OptionalKind.Witness, Boolean> condition = emptyOptional();

      // Branches
      Kind<OptionalKind.Witness, String> thenBranch = presentOf("then");
      Kind<OptionalKind.Witness, String> elseBranch = presentOf("else");

      // When
      Kind<OptionalKind.Witness, String> result = selective.ifS(condition, thenBranch, elseBranch);

      // Then
      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("ifS with empty then-branch but true condition should return empty")
    void ifS_withEmptyThenBranchAndTrueCondition_shouldReturnEmpty() {
      // Given: True condition, empty then-branch
      Kind<OptionalKind.Witness, Boolean> condition = presentOf(true);
      Kind<OptionalKind.Witness, String> thenBranch = emptyOptional();
      Kind<OptionalKind.Witness, String> elseBranch = presentOf("else");

      // When
      Kind<OptionalKind.Witness, String> result = selective.ifS(condition, thenBranch, elseBranch);

      // Then
      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("ifS with empty else-branch but false condition should return empty")
    void ifS_withEmptyElseBranchAndFalseCondition_shouldReturnEmpty() {
      // Given: False condition, empty else-branch
      Kind<OptionalKind.Witness, Boolean> condition = presentOf(false);
      Kind<OptionalKind.Witness, String> thenBranch = presentOf("then");
      Kind<OptionalKind.Witness, String> elseBranch = emptyOptional();

      // When
      Kind<OptionalKind.Witness, String> result = selective.ifS(condition, thenBranch, elseBranch);

      // Then
      assertThatOptional(result).isEmpty();
    }

    @Test
    @DisplayName("ifS with different types for branches")
    void ifS_shouldWorkWithDifferentBranchValues() {
      // Given: True condition
      Kind<OptionalKind.Witness, Boolean> condition = presentOf(true);

      // Different values in branches
      Kind<OptionalKind.Witness, Integer> thenBranch = presentOf(100);
      Kind<OptionalKind.Witness, Integer> elseBranch = presentOf(200);

      // When
      Kind<OptionalKind.Witness, Integer> result = selective.ifS(condition, thenBranch, elseBranch);

      // Then
      assertThatOptional(result).isPresent().contains(100);
    }
  }

  @Nested
  @DisplayName("Integration and Composition Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Composing select and branch operations")
    void composingSelectAndBranch() {
      // Given: A choice based on a condition
      Choice<Integer, String> choice = Selective.left(10);
      Kind<OptionalKind.Witness, Choice<Integer, String>> fab = presentOf(choice);

      // First: use select to transform Left
      Function<Integer, String> doubleAndStringify = i -> String.valueOf(i * 2);
      Kind<OptionalKind.Witness, Function<Integer, String>> ff = presentOf(doubleAndStringify);

      Kind<OptionalKind.Witness, String> intermediate = selective.select(fab, ff);

      // Then: use ifS to choose between branches based on result
      Kind<OptionalKind.Witness, Boolean> condition =
          selective.map(s -> Integer.parseInt(s) > 15, intermediate);

      Kind<OptionalKind.Witness, String> thenBranch = presentOf("High");
      Kind<OptionalKind.Witness, String> elseBranch = presentOf("Low");

      Kind<OptionalKind.Witness, String> result = selective.ifS(condition, thenBranch, elseBranch);

      // Then: 10 * 2 = 20, which is > 15, so "High"
      assertThatOptional(result).isPresent().contains("High");
    }

    @Test
    @DisplayName("Chaining whenS operations")
    void chainingWhenSOperations() {
      // Given: Multiple conditional effects
      Kind<OptionalKind.Witness, Boolean> condition1 = presentOf(true);
      Kind<OptionalKind.Witness, Boolean> condition2 = presentOf(false);

      Kind<OptionalKind.Witness, Unit> effect = presentOf(Unit.INSTANCE);

      // When: Chain whenS operations
      Kind<OptionalKind.Witness, Unit> result1 = selective.whenS(condition1, effect);
      Kind<OptionalKind.Witness, Unit> result2 = selective.whenS(condition2, result1);

      // Then: Both should complete (false condition still returns Unit.INSTANCE)
      assertThatOptional(result1).isPresent();
      assertThatOptional(result2).isPresent();
    }
  }
}
