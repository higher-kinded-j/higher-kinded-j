package org.simulation.hkt.list;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.simulation.hkt.list.ListKindHelper.unwrap;
import static org.simulation.hkt.list.ListKindHelper.wrap;

class ListMonadTest {

  private ListMonad listMonad;

  @BeforeEach
  void setUp() {
    listMonad = new ListMonad();
  }



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
  }
}