// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for IOPath.
 *
 * <p>Tests cover factory methods, Composable/Combinable/Chainable/Effectful operations, utility
 * methods, and object methods.
 */
@DisplayName("IOPath<A> Complete Test Suite")
class IOPathTest {

  private static final String TEST_VALUE = "test";
  private static final Integer TEST_INT = 42;

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.io() creates IOPath from supplier")
    void pathIoCreatesIOPathFromSupplier() {
      IOPath<Integer> path = Path.io(() -> 10 + 32);

      assertThat(path.unsafeRun()).isEqualTo(42);
    }

    @Test
    @DisplayName("Path.io() is lazy - does not execute until run")
    void pathIoIsLazy() {
      AtomicBoolean executed = new AtomicBoolean(false);

      IOPath<Integer> path =
          Path.io(
              () -> {
                executed.set(true);
                return 42;
              });

      assertThat(executed).isFalse();
      path.unsafeRun();
      assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("Path.ioPure() creates IOPath with pure value")
    void pathIoPureCreatesIOPathWithPureValue() {
      IOPath<Integer> path = Path.ioPure(TEST_INT);

      assertThat(path.unsafeRun()).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("Path.ioRunnable() creates IOPath from Runnable")
    void pathIoRunnableCreatesIOPathFromRunnable() {
      AtomicBoolean executed = new AtomicBoolean(false);

      IOPath<Unit> path = Path.ioRunnable(() -> executed.set(true));

      assertThat(executed).isFalse();
      path.unsafeRun();
      assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("Path.ioPath() creates IOPath from IO")
    void pathIoPathCreatesIOPathFromIO() {
      IO<Integer> io = IO.delay(() -> TEST_INT);

      IOPath<Integer> path = Path.ioPath(io);

      assertThat(path.unsafeRun()).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("Path.io() validates non-null supplier")
    void pathIoValidatesNonNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.io(null))
          .withMessageContaining("supplier must not be null");
    }
  }

  @Nested
  @DisplayName("Run Methods")
  class RunMethodsTests {

    @Test
    @DisplayName("run() returns underlying IO")
    void runReturnsUnderlyingIO() {
      IOPath<Integer> path = Path.ioPure(TEST_INT);

      IO<Integer> io = path.run();

      assertThat(io).isInstanceOf(IO.class);
      assertThat(io.unsafeRunSync()).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("unsafeRun() executes and returns result")
    void unsafeRunExecutesAndReturnsResult() {
      IOPath<Integer> path = Path.io(() -> 10 + 32);

      assertThat(path.unsafeRun()).isEqualTo(42);
    }

    @Test
    @DisplayName("unsafeRun() propagates exceptions")
    void unsafeRunPropagatesExceptions() {
      IOPath<Integer> path =
          Path.io(
              () -> {
                throw new RuntimeException("test error");
              });

      assertThatRuntimeException().isThrownBy(path::unsafeRun).withMessage("test error");
    }

    @Test
    @DisplayName("runSafe() returns Try with success")
    void runSafeReturnsTryWithSuccess() {
      IOPath<Integer> path = Path.ioPure(TEST_INT);

      Try<Integer> result = path.runSafe();

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.orElse(null)).isEqualTo(TEST_INT);
    }

    @Test
    @DisplayName("runSafe() returns Try with failure")
    void runSafeReturnsTryWithFailure() {
      IOPath<Integer> path =
          Path.io(
              () -> {
                throw new RuntimeException("test error");
              });

      Try<Integer> result = path.runSafe();

      assertThat(result.isFailure()).isTrue();
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value lazily")
    void mapTransformsValueLazily() {
      AtomicBoolean executed = new AtomicBoolean(false);

      IOPath<Integer> path =
          Path.io(
                  () -> {
                    executed.set(true);
                    return 10;
                  })
              .map(i -> i * 2);

      assertThat(executed).isFalse();
      assertThat(path.unsafeRun()).isEqualTo(20);
      assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      IOPath<String> path = Path.ioPure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      IOPath<String> path = Path.ioPure("hello");

      IOPath<String> result = path.map(String::toUpperCase).map(s -> s + "!").map(s -> s.repeat(2));

      assertThat(result.unsafeRun()).isEqualTo("HELLO!HELLO!");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      AtomicBoolean called = new AtomicBoolean(false);

      IOPath<String> path = Path.ioPure(TEST_VALUE).peek(v -> called.set(true));

      assertThat(called).isFalse();
      assertThat(path.unsafeRun()).isEqualTo(TEST_VALUE);
      assertThat(called).isTrue();
    }

    @Test
    @DisplayName("peek() is also lazy")
    void peekIsAlsoLazy() {
      AtomicBoolean peeked = new AtomicBoolean(false);

      IOPath<String> path = Path.ioPure(TEST_VALUE).peek(v -> peeked.set(true));

      assertThat(peeked).isFalse();
      path.unsafeRun();
      assertThat(peeked).isTrue();
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains dependent computations lazily")
    void viaChainsComputationsLazily() {
      AtomicInteger counter = new AtomicInteger(0);

      IOPath<Integer> path =
          Path.io(
                  () -> {
                    counter.incrementAndGet();
                    return 10;
                  })
              .via(
                  i ->
                      Path.io(
                          () -> {
                            counter.incrementAndGet();
                            return i * 2;
                          }));

      assertThat(counter.get()).isEqualTo(0);
      assertThat(path.unsafeRun()).isEqualTo(20);
      assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      IOPath<String> path = Path.ioPure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() validates result is IOPath")
    void viaValidatesResultType() {
      IOPath<String> path = Path.ioPure(TEST_VALUE);

      IOPath<Integer> result = path.via(s -> Path.just(s.length()));

      assertThatIllegalArgumentException()
          .isThrownBy(result::unsafeRun)
          .withMessageContaining("via mapper must return IOPath");
    }

    @Test
    @DisplayName("flatMap() is alias for via")
    void flatMapIsAliasForVia() {
      IOPath<String> path = Path.ioPure("hello");

      Integer viaResult = path.via(s -> Path.ioPure(s.length())).unsafeRun();
      Integer flatMapResult = path.flatMap(s -> Path.ioPure(s.length())).unsafeRun();

      assertThat(viaResult).isEqualTo(flatMapResult);
    }

    @Test
    @DisplayName("then() sequences computations discarding value")
    void thenSequencesComputationsDiscardingValue() {
      AtomicBoolean firstExecuted = new AtomicBoolean(false);

      IOPath<Integer> path =
          Path.ioPure("hello").peek(v -> firstExecuted.set(true)).then(() -> Path.ioPure(42));

      assertThat(path.unsafeRun()).isEqualTo(42);
      assertThat(firstExecuted).isTrue();
    }

    @Test
    @DisplayName("then() maintains sequencing order")
    void thenMaintainsSequencingOrder() {
      StringBuilder order = new StringBuilder();

      IOPath<String> path =
          Path.io(
                  () -> {
                    order.append("1");
                    return "first";
                  })
              .then(
                  () ->
                      Path.io(
                          () -> {
                            order.append("2");
                            return "second";
                          }))
              .then(
                  () ->
                      Path.io(
                          () -> {
                            order.append("3");
                            return "third";
                          }));

      assertThat(order).isEmpty();
      assertThat(path.unsafeRun()).isEqualTo("third");
      assertThat(order.toString()).isEqualTo("123");
    }

    @Test
    @DisplayName("then() throws when supplier returns wrong type")
    void thenThrowsWhenSupplierReturnsWrongType() {
      IOPath<String> path = Path.ioPure(TEST_VALUE);

      IOPath<Integer> result = path.then(() -> Path.just(42));

      // Exception thrown when executed
      assertThatIllegalArgumentException()
          .isThrownBy(result::unsafeRun)
          .withMessageContaining("then supplier must return IOPath");
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines two IOPaths")
    void zipWithCombinesTwoIOPaths() {
      IOPath<String> first = Path.ioPure("hello");
      IOPath<Integer> second = Path.ioPure(3);

      IOPath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(result.unsafeRun()).isEqualTo("hellohellohello");
    }

    @Test
    @DisplayName("zipWith() executes both IOs")
    void zipWithExecutesBothIOs() {
      AtomicBoolean firstExecuted = new AtomicBoolean(false);
      AtomicBoolean secondExecuted = new AtomicBoolean(false);

      IOPath<String> first =
          Path.io(
              () -> {
                firstExecuted.set(true);
                return "hello";
              });
      IOPath<Integer> second =
          Path.io(
              () -> {
                secondExecuted.set(true);
                return 3;
              });

      IOPath<String> result = first.zipWith(second, (s, n) -> s.repeat(n));

      assertThat(firstExecuted).isFalse();
      assertThat(secondExecuted).isFalse();

      result.unsafeRun();

      assertThat(firstExecuted).isTrue();
      assertThat(secondExecuted).isTrue();
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      IOPath<String> path = Path.ioPure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(Path.ioPure("x"), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith3() combines three IOPaths")
    void zipWith3CombinesThreeIOPaths() {
      IOPath<String> first = Path.ioPure("hello");
      IOPath<String> second = Path.ioPure(" ");
      IOPath<String> third = Path.ioPure("world");

      IOPath<String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.unsafeRun()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("zipWith() throws when given non-IOPath")
    void zipWithThrowsWhenGivenNonIOPath() {
      IOPath<String> path = Path.ioPure(TEST_VALUE);
      MaybePath<Integer> maybePath = Path.just(TEST_INT);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(maybePath, (s, i) -> s + i))
          .withMessageContaining("Cannot zipWith non-IOPath");
    }
  }

  @Nested
  @DisplayName("Error Handling Operations")
  class ErrorHandlingOperationsTests {

    @Test
    @DisplayName("handleError() provides fallback for exceptions")
    void handleErrorProvidesFallbackForExceptions() {
      IOPath<String> path =
          Path.<String>io(
                  () -> {
                    throw new RuntimeException("error");
                  })
              .handleError(ex -> "recovered: " + ex.getMessage());

      assertThat(path.unsafeRun()).isEqualTo("recovered: error");
    }

    @Test
    @DisplayName("handleError() preserves successful value")
    void handleErrorPreservesSuccessfulValue() {
      IOPath<String> path = Path.ioPure(TEST_VALUE).handleError(ex -> "recovered");

      assertThat(path.unsafeRun()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("handleErrorWith() provides fallback IO for exceptions")
    void handleErrorWithProvidesFallbackIOForExceptions() {
      IOPath<String> path =
          Path.<String>io(
                  () -> {
                    throw new RuntimeException("error");
                  })
              .handleErrorWith(ex -> Path.ioPure("fallback"));

      assertThat(path.unsafeRun()).isEqualTo("fallback");
    }

    @Test
    @DisplayName("handleErrorWith() preserves successful value")
    void handleErrorWithPreservesSuccessfulValue() {
      IOPath<String> path = Path.ioPure(TEST_VALUE).handleErrorWith(ex -> Path.ioPure("fallback"));

      assertThat(path.unsafeRun()).isEqualTo(TEST_VALUE);
    }
  }

  @Nested
  @DisplayName("Utility Operations")
  class UtilityOperationsTests {

    @Test
    @DisplayName("asUnit() discards result")
    void asUnitDiscardsResult() {
      AtomicBoolean executed = new AtomicBoolean(false);

      IOPath<Unit> path =
          Path.io(
                  () -> {
                    executed.set(true);
                    return TEST_INT;
                  })
              .asUnit();

      assertThat(executed).isFalse();
      Unit result = path.unsafeRun();
      assertThat(result).isEqualTo(Unit.INSTANCE);
      assertThat(executed).isTrue();
    }

    @Test
    @DisplayName("toTryPath() converts to TryPath")
    void toTryPathConvertsToTryPath() {
      IOPath<Integer> successPath = Path.ioPure(TEST_INT);
      IOPath<Integer> failurePath =
          Path.io(
              () -> {
                throw new RuntimeException("error");
              });

      TryPath<Integer> successResult = successPath.toTryPath();
      TryPath<Integer> failureResult = failurePath.toTryPath();

      assertThat(successResult.run().isSuccess()).isTrue();
      assertThat(successResult.getOrElse(null)).isEqualTo(TEST_INT);
      assertThat(failureResult.run().isFailure()).isTrue();
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("equals() is reference-based for IO")
    void equalsIsReferenceBasedForIO() {
      IOPath<Integer> path1 = Path.ioPure(TEST_INT);
      IOPath<Integer> path2 = Path.ioPure(TEST_INT);

      // IO equality is reference-based since it represents a computation
      assertThat(path1).isEqualTo(path1);
      assertThat(path1).isNotEqualTo(path2);
    }

    @Test
    @DisplayName("hashCode() is identity-based")
    void hashCodeIsIdentityBased() {
      IOPath<Integer> path = Path.ioPure(TEST_INT);

      assertThat(path.hashCode()).isEqualTo(System.identityHashCode(path));
    }

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      IOPath<Integer> path = Path.ioPure(TEST_INT);

      assertThat(path.toString()).contains("IOPath");
      assertThat(path.toString()).contains("deferred");
    }
  }

  @Nested
  @DisplayName("Complex Chaining Patterns")
  class ComplexChainingPatternsTests {

    @Test
    @DisplayName("Deferred computation pattern")
    void deferredComputationPattern() {
      AtomicInteger counter = new AtomicInteger(0);

      IOPath<Integer> computation =
          Path.io(counter::incrementAndGet)
              .map(i -> i * 2)
              .via(i -> Path.io(() -> i + counter.incrementAndGet()));

      // Nothing has executed yet
      assertThat(counter.get()).isEqualTo(0);

      // First run
      Integer result1 = computation.unsafeRun();
      assertThat(result1).isEqualTo(4); // (1 * 2) + 2

      // Second run - executes again (IO is not memoized)
      Integer result2 = computation.unsafeRun();
      assertThat(result2).isEqualTo(10); // (3 * 2) + 4
    }

    @Test
    @DisplayName("Effect sequencing pattern")
    void effectSequencingPattern() {
      StringBuilder log = new StringBuilder();

      IOPath<String> pipeline =
          Path.ioRunnable(() -> log.append("start>"))
              .then(
                  () ->
                      Path.io(
                          () -> {
                            log.append("process>");
                            return "data";
                          }))
              .map(
                  s -> {
                    log.append("transform>");
                    return s.toUpperCase();
                  })
              .peek(s -> log.append("result:").append(s));

      assertThat(log).isEmpty();

      String result = pipeline.unsafeRun();

      assertThat(result).isEqualTo("DATA");
      assertThat(log.toString()).isEqualTo("start>process>transform>result:DATA");
    }

    @Test
    @DisplayName("Resource acquisition pattern")
    void resourceAcquisitionPattern() {
      AtomicBoolean resourceAcquired = new AtomicBoolean(false);
      AtomicBoolean resourceReleased = new AtomicBoolean(false);

      IOPath<String> pipeline =
          Path.io(
                  () -> {
                    resourceAcquired.set(true);
                    return "resource";
                  })
              .map(r -> r + "-used")
              .peek(r -> resourceReleased.set(true));

      assertThat(resourceAcquired).isFalse();
      assertThat(resourceReleased).isFalse();

      String result = pipeline.unsafeRun();

      assertThat(result).isEqualTo("resource-used");
      assertThat(resourceAcquired).isTrue();
      assertThat(resourceReleased).isTrue();
    }

    @Test
    @DisplayName("Combining multiple IO sources")
    void combiningMultipleIOSources() {
      IOPath<String> firstName = Path.ioPure("John");
      IOPath<String> lastName = Path.ioPure("Doe");
      IOPath<Integer> age = Path.ioPure(30);

      IOPath<String> fullRecord =
          firstName.zipWith3(
              lastName, age, (first, last, a) -> String.format("%s %s, age %d", first, last, a));

      assertThat(fullRecord.unsafeRun()).isEqualTo("John Doe, age 30");
    }

    @Test
    @DisplayName("Error recovery with retries")
    void errorRecoveryWithRetries() {
      AtomicInteger attempts = new AtomicInteger(0);

      IOPath<String> unreliable =
          Path.io(
              () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                  throw new RuntimeException("Attempt " + attempt + " failed");
                }
                return "success on attempt " + attempt;
              });

      IOPath<String> withRetry =
          unreliable
              .handleErrorWith(ex -> unreliable)
              .handleErrorWith(ex -> unreliable)
              .handleError(ex -> "all retries failed: " + ex.getMessage());

      String result = withRetry.unsafeRun();

      assertThat(result).isEqualTo("success on attempt 3");
      assertThat(attempts.get()).isEqualTo(3);
    }
  }
}
