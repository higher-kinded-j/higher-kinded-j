// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.*; // unwrap, just, nothing

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MaybeMonadTest {

  private final MaybeMonad maybeMonad = new MaybeMonad();

  // Helper Functions for Laws
  private final Function<Integer, String> intToString = Object::toString;
  private final Function<String, String> appendWorld = s -> s + " world";
  private final Function<Integer, String> intToStringAppendWorld = intToString.andThen(appendWorld);

  // Function a -> M b (Integer -> MaybeKind<String>)
  // wrap returns MaybeKind<String>, which is Kind<MaybeKind.Witness, String>
  private final Function<Integer, Kind<MaybeKind.Witness, String>> f =
      i -> wrap(Maybe.just("v" + i));
  // Function b -> M c (String -> MaybeKind<String>)
  private final Function<String, Kind<MaybeKind.Witness, String>> g =
      s -> wrap(Maybe.just(s + "!"));

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldWrapValueInJust() {
      // maybeMonad.of returns MaybeKind<String>
      MaybeKind<String> kind = maybeMonad.of("test");
      Maybe<String> maybe = unwrap(kind); // unwrap takes Kind<MaybeKind.Witness, A>
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("test");
    }

    @Test
    void of_shouldWrapNullAsNothing() {
      MaybeKind<String> kind = maybeMonad.of(null);
      assertThat(unwrap(kind).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionWhenJust() {
      MaybeKind<Integer> input = just(5); // MaybeKindHelper.just returns MaybeKind<A>
      // maybeMonad.map takes Kind<MaybeKind.Witness, A> and returns MaybeKind<B>
      MaybeKind<String> result = maybeMonad.map(Object::toString, input);
      Maybe<String> maybe = unwrap(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("5");
    }

    @Test
    void map_shouldReturnNothingWhenNothing() {
      MaybeKind<Integer> input = nothing(); // MaybeKindHelper.nothing returns MaybeKind<A>
      MaybeKind<String> result = maybeMonad.map(Object::toString, input);
      assertThat(unwrap(result).isNothing()).isTrue();
    }

    @Test
    void map_shouldHandleMappingToNullAsNothing() {
      MaybeKind<Integer> input = just(5);
      MaybeKind<String> result = maybeMonad.map(x -> null, input);
      assertThat(unwrap(result).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    MaybeKind<Function<Integer, String>> funcKindJust = maybeMonad.of(x -> "N" + x);
    MaybeKind<Function<Integer, String>> funcKindNothing =
        nothing(); // Use nothing() from helper which returns MaybeKind
    MaybeKind<Integer> valueKindJust = maybeMonad.of(10);
    MaybeKind<Integer> valueKindNothing = nothing(); // Use nothing()

    @Test
    void ap_shouldApplyJustFunctionToJustValue() {
      MaybeKind<String> result = maybeMonad.ap(funcKindJust, valueKindJust);
      Maybe<String> maybe = unwrap(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("N10");
    }

    @Test
    void ap_shouldReturnNothingIfFunctionIsNothing() {
      MaybeKind<String> result = maybeMonad.ap(funcKindNothing, valueKindJust);
      assertThat(unwrap(result).isNothing()).isTrue();
    }

    @Test
    void ap_shouldReturnNothingIfValueIsNothing() {
      MaybeKind<String> result = maybeMonad.ap(funcKindJust, valueKindNothing);
      assertThat(unwrap(result).isNothing()).isTrue();
    }

    @Test
    void ap_shouldReturnNothingIfBothAreNothing() {
      MaybeKind<String> result = maybeMonad.ap(funcKindNothing, valueKindNothing);
      assertThat(unwrap(result).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {

    // Function: Integer -> Kind<MaybeKind.Witness, Double> (or MaybeKind<Double>)
    Function<Integer, Kind<MaybeKind.Witness, Double>> safeInvert =
        num -> wrap((num == 0) ? Maybe.nothing() : Maybe.just(1.0 / num));

    MaybeKind<Integer> justValue = just(10);
    MaybeKind<Integer> zeroValue = just(0);
    MaybeKind<Integer> nothingValue = nothing();

    @Test
    void flatMap_shouldApplyFunctionWhenJust() {
      MaybeKind<Double> result = maybeMonad.flatMap(safeInvert, justValue);
      Maybe<Double> maybe = unwrap(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(0.1);
    }

    @Test
    void flatMap_shouldReturnNothingWhenInputIsNothing() {
      MaybeKind<Double> result = maybeMonad.flatMap(safeInvert, nothingValue);
      assertThat(unwrap(result).isNothing()).isTrue();
    }

    @Test
    void flatMap_shouldReturnNothingWhenFunctionResultIsNothing() {
      MaybeKind<Double> result = maybeMonad.flatMap(safeInvert, zeroValue);
      assertThat(unwrap(result).isNothing()).isTrue();
    }

    @Test
    void flatMap_chainingExample() {
      MaybeKind<Integer> initial = just(4);

      // flatMap takes Function<A, Kind<MaybeKind.Witness, B>>
      // safeInvert returns MaybeKind<Double> which is Kind<MaybeKind.Witness, Double>
      MaybeKind<Double> finalResult =
          maybeMonad.flatMap(
              x -> {
                MaybeKind<Integer> intermediate = just(x * 5);
                return maybeMonad.flatMap(safeInvert, intermediate);
              },
              initial);

      Maybe<Double> maybe = unwrap(finalResult);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(0.05);
    }

    @Test
    void flatMap_chainingWithNothingPropagation() {
      MaybeKind<Integer> initial = just(5);

      MaybeKind<Double> finalResult =
          maybeMonad.flatMap(
              x -> {
                MaybeKind<Integer> intermediate = just(x - 5);
                return maybeMonad.flatMap(safeInvert, intermediate);
              },
              initial);

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
      MaybeKind<Integer> fa = just(10);
      MaybeKind<Integer> faNothing = nothing();

      assertThat(unwrap(maybeMonad.map(Function.identity(), fa))).isEqualTo(unwrap(fa));
      assertThat(unwrap(maybeMonad.map(Function.identity(), faNothing)))
          .isEqualTo(unwrap(faNothing));
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      MaybeKind<Integer> fa = just(10);
      MaybeKind<Integer> faNothing = nothing();

      MaybeKind<String> leftSide = maybeMonad.map(intToStringAppendWorld, fa);
      MaybeKind<String> rightSide = maybeMonad.map(appendWorld, maybeMonad.map(intToString, fa));

      MaybeKind<String> leftSideNothing = maybeMonad.map(intToStringAppendWorld, faNothing);
      MaybeKind<String> rightSideNothing =
          maybeMonad.map(appendWorld, maybeMonad.map(intToString, faNothing));

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
      assertThat(unwrap(leftSideNothing)).isEqualTo(unwrap(rightSideNothing));
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {

    MaybeKind<Integer> v = just(5);
    MaybeKind<Integer> vNothing = nothing();
    MaybeKind<Function<Integer, String>> fKind = just(intToString);
    MaybeKind<Function<Integer, String>> fKindNothing = nothing();
    MaybeKind<Function<String, String>> gKind = just(appendWorld);
    MaybeKind<Function<String, String>> gKindNothing = nothing();

    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      MaybeKind<Function<Integer, Integer>> idFuncKind = maybeMonad.of(Function.identity());
      assertThat(unwrap(maybeMonad.ap(idFuncKind, v))).isEqualTo(unwrap(v));
      assertThat(unwrap(maybeMonad.ap(idFuncKind, vNothing))).isEqualTo(unwrap(vNothing));
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      int x = 10;
      Function<Integer, String> fMapFunc = intToString; // Changed variable name to avoid conflict
      MaybeKind<Function<Integer, String>> apFunc = maybeMonad.of(fMapFunc);
      MaybeKind<Integer> apVal = maybeMonad.of(x);

      MaybeKind<String> leftSide = maybeMonad.ap(apFunc, apVal);
      MaybeKind<String> rightSide = maybeMonad.of(fMapFunc.apply(x));

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 20;
      MaybeKind<String> leftSide = maybeMonad.ap(fKind, maybeMonad.of(y));
      MaybeKind<String> leftSideNothing = maybeMonad.ap(fKindNothing, maybeMonad.of(y));

      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      MaybeKind<Function<Function<Integer, String>, String>> evalKind = maybeMonad.of(evalWithY);

      MaybeKind<String> rightSide = maybeMonad.ap(evalKind, fKind);
      MaybeKind<String> rightSideNothingEval = // Renamed to avoid conflict
          maybeMonad.ap(evalKind, fKindNothing);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
      assertThat(unwrap(leftSideNothing)).isEqualTo(unwrap(rightSideNothingEval));
    }

    @Test
    @DisplayName("4. Composition: ap(ap(map(compose, gKind), fKind), v) == ap(gKind, ap(fKind, v))")
    void composition() {
      Function<
              Function<String, String>,
              Function<Function<Integer, String>, Function<Integer, String>>>
          composeMap = funcG -> funcF -> funcG.compose(funcF); // Renamed inner vars

      MaybeKind<Function<Function<Integer, String>, Function<Integer, String>>> mappedCompose =
          maybeMonad.map(composeMap, gKind);
      MaybeKind<Function<Integer, String>> ap1 = maybeMonad.ap(mappedCompose, fKind);
      MaybeKind<String> leftSide = maybeMonad.ap(ap1, v);

      MaybeKind<String> innerAp = maybeMonad.ap(fKind, v);
      MaybeKind<String> rightSide = maybeMonad.ap(gKind, innerAp);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));

      assertThat(
              unwrap(
                      maybeMonad.ap(
                          maybeMonad.ap(maybeMonad.map(composeMap, gKindNothing), fKind), v))
                  .isNothing())
          .isTrue();
      assertThat(unwrap(maybeMonad.ap(gKindNothing, maybeMonad.ap(fKind, v))).isNothing()).isTrue();
      assertThat(
              unwrap(
                      maybeMonad.ap(
                          maybeMonad.ap(maybeMonad.map(composeMap, gKind), fKindNothing), v))
                  .isNothing())
          .isTrue();
      assertThat(unwrap(maybeMonad.ap(gKind, maybeMonad.ap(fKindNothing, v))).isNothing()).isTrue();
      assertThat(
              unwrap(
                      maybeMonad.ap(
                          maybeMonad.ap(maybeMonad.map(composeMap, gKind), fKind), vNothing))
                  .isNothing())
          .isTrue();
      assertThat(unwrap(maybeMonad.ap(gKind, maybeMonad.ap(fKind, vNothing))).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {

    int value = 5;
    MaybeKind<Integer> mValue = just(value);
    MaybeKind<Integer> mValueNothing = nothing();

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      MaybeKind<Integer> ofValue = maybeMonad.of(value);
      // flatMap expects f: A -> Kind<MaybeKind.Witness, B>
      // f is Function<Integer, MaybeKind<String>>, which is compatible.
      MaybeKind<String> leftSide = maybeMonad.<Integer, String>flatMap(f, ofValue);
      MaybeKind<String> rightSide = (MaybeKind<String>) f.apply(value);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<MaybeKind.Witness, Integer>> ofFunc = maybeMonad::of;

      MaybeKind<Integer> leftSide = maybeMonad.<Integer, Integer>flatMap(ofFunc, mValue);
      MaybeKind<Integer> leftSideNothing =
          maybeMonad.<Integer, Integer>flatMap(ofFunc, mValueNothing);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(mValue));
      assertThat(unwrap(leftSideNothing)).isEqualTo(unwrap(mValueNothing));
    }

    @Test
    @DisplayName("3. Associativity: flatMap(g, flatMap(f, m)) == flatMap(a -> flatMap(g, f(a)), m)")
    void associativity() { // Corrected law formulation slightly
      // Left Side: flatMap(m, a -> flatMap(f(a), g)) -- standard way
      // flatMap(flatMap(m,f), g) is also valid

      // Original was: flatMap(g, flatMap(m,f)) - let's stick to user's test structure if possible
      // flatMap(m,f) -> M<B> (MaybeKind<String>)
      // flatMap(that, g) where g: B -> M<C> (String -> MaybeKind<String>)
      MaybeKind<String> innerFlatMap = maybeMonad.flatMap(f, mValue); // f: Int -> MaybeKind<String>
      MaybeKind<String> leftSide =
          maybeMonad.flatMap(g, innerFlatMap); // g: String -> MaybeKind<String>

      // Right Side: flatMap(m, a -> flatMap(f(a), g))
      // a is Integer. f(a) is MaybeKind<String>.
      // We need a function (Integer -> Kind<MaybeKind.Witness, String>) for the outer flatMap
      Function<Integer, Kind<MaybeKind.Witness, String>> rightSideFunc =
          a -> maybeMonad.flatMap(g, f.apply(a)); // f.apply(a) is MaybeKind<String>
      // g is String -> MaybeKind<String>
      // So this inner flatMap is flatMap(MaybeKind<String>, String -> MaybeKind<String>)
      // This returns MaybeKind<String>, compatible with Kind<MaybeKind.Witness, String>
      MaybeKind<String> rightSide = maybeMonad.flatMap(rightSideFunc, mValue);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));

      MaybeKind<String> innerFlatMapNothing = maybeMonad.flatMap(f, mValueNothing);
      MaybeKind<String> leftSideNothing = maybeMonad.flatMap(g, innerFlatMapNothing);
      MaybeKind<String> rightSideNothing = maybeMonad.flatMap(rightSideFunc, mValueNothing);
      assertThat(unwrap(leftSideNothing)).isEqualTo(unwrap(rightSideNothing));
    }
  }

  @Nested
  @DisplayName("MonadError tests")
  class MonadErrorTests {

    MaybeKind<Integer> justVal = just(100);
    MaybeKind<Integer> nothingVal = nothing();
    MaybeKind<Integer> raisedErrorKind = maybeMonad.raiseError(null); // returns MaybeKind<A>

    @Test
    void raiseError_shouldCreateNothing() {
      assertThat(unwrap(raisedErrorKind).isNothing()).isTrue();
    }

    @Test
    void handleErrorWith_shouldHandleNothing() {
      Function<Void, Kind<MaybeKind.Witness, Integer>> handler = err -> just(0);
      MaybeKind<Integer> result = maybeMonad.handleErrorWith(nothingVal, handler);
      assertThat(unwrap(result).isJust()).isTrue();
      assertThat(unwrap(result).get()).isEqualTo(0);
    }

    @Test
    void handleErrorWith_shouldHandleRaisedError() {
      Function<Void, Kind<MaybeKind.Witness, Integer>> handler = err -> just(0);
      MaybeKind<Integer> result = maybeMonad.handleErrorWith(raisedErrorKind, handler);
      assertThat(unwrap(result).isJust()).isTrue();
      assertThat(unwrap(result).get()).isEqualTo(0);
    }

    @Test
    void handleErrorWith_shouldIgnoreJust() {
      Function<Void, Kind<MaybeKind.Witness, Integer>> handler = err -> just(-1);
      MaybeKind<Integer> result = maybeMonad.handleErrorWith(justVal, handler);
      assertThat(result).isSameAs(justVal);
      assertThat(unwrap(result).isJust()).isTrue();
      assertThat(unwrap(result).get()).isEqualTo(100);
    }

    @Test
    void handleError_shouldHandleNothingWithPureValue() {
      Function<Void, Integer> handler = err -> -99;
      // MonadError.handleError default impl might use .of and .map, or .flatMap
      // Assuming MaybeMonad's handleError is as defined in MonadError interface default
      Kind<MaybeKind.Witness, Integer> result = maybeMonad.handleError(nothingVal, handler);
      assertThat(unwrap(result).isJust()).isTrue();
      assertThat(unwrap(result).get()).isEqualTo(-99);
    }

    @Test
    void handleError_shouldIgnoreJust() {
      Function<Void, Integer> handler = err -> -1;
      Kind<MaybeKind.Witness, Integer> result = maybeMonad.handleError(justVal, handler);
      assertThat(result).isSameAs(justVal);
      assertThat(unwrap(result).isJust()).isTrue();
      assertThat(unwrap(result).get()).isEqualTo(100);
    }

    @Test
    void recoverWith_shouldReplaceNothingWithFallbackKind() {
      MaybeKind<Integer> fallback = just(0);
      // MonadError.recoverWith default impl
      Kind<MaybeKind.Witness, Integer> result = maybeMonad.recoverWith(nothingVal, fallback);
      assertThat(result).isSameAs(fallback);
    }

    @Test
    void recoverWith_shouldIgnoreJust() {
      MaybeKind<Integer> fallback = just(0);
      Kind<MaybeKind.Witness, Integer> result = maybeMonad.recoverWith(justVal, fallback);
      assertThat(result).isSameAs(justVal);
    }

    @Test
    void recover_shouldReplaceNothingWithOfValue() {
      // MonadError.recover default impl
      Kind<MaybeKind.Witness, Integer> result = maybeMonad.recover(nothingVal, 0);
      assertThat(unwrap(result).isJust()).isTrue();
      assertThat(unwrap(result).get()).isEqualTo(0);
    }

    @Test
    void recover_shouldIgnoreJust() {
      Kind<MaybeKind.Witness, Integer> result = maybeMonad.recover(justVal, 0);
      assertThat(result).isSameAs(justVal);
    }
  }
}
