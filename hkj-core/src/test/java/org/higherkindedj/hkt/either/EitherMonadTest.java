// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.test.assertions.MonadAssertions.*;
import static org.higherkindedj.hkt.test.data.TestExceptions.*;
import static org.higherkindedj.hkt.test.data.TestFunctions.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.patterns.LawTestPattern;
import org.higherkindedj.hkt.test.patterns.MonadTestPattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherMonad Core Monad Operations Tests")
class EitherMonadTest {

  private EitherMonad<TestError> monad;

  @BeforeEach
  void setUp() {
    monad = EitherMonad.instance();
  }

  // Helper methods
  private <R> Either<TestError, R> narrow(Kind<EitherKind.Witness<TestError>, R> kind) {
    return EITHER.narrow(kind);
  }

  private <R> Kind<EitherKind.Witness<TestError>, R> right(R value) {
    return EITHER.widen(Either.right(value));
  }

  private <R> Kind<EitherKind.Witness<TestError>, R> left(String errorCode) {
    return EITHER.widen(Either.left(new TestError(errorCode)));
  }

  record TestError(String code) {}

  @Nested
  @DisplayName("Complete Monad Test Suite")
  class CompleteMonadTestSuite {

    @Test
    @DisplayName("Run complete monad test pattern")
    void runCompleteMonadTestPattern() {
      // Test data
      Kind<EitherKind.Witness<TestError>, Integer> validKind = right(10);
      Integer testValue = 42;
      Function<Integer, String> validMapper = INT_TO_STRING;
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> validFlatMapper =
          i -> right("flat:" + i);
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> validFunctionKind =
          monad.of(INT_TO_STRING);
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> testFunction =
          i -> right("test:" + i);
      Function<String, Kind<EitherKind.Witness<TestError>, String>> chainFunction =
          s -> right(s + "!");

      BiPredicate<Kind<EitherKind.Witness<TestError>, ?>, Kind<EitherKind.Witness<TestError>, ?>>
          equalityChecker = (k1, k2) -> narrow(k1).equals(narrow(k2));

      // Run complete test suite
      MonadTestPattern.runComplete(
          monad,
          validKind,
          testValue,
          validMapper,
          validFlatMapper,
          validFunctionKind,
          testFunction,
          chainFunction,
          equalityChecker);
    }
  }

  @Nested
  @DisplayName("FlatMap Operation Tests")
  class FlatMapOperationTests {

    @Test
    @DisplayName("flatMap() on Right applies function")
    void flatMapOnRightAppliesFunction() {
      Kind<EitherKind.Witness<TestError>, Integer> rightInput = right(42);
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> mapper =
          i -> right("value:" + i);

      Kind<EitherKind.Witness<TestError>, String> result = monad.flatMap(mapper, rightInput);

      assertThat(narrow(result).getRight()).isEqualTo("value:42");
    }

    @Test
    @DisplayName("flatMap() on Right can return Left")
    void flatMapOnRightCanReturnLeft() {
      Kind<EitherKind.Witness<TestError>, Integer> rightInput = right(42);
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> mapper = i -> left("ERROR");

      Kind<EitherKind.Witness<TestError>, String> result = monad.flatMap(mapper, rightInput);

      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("ERROR"));
    }

    @Test
    @DisplayName("flatMap() on Left passes through unchanged")
    void flatMapOnLeftPassesThrough() {
      Kind<EitherKind.Witness<TestError>, Integer> leftInput = left("E1");
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> mapper =
          i -> right("value:" + i);

      Kind<EitherKind.Witness<TestError>, String> result = monad.flatMap(mapper, leftInput);

      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("E1"));
    }

    @Test
    @DisplayName("flatMap() chaining operations")
    void flatMapChainingOperations() {
      Kind<EitherKind.Witness<TestError>, Integer> start = right(5);

      Kind<EitherKind.Witness<TestError>, String> result =
          monad.flatMap(i -> monad.flatMap(s -> right(s + "!"), right("step:" + i)), start);

      assertThat(narrow(result).getRight()).isEqualTo("step:5!");
    }

    @Test
    @DisplayName("flatMap() null validations")
    void flatMapNullValidations() {
      Kind<EitherKind.Witness<TestError>, Integer> validKind = right(42);
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> validMapper =
          i -> right("value:" + i);

      assertFlatMapFunctionNull(() -> monad.flatMap(null, validKind));
      assertFlatMapKindNull(() -> monad.flatMap(validMapper, null));
    }
  }

  @Nested
  @DisplayName("Monad Laws Tests")
  class MonadLawsTests {

    @Test
    @DisplayName("Left Identity Law")
    void testLeftIdentityLaw() {
      Integer testValue = 30;
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> testFunction =
          i -> right("law:" + i);

      BiPredicate<Kind<EitherKind.Witness<TestError>, ?>, Kind<EitherKind.Witness<TestError>, ?>>
          equalityChecker = (k1, k2) -> narrow(k1).equals(narrow(k2));

      LawTestPattern.testLeftIdentity(monad, testValue, testFunction, equalityChecker);
    }

    @Test
    @DisplayName("Right Identity Law")
    void testRightIdentityLaw() {
      Kind<EitherKind.Witness<TestError>, Integer> testKind = right(15);

      BiPredicate<Kind<EitherKind.Witness<TestError>, ?>, Kind<EitherKind.Witness<TestError>, ?>>
          equalityChecker = (k1, k2) -> narrow(k1).equals(narrow(k2));

      LawTestPattern.testRightIdentity(monad, testKind, equalityChecker);
    }

    @Test
    @DisplayName("Associativity Law")
    void testAssociativityLaw() {
      Kind<EitherKind.Witness<TestError>, Integer> testKind = right(15);
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> f = i -> right("f:" + i);
      Function<String, Kind<EitherKind.Witness<TestError>, String>> g = s -> right(s + ":g");

      BiPredicate<Kind<EitherKind.Witness<TestError>, ?>, Kind<EitherKind.Witness<TestError>, ?>>
          equalityChecker = (k1, k2) -> narrow(k1).equals(narrow(k2));

      LawTestPattern.testAssociativity(monad, testKind, f, g, equalityChecker);
    }

    @Test
    @DisplayName("Monad laws with Left values")
    void monadLawsWithLeftValues() {
      Kind<EitherKind.Witness<TestError>, Integer> leftKind = left("L1");
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> testFunction =
          i -> right("f:" + i);

      // Right identity with Left: flatMap(left, of) == left
      Kind<EitherKind.Witness<TestError>, Integer> rightIdentityResult =
          monad.flatMap(monad::of, leftKind);

      assertThat(narrow(rightIdentityResult)).isEqualTo(narrow(leftKind));

      // Left values pass through flatMap unchanged
      Kind<EitherKind.Witness<TestError>, String> flatMapResult =
          monad.flatMap(testFunction, leftKind);

      assertThat(narrow(flatMapResult).isLeft()).isTrue();
      assertThat(narrow(flatMapResult).getLeft()).isEqualTo(new TestError("L1"));
    }
  }

  @Nested
  @DisplayName("Exception Handling Tests")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("flatMap() propagates function exceptions")
    void flatMapPropagatesFunctionExceptions() {
      RuntimeException testException = runtime("flatMap test");
      Kind<EitherKind.Witness<TestError>, Integer> rightInput = right(42);

      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> throwingMapper =
          i -> {
            throw testException;
          };

      assertThatThrownBy(() -> monad.flatMap(throwingMapper, rightInput)).isSameAs(testException);
    }

    @Test
    @DisplayName("flatMap() on Left doesn't call throwing function")
    void flatMapOnLeftDoesntCallThrowingFunction() {
      RuntimeException testException = runtime("should not throw");
      Kind<EitherKind.Witness<TestError>, Integer> leftInput = left("E1");

      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> throwingMapper =
          i -> {
            throw testException;
          };

      // Should not throw because function not called on Left
      Kind<EitherKind.Witness<TestError>, String> result = monad.flatMap(throwingMapper, leftInput);

      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("E1"));
    }

    @Test
    @DisplayName("flatMap() validates null Kind returned from function")
    void flatMapValidatesNullKindReturned() {
      Kind<EitherKind.Witness<TestError>, Integer> rightInput = right(42);
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> nullReturningMapper =
          i -> null;

      assertThatThrownBy(() -> monad.flatMap(nullReturningMapper, rightInput))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("flatMap")
          .hasMessageContaining("returned null");
    }
  }

  @Nested
  @DisplayName("Complex Scenarios Tests")
  class ComplexScenariosTests {

    @Test
    @DisplayName("Deep flatMap chaining")
    void deepFlatMapChaining() {
      Kind<EitherKind.Witness<TestError>, Integer> start = right(1);

      Kind<EitherKind.Witness<TestError>, Integer> result = start;
      for (int i = 0; i < 10; i++) {
        final int increment = i;
        result = monad.flatMap(x -> right(x + increment), result);
      }

      // Sum: 1 + 0 + 1 + 2 + ... + 9 = 1 + 45 = 46
      assertThat(narrow(result).getRight()).isEqualTo(46);
    }

    @Test
    @DisplayName("flatMap with early Left short-circuits")
    void flatMapWithEarlyLeftShortCircuits() {
      Kind<EitherKind.Witness<TestError>, Integer> start = right(1);

      Kind<EitherKind.Witness<TestError>, Integer> result = start;
      for (int i = 0; i < 50; i++) {
        final int index = i;
        result =
            monad.flatMap(
                x -> {
                  if (index == 25) {
                    return left("STOP");
                  }
                  return right(x + index);
                },
                result);
      }

      assertThat(narrow(result).isLeft()).isTrue();
      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("STOP"));
    }

    @Test
    @DisplayName("flatMap with conditional branching")
    void flatMapWithConditionalBranching() {
      Kind<EitherKind.Witness<TestError>, Integer> number = right(42);

      Kind<EitherKind.Witness<TestError>, String> result =
          monad.flatMap(
              n -> {
                if (n % 2 == 0) {
                  return right("even:" + n);
                } else {
                  return left("ODD_NOT_ALLOWED");
                }
              },
              number);

      assertThat(narrow(result).getRight()).isEqualTo("even:42");
    }

    @Test
    @DisplayName("flatMap with nested Either types")
    void flatMapWithNestedEitherTypes() {
      Kind<EitherKind.Witness<TestError>, Integer> outer = right(10);

      Function<Integer, Kind<EitherKind.Witness<TestError>, Either<String, String>>> nestedMapper =
          i -> right(Either.right("nested:" + i));

      Kind<EitherKind.Witness<TestError>, Either<String, String>> nestedResult =
          monad.flatMap(nestedMapper, outer);

      Either<TestError, Either<String, String>> outerEither = narrow(nestedResult);
      assertThat(outerEither.isRight()).isTrue();
      assertThat(outerEither.getRight().isRight()).isTrue();
      assertThat(outerEither.getRight().getRight()).isEqualTo("nested:10");
    }
  }

  @Nested
  @DisplayName("Integration with Other Operations Tests")
  class IntegrationTests {

    @Test
    @DisplayName("flatMap integrates with map")
    void flatMapIntegratesWithMap() {
      Kind<EitherKind.Witness<TestError>, Integer> start = right(10);

      Kind<EitherKind.Witness<TestError>, String> result =
          monad.flatMap(i -> monad.map(s -> s.toUpperCase(), right("value:" + i)), start);

      assertThat(narrow(result).getRight()).isEqualTo("VALUE:10");
    }

    @Test
    @DisplayName("flatMap integrates with ap")
    void flatMapIntegratesWithAp() {
      Kind<EitherKind.Witness<TestError>, Integer> number = right(5);

      Kind<EitherKind.Witness<TestError>, String> result =
          monad.flatMap(
              n -> {
                Kind<EitherKind.Witness<TestError>, Function<Integer, String>> func =
                    monad.of(i -> "result:" + (i * n));
                return monad.ap(func, monad.of(10));
              },
              number);

      assertThat(narrow(result).getRight()).isEqualTo("result:50");
    }

    @Test
    @DisplayName("flatMap for-comprehension style")
    void flatMapForComprehensionStyle() {
      // Simulating for-comprehension: for { x <- right(1); y <- right(2) } yield x + y
      Kind<EitherKind.Witness<TestError>, Integer> result =
          monad.flatMap(x -> monad.flatMap(y -> monad.of(x + y), right(2)), right(1));

      assertThat(narrow(result).getRight()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Performance Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("flatMap with null values in Right")
    void flatMapWithNullValuesInRight() {
      Kind<EitherKind.Witness<TestError>, Integer> rightNull = right(null);

      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> nullSafeMapper =
          i -> right(i == null ? "was-null" : i.toString());

      Kind<EitherKind.Witness<TestError>, String> result = monad.flatMap(nullSafeMapper, rightNull);

      assertThat(narrow(result).getRight()).isEqualTo("was-null");
    }

    @Test
    @DisplayName("flatMap with large data structures")
    void flatMapWithLargeDataStructures() {
      List<Integer> largeList =
          IntStream.range(0, 1000).boxed().collect(java.util.stream.Collectors.toList());

      Kind<EitherKind.Witness<TestError>, List<Integer>> listKind = right(largeList);

      Kind<EitherKind.Witness<TestError>, Integer> sumResult =
          monad.flatMap(list -> right(list.stream().mapToInt(Integer::intValue).sum()), listKind);

      assertThat(narrow(sumResult).getRight()).isEqualTo(499500);
    }
  }

  @Nested
  @DisplayName("FlatMap Type System Tests")
  class FlatMapTypeSystemTests {

    @Test
    @DisplayName("flatMap() default implementation behavior")
    void flatMapDefaultImplementationBehavior() {
      // Test on concrete Either instances (not Kind)
      Either<TestError, Integer> leftEither = Either.left(new TestError("test error"));
      Either<TestError, Integer> rightEither = Either.right(42);

      Either<TestError, String> leftResult = leftEither.flatMap(i -> Either.right("mapped: " + i));
      assertThat(leftResult).isSameAs(leftEither);
      assertThat(leftResult.isLeft()).isTrue();

      Either<TestError, String> rightResult =
          rightEither.flatMap(i -> Either.right("mapped: " + i));
      assertThat(rightResult.isRight()).isTrue();
      assertThat(rightResult.getRight()).isEqualTo("mapped: 42");
    }

    @Test
    @DisplayName("flatMap() with wildcard types and covariance")
    void flatMapWithWildcardTypesAndCovariance() {
      Kind<EitherKind.Witness<TestError>, Integer> numberKind = right(5);

      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> stringMapper =
          i -> right("Number: " + i);

      Kind<EitherKind.Witness<TestError>, String> result = monad.flatMap(stringMapper, numberKind);
      assertThat(narrow(result).getRight()).isEqualTo("Number: 5");

      // Test with custom value class
      record CustomValue(String value) {
        @Override
        public String toString() {
          return "Custom(" + value + ")";
        }
      }

      Function<Integer, Kind<EitherKind.Witness<TestError>, CustomValue>> customMapper =
          i -> right(new CustomValue("value" + i));

      Kind<EitherKind.Witness<TestError>, CustomValue> customResult =
          monad.flatMap(customMapper, numberKind);
      assertThat(narrow(customResult).getRight().toString()).isEqualTo("Custom(value5)");
    }

    @Test
    @DisplayName("flatMap() type inference with nested Either")
    void flatMapTypeInferenceWithNestedEither() {
      Kind<EitherKind.Witness<TestError>, Integer> outer = right(10);

      // Nested Either - demonstrates type inference
      Function<Integer, Kind<EitherKind.Witness<TestError>, Either<String, String>>> nestedMapper =
          i -> right(Either.right("nested: " + i));

      Kind<EitherKind.Witness<TestError>, Either<String, String>> nestedResult =
          monad.flatMap(nestedMapper, outer);

      Either<TestError, Either<String, String>> outerEither = narrow(nestedResult);
      assertThat(outerEither.isRight()).isTrue();
      assertThat(outerEither.getRight().isRight()).isTrue();
      assertThat(outerEither.getRight().getRight()).isEqualTo("nested: 10");

      // Flattening nested Either
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> flattenMapper =
          i -> {
            Kind<EitherKind.Witness<TestError>, Either<String, String>> nested =
                right(Either.right("flattened: " + i));
            Either<TestError, Either<String, String>> e = narrow(nested);
            return e.isRight() && e.getRight().isRight()
                ? right(e.getRight().getRight())
                : left("FLATTEN_ERROR");
          };

      Kind<EitherKind.Witness<TestError>, String> flattenedResult =
          monad.flatMap(flattenMapper, outer);
      assertThat(narrow(flattenedResult).getRight()).isEqualTo("flattened: 10");
    }

    @Test
    @DisplayName("flatMap() maintains type safety with Records")
    void flatMapMaintainsTypeSafetyWithRecords() {
      record Person(String name, int age, java.util.List<String> hobbies) {}

      Person person = new Person("Alice", 30, List.of("reading", "hiking"));
      Kind<EitherKind.Witness<TestError>, Person> personKind = right(person);

      Kind<EitherKind.Witness<TestError>, String> summary =
          monad.flatMap(
              p ->
                  monad.map(
                      s -> s.toUpperCase(),
                      right(
                          p.name()
                              + " is "
                              + p.age()
                              + " and has "
                              + p.hobbies().size()
                              + " hobbies")),
              personKind);

      assertThat(narrow(summary).getRight()).isEqualTo("ALICE IS 30 AND HAS 2 HOBBIES");
    }
  }

  @Nested
  @DisplayName("FlatMap Complex Data Structure Tests")
  class FlatMapComplexDataTests {

    @Test
    @DisplayName("flatMap() with Map data structures")
    void flatMapWithMapDataStructures() {
      Map<String, List<Integer>> complexMap =
          Map.of(
              "numbers", List.of(1, 2, 3, 4, 5),
              "evens", List.of(2, 4, 6, 8));

      Kind<EitherKind.Witness<TestError>, Map<String, List<Integer>>> mapKind = right(complexMap);

      Kind<EitherKind.Witness<TestError>, Integer> sumResult =
          monad.flatMap(
              map ->
                  right(
                      map.values().stream()
                          .flatMap(Collection::stream)
                          .mapToInt(Integer::intValue)
                          .sum()),
              mapKind);

      assertThat(narrow(sumResult).getRight()).isEqualTo(35);
    }

    @Test
    @DisplayName("flatMap() with List transformations")
    void flatMapWithListTransformations() {
      Kind<EitherKind.Witness<TestError>, Integer> numberKind = right(5);

      Kind<EitherKind.Witness<TestError>, List<Integer>> listResult =
          monad.flatMap(
              n -> right(IntStream.range(0, n).boxed().collect(Collectors.toList())), numberKind);

      assertThat(narrow(listResult).getRight()).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    @DisplayName("flatMap() with conditional result types")
    void flatMapWithConditionalResultTypes() {
      Kind<EitherKind.Witness<TestError>, Integer> oddNumber = right(5);

      Kind<EitherKind.Witness<TestError>, String> conditionalResult =
          monad.flatMap(
              n -> {
                if (n % 2 == 0) {
                  return right("even: " + n);
                } else {
                  return left("ODD_NOT_ALLOWED");
                }
              },
              oddNumber);

      assertThat(narrow(conditionalResult).isLeft()).isTrue();
      assertThat(narrow(conditionalResult).getLeft().code()).isEqualTo("ODD_NOT_ALLOWED");
    }
  }

  @Nested
  @DisplayName("FlatMap Performance and Memory Tests")
  class FlatMapPerformanceTests {

    @Test
    @DisplayName("flatMap() memory efficiency with 100 operations")
    void flatMapMemoryEfficiencyWith100Operations() {
      Kind<EitherKind.Witness<TestError>, Integer> start = right(1);

      Kind<EitherKind.Witness<TestError>, Integer> result = start;
      for (int i = 0; i < 100; i++) {
        final int increment = i;
        result = monad.flatMap(x -> right(x + increment), result);
      }

      // Sum: 1 + 0 + 1 + 2 + ... + 99 = 1 + 4950 = 4951
      int expectedSum = 1 + (99 * 100) / 2;
      assertThat(narrow(result).getRight()).isEqualTo(expectedSum);

      // Test failure propagation at specific point
      Kind<EitherKind.Witness<TestError>, Integer> failingChain = right(1);
      int failurePoint = 25;

      for (int i = 0; i < 50; i++) {
        final int index = i;
        failingChain =
            monad.flatMap(
                x -> {
                  if (index == failurePoint) {
                    return left("FAILED_AT_" + index);
                  }
                  return right(x + index);
                },
                failingChain);
      }

      assertThat(narrow(failingChain).isLeft()).isTrue();
      assertThat(narrow(failingChain).getLeft().code()).isEqualTo("FAILED_AT_25");
    }

    @Test
    @DisplayName("flatMap() efficient with 1000 repeated transformations")
    void flatMapEfficientWith1000Transformations() {
      Kind<EitherKind.Witness<TestError>, String> start = right("start");

      Kind<EitherKind.Witness<TestError>, String> result = start;
      for (int i = 0; i < 1000; i++) {
        final int index = i;
        result = monad.flatMap(s -> right(s + "_" + index), result);
      }

      assertThat(narrow(result).getRight()).startsWith("start_0_1_2");
      assertThat(narrow(result).getRight()).endsWith("_999");

      // Left should not process any operations
      Kind<EitherKind.Witness<TestError>, String> leftStart = left("ERROR");
      Either<TestError, String> originalLeft = narrow(leftStart); // Store original
      Kind<EitherKind.Witness<TestError>, String> leftResult = leftStart;

      for (int i = 0; i < 1000; i++) {
        final int index = i;
        leftResult = monad.flatMap(s -> right(s + "_" + index), leftResult);
      }

      // Check the underlying Either instances are the same
      assertThat(narrow(leftResult)).isSameAs(originalLeft);
    }

    @Test
    @DisplayName("flatMap() with large data structures")
    void flatMapWithLargeDataStructures() {
      List<Integer> largeList = IntStream.range(0, 10000).boxed().collect(Collectors.toList());

      Kind<EitherKind.Witness<TestError>, List<Integer>> largeListKind = right(largeList);

      Kind<EitherKind.Witness<TestError>, Integer> sumResult =
          monad.flatMap(
              list -> right(list.stream().mapToInt(Integer::intValue).sum()), largeListKind);

      assertThat(narrow(sumResult).getRight()).isEqualTo(49995000);
    }
  }

  @Nested
  @DisplayName("FlatMap Default Implementation Tests")
  class FlatMapDefaultImplementationTests {

    @Test
    @DisplayName("flatMap() default implementation behavior on Either interface")
    void flatMapDefaultImplementationBehavior() {
      // Test on concrete Either instances (not Kind)
      Either<TestError, Integer> leftEither = Either.left(new TestError("test error"));
      Either<TestError, Integer> rightEither = Either.right(42);

      Either<TestError, String> leftResult = leftEither.flatMap(i -> Either.right("mapped: " + i));
      assertThat(leftResult).isSameAs(leftEither);
      assertThat(leftResult.isLeft()).isTrue();

      Either<TestError, String> rightResult =
          rightEither.flatMap(i -> Either.right("mapped: " + i));
      assertThat(rightResult.isRight()).isTrue();
      assertThat(rightResult.getRight()).isEqualTo("mapped: 42");
    }
  }

  @Nested
  @DisplayName("FlatMap Advanced Type System Tests")
  class FlatMapAdvancedTypeSystemTests {

    @Test
    @DisplayName("flatMap() with wildcard types and covariance")
    void flatMapWithWildcardTypesAndCovariance() {
      Kind<EitherKind.Witness<TestError>, Integer> numberKind = right(5);

      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> stringMapper =
          i -> right("Number: " + i);

      Kind<EitherKind.Witness<TestError>, String> result = monad.flatMap(stringMapper, numberKind);
      assertThat(narrow(result).getRight()).isEqualTo("Number: 5");

      // Test with custom value class
      record CustomValue(String value) {
        @Override
        public String toString() {
          return "Custom(" + value + ")";
        }
      }

      Function<Integer, Kind<EitherKind.Witness<TestError>, CustomValue>> customMapper =
          i -> right(new CustomValue("value" + i));

      Kind<EitherKind.Witness<TestError>, CustomValue> customResult =
          monad.flatMap(customMapper, numberKind);
      assertThat(narrow(customResult).getRight().toString()).isEqualTo("Custom(value5)");
    }

    @Test
    @DisplayName("flatMap() type inference with nested Either and flattening")
    void flatMapTypeInferenceWithNestedEither() {
      Kind<EitherKind.Witness<TestError>, Integer> outer = right(10);

      // Nested Either - demonstrates type inference
      Function<Integer, Kind<EitherKind.Witness<TestError>, Either<String, String>>> nestedMapper =
          i -> right(Either.right("nested: " + i));

      Kind<EitherKind.Witness<TestError>, Either<String, String>> nestedResult =
          monad.flatMap(nestedMapper, outer);

      Either<TestError, Either<String, String>> outerEither = narrow(nestedResult);
      assertThat(outerEither.isRight()).isTrue();
      assertThat(outerEither.getRight().isRight()).isTrue();
      assertThat(outerEither.getRight().getRight()).isEqualTo("nested: 10");

      // Flattening nested Either
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> flattenMapper =
          i -> {
            Kind<EitherKind.Witness<TestError>, Either<String, String>> nested =
                right(Either.right("flattened: " + i));
            Either<TestError, Either<String, String>> e = narrow(nested);
            return e.isRight() && e.getRight().isRight()
                ? right(e.getRight().getRight())
                : left("FLATTEN_ERROR");
          };

      Kind<EitherKind.Witness<TestError>, String> flattenedResult =
          monad.flatMap(flattenMapper, outer);
      assertThat(narrow(flattenedResult).getRight()).isEqualTo("flattened: 10");
    }

    @Test
    @DisplayName("flatMap() maintains type safety with complex Records")
    void flatMapMaintainsTypeSafetyWithRecords() {
      record Person(String name, int age, List<String> hobbies) {}

      Person person = new Person("Alice", 30, List.of("reading", "hiking"));
      Kind<EitherKind.Witness<TestError>, Person> personKind = right(person);

      Kind<EitherKind.Witness<TestError>, String> summary =
          monad.flatMap(
              p ->
                  monad.map(
                      s -> s.toUpperCase(),
                      right(
                          p.name()
                              + " is "
                              + p.age()
                              + " and has "
                              + p.hobbies().size()
                              + " hobbies")),
              personKind);

      assertThat(narrow(summary).getRight()).isEqualTo("ALICE IS 30 AND HAS 2 HOBBIES");

      Kind<EitherKind.Witness<TestError>, Person> errorKind =
          EITHER.widen(Either.left(new TestError("Person not found")));

      Kind<EitherKind.Witness<TestError>, String> errorResult =
          monad.flatMap(p -> right(p.name()), errorKind);

      assertThat(narrow(errorResult).isLeft()).isTrue();
      assertThat(narrow(errorResult).getLeft().code()).isEqualTo("Person not found");
    }
  }

  @Nested
  @DisplayName("FlatMap Practical Usage Patterns")
  class FlatMapPracticalUsageTests {

    @Test
    @DisplayName("flatMap() validation chaining pattern")
    void flatMapValidationChainingPattern() {
      Kind<EitherKind.Witness<TestError>, String> emailValidation =
          validateEmail("user@example.com");
      Kind<EitherKind.Witness<TestError>, String> passwordValidation =
          validatePassword("strongPassword123!");

      Kind<EitherKind.Witness<TestError>, String> combined =
          monad.flatMap(
              email ->
                  monad.flatMap(
                      password ->
                          right(
                              String.format(
                                  "User: %s, Password: %s chars", email, password.length())),
                      passwordValidation),
              emailValidation);

      assertThat(narrow(combined).getRight())
          .isEqualTo("User: user@example.com, Password: 18 chars");

      Kind<EitherKind.Witness<TestError>, String> invalidEmail = validateEmail("invalid-email");
      Kind<EitherKind.Witness<TestError>, String> invalidCombined =
          monad.flatMap(
              email ->
                  monad.flatMap(password -> right("Should not reach here"), passwordValidation),
              invalidEmail);

      assertThat(narrow(invalidCombined).isLeft()).isTrue();
      assertThat(narrow(invalidCombined).getLeft().code()).isEqualTo("INVALID_EMAIL");
    }

    @Test
    @DisplayName("flatMap() business logic pipeline")
    void flatMapBusinessLogicPipeline() {
      Kind<EitherKind.Witness<TestError>, Integer> userId = right(123);

      Kind<EitherKind.Witness<TestError>, Integer> step1 =
          monad.flatMap(this::validateUserId, userId);
      Kind<EitherKind.Witness<TestError>, String> step2 = monad.flatMap(this::loadUserData, step1);
      Kind<EitherKind.Witness<TestError>, String> step3 =
          monad.flatMap(this::processUserData, step2);
      Kind<EitherKind.Witness<TestError>, String> processResult =
          monad.flatMap(this::generateReport, step3);

      assertThat(narrow(processResult).getRight())
          .isEqualTo("Report for user: User_123 - processed successfully");

      Kind<EitherKind.Witness<TestError>, Integer> invalidUserId = right(-1);

      Kind<EitherKind.Witness<TestError>, Integer> invalidStep1 =
          monad.flatMap(this::validateUserId, invalidUserId);
      Kind<EitherKind.Witness<TestError>, String> invalidStep2 =
          monad.flatMap(this::loadUserData, invalidStep1);
      Kind<EitherKind.Witness<TestError>, String> invalidStep3 =
          monad.flatMap(this::processUserData, invalidStep2);
      Kind<EitherKind.Witness<TestError>, String> invalidResult =
          monad.flatMap(this::generateReport, invalidStep3);

      assertThat(narrow(invalidResult).isLeft()).isTrue();
      assertThat(narrow(invalidResult).getLeft().code()).isEqualTo("INVALID_USER_ID");
    }

    // Helper methods for practical usage examples
    private Kind<EitherKind.Witness<TestError>, String> validateEmail(String email) {
      if (email != null && email.contains("@") && email.contains(".")) {
        return right(email);
      }
      return left("INVALID_EMAIL");
    }

    private Kind<EitherKind.Witness<TestError>, String> validatePassword(String password) {
      if (password != null && password.length() >= 8) {
        return right(password);
      }
      return left("PASSWORD_TOO_SHORT");
    }

    private Kind<EitherKind.Witness<TestError>, Integer> validateUserId(Integer userId) {
      if (userId != null && userId > 0) {
        return right(userId);
      }
      return left("INVALID_USER_ID");
    }

    private Kind<EitherKind.Witness<TestError>, String> loadUserData(Integer userId) {
      return right("User_" + userId);
    }

    private Kind<EitherKind.Witness<TestError>, String> processUserData(String userData) {
      return right(userData + " - processed");
    }

    private Kind<EitherKind.Witness<TestError>, String> generateReport(String processedData) {
      return right("Report for user: " + processedData + " successfully");
    }
  }
}
