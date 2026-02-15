// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.higherkindedj.hkt.list.ListAssert.assertThatList;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ListSelective Tests")
class ListSelectiveTest extends ListTestBase {

  private final Selective<ListKind.Witness> selective = ListSelective.INSTANCE;

  @Nested
  @DisplayName("select() - Core selective operation")
  class SelectTests {

    @Test
    @DisplayName("select with all Right choices should return values directly")
    void select_withAllRightChoices_shouldReturnValues() {
      // Given: List of Right choices
      List<Choice<String, String>> choices =
          Arrays.asList(Selective.right("A"), Selective.right("B"), Selective.right("C"));
      Kind<ListKind.Witness, Choice<String, String>> fab = wrapList(choices);

      // Functions (should not be used)
      Function<String, String> shouldNotBeCalled =
          s -> {
            throw new AssertionError("Function should not be called for Right choices");
          };
      Kind<ListKind.Witness, Function<String, String>> ff = singletonList(shouldNotBeCalled);

      // When
      Kind<ListKind.Witness, String> result = selective.select(fab, ff);

      // Then: All Right values returned
      assertThatList(result).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("select with all Left choices should apply all functions to all values")
    void select_withAllLeftChoices_shouldApplyFunctions() {
      // Given: List of Left choices
      List<Choice<Integer, String>> choices = Arrays.asList(Selective.left(1), Selective.left(2));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = wrapList(choices);

      // Functions to apply
      Kind<ListKind.Witness, Function<Integer, String>> ff = listOf(i -> "A" + i, i -> "B" + i);

      // When
      Kind<ListKind.Witness, String> result = selective.select(fab, ff);

      // Then: Each function applied to each value (cartesian product)
      assertThatList(result).containsExactly("A1", "B1", "A2", "B2");
    }

    @Test
    @DisplayName("select with mixed Left and Right choices")
    void select_withMixedChoices_shouldHandleAppropriately() {
      // Given: Mixed choices
      List<Choice<Integer, String>> choices =
          Arrays.asList(Selective.left(1), Selective.right("X"), Selective.left(2));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = wrapList(choices);

      // Functions
      Kind<ListKind.Witness, Function<Integer, String>> ff = singletonList(i -> "N" + i);

      // When
      Kind<ListKind.Witness, String> result = selective.select(fab, ff);

      // Then: Left(1) -> N1, Right(X) -> X, Left(2) -> N2
      assertThatList(result).containsExactly("N1", "X", "N2");
    }

    @Test
    @DisplayName("select with empty choices should return empty list")
    void select_withEmptyChoices_shouldReturnEmpty() {
      // Given: Empty choices
      Kind<ListKind.Witness, Choice<String, String>> fab = emptyList();

      // Functions
      Kind<ListKind.Witness, Function<String, String>> ff = singletonList(String::toUpperCase);

      // When
      Kind<ListKind.Witness, String> result = selective.select(fab, ff);

      // Then
      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("select with Left choices but empty functions should skip those elements")
    void select_withLeftChoicesButEmptyFunctions_shouldSkipElements() {
      // Given: Left choices
      List<Choice<Integer, String>> choices = Arrays.asList(Selective.left(1), Selective.left(2));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = wrapList(choices);

      // Empty functions
      Kind<ListKind.Witness, Function<Integer, String>> ff = emptyList();

      // When
      Kind<ListKind.Witness, String> result = selective.select(fab, ff);

      // Then: Empty result (no functions to apply)
      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("select with multiple functions creates non-deterministic results")
    void select_withMultipleFunctions_shouldCreateNonDeterministicResults() {
      // Given: One Left choice
      Kind<ListKind.Witness, Choice<Integer, String>> fab = listOf(Selective.left(5));

      // Multiple functions
      Kind<ListKind.Witness, Function<Integer, String>> ff =
          listOf(i -> "double:" + (i * 2), i -> "triple:" + (i * 3), i -> "square:" + (i * i));

      // When
      Kind<ListKind.Witness, String> result = selective.select(fab, ff);

      // Then: All three transformations applied
      assertThatList(result).containsExactly("double:10", "triple:15", "square:25");
    }
  }

  @Nested
  @DisplayName("branch() - Two-way conditional choice")
  class BranchTests {

    @Test
    @DisplayName("branch with all Left choices should use left handler")
    void branch_withAllLeftChoices_shouldUseLeftHandler() {
      // Given: Left choices
      List<Choice<Integer, String>> choices = Arrays.asList(Selective.left(1), Selective.left(2));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = wrapList(choices);

      // Left handler
      Kind<ListKind.Witness, Function<Integer, String>> fl = singletonList(i -> "Left:" + (i * 10));

      // Right handler (should not be used)
      Function<String, String> shouldNotBeCalled =
          s -> {
            throw new AssertionError("Right handler should not be called");
          };
      Kind<ListKind.Witness, Function<String, String>> fr = singletonList(shouldNotBeCalled);

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThatList(result).containsExactly("Left:10", "Left:20");
    }

    @Test
    @DisplayName("branch with all Right choices should use right handler")
    void branch_withAllRightChoices_shouldUseRightHandler() {
      // Given: Right choices
      List<Choice<Integer, String>> choices =
          Arrays.asList(Selective.right("A"), Selective.right("B"));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = wrapList(choices);

      // Left handler (should not be used)
      Function<Integer, String> shouldNotBeCalled =
          i -> {
            throw new AssertionError("Left handler should not be called");
          };
      Kind<ListKind.Witness, Function<Integer, String>> fl = singletonList(shouldNotBeCalled);

      // Right handler
      Kind<ListKind.Witness, Function<String, String>> fr = singletonList(s -> "Right:" + s);

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThatList(result).containsExactly("Right:A", "Right:B");
    }

    @Test
    @DisplayName("branch with mixed choices should use appropriate handlers")
    void branch_withMixedChoices_shouldUseAppropriateHandlers() {
      // Given: Mixed choices
      List<Choice<Integer, String>> choices =
          Arrays.asList(Selective.left(5), Selective.right("X"), Selective.left(10));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = wrapList(choices);

      // Handlers
      Kind<ListKind.Witness, Function<Integer, String>> fl = singletonList(i -> "L" + i);
      Kind<ListKind.Witness, Function<String, String>> fr = singletonList(s -> "R" + s);

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThatList(result).containsExactly("L5", "RX", "L10");
    }

    @Test
    @DisplayName("branch with empty choices should return empty list")
    void branch_withEmptyChoices_shouldReturnEmpty() {
      // Given: Empty choices
      Kind<ListKind.Witness, Choice<Integer, String>> fab = emptyList();

      Kind<ListKind.Witness, Function<Integer, String>> fl = singletonList(i -> "L" + i);
      Kind<ListKind.Witness, Function<String, String>> fr = singletonList(s -> "R" + s);

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("branch with multiple handlers creates non-deterministic results")
    void branch_withMultipleHandlers_shouldCreateNonDeterministicResults() {
      // Given: One Left choice, multiple handlers
      Kind<ListKind.Witness, Choice<Integer, String>> fab = listOf(Selective.left(3));

      // Multiple left handlers
      Kind<ListKind.Witness, Function<Integer, String>> fl = listOf(i -> "A" + i, i -> "B" + i);
      Kind<ListKind.Witness, Function<String, String>> fr = singletonList(s -> "R" + s);

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then: Both handlers applied
      assertThatList(result).containsExactly("A3", "B3");
    }

    @Test
    @DisplayName("branch with Left choices but empty left handlers should skip")
    void branch_withLeftChoicesButEmptyLeftHandlers_shouldSkip() {
      // Given: Left choices
      List<Choice<Integer, String>> choices = Arrays.asList(Selective.left(1), Selective.left(2));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = wrapList(choices);

      // Empty left handlers
      Kind<ListKind.Witness, Function<Integer, String>> fl = emptyList();
      Kind<ListKind.Witness, Function<String, String>> fr = singletonList(s -> "R" + s);

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then: Empty (no left handlers available)
      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("branch with Right choices but empty right handlers should skip")
    void branch_withRightChoicesButEmptyRightHandlers_shouldSkip() {
      // Given: Right choices
      List<Choice<Integer, String>> choices =
          Arrays.asList(Selective.right("A"), Selective.right("B"));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = wrapList(choices);

      Kind<ListKind.Witness, Function<Integer, String>> fl = singletonList(i -> "L" + i);
      Kind<ListKind.Witness, Function<String, String>> fr = emptyList();

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then: Empty (no right handlers available to process Right choices)
      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("branch with mixed choices and selectively empty handlers")
    void branch_withMixedChoicesAndSelectivelyEmptyHandlers_shouldSkipAppropriately() {
      // Given: Mixed choices - Left with handler, Right without handler
      List<Choice<Integer, String>> choices =
          Arrays.asList(Selective.left(1), Selective.right("X"), Selective.left(2));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = wrapList(choices);

      // Left handlers present
      Kind<ListKind.Witness, Function<Integer, String>> fl = singletonList(i -> "L" + i);

      // Empty right handlers
      Kind<ListKind.Witness, Function<String, String>> fr = emptyList();

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then: Only Left choices processed (Right choices skipped due to empty handlers)
      assertThatList(result).containsExactly("L1", "L2");
    }

    @Test
    @DisplayName("branch with mixed choices and opposite empty handlers")
    void branch_withMixedChoicesAndOppositeEmptyHandlers_shouldSkipAppropriately() {
      // Given: Mixed choices - Right with handler, Left without handler
      List<Choice<Integer, String>> choices =
          Arrays.asList(Selective.left(1), Selective.right("X"), Selective.left(2));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = wrapList(choices);

      // Empty left handlers
      Kind<ListKind.Witness, Function<Integer, String>> fl = emptyList();

      // Right handlers present
      Kind<ListKind.Witness, Function<String, String>> fr = singletonList(s -> "R" + s);

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then: Only Right choices processed (Left choices skipped due to empty handlers)
      assertThatList(result).containsExactly("RX");
    }

    @Test
    @DisplayName("branch with both handlers empty should return empty regardless of choices")
    void branch_withBothHandlersEmpty_shouldReturnEmpty() {
      // Given: Mixed choices
      List<Choice<Integer, String>> choices =
          Arrays.asList(Selective.left(1), Selective.right("X"));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = wrapList(choices);

      // Both handlers empty
      Kind<ListKind.Witness, Function<Integer, String>> fl = emptyList();
      Kind<ListKind.Witness, Function<String, String>> fr = emptyList();

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then: Empty (no handlers available for any choices)
      assertThatList(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("whenS() - Conditional effect execution with Unit")
  class WhenSTests {

    @Test
    @DisplayName("whenS with all true conditions should include all effects")
    void whenS_withAllTrueConditions_shouldIncludeAllEffects() {
      // Given: All true conditions
      Kind<ListKind.Witness, Boolean> fcond = listOf(true, true);

      // Effects
      Kind<ListKind.Witness, Unit> fa = listOf(Unit.INSTANCE, Unit.INSTANCE);

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS(fcond, fa);

      // Then: Each true condition pairs with each effect (2 * 2 = 4)
      assertThatList(result).hasSize(4).containsOnly(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS with all false conditions should return Unit.INSTANCE for each")
    void whenS_withAllFalseConditions_shouldReturnUnitsOnly() {
      // Given: All false conditions
      Kind<ListKind.Witness, Boolean> fcond = listOf(false, false, false);

      // Effects
      Kind<ListKind.Witness, Unit> fa = listOf(Unit.INSTANCE, Unit.INSTANCE);

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS(fcond, fa);

      // Then: Each false condition produces one Unit.INSTANCE (skipped effect)
      assertThatList(result).containsExactly(Unit.INSTANCE, Unit.INSTANCE, Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS with mixed conditions should handle both cases")
    void whenS_withMixedConditions_shouldHandleBothCases() {
      // Given: Mixed conditions
      Kind<ListKind.Witness, Boolean> fcond = listOf(true, false, true);

      // Effects
      Kind<ListKind.Witness, Unit> fa = listOf(Unit.INSTANCE, Unit.INSTANCE);

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS(fcond, fa);

      // Then: true (2 effects) + false (1 Unit) + true (2 effects) = 5 Units
      assertThatList(result).hasSize(5).containsOnly(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS with empty conditions should return empty list")
    void whenS_withEmptyConditions_shouldReturnEmpty() {
      // Given: Empty conditions
      Kind<ListKind.Witness, Boolean> fcond = emptyList();

      // Effects
      Kind<ListKind.Witness, Unit> fa = singletonList(Unit.INSTANCE);

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS(fcond, fa);

      // Then
      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("whenS with true condition but empty effects should skip")
    void whenS_withTrueConditionButEmptyEffects_shouldSkip() {
      // Given: True condition
      Kind<ListKind.Witness, Boolean> fcond = singletonList(true);

      // Empty effects
      Kind<ListKind.Witness, Unit> fa = emptyList();

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS(fcond, fa);

      // Then: Empty (no effects to include)
      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("whenS distinguishes between false condition and no effects")
    void whenS_shouldDistinguishFalseFromNoEffects() {
      // Given: False condition vs true condition with no effects
      Kind<ListKind.Witness, Unit> effects = emptyList();

      // When
      Kind<ListKind.Witness, Unit> resultFalse = selective.whenS(listOf(false), effects);
      Kind<ListKind.Witness, Unit> resultTrue = selective.whenS(listOf(true), effects);

      // Then: False condition returns Unit.INSTANCE, true with no effects returns empty
      assertThatList(resultFalse).containsExactly(Unit.INSTANCE);
      assertThatList(resultTrue).isEmpty();
    }

    @Test
    @DisplayName("whenS demonstrates non-deterministic effect execution")
    void whenS_shouldDemonstrateNonDeterministicExecution() {
      // Given: Multiple conditions and multiple effects
      Kind<ListKind.Witness, Boolean> fcond = listOf(true, true);
      Kind<ListKind.Witness, Unit> fa = listOf(Unit.INSTANCE, Unit.INSTANCE, Unit.INSTANCE);

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS(fcond, fa);

      // Then: 2 true conditions * 3 effects = 6 results
      assertThatList(result).hasSize(6);
    }
  }

  @Nested
  @DisplayName("whenS_() - Convenience method with value-discarding")
  class WhenS_Tests {

    @Test
    @DisplayName("whenS_ with true conditions should execute effects and discard results")
    void whenS__withTrueConditions_shouldExecuteAndDiscard() {
      // Given: True conditions
      Kind<ListKind.Witness, Boolean> fcond = singletonList(true);

      // Effects with actual values (will be discarded)
      Kind<ListKind.Witness, String> fa = listOf("result1", "result2");

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS_(fcond, fa);

      // Then: Results discarded, Units returned
      assertThatList(result).hasSize(2).containsOnly(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS_ with false conditions should return Unit.INSTANCE")
    void whenS__withFalseConditions_shouldReturnUnits() {
      // Given: False conditions
      Kind<ListKind.Witness, Boolean> fcond = listOf(false, false);

      // Effects
      Kind<ListKind.Witness, Integer> fa = listOf(1, 2, 3);

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS_(fcond, fa);

      // Then: One Unit per false condition
      assertThatList(result).containsExactly(Unit.INSTANCE, Unit.INSTANCE);
    }
  }

  @Nested
  @DisplayName("ifS() - Ternary conditional operator")
  class IfSTests {

    @Test
    @DisplayName("ifS with all true conditions should return all then-branch values")
    void ifS_withAllTrueConditions_shouldReturnThenBranch() {
      // Given: All true conditions
      Kind<ListKind.Witness, Boolean> fcond = listOf(true, true);

      // Branches
      Kind<ListKind.Witness, String> fthen = listOf("then1", "then2");
      Kind<ListKind.Witness, String> felse = listOf("else1", "else2");

      // When
      Kind<ListKind.Witness, String> result = selective.ifS(fcond, fthen, felse);

      // Then: Each true condition includes all then-branch values (2 * 2 = 4)
      assertThatList(result).containsExactly("then1", "then2", "then1", "then2");
    }

    @Test
    @DisplayName("ifS with all false conditions should return all else-branch values")
    void ifS_withAllFalseConditions_shouldReturnElseBranch() {
      // Given: All false conditions
      Kind<ListKind.Witness, Boolean> fcond = listOf(false, false);

      // Branches
      Kind<ListKind.Witness, String> fthen = listOf("then1", "then2");
      Kind<ListKind.Witness, String> felse = listOf("else1", "else2");

      // When
      Kind<ListKind.Witness, String> result = selective.ifS(fcond, fthen, felse);

      // Then: Each false condition includes all else-branch values
      assertThatList(result).containsExactly("else1", "else2", "else1", "else2");
    }

    @Test
    @DisplayName("ifS with mixed conditions should return appropriate branches")
    void ifS_withMixedConditions_shouldReturnMixedResults() {
      // Given: Mixed conditions
      Kind<ListKind.Witness, Boolean> fcond = listOf(true, false, true);

      // Branches
      Kind<ListKind.Witness, Integer> fthen = listOf(100, 200);
      Kind<ListKind.Witness, Integer> felse = listOf(300, 400);

      // When
      Kind<ListKind.Witness, Integer> result = selective.ifS(fcond, fthen, felse);

      // Then: true (100, 200), false (300, 400), true (100, 200)
      assertThatList(result).containsExactly(100, 200, 300, 400, 100, 200);
    }

    @Test
    @DisplayName("ifS with empty conditions should return empty list")
    void ifS_withEmptyConditions_shouldReturnEmpty() {
      // Given: Empty conditions
      Kind<ListKind.Witness, Boolean> fcond = emptyList();

      Kind<ListKind.Witness, String> fthen = singletonList("then");
      Kind<ListKind.Witness, String> felse = singletonList("else");

      // When
      Kind<ListKind.Witness, String> result = selective.ifS(fcond, fthen, felse);

      // Then
      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("ifS demonstrates non-deterministic branching")
    void ifS_shouldDemonstrateNonDeterministicBranching() {
      // Given: One condition, multiple values in branches
      Kind<ListKind.Witness, Boolean> fcond = singletonList(true);

      Kind<ListKind.Witness, String> fthen = listOf("A", "B", "C");
      Kind<ListKind.Witness, String> felse = listOf("X", "Y");

      // When
      Kind<ListKind.Witness, String> result = selective.ifS(fcond, fthen, felse);

      // Then: All then-branch values included
      assertThatList(result).containsExactly("A", "B", "C");
    }
  }

  @Nested
  @DisplayName("Integration and Composition Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Composing select operations for complex conditional logic")
    void composingSelectOperations() {
      // Given: Initial choices
      List<Choice<Integer, String>> choices = Arrays.asList(Selective.left(5), Selective.left(15));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = wrapList(choices);

      // First transformation
      Kind<ListKind.Witness, Function<Integer, String>> ff =
          singletonList(i -> i > 10 ? "High" : "Low");

      // When
      Kind<ListKind.Witness, String> result = selective.select(fab, ff);

      // Then: 5 -> "Low", 15 -> "High"
      assertThatList(result).containsExactly("Low", "High");
    }

    @Test
    @DisplayName("Chaining whenS for sequential conditional effects")
    void chainingWhenSOperations() {
      // Given: Initial conditions
      Kind<ListKind.Witness, Boolean> fcond1 = listOf(true, false);
      Kind<ListKind.Witness, Unit> fa = singletonList(Unit.INSTANCE);

      // When: First whenS
      Kind<ListKind.Witness, Unit> intermediate = selective.whenS(fcond1, fa);

      // Then: Chain another condition
      Kind<ListKind.Witness, Boolean> fcond2 = singletonList(true);
      Kind<ListKind.Witness, Unit> result = selective.whenS(fcond2, intermediate);

      // Then: Should have results
      assertThatList(result).isNotEmpty().containsOnly(Unit.INSTANCE);
    }

    @Test
    @DisplayName("Using ifS to implement complex branching logic")
    void usingIfSForComplexBranching() {
      // Given: Conditions based on values
      Kind<ListKind.Witness, Integer> valueKind = listOf(1, 10, 100);

      // Map to conditions: [false, true, true]
      Kind<ListKind.Witness, Boolean> conditions = selective.map(v -> v >= 10, valueKind);

      // Branches
      Kind<ListKind.Witness, String> thenBranch = selective.map(v -> "Large: " + v, valueKind);
      Kind<ListKind.Witness, String> elseBranch = selective.map(v -> "Small: " + v, valueKind);

      // When
      Kind<ListKind.Witness, String> result = selective.ifS(conditions, thenBranch, elseBranch);

      // Then: For each condition, ifS includes ALL values from the selected branch
      // Total: 9 results (3 from else + 3 from then + 3 from then)
      assertThatList(result)
          .hasSize(9)
          .containsExactly(
              "Small: 1",
              "Small: 10",
              "Small: 100", // condition false
              "Large: 1",
              "Large: 10",
              "Large: 100", // condition true
              "Large: 1",
              "Large: 10",
              "Large: 100"); // condition true
    }

    @Test
    @DisplayName("Combining branch and select for flexible control flow")
    void combiningBranchAndSelect() {
      // Given: Initial choice
      List<Choice<Integer, Integer>> choices =
          Arrays.asList(Selective.left(10), Selective.right(20));
      Kind<ListKind.Witness, Choice<Integer, Integer>> fab = wrapList(choices);

      // Handlers
      Kind<ListKind.Witness, Function<Integer, String>> fl =
          singletonList(i -> "Process left: " + i);
      Kind<ListKind.Witness, Function<Integer, String>> fr =
          singletonList(i -> "Process right: " + i);

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThatList(result).containsExactly("Process left: 10", "Process right: 20");
    }

    @Test
    @DisplayName("Non-deterministic computation with selective operations")
    void nonDeterministicComputation() {
      // Given: Multiple possible conditions
      Kind<ListKind.Witness, Boolean> fcond = listOf(true, false);

      // Multiple possible values in each branch
      Kind<ListKind.Witness, String> fthen = listOf("A", "B");
      Kind<ListKind.Witness, String> felse = listOf("X", "Y");

      // When
      Kind<ListKind.Witness, String> result = selective.ifS(fcond, fthen, felse);

      // Then: All combinations explored (true: A, B; false: X, Y)
      assertThatList(result).containsExactly("A", "B", "X", "Y");
    }
  }
}
