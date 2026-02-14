// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.effect.capability.Composable;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for FreeApPath.
 *
 * <p>Tests cover factory methods, Composable/Combinable operations (NOT Chainable), interpretation
 * via foldMap and foldMapKind, and object methods.
 */
@DisplayName("FreeApPath<F, A> Complete Test Suite")
class FreeApPathTest {

  private static final Monad<MaybeKind.Witness> MAYBE_MONAD = MaybeMonad.INSTANCE;
  private static final Applicative<MaybeKind.Witness> MAYBE_APPLICATIVE = MaybeMonad.INSTANCE;

  // Identity natural transformation for Maybe
  private static final Natural<MaybeKind.Witness, MaybeKind.Witness> IDENTITY_NAT =
      Natural.identity();

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodsTests {

    @Test
    @DisplayName("pure() creates FreeApPath with value")
    void pureCreatesFreeApPath() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);

      Kind<MaybeKind.Witness, Integer> result = path.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("pure() validates non-null functor")
    void pureValidatesNonNullFunctor() {
      assertThatNullPointerException()
          .isThrownBy(() -> FreeApPath.pure(42, null))
          .withMessageContaining("functor must not be null");
    }

    @Test
    @DisplayName("liftF() creates FreeApPath from Kind")
    void liftFCreatesFreeApPath() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(42);

      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.liftF(just, MaybeMonad.INSTANCE);

      Kind<MaybeKind.Witness, Integer> result = path.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("liftF() validates non-null arguments")
    void liftFValidatesNonNullArguments() {
      assertThatNullPointerException()
          .isThrownBy(() -> FreeApPath.liftF(null, MaybeMonad.INSTANCE))
          .withMessageContaining("fa must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> FreeApPath.liftF(MAYBE.just(42), null))
          .withMessageContaining("functor must not be null");
    }

    @Test
    @DisplayName("of() creates FreeApPath from FreeAp")
    void ofCreatesFreeApPath() {
      FreeAp<MaybeKind.Witness, Integer> freeAp = FreeAp.pure(42);

      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.of(freeAp, MaybeMonad.INSTANCE);

      Kind<MaybeKind.Witness, Integer> result = path.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("Path.freeApPure() creates FreeApPath with value")
    void pathFreeApPureCreatesFreeApPath() {
      FreeApPath<MaybeKind.Witness, Integer> path = Path.freeApPure(42, MaybeMonad.INSTANCE);

      Kind<MaybeKind.Witness, Integer> result = path.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("Path.freeApLift() creates FreeApPath from Kind")
    void pathFreeApLiftCreatesFreeApPath() {
      FreeApPath<MaybeKind.Witness, Integer> path =
          Path.freeApLift(MAYBE.just(42), MaybeMonad.INSTANCE);

      Kind<MaybeKind.Witness, Integer> result = path.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("Path.freeAp(FreeAp, Functor) creates FreeApPath from existing FreeAp")
    void pathFreeApCreatesFromExistingFreeAp() {
      FreeAp<MaybeKind.Witness, Integer> freeAp = FreeAp.pure(42);

      FreeApPath<MaybeKind.Witness, Integer> path = Path.freeAp(freeAp, MaybeMonad.INSTANCE);

      Kind<MaybeKind.Witness, Integer> result = path.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("Path.freeAp() validates non-null arguments")
    void pathFreeApValidatesNonNullArguments() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.freeAp(null, MaybeMonad.INSTANCE))
          .withMessageContaining("freeAp must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> Path.freeAp(FreeAp.pure(42), null))
          .withMessageContaining("functor must not be null");
    }
  }

  @Nested
  @DisplayName("Interpretation (foldMap, foldMapKind)")
  class InterpretationTests {

    @Test
    @DisplayName("foldMap() returns GenericPath for Monad target")
    void foldMapReturnsGenericPath() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);

      GenericPath<MaybeKind.Witness, Integer> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);

      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("foldMapKind() returns Kind for Applicative target")
    void foldMapKindReturnsKind() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);

      Kind<MaybeKind.Witness, Integer> result = path.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("foldMap() interprets lifted value")
    void foldMapInterpretsLiftedValue() {
      FreeApPath<MaybeKind.Witness, Integer> path =
          FreeApPath.liftF(MAYBE.just(42), MaybeMonad.INSTANCE);

      GenericPath<MaybeKind.Witness, Integer> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);

      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("foldMapKind() preserves Nothing")
    void foldMapKindPreservesNothing() {
      FreeApPath<MaybeKind.Witness, String> path =
          FreeApPath.liftF(MAYBE.nothing(), MaybeMonad.INSTANCE);

      Kind<MaybeKind.Witness, String> result = path.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("foldMap() validates non-null arguments")
    void foldMapValidatesNonNullArguments() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.foldMap(null, MAYBE_MONAD))
          .withMessageContaining("interpreter must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.foldMap(IDENTITY_NAT, null))
          .withMessageContaining("targetMonad must not be null");
    }

    @Test
    @DisplayName("foldMapKind() validates non-null arguments")
    void foldMapKindValidatesNonNullArguments() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.foldMapKind(null, MAYBE_APPLICATIVE))
          .withMessageContaining("interpreter must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.foldMapKind(IDENTITY_NAT, null))
          .withMessageContaining("targetApplicative must not be null");
    }

    @Test
    @DisplayName("foldMapWith() interprets using NaturalTransformation")
    void foldMapWithInterpretsUsingNaturalTransformation() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);

      NaturalTransformation<MaybeKind.Witness, MaybeKind.Witness> transform =
          new NaturalTransformation<>() {
            @Override
            public <A> Kind<MaybeKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
              return fa;
            }
          };

      Kind<MaybeKind.Witness, Integer> result = path.foldMapWith(transform, MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value")
    void mapTransformsValue() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);

      FreeApPath<MaybeKind.Witness, String> mapped = path.map(i -> "value: " + i);

      Kind<MaybeKind.Witness, String> result = mapped.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(result).get()).isEqualTo("value: 42");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      FreeApPath<MaybeKind.Witness, String> path =
          FreeApPath.pure("hello", MaybeMonad.INSTANCE)
              .map(String::toUpperCase)
              .map(s -> s + "!")
              .map(s -> s.repeat(2));

      Kind<MaybeKind.Witness, String> result = path.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(result).get()).isEqualTo("HELLO!HELLO!");
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      AtomicBoolean observed = new AtomicBoolean(false);

      FreeApPath<MaybeKind.Witness, Integer> path =
          FreeApPath.pure(42, MaybeMonad.INSTANCE).peek(i -> observed.set(true));

      Kind<MaybeKind.Witness, Integer> result = path.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
      assertThat(observed).isTrue();
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines two FreeApPaths")
    void zipWithCombinesTwoPaths() {
      FreeApPath<MaybeKind.Witness, String> first = FreeApPath.pure("hello", MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, Integer> second = FreeApPath.pure(3, MaybeMonad.INSTANCE);

      FreeApPath<MaybeKind.Witness, String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      Kind<MaybeKind.Witness, String> interpreted =
          result.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(interpreted).get()).isEqualTo("hellohellohello");
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + (Integer) b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(FreeApPath.pure(1, MaybeMonad.INSTANCE), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith() throws when given non-FreeApPath")
    void zipWithThrowsWhenGivenNonFreeApPath() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);
      MaybePath<Integer> maybePath = Path.just(10);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(maybePath, (a, b) -> a + b))
          .withMessageContaining("FreeApPath.zipWith requires FreeApPath");
    }

    @Test
    @DisplayName("zipWith() with Nothing propagates Nothing")
    void zipWithNothingPropagatesNothing() {
      FreeApPath<MaybeKind.Witness, Integer> first =
          FreeApPath.liftF(MAYBE.just(10), MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, Integer> second =
          FreeApPath.liftF(MAYBE.nothing(), MaybeMonad.INSTANCE);

      FreeApPath<MaybeKind.Witness, Integer> result = first.zipWith(second, Integer::sum);

      Kind<MaybeKind.Witness, Integer> interpreted =
          result.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(interpreted).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("zipWith3 Operations")
  class ZipWith3OperationsTests {

    @Test
    @DisplayName("zipWith3() combines three FreeApPaths")
    void zipWith3CombinesThreePaths() {
      FreeApPath<MaybeKind.Witness, Integer> first = FreeApPath.pure(1, MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, Integer> second = FreeApPath.pure(2, MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, Integer> third = FreeApPath.pure(3, MaybeMonad.INSTANCE);

      FreeApPath<MaybeKind.Witness, Integer> result =
          first.zipWith3(second, third, (a, b, c) -> a + b + c);

      Kind<MaybeKind.Witness, Integer> interpreted =
          result.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(interpreted).get()).isEqualTo(6);
    }

    @Test
    @DisplayName("zipWith3() validates null parameters")
    void zipWith3ValidatesNullParameters() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, Integer> other = FreeApPath.pure(1, MaybeMonad.INSTANCE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith3(null, other, (a, b, c) -> a))
          .withMessageContaining("second must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith3(other, null, (a, b, c) -> a))
          .withMessageContaining("third must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith3(other, other, null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith3() with Nothing propagates Nothing")
    void zipWith3NothingPropagatesNothing() {
      FreeApPath<MaybeKind.Witness, Integer> first =
          FreeApPath.liftF(MAYBE.just(1), MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, Integer> second =
          FreeApPath.liftF(MAYBE.nothing(), MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, Integer> third =
          FreeApPath.liftF(MAYBE.just(3), MaybeMonad.INSTANCE);

      FreeApPath<MaybeKind.Witness, Integer> result =
          first.zipWith3(second, third, (a, b, c) -> a + b + c);

      Kind<MaybeKind.Witness, Integer> interpreted =
          result.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(interpreted).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Independence/Parallelism")
  class IndependenceTests {

    @Test
    @DisplayName("zipWith() operands are independent - both are evaluated")
    void zipWithOperandsAreIndependent() {
      AtomicInteger evaluationCount = new AtomicInteger(0);

      Natural<MaybeKind.Witness, MaybeKind.Witness> countingNat =
          new Natural<>() {
            @Override
            public <A> Kind<MaybeKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
              evaluationCount.incrementAndGet();
              return fa;
            }
          };

      FreeApPath<MaybeKind.Witness, Integer> first =
          FreeApPath.liftF(MAYBE.just(1), MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, Integer> second =
          FreeApPath.liftF(MAYBE.just(2), MaybeMonad.INSTANCE);

      FreeApPath<MaybeKind.Witness, Integer> combined = first.zipWith(second, Integer::sum);

      combined.foldMapKind(countingNat, MAYBE_APPLICATIVE);

      // Both should be evaluated
      assertThat(evaluationCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("zipWith3() evaluates all three branches")
    void zipWith3EvaluatesAllBranches() {
      AtomicInteger evaluationCount = new AtomicInteger(0);

      Natural<MaybeKind.Witness, MaybeKind.Witness> countingNat =
          new Natural<>() {
            @Override
            public <A> Kind<MaybeKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
              evaluationCount.incrementAndGet();
              return fa;
            }
          };

      FreeApPath<MaybeKind.Witness, Integer> first =
          FreeApPath.liftF(MAYBE.just(1), MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, Integer> second =
          FreeApPath.liftF(MAYBE.just(2), MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, Integer> third =
          FreeApPath.liftF(MAYBE.just(3), MaybeMonad.INSTANCE);

      FreeApPath<MaybeKind.Witness, Integer> combined =
          first.zipWith3(second, third, (a, b, c) -> a + b + c);

      combined.foldMapKind(countingNat, MAYBE_APPLICATIVE);

      // All three should be evaluated
      assertThat(evaluationCount.get()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Utility Methods")
  class UtilityMethodsTests {

    @Test
    @DisplayName("toFreeAp() returns underlying FreeAp")
    void toFreeApReturnsUnderlyingFreeAp() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);

      FreeAp<MaybeKind.Witness, Integer> freeAp = path.toFreeAp();

      assertThat(freeAp).isNotNull();
      Kind<MaybeKind.Witness, Integer> result = freeAp.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);
      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("functor() returns the functor instance")
    void functorReturnsTheFunctorInstance() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);

      assertThat(path.functor()).isNotNull();
      assertThat(path.functor()).isEqualTo(MaybeMonad.INSTANCE);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);

      assertThat(path.toString()).contains("FreeApPath");
    }

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsReturnsTrueForSameInstance() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);

      assertThat(path.equals(path)).isTrue();
    }

    @Test
    @DisplayName("equals() compares FreeApPaths correctly")
    void equalsComparesFreeApPaths() {
      FreeApPath<MaybeKind.Witness, Integer> path1 = FreeApPath.pure(42, MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, Integer> path2 = FreeApPath.pure(42, MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, Integer> path3 = FreeApPath.pure(99, MaybeMonad.INSTANCE);

      assertThat(path1).isEqualTo(path2);
      assertThat(path1).isNotEqualTo(path3);
      assertThat(path1).isNotEqualTo(null);
      assertThat(path1).isNotEqualTo("not a FreeApPath");
    }

    @Test
    @DisplayName("hashCode() is consistent with equals")
    void hashCodeIsConsistentWithEquals() {
      FreeApPath<MaybeKind.Witness, Integer> path1 = FreeApPath.pure(42, MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, Integer> path2 = FreeApPath.pure(42, MaybeMonad.INSTANCE);

      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }
  }

  @Nested
  @DisplayName("FreeApPath does NOT implement Chainable")
  class NotChainableTests {

    @Test
    @DisplayName("FreeApPath is not a Chainable instance")
    void freeApPathIsNotChainable() {
      FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(42, MaybeMonad.INSTANCE);

      // Verify it implements Composable and Combinable but NOT Chainable
      assertThat(path).isInstanceOf(Composable.class);
      assertThat(path).isInstanceOf(Combinable.class);
      assertThat(path).isNotInstanceOf(Chainable.class);
    }
  }

  @Nested
  @DisplayName("Validation DSL Example")
  class ValidationDSLExampleTests {

    @Test
    @DisplayName("FreeApPath can combine independent validations")
    void freeApPathCanCombineIndependentValidations() {
      // Simulate validation fields
      FreeApPath<MaybeKind.Witness, String> name =
          FreeApPath.liftF(MAYBE.just("John"), MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, Integer> age =
          FreeApPath.liftF(MAYBE.just(25), MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, String> email =
          FreeApPath.liftF(MAYBE.just("john@example.com"), MaybeMonad.INSTANCE);

      // Combine all validations (would collect all errors in a real validation Applicative)
      FreeApPath<MaybeKind.Witness, String> result =
          name.zipWith3(age, email, (n, a, e) -> n + " is " + a + " years old with email " + e);

      Kind<MaybeKind.Witness, String> interpreted =
          result.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(interpreted).get())
          .isEqualTo("John is 25 years old with email john@example.com");
    }

    @Test
    @DisplayName("FreeApPath handles failure in any validation")
    void freeApPathHandlesFailureInAnyValidation() {
      FreeApPath<MaybeKind.Witness, String> name =
          FreeApPath.liftF(MAYBE.just("John"), MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, Integer> age =
          FreeApPath.<MaybeKind.Witness, Integer>liftF(MAYBE.nothing(), MaybeMonad.INSTANCE);
      FreeApPath<MaybeKind.Witness, String> email =
          FreeApPath.liftF(MAYBE.just("john@example.com"), MaybeMonad.INSTANCE);

      FreeApPath<MaybeKind.Witness, String> result =
          name.zipWith3(age, email, (n, a, e) -> n + " is " + a + " years old with email " + e);

      Kind<MaybeKind.Witness, String> interpreted =
          result.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);

      assertThat(MAYBE.narrow(interpreted).isNothing()).isTrue();
    }
  }
}
