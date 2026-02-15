// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.free_ap.FreeApKindHelper.FREE_AP;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link FreeAp}.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Pure and Lift operations
 *   <li>Map operations
 *   <li>Ap (applicative) operations
 *   <li>Map2 combining operations
 *   <li>FoldMap interpretation
 *   <li>Applicative laws
 *   <li>Independence/parallelism verification
 * </ul>
 */
@DisplayName("FreeAp Tests")
class FreeApTest {

  private static final Applicative<MaybeKind.Witness> MAYBE_APPLICATIVE = MaybeMonad.INSTANCE;

  // Identity natural transformation for Maybe
  private static final Natural<MaybeKind.Witness, MaybeKind.Witness> IDENTITY_NAT =
      Natural.identity();

  // ============================================================================
  // Pure Tests
  // ============================================================================

  @Nested
  @DisplayName("Pure Operations")
  class PureTests {

    @Test
    @DisplayName("pure creates a Pure FreeAp")
    void pureCreatesPure() {
      FreeAp<MaybeKind.Witness, Integer> freeAp = FreeAp.pure(42);

      assertThat(freeAp).isInstanceOf(FreeAp.Pure.class);
      assertThat(((FreeAp.Pure<?, Integer>) freeAp).value()).isEqualTo(42);
    }

    @Test
    @DisplayName("pure allows null values")
    void pureAllowsNull() {
      FreeAp<MaybeKind.Witness, String> freeAp = FreeAp.pure(null);

      assertThat(freeAp).isInstanceOf(FreeAp.Pure.class);
      assertThat(((FreeAp.Pure<?, String>) freeAp).value()).isNull();
    }

    @Test
    @DisplayName("pure value can be retrieved via foldMap")
    void pureValueCanBeRetrieved() {
      FreeAp<MaybeKind.Witness, Integer> freeAp = FreeAp.pure(42);

      Kind<MaybeKind.Witness, Integer> result = freeAp.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }
  }

  // ============================================================================
  // Lift Tests
  // ============================================================================

  @Nested
  @DisplayName("Lift Operations")
  class LiftTests {

    @Test
    @DisplayName("lift creates a Lift FreeAp")
    void liftCreatesLift() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(42);

      FreeAp<MaybeKind.Witness, Integer> freeAp = FreeAp.lift(just);

      assertThat(freeAp).isInstanceOf(FreeAp.Lift.class);
    }

    @Test
    @DisplayName("lift throws for null")
    void liftThrowsForNull() {
      assertThatThrownBy(() -> FreeAp.lift(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("null");
    }

    @Test
    @DisplayName("lifted value can be interpreted")
    void liftedValueCanBeInterpreted() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(42);
      FreeAp<MaybeKind.Witness, Integer> freeAp = FreeAp.lift(just);

      Kind<MaybeKind.Witness, Integer> result = freeAp.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("lifted Nothing is preserved")
    void liftedNothingIsPreserved() {
      Kind<MaybeKind.Witness, String> nothing = MAYBE.nothing();
      FreeAp<MaybeKind.Witness, String> freeAp = FreeAp.lift(nothing);

      Kind<MaybeKind.Witness, String> result = freeAp.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }
  }

  // ============================================================================
  // Map Tests
  // ============================================================================

  @Nested
  @DisplayName("Map Operations")
  class MapTests {

    @Test
    @DisplayName("map transforms pure value")
    void mapTransformsPureValue() {
      FreeAp<MaybeKind.Witness, Integer> freeAp = FreeAp.pure(10);

      FreeAp<MaybeKind.Witness, String> mapped = freeAp.map(Object::toString);

      Kind<MaybeKind.Witness, String> result = mapped.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(result).get()).isEqualTo("10");
    }

    @Test
    @DisplayName("map transforms lifted value")
    void mapTransformsLiftedValue() {
      FreeAp<MaybeKind.Witness, Integer> freeAp = FreeAp.lift(MAYBE.just(10));

      FreeAp<MaybeKind.Witness, Integer> mapped = freeAp.map(x -> x * 2);

      Kind<MaybeKind.Witness, Integer> result = mapped.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(result).get()).isEqualTo(20);
    }

    @Test
    @DisplayName("map throws for null function")
    void mapThrowsForNullFunction() {
      FreeAp<MaybeKind.Witness, Integer> freeAp = FreeAp.pure(42);

      assertThatThrownBy(() -> freeAp.map(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("null");
    }

    @Test
    @DisplayName("multiple maps can be chained")
    void multipleMapsCanBeChained() {
      FreeAp<MaybeKind.Witness, Integer> freeAp = FreeAp.pure(5);

      FreeAp<MaybeKind.Witness, String> mapped =
          freeAp.map(x -> x * 2).map(x -> x + 1).map(Object::toString);

      Kind<MaybeKind.Witness, String> result = mapped.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(result).get()).isEqualTo("11"); // (5 * 2) + 1
    }
  }

  // ============================================================================
  // Ap Tests
  // ============================================================================

  @Nested
  @DisplayName("Ap Operations")
  class ApTests {

    @Test
    @DisplayName("ap applies wrapped function to wrapped value")
    void apAppliesFunction() {
      FreeAp<MaybeKind.Witness, Integer> value = FreeAp.pure(10);
      FreeAp<MaybeKind.Witness, Function<Integer, String>> func = FreeAp.pure(x -> "value:" + x);

      FreeAp<MaybeKind.Witness, String> result = value.ap(func);

      Kind<MaybeKind.Witness, String> interpreted = result.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(interpreted).get()).isEqualTo("value:10");
    }

    @Test
    @DisplayName("ap works with lifted values")
    void apWorksWithLiftedValues() {
      FreeAp<MaybeKind.Witness, Integer> value = FreeAp.lift(MAYBE.just(10));
      FreeAp<MaybeKind.Witness, Function<Integer, Integer>> func = FreeAp.pure(x -> x * 2);

      FreeAp<MaybeKind.Witness, Integer> result = value.ap(func);

      Kind<MaybeKind.Witness, Integer> interpreted =
          result.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(interpreted).get()).isEqualTo(20);
    }

    @Test
    @DisplayName("ap throws for null function FreeAp")
    void apThrowsForNullFunction() {
      FreeAp<MaybeKind.Witness, Integer> value = FreeAp.pure(10);

      assertThatThrownBy(() -> value.ap(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("null");
    }

    @Test
    @DisplayName("ap creates Ap structure")
    void apCreatesApStructure() {
      FreeAp<MaybeKind.Witness, Integer> value = FreeAp.pure(10);
      FreeAp<MaybeKind.Witness, Function<Integer, String>> func = FreeAp.pure(Object::toString);

      FreeAp<MaybeKind.Witness, String> result = value.ap(func);

      assertThat(result).isInstanceOf(FreeAp.Ap.class);
    }
  }

  // ============================================================================
  // Map2 Tests
  // ============================================================================

  @Nested
  @DisplayName("Map2 Operations")
  class Map2Tests {

    @Test
    @DisplayName("map2 combines two pure values")
    void map2CombinesPureValues() {
      FreeAp<MaybeKind.Witness, Integer> a = FreeAp.pure(10);
      FreeAp<MaybeKind.Witness, Integer> b = FreeAp.pure(5);

      FreeAp<MaybeKind.Witness, Integer> result = a.map2(b, Integer::sum);

      Kind<MaybeKind.Witness, Integer> interpreted =
          result.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(interpreted).get()).isEqualTo(15);
    }

    @Test
    @DisplayName("map2 combines lifted values")
    void map2CombinesLiftedValues() {
      FreeAp<MaybeKind.Witness, Integer> a = FreeAp.lift(MAYBE.just(10));
      FreeAp<MaybeKind.Witness, Integer> b = FreeAp.lift(MAYBE.just(5));

      FreeAp<MaybeKind.Witness, Integer> result = a.map2(b, (x, y) -> x * y);

      Kind<MaybeKind.Witness, Integer> interpreted =
          result.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(interpreted).get()).isEqualTo(50);
    }

    @Test
    @DisplayName("map2 with Nothing propagates Nothing")
    void map2WithNothingPropagatesNothing() {
      FreeAp<MaybeKind.Witness, Integer> a = FreeAp.lift(MAYBE.just(10));
      FreeAp<MaybeKind.Witness, Integer> b = FreeAp.lift(MAYBE.nothing());

      FreeAp<MaybeKind.Witness, Integer> result = a.map2(b, Integer::sum);

      Kind<MaybeKind.Witness, Integer> interpreted =
          result.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(interpreted).isNothing()).isTrue();
    }
  }

  // ============================================================================
  // FoldMap Tests
  // ============================================================================

  @Nested
  @DisplayName("FoldMap Interpretation")
  class FoldMapTests {

    @Test
    @DisplayName("foldMap interprets complex structure")
    void foldMapInterpretsComplexStructure() {
      FreeAp<MaybeKind.Witness, Integer> a = FreeAp.lift(MAYBE.just(10));
      FreeAp<MaybeKind.Witness, Integer> b = FreeAp.lift(MAYBE.just(5));
      FreeAp<MaybeKind.Witness, Integer> c = FreeAp.lift(MAYBE.just(2));

      // (a + b) * c
      FreeAp<MaybeKind.Witness, Integer> sum = a.map2(b, Integer::sum);
      FreeAp<MaybeKind.Witness, Integer> result = sum.map2(c, (s, mult) -> s * mult);

      Kind<MaybeKind.Witness, Integer> interpreted =
          result.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(interpreted).get()).isEqualTo(30); // (10 + 5) * 2
    }

    @Test
    @DisplayName("foldMap throws for null transform")
    void foldMapThrowsForNullTransform() {
      FreeAp<MaybeKind.Witness, Integer> freeAp = FreeAp.pure(42);

      assertThatThrownBy(() -> freeAp.foldMap(null, MAYBE_APPLICATIVE))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("foldMap throws for null applicative")
    void foldMapThrowsForNullApplicative() {
      FreeAp<MaybeKind.Witness, Integer> freeAp = FreeAp.pure(42);

      assertThatThrownBy(() -> freeAp.foldMap(IDENTITY_NAT, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  // ============================================================================
  // Independence/Parallelism Tests
  // ============================================================================

  @Nested
  @DisplayName("Independence Verification")
  class IndependenceTests {

    @Test
    @DisplayName("ap operands are independent - both are evaluated")
    void apOperandsAreIndependent() {
      List<String> evaluationOrder = new ArrayList<>();

      // Create a tracking applicative
      var trackingApplicative =
          new MaybeMonad() {
            @Override
            public <A> Kind<MaybeKind.Witness, A> of(@Nullable A value) {
              return super.of(value);
            }
          };

      // Create transformation that tracks evaluation
      Natural<MaybeKind.Witness, MaybeKind.Witness> trackingNat =
          new Natural<>() {
            @Override
            public <A> Kind<MaybeKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
              Maybe<A> maybe = MAYBE.narrow(fa);
              if (maybe.isJust()) {
                evaluationOrder.add("evaluated:" + maybe.get());
              }
              return fa;
            }
          };

      FreeAp<MaybeKind.Witness, Integer> a = FreeAp.lift(MAYBE.just(1));
      FreeAp<MaybeKind.Witness, Integer> b = FreeAp.lift(MAYBE.just(2));

      FreeAp<MaybeKind.Witness, Integer> combined = a.map2(b, Integer::sum);

      combined.foldMap(trackingNat, trackingApplicative);

      // Both should be evaluated (order may vary in parallel implementation)
      assertThat(evaluationOrder).hasSize(2);
      assertThat(evaluationOrder).containsExactlyInAnyOrder("evaluated:1", "evaluated:2");
    }

    @Test
    @DisplayName("three-way combination evaluates all branches")
    void threeWayCombinationEvaluatesAllBranches() {
      AtomicInteger evaluationCount = new AtomicInteger(0);

      Natural<MaybeKind.Witness, MaybeKind.Witness> countingNat =
          new Natural<>() {
            @Override
            public <A> Kind<MaybeKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
              evaluationCount.incrementAndGet();
              return fa;
            }
          };

      FreeAp<MaybeKind.Witness, Integer> a = FreeAp.lift(MAYBE.just(1));
      FreeAp<MaybeKind.Witness, Integer> b = FreeAp.lift(MAYBE.just(2));
      FreeAp<MaybeKind.Witness, Integer> c = FreeAp.lift(MAYBE.just(3));

      FreeAp<MaybeKind.Witness, Integer> result = a.map2(b, Integer::sum).map2(c, Integer::sum);

      result.foldMap(countingNat, MAYBE_APPLICATIVE);

      // All three lifted values should be evaluated
      assertThat(evaluationCount.get()).isEqualTo(3);
    }
  }

  // ============================================================================
  // Applicative Laws Tests
  // ============================================================================

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLawsTests {

    @Test
    @DisplayName("Identity law: pure(id).ap(fa) == fa")
    void identityLaw() {
      FreeAp<MaybeKind.Witness, Integer> fa = FreeAp.lift(MAYBE.just(42));
      FreeAp<MaybeKind.Witness, Function<Integer, Integer>> pureId =
          FreeAp.pure(Function.identity());

      FreeAp<MaybeKind.Witness, Integer> result = fa.ap(pureId);

      Kind<MaybeKind.Witness, Integer> originalInterp = fa.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);
      Kind<MaybeKind.Witness, Integer> resultInterp =
          result.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(originalInterp).get()).isEqualTo(MAYBE.narrow(resultInterp).get());
    }

    @Test
    @DisplayName("Homomorphism law: pure(f).ap(pure(x)) == pure(f(x))")
    void homomorphismLaw() {
      Function<Integer, String> f = Object::toString;
      Integer x = 42;

      FreeAp<MaybeKind.Witness, Function<Integer, String>> pureF = FreeAp.pure(f);
      FreeAp<MaybeKind.Witness, Integer> pureX = FreeAp.pure(x);
      FreeAp<MaybeKind.Witness, String> left = pureX.ap(pureF);

      FreeAp<MaybeKind.Witness, String> right = FreeAp.pure(f.apply(x));

      Kind<MaybeKind.Witness, String> leftInterp = left.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);
      Kind<MaybeKind.Witness, String> rightInterp = right.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(leftInterp).get()).isEqualTo(MAYBE.narrow(rightInterp).get());
    }
  }

  // ============================================================================
  // Retract Tests
  // ============================================================================

  @Nested
  @DisplayName("Retract Operations")
  class RetractTests {

    @Test
    @DisplayName("retract recovers lifted value")
    void retractRecoversLiftedValue() {
      Kind<MaybeKind.Witness, Integer> original = MAYBE.just(42);
      FreeAp<MaybeKind.Witness, Integer> freeAp = FreeAp.lift(original);

      Kind<MaybeKind.Witness, Integer> retracted = freeAp.retract(MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(retracted).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("retract works on complex structures")
    void retractWorksOnComplexStructures() {
      FreeAp<MaybeKind.Witness, Integer> a = FreeAp.lift(MAYBE.just(10));
      FreeAp<MaybeKind.Witness, Integer> b = FreeAp.lift(MAYBE.just(5));
      FreeAp<MaybeKind.Witness, Integer> combined = a.map2(b, Integer::sum);

      Kind<MaybeKind.Witness, Integer> retracted = combined.retract(MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(retracted).get()).isEqualTo(15);
    }
  }

  // ============================================================================
  // KindHelper Tests
  // ============================================================================

  @Nested
  @DisplayName("KindHelper Operations")
  class KindHelperTests {

    @Test
    @DisplayName("widen and narrow are inverse operations")
    void widenNarrowInverse() {
      FreeAp<MaybeKind.Witness, Integer> original = FreeAp.pure(42);

      Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> widened = FREE_AP.widen(original);
      FreeAp<MaybeKind.Witness, Integer> narrowed = FREE_AP.narrow(widened);

      Kind<MaybeKind.Witness, Integer> originalResult =
          original.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);
      Kind<MaybeKind.Witness, Integer> narrowedResult =
          narrowed.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(originalResult).get()).isEqualTo(MAYBE.narrow(narrowedResult).get());
    }

    @Test
    @DisplayName("narrow throws for null")
    void narrowThrowsForNull() {
      assertThatThrownBy(() -> FREE_AP.narrow(null)).isInstanceOf(KindUnwrapException.class);
    }

    @Test
    @DisplayName("widen throws for null")
    void widenThrowsForNull() {
      assertThatThrownBy(() -> FREE_AP.widen(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("narrow throws for invalid kind type")
    void narrowThrowsForInvalidKindType() {
      // Create a mock Kind that is not a FreeApHolder
      @SuppressWarnings("unchecked")
      Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> invalidKind =
          (Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer>)
              new FreeApKind<MaybeKind.Witness, Integer>() {};

      assertThatThrownBy(() -> FREE_AP.narrow(invalidKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("expected FreeApHolder");
    }

    @Test
    @DisplayName("pure convenience method works")
    void pureConvenienceMethodWorks() {
      Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> kind = FREE_AP.pure(42);

      FreeAp<MaybeKind.Witness, Integer> freeAp = FREE_AP.narrow(kind);
      Kind<MaybeKind.Witness, Integer> result = freeAp.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("lift convenience method works")
    void liftConvenienceMethodWorks() {
      Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> kind = FREE_AP.lift(MAYBE.just(42));

      FreeAp<MaybeKind.Witness, Integer> freeAp = FREE_AP.narrow(kind);
      Kind<MaybeKind.Witness, Integer> result = freeAp.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }
  }

  // ============================================================================
  // Analyse Tests
  // ============================================================================

  @Nested
  @DisplayName("Analyse Operations")
  class AnalyseTests {

    @Test
    @DisplayName("analyse works like foldMap")
    void analyseWorksLikeFoldMap() {
      FreeAp<MaybeKind.Witness, Integer> a = FreeAp.lift(MAYBE.just(10));
      FreeAp<MaybeKind.Witness, Integer> b = FreeAp.lift(MAYBE.just(5));
      FreeAp<MaybeKind.Witness, Integer> combined = a.map2(b, Integer::sum);

      Kind<MaybeKind.Witness, Integer> analysed = combined.analyse(IDENTITY_NAT, MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(analysed).get()).isEqualTo(15);
    }
  }

  // ============================================================================
  // Functor Instance Tests
  // ============================================================================

  @Nested
  @DisplayName("FreeApFunctor Instance")
  class FreeApFunctorInstanceTests {

    @Test
    @DisplayName("instance returns singleton")
    void instanceReturnsSingleton() {
      FreeApFunctor<MaybeKind.Witness> functor1 = FreeApFunctor.instance();
      FreeApFunctor<MaybeKind.Witness> functor2 = FreeApFunctor.instance();

      assertThat(functor1).isSameAs(functor2);
    }

    @Test
    @DisplayName("functor map works correctly")
    void functorMapWorksCorrectly() {
      FreeApFunctor<MaybeKind.Witness> functor = FreeApFunctor.instance();
      Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> kind = FREE_AP.widen(FreeAp.pure(42));

      Kind<FreeApKind.Witness<MaybeKind.Witness>, String> mapped =
          functor.map(x -> "value:" + x, kind);

      FreeAp<MaybeKind.Witness, String> freeAp = FREE_AP.narrow(mapped);
      Kind<MaybeKind.Witness, String> result = freeAp.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(result).get()).isEqualTo("value:42");
    }
  }

  // ============================================================================
  // Applicative Instance Tests
  // ============================================================================

  @Nested
  @DisplayName("FreeApApplicative Instance")
  class FreeApApplicativeInstanceTests {

    @Test
    @DisplayName("instance returns singleton")
    void instanceReturnsSingleton() {
      FreeApApplicative<MaybeKind.Witness> app1 = FreeApApplicative.instance();
      FreeApApplicative<MaybeKind.Witness> app2 = FreeApApplicative.instance();

      assertThat(app1).isSameAs(app2);
    }
  }
}
