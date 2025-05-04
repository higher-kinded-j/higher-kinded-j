package org.higherkindedj.hkt.trans.maybe_t;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional; // Using Optional as F for testing
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.optional.OptionalKind; // Using Optional as F for testing
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeT Monad Tests (Outer: Optional)") // Test MaybeTMonad
class MaybeTMonadTest {

  // Outer Monad (F = OptionalKind<?>)
  private final Monad<OptionalKind<?>> outerMonad = new OptionalMonad();
  // MaybeT Monad (G = MaybeTKind<OptionalKind<?>, ?>)
  private final MonadError<MaybeTKind<OptionalKind<?>, ?>, Void> maybeTMonad =
      new MaybeTMonad<>(outerMonad);

  // --- Helper Methods ---

  // Unwrap MaybeT<OptionalKind<?>, A> -> Optional<Maybe<A>>
  private <A> Optional<Maybe<A>> unwrapT(Kind<MaybeTKind<OptionalKind<?>, ?>, A> kind) {
    MaybeT<OptionalKind<?>, A> maybeT = (MaybeT<OptionalKind<?>, A>) kind;
    Kind<OptionalKind<?>, Maybe<A>> outerKind = maybeT.value();
    return OptionalKindHelper.unwrap(outerKind);
  }

  // Create MaybeT(Optional.of(Maybe.just(value))) - Requires non-null value
  private <A extends @NonNull Object> Kind<MaybeTKind<OptionalKind<?>, ?>, A> justT(A value) {
    return MaybeT.just(outerMonad, value);
  }

  // Create MaybeT(Optional.of(Maybe.nothing()))
  private <A> Kind<MaybeTKind<OptionalKind<?>, ?>, A> nothingT() {
    return MaybeT.nothing(outerMonad);
  }

  // Create MaybeT(Optional.empty()) - Represents failure/empty in the outer context
  private <A> Kind<MaybeTKind<OptionalKind<?>, ?>, A> outerEmptyT() {
    Kind<OptionalKind<?>, Maybe<A>> emptyOuter = OptionalKindHelper.wrap(Optional.empty());
    return MaybeT.fromKind(emptyOuter);
  }

  // Helper Functions for Laws
  private final Function<Integer, String> intToString = Object::toString;
  private final Function<String, String> appendWorld = s -> s + " world";
  private final Function<Integer, String> intToStringAppendWorld = intToString.andThen(appendWorld);

  // Function a -> M b (Integer -> MaybeT<Optional, String>)
  private final Function<Integer, Kind<MaybeTKind<OptionalKind<?>, ?>, String>> fT =
      i -> justT("v" + i);
  // Function b -> M c (String -> MaybeT<Optional, String>)
  private final Function<String, Kind<MaybeTKind<OptionalKind<?>, ?>, String>> gT =
      s -> justT(s + "!");

  // --- Basic Operations Tests ---

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldWrapValueAsJustInOptional() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> kind = maybeTMonad.of(10);
      Optional<Maybe<Integer>> result = unwrapT(kind);
      assertThat(result).isPresent().contains(Maybe.just(10));
    }

    @Test
    void of_shouldWrapNullAsNothingInOptional() {
      // MaybeTMonad.of uses Maybe.fromNullable
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> kind = maybeTMonad.of(null);
      Optional<Maybe<Integer>> result = unwrapT(kind);
      assertThat(result).isPresent().contains(Maybe.nothing());
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionWhenJust() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> input = justT(5);
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> result =
          maybeTMonad.map(Object::toString, input);
      assertThat(unwrapT(result)).isPresent().contains(Maybe.just("5"));
    }

    @Test
    void map_shouldPropagateNothing() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> input = nothingT();
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> result =
          maybeTMonad.map(Object::toString, input);
      assertThat(unwrapT(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void map_shouldPropagateOuterEmpty() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> input = outerEmptyT();
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> result =
          maybeTMonad.map(Object::toString, input);
      assertThat(unwrapT(result)).isEmpty();
    }

    @Test
    void map_shouldHandleMappingToNullAsNothing() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> input = justT(5);
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> result = maybeTMonad.map(x -> null, input);
      // Maybe.map(null) -> Maybe.fromNullable(null) -> Nothing
      assertThat(unwrapT(result)).isPresent().contains(Maybe.nothing());
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    Kind<MaybeTKind<OptionalKind<?>, ?>, Function<Integer, String>> funcKindJust =
        justT((Function<Integer, String>) x -> "N" + x);
    Kind<MaybeTKind<OptionalKind<?>, ?>, Function<Integer, String>> funcKindNothing = nothingT();
    Kind<MaybeTKind<OptionalKind<?>, ?>, Function<Integer, String>> funcKindOuterEmpty =
        outerEmptyT();

    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> valKindJust = justT(10);
    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> valKindNothing = nothingT();
    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> valKindOuterEmpty = outerEmptyT();

    @Test
    void ap_shouldApplyJustFuncToJustValue() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> result =
          maybeTMonad.ap(funcKindJust, valKindJust);
      assertThat(unwrapT(result)).isPresent().contains(Maybe.just("N10"));
    }

    @Test
    void ap_shouldReturnNothingIfFuncIsNothing() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> result =
          maybeTMonad.ap(funcKindNothing, valKindJust);
      assertThat(unwrapT(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void ap_shouldReturnNothingIfValueIsNothing() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> result =
          maybeTMonad.ap(funcKindJust, valKindNothing);
      assertThat(unwrapT(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void ap_shouldReturnNothingIfBothAreNothing() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> result =
          maybeTMonad.ap(funcKindNothing, valKindNothing);
      assertThat(unwrapT(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void ap_shouldReturnOuterEmptyIfFuncIsOuterEmpty() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> result =
          maybeTMonad.ap(funcKindOuterEmpty, valKindJust);
      assertThat(unwrapT(result)).isEmpty();
    }

    @Test
    void ap_shouldReturnOuterEmptyIfValueIsOuterEmpty() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> result =
          maybeTMonad.ap(funcKindJust, valKindOuterEmpty);
      assertThat(unwrapT(result)).isEmpty();
    }

    @Test
    void ap_shouldReturnOuterEmptyIfBothAreOuterEmpty() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> result =
          maybeTMonad.ap(funcKindOuterEmpty, valKindOuterEmpty);
      assertThat(unwrapT(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    Function<Integer, Kind<MaybeTKind<OptionalKind<?>, ?>, Double>> safeInvertT =
        num -> (num == 0) ? nothingT() : justT(1.0 / num);
    Function<Integer, Kind<MaybeTKind<OptionalKind<?>, ?>, Double>> outerEmptyResultT =
        num -> outerEmptyT();

    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> justValue = justT(5);
    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> zeroValue = justT(0);
    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> nothingValue = nothingT();
    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> outerEmptyValue = outerEmptyT();

    @Test
    void flatMap_shouldApplyFuncWhenJust() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Double> result =
          maybeTMonad.flatMap(safeInvertT, justValue);
      assertThat(unwrapT(result)).isPresent().contains(Maybe.just(0.2));
    }

    @Test
    void flatMap_shouldReturnNothingWhenFuncReturnsNothing() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Double> result =
          maybeTMonad.flatMap(safeInvertT, zeroValue);
      assertThat(unwrapT(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void flatMap_shouldPropagateNothingWhenInputIsNothing() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Double> result =
          maybeTMonad.flatMap(safeInvertT, nothingValue);
      assertThat(unwrapT(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void flatMap_shouldPropagateOuterEmptyWhenInputIsOuterEmpty() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Double> result =
          maybeTMonad.flatMap(safeInvertT, outerEmptyValue);
      assertThat(unwrapT(result)).isEmpty();
    }

    @Test
    void flatMap_shouldPropagateOuterEmptyWhenFuncReturnsOuterEmpty() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Double> result =
          maybeTMonad.flatMap(outerEmptyResultT, justValue);
      assertThat(unwrapT(result)).isEmpty();
    }
  }

  // --- Law Tests ---

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {
    @Test
    @DisplayName("1. Identity: map(id, fa) == fa")
    void identity() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> fa = justT(10);
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> faNothing = nothingT();
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> faOuterEmpty = outerEmptyT();

      assertThat(unwrapT(maybeTMonad.map(Function.identity(), fa))).isEqualTo(unwrapT(fa));
      assertThat(unwrapT(maybeTMonad.map(Function.identity(), faNothing)))
          .isEqualTo(unwrapT(faNothing));
      assertThat(unwrapT(maybeTMonad.map(Function.identity(), faOuterEmpty)))
          .isEqualTo(unwrapT(faOuterEmpty));
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> fa = justT(10);
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> faNothing = nothingT();
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> faOuterEmpty = outerEmptyT();

      Kind<MaybeTKind<OptionalKind<?>, ?>, String> leftSide =
          maybeTMonad.map(intToStringAppendWorld, fa);
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> rightSide =
          maybeTMonad.map(appendWorld, maybeTMonad.map(intToString, fa));

      Kind<MaybeTKind<OptionalKind<?>, ?>, String> leftSideNothing =
          maybeTMonad.map(intToStringAppendWorld, faNothing);
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> rightSideNothing =
          maybeTMonad.map(appendWorld, maybeTMonad.map(intToString, faNothing));

      Kind<MaybeTKind<OptionalKind<?>, ?>, String> leftSideOuterEmpty =
          maybeTMonad.map(intToStringAppendWorld, faOuterEmpty);
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> rightSideOuterEmpty =
          maybeTMonad.map(appendWorld, maybeTMonad.map(intToString, faOuterEmpty));

      assertThat(unwrapT(leftSide)).isEqualTo(unwrapT(rightSide));
      assertThat(unwrapT(leftSideNothing)).isEqualTo(unwrapT(rightSideNothing));
      assertThat(unwrapT(leftSideOuterEmpty)).isEqualTo(unwrapT(rightSideOuterEmpty));
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {

    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> v = justT(5);
    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> vNothing = nothingT();
    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> vOuterEmpty = outerEmptyT();
    Kind<MaybeTKind<OptionalKind<?>, ?>, Function<Integer, String>> fKind = justT(intToString);
    Kind<MaybeTKind<OptionalKind<?>, ?>, Function<Integer, String>> fKindNothing = nothingT();
    Kind<MaybeTKind<OptionalKind<?>, ?>, Function<String, String>> gKind = justT(appendWorld);

    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Function<Integer, Integer>> idFuncKind =
          maybeTMonad.of(Function.identity());
      assertThat(unwrapT(maybeTMonad.ap(idFuncKind, v))).isEqualTo(unwrapT(v));
      assertThat(unwrapT(maybeTMonad.ap(idFuncKind, vNothing))).isEqualTo(unwrapT(vNothing));
      assertThat(unwrapT(maybeTMonad.ap(idFuncKind, vOuterEmpty))).isEqualTo(unwrapT(vOuterEmpty));
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      int x = 10;
      Function<Integer, String> f = intToString;
      Kind<MaybeTKind<OptionalKind<?>, ?>, Function<Integer, String>> apFunc = maybeTMonad.of(f);
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> apVal = maybeTMonad.of(x);

      Kind<MaybeTKind<OptionalKind<?>, ?>, String> leftSide = maybeTMonad.ap(apFunc, apVal);
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> rightSide = maybeTMonad.of(f.apply(x));

      assertThat(unwrapT(leftSide)).isEqualTo(unwrapT(rightSide));
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 20;
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> leftSide =
          maybeTMonad.ap(fKind, maybeTMonad.of(y));
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> leftSideFuncNothing =
          maybeTMonad.ap(fKindNothing, maybeTMonad.of(y));

      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      Kind<MaybeTKind<OptionalKind<?>, ?>, Function<Function<Integer, String>, String>> evalKind =
          maybeTMonad.of(evalWithY);

      Kind<MaybeTKind<OptionalKind<?>, ?>, String> rightSide = maybeTMonad.ap(evalKind, fKind);
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> rightSideFuncNothing =
          maybeTMonad.ap(evalKind, fKindNothing);

      assertThat(unwrapT(leftSide)).isEqualTo(unwrapT(rightSide));
      assertThat(unwrapT(leftSideFuncNothing)).isEqualTo(unwrapT(rightSideFuncNothing));
    }

    @Test
    @DisplayName("4. Composition: ap(ap(map(compose, gKind), fKind), v) == ap(gKind, ap(fKind, v))")
    void composition() {
      Function<
              Function<String, String>,
              Function<Function<Integer, String>, Function<Integer, String>>>
          composeMap = gg -> ff -> gg.compose(ff);

      Kind<
              MaybeTKind<OptionalKind<?>, ?>,
              Function<Function<Integer, String>, Function<Integer, String>>>
          mappedCompose = maybeTMonad.map(composeMap, gKind);
      Kind<MaybeTKind<OptionalKind<?>, ?>, Function<Integer, String>> ap1 =
          maybeTMonad.ap(mappedCompose, fKind);
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> leftSide = maybeTMonad.ap(ap1, v);

      Kind<MaybeTKind<OptionalKind<?>, ?>, String> innerAp = maybeTMonad.ap(fKind, v);
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> rightSide = maybeTMonad.ap(gKind, innerAp);

      assertThat(unwrapT(leftSide)).isEqualTo(unwrapT(rightSide));
      // Add tests for Nothing/OuterEmpty propagation if needed
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {
    int value = 5;
    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> mValue = justT(value);
    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> mValueNothing = nothingT();
    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> mValueOuterEmpty = outerEmptyT();

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> ofValue = maybeTMonad.of(value);
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> leftSide = maybeTMonad.flatMap(fT, ofValue);
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> rightSide = fT.apply(value);

      assertThat(unwrapT(leftSide)).isEqualTo(unwrapT(rightSide));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<MaybeTKind<OptionalKind<?>, ?>, Integer>> ofFunc =
          i -> maybeTMonad.of(i);

      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> leftSide = maybeTMonad.flatMap(ofFunc, mValue);
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> leftSideNothing =
          maybeTMonad.flatMap(ofFunc, mValueNothing);
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> leftSideOuterEmpty =
          maybeTMonad.flatMap(ofFunc, mValueOuterEmpty);

      assertThat(unwrapT(leftSide)).isEqualTo(unwrapT(mValue));
      assertThat(unwrapT(leftSideNothing)).isEqualTo(unwrapT(mValueNothing));
      assertThat(unwrapT(leftSideOuterEmpty)).isEqualTo(unwrapT(mValueOuterEmpty));
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> innerFlatMap = maybeTMonad.flatMap(fT, mValue);
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> leftSide = maybeTMonad.flatMap(gT, innerFlatMap);

      Function<Integer, Kind<MaybeTKind<OptionalKind<?>, ?>, String>> rightSideFunc =
          a -> maybeTMonad.flatMap(gT, fT.apply(a));
      Kind<MaybeTKind<OptionalKind<?>, ?>, String> rightSide =
          maybeTMonad.flatMap(rightSideFunc, mValue);

      assertThat(unwrapT(leftSide)).isEqualTo(unwrapT(rightSide));
      // Add tests for Nothing/OuterEmpty propagation if needed
    }
  }

  // --- MonadError Tests (Error Type E = Void) ---

  @Nested
  @DisplayName("MonadError Tests")
  class MonadErrorTests {
    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> justVal = justT(100);
    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> nothingVal = nothingT();
    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> outerEmptyVal = outerEmptyT();

    // Error type is Void, so just use null for the error value
    Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> raisedErrorKind = maybeTMonad.raiseError(null);

    @Test
    void raiseError_shouldCreateNothingInOptional() {
      Optional<Maybe<Integer>> result = unwrapT(raisedErrorKind);
      assertThat(result).isPresent().contains(Maybe.nothing());
    }

    @Test
    void handleErrorWith_shouldHandleNothing() {
      // Handler recovers Nothing with MaybeT(Optional.of(Maybe.just(0)))
      Function<Void, Kind<MaybeTKind<OptionalKind<?>, ?>, Integer>> handler = err -> justT(0);

      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> result =
          maybeTMonad.handleErrorWith(nothingVal, handler);

      assertThat(unwrapT(result)).isPresent().contains(Maybe.just(0));
    }

    @Test
    void handleErrorWith_shouldHandleRaisedError() {
      Function<Void, Kind<MaybeTKind<OptionalKind<?>, ?>, Integer>> handler = err -> justT(0);
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> result =
          maybeTMonad.handleErrorWith(raisedErrorKind, handler);
      assertThat(unwrapT(result)).isPresent().contains(Maybe.just(0));
    }

    @Test
    void handleErrorWith_shouldIgnoreJust() {
      Function<Void, Kind<MaybeTKind<OptionalKind<?>, ?>, Integer>> handler =
          err -> justT(-1); // Should not be called
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> result =
          maybeTMonad.handleErrorWith(justVal, handler);
      assertThat(unwrapT(result)).isEqualTo(unwrapT(justVal));
    }

    @Test
    void handleErrorWith_shouldIgnoreOuterEmpty() {
      // handleErrorWith only handles the inner Maybe.nothing() state (Void error).
      // It does not handle errors/emptiness from the outer monad F (Optional).
      Function<Void, Kind<MaybeTKind<OptionalKind<?>, ?>, Integer>> handler =
          err -> justT(-1); // Should not be called
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> result =
          maybeTMonad.handleErrorWith(outerEmptyVal, handler);
      assertThat(unwrapT(result)).isEmpty(); // Stays outer empty
    }

    @Test
    void handleError_shouldHandleNothingWithPureValue() {
      Function<Void, Integer> handler = err -> -99;
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> result =
          maybeTMonad.handleError(nothingVal, handler);
      assertThat(unwrapT(result)).isPresent().contains(Maybe.just(-99));
    }

    @Test
    void handleError_shouldIgnoreJust() {
      Function<Void, Integer> handler = err -> -1;
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> result =
          maybeTMonad.handleError(justVal, handler);
      assertThat(unwrapT(result)).isEqualTo(unwrapT(justVal));
    }

    @Test
    void recoverWith_shouldReplaceNothingWithFallbackKind() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> fallback = justT(0);
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> result =
          maybeTMonad.recoverWith(nothingVal, fallback);
      assertThat(unwrapT(result)).isEqualTo(unwrapT(fallback));
    }

    @Test
    void recoverWith_shouldIgnoreJust() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> fallback = justT(0);
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> result =
          maybeTMonad.recoverWith(justVal, fallback);
      assertThat(unwrapT(result)).isEqualTo(unwrapT(justVal));
    }

    @Test
    void recover_shouldReplaceNothingWithOfValue() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> result = maybeTMonad.recover(nothingVal, 0);
      assertThat(unwrapT(result)).isPresent().contains(Maybe.just(0));
    }

    @Test
    void recover_shouldIgnoreJust() {
      Kind<MaybeTKind<OptionalKind<?>, ?>, Integer> result = maybeTMonad.recover(justVal, 0);
      assertThat(unwrapT(result)).isEqualTo(unwrapT(justVal));
    }
  }
}
