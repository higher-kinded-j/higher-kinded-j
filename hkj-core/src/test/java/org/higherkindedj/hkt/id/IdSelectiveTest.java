// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;

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
class IdSelectiveTest {

  private IdSelective selective;

  // Test data
  private static final Integer TEST_VALUE = 42;
  private static final String TEST_RESULT = "result";

  private Kind<Id.Witness, Integer> validKind;
  private Kind<Id.Witness, Choice<Integer, String>> choiceLeftKind;
  private Kind<Id.Witness, Choice<Integer, String>> choiceRightKind;
  private Kind<Id.Witness, Function<Integer, String>> selectFunctionKind;
  private Kind<Id.Witness, Function<Integer, String>> leftHandlerKind;
  private Kind<Id.Witness, Function<String, String>> rightHandlerKind;
  private Kind<Id.Witness, Boolean> conditionTrue;
  private Kind<Id.Witness, Boolean> conditionFalse;
  private Kind<Id.Witness, Unit> unitEffectKind;
  private Kind<Id.Witness, Integer> thenBranch;
  private Kind<Id.Witness, Integer> elseBranch;

  // Test functions
  private Function<Integer, String> selectFunction = i -> "selected:" + i;
  private Function<Integer, String> leftHandler = i -> "left:" + i;
  private Function<String, String> rightHandler = s -> "right:" + s;
  private Function<Integer, String> validMapper = Object::toString;

  @BeforeEach
  void setUp() {
    selective = IdSelective.instance();
    setUpTestData();
  }

  private void setUpTestData() {
    // Create base test Kind
    validKind = ID.widen(Id.of(TEST_VALUE));

    // Create Choice instances
    Choice<Integer, String> choiceLeft = Selective.left(TEST_VALUE);
    Choice<Integer, String> choiceRight = Selective.right(TEST_RESULT);
    choiceLeftKind = ID.widen(Id.of(choiceLeft));
    choiceRightKind = ID.widen(Id.of(choiceRight));

    // Create function handlers
    selectFunctionKind = ID.widen(Id.of(selectFunction));
    leftHandlerKind = ID.widen(Id.of(leftHandler));
    rightHandlerKind = ID.widen(Id.of(rightHandler));

    // Create boolean conditions
    conditionTrue = ID.widen(Id.of(true));
    conditionFalse = ID.widen(Id.of(false));

    // Create Unit effect for whenS
    unitEffectKind = ID.widen(Id.of(Unit.INSTANCE));

    // Create branch kinds for ifS
    thenBranch = validKind;
    elseBranch = ID.widen(Id.of(100));
  }

  @Nested
  @DisplayName("Complete Test Suite Using New API")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Selective test pattern")
    void runCompleteSelectiveTestPattern() {
      TypeClassTest.<Id.Witness>selective(IdSelective.class)
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
          .withLawsTesting(TEST_RESULT, validMapper, IdSelectiveTest::kindEquals)
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
      TypeClassTest.<Id.Witness>selective(IdSelective.class)
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
      TypeClassTest.<Id.Witness>selective(IdSelective.class)
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

    @Test
    @DisplayName("All select operations work correctly")
    void testSelectOperations() {
      new SelectOperationTests().selectAppliesFunctionToLeftValue();
      new SelectOperationTests().selectReturnsRightValue();
    }

    @Test
    @DisplayName("All branch operations work correctly")
    void testBranchOperations() {
      new BranchOperationTests().branchAppliesLeftHandler();
      new BranchOperationTests().branchAppliesRightHandler();
    }

    @Test
    @DisplayName("All whenS operations work correctly")
    void testWhenSOperations() {
      new WhenSOperationTests().whenSExecutesEffectWhenTrue();
      new WhenSOperationTests().whenSSkipsEffectWhenFalse();
    }

    @Test
    @DisplayName("All ifS operations work correctly")
    void testIfSOperations() {
      new IfSOperationTests().ifSReturnsThenBranchWhenTrue();
      new IfSOperationTests().ifSReturnsElseBranchWhenFalse();
    }

    @Nested
    @DisplayName("Select Operation Tests")
    class SelectOperationTests {

      @Test
      @DisplayName("select() applies function to Left value in Choice")
      void selectAppliesFunctionToLeftValue() {
        Kind<Id.Witness, String> result = selective.select(choiceLeftKind, selectFunctionKind);

        Id<String> id = ID.narrow(result);
        assertThat(id.value()).isEqualTo("selected:" + TEST_VALUE);
      }

      @Test
      @DisplayName("select() returns Right value when Choice is Right")
      void selectReturnsRightValue() {
        Kind<Id.Witness, String> result = selective.select(choiceRightKind, selectFunctionKind);

        Id<String> id = ID.narrow(result);
        assertThat(id.value()).isEqualTo(TEST_RESULT);
      }

      @Test
      @DisplayName("select() handles null Choice gracefully")
      void selectHandlesNullChoice() {
        Kind<Id.Witness, Choice<Integer, String>> nullChoice = ID.widen(Id.of(null));

        assertThatThrownBy(() -> selective.select(nullChoice, selectFunctionKind))
            .isInstanceOf(NullPointerException.class);
      }

      @Test
      @DisplayName("select() handles null Function gracefully")
      void selectHandlesNullFunction() {
        Kind<Id.Witness, Function<Integer, String>> nullFunc = ID.widen(Id.of(null));

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
        Kind<Id.Witness, String> result =
            selective.branch(choiceLeftKind, leftHandlerKind, rightHandlerKind);

        Id<String> id = ID.narrow(result);
        assertThat(id.value()).isEqualTo("left:" + TEST_VALUE);
      }

      @Test
      @DisplayName("branch() applies right handler to Right value")
      void branchAppliesRightHandler() {
        Kind<Id.Witness, String> result =
            selective.branch(choiceRightKind, leftHandlerKind, rightHandlerKind);

        Id<String> id = ID.narrow(result);
        assertThat(id.value()).isEqualTo("right:" + TEST_RESULT);
      }

      @Test
      @DisplayName("branch() handles null Choice gracefully")
      void branchHandlesNullChoice() {
        Kind<Id.Witness, Choice<Integer, String>> nullChoice = ID.widen(Id.of(null));

        assertThatThrownBy(() -> selective.branch(nullChoice, leftHandlerKind, rightHandlerKind))
            .isInstanceOf(NullPointerException.class);
      }

      @Test
      @DisplayName("branch() handles null left handler gracefully")
      void branchHandlesNullLeftHandler() {
        Kind<Id.Witness, Function<Integer, String>> nullHandler = ID.widen(Id.of(null));

        assertThatThrownBy(() -> selective.branch(choiceLeftKind, nullHandler, rightHandlerKind))
            .isInstanceOf(NullPointerException.class);
      }

      @Test
      @DisplayName("branch() handles null right handler gracefully")
      void branchHandlesNullRightHandler() {
        Kind<Id.Witness, Function<String, String>> nullHandler = ID.widen(Id.of(null));

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
        Kind<Id.Witness, Unit> result = selective.whenS(conditionTrue, unitEffectKind);

        Id<Unit> id = ID.narrow(result);
        assertThat(id.value()).isEqualTo(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS() skips effect when condition is false")
      void whenSSkipsEffectWhenFalse() {
        Kind<Id.Witness, Unit> result = selective.whenS(conditionFalse, unitEffectKind);

        Id<Unit> id = ID.narrow(result);
        assertThat(id.value()).isEqualTo(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS() handles null condition gracefully")
      void whenSHandlesNullCondition() {
        Kind<Id.Witness, Boolean> nullCond = ID.widen(Id.of(null));

        assertThatThrownBy(() -> selective.whenS(nullCond, unitEffectKind))
            .isInstanceOf(NullPointerException.class);
      }

      @Test
      @DisplayName("whenS() handles null effect gracefully")
      void whenSHandlesNullEffect() {
        Kind<Id.Witness, Unit> nullEffect = ID.widen(Id.of(null));

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
        Kind<Id.Witness, Integer> result = selective.ifS(conditionTrue, thenBranch, elseBranch);

        Id<Integer> id = ID.narrow(result);
        assertThat(id).isSameAs(ID.narrow(thenBranch));
        assertThat(id.value()).isEqualTo(TEST_VALUE);
      }

      @Test
      @DisplayName("ifS() returns else branch when condition is false")
      void ifSReturnsElseBranchWhenFalse() {
        Kind<Id.Witness, Integer> result = selective.ifS(conditionFalse, thenBranch, elseBranch);

        Id<Integer> id = ID.narrow(result);
        assertThat(id).isSameAs(ID.narrow(elseBranch));
        assertThat(id.value()).isEqualTo(100);
      }

      @Test
      @DisplayName("ifS() handles null condition gracefully")
      void ifSHandlesNullCondition() {
        Kind<Id.Witness, Boolean> nullCond = ID.widen(Id.of(null));

        assertThatThrownBy(() -> selective.ifS(nullCond, thenBranch, elseBranch))
            .isInstanceOf(NullPointerException.class);
      }

      @Test
      @DisplayName("ifS() does not evaluate both branches")
      void ifSDoesNotEvaluateBothBranches() {
        Kind<Id.Witness, Integer> result = selective.ifS(conditionTrue, thenBranch, elseBranch);
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
      TypeClassTest.<Id.Witness>selective(IdSelective.class)
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
      TypeClassTest.<Id.Witness>selective(IdSelective.class)
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
      TypeClassTest.<Id.Witness>selective(IdSelective.class)
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
      TypeClassTest.<Id.Witness>selective(IdSelective.class)
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
          .withLawsTesting(TEST_RESULT, validMapper, IdSelectiveTest::kindEquals)
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
      Kind<Id.Witness, Integer> start = validKind;

      Kind<Id.Witness, Boolean> condition = selective.map(i -> i > 0, start);
      Kind<Id.Witness, Integer> doubled = selective.map(i -> i * 2, start);
      Kind<Id.Witness, Unit> result = selective.whenS_(condition, doubled);

      Id<Unit> id = ID.narrow(result);
      assertThat(id.value()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("Nested selective operations")
    void nestedSelectiveOperations() {
      Kind<Id.Witness, Integer> innerResult = selective.ifS(conditionTrue, thenBranch, elseBranch);
      Kind<Id.Witness, Integer> outerResult = selective.ifS(conditionTrue, innerResult, elseBranch);

      Id<Integer> id = ID.narrow(outerResult);
      assertThat(id.value()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("Select with null value in Choice")
    void selectWithNullValueInChoice() {
      Choice<Integer, String> choiceWithNull = Selective.left(null);
      Kind<Id.Witness, Choice<Integer, String>> choiceKind = ID.widen(Id.of(choiceWithNull));

      Function<Integer, String> nullSafeFunc = i -> i == null ? "null-value" : "value:" + i;
      Kind<Id.Witness, Function<Integer, String>> funcKind = ID.widen(Id.of(nullSafeFunc));

      Kind<Id.Witness, String> result = selective.select(choiceKind, funcKind);

      Id<String> id = ID.narrow(result);
      assertThat(id.value()).isEqualTo("null-value");
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

      Id<Integer> input = Id.of(50);
      Kind<Id.Witness, Integer> inputKind = ID.widen(input);

      Function<Integer, Kind<Id.Witness, Integer>> validatePositiveKind =
          i -> ID.widen(validatePositive.apply(i));
      Function<Integer, Kind<Id.Witness, Integer>> validateRangeKind =
          i -> ID.widen(validateRange.apply(i));

      Kind<Id.Witness, Integer> validated = selective.flatMap(validatePositiveKind, inputKind);

      Kind<Id.Witness, Boolean> needsRangeCheck = selective.map(i -> i > 50, validated);

      Kind<Id.Witness, Integer> rangeChecked = selective.flatMap(validateRangeKind, validated);

      Kind<Id.Witness, Integer> result = selective.ifS(needsRangeCheck, rangeChecked, validated);

      Id<Integer> id = ID.narrow(result);
      assertThat(id.value()).isEqualTo(50);
    }

    @Test
    @DisplayName("Real-world scenario: optional effect execution")
    void optionalEffectExecution() {
      java.util.concurrent.atomic.AtomicInteger counter =
          new java.util.concurrent.atomic.AtomicInteger(0);

      Kind<Id.Witness, Boolean> shouldLog = ID.widen(Id.of(true));

      Kind<Id.Witness, Integer> loggingEffect =
          selective.map(
              i -> {
                counter.incrementAndGet();
                return i;
              },
              validKind);

      selective.whenS_(shouldLog, loggingEffect);
      assertThat(counter.get()).isEqualTo(1);

      counter.set(0);
      Kind<Id.Witness, Boolean> shouldNotLog = ID.widen(Id.of(false));
      selective.whenS_(shouldNotLog, loggingEffect);
      assertThat(counter.get()).isEqualTo(0);
    }
  }

  // Helper method for law testing
  private static boolean kindEquals(Kind<Id.Witness, ?> k1, Kind<Id.Witness, ?> k2) {
    Object v1 = ID.narrow(k1).value();
    Object v2 = ID.narrow(k2).value();

    if (v1 == null && v2 == null) return true;
    if (v1 == null || v2 == null) return false;

    return v1.equals(v2);
  }
}
