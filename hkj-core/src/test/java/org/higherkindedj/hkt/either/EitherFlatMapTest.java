// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.test.HKTTestAssertions.*;
import static org.higherkindedj.hkt.test.HKTTestHelpers.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Either flatMap() Method - COMPREHENSIVE COVERAGE")
class EitherFlatMapTest {

  private final String leftValue = "Error Message";
  private final Integer rightValue = 123;
  private final Either<String, Integer> leftInstance = Either.left(leftValue);
  private final Either<String, Integer> rightInstance = Either.right(rightValue);
  private final Either<String, Integer> leftNullInstance = Either.left(null);
  private final Either<String, Integer> rightNullInstance = Either.right(null);

  // Standard test functions
  private final Function<Integer, Either<String, String>> mapperRight =
      i -> Either.right("FlatMapped: " + i);
  private final Function<Integer, Either<String, String>> mapperLeft =
      i -> Either.left("FlatMap Error");
  private final Function<Integer, Either<String, String>> mapperNull = i -> null;

  @Nested
  @DisplayName("Basic flatMap Functionality")
  class BasicFlatMapFunctionality {

    @Test
    @DisplayName("flatMap() on Right instances should apply mapper correctly")
    void flatMapOnRightShouldApplyMapper() {
      Either<String, String> successResult = rightInstance.flatMap(mapperRight);
      assertThat(successResult.isRight()).isTrue();
      assertThat(successResult.getRight()).isEqualTo("FlatMapped: " + rightValue);

      Either<String, String> failResult = rightInstance.flatMap(mapperLeft);
      assertThat(failResult.isLeft()).isTrue();
      assertThat(failResult.getLeft()).isEqualTo("FlatMap Error");

      Either<String, String> nullInputResult = rightNullInstance.flatMap(mapperRight);
      assertThat(nullInputResult.isRight()).isTrue();
      assertThat(nullInputResult.getRight()).isEqualTo("FlatMapped: null");
    }

    @Test
    @DisplayName("flatMap() on Left instances should return same instance (type-safe cast)")
    void flatMapOnLeftShouldReturnSameInstanceWithTypeSafeCast() {
      Either<String, String> result = leftInstance.flatMap(mapperRight);
      assertThat(result).isSameAs(leftInstance);
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo(leftValue);

      Either<String, String> nullResult = leftNullInstance.flatMap(mapperRight);
      assertThat(nullResult).isSameAs(leftNullInstance);
      assertThat(nullResult.isLeft()).isTrue();
      assertThat(nullResult.getLeft()).isNull();

      // Test that flatMap on Left doesn't execute the mapper at all
      AtomicBoolean mapperCalled = new AtomicBoolean(false);
      Function<Integer, Either<String, String>> trackedMapper =
          i -> {
            mapperCalled.set(true);
            return Either.right("Should not be called");
          };

      leftInstance.flatMap(trackedMapper);
      assertThat(mapperCalled.get()).isFalse();
    }

    @Test
    @DisplayName("flatMap() default implementation behavior for interface")
    void flatMapDefaultImplementationBehavior() {
      // The default implementation in Either interface should behave correctly
      Either<String, Integer> testLeft = Either.left("test error");
      Either<String, Integer> testRight = Either.right(42);

      // For Left, default implementation returns cast self
      Either<String, String> leftResult = testLeft.flatMap(i -> Either.right("mapped: " + i));
      assertThat(leftResult).isSameAs(testLeft);
      assertThat(leftResult.isLeft()).isTrue();
      assertThat(leftResult.getLeft()).isEqualTo("test error");

      // For Right, it delegates to Right.flatMap which overrides the default
      Either<String, String> rightResult = testRight.flatMap(i -> Either.right("mapped: " + i));
      assertThat(rightResult.isRight()).isTrue();
      assertThat(rightResult.getRight()).isEqualTo("mapped: 42");
    }
  }

  @Nested
  @DisplayName("Parameter Validation")
  class ParameterValidation {

    @Test
    @DisplayName("flatMap() should validate null mapper parameter")
    void flatMapShouldValidateNullMapper() {
      ValidationTestBuilder.create()
          .assertNullFunction(() -> rightInstance.flatMap(null), "mapper")
          .assertNullFunction(() -> leftInstance.flatMap(null), "mapper")
          .execute();
    }

    @Test
    @DisplayName("flatMap() should validate mapper doesn't return null")
    void flatMapShouldValidateMapperDoesNotReturnNull() {
      // Test null return value validation - specifically in Right.flatMap
      assertThatThrownBy(() -> rightInstance.flatMap(mapperNull))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining(
              "flatMap mapper returned a null Either instance, which is not allowed.");

      // Left instances should not execute the mapper, so no exception even with null-returning
      // mapper
      Either<String, String> leftResult = leftInstance.flatMap(mapperNull);
      assertThat(leftResult).isSameAs(leftInstance);
    }
  }

  @Nested
  @DisplayName("Exception Handling")
  class ExceptionHandling {

    @Test
    @DisplayName("flatMap() should propagate exceptions from mapper correctly")
    void flatMapShouldPropagateExceptions() {
      RuntimeException testException = createTestException("flatMap test");
      Function<Integer, Either<String, String>> throwingMapper =
          CommonTestFunctions.throwingFunction(testException);

      assertThatThrownBy(() -> rightInstance.flatMap(throwingMapper)).isSameAs(testException);

      // Test with null input to mapper that throws
      Function<Integer, Either<String, String>> nullSensitiveMapper =
          i -> {
            if (i == null) throw new IllegalArgumentException("null input");
            return Either.right("processed: " + i);
          };

      assertThatThrownBy(() -> rightNullInstance.flatMap(nullSensitiveMapper))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("null input");

      // Test with mapper that handles null properly
      Function<Integer, Either<String, String>> nullHandlingMapper =
          i -> {
            if (i == null) return Either.right("handled null");
            return Either.right("value: " + i);
          };

      Either<String, String> handledResult = rightNullInstance.flatMap(nullHandlingMapper);
      assertThat(handledResult.getRight()).isEqualTo("handled null");

      // Left instances should not call throwing mappers
      Either<String, String> leftWithThrowingMapper = leftInstance.flatMap(throwingMapper);
      assertThat(leftWithThrowingMapper).isSameAs(leftInstance);
    }
  }

  @Nested
  @DisplayName("Complex Chaining Scenarios")
  class ComplexChainingScenarios {

    @Test
    @DisplayName("flatMap() should handle complex chaining scenarios correctly")
    void flatMapShouldHandleComplexChaining() {
      Either<String, Integer> start = Either.right(10);

      Either<String, String> chainedResult =
          start
              .flatMap(i -> Either.right("step1_" + i))
              .flatMap(s -> Either.right(s + "_step2"))
              .flatMap(s -> Either.right(s + "_final"));

      assertThat(chainedResult.getRight()).isEqualTo("step1_10_step2_final");

      // Test chaining with early failure
      Either<String, String> failedChain =
          start
              .flatMap(i -> Either.right("step1_" + i))
              .flatMap(s -> Either.left("FAIL"))
              .flatMap(s -> Either.right(s + "_never_reached"));

      assertThat(failedChain.isLeft()).isTrue();
      assertThat(failedChain.getLeft()).isEqualTo("FAIL");

      // Test that subsequent operations are not executed after failure
      AtomicBoolean finalStepExecuted = new AtomicBoolean(false);
      Function<String, Either<String, String>> finalStep =
          s -> {
            finalStepExecuted.set(true);
            return Either.right(s + "_final");
          };

      Either<String, String> step1 = start.flatMap(i -> Either.right("step1_" + i));
      Either<String, String> step2 = step1.flatMap(s -> Either.left("EARLY_FAIL"));
      Either<String, String> chainResult = step2.flatMap(finalStep);

      assertThat(finalStepExecuted.get()).isFalse();
      assertThat(chainResult.isLeft()).isTrue();
    }

    @Test
    @DisplayName("flatMap() memory efficiency with large chains")
    void flatMapMemoryEfficiencyWithLargeChains() {
      Either<String, Integer> start = Either.right(1);

      // Build a chain of 100 flatMap operations
      Either<String, Integer> result = start;
      for (int i = 0; i < 100; i++) {
        final int increment = i;
        result = result.flatMap(x -> Either.right(x + increment));
      }

      // Sum should be 1 + 0 + 1 + 2 + ... + 99 = 1 + 4950 = 4951
      int expectedSum = 1 + (99 * 100) / 2;
      assertThat(result.getRight()).isEqualTo(expectedSum);

      // Test with early failure - should stop at failure point
      Either<String, Integer> failingChain = Either.right(1);
      int failurePoint = 25;
      for (int i = 0; i < 50; i++) {
        final int index = i;
        failingChain =
            failingChain.flatMap(
                x -> {
                  if (index == failurePoint) {
                    return Either.left("Failed at step " + index);
                  }
                  return Either.right(x + index);
                });
      }

      assertThat(failingChain.isLeft()).isTrue();
      assertThat(failingChain.getLeft()).isEqualTo("Failed at step " + failurePoint);
    }
  }

  @Nested
  @DisplayName("Type System Coverage")
  class TypeSystemCoverage {

    @Test
    @DisplayName("flatMap() with same Left types only")
    void flatMapWithSameLeftTypes() {
      Either<String, Integer> numberEither = Either.right(42);

      // This test demonstrates that flatMap requires the Left type to match
      // We can't change the Left type in flatMap - it must remain the same
      Function<Integer, Either<String, String>> mapperSameLeftType =
          i -> i > 50 ? Either.right("Big number: " + i) : Either.left("Number too small: " + i);

      Either<String, String> result = numberEither.flatMap(mapperSameLeftType);
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("Number too small: 42");

      // Test with value that produces Right
      Either<String, Integer> bigNumberEither = Either.right(100);
      Either<String, String> bigResult = bigNumberEither.flatMap(mapperSameLeftType);
      assertThat(bigResult.isRight()).isTrue();
      assertThat(bigResult.getRight()).isEqualTo("Big number: 100");
    }

    @Test
    @DisplayName("flatMap() with wildcard types and covariance")
    void flatMapWithWildcardTypesAndCovariance() {
      Either<String, Integer> numberEither = Either.right(5);

      // Test covariance - mapper returns Either<String, ? extends String>
      Function<Integer, Either<String, String>> stringMapper = i -> Either.right("Number: " + i);

      Either<String, String> stringResult = numberEither.flatMap(stringMapper);
      assertThat(stringResult.getRight()).isEqualTo("Number: 5");

      // Test with custom class that demonstrates covariance
      class CustomValue {
        private final String value;

        public CustomValue(String value) {
          this.value = value;
        }

        @Override
        public String toString() {
          return "Custom(" + value + ")";
        }

        @Override
        public boolean equals(Object obj) {
          return obj instanceof CustomValue && ((CustomValue) obj).value.equals(this.value);
        }

        @Override
        public int hashCode() {
          return value.hashCode();
        }
      }

      Function<Integer, Either<String, CustomValue>> customValueMapper =
          i -> Either.right(new CustomValue("value" + i));

      Either<String, CustomValue> customResult = numberEither.flatMap(customValueMapper);
      assertThat(customResult.getRight().toString()).isEqualTo("Custom(value5)");
    }

    @Test
    @DisplayName("flatMap() type inference edge cases")
    void flatMapTypeInferenceEdgeCases() {
      // Test with nested Either types
      Either<String, Integer> outer = Either.right(10);

      Function<Integer, Either<String, Either<String, String>>> nestedMapper =
          i -> Either.right(Either.right("nested: " + i));

      Either<String, Either<String, String>> nestedResult = outer.flatMap(nestedMapper);
      assertThat(nestedResult.isRight()).isTrue();
      assertThat(nestedResult.getRight().isRight()).isTrue();
      assertThat(nestedResult.getRight().getRight()).isEqualTo("nested: 10");

      // Test flattening nested Eithers
      Function<Integer, Either<String, String>> flattenMapper =
          i -> {
            Either<String, Either<String, String>> nested =
                Either.right(Either.right("flattened: " + i));
            return nested.flatMap(innerEither -> innerEither);
          };

      Either<String, String> flattenedResult = outer.flatMap(flattenMapper);
      assertThat(flattenedResult.getRight()).isEqualTo("flattened: 10");
    }
  }

  @Nested
  @DisplayName("Null Value Handling")
  class NullValueHandling {

    @Test
    @DisplayName("flatMap() with null values in various positions")
    void flatMapWithNullValuesInVariousPositions() {
      // Right with null value
      Either<String, Integer> rightNull = Either.right(null);
      Either<String, String> nullProcessed =
          rightNull.flatMap(i -> Either.right(i == null ? "processed null" : "processed: " + i));
      assertThat(nullProcessed.getRight()).isEqualTo("processed null");

      // Left with null value
      Either<String, Integer> leftNull = Either.left(null);
      Either<String, String> leftNullResult =
          leftNull.flatMap(i -> Either.right("should not execute"));
      assertThat(leftNullResult).isSameAs(leftNull);
      assertThat(leftNullResult.getLeft()).isNull();

      // Mapper returns Right with null value
      Either<String, Integer> number = Either.right(42);
      Either<String, String> nullRightResult = number.flatMap(i -> Either.right(null));
      assertThat(nullRightResult.isRight()).isTrue();
      assertThat(nullRightResult.getRight()).isNull();

      // Mapper returns Left with null value
      Either<String, String> nullLeftResult = number.flatMap(i -> Either.left(null));
      assertThat(nullLeftResult.isLeft()).isTrue();
      assertThat(nullLeftResult.getLeft()).isNull();
    }
  }

  @Nested
  @DisplayName("Different Result Types")
  class DifferentResultTypes {

    @Test
    @DisplayName("flatMap() comprehensive testing with different result types")
    void flatMapComprehensiveTesting() {
      Either<String, Integer> numberEither = Either.right(5);

      // flatMap to different types
      Either<String, java.util.List<Integer>> listResult =
          numberEither.flatMap(
              n ->
                  Either.right(
                      java.util.stream.IntStream.range(0, n)
                          .boxed()
                          .collect(java.util.stream.Collectors.toList())));
      assertThat(listResult.getRight()).containsExactly(0, 1, 2, 3, 4);

      // flatMap with conditional logic
      Either<String, String> conditionalResult =
          numberEither.flatMap(
              n -> {
                if (n % 2 == 0) {
                  return Either.right("even: " + n);
                } else {
                  return Either.left("odd number not allowed");
                }
              });
      assertThat(conditionalResult.isLeft()).isTrue();
      assertThat(conditionalResult.getLeft()).isEqualTo("odd number not allowed");
    }
  }

  @Nested
  @DisplayName("Integration with Other Methods")
  class IntegrationWithOtherMethods {

    @Test
    @DisplayName("flatMap() integration with other Either methods")
    void flatMapIntegrationWithOtherMethods() {
      Either<String, Integer> start = Either.right(10);

      // Combine flatMap with map and fold
      String result =
          start
              .flatMap(i -> Either.right("step: " + i))
              .map(s -> s.toUpperCase())
              .fold(error -> "Error: " + error, success -> "Success: " + success);

      assertThat(result).isEqualTo("Success: STEP: 10");

      // Test with side effects - can't chain void methods
      AtomicBoolean leftActionCalled = new AtomicBoolean(false);
      AtomicBoolean rightActionCalled = new AtomicBoolean(false);

      Either<String, String> sideEffectTest = start.flatMap(i -> Either.right("flatmapped: " + i));
      sideEffectTest.ifLeft(left -> leftActionCalled.set(true));
      sideEffectTest.ifRight(right -> rightActionCalled.set(true));

      assertThat(leftActionCalled.get()).isFalse();
      assertThat(rightActionCalled.get()).isTrue();

      // Test with error flow
      AtomicBoolean errorActionCalled = new AtomicBoolean(false);

      Either<String, String> errorFlow = start.flatMap(i -> Either.left("error in flatMap"));
      errorFlow.ifLeft(left -> errorActionCalled.set(true));

      assertThat(errorActionCalled.get()).isTrue();
    }
  }

  @Nested
  @DisplayName("Edge Cases and Advanced Scenarios")
  class EdgeCasesAndAdvancedScenarios {

    @Test
    @DisplayName("flatMap() should handle deeply nested operations")
    void flatMapShouldHandleDeeplyNestedOperations() {
      Either<String, Integer> start = Either.right(1);

      // Build a deeply nested chain of operations
      Either<String, Integer> result = start;
      for (int i = 0; i < 100; i++) {
        final int increment = i;
        result = result.flatMap(x -> Either.right(x + increment));
      }

      // Sum should be 1 + 0 + 1 + 2 + ... + 99 = 1 + 4950 = 4951
      int expectedSum = 1 + (99 * 100) / 2;
      assertThat(result.getRight()).isEqualTo(expectedSum);

      // Test with early failure
      Either<String, Integer> failingChain = Either.right(1);
      for (int i = 0; i < 50; i++) {
        final int index = i;
        failingChain =
            failingChain.flatMap(
                x -> {
                  if (index == 25) {
                    return Either.left("Failed at step " + index);
                  }
                  return Either.right(x + index);
                });
      }

      assertThat(failingChain.isLeft()).isTrue();
      assertThat(failingChain.getLeft()).isEqualTo("Failed at step 25");
    }

    @Test
    @DisplayName("flatMap() should handle complex data structures")
    void flatMapShouldHandleComplexDataStructures() {
      // Test with nested collections
      Either<String, java.util.Map<String, java.util.List<Integer>>> complexEither =
          Either.right(
              java.util.Map.of(
                  "numbers", java.util.List.of(1, 2, 3, 4, 5),
                  "evens", java.util.List.of(2, 4, 6, 8)));

      Either<String, Integer> sumResult =
          complexEither.flatMap(
              map ->
                  Either.right(
                      map.values().stream()
                          .flatMap(java.util.Collection::stream)
                          .mapToInt(Integer::intValue)
                          .sum()));

      assertThat(sumResult.getRight()).isEqualTo(35); // 1+2+3+4+5+2+4+6+8 = 35

      // Test with nested Either operations
      Either<String, Either<String, Integer>> nestedEither = Either.right(Either.right(100));
      Either<String, Integer> flattened =
          nestedEither.flatMap(
              inner ->
                  inner.fold(err -> Either.left("Inner error: " + err), val -> Either.right(val)));

      assertThat(flattened.getRight()).isEqualTo(100);
    }

    @Test
    @DisplayName("flatMap() should maintain type safety across complex transformations")
    void flatMapShouldMaintainTypeSafetyAcrossTransformations() {
      // Start with a complex type
      record Person(String name, int age, java.util.List<String> hobbies) {}

      Person person = new Person("Alice", 30, java.util.List.of("reading", "hiking"));
      Either<RuntimeException, Person> personEither = Either.right(person);

      // Chain multiple transformations maintaining type safety
      Either<RuntimeException, String> summary =
          personEither
              .map(
                  p ->
                      new StringBuilder()
                          .append(p.name())
                          .append(" is ")
                          .append(p.age())
                          .append(" years old")
                          .toString())
              .flatMap(s -> Either.right(s + " and has " + person.hobbies().size() + " hobbies"))
              .map(s -> s.toUpperCase());

      assertThat(summary.getRight()).isEqualTo("ALICE IS 30 YEARS OLD AND HAS 2 HOBBIES");

      // Test error propagation through type transformations
      Either<RuntimeException, Person> errorEither =
          Either.left(new RuntimeException("Person not found"));

      Either<RuntimeException, String> errorResult =
          errorEither
              .map(Person::name)
              .flatMap(name -> Either.right("Hello " + name))
              .map(String::toUpperCase);

      assertThat(errorResult.isLeft()).isTrue();
      assertThat(errorResult.getLeft().getMessage()).isEqualTo("Person not found");
    }
  }

  @Nested
  @DisplayName("Integration with Testing Framework")
  class IntegrationWithTestingFramework {

    @Test
    @DisplayName("flatMap() works with common test functions")
    void flatMapWithCommonTestFunctions() {
      Either<String, Integer> numberEither = Either.right(100);

      // Test with common test functions from HKTTestHelpers
      Either<String, String> result =
          numberEither.flatMap(i -> Either.right(CommonTestFunctions.INT_TO_STRING.apply(i)));
      assertThat(result.getRight()).isEqualTo("100");

      Either<String, String> suffixed =
          result.flatMap(s -> Either.right(CommonTestFunctions.APPEND_SUFFIX.apply(s)));
      assertThat(suffixed.getRight()).isEqualTo("100_test");
    }

    @Test
    @DisplayName("flatMap() works with exception testing utilities")
    void flatMapWithExceptionTestingUtilities() {
      Either<String, Integer> numberEither = Either.right(50);
      Either<String, Integer> leftEither = Either.left("error");

      RuntimeException testException = createTestException("flatMap integration test");
      Function<Integer, Either<String, String>> throwingFlatMapper =
          CommonTestFunctions.throwingFunction(testException);

      // Test exception propagation on Right
      assertThatThrownBy(() -> numberEither.flatMap(throwingFlatMapper)).isSameAs(testException);

      // Test that Left instances don't execute throwing functions
      Either<String, String> leftResult = leftEither.flatMap(throwingFlatMapper);
      assertThat(leftResult).isSameAs(leftEither);
    }

    @Test
    @DisplayName("flatMap() works with validation testing framework")
    void flatMapWithValidationTesting() {
      Either<String, Integer> testEither = Either.right(42);

      // Test that flatMap properly validates null parameters using our framework
      ValidationTestBuilder.create()
          .assertNullFunction(() -> testEither.flatMap(null), "mapper")
          .execute();
    }
  }

  @Nested
  @DisplayName("Performance and Memory Tests")
  class PerformanceAndMemoryTests {

    @Test
    @DisplayName("flatMap() should handle large data efficiently")
    void flatMapShouldHandleLargeDataEfficiently() {
      // Create a large list
      java.util.List<Integer> largeList =
          java.util.stream.IntStream.range(0, 10000)
              .boxed()
              .collect(java.util.stream.Collectors.toList());

      Either<String, java.util.List<Integer>> largeEither = Either.right(largeList);

      // Transform the large list with flatMap
      Either<String, java.util.List<Integer>> transformed =
          largeEither
              .flatMap(
                  list ->
                      Either.right(
                          list.stream()
                              .filter(n -> n % 2 == 0)
                              .collect(java.util.stream.Collectors.toList())))
              .flatMap(
                  list ->
                      Either.right(
                          list.stream()
                              .map(n -> n * 2)
                              .collect(java.util.stream.Collectors.toList())));

      assertThat(transformed.getRight()).hasSize(5000);
      assertThat(transformed.getRight().get(0)).isEqualTo(0);
      assertThat(transformed.getRight().get(4999)).isEqualTo(19996); // 9998 * 2
    }

    @Test
    @DisplayName("flatMap() operations should be efficient with repeated transformations")
    void flatMapOperationsShouldBeEfficientWithRepeatedTransformations() {
      Either<String, String> start = Either.right("start");

      // Apply many flatMap transformations
      Either<String, String> result = start;
      for (int i = 0; i < 1000; i++) {
        final int index = i;
        result = result.flatMap(s -> Either.right(s + "_" + index));
      }

      assertThat(result.getRight()).startsWith("start_0_1_2");
      assertThat(result.getRight()).endsWith("_999");

      // Verify that Left instances are efficient (no transformations applied)
      Either<String, String> leftStart = Either.left("error");
      Either<String, String> leftResult = leftStart;

      for (int i = 0; i < 1000; i++) {
        final int index = i;
        leftResult = leftResult.flatMap(s -> Either.right(s + "_" + index));
      }

      // Should be the exact same instance since Left doesn't transform
      assertThat(leftResult).isSameAs(leftStart);
      assertThat(leftResult.getLeft()).isEqualTo("error");
    }
  }

  @Nested
  @DisplayName("Documentation Examples and Practical Usage")
  class DocumentationExamplesAndPracticalUsage {

    @Test
    @DisplayName("flatMap() practical usage patterns")
    void flatMapPracticalUsagePatterns() {
      // Validation pattern with flatMap
      Either<String, String> emailValidation = validateEmail("user@example.com");
      Either<String, String> passwordValidation = validatePassword("strongPassword123!");

      // Combine validations using flatMap
      Either<String, String> combined =
          emailValidation.flatMap(
              email ->
                  passwordValidation.flatMap(
                      password ->
                          Either.right(
                              String.format(
                                  "User: %s, Password: %s chars", email, password.length()))));

      assertThat(combined.getRight()).isEqualTo("User: user@example.com, Password: 18 chars");

      // Error accumulation pattern (though Either is fail-fast, not accumulating)
      Either<String, String> invalidEmail = validateEmail("invalid-email");
      Either<String, String> invalidCombined =
          invalidEmail.flatMap(
              email ->
                  passwordValidation.flatMap(password -> Either.right("Should not reach here")));

      assertThat(invalidCombined.isLeft()).isTrue();
      assertThat(invalidCombined.getLeft()).isEqualTo("Invalid email format");
    }

    @Test
    @DisplayName("flatMap() chaining for complex business logic")
    void flatMapChainingForComplexBusinessLogic() {
      // Simulate a multi-step process with flatMap
      Either<String, Integer> userId = Either.right(123);

      Either<String, String> processResult =
          userId
              .flatMap(this::validateUserId)
              .flatMap(this::loadUserData)
              .flatMap(this::processUserData)
              .flatMap(this::generateReport);

      assertThat(processResult.getRight())
          .isEqualTo("Report for user: User_123 - processed successfully");

      // Test with invalid user ID
      Either<String, Integer> invalidUserId = Either.right(-1);
      Either<String, String> invalidResult =
          invalidUserId
              .flatMap(this::validateUserId)
              .flatMap(this::loadUserData)
              .flatMap(this::processUserData)
              .flatMap(this::generateReport);

      assertThat(invalidResult.isLeft()).isTrue();
      assertThat(invalidResult.getLeft()).isEqualTo("Invalid user ID: -1");
    }

    // Helper methods for practical usage examples
    private Either<String, String> validateEmail(String email) {
      if (email.contains("@") && email.contains(".")) {
        return Either.right(email);
      }
      return Either.left("Invalid email format");
    }

    private Either<String, String> validatePassword(String password) {
      if (password.length() >= 8) {
        return Either.right(password);
      }
      return Either.left("Password too short");
    }

    private Either<String, Integer> validateUserId(Integer userId) {
      if (userId > 0) {
        return Either.right(userId);
      }
      return Either.left("Invalid user ID: " + userId);
    }

    private Either<String, String> loadUserData(Integer userId) {
      return Either.right("User_" + userId);
    }

    private Either<String, String> processUserData(String userData) {
      return Either.right(userData + " - processed");
    }

    private Either<String, String> generateReport(String processedData) {
      return Either.right("Report for user: " + processedData + " successfully");
    }
  }
}
