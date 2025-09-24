// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.test.data.TestData;
import org.higherkindedj.hkt.test.patterns.KindHelperTestPattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherKindHelper Tests")
class EitherKindHelperTest {

  // Define a simple error type for testing
  record TestError(String code) {}

  @Nested
  @DisplayName("Complete KindHelper Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete KindHelper test suite for Either")
    void completeKindHelperTestSuite() {
      Either<TestError, String> validInstance = Either.right("Success");

      // Run the complete test suite using the pattern
      KindHelperTestPattern.runComplete(
          validInstance,
          Either.class,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<TestError>, String> kind) -> EITHER.narrow(kind));
    }

    @Test
    @DisplayName("Complete test suite with multiple Either types")
    void completeTestSuiteWithMultipleTypes() {
      // Test with Right instance
      Either<TestError, String> rightInstance = Either.right("Success");
      KindHelperTestPattern.runComplete(
          rightInstance,
          Either.class,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<TestError>, String> kind) -> EITHER.narrow(kind));

      // Test with Left instance
      Either<TestError, String> leftInstance = Either.left(new TestError("E404"));
      KindHelperTestPattern.runComplete(
          leftInstance,
          Either.class,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<TestError>, String> kind) -> EITHER.narrow(kind));

      // Test with null values
      Either<TestError, String> rightNull = Either.right(null);
      KindHelperTestPattern.runComplete(
          rightNull,
          Either.class,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<TestError>, String> kind) -> EITHER.narrow(kind));

      Either<TestError, String> leftNull = Either.left(null);
      KindHelperTestPattern.runComplete(
          leftNull,
          Either.class,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<TestError>, String> kind) -> EITHER.narrow(kind));
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponentTests {

    @Test
    @DisplayName("Test round-trip widen/narrow operations")
    void testRoundTripOperations() {
      Either<TestError, String> validInstance = Either.right("test");

      KindHelperTestPattern.testRoundTrip(validInstance, EITHER::widen, EITHER::narrow);
    }

    @Test
    @DisplayName("Test null parameter validations")
    void testNullParameterValidations() {
      KindHelperTestPattern.testNullValidations(
          Either.class,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<TestError>, String> kind) -> EITHER.narrow(kind));
    }

    @Test
    @DisplayName("Test invalid Kind type handling")
    void testInvalidKindType() {
      KindHelperTestPattern.testInvalidType(
          Either.class,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<TestError>, String> kind) -> EITHER.narrow(kind));
    }

    @Test
    @DisplayName("Test idempotency of operations")
    void testIdempotency() {
      Either<TestError, String> validInstance = Either.right("idempotent");

      KindHelperTestPattern.testIdempotency(validInstance, EITHER::widen, EITHER::narrow);
    }

    @Test
    @DisplayName("Test specific error conditions with ValidationTestBuilder")
    void testSpecificErrorConditions() {
      Either<TestError, String> validEither = Either.right("test");
      Kind<EitherKind.Witness<TestError>, String> validKind = EITHER.widen(validEither);
      Kind<EitherKind.Witness<TestError>, String> invalidKind = TestData.createDummyKind("invalid");

      ValidationTestBuilder.create()
          .assertWidenNull(() -> EITHER.widen(null), Either.class)
          .assertNarrowNull(() -> EITHER.narrow(null), Either.class)
          .assertInvalidKindType(() -> EITHER.narrow(invalidKind), Either.class, invalidKind)
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

      // Test round-trip for Left
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
      KindHelperTestPattern.runComplete(
          complexEither,
          Either.class,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<TestError>, String> kind) -> EITHER.narrow(kind));

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

      // Test both types work with the helper methods
      KindHelperTestPattern.runComplete(
          stringLeftEither,
          Either.class,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<TestError>, String> kind) -> EITHER.narrow(kind));

      KindHelperTestPattern.runComplete(
          testErrorLeftEither,
          Either.class,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<TestError>, String> kind) -> EITHER.narrow(kind));

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
      List<Either<TestError, String>> instances = new ArrayList<>();

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
      List<Either<TestError, String>> nullInstances =
          List.of(
              Either.right(null),
              Either.left(null),
              Either.left(new TestError(null)), // TestError with null code
              Either.right("") // Empty string
              );

      // All should work with widen/narrow without validation errors
      for (Either<TestError, String> instance : nullInstances) {
        KindHelperTestPattern.runComplete(
            instance,
            Either.class,
            either -> EITHER.widen(either),
            (Kind<EitherKind.Witness<TestError>, String> kind) -> EITHER.narrow(kind));
      }
    }

    @Test
    @DisplayName("Test error messages contain expected information")
    void testErrorMessageContents() {
      Kind<EitherKind.Witness<TestError>, String> invalidKind =
          TestData.createDummyKind("test_invalid");

      // Test that error messages contain the type name and class information
      ValidationTestBuilder.create()
          .assertWidenNull(() -> EITHER.widen(null), Either.class)
          .assertNarrowNull(() -> EITHER.narrow(null), Either.class)
          .assertInvalidKindType(() -> EITHER.narrow(invalidKind), Either.class, invalidKind)
          .execute();
    }

    @Test
    @DisplayName("Test with nested generic types")
    void testNestedGenericTypes() {
      // Test with complex nested types
      Either<TestError, List<String>> complexRight = Either.right(List.of("a", "b", "c"));
      Either<TestError, List<String>> complexLeft = Either.left(new TestError("ComplexError"));

      // Both should work correctly with the standard operations
      KindHelperTestPattern.runComplete(
          complexRight,
          Either.class,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<TestError>, String> kind) -> EITHER.narrow(kind));

      KindHelperTestPattern.runComplete(
          complexLeft,
          Either.class,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<TestError>, String> kind) -> EITHER.narrow(kind));

      // Verify nested structure is preserved
      Kind<EitherKind.Witness<TestError>, List<String>> rightKind = EITHER.widen(complexRight);
      Kind<EitherKind.Witness<TestError>, List<String>> leftKind = EITHER.widen(complexLeft);

      Either<TestError, List<String>> narrowedRight = EITHER.narrow(rightKind);
      Either<TestError, List<String>> narrowedLeft = EITHER.narrow(leftKind);

      assertThat(narrowedRight).isSameAs(complexRight);
      assertThat(narrowedLeft).isSameAs(complexLeft);
      assertThat(narrowedRight.getRight()).containsExactly("a", "b", "c");
      assertThat(narrowedLeft.getLeft().code()).isEqualTo("ComplexError");
    }
  }

  @Nested
  @DisplayName("Integration with Test Framework")
  class IntegrationTests {

    @Test
    @DisplayName("Test using TestData utilities")
    void testWithTestDataUtilities() {
      Either<TestError, String> testInstance = Either.right("integration_test");

      // Use TestData for creating dummy kinds
      Kind<EitherKind.Witness<TestError>, String> dummyKind =
          TestData.createDummyKind("integration_dummy");

      ValidationTestBuilder.create()
          .assertInvalidKindType(() -> EITHER.narrow(dummyKind), Either.class, dummyKind)
          .execute();
    }

    @Test
    @DisplayName("Test combined with pattern testing")
    void testCombinedPatterns() {
      Either<TestError, String> instance = Either.right("combined");

      // First, run the complete suite using pattern
      KindHelperTestPattern.runComplete(
          instance,
          Either.class,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<TestError>, String> kind) -> EITHER.narrow(kind));

      // Then, add some specific custom validations
      ValidationTestBuilder.create()
          .assertWidenNull(() -> EITHER.widen(null), Either.class)
          .execute();

      // Finally, test individual operations
      KindHelperTestPattern.testRoundTrip(instance, EITHER::widen, EITHER::narrow);

      // All should pass without conflicts
      assertThat(instance.getRight()).isEqualTo("combined");
    }

    @Test
    @DisplayName("Test with various TestData generated values")
    void testWithGeneratedValues() {
      // Use TestData to generate test strings
      List<String> testStrings = TestData.getTestStrings();

      for (String testString : testStrings) {
        Either<TestError, String> either = Either.right(testString);

        Kind<EitherKind.Witness<TestError>, String> widened = EITHER.widen(either);
        Either<TestError, String> narrowed = EITHER.narrow(widened);

        assertThat(narrowed).isSameAs(either);
        assertThat(narrowed.getRight()).isEqualTo(testString);
      }
    }
  }

  @Nested
  @DisplayName("Named Dummy Kind Tests")
  class NamedDummyKindTests {

    @Test
    @DisplayName("Test with named dummy kinds for better error messages")
    void testWithNamedDummyKinds() {
      Kind<EitherKind.Witness<TestError>, String> namedDummy =
          TestData.createNamedDummyKind("TestDummy", "EitherKind.Witness<TestError>", "String");

      ValidationTestBuilder.create()
          .assertInvalidKindType(() -> EITHER.narrow(namedDummy), Either.class, namedDummy)
          .execute();

      // Verify the dummy has descriptive toString
      assertThat(namedDummy.toString())
          .contains("TestDummy")
          .contains("EitherKind.Witness<TestError>")
          .contains("String");
    }
  }
}
