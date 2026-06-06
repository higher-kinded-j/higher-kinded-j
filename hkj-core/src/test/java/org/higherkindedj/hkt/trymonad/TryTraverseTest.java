// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.TraverseLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("TryTraverse")
class TryTraverseTest extends TryTestBase {

  private static final MaybeKindHelper MAYBE = MaybeKindHelper.MAYBE;

  private static final Monoid<Integer> SUM_MONOID = Monoids.integerAddition();

  private Traverse<TryKind.Witness> traverse;
  private Applicative<MaybeKind.Witness> maybeApplicative;
  private Function<String, Kind<MaybeKind.Witness, Integer>> validTraverseFunction;

  @BeforeEach
  void setUpTraverse() {
    traverse = TryTraverse.INSTANCE;
    maybeApplicative = Instances.monadError(maybe());
    validTraverseFunction = s -> MAYBE.widen(Maybe.just(Integer.parseInt(s)));
  }

  // validKind/validMapper are parse-oriented so the traverse function can turn the String success
  // value into an Integer inside the Maybe applicative.
  @Override
  protected Kind<TryKind.Witness, String> createValidKind() {
    return TRY.widen(Try.success("42"));
  }

  @Override
  protected Function<String, Integer> createValidMapper() {
    return Integer::parseInt;
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.trymonad.TryLawFixtures#kinds")
    void identity(String label, Kind<TryKind.Witness, String> fa) {
      TraverseLaws.assertIdentity(traverse, fa, equalityChecker);
    }
  }

  /**
   * {@link Category#EXCEPTIONS} is omitted: the generic contract asserts that {@code map}
   * <em>propagates</em> a thrown mapper exception, but {@code Try.map} instead captures it as a
   * {@link Try.Failure}. ({@code foldMap}/{@code traverse} do propagate — those, and the {@code
   * map} capture, are verified in {@link ExceptionHandlingTests}.)
   */
  @Test
  @DisplayName(
      "Traverse contract — operations & validations (identity law verified above; Try captures the"
          + " map exception, verified below)")
  void traverseContract() {
    TypeClassContract.<TryKind.Witness>traverse(TryTraverse.class)
        .<String>instance(traverse)
        .<Integer>withKind(validKind)
        .withMapper(validMapper)
        .withApplicative(maybeApplicative, validTraverseFunction)
        .withFoldable(SUM_MONOID, validMapper)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("traverse() on Success applies the function and wraps the result")
    void traverseOnSuccessAppliesFunction() {
      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.traverse(maybeApplicative, validTraverseFunction, validKind);

      Maybe<Kind<TryKind.Witness, Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThatTry(maybe.get()).isSuccess().hasValue(42);
    }

    @Test
    @DisplayName("traverse() on Failure wraps the Failure in the applicative")
    void traverseOnFailureWrapsFailure() {
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);

      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.traverse(maybeApplicative, validTraverseFunction, failure);

      Maybe<Kind<TryKind.Witness, Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThatTry(maybe.get()).isFailure().hasException(DEFAULT_TEST_EXCEPTION);
    }

    @Test
    @DisplayName("traverse() with a function returning Nothing collapses to Nothing")
    void traverseWithNothingReturningFunction() {
      Function<String, Kind<MaybeKind.Witness, Integer>> toNothing =
          _ -> MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.traverse(maybeApplicative, toNothing, validKind);

      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("traverse() handles a null value inside Success")
    @SuppressWarnings("ConstantValue") // a Success may legitimately hold a null value
    void traverseHandlesNullValueInSuccess() {
      Kind<TryKind.Witness, String> successNull = TRY.widen(Try.success(null));
      Function<String, Kind<MaybeKind.Witness, Integer>> safe =
          s -> MAYBE.widen(Maybe.just(s == null ? -1 : Integer.parseInt(s)));

      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.traverse(maybeApplicative, safe, successNull);

      Maybe<Kind<TryKind.Witness, Integer>> maybe = MAYBE.narrow(result);
      assertThatTry(maybe.get()).isSuccess().hasValue(-1);
    }
  }

  @Nested
  @DisplayName("Inherited map and foldMap")
  class InheritedOperations {

    @Test
    @DisplayName("map() on Success applies the function")
    void mapOnSuccessAppliesFunction() {
      Kind<TryKind.Witness, Integer> result = traverse.map(Integer::parseInt, validKind);
      assertThatTry(result).isSuccess().hasValue(42);
    }

    @Test
    @DisplayName("map() on Failure preserves the Failure")
    void mapOnFailurePreservesFailure() {
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);
      Kind<TryKind.Witness, Integer> result = traverse.map(Integer::parseInt, failure);
      assertThatTry(result).isFailure().hasException(DEFAULT_TEST_EXCEPTION);
    }

    @Test
    @DisplayName("foldMap() on Success applies the function; on Failure returns identity")
    void foldMapDistinguishesSuccessAndFailure() {
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);

      assertThat(traverse.foldMap(SUM_MONOID, Integer::parseInt, validKind)).isEqualTo(42);
      assertThat(traverse.foldMap(SUM_MONOID, Integer::parseInt, failure))
          .isEqualTo(SUM_MONOID.empty());
    }
  }

  @Nested
  @DisplayName("Exception Handling Tests")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("traverse() propagates a function exception on Success")
    void traversePropagatesExceptionFromFunction() {
      RuntimeException boom = new RuntimeException("traverse boom");
      Function<String, Kind<MaybeKind.Witness, Integer>> throwing =
          _ -> {
            throw boom;
          };

      assertThatThrownBy(() -> traverse.traverse(maybeApplicative, throwing, validKind))
          .isSameAs(boom);
    }

    @Test
    @DisplayName("traverse() does not call the function on Failure")
    void traverseDoesNotCallFunctionOnFailure() {
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);
      Function<String, Kind<MaybeKind.Witness, Integer>> throwing =
          _ -> {
            throw new AssertionError("function must not run on a Failure");
          };

      assertThatCode(() -> traverse.traverse(maybeApplicative, throwing, failure))
          .doesNotThrowAnyException();

      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.traverse(maybeApplicative, throwing, failure);
      assertThatTry(MAYBE.narrow(result).get()).isFailure();
    }

    @Test
    @DisplayName("map() captures a function exception as a Failure on Success")
    void mapCapturesExceptionAsFailure() {
      RuntimeException boom = new RuntimeException("map boom");
      Function<String, Integer> throwing =
          _ -> {
            throw boom;
          };

      Kind<TryKind.Witness, Integer> result = traverse.map(throwing, validKind);
      assertThatTry(result).isFailure().hasException(boom);
    }

    @Test
    @DisplayName("foldMap() propagates a function exception on Success")
    void foldMapPropagatesExceptionFromFunction() {
      RuntimeException boom = new RuntimeException("foldMap boom");
      Function<String, Integer> throwing =
          _ -> {
            throw boom;
          };

      assertThatThrownBy(() -> traverse.foldMap(SUM_MONOID, throwing, validKind)).isSameAs(boom);
    }
  }
}
