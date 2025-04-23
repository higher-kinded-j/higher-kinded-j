package org.simulation.hkt.optional;

import org.junit.jupiter.api.*;
import org.simulation.hkt.Kind;

import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.simulation.hkt.optional.OptionalKindHelper.unwrap;
import static org.simulation.hkt.optional.OptionalKindHelper.wrap;

class OptionalMonadTest {

  private final OptionalMonad optionalMonad = new OptionalMonad();

  // Helper Functions for Laws
  private final Function<Integer, String> intToString = Object::toString;
  private final Function<String, String> appendWorld = s -> s + " world";
  private final Function<Integer, String> intToStringAppendWorld = intToString.andThen(appendWorld);

  // Function a -> M b (Integer -> OptionalKind<String>)
  private final Function<Integer, Kind<OptionalKind<?>, String>> f =
          i -> wrap(Optional.of("v" + i));
  // Function b -> M c (String -> OptionalKind<String>)
  private final Function<String, Kind<OptionalKind<?>, String>> g =
          s -> wrap(Optional.of(s + "!"));

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

  // --- Law Tests ---

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {
    @Test
    @DisplayName("1. Identity: map(id, fa) == fa")
    void identity() {
      Kind<OptionalKind<?>, Integer> fa = optionalMonad.of(10);
      Kind<OptionalKind<?>, Integer> faEmpty = optionalMonad.of(null);

      assertThat(unwrap(optionalMonad.map(Function.identity(), fa)))
              .isEqualTo(unwrap(fa));
      assertThat(unwrap(optionalMonad.map(Function.identity(), faEmpty)))
              .isEqualTo(unwrap(faEmpty));
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      Kind<OptionalKind<?>, Integer> fa = optionalMonad.of(10);
      Kind<OptionalKind<?>, Integer> faEmpty = optionalMonad.of(null);

      Kind<OptionalKind<?>, String> leftSide = optionalMonad.map(intToStringAppendWorld, fa);
      Kind<OptionalKind<?>, String> rightSide = optionalMonad.map(appendWorld, optionalMonad.map(intToString, fa));

      Kind<OptionalKind<?>, String> leftSideEmpty = optionalMonad.map(intToStringAppendWorld, faEmpty);
      Kind<OptionalKind<?>, String> rightSideEmpty = optionalMonad.map(appendWorld, optionalMonad.map(intToString, faEmpty));

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
      assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(rightSideEmpty));
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {

    Kind<OptionalKind<?>, Integer> v = optionalMonad.of(5);
    Kind<OptionalKind<?>, Integer> vEmpty = optionalMonad.of(null);
    Kind<OptionalKind<?>, Function<Integer, String>> fKind = optionalMonad.of(intToString);
    Kind<OptionalKind<?>, Function<Integer, String>> fKindEmpty = optionalMonad.of(null);
    Kind<OptionalKind<?>, Function<String, String>> gKind = optionalMonad.of(appendWorld);
    Kind<OptionalKind<?>, Function<String, String>> gKindEmpty = optionalMonad.of(null);

    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      Kind<OptionalKind<?>, Function<Integer, Integer>> idFuncKind = optionalMonad.of(Function.identity());
      assertThat(unwrap(optionalMonad.ap(idFuncKind, v))).isEqualTo(unwrap(v));
      assertThat(unwrap(optionalMonad.ap(idFuncKind, vEmpty))).isEqualTo(unwrap(vEmpty));
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      int x = 10;
      Function<Integer, String> f = intToString;
      Kind<OptionalKind<?>, Function<Integer, String>> apFunc = optionalMonad.of(f);
      Kind<OptionalKind<?>, Integer> apVal = optionalMonad.of(x);

      Kind<OptionalKind<?>, String> leftSide = optionalMonad.ap(apFunc, apVal);
      Kind<OptionalKind<?>, String> rightSide = optionalMonad.of(f.apply(x));

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 20;
      // Left Side: ap(fKind, of(y))
      Kind<OptionalKind<?>, String> leftSide = optionalMonad.ap(fKind, optionalMonad.of(y));
      Kind<OptionalKind<?>, String> leftSideEmpty = optionalMonad.ap(fKindEmpty, optionalMonad.of(y)); // Should be empty

      // Right Side: ap(of(f -> f(y)), fKind)
      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      Kind<OptionalKind<?>, Function<Function<Integer, String>, String>> evalKind = optionalMonad.of(evalWithY);

      Kind<OptionalKind<?>, String> rightSide = optionalMonad.ap(evalKind, fKind);
      Kind<OptionalKind<?>, String> rightSideEmpty = optionalMonad.ap(evalKind, fKindEmpty); // Should be empty

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
      assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(rightSideEmpty));
    }

    @Test
    @DisplayName("4. Composition: ap(ap(map(compose, gKind), fKind), v) == ap(gKind, ap(fKind, v)) - Adjusted")
    void composition() {
      Function<Function<String, String>, Function<Function<Integer, String>, Function<Integer, String>>> composeMap =
              g -> f -> g.compose(f);

      // Left side: ap(ap(map(composeMap, gKind), fKind), v)
      Kind<OptionalKind<?>, Function<Function<Integer, String>, Function<Integer, String>>> mappedCompose =
              optionalMonad.map(composeMap, gKind);
      Kind<OptionalKind<?>, Function<Integer, String>> ap1 =
              optionalMonad.ap(mappedCompose, fKind);
      Kind<OptionalKind<?>, String> leftSide = optionalMonad.ap(ap1, v);

      // Right side: ap(gKind, ap(fKind, v))
      Kind<OptionalKind<?>, String> innerAp = optionalMonad.ap(fKind, v);
      Kind<OptionalKind<?>, String> rightSide = optionalMonad.ap(gKind, innerAp);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));

      // Test with empty propagation
      assertThat(unwrap(optionalMonad.ap(optionalMonad.ap(optionalMonad.map(composeMap, gKindEmpty), fKind), v))).isEmpty();
      assertThat(unwrap(optionalMonad.ap(gKindEmpty, optionalMonad.ap(fKind, v)))).isEmpty();
      assertThat(unwrap(optionalMonad.ap(optionalMonad.ap(optionalMonad.map(composeMap, gKind), fKindEmpty), v))).isEmpty();
      assertThat(unwrap(optionalMonad.ap(gKind, optionalMonad.ap(fKindEmpty, v)))).isEmpty();
      assertThat(unwrap(optionalMonad.ap(optionalMonad.ap(optionalMonad.map(composeMap, gKind), fKind), vEmpty))).isEmpty();
      assertThat(unwrap(optionalMonad.ap(gKind, optionalMonad.ap(fKind, vEmpty)))).isEmpty();
    }
  }


  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {

    int value = 5;
    Kind<OptionalKind<?>, Integer> mValue = optionalMonad.of(value);
    Kind<OptionalKind<?>, Integer> mValueEmpty = optionalMonad.of(null);

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      Kind<OptionalKind<?>, Integer> ofValue = optionalMonad.of(value);
      Kind<OptionalKind<?>, String> leftSide = optionalMonad.flatMap(f, ofValue);
      Kind<OptionalKind<?>, String> rightSide = f.apply(value);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<OptionalKind<?>, Integer>> ofFunc = i -> optionalMonad.of(i);

      Kind<OptionalKind<?>, Integer> leftSide = optionalMonad.flatMap(ofFunc, mValue);
      Kind<OptionalKind<?>, Integer> leftSideEmpty = optionalMonad.flatMap(ofFunc, mValueEmpty);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(mValue));
      assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(mValueEmpty));
    }


    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      // Left Side: flatMap(flatMap(m, f), g)
      Kind<OptionalKind<?>, String> innerFlatMap = optionalMonad.flatMap(f, mValue);
      Kind<OptionalKind<?>, String> leftSide = optionalMonad.flatMap(g, innerFlatMap);

      // Right Side: flatMap(m, a -> flatMap(f(a), g))
      Function<Integer, Kind<OptionalKind<?>, String>> rightSideFunc =
              a -> optionalMonad.flatMap(g, f.apply(a));
      Kind<OptionalKind<?>, String> rightSide = optionalMonad.flatMap(rightSideFunc, mValue);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));

      // Check empty propagation
      Kind<OptionalKind<?>, String> innerFlatMapEmpty = optionalMonad.flatMap(f, mValueEmpty); // Empty
      Kind<OptionalKind<?>, String> leftSideEmpty = optionalMonad.flatMap(g, innerFlatMapEmpty); // Empty
      Kind<OptionalKind<?>, String> rightSideEmpty = optionalMonad.flatMap(rightSideFunc, mValueEmpty); // Empty
      assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(rightSideEmpty));
    }
  }

  @Nested
  @DisplayName("MonadError tests")
  class MonadErrorTests {

    Kind<OptionalKind<?>, Integer> presentVal = optionalMonad.of(100);
    Kind<OptionalKind<?>, Integer> emptyVal = optionalMonad.of(null); // of(null) -> empty
    // Error type is Void, so just use null for the error value
    Kind<OptionalKind<?>, Integer> raisedErrorKind = optionalMonad.raiseError(null);

    @Test
    void raiseError_shouldCreateEmpty() {
      assertThat(unwrap(raisedErrorKind)).isEmpty();
    }

    @Test
    void handleErrorWith_shouldHandleEmpty() {
      // Handler recovers empty with Optional.of(0)
      Function<Void, Kind<OptionalKind<?>, Integer>> handler = err -> optionalMonad.of(0);

      Kind<OptionalKind<?>, Integer> result = optionalMonad.handleErrorWith(emptyVal, handler);

      assertThat(unwrap(result)).isPresent().contains(0);
    }

    @Test
    void handleErrorWith_shouldHandleRaisedError() {
      // Handler recovers empty with Optional.of(0)
      Function<Void, Kind<OptionalKind<?>, Integer>> handler = err -> optionalMonad.of(0);

      Kind<OptionalKind<?>, Integer> result = optionalMonad.handleErrorWith(raisedErrorKind, handler);

      assertThat(unwrap(result)).isPresent().contains(0);
    }

    @Test
    void handleErrorWith_shouldIgnorePresent() {
      Function<Void, Kind<OptionalKind<?>, Integer>> handler = err -> optionalMonad.of(-1); // Should not be called

      Kind<OptionalKind<?>, Integer> result = optionalMonad.handleErrorWith(presentVal, handler);

      assertThat(result).isSameAs(presentVal); // Should return original Kind instance
      assertThat(unwrap(result)).isPresent().contains(100);
    }

    @Test
    void handleError_shouldHandleEmptyWithPureValue() {
      Function<Void, Integer> handler = err -> -99;

      Kind<OptionalKind<?>, Integer> result = optionalMonad.handleError(emptyVal, handler);

      assertThat(unwrap(result)).isPresent().contains(-99);
    }

    @Test
    void handleError_shouldIgnorePresent() {
      Function<Void, Integer> handler = err -> -1; // Should not be called

      Kind<OptionalKind<?>, Integer> result = optionalMonad.handleError(presentVal, handler);

      assertThat(result).isSameAs(presentVal); // Should return original Kind instance
      assertThat(unwrap(result)).isPresent().contains(100);
    }

    @Test
    void recoverWith_shouldReplaceEmptyWithFallbackKind() {
      Kind<OptionalKind<?>, Integer> fallback = optionalMonad.of(0);
      Kind<OptionalKind<?>, Integer> result = optionalMonad.recoverWith(emptyVal, fallback);
      assertThat(result).isSameAs(fallback);
    }

    @Test
    void recoverWith_shouldIgnorePresent() {
      Kind<OptionalKind<?>, Integer> fallback = optionalMonad.of(0);
      Kind<OptionalKind<?>, Integer> result = optionalMonad.recoverWith(presentVal, fallback);
      assertThat(result).isSameAs(presentVal);
    }

    @Test
    void recover_shouldReplaceEmptyWithOfValue() {
      Kind<OptionalKind<?>, Integer> result = optionalMonad.recover(emptyVal, 0);
      assertThat(unwrap(result)).isPresent().contains(0);
    }

    @Test
    void recover_shouldIgnorePresent() {
      Kind<OptionalKind<?>, Integer> result = optionalMonad.recover(presentVal, 0);
      assertThat(result).isSameAs(presentVal);
    }
  }
}