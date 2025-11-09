// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.Arrays;
import java.util.Collections;
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
class ListSelectiveTest {

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
      Kind<ListKind.Witness, Choice<String, String>> fab = LIST.widen(choices);

      // Functions (should not be used)
      List<Function<String, String>> functions =
          Collections.singletonList(
              s -> {
                throw new AssertionError("Function should not be called for Right choices");
              });
      Kind<ListKind.Witness, Function<String, String>> ff = LIST.widen(functions);

      // When
      Kind<ListKind.Witness, String> result = selective.select(fab, ff);

      // Then: All Right values returned
      assertThat(LIST.narrow(result)).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("select with all Left choices should apply all functions to all values")
    void select_withAllLeftChoices_shouldApplyFunctions() {
      // Given: List of Left choices
      List<Choice<Integer, String>> choices = Arrays.asList(Selective.left(1), Selective.left(2));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = LIST.widen(choices);

      // Functions to apply
      List<Function<Integer, String>> functions = Arrays.asList(i -> "A" + i, i -> "B" + i);
      Kind<ListKind.Witness, Function<Integer, String>> ff = LIST.widen(functions);

      // When
      Kind<ListKind.Witness, String> result = selective.select(fab, ff);

      // Then: Each function applied to each value (cartesian product)
      // 1 with A1, 1 with B1, 2 with A2, 2 with B2
      assertThat(LIST.narrow(result)).containsExactly("A1", "B1", "A2", "B2");
    }

    @Test
    @DisplayName("select with mixed Left and Right choices")
    void select_withMixedChoices_shouldHandleAppropriately() {
      // Given: Mixed choices
      List<Choice<Integer, String>> choices =
          Arrays.asList(Selective.left(1), Selective.right("X"), Selective.left(2));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = LIST.widen(choices);

      // Functions
      List<Function<Integer, String>> functions = Arrays.asList(i -> "N" + i);
      Kind<ListKind.Witness, Function<Integer, String>> ff = LIST.widen(functions);

      // When
      Kind<ListKind.Witness, String> result = selective.select(fab, ff);

      // Then: Left(1) -> N1, Right(X) -> X, Left(2) -> N2
      assertThat(LIST.narrow(result)).containsExactly("N1", "X", "N2");
    }

    @Test
    @DisplayName("select with empty choices should return empty list")
    void select_withEmptyChoices_shouldReturnEmpty() {
      // Given: Empty choices
      Kind<ListKind.Witness, Choice<String, String>> fab = LIST.widen(Collections.emptyList());

      // Functions
      List<Function<String, String>> functions = Collections.singletonList(String::toUpperCase);
      Kind<ListKind.Witness, Function<String, String>> ff = LIST.widen(functions);

      // When
      Kind<ListKind.Witness, String> result = selective.select(fab, ff);

      // Then
      assertThat(LIST.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("select with Left choices but empty functions should skip those elements")
    void select_withLeftChoicesButEmptyFunctions_shouldSkipElements() {
      // Given: Left choices
      List<Choice<Integer, String>> choices = Arrays.asList(Selective.left(1), Selective.left(2));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = LIST.widen(choices);

      // Empty functions
      Kind<ListKind.Witness, Function<Integer, String>> ff = LIST.widen(Collections.emptyList());

      // When
      Kind<ListKind.Witness, String> result = selective.select(fab, ff);

      // Then: Empty result (no functions to apply)
      assertThat(LIST.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("select with multiple functions creates non-deterministic results")
    void select_withMultipleFunctions_shouldCreateNonDeterministicResults() {
      // Given: One Left choice
      List<Choice<Integer, String>> choices = Collections.singletonList(Selective.left(5));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = LIST.widen(choices);

      // Multiple functions
      List<Function<Integer, String>> functions =
          Arrays.asList(
              i -> "double:" + (i * 2), i -> "triple:" + (i * 3), i -> "square:" + (i * i));
      Kind<ListKind.Witness, Function<Integer, String>> ff = LIST.widen(functions);

      // When
      Kind<ListKind.Witness, String> result = selective.select(fab, ff);

      // Then: All three transformations applied
      assertThat(LIST.narrow(result)).containsExactly("double:10", "triple:15", "square:25");
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
      Kind<ListKind.Witness, Choice<Integer, String>> fab = LIST.widen(choices);

      // Left handler
      List<Function<Integer, String>> leftHandlers =
          Collections.singletonList(i -> "Left:" + (i * 10));
      Kind<ListKind.Witness, Function<Integer, String>> fl = LIST.widen(leftHandlers);

      // Right handler (should not be used)
      List<Function<String, String>> rightHandlers =
          Collections.singletonList(
              s -> {
                throw new AssertionError("Right handler should not be called");
              });
      Kind<ListKind.Witness, Function<String, String>> fr = LIST.widen(rightHandlers);

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThat(LIST.narrow(result)).containsExactly("Left:10", "Left:20");
    }

    @Test
    @DisplayName("branch with all Right choices should use right handler")
    void branch_withAllRightChoices_shouldUseRightHandler() {
      // Given: Right choices
      List<Choice<Integer, String>> choices =
          Arrays.asList(Selective.right("A"), Selective.right("B"));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = LIST.widen(choices);

      // Left handler (should not be used)
      List<Function<Integer, String>> leftHandlers =
          Collections.singletonList(
              i -> {
                throw new AssertionError("Left handler should not be called");
              });
      Kind<ListKind.Witness, Function<Integer, String>> fl = LIST.widen(leftHandlers);

      // Right handler
      List<Function<String, String>> rightHandlers = Collections.singletonList(s -> "Right:" + s);
      Kind<ListKind.Witness, Function<String, String>> fr = LIST.widen(rightHandlers);

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThat(LIST.narrow(result)).containsExactly("Right:A", "Right:B");
    }

    @Test
    @DisplayName("branch with mixed choices should use appropriate handlers")
    void branch_withMixedChoices_shouldUseAppropriateHandlers() {
      // Given: Mixed choices
      List<Choice<Integer, String>> choices =
          Arrays.asList(Selective.left(5), Selective.right("X"), Selective.left(10));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = LIST.widen(choices);

      // Left handler
      List<Function<Integer, String>> leftHandlers = Collections.singletonList(i -> "L" + i);
      Kind<ListKind.Witness, Function<Integer, String>> fl = LIST.widen(leftHandlers);

      // Right handler
      List<Function<String, String>> rightHandlers = Collections.singletonList(s -> "R" + s);
      Kind<ListKind.Witness, Function<String, String>> fr = LIST.widen(rightHandlers);

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThat(LIST.narrow(result)).containsExactly("L5", "RX", "L10");
    }

    @Test
    @DisplayName("branch with empty choices should return empty list")
    void branch_withEmptyChoices_shouldReturnEmpty() {
      // Given: Empty choices
      Kind<ListKind.Witness, Choice<Integer, String>> fab = LIST.widen(Collections.emptyList());

      List<Function<Integer, String>> leftHandlers = Collections.singletonList(i -> "L" + i);
      Kind<ListKind.Witness, Function<Integer, String>> fl = LIST.widen(leftHandlers);

      List<Function<String, String>> rightHandlers = Collections.singletonList(s -> "R" + s);
      Kind<ListKind.Witness, Function<String, String>> fr = LIST.widen(rightHandlers);

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThat(LIST.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("branch with multiple handlers creates non-deterministic results")
    void branch_withMultipleHandlers_shouldCreateNonDeterministicResults() {
      // Given: One Left choice, multiple handlers
      List<Choice<Integer, String>> choices = Collections.singletonList(Selective.left(3));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = LIST.widen(choices);

      // Multiple left handlers
      List<Function<Integer, String>> leftHandlers = Arrays.asList(i -> "A" + i, i -> "B" + i);
      Kind<ListKind.Witness, Function<Integer, String>> fl = LIST.widen(leftHandlers);

      List<Function<String, String>> rightHandlers = Collections.singletonList(s -> "R" + s);
      Kind<ListKind.Witness, Function<String, String>> fr = LIST.widen(rightHandlers);

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then: Both handlers applied
      assertThat(LIST.narrow(result)).containsExactly("A3", "B3");
    }

    @Test
    @DisplayName("branch with Left choices but empty left handlers should skip")
    void branch_withLeftChoicesButEmptyLeftHandlers_shouldSkip() {
      // Given: Left choices
      List<Choice<Integer, String>> choices = Arrays.asList(Selective.left(1), Selective.left(2));
      Kind<ListKind.Witness, Choice<Integer, String>> fab = LIST.widen(choices);

      // Empty left handlers
      Kind<ListKind.Witness, Function<Integer, String>> fl = LIST.widen(Collections.emptyList());

      List<Function<String, String>> rightHandlers = Collections.singletonList(s -> "R" + s);
      Kind<ListKind.Witness, Function<String, String>> fr = LIST.widen(rightHandlers);

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then: Empty (no left handlers available)
      assertThat(LIST.narrow(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("whenS() - Conditional effect execution with Unit")
  class WhenSTests {

    @Test
    @DisplayName("whenS with all true conditions should include all effects")
    void whenS_withAllTrueConditions_shouldIncludeAllEffects() {
      // Given: All true conditions
      List<Boolean> conditions = Arrays.asList(true, true);
      Kind<ListKind.Witness, Boolean> fcond = LIST.widen(conditions);

      // Effects
      List<Unit> effects = Arrays.asList(Unit.INSTANCE, Unit.INSTANCE);
      Kind<ListKind.Witness, Unit> fa = LIST.widen(effects);

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS(fcond, fa);

      // Then: Each true condition pairs with each effect
      // 2 true conditions * 2 effects = 4 results
      assertThat(LIST.narrow(result)).hasSize(4);
      assertThat(LIST.narrow(result)).containsOnly(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS with all false conditions should return Unit.INSTANCE for each")
    void whenS_withAllFalseConditions_shouldReturnUnitsOnly() {
      // Given: All false conditions
      List<Boolean> conditions = Arrays.asList(false, false, false);
      Kind<ListKind.Witness, Boolean> fcond = LIST.widen(conditions);

      // Effects
      List<Unit> effects = Arrays.asList(Unit.INSTANCE, Unit.INSTANCE);
      Kind<ListKind.Witness, Unit> fa = LIST.widen(effects);

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS(fcond, fa);

      // Then: Each false condition produces one Unit.INSTANCE (skipped effect)
      assertThat(LIST.narrow(result)).containsExactly(Unit.INSTANCE, Unit.INSTANCE, Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS with mixed conditions should handle both cases")
    void whenS_withMixedConditions_shouldHandleBothCases() {
      // Given: Mixed conditions
      List<Boolean> conditions = Arrays.asList(true, false, true);
      Kind<ListKind.Witness, Boolean> fcond = LIST.widen(conditions);

      // Effects
      List<Unit> effects = Arrays.asList(Unit.INSTANCE, Unit.INSTANCE);
      Kind<ListKind.Witness, Unit> fa = LIST.widen(effects);

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS(fcond, fa);

      // Then:
      // true: 2 effects
      // false: 1 Unit
      // true: 2 effects
      // Total: 5 Units
      assertThat(LIST.narrow(result)).hasSize(5);
      assertThat(LIST.narrow(result)).containsOnly(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS with empty conditions should return empty list")
    void whenS_withEmptyConditions_shouldReturnEmpty() {
      // Given: Empty conditions
      Kind<ListKind.Witness, Boolean> fcond = LIST.widen(Collections.emptyList());

      // Effects
      List<Unit> effects = Collections.singletonList(Unit.INSTANCE);
      Kind<ListKind.Witness, Unit> fa = LIST.widen(effects);

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS(fcond, fa);

      // Then
      assertThat(LIST.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("whenS with true condition but empty effects should skip")
    void whenS_withTrueConditionButEmptyEffects_shouldSkip() {
      // Given: True condition
      List<Boolean> conditions = Collections.singletonList(true);
      Kind<ListKind.Witness, Boolean> fcond = LIST.widen(conditions);

      // Empty effects
      Kind<ListKind.Witness, Unit> fa = LIST.widen(Collections.emptyList());

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS(fcond, fa);

      // Then: Empty (no effects to include)
      assertThat(LIST.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("whenS distinguishes between false condition and no effects")
    void whenS_shouldDistinguishFalseFromNoEffects() {
      // Given: False condition vs true condition with no effects
      List<Boolean> falseConditions = Collections.singletonList(false);
      List<Boolean> trueConditions = Collections.singletonList(true);

      Kind<ListKind.Witness, Unit> effects = LIST.widen(Collections.emptyList());

      // When
      Kind<ListKind.Witness, Unit> resultFalse =
          selective.whenS(LIST.widen(falseConditions), effects);
      Kind<ListKind.Witness, Unit> resultTrue =
          selective.whenS(LIST.widen(trueConditions), effects);

      // Then: False condition returns Unit.INSTANCE, true with no effects returns empty
      assertThat(LIST.narrow(resultFalse)).containsExactly(Unit.INSTANCE);
      assertThat(LIST.narrow(resultTrue)).isEmpty();
    }

    @Test
    @DisplayName("whenS demonstrates non-deterministic effect execution")
    void whenS_shouldDemonstrateNonDeterministicExecution() {
      // Given: Multiple conditions and multiple effects
      List<Boolean> conditions = Arrays.asList(true, true);
      Kind<ListKind.Witness, Boolean> fcond = LIST.widen(conditions);

      List<Unit> effects = Arrays.asList(Unit.INSTANCE, Unit.INSTANCE, Unit.INSTANCE);
      Kind<ListKind.Witness, Unit> fa = LIST.widen(effects);

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS(fcond, fa);

      // Then: 2 true conditions * 3 effects = 6 results
      assertThat(LIST.narrow(result)).hasSize(6);
    }
  }

  @Nested
  @DisplayName("whenS_() - Convenience method with value-discarding")
  class WhenS_Tests {

    @Test
    @DisplayName("whenS_ with true conditions should execute effects and discard results")
    void whenS__withTrueConditions_shouldExecuteAndDiscard() {
      // Given: True conditions
      List<Boolean> conditions = Collections.singletonList(true);
      Kind<ListKind.Witness, Boolean> fcond = LIST.widen(conditions);

      // Effects with actual values (will be discarded)
      List<String> effects = Arrays.asList("result1", "result2");
      Kind<ListKind.Witness, String> fa = LIST.widen(effects);

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS_(fcond, fa);

      // Then: Results discarded, Units returned
      assertThat(LIST.narrow(result)).hasSize(2);
      assertThat(LIST.narrow(result)).containsOnly(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS_ with false conditions should return Unit.INSTANCE")
    void whenS__withFalseConditions_shouldReturnUnits() {
      // Given: False conditions
      List<Boolean> conditions = Arrays.asList(false, false);
      Kind<ListKind.Witness, Boolean> fcond = LIST.widen(conditions);

      // Effects
      List<Integer> effects = Arrays.asList(1, 2, 3);
      Kind<ListKind.Witness, Integer> fa = LIST.widen(effects);

      // When
      Kind<ListKind.Witness, Unit> result = selective.whenS_(fcond, fa);

      // Then: One Unit per false condition
      assertThat(LIST.narrow(result)).containsExactly(Unit.INSTANCE, Unit.INSTANCE);
    }
  }

  @Nested
  @DisplayName("ifS() - Ternary conditional operator")
  class IfSTests {

    @Test
    @DisplayName("ifS with all true conditions should return all then-branch values")
    void ifS_withAllTrueConditions_shouldReturnThenBranch() {
      // Given: All true conditions
      List<Boolean> conditions = Arrays.asList(true, true);
      Kind<ListKind.Witness, Boolean> fcond = LIST.widen(conditions);

      // Branches
      List<String> thenValues = Arrays.asList("then1", "then2");
      List<String> elseValues = Arrays.asList("else1", "else2");

      Kind<ListKind.Witness, String> fthen = LIST.widen(thenValues);
      Kind<ListKind.Witness, String> felse = LIST.widen(elseValues);

      // When
      Kind<ListKind.Witness, String> result = selective.ifS(fcond, fthen, felse);

      // Then: Each true condition includes all then-branch values
      // 2 conditions * 2 then-values = 4 results
      assertThat(LIST.narrow(result)).containsExactly("then1", "then2", "then1", "then2");
    }

    @Test
    @DisplayName("ifS with all false conditions should return all else-branch values")
    void ifS_withAllFalseConditions_shouldReturnElseBranch() {
      // Given: All false conditions
      List<Boolean> conditions = Arrays.asList(false, false);
      Kind<ListKind.Witness, Boolean> fcond = LIST.widen(conditions);

      // Branches
      List<String> thenValues = Arrays.asList("then1", "then2");
      List<String> elseValues = Arrays.asList("else1", "else2");

      Kind<ListKind.Witness, String> fthen = LIST.widen(thenValues);
      Kind<ListKind.Witness, String> felse = LIST.widen(elseValues);

      // When
      Kind<ListKind.Witness, String> result = selective.ifS(fcond, fthen, felse);

      // Then: Each false condition includes all else-branch values
      assertThat(LIST.narrow(result)).containsExactly("else1", "else2", "else1", "else2");
    }

    @Test
    @DisplayName("ifS with mixed conditions should return appropriate branches")
    void ifS_withMixedConditions_shouldReturnMixedResults() {
      // Given: Mixed conditions
      List<Boolean> conditions = Arrays.asList(true, false, true);
      Kind<ListKind.Witness, Boolean> fcond = LIST.widen(conditions);

      // Branches
      List<Integer> thenValues = Arrays.asList(100, 200);
      List<Integer> elseValues = Arrays.asList(300, 400);

      Kind<ListKind.Witness, Integer> fthen = LIST.widen(thenValues);
      Kind<ListKind.Witness, Integer> felse = LIST.widen(elseValues);

      // When
      Kind<ListKind.Witness, Integer> result = selective.ifS(fcond, fthen, felse);

      // Then:
      // true: 100, 200
      // false: 300, 400
      // true: 100, 200
      assertThat(LIST.narrow(result)).containsExactly(100, 200, 300, 400, 100, 200);
    }

    @Test
    @DisplayName("ifS with empty conditions should return empty list")
    void ifS_withEmptyConditions_shouldReturnEmpty() {
      // Given: Empty conditions
      Kind<ListKind.Witness, Boolean> fcond = LIST.widen(Collections.emptyList());

      List<String> thenValues = Collections.singletonList("then");
      List<String> elseValues = Collections.singletonList("else");

      Kind<ListKind.Witness, String> fthen = LIST.widen(thenValues);
      Kind<ListKind.Witness, String> felse = LIST.widen(elseValues);

      // When
      Kind<ListKind.Witness, String> result = selective.ifS(fcond, fthen, felse);

      // Then
      assertThat(LIST.narrow(result)).isEmpty();
    }

    @Test
    @DisplayName("ifS demonstrates non-deterministic branching")
    void ifS_shouldDemonstrateNonDeterministicBranching() {
      // Given: One condition, multiple values in branches
      List<Boolean> conditions = Collections.singletonList(true);
      Kind<ListKind.Witness, Boolean> fcond = LIST.widen(conditions);

      List<String> thenValues = Arrays.asList("A", "B", "C");
      List<String> elseValues = Arrays.asList("X", "Y");

      Kind<ListKind.Witness, String> fthen = LIST.widen(thenValues);
      Kind<ListKind.Witness, String> felse = LIST.widen(elseValues);

      // When
      Kind<ListKind.Witness, String> result = selective.ifS(fcond, fthen, felse);

      // Then: All then-branch values included
      assertThat(LIST.narrow(result)).containsExactly("A", "B", "C");
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
      Kind<ListKind.Witness, Choice<Integer, String>> fab = LIST.widen(choices);

      // First transformation
      List<Function<Integer, String>> functions =
          Collections.singletonList(i -> i > 10 ? "High" : "Low");
      Kind<ListKind.Witness, Function<Integer, String>> ff = LIST.widen(functions);

      // When
      Kind<ListKind.Witness, String> result = selective.select(fab, ff);

      // Then: 5 -> "Low", 15 -> "High"
      assertThat(LIST.narrow(result)).containsExactly("Low", "High");
    }

    @Test
    @DisplayName("Chaining whenS for sequential conditional effects")
    void chainingWhenSOperations() {
      // Given: Initial conditions
      List<Boolean> conditions1 = Arrays.asList(true, false);
      Kind<ListKind.Witness, Boolean> fcond1 = LIST.widen(conditions1);

      List<Unit> effects = Collections.singletonList(Unit.INSTANCE);
      Kind<ListKind.Witness, Unit> fa = LIST.widen(effects);

      // When: First whenS
      Kind<ListKind.Witness, Unit> intermediate = selective.whenS(fcond1, fa);

      // Then: Chain another condition
      List<Boolean> conditions2 = Collections.singletonList(true);
      Kind<ListKind.Witness, Boolean> fcond2 = LIST.widen(conditions2);

      Kind<ListKind.Witness, Unit> result = selective.whenS(fcond2, intermediate);

      // Then: Should have results
      assertThat(LIST.narrow(result)).isNotEmpty();
      assertThat(LIST.narrow(result)).containsOnly(Unit.INSTANCE);
    }

    @Test
    @DisplayName("Using ifS to implement complex branching logic")
    void usingIfSForComplexBranching() {
      // Given: Conditions based on values
      List<Integer> values = Arrays.asList(1, 10, 100);
      Kind<ListKind.Witness, Integer> valueKind = LIST.widen(values);

      // Map to conditions: [false, true, true]
      Kind<ListKind.Witness, Boolean> conditions = selective.map(v -> v >= 10, valueKind);

      // Branches
      Kind<ListKind.Witness, String> thenBranch = selective.map(v -> "Large: " + v, valueKind);
      Kind<ListKind.Witness, String> elseBranch = selective.map(v -> "Small: " + v, valueKind);

      // When
      Kind<ListKind.Witness, String> result = selective.ifS(conditions, thenBranch, elseBranch);

      // Then: For each condition, ifS includes ALL values from the selected branch
      // Condition false (1 >= 10): "Small: 1", "Small: 10", "Small: 100"
      // Condition true  (10 >= 10): "Large: 1", "Large: 10", "Large: 100"
      // Condition true  (100 >= 10): "Large: 1", "Large: 10", "Large: 100"
      // Total: 9 results (3 from else + 3 from then + 3 from then)
      assertThat(LIST.narrow(result)).hasSize(9);
      assertThat(LIST.narrow(result))
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
      Kind<ListKind.Witness, Choice<Integer, Integer>> fab = LIST.widen(choices);

      // Handlers
      List<Function<Integer, String>> leftHandler =
          Collections.singletonList(i -> "Process left: " + i);
      List<Function<Integer, String>> rightHandler =
          Collections.singletonList(i -> "Process right: " + i);

      Kind<ListKind.Witness, Function<Integer, String>> fl = LIST.widen(leftHandler);
      Kind<ListKind.Witness, Function<Integer, String>> fr = LIST.widen(rightHandler);

      // When
      Kind<ListKind.Witness, String> result = selective.branch(fab, fl, fr);

      // Then
      assertThat(LIST.narrow(result)).containsExactly("Process left: 10", "Process right: 20");
    }

    @Test
    @DisplayName("Non-deterministic computation with selective operations")
    void nonDeterministicComputation() {
      // Given: Multiple possible conditions
      List<Boolean> conditions = Arrays.asList(true, false);
      Kind<ListKind.Witness, Boolean> fcond = LIST.widen(conditions);

      // Multiple possible values in each branch
      List<String> thenValues = Arrays.asList("A", "B");
      List<String> elseValues = Arrays.asList("X", "Y");

      Kind<ListKind.Witness, String> fthen = LIST.widen(thenValues);
      Kind<ListKind.Witness, String> felse = LIST.widen(elseValues);

      // When
      Kind<ListKind.Witness, String> result = selective.ifS(fcond, fthen, felse);

      // Then: All combinations explored
      // true: A, B
      // false: X, Y
      assertThat(LIST.narrow(result)).containsExactly("A", "B", "X", "Y");
    }
  }
}
