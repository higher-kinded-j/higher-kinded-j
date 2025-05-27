// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.unwrap;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.wrap;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.junit.jupiter.api.*;

@DisplayName("OptionalMonad Tests")
class OptionalMonadTest {

  private final MonadError<OptionalKind.Witness, Void> optionalMonad = new OptionalMonad();

  // Helper Functions for Laws
  private final Function<Integer, String> intToString = Object::toString;
  private final Function<String, String> appendWorld = s -> s + " world";
  private final Function<Integer, String> intToStringAppendWorld = intToString.andThen(appendWorld);

  private final Function<Integer, Kind<OptionalKind.Witness, String>> f =
      i -> wrap(Optional.of("v" + i));
  private final Function<String, Kind<OptionalKind.Witness, String>> g =
      s -> wrap(Optional.of(s + "!"));

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldWrapValueInOptional() {
      Kind<OptionalKind.Witness, String> kind = optionalMonad.of("test");
      assertThat(unwrap(kind)).isPresent().contains("test");
    }

    @Test
    void of_shouldWrapNullAsOptionalEmpty() {
      Kind<OptionalKind.Witness, String> kind = optionalMonad.of(null);
      assertThat(unwrap(kind)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionWhenPresent() {
      Kind<OptionalKind.Witness, Integer> input = wrap(Optional.of(5));
      Kind<OptionalKind.Witness, String> result = optionalMonad.map(Object::toString, input);
      assertThat(unwrap(result)).isPresent().contains("5");
    }

    @Test
    void map_shouldReturnEmptyWhenEmpty() {
      Kind<OptionalKind.Witness, Integer> input = wrap(Optional.empty());
      Kind<OptionalKind.Witness, String> result = optionalMonad.map(Object::toString, input);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void map_shouldHandleMappingToNullAsEmpty() {
      Kind<OptionalKind.Witness, Integer> input = wrap(Optional.of(5));
      Kind<OptionalKind.Witness, String> result = optionalMonad.map(x -> null, input);
      assertThat(unwrap(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    Kind<OptionalKind.Witness, Function<Integer, String>> funcKindPresent =
        optionalMonad.of(x -> "N" + x);
    Kind<OptionalKind.Witness, Function<Integer, String>> funcKindEmpty =
        optionalMonad.of(null); // Results in empty Optional
    Kind<OptionalKind.Witness, Integer> valueKindPresent = optionalMonad.of(10);
    Kind<OptionalKind.Witness, Integer> valueKindEmpty =
        optionalMonad.of(null); // Results in empty Optional

    @Test
    void ap_shouldApplyPresentFunctionToPresentValue() {
      Kind<OptionalKind.Witness, String> result =
          optionalMonad.ap(funcKindPresent, valueKindPresent);
      assertThat(unwrap(result)).isPresent().contains("N10");
    }

    @Test
    void ap_shouldReturnEmptyIfFunctionIsEmpty() {
      Kind<OptionalKind.Witness, String> result = optionalMonad.ap(funcKindEmpty, valueKindPresent);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void ap_shouldReturnEmptyIfValueIsEmpty() {
      Kind<OptionalKind.Witness, String> result = optionalMonad.ap(funcKindPresent, valueKindEmpty);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void ap_shouldReturnEmptyIfBothAreEmpty() {
      Kind<OptionalKind.Witness, String> result = optionalMonad.ap(funcKindEmpty, valueKindEmpty);
      assertThat(unwrap(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {

    Function<Integer, Kind<OptionalKind.Witness, Double>> safeDivide =
        divisor -> wrap((divisor == 0) ? Optional.empty() : Optional.of(100.0 / divisor));

    @Test
    void flatMap_shouldApplyFunctionWhenPresent() {
      Kind<OptionalKind.Witness, Integer> presentValue = optionalMonad.of(5);
      Kind<OptionalKind.Witness, Double> result = optionalMonad.flatMap(safeDivide, presentValue);
      assertThat(unwrap(result)).isPresent().contains(20.0);
    }

    @Test
    void flatMap_shouldReturnEmptyWhenInputIsEmpty() {
      Kind<OptionalKind.Witness, Integer> emptyValue = optionalMonad.of(null);
      Kind<OptionalKind.Witness, Double> result = optionalMonad.flatMap(safeDivide, emptyValue);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void flatMap_shouldReturnEmptyWhenFunctionResultIsEmpty() {
      Kind<OptionalKind.Witness, Integer> zeroValue = optionalMonad.of(0);
      Kind<OptionalKind.Witness, Double> result = optionalMonad.flatMap(safeDivide, zeroValue);
      assertThat(unwrap(result)).isEmpty();
    }
  }

  // --- Law Tests ---

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {
    @Test
    @DisplayName("1. Identity: map(id, fa) == fa")
    void identity() {
      Kind<OptionalKind.Witness, Integer> fa = optionalMonad.of(10);
      Kind<OptionalKind.Witness, Integer> faEmpty = optionalMonad.of(null);

      assertThat(unwrap(optionalMonad.map(Function.identity(), fa))).isEqualTo(unwrap(fa));
      assertThat(unwrap(optionalMonad.map(Function.identity(), faEmpty)))
          .isEqualTo(unwrap(faEmpty));
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      Kind<OptionalKind.Witness, Integer> fa = optionalMonad.of(10);
      Kind<OptionalKind.Witness, Integer> faEmpty = optionalMonad.of(null);

      Kind<OptionalKind.Witness, String> leftSide = optionalMonad.map(intToStringAppendWorld, fa);
      Kind<OptionalKind.Witness, String> rightSide =
          optionalMonad.map(appendWorld, optionalMonad.map(intToString, fa));

      Kind<OptionalKind.Witness, String> leftSideEmpty =
          optionalMonad.map(intToStringAppendWorld, faEmpty);
      Kind<OptionalKind.Witness, String> rightSideEmpty =
          optionalMonad.map(appendWorld, optionalMonad.map(intToString, faEmpty));

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
      assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(rightSideEmpty));
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {

    Kind<OptionalKind.Witness, Integer> v = optionalMonad.of(5);
    Kind<OptionalKind.Witness, Integer> vEmpty = optionalMonad.of(null);
    Kind<OptionalKind.Witness, Function<Integer, String>> fKind = optionalMonad.of(intToString);
    Kind<OptionalKind.Witness, Function<Integer, String>> fKindEmpty = optionalMonad.of(null);
    Kind<OptionalKind.Witness, Function<String, String>> gKind = optionalMonad.of(appendWorld);

    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      Kind<OptionalKind.Witness, Function<Integer, Integer>> idFuncKind =
          optionalMonad.of(Function.identity());
      assertThat(unwrap(optionalMonad.ap(idFuncKind, v))).isEqualTo(unwrap(v));
      assertThat(unwrap(optionalMonad.ap(idFuncKind, vEmpty))).isEqualTo(unwrap(vEmpty));
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      int x = 10;
      Function<Integer, String> func = intToString;
      Kind<OptionalKind.Witness, Function<Integer, String>> apFunc = optionalMonad.of(func);
      Kind<OptionalKind.Witness, Integer> apVal = optionalMonad.of(x);

      Kind<OptionalKind.Witness, String> leftSide = optionalMonad.ap(apFunc, apVal);
      Kind<OptionalKind.Witness, String> rightSide = optionalMonad.of(func.apply(x));

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 20;
      Kind<OptionalKind.Witness, String> leftSide = optionalMonad.ap(fKind, optionalMonad.of(y));
      Kind<OptionalKind.Witness, String> leftSideEmpty =
          optionalMonad.ap(fKindEmpty, optionalMonad.of(y));

      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      Kind<OptionalKind.Witness, Function<Function<Integer, String>, String>> evalKind =
          optionalMonad.of(evalWithY);

      Kind<OptionalKind.Witness, String> rightSide = optionalMonad.ap(evalKind, fKind);
      Kind<OptionalKind.Witness, String> rightSideEmpty = optionalMonad.ap(evalKind, fKindEmpty);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
      assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(rightSideEmpty));
    }

    @Test
    @DisplayName("4. Composition: ap(ap(map(compose, gKind), fKind), v) == ap(gKind, ap(fKind, v))")
    void composition() {
      Function<
              Function<String, String>,
              Function<Function<Integer, String>, Function<Integer, String>>>
          composeMap = funcG -> funcG::compose;

      Kind<OptionalKind.Witness, Function<Function<Integer, String>, Function<Integer, String>>>
          mappedCompose = optionalMonad.map(composeMap, gKind);
      Kind<OptionalKind.Witness, Function<Integer, String>> ap1 =
          optionalMonad.ap(mappedCompose, fKind);
      Kind<OptionalKind.Witness, String> leftSide = optionalMonad.ap(ap1, v);

      Kind<OptionalKind.Witness, String> innerAp = optionalMonad.ap(fKind, v);
      Kind<OptionalKind.Witness, String> rightSide = optionalMonad.ap(gKind, innerAp);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {

    int value = 5;
    Kind<OptionalKind.Witness, Integer> mValue = optionalMonad.of(value);
    Kind<OptionalKind.Witness, Integer> mValueEmpty = optionalMonad.of(null);

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      Kind<OptionalKind.Witness, Integer> ofValue = optionalMonad.of(value);
      Kind<OptionalKind.Witness, String> leftSide = optionalMonad.flatMap(f, ofValue);
      Kind<OptionalKind.Witness, String> rightSide = f.apply(value);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<OptionalKind.Witness, Integer>> ofFunc = i -> optionalMonad.of(i);

      Kind<OptionalKind.Witness, Integer> leftSide = optionalMonad.flatMap(ofFunc, mValue);
      Kind<OptionalKind.Witness, Integer> leftSideEmpty =
          optionalMonad.flatMap(ofFunc, mValueEmpty);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(mValue));
      assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(mValueEmpty));
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<OptionalKind.Witness, String> innerFlatMap = optionalMonad.flatMap(f, mValue);
      Kind<OptionalKind.Witness, String> leftSide = optionalMonad.flatMap(g, innerFlatMap);

      Function<Integer, Kind<OptionalKind.Witness, String>> rightSideFunc =
          a -> optionalMonad.flatMap(g, f.apply(a));
      Kind<OptionalKind.Witness, String> rightSide = optionalMonad.flatMap(rightSideFunc, mValue);

      assertThat(unwrap(leftSide)).isEqualTo(unwrap(rightSide));

      Kind<OptionalKind.Witness, String> innerFlatMapEmpty = optionalMonad.flatMap(f, mValueEmpty);
      Kind<OptionalKind.Witness, String> leftSideEmpty =
          optionalMonad.flatMap(g, innerFlatMapEmpty);
      Kind<OptionalKind.Witness, String> rightSideEmpty =
          optionalMonad.flatMap(rightSideFunc, mValueEmpty);
      assertThat(unwrap(leftSideEmpty)).isEqualTo(unwrap(rightSideEmpty));
    }
  }

  @Nested
  @DisplayName("MonadError tests")
  class MonadErrorTests {

    Kind<OptionalKind.Witness, Integer> presentVal = optionalMonad.of(100);
    Kind<OptionalKind.Witness, Integer> emptyVal = optionalMonad.of(null);
    Kind<OptionalKind.Witness, Integer> raisedErrorKind = optionalMonad.raiseError(null);

    @Test
    void raiseError_shouldCreateEmpty() {
      assertThat(unwrap(raisedErrorKind)).isEmpty();
    }

    @Test
    void handleErrorWith_shouldHandleEmpty() {
      Function<Void, Kind<OptionalKind.Witness, Integer>> handler = err -> optionalMonad.of(0);
      Kind<OptionalKind.Witness, Integer> result = optionalMonad.handleErrorWith(emptyVal, handler);
      assertThat(unwrap(result)).isPresent().contains(0);
    }

    @Test
    void handleErrorWith_shouldIgnorePresent() {
      Function<Void, Kind<OptionalKind.Witness, Integer>> handler = err -> optionalMonad.of(-1);
      Kind<OptionalKind.Witness, Integer> result =
          optionalMonad.handleErrorWith(presentVal, handler);
      assertThat(result).isSameAs(presentVal);
      assertThat(unwrap(result)).isPresent().contains(100);
    }
  }
}
