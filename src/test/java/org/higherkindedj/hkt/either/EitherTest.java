package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;

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
  private final Either<String, Integer> leftNullInstance = Either.left(null); // Test null Left
  private final Either<String, Integer> rightNullInstance = Either.right(null); // Test null Right

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {
    @Test
    void left_shouldCreateLeftInstance() {
      assertThat(leftInstance).isInstanceOf(Either.Left.class);
      assertThat(leftInstance.isLeft()).isTrue();
      assertThat(leftInstance.isRight()).isFalse();
      assertThat(leftInstance.getLeft()).isEqualTo(leftValue);
    }

    @Test
    void left_shouldAllowNullValue() {
      assertThat(leftNullInstance).isInstanceOf(Either.Left.class);
      assertThat(leftNullInstance.isLeft()).isTrue();
      assertThat(leftNullInstance.getLeft()).isNull();
    }

    @Test
    void right_shouldCreateRightInstance() {
      assertThat(rightInstance).isInstanceOf(Either.Right.class);
      assertThat(rightInstance.isRight()).isTrue();
      assertThat(rightInstance.isLeft()).isFalse();
      assertThat(rightInstance.getRight()).isEqualTo(rightValue);
    }

    @Test
    void right_shouldAllowNullValue() {
      assertThat(rightNullInstance).isInstanceOf(Either.Right.class);
      assertThat(rightNullInstance.isRight()).isTrue();
      assertThat(rightNullInstance.getRight()).isNull();
    }
  }

  @Nested
  @DisplayName("Getters")
  class Getters {
    @Test
    void getLeft_onLeft_shouldReturnValue() {
      assertThat(leftInstance.getLeft()).isEqualTo(leftValue);
      assertThat(leftNullInstance.getLeft()).isNull();
    }

    @Test
    void getLeft_onRight_shouldThrowException() {
      assertThatThrownBy(rightInstance::getLeft)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot getLeft() on a Right");
      assertThatThrownBy(rightNullInstance::getLeft).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getRight_onRight_shouldReturnValue() {
      assertThat(rightInstance.getRight()).isEqualTo(rightValue);
      assertThat(rightNullInstance.getRight()).isNull();
    }

    @Test
    void getRight_onLeft_shouldThrowException() {
      assertThatThrownBy(leftInstance::getRight)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot getRight() on a Left");
      assertThatThrownBy(leftNullInstance::getRight).isInstanceOf(NoSuchElementException.class);
    }
  }

  @Nested
  @DisplayName("fold()")
  class FoldTests {
    final Function<String, String> leftMapper = l -> "Left mapped: " + l;
    final Function<Integer, String> rightMapper = r -> "Right mapped: " + r;

    @Test
    void fold_onLeft_shouldApplyLeftMapper() {
      String result = leftInstance.fold(leftMapper, rightMapper);
      assertThat(result).isEqualTo("Left mapped: " + leftValue);
    }

    @Test
    void fold_onLeftWithNull_shouldApplyLeftMapper() {
      String result = leftNullInstance.fold(leftMapper, rightMapper);
      assertThat(result).isEqualTo("Left mapped: null");
    }

    @Test
    void fold_onRight_shouldApplyRightMapper() {
      String result = rightInstance.fold(leftMapper, rightMapper);
      assertThat(result).isEqualTo("Right mapped: " + rightValue);
    }

    @Test
    void fold_onRightWithNull_shouldApplyRightMapper() {
      String result = rightNullInstance.fold(leftMapper, rightMapper);
      assertThat(result).isEqualTo("Right mapped: null");
    }

    @Test
    void fold_onLeft_shouldThrowIfLeftMapperIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> leftInstance.fold(null, rightMapper))
          .withMessageContaining("leftMapper cannot be null");
    }

    @Test
    void fold_onRight_shouldThrowIfRightMapperIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> rightInstance.fold(leftMapper, null))
          .withMessageContaining("rightMapper cannot be null");
    }
  }

  @Nested
  @DisplayName("map()")
  class MapTests {
    final Function<Integer, String> mapper = i -> "Mapped: " + i;

    @Test
    void map_onRight_shouldApplyMapperAndReturnRight() {
      Either<String, String> result = rightInstance.map(mapper);
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo("Mapped: " + rightValue);
    }

    @Test
    void map_onRightWithNull_shouldApplyMapperAndReturnRight() {
      Either<String, String> result = rightNullInstance.map(mapper);
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo("Mapped: null");
    }

    @Test
    void map_onLeft_shouldReturnSameLeftInstance() {
      Either<String, String> result = leftInstance.map(mapper);
      assertThat(result).isSameAs(leftInstance); // Should return the same instance
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo(leftValue);
    }

    @Test
    void map_onLeftWithNull_shouldReturnSameLeftInstance() {
      Either<String, String> result = leftNullInstance.map(mapper);
      assertThat(result).isSameAs(leftNullInstance);
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isNull();
    }

    @Test
    void map_shouldThrowIfMapperIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> rightInstance.map(null))
          .withMessageContaining("mapper function cannot be null");
      // Left side doesn't execute the mapper, but *does* check for null first in the impl
      assertThatNullPointerException()
          .isThrownBy(() -> leftInstance.map(null))
          .withMessageContaining("mapper function cannot be null");
    }
  }

  @Nested
  @DisplayName("flatMap()")
  class FlatMapTests {
    final Function<Integer, Either<String, String>> mapperRight =
        i -> Either.right("FlatMapped: " + i);
    final Function<Integer, Either<String, String>> mapperLeft = i -> Either.left("FlatMap Error");
    final Function<Integer, Either<String, String>> mapperNull = i -> null;

    @Test
    void flatMap_onRight_shouldApplyMapperAndReturnResult() {
      Either<String, String> result = rightInstance.flatMap(mapperRight);
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo("FlatMapped: " + rightValue);

      Either<String, String> resultLeft = rightInstance.flatMap(mapperLeft);
      assertThat(resultLeft.isLeft()).isTrue();
      assertThat(resultLeft.getLeft()).isEqualTo("FlatMap Error");
    }

    @Test
    void flatMap_onRightWithNull_shouldApplyMapperAndReturnResult() {
      Either<String, String> result = rightNullInstance.flatMap(mapperRight);
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo("FlatMapped: null");
    }

    @Test
    void flatMap_onLeft_shouldReturnSameLeftInstance() {
      Either<String, String> result = leftInstance.flatMap(mapperRight);
      assertThat(result).isSameAs(leftInstance); // Should return the same instance
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo(leftValue);
    }

    @Test
    void flatMap_onLeftWithNull_shouldReturnSameLeftInstance() {
      Either<String, String> result = leftNullInstance.flatMap(mapperRight);
      assertThat(result).isSameAs(leftNullInstance);
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isNull();
    }

    @Test
    void flatMap_shouldThrowIfMapperIsNull() {
      // Both Left and Right implementations check for null mapper first
      assertThatNullPointerException()
          .isThrownBy(() -> rightInstance.flatMap(null))
          .withMessageContaining("mapper function cannot be null");

      assertThatNullPointerException()
          .isThrownBy(() -> leftInstance.flatMap(null)) // <-- Changed Assertion
          .withMessageContaining("mapper function cannot be null");
    }

    @Test
    void flatMap_shouldThrowIfMapperReturnsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> rightInstance.flatMap(mapperNull))
          .withMessageContaining("flatMap mapper returned null Either");
    }
  }

  @Nested
  @DisplayName("ifLeft() / ifRight()")
  class SideEffectTests {

    @Test
    void ifLeft_onLeft_shouldExecuteAction() {
      AtomicBoolean executed = new AtomicBoolean(false);
      Consumer<String> action =
          s -> {
            assertThat(s).isEqualTo(leftValue);
            executed.set(true);
          };
      leftInstance.ifLeft(action);
      assertThat(executed).isTrue();
    }

    @Test
    void ifLeft_onRight_shouldNotExecuteAction() {
      AtomicBoolean executed = new AtomicBoolean(false);
      Consumer<String> action = s -> executed.set(true);
      rightInstance.ifLeft(action);
      assertThat(executed).isFalse();
    }

    @Test
    void ifRight_onRight_shouldExecuteAction() {
      AtomicBoolean executed = new AtomicBoolean(false);
      Consumer<Integer> action =
          i -> {
            assertThat(i).isEqualTo(rightValue);
            executed.set(true);
          };
      rightInstance.ifRight(action);
      assertThat(executed).isTrue();
    }

    @Test
    void ifRight_onLeft_shouldNotExecuteAction() {
      AtomicBoolean executed = new AtomicBoolean(false);
      Consumer<Integer> action = i -> executed.set(true);
      leftInstance.ifRight(action);
      assertThat(executed).isFalse();
    }

    @Test
    void ifLeft_shouldThrowIfActionIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> leftInstance.ifLeft(null))
          .withMessageContaining("action cannot be null");
      assertThatNullPointerException()
          .isThrownBy(() -> rightInstance.ifLeft(null))
          .withMessageContaining("action cannot be null");
    }

    @Test
    void ifRight_shouldThrowIfActionIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> rightInstance.ifRight(null))
          .withMessageContaining("action cannot be null");
      assertThatNullPointerException()
          .isThrownBy(() -> leftInstance.ifRight(null))
          .withMessageContaining("action cannot be null");
    }
  }

  @Nested
  @DisplayName("toString()")
  class ToStringTests {
    @Test
    void toString_onLeft() {
      assertThat(leftInstance.toString()).isEqualTo("Left(" + leftValue + ")");
      assertThat(leftNullInstance.toString()).isEqualTo("Left(null)");
    }

    @Test
    void toString_onRight() {
      assertThat(rightInstance.toString()).isEqualTo("Right(" + rightValue + ")");
      assertThat(rightNullInstance.toString()).isEqualTo("Right(null)");
    }
  }

  @Nested
  @DisplayName("equals() and hashCode()")
  class EqualsHashCodeTests {
    @Test
    void leftEquals() {
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
    void rightEquals() {
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
    void nullValueEquals() {
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
  }

  // Added test for map null check on Left
  @Test
  void map_onLeft_shouldThrowIfMapperIsNull() {
    assertThatNullPointerException()
        .isThrownBy(() -> leftInstance.map(null))
        .withMessageContaining("mapper function cannot be null");
  }
}
