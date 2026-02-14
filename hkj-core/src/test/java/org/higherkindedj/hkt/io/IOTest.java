// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.io.IOAssert.assertThatIO;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IO Core Operations Test Suite")
class IOTest {

  private static final int TEST_VALUE = 42;
  private static final String TEST_STRING = "test";

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {

    @Test
    @DisplayName("delay() creates lazy IO")
    void delayCreatesLazyIO() {
      AtomicInteger counter = new AtomicInteger(0);

      IO<Integer> io = IO.delay(() -> counter.incrementAndGet());

      // Should not have executed yet
      assertThat(counter.get()).isZero();

      // Execute and verify
      assertThatIO(io).hasValue(1);
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("delay() with null supplier throws NullPointerException")
    void delayWithNullSupplierThrows() {
      assertThatThrownBy(() -> IO.delay(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("thunk");
    }

    @Test
    @DisplayName("delay() can create IO with null value")
    void delayCanCreateIOWithNullValue() {
      IO<String> io = IO.delay(() -> null);

      assertThatIO(io).hasValueNull();
    }

    @Test
    @DisplayName("fromRunnable() creates IO from side effect")
    void fromRunnableCreatesIOFromSideEffect() {
      AtomicInteger counter = new AtomicInteger(0);
      Runnable sideEffect = () -> counter.incrementAndGet();

      IO<Unit> io = IO.fromRunnable(sideEffect);

      // Should not have executed yet (lazy)
      assertThat(counter.get()).isZero();

      // Execute and verify
      assertThatIO(io).hasValue(Unit.INSTANCE);
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("fromRunnable() with null throws NullPointerException")
    void fromRunnableWithNullThrows() {
      assertThatThrownBy(() -> IO.fromRunnable(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("runnable cannot be null");
    }

    @Test
    @DisplayName("fromRunnable() is lazy")
    void fromRunnableIsLazy() {
      AtomicInteger counter = new AtomicInteger(0);

      IO<Unit> io = IO.fromRunnable(() -> counter.incrementAndGet());

      // Should not execute on creation
      assertThat(counter.get()).isZero();

      // Should execute on unsafeRunSync
      io.unsafeRunSync();
      assertThat(counter.get()).isEqualTo(1);

      // Should execute again on second call
      io.unsafeRunSync();
      assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("fromRunnable() propagates exceptions")
    void fromRunnablePropagatesExceptions() {
      RuntimeException exception = new RuntimeException("Side effect failed");
      Runnable failingEffect =
          () -> {
            throw exception;
          };

      IO<Unit> io = IO.fromRunnable(failingEffect);

      assertThatIO(io).throwsException(RuntimeException.class).withMessage("Side effect failed");
    }

    @Test
    @DisplayName("fromRunnable() can be composed with flatMap")
    void fromRunnableCanBeComposedWithFlatMap() {
      StringBuilder log = new StringBuilder();

      IO<Unit> step1 = IO.fromRunnable(() -> log.append("A"));
      IO<Unit> step2 = IO.fromRunnable(() -> log.append("B"));
      IO<Unit> step3 = IO.fromRunnable(() -> log.append("C"));

      IO<Unit> sequence = step1.flatMap(u1 -> step2).flatMap(u2 -> step3);

      assertThat(log.toString()).isEmpty();

      assertThatIO(sequence).hasValue(Unit.INSTANCE);
      assertThat(log.toString()).isEqualTo("ABC");
    }

    @Test
    @DisplayName("fromRunnable() returns Unit.INSTANCE")
    void fromRunnableReturnsUnitInstance() {
      IO<Unit> io = IO.fromRunnable(() -> {});

      Unit result = io.unsafeRunSync();

      assertThat(result).isSameAs(Unit.INSTANCE);
      assertThat(result.toString()).isEqualTo("()");
    }
  }

  @Nested
  @DisplayName("asUnit() Method")
  class AsUnitMethod {

    @Test
    @DisplayName("asUnit() discards result and returns Unit")
    void asUnitDiscardsResultAndReturnsUnit() {
      IO<Integer> io = IO.delay(() -> TEST_VALUE);

      IO<Unit> unitIO = io.asUnit();

      assertThatIO(unitIO).hasValue(Unit.INSTANCE);
    }

    @Test
    @DisplayName("asUnit() preserves side effects")
    void asUnitPreservesSideEffects() {
      AtomicInteger counter = new AtomicInteger(0);

      IO<Integer> io =
          IO.delay(
              () -> {
                counter.incrementAndGet();
                return TEST_VALUE;
              });

      IO<Unit> unitIO = io.asUnit();

      // Should not execute yet
      assertThat(counter.get()).isZero();

      // Execute and verify side effect occurred
      assertThatIO(unitIO).hasValue(Unit.INSTANCE);
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("asUnit() discards non-Unit return values")
    void asUnitDiscardsNonUnitReturnValues() {
      IO<String> io = IO.delay(() -> "ignored value");

      IO<Unit> unitIO = io.asUnit();

      Unit result = unitIO.unsafeRunSync();
      assertThat(result).isSameAs(Unit.INSTANCE);
    }

    @Test
    @DisplayName("asUnit() propagates exceptions")
    void asUnitPropagatesExceptions() {
      RuntimeException exception = new RuntimeException("Original error");
      IO<Integer> failingIO =
          IO.delay(
              () -> {
                throw exception;
              });

      IO<Unit> unitIO = failingIO.asUnit();

      assertThatIO(unitIO).throwsException(RuntimeException.class).withMessage("Original error");
    }

    @Test
    @DisplayName("asUnit() is lazy")
    void asUnitIsLazy() {
      AtomicInteger counter = new AtomicInteger(0);

      IO<Integer> io =
          IO.delay(
              () -> {
                counter.incrementAndGet();
                return TEST_VALUE;
              });

      IO<Unit> unitIO = io.asUnit();

      // Should not execute on creation
      assertThat(counter.get()).isZero();

      // Should execute on unsafeRunSync
      unitIO.unsafeRunSync();
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("asUnit() can be chained")
    void asUnitCanBeChained() {
      StringBuilder log = new StringBuilder();

      IO<Integer> io1 =
          IO.delay(
              () -> {
                log.append("1");
                return 1;
              });
      IO<String> io2 =
          IO.delay(
              () -> {
                log.append("2");
                return "two";
              });

      IO<Unit> sequence = io1.asUnit().flatMap(u -> io2.asUnit());

      assertThat(log.toString()).isEmpty();

      assertThatIO(sequence).hasValue(Unit.INSTANCE);
      assertThat(log.toString()).isEqualTo("12");
    }

    @Test
    @DisplayName("asUnit() with null value returns Unit")
    void asUnitWithNullValueReturnsUnit() {
      IO<String> io = IO.delay(() -> null);

      IO<Unit> unitIO = io.asUnit();

      assertThatIO(unitIO).hasValue(Unit.INSTANCE);
    }
  }

  @Nested
  @DisplayName("map() Method")
  class MapMethod {

    @Test
    @DisplayName("map() transforms the value")
    void mapTransformsValue() {
      IO<Integer> io = IO.delay(() -> TEST_VALUE);

      IO<String> mapped = io.map(i -> "Value: " + i);

      assertThatIO(mapped).hasValue("Value: " + TEST_VALUE);
    }

    @Test
    @DisplayName("map() is lazy")
    void mapIsLazy() {
      AtomicInteger counter = new AtomicInteger(0);

      IO<Integer> io = IO.delay(() -> counter.incrementAndGet());
      IO<String> mapped = io.map(Object::toString);

      // Should not execute yet
      assertThat(counter.get()).isZero();

      // Execute and verify
      assertThatIO(mapped).hasValue("1");
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("map() with null mapper throws NullPointerException")
    void mapWithNullMapperThrows() {
      IO<Integer> io = IO.delay(() -> TEST_VALUE);

      assertThatThrownBy(() -> io.map(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f");
    }

    @Test
    @DisplayName("map() propagates exceptions from original IO")
    void mapPropagatesExceptionsFromOriginalIO() {
      RuntimeException exception = new RuntimeException("Original error");
      IO<Integer> failingIO =
          IO.delay(
              () -> {
                throw exception;
              });

      IO<String> mapped = failingIO.map(Object::toString);

      assertThatIO(mapped).throwsException(RuntimeException.class).withMessage("Original error");
    }

    @Test
    @DisplayName("map() propagates exceptions from mapper function")
    void mapPropagatesExceptionsFromMapper() {
      IO<Integer> io = IO.delay(() -> TEST_VALUE);

      RuntimeException exception = new RuntimeException("Mapper error");
      IO<String> mapped =
          io.map(
              i -> {
                throw exception;
              });

      assertThatIO(mapped).throwsException(RuntimeException.class).withMessage("Mapper error");
    }

    @Test
    @DisplayName("map() can be chained")
    void mapCanBeChained() {
      IO<Integer> io = IO.delay(() -> TEST_VALUE);

      IO<String> result = io.map(i -> i * 2).map(i -> i + 10).map(i -> "Result: " + i);

      assertThatIO(result).hasValue("Result: " + (TEST_VALUE * 2 + 10));
    }
  }

  @Nested
  @DisplayName("flatMap() Method")
  class FlatMapMethod {

    @Test
    @DisplayName("flatMap() chains IO computations")
    void flatMapChainsIOComputations() {
      IO<Integer> io = IO.delay(() -> TEST_VALUE);

      IO<String> flatMapped = io.flatMap(i -> IO.delay(() -> "Value: " + i));

      assertThatIO(flatMapped).hasValue("Value: " + TEST_VALUE);
    }

    @Test
    @DisplayName("flatMap() is lazy")
    void flatMapIsLazy() {
      AtomicInteger counter = new AtomicInteger(0);

      IO<Integer> io = IO.delay(() -> counter.incrementAndGet());
      IO<String> flatMapped = io.flatMap(i -> IO.delay(() -> "Value: " + i));

      // Should not execute yet
      assertThat(counter.get()).isZero();

      // Execute and verify
      assertThatIO(flatMapped).hasValue("Value: 1");
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("flatMap() with null function throws NullPointerException")
    void flatMapWithNullFunctionThrows() {
      IO<Integer> io = IO.delay(() -> TEST_VALUE);

      assertThatThrownBy(() -> io.flatMap(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f");
    }

    @Test
    @DisplayName("flatMap() propagates exceptions from original IO")
    void flatMapPropagatesExceptionsFromOriginalIO() {
      RuntimeException exception = new RuntimeException("Original error");
      IO<Integer> failingIO =
          IO.delay(
              () -> {
                throw exception;
              });

      IO<String> flatMapped = failingIO.flatMap(i -> IO.delay(() -> "Value: " + i));

      assertThatIO(flatMapped)
          .throwsException(RuntimeException.class)
          .withMessage("Original error");
    }

    @Test
    @DisplayName("flatMap() propagates exceptions from flatMap function")
    void flatMapPropagatesExceptionsFromFunction() {
      IO<Integer> io = IO.delay(() -> TEST_VALUE);

      RuntimeException exception = new RuntimeException("FlatMap error");
      IO<String> flatMapped =
          io.flatMap(
              i -> {
                throw exception;
              });

      assertThatIO(flatMapped).throwsException(RuntimeException.class).withMessage("FlatMap error");
    }

    @Test
    @DisplayName("flatMap() propagates exceptions from resulting IO")
    void flatMapPropagatesExceptionsFromResultingIO() {
      IO<Integer> io = IO.delay(() -> TEST_VALUE);

      RuntimeException exception = new RuntimeException("Result error");
      IO<String> flatMapped =
          io.flatMap(
              i ->
                  IO.delay(
                      () -> {
                        throw exception;
                      }));

      assertThatIO(flatMapped).throwsException(RuntimeException.class).withMessage("Result error");
    }

    @Test
    @DisplayName("flatMap() can be chained")
    void flatMapCanBeChained() {
      IO<Integer> io = IO.delay(() -> TEST_VALUE);

      IO<String> result =
          io.flatMap(i -> IO.delay(() -> i * 2))
              .flatMap(i -> IO.delay(() -> i + 10))
              .flatMap(i -> IO.delay(() -> "Result: " + i));

      assertThatIO(result).hasValue("Result: " + (TEST_VALUE * 2 + 10));
    }

    @Test
    @DisplayName("flatMap() with null result IO throws NullPointerException")
    void flatMapWithNullResultIOThrows() {
      IO<Integer> io = IO.delay(() -> TEST_VALUE);

      IO<String> flatMapped = io.flatMap(i -> null);

      assertThatThrownBy(() -> flatMapped.unsafeRunSync())
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              "Function f in IO.flatMap returned null when IO expected, which is not allowed");
    }
  }

  @Nested
  @DisplayName("unsafeRunSync() Method")
  class UnsafeRunSyncMethod {

    @Test
    @DisplayName("unsafeRunSync() executes the IO")
    void unsafeRunSyncExecutesIO() {
      AtomicInteger counter = new AtomicInteger(0);

      IO<Integer> io = IO.delay(() -> counter.incrementAndGet());

      int result = io.unsafeRunSync();

      assertThat(result).isEqualTo(1);
      assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("unsafeRunSync() can be called multiple times")
    void unsafeRunSyncCanBeCalledMultipleTimes() {
      AtomicInteger counter = new AtomicInteger(0);

      IO<Integer> io = IO.delay(() -> counter.incrementAndGet());

      int result1 = io.unsafeRunSync();
      int result2 = io.unsafeRunSync();
      int result3 = io.unsafeRunSync();

      assertThat(result1).isEqualTo(1);
      assertThat(result2).isEqualTo(2);
      assertThat(result3).isEqualTo(3);
      assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("unsafeRunSync() propagates exceptions")
    void unsafeRunSyncPropagatesExceptions() {
      RuntimeException exception = new RuntimeException("Execution error");
      IO<Integer> failingIO =
          IO.delay(
              () -> {
                throw exception;
              });

      assertThatThrownBy(() -> failingIO.unsafeRunSync())
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Execution error");
    }

    @Test
    @DisplayName("unsafeRunSync() returns null if computation produces null")
    void unsafeRunSyncReturnsNullIfComputationProducesNull() {
      IO<String> io = IO.delay(() -> null);

      String result = io.unsafeRunSync();

      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Complex chain with map, flatMap, and asUnit")
    void complexChainWithMapFlatMapAndAsUnit() {
      StringBuilder log = new StringBuilder();

      IO<Integer> step1 =
          IO.delay(
              () -> {
                log.append("1");
                return 10;
              });

      IO<Unit> sequence =
          step1
              .map(
                  i -> {
                    log.append("2");
                    return i * 2;
                  })
              .flatMap(
                  i ->
                      IO.delay(
                          () -> {
                            log.append("3");
                            return i + 5;
                          }))
              .asUnit();

      assertThat(log.toString()).isEmpty();

      assertThatIO(sequence).hasValue(Unit.INSTANCE);
      assertThat(log.toString()).isEqualTo("123");
    }

    @Test
    @DisplayName("fromRunnable composition with regular IO")
    void fromRunnableCompositionWithRegularIO() {
      AtomicReference<String> state = new AtomicReference<>("");

      IO<Unit> setup = IO.fromRunnable(() -> state.set("initialised"));
      IO<String> process = IO.delay(() -> state.get() + "-processed");
      IO<Unit> cleanup = IO.fromRunnable(() -> state.set("cleaned"));

      IO<Unit> workflow = setup.flatMap(u1 -> process).flatMap(s -> cleanup);

      assertThat(state.get()).isEmpty();

      assertThatIO(workflow).hasValue(Unit.INSTANCE);
      assertThat(state.get()).isEqualTo("cleaned");
    }

    @Test
    @DisplayName("Sequential side effects with fromRunnable")
    void sequentialSideEffectsWithFromRunnable() {
      StringBuilder log = new StringBuilder();

      IO<Unit> io =
          IO.fromRunnable(() -> log.append("A"))
              .flatMap(u -> IO.fromRunnable(() -> log.append("B")))
              .flatMap(u -> IO.fromRunnable(() -> log.append("C")));

      assertThat(log.toString()).isEmpty();

      io.unsafeRunSync();

      assertThat(log.toString()).isEqualTo("ABC");
    }

    @Test
    @DisplayName("asUnit in conditional logic")
    void asUnitInConditionalLogic() {
      AtomicInteger successCount = new AtomicInteger(0);
      AtomicInteger errorCount = new AtomicInteger(0);

      IO<Boolean> condition = IO.delay(() -> true);

      IO<Unit> workflow =
          condition.flatMap(
              cond -> {
                if (cond) {
                  return IO.fromRunnable(() -> successCount.incrementAndGet());
                } else {
                  return IO.fromRunnable(() -> errorCount.incrementAndGet());
                }
              });

      assertThat(successCount.get()).isZero();
      assertThat(errorCount.get()).isZero();

      assertThatIO(workflow).hasValue(Unit.INSTANCE);
      assertThat(successCount.get()).isEqualTo(1);
      assertThat(errorCount.get()).isZero();
    }

    @Test
    @DisplayName("Database-like transaction pattern")
    void databaseLikeTransactionPattern() {
      StringBuilder log = new StringBuilder();

      IO<Unit> beginTransaction = IO.fromRunnable(() -> log.append("BEGIN;"));
      IO<Integer> insertData =
          IO.delay(
              () -> {
                log.append("INSERT;");
                return 1;
              });
      IO<Unit> commit = IO.fromRunnable(() -> log.append("COMMIT;"));

      IO<Unit> transaction =
          beginTransaction.flatMap(u -> insertData.asUnit()).flatMap(u -> commit);

      assertThat(log.toString()).isEmpty();

      assertThatIO(transaction).hasValue(Unit.INSTANCE);
      assertThat(log.toString()).isEqualTo("BEGIN;INSERT;COMMIT;");
    }
  }

  @Nested
  @DisplayName("Lazy Evaluation Tests")
  class LazyEvaluationTests {

    @Test
    @DisplayName("IO creation does not execute")
    void ioCreationDoesNotExecute() {
      AtomicInteger counter = new AtomicInteger(0);

      IO<Integer> io1 = IO.delay(() -> counter.incrementAndGet());
      IO<Integer> io2 = io1.map(i -> i * 2);
      IO<String> io3 = io2.flatMap(i -> IO.delay(() -> "Value: " + i));
      IO<Unit> io4 = io3.asUnit();

      // None of this should have executed
      assertThat(counter.get()).isZero();
    }

    @Test
    @DisplayName("Multiple map operations don't execute until unsafeRunSync")
    void multipleMapOperationsDontExecuteUntilRun() {
      AtomicInteger executions = new AtomicInteger(0);

      IO<Integer> io =
          IO.delay(
                  () -> {
                    executions.incrementAndGet();
                    return 1;
                  })
              .map(
                  i -> {
                    executions.incrementAndGet();
                    return i + 1;
                  })
              .map(
                  i -> {
                    executions.incrementAndGet();
                    return i + 1;
                  });

      assertThat(executions.get()).isZero();

      int result = io.unsafeRunSync();

      assertThat(result).isEqualTo(3);
      assertThat(executions.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("fromRunnable does not execute until unsafeRunSync")
    void fromRunnableDoesNotExecuteUntilRun() {
      AtomicInteger counter = new AtomicInteger(0);

      IO<Unit> io1 = IO.fromRunnable(() -> counter.incrementAndGet());
      IO<Unit> io2 = io1.flatMap(u -> IO.fromRunnable(() -> counter.incrementAndGet()));

      assertThat(counter.get()).isZero();

      io2.unsafeRunSync();

      assertThat(counter.get()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Empty fromRunnable")
    void emptyFromRunnable() {
      IO<Unit> io = IO.fromRunnable(() -> {});

      assertThatIO(io).hasValue(Unit.INSTANCE);
    }

    @Test
    @DisplayName("asUnit on IO that throws")
    void asUnitOnIOThatThrows() {
      RuntimeException exception = new RuntimeException("Error");
      IO<Integer> failing =
          IO.delay(
              () -> {
                throw exception;
              });

      IO<Unit> unitIO = failing.asUnit();

      assertThatIO(unitIO).throwsException(RuntimeException.class).withMessage("Error");
    }

    @Test
    @DisplayName("fromRunnable that modifies external state")
    void fromRunnableThatModifiesExternalState() {
      AtomicReference<String> state = new AtomicReference<>("initial");

      IO<Unit> modifier =
          IO.fromRunnable(
              () -> {
                String current = state.get();
                state.set(current + "-modified");
              });

      modifier.unsafeRunSync();
      assertThat(state.get()).isEqualTo("initial-modified");

      modifier.unsafeRunSync();
      assertThat(state.get()).isEqualTo("initial-modified-modified");
    }

    @Test
    @DisplayName("Long chain of asUnit operations")
    void longChainOfAsUnitOperations() {
      AtomicInteger counter = new AtomicInteger(0);

      IO<Unit> io =
          IO.delay(() -> counter.incrementAndGet())
              .asUnit()
              .flatMap(u -> IO.delay(() -> counter.incrementAndGet()).asUnit())
              .flatMap(u -> IO.delay(() -> counter.incrementAndGet()).asUnit())
              .flatMap(u -> IO.delay(() -> counter.incrementAndGet()).asUnit());

      assertThat(counter.get()).isZero();

      assertThatIO(io).hasValue(Unit.INSTANCE);
      assertThat(counter.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("fromRunnable with checked exception wrapper")
    void fromRunnableWithCheckedExceptionWrapper() {
      IO<Unit> io =
          IO.fromRunnable(
              () -> {
                try {
                  // Simulate checked exception
                  if (true) throw new Exception("Checked");
                } catch (Exception e) {
                  throw new RuntimeException("Wrapped: " + e.getMessage());
                }
              });

      assertThatIO(io)
          .throwsException(RuntimeException.class)
          .withMessageContaining("Wrapped: Checked");
    }
  }
}
