// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.resilience;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link Saga} and {@link SagaBuilder}.
 *
 * <p>Tests cover successful execution, compensation on failure, compensation ordering, partial
 * compensation failure, builder functionality, async compensation, and map/flatMap/andThen
 * composition.
 */
@DisplayName("Saga Test Suite")
class SagaTest {

  // ===== Helpers =====

  private static final RuntimeException STEP_FAILURE = new RuntimeException("step failed");

  private static final RuntimeException COMPENSATION_FAILURE =
      new RuntimeException("compensation failed");

  @Nested
  @DisplayName("Successful Execution Tests")
  class SuccessfulExecutionTests {

    @Test
    @DisplayName("Single step saga succeeds with correct result")
    void singleStepSagaSucceeds() {
      AtomicInteger compensated = new AtomicInteger(0);

      Saga<String> saga =
          Saga.of(
              VTask.of(() -> "hello"), (Consumer<String>) result -> compensated.incrementAndGet());

      String result = saga.run().run();

      assertThat(result).isEqualTo("hello");
      assertThat(compensated.get()).isZero();
    }

    @Test
    @DisplayName("Multi-step saga succeeds with final result")
    void multiStepSagaSucceeds() {
      CopyOnWriteArrayList<String> compensated = new CopyOnWriteArrayList<>();

      Saga<String> saga =
          Saga.of(VTask.of(() -> 1), (Consumer<Integer>) v -> compensated.add("step1"))
              .andThen(
                  a ->
                      Saga.of(
                          VTask.of(() -> a + 1), (Consumer<Integer>) v -> compensated.add("step2")))
              .andThen(
                  a ->
                      Saga.of(
                          VTask.of(() -> "result=" + a),
                          (Consumer<String>) v -> compensated.add("step3")));

      String result = saga.run().run();

      assertThat(result).isEqualTo("result=2");
      assertThat(compensated).isEmpty();
    }

    @Test
    @DisplayName("runSafe returns Right on success")
    void runSafeReturnsRightOnSuccess() {
      Saga<Integer> saga = Saga.of(VTask.of(() -> 42), (Consumer<Integer>) v -> {});

      Either<SagaError, Integer> result = saga.runSafe().run();

      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("noCompensation saga succeeds")
    void noCompensationSagaSucceeds() {
      Saga<String> saga = Saga.noCompensation(VTask.of(() -> "no-comp"));

      String result = saga.run().run();

      assertThat(result).isEqualTo("no-comp");
    }

    @Test
    @DisplayName("Saga with all steps passing does not invoke any compensation")
    void allStepsPassNoCompensation() {
      AtomicInteger compensationCount = new AtomicInteger(0);

      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .step("step-a", VTask.of(() -> "a"), v -> compensationCount.incrementAndGet())
              .step(
                  "step-b",
                  prev -> VTask.of(() -> prev + "b"),
                  v -> compensationCount.incrementAndGet())
              .step(
                  "step-c",
                  prev -> VTask.of(() -> prev + "c"),
                  v -> compensationCount.incrementAndGet())
              .build();

      String result = saga.run().run();

      assertThat(result).isEqualTo("abc");
      assertThat(compensationCount.get()).isZero();
    }
  }

  @Nested
  @DisplayName("Compensation Tests")
  class CompensationTests {

    @Test
    @DisplayName("Compensation runs when second step fails")
    void compensationRunsWhenSecondStepFails() {
      CopyOnWriteArrayList<String> compensated = new CopyOnWriteArrayList<>();

      Saga<String> saga =
          Saga.of(VTask.of(() -> "first"), (Consumer<String>) v -> compensated.add("comp-first"))
              .andThen(
                  a ->
                      Saga.of(
                          VTask.fail(STEP_FAILURE),
                          (Consumer<String>) v -> compensated.add("comp-second")));

      assertThatThrownBy(() -> saga.run().run()).isSameAs(STEP_FAILURE);

      assertThat(compensated).containsExactly("comp-first");
    }

    @Test
    @DisplayName("Compensation runs when third step fails, compensating first two in reverse")
    void compensationRunsInReverseForThreeSteps() {
      CopyOnWriteArrayList<String> compensated = new CopyOnWriteArrayList<>();

      Saga<String> saga =
          Saga.of(VTask.of(() -> "a"), (Consumer<String>) v -> compensated.add("comp-1"))
              .andThen(
                  a ->
                      Saga.of(
                          VTask.of(() -> "b"), (Consumer<String>) v -> compensated.add("comp-2")))
              .andThen(
                  b ->
                      Saga.of(
                          VTask.<String>fail(STEP_FAILURE),
                          (Consumer<String>) v -> compensated.add("comp-3")));

      assertThatThrownBy(() -> saga.run().run()).isSameAs(STEP_FAILURE);

      // Step 3 failed so it is NOT compensated; steps 2 and 1 are compensated in reverse
      assertThat(compensated).containsExactly("comp-2", "comp-1");
    }

    @Test
    @DisplayName("runSafe returns Left with SagaError on failure")
    void runSafeReturnsLeftOnFailure() {
      Saga<String> saga =
          Saga.of(VTask.of(() -> "ok"), (Consumer<String>) v -> {})
              .andThen(a -> Saga.of(VTask.<String>fail(STEP_FAILURE), (Consumer<String>) v -> {}));

      Either<SagaError, String> result = saga.runSafe().run();

      assertThat(result.isLeft()).isTrue();
      SagaError error = result.getLeft();
      assertThat(error.originalError()).isSameAs(STEP_FAILURE);
      assertThat(error.allCompensationsSucceeded()).isTrue();
    }

    @Test
    @DisplayName("First step failure does not compensate anything")
    void firstStepFailureNoCompensation() {
      AtomicInteger compensationCount = new AtomicInteger(0);

      Saga<String> saga =
          Saga.of(
              VTask.<String>fail(STEP_FAILURE),
              (Consumer<String>) v -> compensationCount.incrementAndGet());

      assertThatThrownBy(() -> saga.run().run()).isSameAs(STEP_FAILURE);

      assertThat(compensationCount.get()).isZero();
    }

    @Test
    @DisplayName("Compensation receives the correct value from the forward action")
    void compensationReceivesCorrectValue() {
      CopyOnWriteArrayList<String> compensatedValues = new CopyOnWriteArrayList<>();

      Saga<String> saga =
          Saga.of(VTask.of(() -> "payment-123"), (Consumer<String>) compensatedValues::add)
              .andThen(
                  paymentId ->
                      Saga.of(VTask.<String>fail(STEP_FAILURE), (Consumer<String>) v -> {}));

      assertThatThrownBy(() -> saga.run().run()).isSameAs(STEP_FAILURE);

      assertThat(compensatedValues).containsExactly("payment-123");
    }
  }

  @Nested
  @DisplayName("Compensation Order Tests")
  class CompensationOrderTests {

    @Test
    @DisplayName("Compensations execute in strict reverse order")
    void compensationsExecuteInStrictReverseOrder() {
      CopyOnWriteArrayList<Integer> order = new CopyOnWriteArrayList<>();

      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .step("step-1", VTask.of(() -> "a"), v -> order.add(1))
              .step("step-2", prev -> VTask.of(() -> "b"), v -> order.add(2))
              .step("step-3", prev -> VTask.of(() -> "c"), v -> order.add(3))
              .step("step-4", prev -> VTask.<String>fail(STEP_FAILURE), v -> order.add(4))
              .build();

      assertThatThrownBy(() -> saga.run().run()).isSameAs(STEP_FAILURE);

      // Step 4 failed so not compensated; steps 3, 2, 1 compensated in reverse
      assertThat(order).containsExactly(3, 2, 1);
    }

    @Test
    @DisplayName("Five step saga compensates in reverse when last step fails")
    void fiveStepSagaCompensatesInReverse() {
      CopyOnWriteArrayList<String> order = new CopyOnWriteArrayList<>();
      AtomicInteger stepCounter = new AtomicInteger(0);

      Saga<Integer> saga =
          Saga.of(
                  VTask.of(() -> stepCounter.incrementAndGet()),
                  (Consumer<Integer>) v -> order.add("comp-1"))
              .andThen(
                  a ->
                      Saga.of(
                          VTask.of(() -> stepCounter.incrementAndGet()),
                          (Consumer<Integer>) v -> order.add("comp-2")))
              .andThen(
                  a ->
                      Saga.of(
                          VTask.of(() -> stepCounter.incrementAndGet()),
                          (Consumer<Integer>) v -> order.add("comp-3")))
              .andThen(
                  a ->
                      Saga.of(
                          VTask.of(() -> stepCounter.incrementAndGet()),
                          (Consumer<Integer>) v -> order.add("comp-4")))
              .andThen(
                  a ->
                      Saga.of(
                          VTask.<Integer>fail(STEP_FAILURE),
                          (Consumer<Integer>) v -> order.add("comp-5")));

      assertThatThrownBy(() -> saga.run().run()).isSameAs(STEP_FAILURE);

      assertThat(stepCounter.get()).isEqualTo(4);
      assertThat(order).containsExactly("comp-4", "comp-3", "comp-2", "comp-1");
    }
  }

  @Nested
  @DisplayName("Partial Compensation Failure Tests")
  class PartialCompensationFailureTests {

    @Test
    @DisplayName("SagaExecutionException thrown when compensation fails")
    void sagaExecutionExceptionWhenCompensationFails() {
      Saga<String> saga =
          Saga.of(
                  VTask.of(() -> "a"),
                  (Consumer<String>)
                      v -> {
                        throw COMPENSATION_FAILURE;
                      })
              .andThen(a -> Saga.of(VTask.<String>fail(STEP_FAILURE), (Consumer<String>) v -> {}));

      assertThatThrownBy(() -> saga.run().run())
          .isInstanceOf(SagaExecutionException.class)
          .satisfies(
              thrown -> {
                SagaExecutionException ex = (SagaExecutionException) thrown;
                SagaError error = ex.sagaError();
                assertThat(error.originalError()).isSameAs(STEP_FAILURE);
                assertThat(error.allCompensationsSucceeded()).isFalse();
                assertThat(error.compensationFailures()).hasSize(1);
                assertThat(error.compensationFailures().getFirst()).isSameAs(COMPENSATION_FAILURE);
              });
    }

    @Test
    @DisplayName("SagaError captures mixed compensation results")
    void sagaErrorCapturesMixedCompensationResults() {
      RuntimeException compFailure = new RuntimeException("comp-2 failed");

      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .step("charge", VTask.of(() -> "paid"), v -> {})
              .step(
                  "reserve",
                  prev -> VTask.of(() -> "reserved"),
                  v -> {
                    throw compFailure;
                  })
              .step("ship", prev -> VTask.of(() -> "shipped"), v -> {})
              .step("notify", prev -> VTask.<String>fail(STEP_FAILURE), v -> {})
              .build();

      Either<SagaError, String> result = saga.runSafe().run();

      assertThat(result.isLeft()).isTrue();
      SagaError error = result.getLeft();
      assertThat(error.originalError()).isSameAs(STEP_FAILURE);
      assertThat(error.allCompensationsSucceeded()).isFalse();

      List<SagaError.CompensationResult> compResults = error.compensationResults();
      // 3 completed steps compensated in reverse: ship, reserve, charge
      assertThat(compResults).hasSize(3);

      // ship compensation succeeded
      assertThat(compResults.get(0).stepName()).isEqualTo("ship");
      assertThat(compResults.get(0).result().isRight()).isTrue();

      // reserve compensation failed
      assertThat(compResults.get(1).stepName()).isEqualTo("reserve");
      assertThat(compResults.get(1).result().isLeft()).isTrue();

      // charge compensation succeeded
      assertThat(compResults.get(2).stepName()).isEqualTo("charge");
      assertThat(compResults.get(2).result().isRight()).isTrue();

      assertThat(error.compensationFailures()).containsExactly(compFailure);
    }

    @Test
    @DisplayName("All compensations fail results in multiple failures in SagaError")
    void allCompensationsFailResultsInMultipleFailures() {
      RuntimeException comp1Failure = new RuntimeException("comp-1 failed");
      RuntimeException comp2Failure = new RuntimeException("comp-2 failed");

      Saga<String> saga =
          Saga.of(
                  VTask.of(() -> "a"),
                  (Consumer<String>)
                      v -> {
                        throw comp1Failure;
                      })
              .andThen(
                  a ->
                      Saga.of(
                          VTask.of(() -> "b"),
                          (Consumer<String>)
                              v -> {
                                throw comp2Failure;
                              }))
              .andThen(b -> Saga.of(VTask.<String>fail(STEP_FAILURE), (Consumer<String>) v -> {}));

      assertThatThrownBy(() -> saga.run().run())
          .isInstanceOf(SagaExecutionException.class)
          .satisfies(
              thrown -> {
                SagaExecutionException ex = (SagaExecutionException) thrown;
                SagaError error = ex.sagaError();
                assertThat(error.compensationFailures()).hasSize(2);
                assertThat(error.compensationFailures())
                    .containsExactly(comp2Failure, comp1Failure);
              });
    }

    @Test
    @DisplayName("SagaExecutionException message includes step name and failure count")
    void sagaExecutionExceptionMessageIncludesDetails() {
      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .step(
                  "payment",
                  VTask.of(() -> "paid"),
                  v -> {
                    throw COMPENSATION_FAILURE;
                  })
              .step("shipping", prev -> VTask.<String>fail(STEP_FAILURE), v -> {})
              .build();

      assertThatThrownBy(() -> saga.run().run())
          .isInstanceOf(SagaExecutionException.class)
          .hasMessageContaining("1 compensation failure(s)")
          .hasCause(STEP_FAILURE);
    }
  }

  @Nested
  @DisplayName("Builder Tests")
  class BuilderTests {

    @Test
    @DisplayName("Empty builder throws IllegalStateException on build")
    void emptyBuilderThrowsOnBuild() {
      assertThatThrownBy(() -> SagaBuilder.<Unit>start().build())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("at least one step");
    }

    @Test
    @DisplayName("Builder with single step produces working saga")
    void builderWithSingleStep() {
      Saga<String> saga =
          SagaBuilder.<Unit>start().step("only", VTask.of(() -> "sole"), v -> {}).build();

      String result = saga.run().run();

      assertThat(result).isEqualTo("sole");
    }

    @Test
    @DisplayName("Builder step with standalone action ignores previous result")
    void builderStepWithStandaloneAction() {
      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .step("first", VTask.of(() -> "A"), v -> {})
              .step("second", VTask.of(() -> "B"), v -> {})
              .build();

      String result = saga.run().run();

      assertThat(result).isEqualTo("B");
    }

    @Test
    @DisplayName("Builder step with dependent action uses previous result")
    void builderStepWithDependentAction() {
      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .step("first", VTask.of(() -> "hello"), v -> {})
              .step("second", prev -> VTask.of(() -> prev + " world"), v -> {})
              .build();

      String result = saga.run().run();

      assertThat(result).isEqualTo("hello world");
    }

    @Test
    @DisplayName("Builder multi-step chain threads results through")
    void builderMultiStepChain() {
      Saga<Integer> saga =
          SagaBuilder.<Unit>start()
              .step("init", VTask.of(() -> 1), v -> {})
              .step("double", prev -> VTask.of(() -> prev * 2), v -> {})
              .step("add-ten", prev -> VTask.of(() -> prev + 10), v -> {})
              .build();

      Integer result = saga.run().run();

      assertThat(result).isEqualTo(12);
    }

    @Test
    @DisplayName("Builder stepNoCompensation adds step without compensation")
    void builderStepNoCompensation() {
      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .step("first", VTask.of(() -> "a"), v -> {})
              .stepNoCompensation("final", prev -> VTask.of(() -> prev + "b"))
              .build();

      String result = saga.run().run();

      assertThat(result).isEqualTo("ab");
    }

    @Test
    @DisplayName("Builder compensations run when later step fails")
    void builderCompensationsRunOnFailure() {
      CopyOnWriteArrayList<String> compensated = new CopyOnWriteArrayList<>();

      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .step("step-a", VTask.of(() -> "a"), v -> compensated.add("comp-a"))
              .step("step-b", prev -> VTask.of(() -> prev + "b"), v -> compensated.add("comp-b"))
              .step(
                  "step-c",
                  prev -> VTask.<String>fail(STEP_FAILURE),
                  v -> compensated.add("comp-c"))
              .build();

      assertThatThrownBy(() -> saga.run().run()).isSameAs(STEP_FAILURE);

      // step-c failed, so steps b and a are compensated in reverse
      assertThat(compensated).containsExactly("comp-b", "comp-a");
    }
  }

  @Nested
  @DisplayName("Async Compensation Tests")
  class AsyncCompensationTests {

    @Test
    @DisplayName("Saga.of with async compensation succeeds")
    void sagaOfWithAsyncCompensationSucceeds() {
      AtomicInteger compensated = new AtomicInteger(0);

      Saga<String> saga =
          Saga.of(
              VTask.of(() -> "async-result"),
              (String v) -> VTask.exec(() -> compensated.incrementAndGet()));

      String result = saga.run().run();

      assertThat(result).isEqualTo("async-result");
      assertThat(compensated.get()).isZero();
    }

    @Test
    @DisplayName("Async compensation runs on failure")
    void asyncCompensationRunsOnFailure() {
      CopyOnWriteArrayList<String> compensated = new CopyOnWriteArrayList<>();

      Saga<String> saga =
          Saga.of(
                  VTask.of(() -> "val"),
                  (String v) -> VTask.exec(() -> compensated.add("async-comp:" + v)))
              .andThen(a -> Saga.of(VTask.<String>fail(STEP_FAILURE), v -> {}));

      assertThatThrownBy(() -> saga.run().run()).isSameAs(STEP_FAILURE);

      assertThat(compensated).containsExactly("async-comp:val");
    }

    @Test
    @DisplayName("Builder stepAsync with VTask compensation")
    void builderStepAsyncWithVTaskCompensation() {
      CopyOnWriteArrayList<String> compensated = new CopyOnWriteArrayList<>();

      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .stepAsync(
                  "async-step",
                  prev -> VTask.of(() -> "async-val"),
                  v -> VTask.exec(() -> compensated.add("async-comp:" + v)))
              .step("failing-step", prev -> VTask.<String>fail(STEP_FAILURE), v -> {})
              .build();

      assertThatThrownBy(() -> saga.run().run()).isSameAs(STEP_FAILURE);

      assertThat(compensated).containsExactly("async-comp:async-val");
    }

    @Test
    @DisplayName("Async compensation failure produces SagaExecutionException")
    void asyncCompensationFailureProducesSagaExecutionException() {
      RuntimeException asyncCompFailure = new RuntimeException("async comp failed");

      Saga<String> saga =
          Saga.of(VTask.of(() -> "val"), (String v) -> VTask.<Unit>fail(asyncCompFailure))
              .andThen(a -> Saga.of(VTask.<String>fail(STEP_FAILURE), v -> {}));

      assertThatThrownBy(() -> saga.run().run())
          .isInstanceOf(SagaExecutionException.class)
          .satisfies(
              thrown -> {
                SagaExecutionException ex = (SagaExecutionException) thrown;
                SagaError error = ex.sagaError();
                assertThat(error.allCompensationsSucceeded()).isFalse();
                assertThat(error.compensationFailures()).hasSize(1);
                assertThat(error.compensationFailures().getFirst()).isSameAs(asyncCompFailure);
              });
    }
  }

  @Nested
  @DisplayName("Map Tests")
  class MapTests {

    @Test
    @DisplayName("map transforms the result")
    void mapTransformsResult() {
      Saga<Integer> saga =
          Saga.of(VTask.of(() -> "hello"), (Consumer<String>) v -> {}).map(String::length);

      Integer result = saga.run().run();

      assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("map chained multiple times")
    void mapChainedMultipleTimes() {
      Saga<String> saga =
          Saga.of(VTask.of(() -> 10), (Consumer<Integer>) v -> {})
              .map(n -> n * 2)
              .map(n -> "result=" + n);

      String result = saga.run().run();

      assertThat(result).isEqualTo("result=20");
    }

    @Test
    @DisplayName("flatMap chains sagas")
    void flatMapChainsSagas() {
      CopyOnWriteArrayList<String> compensated = new CopyOnWriteArrayList<>();

      Saga<String> saga =
          Saga.of(VTask.of(() -> "a"), (Consumer<String>) v -> compensated.add("comp-a"))
              .flatMap(
                  a ->
                      Saga.of(
                          VTask.of(() -> a + "b"),
                          (Consumer<String>) v -> compensated.add("comp-b")));

      String result = saga.run().run();

      assertThat(result).isEqualTo("ab");
      assertThat(compensated).isEmpty();
    }

    @Test
    @DisplayName("flatMap triggers compensation on chained saga failure")
    void flatMapTriggersCompensationOnFailure() {
      CopyOnWriteArrayList<String> compensated = new CopyOnWriteArrayList<>();

      Saga<String> saga =
          Saga.of(VTask.of(() -> "a"), (Consumer<String>) v -> compensated.add("comp-a"))
              .flatMap(
                  a ->
                      Saga.of(
                          VTask.<String>fail(STEP_FAILURE),
                          (Consumer<String>) v -> compensated.add("comp-b")));

      assertThatThrownBy(() -> saga.run().run()).isSameAs(STEP_FAILURE);

      assertThat(compensated).containsExactly("comp-a");
    }

    @Test
    @DisplayName("andThen is equivalent to flatMap")
    void andThenIsEquivalentToFlatMap() {
      Saga<String> sagaFlatMap =
          Saga.of(VTask.of(() -> "x"), (Consumer<String>) v -> {})
              .flatMap(a -> Saga.of(VTask.of(() -> a + "y"), (Consumer<String>) v -> {}));

      Saga<String> sagaAndThen =
          Saga.of(VTask.of(() -> "x"), (Consumer<String>) v -> {})
              .andThen(a -> Saga.of(VTask.of(() -> a + "y"), (Consumer<String>) v -> {}));

      String resultFlatMap = sagaFlatMap.run().run();
      String resultAndThen = sagaAndThen.run().run();

      assertThat(resultFlatMap).isEqualTo("xy");
      assertThat(resultAndThen).isEqualTo("xy");
    }

    @Test
    @DisplayName("map preserves compensation behavior")
    void mapPreservesCompensationBehavior() {
      CopyOnWriteArrayList<String> compensated = new CopyOnWriteArrayList<>();

      Saga<String> saga =
          Saga.of(VTask.of(() -> 42), (Consumer<Integer>) v -> compensated.add("comp-" + v))
              .map(n -> "val=" + n)
              .andThen(
                  s ->
                      Saga.of(
                          VTask.<String>fail(STEP_FAILURE),
                          (Consumer<String>) v -> compensated.add("comp-last")));

      assertThatThrownBy(() -> saga.run().run()).isSameAs(STEP_FAILURE);

      assertThat(compensated).containsExactly("comp-42");
    }

    @Test
    @DisplayName("map with identity returns equivalent result")
    void mapWithIdentityReturnsEquivalent() {
      Saga<String> saga =
          Saga.of(VTask.of(() -> "identity"), (Consumer<String>) v -> {}).map(x -> x);

      String result = saga.run().run();

      assertThat(result).isEqualTo("identity");
    }
  }

  @Nested
  @DisplayName("Named Step Error Handling Tests")
  class NamedStepErrorHandlingTests {

    @Test
    @DisplayName("namedStep wraps non-RuntimeException as SagaStepFailure in error reporting")
    void namedStepWrapsCheckedException() {
      Exception checkedException = new Exception("checked error in step");

      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .<String>stepAsync(
                  "async-failing",
                  prev -> VTask.fail(checkedException),
                  v -> VTask.succeed(Unit.INSTANCE))
              .build();

      Either<SagaError, String> result = saga.runSafe().run();

      assertThat(result.isLeft()).isTrue();
      SagaError error = result.getLeft();
      assertThat(error.failedStep()).isEqualTo("async-failing");
      assertThat(error.originalError()).isSameAs(checkedException);
    }

    @Test
    @DisplayName("namedStepSync wraps non-RuntimeException as SagaStepFailure in error reporting")
    void namedStepSyncWrapsCheckedException() {
      Exception checkedException = new Exception("checked sync error");

      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .<String>step("sync-failing", VTask.fail(checkedException), v -> {})
              .build();

      Either<SagaError, String> result = saga.runSafe().run();

      assertThat(result.isLeft()).isTrue();
      SagaError error = result.getLeft();
      assertThat(error.failedStep()).isEqualTo("sync-failing");
      assertThat(error.originalError()).isSameAs(checkedException);
    }

    @Test
    @DisplayName("namedStep failure triggers compensation of prior steps")
    void namedStepFailureCompensatesPriorSteps() {
      CopyOnWriteArrayList<String> compensated = new CopyOnWriteArrayList<>();

      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .stepAsync(
                  "step-1",
                  prev -> VTask.of(() -> "first"),
                  v -> VTask.exec(() -> compensated.add("comp-1")))
              .<String>stepAsync(
                  "step-2",
                  prev -> VTask.fail(new RuntimeException("fail")),
                  v -> VTask.exec(() -> compensated.add("comp-2")))
              .build();

      assertThatThrownBy(() -> saga.run().run()).isInstanceOf(RuntimeException.class);

      assertThat(compensated).containsExactly("comp-1");
    }

    @Test
    @DisplayName("namedStepSync failure triggers compensation of prior steps")
    void namedStepSyncFailureCompensatesPriorSteps() {
      CopyOnWriteArrayList<String> compensated = new CopyOnWriteArrayList<>();

      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .step("step-1", VTask.of(() -> "first"), v -> compensated.add("comp-1"))
              .<String>step(
                  "step-2",
                  prev -> VTask.fail(new RuntimeException("fail")),
                  v -> compensated.add("comp-2"))
              .build();

      assertThatThrownBy(() -> saga.run().run()).isInstanceOf(RuntimeException.class);

      assertThat(compensated).containsExactly("comp-1");
    }

    @Test
    @DisplayName("noCompensation saga compensates with no-op when later step fails")
    void noCompensationCompensatesWithNoOpOnFailure() {
      CopyOnWriteArrayList<String> compensated = new CopyOnWriteArrayList<>();

      Saga<String> saga =
          Saga.noCompensation(VTask.of(() -> "first"))
              .andThen(
                  a ->
                      Saga.of(
                          VTask.<String>fail(STEP_FAILURE),
                          (Consumer<String>) v -> compensated.add("comp-second")));

      // The noCompensation saga's compensation lambda should run successfully (it's a no-op)
      assertThatThrownBy(() -> saga.run().run()).isSameAs(STEP_FAILURE);

      // No compensation recorded from the first step's no-op
      assertThat(compensated).isEmpty();
    }

    @Test
    @DisplayName("stepNoCompensation compensation lambda runs as no-op on failure")
    void stepNoCompensationLambdaRunsAsNoOp() {
      CopyOnWriteArrayList<String> compensated = new CopyOnWriteArrayList<>();

      Saga<String> saga =
          SagaBuilder.<Unit>start()
              .stepNoCompensation("no-comp", prev -> VTask.of(() -> "val"))
              .step(
                  "failing",
                  prev -> VTask.<String>fail(STEP_FAILURE),
                  v -> compensated.add("comp-fail"))
              .build();

      // The stepNoCompensation step's compensation is a no-op VTask that succeeds
      Either<SagaError, String> result = saga.runSafe().run();

      assertThat(result.isLeft()).isTrue();
      SagaError error = result.getLeft();
      // All compensations should succeed (the no-comp lambda is a no-op that returns Unit)
      assertThat(error.allCompensationsSucceeded()).isTrue();
      assertThat(compensated).isEmpty();
    }

    @Test
    @DisplayName("run() extracts original error from non-SagaStepFailure path")
    void runExtractsErrorFromNonSagaStepFailure() {
      // Use Saga.of directly (which uses singleStep). When the step fails with a non-Runtime
      // exception, it's wrapped in SagaStepFailure by singleStep. But we also need to test
      // the path where the caught exception is NOT SagaStepFailure (lines 218-220 in run()).
      // This happens when the saga runner itself throws a raw exception not wrapped in
      // SagaStepFailure.
      RuntimeException rawError = new RuntimeException("raw error");

      // Using map with a function that throws achieves a non-SagaStepFailure path
      // because map's runner catches no exception and just lets it propagate
      Saga<String> saga =
          Saga.of(VTask.of(() -> "ok"), (Consumer<String>) v -> {})
              .map(
                  s -> {
                    throw rawError;
                  });

      assertThatThrownBy(() -> saga.run().run()).isSameAs(rawError);
    }

    @Test
    @DisplayName("runSafe() extracts original error from non-SagaStepFailure path")
    void runSafeExtractsErrorFromNonSagaStepFailure() {
      RuntimeException rawError = new RuntimeException("raw error from map");

      Saga<String> saga =
          Saga.of(VTask.of(() -> "ok"), (Consumer<String>) v -> {})
              .map(
                  s -> {
                    throw rawError;
                  });

      Either<SagaError, String> result = saga.runSafe().run();

      assertThat(result.isLeft()).isTrue();
      SagaError error = result.getLeft();
      assertThat(error.originalError()).isSameAs(rawError);
      // The failed step name should use the "step-N" fallback format
      assertThat(error.failedStep()).startsWith("step-");
    }
  }
}
