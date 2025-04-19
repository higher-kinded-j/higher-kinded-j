package org.simulation.hkt.maybe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;


import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.simulation.hkt.maybe.MaybeKindHelper.*;


class MaybeMonadTest {

  private final MaybeMonad maybeMonad = new MaybeMonad();


  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldWrapValueInJust() {
      Kind<MaybeKind<?>, String> kind = maybeMonad.of("test");
      Maybe<String> maybe = unwrap(kind);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("test");
    }

    @Test
    void of_shouldWrapNullAsNothing() {
      Kind<MaybeKind<?>, String> kind = maybeMonad.of(null);
      assertThat(unwrap(kind).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionWhenJust() {
      Kind<MaybeKind<?>, Integer> input = just(5);
      Kind<MaybeKind<?>, String> result = maybeMonad.map(Object::toString, input);
      Maybe<String> maybe = unwrap(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("5");
    }

    @Test
    void map_shouldReturnNothingWhenNothing() {
      Kind<MaybeKind<?>, Integer> input = nothing();
      Kind<MaybeKind<?>, String> result = maybeMonad.map(Object::toString, input);
      assertThat(unwrap(result).isNothing()).isTrue();
    }

    @Test
    void map_shouldHandleMappingToNullAsNothing() {
      Kind<MaybeKind<?>, Integer> input = just(5);
      Kind<MaybeKind<?>, String> result = maybeMonad.map(x -> (String)null, input);
      // Maybe.map uses fromNullable, so null result becomes Nothing
      assertThat(unwrap(result).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    Kind<MaybeKind<?>, Function<Integer, String>> funcKindJust = maybeMonad.of(x -> "N" + x);
    Kind<MaybeKind<?>, Function<Integer, String>> funcKindNothing = maybeMonad.of(null);
    Kind<MaybeKind<?>, Integer> valueKindJust = maybeMonad.of(10);
    Kind<MaybeKind<?>, Integer> valueKindNothing = maybeMonad.of(null);

    @Test
    void ap_shouldApplyJustFunctionToJustValue() {
      Kind<MaybeKind<?>, String> result = maybeMonad.ap(funcKindJust, valueKindJust);
      Maybe<String> maybe = unwrap(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("N10");
    }

    @Test
    void ap_shouldReturnNothingIfFunctionIsNothing() {
      Kind<MaybeKind<?>, String> result = maybeMonad.ap(funcKindNothing, valueKindJust);
      assertThat(unwrap(result).isNothing()).isTrue();
    }

    @Test
    void ap_shouldReturnNothingIfValueIsNothing() {
      Kind<MaybeKind<?>, String> result = maybeMonad.ap(funcKindJust, valueKindNothing);
      assertThat(unwrap(result).isNothing()).isTrue();
    }

    @Test
    void ap_shouldReturnNothingIfBothAreNothing() {
      Kind<MaybeKind<?>, String> result = maybeMonad.ap(funcKindNothing, valueKindNothing);
      assertThat(unwrap(result).isNothing()).isTrue();
    }
  }


  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {

    // Function: Integer -> Kind<MaybeKind<?>, Double>
    Function<Integer, Kind<MaybeKind<?>, Double>> safeInvert =
        num -> wrap((num == 0) ? Maybe.nothing() : Maybe.just(1.0 / num));

    Kind<MaybeKind<?>, Integer> justValue = just(10);
    Kind<MaybeKind<?>, Integer> zeroValue = just(0);
    Kind<MaybeKind<?>, Integer> nothingValue = nothing();

    @Test
    void flatMap_shouldApplyFunctionWhenJust() {
      Kind<MaybeKind<?>, Double> result = maybeMonad.flatMap(safeInvert, justValue);
      Maybe<Double> maybe = unwrap(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(0.1);
    }

    @Test
    void flatMap_shouldReturnNothingWhenInputIsNothing() {
      Kind<MaybeKind<?>, Double> result = maybeMonad.flatMap(safeInvert, nothingValue);
      assertThat(unwrap(result).isNothing()).isTrue();
    }

    @Test
    void flatMap_shouldReturnNothingWhenFunctionResultIsNothing() {
      Kind<MaybeKind<?>, Double> result = maybeMonad.flatMap(safeInvert, zeroValue);
      assertThat(unwrap(result).isNothing()).isTrue();
    }

    @Test
    void flatMap_chainingExample() {
      Kind<MaybeKind<?>, Integer> initial = just(4);

      Kind<MaybeKind<?>, Double> finalResult = maybeMonad.flatMap(
          x -> { // x = 4
            Kind<MaybeKind<?>, Integer> intermediate = just(x * 5); // Just(20)
            return maybeMonad.flatMap(safeInvert, intermediate); // safeInvert(20) -> Just(0.05)
          },
          initial
      );

      Maybe<Double> maybe = unwrap(finalResult);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(0.05);
    }

    @Test
    void flatMap_chainingWithNothingPropagation() {
      Kind<MaybeKind<?>, Integer> initial = just(5);

      Kind<MaybeKind<?>, Double> finalResult = maybeMonad.flatMap(
          x -> { // x = 5
            Kind<MaybeKind<?>, Integer> intermediate = just(x - 5); // Just(0)
            // safeInvert(0) returns Nothing
            return maybeMonad.flatMap(safeInvert, intermediate);
          },
          initial
      );

      assertThat(unwrap(finalResult).isNothing()).isTrue();
    }
  }
}