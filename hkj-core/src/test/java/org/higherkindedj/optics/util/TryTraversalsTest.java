// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TryTraversals Utility Class Tests")
class TryTraversalsTest {

  @Nested
  @DisplayName("success() - Traversal for Success case")
  class SuccessTraversal {
    private final Traversal<Try<Integer>, Integer> traversal = TryTraversals.success();

    @Test
    @DisplayName("should modify value in Success")
    void shouldModifySuccess() {
      Try<Integer> tryValue = Try.success(42);
      Try<Integer> result = Traversals.modify(traversal, i -> i * 2, tryValue);
      assertThat(result.isSuccess()).isTrue();
      Integer value =
          result.fold(
              v -> v,
              error -> {
                throw new AssertionError("Expected success", error);
              });
      assertThat(value).isEqualTo(84);
    }

    @Test
    @DisplayName("should not modify Failure")
    void shouldNotModifyFailure() {
      Exception error = new Exception("error");
      Try<Integer> tryValue = Try.failure(error);
      Try<Integer> result = Traversals.modify(traversal, i -> i * 2, tryValue);
      assertThat(result.isFailure()).isTrue();
      Throwable actual =
          result.fold(
              success -> {
                throw new AssertionError();
              },
              failure -> failure);
      assertThat(actual).isEqualTo(error);
    }

    @Test
    @DisplayName("should extract value from Success")
    void shouldExtractFromSuccess() {
      Try<Integer> tryValue = Try.success(42);
      List<Integer> result = Traversals.getAll(traversal, tryValue);
      assertThat(result).containsExactly(42);
    }

    @Test
    @DisplayName("should return empty list for Failure")
    void shouldReturnEmptyForFailure() {
      Try<Integer> tryValue = Try.failure(new Exception("error"));
      List<Integer> result = Traversals.getAll(traversal, tryValue);
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("failure() - Traversal for Failure case")
  class FailureTraversal {
    private final Traversal<Try<Integer>, Throwable> traversal = TryTraversals.failure();

    @Test
    @DisplayName("should modify exception in Failure")
    void shouldModifyFailure() {
      Exception error = new Exception("error");
      Try<Integer> tryValue = Try.failure(error);
      Try<Integer> result =
          Traversals.modify(
              traversal, ex -> new RuntimeException("Wrapped: " + ex.getMessage(), ex), tryValue);
      assertThat(result.isFailure()).isTrue();
      Throwable exception =
          result.fold(
              success -> {
                throw new AssertionError();
              },
              failure -> failure);
      assertThat(exception).isInstanceOf(RuntimeException.class);
      assertThat(exception.getMessage()).isEqualTo("Wrapped: error");
    }

    @Test
    @DisplayName("should not modify Success")
    void shouldNotModifySuccess() {
      Try<Integer> tryValue = Try.success(42);
      Try<Integer> result =
          Traversals.modify(traversal, ex -> new RuntimeException("Wrapped", ex), tryValue);
      assertThat(result.isSuccess()).isTrue();
      Integer value =
          result.fold(
              v -> v,
              error -> {
                throw new AssertionError("Expected success", error);
              });
      assertThat(value).isEqualTo(42);
    }

    @Test
    @DisplayName("should extract exception from Failure")
    void shouldExtractFromFailure() {
      Exception error = new Exception("error");
      Try<Integer> tryValue = Try.failure(error);
      List<Throwable> result = Traversals.getAll(traversal, tryValue);
      assertThat(result).containsExactly(error);
    }

    @Test
    @DisplayName("should return empty list for Success")
    void shouldReturnEmptyForSuccess() {
      Try<Integer> tryValue = Try.success(42);
      List<Throwable> result = Traversals.getAll(traversal, tryValue);
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("successPrism() - Prism for Success case")
  class SuccessPrismMethod {
    private final Prism<Try<Integer>, Integer> prism = TryTraversals.successPrism();

    @Test
    @DisplayName("should extract value from Success")
    void shouldExtractFromSuccess() {
      Try<Integer> tryValue = Try.success(42);
      Optional<Integer> result = prism.getOptional(tryValue);
      assertThat(result).isPresent().contains(42);
    }

    @Test
    @DisplayName("should return empty for Failure")
    void shouldReturnEmptyForFailure() {
      Try<Integer> tryValue = Try.failure(new Exception("error"));
      Optional<Integer> result = prism.getOptional(tryValue);
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("failurePrism() - Prism for Failure case")
  class FailurePrismMethod {
    private final Prism<Try<Integer>, Throwable> prism = TryTraversals.failurePrism();

    @Test
    @DisplayName("should extract exception from Failure")
    void shouldExtractFromFailure() {
      Exception error = new Exception("error");
      Try<Integer> tryValue = Try.failure(error);
      Optional<Throwable> result = prism.getOptional(tryValue);
      assertThat(result).isPresent().contains(error);
    }

    @Test
    @DisplayName("should return empty for Success")
    void shouldReturnEmptyForSuccess() {
      Try<Integer> tryValue = Try.success(42);
      Optional<Throwable> result = prism.getOptional(tryValue);
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {
    @Test
    @DisplayName("should collect all successful results from list")
    void shouldCollectAllSuccesses() {
      List<Try<Integer>> tries =
          List.of(Try.success(42), Try.failure(new Exception("error")), Try.success(100));
      Traversal<List<Try<Integer>>, Integer> allSuccesses =
          Traversals.<Try<Integer>>forList().andThen(TryTraversals.success());

      List<Integer> values = Traversals.getAll(allSuccesses, tries);
      assertThat(values).containsExactly(42, 100);
    }

    @Test
    @DisplayName("should collect all exceptions from list")
    void shouldCollectAllExceptions() {
      Exception error1 = new Exception("error1");
      Exception error2 = new Exception("error2");
      List<Try<Integer>> tries = List.of(Try.success(42), Try.failure(error1), Try.failure(error2));
      Traversal<List<Try<Integer>>, Throwable> allFailures =
          Traversals.<Try<Integer>>forList().andThen(TryTraversals.failure());

      List<Throwable> exceptions = Traversals.getAll(allFailures, tries);
      assertThat(exceptions).containsExactly(error1, error2);
    }

    @Test
    @DisplayName("should modify all successful values")
    void shouldModifyAllSuccesses() {
      List<Try<Integer>> tries =
          List.of(Try.success(1), Try.failure(new Exception("error")), Try.success(2));
      Traversal<List<Try<Integer>>, Integer> allSuccesses =
          Traversals.<Try<Integer>>forList().andThen(TryTraversals.success());

      List<Try<Integer>> result = Traversals.modify(allSuccesses, i -> i * 10, tries);
      Integer value0 =
          result
              .get(0)
              .fold(
                  v -> v,
                  error -> {
                    throw new AssertionError("Expected success", error);
                  });
      assertThat(value0).isEqualTo(10);
      assertThat(result.get(1).isFailure()).isTrue();
      Integer value2 =
          result
              .get(2)
              .fold(
                  v -> v,
                  error -> {
                    throw new AssertionError("Expected success", error);
                  });
      assertThat(value2).isEqualTo(20);
    }

    @Test
    @DisplayName("should wrap all exceptions")
    void shouldWrapAllExceptions() {
      Exception error1 = new Exception("error1");
      Exception error2 = new Exception("error2");
      List<Try<Integer>> tries = List.of(Try.failure(error1), Try.success(42), Try.failure(error2));
      Traversal<List<Try<Integer>>, Throwable> allFailures =
          Traversals.<Try<Integer>>forList().andThen(TryTraversals.failure());

      List<Try<Integer>> result =
          Traversals.modify(
              allFailures, ex -> new RuntimeException("Wrapped: " + ex.getMessage(), ex), tries);
      Throwable exception0 =
          result
              .get(0)
              .fold(
                  success -> {
                    throw new AssertionError();
                  },
                  failure -> failure);
      assertThat(exception0).isInstanceOf(RuntimeException.class);
      assertThat(exception0.getMessage()).isEqualTo("Wrapped: error1");
      Integer value1 =
          result
              .get(1)
              .fold(
                  v -> v,
                  error -> {
                    throw new AssertionError("Expected success", error);
                  });
      assertThat(value1).isEqualTo(42);
      Throwable exception2 =
          result
              .get(2)
              .fold(
                  success -> {
                    throw new AssertionError();
                  },
                  failure -> failure);
      assertThat(exception2).isInstanceOf(RuntimeException.class);
      assertThat(exception2.getMessage()).isEqualTo("Wrapped: error2");
    }
  }
}
