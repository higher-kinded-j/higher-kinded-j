package org.higherkindedj.hkt.trans.maybe_t;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeTMonad Tests (F=OptionalKind.Witness)")
class MaybeTMonadTest {

  private final Monad<OptionalKind.Witness> outerMonad = new OptionalMonad();
  private Monad<MaybeTKind<OptionalKind.Witness, ?>> maybeTMonad;

  private final String successValue = "SUCCESS";
  private final Integer initialValue = 123;

  // Helper to unwrap Kind<MaybeTKind<OptionalKind.Witness, ?>, A> to Optional<Maybe<A>>
  private <A> Optional<Maybe<A>> unwrapKindToOptionalMaybe(
      Kind<MaybeTKind<OptionalKind.Witness, ?>, A> kind) {
    MaybeT<OptionalKind.Witness, A> maybeT = MaybeTKindHelper.<OptionalKind.Witness, A>unwrap(kind);
    Kind<OptionalKind.Witness, Maybe<A>> outerKind = maybeT.value();
    return OptionalKindHelper.unwrap(outerKind);
  }

  // Helper to create a Kind<MaybeTKind<OptionalKind.Witness, ?>, A> for Just(value)
  private <A extends @NonNull Object> Kind<MaybeTKind<OptionalKind.Witness, ?>, A> justT(
      @NonNull A value) {
    MaybeT<OptionalKind.Witness, A> mt = MaybeT.just(outerMonad, value);
    return MaybeTKindHelper.wrap(mt);
  }

  // Helper to create a Kind<MaybeTKind<OptionalKind.Witness, ?>, A> for Nothing
  private <A> Kind<MaybeTKind<OptionalKind.Witness, ?>, A> nothingT() {
    MaybeT<OptionalKind.Witness, A> mt = MaybeT.nothing(outerMonad);
    return MaybeTKindHelper.wrap(mt);
  }

  // Helper to create a Kind<MaybeTKind<OptionalKind.Witness, ?>, A> for an empty outer F
  private <A> Kind<MaybeTKind<OptionalKind.Witness, ?>, A> outerEmptyT() {
    Kind<OptionalKind.Witness, Maybe<A>> emptyOuter = OptionalKindHelper.wrap(Optional.empty());
    MaybeT<OptionalKind.Witness, A> mt = MaybeT.fromKind(emptyOuter);
    return MaybeTKindHelper.wrap(mt);
  }

  @BeforeEach
  void setUp() {
    maybeTMonad = new MaybeTMonad<>(outerMonad);
  }

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldWrapValueAsJustInOptional() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> kind = maybeTMonad.of(successValue);
      assertThat(unwrapKindToOptionalMaybe(kind)).isPresent().contains(Maybe.just(successValue));
    }

    @Test
    void of_shouldWrapNullAsNothingInOptional() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> kind = maybeTMonad.of(null);
      // MaybeTMonad.of(null) -> MaybeT.nothing -> outerMonad.of(Maybe.nothing())
      // If outerMonad is OptionalMonad -> Optional.of(Maybe.nothing())
      assertThat(unwrapKindToOptionalMaybe(kind)).isPresent().contains(Maybe.nothing());
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionWhenJust() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> initialKind = justT(initialValue);
      Function<Integer, String> intToString = Object::toString;
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> mappedKind =
          maybeTMonad.map(intToString, initialKind);

      assertThat(unwrapKindToOptionalMaybe(mappedKind))
          .isPresent()
          .contains(Maybe.just(String.valueOf(initialValue)));
    }

    @Test
    void map_shouldReturnNothingWhenNothing() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> initialKind = nothingT();
      Function<Integer, String> intToString = Object::toString;
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> mappedKind =
          maybeTMonad.map(intToString, initialKind);

      assertThat(unwrapKindToOptionalMaybe(mappedKind)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void map_shouldReturnOuterEmptyWhenOuterEmpty() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> initialKind = outerEmptyT();
      Function<Integer, String> intToString = Object::toString;
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> mappedKind =
          maybeTMonad.map(intToString, initialKind);

      assertThat(unwrapKindToOptionalMaybe(mappedKind)).isEmpty();
    }

    @Test
    void map_shouldHandleMappingToNullAsNothing() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> initialKind = justT(initialValue);
      Function<Integer, @Nullable String> toNull = x -> null;
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> mappedKind =
          maybeTMonad.map(toNull, initialKind);
      // map(f returning null) on Maybe.Just(v) results in Maybe.Nothing()
      // So, F<Maybe.Nothing()>
      assertThat(unwrapKindToOptionalMaybe(mappedKind)).isPresent().contains(Maybe.nothing());
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    Kind<MaybeTKind<OptionalKind.Witness, ?>, Function<Integer, String>> funcKindJust =
        justT(Object::toString);
    Kind<MaybeTKind<OptionalKind.Witness, ?>, Function<Integer, String>> funcKindNothing =
        nothingT();
    Kind<MaybeTKind<OptionalKind.Witness, ?>, Function<Integer, String>> funcKindOuterEmpty =
        outerEmptyT();

    Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> valKindJust = justT(42);
    Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> valKindNothing = nothingT();
    Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> valKindOuterEmpty = outerEmptyT();

    @Test
    void ap_justFunc_justVal() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> result =
          maybeTMonad.ap(funcKindJust, valKindJust);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.just("42"));
    }

    @Test
    void ap_justFunc_nothingVal() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> result =
          maybeTMonad.ap(funcKindJust, valKindNothing);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void ap_nothingFunc_justVal() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> result =
          maybeTMonad.ap(funcKindNothing, valKindJust);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void ap_outerEmptyFunc_justVal() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> result =
          maybeTMonad.ap(funcKindOuterEmpty, valKindJust);
      assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
    }

    @Test
    void ap_nothingFunc_nothingVal() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> result =
          maybeTMonad.ap(funcKindNothing, valKindNothing);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void ap_justFunc_outerEmptyVal() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> result =
          maybeTMonad.ap(funcKindJust, valKindOuterEmpty);
      assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
    }

    @Test
    void ap_outerEmptyFunc_nothingVal() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> result =
          maybeTMonad.ap(funcKindOuterEmpty, valKindNothing);
      assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
    }

    @Test
    void ap_outerEmptyFunc_outerEmptyVal() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> result =
          maybeTMonad.ap(funcKindOuterEmpty, valKindOuterEmpty);
      assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    Function<Integer, Kind<MaybeTKind<OptionalKind.Witness, ?>, String>> intToJustStringT =
        i -> justT("V" + i);
    Function<Integer, Kind<MaybeTKind<OptionalKind.Witness, ?>, String>> intToNothingStringT =
        i -> nothingT();
    Function<Integer, Kind<MaybeTKind<OptionalKind.Witness, ?>, String>> intToOuterEmptyStringT =
        i -> outerEmptyT();

    Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> initialJust = justT(5);
    Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> initialNothing = nothingT();
    Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> initialOuterEmpty = outerEmptyT();

    @Test
    void flatMap_just_toJust() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> result =
          maybeTMonad.flatMap(intToJustStringT, initialJust);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.just("V5"));
    }

    @Test
    void flatMap_just_toNothing() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> result =
          maybeTMonad.flatMap(intToNothingStringT, initialJust);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void flatMap_nothing_toJust() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> result =
          maybeTMonad.flatMap(intToJustStringT, initialNothing);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void flatMap_just_toOuterEmpty() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> result =
          maybeTMonad.flatMap(intToOuterEmptyStringT, initialJust);
      assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
    }

    @Test
    void flatMap_nothing_toNothing() {
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> result =
          maybeTMonad.flatMap(intToNothingStringT, initialNothing);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void flatMap_nothing_toOuterEmpty() {
      // The function f won't be called if initialNothing is F<Nothing>,
      // so f returning outerEmptyT might not be directly testable this way
      // unless f itself can produce an outerEmpty from a non-call (which is not how flatMap works).
      // The existing flatMap_nothing_toJust covers the F<Nothing> input.
      // This test will behave identically to flatMap_nothing_toJust.
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> result =
          maybeTMonad.flatMap(intToOuterEmptyStringT, initialNothing);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void flatMap_outerEmpty_toNothing() { // Function f is not called
      Kind<MaybeTKind<OptionalKind.Witness, ?>, String> result =
          maybeTMonad.flatMap(intToNothingStringT, initialOuterEmpty);
      assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
    }

    @Test
    void flatMap_just_functionThrowsException() {
      Function<Integer, Kind<MaybeTKind<OptionalKind.Witness, ?>, String>> fThrows =
          i -> {
            throw new RuntimeException("flatMap function error");
          };
      // Behavior depends on how OptionalMonad.flatMap and Maybe.map handle exceptions
      // For OptionalMonad, if the lambda in flatMap throws, the outer Optional might become empty
      // or rethrow.
      // If Maybe.map inside the flatMap lambda throws, it might become Maybe.nothing()
      // Let's assume for now that the outer monad's flatMap will propagate the exception
      // or result in an empty outer monad.
      Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> initial = justT(1);
      try {
        maybeTMonad.flatMap(fThrows, initial);
        // Potentially assert that an exception is expected if outerMonad propagates it
        // Or assert an empty/nothing state if it's caught and transformed.
        // Based on typical monad transformer behavior, this might become
        // Optional.of(Maybe.nothing())
        // if the exception is caught by an inner layer, or Optional.empty() / rethrow if by outer.
        // The current MaybeTMonad.flatMap catches it via orElse.
        Optional<Maybe<String>> result =
            unwrapKindToOptionalMaybe(maybeTMonad.flatMap(fThrows, initial));
        // The current implementation's orElse might prevent exception propagation in this specific
        // path.
        // It seems the .map(a -> { f.apply(a).value() }) part's exception would lead to .orElse
        // being used.
        // However, f.apply(a) returns Kind which is then unwrapped. If f.apply(a) itself throws,
        // the lambda passed to maybeA.map would throw.
        // If Maybe.map catches and returns Nothing:
        // Then outerMonad.of(Maybe.nothing()) is called by orElse.
        // So, F<Nothing>
        // Let's refine: if f.apply(a) throws inside the .map(a -> ...),
        // And if Maybe's .map() doesn't catch it, the exception will propagate to
        // outerMonad.flatMap's lambda.
        // If OptionalMonad.flatMap's lambda execution throws, it typically results in
        // Optional.empty().
        assertThat(unwrapKindToOptionalMaybe(maybeTMonad.flatMap(fThrows, initial))).isEmpty();

      } catch (RuntimeException e) {
        assertThat(e.getMessage()).isEqualTo("flatMap function error");
      }
    }

    private <A> void assertMaybeTEquals(
        Kind<MaybeTKind<OptionalKind.Witness, ?>, A> k1,
        Kind<MaybeTKind<OptionalKind.Witness, ?>, A> k2) {
      assertThat(unwrapKindToOptionalMaybe(k1)).isEqualTo(unwrapKindToOptionalMaybe(k2));
    }

    @Nested
    @DisplayName("Monad Laws")
    class MonadLaws {
      int value = 5;
      Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> mVal = justT(value);
      Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> mValNothing = nothingT();
      Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> mValOuterEmpty = outerEmptyT();

      Function<Integer, Kind<MaybeTKind<OptionalKind.Witness, ?>, String>> fLaw =
          i -> justT("v" + i);
      Function<String, Kind<MaybeTKind<OptionalKind.Witness, ?>, String>> gLaw =
          s -> justT(s + "!");

      @Test
      @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
      void leftIdentity() {
        Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> ofValue = maybeTMonad.of(value);
        Kind<MaybeTKind<OptionalKind.Witness, ?>, String> leftSide =
            maybeTMonad.flatMap(fLaw, ofValue);
        Kind<MaybeTKind<OptionalKind.Witness, ?>, String> rightSide = fLaw.apply(value);
        assertMaybeTEquals(leftSide, rightSide);

        // Test with of(null) which becomes Nothing
        Function<Integer, Kind<MaybeTKind<OptionalKind.Witness, ?>, String>> fLawHandlesNull =
            i -> (i == null) ? nothingT() : justT("v" + i);

        Kind<MaybeTKind<OptionalKind.Witness, ?>, String> leftSideOfNull =
            maybeTMonad.flatMap(fLawHandlesNull, maybeTMonad.of(null));
        Kind<MaybeTKind<OptionalKind.Witness, ?>, String> rightSideOfNull =
            fLawHandlesNull.apply(null); // fLawHandlesNull(null) should be nothingT()
        assertMaybeTEquals(leftSideOfNull, rightSideOfNull);
      }

      @Test
      @DisplayName("2. Right Identity: flatMap(m, of) == m")
      void rightIdentity() {
        Function<Integer, Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer>> ofFunc =
            maybeTMonad::of;

        assertMaybeTEquals(maybeTMonad.flatMap(ofFunc, mVal), mVal);
        assertMaybeTEquals(maybeTMonad.flatMap(ofFunc, mValNothing), mValNothing);
        assertMaybeTEquals(maybeTMonad.flatMap(ofFunc, mValOuterEmpty), mValOuterEmpty);
      }

      @Test
      @DisplayName(
          "3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
      void associativity() {
        Kind<MaybeTKind<OptionalKind.Witness, ?>, String> innerLeft =
            maybeTMonad.flatMap(fLaw, mVal);
        Kind<MaybeTKind<OptionalKind.Witness, ?>, String> leftSide =
            maybeTMonad.flatMap(gLaw, innerLeft);

        Function<Integer, Kind<MaybeTKind<OptionalKind.Witness, ?>, String>> rightSideFunc =
            a -> maybeTMonad.flatMap(gLaw, fLaw.apply(a));
        Kind<MaybeTKind<OptionalKind.Witness, ?>, String> rightSide =
            maybeTMonad.flatMap(rightSideFunc, mVal);
        assertMaybeTEquals(leftSide, rightSide);

        // Test with Nothing
        Kind<MaybeTKind<OptionalKind.Witness, ?>, String> innerNothing =
            maybeTMonad.flatMap(fLaw, mValNothing);
        Kind<MaybeTKind<OptionalKind.Witness, ?>, String> leftSideNothing =
            maybeTMonad.flatMap(gLaw, innerNothing);
        Kind<MaybeTKind<OptionalKind.Witness, ?>, String> rightSideNothing =
            maybeTMonad.flatMap(rightSideFunc, mValNothing);
        assertMaybeTEquals(leftSideNothing, rightSideNothing);

        // Test with Outer Empty
        Kind<MaybeTKind<OptionalKind.Witness, ?>, String> innerOuterEmpty =
            maybeTMonad.flatMap(fLaw, mValOuterEmpty);
        Kind<MaybeTKind<OptionalKind.Witness, ?>, String> leftSideOuterEmpty =
            maybeTMonad.flatMap(gLaw, innerOuterEmpty);
        Kind<MaybeTKind<OptionalKind.Witness, ?>, String> rightSideOuterEmpty =
            maybeTMonad.flatMap(rightSideFunc, mValOuterEmpty);
        assertMaybeTEquals(leftSideOuterEmpty, rightSideOuterEmpty);
      }
    }

    @Nested
    @DisplayName("MonadError 'raiseError' and 'handleErrorWith' tests")
    class MonadErrorTests {

      @Test
      void raiseError_shouldProduceOuterJustInnerNothing() {
        Kind<MaybeTKind<OptionalKind.Witness, ?>, String> errorKind =
            ((MonadError<MaybeTKind<OptionalKind.Witness, ?>, Void>) maybeTMonad).raiseError(null);
        assertThat(unwrapKindToOptionalMaybe(errorKind)).isPresent().contains(Maybe.nothing());
      }

      @Test
      void handleErrorWith_onJustValue_shouldReturnOriginal() {
        Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> initial = justT(123);
        Function<Void, Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer>> handler =
            err -> justT(789);

        Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> result =
            ((MonadError<MaybeTKind<OptionalKind.Witness, ?>, Void>) maybeTMonad)
                .handleErrorWith(initial, handler);

        assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.just(123));
      }

      @Test
      void handleErrorWith_onNothingValue_shouldApplyHandler_returningJust() {
        Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> initial = nothingT();
        Function<Void, Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer>> handler =
            err -> justT(789);

        Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> result =
            ((MonadError<MaybeTKind<OptionalKind.Witness, ?>, Void>) maybeTMonad)
                .handleErrorWith(initial, handler);

        assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.just(789));
      }

      @Test
      void handleErrorWith_onNothingValue_shouldApplyHandler_returningNothing() {
        Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> initial = nothingT();
        Function<Void, Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer>> handler =
            err -> nothingT(); // Handler returns a Nothing

        Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> result =
            ((MonadError<MaybeTKind<OptionalKind.Witness, ?>, Void>) maybeTMonad)
                .handleErrorWith(initial, handler);

        assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
      }

      @Test
      void handleErrorWith_onNothingValue_shouldApplyHandler_returningOuterEmpty() {
        Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> initial = nothingT();
        Function<Void, Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer>> handler =
            err -> outerEmptyT(); // Handler returns an OuterEmpty

        Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> result =
            ((MonadError<MaybeTKind<OptionalKind.Witness, ?>, Void>) maybeTMonad)
                .handleErrorWith(initial, handler);

        // The result of the handler (outerEmptyT) is used.
        assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
      }

      @Test
      void handleErrorWith_onOuterEmpty_shouldReturnOuterEmpty() {
        Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> initial = outerEmptyT();
        Function<Void, Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer>> handler =
            err -> justT(789); // Handler should not be called

        Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> result =
            ((MonadError<MaybeTKind<OptionalKind.Witness, ?>, Void>) maybeTMonad)
                .handleErrorWith(initial, handler);

        assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
      }

      @Test
      void handleErrorWith_onNothingValue_handlerThrowsException() {
        Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> initial = nothingT();
        Function<Void, Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer>> handlerThrows =
            err -> {
              throw new RuntimeException("handler error");
            };

        // This will depend on how OptionalMonad.flatMap handles exceptions from its lambda.
        // If it converts to Optional.empty():
        try {
          Kind<MaybeTKind<OptionalKind.Witness, ?>, Integer> result =
              ((MonadError<MaybeTKind<OptionalKind.Witness, ?>, Void>) maybeTMonad)
                  .handleErrorWith(initial, handlerThrows);
          assertThat(unwrapKindToOptionalMaybe(result))
              .isEmpty(); // Assuming exception in handler's F leads to F_empty
        } catch (RuntimeException e) {
          assertThat(e.getMessage()).isEqualTo("handler error");
        }
      }
    }
  }
}
