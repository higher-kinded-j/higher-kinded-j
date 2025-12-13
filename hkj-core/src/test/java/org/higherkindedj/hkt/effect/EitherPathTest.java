// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for EitherPath.
 *
 * <p>Tests cover factory methods, Composable/Combinable/Chainable/Recoverable operations, utility
 * methods, and object methods.
 */
@DisplayName("EitherPath<E, A> Complete Test Suite")
class EitherPathTest {

  private static final String TEST_VALUE = "test";
  private static final String TEST_ERROR = "error";
  private static final Integer TEST_INT = 42;

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.right() creates EitherPath with right value")
    void pathRightCreatesEitherPathWithRightValue() {
      EitherPath<String, Integer> path = Path.right(TEST_INT);

      assertThat(path.run().isRight()).isTrue();
      assertThat(path.run().getRight()).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("Path.left() creates EitherPath with left value")
    void pathLeftCreatesEitherPathWithLeftValue() {
      EitherPath<String, Integer> path = Path.left(TEST_ERROR);

      assertThat(path.run().isLeft()).isTrue();
      assertThat(path.run().getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("Path.either() creates EitherPath from Either")
    void pathEitherCreatesEitherPathFromEither() {
      Either<String, Integer> right = Either.right(TEST_INT);
      Either<String, Integer> left = Either.left(TEST_ERROR);

      EitherPath<String, Integer> rightPath = Path.either(right);
      EitherPath<String, Integer> leftPath = Path.either(left);

      assertThat(rightPath.run().isRight()).isTrue();
      assertThat(rightPath.run().getRight()).isEqualTo(TEST_INT);
      assertThat(leftPath.run().isLeft()).isTrue();
      assertThat(leftPath.run().getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("Path.right() accepts null value")
    void pathRightAcceptsNull() {
      // Either allows null values
      EitherPath<String, String> path = Path.right(null);
      assertThat(path.run().isRight()).isTrue();
      assertThat(path.run().getRight()).isNull();
    }

    @Test
    @DisplayName("Path.left() accepts null error")
    void pathLeftAcceptsNull() {
      // Either allows null values
      EitherPath<String, String> path = Path.left(null);
      assertThat(path.run().isLeft()).isTrue();
      assertThat(path.run().getLeft()).isNull();
    }
  }

  @Nested
  @DisplayName("Run and Getter Methods")
  class RunAndGetterMethodsTests {

    @Test
    @DisplayName("run() returns underlying Either")
    void runReturnsUnderlyingEither() {
      EitherPath<String, Integer> rightPath = Path.right(TEST_INT);
      EitherPath<String, Integer> leftPath = Path.left(TEST_ERROR);

      assertThat(rightPath.run()).isInstanceOf(Either.class);
      assertThat(rightPath.run().getRight()).isEqualTo(TEST_INT);
      assertThat(leftPath.run().getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("getOrElse() returns value for Right")
    void getOrElseReturnsValueForRight() {
      EitherPath<String, Integer> path = Path.right(TEST_INT);

      assertThat(path.getOrElse(0)).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("getOrElse() returns default for Left")
    void getOrElseReturnsDefaultForLeft() {
      EitherPath<String, Integer> path = Path.left(TEST_ERROR);

      assertThat(path.getOrElse(0)).isEqualTo(0);
    }

    @Test
    @DisplayName("getOrElseGet() returns value for Right without calling supplier")
    void getOrElseGetReturnsValueForRightWithoutCallingSupplier() {
      EitherPath<String, Integer> path = Path.right(TEST_INT);
      AtomicBoolean called = new AtomicBoolean(false);

      Integer result =
          path.getOrElseGet(
              () -> {
                called.set(true);
                return 0;
              });

      assertThat(result).isEqualTo(TEST_INT);
      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("getOrElseGet() calls supplier for Left")
    void getOrElseGetCallsSupplierForLeft() {
      EitherPath<String, Integer> path = Path.left(TEST_ERROR);
      AtomicBoolean called = new AtomicBoolean(false);

      Integer result =
          path.getOrElseGet(
              () -> {
                called.set(true);
                return 0;
              });

      assertThat(result).isEqualTo(0);
      assertThat(called).isTrue();
    }

    @Test
    @DisplayName("fold() applies appropriate function")
    void foldAppliesAppropriateFunction() {
      EitherPath<String, Integer> rightPath = Path.right(TEST_INT);
      EitherPath<String, Integer> leftPath = Path.left(TEST_ERROR);

      String rightResult = rightPath.fold(error -> "error: " + error, value -> "value: " + value);

      String leftResult = leftPath.fold(error -> "error: " + error, value -> "value: " + value);

      assertThat(rightResult).isEqualTo("value: 42");
      assertThat(leftResult).isEqualTo("error: error");
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value for Right")
    void mapTransformsValueForRight() {
      EitherPath<String, String> path = Path.right(TEST_VALUE);

      EitherPath<String, Integer> result = path.map(String::length);

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE.length());
    }

    @Test
    @DisplayName("map() preserves Left")
    void mapPreservesLeft() {
      EitherPath<String, String> path = Path.left(TEST_ERROR);

      EitherPath<String, Integer> result = path.map(String::length);

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      EitherPath<String, String> path = Path.right(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      EitherPath<String, String> path = Path.right("hello");

      EitherPath<String, String> result =
          path.map(String::toUpperCase).map(s -> s + "!").map(s -> s.repeat(2));

      assertThat(result.run().getRight()).isEqualTo("HELLO!HELLO!");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      EitherPath<String, String> path = Path.right(TEST_VALUE);
      AtomicBoolean called = new AtomicBoolean(false);

      EitherPath<String, String> result = path.peek(v -> called.set(true));

      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
      assertThat(called).isTrue();
    }

    @Test
    @DisplayName("peek() does not call consumer for Left")
    void peekDoesNotCallConsumerForLeft() {
      EitherPath<String, String> path = Path.left(TEST_ERROR);
      AtomicBoolean called = new AtomicBoolean(false);

      path.peek(v -> called.set(true));

      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("peekLeft() observes error without modifying")
    void peekLeftObservesErrorWithoutModifying() {
      EitherPath<String, String> path = Path.left(TEST_ERROR);
      AtomicBoolean called = new AtomicBoolean(false);

      EitherPath<String, String> result = path.peekLeft(e -> called.set(true));

      assertThat(result.run().getLeft()).isEqualTo(TEST_ERROR);
      assertThat(called).isTrue();
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains dependent computations for Right")
    void viaChainsComputationsForRight() {
      EitherPath<String, String> path = Path.right("hello");

      EitherPath<String, Integer> result = path.via(s -> Path.right(s.length()));

      assertThat(result.run().getRight()).isEqualTo(5);
    }

    @Test
    @DisplayName("via() preserves Left")
    void viaPreservesLeft() {
      EitherPath<String, String> path = Path.left(TEST_ERROR);

      EitherPath<String, Integer> result = path.via(s -> Path.right(s.length()));

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("via() short-circuits on Left result")
    void viaShortCircuitsOnLeft() {
      EitherPath<String, String> path = Path.right("hello");

      EitherPath<String, Integer> result =
          path.via(s -> Path.<String, Integer>left("failed")).via(i -> Path.right(i * 2));

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo("failed");
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      EitherPath<String, String> path = Path.right(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() validates non-null result")
    void viaValidatesNonNullResult() {
      EitherPath<String, String> path = Path.right(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(s -> null))
          .withMessageContaining("mapper must not return null");
    }

    @Test
    @DisplayName("via() validates result is EitherPath")
    void viaValidatesResultType() {
      EitherPath<String, String> path = Path.right(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.via(s -> Path.just(s.length())))
          .withMessageContaining("via mapper must return EitherPath");
    }

    @Test
    @DisplayName("then() sequences computations discarding value")
    void thenSequencesComputationsDiscardingValue() {
      EitherPath<String, String> path = Path.right("hello");
      AtomicBoolean firstExecuted = new AtomicBoolean(false);

      EitherPath<String, Integer> result =
          path.peek(v -> firstExecuted.set(true)).then(() -> Path.right(42));

      assertThat(result.run().getRight()).isEqualTo(42);
      assertThat(firstExecuted).isTrue();
    }

    @Test
    @DisplayName("then() preserves Left")
    void thenPreservesLeft() {
      EitherPath<String, String> path = Path.left(TEST_ERROR);

      EitherPath<String, Integer> result = path.then(() -> Path.right(42));

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("then() throws when supplier returns wrong type")
    void thenThrowsWhenSupplierReturnsWrongType() {
      EitherPath<String, String> path = Path.right(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.then(() -> Path.just(42)))
          .withMessageContaining("then supplier must return EitherPath");
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines two Right values")
    void zipWithCombinesTwoRightValues() {
      EitherPath<String, String> first = Path.right("hello");
      EitherPath<String, Integer> second = Path.right(3);

      EitherPath<String, String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.run().getRight()).isEqualTo("hellohellohello");
    }

    @Test
    @DisplayName("zipWith() returns Left if first is Left")
    void zipWithReturnsLeftIfFirstIsLeft() {
      EitherPath<String, String> first = Path.left("first error");
      EitherPath<String, Integer> second = Path.right(3);

      EitherPath<String, String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo("first error");
    }

    @Test
    @DisplayName("zipWith() returns Left if second is Left")
    void zipWithReturnsLeftIfSecondIsLeft() {
      EitherPath<String, String> first = Path.right("hello");
      EitherPath<String, Integer> second = Path.left("second error");

      EitherPath<String, String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo("second error");
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      EitherPath<String, String> path = Path.right(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(Path.right("x"), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith3() combines three Right values")
    void zipWith3CombinesThreeRightValues() {
      EitherPath<String, String> first = Path.right("hello");
      EitherPath<String, String> second = Path.right(" ");
      EitherPath<String, String> third = Path.right("world");

      EitherPath<String, String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().getRight()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("zipWith() throws when given non-EitherPath")
    void zipWithThrowsWhenGivenNonEitherPath() {
      EitherPath<String, String> path = Path.right(TEST_VALUE);
      MaybePath<Integer> maybePath = Path.just(TEST_INT);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(maybePath, (s, i) -> s + i))
          .withMessageContaining("Cannot zipWith non-EitherPath");
    }

    @Test
    @DisplayName("zipWith3() returns Left if first is Left")
    void zipWith3ReturnsLeftIfFirstIsLeft() {
      EitherPath<String, String> first = Path.left(TEST_ERROR);
      EitherPath<String, String> second = Path.right(" ");
      EitherPath<String, String> third = Path.right("world");

      EitherPath<String, String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("zipWith3() returns Left if second is Left")
    void zipWith3ReturnsLeftIfSecondIsLeft() {
      EitherPath<String, String> first = Path.right("hello");
      EitherPath<String, String> second = Path.left(TEST_ERROR);
      EitherPath<String, String> third = Path.right("world");

      EitherPath<String, String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo(TEST_ERROR);
    }

    @Test
    @DisplayName("zipWith3() returns Left if third is Left")
    void zipWith3ReturnsLeftIfThirdIsLeft() {
      EitherPath<String, String> first = Path.right("hello");
      EitherPath<String, String> second = Path.right(" ");
      EitherPath<String, String> third = Path.left(TEST_ERROR);

      EitherPath<String, String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo(TEST_ERROR);
    }
  }

  @Nested
  @DisplayName("Recoverable Operations")
  class RecoverableOperationsTests {

    @Test
    @DisplayName("recover() provides fallback for Left")
    void recoverProvidesFallbackForLeft() {
      EitherPath<String, String> path = Path.left(TEST_ERROR);

      EitherPath<String, String> result = path.recover(error -> "recovered from: " + error);

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo("recovered from: error");
    }

    @Test
    @DisplayName("recover() preserves Right value")
    void recoverPreservesRightValue() {
      EitherPath<String, String> path = Path.right(TEST_VALUE);

      EitherPath<String, String> result = path.recover(error -> "recovered");

      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recoverWith() provides fallback path for Left")
    void recoverWithProvidesFallbackPathForLeft() {
      EitherPath<String, String> path = Path.left(TEST_ERROR);

      EitherPath<String, String> result = path.recoverWith(error -> Path.right("fallback"));

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo("fallback");
    }

    @Test
    @DisplayName("recoverWith() preserves Right value")
    void recoverWithPreservesRightValue() {
      EitherPath<String, String> path = Path.right(TEST_VALUE);

      EitherPath<String, String> result = path.recoverWith(error -> Path.right("fallback"));

      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("orElse() provides alternative path for Left")
    void orElseAlternativeForLeft() {
      EitherPath<String, String> path = Path.left(TEST_ERROR);

      EitherPath<String, String> result = path.orElse(() -> Path.right("alternative"));

      assertThat(result.run().getRight()).isEqualTo("alternative");
    }

    @Test
    @DisplayName("orElse() preserves Right value")
    void orElsePreservesRightValue() {
      EitherPath<String, String> path = Path.right(TEST_VALUE);
      AtomicBoolean called = new AtomicBoolean(false);

      EitherPath<String, String> result =
          path.orElse(
              () -> {
                called.set(true);
                return Path.right("alternative");
              });

      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("mapError() transforms error type")
    void mapErrorTransformsErrorType() {
      EitherPath<String, String> path = Path.left("error message");

      EitherPath<Integer, String> result = path.mapError(String::length);

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo(13);
    }

    @Test
    @DisplayName("mapError() preserves Right value")
    void mapErrorPreservesRightValue() {
      EitherPath<String, String> path = Path.right(TEST_VALUE);

      EitherPath<Integer, String> result = path.mapError(String::length);

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recoverWith() throws when recovery returns wrong type")
    @SuppressWarnings("unchecked")
    void recoverWithThrowsWhenRecoveryReturnsWrongType() {
      EitherPath<String, String> path = Path.left(TEST_ERROR);

      // Use raw types to bypass compile-time type checking and test runtime validation
      assertThatIllegalArgumentException()
          .isThrownBy(
              () -> {
                var rawPath = (EitherPath) path;
                rawPath.recoverWith(error -> Path.just("fallback"));
              })
          .withMessageContaining("recovery must return EitherPath");
    }

    @Test
    @DisplayName("orElse() throws when alternative returns wrong type")
    @SuppressWarnings("unchecked")
    void orElseThrowsWhenAlternativeReturnsWrongType() {
      EitherPath<String, String> path = Path.left(TEST_ERROR);

      // Use raw types to bypass compile-time type checking and test runtime validation
      assertThatIllegalArgumentException()
          .isThrownBy(
              () -> {
                var rawPath = (EitherPath) path;
                rawPath.orElse(() -> Path.just("alternative"));
              })
          .withMessageContaining("alternative must return EitherPath");
    }
  }

  @Nested
  @DisplayName("Swap Operation")
  class SwapOperationTests {

    @Test
    @DisplayName("swap() converts Right to Left")
    void swapConvertsRightToLeft() {
      EitherPath<String, Integer> path = Path.right(TEST_INT);

      EitherPath<Integer, String> result = path.swap();

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("swap() converts Left to Right")
    void swapConvertsLeftToRight() {
      EitherPath<String, Integer> path = Path.left(TEST_ERROR);

      EitherPath<Integer, String> result = path.swap();

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(TEST_ERROR);
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toMaybePath() converts Right to Just")
    void toMaybePathConvertsRightToJust() {
      EitherPath<String, String> path = Path.right(TEST_VALUE);

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toMaybePath() converts Left to Nothing")
    void toMaybePathConvertsLeftToNothing() {
      EitherPath<String, String> path = Path.left(TEST_ERROR);

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("toTryPath() converts Right to Success")
    void toTryPathConvertsRightToSuccess() {
      EitherPath<String, String> path = Path.right(TEST_VALUE);

      TryPath<String> result = path.toTryPath(RuntimeException::new);

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.getOrElse("default")).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toTryPath() converts Left to Failure")
    void toTryPathConvertsLeftToFailure() {
      EitherPath<String, String> path = Path.left(TEST_ERROR);

      TryPath<String> result = path.toTryPath(RuntimeException::new);

      assertThat(result.run().isFailure()).isTrue();
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("equals() works correctly")
    void equalsWorksCorrectly() {
      EitherPath<String, Integer> path1 = Path.right(TEST_INT);
      EitherPath<String, Integer> path2 = Path.right(TEST_INT);
      EitherPath<String, Integer> path3 = Path.right(99);
      EitherPath<String, Integer> left1 = Path.left(TEST_ERROR);
      EitherPath<String, Integer> left2 = Path.left(TEST_ERROR);

      assertThat(path1).isEqualTo(path2);
      assertThat(path1).isNotEqualTo(path3);
      assertThat(path1).isNotEqualTo(left1);
      assertThat(left1).isEqualTo(left2);
    }

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsReturnsTrueForSameInstance() {
      EitherPath<String, Integer> path = Path.right(TEST_INT);
      assertThat(path.equals(path)).isTrue();
    }

    @Test
    @DisplayName("equals() returns false for non-EitherPath")
    void equalsReturnsFalseForNonEitherPath() {
      EitherPath<String, Integer> path = Path.right(TEST_INT);
      assertThat(path.equals("not an EitherPath")).isFalse();
      assertThat(path.equals(null)).isFalse();
      assertThat(path.equals(Path.just(TEST_INT))).isFalse();
    }

    @Test
    @DisplayName("hashCode() is consistent with equals")
    void hashCodeIsConsistentWithEquals() {
      EitherPath<String, Integer> path1 = Path.right(TEST_INT);
      EitherPath<String, Integer> path2 = Path.right(TEST_INT);

      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      EitherPath<String, Integer> rightPath = Path.right(TEST_INT);
      EitherPath<String, Integer> leftPath = Path.left(TEST_ERROR);

      assertThat(rightPath.toString()).contains("EitherPath");
      assertThat(rightPath.toString()).contains("Right");
      assertThat(leftPath.toString()).contains("EitherPath");
      assertThat(leftPath.toString()).contains("Left");
    }
  }

  @Nested
  @DisplayName("Complex Chaining Patterns")
  class ComplexChainingPatternsTests {

    @Test
    @DisplayName("Railway-oriented programming pattern")
    void railwayOrientedProgrammingPattern() {
      Function<String, EitherPath<String, Integer>> parseInteger =
          s -> {
            try {
              return Path.right(Integer.parseInt(s));
            } catch (NumberFormatException e) {
              return Path.left("Invalid number: " + s);
            }
          };

      Function<Integer, EitherPath<String, Double>> squareRoot =
          i ->
              i < 0
                  ? Path.left("Cannot take square root of negative: " + i)
                  : Path.right(Math.sqrt(i));

      EitherPath<String, Double> success =
          Path.<String, String>right("16").via(parseInteger).via(squareRoot);

      assertThat(success.run().getRight()).isEqualTo(4.0);

      EitherPath<String, Double> parseFailure =
          Path.<String, String>right("not-a-number").via(parseInteger).via(squareRoot);

      assertThat(parseFailure.run().isLeft()).isTrue();
      assertThat(parseFailure.run().getLeft()).contains("Invalid number");

      EitherPath<String, Double> negativeFailure =
          Path.<String, String>right("-4").via(parseInteger).via(squareRoot);

      assertThat(negativeFailure.run().isLeft()).isTrue();
      assertThat(negativeFailure.run().getLeft()).contains("Cannot take square root");
    }

    @Test
    @DisplayName("Combining multiple sources")
    void combiningMultipleSources() {
      EitherPath<String, String> firstName = Path.right("John");
      EitherPath<String, String> lastName = Path.right("Doe");
      EitherPath<String, Integer> age = Path.right(30);

      EitherPath<String, String> fullRecord =
          firstName.zipWith3(
              lastName, age, (first, last, a) -> String.format("%s %s, age %d", first, last, a));

      assertThat(fullRecord.run().getRight()).isEqualTo("John Doe, age 30");
    }

    @Test
    @DisplayName("Recovery with fallback chain")
    void recoveryWithFallbackChain() {
      EitherPath<String, String> primary = Path.left("primary failed");
      EitherPath<String, String> secondary = Path.left("secondary failed");
      EitherPath<String, String> tertiary = Path.right("fallback");

      EitherPath<String, String> result = primary.orElse(() -> secondary).orElse(() -> tertiary);

      assertThat(result.run().getRight()).isEqualTo("fallback");
    }

    @Test
    @DisplayName("Error transformation pattern")
    void errorTransformationPattern() {
      EitherPath<String, Integer> path = Path.left("low level error");

      record AppError(String code, String message) {}

      EitherPath<AppError, Integer> result = path.mapError(msg -> new AppError("ERR001", msg));

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft().code()).isEqualTo("ERR001");
      assertThat(result.run().getLeft().message()).isEqualTo("low level error");
    }
  }
}
