// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.*;
import static org.higherkindedj.hkt.test.HKTTestAssertions.*;
import static org.higherkindedj.hkt.test.HKTTestHelpers.*;

import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherKindHelper Tests")
class EitherKindHelperTest {
    private static final String EITHER_TYPE = "Either";
  // Define a simple error type for testing
  record TestError(String code) {}

  @Nested
  @DisplayName("Complete KindHelper Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete KindHelper test suite for Either")
    void completeKindHelperTestSuite() {
      Either<TestError, String> validInstance = Either.right("Success");

      // Run the complete test suite in one call
      runCompleteKindHelperTestSuite(validInstance, EITHER_TYPE, EITHER::widen, EITHER::narrow);
    }

    @Test
    @DisplayName("Complete test suite with multiple Either types")
    void completeTestSuiteWithMultipleTypes() {
      // Test with Right instance
      Either<TestError, String> rightInstance = Either.right("Success");
      runCompleteKindHelperTestSuite(rightInstance, EITHER_TYPE, EITHER::widen, EITHER::narrow);

      // Test with Left instance
      Either<TestError, String> leftInstance = Either.left(new TestError("E404"));
      runCompleteKindHelperTestSuite(leftInstance, EITHER_TYPE, EITHER::widen, EITHER::narrow);

      // Test with null values
      Either<TestError, String> rightNull = Either.right(null);
      runCompleteKindHelperTestSuite(rightNull, EITHER_TYPE, EITHER::widen, EITHER::narrow);

      Either<TestError, String> leftNull = Either.left(null);
      runCompleteKindHelperTestSuite(leftNull, EITHER_TYPE, EITHER::widen, EITHER::narrow);
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponentTests {

    @Test
    @DisplayName("Test standard KindHelper operations individually")
    void testStandardOperations() {
      Either<TestError, String> validInstance = Either.right("test");
      Kind<EitherKind.Witness<TestError>, String> invalidKind = createDummyKind("invalid");

      // Test using individual helper method
      testStandardKindHelperOperations(
          validInstance, EITHER_TYPE, invalidKind, EITHER::widen, EITHER::narrow);
    }

    @Test
    @DisplayName("Test specific error conditions with ValidationTestBuilder")
    void testSpecificErrorConditions() {
      Either<TestError, String> validEither = Either.right("test");
      Kind<EitherKind.Witness<TestError>, String> validKind = EITHER.widen(validEither);
      Kind<EitherKind.Witness<TestError>, String> invalidKind = createDummyKind("invalid");

      // Use ValidationTestBuilder for custom validation scenarios
      ValidationTestBuilder.create()
          .assertNullWidenInput(() -> EITHER.widen(null), EITHER_TYPE)
          .assertNullKindNarrow(() -> EITHER.narrow(null), EITHER_TYPE)
          .assertInvalidKindType(() -> EITHER.narrow(invalidKind), EITHER_TYPE, invalidKind)
          .execute();
    }
  }

  @Nested
  @DisplayName("Specific Either Behavior Tests")
  class SpecificBehaviorTests {

    @Test
    @DisplayName("Test Both Right and Left instances work correctly")
    void testRightAndLeftInstances() {
      Either<TestError, String> right = Either.right("Success");
      Either<TestError, String> left = Either.left(new TestError("E404"));

      // Test round-trip for Right
      Kind<EitherKind.Witness<TestError>, String> rightKind = EITHER.widen(right);
      assertThat(EITHER.narrow(rightKind)).isSameAs(right);

      // Test round-trip for Left - Fixed: compare narrowed result with original Either
      Kind<EitherKind.Witness<TestError>, String> leftKind = EITHER.widen(left);
      assertThat(EITHER.narrow(leftKind)).isSameAs(left);
    }

    @Test
    @DisplayName("Test null values in Either are preserved")
    void testNullValuesPreserved() {
      Either<TestError, String> rightNull = Either.right(null);
      Either<TestError, String> leftNull = Either.left(null);

      // Test Right with null value
      Kind<EitherKind.Witness<TestError>, String> rightKind = EITHER.widen(rightNull);
      Either<TestError, String> narrowedRight = EITHER.narrow(rightKind);
      assertThat(narrowedRight).isSameAs(rightNull);
      assertThat(narrowedRight.isRight()).isTrue();
      assertThat(narrowedRight.getRight()).isNull();

      // Test Left with null value
      Kind<EitherKind.Witness<TestError>, String> leftKind = EITHER.widen(leftNull);
      Either<TestError, String> narrowedLeft = EITHER.narrow(leftKind);
      assertThat(narrowedLeft).isSameAs(leftNull);
      assertThat(narrowedLeft.isLeft()).isTrue();
      assertThat(narrowedLeft.getLeft()).isNull();
    }

    @Test
    @DisplayName("Test complex error types work correctly")
    void testComplexErrorTypes() {
      // Test with a more complex error type
      record ComplexError(
          String code, String message, Throwable cause, java.time.Instant timestamp) {}

      ComplexError complexError =
          new ComplexError(
              "COMPLEX_001",
              "Complex error occurred",
              new RuntimeException("Root cause"),
              java.time.Instant.now());

      Either<ComplexError, String> complexEither = Either.left(complexError);

      // Run standard operations test with complex type
      testStandardKindHelperOperations(
          complexEither,
              EITHER_TYPE,
          createDummyKind("invalid_complex"),
          EITHER::widen,
          EITHER::narrow);

      // Verify the complex error is preserved
      Kind<EitherKind.Witness<ComplexError>, String> complexKind = EITHER.widen(complexEither);
      Either<ComplexError, String> narrowed = EITHER.narrow(complexKind);
      assertThat(narrowed).isSameAs(complexEither);
      assertThat(narrowed.getLeft()).isSameAs(complexError);
      assertThat(narrowed.getLeft().code()).isEqualTo("COMPLEX_001");
    }

    @Test
    @DisplayName("Test type safety across different generic parameters")
    void testTypeSafetyAcrossDifferentGenerics() {
      // Test with different left types
      Either<String, Integer> stringLeftEither = Either.left("StringError");
      Either<TestError, Integer> testErrorLeftEither = Either.left(new TestError("E1"));

      // Test both types work with the same helper methods
      testStandardKindHelperOperations(
          stringLeftEither,
              EITHER_TYPE,
          createDummyKind("invalid_string"),
          EITHER::widen,
          EITHER::narrow);

      testStandardKindHelperOperations(
          testErrorLeftEither,
              EITHER_TYPE,
          createDummyKind("invalid_test_error"),
          EITHER::widen,
          EITHER::narrow);

      // Verify types are maintained correctly
      Kind<EitherKind.Witness<String>, Integer> stringKind = EITHER.widen(stringLeftEither);
      Kind<EitherKind.Witness<TestError>, Integer> testErrorKind =
          EITHER.widen(testErrorLeftEither);

      Either<String, Integer> narrowedString = EITHER.narrow(stringKind);
      Either<TestError, Integer> narrowedTestError = EITHER.narrow(testErrorKind);

      assertThat(narrowedString).isSameAs(stringLeftEither);
      assertThat(narrowedTestError).isSameAs(testErrorLeftEither);
      assertThat(narrowedString.getLeft()).isInstanceOf(String.class);
      assertThat(narrowedTestError.getLeft()).isInstanceOf(TestError.class);
    }
  }

  @Nested
  @DisplayName("Performance and Memory Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Test holder creates minimal overhead")
    void testMinimalOverhead() {
      Either<TestError, String> original = Either.right("test");
      Kind<EitherKind.Witness<TestError>, String> holder = EITHER.widen(original);

      // The holder should be lightweight
      assertThat(holder).isInstanceOf(EitherKindHelper.EitherHolder.class);

      // Narrowing should return the exact same instance (no copying)
      Either<TestError, String> narrowed = EITHER.narrow(holder);
      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("Test multiple operations are idempotent")
    void testIdempotentOperations() {
      Either<TestError, String> original = Either.right("idempotent");

      // Multiple round trips should not affect the result
      Either<TestError, String> result = original;
      for (int i = 0; i < 5; i++) {
        Kind<EitherKind.Witness<TestError>, String> widened = EITHER.widen(result);
        result = EITHER.narrow(widened);
      }

      assertThat(result).isSameAs(original);
    }

    @Test
    @DisplayName("Test operations work with large numbers of instances")
    void testLargeNumberOfInstances() {
      java.util.List<Either<TestError, String>> instances = new java.util.ArrayList<>();

      // Create many instances of different types
      for (int i = 0; i < 100; i++) {
        instances.add(Either.right("right_" + i));
        instances.add(Either.left(new TestError("E" + i)));
      }

      // Test that all instances work correctly with widen/narrow
      for (Either<TestError, String> instance : instances) {
        Kind<EitherKind.Witness<TestError>, String> widened = EITHER.widen(instance);
        Either<TestError, String> narrowed = EITHER.narrow(widened);
        assertThat(narrowed).isSameAs(instance);
      }
    }
  }

  @Nested
  @DisplayName("Edge Cases and Corner Cases")
  class EdgeCasesTests {

    @Test
    @DisplayName("Test all combinations of null values")
    void testAllNullValueCombinations() {
      java.util.List<Either<TestError, String>> nullInstances =
          java.util.List.of(
              Either.right(null),
              Either.left(null),
              Either.left(new TestError(null)), // TestError with null code
              Either.right("") // Empty string
              );

      // All should work with widen/narrow without validation errors
      for (Either<TestError, String> instance : nullInstances) {
        // Use the complete test suite which includes all validations
        runCompleteKindHelperTestSuite(instance, EITHER_TYPE, EITHER::widen, EITHER::narrow);
      }
    }

    @Test
    @DisplayName("Test error messages contain expected information")
    void testErrorMessageContents() {
      Kind<EitherKind.Witness<TestError>, String> invalidKind = createDummyKind("test_invalid");

      // Test that error messages contain the type name and class information
      ValidationTestBuilder.create()
          .assertNullWidenInput(() -> EITHER.widen(null), EITHER_TYPE)
          .assertNullKindNarrow(() -> EITHER.narrow(null), EITHER_TYPE)
          .assertInvalidKindType(() -> EITHER.narrow(invalidKind), EITHER_TYPE, invalidKind)
          .execute();

      // The ValidationTestBuilder.execute() will verify:
      // - Null widen input message contains "Either"
      // - Null Kind narrow message contains "Either"
      // - Invalid Kind type message contains "Either" and the actual class name
    }

    @Test
    @DisplayName("Test with nested generic types")
    void testNestedGenericTypes() {
      // Test with complex nested types
      Either<TestError, java.util.List<String>> complexRight =
          Either.right(java.util.List.of("a", "b", "c"));
      Either<TestError, java.util.List<String>> complexLeft =
          Either.left(new TestError("ComplexError"));

      // Both should work correctly with the standard operations
      testStandardKindHelperOperations(
          complexRight,
              EITHER_TYPE,
          createDummyKind("invalid_complex"),
          EITHER::widen,
          EITHER::narrow);

      testStandardKindHelperOperations(
          complexLeft, EITHER_TYPE, createDummyKind("invalid_complex"), EITHER::widen, EITHER::narrow);

      // Verify nested structure is preserved
      Kind<EitherKind.Witness<TestError>, java.util.List<String>> rightKind =
          EITHER.widen(complexRight);
      Kind<EitherKind.Witness<TestError>, java.util.List<String>> leftKind =
          EITHER.widen(complexLeft);

      Either<TestError, java.util.List<String>> narrowedRight = EITHER.narrow(rightKind);
      Either<TestError, java.util.List<String>> narrowedLeft = EITHER.narrow(leftKind);

      assertThat(narrowedRight).isSameAs(complexRight);
      assertThat(narrowedLeft).isSameAs(complexLeft);
      assertThat(narrowedRight.getRight()).containsExactly("a", "b", "c");
      assertThat(narrowedLeft.getLeft().code()).isEqualTo("ComplexError");
    }
  }

  @Nested
  @DisplayName("Integration with Common Test Functions")
  class IntegrationTests {

    @Test
    @DisplayName("Test using common test functions and data generators")
    void testWithCommonFunctions() {
      Either<TestError, String> testInstance = Either.right("integration_test");

      // Use common test exception for error testing
      RuntimeException testException = createTestException("EitherKindHelper integration test");

      // This would test exception handling if we had functions that could throw
      // For KindHelper, the main exceptions are from validation, which we test separately
      assertThat(testException).hasMessage("Test exception: EitherKindHelper integration test");

      // Test with dummy Kind from common utilities
      Kind<EitherKind.Witness<TestError>, String> dummyKind = createDummyKind("integration_dummy");

      ValidationTestBuilder.create()
          .assertInvalidKindType(() -> EITHER.narrow(dummyKind), EITHER_TYPE, dummyKind)
          .execute();
    }

    @Test
    @DisplayName("Test combined with other test patterns")
    void testCombinedPatterns() {
      Either<TestError, String> instance = Either.right("combined");

      // First, run the complete suite
      runCompleteKindHelperTestSuite(instance, EITHER_TYPE, EITHER::widen, EITHER::narrow);

      // Then, add some specific custom validations
      ValidationTestBuilder.create()
          .assertNullWidenInput(() -> EITHER.widen(null), EITHER_TYPE)
          .execute();

      // Finally, test individual operations
      testStandardKindHelperOperations(
          instance, EITHER_TYPE, createDummyKind("combined_invalid"), EITHER::widen, EITHER::narrow);

      // All should pass without conflicts
      assertThat(instance.getRight()).isEqualTo("combined");
    }
  }
}
