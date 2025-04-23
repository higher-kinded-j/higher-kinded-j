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


  // Helper Functions for Laws
  private final Function<Integer, String> intToString = Object::toString;
  private final Function<String, String> appendWorld = s -> s + " world";
  private final Function<Integer, String> intToStringAppendWorld = intToString.andThen(appendWorld);

  // Function a -> M b (Integer -> MaybeKind<String>)
  private final Function<Integer, Kind<MaybeKind<?>, String>> f =
          i -> wrap(Maybe.just("v" + i));
  // Function b -> M c (String -> MaybeKind<String>)
  private final Function<String, Kind<MaybeKind<?>, String>> g =
          s -> wrap(Maybe.just(s + "!"));



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

  // --- Law Tests ---

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {
    @Test
    @DisplayName("1. Identity: map(id, fa) == fa")
    void identity() {
      Kind<MaybeKind<?>, Integer> fa = just(10);
      Kind<MaybeKind<?>, Integer> faNothing = nothing();

      assertThat(unwrap(maybeMonad.map(Function.identity(), fa)))
              .isEqualTo(unwrap(fa));
      assertThat(unwrap(maybeMonad.map(Function.identity(), faNothing)))
              .isEqualTo(unwrap(faNothing));
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      Kind<MaybeKind<?>, Integer> fa = just(10);
      Kind<MaybeKind<?>, Integer> faNothing = nothing();

      Kind<MaybeKind<?>, String> leftSide = maybeMonad.map(intToStringAppendWorld, fa);
      Kind<MaybeKind<?>, String> rightSide = maybeMonad.map(appendWorld, maybeMonad.map(intToString, fa));

      Kind<MaybeKind<?>, String> leftSideNothing = maybeMonad.map(intToStringAppendWorld, faNothing);
      Kind<MaybeKind<?>, String> rightSideNothing = maybeMonad.map(appendWorld, maybeMonad.map(intToString, faNothing));

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
      assertThat(unwrap(leftSideNothing)).isEqualTo(unwrap(rightSideNothing));
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {

    Kind<MaybeKind<?>, Integer> v = just(5);
    Kind<MaybeKind<?>, Integer> vNothing = nothing();
    Kind<MaybeKind<?>, Function<Integer, String>> fKind = just(intToString);
    Kind<MaybeKind<?>, Function<Integer, String>> fKindNothing = nothing();
    Kind<MaybeKind<?>, Function<String, String>> gKind = just(appendWorld);
    Kind<MaybeKind<?>, Function<String, String>> gKindNothing = nothing();


    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      Kind<MaybeKind<?>, Function<Integer, Integer>> idFuncKind = maybeMonad.of(Function.identity());
      assertThat(unwrap(maybeMonad.ap(idFuncKind, v))).isEqualTo(unwrap(v));
      assertThat(unwrap(maybeMonad.ap(idFuncKind, vNothing))).isEqualTo(unwrap(vNothing));
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      int x = 10;
      Function<Integer, String> f = intToString;
      Kind<MaybeKind<?>, Function<Integer, String>> apFunc = maybeMonad.of(f);
      Kind<MaybeKind<?>, Integer> apVal = maybeMonad.of(x);

      Kind<MaybeKind<?>, String> leftSide = maybeMonad.ap(apFunc, apVal);
      Kind<MaybeKind<?>, String> rightSide = maybeMonad.of(f.apply(x));

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 20;
      // Left Side: ap(fKind, of(y))
      Kind<MaybeKind<?>, String> leftSide = maybeMonad.ap(fKind, maybeMonad.of(y));
      Kind<MaybeKind<?>, String> leftSideNothing = maybeMonad.ap(fKindNothing, maybeMonad.of(y)); // Should be Nothing

      // Right Side: ap(of(f -> f(y)), fKind)
      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      Kind<MaybeKind<?>, Function<Function<Integer, String>, String>> evalKind = maybeMonad.of(evalWithY);

      Kind<MaybeKind<?>, String> rightSide = maybeMonad.ap(evalKind, fKind);
      Kind<MaybeKind<?>, String> rightSideNothing = maybeMonad.ap(evalKind, fKindNothing); // Should be Nothing

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
      assertThat(unwrap(leftSideNothing)).isEqualTo(unwrap(rightSideNothing));
    }

    @Test
    @DisplayName("4. Composition: ap(ap(map(compose, gKind), fKind), v) == ap(gKind, ap(fKind, v)) - Adjusted")
    void composition() {
      Function<Function<String, String>, Function<Function<Integer, String>, Function<Integer, String>>> composeMap =
              g -> f -> g.compose(f);

      // Left side: ap(ap(map(composeMap, gKind), fKind), v)
      Kind<MaybeKind<?>, Function<Function<Integer, String>, Function<Integer, String>>> mappedCompose =
              maybeMonad.map(composeMap, gKind);
      Kind<MaybeKind<?>, Function<Integer, String>> ap1 =
              maybeMonad.ap(mappedCompose, fKind);
      Kind<MaybeKind<?>, String> leftSide = maybeMonad.ap(ap1, v);

      // Right side: ap(gKind, ap(fKind, v))
      Kind<MaybeKind<?>, String> innerAp = maybeMonad.ap(fKind, v);
      Kind<MaybeKind<?>, String> rightSide = maybeMonad.ap(gKind, innerAp);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));

      // Test with Nothing propagation
      assertThat(unwrap(maybeMonad.ap(maybeMonad.ap(maybeMonad.map(composeMap, gKindNothing), fKind), v)).isNothing()).isTrue();
      assertThat(unwrap(maybeMonad.ap(gKindNothing, maybeMonad.ap(fKind, v))).isNothing()).isTrue();
      assertThat(unwrap(maybeMonad.ap(maybeMonad.ap(maybeMonad.map(composeMap, gKind), fKindNothing), v)).isNothing()).isTrue();
      assertThat(unwrap(maybeMonad.ap(gKind, maybeMonad.ap(fKindNothing, v))).isNothing()).isTrue();
      assertThat(unwrap(maybeMonad.ap(maybeMonad.ap(maybeMonad.map(composeMap, gKind), fKind), vNothing)).isNothing()).isTrue();
      assertThat(unwrap(maybeMonad.ap(gKind, maybeMonad.ap(fKind, vNothing))).isNothing()).isTrue();
    }
  }


  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {

    int value = 5;
    Kind<MaybeKind<?>, Integer> mValue = just(value);
    Kind<MaybeKind<?>, Integer> mValueNothing = nothing();


    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      Kind<MaybeKind<?>, Integer> ofValue = maybeMonad.of(value);
      Kind<MaybeKind<?>, String> leftSide = maybeMonad.flatMap(f, ofValue);
      Kind<MaybeKind<?>, String> rightSide = f.apply(value);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<MaybeKind<?>, Integer>> ofFunc = i -> maybeMonad.of(i);

      Kind<MaybeKind<?>, Integer> leftSide = maybeMonad.flatMap(ofFunc, mValue);
      Kind<MaybeKind<?>, Integer> leftSideNothing = maybeMonad.flatMap(ofFunc, mValueNothing);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(mValue));
      assertThat(unwrap(leftSideNothing)).isEqualTo(unwrap(mValueNothing));
    }


    @Test
    @DisplayName("3. Associativity: flatMap(g, flatMap(m, f)) == flatMap(a -> flatMap(f(a), g), m)")
    void associativity() {
      // Left Side: flatMap(flatMap(m, f), g)
      Kind<MaybeKind<?>, String> innerFlatMap = maybeMonad.flatMap(f, mValue);
      Kind<MaybeKind<?>, String> leftSide = maybeMonad.flatMap(g, innerFlatMap);

      // Right Side: flatMap(a -> flatMap(g, f(a)), m)
      Function<Integer, Kind<MaybeKind<?>, String>> rightSideFunc =
              a -> maybeMonad.flatMap(g, f.apply(a));
      Kind<MaybeKind<?>, String> rightSide = maybeMonad.flatMap(rightSideFunc, mValue);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));

      // Check Nothing propagation
      Kind<MaybeKind<?>, String> innerFlatMapNothing = maybeMonad.flatMap(f, mValueNothing); // Nothing
      Kind<MaybeKind<?>, String> leftSideNothing = maybeMonad.flatMap(g, innerFlatMapNothing); // Nothing
      Kind<MaybeKind<?>, String> rightSideNothing = maybeMonad.flatMap(rightSideFunc, mValueNothing); // Nothing
      assertThat(unwrap(leftSideNothing)).isEqualTo(unwrap(rightSideNothing));
    }
  }

  @Nested
  @DisplayName("MonadError tests")
  class MonadErrorTests {

    Kind<MaybeKind<?>, Integer> justVal = just(100);
    Kind<MaybeKind<?>, Integer> nothingVal = nothing();
    // Error type is Void, so just use null for the error value
    Kind<MaybeKind<?>, Integer> raisedErrorKind = maybeMonad.raiseError(null);


    @Test
    void raiseError_shouldCreateNothing() {
      assertThat(unwrap(raisedErrorKind).isNothing()).isTrue();
    }

    @Test
    void handleErrorWith_shouldHandleNothing() {
      // Handler recovers Nothing with Just(0)
      Function<Void, Kind<MaybeKind<?>, Integer>> handler = err -> just(0);

      Kind<MaybeKind<?>, Integer> result = maybeMonad.handleErrorWith(nothingVal, handler);

      assertThat(unwrap(result).isJust()).isTrue();
      assertThat(unwrap(result).get()).isEqualTo(0);
    }

    @Test
    void handleErrorWith_shouldHandleRaisedError() {
      // Handler recovers Nothing with Just(0)
      Function<Void, Kind<MaybeKind<?>, Integer>> handler = err -> just(0);

      Kind<MaybeKind<?>, Integer> result = maybeMonad.handleErrorWith(raisedErrorKind, handler);

      assertThat(unwrap(result).isJust()).isTrue();
      assertThat(unwrap(result).get()).isEqualTo(0);
    }

    @Test
    void handleErrorWith_shouldIgnoreJust() {
      Function<Void, Kind<MaybeKind<?>, Integer>> handler = err -> just(-1); // Should not be called

      Kind<MaybeKind<?>, Integer> result = maybeMonad.handleErrorWith(justVal, handler);

      assertThat(result).isSameAs(justVal); // Should return original Kind instance
      assertThat(unwrap(result).isJust()).isTrue();
      assertThat(unwrap(result).get()).isEqualTo(100);
    }

    @Test
    void handleError_shouldHandleNothingWithPureValue() {
      Function<Void, Integer> handler = err -> -99;

      Kind<MaybeKind<?>, Integer> result = maybeMonad.handleError(nothingVal, handler);

      assertThat(unwrap(result).isJust()).isTrue();
      assertThat(unwrap(result).get()).isEqualTo(-99);
    }

    @Test
    void handleError_shouldIgnoreJust() {
      Function<Void, Integer> handler = err -> -1; // Should not be called

      Kind<MaybeKind<?>, Integer> result = maybeMonad.handleError(justVal, handler);

      assertThat(result).isSameAs(justVal); // Should return original Kind instance
      assertThat(unwrap(result).isJust()).isTrue();
      assertThat(unwrap(result).get()).isEqualTo(100);
    }

    @Test
    void recoverWith_shouldReplaceNothingWithFallbackKind() {
      Kind<MaybeKind<?>, Integer> fallback = just(0);
      Kind<MaybeKind<?>, Integer> result = maybeMonad.recoverWith(nothingVal, fallback);
      assertThat(result).isSameAs(fallback);
    }

    @Test
    void recoverWith_shouldIgnoreJust() {
      Kind<MaybeKind<?>, Integer> fallback = just(0);
      Kind<MaybeKind<?>, Integer> result = maybeMonad.recoverWith(justVal, fallback);
      assertThat(result).isSameAs(justVal);
    }

    @Test
    void recover_shouldReplaceNothingWithOfValue() {
      Kind<MaybeKind<?>, Integer> result = maybeMonad.recover(nothingVal, 0);
      assertThat(unwrap(result).isJust()).isTrue();
      assertThat(unwrap(result).get()).isEqualTo(0);
    }

    @Test
    void recover_shouldIgnoreJust() {
      Kind<MaybeKind<?>, Integer> result = maybeMonad.recover(justVal, 0);
      assertThat(result).isSameAs(justVal);
    }
  }
}