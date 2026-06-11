// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional_t;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("OptionalTMonad Tests")
// (F=OptionalKind.Witness)
class OptionalTMonadTest {

  private final Monad<OptionalKind.Witness> outerMonad = Instances.monadError(optional());
  private MonadError<OptionalTKind.Witness<OptionalKind.Witness>, Unit> optionalTMonad;

  private final Integer initialValue = 123;

  private <A> Optional<Optional<A>> unwrapOuterOptional(
      Kind<OptionalKind.Witness, Optional<A>> kind) {
    return OPTIONAL.narrow(kind);
  }

  private <A> Kind<OptionalTKind.Witness<OptionalKind.Witness>, A> someT(A value) {
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

  // Shared law equality: run/unwrap to Optional<Optional<·>> and compare.
  private final BiPredicate<
          Kind<OptionalTKind.Witness<OptionalKind.Witness>, ?>,
          Kind<OptionalTKind.Witness<OptionalKind.Witness>, ?>>
      eq = OptionalTLawFixtures.EQ;

  /**
   * OptionalT over the eager {@code Optional} inner monad <em>propagates</em> a thrown function
   * exception, so the contract includes {@link Category#EXCEPTIONS}. {@link Category#VALIDATIONS}
   * <em>is</em> run: {@code OptionalTMonad} now overrides {@code recoverWith} to reject a null
   * fallback eagerly (regardless of {@code ma}'s state), matching {@code Either}/{@code Maybe}. The
   * error type is {@link Unit}, and the MonadError-specific behaviour, including per-method message
   * assertions, is exercised by {@link MonadErrorTests}.
   */
  @Test
  @DisplayName(
      "MonadError contract — operations, validations & exceptions (laws in the *LawTests below)")
  void monadErrorContract() {
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> validKind = someT(10);
    Function<Integer, String> validMapper = Object::toString;
    Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> validFlatMapper =
        i -> someT("v" + i);
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> validFunctionKind =
        someT(Object::toString);
    Function<Unit, Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer>> validHandler =
        _ -> someT(0);
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> validFallback = someT(-1);
    TypeClassContract.<OptionalTKind.Witness<OptionalKind.Witness>, Unit>monadError(
            OptionalTMonad.class)
        .<Integer>instance(optionalTMonad)
        .<String>withKind(validKind)
        .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
        .withErrorHandling(validHandler, validFallback)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldWrapValueAsSomeInOptional() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> kind = ofT("SUCCESS");
      assertThatOptionalT(kind, OptionalTMonadTest.this::unwrapOuterOptional)
          .isPresentSome()
          .hasSomeValue("SUCCESS");
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
    @SuppressWarnings("NullableProblems") // the mapper deliberately returns null
    void map_shouldHandleMappingToNullAsNone() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> initialKind = someT(initialValue);
      Function<Integer, @Nullable String> toNull = _ -> null;
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
        funcKindSomeToNull = someT(_ -> null);

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
    @SuppressWarnings("NullableProblems") // funcKindSomeToNull deliberately yields null -> None
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
        _ -> noneT();
    Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>>
        intToOuterEmptyStringT = _ -> outerEmptyT();

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
          _ -> someT(0);
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
          _ -> someT(0);
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> recovered =
          optionalTMonad.handleErrorWith(mOuterEmpty, handler);
      assertThatOptionalT(recovered, OptionalTMonadTest.this::unwrapOuterOptional).isEmpty();
    }

    @Test
    @DisplayName("handleErrorWith should not affect present value (Some)")
    void handleErrorWith_ignoresSome() {
      Function<Unit, Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer>> handler =
          _ -> someT(-1); // Should not be called
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> notRecovered =
          optionalTMonad.handleErrorWith(mValue, handler);
      assertOptionalTEquals(notRecovered, mValue);
    }

    @Test
    @DisplayName("recoverWith should replace inner None with the fallback")
    void recoverWith_replacesNoneWithFallback() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> result =
          optionalTMonad.recoverWith(mNone, someT(-1));
      assertThatOptionalT(result, OptionalTMonadTest.this::unwrapOuterOptional)
          .isPresentSome()
          .hasSomeValue(-1);
    }

    @Test
    @DisplayName("recoverWith should ignore a present value (Some)")
    void recoverWith_ignoresSome() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> result =
          optionalTMonad.recoverWith(mValue, someT(-1));
      assertOptionalTEquals(result, mValue);
    }

    @Test
    @DisplayName("recover should replace inner None with the value")
    void recover_replacesNoneWithValue() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> result =
          optionalTMonad.recover(mNone, -7);
      assertThatOptionalT(result, OptionalTMonadTest.this::unwrapOuterOptional)
          .isPresentSome()
          .hasSomeValue(-7);
    }

    @Test
    @DisplayName("recover with a null value yields inner None (ofNullable semantics)")
    void recover_withNullValueYieldsNone() {
      Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> result =
          optionalTMonad.recover(mNone, null);
      assertThatOptionalT(result, OptionalTMonadTest.this::unwrapOuterOptional).isPresentNone();
    }

    @Test
    @DisplayName("recoverWith rejects a null source eagerly")
    @SuppressWarnings("DataFlowIssue") // null source exercises recoverWith's guard
    void recoverWith_rejectsNullSource() {
      assertThatThrownBy(() -> optionalTMonad.recoverWith(null, someT(-1)))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("recoverWith (source)");
    }

    @Test
    @DisplayName("recoverWith rejects a null fallback eagerly, even against a Some")
    @SuppressWarnings("DataFlowIssue") // null fallback exercises recoverWith's guard
    void recoverWith_rejectsNullFallbackAgainstSome() {
      assertThatThrownBy(() -> optionalTMonad.recoverWith(mValue, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("recoverWith (fallback)");
    }

    @Test
    @DisplayName("recover rejects a null source, naming recover")
    @SuppressWarnings("DataFlowIssue") // null source exercises recover's guard
    void recover_rejectsNullSource() {
      assertThatThrownBy(() -> optionalTMonad.recover(null, 1))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("recover (source)");
    }
  }

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLawTests {
    final Function<Integer, String> f = Object::toString;
    final Function<String, String> g = s -> s + "!";

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.optional_t.OptionalTLawFixtures#kinds")
    void identity(String label, Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> fa) {
      FunctorLaws.assertIdentity(optionalTMonad, fa, eq);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.optional_t.OptionalTLawFixtures#kinds")
    void composition(String label, Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> fa) {
      FunctorLaws.assertComposition(optionalTMonad, fa, f, g, eq);
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLawTests {
    final Function<Integer, String> f = Object::toString;
    final Function<String, String> g = s -> s + "!";
    final Kind<OptionalTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> u = someT(f);
    final Kind<OptionalTKind.Witness<OptionalKind.Witness>, Function<String, String>> uG = someT(g);

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.optional_t.OptionalTLawFixtures#kinds")
    void identity(String label, Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> w) {
      ApplicativeLaws.assertIdentity(optionalTMonad, w, eq);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.optional_t.OptionalTLawFixtures#values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(optionalTMonad, value, f, eq);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.optional_t.OptionalTLawFixtures#values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(optionalTMonad, u, value, eq);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.optional_t.OptionalTLawFixtures#kinds")
    void composition(String label, Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> w) {
      ApplicativeLaws.assertComposition(optionalTMonad, uG, u, w, eq);
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawTests {
    final Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> fLaw =
        i -> someT("v" + i);
    final Function<String, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> gLaw =
        s -> someT(s + "!");

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.optional_t.OptionalTLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(optionalTMonad, value, fLaw, eq);
    }

    @Test
    @DisplayName("Left Identity holds for a null value (mapped to None)")
    @SuppressWarnings({"ConstantValue", "DataFlowIssue"}) // OptionalT may legitimately hold null
    void leftIdentityNull() {
      Function<Integer, Kind<OptionalTKind.Witness<OptionalKind.Witness>, String>> fLawHandlesNull =
          i -> (i == null) ? noneT() : someT("v" + i);
      MonadLaws.assertLeftIdentity(optionalTMonad, null, fLawHandlesNull, eq);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.optional_t.OptionalTLawFixtures#kinds")
    void rightIdentity(String label, Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> m) {
      MonadLaws.assertRightIdentity(optionalTMonad, m, eq);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.optional_t.OptionalTLawFixtures#kinds")
    void associativity(String label, Kind<OptionalTKind.Witness<OptionalKind.Witness>, Integer> m) {
      MonadLaws.assertAssociativity(optionalTMonad, m, fLaw, gLaw, eq);
    }
  }
}
