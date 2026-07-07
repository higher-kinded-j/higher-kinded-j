// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for VResultPath.
 *
 * <p>Tests cover factory methods, both-channel combinators, laziness semantics, error recovery,
 * conversions and the Functor/Monad laws that pin the direct composition over the carrier.
 */
@DisplayName("VResultPath<E, A> Complete Test Suite")
class VResultPathTest {

  private static final String TEST_VALUE = "test";
  private static final String TEST_ERROR = "boom";
  private static final Integer TEST_INT = 42;

  /** Executes the path's carrier on the calling thread and returns the produced Either. */
  private static <E, A> Either<E, A> execute(VResultPath<E, A> path) {
    return path.run().run();
  }

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.vresultRight() creates successful path")
    void vresultRightCreatesSuccessfulPath() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      assertThatEither(execute(path)).isRight().hasRight(TEST_VALUE);
    }

    @Test
    @DisplayName("Path.vresultLeft() creates failed path")
    void vresultLeftCreatesFailedPath() {
      VResultPath<String, Integer> path = Path.vresultLeft(TEST_ERROR);

      assertThatEither(execute(path)).isLeft().hasLeft(TEST_ERROR);
    }

    @Test
    @DisplayName("Path.vresultLeft() validates non-null error")
    void vresultLeftValidatesNonNullError() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vresultLeft(null))
          .withMessageContaining("error must not be null");
    }

    @Test
    @DisplayName("Path.vresultEither() lifts an existing Either")
    void vresultEitherLiftsExistingEither() {
      VResultPath<String, Integer> right = Path.vresultEither(Either.right(TEST_INT));
      VResultPath<String, Integer> left = Path.vresultEither(Either.left(TEST_ERROR));

      assertThatEither(execute(right)).hasRight(TEST_INT);
      assertThatEither(execute(left)).hasLeft(TEST_ERROR);
    }

    @Test
    @DisplayName("Path.vresultEither() validates non-null either")
    void vresultEitherValidatesNonNullEither() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vresultEither(null))
          .withMessageContaining("either must not be null");
    }

    @Test
    @DisplayName("Path.vresult() wraps an existing carrier")
    void vresultWrapsExistingCarrier() {
      VTask<Either<String, Integer>> carrier = VTask.succeed(Either.right(TEST_INT));
      VResultPath<String, Integer> path = Path.vresult(carrier);

      assertThatEither(execute(path)).hasRight(TEST_INT);
    }

    @Test
    @DisplayName("Path.vresult() validates non-null carrier")
    void vresultValidatesNonNullCarrier() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vresult(null))
          .withMessageContaining("task must not be null");
    }

    @Test
    @DisplayName("Path.vresultDefer() defers the supplier until run")
    void vresultDeferDefersSupplier() {
      AtomicInteger counter = new AtomicInteger(0);
      VResultPath<String, Integer> path =
          Path.vresultDefer(() -> Either.right(counter.incrementAndGet()));

      assertThat(counter.get()).isZero();
      assertThatEither(execute(path)).hasRight(1);
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Path.vresultDefer() validates non-null supplier")
    void vresultDeferValidatesNonNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.vresultDefer(null))
          .withMessageContaining("supplier must not be null");
    }

    @Test
    @DisplayName("Path.vresultDefer() rejects a null result when run")
    void vresultDeferRejectsNullResultWhenRun() {
      VResultPath<String, Integer> path = Path.vresultDefer(() -> null);

      assertThatNullPointerException()
          .isThrownBy(() -> execute(path))
          .withMessageContaining("supplier must not return null");
    }

    @Test
    @DisplayName("VResultPath.pure() creates successful path")
    void staticPureCreatesSuccessfulPath() {
      VResultPath<String, Integer> path = VResultPath.pure(TEST_INT);

      assertThatEither(execute(path)).hasRight(TEST_INT);
    }

    @Test
    @DisplayName("VResultPath.raiseError() creates failed path")
    void staticRaiseErrorCreatesFailedPath() {
      VResultPath<String, Integer> path = VResultPath.raiseError(TEST_ERROR);

      assertThatEither(execute(path)).hasLeft(TEST_ERROR);
    }

    @Test
    @DisplayName("VResultPath.fromEither() defers no side effects")
    void staticFromEitherProducesEither() {
      VResultPath<String, Integer> path = VResultPath.fromEither(Either.right(TEST_INT));

      assertThatEither(execute(path)).hasRight(TEST_INT);
    }

    @Test
    @DisplayName("VResultPath.fromVTask() keeps the carrier lazy")
    void staticFromVTaskKeepsCarrierLazy() {
      AtomicInteger counter = new AtomicInteger(0);
      VTask<Either<String, Integer>> carrier =
          VTask.delay(() -> Either.right(counter.incrementAndGet()));
      VResultPath<String, Integer> path = VResultPath.fromVTask(carrier);

      assertThat(counter.get()).isZero();
      assertThatEither(execute(path)).hasRight(1);
    }
  }

  @Nested
  @DisplayName("Run and Terminal Methods")
  class RunAndTerminalMethodsTests {

    @Test
    @DisplayName("run() returns the underlying carrier")
    void runReturnsUnderlyingCarrier() {
      VTask<Either<String, String>> carrier = VTask.succeed(Either.right(TEST_VALUE));
      VResultPath<String, String> path = VResultPath.fromVTask(carrier);

      assertThat(path.run()).isSameAs(carrier);
      assertThatEither(path.run().run()).hasRight(TEST_VALUE);
    }

    @Test
    @DisplayName("fold() applies the right mapper on success")
    void foldAppliesRightMapperOnSuccess() {
      VResultPath<String, Integer> path = Path.vresultRight(TEST_INT);

      assertThat(path.fold(e -> "error:" + e, a -> "value:" + a).run()).isEqualTo("value:42");
    }

    @Test
    @DisplayName("fold() applies the left mapper on failure")
    void foldAppliesLeftMapperOnFailure() {
      VResultPath<String, Integer> path = Path.vresultLeft(TEST_ERROR);

      assertThat(path.fold(e -> "error:" + e, a -> "value:" + a).run()).isEqualTo("error:boom");
    }

    @Test
    @DisplayName("fold() is lazy until the returned VTask is run")
    void foldIsLazyUntilRun() {
      AtomicBoolean folded = new AtomicBoolean(false);
      VTask<String> foldTask =
          Path.<String, Integer>vresultRight(TEST_INT)
              .fold(
                  e -> "error",
                  a -> {
                    folded.set(true);
                    return "value";
                  });

      assertThat(folded).isFalse();
      assertThat(foldTask.run()).isEqualTo("value");
      assertThat(folded).isTrue();
    }

    @Test
    @DisplayName("fold() validates null mappers")
    void foldValidatesNullMappers() {
      VResultPath<String, Integer> path = Path.vresultRight(TEST_INT);

      assertThatNullPointerException()
          .isThrownBy(() -> path.fold(null, a -> a))
          .withMessageContaining("leftMapper must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.fold(e -> 0, null))
          .withMessageContaining("rightMapper must not be null");
    }

    @Test
    @DisplayName("getOrElse() produces the success value")
    void getOrElseProducesSuccessValue() {
      VResultPath<String, Integer> path = Path.vresultRight(TEST_INT);

      assertThat(path.getOrElse(0).run()).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("getOrElse() produces the default on failure")
    void getOrElseProducesDefaultOnFailure() {
      VResultPath<String, Integer> path = Path.vresultLeft(TEST_ERROR);

      assertThat(path.getOrElse(0).run()).isZero();
    }

    @Test
    @DisplayName("getOrElseGet() does not invoke the supplier on success")
    void getOrElseGetDoesNotInvokeSupplierOnSuccess() {
      AtomicBoolean invoked = new AtomicBoolean(false);
      VResultPath<String, Integer> path = Path.vresultRight(TEST_INT);

      Integer result =
          path.getOrElseGet(
                  () -> {
                    invoked.set(true);
                    return 0;
                  })
              .run();

      assertThat(result).isEqualTo(TEST_INT);
      assertThat(invoked).isFalse();
    }

    @Test
    @DisplayName("getOrElseGet() invokes the supplier on failure")
    void getOrElseGetInvokesSupplierOnFailure() {
      VResultPath<String, Integer> path = Path.vresultLeft(TEST_ERROR);

      assertThat(path.getOrElseGet(() -> 99).run()).isEqualTo(99);
    }

    @Test
    @DisplayName("getOrElseGet() validates null supplier")
    void getOrElseGetValidatesNullSupplier() {
      VResultPath<String, Integer> path = Path.vresultRight(TEST_INT);

      assertThatNullPointerException()
          .isThrownBy(() -> path.getOrElseGet(null))
          .withMessageContaining("supplier must not be null");
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek, peekLeft)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms the success value")
    void mapTransformsSuccessValue() {
      VResultPath<String, String> path = Path.vresultRight("hello");

      VResultPath<String, Integer> result = path.map(String::length);

      assertThatEither(execute(result)).hasRight(5);
    }

    @Test
    @DisplayName("map() short-circuits on failure without invoking the mapper")
    void mapShortCircuitsOnFailure() {
      AtomicBoolean invoked = new AtomicBoolean(false);
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      VResultPath<String, Integer> result =
          path.map(
              s -> {
                invoked.set(true);
                return s.length();
              });

      assertThatEither(execute(result)).hasLeft(TEST_ERROR);
      assertThat(invoked).isFalse();
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      VResultPath<String, String> path = Path.vresultRight("hello");

      VResultPath<String, String> result =
          path.map(String::toUpperCase).map(s -> s + "!").map(s -> "[" + s + "]");

      assertThatEither(execute(result)).hasRight("[HELLO!]");
    }

    @Test
    @DisplayName("peek() observes the success value without modifying it")
    void peekObservesSuccessValue() {
      AtomicBoolean called = new AtomicBoolean(false);
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      VResultPath<String, String> result = path.peek(_ -> called.set(true));

      assertThat(called).isFalse();
      assertThatEither(execute(result)).hasRight(TEST_VALUE);
      assertThat(called).isTrue();
    }

    @Test
    @DisplayName("peek() does not observe a failure")
    void peekDoesNotObserveFailure() {
      AtomicBoolean called = new AtomicBoolean(false);
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      VResultPath<String, String> result = path.peek(_ -> called.set(true));

      assertThatEither(execute(result)).hasLeft(TEST_ERROR);
      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("peek() validates null consumer")
    void peekValidatesNullConsumer() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.peek(null))
          .withMessageContaining("consumer must not be null");
    }

    @Test
    @DisplayName("peekLeft() observes the error without modifying it")
    void peekLeftObservesError() {
      AtomicBoolean called = new AtomicBoolean(false);
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      VResultPath<String, String> result = path.peekLeft(_ -> called.set(true));

      assertThatEither(execute(result)).hasLeft(TEST_ERROR);
      assertThat(called).isTrue();
    }

    @Test
    @DisplayName("peekLeft() does not observe a success")
    void peekLeftDoesNotObserveSuccess() {
      AtomicBoolean called = new AtomicBoolean(false);
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      VResultPath<String, String> result = path.peekLeft(_ -> called.set(true));

      assertThatEither(execute(result)).hasRight(TEST_VALUE);
      assertThat(called).isFalse();
    }

    @Test
    @DisplayName("peekLeft() validates null consumer")
    void peekLeftValidatesNullConsumer() {
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      assertThatNullPointerException()
          .isThrownBy(() -> path.peekLeft(null))
          .withMessageContaining("consumer must not be null");
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains dependent computations")
    void viaChainsComputations() {
      VResultPath<String, String> path = Path.vresultRight("hello");

      VResultPath<String, Integer> result = path.via(s -> Path.vresultRight(s.length()));

      assertThatEither(execute(result)).hasRight(5);
    }

    @Test
    @DisplayName("via() short-circuits on failure without invoking the mapper")
    void viaShortCircuitsOnFailure() {
      AtomicBoolean invoked = new AtomicBoolean(false);
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      VResultPath<String, Integer> result =
          path.via(
              s -> {
                invoked.set(true);
                return Path.vresultRight(s.length());
              });

      assertThatEither(execute(result)).hasLeft(TEST_ERROR);
      assertThat(invoked).isFalse();
    }

    @Test
    @DisplayName("via() propagates a failure produced by the mapper")
    void viaPropagatesMapperFailure() {
      VResultPath<String, String> path = Path.vresultRight("hello");

      VResultPath<String, Integer> result = path.via(_ -> Path.vresultLeft(TEST_ERROR));

      assertThatEither(execute(result)).hasLeft(TEST_ERROR);
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() rejects a null result when run")
    void viaRejectsNullResultWhenRun() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      VResultPath<String, Integer> result = path.via(_ -> null);

      assertThatNullPointerException()
          .isThrownBy(() -> execute(result))
          .withMessageContaining("mapper must not return null");
    }

    @Test
    @DisplayName("via() throws for incompatible path type when run")
    void viaThrowsForIncompatibleType() {
      VResultPath<String, String> path = Path.vresultRight("hello");

      VResultPath<String, Integer> result = path.via(s -> Path.id(s.length()));

      assertThatIllegalArgumentException()
          .isThrownBy(() -> execute(result))
          .withMessageContaining("via mapper must return VResultPath");
    }

    @Test
    @DisplayName("flatMap() is alias for via")
    void flatMapIsAliasForVia() {
      VResultPath<String, String> path = Path.vresultRight("hello");

      VResultPath<String, Integer> viaResult = path.via(s -> Path.vresultRight(s.length()));
      @SuppressWarnings("unchecked")
      VResultPath<String, Integer> flatMapResult =
          (VResultPath<String, Integer>) path.flatMap(s -> Path.vresultRight(s.length()));

      assertThat(execute(flatMapResult)).isEqualTo(execute(viaResult));
    }

    @Test
    @DisplayName("then() sequences computations discarding the value")
    void thenSequencesComputationsDiscardingValue() {
      VResultPath<String, String> path = Path.vresultRight("ignored");

      VResultPath<String, Integer> result = path.then(() -> Path.vresultRight(TEST_INT));

      assertThatEither(execute(result)).hasRight(TEST_INT);
    }

    @Test
    @DisplayName("then() short-circuits on failure without invoking the supplier")
    void thenShortCircuitsOnFailure() {
      AtomicBoolean invoked = new AtomicBoolean(false);
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      VResultPath<String, Integer> result =
          path.then(
              () -> {
                invoked.set(true);
                return Path.vresultRight(TEST_INT);
              });

      assertThatEither(execute(result)).hasLeft(TEST_ERROR);
      assertThat(invoked).isFalse();
    }

    @Test
    @DisplayName("then() validates null supplier")
    void thenValidatesNullSupplier() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.then(null))
          .withMessageContaining("supplier must not be null");
    }

    @Test
    @DisplayName("then() rejects a null result when run")
    void thenRejectsNullResultWhenRun() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      VResultPath<String, Integer> result = path.then(() -> null);

      assertThatNullPointerException()
          .isThrownBy(() -> execute(result))
          .withMessageContaining("supplier must not return null");
    }

    @Test
    @DisplayName("then() throws for incompatible path type when run")
    void thenThrowsForIncompatibleType() {
      VResultPath<String, String> path = Path.vresultRight("hello");

      VResultPath<String, Integer> result = path.then(() -> Path.id(TEST_INT));

      assertThatIllegalArgumentException()
          .isThrownBy(() -> execute(result))
          .withMessageContaining("then supplier must return VResultPath");
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith, zipWith3)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines two success values")
    void zipWithCombinesTwoSuccessValues() {
      VResultPath<String, String> first = Path.vresultRight("hello");
      VResultPath<String, Integer> second = Path.vresultRight(3);

      VResultPath<String, String> result = first.zipWith(second, String::repeat);

      assertThatEither(execute(result)).hasRight("hellohellohello");
    }

    @Test
    @DisplayName("zipWith() short-circuits on the first failure without running the second task")
    void zipWithShortCircuitsOnFirstFailure() {
      AtomicBoolean secondRan = new AtomicBoolean(false);
      VResultPath<String, String> first = Path.vresultLeft(TEST_ERROR);
      VResultPath<String, Integer> second =
          Path.vresultDefer(
              () -> {
                secondRan.set(true);
                return Either.right(3);
              });

      VResultPath<String, String> result = first.zipWith(second, String::repeat);

      assertThatEither(execute(result)).hasLeft(TEST_ERROR);
      assertThat(secondRan).isFalse();
    }

    @Test
    @DisplayName("zipWith() propagates the second failure")
    void zipWithPropagatesSecondFailure() {
      VResultPath<String, String> first = Path.vresultRight("hello");
      VResultPath<String, Integer> second = Path.vresultLeft(TEST_ERROR);

      VResultPath<String, String> result = first.zipWith(second, String::repeat);

      assertThatEither(execute(result)).hasLeft(TEST_ERROR);
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(Path.vresultRight("x"), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith() throws for incompatible path type")
    void zipWithThrowsForIncompatibleType() {
      VResultPath<String, String> path = Path.vresultRight("hello");
      IdPath<Integer> idPath = Path.id(TEST_INT);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(idPath, (s, n) -> s + n))
          .withMessageContaining("Cannot zipWith non-VResultPath");
    }

    @Test
    @DisplayName("zipWith3() combines three success values")
    void zipWith3CombinesThreeSuccessValues() {
      VResultPath<String, String> first = Path.vresultRight("hello");
      VResultPath<String, String> second = Path.vresultRight(" ");
      VResultPath<String, String> third = Path.vresultRight("world");

      VResultPath<String, String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThatEither(execute(result)).hasRight("hello world");
    }

    @Test
    @DisplayName("zipWith3() short-circuits on the first failure")
    void zipWith3ShortCircuitsOnFirstFailure() {
      VResultPath<String, String> first = Path.vresultLeft(TEST_ERROR);
      VResultPath<String, String> second = Path.vresultRight(" ");
      VResultPath<String, String> third = Path.vresultRight("world");

      VResultPath<String, String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThatEither(execute(result)).hasLeft(TEST_ERROR);
    }

    @Test
    @DisplayName("zipWith3() short-circuits on the second failure without running the third task")
    void zipWith3ShortCircuitsOnSecondFailure() {
      AtomicBoolean thirdRan = new AtomicBoolean(false);
      VResultPath<String, String> first = Path.vresultRight("hello");
      VResultPath<String, String> second = Path.vresultLeft(TEST_ERROR);
      VResultPath<String, String> third =
          Path.vresultDefer(
              () -> {
                thirdRan.set(true);
                return Either.right("world");
              });

      VResultPath<String, String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThatEither(execute(result)).hasLeft(TEST_ERROR);
      assertThat(thirdRan).isFalse();
    }

    @Test
    @DisplayName("zipWith3() propagates the third failure")
    void zipWith3PropagatesThirdFailure() {
      VResultPath<String, String> first = Path.vresultRight("hello");
      VResultPath<String, String> second = Path.vresultRight(" ");
      VResultPath<String, String> third = Path.vresultLeft(TEST_ERROR);

      VResultPath<String, String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThatEither(execute(result)).hasLeft(TEST_ERROR);
    }

    @Test
    @DisplayName("zipWith3() validates null parameters")
    void zipWith3ValidatesNullParameters() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);
      VResultPath<String, String> other = Path.vresultRight("x");

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
  }

  @Nested
  @DisplayName("Recoverable Operations")
  class RecoverableOperationsTests {

    @Test
    @DisplayName("recover() provides a fallback value on failure")
    void recoverProvidesFallback() {
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      VResultPath<String, String> result = path.recover(e -> "recovered:" + e);

      assertThatEither(execute(result)).hasRight("recovered:boom");
    }

    @Test
    @DisplayName("recover() keeps the original value on success")
    void recoverKeepsOriginalOnSuccess() {
      AtomicBoolean invoked = new AtomicBoolean(false);
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      VResultPath<String, String> result =
          path.recover(
              _ -> {
                invoked.set(true);
                return "fallback";
              });

      assertThatEither(execute(result)).hasRight(TEST_VALUE);
      assertThat(invoked).isFalse();
    }

    @Test
    @DisplayName("recover() validates null recovery")
    void recoverValidatesNullRecovery() {
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      assertThatNullPointerException()
          .isThrownBy(() -> path.recover(null))
          .withMessageContaining("recovery must not be null");
    }

    @Test
    @DisplayName("recoverWith() provides a fallback path on failure")
    void recoverWithProvidesFallbackPath() {
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      VResultPath<String, String> result = path.recoverWith(_ -> Path.vresultRight("recovered"));

      assertThatEither(execute(result)).hasRight("recovered");
    }

    @Test
    @DisplayName("recoverWith() may recover to another failure")
    void recoverWithMayRecoverToAnotherFailure() {
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      VResultPath<String, String> result = path.recoverWith(e -> Path.vresultLeft(e + "!"));

      assertThatEither(execute(result)).hasLeft("boom!");
    }

    @Test
    @DisplayName("recoverWith() keeps the original value on success")
    void recoverWithKeepsOriginalOnSuccess() {
      AtomicBoolean invoked = new AtomicBoolean(false);
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      VResultPath<String, String> result =
          path.recoverWith(
              _ -> {
                invoked.set(true);
                return Path.vresultRight("fallback");
              });

      assertThatEither(execute(result)).hasRight(TEST_VALUE);
      assertThat(invoked).isFalse();
    }

    @Test
    @DisplayName("recoverWith() validates null recovery")
    void recoverWithValidatesNullRecovery() {
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      assertThatNullPointerException()
          .isThrownBy(() -> path.recoverWith(null))
          .withMessageContaining("recovery must not be null");
    }

    @Test
    @DisplayName("recoverWith() rejects a null result when run")
    void recoverWithRejectsNullResultWhenRun() {
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      VResultPath<String, String> result = path.recoverWith(_ -> null);

      assertThatNullPointerException()
          .isThrownBy(() -> execute(result))
          .withMessageContaining("recovery must not return null");
    }

    @Test
    @DisplayName("recoverWith() throws when recovery returns non-VResultPath")
    void recoverWithThrowsForNonVResultPath() {
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      VResultPath<String, String> result = path.recoverWith(_ -> Path.right("recovered"));

      assertThatIllegalArgumentException()
          .isThrownBy(() -> execute(result))
          .withMessageContaining("recoverWith must return VResultPath");
    }

    @Test
    @DisplayName("orElse() provides an alternative on failure")
    void orElseProvidesAlternative() {
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      VResultPath<String, String> result = path.orElse(() -> Path.vresultRight("alternative"));

      assertThatEither(execute(result)).hasRight("alternative");
    }

    @Test
    @DisplayName("orElse() keeps the original value on success")
    void orElseKeepsOriginalOnSuccess() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      VResultPath<String, String> result = path.orElse(() -> Path.vresultRight("alternative"));

      assertThatEither(execute(result)).hasRight(TEST_VALUE);
    }

    @Test
    @DisplayName("orElse() validates null alternative")
    void orElseValidatesNullAlternative() {
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      assertThatNullPointerException()
          .isThrownBy(() -> path.orElse(null))
          .withMessageContaining("alternative must not be null");
    }

    @Test
    @DisplayName("mapError() transforms the error channel")
    void mapErrorTransformsErrorChannel() {
      VResultPath<String, Integer> path = Path.vresultLeft(TEST_ERROR);

      VResultPath<Integer, Integer> result = path.mapError(String::length);

      assertThatEither(execute(result)).hasLeft(4);
    }

    @Test
    @DisplayName("mapError() leaves the success channel untouched")
    void mapErrorLeavesSuccessChannelUntouched() {
      VResultPath<String, Integer> path = Path.vresultRight(TEST_INT);

      VResultPath<Integer, Integer> result = path.mapError(String::length);

      assertThatEither(execute(result)).hasRight(TEST_INT);
    }

    @Test
    @DisplayName("mapError() validates null mapper")
    void mapErrorValidatesNullMapper() {
      VResultPath<String, Integer> path = Path.vresultLeft(TEST_ERROR);

      assertThatNullPointerException()
          .isThrownBy(() -> path.mapError(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("bimap() transforms the error channel on failure")
    void bimapTransformsErrorChannel() {
      VResultPath<String, Integer> path = Path.vresultLeft(TEST_ERROR);

      VResultPath<Integer, String> result = path.bimap(String::length, n -> "Value: " + n);

      assertThatEither(execute(result)).hasLeft(4);
    }

    @Test
    @DisplayName("bimap() transforms the success channel on success")
    void bimapTransformsSuccessChannel() {
      VResultPath<String, Integer> path = Path.vresultRight(TEST_INT);

      VResultPath<Integer, String> result = path.bimap(String::length, n -> "Value: " + n);

      assertThatEither(execute(result)).hasRight("Value: 42");
    }

    @Test
    @DisplayName("bimap() validates null mappers")
    void bimapValidatesNullMappers() {
      VResultPath<String, Integer> path = Path.vresultRight(TEST_INT);

      assertThatNullPointerException()
          .isThrownBy(() -> path.bimap(null, n -> n))
          .withMessageContaining("errorMapper must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.bimap(e -> e, null))
          .withMessageContaining("successMapper must not be null");
    }
  }

  @Nested
  @DisplayName("Laziness Semantics")
  class LazinessSemanticsTests {

    @Test
    @DisplayName("a composed pipeline executes nothing until run")
    void composedPipelineExecutesNothingUntilRun() {
      AtomicInteger sourceRuns = new AtomicInteger(0);
      AtomicInteger stepRuns = new AtomicInteger(0);

      VResultPath<String, Integer> pipeline =
          Path.<String, Integer>vresultDefer(() -> Either.right(sourceRuns.incrementAndGet()))
              .map(n -> n * 10)
              .via(
                  n ->
                      Path.vresultDefer(
                          () -> {
                            stepRuns.incrementAndGet();
                            return Either.right(n + 1);
                          }));

      assertThat(sourceRuns.get()).isZero();
      assertThat(stepRuns.get()).isZero();

      assertThatEither(execute(pipeline)).hasRight(11);
      assertThat(sourceRuns.get()).isEqualTo(1);
      assertThat(stepRuns.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("running the carrier twice re-executes the pipeline")
    void runningCarrierTwiceReExecutesPipeline() {
      AtomicInteger runs = new AtomicInteger(0);
      VResultPath<String, Integer> path =
          Path.vresultDefer(() -> Either.right(runs.incrementAndGet()));

      assertThatEither(execute(path)).hasRight(1);
      assertThatEither(execute(path)).hasRight(2);
      assertThat(runs.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("a failure short-circuits downstream effects")
    void failureShortCircuitsDownstreamEffects() {
      AtomicBoolean downstreamRan = new AtomicBoolean(false);

      VResultPath<String, Integer> pipeline =
          Path.<String, Integer>vresultLeft(TEST_ERROR)
              .via(
                  n ->
                      Path.vresultDefer(
                          () -> {
                            downstreamRan.set(true);
                            return Either.right(n + 1);
                          }));

      assertThatEither(execute(pipeline)).hasLeft(TEST_ERROR);
      assertThat(downstreamRan).isFalse();
    }

    @Test
    @DisplayName("a defect in the carrier propagates as from VTask.run()")
    void defectInCarrierPropagates() {
      IllegalStateException defect = new IllegalStateException("defect");
      VResultPath<String, Integer> path =
          VResultPath.fromVTask(
              VTask.of(
                  () -> {
                    throw defect;
                  }));

      assertThatThrownBy(() -> execute(path.map(n -> n + 1))).isSameAs(defect);
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toEitherPath() converts success to Right (blocking)")
    void toEitherPathConvertsSuccessToRight() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      EitherPath<String, String> result = path.toEitherPath();

      assertThatEither(result.run()).hasRight(TEST_VALUE);
    }

    @Test
    @DisplayName("toEitherPath() converts failure to Left (blocking)")
    void toEitherPathConvertsFailureToLeft() {
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      EitherPath<String, String> result = path.toEitherPath();

      assertThatEither(result.run()).hasLeft(TEST_ERROR);
    }

    @Test
    @DisplayName("toEitherPath() executes the carrier")
    void toEitherPathExecutesCarrier() {
      AtomicInteger runs = new AtomicInteger(0);
      VResultPath<String, Integer> path =
          Path.vresultDefer(() -> Either.right(runs.incrementAndGet()));

      EitherPath<String, Integer> result = path.toEitherPath();

      assertThat(runs.get()).isEqualTo(1);
      assertThatEither(result.run()).hasRight(1);
    }

    @Test
    @DisplayName("toEitherPath() propagates a defect from the carrier")
    void toEitherPathPropagatesDefect() {
      IllegalStateException defect = new IllegalStateException("defect");
      VResultPath<String, Integer> path =
          VResultPath.fromVTask(
              VTask.of(
                  () -> {
                    throw defect;
                  }));

      assertThatThrownBy(path::toEitherPath).isSameAs(defect);
    }

    @Test
    @DisplayName("toEitherPath() rejects a carrier that produces a null Either")
    void toEitherPathRejectsNullEither() {
      VResultPath<String, Integer> path = VResultPath.fromVTask(VTask.succeed(null));

      assertThatNullPointerException()
          .isThrownBy(path::toEitherPath)
          .withMessageContaining("VTask must not produce a null Either");
    }

    @Test
    @DisplayName("toVTaskPath() converts success to a successful task")
    void toVTaskPathConvertsSuccess() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      VTaskPath<String> result = path.toVTaskPath(IllegalStateException::new);

      assertThat(result.unsafeRun()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toVTaskPath() collapses failure into the VTask failure channel")
    void toVTaskPathCollapsesFailure() {
      VResultPath<String, String> path = Path.vresultLeft(TEST_ERROR);

      VTaskPath<String> result = path.toVTaskPath(IllegalStateException::new);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(TEST_ERROR);
    }

    @Test
    @DisplayName("toVTaskPath() is lazy")
    void toVTaskPathIsLazy() {
      AtomicInteger runs = new AtomicInteger(0);
      VResultPath<String, Integer> path =
          Path.vresultDefer(() -> Either.right(runs.incrementAndGet()));

      VTaskPath<Integer> result = path.toVTaskPath(IllegalStateException::new);

      assertThat(runs.get()).isZero();
      assertThat(result.unsafeRun()).isEqualTo(1);
    }

    @Test
    @DisplayName("toVTaskPath() validates null errorToException")
    void toVTaskPathValidatesNullMapper() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.toVTaskPath(null))
          .withMessageContaining("errorToException must not be null");
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("toString() shows the deferred sentinel without forcing execution")
    void toStringShowsDeferredSentinel() {
      AtomicBoolean ran = new AtomicBoolean(false);
      VResultPath<String, Integer> path =
          Path.vresultDefer(
              () -> {
                ran.set(true);
                return Either.right(TEST_INT);
              });

      assertThat(path.toString()).isEqualTo("VResultPath(<deferred>)");
      assertThat(ran).isFalse();
    }

    @Test
    @DisplayName("equals() returns true for the same instance")
    void equalsReturnsTrueForSameInstance() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      assertThat(path.equals(path)).isTrue();
    }

    @Test
    @DisplayName("equals() returns true for paths sharing the same carrier")
    void equalsReturnsTrueForSharedCarrier() {
      VTask<Either<String, String>> carrier = VTask.succeed(Either.right(TEST_VALUE));

      VResultPath<String, String> path1 = VResultPath.fromVTask(carrier);
      VResultPath<String, String> path2 = VResultPath.fromVTask(carrier);

      assertThat(path1).isEqualTo(path2).hasSameHashCodeAs(path2);
    }

    @Test
    @DisplayName("equals() returns false for different carriers")
    void equalsReturnsFalseForDifferentCarriers() {
      VResultPath<String, String> path1 = Path.vresultRight(TEST_VALUE);
      VResultPath<String, String> path2 = Path.vresultRight(TEST_VALUE);

      assertThat(path1).isNotEqualTo(path2);
    }

    @Test
    @DisplayName("equals() returns false for non-VResultPath")
    void equalsReturnsFalseForOtherTypes() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      assertThat(path.equals("not a path")).isFalse();
      assertThat(path.equals(null)).isFalse();
    }

    @Test
    @DisplayName("hashCode() is consistent")
    void hashCodeIsConsistent() {
      VResultPath<String, String> path = Path.vresultRight(TEST_VALUE);

      assertThat(path.hashCode()).isEqualTo(path.hashCode());
    }
  }

  @Nested
  @DisplayName("Law Verification")
  class LawVerificationTests {

    private final Function<Integer, VResultPath<String, String>> intToPath =
        x -> Path.vresultRight("result:" + x);

    private final Function<String, VResultPath<String, Integer>> stringToPath =
        s -> Path.vresultRight(s.length());

    @Test
    @DisplayName("Functor Identity Law: path.map(id) == path")
    void functorIdentityLaw() {
      VResultPath<String, Integer> right = Path.vresultRight(TEST_INT);
      VResultPath<String, Integer> left = Path.vresultLeft(TEST_ERROR);

      assertThat(execute(right.map(Function.identity()))).isEqualTo(execute(right));
      assertThat(execute(left.map(Function.identity()))).isEqualTo(execute(left));
    }

    @Test
    @DisplayName("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
    void functorCompositionLaw() {
      Function<Integer, Integer> addOne = x -> x + 1;
      Function<Integer, Integer> twice = x -> x * 2;
      VResultPath<String, Integer> path = Path.vresultRight(TEST_INT);

      assertThat(execute(path.map(addOne).map(twice)))
          .isEqualTo(execute(path.map(addOne.andThen(twice))));
    }

    @Test
    @DisplayName("Monad Left Identity Law: pure(a).via(f) == f(a)")
    void monadLeftIdentityLaw() {
      int value = 10;

      assertThat(execute(VResultPath.<String, Integer>pure(value).via(intToPath)))
          .isEqualTo(execute(intToPath.apply(value)));
    }

    @Test
    @DisplayName("Monad Right Identity Law: path.via(pure) == path")
    void monadRightIdentityLaw() {
      VResultPath<String, Integer> right = Path.vresultRight(TEST_INT);
      VResultPath<String, Integer> left = Path.vresultLeft(TEST_ERROR);

      assertThat(execute(right.via(VResultPath::pure))).isEqualTo(execute(right));
      assertThat(execute(left.via(VResultPath::pure))).isEqualTo(execute(left));
    }

    @Test
    @DisplayName("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
    void monadAssociativityLaw() {
      VResultPath<String, Integer> path = Path.vresultRight(10);

      assertThat(execute(path.via(intToPath).via(stringToPath)))
          .isEqualTo(execute(path.via(x -> intToPath.apply(x).via(stringToPath))));
    }

    @Test
    @DisplayName("raiseError() short-circuit invariant: raiseError(e).via(f) == raiseError(e)")
    void raiseErrorShortCircuitInvariant() {
      VResultPath<String, Integer> failed = VResultPath.raiseError(TEST_ERROR);

      assertThat(execute(failed.via(intToPath.compose(Function.identity()))))
          .isEqualTo(execute(VResultPath.<String, String>raiseError(TEST_ERROR)));
    }
  }
}
