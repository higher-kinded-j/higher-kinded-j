// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.stream.StreamAssert.assertThatStream;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StreamMonad Complete Test Suite")
class StreamMonadTest extends StreamTestBase {

  private MonadZero<StreamKind.Witness> streamMonad;

  @BeforeEach
  void setUpMonad() {
    streamMonad = StreamMonad.INSTANCE;
    validateMonadFixtures();
  }

  // Helper Functions for Laws and flatMap tests
  private final Function<Integer, String> intToString = Object::toString;
  private final Function<String, String> appendWorld = s -> s + " world";
  private final Function<Integer, String> intToStringAppendWorld = intToString.andThen(appendWorld);

  // Function a -> M b (Integer -> Kind<StreamKind.Witness, String>)
  private final Function<Integer, Kind<StreamKind.Witness, String>> f_int_to_kindStreamString =
      i -> STREAM.widen(Stream.of("v" + i, "x" + i));
  // Function b -> M c (String -> Kind<StreamKind.Witness, String>)
  private final Function<String, Kind<StreamKind.Witness, String>> g_string_to_kindStreamString =
      s -> STREAM.widen(Stream.of(s + "!", s + "?"));

  // NOTE: TypeClassTest framework and validation disabled for Stream tests.
  // The framework stores Kind instances in fields and reuses them across multiple operations,
  // which violates Java Stream's single-use semantics. Individual law tests below provide
  // comprehensive coverage while respecting stream consumption constraints.
  //
  // @Nested
  // @DisplayName("Complete Monad Test Suite")
  // class CompleteMonadTestSuite {
  //
  //   @Test
  //   @DisplayName("Run complete Monad test pattern")
  //   void runCompleteMonadTestPattern() {
  //     TypeClassTest.<StreamKind.Witness>monad(StreamMonad.class)
  //         .<Integer>instance(streamMonad)
  //         .<String>withKind(validKind)
  //         .withMonadOperations(
  //             validKind2, validMapper, validFlatMapper, validFunctionKind,
  // validCombiningFunction)
  //         .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
  //         .testAll();
  //   }
  //
  //   @Test
  //   @DisplayName("Validate test structure follows standards")
  //   void validateTestStructure() {
  //     TestPatternValidator.ValidationResult result =
  //         TestPatternValidator.validateAndReport(StreamMonadTest.class);
  //
  //     if (result.hasErrors()) {
  //       result.printReport();
  //       throw new AssertionError("Test structure validation failed");
  //     }
  //   }
  // }

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    @DisplayName("of() wraps value in singleton stream")
    void ofWrapsValueInSingletonStream() {
      var result = streamMonad.of(DEFAULT_VALUE);

      assertThatStream(result).isNotEmpty().hasSize(1).containsExactly(DEFAULT_VALUE);
    }

    @Test
    @DisplayName("of() creates empty stream for null value")
    void ofCreatesEmptyStreamForNull() {
      Kind<StreamKind.Witness, String> result = streamMonad.of(null);

      assertThatStream(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    @DisplayName("map() applies function to each element lazily")
    void mapAppliesFunctionToEachElementLazily() {
      var input = streamOf(1, 2, 3);
      var result = streamMonad.map(x -> x * 2, input);

      assertThatStream(result).containsExactly(2, 4, 6);
    }

    @Test
    @DisplayName("map() returns empty stream for empty input")
    void mapReturnsEmptyStreamForEmptyInput() {
      Kind<StreamKind.Witness, Integer> input = emptyStream();
      var result = streamMonad.map(Object::toString, input);

      assertThatStream(result).isEmpty();
    }

    @Test
    @DisplayName("map() handles mapping to different type")
    void mapHandlesMappingToDifferentType() {
      var input = streamOf(1, 2);
      var result = streamMonad.map(x -> "v" + x, input);

      assertThatStream(result).containsExactly("v1", "v2");
    }

    @Test
    @DisplayName("map() works with infinite streams")
    void mapWorksWithInfiniteStreams() {
      Kind<StreamKind.Witness, Integer> infinite = STREAM.widen(Stream.iterate(1, n -> n + 1));
      var mapped = streamMonad.map(x -> x * 2, infinite);
      // Limit to avoid infinite loop
      List<Integer> result = STREAM.narrow(mapped).limit(5).collect(Collectors.toList());

      assertThat(result).containsExactly(2, 4, 6, 8, 10);
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    @Test
    @DisplayName("ap() applies all functions to all values")
    void apAppliesAllFunctionsToAllValues() {
      Kind<StreamKind.Witness, Function<Integer, String>> funcsKind =
          STREAM.widen(Stream.of(x -> "N" + x, x -> "X" + (x * 2)));
      var valuesKind = streamOf(1, 2);

      var result = streamMonad.ap(funcsKind, valuesKind);

      // Note: Due to flatMap semantics in ap, the order might differ from List
      // For streams: each function is applied to each value
      assertThatStream(result).containsExactly("N1", "N2", "X2", "X4");
    }

    @Test
    @DisplayName("ap() returns empty when functions stream is empty")
    void apReturnsEmptyWhenFunctionsStreamIsEmpty() {
      Kind<StreamKind.Witness, Function<Integer, String>> funcsKind = STREAM.widen(Stream.empty());
      var valuesKind = streamOf(1, 2);

      var result = streamMonad.ap(funcsKind, valuesKind);

      assertThatStream(result).isEmpty();
    }

    @Test
    @DisplayName("ap() returns empty when values stream is empty")
    void apReturnsEmptyWhenValuesStreamIsEmpty() {
      Kind<StreamKind.Witness, Function<Integer, String>> funcsKind =
          STREAM.widen(Stream.of(x -> "N" + x));
      Kind<StreamKind.Witness, Integer> valuesKind = emptyStream();

      var result = streamMonad.ap(funcsKind, valuesKind);

      assertThatStream(result).isEmpty();
    }

    @Test
    @DisplayName("ap() returns empty when both streams are empty")
    void apReturnsEmptyWhenBothStreamsAreEmpty() {
      Kind<StreamKind.Witness, Function<Integer, String>> funcsKind = STREAM.widen(Stream.empty());
      Kind<StreamKind.Witness, Integer> valuesKind = emptyStream();

      var result = streamMonad.ap(funcsKind, valuesKind);

      assertThatStream(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {

    Function<Integer, Kind<StreamKind.Witness, String>> duplicateAndStringify =
        x -> STREAM.widen(Stream.of("v" + x, "v" + x));

    @Test
    @DisplayName("flatMap() applies function and flattens results lazily")
    void flatMapAppliesFunctionAndFlattensResultsLazily() {
      var input = streamOf(1, 2);
      var result = streamMonad.flatMap(duplicateAndStringify, input);

      assertThatStream(result).containsExactly("v1", "v1", "v2", "v2");
    }

    @Test
    @DisplayName("flatMap() returns empty stream for empty input")
    void flatMapReturnsEmptyStreamForEmptyInput() {
      Kind<StreamKind.Witness, Integer> input = emptyStream();
      var result = streamMonad.flatMap(duplicateAndStringify, input);

      assertThatStream(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap() handles function returning empty stream")
    void flatMapHandlesFunctionReturningEmptyStream() {
      Function<Integer, Kind<StreamKind.Witness, String>> funcReturningEmpty = x -> emptyStream();
      var input = streamOf(1, 2);
      var result = streamMonad.flatMap(funcReturningEmpty, input);

      assertThatStream(result).isEmpty();
    }

    @Test
    @DisplayName("flatMap() chaining example")
    void flatMapChainingExample() {
      var initial = streamOf(1, 2);

      Function<Integer, Kind<StreamKind.Witness, Integer>> step1Func = x -> streamOf(x, x + 10);
      var step1Result = streamMonad.flatMap(step1Func, initial);

      Function<Integer, Kind<StreamKind.Witness, String>> step2Func = y -> streamOf("N" + y);
      var finalResult = streamMonad.flatMap(step2Func, step1Result);

      assertThatStream(finalResult).containsExactly("N1", "N11", "N2", "N12");
    }

    @Test
    @DisplayName("flatMap() works with infinite streams")
    void flatMapWorksWithInfiniteStreams() {
      Kind<StreamKind.Witness, Integer> infinite = STREAM.widen(Stream.iterate(1, n -> n + 1));
      var flattened =
          streamMonad.flatMap(
              n -> {
                if (n % 2 == 0) {
                  return streamOf(n, n * 10);
                } else {
                  return emptyStream();
                }
              },
              infinite);
      List<Integer> result = STREAM.narrow(flattened).limit(6).collect(Collectors.toList());

      assertThat(result).containsExactly(2, 20, 4, 40, 6, 60);
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {
    int value = 5;

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      var ofValue = streamMonad.of(value);
      var leftSide = streamMonad.flatMap(f_int_to_kindStreamString, ofValue);
      var rightSide = f_int_to_kindStreamString.apply(value);

      List<String> leftList = narrowToList(leftSide);
      List<String> rightList = narrowToList(rightSide);
      assertThat(leftList).isEqualTo(rightList);
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      var mValue1 = streamOf(value, value + 1);
      var mValue2 = streamOf(value, value + 1);
      Kind<StreamKind.Witness, Integer> mValueEmpty1 = emptyStream();
      Kind<StreamKind.Witness, Integer> mValueEmpty2 = emptyStream();
      Function<Integer, Kind<StreamKind.Witness, Integer>> ofFunc = i -> streamMonad.of(i);

      var leftSide = streamMonad.flatMap(ofFunc, mValue1);
      var leftSideEmpty = streamMonad.flatMap(ofFunc, mValueEmpty1);

      List<Integer> leftList = narrowToList(leftSide);
      List<Integer> mValueList = narrowToList(mValue2);
      assertThat(leftList).isEqualTo(mValueList);

      List<Integer> leftEmptyList = narrowToList(leftSideEmpty);
      List<Integer> mValueEmptyList = narrowToList(mValueEmpty2);
      assertThat(leftEmptyList).isEqualTo(mValueEmptyList);
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      var mValue1 = streamOf(value, value + 1);
      var mValue2 = streamOf(value, value + 1);
      Kind<StreamKind.Witness, Integer> mValueEmpty1 = emptyStream();
      Kind<StreamKind.Witness, Integer> mValueEmpty2 = emptyStream();

      var innerFlatMap = streamMonad.flatMap(f_int_to_kindStreamString, mValue1);
      var leftSide = streamMonad.flatMap(g_string_to_kindStreamString, innerFlatMap);

      Function<Integer, Kind<StreamKind.Witness, String>> rightSideFunc =
          a ->
              streamMonad.flatMap(g_string_to_kindStreamString, f_int_to_kindStreamString.apply(a));
      var rightSide = streamMonad.flatMap(rightSideFunc, mValue2);

      List<String> leftList = narrowToList(leftSide);
      List<String> rightList = narrowToList(rightSide);
      assertThat(leftList).isEqualTo(rightList);

      var innerFlatMapEmpty = streamMonad.flatMap(f_int_to_kindStreamString, mValueEmpty1);
      var leftSideEmpty = streamMonad.flatMap(g_string_to_kindStreamString, innerFlatMapEmpty);
      var rightSideEmpty = streamMonad.flatMap(rightSideFunc, mValueEmpty2);

      List<String> leftEmptyList = narrowToList(leftSideEmpty);
      List<String> rightEmptyList = narrowToList(rightSideEmpty);
      assertThat(leftEmptyList).isEqualTo(rightEmptyList);
    }
  }

  @Nested
  @DisplayName("Functor Laws (via Monad)")
  class FunctorLaws {
    @Test
    @DisplayName("1. Identity: map(id, fa) == fa")
    void identity() {
      var fa1 = streamOf(1, 2, 3);
      var fa2 = streamOf(1, 2, 3);
      Kind<StreamKind.Witness, Integer> faEmpty1 = emptyStream();
      Kind<StreamKind.Witness, Integer> faEmpty2 = emptyStream();

      List<Integer> mappedList = narrowToList(streamMonad.map(Function.identity(), fa1));
      List<Integer> faList = narrowToList(fa2);
      assertThat(mappedList).isEqualTo(faList);

      List<Integer> mappedEmptyList = narrowToList(streamMonad.map(Function.identity(), faEmpty1));
      List<Integer> faEmptyList = narrowToList(faEmpty2);
      assertThat(mappedEmptyList).isEqualTo(faEmptyList);
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      var fa1 = streamOf(1, 2);
      var fa2 = streamOf(1, 2);
      Kind<StreamKind.Witness, Integer> faEmpty1 = emptyStream();
      Kind<StreamKind.Witness, Integer> faEmpty2 = emptyStream();

      var leftSide = streamMonad.map(intToStringAppendWorld, fa1);
      var rightSide = streamMonad.map(appendWorld, streamMonad.map(intToString, fa2));

      var leftSideEmpty = streamMonad.map(intToStringAppendWorld, faEmpty1);
      var rightSideEmpty = streamMonad.map(appendWorld, streamMonad.map(intToString, faEmpty2));

      List<String> leftList = narrowToList(leftSide);
      List<String> rightList = narrowToList(rightSide);
      assertThat(leftList).isEqualTo(rightList);

      List<String> leftEmptyList = narrowToList(leftSideEmpty);
      List<String> rightEmptyList = narrowToList(rightSideEmpty);
      assertThat(leftEmptyList).isEqualTo(rightEmptyList);
    }
  }

  @Nested
  @DisplayName("Applicative Laws (via Monad)")
  class ApplicativeLaws {
    // Create fresh streams for each use to avoid single-use violations
    Kind<StreamKind.Witness, Function<Integer, String>> createFKind() {
      return STREAM.widen(Stream.of(intToString, i -> "X" + i));
    }

    Kind<StreamKind.Witness, Function<Integer, String>> createFKindEmpty() {
      return emptyStream();
    }

    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      var v1 = streamOf(1, 2);
      var v2 = streamOf(1, 2);
      Kind<StreamKind.Witness, Integer> vEmpty1 = emptyStream();
      Kind<StreamKind.Witness, Integer> vEmpty2 = emptyStream();
      Kind<StreamKind.Witness, Function<Integer, Integer>> idFuncKind1 =
          streamMonad.of(Function.identity());
      Kind<StreamKind.Witness, Function<Integer, Integer>> idFuncKind2 =
          streamMonad.of(Function.identity());

      List<Integer> apList = narrowToList(streamMonad.ap(idFuncKind1, v1));
      List<Integer> vList = narrowToList(v2);
      assertThat(apList).isEqualTo(vList);

      List<Integer> apEmptyList = narrowToList(streamMonad.ap(idFuncKind2, vEmpty1));
      List<Integer> vEmptyList = narrowToList(vEmpty2);
      assertThat(apEmptyList).isEqualTo(vEmptyList);
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      int x = 10;
      Function<Integer, String> func = intToString;
      var apFunc = streamMonad.of(func);
      var apVal = streamMonad.of(x);

      var leftSide = streamMonad.ap(apFunc, apVal);
      var rightSide = streamMonad.of(func.apply(x));

      List<String> leftList = narrowToList(leftSide);
      List<String> rightList = narrowToList(rightSide);
      assertThat(leftList).isEqualTo(rightList);
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 20;
      var leftSide = streamMonad.ap(createFKind(), streamMonad.of(y));
      var leftSideEmpty = streamMonad.ap(createFKindEmpty(), streamMonad.of(y));

      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      Kind<StreamKind.Witness, Function<Function<Integer, String>, String>> evalKind1 =
          streamMonad.of(evalWithY);
      Kind<StreamKind.Witness, Function<Function<Integer, String>, String>> evalKind2 =
          streamMonad.of(evalWithY);

      var rightSide = streamMonad.ap(evalKind1, createFKind());
      var rightSideEmpty = streamMonad.ap(evalKind2, createFKindEmpty());

      List<String> leftList = narrowToList(leftSide);
      List<String> rightList = narrowToList(rightSide);
      assertThat(leftList).isEqualTo(rightList);

      List<String> leftEmptyList = narrowToList(leftSideEmpty);
      List<String> rightEmptyList = narrowToList(rightSideEmpty);
      assertThat(leftEmptyList).isEqualTo(rightEmptyList);
    }
  }

  @Nested
  @DisplayName("mapN tests")
  class MapNTests {

    @Test
    @DisplayName("map2() combines both non-empty streams")
    void map2BothNonEmpty() {
      var stream1 = streamOf(1, 2);
      var stream2 = STREAM.widen(Stream.of("a", "b"));
      BiFunction<Integer, String, String> f2_bi = (i, s) -> i + s;

      var result = streamMonad.map2(stream1, stream2, f2_bi);

      assertThatStream(result).containsExactly("1a", "1b", "2a", "2b");
    }

    @Test
    @DisplayName("map2() returns empty when first is empty")
    void map2FirstEmpty() {
      Kind<StreamKind.Witness, Integer> emptyStreamInt = emptyStream();
      var stream2 = STREAM.widen(Stream.of("a", "b"));
      BiFunction<Integer, String, String> f2_bi = (i, s) -> i + s;

      var result = streamMonad.map2(emptyStreamInt, stream2, f2_bi);

      assertThatStream(result).isEmpty();
    }

    @Test
    @DisplayName("map2() returns empty when second is empty")
    void map2SecondEmpty() {
      var stream1 = streamOf(1, 2);
      Kind<StreamKind.Witness, String> emptyStreamString = emptyStream();
      BiFunction<Integer, String, String> f2_bi = (i, s) -> i + s;

      var result = streamMonad.map2(stream1, emptyStreamString, f2_bi);

      assertThatStream(result).isEmpty();
    }

    @Test
    @DisplayName("map3() combines all non-empty streams")
    void map3AllNonEmpty() {
      var stream1 = streamOf(1, 2);
      var stream2 = STREAM.widen(Stream.of("a", "b"));
      var stream3 = STREAM.widen(Stream.of(1.0, 2.0));
      Function3<Integer, String, Double, String> f3 =
          (i, s, d) -> String.format("%d-%s-%.1f", i, s, d);

      var result = streamMonad.map3(stream1, stream2, stream3, f3);

      assertThatStream(result)
          .containsExactly(
              "1-a-1.0", "1-a-2.0", "1-b-1.0", "1-b-2.0", "2-a-1.0", "2-a-2.0", "2-b-1.0",
              "2-b-2.0");
    }

    @Test
    @DisplayName("map3() returns empty when middle is empty")
    void map3MiddleEmpty() {
      var stream1 = streamOf(1, 2);
      Kind<StreamKind.Witness, String> emptyStreamString = emptyStream();
      var stream3 = STREAM.widen(Stream.of(1.0, 2.0));
      Function3<Integer, String, Double, String> f3 = (i, s, d) -> "Should not execute";

      var result = streamMonad.map3(stream1, emptyStreamString, stream3, f3);

      assertThatStream(result).isEmpty();
    }

    @Test
    @DisplayName("map4() combines all non-empty streams")
    void map4AllNonEmpty() {
      var stream1 = streamOf(1, 2);
      var stream2 = STREAM.widen(Stream.of("a", "b"));
      var stream3 = STREAM.widen(Stream.of(1.0, 2.0));
      var stream4 = STREAM.widen(Stream.of(true, false));
      Function4<Integer, String, Double, Boolean, String> f4 =
          (i, s, d, b) -> String.format("%d-%s-%.1f-%b", i, s, d, b);

      var result = streamMonad.map4(stream1, stream2, stream3, stream4, f4);

      assertThatStream(result).hasSize(16).contains("1-a-1.0-true", "2-b-2.0-false");
    }

    @Test
    @DisplayName("map4() returns empty when last is empty")
    void map4LastEmpty() {
      var stream1 = streamOf(1, 2);
      var stream2 = STREAM.widen(Stream.of("a", "b"));
      var stream3 = STREAM.widen(Stream.of(1.0, 2.0));
      Kind<StreamKind.Witness, Boolean> emptyStreamBool = emptyStream();
      Function4<Integer, String, Double, Boolean, String> f4 = (i, s, d, b) -> "Should not execute";

      var result = streamMonad.map4(stream1, stream2, stream3, emptyStreamBool, f4);

      assertThatStream(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("MonadZero tests")
  class MonadZeroTests {
    @Test
    @DisplayName("zero() returns empty stream")
    void zeroReturnsEmptyStream() {
      Kind<StreamKind.Witness, String> zero = streamMonad.zero();

      assertThatStream(zero).isEmpty();
    }
  }
}
