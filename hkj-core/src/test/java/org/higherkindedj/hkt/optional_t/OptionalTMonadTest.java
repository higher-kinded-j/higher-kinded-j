// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional_t;

import static org.higherkindedj.hkt.assertions.OptionalTAssert.assertThatOptionalT;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OptionalTMonad Tests")
// (F=OptionalKind.Witness)
class OptionalTMonadTest {

  private final Monad<OptionalKind.Witness> outerMonad = Instances.monadError(optional());
  private MonadError<OptionalTKind.Witness<OptionalKind.Witness>, Unit> optionalTMonad;

  private final String successValue = "SUCCESS";
  private final Integer initialValue = 123;

  private <A> Optional<Optional<A>> unwrapKindToOptionalOptional(
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, A> kind) {
    OptionalT<OptionalKind.Witness, A> optionalT = OPTIONAL_T.narrow(kind);
    Kind<OptionalKind.Witness, Optional<A>> outerKind = optionalT.value();
    return OPTIONAL.narrow(outerKind);
  }

  private <A> Optional<Optional<A>> unwrapOuterOptional(
      Kind<OptionalKind.Witness, Optional<A>> kind) {
    return OPTIONAL.narrow(kind);
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
      assertThatOptionalT(kind, OptionalTMonadTest.this::unwrapOuterOptional)
          .isPresentSome()
          .hasSomeValue(successValue);
    }

    @Test
    void of_shouldWrapNullAsNoneInOptional() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> kind = ofT(null);
      assertThatOptionalT(kind, OptionalTMonadTest.this::unwrapOuterOptional).isPresentNone();
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

      assertThatOptionalT(mappedKind, OptionalTMonadTest.this::unwrapOuterOptional)
          .isPresentSome()
          .hasSomeValue(String.valueOf(initialValue));
    }

    @Test
    void map_shouldReturnNoneWhenNone() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> initialKind = noneT();
      Function<Integer, String> intToString = Object::toString;
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> mappedKind =
          optionalTMonad.map(intToString, initialKind);

      assertThatOptionalT(mappedKind, OptionalTMonadTest.this::unwrapOuterOptional).isPresentNone();
    }

    @Test
    void map_shouldReturnOuterEmptyWhenOuterEmpty() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> initialKind = outerEmptyT();
      Function<Integer, String> intToString = Object::toString;
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> mappedKind =
          optionalTMonad.map(intToString, initialKind);

      assertThatOptionalT(mappedKind, OptionalTMonadTest.this::unwrapOuterOptional).isEmpty();
    }

    @Test
    void map_shouldHandleMappingToNullAsNone() { // Renamed for clarity based on Optional.map
      // behaviour
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> initialKind = someT(initialValue);
      Function<Integer, @Nullable String> toNull = x -> null;
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> mappedKind =
          optionalTMonad.map(toNull, initialKind);
      // Optional.map(x -> null) results in Optional.empty()
      assertThatOptionalT(mappedKind, OptionalTMonadTest.this::unwrapOuterOptional).isPresentNone();
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
      assertThatOptionalT(result, OptionalTMonadTest.this::unwrapOuterOptional)
          .isPresentSome()
          .hasSomeValue("42");
    }

    @Test
    void ap_someFuncReturningNull_someVal_shouldResultInNone() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.ap(funcKindSomeToNull, valKindSome);
      assertThatOptionalT(result, OptionalTMonadTest.this::unwrapOuterOptional).isPresentNone();
    }

    @Test
    void ap_someFunc_noneVal() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.ap(funcKindSome, valKindNone);
      assertThatOptionalT(result, OptionalTMonadTest.this::unwrapOuterOptional).isPresentNone();
    }

    @Test
    void ap_noneFunc_someVal() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.ap(funcKindNone, valKindSome);
      assertThatOptionalT(result, OptionalTMonadTest.this::unwrapOuterOptional).isPresentNone();
    }

    @Test
    void ap_noneFunc_noneVal() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.ap(funcKindNone, valKindNone);
      assertThatOptionalT(result, OptionalTMonadTest.this::unwrapOuterOptional).isPresentNone();
    }

    @Test
    void ap_outerEmptyFunc_someVal() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.ap(funcKindOuterEmpty, valKindSome);
      assertThatOptionalT(result, OptionalTMonadTest.this::unwrapOuterOptional).isEmpty();
    }

    @Test
    void ap_someFunc_outerEmptyVal() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.ap(funcKindSome, valKindOuterEmpty);
      assertThatOptionalT(result, OptionalTMonadTest.this::unwrapOuterOptional).isEmpty();
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
      assertThatOptionalT(result, OptionalTMonadTest.this::unwrapOuterOptional)
          .isPresentSome()
          .hasSomeValue("V5");
    }

    @Test
    void flatMap_some_toNone() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.flatMap(intToNoneStringT, initialSome);
      assertThatOptionalT(result, OptionalTMonadTest.this::unwrapOuterOptional).isPresentNone();
    }

    @Test
    void flatMap_none_toSome() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.flatMap(intToSomeStringT, initialNone);
      assertThatOptionalT(result, OptionalTMonadTest.this::unwrapOuterOptional).isPresentNone();
    }

    @Test
    void flatMap_some_toOuterEmpty() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.flatMap(intToOuterEmptyStringT, initialSome);
      assertThatOptionalT(result, OptionalTMonadTest.this::unwrapOuterOptional).isEmpty();
    }

    @Test
    void flatMap_outerEmpty_toSome() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> result =
          optionalTMonad.flatMap(intToSomeStringT, initialOuterEmpty);
      assertThatOptionalT(result, OptionalTMonadTest.this::unwrapOuterOptional).isEmpty();
    }
  }

  private <A> void assertOptionalTEquals(
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, A> k1,
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, A> k2) {
    assertThatOptionalT(k1, OptionalTMonadTest.this::unwrapOuterOptional).isEqualToOptionalT(k2);
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
      assertThatOptionalT(errorKind, OptionalTMonadTest.this::unwrapOuterOptional).isPresentNone();
    }

    @Test
    @DisplayName("handleErrorWith should recover from inner None")
    void handleErrorWith_recoversNone() {
      Function<Unit, Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer>> handler =
          err -> someT(0);
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> recovered =
          optionalTMonad.handleErrorWith(mNone, handler);
      assertThatOptionalT(recovered, OptionalTMonadTest.this::unwrapOuterOptional)
          .isPresentSome()
          .hasSomeValue(0);
    }

    @Test
    @DisplayName("handleErrorWith should NOT change outer empty; it should propagate outer empty")
    void handleErrorWith_propagatesOuterEmpty() {
      Function<Unit, Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer>> handler =
          err -> someT(0);
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> recovered =
          optionalTMonad.handleErrorWith(mOuterEmpty, handler);
      assertThatOptionalT(recovered, OptionalTMonadTest.this::unwrapOuterOptional).isEmpty();
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
  @DisplayName("Functor Laws")
  class FunctorLawTests {
    final Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> mVal = someT(5);
    final Function<Integer, String> f = Object::toString;
    final Function<String, String> g = s -> s + "!";
    final BiPredicate<
            Kind<OptionalTKind.Witness<OptionalKind.Witness>, ?>,
            Kind<OptionalTKind.Witness<OptionalKind.Witness>, ?>>
        eq = KindEquivalence.byEqualsAfter(OptionalTMonadTest.this::unwrapKindToOptionalOptional);

    @Test
    @DisplayName("Identity holds for Some, None, and empty-outer fixtures")
    void identity() {
      FunctorLaws.assertIdentity(optionalTMonad, mVal, eq);
      FunctorLaws.assertIdentity(optionalTMonad, noneT(), eq);
      FunctorLaws.assertIdentity(optionalTMonad, outerEmptyT(), eq);
    }

    @Test
    @DisplayName("Composition: map(g∘f, fa) == map(g, map(f, fa))")
    void composition() {
      FunctorLaws.assertComposition(optionalTMonad, mVal, f, g, eq);
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLawTests {
    final int value = 5;
    final Function<Integer, String> f = Object::toString;
    final Function<String, String> g = s -> s + "!";
    final Kind<OptionalTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> u = someT(f);
    final Kind<OptionalTKind.Witness<OptionalKind.Witness>, Function<String, String>> uG = someT(g);
    final Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> w = someT(value);
    final BiPredicate<
            Kind<OptionalTKind.Witness<OptionalKind.Witness>, ?>,
            Kind<OptionalTKind.Witness<OptionalKind.Witness>, ?>>
        eq = KindEquivalence.byEqualsAfter(OptionalTMonadTest.this::unwrapKindToOptionalOptional);

    @Test
    @DisplayName("Identity: ap(of(id), v) == v")
    void identity() {
      ApplicativeLaws.assertIdentity(optionalTMonad, w, eq);
    }

    @Test
    @DisplayName("Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      ApplicativeLaws.assertHomomorphism(optionalTMonad, value, f, eq);
    }

    @Test
    @DisplayName("Interchange: ap(u, of(y)) == ap(of(f -> f(y)), u)")
    void interchange() {
      ApplicativeLaws.assertInterchange(optionalTMonad, u, value, eq);
    }

    @Test
    @DisplayName("Composition")
    void composition() {
      ApplicativeLaws.assertComposition(optionalTMonad, uG, u, w, eq);
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawTests {
    final int value = 5;
    final Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> mVal = someT(value);
    final Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> mValNone = noneT();
    final Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> mValOuterEmpty = outerEmptyT();

    final Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> fLaw =
        i -> someT("v" + i);
    final Function<String, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> gLaw =
        s -> someT(s + "!");

    final BiPredicate<
            Kind<OptionalTKind.Witness<OptionalKind.Witness>, ?>,
            Kind<OptionalTKind.Witness<OptionalKind.Witness>, ?>>
        eq = KindEquivalence.byEqualsAfter(OptionalTMonadTest.this::unwrapKindToOptionalOptional);

    @Test
    @DisplayName("Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      MonadLaws.assertLeftIdentity(optionalTMonad, value, fLaw, eq);

      Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> fLawHandlesNull =
          i -> (i == null) ? noneT() : someT("v" + i);
      MonadLaws.assertLeftIdentity(optionalTMonad, null, fLawHandlesNull, eq);
    }

    @Test
    @DisplayName("Right Identity holds for Some, None, and empty-outer fixtures")
    void rightIdentity() {
      MonadLaws.assertRightIdentity(optionalTMonad, mVal, eq);
      MonadLaws.assertRightIdentity(optionalTMonad, mValNone, eq);
      MonadLaws.assertRightIdentity(optionalTMonad, mValOuterEmpty, eq);
    }

    @Test
    @DisplayName("Associativity holds for Some, None, and empty-outer fixtures")
    void associativity() {
      MonadLaws.assertAssociativity(optionalTMonad, mVal, fLaw, gLaw, eq);
      MonadLaws.assertAssociativity(optionalTMonad, mValNone, fLaw, gLaw, eq);
      MonadLaws.assertAssociativity(optionalTMonad, mValOuterEmpty, fLaw, gLaw, eq);
    }
  }
}
