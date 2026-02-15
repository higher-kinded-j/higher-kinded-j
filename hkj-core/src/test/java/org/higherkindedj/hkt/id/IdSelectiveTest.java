// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.id.IdAssert.assertThatId;

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

@DisplayName("IdSelective Complete Test Suite")
class IdSelectiveTest extends IdTestBase {

  private IdSelective selective;

  // Test data
  private static final String TEST_RESULT = "result";

  private Kind<IdKind.Witness, Choice<Integer, String>> choiceLeftKind;
  private Kind<IdKind.Witness, Choice<Integer, String>> choiceRightKind;
  private Kind<IdKind.Witness, Function<Integer, String>> selectFunctionKind;
  private Kind<IdKind.Witness, Function<Integer, String>> leftHandlerKind;
  private Kind<IdKind.Witness, Function<String, String>> rightHandlerKind;
  private Kind<IdKind.Witness, Boolean> conditionTrue;
  private Kind<IdKind.Witness, Boolean> conditionFalse;
  private Kind<IdKind.Witness, Unit> unitEffectKind;
  private Kind<IdKind.Witness, Integer> thenBranch;
  private Kind<IdKind.Witness, Integer> elseBranch;

  // Test functions
  private Function<Integer, String> selectFunction = i -> "selected:" + i;
  private Function<Integer, String> leftHandler = i -> "left:" + i;
  private Function<String, String> rightHandler = s -> "right:" + s;
  private Function<Integer, String> validMapper = Object::toString;

  @BeforeEach
  void setUpSelective() {
    selective = IdSelective.instance();
    setUpTestData();
  }

  private void setUpTestData() {
    // Create Choice instances
    Choice<Integer, String> choiceLeft = Selective.left(DEFAULT_VALUE);
    Choice<Integer, String> choiceRight = Selective.right(TEST_RESULT);
    choiceLeftKind = idOf(choiceLeft);
    choiceRightKind = idOf(choiceRight);

    // Create function handlers
    selectFunctionKind = idOf(selectFunction);
    leftHandlerKind = idOf(leftHandler);
    rightHandlerKind = idOf(rightHandler);

    // Create boolean conditions
    conditionTrue = idOf(true);
    conditionFalse = idOf(false);

    // Create Unit effect for whenS
    unitEffectKind = idOf(Unit.INSTANCE);

    // Create branch kinds for ifS
    thenBranch = validKind;
    elseBranch = idOf(100);
  }

  @Nested
  @DisplayName("Complete Test Suite Using New API")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Selective test pattern")
    void runCompleteSelectiveTestPattern() {
      TypeClassTest.<IdKind.Witness>selective(IdSelective.class)
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
          .withLawsTesting(TEST_RESULT, validMapper, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(IdMonad.class)
          .withApFrom(IdMonad.class)
          .withSelectFrom(IdSelective.class)
          .withBranchFrom(IdSelective.class)
          .withWhenSFrom(IdSelective.class)
          .withIfSFrom(IdSelective.class)
          .done()
          .testAll();
    }

    @Test
    @DisplayName("Selective testing - operations only")
    void selectiveTestingOperationsOnly() {
      TypeClassTest.<IdKind.Witness>selective(IdSelective.class)
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
      TypeClassTest.<IdKind.Witness>selective(IdSelective.class)
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
          .withMapFrom(IdMonad.class)
          .withApFrom(IdMonad.class)
          .withSelectFrom(IdSelective.class)
          .withBranchFrom(IdSelective.class)
          .withWhenSFrom(IdSelective.class)
          .withIfSFrom(IdSelective.class)
          .done()
          .testOperationsAndValidations();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(IdSelectiveTest.class);

      if (result.hasErrors()) {
        result.printReport();
        throw new AssertionError("Test structure validation failed");
      }
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
        var result = selective.select(choiceLeftKind, selectFunctionKind);

        assertThatId(result).hasValue("selected:" + DEFAULT_VALUE);
      }

      @Test
      @DisplayName("select() returns Right value when Choice is Right")
      void selectReturnsRightValue() {
        var result = selective.select(choiceRightKind, selectFunctionKind);

        assertThatId(result).hasValue(TEST_RESULT);
      }

      @Test
      @DisplayName("select() handles null Choice gracefully")
      void selectHandlesNullChoice() {
        Kind<IdKind.Witness, Choice<Integer, String>> nullChoice = idOf(null);

        assertThatThrownBy(() -> selective.select(nullChoice, selectFunctionKind))
            .isInstanceOf(NullPointerException.class);
      }

      @Test
      @DisplayName("select() handles null Function gracefully")
      void selectHandlesNullFunction() {
        Kind<IdKind.Witness, Function<Integer, String>> nullFunc = idOf(null);

        assertThatThrownBy(() -> selective.select(choiceLeftKind, nullFunc))
            .isInstanceOf(NullPointerException.class);
      }
    }

    @Nested
    @DisplayName("Branch Operation Tests")
    class BranchOperationTests {

      @Test
      @DisplayName("branch() applies left handler to Left value")
      void branchAppliesLeftHandler() {
        var result = selective.branch(choiceLeftKind, leftHandlerKind, rightHandlerKind);

        assertThatId(result).hasValue("left:" + DEFAULT_VALUE);
      }

      @Test
      @DisplayName("branch() applies right handler to Right value")
      void branchAppliesRightHandler() {
        var result = selective.branch(choiceRightKind, leftHandlerKind, rightHandlerKind);

        assertThatId(result).hasValue("right:" + TEST_RESULT);
      }

      @Test
      @DisplayName("branch() handles null Choice gracefully")
      void branchHandlesNullChoice() {
        Kind<IdKind.Witness, Choice<Integer, String>> nullChoice = idOf(null);

        assertThatThrownBy(() -> selective.branch(nullChoice, leftHandlerKind, rightHandlerKind))
            .isInstanceOf(NullPointerException.class);
      }

      @Test
      @DisplayName("branch() handles null left handler gracefully")
      void branchHandlesNullLeftHandler() {
        Kind<IdKind.Witness, Function<Integer, String>> nullHandler = idOf(null);

        assertThatThrownBy(() -> selective.branch(choiceLeftKind, nullHandler, rightHandlerKind))
            .isInstanceOf(NullPointerException.class);
      }

      @Test
      @DisplayName("branch() handles null right handler gracefully")
      void branchHandlesNullRightHandler() {
        Kind<IdKind.Witness, Function<String, String>> nullHandler = idOf(null);

        assertThatThrownBy(() -> selective.branch(choiceRightKind, leftHandlerKind, nullHandler))
            .isInstanceOf(NullPointerException.class);
      }
    }

    @Nested
    @DisplayName("WhenS Operation Tests")
    class WhenSOperationTests {

      @Test
      @DisplayName("whenS() executes effect when condition is true")
      void whenSExecutesEffectWhenTrue() {
        var result = selective.whenS(conditionTrue, unitEffectKind);

        assertThatId(result).hasValue(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS() skips effect when condition is false")
      void whenSSkipsEffectWhenFalse() {
        var result = selective.whenS(conditionFalse, unitEffectKind);

        assertThatId(result).hasValue(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS() handles null condition gracefully")
      void whenSHandlesNullCondition() {
        Kind<IdKind.Witness, Boolean> nullCond = idOf(null);

        assertThatThrownBy(() -> selective.whenS(nullCond, unitEffectKind))
            .isInstanceOf(NullPointerException.class);
      }

      @Test
      @DisplayName("whenS() handles null effect gracefully")
      void whenSHandlesNullEffect() {
        Kind<IdKind.Witness, Unit> nullEffect = idOf(null);

        assertThatThrownBy(() -> selective.whenS(conditionTrue, nullEffect))
            .isInstanceOf(NullPointerException.class);
      }
    }

    @Nested
    @DisplayName("IfS Operation Tests")
    class IfSOperationTests {

      @Test
      @DisplayName("ifS() returns then branch when condition is true")
      void ifSReturnsThenBranchWhenTrue() {
        var result = selective.ifS(conditionTrue, thenBranch, elseBranch);

        assertThat(narrowToId(result)).isSameAs(narrowToId(thenBranch));
        assertThatId(result).hasValue(DEFAULT_VALUE);
      }

      @Test
      @DisplayName("ifS() returns else branch when condition is false")
      void ifSReturnsElseBranchWhenFalse() {
        var result = selective.ifS(conditionFalse, thenBranch, elseBranch);

        assertThat(narrowToId(result)).isSameAs(narrowToId(elseBranch));
        assertThatId(result).hasValue(100);
      }

      @Test
      @DisplayName("ifS() handles null condition gracefully")
      void ifSHandlesNullCondition() {
        Kind<IdKind.Witness, Boolean> nullCond = idOf(null);

        assertThatThrownBy(() -> selective.ifS(nullCond, thenBranch, elseBranch))
            .isInstanceOf(NullPointerException.class);
      }

      @Test
      @DisplayName("ifS() does not evaluate both branches")
      void ifSDoesNotEvaluateBothBranches() {
        var result = selective.ifS(conditionTrue, thenBranch, elseBranch);
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
      TypeClassTest.<IdKind.Witness>selective(IdSelective.class)
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
      TypeClassTest.<IdKind.Witness>selective(IdSelective.class)
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
          .withMapFrom(IdMonad.class)
          .withApFrom(IdMonad.class)
          .withSelectFrom(IdSelective.class)
          .withBranchFrom(IdSelective.class)
          .withWhenSFrom(IdSelective.class)
          .withIfSFrom(IdSelective.class)
          .done()
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<IdKind.Witness>selective(IdSelective.class)
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
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<IdKind.Witness>selective(IdSelective.class)
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
          .withLawsTesting(TEST_RESULT, validMapper, equalityChecker)
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
      var start = validKind;

      var condition = selective.map(i -> i > 0, start);
      var doubled = selective.map(i -> i * 2, start);
      var result = selective.whenS_(condition, doubled);

      assertThatId(result).hasValue(Unit.INSTANCE);
    }

    @Test
    @DisplayName("Nested selective operations")
    void nestedSelectiveOperations() {
      var innerResult = selective.ifS(conditionTrue, thenBranch, elseBranch);
      var outerResult = selective.ifS(conditionTrue, innerResult, elseBranch);

      assertThatId(outerResult).hasValue(DEFAULT_VALUE);
    }

    @Test
    @DisplayName("Select with null value in Choice")
    void selectWithNullValueInChoice() {
      Choice<Integer, String> choiceWithNull = Selective.left(null);
      Kind<IdKind.Witness, Choice<Integer, String>> choiceKind = idOf(choiceWithNull);

      Function<Integer, String> nullSafeFunc = i -> i == null ? "null-value" : "value:" + i;
      Kind<IdKind.Witness, Function<Integer, String>> funcKind = idOf(nullSafeFunc);

      var result = selective.select(choiceKind, funcKind);

      assertThatId(result).hasValue("null-value");
    }

    @Test
    @DisplayName("toString representation")
    void toStringRepresentation() {
      assertThat(selective.toString()).isEqualTo("IdSelective");
    }
  }

  @Nested
  @DisplayName("Integration Examples")
  class IntegrationExamples {

    @Test
    @DisplayName("Real-world scenario: conditional validation pipeline")
    void conditionalValidationPipeline() {
      Function<Integer, Id<Integer>> validatePositive = i -> i > 0 ? Id.of(i) : Id.of(-1);
      Function<Integer, Id<Integer>> validateRange = i -> i <= 100 ? Id.of(i) : Id.of(-1);

      var inputKind = idOf(50);

      Function<Integer, Kind<IdKind.Witness, Integer>> validatePositiveKind =
          i -> wrapId(validatePositive.apply(i));
      Function<Integer, Kind<IdKind.Witness, Integer>> validateRangeKind =
          i -> wrapId(validateRange.apply(i));

      var validated = selective.flatMap(validatePositiveKind, inputKind);
      var needsRangeCheck = selective.map(i -> i > 50, validated);
      var rangeChecked = selective.flatMap(validateRangeKind, validated);
      var result = selective.ifS(needsRangeCheck, rangeChecked, validated);

      assertThatId(result).hasValue(50);
    }

    @Test
    @DisplayName("Real-world scenario: optional effect execution")
    void optionalEffectExecution() {
      AtomicInteger counter = new AtomicInteger(0);

      var shouldLog = idOf(true);

      var loggingEffect =
          selective.map(
              i -> {
                counter.incrementAndGet();
                return i;
              },
              validKind);

      selective.whenS_(shouldLog, loggingEffect);
      assertThat(counter.get()).isEqualTo(1);

      counter.set(0);
      var shouldNotLog = idOf(false);
      selective.whenS_(shouldNotLog, loggingEffect);
      assertThat(counter.get()).isEqualTo(0);
    }
  }
}
