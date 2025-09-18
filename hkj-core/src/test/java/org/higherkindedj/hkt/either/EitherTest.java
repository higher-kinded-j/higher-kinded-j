// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.test.HKTTestAssertions.*;
import static org.higherkindedj.hkt.test.HKTTestHelpers.*;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Either<L, R> Direct Tests")
class EitherTest {

  private final String leftValue = "Error Message";
  private final Integer rightValue = 123;
  private final Either<String, Integer> leftInstance = Either.left(leftValue);
  private final Either<String, Integer> rightInstance = Either.right(rightValue);
  private final Either<String, Integer> leftNullInstance = Either.left(null);
  private final Either<String, Integer> rightNullInstance = Either.right(null);

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    @DisplayName("left() should create Left instances correctly")
    void leftShouldCreateLeftInstances() {
      assertThat(leftInstance).isInstanceOf(Either.Left.class);
      assertThat(leftInstance.isLeft()).isTrue();
      assertThat(leftInstance.isRight()).isFalse();
      assertThat(leftInstance.getLeft()).isEqualTo(leftValue);
    }

    @Test
    @DisplayName("left() should allow null values")
    void leftShouldAllowNullValues() {
      assertThat(leftNullInstance).isInstanceOf(Either.Left.class);
      assertThat(leftNullInstance.isLeft()).isTrue();
      assertThat(leftNullInstance.getLeft()).isNull();
    }

    @Test
    @DisplayName("right() should create Right instances correctly")
    void rightShouldCreateRightInstances() {
      assertThat(rightInstance).isInstanceOf(Either.Right.class);
      assertThat(rightInstance.isRight()).isTrue();
      assertThat(rightInstance.isLeft()).isFalse();
      assertThat(rightInstance.getRight()).isEqualTo(rightValue);
    }

    @Test
    @DisplayName("right() should allow null values")
    void rightShouldAllowNullValues() {
      assertThat(rightNullInstance).isInstanceOf(Either.Right.class);
      assertThat(rightNullInstance.isRight()).isTrue();
      assertThat(rightNullInstance.getRight()).isNull();
    }

    @Test
    @DisplayName("Factory methods comprehensive testing with various types")
    void factoryMethodsComprehensiveTesting() {
      // Test with different types
      Either<RuntimeException, String> exceptionLeft = Either.left(new RuntimeException("test"));
      Either<String, java.util.List<Integer>> listRight = Either.right(java.util.List.of(1, 2, 3));
      Either<Integer, Boolean> booleanEither = Either.right(true);

      assertThat(exceptionLeft.isLeft()).isTrue();
      assertThat(exceptionLeft.getLeft()).isInstanceOf(RuntimeException.class);

      assertThat(listRight.isRight()).isTrue();
      assertThat(listRight.getRight()).containsExactly(1, 2, 3);

      assertThat(booleanEither.isRight()).isTrue();
      assertThat(booleanEither.getRight()).isTrue();
    }
  }

  @Nested
  @DisplayName("Getter Methods - Comprehensive Testing")
  class GetterMethodsTests {

    @Test
    @DisplayName("getLeft() on Left instances should return values correctly")
    void getLeftOnLeftInstances() {
      assertThat(leftInstance.getLeft()).isEqualTo(leftValue);
      assertThat(leftNullInstance.getLeft()).isNull();

      // Test with different Left types
      Either<RuntimeException, String> exceptionLeft = Either.left(new RuntimeException("error"));
      assertThat(exceptionLeft.getLeft()).isInstanceOf(RuntimeException.class);
      assertThat(exceptionLeft.getLeft().getMessage()).isEqualTo("error");
    }

    @Test
    @DisplayName("getLeft() on Right instances should throw NoSuchElementException")
    void getLeftOnRightInstancesShouldThrow() {
      assertThatThrownBy(rightInstance::getLeft)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot invoke getLeft() on a Right instance.");

      assertThatThrownBy(rightNullInstance::getLeft).isInstanceOf(NoSuchElementException.class);

      // Test with different Right types
      Either<String, Boolean> booleanRight = Either.right(false);
      assertThatThrownBy(booleanRight::getLeft)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot invoke getLeft() on a Right instance.");
    }

    @Test
    @DisplayName("getRight() on Right instances should return values correctly")
    void getRightOnRightInstances() {
      assertThat(rightInstance.getRight()).isEqualTo(rightValue);
      assertThat(rightNullInstance.getRight()).isNull();

      // Test with different Right types
      Either<String, java.util.List<String>> listRight = Either.right(java.util.List.of("a", "b"));
      assertThat(listRight.getRight()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("getRight() on Left instances should throw NoSuchElementException")
    void getRightOnLeftInstancesShouldThrow() {
      assertThatThrownBy(leftInstance::getRight)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot invoke getRight() on a Left instance.");

      assertThatThrownBy(leftNullInstance::getRight).isInstanceOf(NoSuchElementException.class);

      // Test with different Left types
      Either<RuntimeException, String> exceptionLeft = Either.left(new RuntimeException());
      assertThatThrownBy(exceptionLeft::getRight)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot invoke getRight() on a Left instance.");
    }
  }

  @Nested
  @DisplayName("fold() Method - Comprehensive Testing")
  class FoldMethodTests {

    private final Function<String, String> leftMapper = l -> "Left mapped: " + l;
    private final Function<Integer, String> rightMapper = r -> "Right mapped: " + r;

    @Test
    @DisplayName("fold() on Left instances should apply left mapper")
    void foldOnLeftShouldApplyLeftMapper() {
      String result = leftInstance.fold(leftMapper, rightMapper);
      assertThat(result).isEqualTo("Left mapped: " + leftValue);

      String nullResult = leftNullInstance.fold(leftMapper, rightMapper);
      assertThat(nullResult).isEqualTo("Left mapped: null");
    }

    @Test
    @DisplayName("fold() on Right instances should apply right mapper")
    void foldOnRightShouldApplyRightMapper() {
      String result = rightInstance.fold(leftMapper, rightMapper);
      assertThat(result).isEqualTo("Right mapped: " + rightValue);

      String nullResult = rightNullInstance.fold(leftMapper, rightMapper);
      assertThat(nullResult).isEqualTo("Right mapped: null");
    }

    @Test
    @DisplayName("fold() should validate null mappers using standard assertions")
    void foldShouldValidateNullMappers() {
      // Use ValidationTestBuilder for systematic null parameter testing
      ValidationTestBuilder.create()
          .assertNullFunction(() -> leftInstance.fold(null, rightMapper), "leftMapper")
          .assertNullFunction(() -> rightInstance.fold(leftMapper, null), "rightMapper")
          .execute();
    }

    @Test
    @DisplayName("fold() comprehensive testing with different types")
    void foldComprehensiveTesting() {
      Either<RuntimeException, java.util.List<String>> complexEither =
          Either.right(java.util.List.of("x", "y"));

      String result =
          complexEither.fold(
              ex -> "Exception: " + ex.getMessage(), list -> "List size: " + list.size());

      assertThat(result).isEqualTo("List size: 2");

      Either<Integer, String> leftEither = Either.left(404);
      String leftResult =
          leftEither.fold(code -> "Error code: " + code, value -> "Success: " + value);

      assertThat(leftResult).isEqualTo("Error code: 404");
    }
  }

  @Nested
  @DisplayName("map() Method - Comprehensive Testing")
  class MapMethodTests {

    private final Function<Integer, String> mapper = i -> "Mapped: " + i;

    @Test
    @DisplayName("map() on Right instances should apply mapper")
    void mapOnRightShouldApplyMapper() {
      Either<String, String> result = rightInstance.map(mapper);
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo("Mapped: " + rightValue);

      Either<String, String> nullResult = rightNullInstance.map(mapper);
      assertThat(nullResult.isRight()).isTrue();
      assertThat(nullResult.getRight()).isEqualTo("Mapped: null");
    }

    @Test
    @DisplayName("map() on Left instances should return same instance")
    void mapOnLeftShouldReturnSameInstance() {
      Either<String, String> result = leftInstance.map(mapper);
      assertThat(result).isSameAs(leftInstance);
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo(leftValue);

      Either<String, String> nullResult = leftNullInstance.map(mapper);
      assertThat(nullResult).isSameAs(leftNullInstance);
      assertThat(nullResult.isLeft()).isTrue();
      assertThat(nullResult.getLeft()).isNull();
    }

    @Test
    @DisplayName("map() should validate null mapper using standard assertions")
    void mapShouldValidateNullMapper() {
      ValidationTestBuilder.create()
          .assertNullFunction(() -> rightInstance.map(null), "mapper")
          .assertNullFunction(() -> leftInstance.map(null), "mapper")
          .execute();
    }

    @Test
    @DisplayName("map() comprehensive testing with different transformations")
    void mapComprehensiveTesting() {
      Either<String, Integer> numberEither = Either.right(42);

      // Test mapping to different types
      Either<String, String> stringResult = numberEither.map(Object::toString);
      assertThat(stringResult.getRight()).isEqualTo("42");

      Either<String, Boolean> booleanResult = numberEither.map(n -> n > 40);
      assertThat(booleanResult.getRight()).isTrue();

      Either<String, java.util.List<Integer>> listResult =
          numberEither.map(n -> java.util.List.of(n, n * 2));
      assertThat(listResult.getRight()).containsExactly(42, 84);

      // Test mapping that returns null
      Either<String, String> nullMapResult = numberEither.map(n -> null);
      assertThat(nullMapResult.isRight()).isTrue();
      assertThat(nullMapResult.getRight()).isNull();
    }

    @Test
    @DisplayName("map() should propagate exceptions from mapper function")
    void mapShouldPropagateExceptions() {
      RuntimeException testException = createTestException("map test");
      Function<Integer, String> throwingMapper =
          CommonTestFunctions.throwingFunction(testException);

      assertThatThrownBy(() -> rightInstance.map(throwingMapper)).isSameAs(testException);

      // Left instances should not call the mapper, so no exception
      Either<String, String> leftResult = leftInstance.map(throwingMapper);
      assertThat(leftResult).isSameAs(leftInstance);
    }
  }

  @Nested
  @DisplayName("flatMap() Method - Comprehensive Testing")
  class FlatMapMethodTests {

    private final Function<Integer, Either<String, String>> mapperRight =
        i -> Either.right("FlatMapped: " + i);
    private final Function<Integer, Either<String, String>> mapperLeft =
        i -> Either.left("FlatMap Error");
    private final Function<Integer, Either<String, String>> mapperNull = i -> null;

    @Test
    @DisplayName("flatMap() on Right instances should apply mapper")
    void flatMapOnRightShouldApplyMapper() {
      Either<String, String> successResult = rightInstance.flatMap(mapperRight);
      assertThat(successResult.isRight()).isTrue();
      assertThat(successResult.getRight()).isEqualTo("FlatMapped: " + rightValue);

      Either<String, String> failResult = rightInstance.flatMap(mapperLeft);
      assertThat(failResult.isLeft()).isTrue();
      assertThat(failResult.getLeft()).isEqualTo("FlatMap Error");

      Either<String, String> nullInputResult = rightNullInstance.flatMap(mapperRight);
      assertThat(nullInputResult.isRight()).isTrue();
      assertThat(nullInputResult.getRight()).isEqualTo("FlatMapped: null");
    }

    @Test
    @DisplayName("flatMap() on Left instances should return same instance")
    void flatMapOnLeftShouldReturnSameInstance() {
      Either<String, String> result = leftInstance.flatMap(mapperRight);
      assertThat(result).isSameAs(leftInstance);
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo(leftValue);

      Either<String, String> nullResult = leftNullInstance.flatMap(mapperRight);
      assertThat(nullResult).isSameAs(leftNullInstance);
      assertThat(nullResult.isLeft()).isTrue();
      assertThat(nullResult.getLeft()).isNull();
    }

    @Test
    @DisplayName("flatMap() should validate parameters using standard assertions")
    void flatMapShouldValidateParameters() {
      ValidationTestBuilder.create()
          .assertNullFunction(() -> rightInstance.flatMap(null), "mapper")
          .assertNullFunction(() -> leftInstance.flatMap(null), "mapper")
          .execute();

      // Test null return value validation
      assertThatThrownBy(() -> rightInstance.flatMap(mapperNull))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("flatMap mapper returned a null Either instance");
    }

    @Test
    @DisplayName("flatMap() should handle complex chaining scenarios")
    void flatMapShouldHandleComplexChaining() {
      Either<String, Integer> start = Either.right(10);

      Either<String, String> chainedResult =
          start
              .flatMap(i -> Either.right("step1_" + i))
              .flatMap(s -> Either.right(s + "_step2"))
              .flatMap(s -> Either.right(s + "_final"));

      assertThat(chainedResult.getRight()).isEqualTo("step1_10_step2_final");

      // Test chaining with early failure
      Either<String, String> failedChain =
          start
              .flatMap(i -> Either.right("step1_" + i))
              .flatMap(s -> Either.left("FAIL"))
              .flatMap(s -> Either.right(s + "_never_reached"));

      assertThat(failedChain.isLeft()).isTrue();
      assertThat(failedChain.getLeft()).isEqualTo("FAIL");
    }

    @Test
    @DisplayName("flatMap() should propagate exceptions from mapper")
    void flatMapShouldPropagateExceptions() {
      RuntimeException testException = createTestException("flatMap test");
      Function<Integer, Either<String, String>> throwingMapper =
          CommonTestFunctions.throwingFunction(testException);

      assertThatThrownBy(() -> rightInstance.flatMap(throwingMapper)).isSameAs(testException);

      // Test with null input to mapper
      Function<Integer, Either<String, String>> nullSensitiveMapper =
          i -> {
            if (i == null) throw new IllegalArgumentException("null input");
            return Either.right("processed: " + i);
          };

      assertThatThrownBy(() -> rightNullInstance.flatMap(nullSensitiveMapper))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("null input");

      // Test with mapper that handles null properly
      Function<Integer, Either<String, String>> nullHandlingMapper =
          i -> {
            if (i == null) return Either.right("handled null");
            return Either.right("value: " + i);
          };

      Either<String, String> handledResult = rightNullInstance.flatMap(nullHandlingMapper);
      assertThat(handledResult.getRight()).isEqualTo("handled null");
    }

    @Test
    @DisplayName("flatMap() comprehensive testing with different result types")
    void flatMapComprehensiveTesting() {
      Either<String, Integer> numberEither = Either.right(5);

      // flatMap to different types
      Either<String, java.util.List<Integer>> listResult =
          numberEither.flatMap(
              n ->
                  Either.right(
                      java.util.stream.IntStream.range(0, n)
                          .boxed()
                          .collect(java.util.stream.Collectors.toList())));
      assertThat(listResult.getRight()).containsExactly(0, 1, 2, 3, 4);

      // flatMap with conditional logic
      Either<String, String> conditionalResult =
          numberEither.flatMap(
              n -> {
                if (n % 2 == 0) {
                  return Either.right("even: " + n);
                } else {
                  return Either.left("odd number not allowed");
                }
              });
      assertThat(conditionalResult.isLeft()).isTrue();
      assertThat(conditionalResult.getLeft()).isEqualTo("odd number not allowed");
    }
  }

  @Nested
  @DisplayName("Side Effect Methods - ifLeft() and ifRight()")
  class SideEffectMethodsTests {

    @Test
    @DisplayName("ifLeft() should execute action on Left instances")
    void ifLeftShouldExecuteOnLeft() {
      AtomicBoolean executed = new AtomicBoolean(false);
      Consumer<String> action =
          s -> {
            assertThat(s).isEqualTo(leftValue);
            executed.set(true);
          };

      leftInstance.ifLeft(action);
      assertThat(executed).isTrue();

      // Test with null value
      AtomicBoolean nullExecuted = new AtomicBoolean(false);
      Consumer<String> nullAction =
          s -> {
            assertThat(s).isNull();
            nullExecuted.set(true);
          };

      leftNullInstance.ifLeft(nullAction);
      assertThat(nullExecuted).isTrue();
    }

    @Test
    @DisplayName("ifLeft() should not execute action on Right instances")
    void ifLeftShouldNotExecuteOnRight() {
      AtomicBoolean executed = new AtomicBoolean(false);
      Consumer<String> action = s -> executed.set(true);

      rightInstance.ifLeft(action);
      assertThat(executed).isFalse();

      rightNullInstance.ifLeft(action);
      assertThat(executed).isFalse();
    }

    @Test
    @DisplayName("ifRight() should execute action on Right instances")
    void ifRightShouldExecuteOnRight() {
      AtomicBoolean executed = new AtomicBoolean(false);
      Consumer<Integer> action =
          i -> {
            assertThat(i).isEqualTo(rightValue);
            executed.set(true);
          };

      rightInstance.ifRight(action);
      assertThat(executed).isTrue();

      // Test with null value
      AtomicBoolean nullExecuted = new AtomicBoolean(false);
      Consumer<Integer> nullAction =
          i -> {
            assertThat(i).isNull();
            nullExecuted.set(true);
          };

      rightNullInstance.ifRight(nullAction);
      assertThat(nullExecuted).isTrue();
    }

    @Test
    @DisplayName("ifRight() should not execute action on Left instances")
    void ifRightShouldNotExecuteOnLeft() {
      AtomicBoolean executed = new AtomicBoolean(false);
      Consumer<Integer> action = i -> executed.set(true);

      leftInstance.ifRight(action);
      assertThat(executed).isFalse();

      leftNullInstance.ifRight(action);
      assertThat(executed).isFalse();
    }

    @Test
    @DisplayName("Side effect methods should validate null actions")
    void sideEffectMethodsShouldValidateNullActions() {
      ValidationTestBuilder.create()
          .assertNullFunction(() -> leftInstance.ifLeft(null), "action")
          .assertNullFunction(() -> rightInstance.ifLeft(null), "action")
          .assertNullFunction(() -> rightInstance.ifRight(null), "action")
          .assertNullFunction(() -> leftInstance.ifRight(null), "action")
          .execute();
    }

    @Test
    @DisplayName("Side effect methods comprehensive testing")
    void sideEffectMethodsComprehensiveTesting() {
      AtomicBoolean leftActionExecuted = new AtomicBoolean(false);
      AtomicBoolean rightActionExecuted = new AtomicBoolean(false);

      Consumer<String> leftAction = s -> leftActionExecuted.set(true);
      Consumer<Integer> rightAction = i -> rightActionExecuted.set(true);

      // Test chaining side effects
      Either<String, Integer> testInstance = rightInstance;
      testInstance.ifLeft(leftAction);
      testInstance.ifRight(rightAction);

      assertThat(leftActionExecuted).isFalse();
      assertThat(rightActionExecuted).isTrue();

      // Reset and test with Left instance
      leftActionExecuted.set(false);
      rightActionExecuted.set(false);

      Either<String, Integer> leftTestInstance = leftInstance;
      leftTestInstance.ifLeft(leftAction);
      leftTestInstance.ifRight(rightAction);

      assertThat(leftActionExecuted).isTrue();
      assertThat(rightActionExecuted).isFalse();
    }

    @Test
    @DisplayName("Side effect methods should handle exceptions in actions")
    void sideEffectMethodsShouldHandleActionExceptions() {
      RuntimeException testException = createTestException("side effect test");
      Consumer<String> throwingLeftAction =
          s -> {
            throw testException;
          };
      Consumer<Integer> throwingRightAction =
          i -> {
            throw testException;
          };

      // Actions that throw should propagate the exception
      assertThatThrownBy(() -> leftInstance.ifLeft(throwingLeftAction)).isSameAs(testException);

      assertThatThrownBy(() -> rightInstance.ifRight(throwingRightAction)).isSameAs(testException);

      // Actions that don't execute shouldn't throw
      leftInstance.ifRight(throwingRightAction); // Should not throw
      rightInstance.ifLeft(throwingLeftAction); // Should not throw
    }
  }

  @Nested
  @DisplayName("toString() Method Tests")
  class ToStringMethodTests {

    @Test
    @DisplayName("toString() should return correct format for Left instances")
    void toStringShouldFormatLeftCorrectly() {
      assertThat(leftInstance.toString()).isEqualTo("Left(" + leftValue + ")");
      assertThat(leftNullInstance.toString()).isEqualTo("Left(null)");

      Either<RuntimeException, String> exceptionLeft = Either.left(new RuntimeException("test"));
      assertThat(exceptionLeft.toString()).contains("Left(java.lang.RuntimeException: test)");
    }

    @Test
    @DisplayName("toString() should return correct format for Right instances")
    void toStringShouldFormatRightCorrectly() {
      assertThat(rightInstance.toString()).isEqualTo("Right(" + rightValue + ")");
      assertThat(rightNullInstance.toString()).isEqualTo("Right(null)");

      Either<String, java.util.List<String>> listRight = Either.right(java.util.List.of("a", "b"));
      assertThat(listRight.toString()).isEqualTo("Right([a, b])");
    }

    @Test
    @DisplayName("toString() comprehensive testing with complex types")
    void toStringComprehensiveTesting() {
      // Test with nested structures
      Either<String, java.util.Map<String, Integer>> mapRight =
          Either.right(java.util.Map.of("key1", 1, "key2", 2));
      String mapToString = mapRight.toString();
      assertThat(mapToString).startsWith("Right(");
      assertThat(mapToString).contains("key1");
      assertThat(mapToString).contains("key2");

      // Test with custom objects
      record TestRecord(String name, int value) {}
      Either<String, TestRecord> recordRight = Either.right(new TestRecord("test", 42));
      assertThat(recordRight.toString()).isEqualTo("Right(TestRecord[name=test, value=42])");
    }
  }

  @Nested
  @DisplayName("equals() and hashCode() Tests")
  class EqualsHashCodeTests {

    @Test
    @DisplayName("Left instances should have correct equals behavior")
    void leftInstancesEqualsBehavior() {
      Either<String, Integer> left1 = Either.left("A");
      Either<String, Integer> left2 = Either.left("A");
      Either<String, Integer> left3 = Either.left("B");
      Either<String, Integer> right1 = Either.right(1);

      assertThat(left1).isEqualTo(left2);
      assertThat(left1).hasSameHashCodeAs(left2);
      assertThat(left1).isNotEqualTo(left3);
      assertThat(left1).isNotEqualTo(right1);
      assertThat(left1).isNotEqualTo(null);
      assertThat(left1).isNotEqualTo("A");
    }

    @Test
    @DisplayName("Right instances should have correct equals behavior")
    void rightInstancesEqualsBehavior() {
      Either<String, Integer> right1 = Either.right(1);
      Either<String, Integer> right2 = Either.right(1);
      Either<String, Integer> right3 = Either.right(2);
      Either<String, Integer> left1 = Either.left("A");

      assertThat(right1).isEqualTo(right2);
      assertThat(right1).hasSameHashCodeAs(right2);
      assertThat(right1).isNotEqualTo(right3);
      assertThat(right1).isNotEqualTo(left1);
      assertThat(right1).isNotEqualTo(null);
      assertThat(right1).isNotEqualTo(1);
    }

    @Test
    @DisplayName("Null value instances should have correct equals behavior")
    void nullValueInstancesEqualsBehavior() {
      Either<String, Integer> leftNull1 = Either.left(null);
      Either<String, Integer> leftNull2 = Either.left(null);
      Either<String, Integer> rightNull1 = Either.right(null);
      Either<String, Integer> rightNull2 = Either.right(null);

      assertThat(leftNull1).isEqualTo(leftNull2);
      assertThat(leftNull1).hasSameHashCodeAs(leftNull2);
      assertThat(rightNull1).isEqualTo(rightNull2);
      assertThat(rightNull1).hasSameHashCodeAs(rightNull2);

      assertThat(leftNull1).isNotEqualTo(rightNull1);
      assertThat(leftNull1).isNotEqualTo(leftInstance);
      assertThat(rightNull1).isNotEqualTo(rightInstance);
    }

    @Test
    @DisplayName("equals() and hashCode() comprehensive testing")
    void equalsHashCodeComprehensiveTesting() {
      // Test with complex objects
      record ComplexRecord(String name, java.util.List<Integer> numbers) {}

      ComplexRecord record1 = new ComplexRecord("test", java.util.List.of(1, 2, 3));
      ComplexRecord record2 = new ComplexRecord("test", java.util.List.of(1, 2, 3));
      ComplexRecord record3 = new ComplexRecord("different", java.util.List.of(1, 2, 3));

      Either<String, ComplexRecord> right1 = Either.right(record1);
      Either<String, ComplexRecord> right2 = Either.right(record2);
      Either<String, ComplexRecord> right3 = Either.right(record3);

      assertThat(right1).isEqualTo(right2);
      assertThat(right1).hasSameHashCodeAs(right2);
      assertThat(right1).isNotEqualTo(right3);

      // Test with different generic type parameters but same values
      Either<RuntimeException, String> stringRight1 = Either.right("value");
      Either<IllegalArgumentException, String> stringRight2 = Either.right("value");

      // These should be equal despite different Left type parameters
      // since they're both Right instances with the same value
      assertThat(stringRight1).isEqualTo(stringRight2);
    }
  }

  @Nested
  @DisplayName("Integration with Testing Framework")
  class IntegrationTests {

    @Test
    @DisplayName("Either instances work with common test functions")
    void eitherWithCommonTestFunctions() {
      Either<String, Integer> numberEither = Either.right(100);

      // Test with common test functions from HKTTestHelpers
      Either<String, String> stringResult = numberEither.map(CommonTestFunctions.INT_TO_STRING);
      assertThat(stringResult.getRight()).isEqualTo("100");

      Either<String, String> suffixResult = stringResult.map(CommonTestFunctions.APPEND_SUFFIX);
      assertThat(suffixResult.getRight()).isEqualTo("100_test");

      Either<String, Integer> doubledResult = numberEither.map(CommonTestFunctions.MULTIPLY_BY_2);
      assertThat(doubledResult.getRight()).isEqualTo(200);
    }

    @Test
    @DisplayName("Either instances work with exception testing utilities")
    void eitherWithExceptionTestingUtilities() {
      Either<String, Integer> numberEither = Either.right(50);
      Either<String, Integer> leftEither = Either.left("error");

      RuntimeException testException = createTestException("Either integration test");
      Function<Integer, String> throwingFunction =
          CommonTestFunctions.throwingFunction(testException);

      // Test exception propagation on Right
      assertThatThrownBy(() -> numberEither.map(throwingFunction)).isSameAs(testException);

      // Test that Left instances don't execute throwing functions
      Either<String, String> leftResult = leftEither.map(throwingFunction);
      assertThat(leftResult).isSameAs(leftEither);

      // Test with flatMap
      Function<Integer, Either<String, String>> throwingFlatMapper =
          CommonTestFunctions.throwingFunction(testException);
      assertThatThrownBy(() -> numberEither.flatMap(throwingFlatMapper)).isSameAs(testException);
    }

    @Test
    @DisplayName("Either instances work with validation testing framework")
    void eitherWithValidationTesting() {
      Either<String, Integer> testEither = Either.right(42);

      // Test that Either properly validates null parameters using our framework
      ValidationTestBuilder.create()
          .assertNullFunction(() -> testEither.map(null), "mapper")
          .assertNullFunction(() -> testEither.flatMap(null), "mapper")
          .assertNullFunction(() -> testEither.fold(null, Object::toString), "leftMapper")
          .assertNullFunction(() -> testEither.fold(Object::toString, null), "rightMapper")
          .assertNullFunction(() -> testEither.ifLeft(null), "action")
          .assertNullFunction(() -> testEither.ifRight(null), "action")
          .execute();
    }
  }

  @Nested
  @DisplayName("Edge Cases and Corner Cases")
  class EdgeCasesTests {

    @Test
    @DisplayName("Either should handle deeply nested operations")
    void eitherShouldHandleDeeplyNestedOperations() {
      Either<String, Integer> start = Either.right(1);

      // Build a deeply nested chain of operations
      Either<String, Integer> result = start;
      for (int i = 0; i < 100; i++) {
        final int increment = i;
        result = result.flatMap(x -> Either.right(x + increment));
      }

      // Sum should be 1 + 0 + 1 + 2 + ... + 99 = 1 + 4950 = 4951
      int expectedSum = 1 + (99 * 100) / 2;
      assertThat(result.getRight()).isEqualTo(expectedSum);

      // Test with early failure
      Either<String, Integer> failingChain = Either.right(1);
      for (int i = 0; i < 50; i++) {
        final int index = i;
        failingChain =
            failingChain.flatMap(
                x -> {
                  if (index == 25) {
                    return Either.left("Failed at step " + index);
                  }
                  return Either.right(x + index);
                });
      }

      assertThat(failingChain.isLeft()).isTrue();
      assertThat(failingChain.getLeft()).isEqualTo("Failed at step 25");
    }

    @Test
    @DisplayName("Either should handle complex data structures")
    void eitherShouldHandleComplexDataStructures() {
      // Test with nested collections
      Either<String, java.util.Map<String, java.util.List<Integer>>> complexEither =
          Either.right(
              java.util.Map.of(
                  "numbers", java.util.List.of(1, 2, 3, 4, 5),
                  "evens", java.util.List.of(2, 4, 6, 8)));

      Either<String, Integer> sumResult =
          complexEither.map(
              map ->
                  map.values().stream()
                      .flatMap(java.util.Collection::stream)
                      .mapToInt(Integer::intValue)
                      .sum());

      assertThat(sumResult.getRight()).isEqualTo(35); // 1+2+3+4+5+2+4+6+8 = 35

      // Test with nested Either operations
      Either<String, Either<String, Integer>> nestedEither = Either.right(Either.right(100));
      Either<String, Integer> flattened =
          nestedEither.flatMap(
              inner ->
                  inner.fold(err -> Either.left("Inner error: " + err), val -> Either.right(val)));

      assertThat(flattened.getRight()).isEqualTo(100);
    }

    @Test
    @DisplayName("Either should handle all combinations of null scenarios")
    void eitherShouldHandleAllNullScenarios() {
      // Test all combinations of null values and operations
      java.util.List<Either<String, String>> testCases =
          java.util.List.of(
              Either.left(null), Either.left("error"), Either.right(null), Either.right("value"));

      for (Either<String, String> testCase : testCases) {
        // All these operations should work without throwing unexpected exceptions

        // Test fold with null handling
        String foldResult =
            testCase.fold(
                left -> left == null ? "null left" : "left: " + left,
                right -> right == null ? "null right" : "right: " + right);
        assertThat(foldResult).isNotNull();

        // Test map with null-aware function
        Either<String, String> mapResult =
            testCase.map(s -> s == null ? "mapped null" : "mapped: " + s);
        assertThat(mapResult).isNotNull();

        // Test flatMap with null-aware function
        Either<String, String> flatMapResult =
            testCase.flatMap(s -> Either.right(s == null ? "flatmapped null" : "flatmapped: " + s));
        assertThat(flatMapResult).isNotNull();

        // Test side effects
        AtomicBoolean leftExecuted = new AtomicBoolean(false);
        AtomicBoolean rightExecuted = new AtomicBoolean(false);

        testCase.ifLeft(left -> leftExecuted.set(true));
        testCase.ifRight(right -> rightExecuted.set(true));

        // Exactly one should have executed
        assertThat(leftExecuted.get() ^ rightExecuted.get()).isTrue();
      }
    }

    @Test
    @DisplayName("Either should maintain type safety across complex transformations")
    void eitherShouldMaintainTypeSafetyAcrossTransformations() {
      // Start with a complex type
      record Person(String name, int age, java.util.List<String> hobbies) {}

      Person person = new Person("Alice", 30, java.util.List.of("reading", "hiking"));
      Either<RuntimeException, Person> personEither = Either.right(person);

      // Chain multiple transformations maintaining type safety
      Either<RuntimeException, String> summary =
          personEither
              .map(
                  p ->
                      new StringBuilder()
                          .append(p.name())
                          .append(" is ")
                          .append(p.age())
                          .append(" years old")
                          .toString())
              .flatMap(s -> Either.right(s + " and has " + person.hobbies().size() + " hobbies"))
              .map(s -> s.toUpperCase());

      assertThat(summary.getRight()).isEqualTo("ALICE IS 30 YEARS OLD AND HAS 2 HOBBIES");

      // Test error propagation through type transformations
      Either<RuntimeException, Person> errorEither =
          Either.left(new RuntimeException("Person not found"));

      Either<RuntimeException, String> errorResult =
          errorEither
              .map(Person::name)
              .flatMap(name -> Either.right("Hello " + name))
              .map(String::toUpperCase);

      assertThat(errorResult.isLeft()).isTrue();
      assertThat(errorResult.getLeft().getMessage()).isEqualTo("Person not found");
    }
  }

  @Nested
  @DisplayName("Performance and Memory Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Either should handle large data efficiently")
    void eitherShouldHandleLargeDataEfficiently() {
      // Create a large list
      java.util.List<Integer> largeList =
          java.util.stream.IntStream.range(0, 10000)
              .boxed()
              .collect(java.util.stream.Collectors.toList());

      Either<String, java.util.List<Integer>> largeEither = Either.right(largeList);

      // Transform the large list
      Either<String, java.util.List<Integer>> transformed =
          largeEither
              .map(
                  list ->
                      list.stream()
                          .filter(n -> n % 2 == 0)
                          .collect(java.util.stream.Collectors.toList()))
              .map(
                  list ->
                      list.stream().map(n -> n * 2).collect(java.util.stream.Collectors.toList()));

      assertThat(transformed.getRight()).hasSize(5000);
      assertThat(transformed.getRight().get(0)).isEqualTo(0);
      assertThat(transformed.getRight().get(4999)).isEqualTo(19996); // 9998 * 2
    }

    @Test
    @DisplayName("Either operations should be efficient with repeated transformations")
    void eitherOperationsShouldBeEfficientWithRepeatedTransformations() {
      Either<String, String> start = Either.right("start");

      // Apply many transformations
      Either<String, String> result = start;
      for (int i = 0; i < 1000; i++) {
        final int index = i;
        result = result.map(s -> s + "_" + index);
      }

      assertThat(result.getRight()).startsWith("start_0_1_2");
      assertThat(result.getRight()).endsWith("_999");

      // Verify that Left instances are efficient (no transformations applied)
      Either<String, String> leftStart = Either.left("error");
      Either<String, String> leftResult = leftStart;

      for (int i = 0; i < 1000; i++) {
        final int index = i;
        leftResult = leftResult.map(s -> s + "_" + index);
      }

      // Should be the exact same instance since Left doesn't transform
      assertThat(leftResult).isSameAs(leftStart);
      assertThat(leftResult.getLeft()).isEqualTo("error");
    }
  }

  @Nested
  @DisplayName("Comprehensive Documentation Examples")
  class DocumentationExamplesTests {

    @Test
    @DisplayName("Test examples from Either class documentation")
    void testDocumentationExamples() {
      // Example from the class documentation
      Either<String, Integer> result = parseInteger("123");
      result.fold(
          error -> {
            assertThat(error).startsWith("Invalid number format");
            return null;
          },
          value -> {
            assertThat(value).isEqualTo(123);
            return null;
          });

      Either<String, Integer> length = result.map(v -> v * 2);
      assertThat(length.getRight()).isEqualTo(246);

      // Test with invalid input
      Either<String, Integer> invalidResult = parseInteger("abc");
      assertThat(invalidResult.isLeft()).isTrue();
      assertThat(invalidResult.getLeft()).isEqualTo("Invalid number format: abc");

      Either<String, Integer> invalidMapped = invalidResult.map(v -> v * 2);
      assertThat(invalidMapped).isSameAs(invalidResult);
    }

    @Test
    @DisplayName("Test practical usage patterns")
    void testPracticalUsagePatterns() {
      // Validation pattern
      Either<String, String> emailValidation = validateEmail("user@example.com");
      Either<String, String> passwordValidation = validatePassword("strongPassword123!");

      // Combine validations
      Either<String, String> combined =
          emailValidation.flatMap(
              email ->
                  passwordValidation.map(
                      password ->
                          String.format("User: %s, Password: %s chars", email, password.length())));

      assertThat(combined.getRight()).isEqualTo("User: user@example.com, Password: 18 chars");

      // Error accumulation pattern (though Either is fail-fast, not accumulating)
      Either<String, String> invalidEmail = validateEmail("invalid-email");
      Either<String, String> invalidCombined =
          invalidEmail.flatMap(
              email -> passwordValidation.map(password -> "Should not reach here"));

      assertThat(invalidCombined.isLeft()).isTrue();
      assertThat(invalidCombined.getLeft()).isEqualTo("Invalid email format");
    }

    // Helper methods for documentation examples
    private Either<String, Integer> parseInteger(String s) {
      try {
        return Either.right(Integer.parseInt(s));
      } catch (NumberFormatException e) {
        return Either.left("Invalid number format: " + s);
      }
    }

    private Either<String, String> validateEmail(String email) {
      if (email.contains("@") && email.contains(".")) {
        return Either.right(email);
      }
      return Either.left("Invalid email format");
    }

    private Either<String, String> validatePassword(String password) {
      if (password.length() >= 8) {
        return Either.right(password);
      }
      return Either.left("Password too short");
    }
  }
}
