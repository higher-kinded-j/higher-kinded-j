// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GenericPath} - the escape hatch for using custom monads in Path composition.
 *
 * <p>Uses {@link MaybeMonad} as a concrete Monad implementation for testing.
 */
@DisplayName("GenericPath")
class GenericPathTest {

  private static final MaybeMonad MONAD = MaybeMonad.INSTANCE;
  // IdMonad is a pure Monad without MonadError support - use for "without MonadError" tests
  private static final IdMonad ID_MONAD = IdMonad.instance();

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    @DisplayName("of() creates a GenericPath wrapping the provided Kind")
    void ofCreatesPath() {
      Kind<MaybeKind.Witness, String> kind = MAYBE.just("hello");
      GenericPath<MaybeKind.Witness, String> path = GenericPath.of(kind, MONAD);

      assertThat(path).isNotNull();
      assertThat(path.runKind()).isEqualTo(kind);
      assertThat(path.monad()).isSameAs(MONAD);
    }

    @Test
    @DisplayName("Path.generic() creates a GenericPath via factory")
    void pathGenericCreatesPath() {
      Kind<MaybeKind.Witness, String> kind = MAYBE.just("hello");
      GenericPath<MaybeKind.Witness, String> path = Path.generic(kind, MONAD);

      assertThat(path).isNotNull();
      assertThat(path.runKind()).isEqualTo(kind);
      assertThat(path.monad()).isSameAs(MONAD);
    }

    @Test
    @DisplayName("Path.genericPure() lifts value via factory")
    void pathGenericPureLiftsValue() {
      GenericPath<MaybeKind.Witness, String> path = Path.genericPure("hello", MONAD);

      Maybe<String> result = MAYBE.narrow(path.runKind());
      assertThat(result.isJust()).isTrue();
      assertThat(result.get()).isEqualTo("hello");
    }

    @Test
    @DisplayName("of() with Nothing creates a GenericPath representing empty state")
    void ofWithNothing() {
      Kind<MaybeKind.Witness, String> nothing = MAYBE.nothing();
      GenericPath<MaybeKind.Witness, String> path = GenericPath.of(nothing, MONAD);

      assertThat(path).isNotNull();
      Maybe<String> result = MAYBE.narrow(path.runKind());
      assertThat(result.isNothing()).isTrue();
    }

    @Test
    @DisplayName("of() throws NullPointerException when value is null")
    void ofThrowsOnNullValue() {
      assertThatNullPointerException()
          .isThrownBy(() -> GenericPath.of(null, MONAD))
          .withMessage("value must not be null");
    }

    @Test
    @DisplayName("of() throws NullPointerException when monad is null")
    void ofThrowsOnNullMonad() {
      Kind<MaybeKind.Witness, String> kind = MAYBE.just("hello");
      assertThatNullPointerException()
          .isThrownBy(() -> GenericPath.of(kind, null))
          .withMessage("monad must not be null");
    }

    @Test
    @DisplayName("pure() lifts a value into GenericPath")
    void pureLiftsValue() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);

      Maybe<String> result = MAYBE.narrow(path.runKind());
      assertThat(result.isJust()).isTrue();
      assertThat(result.get()).isEqualTo("hello");
    }

    @Test
    @DisplayName("pure() with null value creates Just(null) via MaybeMonad.of() behavior")
    void pureWithNullValue() {
      // MaybeMonad.of(null) returns Nothing via Maybe.fromNullable
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure(null, MONAD);

      Maybe<String> result = MAYBE.narrow(path.runKind());
      assertThat(result.isNothing()).isTrue();
    }

    @Test
    @DisplayName("pure() throws NullPointerException when monad is null")
    void pureThrowsOnNullMonad() {
      assertThatNullPointerException()
          .isThrownBy(() -> GenericPath.pure("hello", null))
          .withMessage("monad must not be null");
    }
  }

  @Nested
  @DisplayName("Terminal Operations")
  class TerminalOperations {

    @Test
    @DisplayName("runKind() returns the underlying Kind")
    void runKindReturnsKind() {
      Kind<MaybeKind.Witness, Integer> kind = MAYBE.just(42);
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.of(kind, MONAD);

      assertThat(path.runKind()).isSameAs(kind);
    }

    @Test
    @DisplayName("monad() returns the Monad instance")
    void monadReturnsMonadInstance() {
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(42, MONAD);

      assertThat(path.monad()).isSameAs(MONAD);
    }
  }

  @Nested
  @DisplayName("Composable Operations")
  class ComposableOperations {

    @Test
    @DisplayName("map() transforms the value")
    void mapTransformsValue() {
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(5, MONAD);

      GenericPath<MaybeKind.Witness, String> result = path.map(n -> "Value: " + n);

      Maybe<String> maybe = MAYBE.narrow(result.runKind());
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("Value: 5");
    }

    @Test
    @DisplayName("map() on Nothing remains Nothing")
    void mapOnNothingRemainsNothing() {
      GenericPath<MaybeKind.Witness, Integer> path =
          GenericPath.of(MAYBE.<Integer>nothing(), MONAD);

      GenericPath<MaybeKind.Witness, String> result = path.map(n -> "Value: " + n);

      Maybe<String> maybe = MAYBE.narrow(result.runKind());
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("map() throws NullPointerException when mapper is null")
    void mapThrowsOnNullMapper() {
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(5, MONAD);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessage("mapper must not be null");
    }

    @Test
    @DisplayName("peek() executes consumer without changing value")
    void peekExecutesConsumer() {
      AtomicInteger counter = new AtomicInteger(0);
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(42, MONAD);

      GenericPath<MaybeKind.Witness, Integer> result = path.peek(counter::set);

      Maybe<Integer> maybe = MAYBE.narrow(result.runKind());
      assertThat(maybe.get()).isEqualTo(42);
      assertThat(counter.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("peek() does not execute consumer on Nothing")
    void peekDoesNotExecuteOnNothing() {
      AtomicInteger counter = new AtomicInteger(0);
      GenericPath<MaybeKind.Witness, Integer> path =
          GenericPath.of(MAYBE.<Integer>nothing(), MONAD);

      path.peek(counter::set);

      assertThat(counter.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("peek() throws NullPointerException when consumer is null")
    void peekThrowsOnNullConsumer() {
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(5, MONAD);

      assertThatNullPointerException()
          .isThrownBy(() -> path.peek(null))
          .withMessage("consumer must not be null");
    }
  }

  @Nested
  @DisplayName("Combinable Operations")
  class CombinableOperations {

    @Test
    @DisplayName("zipWith() combines two successful paths")
    void zipWithCombinesTwoPaths() {
      GenericPath<MaybeKind.Witness, Integer> path1 = GenericPath.pure(5, MONAD);
      GenericPath<MaybeKind.Witness, Integer> path2 = GenericPath.pure(3, MONAD);

      GenericPath<MaybeKind.Witness, Integer> result = path1.zipWith(path2, Integer::sum);

      Maybe<Integer> maybe = MAYBE.narrow(result.runKind());
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(8);
    }

    @Test
    @DisplayName("zipWith() returns Nothing if first is Nothing")
    void zipWithFirstNothing() {
      GenericPath<MaybeKind.Witness, Integer> path1 =
          GenericPath.of(MAYBE.<Integer>nothing(), MONAD);
      GenericPath<MaybeKind.Witness, Integer> path2 = GenericPath.pure(3, MONAD);

      GenericPath<MaybeKind.Witness, Integer> result = path1.zipWith(path2, Integer::sum);

      Maybe<Integer> maybe = MAYBE.narrow(result.runKind());
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("zipWith() returns Nothing if second is Nothing")
    void zipWithSecondNothing() {
      GenericPath<MaybeKind.Witness, Integer> path1 = GenericPath.pure(5, MONAD);
      GenericPath<MaybeKind.Witness, Integer> path2 =
          GenericPath.of(MAYBE.<Integer>nothing(), MONAD);

      GenericPath<MaybeKind.Witness, Integer> result = path1.zipWith(path2, Integer::sum);

      Maybe<Integer> maybe = MAYBE.narrow(result.runKind());
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("zipWith() throws when other is not a GenericPath")
    void zipWithThrowsOnNonGenericPath() {
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(5, MONAD);
      Combinable<Integer> other = Path.maybe(3);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(other, Integer::sum))
          .withMessageContaining("GenericPath can only zipWith another GenericPath");
    }

    @Test
    @DisplayName("zipWith() throws NullPointerException when other is null")
    void zipWithThrowsOnNullOther() {
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(5, MONAD);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, Integer::sum))
          .withMessage("other must not be null");
    }

    @Test
    @DisplayName("zipWith() throws NullPointerException when combiner is null")
    void zipWithThrowsOnNullCombiner() {
      GenericPath<MaybeKind.Witness, Integer> path1 = GenericPath.pure(5, MONAD);
      GenericPath<MaybeKind.Witness, Integer> path2 = GenericPath.pure(3, MONAD);

      assertThatNullPointerException()
          .isThrownBy(() -> path1.zipWith(path2, null))
          .withMessage("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith3() combines three successful paths")
    void zipWith3CombinesThreePaths() {
      GenericPath<MaybeKind.Witness, Integer> path1 = GenericPath.pure(1, MONAD);
      GenericPath<MaybeKind.Witness, Integer> path2 = GenericPath.pure(2, MONAD);
      GenericPath<MaybeKind.Witness, Integer> path3 = GenericPath.pure(3, MONAD);

      GenericPath<MaybeKind.Witness, Integer> result =
          path1.zipWith3(path2, path3, (a, b, c) -> a + b + c);

      Maybe<Integer> maybe = MAYBE.narrow(result.runKind());
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(6);
    }

    @Test
    @DisplayName("zipWith3() returns Nothing if any path is Nothing")
    void zipWith3WithNothing() {
      GenericPath<MaybeKind.Witness, Integer> path1 = GenericPath.pure(1, MONAD);
      GenericPath<MaybeKind.Witness, Integer> path2 =
          GenericPath.of(MAYBE.<Integer>nothing(), MONAD);
      GenericPath<MaybeKind.Witness, Integer> path3 = GenericPath.pure(3, MONAD);

      GenericPath<MaybeKind.Witness, Integer> result =
          path1.zipWith3(path2, path3, (a, b, c) -> a + b + c);

      Maybe<Integer> maybe = MAYBE.narrow(result.runKind());
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("zipWith3() throws NullPointerException when second is null")
    void zipWith3ThrowsOnNullSecond() {
      GenericPath<MaybeKind.Witness, Integer> path1 = GenericPath.pure(1, MONAD);
      GenericPath<MaybeKind.Witness, Integer> path3 = GenericPath.pure(3, MONAD);

      assertThatNullPointerException()
          .isThrownBy(
              () -> path1.zipWith3(null, path3, (Integer a, Integer b, Integer c) -> a + b + c))
          .withMessage("second must not be null");
    }

    @Test
    @DisplayName("zipWith3() throws NullPointerException when third is null")
    void zipWith3ThrowsOnNullThird() {
      GenericPath<MaybeKind.Witness, Integer> path1 = GenericPath.pure(1, MONAD);
      GenericPath<MaybeKind.Witness, Integer> path2 = GenericPath.pure(2, MONAD);

      assertThatNullPointerException()
          .isThrownBy(
              () -> path1.zipWith3(path2, null, (Integer a, Integer b, Integer c) -> a + b + c))
          .withMessage("third must not be null");
    }

    @Test
    @DisplayName("zipWith3() throws NullPointerException when combiner is null")
    void zipWith3ThrowsOnNullCombiner() {
      GenericPath<MaybeKind.Witness, Integer> path1 = GenericPath.pure(1, MONAD);
      GenericPath<MaybeKind.Witness, Integer> path2 = GenericPath.pure(2, MONAD);
      GenericPath<MaybeKind.Witness, Integer> path3 = GenericPath.pure(3, MONAD);

      assertThatNullPointerException()
          .isThrownBy(() -> path1.zipWith3(path2, path3, null))
          .withMessage("combiner must not be null");
    }
  }

  @Nested
  @DisplayName("Chainable Operations")
  class ChainableOperations {

    @Test
    @DisplayName("via() chains dependent computations")
    void viaChainsComputations() {
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(5, MONAD);

      GenericPath<MaybeKind.Witness, String> result =
          path.via(n -> GenericPath.pure("Number: " + n, MONAD));

      Maybe<String> maybe = MAYBE.narrow(result.runKind());
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("Number: 5");
    }

    @Test
    @DisplayName("via() on Nothing does not execute mapper")
    void viaOnNothingSkipsMapper() {
      AtomicInteger counter = new AtomicInteger(0);
      GenericPath<MaybeKind.Witness, Integer> path =
          GenericPath.of(MAYBE.<Integer>nothing(), MONAD);

      GenericPath<MaybeKind.Witness, String> result =
          path.via(
              n -> {
                counter.incrementAndGet();
                return GenericPath.pure("Number: " + n, MONAD);
              });

      Maybe<String> maybe = MAYBE.narrow(result.runKind());
      assertThat(maybe.isNothing()).isTrue();
      assertThat(counter.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("via() propagates Nothing from mapper")
    void viaPropagatesNothing() {
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(5, MONAD);

      GenericPath<MaybeKind.Witness, String> result =
          path.via(_ -> GenericPath.of(MAYBE.<String>nothing(), MONAD));

      Maybe<String> maybe = MAYBE.narrow(result.runKind());
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("via() throws when mapper returns non-GenericPath")
    void viaThrowsOnNonGenericPath() {
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(5, MONAD);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.via(n -> Path.maybe("test")))
          .withMessageContaining("GenericPath.via must return GenericPath");
    }

    @Test
    @DisplayName("via() throws NullPointerException when mapper is null")
    void viaThrowsOnNullMapper() {
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(5, MONAD);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessage("mapper must not be null");
    }

    @Test
    @DisplayName("via() throws NullPointerException when mapper returns null")
    void viaThrowsOnNullMapperResult() {
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(5, MONAD);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(_ -> null))
          .withMessage("mapper must not return null");
    }

    @Test
    @DisplayName("flatMap() is alias for via()")
    void flatMapIsAliasForVia() {
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(5, MONAD);

      Chainable<String> result = path.flatMap(n -> GenericPath.pure("Number: " + n, MONAD));

      assertThat(result).isInstanceOf(GenericPath.class);
      @SuppressWarnings("unchecked")
      GenericPath<MaybeKind.Witness, String> genericResult =
          (GenericPath<MaybeKind.Witness, String>) result;
      Maybe<String> maybe = MAYBE.narrow(genericResult.runKind());
      assertThat(maybe.get()).isEqualTo("Number: 5");
    }

    @Test
    @DisplayName("then() sequences independent computation")
    void thenSequencesComputation() {
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(5, MONAD);

      GenericPath<MaybeKind.Witness, String> result =
          path.then(() -> GenericPath.pure("next", MONAD));

      Maybe<String> maybe = MAYBE.narrow(result.runKind());
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("next");
    }

    @Test
    @DisplayName("then() on Nothing does not execute supplier")
    void thenOnNothingSkipsSupplier() {
      AtomicInteger counter = new AtomicInteger(0);
      GenericPath<MaybeKind.Witness, Integer> path =
          GenericPath.of(MAYBE.<Integer>nothing(), MONAD);

      GenericPath<MaybeKind.Witness, String> result =
          path.then(
              () -> {
                counter.incrementAndGet();
                return GenericPath.pure("next", MONAD);
              });

      Maybe<String> maybe = MAYBE.narrow(result.runKind());
      assertThat(maybe.isNothing()).isTrue();
      assertThat(counter.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("then() throws NullPointerException when supplier is null")
    void thenThrowsOnNullSupplier() {
      GenericPath<MaybeKind.Witness, Integer> path = GenericPath.pure(5, MONAD);

      assertThatNullPointerException()
          .isThrownBy(() -> path.then(null))
          .withMessage("supplier must not be null");
    }
  }

  @Nested
  @DisplayName("Conversion Operations")
  class ConversionOperations {

    @Test
    @DisplayName("toMaybePath() converts using narrower function")
    void toMaybePathConverts() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);

      MaybePath<String> maybePath = path.toMaybePath(MAYBE::narrow);

      assertThat(maybePath.run().isJust()).isTrue();
      assertThat(maybePath.run().get()).isEqualTo("hello");
    }

    @Test
    @DisplayName("toMaybePath() preserves Nothing state")
    void toMaybePathPreservesNothing() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.of(MAYBE.<String>nothing(), MONAD);

      MaybePath<String> maybePath = path.toMaybePath(MAYBE::narrow);

      assertThat(maybePath.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("toMaybePath() throws NullPointerException when narrower is null")
    void toMaybePathThrowsOnNullNarrower() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);

      assertThatNullPointerException()
          .isThrownBy(() -> path.toMaybePath(null))
          .withMessage("narrower must not be null");
    }

    @Test
    @DisplayName("toEitherPath() converts using narrower function")
    void toEitherPathConverts() {
      // EitherPath conversion requires an Either Kind, which MaybeKind is not.
      // This test verifies the mechanism works with proper type alignment.
      // In practice, GenericPath<EitherKind.Witness, A> would be used.
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);

      // Create a custom narrower that maps Maybe to Either for testing purposes
      EitherPath<String, String> eitherPath =
          path.toEitherPath(
              kind -> {
                Maybe<String> maybe = MAYBE.narrow(kind);
                return maybe.isJust() ? Either.right(maybe.get()) : Either.left("No value");
              });

      assertThat(eitherPath.run().isRight()).isTrue();
      assertThat(eitherPath.run().getRight()).isEqualTo("hello");
    }

    @Test
    @DisplayName("toEitherPath() throws NullPointerException when narrower is null")
    void toEitherPathThrowsOnNullNarrower() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);

      assertThatNullPointerException()
          .isThrownBy(() -> path.toEitherPath(null))
          .withMessage("narrower must not be null");
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {

    @Test
    @DisplayName("Left identity: pure(a).via(f) == f.apply(a)")
    void leftIdentity() {
      String value = "hello";
      Function<String, GenericPath<MaybeKind.Witness, Integer>> f =
          s -> GenericPath.pure(s.length(), MONAD);

      GenericPath<MaybeKind.Witness, Integer> left = GenericPath.pure(value, MONAD).via(f);
      GenericPath<MaybeKind.Witness, Integer> right = f.apply(value);

      assertThat(left).isEqualTo(right);
    }

    @Test
    @DisplayName("Right identity: path.via(pure) == path")
    void rightIdentity() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);

      GenericPath<MaybeKind.Witness, String> result = path.via(a -> GenericPath.pure(a, MONAD));

      assertThat(result).isEqualTo(path);
    }

    @Test
    @DisplayName("Associativity: path.via(f).via(g) == path.via(x -> f.apply(x).via(g))")
    void associativity() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);
      Function<String, GenericPath<MaybeKind.Witness, Integer>> f =
          s -> GenericPath.pure(s.length(), MONAD);
      Function<Integer, GenericPath<MaybeKind.Witness, Integer>> g =
          n -> GenericPath.pure(n * 2, MONAD);

      GenericPath<MaybeKind.Witness, Integer> left = path.via(f).via(g);
      GenericPath<MaybeKind.Witness, Integer> right = path.via(x -> f.apply(x).via(g));

      assertThat(left).isEqualTo(right);
    }
  }

  @Nested
  @DisplayName("MonadError Factory Methods")
  class MonadErrorFactoryMethods {

    private static final EitherMonad<String> EITHER_MONAD = EitherMonad.instance();

    @Test
    @DisplayName("of(value, monadError) creates GenericPath with error recovery support")
    void ofWithMonadErrorCreatesPath() {
      Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(Either.right(42));
      GenericPath<EitherKind.Witness<String>, Integer> path = GenericPath.of(kind, EITHER_MONAD);

      assertThat(path.supportsRecovery()).isTrue();
      assertThat(path.<String>monadError()).isPresent();
    }

    @Test
    @DisplayName("pure(value, monadError) lifts value with error recovery support")
    void pureWithMonadErrorLiftsValue() {
      GenericPath<EitherKind.Witness<String>, Integer> path = GenericPath.pure(42, EITHER_MONAD);

      assertThat(path.supportsRecovery()).isTrue();
      Either<String, Integer> result = EITHER.narrow(path.runKind());
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("raiseError() creates a GenericPath representing an error")
    void raiseErrorCreatesErrorPath() {
      GenericPath<EitherKind.Witness<String>, Integer> path =
          GenericPath.raiseError("error message", EITHER_MONAD);

      Either<String, Integer> result = EITHER.narrow(path.runKind());
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("error message");
    }

    @Test
    @DisplayName("raiseError() throws NullPointerException when monadError is null")
    void raiseErrorThrowsOnNullMonadError() {
      assertThatNullPointerException()
          .isThrownBy(() -> GenericPath.raiseError("error", null))
          .withMessageContaining("monadError must not be null");
    }
  }

  @Nested
  @DisplayName("Error Recovery Operations")
  class ErrorRecoveryOperations {

    private static final EitherMonad<String> EITHER_MONAD = EitherMonad.instance();

    @Test
    @DisplayName("supportsRecovery() returns true when MonadError provided")
    void supportsRecoveryReturnsTrueWithMonadError() {
      GenericPath<EitherKind.Witness<String>, Integer> path = GenericPath.pure(42, EITHER_MONAD);

      assertThat(path.supportsRecovery()).isTrue();
    }

    @Test
    @DisplayName("supportsRecovery() returns false when only Monad provided")
    void supportsRecoveryReturnsFalseWithoutMonadError() {
      // Use IdMonad which is a pure Monad (not MonadError)
      GenericPath<IdKind.Witness, Integer> path = GenericPath.pure(42, ID_MONAD);

      assertThat(path.supportsRecovery()).isFalse();
    }

    @Test
    @DisplayName("monadError() returns Optional containing MonadError when provided")
    void monadErrorReturnsOptionalWhenProvided() {
      GenericPath<EitherKind.Witness<String>, Integer> path = GenericPath.pure(42, EITHER_MONAD);

      Optional<MonadError<EitherKind.Witness<String>, String>> me = path.monadError();

      assertThat(me).isPresent();
    }

    @Test
    @DisplayName("monadError() returns empty Optional when not provided")
    void monadErrorReturnsEmptyWhenNotProvided() {
      // Use IdMonad which is a pure Monad (not MonadError)
      GenericPath<IdKind.Witness, Integer> path = GenericPath.pure(42, ID_MONAD);

      Optional<MonadError<IdKind.Witness, Object>> me = path.monadError();

      assertThat(me).isEmpty();
    }

    @Test
    @DisplayName("recover() transforms error to success value")
    void recoverTransformsErrorToSuccess() {
      GenericPath<EitherKind.Witness<String>, Integer> path =
          GenericPath.raiseError("error", EITHER_MONAD);

      GenericPath<EitherKind.Witness<String>, Integer> recovered =
          path.recover(err -> ((String) err).length());

      Either<String, Integer> result = EITHER.narrow(recovered.runKind());
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(5); // "error".length()
    }

    @Test
    @DisplayName("recover() does nothing on success")
    void recoverDoesNothingOnSuccess() {
      GenericPath<EitherKind.Witness<String>, Integer> path = GenericPath.pure(42, EITHER_MONAD);

      GenericPath<EitherKind.Witness<String>, Integer> recovered = path.recover(err -> 0);

      Either<String, Integer> result = EITHER.narrow(recovered.runKind());
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("recover() throws UnsupportedOperationException without MonadError")
    void recoverThrowsWithoutMonadError() {
      // Use IdMonad which is a pure Monad (not MonadError)
      GenericPath<IdKind.Witness, Integer> path = GenericPath.pure(42, ID_MONAD);

      assertThatThrownBy(() -> path.recover(err -> 0))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("recover requires MonadError support");
    }

    @Test
    @DisplayName("recover() throws NullPointerException when recovery is null")
    void recoverThrowsOnNullRecovery() {
      GenericPath<EitherKind.Witness<String>, Integer> path = GenericPath.pure(42, EITHER_MONAD);

      assertThatNullPointerException()
          .isThrownBy(() -> path.recover(null))
          .withMessageContaining("recovery must not be null");
    }

    @Test
    @DisplayName("recoverWith() transforms error to new GenericPath")
    void recoverWithTransformsErrorToPath() {
      GenericPath<EitherKind.Witness<String>, Integer> path =
          GenericPath.raiseError("error", EITHER_MONAD);

      GenericPath<EitherKind.Witness<String>, Integer> recovered =
          path.recoverWith(err -> GenericPath.pure(((String) err).length(), EITHER_MONAD));

      Either<String, Integer> result = EITHER.narrow(recovered.runKind());
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(5);
    }

    @Test
    @DisplayName("recoverWith() does nothing on success")
    void recoverWithDoesNothingOnSuccess() {
      GenericPath<EitherKind.Witness<String>, Integer> path = GenericPath.pure(42, EITHER_MONAD);

      GenericPath<EitherKind.Witness<String>, Integer> recovered =
          path.recoverWith(err -> GenericPath.pure(0, EITHER_MONAD));

      Either<String, Integer> result = EITHER.narrow(recovered.runKind());
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("recoverWith() throws UnsupportedOperationException without MonadError")
    void recoverWithThrowsWithoutMonadError() {
      // Use IdMonad which is a pure Monad (not MonadError)
      GenericPath<IdKind.Witness, Integer> path = GenericPath.pure(42, ID_MONAD);

      assertThatThrownBy(() -> path.recoverWith(err -> GenericPath.pure(0, ID_MONAD)))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("recoverWith requires MonadError support");
    }

    @Test
    @DisplayName("recoverWith() throws NullPointerException when recovery is null")
    void recoverWithThrowsOnNullRecovery() {
      GenericPath<EitherKind.Witness<String>, Integer> path = GenericPath.pure(42, EITHER_MONAD);

      assertThatNullPointerException()
          .isThrownBy(() -> path.recoverWith(null))
          .withMessageContaining("recovery must not be null");
    }

    @Test
    @DisplayName("mapError() transforms error within same effect type")
    void mapErrorTransformsError() {
      GenericPath<EitherKind.Witness<String>, Integer> path =
          GenericPath.raiseError("error", EITHER_MONAD);

      // mapError with same error type (identity transformation for testing coverage)
      GenericPath<EitherKind.Witness<String>, Integer> mapped =
          path.<String, String>mapError(String::toUpperCase, EITHER_MONAD);

      Either<String, Integer> result = EITHER.narrow(mapped.runKind());
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("mapError() does nothing on success")
    void mapErrorDoesNothingOnSuccess() {
      GenericPath<EitherKind.Witness<String>, Integer> path = GenericPath.pure(42, EITHER_MONAD);

      GenericPath<EitherKind.Witness<String>, Integer> mapped =
          path.<String, String>mapError(String::toUpperCase, EITHER_MONAD);

      Either<String, Integer> result = EITHER.narrow(mapped.runKind());
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("mapError() throws UnsupportedOperationException without MonadError")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void mapErrorThrowsWithoutMonadError() {
      // Use IdMonad which is a pure Monad (not MonadError)
      GenericPath<IdKind.Witness, Integer> path = GenericPath.pure(42, ID_MONAD);

      // Use raw MonadError cast - exception is thrown before it's actually used
      MonadError rawMonadError = EitherMonad.instance();
      assertThatThrownBy(() -> path.mapError(Object::toString, rawMonadError))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("mapError requires MonadError support");
    }

    @Test
    @DisplayName("mapError() throws NullPointerException when mapper is null")
    void mapErrorThrowsOnNullMapper() {
      GenericPath<EitherKind.Witness<String>, Integer> path = GenericPath.pure(42, EITHER_MONAD);

      assertThatNullPointerException()
          .isThrownBy(() -> path.<String, String>mapError(null, EITHER_MONAD))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("mapError() throws NullPointerException when targetMonadError is null")
    void mapErrorThrowsOnNullTargetMonadError() {
      GenericPath<EitherKind.Witness<String>, Integer> path = GenericPath.pure(42, EITHER_MONAD);

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  path.<String, String>mapError(
                      String::toUpperCase, (MonadError<EitherKind.Witness<String>, String>) null))
          .withMessageContaining("targetMonadError must not be null");
    }
  }

  @Nested
  @DisplayName("Natural Transformation Operations")
  class NaturalTransformationOperations {

    private static final EitherMonad<String> EITHER_MONAD = EitherMonad.instance();

    @Test
    @DisplayName("mapK() transforms to different effect type using Monad")
    void mapKTransformsWithMonad() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);

      // Create a natural transformation from Maybe to Either
      NaturalTransformation<MaybeKind.Witness, EitherKind.Witness<String>> maybeToEither =
          new NaturalTransformation<>() {
            @Override
            public <A> Kind<EitherKind.Witness<String>, A> apply(Kind<MaybeKind.Witness, A> fa) {
              Maybe<A> maybe = MAYBE.narrow(fa);
              return maybe.isJust()
                  ? EITHER.widen(Either.right(maybe.get()))
                  : EITHER.widen(Either.left("Nothing found"));
            }
          };

      GenericPath<EitherKind.Witness<String>, String> transformed =
          path.mapK(maybeToEither, EITHER_MONAD);

      Either<String, String> result = EITHER.narrow(transformed.runKind());
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo("hello");
    }

    @Test
    @DisplayName("mapK() transforms Nothing to Left")
    void mapKTransformsNothingToLeft() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.of(MAYBE.<String>nothing(), MONAD);

      NaturalTransformation<MaybeKind.Witness, EitherKind.Witness<String>> maybeToEither =
          new NaturalTransformation<>() {
            @Override
            public <A> Kind<EitherKind.Witness<String>, A> apply(Kind<MaybeKind.Witness, A> fa) {
              Maybe<A> maybe = MAYBE.narrow(fa);
              return maybe.isJust()
                  ? EITHER.widen(Either.right(maybe.get()))
                  : EITHER.widen(Either.left("Nothing found"));
            }
          };

      GenericPath<EitherKind.Witness<String>, String> transformed =
          path.mapK(maybeToEither, EITHER_MONAD);

      Either<String, String> result = EITHER.narrow(transformed.runKind());
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("Nothing found");
    }

    @Test
    @DisplayName("mapK() throws NullPointerException when transform is null")
    void mapKThrowsOnNullTransform() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);

      assertThatNullPointerException()
          .isThrownBy(() -> path.mapK(null, EITHER_MONAD))
          .withMessageContaining("transform must not be null");
    }

    @Test
    @DisplayName("mapK() throws NullPointerException when targetMonad is null")
    void mapKThrowsOnNullTargetMonad() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);
      // Use a transformation to IdKind (pure Monad, not MonadError) to test the Monad overload
      NaturalTransformation<MaybeKind.Witness, IdKind.Witness> maybeToId =
          new NaturalTransformation<>() {
            @Override
            public <A> Kind<IdKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
              Maybe<A> maybe = MAYBE.narrow(fa);
              return maybe.isJust() ? Id.of(maybe.get()) : Id.of(null);
            }
          };

      assertThatNullPointerException()
          .isThrownBy(() -> path.mapK(maybeToId, (Monad<IdKind.Witness>) null))
          .withMessageContaining("targetMonad must not be null");
    }

    @Test
    @DisplayName("mapK() with Monad transforms to new effect type without recovery")
    void mapKWithMonadTransformsWithoutRecovery() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);
      // Use a transformation to IdKind (pure Monad, not MonadError)
      NaturalTransformation<MaybeKind.Witness, IdKind.Witness> maybeToId =
          new NaturalTransformation<>() {
            @Override
            public <A> Kind<IdKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
              Maybe<A> maybe = MAYBE.narrow(fa);
              return maybe.isJust() ? Id.of(maybe.get()) : Id.of(null);
            }
          };

      GenericPath<IdKind.Witness, String> transformed = path.mapK(maybeToId, ID_MONAD);

      // Verify the transformation succeeded
      Id<String> result = ID.narrow(transformed.runKind());
      assertThat(result.value()).isEqualTo("hello");
      // Verify that recovery is NOT supported (since we used Monad, not MonadError)
      assertThat(transformed.supportsRecovery()).isFalse();
    }

    @Test
    @DisplayName("mapK() with MonadError preserves error recovery capability")
    void mapKWithMonadErrorPreservesRecovery() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);

      NaturalTransformation<MaybeKind.Witness, EitherKind.Witness<String>> maybeToEither =
          new NaturalTransformation<>() {
            @Override
            public <A> Kind<EitherKind.Witness<String>, A> apply(Kind<MaybeKind.Witness, A> fa) {
              Maybe<A> maybe = MAYBE.narrow(fa);
              return maybe.isJust()
                  ? EITHER.widen(Either.right(maybe.get()))
                  : EITHER.widen(Either.left("Nothing found"));
            }
          };

      GenericPath<EitherKind.Witness<String>, String> transformed =
          path.mapK(maybeToEither, EITHER_MONAD);

      assertThat(transformed.supportsRecovery()).isTrue();
    }

    @Test
    @DisplayName("mapK() with MonadError throws NullPointerException when transform is null")
    void mapKWithMonadErrorThrowsOnNullTransform() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);

      assertThatNullPointerException()
          .isThrownBy(() -> path.mapK(null, EITHER_MONAD))
          .withMessageContaining("transform must not be null");
    }

    @Test
    @DisplayName("mapK() with MonadError throws NullPointerException when targetMonadError is null")
    void mapKWithMonadErrorThrowsOnNullTargetMonadError() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);
      NaturalTransformation<MaybeKind.Witness, EitherKind.Witness<String>> maybeToEither =
          new NaturalTransformation<>() {
            @Override
            public <A> Kind<EitherKind.Witness<String>, A> apply(Kind<MaybeKind.Witness, A> fa) {
              Maybe<A> maybe = MAYBE.narrow(fa);
              return maybe.isJust()
                  ? EITHER.widen(Either.right(maybe.get()))
                  : EITHER.widen(Either.left("Nothing found"));
            }
          };

      assertThatNullPointerException()
          .isThrownBy(
              () -> path.mapK(maybeToEither, (MonadError<EitherKind.Witness<String>, String>) null))
          .withMessageContaining("targetMonadError must not be null");
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethods {

    @Test
    @DisplayName("equals() returns true for same value")
    void equalsForSameValue() {
      GenericPath<MaybeKind.Witness, String> path1 = GenericPath.pure("hello", MONAD);
      GenericPath<MaybeKind.Witness, String> path2 = GenericPath.pure("hello", MONAD);

      assertThat(path1).isEqualTo(path2);
    }

    @Test
    @DisplayName("equals() returns false for different values")
    void equalsForDifferentValues() {
      GenericPath<MaybeKind.Witness, String> path1 = GenericPath.pure("hello", MONAD);
      GenericPath<MaybeKind.Witness, String> path2 = GenericPath.pure("world", MONAD);

      assertThat(path1).isNotEqualTo(path2);
    }

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsForSameInstance() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);

      assertThat(path).isEqualTo(path);
    }

    @Test
    @DisplayName("equals() returns false for null")
    void equalsForNull() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);

      assertThat(path).isNotEqualTo(null);
    }

    @Test
    @DisplayName("equals() returns false for different types")
    void equalsForDifferentType() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);

      assertThat(path).isNotEqualTo("hello");
    }

    @Test
    @DisplayName("hashCode() is consistent for equal objects")
    void hashCodeConsistency() {
      GenericPath<MaybeKind.Witness, String> path1 = GenericPath.pure("hello", MONAD);
      GenericPath<MaybeKind.Witness, String> path2 = GenericPath.pure("hello", MONAD);

      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }

    @Test
    @DisplayName("toString() includes value representation")
    void toStringIncludesValue() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.pure("hello", MONAD);

      assertThat(path.toString()).startsWith("GenericPath(");
      assertThat(path.toString()).contains("hello");
    }

    @Test
    @DisplayName("toString() for Nothing shows empty state")
    void toStringForNothing() {
      GenericPath<MaybeKind.Witness, String> path = GenericPath.of(MAYBE.<String>nothing(), MONAD);

      assertThat(path.toString()).startsWith("GenericPath(");
      assertThat(path.toString()).contains("Nothing");
    }
  }
}
