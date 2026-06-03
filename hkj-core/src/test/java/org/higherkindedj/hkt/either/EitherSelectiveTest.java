// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.laws.SelectiveLaws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("EitherSelective")
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
  private Kind<EitherKind.Witness<String>, Unit> unitEffectKind;
  private Kind<EitherKind.Witness<String>, Integer> thenBranch;
  private Kind<EitherKind.Witness<String>, Integer> elseBranch;

  @BeforeEach
  void setUpSelective() {
    selective = EitherSelective.instance();
    validateMonadFixtures();

    Choice<Integer, String> choiceLeft = Selective.left(DEFAULT_RIGHT_VALUE);
    Choice<Integer, String> choiceRight = Selective.right("right-value");
    choiceLeftKind = EITHER.widen(Either.right(choiceLeft));
    choiceRightKind = EITHER.widen(Either.right(choiceRight));

    // Function handlers — all must return the same type C = String
    Function<Integer, String> selectFunc = i -> "selected:" + i;
    Function<Integer, String> leftHandler = i -> "left:" + i;
    Function<String, String> rightHandler = s -> "right:" + s;

    selectFunctionKind = EITHER.widen(Either.right(selectFunc));
    leftHandlerKind = EITHER.widen(Either.right(leftHandler));
    rightHandlerKind = EITHER.widen(Either.right(rightHandler));

    conditionTrue = EITHER.widen(Either.right(true));
    conditionFalse = EITHER.widen(Either.right(false));

    unitEffectKind = EITHER.widen(Either.right(Unit.INSTANCE));

    thenBranch = validKind;
    elseBranch = rightKind(ALTERNATIVE_RIGHT_VALUE);
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "left-pure holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.either.EitherLawFixtures#values")
    void leftPure(Integer value) {
      SelectiveLaws.assertLeftPure(selective, value, selective.of(validMapper), equalityChecker);
    }

    @ParameterizedTest(name = "right-pure holds on value \"{0}\"")
    @MethodSource("strings")
    void rightPure(String value) {
      SelectiveLaws.assertRightPure(selective, value, selective.of(validMapper), equalityChecker);
    }

    static Stream<Arguments> strings() {
      return Stream.of(Arguments.of("a"), Arguments.of("hello"), Arguments.of(""));
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
        assertThatEither(result).isRight().hasRight("selected:" + DEFAULT_RIGHT_VALUE);
      }

      @Test
      @DisplayName("select() returns Right value when Choice is Right")
      void selectReturnsRightValue() {
        var result = selective.select(choiceRightKind, selectFunctionKind);
        assertThatEither(result).isRight().hasRight("right-value");
      }

      @Test
      @DisplayName("select() propagates Left from choice")
      void selectPropagatesLeftFromChoice() {
        Kind<EitherKind.Witness<String>, Choice<Integer, String>> errorChoice =
            leftKind(TestErrorType.ERROR_1);
        var result = selective.select(errorChoice, selectFunctionKind);
        assertThatEither(result).isLeft().hasLeft(TestErrorType.ERROR_1.message());
      }

      @Test
      @DisplayName("select() propagates Left from function")
      void selectPropagatesLeftFromFunction() {
        Kind<EitherKind.Witness<String>, Function<Integer, String>> errorFunc =
            leftKind(TestErrorType.FUNCTION_ERROR);
        var result = selective.select(choiceLeftKind, errorFunc);
        assertThatEither(result).isLeft().hasLeft(TestErrorType.FUNCTION_ERROR.message());
      }

      @Test
      @DisplayName("select() short-circuits on first error")
      void selectShortCircuitsOnFirstError() {
        Kind<EitherKind.Witness<String>, Choice<Integer, String>> errorChoice =
            leftKind(TestErrorType.ERROR_1);
        Kind<EitherKind.Witness<String>, Function<Integer, String>> errorFunc =
            leftKind(TestErrorType.ERROR_2);
        var result = selective.select(errorChoice, errorFunc);
        assertThatEither(result).isLeft().hasLeft(TestErrorType.ERROR_1.message());
      }
    }

    @Nested
    @DisplayName("Branch Operation Tests")
    class BranchOperationTests {

      @Test
      @DisplayName("branch() applies left handler to Left value")
      void branchAppliesLeftHandler() {
        var result = selective.branch(choiceLeftKind, leftHandlerKind, rightHandlerKind);
        assertThatEither(result).isRight().hasRight("left:" + DEFAULT_RIGHT_VALUE);
      }

      @Test
      @DisplayName("branch() applies right handler to Right value")
      void branchAppliesRightHandler() {
        var result = selective.branch(choiceRightKind, leftHandlerKind, rightHandlerKind);
        assertThatEither(result).isRight().hasRight("right:right-value");
      }

      @Test
      @DisplayName("branch() propagates Left from choice")
      void branchPropagatesLeftFromChoice() {
        Kind<EitherKind.Witness<String>, Choice<Integer, String>> errorChoice =
            leftKind(TestErrorType.ERROR_1);
        var result = selective.branch(errorChoice, leftHandlerKind, rightHandlerKind);
        assertThatEither(result).isLeft().hasLeft(TestErrorType.ERROR_1.message());
      }

      @Test
      @DisplayName("branch() propagates Left from left handler")
      void branchPropagatesLeftFromLeftHandler() {
        Kind<EitherKind.Witness<String>, Function<Integer, String>> errorLeftHandler =
            leftKind(TestErrorType.FUNCTION_ERROR);
        var result = selective.branch(choiceLeftKind, errorLeftHandler, rightHandlerKind);
        assertThatEither(result).isLeft().hasLeft(TestErrorType.FUNCTION_ERROR.message());
      }

      @Test
      @DisplayName("branch() propagates Left from right handler")
      void branchPropagatesLeftFromRightHandler() {
        Kind<EitherKind.Witness<String>, Function<String, String>> errorRightHandler =
            leftKind(TestErrorType.FUNCTION_ERROR);
        var result = selective.branch(choiceRightKind, leftHandlerKind, errorRightHandler);
        assertThatEither(result).isLeft().hasLeft(TestErrorType.FUNCTION_ERROR.message());
      }
    }

    @Nested
    @DisplayName("WhenS Operation Tests")
    class WhenSOperationTests {

      @Test
      @DisplayName("whenS() executes effect when condition is true")
      void whenSExecutesEffectWhenTrue() {
        var result = selective.whenS(conditionTrue, unitEffectKind);
        assertThatEither(result).isRight().hasRight(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS() skips effect when condition is false")
      void whenSSkipsEffectWhenFalse() {
        var result = selective.whenS(conditionFalse, unitEffectKind);
        assertThatEither(result).isRight().hasRight(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS() propagates Left from condition")
      void whenSPropagatesLeftFromCondition() {
        Kind<EitherKind.Witness<String>, Boolean> errorCondition =
            leftKind(TestErrorType.VALIDATION);
        var result = selective.whenS(errorCondition, unitEffectKind);
        assertThatEither(result).isLeft().hasLeft(TestErrorType.VALIDATION.message());
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

        assertThatEither(result).isRight().hasRight(DEFAULT_RIGHT_VALUE);
        assertThat(result).isSameAs(thenBranch);
      }

      @Test
      @DisplayName("ifS() returns else branch when condition is false")
      void ifSReturnsElseBranchWhenFalse() {
        Kind<EitherKind.Witness<String>, Integer> result =
            selective.ifS(conditionFalse, thenBranch, elseBranch);

        assertThatEither(result).isRight().hasRight(ALTERNATIVE_RIGHT_VALUE);
        assertThat(result).isSameAs(elseBranch);
      }

      @Test
      @DisplayName("ifS() propagates Left from condition")
      void ifSPropagatesLeftFromCondition() {
        Kind<EitherKind.Witness<String>, Boolean> errorCondition =
            leftKind(TestErrorType.VALIDATION);
        var result = selective.ifS(errorCondition, thenBranch, elseBranch);
        assertThatEither(result).isLeft().hasLeft(TestErrorType.VALIDATION.message());
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
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Chained selective operations")
    void chainedSelectiveOperations() {
      Kind<EitherKind.Witness<String>, Integer> start = validKind;

      Kind<EitherKind.Witness<String>, Boolean> condition = selective.map(i -> i > 0, start);

      Kind<EitherKind.Witness<String>, Integer> doubled = selective.map(i -> i * 2, start);
      Kind<EitherKind.Witness<String>, Unit> result = selective.whenS_(condition, doubled);

      assertThatEither(result).isRight().hasRight(Unit.INSTANCE);
    }

    @Test
    @DisplayName("Nested selective operations")
    void nestedSelectiveOperations() {
      Kind<EitherKind.Witness<String>, Integer> innerResult =
          selective.ifS(conditionTrue, thenBranch, elseBranch);

      Kind<EitherKind.Witness<String>, Integer> outerResult =
          selective.ifS(conditionTrue, innerResult, elseBranch);

      assertThatEither(outerResult).isRight().hasRight(DEFAULT_RIGHT_VALUE);
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

      assertThatEither(result).isRight();
    }

    @Test
    @DisplayName("Real-world scenario: optional effect execution")
    void optionalEffectExecution() {
      AtomicInteger counter = new AtomicInteger(0);

      Kind<EitherKind.Witness<String>, Boolean> shouldLog = EITHER.widen(Either.right(true));

      Kind<EitherKind.Witness<String>, Integer> loggingEffect =
          selective.map(
              i -> {
                counter.incrementAndGet();
                return i;
              },
              validKind);

      selective.whenS_(shouldLog, loggingEffect);

      assertThat(counter.get()).isEqualTo(1);

      counter.set(0);
      Kind<EitherKind.Witness<String>, Boolean> shouldNotLog = EITHER.widen(Either.right(false));

      selective.whenS_(shouldNotLog, loggingEffect);

      assertThat(counter.get()).isEqualTo(0);
    }
  }

  // ==========================================================================
  // Audit Issue #15: EitherSelective.whenS/ifS unbox potentially-null Boolean
  // ==========================================================================

  @Nested
  @DisplayName("Null Boolean Unboxing (audit issue #15)")
  class NullBooleanUnboxingTests {

    @Test
    @DisplayName("whenS with Right(null) Boolean should not NPE on unboxing")
    void whenSWithRightNullBooleanShouldNotNpe() {
      // Either.right(null) is valid — creates Right(null); unboxing the condition must not NPE.
      Kind<EitherKind.Witness<String>, Boolean> condRightNull = EITHER.widen(Either.right(null));
      Kind<EitherKind.Witness<String>, Unit> effect = EITHER.widen(Either.right(Unit.INSTANCE));

      assertThatThrownBy(() -> selective.whenS(condRightNull, effect))
          .isNotInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ifS with Right(null) Boolean should not NPE on unboxing")
    void ifSWithRightNullBooleanShouldNotNpe() {
      Kind<EitherKind.Witness<String>, Boolean> condRightNull = EITHER.widen(Either.right(null));
      Kind<EitherKind.Witness<String>, Integer> thenBranch = EITHER.widen(Either.right(1));
      Kind<EitherKind.Witness<String>, Integer> elseBranch = EITHER.widen(Either.right(2));

      assertThatThrownBy(() -> selective.ifS(condRightNull, thenBranch, elseBranch))
          .isNotInstanceOf(NullPointerException.class);
    }
  }
}
