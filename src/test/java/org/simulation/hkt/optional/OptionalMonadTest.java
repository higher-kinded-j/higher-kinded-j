package org.simulation.hkt.optional;

import org.junit.jupiter.api.*;
import org.simulation.hkt.Kind;

import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.simulation.hkt.optional.OptionalKindHelper.unwrap;
import static org.simulation.hkt.optional.OptionalKindHelper.wrap;

class OptionalMonadTest {

  private static OptionalMonad optionalMonad;

  @BeforeAll
  static void setUp() {
    optionalMonad = new OptionalMonad();
  }
  

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldWrapValueInOptional() {
      Kind<OptionalKind<?>, String> kind = optionalMonad.of("test");
      assertThat(unwrap(kind)).isPresent().contains("test");
    }

    @Test
    void of_shouldWrapNullAsOptionalEmpty() {
      Kind<OptionalKind<?>, String> kind = optionalMonad.of(null);
      assertThat(unwrap(kind)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionWhenPresent() {
      Kind<OptionalKind<?>, Integer> input = wrap(Optional.of(5));
      Kind<OptionalKind<?>, String> result = optionalMonad.map(Object::toString, input);
      assertThat(unwrap(result)).isPresent().contains("5");
    }

    @Test
    void map_shouldReturnEmptyWhenEmpty() {
      Kind<OptionalKind<?>, Integer> input = wrap(Optional.empty());
      Kind<OptionalKind<?>, String> result = optionalMonad.map(Object::toString, input);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void map_shouldHandleMappingToNullAsEmpty() {
      Kind<OptionalKind<?>, Integer> input = wrap(Optional.of(5));
      Kind<OptionalKind<?>, String> result = optionalMonad.map(x -> (String)null, input);
      // Note: Optional.map handles mapping to null by returning Optional.empty
      assertThat(unwrap(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    Kind<OptionalKind<?>, Function<Integer, String>> funcKindPresent = optionalMonad.of(x -> "N" + x);
    Kind<OptionalKind<?>, Function<Integer, String>> funcKindEmpty = optionalMonad.of(null);
    Kind<OptionalKind<?>, Integer> valueKindPresent = optionalMonad.of(10);
    Kind<OptionalKind<?>, Integer> valueKindEmpty = optionalMonad.of(null);

    @Test
    void ap_shouldApplyPresentFunctionToPresentValue() {
      Kind<OptionalKind<?>, String> result = optionalMonad.ap(funcKindPresent, valueKindPresent);
      assertThat(unwrap(result)).isPresent().contains("N10");
    }

    @Test
    void ap_shouldReturnEmptyIfFunctionIsEmpty() {
      Kind<OptionalKind<?>, String> result = optionalMonad.ap(funcKindEmpty, valueKindPresent);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void ap_shouldReturnEmptyIfValueIsEmpty() {
      Kind<OptionalKind<?>, String> result = optionalMonad.ap(funcKindPresent, valueKindEmpty);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void ap_shouldReturnEmptyIfBothAreEmpty() {
      Kind<OptionalKind<?>, String> result = optionalMonad.ap(funcKindEmpty, valueKindEmpty);
      assertThat(unwrap(result)).isEmpty();
    }
  }


  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {

    // Function: Integer -> Kind<OptionalKind<?>, Double>
    Function<Integer, Kind<OptionalKind<?>, Double>> safeDivide =
        divisor -> wrap((divisor == 0) ? Optional.empty() : Optional.of(100.0 / divisor));


    @Test
    void flatMap_shouldApplyFunctionWhenPresent() {
      Kind<OptionalKind<?>, Integer> presentValue = optionalMonad.of(5);
      Kind<OptionalKind<?>, Double> result = optionalMonad.flatMap(safeDivide, presentValue);
      assertThat(unwrap(result)).isPresent().contains(20.0);
    }

    @Test
    void flatMap_shouldReturnEmptyWhenInputIsEmpty() {
      Kind<OptionalKind<?>, Integer> emptyValue = optionalMonad.of(null);
      Kind<OptionalKind<?>, Double> result = optionalMonad.flatMap(safeDivide, emptyValue);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void flatMap_shouldReturnEmptyWhenFunctionResultIsEmpty() {
      Kind<OptionalKind<?>, Integer> zeroValue = optionalMonad.of(0);
      Kind<OptionalKind<?>, Double> result = optionalMonad.flatMap(safeDivide, zeroValue);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void flatMap_chainingExample() {
      Kind<OptionalKind<?>, Integer> initial = optionalMonad.of(10);

      Kind<OptionalKind<?>, Double> finalResult = optionalMonad.flatMap(
          x -> { // x = 10
            Kind<OptionalKind<?>, Integer> intermediate = optionalMonad.of(x + 10); // Optional.of(20)
            return optionalMonad.flatMap(safeDivide, intermediate); // safeDivide(20) -> Optional.of(5.0)
          },
          initial
      );

      assertThat(unwrap(finalResult)).isPresent().contains(5.0);
    }

    @Test
    void flatMap_chainingWithEmptyPropagation() {
      Kind<OptionalKind<?>, Integer> initial = optionalMonad.of(5);

      Kind<OptionalKind<?>, Double> finalResult = optionalMonad.flatMap(
          x -> { // x = 5
            Kind<OptionalKind<?>, Integer> intermediate = optionalMonad.of(x - 5); // Optional.of(0)
            // safeDivide(0) returns empty Optional
            return optionalMonad.flatMap(safeDivide, intermediate);
          },
          initial
      );

      assertThat(unwrap(finalResult)).isEmpty();
    }
  }
}