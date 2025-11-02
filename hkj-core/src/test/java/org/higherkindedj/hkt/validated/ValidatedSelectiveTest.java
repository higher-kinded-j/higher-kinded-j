// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.validated.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.Function;
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for {@link ValidatedSelective} with enhanced coverage matching
 * EitherSelective patterns.
 *
 * <p>This test class provides complete coverage of all Selective operations including: - Core
 * select() operation with error accumulation - Branch operation with both handlers and error
 * accumulation - WhenS operation with Unit semantics and error accumulation - IfS conditional
 * operation with error accumulation - Error accumulation advantage over Either - Integration
 * scenarios - Performance tests
 */
@DisplayName("ValidatedSelective Complete Test Suite")
class ValidatedSelectiveTest extends ValidatedTestBase {

  private ValidatedSelective<String> selective;
  private Semigroup<String> stringSemigroup;

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
    stringSemigroup = Semigroups.string(", ");
    selective = ValidatedSelective.instance(stringSemigroup);
    validateMonadFixtures();
    setUpSelectiveFixtures();
  }

  private void setUpSelectiveFixtures() {
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
  @DisplayName("Complete Test Suite Using New API")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Selective test pattern")
    void runCompleteSelectiveTestPattern() {
      TypeClassTest.<ValidatedKind.Witness<String>>selective(ValidatedSelective.class)
          .<Integer>instance(selective)
          .<String>withKind(validKind)
          .withSelectiveOperations(choiceLeftKind, selectFunctionKind)
          .withOperations(
              leftHandlerKind,
              rightHandlerKind,
              conditionTrue,
              unitEffectKind,
              thenBranch,
              elseBranch)
          .withLawsTesting("test-value", validMapper, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(ValidatedMonad.class)
          .withApFrom(ValidatedMonad.class)
          .withSelectFrom(ValidatedSelective.class)
          .withBranchFrom(ValidatedSelective.class)
          .withWhenSFrom(ValidatedSelective.class)
          .withIfSFrom(ValidatedSelective.class)
          .done()
          .testAll();
    }

    @Test
    @DisplayName("Selective testing - operations only")
    void selectiveTestingOperationsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>selective(ValidatedSelective.class)
          .<Integer>instance(selective)
          .<String>withKind(validKind)
          .withSelectiveOperations(choiceLeftKind, selectFunctionKind)
          .withOperations(
              leftHandlerKind,
              rightHandlerKind,
              conditionTrue,
              unitEffectKind,
              thenBranch,
              elseBranch)
          .selectTests()
          .skipValidations()
          .skipLaws()
          .test();
    }

    @Test
    @DisplayName("Quick smoke test - operations and validations")
    void quickSmokeTest() {
      TypeClassTest.<ValidatedKind.Witness<String>>selective(ValidatedSelective.class)
          .<Integer>instance(selective)
          .<String>withKind(validKind)
          .withSelectiveOperations(choiceLeftKind, selectFunctionKind)
          .withOperations(
              leftHandlerKind,
              rightHandlerKind,
              conditionTrue,
              unitEffectKind,
              thenBranch,
              elseBranch)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(ValidatedMonad.class)
          .withApFrom(ValidatedMonad.class)
          .withSelectFrom(ValidatedSelective.class)
          .withBranchFrom(ValidatedSelective.class)
          .withWhenSFrom(ValidatedSelective.class)
          .withIfSFrom(ValidatedSelective.class)
          .done()
          .testOperationsAndValidations();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(ValidatedSelectiveTest.class);

      if (result.hasErrors()) {
        result.printReport();
        throw new AssertionError("Test structure validation failed");
      }
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("All select operations work correctly")
    void testSelectOperations() {
      // Delegate to the detailed select tests
      new SelectOperationTests().selectAppliesFunctionToLeftValue();
      new SelectOperationTests().selectReturnsRightValue();
    }

    @Test
    @DisplayName("All branch operations work correctly")
    void testBranchOperations() {
      // Delegate to the detailed branch tests
      new BranchOperationTests().branchAppliesLeftHandler();
      new BranchOperationTests().branchAppliesRightHandler();
    }

    @Test
    @DisplayName("All whenS operations work correctly")
    void testWhenSOperations() {
      // Delegate to the detailed whenS tests
      new WhenSOperationTests().whenSExecutesEffectWhenTrue();
      new WhenSOperationTests().whenSSkipsEffectWhenFalse();
    }

    @Test
    @DisplayName("All ifS operations work correctly")
    void testIfSOperations() {
      // Delegate to the detailed ifS tests
      new IfSOperationTests().ifSReturnsThenBranchWhenTrue();
      new IfSOperationTests().ifSReturnsElseBranchWhenFalse();
    }

    @Nested
    @DisplayName("Select Operation Tests")
    class SelectOperationTests {

      @Test
      @DisplayName("select() applies function to Left value in Choice")
      void selectAppliesFunctionToLeftValue() {
        Kind<ValidatedKind.Witness<String>, String> result =
            selective.select(choiceLeftKind, selectFunctionKind);

        Validated<String, String> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isValid().hasValue("selected:" + DEFAULT_VALID_VALUE);
      }

      @Test
      @DisplayName("select() returns Right value when Choice is Right")
      void selectReturnsRightValue() {
        Kind<ValidatedKind.Witness<String>, String> result =
            selective.select(choiceRightKind, selectFunctionKind);

        Validated<String, String> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isValid().hasValue("right-value");
      }

      @Test
      @DisplayName("select() propagates Invalid from choice")
      void selectPropagatesInvalidFromChoice() {
        Kind<ValidatedKind.Witness<String>, Choice<Integer, String>> errorChoice =
            invalidKind(DEFAULT_ERROR);

        Kind<ValidatedKind.Witness<String>, String> result =
            selective.select(errorChoice, selectFunctionKind);

        Validated<String, String> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isInvalid().hasError(DEFAULT_ERROR);
      }

      @Test
      @DisplayName("select() propagates Invalid from function")
      void selectPropagatesInvalidFromFunction() {
        Kind<ValidatedKind.Witness<String>, Function<Integer, String>> errorFunc =
            invalidKind("function-error");

        Kind<ValidatedKind.Witness<String>, String> result =
            selective.select(choiceLeftKind, errorFunc);

        Validated<String, String> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isInvalid().hasError("function-error");
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

        Validated<String, String> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isInvalid().hasError("error1, error2");
      }

      @Test
      @DisplayName("select() does not accumulate when choice is right with valid value")
      void selectDoesNotAccumulateWhenChoiceIsRight() {
        Kind<ValidatedKind.Witness<String>, Function<Integer, String>> errorFunc =
            invalidKind("function-error");

        Kind<ValidatedKind.Witness<String>, String> result =
            selective.select(choiceRightKind, errorFunc);

        Validated<String, String> validated = VALIDATED.narrow(result);
        // Should return the right value, not the function error
        assertThatValidated(validated).isValid().hasValue("right-value");
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

        Validated<String, String> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isValid().hasValue("left:" + DEFAULT_VALID_VALUE);
      }

      @Test
      @DisplayName("branch() applies right handler to Right value")
      void branchAppliesRightHandler() {
        Kind<ValidatedKind.Witness<String>, String> result =
            selective.branch(choiceRightKind, leftHandlerKind, rightHandlerKind);

        Validated<String, String> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isValid().hasValue("right:right-value");
      }

      @Test
      @DisplayName("branch() propagates Invalid from choice")
      void branchPropagatesInvalidFromChoice() {
        Kind<ValidatedKind.Witness<String>, Choice<Integer, String>> errorChoice =
            invalidKind(DEFAULT_ERROR);

        Kind<ValidatedKind.Witness<String>, String> result =
            selective.branch(errorChoice, leftHandlerKind, rightHandlerKind);

        Validated<String, String> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isInvalid().hasError(DEFAULT_ERROR);
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

        Validated<String, String> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isInvalid().hasError("error1, error2, error3");
      }

      @Test
      @DisplayName("branch() accumulates only relevant handler errors based on choice")
      void branchAccumulatesOnlyRelevantHandlerErrors() {
        // When choice is left, only left handler error should accumulate
        Kind<ValidatedKind.Witness<String>, Function<Integer, String>> errorLeftHandler =
            invalidKind("left-error");

        Kind<ValidatedKind.Witness<String>, String> leftResult =
            selective.branch(choiceLeftKind, errorLeftHandler, rightHandlerKind);

        Validated<String, String> leftValidated = VALIDATED.narrow(leftResult);
        assertThatValidated(leftValidated).isInvalid().hasError("left-error");

        // When choice is right, only right handler error should accumulate
        Kind<ValidatedKind.Witness<String>, Function<String, String>> errorRightHandler =
            invalidKind("right-error");

        Kind<ValidatedKind.Witness<String>, String> rightResult =
            selective.branch(choiceRightKind, leftHandlerKind, errorRightHandler);

        Validated<String, String> rightValidated = VALIDATED.narrow(rightResult);
        assertThatValidated(rightValidated).isInvalid().hasError("right-error");
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

        Validated<String, Unit> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isValid().hasValue(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS() skips effect when condition is false")
      void whenSSkipsEffectWhenFalse() {
        Kind<ValidatedKind.Witness<String>, Unit> result =
            selective.whenS(conditionFalse, unitEffectKind);

        Validated<String, Unit> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isValid().hasValue(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS() propagates Invalid from condition")
      void whenSPropagatesInvalidFromCondition() {
        Kind<ValidatedKind.Witness<String>, Boolean> errorCondition = invalidKind(DEFAULT_ERROR);

        Kind<ValidatedKind.Witness<String>, Unit> result =
            selective.whenS(errorCondition, unitEffectKind);

        Validated<String, Unit> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isInvalid().hasError(DEFAULT_ERROR);
      }

      @Test
      @DisplayName("whenS() accumulates errors from condition and effect")
      void whenSAccumulatesErrors() {
        Kind<ValidatedKind.Witness<String>, Boolean> errorCondition = invalidKind("error1");
        Kind<ValidatedKind.Witness<String>, Unit> errorEffect = invalidKind("error2");

        Kind<ValidatedKind.Witness<String>, Unit> result =
            selective.whenS(errorCondition, errorEffect);

        Validated<String, Unit> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isInvalid().hasError("error1, error2");
      }

      @Test
      @DisplayName("whenS() does not accumulate effect error when condition is false")
      void whenSDoesNotAccumulateEffectErrorWhenConditionFalse() {
        Kind<ValidatedKind.Witness<String>, Unit> errorEffect = invalidKind("effect-error");

        Kind<ValidatedKind.Witness<String>, Unit> result =
            selective.whenS(conditionFalse, errorEffect);

        Validated<String, Unit> validated = VALIDATED.narrow(result);
        // Should return Valid(Unit), not accumulate the effect error
        assertThatValidated(validated).isValid().hasValue(Unit.INSTANCE);
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

        Validated<String, Integer> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isValid().hasValue(DEFAULT_VALID_VALUE);

        assertThat(validated).isSameAs(VALIDATED.narrow(thenBranch));
      }

      @Test
      @DisplayName("ifS() returns else branch when condition is false")
      void ifSReturnsElseBranchWhenFalse() {
        Kind<ValidatedKind.Witness<String>, Integer> result =
            selective.ifS(conditionFalse, thenBranch, elseBranch);

        Validated<String, Integer> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isValid().hasValue(ALTERNATIVE_VALID_VALUE);

        assertThat(validated).isSameAs(VALIDATED.narrow(elseBranch));
      }

      @Test
      @DisplayName("ifS() propagates Invalid from condition")
      void ifSPropagatesInvalidFromCondition() {
        Kind<ValidatedKind.Witness<String>, Boolean> errorCondition = invalidKind(DEFAULT_ERROR);

        Kind<ValidatedKind.Witness<String>, Integer> result =
            selective.ifS(errorCondition, thenBranch, elseBranch);

        Validated<String, Integer> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isInvalid().hasError(DEFAULT_ERROR);
      }

      @Test
      @DisplayName("ifS() accumulates errors from all branches")
      void ifSAccumulatesAllErrors() {
        Kind<ValidatedKind.Witness<String>, Boolean> errorCondition = invalidKind("error1");
        Kind<ValidatedKind.Witness<String>, Integer> errorThen = invalidKind("error2");
        Kind<ValidatedKind.Witness<String>, Integer> errorElse = invalidKind("error3");

        Kind<ValidatedKind.Witness<String>, Integer> result =
            selective.ifS(errorCondition, errorThen, errorElse);

        Validated<String, Integer> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isInvalid().hasError("error1, error2, error3");
      }

      @Test
      @DisplayName("ifS() accumulates only relevant branch error based on condition")
      void ifSAccumulatesOnlyRelevantBranchError() {
        // When condition is true, only then branch error should accumulate
        Kind<ValidatedKind.Witness<String>, Integer> errorThen = invalidKind("then-error");

        Kind<ValidatedKind.Witness<String>, Integer> thenResult =
            selective.ifS(conditionTrue, errorThen, elseBranch);

        Validated<String, Integer> thenValidated = VALIDATED.narrow(thenResult);
        assertThatValidated(thenValidated).isInvalid().hasError("then-error");

        // When condition is false, only else branch error should accumulate
        Kind<ValidatedKind.Witness<String>, Integer> errorElse = invalidKind("else-error");

        Kind<ValidatedKind.Witness<String>, Integer> elseResult =
            selective.ifS(conditionFalse, thenBranch, errorElse);

        Validated<String, Integer> elseValidated = VALIDATED.narrow(elseResult);
        assertThatValidated(elseValidated).isInvalid().hasError("else-error");
      }
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>selective(ValidatedSelective.class)
          .<Integer>instance(selective)
          .<String>withKind(validKind)
          .withSelectiveOperations(choiceLeftKind, selectFunctionKind)
          .<String>withOperations(
              leftHandlerKind,
              rightHandlerKind,
              conditionTrue,
              unitEffectKind,
              thenBranch,
              elseBranch)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>selective(ValidatedSelective.class)
          .<Integer>instance(selective)
          .<String>withKind(validKind)
          .withSelectiveOperations(choiceLeftKind, selectFunctionKind)
          .withOperations(
              leftHandlerKind,
              rightHandlerKind,
              conditionTrue,
              unitEffectKind,
              thenBranch,
              elseBranch)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(ValidatedMonad.class)
          .withApFrom(ValidatedMonad.class)
          .withSelectFrom(ValidatedSelective.class)
          .withBranchFrom(ValidatedSelective.class)
          .withWhenSFrom(ValidatedSelective.class)
          .withIfSFrom(ValidatedSelective.class)
          .done()
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>selective(ValidatedSelective.class)
          .<Integer>instance(selective)
          .<String>withKind(validKind)
          .withSelectiveOperations(choiceLeftKind, selectFunctionKind)
          .<String>withOperations(
              leftHandlerKind,
              rightHandlerKind,
              conditionTrue,
              unitEffectKind,
              thenBranch,
              elseBranch)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>selective(ValidatedSelective.class)
          .<Integer>instance(selective)
          .<String>withKind(validKind)
          .withSelectiveOperations(choiceLeftKind, selectFunctionKind)
          .withOperations(
              leftHandlerKind,
              rightHandlerKind,
              conditionTrue,
              unitEffectKind,
              thenBranch,
              elseBranch)
          .withLawsTesting("test-value", validMapper, equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
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

      Validated<String, Unit> validated = VALIDATED.narrow(result);
      assertThatValidated(validated).isValid().hasValue(Unit.INSTANCE);
    }

    @Test
    @DisplayName("Nested selective operations")
    void nestedSelectiveOperations() {
      Kind<ValidatedKind.Witness<String>, Integer> innerResult =
          selective.ifS(conditionTrue, thenBranch, elseBranch);

      Kind<ValidatedKind.Witness<String>, Integer> outerResult =
          selective.ifS(conditionTrue, innerResult, elseBranch);

      Validated<String, Integer> validated = VALIDATED.narrow(outerResult);
      assertThatValidated(validated).isValid().hasValue(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("Select with null value in Choice")
    void selectWithNullValueInChoice() {
      Choice<Integer, String> choiceWithNull = Selective.left(null);
      Kind<ValidatedKind.Witness<String>, Choice<Integer, String>> choiceKind =
          VALIDATED.widen(Validated.valid(choiceWithNull));

      Function<Integer, String> nullSafeFunc = i -> i == null ? "null-value" : "value:" + i;
      Kind<ValidatedKind.Witness<String>, Function<Integer, String>> funcKind =
          VALIDATED.widen(Validated.valid(nullSafeFunc));

      Kind<ValidatedKind.Witness<String>, String> result = selective.select(choiceKind, funcKind);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThatValidated(validated).isValid().hasValue("null-value");
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

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThatValidated(validated)
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

      Validated<String, Integer> resultValidated = VALIDATED.narrow(result);
      assertThatValidated(resultValidated).isValid();
    }

    @Test
    @DisplayName("Real-world scenario: optional effect execution with error accumulation")
    void optionalEffectExecutionWithErrorAccumulation() {
      java.util.concurrent.atomic.AtomicInteger counter =
          new java.util.concurrent.atomic.AtomicInteger(0);

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

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThatValidated(validated)
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
              b1 -> selective.flatMap(b2 -> selective.ifS(condition1, step2, step3), condition2),
              condition1);

      Validated<String, Integer> result = VALIDATED.narrow(combined);
      assertThatValidated(result).isInvalid();
      // Verify that errors were accumulated
      assertThat(result.getError()).contains("step1-error");
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Selective operations efficient with many conditionals")
    void selectiveOperationsEfficientWithManyConditionals() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Kind<ValidatedKind.Witness<String>, Integer> start = validKind;

        Kind<ValidatedKind.Witness<String>, Integer> result = start;
        for (int i = 0; i < 100; i++) {
          final int index = i;
          Kind<ValidatedKind.Witness<String>, Boolean> condition =
              selective.map(val -> val > index, result);
          Kind<ValidatedKind.Witness<String>, Integer> thenValue =
              selective.map(val -> val + 1, result);
          result = selective.ifS(condition, thenValue, result);
        }

        Validated<String, Integer> validated = VALIDATED.narrow(result);
        assertThatValidated(validated).isValid();
      }
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

      Validated<String, Unit> validated = VALIDATED.narrow(result);
      assertThatValidated(validated).isInvalid().hasError("effect-error");
    }

    @Test
    @DisplayName("select accumulates errors from choice even when function is valid")
    void selectAccumulatesErrorsFromChoiceEvenWhenFunctionValid() {
      Kind<ValidatedKind.Witness<String>, Choice<Integer, String>> errorChoice =
          invalidKind("choice-error");

      Kind<ValidatedKind.Witness<String>, String> result =
          selective.select(errorChoice, selectFunctionKind);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThatValidated(validated).isInvalid().hasError("choice-error");
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

      Validated<String, String> validated = VALIDATED.narrow(result);
      String error = validated.getError();
      assertThat(error).contains("choice-error");
      assertThat(error).contains("left-error");
      assertThat(error).contains("right-error");
    }

    @Test
    @DisplayName("ifS accumulates errors from both branches when condition is invalid")
    void ifSAccumulatesErrorsFromBothBranchesWhenConditionInvalid() {
      Kind<ValidatedKind.Witness<String>, Boolean> errorCondition = invalidKind("condition-error");
      Kind<ValidatedKind.Witness<String>, Integer> errorThen = invalidKind("then-error");
      Kind<ValidatedKind.Witness<String>, Integer> errorElse = invalidKind("else-error");

      Kind<ValidatedKind.Witness<String>, Integer> result =
          selective.ifS(errorCondition, errorThen, errorElse);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      String error = validated.getError();
      assertThat(error).contains("condition-error");
      assertThat(error).contains("then-error");
      assertThat(error).contains("else-error");
    }
  }
}
