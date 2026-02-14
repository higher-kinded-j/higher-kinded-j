// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.io.IOAssert.assertThatIO;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.concurrent.atomic.AtomicInteger;
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

@DisplayName("IOSelective Complete Test Suite")
class IOSelectiveTest extends IOTestBase {

  private IOSelective selective;

  // Selective-specific test data
  private Kind<IOKind.Witness, Choice<Integer, String>> choiceLeftKind;
  private Kind<IOKind.Witness, Choice<Integer, String>> choiceRightKind;
  private Kind<IOKind.Witness, Function<Integer, String>> selectFunctionKind;
  private Kind<IOKind.Witness, Function<Integer, String>> leftHandlerKind;
  private Kind<IOKind.Witness, Function<String, String>> rightHandlerKind;
  private Kind<IOKind.Witness, Boolean> conditionTrue;
  private Kind<IOKind.Witness, Boolean> conditionFalse;
  private Kind<IOKind.Witness, Unit> unitEffectKind;
  private Kind<IOKind.Witness, Integer> thenBranch;
  private Kind<IOKind.Witness, Integer> elseBranch;

  @BeforeEach
  void setUpSelective() {
    selective = IOSelective.INSTANCE;
    validateMonadFixtures();
    setUpSelectiveFixtures();
  }

  private void setUpSelectiveFixtures() {
    // Create Choice instances
    Choice<Integer, String> choiceLeft = Selective.left(DEFAULT_IO_VALUE);
    Choice<Integer, String> choiceRight = Selective.right("right-value");
    choiceLeftKind = IO_OP.widen(IO.delay(() -> choiceLeft));
    choiceRightKind = IO_OP.widen(IO.delay(() -> choiceRight));

    // Create function handlers - all must return the same type C = String
    Function<Integer, String> selectFunc = i -> "selected:" + i;
    Function<Integer, String> leftHandler = i -> "left:" + i;
    Function<String, String> rightHandler = s -> "right:" + s;

    selectFunctionKind = IO_OP.widen(IO.delay(() -> selectFunc));
    leftHandlerKind = IO_OP.widen(IO.delay(() -> leftHandler));
    rightHandlerKind = IO_OP.widen(IO.delay(() -> rightHandler));

    // Create boolean conditions
    conditionTrue = IO_OP.widen(IO.delay(() -> true));
    conditionFalse = IO_OP.widen(IO.delay(() -> false));

    // Create Unit effect for whenS
    unitEffectKind = IO_OP.widen(IO.delay(() -> Unit.INSTANCE));

    // Create branch kinds for ifS (these stay as Integer)
    thenBranch = validKind;
    elseBranch = ioKind(ALTERNATIVE_IO_VALUE);
  }

  @Nested
  @DisplayName("Complete Test Suite Using New API")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Selective test pattern")
    void runCompleteSelectiveTestPattern() {
      // IO has lazy evaluation, so we skip default exception tests
      // and provide our own in OperationTests nested classes
      TypeClassTest.<IOKind.Witness>selective(IOSelective.class)
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
          .skipExceptions()
          .and()
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(IOFunctor.class)
          .withApFrom(IOApplicative.class)
          .withSelectFrom(IOSelective.class)
          .withBranchFrom(IOSelective.class)
          .withWhenSFrom(IOSelective.class)
          .withIfSFrom(IOSelective.class)
          .testAll();
    }

    @Test
    @DisplayName("Selective testing - operations only")
    void selectiveTestingOperationsOnly() {
      TypeClassTest.<IOKind.Witness>selective(IOSelective.class)
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
          .skipExceptions()
          .test();
    }

    @Test
    @DisplayName("Quick smoke test - operations and validations")
    void quickSmokeTest() {
      TypeClassTest.<IOKind.Witness>selective(IOSelective.class)
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
          .withMapFrom(IOFunctor.class)
          .withApFrom(IOApplicative.class)
          .withSelectFrom(IOSelective.class)
          .withBranchFrom(IOSelective.class)
          .withWhenSFrom(IOSelective.class)
          .withIfSFrom(IOSelective.class)
          .done()
          .testOperationsAndValidations();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(IOSelectiveTest.class);

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
        Kind<IOKind.Witness, String> result = selective.select(choiceLeftKind, selectFunctionKind);

        assertThatIO(narrowToIO(result)).hasValue("selected:" + DEFAULT_IO_VALUE);
      }

      @Test
      @DisplayName("select() returns Right value when Choice is Right")
      void selectReturnsRightValue() {
        Kind<IOKind.Witness, String> result = selective.select(choiceRightKind, selectFunctionKind);

        assertThatIO(narrowToIO(result)).hasValue("right-value");
      }

      @Test
      @DisplayName("select() propagates exceptions from choice IO")
      void selectPropagatesExceptionsFromChoice() {
        RuntimeException exception = new RuntimeException("Choice error");
        Kind<IOKind.Witness, Choice<Integer, String>> errorChoice = failingIO(exception);

        Kind<IOKind.Witness, String> result = selective.select(errorChoice, selectFunctionKind);

        assertThatIO(narrowToIO(result))
            .throwsException(RuntimeException.class)
            .withMessage("Choice error");
      }

      @Test
      @DisplayName("select() propagates exceptions from function IO")
      void selectPropagatesExceptionsFromFunction() {
        RuntimeException exception = new RuntimeException("Function error");
        Kind<IOKind.Witness, Function<Integer, String>> errorFunc = failingIO(exception);

        Kind<IOKind.Witness, String> result = selective.select(choiceLeftKind, errorFunc);

        assertThatIO(narrowToIO(result))
            .throwsException(RuntimeException.class)
            .withMessage("Function error");
      }

      @Test
      @DisplayName("select() does not execute function when Choice is Right")
      void selectDoesNotExecuteFunctionWhenRight() {
        AtomicInteger functionExecutions = new AtomicInteger(0);
        Function<Integer, String> trackingFunc =
            i -> {
              functionExecutions.incrementAndGet();
              return "executed:" + i;
            };
        Kind<IOKind.Witness, Function<Integer, String>> trackingFuncKind =
            IO_OP.widen(IO.delay(() -> trackingFunc));

        Kind<IOKind.Witness, String> result = selective.select(choiceRightKind, trackingFuncKind);

        // Execute the result
        assertThatIO(narrowToIO(result)).hasValue("right-value");

        // Function should not have been executed
        assertThat(functionExecutions.get()).isZero();
      }

      @Test
      @DisplayName("select() maintains lazy evaluation")
      void selectMaintainsLazyEvaluation() {
        AtomicInteger choiceExecutions = new AtomicInteger(0);
        AtomicInteger functionExecutions = new AtomicInteger(0);

        Kind<IOKind.Witness, Choice<Integer, String>> lazyChoice =
            IO_OP.widen(
                IO.delay(
                    () -> {
                      choiceExecutions.incrementAndGet();
                      return Selective.left(DEFAULT_IO_VALUE);
                    }));

        Kind<IOKind.Witness, Function<Integer, String>> lazyFunc =
            IO_OP.widen(
                IO.delay(
                    () -> {
                      functionExecutions.incrementAndGet();
                      return i -> "lazy:" + i;
                    }));

        Kind<IOKind.Witness, String> result = selective.select(lazyChoice, lazyFunc);

        // Neither should have executed yet
        assertThat(choiceExecutions.get()).isZero();
        assertThat(functionExecutions.get()).isZero();

        // Execute the result
        assertThatIO(narrowToIO(result)).hasValue("lazy:" + DEFAULT_IO_VALUE);

        // Both should have executed once
        assertThat(choiceExecutions.get()).isEqualTo(1);
        assertThat(functionExecutions.get()).isEqualTo(1);
      }
    }

    @Nested
    @DisplayName("Branch Operation Tests")
    class BranchOperationTests {

      @Test
      @DisplayName("branch() applies left handler to Left value")
      void branchAppliesLeftHandler() {
        Kind<IOKind.Witness, String> result =
            selective.branch(choiceLeftKind, leftHandlerKind, rightHandlerKind);

        assertThatIO(narrowToIO(result)).hasValue("left:" + DEFAULT_IO_VALUE);
      }

      @Test
      @DisplayName("branch() applies right handler to Right value")
      void branchAppliesRightHandler() {
        Kind<IOKind.Witness, String> result =
            selective.branch(choiceRightKind, leftHandlerKind, rightHandlerKind);

        assertThatIO(narrowToIO(result)).hasValue("right:right-value");
      }

      @Test
      @DisplayName("branch() propagates exceptions from choice IO")
      void branchPropagatesExceptionsFromChoice() {
        RuntimeException exception = new RuntimeException("Choice error");
        Kind<IOKind.Witness, Choice<Integer, String>> errorChoice = failingIO(exception);

        Kind<IOKind.Witness, String> result =
            selective.branch(errorChoice, leftHandlerKind, rightHandlerKind);

        assertThatIO(narrowToIO(result))
            .throwsException(RuntimeException.class)
            .withMessage("Choice error");
      }

      @Test
      @DisplayName("branch() propagates exceptions from left handler")
      void branchPropagatesExceptionsFromLeftHandler() {
        RuntimeException exception = new RuntimeException("Left handler error");
        Kind<IOKind.Witness, Function<Integer, String>> errorLeftHandler = failingIO(exception);

        Kind<IOKind.Witness, String> result =
            selective.branch(choiceLeftKind, errorLeftHandler, rightHandlerKind);

        assertThatIO(narrowToIO(result))
            .throwsException(RuntimeException.class)
            .withMessage("Left handler error");
      }

      @Test
      @DisplayName("branch() propagates exceptions from right handler")
      void branchPropagatesExceptionsFromRightHandler() {
        RuntimeException exception = new RuntimeException("Right handler error");
        Kind<IOKind.Witness, Function<String, String>> errorRightHandler = failingIO(exception);

        Kind<IOKind.Witness, String> result =
            selective.branch(choiceRightKind, leftHandlerKind, errorRightHandler);

        assertThatIO(narrowToIO(result))
            .throwsException(RuntimeException.class)
            .withMessage("Right handler error");
      }

      @Test
      @DisplayName("branch() only executes relevant handler")
      void branchOnlyExecutesRelevantHandler() {
        AtomicInteger leftExecutions = new AtomicInteger(0);
        AtomicInteger rightExecutions = new AtomicInteger(0);

        Function<Integer, String> trackingLeftHandler =
            i -> {
              leftExecutions.incrementAndGet();
              return "left:" + i;
            };
        Function<String, String> trackingRightHandler =
            s -> {
              rightExecutions.incrementAndGet();
              return "right:" + s;
            };

        Kind<IOKind.Witness, Function<Integer, String>> leftKind =
            IO_OP.widen(IO.delay(() -> trackingLeftHandler));
        Kind<IOKind.Witness, Function<String, String>> rightKind =
            IO_OP.widen(IO.delay(() -> trackingRightHandler));

        // Test with Left choice - only left handler should execute
        Kind<IOKind.Witness, String> resultLeft =
            selective.branch(choiceLeftKind, leftKind, rightKind);
        assertThatIO(narrowToIO(resultLeft)).hasValue("left:" + DEFAULT_IO_VALUE);
        assertThat(leftExecutions.get()).isEqualTo(1);
        assertThat(rightExecutions.get()).isZero();

        // Reset and test with Right choice - only right handler should execute
        leftExecutions.set(0);
        rightExecutions.set(0);
        Kind<IOKind.Witness, String> resultRight =
            selective.branch(choiceRightKind, leftKind, rightKind);
        assertThatIO(narrowToIO(resultRight)).hasValue("right:right-value");
        assertThat(leftExecutions.get()).isZero();
        assertThat(rightExecutions.get()).isEqualTo(1);
      }

      @Test
      @DisplayName("branch() maintains lazy evaluation")
      void branchMaintainsLazyEvaluation() {
        AtomicInteger choiceExecutions = new AtomicInteger(0);

        Kind<IOKind.Witness, Choice<Integer, String>> lazyChoice =
            IO_OP.widen(
                IO.delay(
                    () -> {
                      choiceExecutions.incrementAndGet();
                      return Selective.left(DEFAULT_IO_VALUE);
                    }));

        Kind<IOKind.Witness, String> result =
            selective.branch(lazyChoice, leftHandlerKind, rightHandlerKind);

        // Should not have executed yet
        assertThat(choiceExecutions.get()).isZero();

        // Execute the result
        assertThatIO(narrowToIO(result)).hasValue("left:" + DEFAULT_IO_VALUE);

        // Should have executed once
        assertThat(choiceExecutions.get()).isEqualTo(1);
      }
    }

    @Nested
    @DisplayName("WhenS Operation Tests")
    class WhenSOperationTests {

      @Test
      @DisplayName("whenS() executes effect when condition is true")
      void whenSExecutesEffectWhenTrue() {
        AtomicInteger effectExecutions = new AtomicInteger(0);
        Kind<IOKind.Witness, Unit> trackingEffect =
            IO_OP.widen(
                IO.delay(
                    () -> {
                      effectExecutions.incrementAndGet();
                      return Unit.INSTANCE;
                    }));

        Kind<IOKind.Witness, Unit> result = selective.whenS(conditionTrue, trackingEffect);

        assertThatIO(narrowToIO(result)).hasValue(Unit.INSTANCE);
        assertThat(effectExecutions.get()).isEqualTo(1);
      }

      @Test
      @DisplayName("whenS() skips effect when condition is false")
      void whenSSkipsEffectWhenFalse() {
        AtomicInteger effectExecutions = new AtomicInteger(0);
        Kind<IOKind.Witness, Unit> trackingEffect =
            IO_OP.widen(
                IO.delay(
                    () -> {
                      effectExecutions.incrementAndGet();
                      return Unit.INSTANCE;
                    }));

        Kind<IOKind.Witness, Unit> result = selective.whenS(conditionFalse, trackingEffect);

        assertThatIO(narrowToIO(result)).hasValue(Unit.INSTANCE);
        assertThat(effectExecutions.get()).isZero();
      }

      @Test
      @DisplayName("whenS() propagates exceptions from condition")
      void whenSPropagatesExceptionsFromCondition() {
        RuntimeException exception = new RuntimeException("Condition error");
        Kind<IOKind.Witness, Boolean> errorCondition = failingIO(exception);

        Kind<IOKind.Witness, Unit> result = selective.whenS(errorCondition, unitEffectKind);

        assertThatIO(narrowToIO(result))
            .throwsException(RuntimeException.class)
            .withMessage("Condition error");
      }

      @Test
      @DisplayName("whenS() propagates exceptions from effect when true")
      void whenSPropagatesExceptionsFromEffect() {
        RuntimeException exception = new RuntimeException("Effect error");
        Kind<IOKind.Witness, Unit> errorEffect = failingIO(exception);

        Kind<IOKind.Witness, Unit> result = selective.whenS(conditionTrue, errorEffect);

        assertThatIO(narrowToIO(result))
            .throwsException(RuntimeException.class)
            .withMessage("Effect error");
      }

      @Test
      @DisplayName("whenS() does not execute effect on exception in condition")
      void whenSDoesNotExecuteEffectOnConditionError() {
        AtomicInteger effectExecutions = new AtomicInteger(0);
        RuntimeException exception = new RuntimeException("Condition error");
        Kind<IOKind.Witness, Boolean> errorCondition = failingIO(exception);

        Kind<IOKind.Witness, Unit> trackingEffect =
            IO_OP.widen(
                IO.delay(
                    () -> {
                      effectExecutions.incrementAndGet();
                      return Unit.INSTANCE;
                    }));

        Kind<IOKind.Witness, Unit> result = selective.whenS(errorCondition, trackingEffect);

        assertThatCode(() -> narrowToIO(result).unsafeRunSync())
            .isInstanceOf(RuntimeException.class);
        assertThat(effectExecutions.get()).isZero();
      }

      @Test
      @DisplayName("whenS() maintains lazy evaluation")
      void whenSMaintainsLazyEvaluation() {
        AtomicInteger conditionExecutions = new AtomicInteger(0);
        AtomicInteger effectExecutions = new AtomicInteger(0);

        Kind<IOKind.Witness, Boolean> lazyCondition =
            IO_OP.widen(
                IO.delay(
                    () -> {
                      conditionExecutions.incrementAndGet();
                      return true;
                    }));

        Kind<IOKind.Witness, Unit> lazyEffect =
            IO_OP.widen(
                IO.delay(
                    () -> {
                      effectExecutions.incrementAndGet();
                      return Unit.INSTANCE;
                    }));

        Kind<IOKind.Witness, Unit> result = selective.whenS(lazyCondition, lazyEffect);

        // Neither should have executed yet
        assertThat(conditionExecutions.get()).isZero();
        assertThat(effectExecutions.get()).isZero();

        // Execute the result
        assertThatIO(narrowToIO(result)).hasValue(Unit.INSTANCE);

        // Both should have executed once
        assertThat(conditionExecutions.get()).isEqualTo(1);
        assertThat(effectExecutions.get()).isEqualTo(1);
      }

      @Test
      @DisplayName("whenS() returns Unit.INSTANCE when condition is false")
      void whenSReturnsUnitInstanceWhenFalse() {
        Kind<IOKind.Witness, Unit> result = selective.whenS(conditionFalse, unitEffectKind);

        Unit resultValue = narrowToIO(result).unsafeRunSync();
        assertThat(resultValue).isSameAs(Unit.INSTANCE);
      }
    }

    @Nested
    @DisplayName("IfS Operation Tests")
    class IfSOperationTests {

      @Test
      @DisplayName("ifS() returns then branch when condition is true")
      void ifSReturnsThenBranchWhenTrue() {
        Kind<IOKind.Witness, Integer> result = selective.ifS(conditionTrue, thenBranch, elseBranch);

        assertThatIO(narrowToIO(result)).hasValue(DEFAULT_IO_VALUE);
      }

      @Test
      @DisplayName("ifS() returns else branch when condition is false")
      void ifSReturnsElseBranchWhenFalse() {
        Kind<IOKind.Witness, Integer> result =
            selective.ifS(conditionFalse, thenBranch, elseBranch);

        assertThatIO(narrowToIO(result)).hasValue(ALTERNATIVE_IO_VALUE);
      }

      @Test
      @DisplayName("ifS() propagates exceptions from condition")
      void ifSPropagatesExceptionsFromCondition() {
        RuntimeException exception = new RuntimeException("Condition error");
        Kind<IOKind.Witness, Boolean> errorCondition = failingIO(exception);

        Kind<IOKind.Witness, Integer> result =
            selective.ifS(errorCondition, thenBranch, elseBranch);

        assertThatIO(narrowToIO(result))
            .throwsException(RuntimeException.class)
            .withMessage("Condition error");
      }

      @Test
      @DisplayName("ifS() propagates exceptions from then branch")
      void ifSPropagatesExceptionsFromThenBranch() {
        RuntimeException exception = new RuntimeException("Then branch error");
        Kind<IOKind.Witness, Integer> errorThen = failingIO(exception);

        Kind<IOKind.Witness, Integer> result = selective.ifS(conditionTrue, errorThen, elseBranch);

        assertThatIO(narrowToIO(result))
            .throwsException(RuntimeException.class)
            .withMessage("Then branch error");
      }

      @Test
      @DisplayName("ifS() propagates exceptions from else branch")
      void ifSPropagatesExceptionsFromElseBranch() {
        RuntimeException exception = new RuntimeException("Else branch error");
        Kind<IOKind.Witness, Integer> errorElse = failingIO(exception);

        Kind<IOKind.Witness, Integer> result = selective.ifS(conditionFalse, thenBranch, errorElse);

        assertThatIO(narrowToIO(result))
            .throwsException(RuntimeException.class)
            .withMessage("Else branch error");
      }

      @Test
      @DisplayName("ifS() only executes selected branch")
      void ifSOnlyExecutesSelectedBranch() {
        AtomicInteger thenExecutions = new AtomicInteger(0);
        AtomicInteger elseExecutions = new AtomicInteger(0);

        Kind<IOKind.Witness, Integer> trackingThen =
            IO_OP.widen(
                IO.delay(
                    () -> {
                      thenExecutions.incrementAndGet();
                      return DEFAULT_IO_VALUE;
                    }));

        Kind<IOKind.Witness, Integer> trackingElse =
            IO_OP.widen(
                IO.delay(
                    () -> {
                      elseExecutions.incrementAndGet();
                      return ALTERNATIVE_IO_VALUE;
                    }));

        // Test with true condition - only then branch should execute
        Kind<IOKind.Witness, Integer> resultTrue =
            selective.ifS(conditionTrue, trackingThen, trackingElse);
        assertThatIO(narrowToIO(resultTrue)).hasValue(DEFAULT_IO_VALUE);
        assertThat(thenExecutions.get()).isEqualTo(1);
        assertThat(elseExecutions.get()).isZero();

        // Reset and test with false condition - only else branch should execute
        thenExecutions.set(0);
        elseExecutions.set(0);
        Kind<IOKind.Witness, Integer> resultFalse =
            selective.ifS(conditionFalse, trackingThen, trackingElse);
        assertThatIO(narrowToIO(resultFalse)).hasValue(ALTERNATIVE_IO_VALUE);
        assertThat(thenExecutions.get()).isZero();
        assertThat(elseExecutions.get()).isEqualTo(1);
      }

      @Test
      @DisplayName("ifS() maintains lazy evaluation")
      void ifSMaintainsLazyEvaluation() {
        AtomicInteger conditionExecutions = new AtomicInteger(0);
        AtomicInteger branchExecutions = new AtomicInteger(0);

        Kind<IOKind.Witness, Boolean> lazyCondition =
            IO_OP.widen(
                IO.delay(
                    () -> {
                      conditionExecutions.incrementAndGet();
                      return true;
                    }));

        Kind<IOKind.Witness, Integer> lazyBranch =
            IO_OP.widen(
                IO.delay(
                    () -> {
                      branchExecutions.incrementAndGet();
                      return DEFAULT_IO_VALUE;
                    }));

        Kind<IOKind.Witness, Integer> result = selective.ifS(lazyCondition, lazyBranch, elseBranch);

        // Neither should have executed yet
        assertThat(conditionExecutions.get()).isZero();
        assertThat(branchExecutions.get()).isZero();

        // Execute the result
        assertThatIO(narrowToIO(result)).hasValue(DEFAULT_IO_VALUE);

        // Both should have executed once
        assertThat(conditionExecutions.get()).isEqualTo(1);
        assertThat(branchExecutions.get()).isEqualTo(1);
      }
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<IOKind.Witness>selective(IOSelective.class)
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
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<IOKind.Witness>selective(IOSelective.class)
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
          .withMapFrom(IOFunctor.class)
          .withApFrom(IOApplicative.class)
          .withSelectFrom(IOSelective.class)
          .withBranchFrom(IOSelective.class)
          .withWhenSFrom(IOSelective.class)
          .withIfSFrom(IOSelective.class)
          .done()
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      // Note: Generic exception propagation tests don't work with IO's lazy evaluation
      // IO-specific exception tests are covered in the Operation Tests nested classes
      // which properly execute the IO to trigger exceptions
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<IOKind.Witness>selective(IOSelective.class)
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
      Kind<IOKind.Witness, Integer> start = validKind;

      Kind<IOKind.Witness, Boolean> condition = selective.map(i -> i > 0, start);

      Kind<IOKind.Witness, Integer> doubled = selective.map(i -> i * 2, start);
      Kind<IOKind.Witness, Unit> result = selective.whenS_(condition, doubled);

      assertThatIO(narrowToIO(result)).hasValue(Unit.INSTANCE);
    }

    @Test
    @DisplayName("Nested selective operations")
    void nestedSelectiveOperations() {
      Kind<IOKind.Witness, Integer> innerResult =
          selective.ifS(conditionTrue, thenBranch, elseBranch);

      Kind<IOKind.Witness, Integer> outerResult =
          selective.ifS(conditionTrue, innerResult, elseBranch);

      assertThatIO(narrowToIO(outerResult)).hasValue(DEFAULT_IO_VALUE);
    }

    @Test
    @DisplayName("Select with null value in Choice")
    void selectWithNullValueInChoice() {
      Choice<Integer, String> choiceWithNull = Selective.left(null);
      Kind<IOKind.Witness, Choice<Integer, String>> choiceKind =
          IO_OP.widen(IO.delay(() -> choiceWithNull));

      Function<Integer, String> nullSafeFunc = i -> i == null ? "null-value" : "value:" + i;
      Kind<IOKind.Witness, Function<Integer, String>> funcKind =
          IO_OP.widen(IO.delay(() -> nullSafeFunc));

      Kind<IOKind.Witness, String> result = selective.select(choiceKind, funcKind);

      assertThatIO(narrowToIO(result)).hasValue("null-value");
    }

    @Test
    @DisplayName("whenS_ discards non-Unit result correctly")
    void whenSDiscardsNonUnitResult() {
      AtomicInteger counter = new AtomicInteger(0);
      Kind<IOKind.Witness, Integer> effect =
          IO_OP.widen(
              IO.delay(
                  () -> {
                    counter.incrementAndGet();
                    return 999; // This should be discarded
                  }));

      Kind<IOKind.Witness, Unit> result = selective.whenS_(conditionTrue, effect);

      Unit resultValue = narrowToIO(result).unsafeRunSync();
      assertThat(resultValue).isSameAs(Unit.INSTANCE);
      assertThat(counter.get()).isEqualTo(1); // Effect was executed
    }

    @Test
    @DisplayName("Complex nested conditions with lazy evaluation")
    void complexNestedConditionsWithLazyEvaluation() {
      AtomicInteger executions = new AtomicInteger(0);

      Kind<IOKind.Witness, Boolean> dynamicCondition =
          IO_OP.widen(IO.delay(() -> executions.incrementAndGet() < 2));

      Kind<IOKind.Witness, Integer> branch1 = IO_OP.widen(IO.delay(() -> 1));
      Kind<IOKind.Witness, Integer> branch2 = IO_OP.widen(IO.delay(() -> 2));

      Kind<IOKind.Witness, Integer> firstChoice = selective.ifS(dynamicCondition, branch1, branch2);
      Kind<IOKind.Witness, Integer> secondChoice =
          selective.ifS(dynamicCondition, branch1, branch2);

      // Execute both
      Integer first = narrowToIO(firstChoice).unsafeRunSync();
      Integer second = narrowToIO(secondChoice).unsafeRunSync();

      assertThat(first).isEqualTo(1);
      assertThat(second).isEqualTo(2);
      assertThat(executions.get()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("Integration Examples")
  class IntegrationExamples {

    @Test
    @DisplayName("Real-world scenario: conditional logging pipeline")
    void conditionalLoggingPipeline() {
      StringBuilder log = new StringBuilder();
      AtomicInteger processedCount = new AtomicInteger(0);

      IO<Boolean> shouldLog = IO.delay(() -> true);
      IO<Integer> processData =
          IO.delay(
              () -> {
                int count = processedCount.incrementAndGet();
                return count;
              });

      IO<Unit> logEffect =
          IO.delay(
              () -> {
                log.append("Processing complete. Count: ")
                    .append(processedCount.get())
                    .append("; ");
                return Unit.INSTANCE;
              });

      Kind<IOKind.Witness, Boolean> shouldLogKind = IO_OP.widen(shouldLog);
      Kind<IOKind.Witness, Integer> processKind = IO_OP.widen(processData);
      Kind<IOKind.Witness, Unit> logKind = IO_OP.widen(logEffect);

      // Process and conditionally log
      Kind<IOKind.Witness, Unit> pipeline = selective.whenS_(shouldLogKind, processKind);
      Kind<IOKind.Witness, Unit> withLogging = selective.whenS(shouldLogKind, logKind);

      // Execute pipeline
      narrowToIO(pipeline).unsafeRunSync();
      narrowToIO(withLogging).unsafeRunSync();

      assertThat(processedCount.get()).isEqualTo(1);
      assertThat(log.toString()).contains("Processing complete");
    }

    @Test
    @DisplayName("Real-world scenario: optional effect execution")
    void optionalEffectExecution() {
      AtomicInteger sideEffectCounter = new AtomicInteger(0);

      Kind<IOKind.Witness, Boolean> shouldExecute = IO_OP.widen(IO.delay(() -> true));
      Kind<IOKind.Witness, Boolean> shouldNotExecute = IO_OP.widen(IO.delay(() -> false));

      Kind<IOKind.Witness, Integer> sideEffect =
          IO_OP.widen(
              IO.delay(
                  () -> {
                    sideEffectCounter.incrementAndGet();
                    return 42;
                  }));

      // Execute when condition is true
      selective.whenS_(shouldExecute, sideEffect);
      narrowToIO(selective.whenS_(shouldExecute, sideEffect)).unsafeRunSync();

      assertThat(sideEffectCounter.get()).isEqualTo(1);

      // Reset and verify no execution when false
      sideEffectCounter.set(0);
      narrowToIO(selective.whenS_(shouldNotExecute, sideEffect)).unsafeRunSync();

      assertThat(sideEffectCounter.get()).isZero();
    }

    @Test
    @DisplayName("Real-world scenario: validation with selective error handling")
    void validationWithSelectiveErrorHandling() {
      IO<Integer> input = IO.delay(() -> 50);
      Kind<IOKind.Witness, Integer> inputKind = IO_OP.widen(input);

      // Validate range
      Kind<IOKind.Witness, Boolean> isValid = selective.map(i -> i > 0 && i <= 100, inputKind);

      // Valid branch: process the value
      Kind<IOKind.Witness, Integer> processedKind = selective.map(i -> i * 2, inputKind);

      // Invalid branch: return default
      Kind<IOKind.Witness, Integer> defaultKind = IO_OP.widen(IO.delay(() -> 0));

      Kind<IOKind.Witness, Integer> result = selective.ifS(isValid, processedKind, defaultKind);

      assertThatIO(narrowToIO(result)).hasValue(100);
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("select() validates null choice Kind")
    void selectValidatesNullChoiceKind() {
      assertThatThrownBy(() -> selective.select(null, selectFunctionKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("choice");
    }

    @Test
    @DisplayName("select() validates null function Kind")
    void selectValidatesNullFunctionKind() {
      assertThatThrownBy(() -> selective.select(choiceLeftKind, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("function");
    }

    @Test
    @DisplayName("branch() validates null arguments")
    void branchValidatesNullArguments() {
      assertThatThrownBy(() -> selective.branch(null, leftHandlerKind, rightHandlerKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("choice");

      assertThatThrownBy(() -> selective.branch(choiceLeftKind, null, rightHandlerKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("leftHandler");

      assertThatThrownBy(() -> selective.branch(choiceLeftKind, leftHandlerKind, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("rightHandler");
    }

    @Test
    @DisplayName("whenS() validates null arguments")
    void whenSValidatesNullArguments() {
      assertThatThrownBy(() -> selective.whenS(null, unitEffectKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("condition");

      assertThatThrownBy(() -> selective.whenS(conditionTrue, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("effect");
    }

    @Test
    @DisplayName("ifS() validates null arguments")
    void ifSValidatesNullArguments() {
      assertThatThrownBy(() -> selective.ifS(null, thenBranch, elseBranch))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("condition");

      assertThatThrownBy(() -> selective.ifS(conditionTrue, null, elseBranch))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("thenBranch");

      assertThatThrownBy(() -> selective.ifS(conditionTrue, thenBranch, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("elseBranch");
    }

    @Test
    @DisplayName("whenS_() validates null arguments")
    void whenS_ValidatesNullArguments() {
      assertThatThrownBy(() -> selective.whenS_(null, validKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("condition");

      assertThatThrownBy(() -> selective.whenS_(conditionTrue, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("effect");
    }
  }
}
