// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.laws.SelectiveLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Comprehensive tests for {@link ValidatedSelective}, mirroring the EitherSelective coverage.
 *
 * <p>Where Either short-circuits on the first error, Validated's Selective <em>accumulates</em>
 * errors from every Invalid source via the configured {@link org.higherkindedj.hkt.Semigroup} —
 * that accumulation is the behaviour these tests exist to pin down.
 */
@DisplayName("ValidatedSelective Complete Test Suite")
class ValidatedSelectiveTest extends ValidatedTestBase {

  private ValidatedSelective<String> selective;

  // Selective-specific test data
  private Kind<ValidatedKind.Witness<String>, Choice<Integer, String>> choiceLeftKind;
  private Kind<ValidatedKind.Witness<String>, Choice<Integer, String>> choiceRightKind;
  private Kind<ValidatedKind.Witness<String>, Function<Integer, String>> selectFunctionKind;
  private Kind<ValidatedKind.Witness<String>, Function<Integer, String>> leftHandlerKind;
  private Kind<ValidatedKind.Witness<String>, Function<String, String>> rightHandlerKind;
  private Kind<ValidatedKind.Witness<String>, Boolean> conditionTrue;
  private Kind<ValidatedKind.Witness<String>, Boolean> conditionFalse;
  private Kind<ValidatedKind.Witness<String>, Unit> unitEffectKind;
  private Kind<ValidatedKind.Witness<String>, Integer> thenBranch;
  private Kind<ValidatedKind.Witness<String>, Integer> elseBranch;

  @BeforeEach
  void setUpSelective() {
    selective = ValidatedSelective.instance(Semigroups.string(", "));
    validateMonadFixtures();

    // Create Choice instances
    choiceLeftKind = VALIDATED.widen(Validated.valid(Selective.left(DEFAULT_VALID_VALUE)));
    choiceRightKind = VALIDATED.widen(Validated.valid(Selective.right("right-value")));

    // Create function handlers - all must return the same type C = String
    Function<Integer, String> selectFunc = i -> "selected:" + i;
    Function<Integer, String> leftHandler = i -> "left:" + i;
    Function<String, String> rightHandler = s -> "right:" + s;

    selectFunctionKind = VALIDATED.widen(Validated.valid(selectFunc));
    leftHandlerKind = VALIDATED.widen(Validated.valid(leftHandler));
    rightHandlerKind = VALIDATED.widen(Validated.valid(rightHandler));

    // Create boolean conditions
    conditionTrue = VALIDATED.widen(Validated.valid(true));
    conditionFalse = VALIDATED.widen(Validated.valid(false));

    // Create Unit effect for whenS
    unitEffectKind = VALIDATED.widen(Validated.valid(Unit.INSTANCE));

    // Create branch kinds for ifS (these stay as Integer)
    thenBranch = validKind;
    elseBranch = validKind(ALTERNATIVE_VALID_VALUE);
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "left-pure holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.validated.ValidatedLawFixtures#values")
    void leftPure(Integer value) {
      SelectiveLaws.assertLeftPure(selective, value, selective.of(validMapper), equalityChecker);
    }

    @ParameterizedTest(name = "right-pure holds on value \"{0}\"")
    @MethodSource("strings")
    void rightPure(String value) {
      SelectiveLaws.assertRightPure(selective, value, selective.of(validMapper), equalityChecker);
    }

    static Stream<Arguments> strings() {
      return Stream.of(Arguments.of("a"), Arguments.of("hello"));
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Nested
    @DisplayName("Select Operation Tests")
    class SelectOperationTests {

      @Test
      @DisplayName("select() applies function to Left value in Choice")
      void selectAppliesFunctionToLeftValue() {
        Kind<ValidatedKind.Witness<String>, String> result =
            selective.select(choiceLeftKind, selectFunctionKind);
        assertThatValidated(result).isValid().hasValue("selected:" + DEFAULT_VALID_VALUE);
      }

      @Test
      @DisplayName("select() returns Right value when Choice is Right")
      void selectReturnsRightValue() {
        Kind<ValidatedKind.Witness<String>, String> result =
            selective.select(choiceRightKind, selectFunctionKind);
        assertThatValidated(result).isValid().hasValue("right-value");
      }

      @Test
      @DisplayName("select() propagates Invalid from choice")
      void selectPropagatesInvalidFromChoice() {
        Kind<ValidatedKind.Witness<String>, Choice<Integer, String>> errorChoice =
            invalidKind(DEFAULT_ERROR);

        Kind<ValidatedKind.Witness<String>, String> result =
            selective.select(errorChoice, selectFunctionKind);
        assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR);
      }

      @Test
      @DisplayName("select() propagates Invalid from function")
      void selectPropagatesInvalidFromFunction() {
        Kind<ValidatedKind.Witness<String>, Function<Integer, String>> errorFunc =
            invalidKind("function-error");

        Kind<ValidatedKind.Witness<String>, String> result =
            selective.select(choiceLeftKind, errorFunc);
        assertThatValidated(result).isInvalid().hasError("function-error");
      }

      @Test
      @DisplayName("select() accumulates errors from both choice and function")
      void selectAccumulatesErrorsFromBoth() {
        Kind<ValidatedKind.Witness<String>, Choice<Integer, String>> errorChoice =
            invalidKind("error1");
        Kind<ValidatedKind.Witness<String>, Function<Integer, String>> errorFunc =
            invalidKind("error2");

        Kind<ValidatedKind.Witness<String>, String> result =
            selective.select(errorChoice, errorFunc);
        assertThatValidated(result).isInvalid().hasError("error1, error2");
      }

      @Test
      @DisplayName("select() does not accumulate when choice is right with valid value")
      void selectDoesNotAccumulateWhenChoiceIsRight() {
        Kind<ValidatedKind.Witness<String>, Function<Integer, String>> errorFunc =
            invalidKind("function-error");

        Kind<ValidatedKind.Witness<String>, String> result =
            selective.select(choiceRightKind, errorFunc);
        // Should return the right value, not the function error
        assertThatValidated(result).isValid().hasValue("right-value");
      }
    }

    @Nested
    @DisplayName("Branch Operation Tests")
    class BranchOperationTests {

      @Test
      @DisplayName("branch() applies left handler to Left value")
      void branchAppliesLeftHandler() {
        Kind<ValidatedKind.Witness<String>, String> result =
            selective.branch(choiceLeftKind, leftHandlerKind, rightHandlerKind);
        assertThatValidated(result).isValid().hasValue("left:" + DEFAULT_VALID_VALUE);
      }

      @Test
      @DisplayName("branch() applies right handler to Right value")
      void branchAppliesRightHandler() {
        Kind<ValidatedKind.Witness<String>, String> result =
            selective.branch(choiceRightKind, leftHandlerKind, rightHandlerKind);
        assertThatValidated(result).isValid().hasValue("right:right-value");
      }

      @Test
      @DisplayName("branch() propagates Invalid from choice")
      void branchPropagatesInvalidFromChoice() {
        Kind<ValidatedKind.Witness<String>, Choice<Integer, String>> errorChoice =
            invalidKind(DEFAULT_ERROR);

        Kind<ValidatedKind.Witness<String>, String> result =
            selective.branch(errorChoice, leftHandlerKind, rightHandlerKind);
        assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR);
      }

      @Test
      @DisplayName("branch() accumulates errors from all sources")
      void branchAccumulatesAllErrors() {
        Kind<ValidatedKind.Witness<String>, Choice<Integer, String>> errorChoice =
            invalidKind("error1");
        Kind<ValidatedKind.Witness<String>, Function<Integer, String>> errorLeftHandler =
            invalidKind("error2");
        Kind<ValidatedKind.Witness<String>, Function<String, String>> errorRightHandler =
            invalidKind("error3");

        Kind<ValidatedKind.Witness<String>, String> result =
            selective.branch(errorChoice, errorLeftHandler, errorRightHandler);
        assertThatValidated(result).isInvalid().hasError("error1, error2, error3");
      }

      @Test
      @DisplayName("branch() accumulates only relevant handler errors based on choice")
      void branchAccumulatesOnlyRelevantHandlerErrors() {
        // When choice is left, only left handler error should accumulate
        Kind<ValidatedKind.Witness<String>, Function<Integer, String>> errorLeftHandler =
            invalidKind("left-error");

        Kind<ValidatedKind.Witness<String>, String> leftResult =
            selective.branch(choiceLeftKind, errorLeftHandler, rightHandlerKind);
        assertThatValidated(leftResult).isInvalid().hasError("left-error");

        // When choice is right, only right handler error should accumulate
        Kind<ValidatedKind.Witness<String>, Function<String, String>> errorRightHandler =
            invalidKind("right-error");

        Kind<ValidatedKind.Witness<String>, String> rightResult =
            selective.branch(choiceRightKind, leftHandlerKind, errorRightHandler);
        assertThatValidated(rightResult).isInvalid().hasError("right-error");
      }
    }

    @Nested
    @DisplayName("WhenS Operation Tests")
    class WhenSOperationTests {

      @Test
      @DisplayName("whenS() executes effect when condition is true")
      void whenSExecutesEffectWhenTrue() {
        Kind<ValidatedKind.Witness<String>, Unit> result =
            selective.whenS(conditionTrue, unitEffectKind);
        assertThatValidated(result).isValid().hasValue(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS() skips effect when condition is false")
      void whenSSkipsEffectWhenFalse() {
        Kind<ValidatedKind.Witness<String>, Unit> result =
            selective.whenS(conditionFalse, unitEffectKind);
        assertThatValidated(result).isValid().hasValue(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS() propagates Invalid from condition")
      void whenSPropagatesInvalidFromCondition() {
        Kind<ValidatedKind.Witness<String>, Boolean> errorCondition = invalidKind(DEFAULT_ERROR);

        Kind<ValidatedKind.Witness<String>, Unit> result =
            selective.whenS(errorCondition, unitEffectKind);
        assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR);
      }

      @Test
      @DisplayName("whenS() accumulates errors from condition and effect")
      void whenSAccumulatesErrors() {
        Kind<ValidatedKind.Witness<String>, Boolean> errorCondition = invalidKind("error1");
        Kind<ValidatedKind.Witness<String>, Unit> errorEffect = invalidKind("error2");

        Kind<ValidatedKind.Witness<String>, Unit> result =
            selective.whenS(errorCondition, errorEffect);
        assertThatValidated(result).isInvalid().hasError("error1, error2");
      }

      @Test
      @DisplayName("whenS() does not accumulate effect error when condition is false")
      void whenSDoesNotAccumulateEffectErrorWhenConditionFalse() {
        Kind<ValidatedKind.Witness<String>, Unit> errorEffect = invalidKind("effect-error");

        Kind<ValidatedKind.Witness<String>, Unit> result =
            selective.whenS(conditionFalse, errorEffect);
        // Should return Valid(Unit), not accumulate the effect error
        assertThatValidated(result).isValid().hasValue(Unit.INSTANCE);
      }
    }

    @Nested
    @DisplayName("IfS Operation Tests")
    class IfSOperationTests {

      @Test
      @DisplayName("ifS() returns then branch when condition is true")
      void ifSReturnsThenBranchWhenTrue() {
        Kind<ValidatedKind.Witness<String>, Integer> result =
            selective.ifS(conditionTrue, thenBranch, elseBranch);
        assertThatValidated(result).isValid().hasValue(DEFAULT_VALID_VALUE);
        assertThat(result).isSameAs(thenBranch);
      }

      @Test
      @DisplayName("ifS() returns else branch when condition is false")
      void ifSReturnsElseBranchWhenFalse() {
        Kind<ValidatedKind.Witness<String>, Integer> result =
            selective.ifS(conditionFalse, thenBranch, elseBranch);
        assertThatValidated(result).isValid().hasValue(ALTERNATIVE_VALID_VALUE);
        assertThat(result).isSameAs(elseBranch);
      }

      @Test
      @DisplayName("ifS() propagates Invalid from condition")
      void ifSPropagatesInvalidFromCondition() {
        Kind<ValidatedKind.Witness<String>, Boolean> errorCondition = invalidKind(DEFAULT_ERROR);

        Kind<ValidatedKind.Witness<String>, Integer> result =
            selective.ifS(errorCondition, thenBranch, elseBranch);
        assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR);
      }

      @Test
      @DisplayName("ifS() accumulates errors from all branches")
      void ifSAccumulatesAllErrors() {
        Kind<ValidatedKind.Witness<String>, Boolean> errorCondition = invalidKind("error1");
        Kind<ValidatedKind.Witness<String>, Integer> errorThen = invalidKind("error2");
        Kind<ValidatedKind.Witness<String>, Integer> errorElse = invalidKind("error3");

        Kind<ValidatedKind.Witness<String>, Integer> result =
            selective.ifS(errorCondition, errorThen, errorElse);
        assertThatValidated(result).isInvalid().hasError("error1, error2, error3");
      }

      @Test
      @DisplayName("ifS() accumulates only relevant branch error based on condition")
      void ifSAccumulatesOnlyRelevantBranchError() {
        // When condition is true, only then branch error should accumulate
        Kind<ValidatedKind.Witness<String>, Integer> errorThen = invalidKind("then-error");

        Kind<ValidatedKind.Witness<String>, Integer> thenResult =
            selective.ifS(conditionTrue, errorThen, elseBranch);
        assertThatValidated(thenResult).isInvalid().hasError("then-error");

        // When condition is false, only else branch error should accumulate
        Kind<ValidatedKind.Witness<String>, Integer> errorElse = invalidKind("else-error");

        Kind<ValidatedKind.Witness<String>, Integer> elseResult =
            selective.ifS(conditionFalse, thenBranch, errorElse);
        assertThatValidated(elseResult).isInvalid().hasError("else-error");
      }
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Chained selective operations")
    void chainedSelectiveOperations() {
      Kind<ValidatedKind.Witness<String>, Integer> start = validKind;

      Kind<ValidatedKind.Witness<String>, Boolean> condition = selective.map(i -> i > 0, start);

      Kind<ValidatedKind.Witness<String>, Integer> doubled = selective.map(i -> i * 2, start);
      Kind<ValidatedKind.Witness<String>, Unit> result = selective.whenS_(condition, doubled);
      assertThatValidated(result).isValid().hasValue(Unit.INSTANCE);
    }

    @Test
    @DisplayName("Nested selective operations")
    void nestedSelectiveOperations() {
      Kind<ValidatedKind.Witness<String>, Integer> innerResult =
          selective.ifS(conditionTrue, thenBranch, elseBranch);

      Kind<ValidatedKind.Witness<String>, Integer> outerResult =
          selective.ifS(conditionTrue, innerResult, elseBranch);
      assertThatValidated(outerResult).isValid().hasValue(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("Error accumulation with multiple Invalid values")
    void errorAccumulationWithMultipleInvalidValues() {
      Kind<ValidatedKind.Witness<String>, Choice<Integer, String>> errorChoice =
          invalidKind("error1");
      Kind<ValidatedKind.Witness<String>, Function<Integer, String>> errorFunc1 =
          invalidKind("error2");

      Kind<ValidatedKind.Witness<String>, String> result =
          selective.select(errorChoice, errorFunc1);
      assertThatValidated(result)
          .isInvalid()
          .hasErrorSatisfying(
              error -> error.contains("error1") && error.contains("error2"),
              "contains both errors");
    }
  }

  @Nested
  @DisplayName("Integration Examples")
  class IntegrationExamples {

    @Test
    @DisplayName("Real-world scenario: conditional validation pipeline with error accumulation")
    void conditionalValidationPipelineWithErrorAccumulation() {
      Function<Integer, Validated<String, Integer>> validatePositive =
          i -> i > 0 ? Validated.valid(i) : Validated.invalid("Must be positive");

      Function<Integer, Validated<String, Integer>> validateRange =
          i -> i <= 100 ? Validated.valid(i) : Validated.invalid("Must be <= 100");

      Validated<String, Integer> input = Validated.valid(50);
      Kind<ValidatedKind.Witness<String>, Integer> inputKind = VALIDATED.widen(input);

      Function<Integer, Kind<ValidatedKind.Witness<String>, Integer>> validatePositiveKind =
          i -> VALIDATED.widen(validatePositive.apply(i));

      Kind<ValidatedKind.Witness<String>, Integer> validated =
          selective.flatMap(validatePositiveKind, inputKind);

      Kind<ValidatedKind.Witness<String>, Boolean> needsRangeCheck =
          selective.map(i -> i > 50, validated);

      Function<Integer, Kind<ValidatedKind.Witness<String>, Integer>> validateRangeKind =
          i -> VALIDATED.widen(validateRange.apply(i));
      Kind<ValidatedKind.Witness<String>, Integer> rangeChecked =
          selective.flatMap(validateRangeKind, validated);

      Kind<ValidatedKind.Witness<String>, Integer> result =
          selective.ifS(needsRangeCheck, rangeChecked, validated);
      assertThatValidated(result).isValid();
    }

    @Test
    @DisplayName("Real-world scenario: optional effect execution with error accumulation")
    void optionalEffectExecutionWithErrorAccumulation() {
      AtomicInteger counter = new AtomicInteger(0);

      Kind<ValidatedKind.Witness<String>, Boolean> shouldLog =
          VALIDATED.widen(Validated.valid(true));

      Kind<ValidatedKind.Witness<String>, Integer> loggingEffect =
          selective.map(
              i -> {
                counter.incrementAndGet();
                return i;
              },
              validKind);

      selective.whenS_(shouldLog, loggingEffect);

      assertThat(counter.get()).isEqualTo(1);

      counter.set(0);
      Kind<ValidatedKind.Witness<String>, Boolean> shouldNotLog =
          VALIDATED.widen(Validated.valid(false));

      selective.whenS_(shouldNotLog, loggingEffect);

      assertThat(counter.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("Demonstrating error accumulation advantage over Either")
    void errorAccumulationAdvantageOverEither() {
      // Multiple validation errors that will all be collected
      Kind<ValidatedKind.Witness<String>, Boolean> invalidCondition =
          invalidKind("invalid-condition");
      Kind<ValidatedKind.Witness<String>, Integer> invalidThen = invalidKind("invalid-then");
      Kind<ValidatedKind.Witness<String>, Integer> invalidElse = invalidKind("invalid-else");

      Kind<ValidatedKind.Witness<String>, Integer> result =
          selective.ifS(invalidCondition, invalidThen, invalidElse);
      assertThatValidated(result)
          .isInvalid()
          .hasErrorSatisfying(
              error ->
                  error.contains("invalid-condition")
                      && error.contains("invalid-then")
                      && error.contains("invalid-else"),
              "accumulates all three errors");
    }

    @Test
    @DisplayName("Complex validation scenario with multiple error sources")
    void complexValidationScenarioWithMultipleErrorSources() {
      // Create multiple validation steps that can fail
      Kind<ValidatedKind.Witness<String>, Integer> step1 = invalidKind("step1-error");
      Kind<ValidatedKind.Witness<String>, Integer> step2 = invalidKind("step2-error");
      Kind<ValidatedKind.Witness<String>, Integer> step3 = invalidKind("step3-error");

      // Combine them using selective operations
      Kind<ValidatedKind.Witness<String>, Boolean> condition1 = selective.map(i -> i > 0, step1);
      Kind<ValidatedKind.Witness<String>, Boolean> condition2 = selective.map(i -> i < 100, step2);

      // This should accumulate all errors
      Kind<ValidatedKind.Witness<String>, Integer> combined =
          selective.flatMap(
              _ -> selective.flatMap(_ -> selective.ifS(condition1, step2, step3), condition2),
              condition1);
      assertThatValidated(combined)
          .isInvalid()
          .hasErrorSatisfying(error -> error.contains("step1-error"), "contains step1 error");
    }
  }

  @Nested
  @DisplayName("Error Accumulation Semantics Tests")
  class ErrorAccumulationSemanticsTests {

    @Test
    @DisplayName("whenS accumulates errors even when condition is valid")
    void whenSAccumulatesErrorsEvenWhenConditionValid() {
      Kind<ValidatedKind.Witness<String>, Unit> errorEffect = invalidKind("effect-error");

      Kind<ValidatedKind.Witness<String>, Unit> result =
          selective.whenS(conditionTrue, errorEffect);
      assertThatValidated(result).isInvalid().hasError("effect-error");
    }

    @Test
    @DisplayName("select accumulates errors from choice even when function is valid")
    void selectAccumulatesErrorsFromChoiceEvenWhenFunctionValid() {
      Kind<ValidatedKind.Witness<String>, Choice<Integer, String>> errorChoice =
          invalidKind("choice-error");

      Kind<ValidatedKind.Witness<String>, String> result =
          selective.select(errorChoice, selectFunctionKind);
      assertThatValidated(result).isInvalid().hasError("choice-error");
    }

    @Test
    @DisplayName("branch accumulates errors from both handlers when choice is invalid")
    void branchAccumulatesErrorsFromBothHandlersWhenChoiceInvalid() {
      Kind<ValidatedKind.Witness<String>, Choice<Integer, String>> errorChoice =
          invalidKind("choice-error");
      Kind<ValidatedKind.Witness<String>, Function<Integer, String>> errorLeft =
          invalidKind("left-error");
      Kind<ValidatedKind.Witness<String>, Function<String, String>> errorRight =
          invalidKind("right-error");

      Kind<ValidatedKind.Witness<String>, String> result =
          selective.branch(errorChoice, errorLeft, errorRight);
      assertThatValidated(result)
          .isInvalid()
          .hasErrorSatisfying(
              error ->
                  error.contains("choice-error")
                      && error.contains("left-error")
                      && error.contains("right-error"),
              "accumulates choice and both handler errors");
    }

    @Test
    @DisplayName("ifS accumulates errors from both branches when condition is invalid")
    void ifSAccumulatesErrorsFromBothBranchesWhenConditionInvalid() {
      Kind<ValidatedKind.Witness<String>, Boolean> errorCondition = invalidKind("condition-error");
      Kind<ValidatedKind.Witness<String>, Integer> errorThen = invalidKind("then-error");
      Kind<ValidatedKind.Witness<String>, Integer> errorElse = invalidKind("else-error");

      Kind<ValidatedKind.Witness<String>, Integer> result =
          selective.ifS(errorCondition, errorThen, errorElse);
      assertThatValidated(result)
          .isInvalid()
          .hasErrorSatisfying(
              error ->
                  error.contains("condition-error")
                      && error.contains("then-error")
                      && error.contains("else-error"),
              "accumulates condition and both branch errors");
    }
  }
}
