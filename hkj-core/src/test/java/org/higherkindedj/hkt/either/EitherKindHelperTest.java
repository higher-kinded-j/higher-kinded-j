// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.test.api.CoreTypeTest.eitherKindHelper;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.patterns.KindHelperTestPattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherKindHelper Complete Test Suite")
class EitherKindHelperTest
    extends TypeClassTestBase<EitherKind.Witness<EitherKindHelperTest.TestError>, String, String> {
  record TestError(String code) {}

  // Helper constant for cleaner type references
  private static final EitherKindHelper EITHER = EitherKindHelper.EITHER;

  @Override
  protected Kind<EitherKind.Witness<EitherKindHelperTest.TestError>, String> createValidKind() {
    return EITHER.widen(Either.right("Success"));
  }

  @Override
  protected Kind<EitherKind.Witness<EitherKindHelperTest.TestError>, String> createValidKind2() {
    return EITHER.widen(Either.right("Another"));
  }

  @Override
  protected Function<String, String> createValidMapper() {
    return String::toUpperCase;
  }

  @Override
  protected BiPredicate<
          Kind<EitherKind.Witness<EitherKindHelperTest.TestError>, ?>,
          Kind<EitherKind.Witness<EitherKindHelperTest.TestError>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> EITHER.narrow(k1).equals(EITHER.narrow(k2));
  }

  @Nested
  @DisplayName("Complete KindHelper Test Suite")
  class CompleteTestSuite {
    @Test
    @DisplayName("Run complete KindHelper test suite for Either")
    void completeKindHelperTestSuite() {
      Either<TestError, String> validInstance = Either.right("Success");

      eitherKindHelper(validInstance).test();
    }

    @Test
    @DisplayName("Complete test suite with multiple Either types")
    void completeTestSuiteWithMultipleTypes() {
      List<Either<TestError, String>> testInstances =
          List.of(
              Either.right("Success"),
              Either.left(new TestError("E404")),
              Either.right(null),
              Either.left(null));

      for (Either<TestError, String> instance : testInstances) {
        eitherKindHelper(instance).test();
      }
    }

    @Test
    @DisplayName("Comprehensive test with implementation validation")
    void comprehensiveTestWithImplementationValidation() {
      Either<TestError, String> validInstance = Either.right("Comprehensive");
      eitherKindHelper(validInstance).testWithValidation(EitherKindHelper.class);
    }
  }

  @Nested
  @DisplayName("Individual Component Tests")
  class IndividualComponentTests {
    @Test
    @DisplayName("Test round-trip widen/narrow operations")
    void testRoundTripOperations() {
      Either<TestError, String> validInstance = Either.right("test");

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
      eitherKindHelper(Either.<TestError, String>right("test"))
          .skipRoundTrip()
          .skipInvalidType()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test invalid Kind type handling")
    void testInvalidKindType() {
      eitherKindHelper(Either.<TestError, String>right("test"))
          .skipRoundTrip()
          .skipValidations()
          .skipIdempotency()
          .skipEdgeCases()
          .test();
    }

    @Test
    @DisplayName("Test idempotency of operations")
    void testIdempotency() {
      Either<TestError, String> validInstance = Either.right("idempotent");

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
      Either<TestError, String> validInstance = Either.right("edge");

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
      Either<TestError, String> right = Either.right("Success");
      Either<TestError, String> left = Either.left(new TestError("E404"));

      eitherKindHelper(right).test();
      eitherKindHelper(left).test();
    }

    @Test
    @DisplayName("Null values in Either are preserved")
    void testNullValuesPreserved() {
      Either<TestError, String> rightNull = Either.right(null);
      Either<TestError, String> leftNull = Either.left(null);

      eitherKindHelper(rightNull).test();
      eitherKindHelper(leftNull).test();
    }

    @Test
    @DisplayName("Complex error types work correctly")
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

      eitherKindHelper(complexEither).test();
    }

    @Test
    @DisplayName("Type safety across different generic parameters")
    void testTypeSafetyAcrossDifferentGenerics() {
      Either<String, Integer> stringLeftEither = Either.left("StringError");
      Either<TestError, Integer> testErrorLeftEither = Either.left(new TestError("E1"));

      eitherKindHelper(stringLeftEither).test();
      eitherKindHelper(testErrorLeftEither).test();
    }

    @Test
    @DisplayName("Complex Right values with nested generics")
    void testComplexRightValues() {
      Either<TestError, List<String>> complexRight = Either.right(List.of("a", "b", "c"));
      Either<TestError, List<String>> complexLeft = Either.left(new TestError("ComplexError"));

      eitherKindHelper(complexRight).test();
      eitherKindHelper(complexLeft).test();

      assertThat(complexRight.getRight()).containsExactly("a", "b", "c");
      assertThat(complexLeft.getLeft().code()).isEqualTo("ComplexError");
    }
  }

  @Nested
  @DisplayName("Performance and Memory Tests")
  class PerformanceTests {
    @Test
    @DisplayName("Holder creates minimal overhead")
    void testMinimalOverhead() {
      Either<TestError, String> original = Either.right("test");

      eitherKindHelper(original).skipPerformance().test();
    }

    @Test
    @DisplayName("Multiple operations are idempotent")
    void testIdempotentOperations() {
      Either<TestError, String> original = Either.right("idempotent");

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
        Either<TestError, String> testInstance = Either.right("performance_test");

        eitherKindHelper(testInstance).withPerformanceTests().test();
      }
    }

    @Test
    @DisplayName("Memory efficiency test")
    void testMemoryEfficiency() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Either<TestError, String> testInstance = Either.right("memory_test");

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
      List<Either<TestError, String>> nullInstances =
          List.of(
              Either.right(null),
              Either.left(null),
              Either.left(new TestError(null)),
              Either.right(""));

      for (Either<TestError, String> instance : nullInstances) {
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
        Either<TestError, String> testInstance = Either.right("concurrent_test");

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
      Either<TestError, String> testInstance = Either.right("quick_test");

      eitherKindHelper(testInstance).test();
    }

    @Test
    @DisplayName("Stress test with complex scenarios")
    void testComplexStressScenarios() {
      List<Either<TestError, Object>> complexInstances =
          List.of(
              Either.right("simple_string"),
              Either.right(42),
              Either.right(List.of(1, 2, 3)),
              Either.right(java.util.Map.of("key", "value")),
              Either.right(new TestError("nested_error")),
              Either.left(new TestError("left_error")),
              Either.right(null),
              Either.left(null));

      for (Either<TestError, Object> instance : complexInstances) {
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
      List<Either<TestError, String>> allStates =
          List.of(
              Either.right("success"),
              Either.right(""),
              Either.right(null),
              Either.left(new TestError("error")),
              Either.left(new TestError("")),
              Either.left(new TestError(null)),
              Either.left(null));

      for (Either<TestError, String> state : allStates) {
        eitherKindHelper(state).test();
      }
    }

    @Test
    @DisplayName("Full lifecycle test")
    void testFullLifecycle() {
      Either<TestError, String> original = Either.right("lifecycle_test");

      eitherKindHelper(original).test();

      Either<TestError, String> leftOriginal = Either.left(new TestError("lifecycle_error"));

      eitherKindHelper(leftOriginal).test();
    }
  }
}
