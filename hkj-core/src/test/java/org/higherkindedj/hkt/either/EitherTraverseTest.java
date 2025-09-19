// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.test.HKTTestAssertions.*;
import static org.higherkindedj.hkt.test.HKTTestHelpers.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherTraverse Comprehensive Tests")
class EitherTraverseTest {

  // Test data types
  private static final String ERROR_VALUE = "TestError";
  private static final Integer SUCCESS_VALUE = 42;
  private static final String TRANSFORMED_VALUE = "Transformed: 42";

  // Core instances
  private EitherTraverse<String> traverse;
  private Traverse<EitherKind.Witness<String>> traverseInterface;
  private Foldable<EitherKind.Witness<String>> foldable;
  private Applicative<MaybeKind.Witness> maybeApplicative;

  // Test data
  private Kind<EitherKind.Witness<String>, Integer> rightKind;
  private Kind<EitherKind.Witness<String>, Integer> leftKind;
  private Function<Integer, String> validMapper;
  private Function<Integer, Kind<MaybeKind.Witness, String>> validTraverseFunction;
  private Function<Integer, Kind<MaybeKind.Witness, String>> failingTraverseFunction;
  private Monoid<String> stringMonoid;
  private Monoid<Integer> intMonoid;
  private Function<Integer, String> validFoldMapFunction;

  @BeforeEach
  void setUp() {
    // Initialize core instances
    traverse = EitherTraverse.instance();
    traverseInterface = EitherTraverse.instance();
    foldable = EitherTraverse.instance();
    maybeApplicative = MaybeMonad.INSTANCE;

    // Initialize test data
    rightKind = EITHER.widen(Either.right(SUCCESS_VALUE));
    leftKind = EITHER.widen(Either.left(ERROR_VALUE));
    validMapper = i -> "Mapped: " + i;
    validTraverseFunction = i -> MAYBE.widen(Maybe.just("Traversed: " + i));
    failingTraverseFunction = i -> MAYBE.widen(Maybe.nothing());
    stringMonoid = Monoids.string();
    intMonoid = Monoids.integerAddition();
    validFoldMapFunction = i -> "Fold: " + i;
  }

  @Nested
  @DisplayName("Instance Method Coverage")
  class InstanceMethodTests {

    @Test
    @DisplayName("Test EitherTraverse.instance() method")
    void testInstanceMethod() {
      EitherTraverse<String> instance1 = EitherTraverse.instance();
      EitherTraverse<String> instance2 = EitherTraverse.instance();

      assertThat(instance1).isNotNull();
      assertThat(instance2).isNotNull();
      assertThat(instance1).isSameAs(instance2);

      // Test with different error types
      EitherTraverse<RuntimeException> exceptionInstance = EitherTraverse.instance();
      EitherTraverse<Integer> integerInstance = EitherTraverse.instance();

      assertThat(exceptionInstance).isNotNull();
      assertThat(integerInstance).isNotNull();
    }
  }

  @Nested
  @DisplayName("Complete Test Suite Using Framework")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete Traverse test suite")
    void completeTraverseTestSuite() {
      // Run the complete standardized test suite
      runCompleteTraverseTestSuite(
          traverse,
          "EitherTraverse",
          rightKind,
          validMapper,
          maybeApplicative,
          validTraverseFunction,
          stringMonoid,
          validFoldMapFunction);
    }
  }

  @Nested
  @DisplayName("Map Method - Comprehensive Coverage")
  class MapMethodTests {

    @Test
    @DisplayName("map() on Right should apply function")
    void mapOnRightShouldApplyFunction() {
      Kind<EitherKind.Witness<String>, String> result = traverse.map(validMapper, rightKind);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo("Mapped: " + SUCCESS_VALUE);
    }

    @Test
    @DisplayName("map() on Left should pass through unchanged")
    void mapOnLeftShouldPassThrough() {
      Kind<EitherKind.Witness<String>, String> result = traverse.map(validMapper, leftKind);

      Either<String, String> either = EITHER.narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(ERROR_VALUE);
    }

    @Test
    @DisplayName("map() comprehensive testing with different transformations")
    void mapComprehensiveTesting() {
      // Test mapping to different types
      Kind<EitherKind.Witness<String>, Boolean> boolResult = traverse.map(i -> i > 40, rightKind);
      assertThat(EITHER.<String, Boolean>narrow(boolResult).getRight()).isTrue();

      // Test mapping with null result
      Kind<EitherKind.Witness<String>, String> nullResult = traverse.map(i -> null, rightKind);
      assertThat(EITHER.<String, String>narrow(nullResult).getRight()).isNull();

      // Test Left remains unchanged regardless of mapper
      Kind<EitherKind.Witness<String>, Boolean> leftResult = traverse.map(i -> i > 40, leftKind);
      assertThat(EITHER.<String, Boolean>narrow(leftResult).getLeft()).isEqualTo(ERROR_VALUE);
    }

    @Test
    @DisplayName("map() should validate null parameters")
    void mapShouldValidateNullParameters() {
      ValidationTestBuilder.create()
          .assertNullFunction(() -> traverse.map(null, rightKind), "function f for map")
          .assertNullKind(() -> traverse.map(validMapper, null), "source Kind for map")
          .execute();
    }

    @Test
    @DisplayName("map() should propagate function exceptions")
    void mapShouldPropagateExceptions() {
      RuntimeException testException = createTestException("map test");
      Function<Integer, String> throwingMapper =
          CommonTestFunctions.throwingFunction(testException);

      assertThatThrownBy(() -> traverse.map(throwingMapper, rightKind)).isSameAs(testException);

      // Left should not call mapper, so no exception
      Kind<EitherKind.Witness<String>, String> leftResult = traverse.map(throwingMapper, leftKind);
      assertThat(EITHER.<String, String>narrow(leftResult).getLeft()).isEqualTo(ERROR_VALUE);
    }
  }

  @Nested
  @DisplayName("Traverse Method - Comprehensive Coverage")
  class TraverseMethodTests {

    @Test
    @DisplayName("traverse() on Right with successful function")
    void traverseRightSuccessful() {
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, validTraverseFunction, rightKind);

      // Result should be Just(Right("Traversed: 42"))
      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();

      Either<String, String> either = EITHER.narrow(maybe.get());
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo("Traversed: " + SUCCESS_VALUE);
    }

    @Test
    @DisplayName("traverse() on Right with failing function")
    void traverseRightFailing() {
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, failingTraverseFunction, rightKind);

      // Result should be Nothing
      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("traverse() on Left should preserve Left in applicative context")
    void traverseLeftPreservesError() {
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, validTraverseFunction, leftKind);

      // Result should be Just(Left("TestError"))
      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();

      Either<String, String> either = EITHER.narrow(maybe.get());
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(ERROR_VALUE);
    }

    @Test
    @DisplayName("traverse() comprehensive testing with different scenarios")
    void traverseComprehensiveTesting() {
      // Test conditional traverse function
      Function<Integer, Kind<MaybeKind.Witness, String>> conditionalFunction =
          CommonTraverseFunctions.conditionalMaybe(i -> i > 50);

      // Should fail because 42 <= 50
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> failResult =
          traverse.traverse(maybeApplicative, conditionalFunction, rightKind);
      assertThat(MAYBE.narrow(failResult).isNothing()).isTrue();

      // Test with value that passes condition
      Kind<EitherKind.Witness<String>, Integer> bigRightKind = EITHER.widen(Either.right(100));
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> successResult =
          traverse.traverse(maybeApplicative, conditionalFunction, bigRightKind);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(successResult);
      assertThat(maybe.isJust()).isTrue();
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight()).isEqualTo("100");
    }

    @Test
    @DisplayName("traverse() should validate null parameters")
    void traverseShouldValidateNullParameters() {
      TraverseValidationTestBuilder.create()
          .assertTraverseNullApplicative(
              () -> traverse.traverse(null, validTraverseFunction, rightKind))
          .assertTraverseNullFunction(() -> traverse.traverse(maybeApplicative, null, rightKind))
          .assertTraverseNullSourceKind(
              () -> traverse.traverse(maybeApplicative, validTraverseFunction, null))
          .execute();
    }

    @Test
    @DisplayName("traverse() should propagate function exceptions")
    void traverseShouldPropagateExceptions() {
      RuntimeException testException = createTestException("traverse test");
      Function<Integer, Kind<MaybeKind.Witness, String>> throwingFunction =
          CommonTestFunctions.throwingFunction(testException);

      assertThatThrownBy(() -> traverse.traverse(maybeApplicative, throwingFunction, rightKind))
          .isSameAs(testException);

      // Left should not call function, so no exception
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> leftResult =
          traverse.traverse(maybeApplicative, throwingFunction, leftKind);
      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(leftResult);
      assertThat(maybe.isJust()).isTrue();
      assertThat(EITHER.<String, String>narrow(maybe.get()).getLeft()).isEqualTo(ERROR_VALUE);
    }
  }

  @Nested
  @DisplayName("SequenceA Method - Comprehensive Coverage")
  class SequenceAMethodTests {

    @Test
    @DisplayName("sequenceA() should turn Right<Just<A>> into Just<Right<A>>")
    void sequenceRightJustToJustRight() {
      Kind<MaybeKind.Witness, Integer> maybeKind = MAYBE.widen(Maybe.just(SUCCESS_VALUE));
      Kind<EitherKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> input =
          EITHER.widen(Either.right(maybeKind));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      Maybe<Kind<EitherKind.Witness<String>, Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();

      Either<String, Integer> either = EITHER.narrow(maybe.get());
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo(SUCCESS_VALUE);
    }

    @Test
    @DisplayName("sequenceA() should turn Right<Nothing> into Nothing")
    void sequenceRightNothingToNothing() {
      Kind<MaybeKind.Witness, Integer> nothingKind = MAYBE.widen(Maybe.nothing());
      Kind<EitherKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> input =
          EITHER.widen(Either.right(nothingKind));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("sequenceA() should preserve Left values")
    void sequenceLeftPreservesError() {
      Kind<EitherKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> leftInput =
          EITHER.widen(Either.left(ERROR_VALUE));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, Integer>> result =
          traverse.sequenceA(maybeApplicative, leftInput);

      Maybe<Kind<EitherKind.Witness<String>, Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();

      Either<String, Integer> either = EITHER.narrow(maybe.get());
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(ERROR_VALUE);
    }
  }

  @Nested
  @DisplayName("FoldMap Method - Comprehensive Coverage")
  class FoldMapMethodTests {

    @Test
    @DisplayName("foldMap() on Right should apply function")
    void foldMapOnRightShouldApplyFunction() {
      String result = traverse.foldMap(stringMonoid, validFoldMapFunction, rightKind);
      assertThat(result).isEqualTo("Fold: " + SUCCESS_VALUE);
    }

    @Test
    @DisplayName("foldMap() on Left should return monoid empty")
    void foldMapOnLeftShouldReturnEmpty() {
      String result = traverse.foldMap(stringMonoid, validFoldMapFunction, leftKind);
      assertThat(result).isEqualTo(stringMonoid.empty());
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("foldMap() comprehensive testing with different monoids")
    void foldMapComprehensiveTesting() {
      // Test with integer monoid
      Function<Integer, Integer> doubleFunction = i -> i * 2;
      Integer intResult = traverse.foldMap(intMonoid, doubleFunction, rightKind);
      assertThat(intResult).isEqualTo(SUCCESS_VALUE * 2);

      Integer intLeftResult = traverse.foldMap(intMonoid, doubleFunction, leftKind);
      assertThat(intLeftResult).isEqualTo(intMonoid.empty());
      assertThat(intLeftResult).isZero();

      // Test with boolean monoids
      Monoid<Boolean> andMonoid = Monoids.booleanAnd();
      Function<Integer, Boolean> isPositive = i -> i > 0;
      Boolean boolResult = traverse.foldMap(andMonoid, isPositive, rightKind);
      assertThat(boolResult).isTrue();

      Boolean boolLeftResult = traverse.foldMap(andMonoid, isPositive, leftKind);
      assertThat(boolLeftResult).isEqualTo(andMonoid.empty());
      assertThat(boolLeftResult).isTrue(); // AND identity is true
    }

    @Test
    @DisplayName("foldMap() should validate null parameters")
    void foldMapShouldValidateNullParameters() {
      TraverseValidationTestBuilder.create()
          .assertFoldMapNullMonoid(() -> traverse.foldMap(null, validFoldMapFunction, rightKind))
          .assertFoldMapNullFunction(() -> traverse.foldMap(stringMonoid, null, rightKind))
          .assertFoldMapNullSourceKind(
              () -> traverse.foldMap(stringMonoid, validFoldMapFunction, null))
          .execute();
    }

    @Test
    @DisplayName("foldMap() should propagate function exceptions")
    void foldMapShouldPropagateExceptions() {
      RuntimeException testException = createTestException("foldMap test");
      Function<Integer, String> throwingFunction =
          CommonTestFunctions.throwingFunction(testException);

      assertThatThrownBy(() -> traverse.foldMap(stringMonoid, throwingFunction, rightKind))
          .isSameAs(testException);

      // Left should not call function, so no exception
      String leftResult = traverse.foldMap(stringMonoid, throwingFunction, leftKind);
      assertThat(leftResult).isEqualTo(stringMonoid.empty());
    }
  }

  @Nested
  @DisplayName("Error Handling and Edge Cases")
  class ErrorHandlingTests {

    @Test
    @DisplayName("All operations should handle null values within Right")
    void operationsShouldHandleNullValuesInRight() {
      Kind<EitherKind.Witness<String>, Integer> rightNullKind = EITHER.widen(Either.right(null));

      // map with null value
      Kind<EitherKind.Witness<String>, String> mapResult =
          traverse.map(i -> i == null ? "was null" : i.toString(), rightNullKind);
      assertThat(EITHER.<String, String>narrow(mapResult).getRight()).isEqualTo("was null");

      // traverse with null value
      Function<Integer, Kind<MaybeKind.Witness, String>> nullSafeTraverse =
          i -> MAYBE.widen(Maybe.just(i == null ? "null traversed" : i.toString()));
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> traverseResult =
          traverse.traverse(maybeApplicative, nullSafeTraverse, rightNullKind);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(traverseResult);
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight()).isEqualTo("null traversed");

      // foldMap with null value
      Function<Integer, String> nullSafeFold = i -> i == null ? "null folded" : i.toString();
      String foldResult = traverse.foldMap(stringMonoid, nullSafeFold, rightNullKind);
      assertThat(foldResult).isEqualTo("null folded");
    }

    @Test
    @DisplayName("All operations should handle null error values in Left")
    void operationsShouldHandleNullErrorValues() {
      Kind<EitherKind.Witness<String>, Integer> leftNullKind = EITHER.widen(Either.left(null));

      // All operations should preserve null Left values
      Kind<EitherKind.Witness<String>, String> mapResult = traverse.map(validMapper, leftNullKind);
      assertThat(EITHER.<String, String>narrow(mapResult).getLeft()).isNull();

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> traverseResult =
          traverse.traverse(maybeApplicative, validTraverseFunction, leftNullKind);
      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(traverseResult);
      assertThat(EITHER.<String, String>narrow(maybe.get()).getLeft()).isNull();

      String foldResult = traverse.foldMap(stringMonoid, validFoldMapFunction, leftNullKind);
      assertThat(foldResult).isEqualTo(stringMonoid.empty());
    }

    @Test
    @DisplayName("Operations with complex nested structures")
    void operationsWithComplexStructures() {
      // Test with complex Right value
      Kind<EitherKind.Witness<String>, java.util.List<Integer>> complexRight =
          EITHER.widen(Either.right(java.util.List.of(1, 2, 3)));

      Kind<EitherKind.Witness<String>, Integer> sizeResult =
          traverse.map(list -> list.size(), complexRight);
      assertThat(EITHER.<String, Integer>narrow(sizeResult).getRight()).isEqualTo(3);

      // Test traverse with list processing
      Function<java.util.List<Integer>, Kind<MaybeKind.Witness, String>> listTraverse =
          list -> MAYBE.widen(Maybe.just("List size: " + list.size()));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> listTraverseResult =
          traverse.traverse(maybeApplicative, listTraverse, complexRight);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(listTraverseResult);
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight()).isEqualTo("List size: 3");
    }
  }

  @Nested
  @DisplayName("Type Safety and Generic Behavior")
  class TypeSafetyTests {

    @Test
    @DisplayName("Test with different error types")
    void testDifferentErrorTypes() {
      // Test with RuntimeException as error type
      EitherTraverse<RuntimeException> exceptionTraverse = EitherTraverse.instance();
      RuntimeException error = new RuntimeException("Test error");

      Kind<EitherKind.Witness<RuntimeException>, Integer> exceptionLeft =
          EITHER.widen(Either.left(error));
      Kind<EitherKind.Witness<RuntimeException>, Integer> exceptionRight =
          EITHER.widen(Either.right(100));

      // Map should preserve exception in Left
      Kind<EitherKind.Witness<RuntimeException>, String> mapResult =
          exceptionTraverse.map(Object::toString, exceptionLeft);
      assertThat(EITHER.<RuntimeException, String>narrow(mapResult).getLeft()).isSameAs(error);

      // Map should work on Right
      Kind<EitherKind.Witness<RuntimeException>, String> rightMapResult =
          exceptionTraverse.map(Object::toString, exceptionRight);
      assertThat(EITHER.<RuntimeException, String>narrow(rightMapResult).getRight())
          .isEqualTo("100");
    }

    @Test
    @DisplayName("Test type inference with different value types")
    void testDifferentValueTypes() {
      // Test with Double values
      Kind<EitherKind.Witness<String>, Double> doubleRight = EITHER.widen(Either.right(3.14159));

      Kind<EitherKind.Witness<String>, String> doubleResult =
          traverse.map(d -> String.format("%.2f", d), doubleRight);
      assertThat(EITHER.<String, String>narrow(doubleResult).getRight()).isEqualTo("3.14");

      // Test with Boolean values
      Kind<EitherKind.Witness<String>, Boolean> booleanRight = EITHER.widen(Either.right(true));

      Kind<EitherKind.Witness<String>, String> boolResult =
          traverse.map(b -> b ? "yes" : "no", booleanRight);
      assertThat(EITHER.<String, String>narrow(boolResult).getRight()).isEqualTo("yes");
    }
  }

  @Nested
  @DisplayName("Performance and Memory Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Test operations with large data structures")
    void testLargeDataStructures() {
      // Create a large list in Right
      java.util.List<Integer> largeList =
          java.util.stream.IntStream.range(0, 1000)
              .boxed()
              .collect(java.util.stream.Collectors.toList());

      Kind<EitherKind.Witness<String>, java.util.List<Integer>> largeRight =
          EITHER.widen(Either.right(largeList));

      // Test map with large data
      Kind<EitherKind.Witness<String>, Integer> sizeResult =
          traverse.map(java.util.List::size, largeRight);
      assertThat(EITHER.<String, Integer>narrow(sizeResult).getRight()).isEqualTo(1000);

      // Test foldMap with large data
      Function<java.util.List<Integer>, String> listProcessor =
          list -> "Processed " + list.size() + " items";
      String foldResult = traverse.foldMap(stringMonoid, listProcessor, largeRight);
      assertThat(foldResult).isEqualTo("Processed 1000 items");

      // Test traverse with large data (but simple function to avoid memory issues)
      Function<java.util.List<Integer>, Kind<MaybeKind.Witness, String>> largeTraverse =
          list -> MAYBE.widen(Maybe.just("Large list: " + list.size()));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> traverseResult =
          traverse.traverse(maybeApplicative, largeTraverse, largeRight);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(traverseResult);
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight())
          .isEqualTo("Large list: 1000");
    }

    @Test
    @DisplayName("Test chained operations for memory efficiency")
    void testChainedOperations() {
      Kind<EitherKind.Witness<String>, Integer> start = rightKind;

      // Chain multiple map operations
      Kind<EitherKind.Witness<String>, String> result = traverse.map(Object::toString, start);
      for (int i = 0; i < 10; i++) {
        final int iteration = i;
        result = traverse.map(current -> current + ":" + iteration, result);
      }

      Either<String, String> finalResult = EITHER.narrow(result);
      assertThat(finalResult.isRight()).isTrue();
      assertThat(finalResult.getRight()).isEqualTo(SUCCESS_VALUE + ":0:1:2:3:4:5:6:7:8:9");
    }
  }

  @Nested
  @DisplayName("Integration with Test Framework")
  class IntegrationTests {

    @Test
    @DisplayName("Test using common test functions and utilities")
    void testWithCommonFunctions() {
      // Test with common test functions
      Kind<EitherKind.Witness<String>, String> result1 =
          traverse.map(CommonTestFunctions.INT_TO_STRING, rightKind);
      assertThat(EITHER.<String, String>narrow(result1).getRight()).isEqualTo("42");

      Kind<EitherKind.Witness<String>, String> result2 =
          traverse.map(CommonTestFunctions.APPEND_SUFFIX, result1);
      assertThat(EITHER.<String, String>narrow(result2).getRight()).isEqualTo("42_test");

      // Test exception propagation with common test exception
      RuntimeException testException = createTestException("integration test");
      Function<Integer, String> throwingFunction =
          CommonTestFunctions.throwingFunction(testException);

      assertThatThrownBy(() -> traverse.map(throwingFunction, rightKind)).isSameAs(testException);
    }

    @Test
    @DisplayName("Test combined with other testing utilities")
    void testCombinedWithOtherUtilities() {
      // Test basic Traverse operations
      testBasicTraverseOperations(
          traverse,
          rightKind,
          validMapper,
          maybeApplicative,
          validTraverseFunction,
          stringMonoid,
          validFoldMapFunction);

      // Test null validations
      testAllTraverseNullValidations(
          traverse,
          rightKind,
          validMapper,
          maybeApplicative,
          validTraverseFunction,
          stringMonoid,
          validFoldMapFunction);

      // Test exception propagation
      RuntimeException testException = createTestException("combined test");
      testTraverseExceptionPropagation(
          traverse, rightKind, maybeApplicative, stringMonoid, testException);

      // Verify specific EitherTraverse behavior
      Either<String, String> mapResult = EITHER.narrow(traverse.map(validMapper, rightKind));
      assertThat(mapResult.getRight()).isEqualTo("Mapped: " + SUCCESS_VALUE);
    }
  }

  @Nested
  @DisplayName("Comprehensive Traverse Laws and Properties")
  class TraverseLawsTests {

    @Test
    @DisplayName("Test Traverse naturality with proper error validation")
    void testTraverseNaturality() {
      // This is a simplified test of naturality concepts
      // Full naturality testing requires natural transformations which are complex to implement
      // generically

      // Test that traverse preserves the structure appropriately
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result1 =
          traverse.traverse(maybeApplicative, validTraverseFunction, rightKind);

      // The result should be consistent regardless of how we approach it
      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result1);
      assertThat(maybe.isJust()).isTrue();

      Either<String, String> either = EITHER.narrow(maybe.get());
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo("Traversed: " + SUCCESS_VALUE);

      // Test error validation for naturality components
      TraverseValidationTestBuilder.create()
          .assertTraverseNullApplicative(
              () -> traverse.traverse(null, validTraverseFunction, rightKind))
          .assertTraverseNullFunction(() -> traverse.traverse(maybeApplicative, null, rightKind))
          .assertTraverseNullSourceKind(
              () -> traverse.traverse(maybeApplicative, validTraverseFunction, null))
          .execute();
    }

    @Test
    @DisplayName("Test Traverse composition properties")
    void testTraverseComposition() {
      // Test that traverse can be composed properly
      Function<Integer, Kind<MaybeKind.Witness, String>> f1 = validTraverseFunction;

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result1 =
          traverse.traverse(maybeApplicative, f1, rightKind);

      assertThat(MAYBE.narrow(result1).isJust()).isTrue();

      // Test with Left - should preserve the structure
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result2 =
          traverse.traverse(maybeApplicative, f1, leftKind);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe2 = MAYBE.narrow(result2);
      assertThat(maybe2.isJust()).isTrue();
      assertThat(EITHER.<String, String>narrow(maybe2.get()).isLeft()).isTrue();
    }

    @Test
    @DisplayName("Test Traverse preservation properties")
    void testTraversePreservation() {
      // Test that traverse preserves the essential structure
      // Right values get transformed, Left values pass through

      // Multiple Right values with different functions
      Function<Integer, Kind<MaybeKind.Witness, String>> successFunction =
          i -> MAYBE.widen(Maybe.just("Success: " + i));
      Function<Integer, Kind<MaybeKind.Witness, String>> failFunction =
          i -> MAYBE.widen(Maybe.nothing());

      // Success case
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> successResult =
          traverse.traverse(maybeApplicative, successFunction, rightKind);
      TraverseResultVerifiers.verifyTraverseSuccess(
          successResult,
          MAYBE::narrow,
          maybeResult -> EITHER.<String, String>narrow(maybeResult.get()).getRight(),
          "Success: " + SUCCESS_VALUE);

      // Failure case
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> failResult =
          traverse.traverse(maybeApplicative, failFunction, rightKind);
      TraverseResultVerifiers.verifyTraverseFailure(
          failResult, result -> MAYBE.narrow(result).isNothing());

      // Left preservation
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> leftResult =
          traverse.traverse(maybeApplicative, successFunction, leftKind);
      TraverseResultVerifiers.verifyTraverseSuccess(
          leftResult,
          MAYBE::narrow,
          maybeResult -> EITHER.<String, String>narrow(maybeResult.get()).getLeft(),
          ERROR_VALUE);
    }
  }

  @Nested
  @DisplayName("Comprehensive Foldable Properties")
  class FoldablePropertiesTests {

    @Test
    @DisplayName("Test foldMap with different monoid properties")
    void testFoldMapMonoidProperties() {
      // Test with different monoids to ensure they work correctly

      // String concatenation
      String stringResult = traverse.foldMap(Monoids.string(), i -> "Item" + i + ",", rightKind);
      assertThat(stringResult).isEqualTo("Item" + SUCCESS_VALUE + ",");

      // Integer addition
      Integer addResult = traverse.foldMap(Monoids.integerAddition(), i -> i * 2, rightKind);
      assertThat(addResult).isEqualTo(SUCCESS_VALUE * 2);

      // Integer multiplication
      Integer multResult = traverse.foldMap(Monoids.integerMultiplication(), i -> i, rightKind);
      assertThat(multResult).isEqualTo(SUCCESS_VALUE);

      // Boolean operations
      Boolean andResult = traverse.foldMap(Monoids.booleanAnd(), i -> i > 0, rightKind);
      assertThat(andResult).isTrue();

      Boolean orResult = traverse.foldMap(Monoids.booleanOr(), i -> i < 0, rightKind);
      assertThat(orResult).isFalse();
    }

    @Test
    @DisplayName("Test foldMap empty behavior with Left values")
    void testFoldMapEmptyBehavior() {
      // All Left values should result in monoid empty regardless of function
      Function<Integer, String> complexFunction = i -> "Complex processing: " + (i * i + i / 2.0);

      String result = traverse.foldMap(stringMonoid, complexFunction, leftKind);
      assertThat(result).isEqualTo(stringMonoid.empty());

      // Even with functions that would throw on the value
      Function<Integer, String> throwingFunction =
          i -> {
            throw new RuntimeException("Should not be called");
          };

      String safeResult = traverse.foldMap(stringMonoid, throwingFunction, leftKind);
      assertThat(safeResult).isEqualTo(stringMonoid.empty());
    }
  }

  @Nested
  @DisplayName("Edge Cases and Corner Cases")
  class EdgeCasesTests {

    @Test
    @DisplayName("Test operations with various null configurations")
    void testNullConfigurations() {
      // Right with null value
      Kind<EitherKind.Witness<String>, Integer> rightNull = EITHER.widen(Either.right(null));

      // Left with null error
      Kind<EitherKind.Witness<String>, Integer> leftNull = EITHER.widen(Either.left(null));

      // Functions that handle nulls appropriately
      Function<Integer, String> nullSafeMapper = i -> i == null ? "NULL" : i.toString();

      // Test all operations handle these cases
      assertThat(EITHER.<String, String>narrow(traverse.map(nullSafeMapper, rightNull)).getRight())
          .isEqualTo("NULL");

      assertThat(EITHER.<String, String>narrow(traverse.map(nullSafeMapper, leftNull)).getLeft())
          .isNull();

      String foldNull = traverse.foldMap(stringMonoid, nullSafeMapper, rightNull);
      assertThat(foldNull).isEqualTo("NULL");

      String foldLeftNull = traverse.foldMap(stringMonoid, nullSafeMapper, leftNull);
      assertThat(foldLeftNull).isEqualTo(stringMonoid.empty());
    }

    @Test
    @DisplayName("Test with extreme or boundary values")
    void testBoundaryValues() {
      // Test with Integer boundary values
      Kind<EitherKind.Witness<String>, Integer> maxInt =
          EITHER.widen(Either.right(Integer.MAX_VALUE));
      Kind<EitherKind.Witness<String>, Integer> minInt =
          EITHER.widen(Either.right(Integer.MIN_VALUE));
      Kind<EitherKind.Witness<String>, Integer> zero = EITHER.widen(Either.right(0));

      Function<Integer, String> boundaryMapper =
          i ->
              String.format(
                  "Value: %d, Sign: %s", i, i > 0 ? "positive" : i < 0 ? "negative" : "zero");

      assertThat(EITHER.<String, String>narrow(traverse.map(boundaryMapper, maxInt)).getRight())
          .contains("positive");

      assertThat(EITHER.<String, String>narrow(traverse.map(boundaryMapper, minInt)).getRight())
          .contains("negative");

      assertThat(EITHER.<String, String>narrow(traverse.map(boundaryMapper, zero)).getRight())
          .contains("zero");
    }

    @Test
    @DisplayName("Test deeply nested generic types")
    void testDeeplyNestedTypes() {
      // Test with nested generic structures
      java.util.Map<String, java.util.List<Integer>> complexMap =
          java.util.Map.of("numbers", java.util.List.of(1, 2, 3), "more", java.util.List.of(4, 5));

      Kind<EitherKind.Witness<String>, java.util.Map<String, java.util.List<Integer>>>
          complexRight = EITHER.widen(Either.right(complexMap));

      Function<java.util.Map<String, java.util.List<Integer>>, String> complexMapper =
          map ->
              "Map has "
                  + map.size()
                  + " keys with "
                  + map.values().stream().mapToInt(java.util.List::size).sum()
                  + " total items";

      Kind<EitherKind.Witness<String>, String> result = traverse.map(complexMapper, complexRight);

      assertThat(EITHER.<String, String>narrow(result).getRight())
          .isEqualTo("Map has 2 keys with 5 total items");
    }
  }

  @Nested
  @DisplayName("Documentation and Example Verification")
  class DocumentationTests {

    @Test
    @DisplayName("Verify examples from class documentation work correctly")
    void verifyDocumentationExamples() {
      // Test the right-biased nature documented in the class

      // Right case: operation applied
      Either<String, Integer> rightEither = Either.right(10);
      Kind<EitherKind.Witness<String>, Integer> rightKind = EITHER.widen(rightEither);

      Kind<EitherKind.Witness<String>, String> mappedRight =
          traverse.map(i -> "Processed: " + i, rightKind);

      assertThat(EITHER.<String, String>narrow(mappedRight).getRight()).isEqualTo("Processed: 10");

      // Left case: operation bypassed
      Either<String, Integer> leftEither = Either.left("Error occurred");
      Kind<EitherKind.Witness<String>, Integer> leftKind = EITHER.widen(leftEither);

      Kind<EitherKind.Witness<String>, String> mappedLeft =
          traverse.map(i -> "Processed: " + i, leftKind);

      assertThat(EITHER.<String, String>narrow(mappedLeft).getLeft()).isEqualTo("Error occurred");

      // Traverse example: Right gets transformed through applicative
      Function<Integer, Kind<MaybeKind.Witness, String>> validatePositive =
          i -> i > 0 ? MAYBE.widen(Maybe.just("Valid: " + i)) : MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> traverseResult =
          traverse.traverse(maybeApplicative, validatePositive, rightKind);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(traverseResult);
      assertThat(maybe.isJust()).isTrue();
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight()).isEqualTo("Valid: 10");
    }

    @Test
    @DisplayName("Verify type safety promises from documentation")
    void verifyTypeSafetyPromises() {
      // Documentation promises that operations are right-biased and type-safe

      // Test with different generic type combinations
      EitherTraverse<RuntimeException> exceptionTraverse = EitherTraverse.instance();
      EitherTraverse<java.time.LocalDate> dateTraverse = EitherTraverse.instance();
      EitherTraverse<java.util.UUID> uuidTraverse = EitherTraverse.instance();

      // All should be the same singleton instance due to type erasure
      assertThat(exceptionTraverse).isSameAs(traverse);
      assertThat(dateTraverse).isSameAs(traverse);
      assertThat(uuidTraverse).isSameAs(traverse);

      // But they should work correctly with their respective types
      RuntimeException error = new RuntimeException("Test");
      Kind<EitherKind.Witness<RuntimeException>, String> exceptionLeft =
          EITHER.widen(Either.left(error));

      Kind<EitherKind.Witness<RuntimeException>, String> exceptionResult =
          exceptionTraverse.map(s -> s.toUpperCase(), exceptionLeft);

      assertThat(EITHER.<RuntimeException, String>narrow(exceptionResult).getLeft())
          .isSameAs(error);
    }
  }
}
