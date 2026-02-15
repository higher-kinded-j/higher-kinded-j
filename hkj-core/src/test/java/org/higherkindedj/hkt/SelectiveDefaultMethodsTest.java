// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherSelective;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Selective Default Methods Tests")
class SelectiveDefaultMethodsTest {

  private EitherSelective<String> selective;

  /**
   * A minimal Selective implementation for testing default methods. This implementation only
   * provides the abstract select method and relies entirely on default implementations for branch,
   * whenS, and ifS. This is needed because EitherSelective overrides those methods.
   */
  private static class DefaultMethodsSelective<L> implements Selective<EitherKind.Witness<L>> {

    @Override
    public <A> Kind<EitherKind.Witness<L>, A> of(@Nullable A value) {
      return EITHER.widen(Either.right(value));
    }

    @Override
    public <A, B> Kind<EitherKind.Witness<L>, B> map(
        Function<? super A, ? extends @Nullable B> f, Kind<EitherKind.Witness<L>, A> fa) {
      Either<L, A> either = EITHER.narrow(fa);
      return EITHER.widen(either.map(f));
    }

    @Override
    public <A, B> Kind<EitherKind.Witness<L>, B> ap(
        Kind<EitherKind.Witness<L>, ? extends Function<A, B>> ff,
        Kind<EitherKind.Witness<L>, A> fa) {
      Either<L, ? extends Function<A, B>> eitherF = EITHER.narrow(ff);
      Either<L, A> eitherA = EITHER.narrow(fa);
      Either<L, B> result =
          eitherF.isLeft()
              ? Either.left(eitherF.getLeft())
              : (eitherA.isLeft()
                  ? Either.left(eitherA.getLeft())
                  : Either.right(eitherF.getRight().apply(eitherA.getRight())));
      return EITHER.widen(result);
    }

    @Override
    public <A, B> Kind<EitherKind.Witness<L>, B> select(
        Kind<EitherKind.Witness<L>, Choice<A, B>> fab,
        Kind<EitherKind.Witness<L>, Function<A, B>> ff) {
      Either<L, Choice<A, B>> eitherChoice = EITHER.narrow(fab);
      Either<L, Function<A, B>> eitherFunc = EITHER.narrow(ff);

      if (eitherChoice.isLeft()) {
        return EITHER.widen(Either.left(eitherChoice.getLeft()));
      }

      Choice<A, B> choice = eitherChoice.getRight();
      if (choice.isRight()) {
        return EITHER.widen(Either.right(choice.getRight()));
      }

      // Left case - apply the function
      if (eitherFunc.isLeft()) {
        return EITHER.widen(Either.left(eitherFunc.getLeft()));
      }
      return EITHER.widen(Either.right(eitherFunc.getRight().apply(choice.getLeft())));
    }
  }

  @BeforeEach
  void setUp() {
    selective = EitherSelective.instance();
  }

  @Nested
  @DisplayName("whenS Operation Tests")
  class WhenSOperationTests {

    @Test
    @DisplayName("whenS should execute effect when condition is true")
    void whenSExecutesOnTrue() {
      Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(true));
      Kind<EitherKind.Witness<String>, Unit> effect = EITHER.widen(Either.right(Unit.INSTANCE));

      Kind<EitherKind.Witness<String>, Unit> result = selective.whenS(condition, effect);

      Either<String, Unit> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS should skip effect when condition is false")
    void whenSSkipsOnFalse() {
      Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(false));
      Kind<EitherKind.Witness<String>, Unit> effect = EITHER.widen(Either.right(Unit.INSTANCE));

      Kind<EitherKind.Witness<String>, Unit> result = selective.whenS(condition, effect);

      Either<String, Unit> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS should propagate error from condition")
    void whenSPropagatesConditionError() {
      Kind<EitherKind.Witness<String>, Boolean> condition =
          EITHER.widen(Either.left("condition error"));
      Kind<EitherKind.Witness<String>, Unit> effect = EITHER.widen(Either.right(Unit.INSTANCE));

      Kind<EitherKind.Witness<String>, Unit> result = selective.whenS(condition, effect);

      Either<String, Unit> either = EITHER.narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo("condition error");
    }

    @Test
    @DisplayName("whenS should propagate error from effect when condition is true")
    void whenSPropagatesEffectError() {
      Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(true));
      Kind<EitherKind.Witness<String>, Unit> effect = EITHER.widen(Either.left("effect error"));

      Kind<EitherKind.Witness<String>, Unit> result = selective.whenS(condition, effect);

      Either<String, Unit> either = EITHER.narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo("effect error");
    }
  }

  @Nested
  @DisplayName("whenS_ Operation Tests")
  class WhenS_OperationTests {

    @Test
    @DisplayName("whenS_ should execute and discard effect result when true")
    void whenS_ExecutesAndDiscardsOnTrue() {
      Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(true));
      Kind<EitherKind.Witness<String>, Integer> effect = EITHER.widen(Either.right(42));

      Kind<EitherKind.Witness<String>, Unit> result = selective.whenS_(condition, effect);

      Either<String, Unit> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS_ should skip effect when false")
    void whenS_SkipsOnFalse() {
      Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(false));
      Kind<EitherKind.Witness<String>, Integer> effect = EITHER.widen(Either.right(42));

      Kind<EitherKind.Witness<String>, Unit> result = selective.whenS_(condition, effect);

      Either<String, Unit> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("whenS_ should propagate effect error")
    void whenS_PropagatesEffectError() {
      Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(true));
      Kind<EitherKind.Witness<String>, Integer> effect = EITHER.widen(Either.left("effect error"));

      Kind<EitherKind.Witness<String>, Unit> result = selective.whenS_(condition, effect);

      Either<String, Unit> either = EITHER.narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo("effect error");
    }
  }

  @Nested
  @DisplayName("ifS Operation Tests")
  class IfSOperationTests {

    @Test
    @DisplayName("ifS should return then branch when condition is true")
    void ifSReturnsThenOnTrue() {
      Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(true));
      Kind<EitherKind.Witness<String>, Integer> thenBranch = EITHER.widen(Either.right(42));
      Kind<EitherKind.Witness<String>, Integer> elseBranch = EITHER.widen(Either.right(100));

      Kind<EitherKind.Witness<String>, Integer> result =
          selective.ifS(condition, thenBranch, elseBranch);

      Either<String, Integer> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("ifS should return else branch when condition is false")
    void ifSReturnsElseOnFalse() {
      Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(false));
      Kind<EitherKind.Witness<String>, Integer> thenBranch = EITHER.widen(Either.right(42));
      Kind<EitherKind.Witness<String>, Integer> elseBranch = EITHER.widen(Either.right(100));

      Kind<EitherKind.Witness<String>, Integer> result =
          selective.ifS(condition, thenBranch, elseBranch);

      Either<String, Integer> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo(100);
    }

    @Test
    @DisplayName("ifS should propagate error from condition")
    void ifSPropagatesConditionError() {
      Kind<EitherKind.Witness<String>, Boolean> condition =
          EITHER.widen(Either.left("condition error"));
      Kind<EitherKind.Witness<String>, Integer> thenBranch = EITHER.widen(Either.right(42));
      Kind<EitherKind.Witness<String>, Integer> elseBranch = EITHER.widen(Either.right(100));

      Kind<EitherKind.Witness<String>, Integer> result =
          selective.ifS(condition, thenBranch, elseBranch);

      Either<String, Integer> either = EITHER.narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo("condition error");
    }

    @Test
    @DisplayName("ifS should propagate error from then branch when true")
    void ifSPropagatesThenError() {
      Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(true));
      Kind<EitherKind.Witness<String>, Integer> thenBranch =
          EITHER.widen(Either.left("then error"));
      Kind<EitherKind.Witness<String>, Integer> elseBranch = EITHER.widen(Either.right(100));

      Kind<EitherKind.Witness<String>, Integer> result =
          selective.ifS(condition, thenBranch, elseBranch);

      Either<String, Integer> either = EITHER.narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo("then error");
    }

    @Test
    @DisplayName("ifS should propagate error from else branch when false")
    void ifSPropagatesElseError() {
      Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(false));
      Kind<EitherKind.Witness<String>, Integer> thenBranch = EITHER.widen(Either.right(42));
      Kind<EitherKind.Witness<String>, Integer> elseBranch =
          EITHER.widen(Either.left("else error"));

      Kind<EitherKind.Witness<String>, Integer> result =
          selective.ifS(condition, thenBranch, elseBranch);

      Either<String, Integer> either = EITHER.narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo("else error");
    }
  }

  @Nested
  @DisplayName("branch Operation Tests")
  class BranchOperationTests {

    @Test
    @DisplayName("branch should apply left handler on Left choice")
    void branchAppliesLeftHandler() {
      Kind<EitherKind.Witness<String>, Choice<Integer, String>> choice =
          EITHER.widen(Either.right(Selective.left(42)));
      Kind<EitherKind.Witness<String>, Function<Integer, String>> leftHandler =
          EITHER.widen(Either.right(i -> "Number: " + i));
      Kind<EitherKind.Witness<String>, Function<String, String>> rightHandler =
          EITHER.widen(Either.right(s -> "Text: " + s));

      Kind<EitherKind.Witness<String>, String> result =
          selective.branch(choice, leftHandler, rightHandler);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo("Number: 42");
    }

    @Test
    @DisplayName("branch should apply right handler on Right choice")
    void branchAppliesRightHandler() {
      Kind<EitherKind.Witness<String>, Choice<Integer, String>> choice =
          EITHER.widen(Either.right(Selective.right("hello")));
      Kind<EitherKind.Witness<String>, Function<Integer, String>> leftHandler =
          EITHER.widen(Either.right(i -> "Number: " + i));
      Kind<EitherKind.Witness<String>, Function<String, String>> rightHandler =
          EITHER.widen(Either.right(s -> "Text: " + s));

      Kind<EitherKind.Witness<String>, String> result =
          selective.branch(choice, leftHandler, rightHandler);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo("Text: hello");
    }

    @Test
    @DisplayName("branch should propagate error from choice")
    void branchPropagatesChoiceError() {
      Kind<EitherKind.Witness<String>, Choice<Integer, String>> choice =
          EITHER.widen(Either.left("choice error"));
      Kind<EitherKind.Witness<String>, Function<Integer, String>> leftHandler =
          EITHER.widen(Either.right(i -> "Number: " + i));
      Kind<EitherKind.Witness<String>, Function<String, String>> rightHandler =
          EITHER.widen(Either.right(s -> "Text: " + s));

      Kind<EitherKind.Witness<String>, String> result =
          selective.branch(choice, leftHandler, rightHandler);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo("choice error");
    }

    @Test
    @DisplayName("branch should propagate error from left handler")
    void branchPropagatesLeftHandlerError() {
      Kind<EitherKind.Witness<String>, Choice<Integer, String>> choice =
          EITHER.widen(Either.right(Selective.left(42)));
      Kind<EitherKind.Witness<String>, Function<Integer, String>> leftHandler =
          EITHER.widen(Either.left("left handler error"));
      Kind<EitherKind.Witness<String>, Function<String, String>> rightHandler =
          EITHER.widen(Either.right(s -> "Text: " + s));

      Kind<EitherKind.Witness<String>, String> result =
          selective.branch(choice, leftHandler, rightHandler);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo("left handler error");
    }

    @Test
    @DisplayName("branch should propagate error from right handler")
    void branchPropagatesRightHandlerError() {
      Kind<EitherKind.Witness<String>, Choice<Integer, String>> choice =
          EITHER.widen(Either.right(Selective.right("hello")));
      Kind<EitherKind.Witness<String>, Function<Integer, String>> leftHandler =
          EITHER.widen(Either.right(i -> "Number: " + i));
      Kind<EitherKind.Witness<String>, Function<String, String>> rightHandler =
          EITHER.widen(Either.left("right handler error"));

      Kind<EitherKind.Witness<String>, String> result =
          selective.branch(choice, leftHandler, rightHandler);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo("right handler error");
    }
  }

  @Nested
  @DisplayName("orElse Operation Tests")
  class OrElseOperationTests {

    @Test
    @DisplayName("orElse should return first Right when available")
    void orElseReturnsFirstRight() {
      Kind<EitherKind.Witness<String>, Choice<String, Integer>> first =
          EITHER.widen(Either.right(Selective.right(42)));
      Kind<EitherKind.Witness<String>, Choice<String, Integer>> second =
          EITHER.widen(Either.right(Selective.right(100)));

      Kind<EitherKind.Witness<String>, Choice<String, Integer>> result =
          selective.orElse(List.of(first, second));

      Either<String, Choice<String, Integer>> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight().isRight()).isTrue();
      assertThat(either.getRight().getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("orElse should try second alternative when first is Left")
    void orElseFallsBackToSecond() {
      Kind<EitherKind.Witness<String>, Choice<String, Integer>> first =
          EITHER.widen(Either.right(Selective.left("error1")));
      Kind<EitherKind.Witness<String>, Choice<String, Integer>> second =
          EITHER.widen(Either.right(Selective.right(100)));

      Kind<EitherKind.Witness<String>, Choice<String, Integer>> result =
          selective.orElse(List.of(first, second));

      Either<String, Choice<String, Integer>> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight().isRight()).isTrue();
      assertThat(either.getRight().getRight()).isEqualTo(100);
    }

    @Test
    @DisplayName("orElse should return last error when all alternatives fail")
    void orElseReturnsLastError() {
      Kind<EitherKind.Witness<String>, Choice<String, Integer>> first =
          EITHER.widen(Either.right(Selective.left("error1")));
      Kind<EitherKind.Witness<String>, Choice<String, Integer>> second =
          EITHER.widen(Either.right(Selective.left("error2")));

      Kind<EitherKind.Witness<String>, Choice<String, Integer>> result =
          selective.orElse(List.of(first, second));

      Either<String, Choice<String, Integer>> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight().isLeft()).isTrue();
      assertThat(either.getRight().getLeft()).isEqualTo("error2");
    }

    @Test
    @DisplayName("orElse should work with three alternatives")
    void orElseWithThreeAlternatives() {
      Kind<EitherKind.Witness<String>, Choice<String, Integer>> first =
          EITHER.widen(Either.right(Selective.left("error1")));
      Kind<EitherKind.Witness<String>, Choice<String, Integer>> second =
          EITHER.widen(Either.right(Selective.left("error2")));
      Kind<EitherKind.Witness<String>, Choice<String, Integer>> third =
          EITHER.widen(Either.right(Selective.right(200)));

      Kind<EitherKind.Witness<String>, Choice<String, Integer>> result =
          selective.orElse(List.of(first, second, third));

      Either<String, Choice<String, Integer>> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight().isRight()).isTrue();
      assertThat(either.getRight().getRight()).isEqualTo(200);
    }

    @Test
    @DisplayName("orElse should throw on empty list")
    void orElseThrowsOnEmptyList() {
      assertThatThrownBy(() -> selective.orElse(List.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("orElse requires at least one alternative");
    }

    @Test
    @DisplayName("orElse should work with single alternative")
    void orElseWithSingleAlternative() {
      Kind<EitherKind.Witness<String>, Choice<String, Integer>> single =
          EITHER.widen(Either.right(Selective.right(42)));

      Kind<EitherKind.Witness<String>, Choice<String, Integer>> result =
          selective.orElse(List.of(single));

      Either<String, Choice<String, Integer>> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight().getRight()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("apS Operation Tests")
  class ApSOperationTests {

    @Test
    @DisplayName("apS should apply functions in sequence on Right values")
    void apSAppliesFunctionsOnRight() {
      Kind<EitherKind.Witness<String>, Choice<String, Integer>> initial =
          EITHER.widen(Either.right(Selective.right(10)));

      Function<Integer, Choice<String, Integer>> doubleFunc = i -> Selective.right(i * 2);
      Function<Integer, Choice<String, Integer>> addTenFunc = i -> Selective.right(i + 10);

      Kind<EitherKind.Witness<String>, Function<Integer, Choice<String, Integer>>> doubleFuncKind =
          EITHER.widen(Either.right(doubleFunc));
      Kind<EitherKind.Witness<String>, Function<Integer, Choice<String, Integer>>> addTenFuncKind =
          EITHER.widen(Either.right(addTenFunc));

      Kind<EitherKind.Witness<String>, Choice<String, Integer>> result =
          selective.apS(initial, List.of(doubleFuncKind, addTenFuncKind));

      Either<String, Choice<String, Integer>> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight().isRight()).isTrue();
      // 10 * 2 = 20, 20 + 10 = 30
      assertThat(either.getRight().getRight()).isEqualTo(30);
    }

    @Test
    @DisplayName("apS should stop on first Left result from function")
    void apSStopsOnLeftFromFunction() {
      Kind<EitherKind.Witness<String>, Choice<String, Integer>> initial =
          EITHER.widen(Either.right(Selective.right(10)));

      Function<Integer, Choice<String, Integer>> failFunc = i -> Selective.left("failed at " + i);
      Function<Integer, Choice<String, Integer>> shouldNotRun = i -> Selective.right(i * 100);

      Kind<EitherKind.Witness<String>, Function<Integer, Choice<String, Integer>>> failFuncKind =
          EITHER.widen(Either.right(failFunc));
      Kind<EitherKind.Witness<String>, Function<Integer, Choice<String, Integer>>>
          shouldNotRunKind = EITHER.widen(Either.right(shouldNotRun));

      Kind<EitherKind.Witness<String>, Choice<String, Integer>> result =
          selective.apS(initial, List.of(failFuncKind, shouldNotRunKind));

      Either<String, Choice<String, Integer>> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight().isLeft()).isTrue();
      assertThat(either.getRight().getLeft()).isEqualTo("failed at 10");
    }

    @Test
    @DisplayName("apS should skip functions when initial is Left")
    void apSSkipsFunctionsOnInitialLeft() {
      Kind<EitherKind.Witness<String>, Choice<String, Integer>> initial =
          EITHER.widen(Either.right(Selective.left("initial error")));

      Function<Integer, Choice<String, Integer>> doubleFunc = i -> Selective.right(i * 2);

      Kind<EitherKind.Witness<String>, Function<Integer, Choice<String, Integer>>> doubleFuncKind =
          EITHER.widen(Either.right(doubleFunc));

      Kind<EitherKind.Witness<String>, Choice<String, Integer>> result =
          selective.apS(initial, List.of(doubleFuncKind));

      Either<String, Choice<String, Integer>> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight().isLeft()).isTrue();
      assertThat(either.getRight().getLeft()).isEqualTo("initial error");
    }

    @Test
    @DisplayName("apS should return initial value when function list is empty")
    void apSWithEmptyFunctionList() {
      Kind<EitherKind.Witness<String>, Choice<String, Integer>> initial =
          EITHER.widen(Either.right(Selective.right(42)));

      Kind<EitherKind.Witness<String>, Choice<String, Integer>> result =
          selective.apS(initial, List.of());

      Either<String, Choice<String, Integer>> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight().getRight()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("SimpleChoice Tests")
  class SimpleChoiceTests {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

      @Test
      @DisplayName("Selective.left should create a Left Choice")
      void leftCreatesLeftChoice() {
        Choice<String, Integer> choice = Selective.left("error");

        assertThat(choice.isLeft()).isTrue();
        assertThat(choice.isRight()).isFalse();
        assertThat(choice.getLeft()).isEqualTo("error");
      }

      @Test
      @DisplayName("Selective.right should create a Right Choice")
      void rightCreatesRightChoice() {
        Choice<String, Integer> choice = Selective.right(42);

        assertThat(choice.isLeft()).isFalse();
        assertThat(choice.isRight()).isTrue();
        assertThat(choice.getRight()).isEqualTo(42);
      }
    }

    @Nested
    @DisplayName("map Operation")
    class MapOperation {

      @Test
      @DisplayName("map should transform Right value")
      void mapTransformsRight() {
        Choice<String, Integer> choice = Selective.right(10);

        Choice<String, String> mapped = choice.map(i -> "Value: " + i);

        assertThat(mapped.isRight()).isTrue();
        assertThat(mapped.getRight()).isEqualTo("Value: 10");
      }

      @Test
      @DisplayName("map should not affect Left value")
      void mapDoesNotAffectLeft() {
        Choice<String, Integer> choice = Selective.left("error");

        Choice<String, String> mapped = choice.map(i -> "Value: " + i);

        assertThat(mapped.isLeft()).isTrue();
        assertThat(mapped.getLeft()).isEqualTo("error");
      }
    }

    @Nested
    @DisplayName("mapLeft Operation")
    class MapLeftOperation {

      @Test
      @DisplayName("mapLeft should transform Left value")
      void mapLeftTransformsLeft() {
        Choice<String, Integer> choice = Selective.left("error");

        Choice<Integer, Integer> mapped = choice.mapLeft(String::length);

        assertThat(mapped.isLeft()).isTrue();
        assertThat(mapped.getLeft()).isEqualTo(5);
      }

      @Test
      @DisplayName("mapLeft should not affect Right value")
      void mapLeftDoesNotAffectRight() {
        Choice<String, Integer> choice = Selective.right(42);

        Choice<Integer, Integer> mapped = choice.mapLeft(String::length);

        assertThat(mapped.isRight()).isTrue();
        assertThat(mapped.getRight()).isEqualTo(42);
      }
    }

    @Nested
    @DisplayName("swap Operation")
    class SwapOperation {

      @Test
      @DisplayName("swap should convert Left to Right")
      void swapLeftToRight() {
        Choice<String, Integer> choice = Selective.left("error");

        Choice<Integer, String> swapped = choice.swap();

        assertThat(swapped.isRight()).isTrue();
        assertThat(swapped.getRight()).isEqualTo("error");
      }

      @Test
      @DisplayName("swap should convert Right to Left")
      void swapRightToLeft() {
        Choice<String, Integer> choice = Selective.right(42);

        Choice<Integer, String> swapped = choice.swap();

        assertThat(swapped.isLeft()).isTrue();
        assertThat(swapped.getLeft()).isEqualTo(42);
      }

      @Test
      @DisplayName("swap twice should return to original")
      void swapTwice() {
        Choice<String, Integer> original = Selective.right(42);

        Choice<String, Integer> swappedTwice = original.swap().swap();

        assertThat(swappedTwice.isRight()).isTrue();
        assertThat(swappedTwice.getRight()).isEqualTo(42);
      }
    }

    @Nested
    @DisplayName("fold Operation")
    class FoldOperation {

      @Test
      @DisplayName("fold should apply left mapper for Left value")
      void foldAppliesLeftMapper() {
        Choice<String, Integer> choice = Selective.left("error");

        String result = choice.fold(l -> "Left: " + l, r -> "Right: " + r);

        assertThat(result).isEqualTo("Left: error");
      }

      @Test
      @DisplayName("fold should apply right mapper for Right value")
      void foldAppliesRightMapper() {
        Choice<String, Integer> choice = Selective.right(42);

        String result = choice.fold(l -> "Left: " + l, r -> "Right: " + r);

        assertThat(result).isEqualTo("Right: 42");
      }
    }

    @Nested
    @DisplayName("flatMap Operation")
    class FlatMapOperation {

      @Test
      @DisplayName("flatMap should chain Right values")
      void flatMapChainsRight() {
        Choice<String, Integer> choice = Selective.right(10);

        Choice<String, Integer> result =
            choice.flatMap(i -> i > 5 ? Selective.right(i * 2) : Selective.left("too small"));

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEqualTo(20);
      }

      @Test
      @DisplayName("flatMap should return Left from function")
      void flatMapReturnsLeftFromFunction() {
        Choice<String, Integer> choice = Selective.right(3);

        Choice<String, Integer> result =
            choice.flatMap(i -> i > 5 ? Selective.right(i * 2) : Selective.left("too small"));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo("too small");
      }

      @Test
      @DisplayName("flatMap should not apply function to Left")
      void flatMapSkipsLeft() {
        Choice<String, Integer> choice = Selective.left("initial error");

        Choice<String, Integer> result = choice.flatMap(i -> Selective.right(i * 2));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo("initial error");
      }

      @Test
      @DisplayName("flatMap should chain multiple operations")
      void flatMapChains() {
        Choice<String, Integer> choice = Selective.right(5);

        Choice<String, Integer> result =
            choice.flatMap(i -> Selective.right(i + 5)).flatMap(i -> Selective.right(i * 2));

        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEqualTo(20);
      }
    }

    @Nested
    @DisplayName("getLeft/getRight Exception Tests")
    class GetterExceptionTests {

      @Test
      @DisplayName("getLeft on Right should throw NoSuchElementException")
      void getLeftOnRightThrows() {
        Choice<String, Integer> choice = Selective.right(42);

        assertThatThrownBy(choice::getLeft)
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("Not a left value");
      }

      @Test
      @DisplayName("getRight on Left should throw NoSuchElementException")
      void getRightOnLeftThrows() {
        Choice<String, Integer> choice = Selective.left("error");

        assertThatThrownBy(choice::getRight)
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("Not a right value");
      }
    }
  }

  /**
   * Tests that exercise the default method implementations in the Selective interface. Uses
   * DefaultMethodsSelective which doesn't override branch, whenS, or ifS, forcing use of the
   * default implementations.
   */
  @Nested
  @DisplayName("Default Method Implementations (not overridden)")
  class DefaultMethodImplementationTests {

    private DefaultMethodsSelective<String> defaultSelective;

    @BeforeEach
    void setUp() {
      defaultSelective = new DefaultMethodsSelective<>();
    }

    @Nested
    @DisplayName("branch default implementation")
    class BranchDefaultTests {

      @Test
      @DisplayName("branch should apply left handler on Left choice")
      void branchAppliesLeftHandlerDefault() {
        Kind<EitherKind.Witness<String>, Choice<Integer, String>> choice =
            EITHER.widen(Either.right(Selective.left(42)));
        Kind<EitherKind.Witness<String>, Function<Integer, String>> leftHandler =
            EITHER.widen(Either.right(i -> "Number: " + i));
        Kind<EitherKind.Witness<String>, Function<String, String>> rightHandler =
            EITHER.widen(Either.right(s -> "Text: " + s));

        Kind<EitherKind.Witness<String>, String> result =
            defaultSelective.branch(choice, leftHandler, rightHandler);

        Either<String, String> either = EITHER.narrow(result);
        assertThat(either.isRight()).isTrue();
        assertThat(either.getRight()).isEqualTo("Number: 42");
      }

      @Test
      @DisplayName("branch should apply right handler on Right choice")
      void branchAppliesRightHandlerDefault() {
        Kind<EitherKind.Witness<String>, Choice<Integer, String>> choice =
            EITHER.widen(Either.right(Selective.right("hello")));
        Kind<EitherKind.Witness<String>, Function<Integer, String>> leftHandler =
            EITHER.widen(Either.right(i -> "Number: " + i));
        Kind<EitherKind.Witness<String>, Function<String, String>> rightHandler =
            EITHER.widen(Either.right(s -> "Text: " + s));

        Kind<EitherKind.Witness<String>, String> result =
            defaultSelective.branch(choice, leftHandler, rightHandler);

        Either<String, String> either = EITHER.narrow(result);
        assertThat(either.isRight()).isTrue();
        assertThat(either.getRight()).isEqualTo("Text: hello");
      }

      @Test
      @DisplayName("branch should propagate error from choice")
      void branchPropagatesChoiceErrorDefault() {
        Kind<EitherKind.Witness<String>, Choice<Integer, String>> choice =
            EITHER.widen(Either.left("choice error"));
        Kind<EitherKind.Witness<String>, Function<Integer, String>> leftHandler =
            EITHER.widen(Either.right(i -> "Number: " + i));
        Kind<EitherKind.Witness<String>, Function<String, String>> rightHandler =
            EITHER.widen(Either.right(s -> "Text: " + s));

        Kind<EitherKind.Witness<String>, String> result =
            defaultSelective.branch(choice, leftHandler, rightHandler);

        Either<String, String> either = EITHER.narrow(result);
        assertThat(either.isLeft()).isTrue();
        assertThat(either.getLeft()).isEqualTo("choice error");
      }

      @Test
      @DisplayName("branch should throw on null choice")
      void branchThrowsOnNullChoice() {
        Kind<EitherKind.Witness<String>, Function<Integer, String>> leftHandler =
            EITHER.widen(Either.right(i -> "Number: " + i));
        Kind<EitherKind.Witness<String>, Function<String, String>> rightHandler =
            EITHER.widen(Either.right(s -> "Text: " + s));

        assertThatThrownBy(() -> defaultSelective.branch(null, leftHandler, rightHandler))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("fab for branch cannot be null");
      }

      @Test
      @DisplayName("branch should throw on null left handler")
      void branchThrowsOnNullLeftHandler() {
        Kind<EitherKind.Witness<String>, Choice<Integer, String>> choice =
            EITHER.widen(Either.right(Selective.left(42)));
        Kind<EitherKind.Witness<String>, Function<String, String>> rightHandler =
            EITHER.widen(Either.right(s -> "Text: " + s));

        assertThatThrownBy(() -> defaultSelective.branch(choice, null, rightHandler))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("fl for branch cannot be null");
      }

      @Test
      @DisplayName("branch should throw on null right handler")
      void branchThrowsOnNullRightHandler() {
        Kind<EitherKind.Witness<String>, Choice<Integer, String>> choice =
            EITHER.widen(Either.right(Selective.left(42)));
        Kind<EitherKind.Witness<String>, Function<Integer, String>> leftHandler =
            EITHER.widen(Either.right(i -> "Number: " + i));

        assertThatThrownBy(() -> defaultSelective.branch(choice, leftHandler, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("fr for branch cannot be null");
      }
    }

    @Nested
    @DisplayName("whenS default implementation")
    class WhenSDefaultTests {

      @Test
      @DisplayName("whenS should execute effect when condition is true")
      void whenSExecutesOnTrueDefault() {
        Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(true));
        Kind<EitherKind.Witness<String>, Unit> effect = EITHER.widen(Either.right(Unit.INSTANCE));

        Kind<EitherKind.Witness<String>, Unit> result = defaultSelective.whenS(condition, effect);

        Either<String, Unit> either = EITHER.narrow(result);
        assertThat(either.isRight()).isTrue();
        assertThat(either.getRight()).isEqualTo(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS should skip effect when condition is false")
      void whenSSkipsOnFalseDefault() {
        Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(false));
        Kind<EitherKind.Witness<String>, Unit> effect = EITHER.widen(Either.right(Unit.INSTANCE));

        Kind<EitherKind.Witness<String>, Unit> result = defaultSelective.whenS(condition, effect);

        Either<String, Unit> either = EITHER.narrow(result);
        assertThat(either.isRight()).isTrue();
        assertThat(either.getRight()).isEqualTo(Unit.INSTANCE);
      }

      @Test
      @DisplayName("whenS should propagate error from condition")
      void whenSPropagatesConditionErrorDefault() {
        Kind<EitherKind.Witness<String>, Boolean> condition =
            EITHER.widen(Either.left("condition error"));
        Kind<EitherKind.Witness<String>, Unit> effect = EITHER.widen(Either.right(Unit.INSTANCE));

        Kind<EitherKind.Witness<String>, Unit> result = defaultSelective.whenS(condition, effect);

        Either<String, Unit> either = EITHER.narrow(result);
        assertThat(either.isLeft()).isTrue();
        assertThat(either.getLeft()).isEqualTo("condition error");
      }

      @Test
      @DisplayName("whenS should throw on null condition")
      void whenSThrowsOnNullCondition() {
        Kind<EitherKind.Witness<String>, Unit> effect = EITHER.widen(Either.right(Unit.INSTANCE));

        assertThatThrownBy(() -> defaultSelective.whenS(null, effect))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("fcond for whenS cannot be null");
      }

      @Test
      @DisplayName("whenS should throw on null effect")
      void whenSThrowsOnNullEffect() {
        Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(true));

        assertThatThrownBy(() -> defaultSelective.whenS(condition, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("fa for whenS cannot be null");
      }
    }

    @Nested
    @DisplayName("ifS default implementation")
    class IfSDefaultTests {

      @Test
      @DisplayName("ifS should return then branch when condition is true")
      void ifSReturnsThenOnTrueDefault() {
        Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(true));
        Kind<EitherKind.Witness<String>, Integer> thenBranch = EITHER.widen(Either.right(42));
        Kind<EitherKind.Witness<String>, Integer> elseBranch = EITHER.widen(Either.right(100));

        Kind<EitherKind.Witness<String>, Integer> result =
            defaultSelective.ifS(condition, thenBranch, elseBranch);

        Either<String, Integer> either = EITHER.narrow(result);
        assertThat(either.isRight()).isTrue();
        assertThat(either.getRight()).isEqualTo(42);
      }

      @Test
      @DisplayName("ifS should return else branch when condition is false")
      void ifSReturnsElseOnFalseDefault() {
        Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(false));
        Kind<EitherKind.Witness<String>, Integer> thenBranch = EITHER.widen(Either.right(42));
        Kind<EitherKind.Witness<String>, Integer> elseBranch = EITHER.widen(Either.right(100));

        Kind<EitherKind.Witness<String>, Integer> result =
            defaultSelective.ifS(condition, thenBranch, elseBranch);

        Either<String, Integer> either = EITHER.narrow(result);
        assertThat(either.isRight()).isTrue();
        assertThat(either.getRight()).isEqualTo(100);
      }

      @Test
      @DisplayName("ifS should propagate error from condition")
      void ifSPropagatesConditionErrorDefault() {
        Kind<EitherKind.Witness<String>, Boolean> condition =
            EITHER.widen(Either.left("condition error"));
        Kind<EitherKind.Witness<String>, Integer> thenBranch = EITHER.widen(Either.right(42));
        Kind<EitherKind.Witness<String>, Integer> elseBranch = EITHER.widen(Either.right(100));

        Kind<EitherKind.Witness<String>, Integer> result =
            defaultSelective.ifS(condition, thenBranch, elseBranch);

        Either<String, Integer> either = EITHER.narrow(result);
        assertThat(either.isLeft()).isTrue();
        assertThat(either.getLeft()).isEqualTo("condition error");
      }

      @Test
      @DisplayName("ifS should throw on null condition")
      void ifSThrowsOnNullCondition() {
        Kind<EitherKind.Witness<String>, Integer> thenBranch = EITHER.widen(Either.right(42));
        Kind<EitherKind.Witness<String>, Integer> elseBranch = EITHER.widen(Either.right(100));

        assertThatThrownBy(() -> defaultSelective.ifS(null, thenBranch, elseBranch))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("fcond for ifS cannot be null");
      }

      @Test
      @DisplayName("ifS should throw on null then branch")
      void ifSThrowsOnNullThenBranch() {
        Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(true));
        Kind<EitherKind.Witness<String>, Integer> elseBranch = EITHER.widen(Either.right(100));

        assertThatThrownBy(() -> defaultSelective.ifS(condition, null, elseBranch))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("fthen for ifS cannot be null");
      }

      @Test
      @DisplayName("ifS should throw on null else branch")
      void ifSThrowsOnNullElseBranch() {
        Kind<EitherKind.Witness<String>, Boolean> condition = EITHER.widen(Either.right(true));
        Kind<EitherKind.Witness<String>, Integer> thenBranch = EITHER.widen(Either.right(42));

        assertThatThrownBy(() -> defaultSelective.ifS(condition, thenBranch, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("felse for ifS cannot be null");
      }
    }
  }
}
