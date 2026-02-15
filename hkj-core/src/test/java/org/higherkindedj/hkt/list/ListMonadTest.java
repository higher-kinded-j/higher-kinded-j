// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.list.ListAssert.assertThatList;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.function.Function5;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ListMonad Complete Test Suite")
class ListMonadTest extends ListTestBase {

  private Monad<ListKind.Witness> listMonad;

  @BeforeEach
  void setUpMonad() {
    listMonad = ListMonad.INSTANCE;
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Complete Monad Test Suite")
  class CompleteMonadTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      TypeClassTest.<ListKind.Witness>monad(ListMonad.class)
          .<Integer>instance(listMonad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testAll();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(ListMonadTest.class);

      if (result.hasErrors()) {
        result.printReport();
        throw new AssertionError("Test structure validation failed");
      }
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("of() wraps value in singleton list")
    void ofWrapsValueInSingletonList() {
      var result = listMonad.of(DEFAULT_VALUE);

      assertThatList(result).containsExactly(DEFAULT_VALUE);
    }

    @Test
    @DisplayName("of() creates empty list for null value")
    void ofCreatesEmptyListForNull() {
      Kind<ListKind.Witness, String> result = listMonad.of(null);

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("map() applies function to each element")
    void mapAppliesFunctionToEachElement() {
      var input = listOf(1, 2, 3);
      var result = listMonad.map(x -> x * 2, input);

      assertThatList(result).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("map() returns empty list for empty input")
    void mapReturnsEmptyListForEmptyInput() {
      Kind<ListKind.Witness, Integer> input = emptyList();
      var result = listMonad.map(Object::toString, input);

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("map() handles mapping to different type")
    void mapHandlesMappingToDifferentType() {
      var input = listOf(1, 2);
      var result = listMonad.map(x -> "v" + x, input);

      assertThatList(result).containsExactly("v1", "v2");
    }

    @Test
    @DisplayName("ap() applies all functions to all values")
    void apAppliesAllFunctionsToAllValues() {
      Kind<ListKind.Witness, Function<Integer, String>> funcs =
          listOf(x -> "N" + x, x -> "X" + (x * 2));
      var values = listOf(1, 2);

      var result = listMonad.ap(funcs, values);

      assertThatList(result).containsExactly("N1", "N2", "X2", "X4");
    }

    @Test
    @DisplayName("ap() returns empty when functions list is empty")
    void apReturnsEmptyWhenFunctionsListIsEmpty() {
      Kind<ListKind.Witness, Function<Integer, String>> funcs = emptyList();
      var values = listOf(1, 2);

      var result = listMonad.ap(funcs, values);

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("ap() returns empty when values list is empty")
    void apReturnsEmptyWhenValuesListIsEmpty() {
      Kind<ListKind.Witness, Function<Integer, String>> funcs = singletonList(x -> "N" + x);
      Kind<ListKind.Witness, Integer> values = emptyList();

      var result = listMonad.ap(funcs, values);

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("ap() returns empty when both lists are empty")
    void apReturnsEmptyWhenBothListsAreEmpty() {
      Kind<ListKind.Witness, Function<Integer, String>> funcs = emptyList();
      Kind<ListKind.Witness, Integer> values = emptyList();

      var result = listMonad.ap(funcs, values);

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap() applies function and flattens results")
    void flatMapAppliesFunctionAndFlattensResults() {
      var input = listOf(1, 2);
      Function<Integer, Kind<ListKind.Witness, String>> duplicateAndStringify =
          x -> listOf("v" + x, "v" + x);

      var result = listMonad.flatMap(duplicateAndStringify, input);

      assertThatList(result).containsExactly("v1", "v1", "v2", "v2");
    }

    @Test
    @DisplayName("flatMap() returns empty list for empty input")
    void flatMapReturnsEmptyListForEmptyInput() {
      Kind<ListKind.Witness, Integer> input = emptyList();
      Function<Integer, Kind<ListKind.Witness, String>> func = x -> listOf("v" + x);

      var result = listMonad.flatMap(func, input);

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap() handles function returning empty list")
    void flatMapHandlesFunctionReturningEmptyList() {
      var input = listOf(1, 2);
      Function<Integer, Kind<ListKind.Witness, String>> funcReturningEmpty = x -> emptyList();

      var result = listMonad.flatMap(funcReturningEmpty, input);

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap() chains correctly")
    void flatMapChainsCorrectly() {
      var initial = listOf(1, 2);

      Function<Integer, Kind<ListKind.Witness, Integer>> step1Func = x -> listOf(x, x + 10);
      var step1Result = listMonad.flatMap(step1Func, initial);

      Function<Integer, Kind<ListKind.Witness, String>> step2Func = y -> singletonList("N" + y);
      var finalResult = listMonad.flatMap(step2Func, step1Result);

      assertThatList(finalResult).containsExactly("N1", "N11", "N2", "N12");
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<ListKind.Witness>monad(ListMonad.class)
          .<Integer>instance(listMonad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<ListKind.Witness>monad(ListMonad.class)
          .<Integer>instance(listMonad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<ListKind.Witness>monad(ListMonad.class)
          .<Integer>instance(listMonad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<ListKind.Witness>monad(ListMonad.class)
          .<Integer>instance(listMonad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("mapN Tests")
  class MapNTests {

    @Test
    @DisplayName("map2() combines both non-empty lists")
    void map2CombinesBothNonEmptyLists() {
      var list1 = listOf(1, 2);
      var list2 = listOf("a", "b");

      var result = listMonad.map2(list1, list2, (i, s) -> i + s);

      assertThatList(result).containsExactly("1a", "1b", "2a", "2b");
    }

    @Test
    @DisplayName("map2() returns empty if first list is empty")
    void map2ReturnsEmptyIfFirstListIsEmpty() {
      Kind<ListKind.Witness, Integer> list1 = emptyList();
      var list2 = listOf("a", "b");

      var result = listMonad.map2(list1, list2, (i, s) -> i + s);

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("map2() returns empty if second list is empty")
    void map2ReturnsEmptyIfSecondListIsEmpty() {
      var list1 = listOf(1, 2);
      Kind<ListKind.Witness, String> list2 = emptyList();

      var result = listMonad.map2(list1, list2, (i, s) -> i + s);

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("map3() combines all non-empty lists")
    void map3CombinesAllNonEmptyLists() {
      var list1 = listOf(1, 2);
      var list2 = listOf("a", "b");
      var list3 = listOf(1.0, 2.0);
      Function3<Integer, String, Double, String> f3 =
          (i, s, d) -> String.format("%d-%s-%.1f", i, s, d);

      var result = listMonad.map3(list1, list2, list3, f3);

      assertThatList(result)
          .containsExactly(
              "1-a-1.0", "1-a-2.0", "1-b-1.0", "1-b-2.0", "2-a-1.0", "2-a-2.0", "2-b-1.0",
              "2-b-2.0");
    }

    @Test
    @DisplayName("map3() returns empty if middle list is empty")
    void map3ReturnsEmptyIfMiddleListIsEmpty() {
      var list1 = listOf(1, 2);
      Kind<ListKind.Witness, String> list2 = emptyList();
      var list3 = listOf(1.0, 2.0);
      Function3<Integer, String, Double, String> f3 = (i, s, d) -> "Should not execute";

      var result = listMonad.map3(list1, list2, list3, f3);

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("map4() combines all non-empty lists")
    void map4CombinesAllNonEmptyLists() {
      var list1 = listOf(1, 2);
      var list2 = listOf("a", "b");
      var list3 = listOf(1.0, 2.0);
      var list4 = listOf(true, false);
      Function4<Integer, String, Double, Boolean, String> f4 =
          (i, s, d, b) -> String.format("%d-%s-%.1f-%b", i, s, d, b);

      var result = listMonad.map4(list1, list2, list3, list4, f4);

      assertThatList(result).hasSize(16);
      assertThatList(result).contains("1-a-1.0-true", "2-b-2.0-false");
    }

    @Test
    @DisplayName("map4() returns empty if last list is empty")
    void map4ReturnsEmptyIfLastListIsEmpty() {
      var list1 = listOf(1, 2);
      var list2 = listOf("a", "b");
      var list3 = listOf(1.0, 2.0);
      Kind<ListKind.Witness, Boolean> list4 = emptyList();
      Function4<Integer, String, Double, Boolean, String> f4 = (i, s, d, b) -> "Should not execute";

      var result = listMonad.map4(list1, list2, list3, list4, f4);

      assertThatList(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("flatMapN Tests")
  class FlatMapNTests {

    @Test
    @DisplayName("flatMap2() sequences two lists and applies combining function")
    void flatMap2SequencesTwoListsAndAppliesCombiningFunction() {
      var list1 = listOf(1, 2);
      var list2 = listOf("a", "b");

      var result = listMonad.flatMap2(list1, list2, (i, s) -> listOf(i + s, i + s.toUpperCase()));

      assertThatList(result).containsExactly("1a", "1A", "1b", "1B", "2a", "2A", "2b", "2B");
    }

    @Test
    @DisplayName("flatMap2() returns empty if first list is empty")
    void flatMap2ReturnsEmptyIfFirstListIsEmpty() {
      Kind<ListKind.Witness, Integer> list1 = emptyList();
      var list2 = listOf("a", "b");

      var result = listMonad.flatMap2(list1, list2, (i, s) -> listOf(i + s));

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap2() returns empty if second list is empty")
    void flatMap2ReturnsEmptyIfSecondListIsEmpty() {
      var list1 = listOf(1, 2);
      Kind<ListKind.Witness, String> list2 = emptyList();

      var result = listMonad.flatMap2(list1, list2, (i, s) -> listOf(i + s));

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap2() handles function returning empty list")
    void flatMap2HandlesFunctionReturningEmptyList() {
      var list1 = listOf(1, 2);
      var list2 = listOf("a", "b");

      var result = listMonad.flatMap2(list1, list2, (i, s) -> emptyList());

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap3() sequences three lists and applies combining function")
    void flatMap3SequencesThreeListsAndAppliesCombiningFunction() {
      var list1 = listOf(1, 2);
      var list2 = listOf("x");
      var list3 = listOf(10.0);
      Function3<Integer, String, Double, Kind<ListKind.Witness, String>> f3 =
          (i, s, d) -> listOf(String.format("%d-%s-%.0f", i, s, d));

      var result = listMonad.flatMap3(list1, list2, list3, f3);

      assertThatList(result).containsExactly("1-x-10", "2-x-10");
    }

    @Test
    @DisplayName("flatMap3() returns empty if middle list is empty")
    void flatMap3ReturnsEmptyIfMiddleListIsEmpty() {
      var list1 = listOf(1, 2);
      Kind<ListKind.Witness, String> list2 = emptyList();
      var list3 = listOf(10.0);
      Function3<Integer, String, Double, Kind<ListKind.Witness, String>> f3 =
          (i, s, d) -> listOf("Should not execute");

      var result = listMonad.flatMap3(list1, list2, list3, f3);

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap3() with function returning multiple results")
    void flatMap3WithFunctionReturningMultipleResults() {
      var list1 = listOf(1);
      var list2 = listOf("a");
      var list3 = listOf(2.0);
      Function3<Integer, String, Double, Kind<ListKind.Witness, String>> f3 =
          (i, s, d) -> listOf(i + s, s + d, i + s + d);

      var result = listMonad.flatMap3(list1, list2, list3, f3);

      assertThatList(result).containsExactly("1a", "a2.0", "1a2.0");
    }

    @Test
    @DisplayName("flatMap4() sequences four lists and applies combining function")
    void flatMap4SequencesFourListsAndAppliesCombiningFunction() {
      var list1 = listOf(1);
      var list2 = listOf("a");
      var list3 = listOf(2.0);
      var list4 = listOf(true);
      Function4<Integer, String, Double, Boolean, Kind<ListKind.Witness, String>> f4 =
          (i, s, d, b) -> listOf(String.format("%d-%s-%.0f-%b", i, s, d, b));

      var result = listMonad.flatMap4(list1, list2, list3, list4, f4);

      assertThatList(result).containsExactly("1-a-2-true");
    }

    @Test
    @DisplayName("flatMap4() returns empty if any list is empty")
    void flatMap4ReturnsEmptyIfAnyListIsEmpty() {
      var list1 = listOf(1);
      var list2 = listOf("a");
      Kind<ListKind.Witness, Double> list3 = emptyList();
      var list4 = listOf(true);
      Function4<Integer, String, Double, Boolean, Kind<ListKind.Witness, String>> f4 =
          (i, s, d, b) -> listOf("Should not execute");

      var result = listMonad.flatMap4(list1, list2, list3, list4, f4);

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap5() sequences five lists and applies combining function")
    void flatMap5SequencesFiveListsAndAppliesCombiningFunction() {
      var list1 = listOf(1);
      var list2 = listOf("a");
      var list3 = listOf(2.0);
      var list4 = listOf(true);
      var list5 = listOf('X');
      Function5<Integer, String, Double, Boolean, Character, Kind<ListKind.Witness, String>> f5 =
          (i, s, d, b, c) -> listOf(String.format("%d-%s-%.0f-%b-%c", i, s, d, b, c));

      var result = listMonad.flatMap5(list1, list2, list3, list4, list5, f5);

      assertThatList(result).containsExactly("1-a-2-true-X");
    }

    @Test
    @DisplayName("flatMap5() returns empty if any list is empty")
    void flatMap5ReturnsEmptyIfAnyListIsEmpty() {
      var list1 = listOf(1);
      var list2 = listOf("a");
      var list3 = listOf(2.0);
      var list4 = listOf(true);
      Kind<ListKind.Witness, Character> list5 = emptyList();
      Function5<Integer, String, Double, Boolean, Character, Kind<ListKind.Witness, String>> f5 =
          (i, s, d, b, c) -> listOf("Should not execute");

      var result = listMonad.flatMap5(list1, list2, list3, list4, list5, f5);

      assertThatList(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap5() with function returning multiple results")
    void flatMap5WithFunctionReturningMultipleResults() {
      var list1 = listOf(1);
      var list2 = listOf("a");
      var list3 = listOf(2.0);
      var list4 = listOf(true);
      var list5 = listOf('X');
      Function5<Integer, String, Double, Boolean, Character, Kind<ListKind.Witness, String>> f5 =
          (i, s, d, b, c) -> listOf("first", "second", "third");

      var result = listMonad.flatMap5(list1, list2, list3, list4, list5, f5);

      assertThatList(result).containsExactly("first", "second", "third");
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Large list operations perform efficiently")
    void largeListOperationsPerformEfficiently() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        var large = rangeList(0, 1000);

        long startTime = System.nanoTime();
        var result = listMonad.map(x -> x * 2, large);
        long duration = System.nanoTime() - startTime;

        assertThatList(result).hasSize(1000);
        assertThat(duration).isLessThan(50_000_000L); // Less than 50ms
      }
    }

    @Test
    @DisplayName("flatMap preserves order")
    void flatMapPreservesOrder() {
      var input = listOf(3, 1, 2);
      Function<Integer, Kind<ListKind.Witness, Integer>> func = x -> listOf(x, x * 10);

      var result = listMonad.flatMap(func, input);

      assertThatList(result).startsWith(3, 30, 1, 10);
      assertThatList(result).endsWith(2, 20);
    }

    @Test
    @DisplayName("Nested list flattening")
    void nestedListFlattening() {
      // List<List<Integer>> flattened to List<Integer>
      var input = listOf(1, 2);
      Function<Integer, Kind<ListKind.Witness, Integer>> nested = x -> listOf(x, x + 10, x + 20);

      Kind<ListKind.Witness, Kind<ListKind.Witness, Integer>> step1 = listMonad.map(nested, input);
      Kind<ListKind.Witness, Integer> flattened = listMonad.flatMap(innerList -> innerList, step1);

      assertThatList(flattened).containsExactly(1, 11, 21, 2, 12, 22);
    }
  }
}
