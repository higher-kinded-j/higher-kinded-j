package org.higherkindedj.hkt.trans.optional_t;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OptionalT Monad Tests (Outer: Optional)")
class OptionalTMonadTest {

  // Outer Monad (F = OptionalKind<?>)
  private final Monad<OptionalKind<?>> outerMonad = new OptionalMonad();
  // OptionalT Monad (G = OptionalTKind<OptionalKind<?>, ?>)
  private final MonadError<OptionalTKind<OptionalKind<?>, ?>, Void> optionalTMonad =
      new OptionalTMonad<>(outerMonad);

  // --- Helper Methods ---

  // Unwrap OptionalT<OptionalKind<?>, A> -> Optional<Optional<A>>
  private <A> Optional<Optional<A>> unwrapT(Kind<OptionalTKind<OptionalKind<?>, ?>, A> kind) {
    OptionalT<OptionalKind<?>, A> optionalT = (OptionalT<OptionalKind<?>, A>) kind;
    Kind<OptionalKind<?>, Optional<A>> outerKind = optionalT.value();
    return OptionalKindHelper.unwrap(outerKind);
  }

  // Create OptionalT(Optional.of(Optional.of(value)))
  private <A> Kind<OptionalTKind<OptionalKind<?>, ?>, A> someT(A value) {
    // Use OptionalT.some for non-null values
    if (value == null) {
      // If input is null, 'some' would throw, use 'of' which handles null
      return optionalTMonad.of(null);
    }
    // FIX: Remove the unnecessary cast to (Object).
    // The `if (value == null)` check ensures `value` is non-null here,
    // satisfying the @NonNull constraint of OptionalT.some.
    return OptionalT.some(outerMonad, value);
  }

  // Create OptionalT(Optional.of(Optional.empty()))
  private <A> Kind<OptionalTKind<OptionalKind<?>, ?>, A> noneT() {
    return OptionalT.none(outerMonad);
  }

  // Create OptionalT(Optional.empty()) - Represents failure in the outer context
  private <A> Kind<OptionalTKind<OptionalKind<?>, ?>, A> outerEmptyT() {
    Kind<OptionalKind<?>, Optional<A>> emptyOuter = OptionalKindHelper.wrap(Optional.empty());
    return OptionalT.fromKind(emptyOuter);
  }

  // Helper Functions for Laws
  private final Function<Integer, String> intToString = Object::toString;
  private final Function<String, String> appendWorld = s -> s + " world";
  private final Function<Integer, String> intToStringAppendWorld = intToString.andThen(appendWorld);

  // Function a -> M b (Integer -> OptionalT<Optional, String>)
  private final Function<Integer, Kind<OptionalTKind<OptionalKind<?>, ?>, String>> fT =
      i -> someT("v" + i);
  // Function b -> M c (String -> OptionalT<Optional, String>)
  private final Function<String, Kind<OptionalTKind<OptionalKind<?>, ?>, String>> gT =
      s -> someT(s + "!");

  // --- Basic Operations Tests ---

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldWrapValueAsSomeInOptional() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> kind = optionalTMonad.of(10);
      Optional<Optional<Integer>> result = unwrapT(kind);
      assertThat(result).isPresent().contains(Optional.of(10));
    }

    @Test
    void of_shouldWrapNullAsNoneInOptional() {
      // OptionalTMonad.of uses Optional.ofNullable
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> kind = optionalTMonad.of(null);
      Optional<Optional<Integer>> result = unwrapT(kind);
      assertThat(result).isPresent().contains(Optional.empty());
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionWhenSome() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> input = someT(5);
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> result =
          optionalTMonad.map(Object::toString, input);
      assertThat(unwrapT(result)).isPresent().contains(Optional.of("5"));
    }

    @Test
    void map_shouldPropagateNone() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> input = noneT();
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> result =
          optionalTMonad.map(Object::toString, input);
      assertThat(unwrapT(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void map_shouldPropagateOuterEmpty() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> input = outerEmptyT();
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> result =
          optionalTMonad.map(Object::toString, input);
      assertThat(unwrapT(result)).isEmpty();
    }

    @Test
    void map_shouldHandleMappingToNullAsNone() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> input = someT(5);
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> result = optionalTMonad.map(x -> null, input);
      // Optional.map(null) results in Optional.empty()
      assertThat(unwrapT(result)).isPresent().contains(Optional.empty());
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    Kind<OptionalTKind<OptionalKind<?>, ?>, Function<Integer, String>> funcKindSome =
        someT((Function<Integer, String>) x -> "N" + x);
    Kind<OptionalTKind<OptionalKind<?>, ?>, Function<Integer, String>> funcKindNone = noneT();
    Kind<OptionalTKind<OptionalKind<?>, ?>, Function<Integer, String>> funcKindOuterEmpty =
        outerEmptyT();

    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> valKindSome = someT(10);
    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> valKindNone = noneT();
    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> valKindOuterEmpty = outerEmptyT();

    @Test
    void ap_shouldApplySomeFuncToSomeValue() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> result =
          optionalTMonad.ap(funcKindSome, valKindSome);
      assertThat(unwrapT(result)).isPresent().contains(Optional.of("N10"));
    }

    @Test
    void ap_shouldReturnNoneIfFuncIsNone() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> result =
          optionalTMonad.ap(funcKindNone, valKindSome);
      assertThat(unwrapT(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void ap_shouldReturnNoneIfValueIsNone() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> result =
          optionalTMonad.ap(funcKindSome, valKindNone);
      assertThat(unwrapT(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void ap_shouldReturnNoneIfBothAreNone() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> result =
          optionalTMonad.ap(funcKindNone, valKindNone);
      assertThat(unwrapT(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void ap_shouldReturnOuterEmptyIfFuncIsOuterEmpty() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> result =
          optionalTMonad.ap(funcKindOuterEmpty, valKindSome);
      assertThat(unwrapT(result)).isEmpty();
    }

    @Test
    void ap_shouldReturnOuterEmptyIfValueIsOuterEmpty() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> result =
          optionalTMonad.ap(funcKindSome, valKindOuterEmpty);
      assertThat(unwrapT(result)).isEmpty();
    }

    @Test
    void ap_shouldReturnOuterEmptyIfBothAreOuterEmpty() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> result =
          optionalTMonad.ap(funcKindOuterEmpty, valKindOuterEmpty);
      assertThat(unwrapT(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    Function<Integer, Kind<OptionalTKind<OptionalKind<?>, ?>, Double>> safeDivideT =
        num -> (num == 0) ? noneT() : someT(100.0 / num);
    Function<Integer, Kind<OptionalTKind<OptionalKind<?>, ?>, Double>> outerEmptyResultT =
        num -> outerEmptyT();

    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> someValue = someT(5);
    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> zeroValue = someT(0);
    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> noneValue = noneT();
    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> outerEmptyValue = outerEmptyT();

    @Test
    void flatMap_shouldApplyFuncWhenSome() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Double> result =
          optionalTMonad.flatMap(safeDivideT, someValue);
      assertThat(unwrapT(result)).isPresent().contains(Optional.of(20.0));
    }

    @Test
    void flatMap_shouldReturnNoneWhenFuncReturnsNone() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Double> result =
          optionalTMonad.flatMap(safeDivideT, zeroValue);
      assertThat(unwrapT(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void flatMap_shouldPropagateNoneWhenInputIsNone() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Double> result =
          optionalTMonad.flatMap(safeDivideT, noneValue);
      assertThat(unwrapT(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void flatMap_shouldPropagateOuterEmptyWhenInputIsOuterEmpty() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Double> result =
          optionalTMonad.flatMap(safeDivideT, outerEmptyValue);
      assertThat(unwrapT(result)).isEmpty();
    }

    @Test
    void flatMap_shouldPropagateOuterEmptyWhenFuncReturnsOuterEmpty() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Double> result =
          optionalTMonad.flatMap(outerEmptyResultT, someValue);
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
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> fa = someT(10);
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> faNone = noneT();
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> faOuterEmpty = outerEmptyT();

      assertThat(unwrapT(optionalTMonad.map(Function.identity(), fa))).isEqualTo(unwrapT(fa));
      assertThat(unwrapT(optionalTMonad.map(Function.identity(), faNone)))
          .isEqualTo(unwrapT(faNone));
      assertThat(unwrapT(optionalTMonad.map(Function.identity(), faOuterEmpty)))
          .isEqualTo(unwrapT(faOuterEmpty));
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> fa = someT(10);
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> faNone = noneT();
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> faOuterEmpty = outerEmptyT();

      Kind<OptionalTKind<OptionalKind<?>, ?>, String> leftSide =
          optionalTMonad.map(intToStringAppendWorld, fa);
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> rightSide =
          optionalTMonad.map(appendWorld, optionalTMonad.map(intToString, fa));

      Kind<OptionalTKind<OptionalKind<?>, ?>, String> leftSideNone =
          optionalTMonad.map(intToStringAppendWorld, faNone);
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> rightSideNone =
          optionalTMonad.map(appendWorld, optionalTMonad.map(intToString, faNone));

      Kind<OptionalTKind<OptionalKind<?>, ?>, String> leftSideOuterEmpty =
          optionalTMonad.map(intToStringAppendWorld, faOuterEmpty);
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> rightSideOuterEmpty =
          optionalTMonad.map(appendWorld, optionalTMonad.map(intToString, faOuterEmpty));

      assertThat(unwrapT(leftSide)).isEqualTo(unwrapT(rightSide));
      assertThat(unwrapT(leftSideNone)).isEqualTo(unwrapT(rightSideNone));
      assertThat(unwrapT(leftSideOuterEmpty)).isEqualTo(unwrapT(rightSideOuterEmpty));
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {

    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> v = someT(5);
    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> vNone = noneT();
    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> vOuterEmpty = outerEmptyT();
    Kind<OptionalTKind<OptionalKind<?>, ?>, Function<Integer, String>> fKind = someT(intToString);
    Kind<OptionalTKind<OptionalKind<?>, ?>, Function<Integer, String>> fKindNone = noneT();
    Kind<OptionalTKind<OptionalKind<?>, ?>, Function<String, String>> gKind = someT(appendWorld);

    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Function<Integer, Integer>> idFuncKind =
          optionalTMonad.of(Function.identity());
      assertThat(unwrapT(optionalTMonad.ap(idFuncKind, v))).isEqualTo(unwrapT(v));
      assertThat(unwrapT(optionalTMonad.ap(idFuncKind, vNone))).isEqualTo(unwrapT(vNone));
      assertThat(unwrapT(optionalTMonad.ap(idFuncKind, vOuterEmpty)))
          .isEqualTo(unwrapT(vOuterEmpty));
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      int x = 10;
      Function<Integer, String> f = intToString;
      Kind<OptionalTKind<OptionalKind<?>, ?>, Function<Integer, String>> apFunc =
          optionalTMonad.of(f);
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> apVal = optionalTMonad.of(x);

      Kind<OptionalTKind<OptionalKind<?>, ?>, String> leftSide = optionalTMonad.ap(apFunc, apVal);
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> rightSide = optionalTMonad.of(f.apply(x));

      assertThat(unwrapT(leftSide)).isEqualTo(unwrapT(rightSide));
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 20;
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> leftSide =
          optionalTMonad.ap(fKind, optionalTMonad.of(y));
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> leftSideFuncNone =
          optionalTMonad.ap(fKindNone, optionalTMonad.of(y));

      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      Kind<OptionalTKind<OptionalKind<?>, ?>, Function<Function<Integer, String>, String>>
          evalKind = optionalTMonad.of(evalWithY);

      Kind<OptionalTKind<OptionalKind<?>, ?>, String> rightSide =
          optionalTMonad.ap(evalKind, fKind);
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> rightSideFuncNone =
          optionalTMonad.ap(evalKind, fKindNone);

      assertThat(unwrapT(leftSide)).isEqualTo(unwrapT(rightSide));
      assertThat(unwrapT(leftSideFuncNone)).isEqualTo(unwrapT(rightSideFuncNone));
    }

    @Test
    @DisplayName("4. Composition: ap(ap(map(compose, gKind), fKind), v) == ap(gKind, ap(fKind, v))")
    void composition() {
      Function<
              Function<String, String>,
              Function<Function<Integer, String>, Function<Integer, String>>>
          composeMap = gg -> ff -> gg.compose(ff);

      Kind<
              OptionalTKind<OptionalKind<?>, ?>,
              Function<Function<Integer, String>, Function<Integer, String>>>
          mappedCompose = optionalTMonad.map(composeMap, gKind);
      Kind<OptionalTKind<OptionalKind<?>, ?>, Function<Integer, String>> ap1 =
          optionalTMonad.ap(mappedCompose, fKind);
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> leftSide = optionalTMonad.ap(ap1, v);

      Kind<OptionalTKind<OptionalKind<?>, ?>, String> innerAp = optionalTMonad.ap(fKind, v);
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> rightSide = optionalTMonad.ap(gKind, innerAp);

      assertThat(unwrapT(leftSide)).isEqualTo(unwrapT(rightSide));
      // Add tests for None/OuterEmpty propagation if needed
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {
    int value = 5;
    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> mValue = someT(value);
    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> mValueNone = noneT();
    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> mValueOuterEmpty = outerEmptyT();

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> ofValue = optionalTMonad.of(value);
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> leftSide =
          optionalTMonad.flatMap(fT, ofValue);
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> rightSide = fT.apply(value);

      assertThat(unwrapT(leftSide)).isEqualTo(unwrapT(rightSide));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<OptionalTKind<OptionalKind<?>, ?>, Integer>> ofFunc =
          i -> optionalTMonad.of(i);

      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> leftSide =
          optionalTMonad.flatMap(ofFunc, mValue);
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> leftSideNone =
          optionalTMonad.flatMap(ofFunc, mValueNone);
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> leftSideOuterEmpty =
          optionalTMonad.flatMap(ofFunc, mValueOuterEmpty);

      assertThat(unwrapT(leftSide)).isEqualTo(unwrapT(mValue));
      assertThat(unwrapT(leftSideNone)).isEqualTo(unwrapT(mValueNone));
      assertThat(unwrapT(leftSideOuterEmpty)).isEqualTo(unwrapT(mValueOuterEmpty));
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> innerFlatMap =
          optionalTMonad.flatMap(fT, mValue);
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> leftSide =
          optionalTMonad.flatMap(gT, innerFlatMap);

      Function<Integer, Kind<OptionalTKind<OptionalKind<?>, ?>, String>> rightSideFunc =
          a -> optionalTMonad.flatMap(gT, fT.apply(a));
      Kind<OptionalTKind<OptionalKind<?>, ?>, String> rightSide =
          optionalTMonad.flatMap(rightSideFunc, mValue);

      assertThat(unwrapT(leftSide)).isEqualTo(unwrapT(rightSide));
      // Add tests for None/OuterEmpty propagation if needed
    }
  }

  // --- MonadError Tests (Error Type E = Void) ---

  @Nested
  @DisplayName("MonadError Tests")
  class MonadErrorTests {
    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> someVal = someT(100);
    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> noneVal = noneT();
    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> outerEmptyVal = outerEmptyT();

    // Error type is Void, so just use null for the error value
    Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> raisedErrorKind =
        optionalTMonad.raiseError(null);

    @Test
    void raiseError_shouldCreateNoneInOptional() {
      Optional<Optional<Integer>> result = unwrapT(raisedErrorKind);
      assertThat(result).isPresent().contains(Optional.empty());
    }

    @Test
    void handleErrorWith_shouldHandleNone() {
      // Handler recovers empty Optional with OptionalT(Optional.of(Optional.of(0)))
      Function<Void, Kind<OptionalTKind<OptionalKind<?>, ?>, Integer>> handler = err -> someT(0);

      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> result =
          optionalTMonad.handleErrorWith(noneVal, handler);

      assertThat(unwrapT(result)).isPresent().contains(Optional.of(0));
    }

    @Test
    void handleErrorWith_shouldHandleRaisedError() {
      Function<Void, Kind<OptionalTKind<OptionalKind<?>, ?>, Integer>> handler = err -> someT(0);

      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> result =
          optionalTMonad.handleErrorWith(raisedErrorKind, handler);

      assertThat(unwrapT(result)).isPresent().contains(Optional.of(0));
    }

    @Test
    void handleErrorWith_shouldIgnoreSome() {
      Function<Void, Kind<OptionalTKind<OptionalKind<?>, ?>, Integer>> handler =
          err -> someT(-1); // Should not be called

      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> result =
          optionalTMonad.handleErrorWith(someVal, handler);

      assertThat(unwrapT(result)).isEqualTo(unwrapT(someVal));
    }

    @Test
    void handleErrorWith_shouldIgnoreOuterEmpty() {
      // handleErrorWith only handles the inner Optional.empty state (Void error).
      // It does not handle errors/emptiness from the outer monad F.
      Function<Void, Kind<OptionalTKind<OptionalKind<?>, ?>, Integer>> handler =
          err -> someT(-1); // Should not be called

      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> result =
          optionalTMonad.handleErrorWith(outerEmptyVal, handler);
      assertThat(unwrapT(result)).isEmpty(); // Stays outer empty
    }

    @Test
    void handleError_shouldHandleNoneWithPureValue() {
      Function<Void, Integer> handler = err -> -99;
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> result =
          optionalTMonad.handleError(noneVal, handler);
      assertThat(unwrapT(result)).isPresent().contains(Optional.of(-99));
    }

    @Test
    void handleError_shouldIgnoreSome() {
      Function<Void, Integer> handler = err -> -1;
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> result =
          optionalTMonad.handleError(someVal, handler);
      assertThat(unwrapT(result)).isEqualTo(unwrapT(someVal));
    }

    @Test
    void recoverWith_shouldReplaceNoneWithFallbackKind() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> fallback = someT(0);
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> result =
          optionalTMonad.recoverWith(noneVal, fallback);
      assertThat(unwrapT(result)).isEqualTo(unwrapT(fallback));
    }

    @Test
    void recoverWith_shouldIgnoreSome() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> fallback = someT(0);
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> result =
          optionalTMonad.recoverWith(someVal, fallback);
      assertThat(unwrapT(result)).isEqualTo(unwrapT(someVal));
    }

    @Test
    void recover_shouldReplaceNoneWithOfValue() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> result = optionalTMonad.recover(noneVal, 0);
      assertThat(unwrapT(result)).isPresent().contains(Optional.of(0));
    }

    @Test
    void recover_shouldIgnoreSome() {
      Kind<OptionalTKind<OptionalKind<?>, ?>, Integer> result = optionalTMonad.recover(someVal, 0);
      assertThat(unwrapT(result)).isEqualTo(unwrapT(someVal));
    }
  }
}
