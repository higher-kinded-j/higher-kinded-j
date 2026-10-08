// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Test suite for ScopeJoiner - the hybrid wrapper around Java 25's StructuredTaskScope.Joiner. */
@DisplayName("ScopeJoiner<T, R> Test Suite")
class ScopeJoinerTest {

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodsTests {

    @Test
    @DisplayName("allSucceed() creates joiner that waits for all tasks")
    void allSucceedCreatesJoinerForAllTasks() {
      ScopeJoiner<String, List<String>> joiner = ScopeJoiner.allSucceed();

      assertThat(joiner).isNotNull();
      assertThat(joiner.joiner()).isNotNull();
    }

    @Test
    @DisplayName("anySucceed() creates joiner that returns first success")
    void anySucceedCreatesJoinerForFirstSuccess() {
      ScopeJoiner<String, String> joiner = ScopeJoiner.anySucceed();

      assertThat(joiner).isNotNull();
      assertThat(joiner.joiner()).isNotNull();
    }

    @Test
    @DisplayName("firstComplete() creates joiner that returns first completion")
    void firstCompleteCreatesJoinerForFirstCompletion() {
      ScopeJoiner<String, String> joiner = ScopeJoiner.firstComplete();

      assertThat(joiner).isNotNull();
      assertThat(joiner.joiner()).isNotNull();
    }

    @Test
    @DisplayName("accumulating() creates joiner that collects errors")
    void accumulatingCreatesJoinerForErrorCollection() {
      ScopeJoiner<String, Validated<List<String>, List<String>>> joiner =
          ScopeJoiner.accumulating(Throwable::getMessage);

      assertThat(joiner).isNotNull();
      assertThat(joiner.joiner()).isNotNull();
    }

    @Test
    @DisplayName("accumulating() validates non-null errorMapper")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void accumulatingValidatesNonNullErrorMapper() {
      assertThatNullPointerException()
          .isThrownBy(() -> ScopeJoiner.accumulating(null))
          .withMessageContaining("errorMapper must not be null");
    }
  }

  @Nested
  @DisplayName("AllSucceed Joiner")
  class AllSucceedJoinerTests {

    @Test
    @DisplayName("collects all successful results")
    @SuppressWarnings("preview")
    void collectsAllSuccessfulResults() throws Throwable {
      ScopeJoiner<String, List<String>> joiner = ScopeJoiner.allSucceed();

      try (var scope = StructuredTaskScope.open(joiner.joiner())) {
        scope.fork(() -> "first");
        scope.fork(() -> "second");
        scope.fork(() -> "third");

        List<String> result = scope.join();

        assertThat(result).containsExactlyInAnyOrder("first", "second", "third");
      }
    }

    @Test
    @DisplayName("fails if any task fails")
    @SuppressWarnings("preview")
    void failsIfAnyTaskFails() {
      ScopeJoiner<String, List<String>> joiner = ScopeJoiner.allSucceed();

      assertThatThrownBy(
              () -> {
                try (var scope = StructuredTaskScope.open(joiner.joiner())) {
                  scope.fork(() -> "success");
                  scope.fork(
                      () -> {
                        throw new RuntimeException("task failed");
                      });

                  scope.join();
                }
              })
          .isInstanceOf(StructuredTaskScope.FailedException.class);
    }
  }

  @Nested
  @DisplayName("AnySucceed Joiner")
  class AnySucceedJoinerTests {

    @Test
    @DisplayName("returns first successful result")
    @SuppressWarnings("preview")
    void returnsFirstSuccessfulResult() throws Throwable {
      ScopeJoiner<String, String> joiner = ScopeJoiner.anySucceed();

      try (var scope = StructuredTaskScope.open(joiner.joiner())) {
        scope.fork(() -> "first");
        scope.fork(
            () -> {
              Thread.sleep(1000);
              return "slow";
            });

        String result = scope.join();

        // Should get the fast result
        assertThat(result).isEqualTo("first");
      }
    }

    @Test
    @DisplayName("fails if all tasks fail")
    @SuppressWarnings("preview")
    void failsIfAllTasksFail() {
      ScopeJoiner<String, String> joiner = ScopeJoiner.anySucceed();

      assertThatThrownBy(
              () -> {
                try (var scope = StructuredTaskScope.open(joiner.joiner())) {
                  scope.fork(
                      () -> {
                        throw new RuntimeException("error1");
                      });
                  scope.fork(
                      () -> {
                        throw new RuntimeException("error2");
                      });

                  scope.join();
                }
              })
          .isInstanceOf(StructuredTaskScope.FailedException.class);
    }
  }

  @Nested
  @DisplayName("FirstComplete Joiner")
  class FirstCompleteJoinerTests {

    @Test
    @DisplayName("returns first completed result (success)")
    @SuppressWarnings("preview")
    void returnsFirstCompletedSuccess() throws Throwable {
      ScopeJoiner<String, String> joiner = ScopeJoiner.firstComplete();

      try (var scope = StructuredTaskScope.open(joiner.joiner())) {
        scope.fork(() -> "fast");
        scope.fork(
            () -> {
              Thread.sleep(1000);
              return "slow";
            });

        String result = scope.join();

        assertThat(result).isEqualTo("fast");
      }
    }

    @Test
    @DisplayName("returns first completed result (failure)")
    @SuppressWarnings("preview")
    void returnsFirstCompletedFailure() {
      ScopeJoiner<String, String> joiner = ScopeJoiner.firstComplete();

      assertThatThrownBy(
              () -> {
                try (var scope = StructuredTaskScope.open(joiner.joiner())) {
                  scope.fork(
                      () -> {
                        throw new RuntimeException("fast failure");
                      });
                  scope.fork(
                      () -> {
                        Thread.sleep(1000);
                        return "slow success";
                      });

                  scope.join();
                }
              })
          .isInstanceOf(StructuredTaskScope.FailedException.class)
          .hasCauseInstanceOf(RuntimeException.class)
          .hasMessageContaining("fast failure");
    }

    @Test
    @DisplayName("throws if no subtask completed")
    @SuppressWarnings("preview")
    void throwsIfNoSubtaskCompleted() {
      ScopeJoiner<String, String> joiner = ScopeJoiner.firstComplete();

      assertThatThrownBy(
              () -> {
                try (var scope = StructuredTaskScope.open(joiner.joiner())) {
                  // No tasks forked
                  scope.join();
                }
              })
          .isInstanceOf(StructuredTaskScope.FailedException.class)
          .hasCauseInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No subtask completed");
    }

    @Test
    @DisplayName("result() returns success value directly")
    @SuppressWarnings("preview")
    void resultReturnsSuccessValueDirectly() throws Throwable {
      StructuredTaskScope.Joiner<String, String> joiner =
          ScopeJoiner.<String>firstComplete().joiner();

      try (var scope = StructuredTaskScope.open(joiner)) {
        scope.fork(() -> "result");
        scope.join();
      }

      // Call result() directly on the Joiner the scope used
      String result = joiner.result();
      assertThat(result).isEqualTo("result");
    }

    @Test
    @DisplayName("result() throws on failure")
    @SuppressWarnings("preview")
    void resultThrowsOnFailure() {
      StructuredTaskScope.Joiner<String, String> joiner =
          ScopeJoiner.<String>firstComplete().joiner();

      try (var scope = StructuredTaskScope.open(joiner)) {
        scope.fork(
            () -> {
              throw new RuntimeException("task failed");
            });
        try {
          scope.join();
        } catch (Exception e) {
          // Expected
        }
      }

      // Call result() directly on the Joiner the scope used - should throw
      assertThatThrownBy(joiner::result)
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("task failed");
    }

    @Test
    @DisplayName("the first completion cancels the scope, and a later one does not displace it")
    @SuppressWarnings("preview")
    void firstCompletionCancelsTheScope() throws Throwable {
      // Subtask is a sealed type, so we obtain real, already-completed ones from a throwaway,
      // fully-joined scope rather than stubbing them.
      StructuredTaskScope.Subtask<String> first;
      StructuredTaskScope.Subtask<String> later;
      try (var scope = StructuredTaskScope.open(ScopeJoiner.<String>allSucceed().joiner())) {
        first = scope.fork(() -> "winner");
        later = scope.fork(() -> "too late");
        scope.join();
      }

      StructuredTaskScope.Joiner<String, String> joiner =
          ScopeJoiner.<String>firstComplete().joiner();

      // Returning true is how a Joiner cancels its scope.
      assertThat(joiner.onComplete(first)).isTrue();
      // A completion racing the cancellation finds the winner already set. This deterministically
      // exercises the CAS-false branch that a live race hits only intermittently.
      assertThat(joiner.onComplete(later)).isFalse();
      assertThat(joiner.result()).isEqualTo("winner");
    }
  }

  @Nested
  @DisplayName("FirstSuccessEither Joiner")
  class FirstSuccessEitherJoinerTests {

    @Test
    @DisplayName("a second successful subtask does not overwrite the winner (deterministic)")
    @SuppressWarnings("preview")
    void secondSuccessDoesNotOverwriteWinner() throws Throwable {
      // Subtask is a sealed type, so we obtain real, already-completed Rights from a throwaway,
      // fully-joined scope rather than stubbing them.
      StructuredTaskScope.Subtask<Either<String, String>> won;
      StructuredTaskScope.Subtask<Either<String, String>> runnerUp;
      try (var scope =
          StructuredTaskScope.open(ScopeJoiner.<Either<String, String>>allSucceed().joiner())) {
        won = scope.fork(() -> Either.<String, String>right("won"));
        runnerUp = scope.fork(() -> Either.<String, String>right("late"));
        scope.join();
      }

      StructuredTaskScope.Joiner<Either<String, String>, Either<List<String>, String>> joiner =
          ScopeJoiner.<String, String>firstSuccessEither().joiner();

      // The first success wins the winner CAS.
      assertThat(joiner.onComplete(won)).isTrue();
      // A second success finds the winner already set: the compareAndSet fails, so it does not
      // win. This deterministically exercises the CAS-false branch that a live race hits only
      // intermittently (previously flaky coverage).
      assertThat(joiner.onComplete(runnerUp)).isFalse();
      // The original winner still stands.
      assertThat(joiner.result()).isEqualTo(Either.right("won"));
    }
  }

  @Nested
  @DisplayName("Accumulating Joiner")
  class AccumulatingJoinerTests {

    @Test
    @DisplayName("returns Valid when all tasks succeed")
    @SuppressWarnings("preview")
    void returnsValidWhenAllSucceed() throws Throwable {
      ScopeJoiner<String, Validated<List<String>, List<String>>> joiner =
          ScopeJoiner.accumulating(Throwable::getMessage);

      try (var scope = StructuredTaskScope.open(joiner.joiner())) {
        scope.fork(() -> "first");
        scope.fork(() -> "second");

        Validated<List<String>, List<String>> result = scope.join();

        assertThat(result.isValid()).isTrue();
        assertThat(result.get()).containsExactlyInAnyOrder("first", "second");
      }
    }

    @Test
    @DisplayName("returns Invalid with all errors when some tasks fail")
    @SuppressWarnings("preview")
    void returnsInvalidWithAllErrors() throws Throwable {
      ScopeJoiner<String, Validated<List<String>, List<String>>> joiner =
          ScopeJoiner.accumulating(Throwable::getMessage);

      try (var scope = StructuredTaskScope.open(joiner.joiner())) {
        scope.fork(() -> "success");
        scope.fork(
            () -> {
              throw new RuntimeException("error1");
            });
        scope.fork(
            () -> {
              throw new RuntimeException("error2");
            });

        Validated<List<String>, List<String>> result = scope.join();

        assertThat(result.isInvalid()).isTrue();
        assertThat(result.getError()).containsExactlyInAnyOrder("error1", "error2");
      }
    }

    @Test
    @DisplayName("returns Invalid when all tasks fail")
    @SuppressWarnings("preview")
    void returnsInvalidWhenAllTasksFail() throws Throwable {
      ScopeJoiner<String, Validated<List<String>, List<String>>> joiner =
          ScopeJoiner.accumulating(Throwable::getMessage);

      try (var scope = StructuredTaskScope.open(joiner.joiner())) {
        scope.fork(
            () -> {
              throw new RuntimeException("error1");
            });
        scope.fork(
            () -> {
              throw new RuntimeException("error2");
            });

        Validated<List<String>, List<String>> result = scope.join();

        assertThat(result.isInvalid()).isTrue();
        assertThat(result.getError()).containsExactlyInAnyOrder("error1", "error2");
      }
    }

    @Test
    @DisplayName("applies error mapper to exceptions")
    @SuppressWarnings("preview")
    void appliesErrorMapperToExceptions() throws Throwable {
      record ErrorInfo(String message, String type) {}

      ScopeJoiner<String, Validated<List<ErrorInfo>, List<String>>> joiner =
          ScopeJoiner.accumulating(
              t -> new ErrorInfo(t.getMessage(), t.getClass().getSimpleName()));

      try (var scope = StructuredTaskScope.open(joiner.joiner())) {
        scope.fork(
            () -> {
              throw new IllegalArgumentException("bad arg");
            });

        Validated<List<ErrorInfo>, List<String>> result = scope.join();

        assertThat(result.isInvalid()).isTrue();
        assertThat(result.getError())
            .hasSize(1)
            .first()
            .satisfies(
                err -> {
                  assertThat(err.message()).isEqualTo("bad arg");
                  assertThat(err.type()).isEqualTo("IllegalArgumentException");
                });
      }
    }

    @Test
    @DisplayName("result() returns Valid directly when called on joiner")
    @SuppressWarnings("preview")
    void resultReturnsValidDirectly() throws Throwable {
      StructuredTaskScope.Joiner<String, Validated<List<String>, List<String>>> joiner =
          ScopeJoiner.<String, String>accumulating(Throwable::getMessage).joiner();

      try (var scope = StructuredTaskScope.open(joiner)) {
        scope.fork(() -> "value");
        scope.join();
      }

      // Call result() directly on the Joiner the scope used
      Validated<List<String>, List<String>> result = joiner.result();
      assertThat(result.isValid()).isTrue();
      assertThat(result.get()).containsExactly("value");
    }

    @Test
    @DisplayName("result() returns Invalid directly when called on joiner")
    @SuppressWarnings("preview")
    void resultReturnsInvalidDirectly() throws Throwable {
      StructuredTaskScope.Joiner<String, Validated<List<String>, List<String>>> joiner =
          ScopeJoiner.<String, String>accumulating(Throwable::getMessage).joiner();

      try (var scope = StructuredTaskScope.open(joiner)) {
        scope.fork(
            () -> {
              throw new RuntimeException("error");
            });
        scope.join();
      }

      // Call result() directly on the Joiner the scope used
      Validated<List<String>, List<String>> result = joiner.result();
      assertThat(result.isInvalid()).isTrue();
      assertThat(result.getError()).containsExactly("error");
    }
  }

  @Nested
  @DisplayName("ResultEither Method")
  @SuppressWarnings("removal") // exercises the deprecated method until its removal
  class ResultEitherTests {

    @Test
    @DisplayName("returns Right on success")
    @SuppressWarnings("preview")
    void returnsRightOnSuccess() throws Throwable {
      ScopeJoiner<String, List<String>> joiner = ScopeJoiner.allSucceed();

      try (var scope = StructuredTaskScope.open(joiner.joiner())) {
        scope.fork(() -> "test");
        scope.join();
      }

      Either<Throwable, List<String>> result = joiner.resultEither();

      assertThat(result.isRight()).isTrue();
    }

    @Test
    @DisplayName("returns Left on failure")
    @SuppressWarnings("preview")
    void returnsLeftOnFailure() {
      ScopeJoiner<String, List<String>> joiner = ScopeJoiner.allSucceed();

      try (var scope = StructuredTaskScope.open(joiner.joiner())) {
        scope.fork(
            () -> {
              throw new RuntimeException("task error");
            });
        try {
          scope.join();
        } catch (Exception e) {
          // Expected
        }
      }

      Either<Throwable, List<String>> result = joiner.resultEither();

      assertThat(result.isLeft()).isTrue();
    }

    @Test
    @DisplayName("accumulating joiner resultEither returns Right with valid")
    @SuppressWarnings({"preview", "DataFlowIssue"}) // non-null in this fixture
    void accumulatingResultEitherReturnsRightOnSuccess() throws Throwable {
      ScopeJoiner<String, Validated<List<String>, List<String>>> joiner =
          ScopeJoiner.accumulating(Throwable::getMessage);

      try (var scope = StructuredTaskScope.open(joiner.joiner())) {
        scope.fork(() -> "test");
        scope.join();
      }

      Either<Throwable, Validated<List<String>, List<String>>> result = joiner.resultEither();

      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight().isValid()).isTrue();
    }

    @Test
    @DisplayName("reads the Joiner handed out most recently")
    @SuppressWarnings("preview")
    void readsTheJoinerHandedOutMostRecently() throws InterruptedException {
      ScopeJoiner<String, List<String>> joiner = ScopeJoiner.allSucceed();

      try (var scope = StructuredTaskScope.open(joiner.joiner())) {
        scope.fork(() -> "earlier");
        scope.join();
      }
      try (var scope = StructuredTaskScope.open(joiner.joiner())) {
        scope.fork(() -> "latest");
        scope.join();
      }

      assertThat(joiner.resultEither().getRight()).containsExactly("latest");
    }

    @Test
    @DisplayName("reads an unused Joiner before joiner() is called")
    void readsAnUnusedJoinerBeforeJoinerIsCalled() {
      assertThat(ScopeJoiner.<String>allSucceed().resultEither().getRight()).isEmpty();
    }
  }

  @Nested
  @DisplayName("One ScopeJoiner, many scopes")
  class OneScopeJoinerManyScopesTests {

    @Test
    @DisplayName("joiner() returns a new Joiner on each call")
    void joinerReturnsANewJoinerOnEachCall() {
      List<ScopeJoiner<?, ?>> joiners =
          List.of(
              ScopeJoiner.allSucceed(),
              ScopeJoiner.anySucceed(),
              ScopeJoiner.firstComplete(),
              ScopeJoiner.accumulating(Throwable::getMessage),
              ScopeJoiner.firstSuccessEither());

      for (ScopeJoiner<?, ?> joiner : joiners) {
        assertThat(joiner.joiner()).isNotSameAs(joiner.joiner());
      }
    }

    @Test
    @DisplayName("two scopes opened from one ScopeJoiner each join their own subtasks")
    @SuppressWarnings("preview")
    void twoScopesEachJoinTheirOwnSubtasks() throws InterruptedException {
      ScopeJoiner<String, List<String>> joiner = ScopeJoiner.allSucceed();
      List<String> first;
      List<String> second;

      try (var scope = StructuredTaskScope.open(joiner.joiner())) {
        scope.fork(() -> "first");
        first = scope.join();
      }
      try (var scope = StructuredTaskScope.open(joiner.joiner())) {
        scope.fork(() -> "second");
        second = scope.join();
      }

      assertThat(first).containsExactly("first");
      assertThat(second).containsExactly("second");
    }
  }
}
