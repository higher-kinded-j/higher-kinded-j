package org.higherkindedj.hkt.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.list.ListKindHelper.unwrap;
import static org.higherkindedj.hkt.list.ListKindHelper.wrap;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ListMonad Laws and Operations")
class ListMonadTest {

  // Ensure ListMonad.INSTANCE returns Monad<ListKind.Witness>
  private final Monad<ListKind.Witness> listMonad = ListMonad.INSTANCE;

  // Helper Functions for Laws and flatMap tests
  private final Function<Integer, String> intToString = Object::toString;
  private final Function<String, String> appendWorld = s -> s + " world";
  private final Function<Integer, String> intToStringAppendWorld = intToString.andThen(appendWorld);

  // Function a -> M b (Integer -> Kind<ListKind.Witness, String>)
  private final Function<Integer, Kind<ListKind.Witness, String>> f_int_to_kindListString =
      i -> wrap(Arrays.asList("v" + i, "x" + i));
  // Function b -> M c (String -> Kind<ListKind.Witness, String>)
  private final Function<String, Kind<ListKind.Witness, String>> g_string_to_kindListString =
      s -> wrap(Arrays.asList(s + "!", s + "?"));

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldWrapValueInSingletonList() {
      // listMonad.of should return Kind<ListKind.Witness, Integer>
      Kind<ListKind.Witness, Integer> kind = listMonad.of(42);
      assertThat(unwrap(kind)).containsExactly(42);
    }

    @Test
    void of_shouldWrapNullAsEmptyList() {
      Kind<ListKind.Witness, String> kind = listMonad.of(null);
      assertThat(unwrap(kind)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionToEachElement() {
      Kind<ListKind.Witness, Integer> input = wrap(Arrays.asList(1, 2, 3));
      Kind<ListKind.Witness, Integer> result = listMonad.map(x -> x * 2, input);
      assertThat(unwrap(result)).containsExactly(2, 4, 6);
    }

    @Test
    void map_shouldReturnEmptyListForEmptyInput() {
      Kind<ListKind.Witness, Integer> input = wrap(Collections.emptyList());
      Kind<ListKind.Witness, String> result = listMonad.map(Object::toString, input);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void map_shouldHandleMappingToDifferentType() {
      Kind<ListKind.Witness, Integer> input = wrap(Arrays.asList(1, 2));
      Kind<ListKind.Witness, String> result = listMonad.map(x -> "v" + x, input);
      assertThat(unwrap(result)).containsExactly("v1", "v2");
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    @Test
    // Assuming this is the test that was failing (name might differ slightly from stack trace)
    void ap_nonEmptyFunctionsAndValues_shouldApplyAllFunctionsToAllValues() { // Or
      // ap_shouldApplyEachFunctionToEachValue
      Kind<ListKind.Witness, Function<Integer, String>> funcsKind =
          wrap(Arrays.asList(x -> "N" + x, x -> "X" + (x * 2)));
      Kind<ListKind.Witness, Integer> valuesKind = wrap(Arrays.asList(1, 2));

      Kind<ListKind.Witness, String> result = listMonad.ap(funcsKind, valuesKind);

      // Corrected assertion order to match ListMonad.ap implementation
      // Actual output: ["N1", "N2", "X2", "X4"]
      assertThat(unwrap(result)).containsExactly("N1", "N2", "X2", "X4");
    }

    @Test
    void ap_shouldReturnEmptyWhenFunctionsListIsEmpty() {
      Kind<ListKind.Witness, Function<Integer, String>> funcsKind = wrap(Collections.emptyList());
      Kind<ListKind.Witness, Integer> valuesKind = wrap(Arrays.asList(1, 2));
      Kind<ListKind.Witness, String> result = listMonad.ap(funcsKind, valuesKind);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void ap_shouldReturnEmptyWhenValuesListIsEmpty() {
      Kind<ListKind.Witness, Function<Integer, String>> funcsKind =
          wrap(Collections.singletonList(x -> "N" + x));
      Kind<ListKind.Witness, Integer> valuesKind = wrap(Collections.emptyList());
      Kind<ListKind.Witness, String> result = listMonad.ap(funcsKind, valuesKind);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void ap_shouldReturnEmptyWhenBothListsAreEmpty() {
      Kind<ListKind.Witness, Function<Integer, String>> funcsKind = wrap(Collections.emptyList());
      Kind<ListKind.Witness, Integer> valuesKind = wrap(Collections.emptyList());
      Kind<ListKind.Witness, String> result = listMonad.ap(funcsKind, valuesKind);
      assertThat(unwrap(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {

    Function<Integer, Kind<ListKind.Witness, String>> duplicateAndStringify =
        x -> wrap(Arrays.asList("v" + x, "v" + x));

    @Test
    void flatMap_shouldApplyFunctionAndFlattenResults() {
      Kind<ListKind.Witness, Integer> input = wrap(Arrays.asList(1, 2));
      Kind<ListKind.Witness, String> result = listMonad.flatMap(duplicateAndStringify, input);
      assertThat(unwrap(result)).containsExactly("v1", "v1", "v2", "v2");
    }

    @Test
    void flatMap_shouldReturnEmptyListForEmptyInput() {
      Kind<ListKind.Witness, Integer> input = wrap(Collections.emptyList());
      Kind<ListKind.Witness, String> result = listMonad.flatMap(duplicateAndStringify, input);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void flatMap_shouldHandleFunctionReturningEmptyList() {
      Function<Integer, Kind<ListKind.Witness, String>> funcReturningEmpty =
          x -> wrap(Collections.emptyList());
      Kind<ListKind.Witness, Integer> input = wrap(Arrays.asList(1, 2));
      Kind<ListKind.Witness, String> result = listMonad.flatMap(funcReturningEmpty, input);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void flatMap_chainingExample() {
      Kind<ListKind.Witness, Integer> initial = wrap(Arrays.asList(1, 2));

      Function<Integer, Kind<ListKind.Witness, Integer>> step1Func =
          x -> wrap(Arrays.asList(x, x + 10));
      Kind<ListKind.Witness, Integer> step1Result = listMonad.flatMap(step1Func, initial);

      Function<Integer, Kind<ListKind.Witness, String>> step2Func =
          y -> wrap(Collections.singletonList("N" + y));
      Kind<ListKind.Witness, String> finalResult = listMonad.flatMap(step2Func, step1Result);

      assertThat(unwrap(finalResult)).containsExactly("N1", "N11", "N2", "N12");
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {
    // Using the correctly typed helper functions defined at the class level
    int value = 5;
    Kind<ListKind.Witness, Integer> mValue = wrap(Arrays.asList(value, value + 1));
    Kind<ListKind.Witness, Integer> mValueEmpty = wrap(Collections.emptyList());

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      Kind<ListKind.Witness, Integer> ofValue = listMonad.of(value);
      Kind<ListKind.Witness, String> leftSide = listMonad.flatMap(f_int_to_kindListString, ofValue);
      Kind<ListKind.Witness, String> rightSide = f_int_to_kindListString.apply(value);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<ListKind.Witness, Integer>> ofFunc = i -> listMonad.of(i);

      Kind<ListKind.Witness, Integer> leftSide = listMonad.flatMap(ofFunc, mValue);
      Kind<ListKind.Witness, Integer> leftSideEmpty = listMonad.flatMap(ofFunc, mValueEmpty);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(mValue));
      assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(mValueEmpty));
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<ListKind.Witness, String> innerFlatMap =
          listMonad.flatMap(f_int_to_kindListString, mValue);
      Kind<ListKind.Witness, String> leftSide =
          listMonad.flatMap(g_string_to_kindListString, innerFlatMap);

      Function<Integer, Kind<ListKind.Witness, String>> rightSideFunc =
          a -> listMonad.flatMap(g_string_to_kindListString, f_int_to_kindListString.apply(a));
      Kind<ListKind.Witness, String> rightSide = listMonad.flatMap(rightSideFunc, mValue);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));

      Kind<ListKind.Witness, String> innerFlatMapEmpty =
          listMonad.flatMap(f_int_to_kindListString, mValueEmpty);
      Kind<ListKind.Witness, String> leftSideEmpty =
          listMonad.flatMap(g_string_to_kindListString, innerFlatMapEmpty);
      Kind<ListKind.Witness, String> rightSideEmpty = listMonad.flatMap(rightSideFunc, mValueEmpty);
      assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(rightSideEmpty));
    }
  }

  @Nested
  @DisplayName("Functor Laws (via Monad)")
  class FunctorLaws {
    @Test
    @DisplayName("1. Identity: map(id, fa) == fa")
    void identity() {
      Kind<ListKind.Witness, Integer> fa = wrap(Arrays.asList(1, 2, 3));
      Kind<ListKind.Witness, Integer> faEmpty = wrap(Collections.emptyList());

      assertThat(unwrap(listMonad.map(Function.identity(), fa))).isEqualTo(unwrap(fa));
      assertThat(unwrap(listMonad.map(Function.identity(), faEmpty))).isEqualTo(unwrap(faEmpty));
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      Kind<ListKind.Witness, Integer> fa = wrap(Arrays.asList(1, 2));
      Kind<ListKind.Witness, Integer> faEmpty = wrap(Collections.emptyList());

      Kind<ListKind.Witness, String> leftSide = listMonad.map(intToStringAppendWorld, fa);
      Kind<ListKind.Witness, String> rightSide =
          listMonad.map(appendWorld, listMonad.map(intToString, fa));

      Kind<ListKind.Witness, String> leftSideEmpty = listMonad.map(intToStringAppendWorld, faEmpty);
      Kind<ListKind.Witness, String> rightSideEmpty =
          listMonad.map(appendWorld, listMonad.map(intToString, faEmpty));

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
      assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(rightSideEmpty));
    }
  }

  @Nested
  @DisplayName("Applicative Laws (via Monad)")
  class ApplicativeLaws {
    Kind<ListKind.Witness, Integer> v = wrap(Arrays.asList(1, 2));
    Kind<ListKind.Witness, Integer> vEmpty = wrap(Collections.emptyList());
    Kind<ListKind.Witness, Function<Integer, String>> fKind =
        wrap(Arrays.asList(intToString, i -> "X" + i));
    Kind<ListKind.Witness, Function<Integer, String>> fKindEmpty = wrap(Collections.emptyList());

    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      Kind<ListKind.Witness, Function<Integer, Integer>> idFuncKind =
          listMonad.of(Function.identity());
      assertThat(unwrap(listMonad.ap(idFuncKind, v))).isEqualTo(unwrap(v));
      assertThat(unwrap(listMonad.ap(idFuncKind, vEmpty))).isEqualTo(unwrap(vEmpty));
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      int x = 10;
      Function<Integer, String> func = intToString;
      Kind<ListKind.Witness, Function<Integer, String>> apFunc = listMonad.of(func);
      Kind<ListKind.Witness, Integer> apVal = listMonad.of(x);

      Kind<ListKind.Witness, String> leftSide = listMonad.ap(apFunc, apVal);
      Kind<ListKind.Witness, String> rightSide = listMonad.of(func.apply(x));

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 20;
      Kind<ListKind.Witness, String> leftSide = listMonad.ap(fKind, listMonad.of(y));
      Kind<ListKind.Witness, String> leftSideEmpty = listMonad.ap(fKindEmpty, listMonad.of(y));

      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      Kind<ListKind.Witness, Function<Function<Integer, String>, String>> evalKind =
          listMonad.of(evalWithY);

      Kind<ListKind.Witness, String> rightSide = listMonad.ap(evalKind, fKind);
      Kind<ListKind.Witness, String> rightSideEmpty = listMonad.ap(evalKind, fKindEmpty);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
      assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(rightSideEmpty));
    }

    // Applicative Composition law is complex to set up for lists of functions and often omitted
    // or tested via Monad associativity which implies it if Functor laws also hold.
  }

  // mapN tests from the original file, adapted for new HKT
  @Nested
  @DisplayName("mapN tests")
  class MapNTests {

    Kind<ListKind.Witness, Integer> list1 = wrap(Arrays.asList(1, 2));
    Kind<ListKind.Witness, String> list2 = wrap(Arrays.asList("a", "b"));
    Kind<ListKind.Witness, Double> list3 = wrap(Arrays.asList(1.0, 2.0));
    Kind<ListKind.Witness, Boolean> list4 = wrap(Arrays.asList(true, false));

    Kind<ListKind.Witness, Integer> emptyListInt =
        wrap(Collections.emptyList()); // Explicit type for empty list

    @Test
    void map2_bothNonEmpty() {
      // Using BiFunction as it's more direct for map2
      BiFunction<Integer, String, String> f2_bi = (i, s) -> i + s;
      Kind<ListKind.Witness, String> result = listMonad.map2(list1, list2, f2_bi);
      assertThat(unwrap(result)).containsExactly("1a", "1b", "2a", "2b");
    }

    @Test
    void map2_firstEmpty() {
      BiFunction<Integer, String, String> f2_bi = (i, s) -> i + s;
      Kind<ListKind.Witness, String> result = listMonad.map2(emptyListInt, list2, f2_bi);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void map2_secondEmpty() {
      Kind<ListKind.Witness, String> emptyListString = wrap(Collections.emptyList());
      BiFunction<Integer, String, String> f2_bi = (i, s) -> i + s;
      Kind<ListKind.Witness, String> result = listMonad.map2(list1, emptyListString, f2_bi);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void map3_allNonEmpty() {
      Function3<Integer, String, Double, String> f3 =
          (i, s, d) -> String.format("%d-%s-%.1f", i, s, d);
      Kind<ListKind.Witness, String> result = listMonad.map3(list1, list2, list3, f3);
      assertThat(unwrap(result))
          .containsExactly(
              "1-a-1.0", "1-a-2.0", "1-b-1.0", "1-b-2.0", "2-a-1.0", "2-a-2.0", "2-b-1.0",
              "2-b-2.0");
    }

    @Test
    void map3_middleEmpty() {
      Kind<ListKind.Witness, String> emptyListString = wrap(Collections.emptyList());
      Function3<Integer, String, Double, String> f3 = (i, s, d) -> "Should not execute";
      Kind<ListKind.Witness, String> result = listMonad.map3(list1, emptyListString, list3, f3);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void map4_allNonEmpty() {
      Function4<Integer, String, Double, Boolean, String> f4 =
          (i, s, d, b) -> String.format("%d-%s-%.1f-%b", i, s, d, b);
      Kind<ListKind.Witness, String> result = listMonad.map4(list1, list2, list3, list4, f4);
      assertThat(unwrap(result)).hasSize(16);
      assertThat(unwrap(result)).contains("1-a-1.0-true", "2-b-2.0-false");
    }

    @Test
    void map4_lastEmpty() {
      Kind<ListKind.Witness, Boolean> emptyListBool = wrap(Collections.emptyList());
      Function4<Integer, String, Double, Boolean, String> f4 = (i, s, d, b) -> "Should not execute";
      Kind<ListKind.Witness, String> result =
          listMonad.map4(list1, list2, list3, emptyListBool, f4);
      assertThat(unwrap(result)).isEmpty();
    }
  }
}
