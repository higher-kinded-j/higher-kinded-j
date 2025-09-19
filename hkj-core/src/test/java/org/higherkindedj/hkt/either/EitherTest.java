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

@DisplayName("Either<L, R> Core Functionality Tests")
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
}
