// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;

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

@DisplayName("ReaderSelective Complete Test Suite")
class ReaderSelectiveTest extends ReaderTestBase {

  private ReaderSelective<TestConfig> selective;

  // Selective-specific test data
  private Kind<ReaderKind.Witness<TestConfig>, Choice<Integer, String>> choiceLeftKind;
  private Kind<ReaderKind.Witness<TestConfig>, Choice<Integer, String>> choiceRightKind;
  private Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> selectFunctionKind;
  private Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> leftHandlerKind;
  private Kind<ReaderKind.Witness<TestConfig>, Function<String, String>> rightHandlerKind;
  private Kind<ReaderKind.Witness<TestConfig>, Boolean> conditionTrue;
  private Kind<ReaderKind.Witness<TestConfig>, Boolean> conditionFalse;
  private Kind<ReaderKind.Witness<TestConfig>, Unit> unitEffectKind;
  private Kind<ReaderKind.Witness<TestConfig>, Integer> thenBranch;
  private Kind<ReaderKind.Witness<TestConfig>, Integer> elseBranch;

  @BeforeEach
  void setUpSelective() {
    selective = ReaderSelective.instance();
    validateMonadFixtures();
    setUpSelectiveFixtures();
  }

  private void setUpSelectiveFixtures() {
    // Create Choice instances
    Choice<Integer, String> choiceLeft = Selective.left(DEFAULT_MAX_CONNECTIONS);
    Choice<Integer, String> choiceRight = Selective.right("right-value");
    choiceLeftKind = READER.widen(Reader.constant(choiceLeft));
    choiceRightKind = READER.widen(Reader.constant(choiceRight));

    // Create function handlers - all must return the same type C = String
    Function<Integer, String> selectFunc = i -> "selected:" + i;
    Function<Integer, String> leftHandler = i -> "left:" + i;
    Function<String, String> rightHandler = s -> "right:" + s;

    selectFunctionKind = READER.widen(Reader.constant(selectFunc));
    leftHandlerKind = READER.widen(Reader.constant(leftHandler));
    rightHandlerKind = READER.widen(Reader.constant(rightHandler));

    // Create boolean conditions
    conditionTrue = READER.widen(Reader.constant(true));
    conditionFalse = READER.widen(Reader.constant(false));

    // Create Unit effect for whenS
    unitEffectKind = READER.widen(Reader.constant(Unit.INSTANCE));

    // Create branch kinds for ifS (these stay as Integer)
    thenBranch = validKind;
    elseBranch = READER.widen(Reader.constant(ALTERNATIVE_MAX_CONNECTIONS));
  }

  @Nested
  @DisplayName("Complete Test Suite Using New API")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Selective test pattern")
    void runCompleteSelectiveTestPattern() {
      TypeClassTest.<ReaderKind.Witness<TestConfig>>selective(ReaderSelective.class)
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
          .withLawsTesting("test-string", i -> "mapped:" + i, equalityChecker)
          .selectTests()
          .skipExceptions()
          .and()
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(ReaderFunctor.class)
          .withApFrom(ReaderApplicative.class)
          .withSelectFrom(ReaderSelective.class)
          .withBranchFrom(ReaderSelective.class)
          .withWhenSFrom(ReaderSelective.class)
          .withIfSFrom(ReaderSelective.class)
          .done()
          .testAll();
    }

    @Test
    @DisplayName("Selective testing - operations only")
    void selectiveTestingOperationsOnly() {
      TypeClassTest.<ReaderKind.Witness<TestConfig>>selective(ReaderSelective.class)
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
          .skipExceptions() //  Reader has lazy evaluation
          .test();
    }

    @Test
    @DisplayName("Quick smoke test - operations and validations")
    void quickSmokeTest() {
      TypeClassTest.<ReaderKind.Witness<TestConfig>>selective(ReaderSelective.class)
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
          .withMapFrom(ReaderFunctor.class)
          .withApFrom(ReaderApplicative.class)
          .withSelectFrom(ReaderSelective.class)
          .withBranchFrom(ReaderSelective.class)
          .withWhenSFrom(ReaderSelective.class)
          .withIfSFrom(ReaderSelective.class)
          .done()
          .testOperationsAndValidations();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(ReaderSelectiveTest.class);

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
        Kind<ReaderKind.Witness<TestConfig>, String> result =
            selective.select(choiceLeftKind, selectFunctionKind);

        Reader<TestConfig, String> reader = READER.narrow(result);
        String value = reader.run(TEST_CONFIG);
        assertThat(value).isEqualTo("selected:" + DEFAULT_MAX_CONNECTIONS);
      }

      @Test
      @DisplayName("select() returns Right value when Choice is Right")
      void selectReturnsRightValue() {
        Kind<ReaderKind.Witness<TestConfig>, String> result =
            selective.select(choiceRightKind, selectFunctionKind);

        Reader<TestConfig, String> reader = READER.narrow(result);
        String value = reader.run(TEST_CONFIG);
        assertThat(value).isEqualTo("right-value");
      }

      @Test
      @DisplayName("select() does not evaluate function for Right choice")
      void selectDoesNotEvaluateFunctionForRight() {
        AtomicInteger callCount = new AtomicInteger(0);

        Function<Integer, String> trackingFunction =
            i -> {
              callCount.incrementAndGet();
              return "selected:" + i;
            };

        Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> trackingFunctionKind =
            READER.widen(Reader.constant(trackingFunction));

        Kind<ReaderKind.Witness<TestConfig>, String> result =
            selective.select(choiceRightKind, trackingFunctionKind);

        Reader<TestConfig, String> reader = READER.narrow(result);
        reader.run(TEST_CONFIG);

        // Function should not be called when choice is Right
        assertThat(callCount.get()).isEqualTo(0);
      }

      @Test
      @DisplayName("select() evaluates function for Left choice")
      void selectEvaluatesFunctionForLeft() {
        AtomicInteger callCount = new AtomicInteger(0);

        Function<Integer, String> trackingFunction =
            i -> {
              callCount.incrementAndGet();
              return "selected:" + i;
            };

        Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> trackingFunctionKind =
            READER.widen(Reader.constant(trackingFunction));

        Kind<ReaderKind.Witness<TestConfig>, String> result =
            selective.select(choiceLeftKind, trackingFunctionKind);

        Reader<TestConfig, String> reader = READER.narrow(result);
        reader.run(TEST_CONFIG);

        // Function should be called when choice is Left
        assertThat(callCount.get()).isEqualTo(1);
      }

      @Test
      @DisplayName("select() accesses environment correctly")
      void selectAccessesEnvironmentCorrectly() {
        // Choice that depends on environment
        Reader<TestConfig, Choice<Integer, String>> envChoiceReader =
            Reader.of(
                cfg ->
                    cfg.maxConnections() > 5
                        ? Selective.left(cfg.maxConnections())
                        : Selective.right("too-small"));

        Kind<ReaderKind.Witness<TestConfig>, Choice<Integer, String>> envChoiceKind =
            READER.widen(envChoiceReader);

        Kind<ReaderKind.Witness<TestConfig>, String> result =
            selective.select(envChoiceKind, selectFunctionKind);

        Reader<TestConfig, String> reader = READER.narrow(result);

        // With TEST_CONFIG (maxConnections = 10 > 5), should apply function
        String value1 = reader.run(TEST_CONFIG);
        assertThat(value1).isEqualTo("selected:10");

        // With ALTERNATIVE_CONFIG (maxConnections = 5 <= 5), should use Right value
        String value2 = reader.run(ALTERNATIVE_CONFIG);
        assertThat(value2).isEqualTo("too-small");
      }
    }

    @Nested
    @DisplayName("Branch Operation Tests")
    class BranchOperationTests {

      @Test
      @DisplayName("branch() applies left handler to Left value")
      void branchAppliesLeftHandler() {
        Kind<ReaderKind.Witness<TestConfig>, String> result =
            selective.branch(choiceLeftKind, leftHandlerKind, rightHandlerKind);

        Reader<TestConfig, String> reader = READER.narrow(result);
        String value = reader.run(TEST_CONFIG);
        assertThat(value).isEqualTo("left:" + DEFAULT_MAX_CONNECTIONS);
      }

      @Test
      @DisplayName("branch() applies right handler to Right value")
      void branchAppliesRightHandler() {
        Kind<ReaderKind.Witness<TestConfig>, String> result =
            selective.branch(choiceRightKind, leftHandlerKind, rightHandlerKind);

        Reader<TestConfig, String> reader = READER.narrow(result);
        String value = reader.run(TEST_CONFIG);
        assertThat(value).isEqualTo("right:right-value");
      }

      @Test
      @DisplayName("branch() does not evaluate unused handler")
      void branchDoesNotEvaluateUnusedHandler() {
        AtomicInteger leftCalls = new AtomicInteger(0);
        AtomicInteger rightCalls = new AtomicInteger(0);

        Function<Integer, String> trackingLeftHandler =
            i -> {
              leftCalls.incrementAndGet();
              return "left:" + i;
            };

        Function<String, String> trackingRightHandler =
            s -> {
              rightCalls.incrementAndGet();
              return "right:" + s;
            };

        Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> trackingLeftKind =
            READER.widen(Reader.constant(trackingLeftHandler));
        Kind<ReaderKind.Witness<TestConfig>, Function<String, String>> trackingRightKind =
            READER.widen(Reader.constant(trackingRightHandler));

        // Test with Left choice - only left handler should be called
        Kind<ReaderKind.Witness<TestConfig>, String> resultLeft =
            selective.branch(choiceLeftKind, trackingLeftKind, trackingRightKind);
        READER.narrow(resultLeft).run(TEST_CONFIG);

        assertThat(leftCalls.get()).isEqualTo(1);
        assertThat(rightCalls.get()).isEqualTo(0);

        leftCalls.set(0);
        rightCalls.set(0);

        // Test with Right choice - only right handler should be called
        Kind<ReaderKind.Witness<TestConfig>, String> resultRight =
            selective.branch(choiceRightKind, trackingLeftKind, trackingRightKind);
        READER.narrow(resultRight).run(TEST_CONFIG);

        assertThat(leftCalls.get()).isEqualTo(0);
        assertThat(rightCalls.get()).isEqualTo(1);
      }

      @Test
      @DisplayName("branch() accesses environment in handlers")
      void branchAccessesEnvironmentInHandlers() {
        // Handlers that depend on environment
        Reader<TestConfig, Function<Integer, String>> envLeftHandler =
            Reader.of(cfg -> i -> "left:" + i + "@" + cfg.url());

        Reader<TestConfig, Function<String, String>> envRightHandler =
            Reader.of(cfg -> s -> "right:" + s + "@" + cfg.url());

        Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> envLeftKind =
            READER.widen(envLeftHandler);
        Kind<ReaderKind.Witness<TestConfig>, Function<String, String>> envRightKind =
            READER.widen(envRightHandler);

        Kind<ReaderKind.Witness<TestConfig>, String> result =
            selective.branch(choiceLeftKind, envLeftKind, envRightKind);

        Reader<TestConfig, String> reader = READER.narrow(result);
        String value = reader.run(TEST_CONFIG);
        assertThat(value).isEqualTo("left:10@" + DEFAULT_URL);
      }
    }

    @Nested
    @DisplayName("WhenS Operation Tests")
    class WhenSOperationTests {

      @Test
      @DisplayName("whenS() executes effect when condition is true")
      void whenSExecutesEffectWhenTrue() {
        Kind<ReaderKind.Witness<TestConfig>, Unit> result =
            selective.whenS(conditionTrue, unitEffectKind);

        Reader<TestConfig, Unit> reader = READER.narrow(result);
        Unit unit = reader.run(TEST_CONFIG);
        assertThat(unit).isEqualTo(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS() skips effect when condition is false")
      void whenSSkipsEffectWhenFalse() {
        Kind<ReaderKind.Witness<TestConfig>, Unit> result =
            selective.whenS(conditionFalse, unitEffectKind);

        Reader<TestConfig, Unit> reader = READER.narrow(result);
        Unit unit = reader.run(TEST_CONFIG);
        assertThat(unit).isEqualTo(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS() actually skips effect computation when false")
      void whenSActuallySkipsEffectWhenFalse() {
        AtomicInteger effectCalls = new AtomicInteger(0);

        Reader<TestConfig, Unit> trackingEffect =
            Reader.of(
                cfg -> {
                  effectCalls.incrementAndGet();
                  return Unit.INSTANCE;
                });

        Kind<ReaderKind.Witness<TestConfig>, Unit> trackingEffectKind =
            READER.widen(trackingEffect);

        // With true condition, effect should be executed
        Kind<ReaderKind.Witness<TestConfig>, Unit> resultTrue =
            selective.whenS(conditionTrue, trackingEffectKind);
        READER.narrow(resultTrue).run(TEST_CONFIG);
        assertThat(effectCalls.get()).isEqualTo(1);

        effectCalls.set(0);

        // With false condition, effect should NOT be executed
        Kind<ReaderKind.Witness<TestConfig>, Unit> resultFalse =
            selective.whenS(conditionFalse, trackingEffectKind);
        READER.narrow(resultFalse).run(TEST_CONFIG);
        assertThat(effectCalls.get()).isEqualTo(0);
      }

      @Test
      @DisplayName("whenS() accesses environment in condition")
      void whenSAccessesEnvironmentInCondition() {
        // Condition that depends on environment
        Reader<TestConfig, Boolean> envCondition = Reader.of(cfg -> cfg.maxConnections() > 5);

        Kind<ReaderKind.Witness<TestConfig>, Boolean> envConditionKind = READER.widen(envCondition);

        AtomicInteger effectCalls = new AtomicInteger(0);
        Reader<TestConfig, Unit> trackingEffect =
            Reader.of(
                cfg -> {
                  effectCalls.incrementAndGet();
                  return Unit.INSTANCE;
                });

        Kind<ReaderKind.Witness<TestConfig>, Unit> trackingEffectKind =
            READER.widen(trackingEffect);

        Kind<ReaderKind.Witness<TestConfig>, Unit> result =
            selective.whenS(envConditionKind, trackingEffectKind);

        Reader<TestConfig, Unit> reader = READER.narrow(result);

        // With TEST_CONFIG (maxConnections = 10 > 5), effect should execute
        reader.run(TEST_CONFIG);
        assertThat(effectCalls.get()).isEqualTo(1);

        effectCalls.set(0);

        // With ALTERNATIVE_CONFIG (maxConnections = 5 <= 5), effect should not execute
        reader.run(ALTERNATIVE_CONFIG);
        assertThat(effectCalls.get()).isEqualTo(0);
      }

      @Test
      @DisplayName("whenS_() discards non-Unit effect result")
      void whenS_DiscardsNonUnitEffectResult() {
        Kind<ReaderKind.Witness<TestConfig>, Integer> intEffect = READER.widen(Reader.constant(42));

        Kind<ReaderKind.Witness<TestConfig>, Unit> result =
            selective.whenS_(conditionTrue, intEffect);

        Reader<TestConfig, Unit> reader = READER.narrow(result);
        Unit unit = reader.run(TEST_CONFIG);
        assertThat(unit).isEqualTo(Unit.INSTANCE);
      }
    }

    @Nested
    @DisplayName("IfS Operation Tests")
    class IfSOperationTests {

      @Test
      @DisplayName("ifS() returns then branch when condition is true")
      void ifSReturnsThenBranchWhenTrue() {
        Kind<ReaderKind.Witness<TestConfig>, Integer> result =
            selective.ifS(conditionTrue, thenBranch, elseBranch);

        Reader<TestConfig, Integer> reader = READER.narrow(result);
        Integer value = reader.run(TEST_CONFIG);
        assertThat(value).isEqualTo(DEFAULT_MAX_CONNECTIONS);
      }

      @Test
      @DisplayName("ifS() returns else branch when condition is false")
      void ifSReturnsElseBranchWhenFalse() {
        Kind<ReaderKind.Witness<TestConfig>, Integer> result =
            selective.ifS(conditionFalse, thenBranch, elseBranch);

        Reader<TestConfig, Integer> reader = READER.narrow(result);
        Integer value = reader.run(TEST_CONFIG);
        assertThat(value).isEqualTo(ALTERNATIVE_MAX_CONNECTIONS);
      }

      @Test
      @DisplayName("ifS() does not evaluate unused branch")
      void ifSDoesNotEvaluateUnusedBranch() {
        AtomicInteger thenCalls = new AtomicInteger(0);
        AtomicInteger elseCalls = new AtomicInteger(0);

        Reader<TestConfig, Integer> trackingThen =
            Reader.of(
                cfg -> {
                  thenCalls.incrementAndGet();
                  return cfg.maxConnections();
                });

        Reader<TestConfig, Integer> trackingElse =
            Reader.of(
                cfg -> {
                  elseCalls.incrementAndGet();
                  return cfg.maxConnections() * 2;
                });

        Kind<ReaderKind.Witness<TestConfig>, Integer> trackingThenKind = READER.widen(trackingThen);
        Kind<ReaderKind.Witness<TestConfig>, Integer> trackingElseKind = READER.widen(trackingElse);

        // With true condition, only then branch should execute
        Kind<ReaderKind.Witness<TestConfig>, Integer> resultTrue =
            selective.ifS(conditionTrue, trackingThenKind, trackingElseKind);
        READER.narrow(resultTrue).run(TEST_CONFIG);

        assertThat(thenCalls.get()).isEqualTo(1);
        assertThat(elseCalls.get()).isEqualTo(0);

        thenCalls.set(0);
        elseCalls.set(0);

        // With false condition, only else branch should execute
        Kind<ReaderKind.Witness<TestConfig>, Integer> resultFalse =
            selective.ifS(conditionFalse, trackingThenKind, trackingElseKind);
        READER.narrow(resultFalse).run(TEST_CONFIG);

        assertThat(thenCalls.get()).isEqualTo(0);
        assertThat(elseCalls.get()).isEqualTo(1);
      }

      @Test
      @DisplayName("ifS() accesses environment in condition and branches")
      void ifSAccessesEnvironmentInConditionAndBranches() {
        // Condition that depends on environment
        Reader<TestConfig, Boolean> envCondition = Reader.of(cfg -> cfg.maxConnections() > 5);

        // Branches that depend on environment
        Reader<TestConfig, String> envThen = Reader.of(cfg -> "high:" + cfg.maxConnections());
        Reader<TestConfig, String> envElse = Reader.of(cfg -> "low:" + cfg.maxConnections());

        Kind<ReaderKind.Witness<TestConfig>, Boolean> envConditionKind = READER.widen(envCondition);
        Kind<ReaderKind.Witness<TestConfig>, String> envThenKind = READER.widen(envThen);
        Kind<ReaderKind.Witness<TestConfig>, String> envElseKind = READER.widen(envElse);

        Kind<ReaderKind.Witness<TestConfig>, String> result =
            selective.ifS(envConditionKind, envThenKind, envElseKind);

        Reader<TestConfig, String> reader = READER.narrow(result);

        // With TEST_CONFIG (maxConnections = 10 > 5), should use then branch
        String value1 = reader.run(TEST_CONFIG);
        assertThat(value1).isEqualTo("high:10");

        // With ALTERNATIVE_CONFIG (maxConnections = 5 <= 5), should use else branch
        String value2 = reader.run(ALTERNATIVE_CONFIG);
        assertThat(value2).isEqualTo("low:5");
      }
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<ReaderKind.Witness<TestConfig>>selective(ReaderSelective.class)
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
      TypeClassTest.<ReaderKind.Witness<TestConfig>>selective(ReaderSelective.class)
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
          .withMapFrom(ReaderFunctor.class)
          .withApFrom(ReaderApplicative.class)
          .withSelectFrom(ReaderSelective.class)
          .withBranchFrom(ReaderSelective.class)
          .withWhenSFrom(ReaderSelective.class)
          .withIfSFrom(ReaderSelective.class)
          .done()
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      // Reader is Lazy evaluation
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<ReaderKind.Witness<TestConfig>>selective(ReaderSelective.class)
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
          .withLawsTesting("test-string", i -> "mapped:" + i, equalityChecker)
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
      Kind<ReaderKind.Witness<TestConfig>, Integer> start = validKind;

      Kind<ReaderKind.Witness<TestConfig>, Boolean> condition = selective.map(i -> i > 0, start);

      Kind<ReaderKind.Witness<TestConfig>, Integer> doubled = selective.map(i -> i * 2, start);
      Kind<ReaderKind.Witness<TestConfig>, Unit> result = selective.whenS_(condition, doubled);

      Reader<TestConfig, Unit> reader = READER.narrow(result);
      Unit unit = reader.run(TEST_CONFIG);
      assertThat(unit).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("Nested selective operations")
    void nestedSelectiveOperations() {
      Kind<ReaderKind.Witness<TestConfig>, Integer> innerResult =
          selective.ifS(conditionTrue, thenBranch, elseBranch);

      Kind<ReaderKind.Witness<TestConfig>, Integer> outerResult =
          selective.ifS(conditionTrue, innerResult, elseBranch);

      Reader<TestConfig, Integer> reader = READER.narrow(outerResult);
      Integer value = reader.run(TEST_CONFIG);
      assertThat(value).isEqualTo(DEFAULT_MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("Select with null value in Choice")
    void selectWithNullValueInChoice() {
      Choice<Integer, String> choiceWithNull = Selective.left(null);
      Kind<ReaderKind.Witness<TestConfig>, Choice<Integer, String>> choiceKind =
          READER.widen(Reader.constant(choiceWithNull));

      Function<Integer, String> nullSafeFunc = i -> i == null ? "null-value" : "value:" + i;
      Kind<ReaderKind.Witness<TestConfig>, Function<Integer, String>> funcKind =
          READER.widen(Reader.constant(nullSafeFunc));

      Kind<ReaderKind.Witness<TestConfig>, String> result = selective.select(choiceKind, funcKind);

      Reader<TestConfig, String> reader = READER.narrow(result);
      String value = reader.run(TEST_CONFIG);
      assertThat(value).isEqualTo("null-value");
    }

    @Test
    @DisplayName("Multiple runs of same selective Reader produce consistent results")
    void multipleRunsProduceConsistentResults() {
      Kind<ReaderKind.Witness<TestConfig>, Integer> result =
          selective.ifS(conditionTrue, thenBranch, elseBranch);

      Reader<TestConfig, Integer> reader = READER.narrow(result);

      Integer value1 = reader.run(TEST_CONFIG);
      Integer value2 = reader.run(TEST_CONFIG);
      Integer value3 = reader.run(TEST_CONFIG);

      assertThat(value1).isEqualTo(value2).isEqualTo(value3).isEqualTo(DEFAULT_MAX_CONNECTIONS);
    }
  }

  @Nested
  @DisplayName("Integration Examples")
  class IntegrationExamples {

    @Test
    @DisplayName("Real-world scenario: conditional validation pipeline")
    void conditionalValidationPipeline() {
      // Validation based on environment
      Reader<TestConfig, Choice<String, Integer>> validateConnections =
          Reader.of(
              cfg -> {
                int maxConn = cfg.maxConnections();
                if (maxConn <= 0) {
                  return Selective.left("Must be positive");
                } else if (maxConn > 100) {
                  return Selective.left("Must be <= 100");
                } else {
                  return Selective.right(maxConn);
                }
              });

      Kind<ReaderKind.Witness<TestConfig>, Choice<String, Integer>> validateKind =
          READER.widen(validateConnections);

      Function<String, Integer> handleError = error -> -1;
      Kind<ReaderKind.Witness<TestConfig>, Function<String, Integer>> errorHandlerKind =
          READER.widen(Reader.constant(handleError));

      Kind<ReaderKind.Witness<TestConfig>, Integer> result =
          selective.select(validateKind, errorHandlerKind);

      Reader<TestConfig, Integer> reader = READER.narrow(result);

      // Valid config should return the connection count
      assertThat(reader.run(TEST_CONFIG)).isEqualTo(DEFAULT_MAX_CONNECTIONS);

      // Invalid config should trigger error handler
      TestConfig invalidConfig = new TestConfig(DEFAULT_URL, 150);
      assertThat(reader.run(invalidConfig)).isEqualTo(-1);
    }

    @Test
    @DisplayName("Real-world scenario: optional effect execution")
    void optionalEffectExecution() {
      AtomicInteger counter = new AtomicInteger(0);

      Reader<TestConfig, Boolean> shouldLog = Reader.of(cfg -> cfg.maxConnections() > 5);

      Reader<TestConfig, Integer> loggingEffect =
          Reader.of(
              cfg -> {
                counter.incrementAndGet();
                return cfg.maxConnections();
              });

      Kind<ReaderKind.Witness<TestConfig>, Boolean> shouldLogKind = READER.widen(shouldLog);
      Kind<ReaderKind.Witness<TestConfig>, Integer> loggingEffectKind = READER.widen(loggingEffect);

      Kind<ReaderKind.Witness<TestConfig>, Unit> result =
          selective.whenS_(shouldLogKind, loggingEffectKind);

      Reader<TestConfig, Unit> reader = READER.narrow(result);

      // With TEST_CONFIG (maxConnections = 10 > 5), should log
      reader.run(TEST_CONFIG);
      assertThat(counter.get()).isEqualTo(1);

      counter.set(0);

      // With ALTERNATIVE_CONFIG (maxConnections = 5 <= 5), should not log
      reader.run(ALTERNATIVE_CONFIG);
      assertThat(counter.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("Real-world scenario: environment-dependent branching")
    void environmentDependentBranching() {
      record DatabaseConfig(String url, int maxConnections, boolean usePooling) {}

      DatabaseConfig dbConfig = new DatabaseConfig(DEFAULT_URL, 10, true);

      Reader<DatabaseConfig, Choice<String, String>> connectionStrategy =
          Reader.of(
              cfg ->
                  cfg.usePooling()
                      ? Selective.left(cfg.url())
                      : Selective.right("direct:" + cfg.url()));

      Reader<DatabaseConfig, Function<String, String>> pooledHandler =
          Reader.of(cfg -> url -> "pooled[" + cfg.maxConnections() + "]:" + url);

      Reader<DatabaseConfig, Function<String, String>> directHandler =
          Reader.constant(directUrl -> directUrl.replace("direct:", "DIRECT:"));

      ReaderSelective<DatabaseConfig> dbSelective = ReaderSelective.instance();

      Kind<ReaderKind.Witness<DatabaseConfig>, Choice<String, String>> strategyKind =
          READER.widen(connectionStrategy);
      Kind<ReaderKind.Witness<DatabaseConfig>, Function<String, String>> pooledKind =
          READER.widen(pooledHandler);
      Kind<ReaderKind.Witness<DatabaseConfig>, Function<String, String>> directKind =
          READER.widen(directHandler);

      Kind<ReaderKind.Witness<DatabaseConfig>, String> result =
          dbSelective.branch(strategyKind, pooledKind, directKind);

      Reader<DatabaseConfig, String> reader = READER.narrow(result);

      // With pooling enabled
      String pooledResult = reader.run(dbConfig);
      assertThat(pooledResult).isEqualTo("pooled[10]:" + DEFAULT_URL);

      // With pooling disabled
      DatabaseConfig nPoolConfig = new DatabaseConfig(DEFAULT_URL, 10, false);
      String directResult = reader.run(nPoolConfig);
      assertThat(directResult).isEqualTo("DIRECT:" + DEFAULT_URL);
    }
  }

}
