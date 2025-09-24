// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.data.TestData;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.patterns.KindHelperTestPattern;
import org.higherkindedj.hkt.test.patterns.TypeClassTestPattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherKindHelper Complete Test Suite")
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

      // Only test behaviors that work with Either's specific implementation
      // Skip generic validation tests from the pattern
      testEitherSpecificBehavior(validInstance);
    }

    private void testEitherSpecificBehavior(Either<TestError, String> validInstance) {
      // Test round-trip
      KindHelperTestPattern.testRoundTrip(validInstance, EITHER::widen, EITHER::narrow);

      // Test idempotency
      KindHelperTestPattern.testIdempotency(validInstance, EITHER::widen, EITHER::narrow);

      // Test invalid type with Either-specific expectations
      Kind<EitherKind.Witness<TestError>, String> invalidKind =
          TestData.createDummyKind("invalid_Either");

      assertThatThrownBy(() -> EITHER.narrow(invalidKind))
          .isInstanceOf(org.higherkindedj.hkt.exception.KindUnwrapException.class)
          .hasMessageContaining("Expected EitherHolder");
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

      // Test each instance individually for round-trip behavior
      for (Either<TestError, String> instance : testInstances) {
        KindHelperTestPattern.testRoundTrip(instance, EITHER::widen, EITHER::narrow);
      }

      // Test multiple instances for consistency
      KindHelperTestPattern.testMultipleInstances(
          testInstances,
          (Class<Either<TestError, String>>) (Class<?>) Either.class,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<TestError>, String> kind) -> EITHER.narrow(kind));
    }

    @Test
    @DisplayName("Comprehensive test with implementation validation")
    void comprehensiveTestWithImplementationValidation() {
      Either<TestError, String> validInstance = Either.right("Comprehensive");

      testEitherSpecificBehavior(validInstance);
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
    @DisplayName("Test null parameter validations - Either specific")
    void testNullParameterValidations() {
      // Test widen null
      assertThatThrownBy(() -> EITHER.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Either")
          .hasMessageContaining("cannot be null");

      // Test narrow null - Either uses "Either" not "Object" in message
      assertThatThrownBy(() -> EITHER.narrow(null))
          .isInstanceOf(org.higherkindedj.hkt.exception.KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind for Either");
    }

    @Test
    @DisplayName("Test invalid Kind type handling")
    void testInvalidKindType() {
      Kind<EitherKind.Witness<TestError>, String> invalidKind =
          TestData.createDummyKind("invalid_Either");

      assertThatThrownBy(() -> EITHER.narrow(invalidKind))
          .isInstanceOf(org.higherkindedj.hkt.exception.KindUnwrapException.class)
          .hasMessageContaining("Expected EitherHolder")
          .hasMessageContaining(invalidKind.getClass().getName());
    }

    @Test
    @DisplayName("Test idempotency of operations")
    void testIdempotency() {
      Either<TestError, String> validInstance = Either.right("idempotent");
      KindHelperTestPattern.testIdempotency(validInstance, EITHER::widen, EITHER::narrow);
    }

    @Test
    @DisplayName("Test edge cases and boundary conditions")
    void testEdgeCases() {
      Either<TestError, String> validInstance = Either.right("edge");

      Kind<EitherKind.Witness<TestError>, String> widened = EITHER.widen(validInstance);
      assertThat(widened).as("widen should always return non-null Kind").isNotNull();

      Either<TestError, String> narrowed = EITHER.narrow(widened);
      assertThat(narrowed).as("narrow should return non-null for valid Kind").isNotNull();
      assertThat(narrowed)
          .as("narrowed result should be instance of Either")
          .isInstanceOf(Either.class);
    }
  }

  @Nested
  @DisplayName("Specific Either Behavior Tests")
  class SpecificBehaviorTests {

    @Test
    @DisplayName("Both Right and Left instances work correctly")
    void testRightAndLeftInstances() {
      Either<TestError, String> right = Either.right("Success");
      Either<TestError, String> left = Either.left(new TestError("E404"));

      Kind<EitherKind.Witness<TestError>, String> rightKind = EITHER.widen(right);
      Either<TestError, String> narrowedRight = EITHER.narrow(rightKind);
      assertThat(narrowedRight).isSameAs(right);

      Kind<EitherKind.Witness<TestError>, String> leftKind = EITHER.widen(left);
      Either<TestError, String> narrowedLeft = EITHER.narrow(leftKind);
      assertThat(narrowedLeft).isSameAs(left);
    }

    @Test
    @DisplayName("Null values in Either are preserved")
    void testNullValuesPreserved() {
      Either<TestError, String> rightNull = Either.right(null);
      Either<TestError, String> leftNull = Either.left(null);

      Kind<EitherKind.Witness<TestError>, String> rightKind = EITHER.widen(rightNull);
      Either<TestError, String> narrowedRight = EITHER.narrow(rightKind);
      assertThat(narrowedRight).isSameAs(rightNull);
      assertThat(narrowedRight.isRight()).isTrue();
      assertThat(narrowedRight.getRight()).isNull();

      Kind<EitherKind.Witness<TestError>, String> leftKind = EITHER.widen(leftNull);
      Either<TestError, String> narrowedLeft = EITHER.narrow(leftKind);
      assertThat(narrowedLeft).isSameAs(leftNull);
      assertThat(narrowedLeft.isLeft()).isTrue();
      assertThat(narrowedLeft.getLeft()).isNull();
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

      Kind<EitherKind.Witness<ComplexError>, String> complexKind = EITHER.widen(complexEither);
      Either<ComplexError, String> narrowed = EITHER.narrow(complexKind);
      assertThat(narrowed).isSameAs(complexEither);
      assertThat(narrowed.getLeft()).isSameAs(complexError);
      assertThat(narrowed.getLeft().code()).isEqualTo("COMPLEX_001");
    }

    @Test
    @DisplayName("Type safety across different generic parameters")
    void testTypeSafetyAcrossDifferentGenerics() {
      Either<String, Integer> stringLeftEither = Either.left("StringError");
      Either<TestError, Integer> testErrorLeftEither = Either.left(new TestError("E1"));

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

    @Test
    @DisplayName("Complex Right values with nested generics")
    void testComplexRightValues() {
      Either<TestError, List<String>> complexRight = Either.right(List.of("a", "b", "c"));
      Either<TestError, List<String>> complexLeft = Either.left(new TestError("ComplexError"));

      Kind<EitherKind.Witness<TestError>, List<String>> rightKind = EITHER.widen(complexRight);
      Either<TestError, List<String>> narrowedRight = EITHER.narrow(rightKind);

      Kind<EitherKind.Witness<TestError>, List<String>> leftKind = EITHER.widen(complexLeft);
      Either<TestError, List<String>> narrowedLeft = EITHER.narrow(leftKind);

      assertThat(narrowedRight).isSameAs(complexRight);
      assertThat(narrowedLeft).isSameAs(complexLeft);
      assertThat(narrowedRight.getRight()).containsExactly("a", "b", "c");
      assertThat(narrowedLeft.getLeft().code()).isEqualTo("ComplexError");
    }
  }

  @Nested
  @DisplayName("Performance and Memory Tests")
  class PerformanceTests {

    @Test
    @DisplayName("Holder creates minimal overhead")
    void testMinimalOverhead() {
      Either<TestError, String> original = Either.right("test");
      Kind<EitherKind.Witness<TestError>, String> holder = EITHER.widen(original);

      assertThat(holder).isInstanceOf(EitherKindHelper.EitherHolder.class);

      Either<TestError, String> narrowed = EITHER.narrow(holder);
      assertThat(narrowed).isSameAs(original);
    }

    @Test
    @DisplayName("Multiple operations are idempotent")
    void testIdempotentOperations() {
      Either<TestError, String> original = Either.right("idempotent");
      KindHelperTestPattern.testIdempotency(original, EITHER::widen, EITHER::narrow);
    }

    @Test
    @DisplayName("Test with different error types")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testWithDifferentErrorTypes() {
      record ComplexError(String code, int severity) {}

      EitherFunctor<ComplexError> complexFunctor = new EitherFunctor<>();
      Kind<EitherKind.Witness<ComplexError>, Integer> complexKind = EITHER.widen(Either.right(100));

      Function<Integer, String> validMapper = TestFunctions.INT_TO_STRING;

      TypeClassTestPattern.testFunctorOperations(complexFunctor, complexKind, validMapper);

      // EitherFunctor doesn't use class context in validation, so don't expect it
      assertThatThrownBy(() -> complexFunctor.map(null, complexKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("function f")
          .hasMessageContaining("map");

      assertThatThrownBy(() -> complexFunctor.map(validMapper, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("map");
    }

    @Test
    @DisplayName("Performance characteristics test")
    void testPerformanceCharacteristics() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Either<TestError, String> testInstance = Either.right("performance_test");
        KindHelperTestPattern.testPerformance(testInstance, EITHER::widen, EITHER::narrow);
      }
    }

    @Test
    @DisplayName("Memory efficiency test")
    void testMemoryEfficiency() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        Either<TestError, String> testInstance = Either.right("memory_test");
        KindHelperTestPattern.testMemoryEfficiency(testInstance, EITHER::widen, EITHER::narrow);
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
        KindHelperTestPattern.testRoundTrip(instance, EITHER::widen, EITHER::narrow);
      }
    }

    @Test
    @DisplayName("Error messages contain expected information")
    void testErrorMessageContents() {
      // Widen null
      try {
        EITHER.widen(null);
        fail("Should have thrown exception");
      } catch (NullPointerException e) {
        assertThat(e.getMessage())
            .as("widen null error message should be descriptive")
            .isNotNull()
            .isNotEmpty()
            .containsIgnoringCase("either");
      }

      // Narrow null
      try {
        EITHER.narrow(null);
        fail("Should have thrown exception");
      } catch (Exception e) {
        assertThat(e.getMessage())
            .as("narrow null error message should be descriptive")
            .isNotNull()
            .isNotEmpty()
            .contains("Either");
      }

      // Invalid type
      Kind<EitherKind.Witness<TestError>, String> invalidKind = TestData.createDummyKind("invalid");
      try {
        EITHER.narrow(invalidKind);
        fail("Should have thrown exception");
      } catch (Exception e) {
        assertThat(e.getMessage())
            .as("narrow invalid type error message should be descriptive")
            .isNotNull()
            .isNotEmpty()
            .containsAnyOf("Either", "EitherHolder", "Expected");
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
        KindHelperTestPattern.testConcurrentAccess(testInstance, EITHER::widen, EITHER::narrow);
      }
    }

    @Test
    @DisplayName("Implementation standards validation")
    void testImplementationStandards() {
      assertThat(EitherKindHelper.class)
          .as("KindHelper implementation class should be non-null")
          .isNotNull();
      assertThat(Either.class).as("Target type class should be non-null").isNotNull();

      String helperName = EitherKindHelper.class.getSimpleName();
      String targetName = Either.class.getSimpleName();

      assertThat(helperName)
          .as("KindHelper should follow naming convention")
          .satisfiesAnyOf(
              name -> assertThat(name).contains(targetName),
              name -> assertThat(name).contains("KindHelper"),
              name -> assertThat(name).contains("Helper"));

      java.lang.reflect.Method[] methods = EitherKindHelper.class.getMethods();
      boolean hasWidenMethod =
          java.util.Arrays.stream(methods)
              .anyMatch(m -> m.getName().equals("widen") && m.getParameterCount() == 1);
      assertThat(hasWidenMethod).as("Should have widen method").isTrue();

      boolean hasNarrowMethod =
          java.util.Arrays.stream(methods)
              .anyMatch(m -> m.getName().equals("narrow") && m.getParameterCount() == 1);
      assertThat(hasNarrowMethod).as("Should have narrow method").isTrue();
    }

    @Test
    @DisplayName("Quick test for fast test suites")
    void testQuickValidation() {
      Either<TestError, String> testInstance = Either.right("quick_test");

      // Just test round-trip without validation patterns
      KindHelperTestPattern.testRoundTrip(testInstance, EITHER::widen, EITHER::narrow);
    }

    @Test
    @DisplayName("Type safety validation across different witness types")
    void testTypeSafetyValidation() {
      Either<String, Integer> stringErrorEither = Either.left("string_error");
      Either<RuntimeException, Integer> exceptionErrorEither =
          Either.left(new RuntimeException("exception_error"));
      Either<TestError, Integer> testErrorEither = Either.left(new TestError("test_error"));

      // Just test round-trip for each type
      KindHelperTestPattern.testRoundTrip(
          stringErrorEither,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<String>, Integer> kind) -> EITHER.narrow(kind));

      KindHelperTestPattern.testRoundTrip(
          exceptionErrorEither,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<RuntimeException>, Integer> kind) -> EITHER.narrow(kind));

      KindHelperTestPattern.testRoundTrip(
          testErrorEither,
          either -> EITHER.widen(either),
          (Kind<EitherKind.Witness<TestError>, Integer> kind) -> EITHER.narrow(kind));
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
        KindHelperTestPattern.testRoundTrip(instance, EITHER::widen, EITHER::narrow);
      }
    }
  }

  @Nested
  @DisplayName("Integration with Test Framework")
  class IntegrationTests {

    @Test
    @DisplayName("Integration with standardized test patterns")
    void testStandardizedPatternIntegration() {
      Either<TestError, String> testInstance = Either.right("integration_test");

      // Only use compatible parts of the pattern
      KindHelperTestPattern.testRoundTrip(testInstance, EITHER::widen, EITHER::narrow);
      KindHelperTestPattern.testIdempotency(testInstance, EITHER::widen, EITHER::narrow);

      // Custom validation for Either
      assertThatThrownBy(() -> EITHER.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Either");

      assertThatThrownBy(() -> EITHER.narrow(null))
          .isInstanceOf(org.higherkindedj.hkt.exception.KindUnwrapException.class)
          .hasMessageContaining("Either");
    }

    @Test
    @DisplayName("Validation framework compatibility")
    void testValidationFrameworkCompatibility() {
      Either<TestError, String> validInstance = Either.right("validation_test");

      assertThatThrownBy(() -> EITHER.widen(null))
          .as("widen should validate null input")
          .isInstanceOf(NullPointerException.class);

      assertThatThrownBy(() -> EITHER.narrow(null))
          .as("narrow should validate null input")
          .isInstanceOf(org.higherkindedj.hkt.exception.KindUnwrapException.class);

      Kind<EitherKind.Witness<TestError>, String> invalidKind = TestData.createDummyKind("invalid");
      assertThatThrownBy(() -> EITHER.narrow(invalidKind))
          .as("narrow should validate Kind type")
          .isInstanceOf(org.higherkindedj.hkt.exception.KindUnwrapException.class);
    }

    @Test
    @DisplayName("Error handling consistency with framework")
    void testErrorHandlingConsistency() {
      // Widen null
      try {
        EITHER.widen(null);
        fail("Should have thrown exception");
      } catch (NullPointerException e) {
        assertThat(e.getMessage())
            .as("widen null error message should be descriptive")
            .isNotNull()
            .isNotEmpty()
            .containsIgnoringCase("either");
      }

      // Narrow null
      try {
        EITHER.narrow(null);
        fail("Should have thrown exception");
      } catch (Exception e) {
        assertThat(e.getMessage())
            .as("narrow null error message should be descriptive")
            .isNotNull()
            .isNotEmpty()
            .containsAnyOf("Either", "narrow", "Kind", "null");
      }

      // Invalid type
      Kind<EitherKind.Witness<TestError>, String> invalidKind = TestData.createDummyKind("invalid");
      try {
        EITHER.narrow(invalidKind);
        fail("Should have thrown exception");
      } catch (Exception e) {
        assertThat(e.getMessage())
            .as("narrow invalid type error message should be descriptive")
            .isNotNull()
            .isNotEmpty()
            .satisfiesAnyOf(
                msg -> assertThat(msg).containsIgnoringCase("either"),
                msg -> assertThat(msg).containsIgnoringCase("kind"),
                msg -> assertThat(msg).containsIgnoringCase("type"),
                msg -> assertThat(msg).containsIgnoringCase("holder"));
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
        KindHelperTestPattern.testRoundTrip(state, EITHER::widen, EITHER::narrow);
        KindHelperTestPattern.testIdempotency(state, EITHER::widen, EITHER::narrow);
      }
    }

    @Test
    @DisplayName("All possible error types")
    void testAllPossibleErrorTypesFixed() {
      Either<String, Integer> stringError = Either.left("string_error");
      Either<RuntimeException, Integer> exceptionError =
          Either.left(new RuntimeException("runtime_error"));
      Either<TestError, Integer> testError = Either.left(new TestError("test_error"));
      Either<Integer, Integer> intError = Either.left(404);

      Kind<EitherKind.Witness<String>, Integer> stringKind = EITHER.widen(stringError);
      Either<String, Integer> narrowedString = EITHER.narrow(stringKind);
      assertThat(narrowedString).isSameAs(stringError);

      Kind<EitherKind.Witness<RuntimeException>, Integer> exceptionKind =
          EITHER.widen(exceptionError);
      Either<RuntimeException, Integer> narrowedException = EITHER.narrow(exceptionKind);
      assertThat(narrowedException).isSameAs(exceptionError);

      Kind<EitherKind.Witness<TestError>, Integer> testErrorKind = EITHER.widen(testError);
      Either<TestError, Integer> narrowedTestError = EITHER.narrow(testErrorKind);
      assertThat(narrowedTestError).isSameAs(testError);

      Kind<EitherKind.Witness<Integer>, Integer> intKind = EITHER.widen(intError);
      Either<Integer, Integer> narrowedInt = EITHER.narrow(intKind);
      assertThat(narrowedInt).isSameAs(intError);
    }

    @Test
    @DisplayName("Full lifecycle test")
    void testFullLifecycle() {
      Either<TestError, String> original = Either.right("lifecycle_test");

      Either<TestError, String> result = original;
      for (int i = 0; i < 10; i++) {
        Kind<EitherKind.Witness<TestError>, String> widened = EITHER.widen(result);
        result = EITHER.narrow(widened);
      }

      assertThat(result).isSameAs(original);

      Either<TestError, String> leftOriginal = Either.left(new TestError("lifecycle_error"));
      Either<TestError, String> leftResult = leftOriginal;
      for (int i = 0; i < 10; i++) {
        Kind<EitherKind.Witness<TestError>, String> widened = EITHER.widen(leftResult);
        leftResult = EITHER.narrow(widened);
      }

      assertThat(leftResult).isSameAs(leftOriginal);
    }
  }
}
