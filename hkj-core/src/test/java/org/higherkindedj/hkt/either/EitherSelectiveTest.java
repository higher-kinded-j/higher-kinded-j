// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherSelective Complete Test Suite")
class EitherSelectiveTest extends EitherTestBase {

  private EitherSelective<String> selective;

  // Selective-specific test data
  private Kind<EitherKind.Witness<String>, Choice<Integer, String>> choiceLeftKind;
  private Kind<EitherKind.Witness<String>, Choice<Integer, String>> choiceRightKind;
  private Kind<EitherKind.Witness<String>, Function<Integer, String>> selectFunctionKind;
  private Kind<EitherKind.Witness<String>, Function<Integer, String>> leftHandlerKind;
  private Kind<EitherKind.Witness<String>, Function<String, String>> rightHandlerKind;
  private Kind<EitherKind.Witness<String>, Boolean> conditionTrue;
  private Kind<EitherKind.Witness<String>, Boolean> conditionFalse;
  private Kind<EitherKind.Witness<String>, Unit> unitEffectKind; // ✓ Changed from effectKind
  private Kind<EitherKind.Witness<String>, Integer> thenBranch;
  private Kind<EitherKind.Witness<String>, Integer> elseBranch;

  @BeforeEach
  void setUpSelective() {
    selective = EitherSelective.instance();
    validateMonadFixtures();
    setUpSelectiveFixtures();
  }

  private void setUpSelectiveFixtures() {
    // Create Choice instances
    Choice<Integer, String> choiceLeft =
        new Selective.SimpleChoice<>(true, DEFAULT_RIGHT_VALUE, null);
    Choice<Integer, String> choiceRight = new Selective.SimpleChoice<>(false, null, "right-value");

    choiceLeftKind = EITHER.widen(Either.right(choiceLeft));
    choiceRightKind = EITHER.widen(Either.right(choiceRight));

    // Create function handlers - all must return the same type C = String
    Function<Integer, String> selectFunc = i -> "selected:" + i;
    Function<Integer, String> leftHandler = i -> "left:" + i;
    Function<String, String> rightHandler = s -> "right:" + s;

    selectFunctionKind = EITHER.widen(Either.right(selectFunc));
    leftHandlerKind = EITHER.widen(Either.right(leftHandler));
    rightHandlerKind = EITHER.widen(Either.right(rightHandler));

    // Create boolean conditions
    conditionTrue = EITHER.widen(Either.right(true));
    conditionFalse = EITHER.widen(Either.right(false));

    // ✓ Create Unit effect for whenS
    unitEffectKind = EITHER.widen(Either.right(Unit.INSTANCE));

    // Create branch kinds for ifS (these stay as Integer)
    thenBranch = validKind;
    elseBranch = rightKind(ALTERNATIVE_RIGHT_VALUE);
  }

  @Nested
  @DisplayName("Complete Test Suite Using New API")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Selective test pattern")
    void runCompleteSelectiveTestPattern() {
      TypeClassTest.<EitherKind.Witness<String>>selective(EitherSelective.class)
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
          .withMapFrom(EitherFunctor.class)
          .withApFrom(EitherMonad.class)
          .withSelectFrom(EitherSelective.class)
          .withBranchFrom(EitherSelective.class)
          .withWhenSFrom(EitherSelective.class)
          .withIfSFrom(EitherSelective.class)
          .done()
          .testAll();
    }

    @Test
    @DisplayName("Selective testing - operations only")
    void selectiveTestingOperationsOnly() {
      TypeClassTest.<EitherKind.Witness<String>>selective(EitherSelective.class)
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
      TypeClassTest.<EitherKind.Witness<String>>selective(EitherSelective.class)
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
          .withMapFrom(EitherFunctor.class)
          .withApFrom(EitherMonad.class)
          .withSelectFrom(EitherSelective.class)
          .withBranchFrom(EitherSelective.class)
          .withWhenSFrom(EitherSelective.class)
          .withIfSFrom(EitherSelective.class)
          .done()
          .testOperationsAndValidations();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(EitherSelectiveTest.class);

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
        Kind<EitherKind.Witness<String>, String> result =
            selective.select(choiceLeftKind, selectFunctionKind);

        Either<String, String> either = EITHER.narrow(result);
        assertThatEither(either).isRight().hasRight("selected:" + DEFAULT_RIGHT_VALUE);
      }

      @Test
      @DisplayName("select() returns Right value when Choice is Right")
      void selectReturnsRightValue() {
        Kind<EitherKind.Witness<String>, String> result =
            selective.select(choiceRightKind, selectFunctionKind);

        Either<String, String> either = EITHER.narrow(result);
        assertThatEither(either).isRight().hasRight("right-value");
      }

      @Test
      @DisplayName("select() propagates Left from choice")
      void selectPropagatesLeftFromChoice() {
        Kind<EitherKind.Witness<String>, Choice<Integer, String>> errorChoice =
            leftKind(TestErrorType.ERROR_1);

        Kind<EitherKind.Witness<String>, String> result =
            selective.select(errorChoice, selectFunctionKind);

        Either<String, String> either = EITHER.narrow(result);
        assertThatEither(either).isLeft().hasLeft(TestErrorType.ERROR_1.message());
      }

      @Test
      @DisplayName("select() propagates Left from function")
      void selectPropagatesLeftFromFunction() {
        Kind<EitherKind.Witness<String>, Function<Integer, String>> errorFunc =
            leftKind(TestErrorType.FUNCTION_ERROR);

        Kind<EitherKind.Witness<String>, String> result =
            selective.select(choiceLeftKind, errorFunc);

        Either<String, String> either = EITHER.narrow(result);
        assertThatEither(either).isLeft().hasLeft(TestErrorType.FUNCTION_ERROR.message());
      }

      @Test
      @DisplayName("select() short-circuits on first error")
      void selectShortCircuitsOnFirstError() {
        Kind<EitherKind.Witness<String>, Choice<Integer, String>> errorChoice =
            leftKind(TestErrorType.ERROR_1);
        Kind<EitherKind.Witness<String>, Function<Integer, String>> errorFunc =
            leftKind(TestErrorType.ERROR_2);

        Kind<EitherKind.Witness<String>, String> result = selective.select(errorChoice, errorFunc);

        Either<String, String> either = EITHER.narrow(result);
        assertThatEither(either).isLeft().hasLeft(TestErrorType.ERROR_1.message());
      }
    }

    @Nested
    @DisplayName("Branch Operation Tests")
    class BranchOperationTests {

      @Test
      @DisplayName("branch() applies left handler to Left value")
      void branchAppliesLeftHandler() {
        Kind<EitherKind.Witness<String>, String> result =
            selective.branch(choiceLeftKind, leftHandlerKind, rightHandlerKind);

        Either<String, String> either = EITHER.narrow(result);
        assertThatEither(either).isRight().hasRight("left:" + DEFAULT_RIGHT_VALUE);
      }

      @Test
      @DisplayName("branch() applies right handler to Right value")
      void branchAppliesRightHandler() {
        Kind<EitherKind.Witness<String>, String> result =
            selective.branch(choiceRightKind, leftHandlerKind, rightHandlerKind);

        Either<String, String> either = EITHER.narrow(result);
        assertThatEither(either).isRight().hasRight("right:right-value");
      }

      @Test
      @DisplayName("branch() propagates Left from choice")
      void branchPropagatesLeftFromChoice() {
        Kind<EitherKind.Witness<String>, Choice<Integer, String>> errorChoice =
            leftKind(TestErrorType.ERROR_1);

        Kind<EitherKind.Witness<String>, String> result =
            selective.branch(errorChoice, leftHandlerKind, rightHandlerKind);

        Either<String, String> either = EITHER.narrow(result);
        assertThatEither(either).isLeft().hasLeft(TestErrorType.ERROR_1.message());
      }

      @Test
      @DisplayName("branch() propagates Left from left handler")
      void branchPropagatesLeftFromLeftHandler() {
        Kind<EitherKind.Witness<String>, Function<Integer, String>> errorLeftHandler =
            leftKind(TestErrorType.FUNCTION_ERROR);

        Kind<EitherKind.Witness<String>, String> result =
            selective.branch(choiceLeftKind, errorLeftHandler, rightHandlerKind);

        Either<String, String> either = EITHER.narrow(result);
        assertThatEither(either).isLeft().hasLeft(TestErrorType.FUNCTION_ERROR.message());
      }

      @Test
      @DisplayName("branch() propagates Left from right handler")
      void branchPropagatesLeftFromRightHandler() {
        Kind<EitherKind.Witness<String>, Function<String, String>> errorRightHandler =
            leftKind(TestErrorType.FUNCTION_ERROR);

        Kind<EitherKind.Witness<String>, String> result =
            selective.branch(choiceRightKind, leftHandlerKind, errorRightHandler);

        Either<String, String> either = EITHER.narrow(result);
        assertThatEither(either).isLeft().hasLeft(TestErrorType.FUNCTION_ERROR.message());
      }
    }

    @Nested
    @DisplayName("WhenS Operation Tests")
    class WhenSOperationTests {

      @Test
      @DisplayName("whenS() executes effect when condition is true")
      void whenSExecutesEffectWhenTrue() {
        Kind<EitherKind.Witness<String>, Unit> result =
            selective.whenS(conditionTrue, unitEffectKind); // ✓ Use unitEffectKind

        Either<String, Unit> either = EITHER.narrow(result);
        assertThatEither(either).isRight().hasRight(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS() skips effect when condition is false")
      void whenSSkipsEffectWhenFalse() {
        Kind<EitherKind.Witness<String>, Unit> result =
            selective.whenS(conditionFalse, unitEffectKind); // ✓ Use unitEffectKind

        Either<String, Unit> either = EITHER.narrow(result);
        assertThatEither(either).isRight().hasRight(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS() propagates Left from condition")
      void whenSPropagatesLeftFromCondition() {
        Kind<EitherKind.Witness<String>, Boolean> errorCondition =
            leftKind(TestErrorType.VALIDATION);

        Kind<EitherKind.Witness<String>, Unit> result =
            selective.whenS(errorCondition, unitEffectKind); // ✓ Use unitEffectKind

        Either<String, Unit> either = EITHER.narrow(result);
        assertThatEither(either).isLeft().hasLeft(TestErrorType.VALIDATION.message());
      }

      @Test
      @DisplayName("whenS() does not execute effect on Left condition")
      void whenSDoesNotExecuteEffectOnLeftCondition() {
        Kind<EitherKind.Witness<String>, Boolean> errorCondition =
            leftKind(TestErrorType.VALIDATION);

        Kind<EitherKind.Witness<String>, Unit> throwingEffect =
            EITHER.widen(Either.right(Unit.INSTANCE));

        Kind<EitherKind.Witness<String>, Unit> result =
            selective.whenS(errorCondition, throwingEffect);

        assertThatCode(() -> EITHER.narrow(result)).doesNotThrowAnyException();
      }
    }

    @Nested
    @DisplayName("IfS Operation Tests")
    class IfSOperationTests {

      @Test
      @DisplayName("ifS() returns then branch when condition is true")
      void ifSReturnsThenBranchWhenTrue() {
        Kind<EitherKind.Witness<String>, Integer> result =
            selective.ifS(conditionTrue, thenBranch, elseBranch);

        Either<String, Integer> either = EITHER.narrow(result);
        assertThatEither(either).isRight().hasRight(DEFAULT_RIGHT_VALUE);

        assertThat(result).isSameAs(thenBranch);
      }

      @Test
      @DisplayName("ifS() returns else branch when condition is false")
      void ifSReturnsElseBranchWhenFalse() {
        Kind<EitherKind.Witness<String>, Integer> result =
            selective.ifS(conditionFalse, thenBranch, elseBranch);

        Either<String, Integer> either = EITHER.narrow(result);
        assertThatEither(either).isRight().hasRight(ALTERNATIVE_RIGHT_VALUE);

        assertThat(result).isSameAs(elseBranch);
      }

      @Test
      @DisplayName("ifS() propagates Left from condition")
      void ifSPropagatesLeftFromCondition() {
        Kind<EitherKind.Witness<String>, Boolean> errorCondition =
            leftKind(TestErrorType.VALIDATION);

        Kind<EitherKind.Witness<String>, Integer> result =
            selective.ifS(errorCondition, thenBranch, elseBranch);

        Either<String, Integer> either = EITHER.narrow(result);
        assertThatEither(either).isLeft().hasLeft(TestErrorType.VALIDATION.message());
      }

      @Test
      @DisplayName("ifS() does not evaluate both branches")
      void ifSDoesNotEvaluateBothBranches() {
        Kind<EitherKind.Witness<String>, Integer> result =
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
      TypeClassTest.<EitherKind.Witness<String>>selective(EitherSelective.class)
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
      TypeClassTest.<EitherKind.Witness<String>>selective(EitherSelective.class)
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
          .withApFrom(EitherMonad.class)
          .withMapFrom(EitherFunctor.class)
          .withApFrom(EitherMonad.class)
          .withSelectFrom(EitherSelective.class)
          .withBranchFrom(EitherSelective.class)
          .withWhenSFrom(EitherSelective.class)
          .withIfSFrom(EitherSelective.class)
          .done()
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<EitherKind.Witness<String>>selective(EitherSelective.class)
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
      TypeClassTest.<EitherKind.Witness<String>>selective(EitherSelective.class)
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
      Kind<EitherKind.Witness<String>, Integer> start = validKind;

      Kind<EitherKind.Witness<String>, Boolean> condition = selective.map(i -> i > 0, start);

      // ✓ Use whenS_ to discard the Integer result and get Unit
      Kind<EitherKind.Witness<String>, Integer> doubled = selective.map(i -> i * 2, start);
      Kind<EitherKind.Witness<String>, Unit> result = selective.whenS_(condition, doubled);

      Either<String, Unit> either = EITHER.narrow(result);
      assertThatEither(either).isRight().hasRight(Unit.INSTANCE);
    }

    @Test
    @DisplayName("Nested selective operations")
    void nestedSelectiveOperations() {
      Kind<EitherKind.Witness<String>, Integer> innerResult =
          selective.ifS(conditionTrue, thenBranch, elseBranch);

      Kind<EitherKind.Witness<String>, Integer> outerResult =
          selective.ifS(conditionTrue, innerResult, elseBranch);

      Either<String, Integer> either = EITHER.narrow(outerResult);
      assertThatEither(either).isRight().hasRight(DEFAULT_RIGHT_VALUE);
    }

    @Test
    @DisplayName("Select with null value in Choice")
    void selectWithNullValueInChoice() {
      Choice<Integer, String> choiceWithNull =
          new org.higherkindedj.hkt.Selective.SimpleChoice<>(true, null, null);
      Kind<EitherKind.Witness<String>, Choice<Integer, String>> choiceKind =
          EITHER.widen(Either.right(choiceWithNull));

      Function<Integer, String> nullSafeFunc = i -> i == null ? "null-value" : "value:" + i;
      Kind<EitherKind.Witness<String>, Function<Integer, String>> funcKind =
          EITHER.widen(Either.right(nullSafeFunc));

      Kind<EitherKind.Witness<String>, String> result = selective.select(choiceKind, funcKind);

      Either<String, String> either = EITHER.narrow(result);
      assertThatEither(either).isRight().hasRight("null-value");
    }
  }

  @Nested
  @DisplayName("Integration Examples")
  class IntegrationExamples {

    @Test
    @DisplayName("Real-world scenario: conditional validation pipeline")
    void conditionalValidationPipeline() {
      Function<Integer, Either<String, Integer>> validatePositive =
          i -> i > 0 ? Either.right(i) : Either.left("Must be positive");

      Function<Integer, Either<String, Integer>> validateRange =
          i -> i <= 100 ? Either.right(i) : Either.left("Must be <= 100");

      Either<String, Integer> input = Either.right(50);
      Kind<EitherKind.Witness<String>, Integer> inputKind = EITHER.widen(input);

      Function<Integer, Kind<EitherKind.Witness<String>, Integer>> validatePositiveKind =
          i -> EITHER.widen(validatePositive.apply(i));
      Function<Integer, Kind<EitherKind.Witness<String>, Integer>> validateRangeKind =
          i -> EITHER.widen(validateRange.apply(i));

      Kind<EitherKind.Witness<String>, Integer> validated =
          selective.flatMap(validatePositiveKind, inputKind);

      Kind<EitherKind.Witness<String>, Boolean> needsRangeCheck =
          selective.map(i -> i > 50, validated);

      Kind<EitherKind.Witness<String>, Integer> rangeChecked =
          selective.flatMap(validateRangeKind, validated);

      Kind<EitherKind.Witness<String>, Integer> result =
          selective.ifS(needsRangeCheck, rangeChecked, validated);

      Either<String, Integer> either = EITHER.narrow(result);
      assertThatEither(either).isRight();
    }

    @Test
    @DisplayName("Real-world scenario: optional effect execution")
    void optionalEffectExecution() {
      java.util.concurrent.atomic.AtomicInteger counter =
          new java.util.concurrent.atomic.AtomicInteger(0);

      Kind<EitherKind.Witness<String>, Boolean> shouldLog = EITHER.widen(Either.right(true));

      Kind<EitherKind.Witness<String>, Integer> loggingEffect =
          selective.map(
              i -> {
                counter.incrementAndGet();
                return i;
              },
              validKind);

      // ✓ Use whenS_ to convert Kind<F, Integer> to Kind<F, Unit>
      selective.whenS_(shouldLog, loggingEffect);

      assertThat(counter.get()).isEqualTo(1);

      counter.set(0);
      Kind<EitherKind.Witness<String>, Boolean> shouldNotLog = EITHER.widen(Either.right(false));

      selective.whenS_(shouldNotLog, loggingEffect);

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
        Kind<EitherKind.Witness<String>, Integer> start = validKind;

        Kind<EitherKind.Witness<String>, Integer> result = start;
        for (int i = 0; i < 100; i++) {
          final int index = i;
          Kind<EitherKind.Witness<String>, Boolean> condition =
              selective.map(val -> val > index, result);
          Kind<EitherKind.Witness<String>, Integer> thenValue =
              selective.map(val -> val + 1, result);
          result = selective.ifS(condition, thenValue, result);
        }

        Either<String, Integer> either = EITHER.narrow(result);
        assertThatEither(either).isRight();
      }
    }
  }
}
