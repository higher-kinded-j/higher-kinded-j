// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe_t;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.MaybeTAssert.assertThatMaybeT;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.maybe_t.MaybeTKindHelper.MAYBE_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.higherkindedj.hkt.test.fixtures.TypeClassTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("MaybeTMonad Complete Test Suite")
//   (Outer: OptionalKind.Witness)
class MaybeTMonadTest
    extends TypeClassTestBase<MaybeTKind.Witness<OptionalKind.Witness>, Integer, String> {

  private MonadError<OptionalKind.Witness, Unit> outerMonad = Instances.monadError(optional());
  private MonadError<MaybeTKind.Witness<OptionalKind.Witness>, Unit> maybeTMonad =
      Instances.maybeT(outerMonad);

  @BeforeEach
  void setUpMonad() {
    outerMonad = Instances.monadError(optional());
    maybeTMonad = Instances.maybeT(outerMonad);
  }

  private <A> Optional<Maybe<A>> unwrapOuterOptional(Kind<OptionalKind.Witness, Maybe<A>> kind) {
    return OPTIONAL.narrow(kind);
  }

  private <R> Kind<MaybeTKind.Witness<OptionalKind.Witness>, R> justT(R value) {
    return MAYBE_T.widen(MaybeT.just(outerMonad, value));
  }

  private <R> Kind<MaybeTKind.Witness<OptionalKind.Witness>, R> nothingT() {
    return MAYBE_T.widen(MaybeT.nothing(outerMonad));
  }

  private <R> Kind<MaybeTKind.Witness<OptionalKind.Witness>, R> emptyT() {
    Kind<OptionalKind.Witness, Maybe<R>> emptyOuter = OPTIONAL.widen(Optional.empty());
    return MAYBE_T.widen(MaybeT.fromKind(emptyOuter));
  }

  // TypeClassTestBase implementations
  @Override
  protected Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> createValidKind() {
    return justT(10);
  }

  @Override
  protected Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> createValidKind2() {
    return justT(20);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return Object::toString;
  }

  @Override
  protected BiPredicate<
          Kind<MaybeTKind.Witness<OptionalKind.Witness>, ?>,
          Kind<MaybeTKind.Witness<OptionalKind.Witness>, ?>>
      createEqualityChecker() {
    return MaybeTLawFixtures.EQ;
  }

  @Override
  protected Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>>
      createValidFlatMapper() {
    return i -> justT("v" + i);
  }

  @Override
  protected Kind<MaybeTKind.Witness<OptionalKind.Witness>, Function<Integer, String>>
      createValidFunctionKind() {
    return justT(Object::toString);
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> a + "+" + b;
  }

  @Override
  protected Integer createTestValue() {
    return 5;
  }

  @Override
  protected Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>>
      createTestFunction() {
    return i -> justT("v" + i);
  }

  @Override
  protected Function<String, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>>
      createChainFunction() {
    return s -> justT(s + "!");
  }

  /**
   * MaybeT over the eager {@code Optional} inner monad <em>propagates</em> a thrown function
   * exception, so the contract includes {@link Category#EXCEPTIONS}. {@link Category#VALIDATIONS}
   * is omitted because, like {@code Optional}/{@code Try}, {@code MaybeTMonad} inherits the default
   * {@code recoverWith} (no eager null-fallback check); the error type is {@link Unit}, and the
   * MonadError-specific behaviour is exercised by {@link MonadErrorOperationTests}.
   */
  @Test
  @DisplayName(
      "MonadError contract — operations & exceptions (laws verified in the *LawTests below)")
  void monadErrorContract() {
    Function<Unit, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>> validHandler =
        _ -> justT(0);
    Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> validFallback = justT(-1);
    TypeClassContract.<MaybeTKind.Witness<OptionalKind.Witness>, Unit>monadError(MaybeTMonad.class)
        .<Integer>instance(maybeTMonad)
        .<String>withKind(validKind)
        .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
        .withErrorHandling(validHandler, validFallback)
        .verifyOnly(Category.OPERATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Functor Operations")
  class FunctorOperationTests {

    @Test
    @DisplayName("map should apply function when Just")
    void map_shouldApplyFunctionWhenJust() {
      var input = justT(10);
      var result = maybeTMonad.map(Object::toString, input);

      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional)
          .isPresentJust()
          .hasJustValue("10");
    }

    @Test
    @DisplayName("map should propagate Nothing when Nothing")
    void map_shouldPropagateNothingWhenNothing() {
      var input = nothingT();
      var result = maybeTMonad.map(Object::toString, input);

      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isPresentNothing();
    }

    @Test
    @DisplayName("map should propagate empty outer monad")
    void map_shouldPropagateEmpty() {
      var input = emptyT();
      var result = maybeTMonad.map(Object::toString, input);

      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isEmpty();
    }

    @Test
    @DisplayName("map should convert null result to Nothing")
    @SuppressWarnings("DataFlowIssue") // the mapper deliberately returns null to verify Nothing
    void map_shouldConvertNullResultToNothing() {
      var input = justT(10);
      Function<Integer, String> nullReturningMapper = _ -> null;
      var result = maybeTMonad.map(nullReturningMapper, input);

      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isPresentNothing();
    }
  }

  @Nested
  @DisplayName("Applicative Operations")
  class ApplicativeOperationTests {

    final Function<Integer, String> multiplyToString = i -> "Res:" + (i * 2);

    @Test
    @DisplayName("ap: F<Just(func)> ap F<Just(val)> should apply function")
    void ap_FuncJust_ValJust_shouldApplyFunction() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> ff =
          justT(multiplyToString);
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> fa = justT(10);

      var result = maybeTMonad.ap(ff, fa);
      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional)
          .isPresentJust()
          .hasJustValue("Res:20");
    }

    @Test
    @DisplayName("ap: F<Just(func)> ap F<Nothing> should propagate val Nothing")
    void ap_FuncJust_ValNothing_shouldPropagateValNothing() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> ff =
          justT(multiplyToString);
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> fa = nothingT();

      var result = maybeTMonad.ap(ff, fa);
      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isPresentNothing();
    }

    @Test
    @DisplayName("ap: F<Nothing> ap F<Just(val)> should propagate func Nothing")
    void ap_FuncNothing_ValJust_shouldPropagateFuncNothing() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> ff = nothingT();
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> fa = justT(10);

      var result = maybeTMonad.ap(ff, fa);
      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isPresentNothing();
    }

    @Test
    @DisplayName("ap: F<Nothing> ap F<Nothing> should propagate func Nothing")
    void ap_FuncNothing_ValNothing_shouldPropagateFuncNothing() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> ff = nothingT();
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> fa = nothingT();

      var result = maybeTMonad.ap(ff, fa);
      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isPresentNothing();
    }

    @Test
    @DisplayName("ap: F.empty (for function) ap F<Just(val)> should be outer empty")
    void ap_FuncOuterEmpty_ValJust_shouldBeOuterEmpty() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> ff = emptyT();
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> fa = justT(10);

      var result = maybeTMonad.ap(ff, fa);
      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isEmpty();
    }

    @Test
    @DisplayName("ap: F<Just(func)> ap F.empty (for value) should be outer empty")
    void ap_FuncJust_ValOuterEmpty_shouldBeOuterEmpty() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> ff =
          justT(multiplyToString);
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> fa = emptyT();

      var result = maybeTMonad.ap(ff, fa);
      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isEmpty();
    }

    @Test
    @DisplayName("ap: F<Nothing> ap F<Just(val)> should propagate Nothing")
    void ap_FuncNothing_ValJust_shouldPropagateNothing() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> ff = nothingT();
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> fa = justT(10);

      var result = maybeTMonad.ap(ff, fa);
      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isPresentNothing();
    }

    @Test
    @DisplayName("ap: F<Just(function_throws)> ap F<Just(val)> should throw exception")
    void ap_FuncJustThrows_ValJust_shouldThrowException() {
      RuntimeException ex = new RuntimeException("Function apply crashed");
      Function<Integer, String> throwingFunc =
          _ -> {
            throw ex;
          };
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Function<Integer, String>> ff =
          justT(throwingFunc);
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> fa = justT(10);

      assertThatThrownBy(() -> maybeTMonad.ap(ff, fa))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(ex);
    }
  }

  @Nested
  @DisplayName("Monad Operations")
  class MonadOperationTests {

    @Test
    @DisplayName("flatMap: Initial Just, Function returns Just")
    void flatMap_initialJust_funcReturnsJust() {
      var initialJust = justT(10);
      Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> funcReturnsJust =
          i -> justT("Value:" + i);

      var result = maybeTMonad.flatMap(funcReturnsJust, initialJust);

      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional)
          .isPresentJust()
          .hasJustValue("Value:10");
    }

    @Test
    @DisplayName("flatMap: Initial Just, Function returns Nothing")
    void flatMap_initialJust_funcReturnsNothing() {
      var initialJust = justT(10);
      Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> funcReturnsNothing =
          _ -> nothingT();

      var result = maybeTMonad.flatMap(funcReturnsNothing, initialJust);

      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isPresentNothing();
    }

    @Test
    @DisplayName("flatMap: Initial Just, Function returns Empty Outer Monad")
    void flatMap_initialJust_funcReturnsEmptyOuter() {
      var initialJust = justT(20);
      Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> funcReturnsEmpty =
          _ -> emptyT();

      var result = maybeTMonad.flatMap(funcReturnsEmpty, initialJust);

      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isEmpty();
    }

    @Test
    @DisplayName("flatMap: Initial Nothing, Function should not be called")
    void flatMap_initialNothing_funcNotCalled() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initialNothing = nothingT();
      Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> funcShouldNotRun =
          _ -> {
            throw new AssertionError("Function should not have been called for Nothing input");
          };

      var result = maybeTMonad.flatMap(funcShouldNotRun, initialNothing);

      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isPresentNothing();
    }

    @Test
    @DisplayName("flatMap: Initial Empty Outer Monad, Function should not be called")
    void flatMap_initialEmptyOuter_funcNotCalled() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> initialEmptyOuter = emptyT();
      Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> funcShouldNotRun =
          _ -> {
            throw new AssertionError("Function should not have been called for empty outer input");
          };

      var result = maybeTMonad.flatMap(funcShouldNotRun, initialEmptyOuter);

      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isEmpty();
    }

    @Test
    @DisplayName("flatMap: Function throws unhandled RuntimeException")
    void flatMap_functionThrowsRuntimeException() {
      var initialJust = justT(30);
      RuntimeException runtimeEx = new RuntimeException("Error in function application!");
      Function<Integer, Kind<MaybeTKind.Witness<OptionalKind.Witness>, String>> funcThrows =
          _ -> {
            throw runtimeEx;
          };

      assertThatThrownBy(() -> maybeTMonad.flatMap(funcThrows, initialJust))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(runtimeEx);
    }
  }

  @Nested
  @DisplayName("MonadError Operations")
  class MonadErrorOperationTests {

    Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> justVal;
    Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> nothingVal;
    Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> emptyVal;
    Unit raisedErrorObj;
    Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> raisedErrorKind;

    @BeforeEach
    void setUpMonadError() {
      justVal = justT(100);
      nothingVal = nothingT();
      emptyVal = emptyT();
      raisedErrorObj = Unit.INSTANCE;
      raisedErrorKind = maybeTMonad.raiseError(raisedErrorObj);
    }

    @Test
    @DisplayName("raiseError should create Nothing in Optional")
    void raiseError_shouldCreateNothingInOptional() {
      assertThatMaybeT(raisedErrorKind, MaybeTMonadTest.this::unwrapOuterOptional)
          .isPresentNothing();
    }

    @Test
    @DisplayName("handleErrorWith should handle Nothing")
    void handleErrorWith_shouldHandleNothing() {
      Function<Unit, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>> handler =
          _ -> justT(404);

      var result = maybeTMonad.handleErrorWith(nothingVal, handler);
      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional)
          .isPresentJust()
          .hasJustValue(404);
    }

    @Test
    @DisplayName("handleErrorWith should ignore Just")
    void handleErrorWith_shouldIgnoreJust() {
      Function<Unit, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>> handler =
          _ -> justT(-1);

      var result = maybeTMonad.handleErrorWith(justVal, handler);
      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isEqualToMaybeT(justVal);
    }

    @Test
    @DisplayName("handleErrorWith should propagate empty")
    void handleErrorWith_shouldPropagateEmpty() {
      Function<Unit, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer>> handler =
          _ -> justT(-1);

      var result = maybeTMonad.handleErrorWith(emptyVal, handler);
      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isEmpty();
    }
  }

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLawTests {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.maybe_t.MaybeTLawFixtures#kinds")
    void identity(String label, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> fa) {
      FunctorLaws.assertIdentity(maybeTMonad, fa, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.maybe_t.MaybeTLawFixtures#kinds")
    void composition(String label, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> fa) {
      FunctorLaws.assertComposition(maybeTMonad, fa, validMapper, secondMapper, equalityChecker);
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLawTests {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.maybe_t.MaybeTLawFixtures#kinds")
    void identity(String label, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> v) {
      ApplicativeLaws.assertIdentity(maybeTMonad, v, equalityChecker);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.maybe_t.MaybeTLawFixtures#values")
    void homomorphism(Integer value) {
      ApplicativeLaws.assertHomomorphism(maybeTMonad, value, validMapper, equalityChecker);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.maybe_t.MaybeTLawFixtures#values")
    void interchange(Integer value) {
      ApplicativeLaws.assertInterchange(maybeTMonad, validFunctionKind, value, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.maybe_t.MaybeTLawFixtures#kinds")
    void composition(String label, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> w) {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Function<String, String>> u =
          justT(secondMapper);
      ApplicativeLaws.assertComposition(maybeTMonad, u, validFunctionKind, w, equalityChecker);
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLawTests {

    @ParameterizedTest(name = "left identity holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.maybe_t.MaybeTLawFixtures#values")
    void leftIdentity(Integer value) {
      MonadLaws.assertLeftIdentity(maybeTMonad, value, testFunction, equalityChecker);
    }

    @ParameterizedTest(name = "right identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.maybe_t.MaybeTLawFixtures#kinds")
    void rightIdentity(String label, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> m) {
      MonadLaws.assertRightIdentity(maybeTMonad, m, equalityChecker);
    }

    @ParameterizedTest(name = "associativity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.maybe_t.MaybeTLawFixtures#kinds")
    void associativity(String label, Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> m) {
      MonadLaws.assertAssociativity(maybeTMonad, m, testFunction, chainFunction, equalityChecker);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("of with null value")
    void of_withNullValue() {
      var result = maybeTMonad.of(null);
      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isPresentNothing();
    }

    @Test
    @DisplayName("raiseError with null error")
    void raiseError_withNullError() {
      Kind<MaybeTKind.Witness<OptionalKind.Witness>, Integer> result = maybeTMonad.raiseError(null);
      assertThatMaybeT(result, MaybeTMonadTest.this::unwrapOuterOptional).isPresentNothing();
    }
  }
}
