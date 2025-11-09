// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OptionalTMonad Tests (F=OptionalKind.Witness)")
class OptionalTMonadTest {

  private final Monad<OptionalKind.Witness> outerMonad = OptionalMonad.INSTANCE;
  private MonadError<OptionalTKind.Witness<OptionalKind.Witness>, Unit> optionalTMonad;

  private final String successValue = "SUCCESS";
  private final Integer initialValue = 123;

  private <A> Optional<Optional<A>> unwrapKindToOptionalOptional(
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, A> kind) {
    OptionalT<OptionalKind.Witness, A> optionalT = OPTIONAL_T.narrow(kind);
    Kind<OptionalKind.Witness, Optional<A>> outerKind = optionalT.value();
    return OPTIONAL.narrow(outerKind);
  }

  private <A extends @NonNull Object> Kind<OptionalTKind.Witness<OptionalKind.Witness>, A> someT(
      @NonNull A value) {
    OptionalT<OptionalKind.Witness, A> ot = OptionalT.some(outerMonad, value);
    return OPTIONAL_T.widen(ot);
  }

  private <A> Kind<OptionalTKind.Witness<OptionalKind.Witness>, A> ofT(@Nullable A value) {
    return optionalTMonad.of(value);
  }

  private <A> Kind<OptionalTKind.Witness<OptionalKind.Witness>, A> noneT() {
    OptionalT<OptionalKind.Witness, A> ot = OptionalT.none(outerMonad);
    return OPTIONAL_T.widen(ot);
  }

  private <A> Kind<OptionalTKind.Witness<OptionalKind.Witness>, A> outerEmptyT() {
    Kind<OptionalKind.Witness, Optional<A>> emptyOuter = OPTIONAL.widen(Optional.empty());
    OptionalT<OptionalKind.Witness, A> ot = OptionalT.fromKind(emptyOuter);
    return OPTIONAL_T.widen(ot);
  }

  @BeforeEach
  void setUp() {
    optionalTMonad = new OptionalTMonad<>(outerMonad);
  }

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldWrapValueAsSomeInOptional() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> kind = ofT(successValue);
      assertThat(unwrapKindToOptionalOptional(kind))
          .isPresent()
          .contains(Optional.of(successValue));
    }

    @Test
    void of_shouldWrapNullAsNoneInOptional() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> kind = ofT(null);
      assertThat(unwrapKindToOptionalOptional(kind)).isPresent().contains(Optional.empty());
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionWhenSome() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> initialKind = someT(initialValue);
      Function<Integer, String> intToString = Object::toString;
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> mappedKind =
          optionalTMonad.map(intToString, initialKind);

      assertThat(unwrapKindToOptionalOptional(mappedKind))
          .isPresent()
          .contains(Optional.of(String.valueOf(initialValue)));
    }

    @Test
    void map_shouldReturnNoneWhenNone() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> initialKind = noneT();
      Function<Integer, String> intToString = Object::toString;
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> mappedKind =
          optionalTMonad.map(intToString, initialKind);

      assertThat(unwrapKindToOptionalOptional(mappedKind)).isPresent().contains(Optional.empty());
    }

    @Test
    void map_shouldReturnOuterEmptyWhenOuterEmpty() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> initialKind = outerEmptyT();
      Function<Integer, String> intToString = Object::toString;
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> mappedKind =
          optionalTMonad.map(intToString, initialKind);

      assertThat(unwrapKindToOptionalOptional(mappedKind)).isEmpty();
    }

    @Test
    void map_shouldHandleMappingToNullAsNone() { // Renamed for clarity based on Optional.map
      // behaviour
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> initialKind = someT(initialValue);
      Function<Integer, @Nullable String> toNull = x -> null;
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> mappedKind =
          optionalTMonad.map(toNull, initialKind);
      // Optional.map(x -> null) results in Optional.empty()
      assertThat(unwrapKindToOptionalOptional(mappedKind)).isPresent().contains(Optional.empty());
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> funcKindSome =
        someT(Object::toString);
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> funcKindNone =
        noneT();
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Function<Integer, String>>
        funcKindOuterEmpty = outerEmptyT();
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Function<Integer, @Nullable String>>
        funcKindSomeToNull = someT(x -> null);

    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> valKindSome = someT(42);
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> valKindNone = noneT();
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> valKindOuterEmpty = outerEmptyT();

    @Test
    void ap_someFunc_someVal() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.ap(funcKindSome, valKindSome);
      assertThat(unwrapKindToOptionalOptional(result)).isPresent().contains(Optional.of("42"));
    }

    @Test
    void ap_someFuncReturningNull_someVal_shouldResultInNone() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.ap(funcKindSomeToNull, valKindSome);
      assertThat(unwrapKindToOptionalOptional(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void ap_someFunc_noneVal() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.ap(funcKindSome, valKindNone);
      assertThat(unwrapKindToOptionalOptional(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void ap_noneFunc_someVal() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.ap(funcKindNone, valKindSome);
      assertThat(unwrapKindToOptionalOptional(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void ap_noneFunc_noneVal() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.ap(funcKindNone, valKindNone);
      assertThat(unwrapKindToOptionalOptional(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void ap_outerEmptyFunc_someVal() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.ap(funcKindOuterEmpty, valKindSome);
      assertThat(unwrapKindToOptionalOptional(result)).isEmpty();
    }

    @Test
    void ap_someFunc_outerEmptyVal() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.ap(funcKindSome, valKindOuterEmpty);
      assertThat(unwrapKindToOptionalOptional(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> intToSomeStringT =
        i -> someT("V" + i);
    Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> intToNoneStringT =
        i -> noneT();
    Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>>
        intToOuterEmptyStringT = i -> outerEmptyT();

    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> initialSome = someT(5);
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> initialNone = noneT();
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> initialOuterEmpty = outerEmptyT();

    @Test
    void flatMap_some_toSome() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.flatMap(intToSomeStringT, initialSome);
      assertThat(unwrapKindToOptionalOptional(result)).isPresent().contains(Optional.of("V5"));
    }

    @Test
    void flatMap_some_toNone() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.flatMap(intToNoneStringT, initialSome);
      assertThat(unwrapKindToOptionalOptional(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void flatMap_none_toSome() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.flatMap(intToSomeStringT, initialNone);
      assertThat(unwrapKindToOptionalOptional(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void flatMap_some_toOuterEmpty() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.flatMap(intToOuterEmptyStringT, initialSome);
      assertThat(unwrapKindToOptionalOptional(result)).isEmpty();
    }

    @Test
    void flatMap_outerEmpty_toSome() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.flatMap(intToSomeStringT, initialOuterEmpty);
      assertThat(unwrapKindToOptionalOptional(result)).isEmpty();
    }
  }

  private <A> void assertOptionalTEquals(
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, A> k1,
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, A> k2) {
    assertThat(unwrapKindToOptionalOptional(k1)).isEqualTo(unwrapKindToOptionalOptional(k2));
  }

  @Nested
  @DisplayName("MonadError Laws & Methods")
  class MonadErrorTests {
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> mValue = someT(initialValue);
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> mNone = noneT();
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> mOuterEmpty = outerEmptyT();

    @Test
    @DisplayName("raiseError should create an inner None Kind (F<Optional.empty>) for OptionalT")
    void raiseError_createsInnerNone() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> errorKind =
          optionalTMonad.raiseError(null); // No cast needed, optionalTMonad is already MonadError
      assertThat(unwrapKindToOptionalOptional(errorKind)).isPresent().contains(Optional.empty());
    }

    @Test
    @DisplayName("handleErrorWith should recover from inner None")
    void handleErrorWith_recoversNone() {
      Function<Unit, Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer>> handler =
          err -> someT(0);
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> recovered =
          optionalTMonad.handleErrorWith(mNone, handler);
      assertThat(unwrapKindToOptionalOptional(recovered)).isPresent().contains(Optional.of(0));
    }

    @Test
    @DisplayName("handleErrorWith should NOT change outer empty; it should propagate outer empty")
    void handleErrorWith_propagatesOuterEmpty() {
      Function<Unit, Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer>> handler =
          err -> someT(0);
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> recovered =
          optionalTMonad.handleErrorWith(mOuterEmpty, handler);
      assertThat(unwrapKindToOptionalOptional(recovered)).isEmpty();
    }

    @Test
    @DisplayName("handleErrorWith should not affect present value (Some)")
    void handleErrorWith_ignoresSome() {
      Function<Unit, Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer>> handler =
          err -> someT(-1); // Should not be called
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> notRecovered =
          optionalTMonad.handleErrorWith(mValue, handler);
      assertOptionalTEquals(notRecovered, mValue);
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {
    int value = 5;
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> mVal = someT(value);
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> mValNone = noneT();
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> mValOuterEmpty = outerEmptyT();

    Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> fLaw =
        i -> someT("v" + i);
    Function<String, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> gLaw =
        s -> someT(s + "!");

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> ofValue = ofT(value);
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> leftSide =
          optionalTMonad.flatMap(fLaw, ofValue);
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> rightSide = fLaw.apply(value);
      assertOptionalTEquals(leftSide, rightSide);

      // Test with of(null) which becomes None
      Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> fLawHandlesNull =
          i -> (i == null) ? noneT() : someT("v" + i);
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> leftSideOfNull =
          optionalTMonad.flatMap(fLawHandlesNull, ofT(null));
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> rightSideOfNull =
          fLawHandlesNull.apply(null);
      assertOptionalTEquals(leftSideOfNull, rightSideOfNull);
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer>> ofFunc =
          optionalTMonad::of; // or `v -> ofT(v)`

      assertOptionalTEquals(optionalTMonad.flatMap(ofFunc, mVal), mVal);
      assertOptionalTEquals(optionalTMonad.flatMap(ofFunc, mValNone), mValNone);
      assertOptionalTEquals(optionalTMonad.flatMap(ofFunc, mValOuterEmpty), mValOuterEmpty);
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> innerLeft =
          optionalTMonad.flatMap(fLaw, mVal);
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> leftSide =
          optionalTMonad.flatMap(gLaw, innerLeft);

      Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> rightSideFunc =
          a -> optionalTMonad.flatMap(gLaw, fLaw.apply(a));
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> rightSide =
          optionalTMonad.flatMap(rightSideFunc, mVal);
      assertOptionalTEquals(leftSide, rightSide);

      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> innerNone =
          optionalTMonad.flatMap(fLaw, mValNone);
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> leftSideNone =
          optionalTMonad.flatMap(gLaw, innerNone);
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> rightSideNone =
          optionalTMonad.flatMap(rightSideFunc, mValNone);
      assertOptionalTEquals(leftSideNone, rightSideNone);

      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> innerOuterEmpty =
          optionalTMonad.flatMap(fLaw, mValOuterEmpty);
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> leftSideOuterEmpty =
          optionalTMonad.flatMap(gLaw, innerOuterEmpty);
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> rightSideOuterEmpty =
          optionalTMonad.flatMap(rightSideFunc, mValOuterEmpty);
      assertOptionalTEquals(leftSideOuterEmpty, rightSideOuterEmpty);
    }
  }
}
