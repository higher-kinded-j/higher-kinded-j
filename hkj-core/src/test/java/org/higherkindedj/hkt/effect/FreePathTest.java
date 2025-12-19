// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for FreePath.
 *
 * <p>Tests cover factory methods, Composable/Combinable/Chainable operations, interpretation via
 * foldMap, and object methods.
 */
@DisplayName("FreePath<F, A> Complete Test Suite")
class FreePathTest {

  private static final Monad<MaybeKind.Witness> MAYBE_MONAD = MaybeMonad.INSTANCE;

  // Identity natural transformation for Maybe
  private static final Natural<MaybeKind.Witness, MaybeKind.Witness> IDENTITY_NAT =
      Natural.identity();

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodsTests {

    @Test
    @DisplayName("pure() creates FreePath with value")
    void pureCreatesFreePath() {
      FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(42, MaybeMonad.INSTANCE);

      GenericPath<MaybeKind.Witness, Integer> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);
      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("pure() validates non-null functor")
    void pureValidatesNonNullFunctor() {
      assertThatNullPointerException()
          .isThrownBy(() -> FreePath.pure(42, null))
          .withMessageContaining("functor must not be null");
    }

    @Test
    @DisplayName("liftF() creates FreePath from Kind")
    void liftFCreatesFreePath() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.just(42);

      FreePath<MaybeKind.Witness, Integer> path = FreePath.liftF(just, MaybeMonad.INSTANCE);

      GenericPath<MaybeKind.Witness, Integer> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);
      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("liftF() validates non-null arguments")
    void liftFValidatesNonNullArguments() {
      assertThatNullPointerException()
          .isThrownBy(() -> FreePath.liftF(null, MaybeMonad.INSTANCE))
          .withMessageContaining("fa must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> FreePath.liftF(MAYBE.just(42), null))
          .withMessageContaining("functor must not be null");
    }

    @Test
    @DisplayName("of() creates FreePath from Free")
    void ofCreatesFreePath() {
      Free<MaybeKind.Witness, Integer> free = Free.pure(42);

      FreePath<MaybeKind.Witness, Integer> path = FreePath.of(free, MaybeMonad.INSTANCE);

      GenericPath<MaybeKind.Witness, Integer> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);
      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("Path.freePure() creates FreePath with value")
    void pathFreePureCreatesFreePath() {
      FreePath<MaybeKind.Witness, Integer> path = Path.freePure(42, MaybeMonad.INSTANCE);

      GenericPath<MaybeKind.Witness, Integer> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);
      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("Path.freeLift() creates FreePath from Kind")
    void pathFreeLiftCreatesFreePath() {
      FreePath<MaybeKind.Witness, Integer> path =
          Path.freeLift(MAYBE.just(42), MaybeMonad.INSTANCE);

      GenericPath<MaybeKind.Witness, Integer> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);
      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("Path.free(Free, Functor) creates FreePath from existing Free")
    void pathFreeCreatesFromExistingFree() {
      Free<MaybeKind.Witness, Integer> free = Free.pure(42);

      FreePath<MaybeKind.Witness, Integer> path = Path.free(free, MaybeMonad.INSTANCE);

      GenericPath<MaybeKind.Witness, Integer> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);
      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("Path.free() validates non-null arguments")
    void pathFreeValidatesNonNullArguments() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.free(null, MaybeMonad.INSTANCE))
          .withMessageContaining("free must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> Path.free(Free.pure(42), null))
          .withMessageContaining("functor must not be null");
    }
  }

  @Nested
  @DisplayName("Interpretation (foldMap)")
  class InterpretationTests {

    @Test
    @DisplayName("foldMap() interprets pure value")
    void foldMapInterpretsPureValue() {
      FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(42, MaybeMonad.INSTANCE);

      GenericPath<MaybeKind.Witness, Integer> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);

      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("foldMap() interprets lifted value")
    void foldMapInterpretsLiftedValue() {
      FreePath<MaybeKind.Witness, Integer> path =
          FreePath.liftF(MAYBE.just(42), MaybeMonad.INSTANCE);

      GenericPath<MaybeKind.Witness, Integer> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);

      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("foldMap() preserves Nothing")
    void foldMapPreservesNothing() {
      FreePath<MaybeKind.Witness, String> path =
          FreePath.liftF(MAYBE.nothing(), MaybeMonad.INSTANCE);

      GenericPath<MaybeKind.Witness, String> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);

      assertThat(MAYBE.narrow(result.runKind()).isNothing()).isTrue();
    }

    @Test
    @DisplayName("foldMap() validates non-null arguments")
    void foldMapValidatesNonNullArguments() {
      FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(42, MaybeMonad.INSTANCE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.foldMap(null, MAYBE_MONAD))
          .withMessageContaining("interpreter must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.foldMap(IDENTITY_NAT, null))
          .withMessageContaining("targetMonad must not be null");
    }

    @Test
    @DisplayName("foldMapWith() interprets using NaturalTransformation")
    void foldMapWithInterpretsUsingNaturalTransformation() {
      FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(42, MaybeMonad.INSTANCE);

      NaturalTransformation<MaybeKind.Witness, MaybeKind.Witness> transform =
          new NaturalTransformation<>() {
            @Override
            public <A> Kind<MaybeKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
              return fa;
            }
          };

      GenericPath<MaybeKind.Witness, Integer> result = path.foldMapWith(transform, MAYBE_MONAD);

      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value")
    void mapTransformsValue() {
      FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(42, MaybeMonad.INSTANCE);

      FreePath<MaybeKind.Witness, String> mapped = path.map(i -> "value: " + i);

      GenericPath<MaybeKind.Witness, String> result = mapped.foldMap(IDENTITY_NAT, MAYBE_MONAD);
      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo("value: 42");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      FreePath<MaybeKind.Witness, String> path =
          FreePath.pure("hello", MaybeMonad.INSTANCE)
              .map(String::toUpperCase)
              .map(s -> s + "!")
              .map(s -> s.repeat(2));

      GenericPath<MaybeKind.Witness, String> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);
      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo("HELLO!HELLO!");
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(42, MaybeMonad.INSTANCE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      AtomicBoolean observed = new AtomicBoolean(false);

      FreePath<MaybeKind.Witness, Integer> path =
          FreePath.pure(42, MaybeMonad.INSTANCE).peek(i -> observed.set(true));

      GenericPath<MaybeKind.Witness, Integer> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);

      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo(42);
      assertThat(observed).isTrue();
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains dependent computations")
    void viaChainsComputations() {
      FreePath<MaybeKind.Witness, Integer> path =
          FreePath.pure(10, MaybeMonad.INSTANCE)
              .via(i -> FreePath.pure(i * 2, MaybeMonad.INSTANCE));

      GenericPath<MaybeKind.Witness, Integer> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);
      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo(20);
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(42, MaybeMonad.INSTANCE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() throws when mapper returns non-FreePath")
    void viaThrowsWhenMapperReturnsNonFreePath() {
      FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(42, MaybeMonad.INSTANCE);

      FreePath<MaybeKind.Witness, Integer> result = path.via(_ -> Path.just(100));

      assertThatIllegalArgumentException()
          .isThrownBy(() -> result.foldMap(IDENTITY_NAT, MAYBE_MONAD))
          .withMessageContaining("FreePath.via must return FreePath");
    }

    @Test
    @DisplayName("then() sequences computations discarding value")
    void thenSequencesComputationsDiscardingValue() {
      AtomicBoolean firstExecuted = new AtomicBoolean(false);

      FreePath<MaybeKind.Witness, String> path =
          FreePath.pure(42, MaybeMonad.INSTANCE)
              .peek(_ -> firstExecuted.set(true))
              .then(() -> FreePath.pure("result", MaybeMonad.INSTANCE));

      GenericPath<MaybeKind.Witness, String> result = path.foldMap(IDENTITY_NAT, MAYBE_MONAD);

      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo("result");
      assertThat(firstExecuted).isTrue();
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines two FreePaths")
    void zipWithCombinesTwoPaths() {
      FreePath<MaybeKind.Witness, String> first = FreePath.pure("hello", MaybeMonad.INSTANCE);
      FreePath<MaybeKind.Witness, Integer> second = FreePath.pure(3, MaybeMonad.INSTANCE);

      FreePath<MaybeKind.Witness, String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      GenericPath<MaybeKind.Witness, String> interpreted =
          result.foldMap(IDENTITY_NAT, MAYBE_MONAD);
      assertThat(MAYBE.narrow(interpreted.runKind()).get()).isEqualTo("hellohellohello");
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(42, MaybeMonad.INSTANCE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + (Integer) b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(FreePath.pure(1, MaybeMonad.INSTANCE), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith() throws when given non-FreePath")
    void zipWithThrowsWhenGivenNonFreePath() {
      FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(42, MaybeMonad.INSTANCE);
      MaybePath<Integer> maybePath = Path.just(10);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(maybePath, (a, b) -> a + b))
          .withMessageContaining("Cannot zipWith non-FreePath");
    }
  }

  @Nested
  @DisplayName("Utility Methods")
  class UtilityMethodsTests {

    @Test
    @DisplayName("toFree() returns underlying Free")
    void toFreeReturnsUnderlyingFree() {
      FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(42, MaybeMonad.INSTANCE);

      Free<MaybeKind.Witness, Integer> free = path.toFree();

      assertThat(free).isNotNull();
      Kind<MaybeKind.Witness, Integer> result = free.foldMap(IDENTITY_NAT, MAYBE_MONAD);
      assertThat(MAYBE.narrow(result).get()).isEqualTo(42);
    }

    @Test
    @DisplayName("functor() returns the functor instance")
    void functorReturnsTheFunctorInstance() {
      FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(42, MaybeMonad.INSTANCE);

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
      FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(42, MaybeMonad.INSTANCE);

      assertThat(path.toString()).contains("FreePath");
    }

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsReturnsTrueForSameInstance() {
      FreePath<MaybeKind.Witness, Integer> path = FreePath.pure(42, MaybeMonad.INSTANCE);

      assertThat(path.equals(path)).isTrue();
    }

    @Test
    @DisplayName("equals() compares FreePaths correctly")
    void equalsComparesFreePaths() {
      FreePath<MaybeKind.Witness, Integer> path1 = FreePath.pure(42, MaybeMonad.INSTANCE);
      FreePath<MaybeKind.Witness, Integer> path2 = FreePath.pure(42, MaybeMonad.INSTANCE);
      FreePath<MaybeKind.Witness, Integer> path3 = FreePath.pure(99, MaybeMonad.INSTANCE);

      assertThat(path1).isEqualTo(path2);
      assertThat(path1).isNotEqualTo(path3);
      assertThat(path1).isNotEqualTo(null);
      assertThat(path1).isNotEqualTo("not a FreePath");
    }

    @Test
    @DisplayName("hashCode() is consistent with equals")
    void hashCodeIsConsistentWithEquals() {
      FreePath<MaybeKind.Witness, Integer> path1 = FreePath.pure(42, MaybeMonad.INSTANCE);
      FreePath<MaybeKind.Witness, Integer> path2 = FreePath.pure(42, MaybeMonad.INSTANCE);

      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }
  }

  @Nested
  @DisplayName("Complex DSL Example")
  class DSLExampleTests {

    @Test
    @DisplayName("FreePath can build and interpret a simple DSL")
    void freePathCanBuildAndInterpretSimpleDSL() {
      // Build a program that gets a value, doubles it, and formats it
      FreePath<MaybeKind.Witness, String> program =
          FreePath.liftF(MAYBE.just(5), MaybeMonad.INSTANCE)
              .map(x -> x * 2)
              .map(x -> "Result: " + x);

      // Interpret the program
      GenericPath<MaybeKind.Witness, String> result = program.foldMap(IDENTITY_NAT, MAYBE_MONAD);

      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo("Result: 10");
    }

    @Test
    @DisplayName("FreePath handles Nothing in DSL chain")
    void freePathHandlesNothingInDSLChain() {
      FreePath<MaybeKind.Witness, String> program =
          FreePath.<MaybeKind.Witness, Integer>liftF(MAYBE.nothing(), MaybeMonad.INSTANCE)
              .map(x -> x * 2)
              .map(x -> "Result: " + x);

      GenericPath<MaybeKind.Witness, String> result = program.foldMap(IDENTITY_NAT, MAYBE_MONAD);

      assertThat(MAYBE.narrow(result.runKind()).isNothing()).isTrue();
    }

    @Test
    @DisplayName("FreePath supports via for sequential computation")
    void freePathSupportsViaForSequentialComputation() {
      Function<Integer, FreePath<MaybeKind.Witness, Integer>> doubleIt =
          n -> FreePath.pure(n * 2, MaybeMonad.INSTANCE);

      FreePath<MaybeKind.Witness, Integer> program =
          FreePath.pure(5, MaybeMonad.INSTANCE)
              .via(doubleIt)
              .via(doubleIt)
              .via(doubleIt); // 5 * 2 * 2 * 2 = 40

      GenericPath<MaybeKind.Witness, Integer> result = program.foldMap(IDENTITY_NAT, MAYBE_MONAD);

      assertThat(MAYBE.narrow(result.runKind()).get()).isEqualTo(40);
    }
  }
}
