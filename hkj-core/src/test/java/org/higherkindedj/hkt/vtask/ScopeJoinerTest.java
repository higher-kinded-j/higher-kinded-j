// Copyright (c) 2025 Magnus Smith
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
  }

  @Nested
  @DisplayName("ResultEither Method")
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
  }
}
