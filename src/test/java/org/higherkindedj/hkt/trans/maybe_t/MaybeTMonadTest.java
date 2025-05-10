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
  private Monad<MaybeTKind.Witness<OptionalKind.Witness>> maybeTMonad;

  private final String successValue = "SUCCESS";
  private final Integer initialValue = 123;

  private <A> Optional<Maybe<A>> unwrapKindToOptionalMaybe(
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, A> kind) {
    MaybeT<OptionalKind.Witness, A> maybeT = MaybeTKindHelper.<OptionalKind.Witness, A>unwrap(kind);
    Kind<OptionalKind.Witness, Maybe<A>> outerKind = maybeT.value();
    return OptionalKindHelper.unwrap(outerKind);
  }

  private <A extends @NonNull Object> Kind<MaybeTKind.Witness<OptionalKind.Witness>, A> justT(
      @NonNull A value) {
    MaybeT<OptionalKind.Witness, A> mt = MaybeT.just(outerMonad, value);
    return MaybeTKindHelper.wrap(mt);
  }

  private <A> Kind<MaybeTKind.Witness<OptionalKind.Witness>, A> nothingT() {
    MaybeT<OptionalKind.Witness, A> mt = MaybeT.nothing(outerMonad);
    return MaybeTKindHelper.wrap(mt);
  }

  private <A> Kind<MaybeTKind.Witness<OptionalKind.Witness>, A> outerEmptyT() {
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
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> kind = maybeTMonad.of(successValue);
      assertThat(unwrapKindToOptionalMaybe(kind)).isPresent().contains(Maybe.just(successValue));
    }

    @Test
    void of_shouldWrapNullAsNothingInOptional() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> kind = maybeTMonad.of(null);
      assertThat(unwrapKindToOptionalMaybe(kind)).isPresent().contains(Maybe.nothing());
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionWhenJust() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initialKind = justT(initialValue);
      Function<Integer, String> intToString = Object::toString;
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> mappedKind =
          maybeTMonad.map(intToString, initialKind);

      assertThat(unwrapKindToOptionalMaybe(mappedKind))
          .isPresent()
          .contains(Maybe.just(String.valueOf(initialValue)));
    }

    @Test
    void map_shouldReturnNothingWhenNothing() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initialKind = nothingT();
      Function<Integer, String> intToString = Object::toString;
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> mappedKind =
          maybeTMonad.map(intToString, initialKind);

      assertThat(unwrapKindToOptionalMaybe(mappedKind)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void map_shouldReturnOuterEmptyWhenOuterEmpty() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initialKind = outerEmptyT();
      Function<Integer, String> intToString = Object::toString;
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> mappedKind =
          maybeTMonad.map(intToString, initialKind);

      assertThat(unwrapKindToOptionalMaybe(mappedKind)).isEmpty();
    }

    @Test
    void map_shouldHandleMappingToNullAsNothing() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initialKind = justT(initialValue);
      Function<Integer, @Nullable String> toNull = x -> null;
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> mappedKind =
          maybeTMonad.map(toNull, initialKind);
      assertThat(unwrapKindToOptionalMaybe(mappedKind)).isPresent().contains(Maybe.nothing());
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    Kind<MaybeTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> funcKindJust =
        justT(Object::toString);
    Kind<MaybeTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> funcKindNothing =
        nothingT();
    Kind<MaybeTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> funcKindOuterEmpty =
        outerEmptyT();

    Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> valKindJust = justT(42);
    Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> valKindNothing = nothingT();
    Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> valKindOuterEmpty = outerEmptyT();

    @Test
    void ap_justFunc_justVal() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> result =
          maybeTMonad.ap(funcKindJust, valKindJust);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.just("42"));
    }

    @Test
    void ap_justFunc_nothingVal() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> result =
          maybeTMonad.ap(funcKindJust, valKindNothing);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void ap_nothingFunc_justVal() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> result =
          maybeTMonad.ap(funcKindNothing, valKindJust);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void ap_outerEmptyFunc_justVal() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> result =
          maybeTMonad.ap(funcKindOuterEmpty, valKindJust);
      assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
    }

    @Test
    void ap_nothingFunc_nothingVal() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> result =
          maybeTMonad.ap(funcKindNothing, valKindNothing);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void ap_justFunc_outerEmptyVal() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> result =
          maybeTMonad.ap(funcKindJust, valKindOuterEmpty);
      assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
    }

    @Test
    void ap_outerEmptyFunc_nothingVal() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> result =
          maybeTMonad.ap(funcKindOuterEmpty, valKindNothing);
      assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
    }

    @Test
    void ap_outerEmptyFunc_outerEmptyVal() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> result =
          maybeTMonad.ap(funcKindOuterEmpty, valKindOuterEmpty);
      assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> intToJustStringT =
        i -> justT("V" + i);
    Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> intToNothingStringT =
        i -> nothingT();
    Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>>
        intToOuterEmptyStringT = i -> outerEmptyT();

    Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initialJust = justT(5);
    Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initialNothing = nothingT();
    Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initialOuterEmpty = outerEmptyT();

    @Test
    void flatMap_just_toJust() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> result =
          maybeTMonad.flatMap(intToJustStringT, initialJust);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.just("V5"));
    }

    @Test
    void flatMap_just_toNothing() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> result =
          maybeTMonad.flatMap(intToNothingStringT, initialJust);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void flatMap_nothing_toJust() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> result =
          maybeTMonad.flatMap(intToJustStringT, initialNothing);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void flatMap_just_toOuterEmpty() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> result =
          maybeTMonad.flatMap(intToOuterEmptyStringT, initialJust);
      assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
    }

    @Test
    void flatMap_nothing_toNothing() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> result =
          maybeTMonad.flatMap(intToNothingStringT, initialNothing);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void flatMap_nothing_toOuterEmpty() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> result =
          maybeTMonad.flatMap(intToOuterEmptyStringT, initialNothing);
      assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
    }

    @Test
    void flatMap_outerEmpty_toNothing() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> result =
          maybeTMonad.flatMap(intToNothingStringT, initialOuterEmpty);
      assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
    }

    @Test
    void flatMap_just_functionThrowsException() {
      Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> fThrows =
          i -> {
            throw new RuntimeException("flatMap function error");
          };
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initial = justT(1);
      try {
        // The type of the result from flatMap will be correct due to method signature.
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> resultKind =
            maybeTMonad.flatMap(fThrows, initial);
        assertThat(unwrapKindToOptionalMaybe(resultKind)).isEmpty();

      } catch (RuntimeException e) {
        assertThat(e.getMessage()).isEqualTo("flatMap function error");
      }
    }

    private <A> void assertMaybeTEquals(
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, A> k1,
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, A> k2) {
      assertThat(unwrapKindToOptionalMaybe(k1)).isEqualTo(unwrapKindToOptionalMaybe(k2));
    }

    @Nested
    @DisplayName("Monad Laws")
    class MonadLaws {
      int value = 5;
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> mVal = justT(value);
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> mValNothing = nothingT();
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> mValOuterEmpty = outerEmptyT();

      Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> fLaw =
          i -> justT("v" + i);
      Function<String, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> gLaw =
          s -> justT(s + "!");

      @Test
      @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
      void leftIdentity() {
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> ofValue = maybeTMonad.of(value);
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> leftSide =
            maybeTMonad.flatMap(fLaw, ofValue);
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> rightSide = fLaw.apply(value);
        assertMaybeTEquals(leftSide, rightSide);

        Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> fLawHandlesNull =
            i -> (i == null) ? nothingT() : justT("v" + i);

        Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> leftSideOfNull =
            maybeTMonad.flatMap(fLawHandlesNull, maybeTMonad.of(null));
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> rightSideOfNull =
            fLawHandlesNull.apply(null);
        assertMaybeTEquals(leftSideOfNull, rightSideOfNull);
      }

      @Test
      @DisplayName("2. Right Identity: flatMap(m, of) == m")
      void rightIdentity() {
        Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>> ofFunc =
            f -> maybeTMonad.of(f);

        assertMaybeTEquals(maybeTMonad.flatMap(ofFunc, mVal), mVal);
        assertMaybeTEquals(maybeTMonad.flatMap(ofFunc, mValNothing), mValNothing);
        assertMaybeTEquals(maybeTMonad.flatMap(ofFunc, mValOuterEmpty), mValOuterEmpty);
      }

      @Test
      @DisplayName(
          "3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
      void associativity() {
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> innerLeft =
            maybeTMonad.flatMap(fLaw, mVal);
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> leftSide =
            maybeTMonad.flatMap(gLaw, innerLeft);

        Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> rightSideFunc =
            a -> maybeTMonad.flatMap(gLaw, fLaw.apply(a));
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> rightSide =
            maybeTMonad.flatMap(rightSideFunc, mVal);
        assertMaybeTEquals(leftSide, rightSide);

        Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> innerNothing =
            maybeTMonad.flatMap(fLaw, mValNothing);
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> leftSideNothing =
            maybeTMonad.flatMap(gLaw, innerNothing);
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> rightSideNothing =
            maybeTMonad.flatMap(rightSideFunc, mValNothing);
        assertMaybeTEquals(leftSideNothing, rightSideNothing);

        Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> innerOuterEmpty =
            maybeTMonad.flatMap(fLaw, mValOuterEmpty);
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> leftSideOuterEmpty =
            maybeTMonad.flatMap(gLaw, innerOuterEmpty);
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> rightSideOuterEmpty =
            maybeTMonad.flatMap(rightSideFunc, mValOuterEmpty);
        assertMaybeTEquals(leftSideOuterEmpty, rightSideOuterEmpty);
      }
    }

    @Nested
    @DisplayName("MonadError 'raiseError' and 'handleErrorWith' tests")
    class MonadErrorTests {

      @Test
      void raiseError_shouldProduceOuterJustInnerNothing() {
        // Cast to MonadError with the correct Witness type
        MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Void> monadErr =
            (MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Void>) maybeTMonad;

        Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> errorKind =
            monadErr.raiseError(null);
        assertThat(unwrapKindToOptionalMaybe(errorKind)).isPresent().contains(Maybe.nothing());
      }

      @Test
      void handleErrorWith_onJustValue_shouldReturnOriginal() {
        MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Void> monadErr =
            (MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Void>) maybeTMonad;
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initial = justT(123);
        Function<Void, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>> handler =
            err -> justT(789);

        Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> result =
            monadErr.handleErrorWith(initial, handler);

        assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.just(123));
      }

      @Test
      void handleErrorWith_onNothingValue_shouldApplyHandler_returningJust() {
        MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Void> monadErr =
            (MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Void>) maybeTMonad;
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initial = nothingT();
        Function<Void, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>> handler =
            err -> justT(789);

        Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> result =
            monadErr.handleErrorWith(initial, handler);

        assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.just(789));
      }

      @Test
      void handleErrorWith_onNothingValue_shouldApplyHandler_returningNothing() {
        MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Void> monadErr =
            (MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Void>) maybeTMonad;
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initial = nothingT();
        Function<Void, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>> handler =
            err -> nothingT();

        Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> result =
            monadErr.handleErrorWith(initial, handler);

        assertThat(unwrapKindToOptionalMaybe(result)).isPresent().contains(Maybe.nothing());
      }

      @Test
      void handleErrorWith_onNothingValue_shouldApplyHandler_returningOuterEmpty() {
        MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Void> monadErr =
            (MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Void>) maybeTMonad;
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initial = nothingT();
        Function<Void, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>> handler =
            err -> outerEmptyT();

        Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> result =
            monadErr.handleErrorWith(initial, handler);

        assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
      }

      @Test
      void handleErrorWith_onOuterEmpty_shouldReturnOuterEmpty() {
        MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Void> monadErr =
            (MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Void>) maybeTMonad;
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initial = outerEmptyT();
        Function<Void, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>> handler =
            err -> justT(789);

        Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> result =
            monadErr.handleErrorWith(initial, handler);

        assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
      }

      @Test
      void handleErrorWith_onNothingValue_handlerThrowsException() {
        MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Void> monadErr =
            (MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Void>) maybeTMonad;
        Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initial = nothingT();
        Function<Void, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>> handlerThrows =
            err -> {
              throw new RuntimeException("handler error");
            };
        try {
          Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> result =
              monadErr.handleErrorWith(initial, handlerThrows);
          assertThat(unwrapKindToOptionalMaybe(result)).isEmpty();
        } catch (RuntimeException e) {
          assertThat(e.getMessage()).isEqualTo("handler error");
        }
      }
    }
  }
}
