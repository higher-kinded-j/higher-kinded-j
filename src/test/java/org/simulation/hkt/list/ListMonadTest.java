package org.simulation.hkt.list;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.function.Function3;
import org.simulation.hkt.function.Function4;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.simulation.hkt.list.ListKindHelper.unwrap;
import static org.simulation.hkt.list.ListKindHelper.wrap;

class ListMonadTest {

  private final ListMonad listMonad = new ListMonad();

  // Helper Functions for Laws
  private final Function<Integer, String> intToString = Object::toString;
  private final Function<String, String> appendWorld = s -> s + " world";
  private final Function<Integer, String> intToStringAppendWorld = intToString.andThen(appendWorld);

  // Function a -> M b (Integer -> ListKind<String>)
  private final Function<Integer, Kind<ListKind<?>, String>> f =
          i -> wrap(Arrays.asList("v" + i, "x" + i));
  // Function b -> M c (String -> ListKind<String>)
  private final Function<String, Kind<ListKind<?>, String>> g =
          s -> wrap(Arrays.asList(s + "!", s + "?"));


  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldWrapValueInSingletonList() {
      Kind<ListKind<?>, Integer> kind = listMonad.of(42);
      assertThat(unwrap(kind)).containsExactly(42);
    }

    @Test
    void of_shouldWrapNullInSingletonList() {
      Kind<ListKind<?>, String> kind = listMonad.of(null);
      assertThat(unwrap(kind)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionToEachElement() {
      Kind<ListKind<?>, Integer> input = wrap(Arrays.asList(1, 2, 3));
      Kind<ListKind<?>, Integer> result = listMonad.map(x -> x * 2, input);
      assertThat(unwrap(result)).containsExactly(2, 4, 6);
    }

    @Test
    void map_shouldReturnEmptyListForEmptyInput() {
      Kind<ListKind<?>, Integer> input = wrap(Collections.emptyList());
      Kind<ListKind<?>, String> result = listMonad.map(Object::toString, input);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void map_shouldHandleMappingToDifferentType() {
      Kind<ListKind<?>, Integer> input = wrap(Arrays.asList(1, 2));
      Kind<ListKind<?>, String> result = listMonad.map(x -> "v" + x, input);
      assertThat(unwrap(result)).containsExactly("v1", "v2");
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    @Test
    void ap_shouldApplyEachFunctionToEachValue() {
      Kind<ListKind<?>, Function<Integer, String>> funcsKind = wrap(Arrays.asList(
              x -> "N" + x,
              x -> "X" + (x * 2)
      ));
      Kind<ListKind<?>, Integer> valuesKind = wrap(Arrays.asList(1, 2));

      Kind<ListKind<?>, String> result = listMonad.ap(funcsKind, valuesKind);

      // Cartesian product: (f1(1), f1(2), f2(1), f2(2))
      assertThat(unwrap(result)).containsExactly("N1", "N2", "X2", "X4");
    }

    @Test
    void ap_shouldReturnEmptyWhenFunctionsListIsEmpty() {
      Kind<ListKind<?>, Function<Integer, String>> funcsKind = wrap(Collections.emptyList());
      Kind<ListKind<?>, Integer> valuesKind = wrap(Arrays.asList(1, 2));
      Kind<ListKind<?>, String> result = listMonad.ap(funcsKind, valuesKind);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void ap_shouldReturnEmptyWhenValuesListIsEmpty() {
      Kind<ListKind<?>, Function<Integer, String>> funcsKind = wrap(Collections.singletonList(x -> "N" + x));
      Kind<ListKind<?>, Integer> valuesKind = wrap(Collections.emptyList());
      Kind<ListKind<?>, String> result = listMonad.ap(funcsKind, valuesKind);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void ap_shouldReturnEmptyWhenBothListsAreEmpty() {
      Kind<ListKind<?>, Function<Integer, String>> funcsKind = wrap(Collections.emptyList());
      Kind<ListKind<?>, Integer> valuesKind = wrap(Collections.emptyList());
      Kind<ListKind<?>, String> result = listMonad.ap(funcsKind, valuesKind);
      assertThat(unwrap(result)).isEmpty();
    }
  }


  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {

    Function<Integer, Kind<ListKind<?>, String>> duplicateAndStringify =
            x -> wrap(Arrays.asList("v" + x, "v" + x));

    @Test
    void flatMap_shouldApplyFunctionAndFlattenResults() {
      Kind<ListKind<?>, Integer> input = wrap(Arrays.asList(1, 2));
      Kind<ListKind<?>, String> result = listMonad.flatMap(duplicateAndStringify, input);
      assertThat(unwrap(result)).containsExactly("v1", "v1", "v2", "v2");
    }

    @Test
    void flatMap_shouldReturnEmptyListForEmptyInput() {
      Kind<ListKind<?>, Integer> input = wrap(Collections.emptyList());
      Kind<ListKind<?>, String> result = listMonad.flatMap(duplicateAndStringify, input);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void flatMap_shouldHandleFunctionReturningEmptyList() {
      Function<Integer, Kind<ListKind<?>, String>> funcReturningEmpty =
              x -> wrap(Collections.emptyList());
      Kind<ListKind<?>, Integer> input = wrap(Arrays.asList(1, 2));
      Kind<ListKind<?>, String> result = listMonad.flatMap(funcReturningEmpty, input);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void flatMap_chainingExample() {
      Kind<ListKind<?>, Integer> initial = wrap(Arrays.asList(1, 2));

      // Step 1: x -> List(x, x+10)
      Kind<ListKind<?>, Integer> step1Result = listMonad.flatMap(
              x -> wrap(Arrays.asList(x, x + 10)),
              initial
      ); // Expected: List(1, 11, 2, 12)

      // Step 2: y -> List("N" + y)
      Kind<ListKind<?>, String> finalResult = listMonad.flatMap(
              y -> wrap(Collections.singletonList("N" + y)),
              step1Result
      );

      assertThat(unwrap(finalResult)).containsExactly("N1", "N11", "N2", "N12");
    }

    // --- Law Tests ---

    @Nested
    @DisplayName("Functor Laws")
    class FunctorLaws {
      @Test
      @DisplayName("1. Identity: map(id, fa) == fa")
      void identity() {
        Kind<ListKind<?>, Integer> fa = wrap(Arrays.asList(1, 2, 3));
        Kind<ListKind<?>, Integer> faEmpty = wrap(Collections.emptyList());

        assertThat(unwrap(listMonad.map(Function.identity(), fa)))
                .isEqualTo(unwrap(fa));
        assertThat(unwrap(listMonad.map(Function.identity(), faEmpty)))
                .isEqualTo(unwrap(faEmpty));
      }

      @Test
      @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
      void composition() {
        Kind<ListKind<?>, Integer> fa = wrap(Arrays.asList(1, 2));
        Kind<ListKind<?>, Integer> faEmpty = wrap(Collections.emptyList());

        Kind<ListKind<?>, String> leftSide = listMonad.map(intToStringAppendWorld, fa);
        Kind<ListKind<?>, String> rightSide = listMonad.map(appendWorld, listMonad.map(intToString, fa));

        Kind<ListKind<?>, String> leftSideEmpty = listMonad.map(intToStringAppendWorld, faEmpty);
        Kind<ListKind<?>, String> rightSideEmpty = listMonad.map(appendWorld, listMonad.map(intToString, faEmpty));

        assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
        assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(rightSideEmpty));
      }
    }

    @Nested
    @DisplayName("Applicative Laws")
    class ApplicativeLaws {

      Kind<ListKind<?>, Integer> v = wrap(Arrays.asList(1, 2));
      Kind<ListKind<?>, Integer> vEmpty = wrap(Collections.emptyList());
      Kind<ListKind<?>, Function<Integer, String>> fKind = wrap(Arrays.asList(intToString, i -> "X" + i));
      Kind<ListKind<?>, Function<Integer, String>> fKindEmpty = wrap(Collections.emptyList());
      Kind<ListKind<?>, Function<String, String>> gKind = wrap(Arrays.asList(appendWorld, s -> s.toUpperCase()));
      Kind<ListKind<?>, Function<String, String>> gKindEmpty = wrap(Collections.emptyList());

      @Test
      @DisplayName("1. Identity: ap(of(id), v) == v")
      void identity() {
        Kind<ListKind<?>, Function<Integer, Integer>> idFuncKind = listMonad.of(Function.identity()); // List(id)
        assertThat(unwrap(listMonad.ap(idFuncKind, v))).isEqualTo(unwrap(v));
        assertThat(unwrap(listMonad.ap(idFuncKind, vEmpty))).isEqualTo(unwrap(vEmpty));
      }

      @Test
      @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
      void homomorphism() {
        int x = 10;
        Function<Integer, String> f = intToString;
        Kind<ListKind<?>, Function<Integer, String>> apFunc = listMonad.of(f); // List(f)
        Kind<ListKind<?>, Integer> apVal = listMonad.of(x); // List(x)

        Kind<ListKind<?>, String> leftSide = listMonad.ap(apFunc, apVal); // List(f(x))
        Kind<ListKind<?>, String> rightSide = listMonad.of(f.apply(x)); // List(f(x))

        assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
      }

      @Test
      @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
      void interchange() {
        int y = 20;
        // Left Side: ap(fKind, of(y)) -> applies each function in fKind to y
        Kind<ListKind<?>, String> leftSide = listMonad.ap(fKind, listMonad.of(y));
        Kind<ListKind<?>, String> leftSideEmpty = listMonad.ap(fKindEmpty, listMonad.of(y)); // Should be empty

        // Right Side: ap(of(f -> f(y)), fKind) -> applies the ($ y) function to each function in fKind
        Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
        Kind<ListKind<?>, Function<Function<Integer, String>, String>> evalKind = listMonad.of(evalWithY); // List(fn -> fn(y))

        Kind<ListKind<?>, String> rightSide = listMonad.ap(evalKind, fKind);
        Kind<ListKind<?>, String> rightSideEmpty = listMonad.ap(evalKind, fKindEmpty); // Should be empty

        // Both sides should evaluate to List(f1(y), f2(y), ...)
        assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
        assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(rightSideEmpty));
      }

      @Test
      @DisplayName("4. Composition: ap(ap(map(compose, gKind), fKind), v) == ap(gKind, ap(fKind, v)) - Adjusted")
      void composition() {
        Function<Function<String, String>, Function<Function<Integer, String>, Function<Integer, String>>> composeMap =
                g -> f -> g.compose(f);

        // Left side: ap(ap(map(composeMap, gKind), fKind), v)
        Kind<ListKind<?>, Function<Function<Integer, String>, Function<Integer, String>>> mappedCompose =
                listMonad.map(composeMap, gKind); // List(g -> f -> g.compose(f), ...)
        Kind<ListKind<?>, Function<Integer, String>> ap1 =
                listMonad.ap(mappedCompose, fKind); // List(g1.compose(f1), g1.compose(f2), g2.compose(f1), g2.compose(f2))
        Kind<ListKind<?>, String> leftSide = listMonad.ap(ap1, v); // Apply composed functions to v

        // Right side: ap(gKind, ap(fKind, v))
        Kind<ListKind<?>, String> innerAp = listMonad.ap(fKind, v); // List(f1(v1), f1(v2), f2(v1), f2(v2))
        Kind<ListKind<?>, String> rightSide = listMonad.ap(gKind, innerAp); // Apply g functions to results of innerAp

        // Both sides represent applying all combinations of g(f(v))
        assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
        // Test with empty lists
        assertThat(unwrap(listMonad.ap(listMonad.ap(listMonad.map(composeMap, gKindEmpty), fKind), v))).isEmpty();
        assertThat(unwrap(listMonad.ap(gKindEmpty, listMonad.ap(fKind, v)))).isEmpty();
        assertThat(unwrap(listMonad.ap(listMonad.ap(listMonad.map(composeMap, gKind), fKindEmpty), v))).isEmpty();
        assertThat(unwrap(listMonad.ap(gKind, listMonad.ap(fKindEmpty, v)))).isEmpty();
        assertThat(unwrap(listMonad.ap(listMonad.ap(listMonad.map(composeMap, gKind), fKind), vEmpty))).isEmpty();
        assertThat(unwrap(listMonad.ap(gKind, listMonad.ap(fKind, vEmpty)))).isEmpty();
      }
    }


    @Nested
    @DisplayName("Monad Laws")
    class MonadLaws {

      int value = 5;
      Kind<ListKind<?>, Integer> mValue = wrap(Arrays.asList(value, value + 1)); // List(5, 6)
      Kind<ListKind<?>, Integer> mValueEmpty = wrap(Collections.emptyList());


      @Test
      @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
      void leftIdentity() {
        Kind<ListKind<?>, Integer> ofValue = listMonad.of(value); // List(5)
        Kind<ListKind<?>, String> leftSide = listMonad.flatMap(f, ofValue); // f(5) -> List("v5", "x5")
        Kind<ListKind<?>, String> rightSide = f.apply(value); // f(5) -> List("v5", "x5")

        assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
      }

      @Test
      @DisplayName("2. Right Identity: flatMap(m, of) == m")
      void rightIdentity() {
        // Function a -> Kind<..., a>
        Function<Integer, Kind<ListKind<?>, Integer>> ofFunc = i -> listMonad.of(i); // i -> List(i)

        Kind<ListKind<?>, Integer> leftSide = listMonad.flatMap(ofFunc, mValue); // flatMap( i->List(i), List(5,6)) -> List(5,6)
        Kind<ListKind<?>, Integer> leftSideEmpty = listMonad.flatMap(ofFunc, mValueEmpty); // flatMap(i->List(i), []) -> []

        assertThat(unwrap(leftSide)).isEqualTo(unwrap(mValue));
        assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(mValueEmpty));
      }


      @Test
      @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
      void associativity() {
        // Left Side: flatMap(flatMap(m, f), g)
        // flatMap(List(5, 6), f) -> List("v5", "x5", "v6", "x6")
        Kind<ListKind<?>, String> innerFlatMap = listMonad.flatMap(f, mValue);
        // flatMap(g, List("v5", "x5", "v6", "x6"), -> applies g to each and flattens
        Kind<ListKind<?>, String> leftSide = listMonad.flatMap(g, innerFlatMap);

        // Right Side: flatMap(a -> flatMap(f(a), m, g))
        // a -> flatMap(g, f(a))
        // 5 -> flatMap(g, List("v5", "x5")) -> List("v5!", "v5?", "x5!", "x5?")
        // 6 -> flatMap(List("v6", "x6"), g) -> List("v6!", "v6?", "x6!", "x6?")
        Function<Integer, Kind<ListKind<?>, String>> rightSideFunc =
                a -> listMonad.flatMap(g, f.apply(a));
        // flatMap(List(5, 6), a -> ...) -> applies func to 5 and 6 and flattens
        Kind<ListKind<?>, String> rightSide = listMonad.flatMap(rightSideFunc, mValue);

        // Both sides should result in List("v5!", "v5?", "x5!", "x5?", "v6!", "v6?", "x6!", "x6?")
        assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));

        // Check empty case
        Kind<ListKind<?>, String> innerFlatMapEmpty = listMonad.flatMap(f, mValueEmpty); // []
        Kind<ListKind<?>, String> leftSideEmpty = listMonad.flatMap(g, innerFlatMapEmpty); // []
        Kind<ListKind<?>, String> rightSideEmpty = listMonad.flatMap(rightSideFunc, mValueEmpty); // []
        assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(rightSideEmpty));
      }
    }
  }


  @Nested
  @DisplayName("mapN tests")
  class MapNTests {

    Kind<ListKind<?>, Integer> list1 = wrap(Arrays.asList(1, 2));
    Kind<ListKind<?>, String> list2 = wrap(Arrays.asList("a", "b"));
    Kind<ListKind<?>, Double> list3 = wrap(Arrays.asList(1.0, 2.0));
    Kind<ListKind<?>, Boolean> list4 = wrap(Arrays.asList(true, false));

    Kind<ListKind<?>, Integer> emptyList = wrap(Collections.emptyList());


    @Test
    void map2_bothNonEmpty() {
      Function<Integer, Function<String, String>> f2 = i -> s -> i + s;
      Kind<ListKind<?>, String> result = listMonad.map2(list1, list2, f2);
      // Expected: Cartesian product applied: 1a, 1b, 2a, 2b
      assertThat(unwrap(result)).containsExactly("1a", "1b", "2a", "2b");
    }

    @Test
    void map2_firstEmpty() {
      Function<Integer, Function<String, String>> f2 = i -> s -> i + s;
      Kind<ListKind<?>, String> result = listMonad.map2(emptyList, list2, f2);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void map2_secondEmpty() {
      Function<String, Function<Integer, String>> f2 = i -> s -> i + s;
      Kind<ListKind<?>, String> result = listMonad.map2(list2, emptyList, f2);
      assertThat(unwrap(result)).isEmpty();
    }


    @Test
    void map2_biFunctionBothNonEmpty() {
      BiFunction<Integer, String, String> f2 = (i, s) -> i + s;
      Kind<ListKind<?>, String> result = listMonad.map2(list1, list2, f2);
      // Expected: Cartesian product applied: 1a, 1b, 2a, 2b
      assertThat(unwrap(result)).containsExactly("1a", "1b", "2a", "2b");
    }

    @Test
    void map2_biFunctionFirstEmpty() {
      BiFunction<Integer, String, String> f2 = (i, s) -> i + s;
      Kind<ListKind<?>, String> result = listMonad.map2(emptyList, list2, f2);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void map2_biFunctionSecondEmpty() {
      BiFunction<String, Integer, String> f2 = (i, s) -> i + s;
      Kind<ListKind<?>, String> result = listMonad.map2(list2, emptyList, f2);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void map3_allNonEmpty() {
      Function3<Integer, String, Double, String> f3 =
          (i, s, d) -> String.format("%d-%s-%.1f", i, s, d);
      Kind<ListKind<?>, String> result = listMonad.map3(list1, list2, list3, f3);
      // Expected: Cartesian product:
      // 1-a-1.0, 1-a-2.0, 1-b-1.0, 1-b-2.0,
      // 2-a-1.0, 2-a-2.0, 2-b-1.0, 2-b-2.0
      assertThat(unwrap(result)).containsExactly(
          "1-a-1.0", "1-a-2.0", "1-b-1.0", "1-b-2.0",
          "2-a-1.0", "2-a-2.0", "2-b-1.0", "2-b-2.0"
      );
    }

    @Test
    void map3_middleEmpty() {
      Function3<Integer, Integer, Double, String> f3 = (i, s, d) -> "Should not execute";
      Kind<ListKind<?>, String> result = listMonad.map3(list1, emptyList, list3, f3);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void map4_allNonEmpty() {
      Function4<Integer, String, Double, Boolean, String> f4 =
          (i, s, d, b) -> String.format("%d-%s-%.1f-%b", i, s, d, b);
      Kind<ListKind<?>, String> result = listMonad.map4(list1, list2, list3, list4, f4);
      // Expected: 2 * 2 * 2 * 2 = 16 combinations
      assertThat(unwrap(result)).hasSize(16);
      // Spot check a few
      assertThat(unwrap(result)).contains("1-a-1.0-true", "2-b-2.0-false");
    }

    @Test
    void map4_lastEmpty() {
      Function4<Integer, String, Double, Integer, String> f4 = (i, s, d, b) -> "Should not execute";
      Kind<ListKind<?>, String> result = listMonad.map4(list1, list2, list3, emptyList, f4);
      assertThat(unwrap(result)).isEmpty();
    }
  }
}