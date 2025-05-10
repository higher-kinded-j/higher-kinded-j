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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OptionalTMonad Tests (F=OptionalKind.Witness)")
class OptionalTMonadTest {

  private final Monad<OptionalKind.Witness> outerMonad = new OptionalMonad();
  private MonadError<OptionalTKind<OptionalKind.Witness, ?>, Void> optionalTMonad;

  private final String successValue = "SUCCESS";
  private final Integer initialValue = 123;

  // Helper to unwrap Kind<OptionalTKind<OptionalKind.Witness, ?>, A> to Optional<Optional<A>>
  private <A> Optional<Optional<A>> unwrapKindToOptionalOptional(
      Kind<OptionalTKind<OptionalKind.Witness, ?>, A> kind) {
    OptionalT<OptionalKind.Witness, A> optionalT =
        OptionalTKindHelper.<OptionalKind.Witness, A>unwrap(kind);
    Kind<OptionalKind.Witness, Optional<A>> outerKind = optionalT.value();
    return OptionalKindHelper.unwrap(outerKind);
  }

  // Helper to create a Kind<OptionalTKind<OptionalKind.Witness, ?>, A> for some value
  private <A> Kind<OptionalTKind<OptionalKind.Witness, ?>, A> someT(@NonNull A value) {
    OptionalT<OptionalKind.Witness, A> ot = OptionalT.some(outerMonad, value);
    return OptionalTKindHelper.wrap(ot);
  }

  private <A> Kind<OptionalTKind<OptionalKind.Witness, ?>, A> ofT(@Nullable A value) {
    return optionalTMonad.of(value);
  }

  // Helper to create a Kind<OptionalTKind<OptionalKind.Witness, ?>, A> for none (inner empty)
  private <A> Kind<OptionalTKind<OptionalKind.Witness, ?>, A> noneT() {
    OptionalT<OptionalKind.Witness, A> ot = OptionalT.none(outerMonad);
    return OptionalTKindHelper.wrap(ot);
  }

  // Helper to create a Kind<OptionalTKind<OptionalKind.Witness, ?>, A> for outer empty
  private <A> Kind<OptionalTKind<OptionalKind.Witness, ?>, A> outerEmptyT() {
    Kind<OptionalKind.Witness, Optional<A>> emptyOuter = OptionalKindHelper.wrap(Optional.empty());
    OptionalT<OptionalKind.Witness, A> ot = OptionalT.fromKind(emptyOuter);
    return OptionalTKindHelper.wrap(ot);
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
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> kind = optionalTMonad.of(successValue);
      assertThat(unwrapKindToOptionalOptional(kind))
          .isPresent()
          .contains(Optional.of(successValue));
    }

    @Test
    void of_shouldWrapNullAsNoneInOptional() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> kind = optionalTMonad.of(null);
      assertThat(unwrapKindToOptionalOptional(kind)).isPresent().contains(Optional.empty());
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionWhenSome() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> initialKind = someT(initialValue);
      Function<Integer, String> intToString = Object::toString;
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> mappedKind =
          optionalTMonad.map(intToString, initialKind);

      assertThat(unwrapKindToOptionalOptional(mappedKind))
          .isPresent()
          .contains(Optional.of(String.valueOf(initialValue)));
    }

    @Test
    void map_shouldReturnNoneWhenNone() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> initialKind = noneT();
      Function<Integer, String> intToString = Object::toString;
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> mappedKind =
          optionalTMonad.map(intToString, initialKind);

      assertThat(unwrapKindToOptionalOptional(mappedKind)).isPresent().contains(Optional.empty());
    }

    @Test
    void map_shouldReturnOuterEmptyWhenOuterEmpty() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> initialKind = outerEmptyT();
      Function<Integer, String> intToString = Object::toString;
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> mappedKind =
          optionalTMonad.map(intToString, initialKind);

      assertThat(unwrapKindToOptionalOptional(mappedKind)).isEmpty();
    }

    @Test
    void map_shouldHandleMappingToNullAsSomeNone() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> initialKind = someT(initialValue);
      Function<Integer, @Nullable String> toNull = x -> null;
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> mappedKind =
          optionalTMonad.map(toNull, initialKind);

      assertThat(unwrapKindToOptionalOptional(mappedKind)).isPresent().contains(Optional.empty());
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    Kind<OptionalTKind<OptionalKind.Witness, ?>, Function<Integer, String>> funcKindSome =
        someT(Object::toString);
    Kind<OptionalTKind<OptionalKind.Witness, ?>, Function<Integer, String>> funcKindNone = noneT();
    Kind<OptionalTKind<OptionalKind.Witness, ?>, Function<Integer, String>> funcKindOuterEmpty =
        outerEmptyT();

    Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> valKindSome = someT(42);
    Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> valKindNone = noneT();
    Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> valKindOuterEmpty = outerEmptyT();

    @Test
    void ap_someFunc_someVal() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> result =
          optionalTMonad.ap(funcKindSome, valKindSome);
      assertThat(unwrapKindToOptionalOptional(result)).isPresent().contains(Optional.of("42"));
    }

    @Test
    void ap_someFunc_noneVal() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> result =
          optionalTMonad.ap(funcKindSome, valKindNone);
      assertThat(unwrapKindToOptionalOptional(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void ap_noneFunc_someVal() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> result =
          optionalTMonad.ap(funcKindNone, valKindSome);
      assertThat(unwrapKindToOptionalOptional(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void ap_noneFunc_noneVal() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> result =
          optionalTMonad.ap(funcKindNone, valKindNone);
      assertThat(unwrapKindToOptionalOptional(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void ap_outerEmptyFunc_someVal() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> result =
          optionalTMonad.ap(funcKindOuterEmpty, valKindSome);
      assertThat(unwrapKindToOptionalOptional(result)).isEmpty();
    }

    @Test
    void ap_someFunc_outerEmptyVal() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> result =
          optionalTMonad.ap(funcKindSome, valKindOuterEmpty);
      assertThat(unwrapKindToOptionalOptional(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    Function<Integer, Kind<OptionalTKind<OptionalKind.Witness, ?>, String>> intToSomeStringT =
        i -> someT("V" + i);
    Function<Integer, Kind<OptionalTKind<OptionalKind.Witness, ?>, String>> intToNoneStringT =
        i -> noneT();
    Function<Integer, Kind<OptionalTKind<OptionalKind.Witness, ?>, String>> intToOuterEmptyStringT =
        i -> outerEmptyT();

    Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> initialSome = someT(5);
    Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> initialNone = noneT();
    Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> initialOuterEmpty = outerEmptyT();

    @Test
    void flatMap_some_toSome() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> result =
          optionalTMonad.flatMap(intToSomeStringT, initialSome);
      assertThat(unwrapKindToOptionalOptional(result)).isPresent().contains(Optional.of("V5"));
    }

    @Test
    void flatMap_some_toNone() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> result =
          optionalTMonad.flatMap(intToNoneStringT, initialSome);
      assertThat(unwrapKindToOptionalOptional(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void flatMap_none_toSome() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> result =
          optionalTMonad.flatMap(intToSomeStringT, initialNone);
      assertThat(unwrapKindToOptionalOptional(result)).isPresent().contains(Optional.empty());
    }

    @Test
    void flatMap_some_toOuterEmpty() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> result =
          optionalTMonad.flatMap(intToOuterEmptyStringT, initialSome);
      assertThat(unwrapKindToOptionalOptional(result)).isEmpty();
    }

    @Test
    void flatMap_outerEmpty_toSome() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> result =
          optionalTMonad.flatMap(intToSomeStringT, initialOuterEmpty);
      assertThat(unwrapKindToOptionalOptional(result)).isEmpty();
    }
  }

  private <A> void assertOptionalTEquals(
      Kind<OptionalTKind<OptionalKind.Witness, ?>, A> k1,
      Kind<OptionalTKind<OptionalKind.Witness, ?>, A> k2) {
    assertThat(unwrapKindToOptionalOptional(k1)).isEqualTo(unwrapKindToOptionalOptional(k2));
  }

  @Nested
  @DisplayName("MonadError Laws & Methods")
  class MonadErrorTests {
    Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> mValue = someT(initialValue);
    Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> mNone = noneT();
    Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> mOuterEmpty = outerEmptyT();

    @Test
    @DisplayName("raiseError should create an inner None Kind (F<Optional.empty>) for OptionalT")
    void raiseError_createsInnerNone() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> errorKind =
          optionalTMonad.raiseError(null);
      assertThat(unwrapKindToOptionalOptional(errorKind)).isPresent().contains(Optional.empty());
    }

    @Test
    @DisplayName("handleErrorWith should recover from inner None")
    void handleErrorWith_recoversNone() {
      Function<Void, Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer>> handler =
          err -> someT(0);
      Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> recovered =
          optionalTMonad.handleErrorWith(mNone, handler);
      assertThat(unwrapKindToOptionalOptional(recovered)).isPresent().contains(Optional.of(0));
    }

    @Test
    @DisplayName("handleErrorWith should NOT change outer empty; it should propagate outer empty")
    void handleErrorWith_propagatesOuterEmpty() {
      Function<Void, Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer>> handler =
          err -> someT(0);
      Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> recovered =
          optionalTMonad.handleErrorWith(mOuterEmpty, handler);
      assertThat(unwrapKindToOptionalOptional(recovered)).isEmpty();
    }

    @Test
    @DisplayName("handleErrorWith should not affect present value (Some)")
    void handleErrorWith_ignoresSome() {
      Function<Void, Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer>> handler =
          err -> someT(-1);
      Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> notRecovered =
          optionalTMonad.handleErrorWith(mValue, handler);
      assertOptionalTEquals(notRecovered, mValue);
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {
    int value = 5;
    Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> mVal = someT(value);
    Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> mValNone = noneT();
    Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> mValOuterEmpty = outerEmptyT();

    Function<Integer, Kind<OptionalTKind<OptionalKind.Witness, ?>, String>> fLaw =
        i -> someT("v" + i);
    Function<String, Kind<OptionalTKind<OptionalKind.Witness, ?>, String>> gLaw =
        s -> someT(s + "!");

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer> ofValue = optionalTMonad.of(value);
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> leftSide =
          optionalTMonad.flatMap(fLaw, ofValue);
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> rightSide = fLaw.apply(value);
      assertOptionalTEquals(leftSide, rightSide);

      Function<Integer, Kind<OptionalTKind<OptionalKind.Witness, ?>, String>> fLawHandlesNull =
          i -> (i == null) ? noneT() : someT("v" + i);
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> leftSideOfNull =
          optionalTMonad.flatMap(fLawHandlesNull, optionalTMonad.of(null));
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> rightSideOfNull =
          fLawHandlesNull.apply(null);
      assertOptionalTEquals(leftSideOfNull, rightSideOfNull);
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<OptionalTKind<OptionalKind.Witness, ?>, Integer>> ofFunc =
          optionalTMonad::of;

      assertOptionalTEquals(optionalTMonad.flatMap(ofFunc, mVal), mVal);
      assertOptionalTEquals(optionalTMonad.flatMap(ofFunc, mValNone), mValNone);
      assertOptionalTEquals(optionalTMonad.flatMap(ofFunc, mValOuterEmpty), mValOuterEmpty);
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> innerLeft =
          optionalTMonad.flatMap(fLaw, mVal);
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> leftSide =
          optionalTMonad.flatMap(gLaw, innerLeft);

      Function<Integer, Kind<OptionalTKind<OptionalKind.Witness, ?>, String>> rightSideFunc =
          a -> optionalTMonad.flatMap(gLaw, fLaw.apply(a));
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> rightSide =
          optionalTMonad.flatMap(rightSideFunc, mVal);
      assertOptionalTEquals(leftSide, rightSide);

      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> innerNone =
          optionalTMonad.flatMap(fLaw, mValNone);
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> leftSideNone =
          optionalTMonad.flatMap(gLaw, innerNone);
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> rightSideNone =
          optionalTMonad.flatMap(rightSideFunc, mValNone);
      assertOptionalTEquals(leftSideNone, rightSideNone);

      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> innerOuterEmpty =
          optionalTMonad.flatMap(fLaw, mValOuterEmpty);
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> leftSideOuterEmpty =
          optionalTMonad.flatMap(gLaw, innerOuterEmpty);
      Kind<OptionalTKind<OptionalKind.Witness, ?>, String> rightSideOuterEmpty =
          optionalTMonad.flatMap(rightSideFunc, mValOuterEmpty);
      assertOptionalTEquals(leftSideOuterEmpty, rightSideOuterEmpty);
    }
  }
}
