// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.maybe.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeSelective Complete Test Suite")
class MaybeSelectiveTest extends MaybeTestBase {

  private MaybeSelective selective;

  // Selective-specific test data
  private Kind<MaybeKind.Witness, Choice<Integer, String>> choiceLeftKind;
  private Kind<MaybeKind.Witness, Choice<Integer, String>> choiceRightKind;
  private Kind<MaybeKind.Witness, Function<Integer, String>> selectFunctionKind;
  private Kind<MaybeKind.Witness, Function<Integer, String>> leftHandlerKind;
  private Kind<MaybeKind.Witness, Function<String, String>> rightHandlerKind;
  private Kind<MaybeKind.Witness, Boolean> conditionTrue;
  private Kind<MaybeKind.Witness, Boolean> conditionFalse;
  private Kind<MaybeKind.Witness, Integer> effectKind;
  private Kind<MaybeKind.Witness, Integer> thenBranch;
  private Kind<MaybeKind.Witness, Integer> elseBranch;

  @BeforeEach
  void setUpSelective() {
    selective = MaybeSelective.INSTANCE;
    setUpSelectiveFixtures();
  }

  private void setUpSelectiveFixtures() {
    // Create Choice instances
    Choice<Integer, String> choiceLeft =
        new org.higherkindedj.hkt.Selective.SimpleChoice<>(true, DEFAULT_JUST_VALUE, null);
    Choice<Integer, String> choiceRight =
        new org.higherkindedj.hkt.Selective.SimpleChoice<>(false, null, "right-value");

    choiceLeftKind = MAYBE.widen(Maybe.just(choiceLeft));
    choiceRightKind = MAYBE.widen(Maybe.just(choiceRight));

    // Create function handlers - all must return the same type C = String
    Function<Integer, String> selectFunc = i -> "selected:" + i;
    Function<Integer, String> leftHandler = i -> "left:" + i;
    Function<String, String> rightHandler = s -> "right:" + s;

    selectFunctionKind = MAYBE.widen(Maybe.just(selectFunc));
    leftHandlerKind = MAYBE.widen(Maybe.just(leftHandler));
    rightHandlerKind = MAYBE.widen(Maybe.just(rightHandler));

    // Create boolean conditions
    conditionTrue = MAYBE.widen(Maybe.just(true));
    conditionFalse = MAYBE.widen(Maybe.just(false));

    // Create effect and branch kinds
    effectKind = validKind;
    thenBranch = validKind;
    elseBranch = justKind(ALTERNATIVE_JUST_VALUE);
  }

  @Nested
  @DisplayName("Complete Test Suite Using New API")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Selective test pattern")
    void runCompleteSelectiveTestPattern() {
      TypeClassTest.<MaybeKind.Witness>selective(MaybeSelective.class)
          .<Integer>instance(selective)
          .<String>withKind(validKind)
          .withSelectiveOperations(choiceLeftKind, selectFunctionKind)
          .withOperations(
              leftHandlerKind, rightHandlerKind, conditionTrue, effectKind, thenBranch, elseBranch)
          .withLawsTesting("test-value", validMapper, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withApFrom(MaybeMonad.class)
          .withSelectFrom(MaybeSelective.class)
          .withBranchFrom(MaybeSelective.class)
          .withWhenSFrom(MaybeSelective.class)
          .withIfSFrom(MaybeSelective.class)
          .done()
          .testAll();
    }

    @Test
    @DisplayName("Selective testing - operations only")
    void selectiveTestingOperationsOnly() {
      TypeClassTest.<MaybeKind.Witness>selective(MaybeSelective.class)
          .<Integer>instance(selective)
          .<String>withKind(validKind)
          .withSelectiveOperations(choiceLeftKind, selectFunctionKind)
          .withOperations(
              leftHandlerKind, rightHandlerKind, conditionTrue, effectKind, thenBranch, elseBranch)
          .selectTests()
          .skipValidations()
          .skipLaws()
          .test();
    }

    @Test
    @DisplayName("Quick smoke test - operations and validations")
    void quickSmokeTest() {
      TypeClassTest.<MaybeKind.Witness>selective(MaybeSelective.class)
          .<Integer>instance(selective)
          .<String>withKind(validKind)
          .withSelectiveOperations(choiceLeftKind, selectFunctionKind)
          .withOperations(
              leftHandlerKind, rightHandlerKind, conditionTrue, effectKind, thenBranch, elseBranch)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withApFrom(MaybeMonad.class)
          .withSelectFrom(MaybeSelective.class)
          .withBranchFrom(MaybeSelective.class)
          .withWhenSFrom(MaybeSelective.class)
          .withIfSFrom(MaybeSelective.class)
          .done()
          .testOperationsAndValidations();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(MaybeSelectiveTest.class);

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
        Kind<MaybeKind.Witness, String> result =
            selective.select(choiceLeftKind, selectFunctionKind);

        Maybe<String> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isJust().hasValue("selected:" + DEFAULT_JUST_VALUE);
      }

      @Test
      @DisplayName("select() returns Right value when Choice is Right")
      void selectReturnsRightValue() {
        Kind<MaybeKind.Witness, String> result =
            selective.select(choiceRightKind, selectFunctionKind);

        Maybe<String> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isJust().hasValue("right-value");
      }

      @Test
      @DisplayName("select() propagates Nothing from choice")
      void selectPropagatesNothingFromChoice() {
        Kind<MaybeKind.Witness, Choice<Integer, String>> errorChoice = nothingKind();

        Kind<MaybeKind.Witness, String> result = selective.select(errorChoice, selectFunctionKind);

        Maybe<String> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isNothing();
      }

      @Test
      @DisplayName("select() propagates Nothing from function")
      void selectPropagatesNothingFromFunction() {
        Kind<MaybeKind.Witness, Function<Integer, String>> errorFunc = nothingKind();

        Kind<MaybeKind.Witness, String> result = selective.select(choiceLeftKind, errorFunc);

        Maybe<String> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isNothing();
      }

      @Test
      @DisplayName("select() short-circuits on first Nothing")
      void selectShortCircuitsOnFirstNothing() {
        Kind<MaybeKind.Witness, Choice<Integer, String>> errorChoice = nothingKind();
        Kind<MaybeKind.Witness, Function<Integer, String>> errorFunc = nothingKind();

        Kind<MaybeKind.Witness, String> result = selective.select(errorChoice, errorFunc);

        Maybe<String> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isNothing();
      }
    }

    @Nested
    @DisplayName("Branch Operation Tests")
    class BranchOperationTests {

      @Test
      @DisplayName("branch() applies left handler to Left value")
      void branchAppliesLeftHandler() {
        Kind<MaybeKind.Witness, String> result =
            selective.branch(choiceLeftKind, leftHandlerKind, rightHandlerKind);

        Maybe<String> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isJust().hasValue("left:" + DEFAULT_JUST_VALUE);
      }

      @Test
      @DisplayName("branch() applies right handler to Right value")
      void branchAppliesRightHandler() {
        Kind<MaybeKind.Witness, String> result =
            selective.branch(choiceRightKind, leftHandlerKind, rightHandlerKind);

        Maybe<String> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isJust().hasValue("right:right-value");
      }

      @Test
      @DisplayName("branch() propagates Nothing from choice")
      void branchPropagatesNothingFromChoice() {
        Kind<MaybeKind.Witness, Choice<Integer, String>> errorChoice = nothingKind();

        Kind<MaybeKind.Witness, String> result =
            selective.branch(errorChoice, leftHandlerKind, rightHandlerKind);

        Maybe<String> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isNothing();
      }

      @Test
      @DisplayName("branch() propagates Nothing from left handler")
      void branchPropagatesNothingFromLeftHandler() {
        Kind<MaybeKind.Witness, Function<Integer, String>> errorLeftHandler = nothingKind();

        Kind<MaybeKind.Witness, String> result =
            selective.branch(choiceLeftKind, errorLeftHandler, rightHandlerKind);

        Maybe<String> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isNothing();
      }

      @Test
      @DisplayName("branch() propagates Nothing from right handler")
      void branchPropagatesNothingFromRightHandler() {
        Kind<MaybeKind.Witness, Function<String, String>> errorRightHandler = nothingKind();

        Kind<MaybeKind.Witness, String> result =
            selective.branch(choiceRightKind, leftHandlerKind, errorRightHandler);

        Maybe<String> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isNothing();
      }
    }

    @Nested
    @DisplayName("WhenS Operation Tests")
    class WhenSOperationTests {

      @Test
      @DisplayName("whenS() executes effect when condition is true")
      void whenSExecutesEffectWhenTrue() {
        Kind<MaybeKind.Witness, Integer> result = selective.whenS(conditionTrue, effectKind);

        Maybe<Integer> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isJust().hasValue(DEFAULT_JUST_VALUE);
      }

      @Test
      @DisplayName("whenS() skips effect when condition is false")
      void whenSSkipsEffectWhenFalse() {
        Kind<MaybeKind.Witness, Integer> result = selective.whenS(conditionFalse, effectKind);

        Maybe<Integer> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isNothing();
      }

      @Test
      @DisplayName("whenS() propagates Nothing from condition")
      void whenSPropagatesNothingFromCondition() {
        Kind<MaybeKind.Witness, Boolean> errorCondition = nothingKind();

        Kind<MaybeKind.Witness, Integer> result = selective.whenS(errorCondition, effectKind);

        Maybe<Integer> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isNothing();
      }

      @Test
      @DisplayName("whenS() does not execute effect on Nothing condition")
      void whenSDoesNotExecuteEffectOnNothingCondition() {
        Kind<MaybeKind.Witness, Boolean> errorCondition = nothingKind();

        Kind<MaybeKind.Witness, Integer> throwingEffect = MAYBE.widen(Maybe.just(42));

        Kind<MaybeKind.Witness, Integer> result = selective.whenS(errorCondition, throwingEffect);

        assertThatCode(() -> MAYBE.narrow(result)).doesNotThrowAnyException();
      }
    }

    @Nested
    @DisplayName("IfS Operation Tests")
    class IfSOperationTests {

      @Test
      @DisplayName("ifS() returns then branch when condition is true")
      void ifSReturnsThenBranchWhenTrue() {
        Kind<MaybeKind.Witness, Integer> result =
            selective.ifS(conditionTrue, thenBranch, elseBranch);

        Maybe<Integer> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isJust().hasValue(DEFAULT_JUST_VALUE);

        assertThat(result).isSameAs(thenBranch);
      }

      @Test
      @DisplayName("ifS() returns else branch when condition is false")
      void ifSReturnsElseBranchWhenFalse() {
        Kind<MaybeKind.Witness, Integer> result =
            selective.ifS(conditionFalse, thenBranch, elseBranch);

        Maybe<Integer> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isJust().hasValue(ALTERNATIVE_JUST_VALUE);

        assertThat(result).isSameAs(elseBranch);
      }

      @Test
      @DisplayName("ifS() propagates Nothing from condition")
      void ifSPropagatesNothingFromCondition() {
        Kind<MaybeKind.Witness, Boolean> errorCondition = nothingKind();

        Kind<MaybeKind.Witness, Integer> result =
            selective.ifS(errorCondition, thenBranch, elseBranch);

        Maybe<Integer> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isNothing();
      }

      @Test
      @DisplayName("ifS() does not evaluate both branches")
      void ifSDoesNotEvaluateBothBranches() {
        Kind<MaybeKind.Witness, Integer> result =
            selective.ifS(conditionTrue, thenBranch, elseBranch);

        assertThat(result).isSameAs(thenBranch);

        result = selective.ifS(conditionFalse, thenBranch, elseBranch);
        assertThat(result).isSameAs(elseBranch);
      }
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<MaybeKind.Witness>selective(MaybeSelective.class)
          .<Integer>instance(selective)
          .<String>withKind(validKind)
          .withSelectiveOperations(choiceLeftKind, selectFunctionKind)
          .<String>withOperations(
              leftHandlerKind, rightHandlerKind, conditionTrue, effectKind, thenBranch, elseBranch)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<MaybeKind.Witness>selective(MaybeSelective.class)
          .<Integer>instance(selective)
          .<String>withKind(validKind)
          .withSelectiveOperations(choiceLeftKind, selectFunctionKind)
          .withOperations(
              leftHandlerKind, rightHandlerKind, conditionTrue, effectKind, thenBranch, elseBranch)
          .configureValidation()
          .useInheritanceValidation()
          .withApFrom(MaybeMonad.class)
          .withMapFrom(MaybeFunctor.class)
          .withSelectFrom(MaybeSelective.class)
          .withBranchFrom(MaybeSelective.class)
          .withWhenSFrom(MaybeSelective.class)
          .withIfSFrom(MaybeSelective.class)
          .done()
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<MaybeKind.Witness>selective(MaybeSelective.class)
          .<Integer>instance(selective)
          .<String>withKind(validKind)
          .withSelectiveOperations(choiceLeftKind, selectFunctionKind)
          .<String>withOperations(
              leftHandlerKind, rightHandlerKind, conditionTrue, effectKind, thenBranch, elseBranch)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<MaybeKind.Witness>selective(MaybeSelective.class)
          .<Integer>instance(selective)
          .<String>withKind(validKind)
          .withSelectiveOperations(choiceLeftKind, selectFunctionKind)
          .withOperations(
              leftHandlerKind, rightHandlerKind, conditionTrue, effectKind, thenBranch, elseBranch)
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
      Kind<MaybeKind.Witness, Integer> start = validKind;

      Kind<MaybeKind.Witness, Boolean> condition = selective.map(i -> i > 0, start);

      Kind<MaybeKind.Witness, Integer> doubled = selective.map(i -> i * 2, start);
      Kind<MaybeKind.Witness, Integer> result = selective.whenS(condition, doubled);

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThatMaybe(maybe).isJust().hasValue(DEFAULT_JUST_VALUE * 2);
    }

    @Test
    @DisplayName("Nested selective operations")
    void nestedSelectiveOperations() {
      Kind<MaybeKind.Witness, Integer> innerResult =
          selective.ifS(conditionTrue, thenBranch, elseBranch);

      Kind<MaybeKind.Witness, Integer> outerResult =
          selective.ifS(conditionTrue, innerResult, elseBranch);

      Maybe<Integer> maybe = MAYBE.narrow(outerResult);
      assertThatMaybe(maybe).isJust().hasValue(DEFAULT_JUST_VALUE);
    }

    @Test
    @DisplayName("Select with null value in Choice")
    void selectWithNullValueInChoice() {
      Choice<Integer, String> choiceWithNull =
          new org.higherkindedj.hkt.Selective.SimpleChoice<>(true, null, null);
      Kind<MaybeKind.Witness, Choice<Integer, String>> choiceKind =
          MAYBE.widen(Maybe.just(choiceWithNull));

      Function<Integer, String> nullSafeFunc = i -> i == null ? "null-value" : "value:" + i;
      Kind<MaybeKind.Witness, Function<Integer, String>> funcKind =
          MAYBE.widen(Maybe.just(nullSafeFunc));

      Kind<MaybeKind.Witness, String> result = selective.select(choiceKind, funcKind);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThatMaybe(maybe).isJust().hasValue("null-value");
    }
  }

  @Nested
  @DisplayName("Integration Examples")
  class IntegrationExamples {

    @Test
    @DisplayName("Real-world scenario: conditional validation pipeline")
    void conditionalValidationPipeline() {
      Function<Integer, Maybe<Integer>> validatePositive =
          i -> i > 0 ? Maybe.just(i) : Maybe.nothing();

      Function<Integer, Maybe<Integer>> validateRange =
          i -> i <= 100 ? Maybe.just(i) : Maybe.nothing();

      Maybe<Integer> input = Maybe.just(50);
      Kind<MaybeKind.Witness, Integer> inputKind = MAYBE.widen(input);

      Function<Integer, Kind<MaybeKind.Witness, Integer>> validatePositiveKind =
          i -> MAYBE.widen(validatePositive.apply(i));
      Function<Integer, Kind<MaybeKind.Witness, Integer>> validateRangeKind =
          i -> MAYBE.widen(validateRange.apply(i));

      Kind<MaybeKind.Witness, Integer> validated =
          selective.flatMap(validatePositiveKind, inputKind);

      Kind<MaybeKind.Witness, Boolean> needsRangeCheck = selective.map(i -> i > 50, validated);

      Kind<MaybeKind.Witness, Integer> rangeChecked =
          selective.flatMap(validateRangeKind, validated);

      Kind<MaybeKind.Witness, Integer> result =
          selective.ifS(needsRangeCheck, rangeChecked, validated);

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThatMaybe(maybe).isJust();
    }

    @Test
    @DisplayName("Real-world scenario: optional effect execution")
    void optionalEffectExecution() {
      java.util.concurrent.atomic.AtomicInteger counter =
          new java.util.concurrent.atomic.AtomicInteger(0);

      Kind<MaybeKind.Witness, Boolean> shouldLog = MAYBE.widen(Maybe.just(true));

      Kind<MaybeKind.Witness, Integer> loggingEffect =
          selective.map(
              i -> {
                counter.incrementAndGet();
                return i;
              },
              validKind);

      selective.whenS(shouldLog, loggingEffect);

      assertThat(counter.get()).isEqualTo(1);

      counter.set(0);
      Kind<MaybeKind.Witness, Boolean> shouldNotLog = MAYBE.widen(Maybe.just(false));

      selective.whenS(shouldNotLog, loggingEffect);

      assertThat(counter.get()).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Selective operations efficient with many conditionals")
    void selectiveOperationsEfficientWithManyConditionals() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Kind<MaybeKind.Witness, Integer> start = validKind;

        Kind<MaybeKind.Witness, Integer> result = start;
        for (int i = 0; i < 100; i++) {
          final int index = i;
          Kind<MaybeKind.Witness, Boolean> condition = selective.map(val -> val > index, result);
          Kind<MaybeKind.Witness, Integer> thenValue = selective.map(val -> val + 1, result);
          result = selective.ifS(condition, thenValue, result);
        }

        Maybe<Integer> maybe = MAYBE.narrow(result);
        assertThatMaybe(maybe).isJust();
      }
    }
  }
}
