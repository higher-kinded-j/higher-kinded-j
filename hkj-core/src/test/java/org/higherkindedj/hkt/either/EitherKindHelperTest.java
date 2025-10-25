// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.test.api.CoreTypeTest.eitherKindHelper;

import java.util.List;
import org.higherkindedj.hkt.test.patterns.KindHelperTestPattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherKindHelper Complete Test Suite")
class EitherKindHelperTest extends EitherTestBase {

  @Nested
  @DisplayName("Complete KindHelper Test Suite")
  class CompleteTestSuite {
    @Test
    @DisplayName("Run complete KindHelper test suite for Either")
    void completeKindHelperTestSuite() {
      Either<ComplexTestError, String> validInstance = Either.right("Success");

      eitherKindHelper(validInstance).test();
    }

    @Test
    @DisplayName("Complete test suite with multiple Either types")
    void completeTestSuiteWithMultipleTypes() {
      List<Either<ComplexTestError, String>> testInstances =
          List.of(
              Either.right("Success"),
              Either.left(ComplexTestError.high("E404", "Not found")),
              Either.right(null),
              Either.left(null));

      for (Either<ComplexTestError, String> instance : testInstances) {
        eitherKindHelper(instance).test();
      }
    }

    @Test
    @DisplayName("Comprehensive test with implementation validation")
    void comprehensiveTestWithImplementationValidation() {
      Either<ComplexTestError, String> validInstance = Either.right("Comprehensive");
      eitherKindHelper(validInstance).testWithValidation(EitherKindHelper.class);
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponentTests {
    @Test
    @DisplayName("Test round-trip widen/narrow operations")
    void testRoundTripOperations() {
      Either<ComplexTestError, String> validInstance = Either.right("test");

      eitherKindHelper(validInstance)
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test null parameter validations")
    void testNullParameterValidations() {
      eitherKindHelper(Either.<ComplexTestError, String>right("test"))
          .skipRoundTrip()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test invalid Kind type handling")
    void testInvalidKindType() {
      eitherKindHelper(Either.<ComplexTestError, String>right("test"))
          .skipRoundTrip()
          .skipValidations()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test idempotency of operations")
    void testIdempotency() {
      Either<ComplexTestError, String> validInstance = Either.right("idempotent");

      eitherKindHelper(validInstance)
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test edge cases and boundary conditions")
    void testEdgeCases() {
      Either<ComplexTestError, String> validInstance = Either.right("edge");

      eitherKindHelper(validInstance)
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipIdempotency()
          .test();
    }
  }

  @Nested
  @DisplayName("Specific Either Behaviour Tests")
  class SpecificBehaviourTests {
    @Test
    @DisplayName("Both Right and Left instances work correctly")
    void testRightAndLeftInstances() {
      Either<ComplexTestError, String> right = Either.right("Success");
      Either<ComplexTestError, String> left =
          Either.left(ComplexTestError.medium("E404", "Not found"));

      eitherKindHelper(right).test();
      eitherKindHelper(left).test();

      // Verify non-null values are preserved
      assertThatEither(right).hasRightNonNull();
      assertThatEither(left).hasLeftNonNull();
    }

    @Test
    @DisplayName("Null values in Either are preserved")
    void testNullValuesPreserved() {
      Either<ComplexTestError, String> rightNull = Either.right(null);
      Either<ComplexTestError, String> leftNull = Either.left(null);

      eitherKindHelper(rightNull).test();
      eitherKindHelper(leftNull).test();
    }

    @Test
    @DisplayName("Complex error types work correctly")
    void testComplexErrorTypes() {
      ComplexTestError complexError =
          new ComplexTestError("COMPLEX_001", 8, "Complex error occurred");

      Either<ComplexTestError, String> complexEither = Either.left(complexError);

      eitherKindHelper(complexEither).test();
      assertThatEither(complexEither).hasLeftNonNull();
    }

    @Test
    @DisplayName("Type safety across different generic parameters")
    void testTypeSafetyAcrossDifferentGenerics() {
      Either<String, Integer> stringLeftEither = Either.left("StringError");
      Either<ComplexTestError, Integer> complexLeftEither =
          Either.left(ComplexTestError.low("E1", "Error"));

      eitherKindHelper(stringLeftEither).test();
      eitherKindHelper(complexLeftEither).test();

      assertThatEither(stringLeftEither).hasLeftNonNull();
      assertThatEither(complexLeftEither).hasLeftNonNull();
    }

    @Test
    @DisplayName("Complex Right values with nested generics")
    void testComplexRightValues() {
      Either<ComplexTestError, List<String>> complexRight = Either.right(List.of("a", "b", "c"));
      Either<ComplexTestError, List<String>> complexLeft =
          Either.left(ComplexTestError.high("ComplexError", "Fatal"));

      eitherKindHelper(complexRight).test();
      eitherKindHelper(complexLeft).test();

      assertThat(complexRight.getRight()).containsExactly("a", "b", "c");
      assertThat(complexLeft.getLeft().code()).isEqualTo("ComplexError");

      assertThatEither(complexRight).hasRightNonNull();
      assertThatEither(complexLeft).hasLeftNonNull();
    }

    @Test
    @DisplayName("Operations preserve non-null values")
    void testOperationsPreserveNonNull() {
      Either<ComplexTestError, String> original = Either.right("test");
      assertThatEither(original).hasRightNonNull();

      // Verify widen/narrow preserves non-null
      var widened = EITHER.widen(original);
      var narrowed = EITHER.narrow(widened);
      assertThatEither(narrowed).hasRightNonNull();
      assertThat(narrowed).isEqualTo(original);
    }
  }

  @Nested
  @DisplayName("Performance and Memory Tests")
  class PerformanceTests {
    @Test
    @DisplayName("Holder creates minimal overhead")
    void testMinimalOverhead() {
      Either<ComplexTestError, String> original = Either.right("test");

      eitherKindHelper(original).skipPerformance().test();
    }

    @Test
    @DisplayName("Multiple operations are idempotent")
    void testIdempotentOperations() {
      Either<ComplexTestError, String> original = Either.right("idempotent");

      eitherKindHelper(original)
          .skipRoundTrip()
          .skipValidations()
          .skipInvalidType()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Performance characteristics test")
    void testPerformanceCharacteristics() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Either<ComplexTestError, String> testInstance = Either.right("performance_test");

        eitherKindHelper(testInstance).withPerformanceTests().test();
      }
    }

    @Test
    @DisplayName("Memory efficiency test")
    void testMemoryEfficiency() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Either<ComplexTestError, String> testInstance = Either.right("memory_test");

        eitherKindHelper(testInstance).withPerformanceTests().test();
      }
    }
  }

  @Nested
  @DisplayName("Edge Cases and Corner Cases")
  class EdgeCasesTests {
    @Test
    @DisplayName("All combinations of null values")
    void testAllNullValueCombinations() {
      List<Either<ComplexTestError, String>> nullInstances =
          List.of(
              Either.right(null),
              Either.left(null),
              Either.left(new ComplexTestError(null, 0, null)),
              Either.right(""));

      for (Either<ComplexTestError, String> instance : nullInstances) {
        eitherKindHelper(instance).test();
      }
    }
  }

  @Nested
  @DisplayName("Advanced Testing Scenarios")
  class AdvancedTestingScenarios {
    @Test
    @DisplayName("Concurrent access test")
    void testConcurrentAccess() {
      if (Boolean.parseBoolean(System.getProperty("test.concurrency", "false"))) {
        Either<ComplexTestError, String> testInstance = Either.right("concurrent_test");

        eitherKindHelper(testInstance).withConcurrencyTests().test();
      }
    }

    @Test
    @DisplayName("Implementation standards validation")
    void testImplementationStandards() {
      KindHelperTestPattern.validateImplementationStandards(Either.class, EitherKindHelper.class);
    }

    @Test
    @DisplayName("Quick test for fast test suites")
    void testQuickValidation() {
      Either<ComplexTestError, String> testInstance = Either.right("quick_test");

      eitherKindHelper(testInstance).test();
    }

    @Test
    @DisplayName("Stress test with complex scenarios")
    void testComplexStressScenarios() {
      List<Either<ComplexTestError, Object>> complexInstances =
          List.of(
              Either.right("simple_string"),
              Either.right(42),
              Either.right(List.of(1, 2, 3)),
              Either.right(java.util.Map.of("key", "value")),
              Either.right(ComplexTestError.medium("nested_error", "Nested")),
              Either.left(ComplexTestError.low("left_error", "Error")),
              Either.right(null),
              Either.left(null));

      for (Either<ComplexTestError, Object> instance : complexInstances) {
        eitherKindHelper(instance).test();
      }
    }
  }

  @Nested
  @DisplayName("Comprehensive Coverage Tests")
  class ComprehensiveCoverageTests {
    @Test
    @DisplayName("All Either types and states")
    void testAllEitherTypesAndStates() {
      List<Either<ComplexTestError, String>> allStates =
          List.of(
              Either.right("success"),
              Either.right(""),
              Either.right(null),
              Either.left(ComplexTestError.medium("error", "Error")),
              Either.left(ComplexTestError.low("", "")),
              Either.left(new ComplexTestError(null, 0, null)),
              Either.left(null));

      for (Either<ComplexTestError, String> state : allStates) {
        eitherKindHelper(state).test();
      }
    }

    @Test
    @DisplayName("Full lifecycle test")
    void testFullLifecycle() {
      Either<ComplexTestError, String> original = Either.right("lifecycle_test");

      eitherKindHelper(original).test();
      assertThatEither(original).hasRightNonNull();

      Either<ComplexTestError, String> leftOriginal =
          Either.left(ComplexTestError.high("lifecycle_error", "Fatal"));

      eitherKindHelper(leftOriginal).test();
      assertThatEither(leftOriginal).hasLeftNonNull();
    }
  }
}
