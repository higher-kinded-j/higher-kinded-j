// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OptionalSelective Tests")
class OptionalSelectiveTest {

  private final Selective<OptionalKind.Witness> selective = OptionalSelective.INSTANCE;

  @Nested
  @DisplayName("select() - Core selective operation")
  class SelectTests {

    @Test
    @DisplayName("select with Right choice should return the value directly, ignoring function")
    void select_withRightChoice_shouldReturnValue() {
      // Given: A Right choice with value "result"
      Choice<String, String> rightChoice = Selective.right("result");
      Kind<OptionalKind.Witness, Choice<String, String>> fab =
          OPTIONAL.widen(Optional.of(rightChoice));

      // Function should not be used
      Function<String, String> shouldNotBeCalled =
          s -> {
            throw new AssertionError("Function should not be called for Right choice");
          };
      Kind<OptionalKind.Witness, Function<String, String>> ff =
          OPTIONAL.widen(Optional.of(shouldNotBeCalled));

      // When
      Kind<OptionalKind.Witness, String> result = selective.select(fab, ff);

      // Then
      assertThat(OPTIONAL.narrow(result)).isPresent().contains("result");
    }

    @Test
    @DisplayName("select with Left choice and present function should apply function")
    void select_withLeftChoice_shouldApplyFunction() {
      // Given: A Left choice with value "input"
      Choice<String, String> leftChoice = Selective.left("input");
      Kind<OptionalKind.Witness, Choice<String, String>> fab =
          OPTIONAL.widen(Optional.of(leftChoice));

      // Function to apply
      Function<String, String> toUpperCase = String::toUpperCase;
      Kind<OptionalKind.Witness, Function<String, String>> ff =
          OPTIONAL.widen(Optional.of(toUpperCase));

      // When
      Kind<OptionalKind.Witness, String> result = selective.select(fab, ff);

      // Then
      assertThat(OPTIONAL.narrow(result)).isPresent().contains("INPUT");
    }

    @Test
    @DisplayName("select with empty choice should return empty")
    void select_withEmptyChoice_shouldReturnEmpty() {
      // Given: Empty choice
      Kind<OptionalKind.Witness, Choice<String, String>> fab = OPTIONAL.widen(Optional.empty());

      // Function (won't be used)
      Function<String, String> func = String::toUpperCase;
      Kind<OptionalKind.Witness, Function<String, String>> ff = OPTIONAL.widen(Optional.of(func));

      // When
      Kind<OptionalKind.Witness, String> result = selective.select(fab, ff);

      // Then
      assertThat(OPTIONAL.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("select with Left choice but empty function should return empty")
    void select_withLeftChoiceButEmptyFunction_shouldReturnEmpty() {
      // Given: A Left choice
      Choice<String, String> leftChoice = Selective.left("input");
      Kind<OptionalKind.Witness, Choice<String, String>> fab =
          OPTIONAL.widen(Optional.of(leftChoice));

      // Empty function
      Kind<OptionalKind.Witness, Function<String, String>> ff = OPTIONAL.widen(Optional.empty());

      // When
      Kind<OptionalKind.Witness, String> result = selective.select(fab, ff);

      // Then
      assertThat(OPTIONAL.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("select with function returning null should return empty")
    void select_withFunctionReturningNull_shouldReturnEmpty() {
      // Given: A Left choice
      Choice<String, String> leftChoice = Selective.left("input");
      Kind<OptionalKind.Witness, Choice<String, String>> fab =
          OPTIONAL.widen(Optional.of(leftChoice));

      // Function that returns null
      Function<String, String> returnsNull = s -> null;
      Kind<OptionalKind.Witness, Function<String, String>> ff =
          OPTIONAL.widen(Optional.of(returnsNull));

      // When
      Kind<OptionalKind.Witness, String> result = selective.select(fab, ff);

      // Then
      assertThat(OPTIONAL.narrow(result)).isEmpty();
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
      Kind<OptionalKind.Witness, Choice<Integer, String>> fab =
          OPTIONAL.widen(Optional.of(leftChoice));

      // Left handler: multiply by 2
      Function<Integer, String> leftHandler = i -> "Left: " + (i * 2);
      Kind<OptionalKind.Witness, Function<Integer, String>> fl =
          OPTIONAL.widen(Optional.of(leftHandler));

      // Right handler: should not be called
      Function<String, String> rightHandler =
          s -> {
            throw new AssertionError("Right handler should not be called");
          };
      Kind<OptionalKind.Witness, Function<String, String>> fr =
          OPTIONAL.widen(Optional.of(rightHandler));

      // When
      Kind<OptionalKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThat(OPTIONAL.narrow(result)).isPresent().contains("Left: 10");
    }

    @Test
    @DisplayName("branch with Right choice should use right handler")
    void branch_withRightChoice_shouldUseRightHandler() {
      // Given: A Right choice
      Choice<Integer, String> rightChoice = Selective.right("value");
      Kind<OptionalKind.Witness, Choice<Integer, String>> fab =
          OPTIONAL.widen(Optional.of(rightChoice));

      // Left handler: should not be called
      Function<Integer, String> leftHandler =
          i -> {
            throw new AssertionError("Left handler should not be called");
          };
      Kind<OptionalKind.Witness, Function<Integer, String>> fl =
          OPTIONAL.widen(Optional.of(leftHandler));

      // Right handler: convert to uppercase
      Function<String, String> rightHandler = s -> "Right: " + s.toUpperCase();
      Kind<OptionalKind.Witness, Function<String, String>> fr =
          OPTIONAL.widen(Optional.of(rightHandler));

      // When
      Kind<OptionalKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThat(OPTIONAL.narrow(result)).isPresent().contains("Right: VALUE");
    }

    @Test
    @DisplayName("branch with empty choice should return empty")
    void branch_withEmptyChoice_shouldReturnEmpty() {
      // Given: Empty choice
      Kind<OptionalKind.Witness, Choice<Integer, String>> fab = OPTIONAL.widen(Optional.empty());

      Function<Integer, String> leftHandler = i -> "Left: " + i;
      Kind<OptionalKind.Witness, Function<Integer, String>> fl =
          OPTIONAL.widen(Optional.of(leftHandler));

      Function<String, String> rightHandler = s -> "Right: " + s;
      Kind<OptionalKind.Witness, Function<String, String>> fr =
          OPTIONAL.widen(Optional.of(rightHandler));

      // When
      Kind<OptionalKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThat(OPTIONAL.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("branch with Left choice but empty left handler should return empty")
    void branch_withLeftChoiceButEmptyLeftHandler_shouldReturnEmpty() {
      // Given: A Left choice
      Choice<Integer, String> leftChoice = Selective.left(5);
      Kind<OptionalKind.Witness, Choice<Integer, String>> fab =
          OPTIONAL.widen(Optional.of(leftChoice));

      // Empty left handler
      Kind<OptionalKind.Witness, Function<Integer, String>> fl = OPTIONAL.widen(Optional.empty());

      Function<String, String> rightHandler = s -> "Right: " + s;
      Kind<OptionalKind.Witness, Function<String, String>> fr =
          OPTIONAL.widen(Optional.of(rightHandler));

      // When
      Kind<OptionalKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThat(OPTIONAL.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("branch with Right choice but empty right handler should return empty")
    void branch_withRightChoiceButEmptyRightHandler_shouldReturnEmpty() {
      // Given: A Right choice
      Choice<Integer, String> rightChoice = Selective.right("value");
      Kind<OptionalKind.Witness, Choice<Integer, String>> fab =
          OPTIONAL.widen(Optional.of(rightChoice));

      Function<Integer, String> leftHandler = i -> "Left: " + i;
      Kind<OptionalKind.Witness, Function<Integer, String>> fl =
          OPTIONAL.widen(Optional.of(leftHandler));

      // Empty right handler
      Kind<OptionalKind.Witness, Function<String, String>> fr = OPTIONAL.widen(Optional.empty());

      // When
      Kind<OptionalKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThat(OPTIONAL.narrow(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("whenS() - Conditional effect execution with Unit")
  class WhenSTests {

    @Test
    @DisplayName("whenS with true condition should execute effect")
    void whenS_withTrueCondition_shouldExecuteEffect() {
      // Given: Condition is true
      Kind<OptionalKind.Witness, Boolean> condition = OPTIONAL.widen(Optional.of(true));

      // Effect to execute
      Kind<OptionalKind.Witness, Unit> effect = OPTIONAL.widen(Optional.of(Unit.INSTANCE));

      // When
      Kind<OptionalKind.Witness, Unit> result = selective.whenS(condition, effect);

      // Then: Effect was executed, returns Unit
      assertThat(OPTIONAL.narrow(result)).isPresent().contains(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS with false condition should skip effect and return Unit")
    void whenS_withFalseCondition_shouldSkipEffectAndReturnUnit() {
      // Given: Condition is false
      Kind<OptionalKind.Witness, Boolean> condition = OPTIONAL.widen(Optional.of(false));

      // Effect (should not be executed)
      Kind<OptionalKind.Witness, Unit> effect = OPTIONAL.widen(Optional.of(Unit.INSTANCE));

      // When
      Kind<OptionalKind.Witness, Unit> result = selective.whenS(condition, effect);

      // Then: Effect was skipped, but returns Unit.INSTANCE (not empty)
      assertThat(OPTIONAL.narrow(result)).isPresent().contains(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS with empty condition should return empty (no condition to evaluate)")
    void whenS_withEmptyCondition_shouldReturnEmpty() {
      // Given: Condition is empty
      Kind<OptionalKind.Witness, Boolean> condition = OPTIONAL.widen(Optional.empty());

      // Effect
      Kind<OptionalKind.Witness, Unit> effect = OPTIONAL.widen(Optional.of(Unit.INSTANCE));

      // When
      Kind<OptionalKind.Witness, Unit> result = selective.whenS(condition, effect);

      // Then: Returns empty (no condition to evaluate)
      assertThat(OPTIONAL.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("whenS distinguishes between empty condition and false condition")
    void whenS_shouldDistinguishEmptyFromFalse() {
      // Given: False condition
      Kind<OptionalKind.Witness, Boolean> falseCondition = OPTIONAL.widen(Optional.of(false));

      // Empty condition
      Kind<OptionalKind.Witness, Boolean> emptyCondition = OPTIONAL.widen(Optional.empty());

      Kind<OptionalKind.Witness, Unit> effect = OPTIONAL.widen(Optional.of(Unit.INSTANCE));

      // When
      Kind<OptionalKind.Witness, Unit> resultFalse = selective.whenS(falseCondition, effect);
      Kind<OptionalKind.Witness, Unit> resultEmpty = selective.whenS(emptyCondition, effect);

      // Then: False condition returns Unit.INSTANCE, empty condition returns empty
      assertThat(OPTIONAL.narrow(resultFalse)).isPresent().contains(Unit.INSTANCE);
      assertThat(OPTIONAL.narrow(resultEmpty)).isEmpty();
    }
  }

  @Nested
  @DisplayName("whenS_() - Convenience method with value-discarding")
  class WhenS_Tests {

    @Test
    @DisplayName("whenS_ with true condition should execute effect and discard result")
    void whenS__withTrueCondition_shouldExecuteEffectAndDiscardResult() {
      // Given: Condition is true
      Kind<OptionalKind.Witness, Boolean> condition = OPTIONAL.widen(Optional.of(true));

      // Effect that returns a String (will be discarded)
      Kind<OptionalKind.Witness, String> effect = OPTIONAL.widen(Optional.of("some result"));

      // When
      Kind<OptionalKind.Witness, Unit> result = selective.whenS_(condition, effect);

      // Then: Effect was executed, result discarded, Unit returned
      assertThat(OPTIONAL.narrow(result)).isPresent().contains(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS_ with false condition should skip effect and return Unit")
    void whenS__withFalseCondition_shouldSkipEffect() {
      // Given: Condition is false
      Kind<OptionalKind.Witness, Boolean> condition = OPTIONAL.widen(Optional.of(false));

      // Effect
      Kind<OptionalKind.Witness, Integer> effect = OPTIONAL.widen(Optional.of(42));

      // When
      Kind<OptionalKind.Witness, Unit> result = selective.whenS_(condition, effect);

      // Then: Effect skipped, Unit.INSTANCE returned
      assertThat(OPTIONAL.narrow(result)).isPresent().contains(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS_ with empty condition should return empty")
    void whenS__withEmptyCondition_shouldReturnEmpty() {
      // Given: Empty condition
      Kind<OptionalKind.Witness, Boolean> condition = OPTIONAL.widen(Optional.empty());

      // Effect
      Kind<OptionalKind.Witness, String> effect = OPTIONAL.widen(Optional.of("value"));

      // When
      Kind<OptionalKind.Witness, Unit> result = selective.whenS_(condition, effect);

      // Then: Returns empty
      assertThat(OPTIONAL.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("whenS_ should handle empty effect with true condition")
    void whenS__withEmptyEffectAndTrueCondition_shouldReturnEmpty() {
      // Given: True condition but empty effect
      Kind<OptionalKind.Witness, Boolean> condition = OPTIONAL.widen(Optional.of(true));
      Kind<OptionalKind.Witness, String> emptyEffect = OPTIONAL.widen(Optional.empty());

      // When
      Kind<OptionalKind.Witness, Unit> result = selective.whenS_(condition, emptyEffect);

      // Then: Returns empty (effect itself was empty)
      assertThat(OPTIONAL.narrow(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("ifS() - Ternary conditional operator")
  class IfSTests {

    @Test
    @DisplayName("ifS with true condition should return then-branch")
    void ifS_withTrueCondition_shouldReturnThenBranch() {
      // Given: Condition is true
      Kind<OptionalKind.Witness, Boolean> condition = OPTIONAL.widen(Optional.of(true));

      // Branches
      Kind<OptionalKind.Witness, String> thenBranch = OPTIONAL.widen(Optional.of("then"));
      Kind<OptionalKind.Witness, String> elseBranch = OPTIONAL.widen(Optional.of("else"));

      // When
      Kind<OptionalKind.Witness, String> result = selective.ifS(condition, thenBranch, elseBranch);

      // Then
      assertThat(OPTIONAL.narrow(result)).isPresent().contains("then");
    }

    @Test
    @DisplayName("ifS with false condition should return else-branch")
    void ifS_withFalseCondition_shouldReturnElseBranch() {
      // Given: Condition is false
      Kind<OptionalKind.Witness, Boolean> condition = OPTIONAL.widen(Optional.of(false));

      // Branches
      Kind<OptionalKind.Witness, String> thenBranch = OPTIONAL.widen(Optional.of("then"));
      Kind<OptionalKind.Witness, String> elseBranch = OPTIONAL.widen(Optional.of("else"));

      // When
      Kind<OptionalKind.Witness, String> result = selective.ifS(condition, thenBranch, elseBranch);

      // Then
      assertThat(OPTIONAL.narrow(result)).isPresent().contains("else");
    }

    @Test
    @DisplayName("ifS with empty condition should return empty")
    void ifS_withEmptyCondition_shouldReturnEmpty() {
      // Given: Empty condition
      Kind<OptionalKind.Witness, Boolean> condition = OPTIONAL.widen(Optional.empty());

      // Branches
      Kind<OptionalKind.Witness, String> thenBranch = OPTIONAL.widen(Optional.of("then"));
      Kind<OptionalKind.Witness, String> elseBranch = OPTIONAL.widen(Optional.of("else"));

      // When
      Kind<OptionalKind.Witness, String> result = selective.ifS(condition, thenBranch, elseBranch);

      // Then
      assertThat(OPTIONAL.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("ifS with empty then-branch but true condition should return empty")
    void ifS_withEmptyThenBranchAndTrueCondition_shouldReturnEmpty() {
      // Given: True condition, empty then-branch
      Kind<OptionalKind.Witness, Boolean> condition = OPTIONAL.widen(Optional.of(true));
      Kind<OptionalKind.Witness, String> thenBranch = OPTIONAL.widen(Optional.empty());
      Kind<OptionalKind.Witness, String> elseBranch = OPTIONAL.widen(Optional.of("else"));

      // When
      Kind<OptionalKind.Witness, String> result = selective.ifS(condition, thenBranch, elseBranch);

      // Then
      assertThat(OPTIONAL.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("ifS with empty else-branch but false condition should return empty")
    void ifS_withEmptyElseBranchAndFalseCondition_shouldReturnEmpty() {
      // Given: False condition, empty else-branch
      Kind<OptionalKind.Witness, Boolean> condition = OPTIONAL.widen(Optional.of(false));
      Kind<OptionalKind.Witness, String> thenBranch = OPTIONAL.widen(Optional.of("then"));
      Kind<OptionalKind.Witness, String> elseBranch = OPTIONAL.widen(Optional.empty());

      // When
      Kind<OptionalKind.Witness, String> result = selective.ifS(condition, thenBranch, elseBranch);

      // Then
      assertThat(OPTIONAL.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("ifS with different types for branches")
    void ifS_shouldWorkWithDifferentBranchValues() {
      // Given: True condition
      Kind<OptionalKind.Witness, Boolean> condition = OPTIONAL.widen(Optional.of(true));

      // Different values in branches
      Kind<OptionalKind.Witness, Integer> thenBranch = OPTIONAL.widen(Optional.of(100));
      Kind<OptionalKind.Witness, Integer> elseBranch = OPTIONAL.widen(Optional.of(200));

      // When
      Kind<OptionalKind.Witness, Integer> result = selective.ifS(condition, thenBranch, elseBranch);

      // Then
      assertThat(OPTIONAL.narrow(result)).isPresent().contains(100);
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
      Kind<OptionalKind.Witness, Choice<Integer, String>> fab = OPTIONAL.widen(Optional.of(choice));

      // First: use select to transform Left
      Function<Integer, String> doubleAndStringify = i -> String.valueOf(i * 2);
      Kind<OptionalKind.Witness, Function<Integer, String>> ff =
          OPTIONAL.widen(Optional.of(doubleAndStringify));

      Kind<OptionalKind.Witness, String> intermediate = selective.select(fab, ff);

      // Then: use ifS to choose between branches based on result
      Kind<OptionalKind.Witness, Boolean> condition =
          selective.map(s -> Integer.parseInt(s) > 15, intermediate);

      Kind<OptionalKind.Witness, String> thenBranch = OPTIONAL.widen(Optional.of("High"));
      Kind<OptionalKind.Witness, String> elseBranch = OPTIONAL.widen(Optional.of("Low"));

      Kind<OptionalKind.Witness, String> result = selective.ifS(condition, thenBranch, elseBranch);

      // Then: 10 * 2 = 20, which is > 15, so "High"
      assertThat(OPTIONAL.narrow(result)).isPresent().contains("High");
    }

    @Test
    @DisplayName("Chaining whenS operations")
    void chainingWhenSOperations() {
      // Given: Multiple conditional effects
      Kind<OptionalKind.Witness, Boolean> condition1 = OPTIONAL.widen(Optional.of(true));
      Kind<OptionalKind.Witness, Boolean> condition2 = OPTIONAL.widen(Optional.of(false));

      Kind<OptionalKind.Witness, Unit> effect = OPTIONAL.widen(Optional.of(Unit.INSTANCE));

      // When: Chain whenS operations
      Kind<OptionalKind.Witness, Unit> result1 = selective.whenS(condition1, effect);
      Kind<OptionalKind.Witness, Unit> result2 = selective.whenS(condition2, result1);

      // Then: Both should complete (false condition still returns Unit.INSTANCE)
      assertThat(OPTIONAL.narrow(result1)).isPresent();
      assertThat(OPTIONAL.narrow(result2)).isPresent();
    }
  }
}
