// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherAssert.assertThatEither;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.util.validation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Either core functionality test using standardised patterns.
 *
 * <p>This test focuses on the core Either functionality whilst using the standardised validation
 * framework for consistent error handling.
 */
@DisplayName("Either<L, R> Core Functionality - Standardised Test Suite")
class EitherTest extends EitherTestBase {

  private final String leftValue = "Error Message";
  private final Integer rightValue = 123;
  private final Either<String, Integer> leftInstance = Either.left(leftValue);
  private final Either<String, Integer> rightInstance = Either.right(rightValue);
  private final Either<String, Integer> leftNullInstance = Either.left(null);
  private final Either<String, Integer> rightNullInstance = Either.right(null);

  // Type class testing fixtures
  private EitherMonad<String> monad;
  private EitherFunctor<String> functor;

  @BeforeEach
  void setUpEither() {
    monad = EitherMonad.instance();
    functor = EitherFunctor.instance();
  }

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      TypeClassTest.<EitherKind.Witness<String>>monad(EitherMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(EitherFunctor.class)
          .withApFrom(EitherMonad.class)
          .withFlatMapFrom(EitherMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Functor test pattern")
    void runCompleteFunctorTestPattern() {
      TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(validMapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(equalityChecker)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Core Type Testing with TypeClassTest API")
  class CoreTypeTestingSuite {

    @Test
    @DisplayName("Test all Either core operations")
    void testAllEitherCoreOperations() {
      CoreTypeTest.<String, Integer>either(Either.class)
          .withLeft(leftInstance)
          .withRight(rightInstance)
          .withMappers(TestFunctions.INT_TO_STRING)
          .testAll();
    }

    @Test
    @DisplayName("Test Either with validation configuration")
    void testEitherWithValidationConfiguration() {
      CoreTypeTest.<String, Integer>either(Either.class)
          .withLeft(leftInstance)
          .withRight(rightInstance)
          .withMappers(TestFunctions.INT_TO_STRING)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(EitherFunctor.class)
          .withFlatMapFrom(EitherMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Test Either selective operations - basic")
    void testEitherSelectiveOperations() {
      CoreTypeTest.<String, Integer>either(Either.class)
          .withLeft(leftInstance)
          .withRight(rightInstance)
          .withMappers(TestFunctions.INT_TO_STRING)
          .onlyFactoryMethods()
          .testAll();
    }

    @Test
    @DisplayName("Test Either Selective with core type API")
    void testEitherSelectiveWithCoreTypeAPI() {
      // Create Choice instances for Selective testing
      Choice<Integer, String> choiceLeft = Selective.left(rightValue);
      Choice<Integer, String> choiceRight = Selective.right("right-value");

      Either<String, Choice<Integer, String>> eitherChoiceLeft = Either.right(choiceLeft);
      Either<String, Choice<Integer, String>> eitherChoiceRight = Either.right(choiceRight);
      Either<String, Boolean> eitherTrue = Either.right(true);
      Either<String, Boolean> eitherFalse = Either.right(false);

      CoreTypeTest.<String, Integer>either(Either.class)
          .withLeft(leftInstance)
          .withRight(rightInstance)
          .withMappers(TestFunctions.INT_TO_STRING)
          .withSelectiveOperations(eitherChoiceLeft, eitherChoiceRight, eitherTrue, eitherFalse)
          .withHandlers(i -> "selected:" + i, i -> "left:" + i, s -> "right:" + s)
          .configureValidation()
          .useSelectiveInheritanceValidation()
          .withSelectFrom(EitherSelective.class)
          .withBranchFrom(EitherSelective.class)
          .withWhenSFrom(EitherSelective.class)
          .withIfSFrom(EitherSelective.class)
          .testAll();
    }

    @Test
    @DisplayName("Test Either Selective with inheritance validation")
    void testEitherSelectiveWithInheritanceValidation() {
      // Create Choice instances for Selective testing
      Choice<Integer, String> choiceLeft = Selective.left(rightValue);
      Choice<Integer, String> choiceRight = Selective.right("right-value");

      Either<String, Choice<Integer, String>> eitherChoiceLeft = Either.right(choiceLeft);
      Either<String, Choice<Integer, String>> eitherChoiceRight = Either.right(choiceRight);
      Either<String, Boolean> eitherTrue = Either.right(true);
      Either<String, Boolean> eitherFalse = Either.right(false);

      CoreTypeTest.<String, Integer>either(Either.class)
          .withLeft(leftInstance)
          .withRight(rightInstance)
          .withMappers(TestFunctions.INT_TO_STRING)
          .withSelectiveOperations(eitherChoiceLeft, eitherChoiceRight, eitherTrue, eitherFalse)
          .withHandlers(i -> "selected:" + i, i -> "left:" + i, s -> "right:" + s)
          .configureValidation()
          .useSelectiveInheritanceValidation()
          .withSelectFrom(EitherSelective.class)
          .withBranchFrom(EitherSelective.class)
          .withWhenSFrom(EitherSelective.class)
          .withIfSFrom(EitherSelective.class)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Factory Methods - Complete Coverage")
  class FactoryMethods {

    @Test
    @DisplayName("left() creates correct Left instances with all value types")
    void leftCreatesCorrectInstances() {
      // Non-null values
      assertThat(leftInstance).isInstanceOf(Either.Left.class);
      assertThatEither(leftInstance).isLeft().hasLeftNonNull().hasLeft(leftValue);

      // Null values
      assertThat(leftNullInstance).isInstanceOf(Either.Left.class);
      assertThatEither(leftNullInstance).isLeft().hasLeftNull();

      // Complex types
      Exception exception = new RuntimeException("test");
      Either<Exception, String> exceptionLeft = Either.left(exception);
      assertThatEither(exceptionLeft).isLeft().hasLeftNonNull().hasLeft(exception);

      // Empty string
      Either<String, Integer> emptyLeft = Either.left("");
      assertThatEither(emptyLeft).isLeft().hasLeftNonNull().hasLeft("");
    }

    @Test
    @DisplayName("right() creates correct Right instances with all value types")
    void rightCreatesCorrectInstances() {
      // Non-null values
      assertThat(rightInstance).isInstanceOf(Either.Right.class);
      assertThatEither(rightInstance).isRight().hasRightNonNull().hasRight(rightValue);

      // Null values
      assertThat(rightNullInstance).isInstanceOf(Either.Right.class);
      assertThatEither(rightNullInstance).isRight().hasRightNull();

      // Complex types
      List<String> list = List.of("a", "b", "c");
      Either<String, List<String>> listRight = Either.right(list);
      assertThatEither(listRight).isRight().hasRightNonNull().hasRight(list);

      // Primitives and wrappers
      Either<String, Boolean> boolRight = Either.right(true);
      assertThatEither(boolRight).isRight().hasRightNonNull().hasRight(true);
    }

    @Test
    @DisplayName("Factory methods type inference works correctly")
    void factoryMethodsTypeInference() {
      // Test that type inference works without explicit type parameters
      var stringLeft = Either.left("error");
      var intRight = Either.right(42);

      // Should be able to assign to properly typed variables
      Either<String, Object> leftAssignment = stringLeft;
      Either<Object, Integer> rightAssignment = intRight;

      assertThatEither(leftAssignment).isLeft().hasLeftNonNull().hasLeft("error");
      assertThatEither(rightAssignment).isRight().hasRightNonNull().hasRight(42);
    }
  }

  @Nested
  @DisplayName("Getter Methods - Comprehensive Edge Cases")
  class GetterMethodsTests {

    @Test
    @DisplayName("getLeft() works correctly on all Left variations")
    void getLeftWorksCorrectly() {
      // Standard case
      assertThat(leftInstance.getLeft()).isEqualTo(leftValue);
      assertThatEither(leftInstance).hasLeftNonNull();

      // Null case
      assertThat(leftNullInstance.getLeft()).isNull();

      // Complex types
      RuntimeException exception = new RuntimeException("Test exception: test");
      Either<RuntimeException, String> exceptionLeft = Either.left(exception);
      assertThat(exceptionLeft.getLeft()).isSameAs(exception);
      assertThatEither(exceptionLeft).hasLeftNonNull();

      // Generic types
      List<String> errorList = List.of("error1", "error2");
      Either<List<String>, Integer> listLeft = Either.left(errorList);
      assertThat(listLeft.getLeft()).isSameAs(errorList);
      assertThatEither(listLeft).hasLeftNonNull();
    }

    @Test
    @DisplayName("getLeft() throws correct exceptions on Right instances")
    void getLeftThrowsOnRight() {
      // Standard Right
      assertThatThrownBy(rightInstance::getLeft)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot invoke getLeft() on a Right instance.");

      // Right with null
      assertThatThrownBy(rightNullInstance::getLeft)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot invoke getLeft() on a Right instance.");

      // Complex Right types
      Either<String, List<Integer>> listRight = Either.right(List.of(1, 2, 3));
      assertThatThrownBy(listRight::getLeft)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot invoke getLeft() on a Right instance.");
    }

    @Test
    @DisplayName("getRight() works correctly on all Right variations")
    void getRightWorksCorrectly() {
      // Standard case
      assertThat(rightInstance.getRight()).isEqualTo(rightValue);
      assertThatEither(rightInstance).hasRightNonNull();

      // Null case
      assertThat(rightNullInstance.getRight()).isNull();

      // Complex types
      List<String> resultList = List.of("a", "b", "c");
      Either<String, List<String>> listRight = Either.right(resultList);
      assertThat(listRight.getRight()).isSameAs(resultList);
      assertThatEither(listRight).hasRightNonNull();

      // Nested Either (Either as value)
      Either<String, Integer> nested = Either.right(99);
      Either<String, Either<String, Integer>> nestedRight = Either.right(nested);
      assertThat(nestedRight.getRight()).isSameAs(nested);
      assertThatEither(nestedRight).hasRightNonNull();
    }

    @Test
    @DisplayName("getRight() throws correct exceptions on Left instances")
    void getRightThrowsOnLeft() {
      // Standard Left
      assertThatThrownBy(leftInstance::getRight)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot invoke getRight() on a Left instance.");

      // Left with null
      assertThatThrownBy(leftNullInstance::getRight)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot invoke getRight() on a Left instance.");

      // Complex Left types
      Either<RuntimeException, String> exceptionLeft =
          Either.left(new RuntimeException("Test exception: test"));
      assertThatThrownBy(exceptionLeft::getRight)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot invoke getRight() on a Left instance.");
    }
  }

  @Nested
  @DisplayName("fold() Method - Complete Validation and Edge Cases")
  class FoldMethodTests {

    @Test
    @DisplayName("fold() applies correct mapper based on Either type")
    void foldAppliesCorrectMapper() {
      // Left mapping
      String leftResult = leftInstance.fold(l -> "Left mapped: " + l, r -> "Right mapped: " + r);
      assertThat(leftResult).isEqualTo("Left mapped: " + leftValue);

      // Right mapping
      String rightResult = rightInstance.fold(l -> "Left mapped: " + l, r -> "Right mapped: " + r);
      assertThat(rightResult).isEqualTo("Right mapped: " + rightValue);

      // Null value handling
      String leftNullResult =
          leftNullInstance.fold(l -> "Left mapped: " + l, r -> "Right mapped: " + r);
      assertThat(leftNullResult).isEqualTo("Left mapped: null");

      String rightNullResult =
          rightNullInstance.fold(l -> "Left mapped: " + l, r -> "Right mapped: " + r);
      assertThat(rightNullResult).isEqualTo("Right mapped: null");
    }

    @Test
    @DisplayName("fold() validates null mappers using standardised validation")
    void foldValidatesNullMappers() {
      ValidationTestBuilder.create()
          .assertFunctionNull(
              () -> leftInstance.fold(null, r -> "Right mapped: " + r),
              "leftMapper",
              Either.class,
              Operation.FOLD)
          .assertFunctionNull(
              () -> rightInstance.fold(l -> "Left mapped: " + l, null),
              "rightMapper",
              Either.class,
              Operation.FOLD)
          .assertFunctionNull(
              () -> leftInstance.fold(null, null), "leftMapper", Either.class, Operation.FOLD)
          .execute();
    }

    @Test
    @DisplayName("fold() handles exception propagation correctly")
    void foldHandlesExceptionPropagation() {
      RuntimeException testException = new RuntimeException("Test exception: fold test");

      // Left instance should call left mapper and propagate exception
      assertThatThrownBy(
              () -> leftInstance.fold(TestFunctions.throwingFunction(testException), r -> "Right"))
          .isSameAs(testException);

      // Right instance should call right mapper and propagate exception
      assertThatThrownBy(
              () -> rightInstance.fold(l -> "Left", TestFunctions.throwingFunction(testException)))
          .isSameAs(testException);

      // Non-throwing mapper shouldn't be called
      String leftResult =
          leftInstance.fold(
              l -> "Left mapped: " + l, TestFunctions.throwingFunction(testException));
      assertThat(leftResult).isEqualTo("Left mapped: " + leftValue);

      String rightResult =
          rightInstance.fold(
              TestFunctions.throwingFunction(testException), r -> "Right mapped: " + r);
      assertThat(rightResult).isEqualTo("Right mapped: " + rightValue);
    }

    @Test
    @DisplayName("fold() works with complex type transformations")
    void foldWorksWithComplexTransformations() {
      Either<List<String>, Integer> complexEither = Either.left(List.of("error1", "error2"));

      String result =
          complexEither.fold(
              errors -> "Errors: " + String.join(", ", errors), value -> "Success: " + value);

      assertThat(result).isEqualTo("Errors: error1, error2");

      // Test with Right complex type
      Either<String, List<Integer>> rightComplex = Either.right(List.of(1, 2, 3));
      String rightResult =
          rightComplex.fold(
              error -> "Error: " + error,
              values -> "Sum: " + values.stream().mapToInt(Integer::intValue).sum());

      assertThat(rightResult).isEqualTo("Sum: 6");
    }
  }

  @Nested
  @DisplayName("map() Method - Comprehensive Right-Biased Testing")
  class MapMethodTests {

    @Test
    @DisplayName("map() applies function to Right values")
    void mapAppliesFunctionToRight() {
      // Standard transformation
      Either<String, String> result = rightInstance.map(TestFunctions.INT_TO_STRING);
      assertThatEither(result).isRight().hasRightNonNull().hasRight("123");

      // Complex transformation
      Either<String, List<Integer>> listResult = rightInstance.map(i -> List.of(i, i * 2));
      assertThatEither(listResult)
          .isRight()
          .hasRightNonNull()
          .hasRightSatisfying(
              list -> {
                assertThat(list).containsExactly(123, 246);
              });

      // Null-safe transformation
      Either<String, String> nullResult = rightNullInstance.map(String::valueOf);
      assertThatEither(nullResult).isRight().hasRightNonNull().hasRight("null");
    }

    @Test
    @DisplayName("map() preserves Left instances unchanged")
    void mapPreservesLeftInstances() {
      // Standard Left
      Either<String, String> result = leftInstance.map(TestFunctions.INT_TO_STRING);
      assertThat(result).isSameAs(leftInstance);
      assertThatEither(result).isLeft().hasLeftNonNull().hasLeft(leftValue);

      // Left with null
      Either<String, String> nullResult = leftNullInstance.map(TestFunctions.INT_TO_STRING);
      assertThat(nullResult).isSameAs(leftNullInstance);
      assertThatEither(nullResult).isLeft().hasLeftNull();

      // Complex Left type
      RuntimeException exception = new RuntimeException("Test exception: test");
      Either<RuntimeException, Integer> exceptionLeft = Either.left(exception);
      Either<RuntimeException, String> mappedLeft = exceptionLeft.map(TestFunctions.INT_TO_STRING);
      assertThat(mappedLeft).isSameAs(exceptionLeft);
      assertThat(mappedLeft.getLeft()).isSameAs(exception);
    }

    @Test
    @DisplayName("map() validates null mapper using standardised validation")
    void mapValidatesNullMapper() {
      ValidationTestBuilder.create()
          .assertMapperNull(() -> rightInstance.map(null), "mapper", Either.class, Operation.MAP)
          .assertMapperNull(() -> leftInstance.map(null), "mapper", Either.class, Operation.MAP)
          .execute();
    }

    @Test
    @DisplayName("map() handles exception propagation and chaining")
    void mapHandlesExceptionPropagation() {
      RuntimeException testException = new RuntimeException("Test exception: map test");

      // Right instances should propagate exceptions
      assertThatThrownBy(() -> rightInstance.map(TestFunctions.throwingFunction(testException)))
          .isSameAs(testException);

      // Left instances should not call mapper
      Either<String, String> leftResult =
          leftInstance.map(TestFunctions.throwingFunction(testException));
      assertThat(leftResult).isSameAs(leftInstance);

      // Test chaining
      Either<String, Integer> start = Either.right(10);
      Either<String, String> chainResult =
          start.map(i -> i * 2).map(i -> "Value: " + i).map(String::toUpperCase);
      assertThatEither(chainResult).isRight().hasRightNonNull().hasRight("VALUE: 20");

      // Test chaining with Left short-circuit
      Either<String, Integer> leftStart = Either.left("error");
      Either<String, String> leftChainResult =
          leftStart.map(i -> i * 2).map(i -> "Value: " + i).map(String::toUpperCase);
      assertThatEither(leftChainResult).isLeft().hasLeftNonNull().hasLeft("error");
    }

    @Test
    @DisplayName("map() handles null-returning functions")
    void mapHandlesNullReturningFunctions() {
      Either<String, String> result = rightInstance.map(TestFunctions.nullReturningFunction());
      assertThatEither(result).isRight().hasRightNull();
    }

    @Test
    @DisplayName("map() operations preserve non-null values")
    void mapOperationsPreserveNonNull() {
      Either<String, String> right = Either.right("test");
      assertThatEither(right).hasRightNonNull();

      Either<String, Integer> mapped = right.map(String::length);
      assertThatEither(mapped).isRight().hasRightNonNull();

      Either<String, String> remapped = mapped.map(i -> "length:" + i);
      assertThatEither(remapped).isRight().hasRightNonNull();
    }
  }

  @Nested
  @DisplayName("Side Effect Methods - Complete ifLeft/ifRight Testing")
  class SideEffectMethodsTests {

    @Test
    @DisplayName("ifLeft() executes action on Left instances")
    void ifLeftExecutesOnLeft() {
      AtomicBoolean executed = new AtomicBoolean(false);
      AtomicBoolean correctValue = new AtomicBoolean(false);

      leftInstance.ifLeft(
          s -> {
            executed.set(true);
            correctValue.set(leftValue.equals(s));
          });

      assertThat(executed).isTrue();
      assertThat(correctValue).isTrue();

      // Test with null value
      AtomicBoolean nullExecuted = new AtomicBoolean(false);
      leftNullInstance.ifLeft(
          s -> {
            nullExecuted.set(true);
            assertThat(s).isNull();
          });

      assertThat(nullExecuted).isTrue();
    }

    @Test
    @DisplayName("ifLeft() does not execute action on Right instances")
    void ifLeftDoesNotExecuteOnRight() {
      AtomicBoolean shouldNotExecute = new AtomicBoolean(false);

      rightInstance.ifLeft(s -> shouldNotExecute.set(true));
      assertThat(shouldNotExecute).isFalse();

      rightNullInstance.ifLeft(s -> shouldNotExecute.set(true));
      assertThat(shouldNotExecute).isFalse();
    }

    @Test
    @DisplayName("ifRight() executes action on Right instances")
    void ifRightExecutesOnRight() {
      AtomicBoolean executed = new AtomicBoolean(false);
      AtomicBoolean correctValue = new AtomicBoolean(false);

      rightInstance.ifRight(
          i -> {
            executed.set(true);
            correctValue.set(rightValue.equals(i));
          });

      assertThat(executed).isTrue();
      assertThat(correctValue).isTrue();

      // Test with null value
      AtomicBoolean nullExecuted = new AtomicBoolean(false);
      rightNullInstance.ifRight(
          i -> {
            nullExecuted.set(true);
            assertThat(i).isNull();
          });

      assertThat(nullExecuted).isTrue();
    }

    @Test
    @DisplayName("ifRight() does not execute action on Left instances")
    void ifRightDoesNotExecuteOnLeft() {
      AtomicBoolean shouldNotExecute = new AtomicBoolean(false);

      leftInstance.ifRight(i -> shouldNotExecute.set(true));
      assertThat(shouldNotExecute).isFalse();

      leftNullInstance.ifRight(i -> shouldNotExecute.set(true));
      assertThat(shouldNotExecute).isFalse();
    }

    @Test
    @DisplayName("Side effect methods validate null actions")
    void sideEffectMethodsValidateNullActions() {
      ValidationTestBuilder.create()
          .assertFunctionNull(
              () -> leftInstance.ifLeft(null), "action", Either.class, Operation.IF_LEFT)
          .assertFunctionNull(
              () -> rightInstance.ifLeft(null), "action", Either.class, Operation.IF_LEFT)
          .assertFunctionNull(
              () -> rightInstance.ifRight(null), "action", Either.class, Operation.IF_RIGHT)
          .assertFunctionNull(
              () -> leftInstance.ifRight(null), "action", Either.class, Operation.IF_RIGHT)
          .execute();
    }

    @Test
    @DisplayName("Side effect methods handle exceptions in actions")
    void sideEffectMethodsHandleExceptions() {
      RuntimeException testException = new RuntimeException("Test exception: side effect test");

      // Exceptions should propagate from actions
      assertThatThrownBy(
              () ->
                  leftInstance.ifLeft(
                      s -> {
                        throw testException;
                      }))
          .isSameAs(testException);

      assertThatThrownBy(
              () ->
                  rightInstance.ifRight(
                      i -> {
                        throw testException;
                      }))
          .isSameAs(testException);

      // Non-matching sides should not call actions
      assertThatCode(
              () ->
                  rightInstance.ifLeft(
                      s -> {
                        throw testException;
                      }))
          .doesNotThrowAnyException();

      assertThatCode(
              () ->
                  leftInstance.ifRight(
                      i -> {
                        throw testException;
                      }))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("flatMap() Method - Comprehensive Monadic Testing")
  class FlatMapMethodTests {

    @Test
    @DisplayName("flatMap() applies function to Right values")
    void flatMapAppliesFunctionToRight() {
      Either<String, String> result = rightInstance.flatMap(i -> Either.right("Value: " + i));
      assertThatEither(result).isRight().hasRightNonNull().hasRight("Value: 123");

      // Test returning Left from flatMap
      Either<String, String> errorResult =
          rightInstance.flatMap(i -> Either.left("Converted to error"));
      assertThatEither(errorResult).isLeft().hasLeftNonNull().hasLeft("Converted to error");
    }

    @Test
    @DisplayName("flatMap() preserves Left instances unchanged")
    void flatMapPreservesLeftInstances() {
      Either<String, String> result = leftInstance.flatMap(i -> Either.right("Value: " + i));
      assertThat(result).isSameAs(leftInstance);
      assertThatEither(result).isLeft().hasLeftNonNull().hasLeft(leftValue);
    }

    @Test
    @DisplayName("flatMap() validates parameters using standardised validation")
    void flatMapValidatesParameters() {
      ValidationTestBuilder.create()
          .assertFlatMapperNull(
              () -> rightInstance.flatMap(null), "mapper", Either.class, Operation.FLAT_MAP)
          .assertFlatMapperNull(
              () -> leftInstance.flatMap(null), "mapper", Either.class, Operation.FLAT_MAP)
          .execute();
    }

    @Test
    @DisplayName("flatMap() validates non-null results")
    void flatMapValidatesNonNullResults() {
      assertThatThrownBy(() -> rightInstance.flatMap(i -> null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              "Function mapper in Either.flatMap returned null when Either expected, which is not"
                  + " allowed");
    }

    @Test
    @DisplayName("flatMap() supports complex chaining patterns")
    void flatMapSupportsComplexChaining() {
      // Success chain
      Either<String, Integer> start = Either.right(10);
      Either<String, String> result =
          start
              .flatMap(i -> Either.right(i * 2))
              .flatMap(i -> Either.right("Value: " + i))
              .flatMap(s -> Either.right(s.toUpperCase()));
      assertThatEither(result).isRight().hasRightNonNull().hasRight("VALUE: 20");

      // Failure in middle of chain
      Either<String, String> failureResult =
          start
              .flatMap(i -> Either.right(i * 2))
              .flatMap(i -> Either.left("Error occurred"))
              .flatMap(i -> Either.right("Should not reach"));
      assertThatEither(failureResult).isLeft().hasLeftNonNull().hasLeft("Error occurred");

      // Mixed operations
      Either<String, Integer> mixedResult =
          start
              .map(i -> i + 5) // 15
              .flatMap(i -> Either.right(i * 2)) // 30
              .map(i -> i - 10); // 20
      assertThatEither(mixedResult).isRight().hasRightNonNull().hasRight(20);
    }

    @Test
    @DisplayName("flatMap() handles exception propagation")
    void flatMapHandlesExceptionPropagation() {
      RuntimeException testException = new RuntimeException("Test exception: flatMap test");

      // Right instances should propagate exceptions
      assertThatThrownBy(() -> rightInstance.flatMap(TestFunctions.throwingFunction(testException)))
          .isSameAs(testException);

      // Left instances should not call mapper
      Either<String, String> leftResult =
          leftInstance.flatMap(TestFunctions.throwingFunction(testException));
      assertThat(leftResult).isSameAs(leftInstance);
    }

    @Test
    @DisplayName("flatMap() preserves non-null values through chains")
    void flatMapPreservesNonNullThroughChains() {
      Either<String, String> start = Either.right("test");
      assertThatEither(start).hasRightNonNull();

      Either<String, Integer> step1 = start.flatMap(s -> Either.right(s.length()));
      assertThatEither(step1).isRight().hasRightNonNull();

      Either<String, String> step2 = step1.flatMap(i -> Either.right("length:" + i));
      assertThatEither(step2).isRight().hasRightNonNull();
    }
  }

  @Nested
  @DisplayName("toString() and Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("toString() provides meaningful representations")
    void toStringProvidesMeaningfulRepresentations() {
      // Left toString
      assertThat(leftInstance.toString()).isEqualTo("Left(" + leftValue + ")");
      assertThat(leftNullInstance.toString()).isEqualTo("Left(null)");

      // Right toString
      assertThat(rightInstance.toString()).isEqualTo("Right(" + rightValue + ")");
      assertThat(rightNullInstance.toString()).isEqualTo("Right(null)");

      // Complex types
      Either<List<String>, Integer> complexLeft = Either.left(List.of("a", "b"));
      assertThat(complexLeft.toString()).isEqualTo("Left([a, b])");

      Either<String, List<Integer>> complexRight = Either.right(List.of(1, 2, 3));
      assertThat(complexRight.toString()).isEqualTo("Right([1, 2, 3])");
    }

    @Test
    @DisplayName("equals() and hashCode() work correctly")
    void equalsAndHashCodeWorkCorrectly() {
      // Same instances
      assertThat(leftInstance).isEqualTo(leftInstance);
      assertThat(rightInstance).isEqualTo(rightInstance);

      // Equal instances
      Either<String, Integer> anotherLeft = Either.left(leftValue);
      Either<String, Integer> anotherRight = Either.right(rightValue);
      assertThat(leftInstance).isEqualTo(anotherLeft);
      assertThat(rightInstance).isEqualTo(anotherRight);
      assertThat(leftInstance.hashCode()).isEqualTo(anotherLeft.hashCode());
      assertThat(rightInstance.hashCode()).isEqualTo(anotherRight.hashCode());

      // Different instances
      assertThat(leftInstance).isNotEqualTo(rightInstance);
      assertThat(leftInstance).isNotEqualTo(Either.left("different"));
      assertThat(rightInstance).isNotEqualTo(Either.right(999));

      // Null handling
      assertThat(leftNullInstance).isEqualTo(Either.left(null));
      assertThat(rightNullInstance).isEqualTo(Either.right(null));
      assertThat(leftNullInstance).isNotEqualTo(rightNullInstance);
    }
  }

  @Nested
  @DisplayName("Advanced Usage Patterns")
  class AdvancedUsagePatterns {

    @Test
    @DisplayName("Either as functor maintains structure")
    void eitherAsFunctorMaintainsStructure() {
      Either<String, Integer> start = Either.right(5);

      Either<String, Double> result = start.map(i -> i * 2.0).map(d -> d + 0.5).map(Math::sqrt);

      assertThatEither(result)
          .isRight()
          .hasRightNonNull()
          .hasRightSatisfying(
              value -> {
                assertThat(value).isCloseTo(Math.sqrt(10.5), within(0.001));
              });
    }

    @Test
    @DisplayName("Either for railway oriented programming")
    void eitherForRailwayOrientedProgramming() {
      // Simulate a pipeline where each step can fail
      Function<String, Either<String, Integer>> parseInteger =
          (String s) -> {
            try {
              return Either.<String, Integer>right(Integer.parseInt(s));
            } catch (NumberFormatException e) {
              return Either.<String, Integer>left("Invalid number: " + s);
            }
          };

      Function<Integer, Either<String, Double>> squareRoot =
          (Integer i) -> {
            if (i < 0) {
              return Either.<String, Double>left(
                  "Cannot take square root of negative number: " + i);
            }
            return Either.<String, Double>right(Math.sqrt(i));
          };

      Function<Double, Either<String, String>> formatResult =
          (Double d) -> {
            if (d > 100) {
              return Either.<String, String>left("Result too large: " + d);
            }
            return Either.<String, String>right(String.format("%.2f", d));
          };

      Either<String, String> success =
          Either.<String, String>right("16")
              .flatMap(parseInteger)
              .flatMap(squareRoot)
              .flatMap(formatResult);
      assertThatEither(success).isRight().hasRightNonNull().hasRight("4.00");

      // Failure paths
      Either<String, String> parseFailure =
          Either.<String, String>right("not-a-number")
              .flatMap(parseInteger)
              .flatMap(squareRoot)
              .flatMap(formatResult);
      assertThatEither(parseFailure)
          .isLeft()
          .hasLeftNonNull()
          .hasLeftSatisfying(
              error -> {
                assertThat(error).contains("Invalid number");
              });

      Either<String, String> negativeFailure =
          Either.<String, String>right("-4")
              .flatMap(parseInteger)
              .flatMap(squareRoot)
              .flatMap(formatResult);
      assertThatEither(negativeFailure)
          .isLeft()
          .hasLeftNonNull()
          .hasLeftSatisfying(
              error -> {
                assertThat(error).contains("Cannot take square root");
              });
    }

    @Test
    @DisplayName("Either with resource management patterns")
    void eitherWithResourceManagement() {
      // Simulate resource acquisition and cleanup
      record Resource(String name, boolean open) {
        Resource close() {
          return new Resource(name, false);
        }
      }

      Function<String, Either<String, Resource>> openResource =
          (String name) -> {
            if (name.equals("invalid")) {
              return Either.<String, Resource>left("Cannot open resource: " + name);
            }
            return Either.<String, Resource>right(new Resource(name, true));
          };

      Function<Resource, Either<String, String>> processResource =
          (Resource resource) -> {
            if (!resource.open()) {
              return Either.<String, String>left("Resource is closed");
            }
            if (resource.name().equals("fail")) {
              return Either.<String, String>left("Processing failed");
            }
            return Either.<String, String>right("Processed: " + resource.name());
          };

      // Success case
      Either<String, String> result =
          openResource
              .apply("test")
              .flatMap(
                  resource -> {
                    Either<String, String> processed = processResource.apply(resource);
                    Resource closed = resource.close();
                    assertThat(closed.open()).isFalse();
                    return processed;
                  });

      assertThatEither(result).isRight().hasRightNonNull().hasRight("Processed: test");

      // Failure case
      Either<String, String> failureResult =
          openResource
              .apply("fail")
              .flatMap(
                  resource -> {
                    Either<String, String> processed = processResource.apply(resource);
                    Resource closed = resource.close();
                    assertThat(closed.open()).isFalse();
                    return processed;
                  });

      assertThatEither(failureResult).isLeft().hasLeftNonNull().hasLeft("Processing failed");
    }

    @Test
    @DisplayName("Either pattern matching with switch expressions")
    void eitherPatternMatchingWithSwitch() {
      // Test exhaustive pattern matching
      Function<Either<String, Integer>, String> processEither =
          (Either<String, Integer> either) ->
              switch (either) {
                case Either.Left<String, Integer>(var error) -> "Error: " + error;
                case Either.Right<String, Integer>(var value) -> "Success: " + value;
              };

      assertThat(processEither.apply(leftInstance)).isEqualTo("Error: " + leftValue);
      assertThat(processEither.apply(rightInstance)).isEqualTo("Success: " + rightValue);
      assertThat(processEither.apply(leftNullInstance)).isEqualTo("Error: null");
      assertThat(processEither.apply(rightNullInstance)).isEqualTo("Success: null");

      // Test with nested pattern matching
      Either<Either<String, Integer>, Boolean> nested = Either.left(Either.right(42));
      String nestedResult =
          switch (nested) {
            case Either.Left<Either<String, Integer>, Boolean>(var innerEither) ->
                switch (innerEither) {
                  case Either.Left<String, Integer>(var error) -> "Nested error: " + error;
                  case Either.Right<String, Integer>(var value) -> "Nested value: " + value;
                };
            case Either.Right<Either<String, Integer>, Boolean>(var bool) -> "Boolean: " + bool;
          };
      assertThat(nestedResult).isEqualTo("Nested value: 42");
    }
  }

  @Nested
  @DisplayName("Performance and Memory Characteristics")
  class PerformanceAndMemoryTests {

    @Test
    @DisplayName("Either operations complete in reasonable time")
    void eitherOperationsCompleteInReasonableTime() {
      Either<String, Integer> test = Either.right(DEFAULT_RIGHT_VALUE);

      // Verify operations complete without hanging (generous timeout)
      // Use JMH benchmarks in hkj-benchmarks module for precise performance measurement
      assertTimeoutPreemptively(
          Duration.ofSeconds(2),
          () -> {
            for (int i = 0; i < 100_000; i++) {
              test.map(x -> x + 1).flatMap(x -> Either.right(x * 2)).isRight();
            }
          },
          "Either operations should complete within reasonable time");
    }

    @Test
    @DisplayName("Left instances are reused efficiently")
    void leftInstancesAreReusedEfficiently() {
      Either<String, Integer> left = Either.left("error");

      // map should return same instance for Left
      Either<String, String> mapped = left.map(Object::toString);
      assertThat(mapped).isSameAs(left);

      // Multiple map operations should all return same instance
      Either<String, Boolean> multiMapped =
          left.map(Object::toString).map(String::length).map(len -> len > 0);
      assertThat(multiMapped).isSameAs(left);

      // flatMap should also return same instance for Left
      Either<String, String> flatMapped = left.flatMap(x -> Either.right("not reached"));
      assertThat(flatMapped).isSameAs(left);
    }

    @Test
    @DisplayName("Memory usage is reasonable for large chains")
    void memoryUsageIsReasonableForLargeChains() {
      // Test that long chains don't create excessive rubbish
      Either<String, Integer> start = Either.right(1);

      Either<String, Integer> result = start;
      for (int i = 0; i < 1000; i++) {
        final int increment = i;
        result = result.map(x -> x + increment);
      }

      // Should complete without memory issues
      assertThatEither(result).isRight().hasRightNonNull().hasRight(1 + (999 * 1000) / 2);

      // Left chains should be even more efficient
      Either<String, Integer> leftStart = Either.left("error");
      Either<String, Integer> leftResult = leftStart;
      for (int i = 0; i < 1000; i++) {
        int finalI = i;
        leftResult = leftResult.map(x -> x + finalI);
      }

      // Should be same instance throughout
      assertThat(leftResult).isSameAs(leftStart);
    }
  }

  @Nested
  @DisplayName("Type Safety and Variance")
  class TypeSafetyAndVarianceTests {

    @Test
    @DisplayName("Either maintains type safety across operations")
    void eitherMaintainsTypeSafety() {
      // Test covariance in Right type
      Either<String, Number> numberEither = Either.right(42);
      Either<String, Integer> intEither = numberEither.flatMap(n -> Either.right(n.intValue()));
      assertThatEither(intEither).isRight().hasRightNonNull().hasRight(42);

      Either<Exception, String> exceptionEither = Either.left(new RuntimeException("test"));

      Either<Exception, String> processedEither =
          exceptionEither.flatMap(s -> Either.right(s.toUpperCase()));
      assertThatEither(processedEither)
          .isLeft()
          .hasLeftNonNull()
          .hasLeftSatisfying(
              ex -> {
                assertThat(ex).isInstanceOf(RuntimeException.class);
              });

      Either<RuntimeException, String> runtimeEither;
      if (exceptionEither.isLeft() && exceptionEither.getLeft() instanceof RuntimeException) {
        runtimeEither = Either.left((RuntimeException) exceptionEither.getLeft());
      } else {
        runtimeEither = Either.right("default");
      }

      Either<RuntimeException, String> processedRuntime =
          runtimeEither.flatMap(s -> Either.right(s.toUpperCase()));
      assertThatEither(processedRuntime)
          .isLeft()
          .hasLeftNonNull()
          .hasLeftSatisfying(
              ex -> {
                assertThat(ex).isInstanceOf(RuntimeException.class);
              });
    }

    @Test
    @DisplayName("Either works with complex generic types")
    void eitherWorksWithComplexGenericTypes() {
      // Nested generics
      Either<List<String>, List<Integer>> complexEither = Either.right(List.of(1, 2, 3));

      Either<List<String>, Integer> summed =
          complexEither.map(list -> list.stream().mapToInt(Integer::intValue).sum());
      assertThatEither(summed).isRight().hasRightNonNull().hasRight(6);

      // Map transformations
      Either<String, Map<String, Integer>> mapEither = Either.right(Map.of("a", 1, "b", 2));

      Either<String, Set<String>> keySet = mapEither.map(map -> map.keySet());
      assertThatEither(keySet)
          .isRight()
          .hasRightNonNull()
          .hasRightSatisfying(
              keys -> {
                assertThat(keys).containsExactlyInAnyOrder("a", "b");
              });
    }

    @Test
    @DisplayName("Either handles wildcard types correctly")
    void eitherHandlesWildcardTypesCorrectly() {
      // Test with wildcards
      Either<? extends Exception, ? extends Number> wildcardEither = Either.right(42.0);

      // Should be able to call methods that don't require specific bounds
      assertThat(wildcardEither.isRight()).isTrue();

      // Folding should work with appropriate bounds
      String result =
          wildcardEither.fold(
              ex -> "Exception: " + ex.getMessage(), num -> "Number: " + num.doubleValue());
      assertThat(result).isEqualTo("Number: 42.0");
    }
  }

  @Nested
  @DisplayName("Edge Cases and Boundary Conditions")
  class EdgeCasesAndBoundaryConditions {

    @Test
    @DisplayName("Either handles extreme values correctly")
    void eitherHandlesExtremeValuesCorrectly() {
      // Very large strings
      String largeString = "x".repeat(10000);
      Either<String, String> largeRight = Either.right(largeString);
      assertThatEither(largeRight.map(String::length)).isRight().hasRightNonNull().hasRight(10000);

      // Maximum/minimum integer values
      Either<String, Integer> maxInt = Either.right(Integer.MAX_VALUE);
      Either<String, Long> promoted = maxInt.map(i -> i.longValue() + 1);
      assertThatEither(promoted).isRight().hasRightNonNull().hasRight((long) Integer.MAX_VALUE + 1);

      // Very nested structures
      Either<String, Either<String, Either<String, Integer>>> tripleNested =
          Either.right(Either.right(Either.right(42)));

      Either<String, Integer> flattened =
          tripleNested.flatMap(inner -> inner.flatMap(innerInner -> innerInner));
      assertThatEither(flattened).isRight().hasRightNonNull().hasRight(42);
    }

    @Test
    @DisplayName("Either operations are stack-safe for deep recursion")
    void eitherOperationsAreStackSafe() {
      // Test that deep map chains don't cause stack overflow
      Either<String, Integer> start = Either.right(0);

      // Create a very deep chain (but not infinite)
      Either<String, Integer> result = start;
      for (int i = 0; i < 10000; i++) {
        result = result.map(x -> x + 1);
      }

      assertThatEither(result).isRight().hasRightNonNull().hasRight(10000);

      // Test with flatMap chains
      Either<String, Integer> flatMapResult = start;
      for (int i = 0; i < 1000; i++) {
        flatMapResult = flatMapResult.flatMap(x -> Either.right(x + 1));
      }

      assertThatEither(flatMapResult).isRight().hasRightNonNull().hasRight(1000);
    }

    @Test
    @DisplayName("Either handles concurrent modifications safely")
    void eitherHandlesConcurrentModificationsSafely() {
      // Either instances should be immutable and thread-safe
      Either<String, List<Integer>> listEither = Either.right(new ArrayList<>(List.of(1, 2, 3)));

      // Even if the contained list is mutable, Either operations should be safe
      Either<String, Integer> sizeEither = listEither.map(List::size);
      assertThatEither(sizeEither).isRight().hasRightNonNull().hasRight(3);

      // Modifying original list shouldn't affect Either operations
      listEither.getRight().add(4);
      Either<String, Integer> newSizeEither = listEither.map(List::size);
      assertThatEither(newSizeEither).isRight().hasRightNonNull().hasRight(4);
    }

    @Test
    @DisplayName("Either maintains referential transparency")
    void eitherMaintainsReferentialTransparency() {
      // Same operations should always produce same results
      Either<String, Integer> either = Either.right(DEFAULT_RIGHT_VALUE);

      Either<String, String> result1 = either.map(i -> "value:" + i);
      Either<String, String> result2 = either.map(i -> "value:" + i);

      assertThat(result1).isEqualTo(result2);
      assertThat(result1.getRight()).isEqualTo(result2.getRight());

      // This should be true for all Either operations
      Either<String, String> flatMapResult1 = either.flatMap(i -> Either.right("flat:" + i));
      Either<String, String> flatMapResult2 = either.flatMap(i -> Either.right("flat:" + i));

      assertThat(flatMapResult1).isEqualTo(flatMapResult2);
    }

    @Test
    @DisplayName("Complex types remain non-null through operations")
    void complexTypesRemainNonNullThroughOperations() {
      Either<String, List<Integer>> listRight = Either.right(List.of(1, 2, 3));
      assertThatEither(listRight).hasRightNonNull();

      Either<String, Integer> summed =
          listRight.map(list -> list.stream().mapToInt(Integer::intValue).sum());
      assertThatEither(summed).isRight().hasRightNonNull();

      Either<String, String> formatted = summed.map(sum -> "Total: " + sum);
      assertThatEither(formatted).isRight().hasRightNonNull();
    }
  }
}
