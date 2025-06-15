// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.*; // unwrap, just, nothing

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.unit.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MaybeMonadTest {

  private final MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

  // Helper Functions for Laws
  private final Function<Integer, String> intToString = Object::toString;
  private final Function<String, String> appendWorld = s -> s + " world";
  private final Function<Integer, String> intToStringAppendWorld = intToString.andThen(appendWorld);

  // Function a -> M b (Integer -> var)
  // wrap returns var, which is Kind<MaybeKind.Witness, String>
  private final Function<Integer, Kind<MaybeKind.Witness, String>> f =
      i -> MAYBE.widen(Maybe.just("v" + i));
  // Function b -> M c (String -> var)
  private final Function<String, Kind<MaybeKind.Witness, String>> g =
      s -> MAYBE.widen(Maybe.just(s + "!"));

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldWrapValueInJust() {
      // maybeMonad.of returns var
      var kind = maybeMonad.of("test");
      Maybe<String> maybe = MAYBE.narrow(kind); // unwrap takes Kind<MaybeKind.Witness, A>
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("test");
    }

    @Test
    void of_shouldWrapNullAsNothing() {
      var kind = maybeMonad.of(null);
      assertThat(MAYBE.narrow(kind).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionWhenJust() {
      var input = MAYBE.MAYBE.just(5); // MaybeKindHelper.just returns MaybeKind<A>
      // maybeMonad.map takes Kind<MaybeKind.Witness, A> and returns MaybeKind<B>
      var result = maybeMonad.map(Object::toString, input);
      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("5");
    }

    @Test
    void map_shouldReturnNothingWhenNothing() {
      var input = MAYBE.nothing(); // MaybeKindHelper.nothing returns MaybeKind<A>
      var result = maybeMonad.map(Object::toString, input);
      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    void map_shouldHandleMappingToNullAsNothing() {
      var input = MAYBE.just(5);
      var result = maybeMonad.map(x -> null, input);
      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    Kind<MaybeKind.Witness, Function<Integer, String>> funcKindJust = maybeMonad.of(x -> "N" + x);
    Kind<MaybeKind.Witness, Function<Integer, String>> funcKindNothing =
        MAYBE.nothing(); // Use MAYBE.nothing() from helper which returns MaybeKind
    Kind<MaybeKind.Witness, Integer> valueKindJust = maybeMonad.of(10);
    Kind<MaybeKind.Witness, Integer> valueKindNothing = MAYBE.nothing(); // Use MAYBE.nothing()

    @Test
    void ap_shouldApplyJustFunctionToJustValue() {
      var result = maybeMonad.ap(funcKindJust, valueKindJust);
      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("N10");
    }

    @Test
    void ap_shouldReturnNothingIfFunctionIsNothing() {
      var result = maybeMonad.ap(funcKindNothing, valueKindJust);
      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    void ap_shouldReturnNothingIfValueIsNothing() {
      var result = maybeMonad.ap(funcKindJust, valueKindNothing);
      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    void ap_shouldReturnNothingIfBothAreNothing() {
      var result = maybeMonad.ap(funcKindNothing, valueKindNothing);
      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {

    // Function: Integer -> Kind<MaybeKind.Witness, Double> (or MaybeKind<Double>)
    Function<Integer, Kind<MaybeKind.Witness, Double>> safeInvert =
        num -> MAYBE.widen((num == 0) ? Maybe.nothing() : Maybe.just(1.0 / num));

    Kind<MaybeKind.Witness, Integer> justValue = MAYBE.just(10);
    Kind<MaybeKind.Witness, Integer> zeroValue = MAYBE.just(0);
    Kind<MaybeKind.Witness, Integer> nothingValue = MAYBE.nothing();

    @Test
    void flatMap_shouldApplyFunctionWhenJust() {
      var result = maybeMonad.flatMap(safeInvert, justValue);
      Maybe<Double> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(0.1);
    }

    @Test
    void flatMap_shouldReturnNothingWhenInputIsNothing() {
      var result = maybeMonad.flatMap(safeInvert, nothingValue);
      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    void flatMap_shouldReturnNothingWhenFunctionResultIsNothing() {
      var result = maybeMonad.flatMap(safeInvert, zeroValue);
      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    void flatMap_chainingExample() {
      var initial = MAYBE.just(4);

      // flatMap takes Function<A, Kind<MaybeKind.Witness, B>>
      // safeInvert returns MaybeKind<Double> which is Kind<MaybeKind.Witness, Double>
      var finalResult =
          maybeMonad.flatMap(
              x -> {
                var intermediate = MAYBE.just(x * 5);
                return maybeMonad.flatMap(safeInvert, intermediate);
              },
              initial);

      Maybe<Double> maybe = MAYBE.narrow(finalResult);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(0.05);
    }

    @Test
    void flatMap_chainingWithNothingPropagation() {
      var initial = MAYBE.just(5);

      var finalResult =
          maybeMonad.flatMap(
              x -> {
                var intermediate = MAYBE.just(x - 5);
                return maybeMonad.flatMap(safeInvert, intermediate);
              },
              initial);

      assertThat(MAYBE.narrow(finalResult).isNothing()).isTrue();
    }
  }

  // --- Law Tests ---

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {
    @Test
    @DisplayName("1. Identity: map(id, fa) == fa")
    void identity() {
      var fa = MAYBE.just(10);
      Kind<MaybeKind.Witness, Integer> faNothing = MAYBE.nothing();

      assertThat(MAYBE.narrow(maybeMonad.map(Function.identity(), fa))).isEqualTo(MAYBE.narrow(fa));
      assertThat(MAYBE.narrow(maybeMonad.map(Function.identity(), faNothing)))
          .isEqualTo(MAYBE.narrow(faNothing));
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      var fa = MAYBE.just(10);
      Kind<MaybeKind.Witness, Integer> faNothing = MAYBE.nothing();

      var leftSide = maybeMonad.map(intToStringAppendWorld, fa);
      var rightSide = maybeMonad.map(appendWorld, maybeMonad.map(intToString, fa));

      var leftSideNothing = maybeMonad.map(intToStringAppendWorld, faNothing);
      var rightSideNothing = maybeMonad.map(appendWorld, maybeMonad.map(intToString, faNothing));

      assertThat(MAYBE.narrow(leftSide)).isEqualTo(MAYBE.narrow(rightSide));
      assertThat(MAYBE.narrow(leftSideNothing)).isEqualTo(MAYBE.narrow(rightSideNothing));
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {

    Kind<MaybeKind.Witness, Integer> v = MAYBE.just(5);
    Kind<MaybeKind.Witness, Integer> vNothing = MAYBE.nothing();
    Kind<MaybeKind.Witness, Function<Integer, String>> fKind = MAYBE.just(intToString);
    Kind<MaybeKind.Witness, Function<Integer, String>> fKindNothing = MAYBE.nothing();
    Kind<MaybeKind.Witness, Function<String, String>> gKind = MAYBE.just(appendWorld);
    Kind<MaybeKind.Witness, Function<String, String>> gKindNothing = MAYBE.nothing();

    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      Kind<MaybeKind.Witness, Function<Integer, Integer>> idFuncKind =
          maybeMonad.of(Function.identity());
      assertThat(MAYBE.narrow(maybeMonad.ap(idFuncKind, v))).isEqualTo(MAYBE.narrow(v));
      assertThat(MAYBE.narrow(maybeMonad.ap(idFuncKind, vNothing)))
          .isEqualTo(MAYBE.narrow(vNothing));
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      int x = 10;
      Function<Integer, String> fMapFunc = intToString; // Changed variable name to avoid conflict
      Kind<MaybeKind.Witness, Function<Integer, String>> apFunc = maybeMonad.of(fMapFunc);
      var apVal = maybeMonad.of(x);

      var leftSide = maybeMonad.ap(apFunc, apVal);
      var rightSide = maybeMonad.of(fMapFunc.apply(x));

      assertThat(MAYBE.narrow(leftSide)).isEqualTo(MAYBE.narrow(rightSide));
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 20;
      var leftSide = maybeMonad.ap(fKind, maybeMonad.of(y));
      var leftSideNothing = maybeMonad.ap(fKindNothing, maybeMonad.of(y));

      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      Kind<MaybeKind.Witness, Function<Function<Integer, String>, String>> evalKind =
          maybeMonad.of(evalWithY);

      var rightSide = maybeMonad.ap(evalKind, fKind);
      var rightSideNothingEval = // Renamed to avoid conflict
          maybeMonad.ap(evalKind, fKindNothing);

      assertThat(MAYBE.narrow(leftSide)).isEqualTo(MAYBE.narrow(rightSide));
      assertThat(MAYBE.narrow(leftSideNothing)).isEqualTo(MAYBE.narrow(rightSideNothingEval));
    }

    @Test
    @DisplayName("4. Composition: ap(ap(map(compose, gKind), fKind), v) == ap(gKind, ap(fKind, v))")
    void composition() {
      Function<
              Function<String, String>,
              Function<Function<Integer, String>, Function<Integer, String>>>
          composeMap = funcG -> funcF -> funcG.compose(funcF); // Renamed inner vars

      Kind<MaybeKind.Witness, Function<Function<Integer, String>, Function<Integer, String>>>
          mappedCompose = maybeMonad.map(composeMap, gKind);
      var ap1 = maybeMonad.ap(mappedCompose, fKind);
      var leftSide = maybeMonad.ap(ap1, v);

      var innerAp = maybeMonad.ap(fKind, v);
      var rightSide = maybeMonad.ap(gKind, innerAp);

      assertThat(MAYBE.narrow(leftSide)).isEqualTo(MAYBE.narrow(rightSide));

      assertThat(
              MAYBE
                  .narrow(
                      maybeMonad.ap(
                          maybeMonad.ap(maybeMonad.map(composeMap, gKindNothing), fKind), v))
                  .isNothing())
          .isTrue();
      assertThat(MAYBE.narrow(maybeMonad.ap(gKindNothing, maybeMonad.ap(fKind, v))).isNothing())
          .isTrue();
      assertThat(
              MAYBE
                  .narrow(
                      maybeMonad.ap(
                          maybeMonad.ap(maybeMonad.map(composeMap, gKind), fKindNothing), v))
                  .isNothing())
          .isTrue();
      assertThat(MAYBE.narrow(maybeMonad.ap(gKind, maybeMonad.ap(fKindNothing, v))).isNothing())
          .isTrue();
      assertThat(
              MAYBE
                  .narrow(
                      maybeMonad.ap(
                          maybeMonad.ap(maybeMonad.map(composeMap, gKind), fKind), vNothing))
                  .isNothing())
          .isTrue();
      assertThat(MAYBE.narrow(maybeMonad.ap(gKind, maybeMonad.ap(fKind, vNothing))).isNothing())
          .isTrue();
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {

    int value = 5;
    Kind<MaybeKind.Witness, Integer> mValue = MAYBE.just(value);
    Kind<MaybeKind.Witness, Integer> mValueNothing = MAYBE.nothing();

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      var ofValue = maybeMonad.of(value);
      // flatMap expects f: A -> Kind<MaybeKind.Witness, B>
      // f is Function<Integer, var>, which is compatible.
      var leftSide = maybeMonad.<Integer, String>flatMap(f, ofValue);
      var rightSide = f.apply(value);

      assertThat(MAYBE.narrow(leftSide)).isEqualTo(MAYBE.narrow(rightSide));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<MaybeKind.Witness, Integer>> ofFunc = maybeMonad::of;

      var leftSide = maybeMonad.<Integer, Integer>flatMap(ofFunc, mValue);
      var leftSideNothing = maybeMonad.<Integer, Integer>flatMap(ofFunc, mValueNothing);

      assertThat(MAYBE.narrow(leftSide)).isEqualTo(MAYBE.narrow(mValue));
      assertThat(MAYBE.narrow(leftSideNothing)).isEqualTo(MAYBE.narrow(mValueNothing));
    }

    @Test
    @DisplayName("3. Associativity: flatMap(g, flatMap(f, m)) == flatMap(a -> flatMap(g, f(a)), m)")
    void associativity() { // Corrected law formulation slightly
      // Left Side: flatMap(m, a -> flatMap(f(a), g)) -- standard way
      // flatMap(flatMap(m,f), g) is also valid

      // Original was: flatMap(g, flatMap(m,f)) - let's stick to user's test structure if possible
      // flatMap(m,f) -> M<B> (var)
      // flatMap(that, g) where g: B -> M<C> (String -> var)
      var innerFlatMap = maybeMonad.flatMap(f, mValue); // f: Int -> var
      var leftSide = maybeMonad.flatMap(g, innerFlatMap); // g: String -> var

      // Right Side: flatMap(m, a -> flatMap(f(a), g))
      // a is Integer. f(a) is var.
      // We need a function (Integer -> Kind<MaybeKind.Witness, String>) for the outer flatMap
      Function<Integer, Kind<MaybeKind.Witness, String>> rightSideFunc =
          a -> maybeMonad.flatMap(g, f.apply(a)); // f.apply(a) is var
      // g is String -> var
      // So this inner flatMap is flatMap(var, String -> var)
      // This returns var, compatible with Kind<MaybeKind.Witness, String>
      var rightSide = maybeMonad.flatMap(rightSideFunc, mValue);

      assertThat(MAYBE.narrow(leftSide)).isEqualTo(MAYBE.narrow(rightSide));

      var innerFlatMapNothing = maybeMonad.flatMap(f, mValueNothing);
      var leftSideNothing = maybeMonad.flatMap(g, innerFlatMapNothing);
      var rightSideNothing = maybeMonad.flatMap(rightSideFunc, mValueNothing);
      assertThat(MAYBE.narrow(leftSideNothing)).isEqualTo(MAYBE.narrow(rightSideNothing));
    }
  }

  @Nested
  @DisplayName("MonadError tests")
  class MonadErrorTests {

    Kind<MaybeKind.Witness, Integer> justVal = MAYBE.just(100);
    Kind<MaybeKind.Witness, Integer> nothingVal = MAYBE.nothing();
    Kind<MaybeKind.Witness, Integer> raisedErrorKind = maybeMonad.raiseError(null);

    @Test
    void raiseError_shouldCreateNothing() {
      assertThat(MAYBE.narrow(raisedErrorKind).isNothing()).isTrue();
    }

    @Test
    void handleErrorWith_shouldHandleNothing() {
      Function<Unit, Kind<MaybeKind.Witness, Integer>> handler = err -> MAYBE.just(0);
      var result = maybeMonad.handleErrorWith(nothingVal, handler);
      assertThat(MAYBE.narrow(result).isJust()).isTrue();
      assertThat(MAYBE.narrow(result).get()).isEqualTo(0);
    }

    @Test
    void handleErrorWith_shouldHandleRaisedError() {
      Function<Unit, Kind<MaybeKind.Witness, Integer>> handler = err -> MAYBE.just(0);
      var result = maybeMonad.handleErrorWith(raisedErrorKind, handler);
      assertThat(MAYBE.narrow(result).isJust()).isTrue();
      assertThat(MAYBE.narrow(result).get()).isEqualTo(0);
    }

    @Test
    void handleErrorWith_shouldIgnoreJust() {
      Function<Unit, Kind<MaybeKind.Witness, Integer>> handler = err -> MAYBE.just(-1);
      var result = maybeMonad.handleErrorWith(justVal, handler);
      assertThat(result).isSameAs(justVal);
      assertThat(MAYBE.narrow(result).isJust()).isTrue();
      assertThat(MAYBE.narrow(result).get()).isEqualTo(100);
    }

    @Test
    void handleError_shouldHandleNothingWithPureValue() {
      Function<Unit, Integer> handler = err -> -99;
      // MonadError.handleError default impl might use .of and .map, or .flatMap
      // Assuming MaybeMonad's handleError is as defined in MonadError interface default
      Kind<MaybeKind.Witness, Integer> result = maybeMonad.handleError(nothingVal, handler);
      assertThat(MAYBE.narrow(result).isJust()).isTrue();
      assertThat(MAYBE.narrow(result).get()).isEqualTo(-99);
    }

    @Test
    void handleError_shouldIgnoreJust() {
      Function<Unit, Integer> handler = err -> -1;
      Kind<MaybeKind.Witness, Integer> result = maybeMonad.handleError(justVal, handler);
      assertThat(result).isSameAs(justVal);
      assertThat(MAYBE.narrow(result).isJust()).isTrue();
      assertThat(MAYBE.narrow(result).get()).isEqualTo(100);
    }

    @Test
    void recoverWith_shouldReplaceNothingWithFallbackKind() {
      var fallback = MAYBE.just(0);
      // MonadError.recoverWith default impl
      Kind<MaybeKind.Witness, Integer> result = maybeMonad.recoverWith(nothingVal, fallback);
      assertThat(result).isSameAs(fallback);
    }

    @Test
    void recoverWith_shouldIgnoreJust() {
      var fallback = MAYBE.just(0);
      Kind<MaybeKind.Witness, Integer> result = maybeMonad.recoverWith(justVal, fallback);
      assertThat(result).isSameAs(justVal);
    }

    @Test
    void recover_shouldReplaceNothingWithOfValue() {
      // MonadError.recover default impl
      Kind<MaybeKind.Witness, Integer> result = maybeMonad.recover(nothingVal, 0);
      assertThat(MAYBE.narrow(result).isJust()).isTrue();
      assertThat(MAYBE.narrow(result).get()).isEqualTo(0);
    }

    @Test
    void recover_shouldIgnoreJust() {
      Kind<MaybeKind.Witness, Integer> result = maybeMonad.recover(justVal, 0);
      assertThat(result).isSameAs(justVal);
    }
  }
}
