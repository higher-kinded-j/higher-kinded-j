// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.coyoneda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.coyoneda.CoyonedaKindHelper.COYONEDA;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.maybe.MaybeFunctor;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link Coyoneda}.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Lift operations
 *   <li>Map operations and fusion
 *   <li>Lower operations
 *   <li>Functor laws
 *   <li>Edge cases
 * </ul>
 */
@DisplayName("Coyoneda Tests")
class CoyonedaTest {

  private static final MaybeFunctor MAYBE_FUNCTOR = MaybeFunctor.INSTANCE;

  // ============================================================================
  // Lift Tests
  // ============================================================================

  @Nested
  @DisplayName("Lift Operations")
  class LiftTests {

    @Test
    @DisplayName("lift wraps a Kind with identity transformation")
    void liftWrapsWithIdentity() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(42);

      Coyoneda<MaybeKind.Witness, Integer> coyo = Coyoneda.lift(just);

      // Lower immediately should give back the same value
      Kind<MaybeKind.Witness, Integer> result = coyo.lower(MAYBE_FUNCTOR);
      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("lift preserves Nothing")
    void liftPreservesNothing() {
      Kind<MaybeKind.Witness, String> nothing = MAYBE.nothing();

      Coyoneda<MaybeKind.Witness, String> coyo = Coyoneda.lift(nothing);

      Kind<MaybeKind.Witness, String> result = coyo.lower(MAYBE_FUNCTOR);
      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("lift throws NullPointerException for null Kind")
    void liftThrowsForNull() {
      assertThatThrownBy(() -> Coyoneda.lift(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("null");
    }

    @Test
    @DisplayName("underlying returns the original Kind")
    void underlyingReturnsOriginal() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(99);

      Coyoneda<MaybeKind.Witness, Integer> coyo = Coyoneda.lift(just);

      assertThat(coyo.underlying()).isSameAs(just);
    }
  }

  // ============================================================================
  // Apply Factory Tests
  // ============================================================================

  @Nested
  @DisplayName("Apply Factory")
  class ApplyTests {

    @Test
    @DisplayName("apply creates Coyoneda with custom transformation")
    void applyCreatesWithTransformation() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(10);
      Function<Integer, String> transform = x -> "value:" + x;

      Coyoneda<MaybeKind.Witness, String> coyo = Coyoneda.apply(just, transform);

      Kind<MaybeKind.Witness, String> result = coyo.lower(MAYBE_FUNCTOR);
      assertThat(MAYBE.narrow(result).get()).isEqualTo("value:10");
    }

    @Test
    @DisplayName("apply with identity equals lift")
    void applyWithIdentityEqualsLift() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(42);

      Coyoneda<MaybeKind.Witness, Integer> fromLift = Coyoneda.lift(just);
      Coyoneda<MaybeKind.Witness, Integer> fromApply = Coyoneda.apply(just, Function.identity());

      Kind<MaybeKind.Witness, Integer> liftResult = fromLift.lower(MAYBE_FUNCTOR);
      Kind<MaybeKind.Witness, Integer> applyResult = fromApply.lower(MAYBE_FUNCTOR);

      assertThat(MAYBE.narrow(liftResult).get()).isEqualTo(MAYBE.narrow(applyResult).get());
    }
  }

  // ============================================================================
  // Map Tests
  // ============================================================================

  @Nested
  @DisplayName("Map Operations")
  class MapTests {

    @Test
    @DisplayName("map transforms the value")
    void mapTransformsValue() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(10);

      Coyoneda<MaybeKind.Witness, Integer> coyo =
          Coyoneda.lift(just).map(x -> x * 2).map(x -> x + 5);

      Kind<MaybeKind.Witness, Integer> result = coyo.lower(MAYBE_FUNCTOR);
      assertThat(MAYBE.narrow(result).get()).isEqualTo(25); // (10 * 2) + 5
    }

    @Test
    @DisplayName("map preserves Nothing")
    void mapPreservesNothing() {
      Kind<MaybeKind.Witness, Integer> nothing = MAYBE.nothing();

      Coyoneda<MaybeKind.Witness, String> coyo =
          Coyoneda.lift(nothing).map(x -> x * 2).map(Object::toString);

      Kind<MaybeKind.Witness, String> result = coyo.lower(MAYBE_FUNCTOR);
      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("map throws NullPointerException for null function")
    void mapThrowsForNullFunction() {
      Coyoneda<MaybeKind.Witness, Integer> coyo = Coyoneda.lift(MAYBE.just(42));

      assertThatThrownBy(() -> coyo.map(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("null");
    }

    @Test
    @DisplayName("map changes type")
    void mapChangesType() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(42);

      Coyoneda<MaybeKind.Witness, String> coyo = Coyoneda.lift(just).map(Object::toString);

      Kind<MaybeKind.Witness, String> result = coyo.lower(MAYBE_FUNCTOR);
      assertThat(MAYBE.narrow(result).get()).isEqualTo("42");
    }
  }

  // ============================================================================
  // Map Fusion Tests
  // ============================================================================

  @Nested
  @DisplayName("Map Fusion")
  class MapFusionTests {

    @Test
    @DisplayName("multiple maps are fused into single traversal")
    void multipleMapsAreFused() {
      AtomicInteger mapCount = new AtomicInteger(0);

      // Create a counting functor that tracks how many times map is called
      var countingFunctor =
          new MaybeFunctor() {
            @Override
            public <A, B> Kind<MaybeKind.Witness, B> map(
                Function<? super A, ? extends B> f, Kind<MaybeKind.Witness, A> fa) {
              mapCount.incrementAndGet();
              return super.map(f, fa);
            }
          };

      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(1);

      // Chain multiple maps
      Coyoneda<MaybeKind.Witness, Integer> coyo =
          Coyoneda.lift(just)
              .map(x -> x + 1)
              .map(x -> x + 1)
              .map(x -> x + 1)
              .map(x -> x + 1)
              .map(x -> x + 1);

      // Before lowering, no maps have been called
      assertThat(mapCount.get()).isZero();

      // Lower - should only call map ONCE
      Kind<MaybeKind.Witness, Integer> result = coyo.lower(countingFunctor);

      assertThat(mapCount.get()).isEqualTo(1);
      assertThat(MAYBE.narrow(result).get()).isEqualTo(6); // 1 + 5 = 6
    }

    @Test
    @DisplayName("fusion preserves computation order")
    void fusionPreservesOrder() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(10);

      // These operations are NOT commutative, so order matters
      Coyoneda<MaybeKind.Witness, Integer> coyo =
          Coyoneda.lift(just)
              .map(x -> x + 5) // 10 + 5 = 15
              .map(x -> x * 2); // 15 * 2 = 30

      Kind<MaybeKind.Witness, Integer> result = coyo.lower(MAYBE_FUNCTOR);
      assertThat(MAYBE.narrow(result).get()).isEqualTo(30);

      // Compare with opposite order
      Coyoneda<MaybeKind.Witness, Integer> coyo2 =
          Coyoneda.lift(just)
              .map(x -> x * 2) // 10 * 2 = 20
              .map(x -> x + 5); // 20 + 5 = 25

      Kind<MaybeKind.Witness, Integer> result2 = coyo2.lower(MAYBE_FUNCTOR);
      assertThat(MAYBE.narrow(result2).get()).isEqualTo(25);
    }
  }

  // ============================================================================
  // Lower Tests
  // ============================================================================

  @Nested
  @DisplayName("Lower Operations")
  class LowerTests {

    @Test
    @DisplayName("lower applies accumulated transformation")
    void lowerAppliesTransformation() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(5);

      Coyoneda<MaybeKind.Witness, String> coyo =
          Coyoneda.lift(just).map(x -> x * x).map(Object::toString);

      Kind<MaybeKind.Witness, String> result = coyo.lower(MAYBE_FUNCTOR);
      assertThat(MAYBE.narrow(result).get()).isEqualTo("25");
    }

    @Test
    @DisplayName("lower throws NullPointerException for null functor")
    void lowerThrowsForNullFunctor() {
      Coyoneda<MaybeKind.Witness, Integer> coyo = Coyoneda.lift(MAYBE.just(42));

      assertThatThrownBy(() -> coyo.lower(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("null");
    }

    @Test
    @DisplayName("lift then immediate lower is identity")
    void liftThenLowerIsIdentity() {
      Kind<MaybeKind.Witness, Integer> original = MAYBE.just(42);

      Kind<MaybeKind.Witness, Integer> result = Coyoneda.lift(original).lower(MAYBE_FUNCTOR);

      assertThat(MAYBE.narrow(result).get()).isEqualTo(MAYBE.narrow(original).get());
    }
  }

  // ============================================================================
  // Functor Laws Tests
  // ============================================================================

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLawsTests {

    @Test
    @DisplayName("Identity law: map(id, fa) == fa")
    void identityLaw() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(42);

      Coyoneda<MaybeKind.Witness, Integer> coyo = Coyoneda.lift(just);
      Coyoneda<MaybeKind.Witness, Integer> mapped = coyo.map(Function.identity());

      Kind<MaybeKind.Witness, Integer> original = coyo.lower(MAYBE_FUNCTOR);
      Kind<MaybeKind.Witness, Integer> afterMap = mapped.lower(MAYBE_FUNCTOR);

      assertThat(MAYBE.narrow(original).get()).isEqualTo(MAYBE.narrow(afterMap).get());
    }

    @Test
    @DisplayName("Composition law: map(g, map(f, fa)) == map(g.compose(f), fa)")
    void compositionLaw() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(5);
      Function<Integer, Integer> f = x -> x * 2;
      Function<Integer, String> g = Object::toString;

      // map(g, map(f, fa))
      Coyoneda<MaybeKind.Witness, String> chained = Coyoneda.lift(just).map(f).map(g);

      // map(g.compose(f), fa)
      Function<Integer, String> composed = f.andThen(g);
      Coyoneda<MaybeKind.Witness, String> direct = Coyoneda.lift(just).map(composed);

      Kind<MaybeKind.Witness, String> chainedResult = chained.lower(MAYBE_FUNCTOR);
      Kind<MaybeKind.Witness, String> directResult = direct.lower(MAYBE_FUNCTOR);

      assertThat(MAYBE.narrow(chainedResult).get()).isEqualTo(MAYBE.narrow(directResult).get());
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
      Coyoneda<MaybeKind.Witness, Integer> original = Coyoneda.lift(MAYBE.just(42));

      Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer> widened = COYONEDA.widen(original);
      Coyoneda<MaybeKind.Witness, Integer> narrowed = COYONEDA.narrow(widened);

      Kind<MaybeKind.Witness, Integer> originalResult = original.lower(MAYBE_FUNCTOR);
      Kind<MaybeKind.Witness, Integer> narrowedResult = narrowed.lower(MAYBE_FUNCTOR);

      assertThat(MAYBE.narrow(originalResult).get()).isEqualTo(MAYBE.narrow(narrowedResult).get());
    }

    @Test
    @DisplayName("lift convenience method creates widened Coyoneda")
    void liftConvenienceMethod() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(42);

      Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer> widened = COYONEDA.lift(just);

      Coyoneda<MaybeKind.Witness, Integer> coyo = COYONEDA.narrow(widened);
      Kind<MaybeKind.Witness, Integer> result = coyo.lower(MAYBE_FUNCTOR);

      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("narrow throws for null")
    void narrowThrowsForNull() {
      assertThatThrownBy(() -> COYONEDA.narrow(null)).isInstanceOf(KindUnwrapException.class);
    }

    @Test
    @DisplayName("widen throws for null")
    void widenThrowsForNull() {
      assertThatThrownBy(() -> COYONEDA.widen(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("narrow throws for invalid kind type")
    void narrowThrowsForInvalidKindType() {
      // Create a mock Kind that is not a CoyonedaHolder
      @SuppressWarnings("unchecked")
      Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer> invalidKind =
          (Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer>)
              new CoyonedaKind<MaybeKind.Witness, Integer>() {};

      assertThatThrownBy(() -> COYONEDA.narrow(invalidKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("expected CoyonedaHolder");
    }
  }

  // ============================================================================
  // Functor Instance Tests
  // ============================================================================

  @Nested
  @DisplayName("CoyonedaFunctor Instance")
  class CoyonedaFunctorInstanceTests {

    @Test
    @DisplayName("instance returns singleton")
    void instanceReturnsSingleton() {
      CoyonedaFunctor<MaybeKind.Witness> functor1 = CoyonedaFunctor.instance();
      CoyonedaFunctor<MaybeKind.Witness> functor2 = CoyonedaFunctor.instance();

      assertThat(functor1).isSameAs(functor2);
    }
  }

  // ============================================================================
  // Edge Cases
  // ============================================================================

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("handles complex type transformations")
    void handlesComplexTypeTransformations() {
      record Person(String name, int age) {}

      Kind<MaybeKind.Witness, Person> just = MAYBE.just(new Person("Alice", 30));

      Coyoneda<MaybeKind.Witness, String> coyo =
          Coyoneda.lift(just).map(Person::name).map(String::toUpperCase).map(s -> s + "!");

      Kind<MaybeKind.Witness, String> result = coyo.lower(MAYBE_FUNCTOR);
      assertThat(MAYBE.narrow(result).get()).isEqualTo("ALICE!");
    }

    @Test
    @DisplayName("many chained maps work correctly")
    void manyChainedMapsWork() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(0);

      Coyoneda<MaybeKind.Witness, Integer> coyo = Coyoneda.lift(just);
      for (int i = 0; i < 1000; i++) {
        coyo = coyo.map(x -> x + 1);
      }

      Kind<MaybeKind.Witness, Integer> result = coyo.lower(MAYBE_FUNCTOR);
      assertThat(MAYBE.narrow(result).get()).isEqualTo(1000);
    }
  }
}
