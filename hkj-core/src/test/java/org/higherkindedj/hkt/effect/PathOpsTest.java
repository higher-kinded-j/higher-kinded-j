// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for PathOps utility class.
 *
 * <p>Tests cover sequence and traverse operations for all path types, plus utility operations like
 * firstSuccess.
 */
@DisplayName("PathOps Utility Test Suite")
class PathOpsTest {

  private static final Semigroup<List<String>> LIST_SEMIGROUP =
      (a, b) -> {
        var result = new ArrayList<>(a);
        result.addAll(b);
        return result;
      };

  @Nested
  @DisplayName("MaybePath Operations")
  class MaybePathOperationsTests {

    @Test
    @DisplayName("sequenceMaybe() converts list of Just to Just of list")
    void sequenceMaybeConvertsListOfJustToJustOfList() {
      List<MaybePath<Integer>> paths = List.of(Path.just(1), Path.just(2), Path.just(3));

      MaybePath<List<Integer>> result = PathOps.sequenceMaybe(paths);

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("sequenceMaybe() returns Nothing if any is Nothing")
    void sequenceMaybeReturnsNothingIfAnyIsNothing() {
      List<MaybePath<Integer>> paths = List.of(Path.just(1), Path.nothing(), Path.just(3));

      MaybePath<List<Integer>> result = PathOps.sequenceMaybe(paths);

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("sequenceMaybe() returns Just of empty list for empty input")
    void sequenceMaybeReturnsJustOfEmptyListForEmptyInput() {
      List<MaybePath<Integer>> paths = List.of();

      MaybePath<List<Integer>> result = PathOps.sequenceMaybe(paths);

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEmpty();
    }

    @Test
    @DisplayName("traverseMaybe() maps and sequences")
    void traverseMaybeMapsAndSequences() {
      List<String> items = List.of("1", "2", "3");

      MaybePath<List<Integer>> result =
          PathOps.traverseMaybe(items, s -> Path.just(Integer.parseInt(s)));

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("traverseMaybe() returns Nothing if any mapping fails")
    void traverseMaybeReturnsNothingIfAnyMappingFails() {
      List<String> items = List.of("1", "not-a-number", "3");

      MaybePath<List<Integer>> result =
          PathOps.traverseMaybe(
              items,
              s -> {
                try {
                  return Path.just(Integer.parseInt(s));
                } catch (NumberFormatException e) {
                  return Path.nothing();
                }
              });

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("sequenceMaybe() validates non-null paths")
    void sequenceMaybeValidatesNonNullPaths() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.sequenceMaybe(null))
          .withMessageContaining("paths must not be null");
    }
  }

  @Nested
  @DisplayName("EitherPath Operations")
  class EitherPathOperationsTests {

    @Test
    @DisplayName("sequenceEither() converts list of Right to Right of list")
    void sequenceEitherConvertsListOfRightToRightOfList() {
      List<EitherPath<String, Integer>> paths =
          List.of(Path.right(1), Path.right(2), Path.right(3));

      EitherPath<String, List<Integer>> result = PathOps.sequenceEither(paths);

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("sequenceEither() returns first Left error")
    void sequenceEitherReturnsFirstLeftError() {
      List<EitherPath<String, Integer>> paths =
          List.of(Path.right(1), Path.left("error1"), Path.left("error2"));

      EitherPath<String, List<Integer>> result = PathOps.sequenceEither(paths);

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo("error1");
    }

    @Test
    @DisplayName("sequenceEither() returns Right of empty list for empty input")
    void sequenceEitherReturnsRightOfEmptyListForEmptyInput() {
      List<EitherPath<String, Integer>> paths = List.of();

      EitherPath<String, List<Integer>> result = PathOps.sequenceEither(paths);

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEmpty();
    }

    @Test
    @DisplayName("traverseEither() maps and sequences")
    void traverseEitherMapsAndSequences() {
      List<String> items = List.of("1", "2", "3");

      EitherPath<String, List<Integer>> result =
          PathOps.traverseEither(items, s -> Path.right(Integer.parseInt(s)));

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("traverseEither() returns first error")
    void traverseEitherReturnsFirstError() {
      List<String> items = List.of("1", "not-a-number", "3");

      EitherPath<String, List<Integer>> result =
          PathOps.traverseEither(
              items,
              s -> {
                try {
                  return Path.right(Integer.parseInt(s));
                } catch (NumberFormatException e) {
                  return Path.left("Invalid: " + s);
                }
              });

      assertThat(result.run().isLeft()).isTrue();
      assertThat(result.run().getLeft()).isEqualTo("Invalid: not-a-number");
    }
  }

  @Nested
  @DisplayName("ValidationPath Operations")
  class ValidationPathOperationsTests {

    @Test
    @DisplayName("sequenceValidated() converts list of Valid to Valid of list")
    void sequenceValidatedConvertsListOfValidToValidOfList() {
      List<ValidationPath<List<String>, Integer>> paths =
          List.of(
              Path.valid(1, LIST_SEMIGROUP),
              Path.valid(2, LIST_SEMIGROUP),
              Path.valid(3, LIST_SEMIGROUP));

      ValidationPath<List<String>, List<Integer>> result =
          PathOps.sequenceValidated(paths, LIST_SEMIGROUP);

      assertThat(result.run().isValid()).isTrue();
      assertThat(result.run().get()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("sequenceValidated() accumulates all errors")
    void sequenceValidatedAccumulatesAllErrors() {
      List<ValidationPath<List<String>, Integer>> paths =
          List.of(
              Path.valid(1, LIST_SEMIGROUP),
              Path.invalid(List.of("error1"), LIST_SEMIGROUP),
              Path.invalid(List.of("error2", "error3"), LIST_SEMIGROUP));

      ValidationPath<List<String>, List<Integer>> result =
          PathOps.sequenceValidated(paths, LIST_SEMIGROUP);

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError()).containsExactly("error1", "error2", "error3");
    }

    @Test
    @DisplayName("sequenceValidated() returns Valid of empty list for empty input")
    void sequenceValidatedReturnsValidOfEmptyListForEmptyInput() {
      List<ValidationPath<List<String>, Integer>> paths = List.of();

      ValidationPath<List<String>, List<Integer>> result =
          PathOps.sequenceValidated(paths, LIST_SEMIGROUP);

      assertThat(result.run().isValid()).isTrue();
      assertThat(result.run().get()).isEmpty();
    }

    @Test
    @DisplayName("traverseValidated() maps and sequences, accumulating errors")
    void traverseValidatedMapsAndSequencesAccumulatingErrors() {
      List<String> items = List.of("valid1", "invalid", "valid2", "also-invalid");

      ValidationPath<List<String>, List<String>> result =
          PathOps.traverseValidated(
              items,
              s -> {
                if (s.startsWith("valid")) {
                  return Path.valid(s.toUpperCase(), LIST_SEMIGROUP);
                } else {
                  return Path.invalid(List.of("Invalid: " + s), LIST_SEMIGROUP);
                }
              },
              LIST_SEMIGROUP);

      assertThat(result.run().isInvalid()).isTrue();
      assertThat(result.run().getError())
          .containsExactly("Invalid: invalid", "Invalid: also-invalid");
    }

    @Test
    @DisplayName("traverseValidated() returns Valid list when all succeed")
    void traverseValidatedReturnsValidListWhenAllSucceed() {
      List<String> items = List.of("a", "b", "c");

      ValidationPath<List<String>, List<String>> result =
          PathOps.traverseValidated(
              items, s -> Path.valid(s.toUpperCase(), LIST_SEMIGROUP), LIST_SEMIGROUP);

      assertThat(result.run().isValid()).isTrue();
      assertThat(result.run().get()).containsExactly("A", "B", "C");
    }
  }

  @Nested
  @DisplayName("TryPath Operations")
  class TryPathOperationsTests {

    @Test
    @DisplayName("sequenceTry() converts list of Success to Success of list")
    void sequenceTryConvertsListOfSuccessToSuccessOfList() {
      List<TryPath<Integer>> paths = List.of(Path.success(1), Path.success(2), Path.success(3));

      TryPath<List<Integer>> result = PathOps.sequenceTry(paths);

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.run().orElse(null)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("sequenceTry() returns first Failure")
    void sequenceTryReturnsFirstFailure() {
      RuntimeException error1 = new RuntimeException("error1");
      RuntimeException error2 = new RuntimeException("error2");
      List<TryPath<Integer>> paths =
          List.of(Path.success(1), Path.failure(error1), Path.failure(error2));

      TryPath<List<Integer>> result = PathOps.sequenceTry(paths);

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("sequenceTry() returns Success of empty list for empty input")
    void sequenceTryReturnsSuccessOfEmptyListForEmptyInput() {
      List<TryPath<Integer>> paths = List.of();

      TryPath<List<Integer>> result = PathOps.sequenceTry(paths);

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.run().orElse(null)).isEmpty();
    }

    @Test
    @DisplayName("traverseTry() maps and sequences")
    void traverseTryMapsAndSequences() {
      List<String> items = List.of("1", "2", "3");

      TryPath<List<Integer>> result =
          PathOps.traverseTry(items, s -> Path.success(Integer.parseInt(s)));

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.run().orElse(null)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("traverseTry() returns first failure")
    void traverseTryReturnsFirstFailure() {
      List<String> items = List.of("1", "not-a-number", "3");

      TryPath<List<Integer>> result =
          PathOps.traverseTry(
              items,
              s ->
                  Path.tryOf(
                      () -> {
                        return Integer.parseInt(s);
                      }));

      assertThat(result.run().isFailure()).isTrue();
    }
  }

  @Nested
  @DisplayName("TryPath firstSuccess Operation")
  class FirstSuccessOperationsTests {

    @Test
    @DisplayName("firstSuccess() returns first successful path")
    void firstSuccessReturnsFirstSuccessfulPath() {
      List<TryPath<String>> paths =
          List.of(
              Path.failure(new RuntimeException("error1")),
              Path.success("found"),
              Path.success("also found"));

      TryPath<String> result = PathOps.firstSuccess(paths);

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.run().orElse(null)).isEqualTo("found");
    }

    @Test
    @DisplayName("firstSuccess() returns last failure if all fail")
    void firstSuccessReturnsLastFailureIfAllFail() {
      RuntimeException error1 = new RuntimeException("error1");
      RuntimeException error2 = new RuntimeException("error2");
      List<TryPath<String>> paths = List.of(Path.failure(error1), Path.failure(error2));

      TryPath<String> result = PathOps.firstSuccess(paths);

      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("firstSuccess() returns sole success")
    void firstSuccessReturnsSoleSuccess() {
      List<TryPath<String>> paths = List.of(Path.success("only one"));

      TryPath<String> result = PathOps.firstSuccess(paths);

      assertThat(result.run().isSuccess()).isTrue();
      assertThat(result.run().orElse(null)).isEqualTo("only one");
    }

    @Test
    @DisplayName("firstSuccess() throws for empty list")
    void firstSuccessThrowsForEmptyList() {
      List<TryPath<String>> paths = List.of();

      assertThatIllegalArgumentException()
          .isThrownBy(() -> PathOps.firstSuccess(paths))
          .withMessageContaining("paths must not be empty");
    }

    @Test
    @DisplayName("firstSuccess() validates non-null paths")
    void firstSuccessValidatesNonNullPaths() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.firstSuccess(null))
          .withMessageContaining("paths must not be null");
    }
  }

  @Nested
  @DisplayName("VTaskPath Operations")
  class VTaskPathOperationsTests {

    @Test
    @DisplayName("sequenceVTask() converts list of success to success of list")
    void sequenceVTaskConvertsListOfSuccessToSuccessOfList() {
      List<VTaskPath<Integer>> paths =
          List.of(Path.vtaskPure(1), Path.vtaskPure(2), Path.vtaskPure(3));

      VTaskPath<List<Integer>> result = PathOps.sequenceVTask(paths);

      assertThat(result.unsafeRun()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("sequenceVTask() returns success of empty list for empty input")
    void sequenceVTaskReturnsSuccessOfEmptyListForEmptyInput() {
      List<VTaskPath<Integer>> paths = List.of();

      VTaskPath<List<Integer>> result = PathOps.sequenceVTask(paths);

      assertThat(result.unsafeRun()).isEmpty();
    }

    @Test
    @DisplayName("sequenceVTask() propagates first failure")
    void sequenceVTaskPropagatesFirstFailure() {
      RuntimeException error = new RuntimeException("VTask failed");
      List<VTaskPath<Integer>> paths =
          List.of(Path.vtaskPure(1), Path.vtaskFail(error), Path.vtaskPure(3));

      VTaskPath<List<Integer>> result = PathOps.sequenceVTask(paths);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("VTask failed");
    }

    @Test
    @DisplayName("sequenceVTask() executes sequentially")
    void sequenceVTaskExecutesSequentially() {
      AtomicInteger counter = new AtomicInteger(0);
      List<Integer> executionOrder = new ArrayList<>();

      List<VTaskPath<Integer>> paths =
          List.of(
              Path.vtask(
                  () -> {
                    executionOrder.add(counter.incrementAndGet());
                    return 1;
                  }),
              Path.vtask(
                  () -> {
                    executionOrder.add(counter.incrementAndGet());
                    return 2;
                  }),
              Path.vtask(
                  () -> {
                    executionOrder.add(counter.incrementAndGet());
                    return 3;
                  }));

      VTaskPath<List<Integer>> result = PathOps.sequenceVTask(paths);
      result.unsafeRun();

      assertThat(executionOrder).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("sequenceVTask() validates non-null paths")
    void sequenceVTaskValidatesNonNullPaths() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.sequenceVTask(null))
          .withMessageContaining("paths must not be null");
    }

    @Test
    @DisplayName("traverseVTask() maps and sequences")
    void traverseVTaskMapsAndSequences() {
      List<String> items = List.of("1", "2", "3");

      VTaskPath<List<Integer>> result =
          PathOps.traverseVTask(items, s -> Path.vtaskPure(Integer.parseInt(s)));

      assertThat(result.unsafeRun()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("traverseVTask() returns success of empty list for empty input")
    void traverseVTaskReturnsSuccessOfEmptyListForEmptyInput() {
      List<String> items = List.of();

      VTaskPath<List<Integer>> result =
          PathOps.traverseVTask(items, s -> Path.vtaskPure(Integer.parseInt(s)));

      assertThat(result.unsafeRun()).isEmpty();
    }

    @Test
    @DisplayName("traverseVTask() propagates first failure")
    void traverseVTaskPropagatesFirstFailure() {
      List<String> items = List.of("1", "not-a-number", "3");

      VTaskPath<List<Integer>> result =
          PathOps.traverseVTask(
              items,
              s ->
                  Path.vtask(
                      () -> {
                        return Integer.parseInt(s);
                      }));

      assertThatThrownBy(result::unsafeRun).isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("traverseVTask() validates non-null items")
    void traverseVTaskValidatesNonNullItems() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.traverseVTask(null, s -> Path.vtaskPure(s)))
          .withMessageContaining("items must not be null");
    }

    @Test
    @DisplayName("traverseVTask() validates non-null function")
    void traverseVTaskValidatesNonNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.traverseVTask(List.of("a"), null))
          .withMessageContaining("f must not be null");
    }

    @Test
    @DisplayName("sequenceVTaskPar() converts list of success to success of list")
    void sequenceVTaskParConvertsListOfSuccessToSuccessOfList() {
      List<VTaskPath<Integer>> paths =
          List.of(Path.vtaskPure(1), Path.vtaskPure(2), Path.vtaskPure(3));

      VTaskPath<List<Integer>> result = PathOps.sequenceVTaskPar(paths);

      assertThat(result.unsafeRun()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("sequenceVTaskPar() returns success of empty list for empty input")
    void sequenceVTaskParReturnsSuccessOfEmptyListForEmptyInput() {
      List<VTaskPath<Integer>> paths = List.of();

      VTaskPath<List<Integer>> result = PathOps.sequenceVTaskPar(paths);

      assertThat(result.unsafeRun()).isEmpty();
    }

    @Test
    @DisplayName("sequenceVTaskPar() validates non-null paths")
    void sequenceVTaskParValidatesNonNullPaths() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.sequenceVTaskPar(null))
          .withMessageContaining("paths must not be null");
    }

    @Test
    @DisplayName("sequenceVTaskPar() propagates failure")
    void sequenceVTaskParPropagatesFailure() {
      RuntimeException error = new RuntimeException("Parallel VTask failed");
      List<VTaskPath<Integer>> paths =
          List.of(Path.vtaskPure(1), Path.vtaskFail(error), Path.vtaskPure(3));

      VTaskPath<List<Integer>> result = PathOps.sequenceVTaskPar(paths);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Parallel VTask failed");
    }

    @Test
    @DisplayName("traverseVTaskPar() maps and sequences in parallel")
    void traverseVTaskParMapsAndSequences() {
      List<String> items = List.of("1", "2", "3");

      VTaskPath<List<Integer>> result =
          PathOps.traverseVTaskPar(items, s -> Path.vtaskPure(Integer.parseInt(s)));

      assertThat(result.unsafeRun()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("traverseVTaskPar() returns success of empty list for empty input")
    void traverseVTaskParReturnsSuccessOfEmptyListForEmptyInput() {
      List<String> items = List.of();

      VTaskPath<List<Integer>> result =
          PathOps.traverseVTaskPar(items, s -> Path.vtaskPure(Integer.parseInt(s)));

      assertThat(result.unsafeRun()).isEmpty();
    }

    @Test
    @DisplayName("traverseVTaskPar() validates non-null items")
    void traverseVTaskParValidatesNonNullItems() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.traverseVTaskPar(null, s -> Path.vtaskPure(s)))
          .withMessageContaining("items must not be null");
    }

    @Test
    @DisplayName("traverseVTaskPar() validates non-null function")
    void traverseVTaskParValidatesNonNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.traverseVTaskPar(List.of("a"), null))
          .withMessageContaining("f must not be null");
    }

    @Test
    @DisplayName("raceVTask() returns first successful result")
    void raceVTaskReturnsFirstSuccessfulResult() {
      VTaskPath<String> fast = Path.vtaskPure("fast");
      VTaskPath<String> slow =
          Path.vtask(
              () -> {
                try {
                  Thread.sleep(100);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return "slow";
              });

      List<VTaskPath<String>> paths = List.of(slow, fast);

      VTaskPath<String> result = PathOps.raceVTask(paths);

      // Either could win depending on scheduling
      String winner = result.unsafeRun();
      assertThat(winner).isIn("fast", "slow");
    }

    @Test
    @DisplayName("raceVTask() returns sole path for single-element list")
    void raceVTaskReturnsSolePath() {
      VTaskPath<String> path = Path.vtaskPure("only one");
      List<VTaskPath<String>> paths = List.of(path);

      VTaskPath<String> result = PathOps.raceVTask(paths);

      assertThat(result).isSameAs(path);
    }

    @Test
    @DisplayName("raceVTask() throws for empty list")
    void raceVTaskThrowsForEmptyList() {
      List<VTaskPath<String>> paths = List.of();

      assertThatIllegalArgumentException()
          .isThrownBy(() -> PathOps.raceVTask(paths))
          .withMessageContaining("paths must not be empty");
    }

    @Test
    @DisplayName("raceVTask() validates non-null paths")
    void raceVTaskValidatesNonNullPaths() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.raceVTask(null))
          .withMessageContaining("paths must not be null");
    }

    @Test
    @DisplayName("firstVTaskSuccess() returns first successful path")
    void firstVTaskSuccessReturnsFirstSuccessfulPath() {
      List<VTaskPath<String>> paths =
          List.of(
              Path.vtaskFail(new RuntimeException("error1")),
              Path.vtaskPure("found"),
              Path.vtaskPure("also found"));

      VTaskPath<String> result = PathOps.firstVTaskSuccess(paths);

      assertThat(result.unsafeRun()).isEqualTo("found");
    }

    @Test
    @DisplayName("firstVTaskSuccess() returns last failure if all fail")
    void firstVTaskSuccessReturnsLastFailureIfAllFail() {
      RuntimeException error1 = new RuntimeException("error1");
      RuntimeException error2 = new RuntimeException("error2");
      List<VTaskPath<String>> paths = List.of(Path.vtaskFail(error1), Path.vtaskFail(error2));

      VTaskPath<String> result = PathOps.firstVTaskSuccess(paths);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("error2");
    }

    @Test
    @DisplayName("firstVTaskSuccess() returns sole success")
    void firstVTaskSuccessReturnsSoleSuccess() {
      List<VTaskPath<String>> paths = List.of(Path.vtaskPure("only one"));

      VTaskPath<String> result = PathOps.firstVTaskSuccess(paths);

      assertThat(result.unsafeRun()).isEqualTo("only one");
    }

    @Test
    @DisplayName("firstVTaskSuccess() throws for empty list")
    void firstVTaskSuccessThrowsForEmptyList() {
      List<VTaskPath<String>> paths = List.of();

      assertThatIllegalArgumentException()
          .isThrownBy(() -> PathOps.firstVTaskSuccess(paths))
          .withMessageContaining("paths must not be empty");
    }

    @Test
    @DisplayName("firstVTaskSuccess() validates non-null paths")
    void firstVTaskSuccessValidatesNonNullPaths() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.firstVTaskSuccess(null))
          .withMessageContaining("paths must not be null");
    }

    @Test
    @DisplayName("firstVTaskSuccess() executes sequentially, not in parallel")
    void firstVTaskSuccessExecutesSequentially() {
      AtomicInteger counter = new AtomicInteger(0);
      List<Integer> executionOrder = new ArrayList<>();

      List<VTaskPath<String>> paths =
          List.of(
              Path.vtask(
                  () -> {
                    executionOrder.add(counter.incrementAndGet());
                    throw new RuntimeException("first");
                  }),
              Path.vtask(
                  () -> {
                    executionOrder.add(counter.incrementAndGet());
                    return "success";
                  }),
              Path.vtask(
                  () -> {
                    executionOrder.add(counter.incrementAndGet());
                    return "not executed";
                  }));

      VTaskPath<String> result = PathOps.firstVTaskSuccess(paths);
      String value = result.unsafeRun();

      assertThat(value).isEqualTo("success");
      // Third task should not execute since second succeeded
      assertThat(executionOrder).containsExactly(1, 2);
    }

    @Test
    @DisplayName("firstVTaskSuccess() propagates Error when all fail with Error")
    void firstVTaskSuccessPropagatesErrorWhenAllFailWithError() {
      Error error = new AssertionError("test error");
      List<VTaskPath<String>> paths =
          List.of(
              Path.vtask(
                  () -> {
                    throw error;
                  }));

      VTaskPath<String> result = PathOps.firstVTaskSuccess(paths);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(AssertionError.class)
          .hasMessage("test error");
    }

    @Test
    @DisplayName("firstVTaskSuccess() wraps checked exception in RuntimeException when all fail")
    void firstVTaskSuccessWrapsCheckedExceptionWhenAllFail() {
      List<VTaskPath<String>> paths =
          List.of(
              Path.vtask(
                  () -> {
                    throw new Exception("checked exception");
                  }));

      VTaskPath<String> result = PathOps.firstVTaskSuccess(paths);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasCauseInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("sequenceVTask() handles Error properly")
    void sequenceVTaskHandlesError() {
      Error error = new OutOfMemoryError("test error");
      List<VTaskPath<Integer>> paths =
          List.of(
              Path.vtaskPure(1),
              Path.vtask(
                  () -> {
                    throw error;
                  }));

      VTaskPath<List<Integer>> result = PathOps.sequenceVTask(paths);

      assertThatThrownBy(result::unsafeRun).isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    @DisplayName("traverseVTask() handles Error properly")
    void traverseVTaskHandlesError() {
      List<String> items = List.of("a", "error", "b");

      VTaskPath<List<Integer>> result =
          PathOps.traverseVTask(
              items,
              s ->
                  Path.vtask(
                      () -> {
                        if (s.equals("error")) {
                          throw new StackOverflowError("test error");
                        }
                        return s.length();
                      }));

      assertThatThrownBy(result::unsafeRun).isInstanceOf(StackOverflowError.class);
    }
  }

  @Nested
  @DisplayName("OptionalPath Operations")
  class OptionalPathOperationsTests {

    @Test
    @DisplayName("sequenceOptional() converts list of present to present of list")
    void sequenceOptionalConvertsListOfPresentToPresentOfList() {
      List<OptionalPath<Integer>> paths =
          List.of(Path.present(1), Path.present(2), Path.present(3));

      OptionalPath<List<Integer>> result = PathOps.sequenceOptional(paths);

      assertThat(result.run()).isPresent();
      assertThat(result.run().get()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("sequenceOptional() returns empty if any is empty")
    void sequenceOptionalReturnsEmptyIfAnyIsEmpty() {
      List<OptionalPath<Integer>> paths = List.of(Path.present(1), Path.absent(), Path.present(3));

      OptionalPath<List<Integer>> result = PathOps.sequenceOptional(paths);

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("sequenceOptional() returns present of empty list for empty input")
    void sequenceOptionalReturnsPresentOfEmptyListForEmptyInput() {
      List<OptionalPath<Integer>> paths = List.of();

      OptionalPath<List<Integer>> result = PathOps.sequenceOptional(paths);

      assertThat(result.run()).isPresent();
      assertThat(result.run().get()).isEmpty();
    }

    @Test
    @DisplayName("traverseOptional() maps and sequences")
    void traverseOptionalMapsAndSequences() {
      List<String> items = List.of("1", "2", "3");

      OptionalPath<List<Integer>> result =
          PathOps.traverseOptional(items, s -> Path.present(Integer.parseInt(s)));

      assertThat(result.run()).isPresent();
      assertThat(result.run().get()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("traverseOptional() returns empty if any mapping fails")
    void traverseOptionalReturnsEmptyIfAnyMappingFails() {
      List<String> items = List.of("1", "not-a-number", "3");

      OptionalPath<List<Integer>> result =
          PathOps.traverseOptional(
              items,
              s -> {
                try {
                  return Path.present(Integer.parseInt(s));
                } catch (NumberFormatException e) {
                  return Path.absent();
                }
              });

      assertThat(result.run()).isEmpty();
    }
  }

  @Nested
  @DisplayName("NonDetPath Operations")
  class NonDetPathOperationsTests {

    @Test
    @DisplayName("sequenceNonDet() produces Cartesian product of lists")
    void sequenceNonDetProducesCartesianProduct() {
      List<NonDetPath<Integer>> paths =
          List.of(NonDetPath.of(List.of(1, 2)), NonDetPath.of(List.of(3, 4)));

      NonDetPath<List<Integer>> result = PathOps.sequenceNonDet(paths);

      List<List<Integer>> lists = result.run();
      assertThat(lists)
          .containsExactlyInAnyOrder(List.of(1, 3), List.of(1, 4), List.of(2, 3), List.of(2, 4));
    }

    @Test
    @DisplayName("sequenceNonDet() returns list of empty list for empty input")
    void sequenceNonDetReturnsEmptyListForEmptyInput() {
      List<NonDetPath<Integer>> paths = List.of();

      NonDetPath<List<Integer>> result = PathOps.sequenceNonDet(paths);

      assertThat(result.run()).containsExactly(List.of());
    }

    @Test
    @DisplayName("sequenceNonDet() with single path returns each element as list")
    void sequenceNonDetWithSinglePath() {
      List<NonDetPath<Integer>> paths = List.of(NonDetPath.of(List.of(1, 2, 3)));

      NonDetPath<List<Integer>> result = PathOps.sequenceNonDet(paths);

      assertThat(result.run()).containsExactly(List.of(1), List.of(2), List.of(3));
    }

    @Test
    @DisplayName("sequenceNonDet() validates non-null paths")
    void sequenceNonDetValidatesNonNullPaths() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.sequenceNonDet(null))
          .withMessageContaining("paths must not be null");
    }

    @Test
    @DisplayName("traverseNonDet() maps and produces Cartesian product")
    void traverseNonDetMapsAndProducesCartesianProduct() {
      List<String> items = List.of("a", "b");

      NonDetPath<List<String>> result =
          PathOps.traverseNonDet(items, s -> NonDetPath.of(List.of(s.toUpperCase(), s + s)));

      List<List<String>> lists = result.run();
      assertThat(lists)
          .containsExactlyInAnyOrder(
              List.of("A", "B"), List.of("A", "bb"), List.of("aa", "B"), List.of("aa", "bb"));
    }

    @Test
    @DisplayName("traverseNonDet() returns list of empty list for empty input")
    void traverseNonDetReturnsEmptyListForEmptyInput() {
      List<String> items = List.of();

      NonDetPath<List<Integer>> result =
          PathOps.traverseNonDet(items, s -> NonDetPath.of(List.of(s.length())));

      assertThat(result.run()).containsExactly(List.of());
    }

    @Test
    @DisplayName("traverseNonDet() validates non-null items")
    void traverseNonDetValidatesNonNullItems() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.traverseNonDet(null, s -> NonDetPath.pure(s)))
          .withMessageContaining("items must not be null");
    }

    @Test
    @DisplayName("traverseNonDet() validates non-null function")
    void traverseNonDetValidatesNonNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.traverseNonDet(List.of("a"), null))
          .withMessageContaining("f must not be null");
    }

    @Test
    @DisplayName("flatten() concatenates nested NonDetPaths")
    void flattenConcatenatesNested() {
      NonDetPath<NonDetPath<Integer>> nested =
          NonDetPath.of(List.of(NonDetPath.of(List.of(1, 2)), NonDetPath.of(List.of(3, 4, 5))));

      NonDetPath<Integer> result = PathOps.flatten(nested);

      assertThat(result.run()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("flatten() validates non-null nested")
    void flattenValidatesNonNullNested() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.flatten(null))
          .withMessageContaining("nested must not be null");
    }
  }

  @Nested
  @DisplayName("CompletableFuturePath Operations")
  class CompletableFuturePathOperationsTests {

    @Test
    @DisplayName("sequenceFuture() converts list of futures to future of list")
    void sequenceFutureConvertsListOfFutures() throws Exception {
      List<CompletableFuturePath<Integer>> paths =
          List.of(
              CompletableFuturePath.completed(1),
              CompletableFuturePath.completed(2),
              CompletableFuturePath.completed(3));

      CompletableFuturePath<List<Integer>> result = PathOps.sequenceFuture(paths);

      assertThat(result.join()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("sequenceFuture() returns empty list for empty input")
    void sequenceFutureReturnsEmptyListForEmptyInput() {
      List<CompletableFuturePath<Integer>> paths = List.of();

      CompletableFuturePath<List<Integer>> result = PathOps.sequenceFuture(paths);

      assertThat(result.join()).isEmpty();
    }

    @Test
    @DisplayName("sequenceFuture() validates non-null paths")
    void sequenceFutureValidatesNonNullPaths() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.sequenceFuture(null))
          .withMessageContaining("paths must not be null");
    }

    @Test
    @DisplayName("traverseFuture() maps and sequences concurrently")
    void traverseFutureMapsAndSequences() {
      List<String> items = List.of("1", "2", "3");

      CompletableFuturePath<List<Integer>> result =
          PathOps.traverseFuture(items, s -> CompletableFuturePath.completed(Integer.parseInt(s)));

      assertThat(result.join()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("traverseFuture() returns empty list for empty input")
    void traverseFutureReturnsEmptyListForEmptyInput() {
      List<String> items = List.of();

      CompletableFuturePath<List<Integer>> result =
          PathOps.traverseFuture(items, s -> CompletableFuturePath.completed(s.length()));

      assertThat(result.join()).isEmpty();
    }

    @Test
    @DisplayName("traverseFuture() validates non-null items")
    void traverseFutureValidatesNonNullItems() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.traverseFuture(null, s -> CompletableFuturePath.completed(s)))
          .withMessageContaining("items must not be null");
    }

    @Test
    @DisplayName("traverseFuture() validates non-null function")
    void traverseFutureValidatesNonNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.traverseFuture(List.of("a"), null))
          .withMessageContaining("f must not be null");
    }

    @Test
    @DisplayName("firstCompletedSuccess() returns first successful future")
    void firstCompletedSuccessReturnsFirstSuccess() throws Exception {
      CompletableFuture<String> fast = new CompletableFuture<>();
      CompletableFuture<String> slow = new CompletableFuture<>();

      List<CompletableFuturePath<String>> paths =
          List.of(CompletableFuturePath.fromFuture(slow), CompletableFuturePath.fromFuture(fast));

      CompletableFuturePath<String> result = PathOps.firstCompletedSuccess(paths);

      fast.complete("fast wins");

      assertThat(result.toCompletableFuture().get(1, TimeUnit.SECONDS)).isEqualTo("fast wins");
    }

    @Test
    @DisplayName("firstCompletedSuccess() returns last failure if all fail")
    void firstCompletedSuccessReturnsLastFailure() {
      CompletableFuture<String> first = new CompletableFuture<>();
      CompletableFuture<String> second = new CompletableFuture<>();

      List<CompletableFuturePath<String>> paths =
          List.of(
              CompletableFuturePath.fromFuture(first), CompletableFuturePath.fromFuture(second));

      CompletableFuturePath<String> result = PathOps.firstCompletedSuccess(paths);

      first.completeExceptionally(new RuntimeException("first failed"));
      second.completeExceptionally(new RuntimeException("second failed"));

      assertThat(result.isCompletedExceptionally()).isTrue();
    }

    @Test
    @DisplayName("firstCompletedSuccess() returns sole path for single-element list")
    void firstCompletedSuccessReturnsSolePath() {
      CompletableFuturePath<String> path = CompletableFuturePath.completed("only one");
      List<CompletableFuturePath<String>> paths = List.of(path);

      CompletableFuturePath<String> result = PathOps.firstCompletedSuccess(paths);

      assertThat(result).isSameAs(path);
    }

    @Test
    @DisplayName("firstCompletedSuccess() throws for empty list")
    void firstCompletedSuccessThrowsForEmptyList() {
      List<CompletableFuturePath<String>> paths = List.of();

      assertThatIllegalArgumentException()
          .isThrownBy(() -> PathOps.firstCompletedSuccess(paths))
          .withMessageContaining("paths must not be empty");
    }

    @Test
    @DisplayName("firstCompletedSuccess() validates non-null paths")
    void firstCompletedSuccessValidatesNonNullPaths() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.firstCompletedSuccess(null))
          .withMessageContaining("paths must not be null");
    }

    @Test
    @DisplayName("firstCompletedSuccess() handles concurrent success before other futures complete")
    void firstCompletedSuccessHandlesConcurrentSuccess() throws Exception {
      // Create multiple futures to test race condition branches
      CompletableFuture<String> fast = new CompletableFuture<>();
      CompletableFuture<String> medium = new CompletableFuture<>();
      CompletableFuture<String> slow = new CompletableFuture<>();

      List<CompletableFuturePath<String>> paths =
          List.of(
              CompletableFuturePath.fromFuture(fast),
              CompletableFuturePath.fromFuture(medium),
              CompletableFuturePath.fromFuture(slow));

      CompletableFuturePath<String> result = PathOps.firstCompletedSuccess(paths);

      // Complete the fast one first
      fast.complete("fast");

      // Now the result should be done, but complete others anyway to trigger race condition
      // branches
      // This tests line 613: when ex == null but result.isDone() is true
      medium.complete("medium");
      slow.complete("slow");

      assertThat(result.toCompletableFuture().get(1, TimeUnit.SECONDS)).isEqualTo("fast");
    }

    @Test
    @DisplayName("firstCompletedSuccess() handles mixed success and failure")
    void firstCompletedSuccessHandlesMixedSuccessAndFailure() throws Exception {
      CompletableFuture<String> willFail = new CompletableFuture<>();
      CompletableFuture<String> willSucceed = new CompletableFuture<>();

      List<CompletableFuturePath<String>> paths =
          List.of(
              CompletableFuturePath.fromFuture(willFail),
              CompletableFuturePath.fromFuture(willSucceed));

      CompletableFuturePath<String> result = PathOps.firstCompletedSuccess(paths);

      // Fail one first, then succeed the other
      willFail.completeExceptionally(new RuntimeException("failed"));
      willSucceed.complete("success");

      assertThat(result.toCompletableFuture().get(1, TimeUnit.SECONDS)).isEqualTo("success");
    }

    @Test
    @DisplayName("firstCompletedSuccess() handles success after failure with result already done")
    void firstCompletedSuccessHandlesSuccessAfterFailureWhenDone() throws Exception {
      CompletableFuture<String> first = new CompletableFuture<>();
      CompletableFuture<String> second = new CompletableFuture<>();
      CompletableFuture<String> third = new CompletableFuture<>();

      List<CompletableFuturePath<String>> paths =
          List.of(
              CompletableFuturePath.fromFuture(first),
              CompletableFuturePath.fromFuture(second),
              CompletableFuturePath.fromFuture(third));

      CompletableFuturePath<String> result = PathOps.firstCompletedSuccess(paths);

      // Complete second successfully first
      second.complete("second wins");

      // Now complete others - these should hit race condition branches
      // Line 615: ex != null when result is already done
      first.completeExceptionally(new RuntimeException("first failed"));
      third.complete("third too late");

      assertThat(result.toCompletableFuture().get(1, TimeUnit.SECONDS)).isEqualTo("second wins");
    }

    @Test
    @DisplayName("firstCompletedSuccess() handles all failures except one races with completion")
    void firstCompletedSuccessHandlesFailureRaceWithCompletion() throws Exception {
      CompletableFuture<String> f1 = new CompletableFuture<>();
      CompletableFuture<String> f2 = new CompletableFuture<>();
      CompletableFuture<String> f3 = new CompletableFuture<>();

      List<CompletableFuturePath<String>> paths =
          List.of(
              CompletableFuturePath.fromFuture(f1),
              CompletableFuturePath.fromFuture(f2),
              CompletableFuturePath.fromFuture(f3));

      CompletableFuturePath<String> result = PathOps.firstCompletedSuccess(paths);

      // Fail first two
      f1.completeExceptionally(new RuntimeException("f1 failed"));
      f2.completeExceptionally(new RuntimeException("f2 failed"));

      // Success before the last failure
      f3.complete("f3 wins");

      assertThat(result.toCompletableFuture().get(1, TimeUnit.SECONDS)).isEqualTo("f3 wins");
    }
  }

  @Nested
  @DisplayName("ListPath Operations")
  class ListPathOperationsTests {

    @Test
    @DisplayName("sequenceListPath() transposes list of ListPaths positionally")
    void sequenceListPathTransposesPositionally() {
      List<ListPath<Integer>> paths =
          List.of(ListPath.of(1, 2, 3), ListPath.of(4, 5, 6), ListPath.of(7, 8, 9));

      ListPath<List<Integer>> result = PathOps.sequenceListPath(paths);

      // Should transpose: [[1,4,7], [2,5,8], [3,6,9]]
      assertThat(result.run())
          .containsExactly(List.of(1, 4, 7), List.of(2, 5, 8), List.of(3, 6, 9));
    }

    @Test
    @DisplayName("sequenceListPath() returns list of empty list for empty input")
    void sequenceListPathReturnsEmptyListForEmptyInput() {
      List<ListPath<Integer>> paths = List.of();

      ListPath<List<Integer>> result = PathOps.sequenceListPath(paths);

      assertThat(result.run()).containsExactly(List.of());
    }

    @Test
    @DisplayName("sequenceListPath() handles unequal sizes by using minimum length")
    void sequenceListPathHandlesUnequalSizes() {
      List<ListPath<Integer>> paths =
          List.of(ListPath.of(1, 2, 3, 4), ListPath.of(5, 6)); // Different sizes

      ListPath<List<Integer>> result = PathOps.sequenceListPath(paths);

      // Should use minimum size (2), producing [[1,5], [2,6]]
      assertThat(result.run()).containsExactly(List.of(1, 5), List.of(2, 6));
    }

    @Test
    @DisplayName("sequenceListPath() validates non-null paths")
    void sequenceListPathValidatesNonNullPaths() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.sequenceListPath(null))
          .withMessageContaining("paths must not be null");
    }

    @Test
    @DisplayName("sequenceListPath() with single path returns each element wrapped")
    void sequenceListPathWithSinglePath() {
      List<ListPath<Integer>> paths = List.of(ListPath.of(1, 2, 3));

      ListPath<List<Integer>> result = PathOps.sequenceListPath(paths);

      assertThat(result.run()).containsExactly(List.of(1), List.of(2), List.of(3));
    }

    @Test
    @DisplayName("traverseListPath() maps and sequences positionally")
    void traverseListPathMapsAndSequences() {
      List<String> items = List.of("a", "b");

      ListPath<List<String>> result =
          PathOps.traverseListPath(items, s -> ListPath.of(s.toUpperCase(), s + s));

      // Each item produces ["A", "aa"] and ["B", "bb"]
      // Transposed: [["A", "B"], ["aa", "bb"]]
      assertThat(result.run()).containsExactly(List.of("A", "B"), List.of("aa", "bb"));
    }

    @Test
    @DisplayName("traverseListPath() returns list of empty list for empty input")
    void traverseListPathReturnsEmptyListForEmptyInput() {
      List<String> items = List.of();

      ListPath<List<Integer>> result =
          PathOps.traverseListPath(items, s -> ListPath.of(s.length()));

      assertThat(result.run()).containsExactly(List.of());
    }

    @Test
    @DisplayName("traverseListPath() validates non-null items")
    void traverseListPathValidatesNonNullItems() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.traverseListPath(null, s -> ListPath.of(s)))
          .withMessageContaining("items must not be null");
    }

    @Test
    @DisplayName("traverseListPath() validates non-null function")
    void traverseListPathValidatesNonNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.traverseListPath(List.of("a"), null))
          .withMessageContaining("f must not be null");
    }

    @Test
    @DisplayName("flattenListPath() concatenates nested ListPaths")
    void flattenListPathConcatenatesNested() {
      ListPath<ListPath<Integer>> nested =
          ListPath.of(ListPath.of(1, 2), ListPath.of(3, 4, 5), ListPath.of(6));

      ListPath<Integer> result = PathOps.flattenListPath(nested);

      assertThat(result.run()).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("flattenListPath() returns empty for nested empty")
    void flattenListPathReturnsEmptyForNestedEmpty() {
      ListPath<ListPath<Integer>> nested = ListPath.of();

      ListPath<Integer> result = PathOps.flattenListPath(nested);

      assertThat(result.run()).isEmpty();
    }

    @Test
    @DisplayName("flattenListPath() validates non-null nested")
    void flattenListPathValidatesNonNullNested() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.flattenListPath(null))
          .withMessageContaining("nested must not be null");
    }

    @Test
    @DisplayName("zipAll() is alias for sequenceListPath()")
    void zipAllIsAliasForSequenceListPath() {
      List<ListPath<Integer>> paths = List.of(ListPath.of(1, 2), ListPath.of(3, 4));

      ListPath<List<Integer>> result = PathOps.zipAll(paths);

      assertThat(result.run()).containsExactly(List.of(1, 3), List.of(2, 4));
    }

    @Test
    @DisplayName("zipAll() handles empty paths")
    void zipAllHandlesEmptyPaths() {
      List<ListPath<Integer>> paths = List.of(ListPath.of(), ListPath.of(1, 2));

      ListPath<List<Integer>> result = PathOps.zipAll(paths);

      // Minimum size is 0, so result is empty
      assertThat(result.run()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Parallel IOPath Operations")
  class ParallelIOPathOperationsTests {

    @Test
    @DisplayName("parSequenceIO() executes IOPaths in parallel and collects results")
    void parSequenceIOExecutesInParallel() {
      List<IOPath<Integer>> paths = List.of(Path.ioPure(1), Path.ioPure(2), Path.ioPure(3));

      IOPath<List<Integer>> result = PathOps.parSequenceIO(paths);

      assertThat(result.unsafeRun()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("parSequenceIO() returns empty list for empty input")
    void parSequenceIOReturnsEmptyListForEmptyInput() {
      List<IOPath<Integer>> paths = List.of();

      IOPath<List<Integer>> result = PathOps.parSequenceIO(paths);

      assertThat(result.unsafeRun()).isEmpty();
    }

    @Test
    @DisplayName("parSequenceIO() validates non-null paths")
    void parSequenceIOValidatesNonNullPaths() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parSequenceIO(null))
          .withMessageContaining("paths must not be null");
    }

    @Test
    @DisplayName("parSequenceIO() propagates RuntimeException from failed IOPath")
    void parSequenceIOPropagatesRuntimeException() {
      RuntimeException error = new RuntimeException("IO failed");
      List<IOPath<Integer>> paths =
          List.of(
              Path.ioPure(1),
              Path.io(
                  () -> {
                    throw error;
                  }),
              Path.ioPure(3));

      IOPath<List<Integer>> result = PathOps.parSequenceIO(paths);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("IO failed");
    }

    @Test
    @DisplayName("parZip3() combines three IOPaths in parallel")
    void parZip3CombinesThreeIOPaths() {
      IOPath<Integer> first = Path.ioPure(1);
      IOPath<Integer> second = Path.ioPure(2);
      IOPath<Integer> third = Path.ioPure(3);

      IOPath<Integer> result = PathOps.parZip3(first, second, third, (a, b, c) -> a + b + c);

      assertThat(result.unsafeRun()).isEqualTo(6);
    }

    @Test
    @DisplayName("parZip3() validates non-null arguments")
    void parZip3ValidatesNonNullArguments() {
      IOPath<Integer> path = Path.ioPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip3(null, path, path, (a, b, c) -> a))
          .withMessageContaining("first must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip3(path, null, path, (a, b, c) -> a))
          .withMessageContaining("second must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip3(path, path, null, (a, b, c) -> a))
          .withMessageContaining("third must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip3(path, path, path, null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("parZip3() propagates RuntimeException from any failed IOPath")
    void parZip3PropagatesRuntimeException() {
      RuntimeException error = new RuntimeException("parZip3 failed");
      IOPath<Integer> first = Path.ioPure(1);
      IOPath<Integer> second =
          Path.io(
              () -> {
                throw error;
              });
      IOPath<Integer> third = Path.ioPure(3);

      IOPath<Integer> result = PathOps.parZip3(first, second, third, (a, b, c) -> a + b + c);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("parZip3 failed");
    }

    @Test
    @DisplayName("parZip4() combines four IOPaths in parallel")
    void parZip4CombinesFourIOPaths() {
      IOPath<Integer> first = Path.ioPure(1);
      IOPath<Integer> second = Path.ioPure(2);
      IOPath<Integer> third = Path.ioPure(3);
      IOPath<Integer> fourth = Path.ioPure(4);

      IOPath<Integer> result =
          PathOps.parZip4(first, second, third, fourth, (a, b, c, d) -> a + b + c + d);

      assertThat(result.unsafeRun()).isEqualTo(10);
    }

    @Test
    @DisplayName("parZip4() validates non-null arguments")
    void parZip4ValidatesNonNullArguments() {
      IOPath<Integer> path = Path.ioPure(1);

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip4(null, path, path, path, (a, b, c, d) -> a))
          .withMessageContaining("first must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip4(path, null, path, path, (a, b, c, d) -> a))
          .withMessageContaining("second must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip4(path, path, null, path, (a, b, c, d) -> a))
          .withMessageContaining("third must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip4(path, path, path, null, (a, b, c, d) -> a))
          .withMessageContaining("fourth must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.parZip4(path, path, path, path, null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("parZip4() propagates RuntimeException from any failed IOPath")
    void parZip4PropagatesRuntimeException() {
      RuntimeException error = new RuntimeException("parZip4 failed");
      IOPath<Integer> first = Path.ioPure(1);
      IOPath<Integer> second = Path.ioPure(2);
      IOPath<Integer> third =
          Path.io(
              () -> {
                throw error;
              });
      IOPath<Integer> fourth = Path.ioPure(4);

      IOPath<Integer> result =
          PathOps.parZip4(first, second, third, fourth, (a, b, c, d) -> a + b + c + d);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("parZip4 failed");
    }

    @Test
    @DisplayName("raceIO() returns first successful result")
    void raceIOReturnsFirstSuccessfulResult() {
      IOPath<String> fast = Path.ioPure("fast");
      IOPath<String> slow =
          Path.io(
              () -> {
                try {
                  Thread.sleep(100);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return "slow";
              });

      List<IOPath<String>> paths = List.of(slow, fast);

      IOPath<String> result = PathOps.raceIO(paths);

      // The "fast" one should win since it doesn't sleep
      String winner = result.unsafeRun();
      assertThat(winner).isIn("fast", "slow"); // Either could win depending on scheduling
    }

    @Test
    @DisplayName("raceIO() returns sole path for single-element list")
    void raceIOReturnsSolePath() {
      IOPath<String> path = Path.ioPure("only one");
      List<IOPath<String>> paths = List.of(path);

      IOPath<String> result = PathOps.raceIO(paths);

      assertThat(result).isSameAs(path);
    }

    @Test
    @DisplayName("raceIO() throws for empty list")
    void raceIOThrowsForEmptyList() {
      List<IOPath<String>> paths = List.of();

      assertThatIllegalArgumentException()
          .isThrownBy(() -> PathOps.raceIO(paths))
          .withMessageContaining("paths must not be empty");
    }

    @Test
    @DisplayName("raceIO() validates non-null paths")
    void raceIOValidatesNonNullPaths() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.raceIO(null))
          .withMessageContaining("paths must not be null");
    }

    @Test
    @DisplayName("raceIO() propagates last failure if all fail")
    void raceIOPropagatesLastFailureIfAllFail() {
      RuntimeException error1 = new RuntimeException("error1");
      RuntimeException error2 = new RuntimeException("error2");
      IOPath<String> fail1 =
          Path.io(
              () -> {
                throw error1;
              });
      IOPath<String> fail2 =
          Path.io(
              () -> {
                throw error2;
              });

      List<IOPath<String>> paths = List.of(fail1, fail2);

      IOPath<String> result = PathOps.raceIO(paths);

      assertThatThrownBy(result::unsafeRun).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("raceIO() handles mixed success and failure")
    void raceIOHandlesMixedSuccessAndFailure() {
      RuntimeException error = new RuntimeException("will fail");
      IOPath<String> willFail =
          Path.io(
              () -> {
                throw error;
              });
      IOPath<String> willSucceed = Path.ioPure("success");

      List<IOPath<String>> paths = List.of(willFail, willSucceed);

      IOPath<String> result = PathOps.raceIO(paths);

      // The successful one should win or be available
      String winner = result.unsafeRun();
      assertThat(winner).isEqualTo("success");
    }

    @Test
    @DisplayName("parSequenceFuture() is alias for sequenceFuture()")
    void parSequenceFutureIsAliasForSequenceFuture() {
      List<CompletableFuturePath<Integer>> paths =
          List.of(
              CompletableFuturePath.completed(1),
              CompletableFuturePath.completed(2),
              CompletableFuturePath.completed(3));

      CompletableFuturePath<List<Integer>> result = PathOps.parSequenceFuture(paths);

      assertThat(result.join()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("parSequenceIO() handles InterruptedException during parallel execution")
    void parSequenceIOHandlesInterruptedException() throws InterruptedException {
      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch canProceed = new CountDownLatch(1);
      AtomicReference<Throwable> caught = new AtomicReference<>();

      // Create an IOPath that blocks until we signal
      List<IOPath<Integer>> paths =
          List.of(
              Path.io(
                  () -> {
                    started.countDown();
                    try {
                      canProceed.await(); // Block until interrupted
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                      throw new RuntimeException(e);
                    }
                    return 1;
                  }));

      IOPath<List<Integer>> result = PathOps.parSequenceIO(paths);

      Thread testThread =
          new Thread(
              () -> {
                try {
                  result.unsafeRun();
                } catch (Throwable t) {
                  caught.set(t);
                }
              });

      testThread.start();
      started.await(); // Wait for the IOPath to start
      testThread.interrupt(); // Interrupt while waiting
      testThread.join(5000);

      assertThat(testThread.isAlive()).isFalse();
      // The exception should be propagated
      assertThat(caught.get()).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("parSequenceIO() wraps checked exception in RuntimeException")
    void parSequenceIOWrapsCheckedException() {
      Exception checkedException = new Exception("Checked exception from parallel execution");
      List<IOPath<Integer>> paths =
          List.of(
              Path.io(
                  () -> {
                    throw sneakyThrow(checkedException);
                  }));

      IOPath<List<Integer>> result = PathOps.parSequenceIO(paths);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasCause(checkedException);
    }

    @Test
    @DisplayName("parZip3() wraps checked exception in RuntimeException")
    void parZip3WrapsCheckedException() {
      Exception checkedException = new Exception("parZip3 checked exception");
      IOPath<Integer> first = Path.ioPure(1);
      IOPath<Integer> second =
          Path.io(
              () -> {
                throw sneakyThrow(checkedException);
              });
      IOPath<Integer> third = Path.ioPure(3);

      IOPath<Integer> result = PathOps.parZip3(first, second, third, (a, b, c) -> a + b + c);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasCause(checkedException);
    }

    @Test
    @DisplayName("parZip4() wraps checked exception in RuntimeException")
    void parZip4WrapsCheckedException() {
      Exception checkedException = new Exception("parZip4 checked exception");
      IOPath<Integer> first = Path.ioPure(1);
      IOPath<Integer> second = Path.ioPure(2);
      IOPath<Integer> third =
          Path.io(
              () -> {
                throw sneakyThrow(checkedException);
              });
      IOPath<Integer> fourth = Path.ioPure(4);

      IOPath<Integer> result =
          PathOps.parZip4(first, second, third, fourth, (a, b, c, d) -> a + b + c + d);

      assertThatThrownBy(result::unsafeRun)
          .isInstanceOf(RuntimeException.class)
          .hasCause(checkedException);
    }

    @Test
    @DisplayName(
        "firstCompletedSuccess() handles failure after all others completed - result already done")
    void firstCompletedSuccessFailureWhenResultAlreadyDone() throws Exception {
      CompletableFuture<String> f1 = new CompletableFuture<>();
      CompletableFuture<String> f2 = new CompletableFuture<>();

      List<CompletableFuturePath<String>> paths =
          List.of(CompletableFuturePath.fromFuture(f1), CompletableFuturePath.fromFuture(f2));

      CompletableFuturePath<String> result = PathOps.firstCompletedSuccess(paths);

      // Complete one successfully first
      f1.complete("success");

      // Then fail the other - exercises the ex != null branch where result.isDone()
      // and failures.size() == paths.size() but result.isDone() is true
      f2.completeExceptionally(new RuntimeException("too late failure"));

      assertThat(result.toCompletableFuture().get(1, TimeUnit.SECONDS)).isEqualTo("success");
    }

    @Test
    @DisplayName("raceIO() handles success arriving when result is already done")
    void raceIOSuccessWhenResultAlreadyDone() {
      // Two fast-completing IOPaths. Both complete successfully but only the first
      // result.complete() succeeds - the second hits the ex == null && result.isDone() branch.
      IOPath<String> fast1 = Path.ioPure("first");
      IOPath<String> fast2 = Path.ioPure("second");

      List<IOPath<String>> paths = List.of(fast1, fast2);

      IOPath<String> result = PathOps.raceIO(paths);

      String winner = result.unsafeRun();
      assertThat(winner).isIn("first", "second");
    }

    @Test
    @DisplayName("raceIO() handles failure when result is already done (success arrived first)")
    void raceIOFailureWhenResultAlreadyDone() {
      // One succeeds immediately, then the other fails. The failure path hits
      // ex != null, but result.isDone() is true, so completeExceptionally is not called.
      CountDownLatch successDone = new CountDownLatch(1);
      IOPath<String> succeeds =
          Path.io(
              () -> {
                String value = "won";
                successDone.countDown();
                return value;
              });
      IOPath<String> failsLater =
          Path.io(
              () -> {
                Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .until(() -> successDone.getCount() == 0);
                throw new RuntimeException("too late");
              });

      List<IOPath<String>> paths = List.of(succeeds, failsLater);

      IOPath<String> result = PathOps.raceIO(paths);

      assertThat(result.unsafeRun()).isEqualTo("won");
    }

    @Test
    @DisplayName(
        "raceIO() all fail but result completed by late success before last failure count check")
    void raceIOAllFailButResultDone() {
      // Three IOPaths: two fail, one succeeds. The two failures increment failure count
      // but the success completes result first, so failures.size() == paths.size() &&
      // !result.isDone()
      // is never true. Instead, some failures happen after result is already done.
      CountDownLatch successDone = new CountDownLatch(1);
      IOPath<String> fail1 =
          Path.io(
              () -> {
                Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .until(() -> successDone.getCount() == 0);
                throw new RuntimeException("fail1");
              });
      IOPath<String> succeed =
          Path.io(
              () -> {
                String value = "success";
                successDone.countDown();
                return value;
              });
      IOPath<String> fail2 =
          Path.io(
              () -> {
                Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .until(() -> successDone.getCount() == 0);
                throw new RuntimeException("fail2");
              });

      List<IOPath<String>> paths = List.of(fail1, succeed, fail2);

      IOPath<String> result = PathOps.raceIO(paths);

      assertThat(result.unsafeRun()).isEqualTo("success");
    }

    @Test
    @DisplayName("raceIO() wraps checked exception in RuntimeException when all fail")
    void raceIOWrapsCheckedException() {
      Exception checkedException1 = new Exception("raceIO checked exception 1");
      Exception checkedException2 = new Exception("raceIO checked exception 2");
      IOPath<String> failing1 =
          Path.io(
              () -> {
                throw sneakyThrow(checkedException1);
              });
      IOPath<String> failing2 =
          Path.io(
              () -> {
                throw sneakyThrow(checkedException2);
              });

      // Need at least 2 paths to test the exception wrapping code path
      // (single path optimization returns the path directly)
      List<IOPath<String>> paths = List.of(failing1, failing2);

      IOPath<String> result = PathOps.raceIO(paths);

      assertThatThrownBy(result::unsafeRun).isInstanceOf(RuntimeException.class);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneakyThrow(Throwable e) throws E {
      throw (E) e;
    }
  }

  @Nested
  @DisplayName("Edge Cases and Null Validation")
  class EdgeCasesTests {

    @Test
    @DisplayName("All sequence methods validate non-null paths")
    void allSequenceMethodsValidateNonNullPaths() {
      assertThatNullPointerException().isThrownBy(() -> PathOps.sequenceMaybe(null));

      assertThatNullPointerException().isThrownBy(() -> PathOps.sequenceEither(null));

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.sequenceValidated(null, LIST_SEMIGROUP));

      assertThatNullPointerException().isThrownBy(() -> PathOps.sequenceTry(null));

      assertThatNullPointerException().isThrownBy(() -> PathOps.sequenceOptional(null));
    }

    @Test
    @DisplayName("All traverse methods validate non-null parameters")
    void allTraverseMethodsValidateNonNullParameters() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.traverseMaybe(null, s -> Path.just(s)));

      assertThatNullPointerException().isThrownBy(() -> PathOps.traverseMaybe(List.of(), null));

      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.traverseEither(null, s -> Path.right(s)));

      assertThatNullPointerException().isThrownBy(() -> PathOps.traverseEither(List.of(), null));
    }

    @Test
    @DisplayName("sequenceValidated() validates non-null semigroup")
    void sequenceValidatedValidatesNonNullSemigroup() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.sequenceValidated(List.of(), null))
          .withMessageContaining("semigroup must not be null");
    }

    @Test
    @DisplayName("traverseValidated() validates non-null semigroup")
    void traverseValidatedValidatesNonNullSemigroup() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  PathOps.traverseValidated(List.of("a"), s -> Path.valid(s, LIST_SEMIGROUP), null))
          .withMessageContaining("semigroup must not be null");
    }
  }

  @Nested
  @DisplayName("Each-based Traversal Operations")
  class EachBasedTraversalTests {

    @Nested
    @DisplayName("traverseEachMaybe() Operation")
    class TraverseEachMaybeTests {

      @Test
      @DisplayName("traverseEachMaybe() traverses list structure successfully")
      void traverseEachMaybeTraversesList() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();
        List<Integer> list = List.of(1, 2, 3);

        MaybePath<List<Integer>> result =
            PathOps.traverseEachMaybe(list, listEach, n -> Path.just(n * 2));

        assertThat(result.run().isJust()).isTrue();
        assertThat(result.run().get()).containsExactly(2, 4, 6);
      }

      @Test
      @DisplayName("traverseEachMaybe() returns Nothing if any element fails")
      void traverseEachMaybeReturnsNothingOnFailure() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();
        List<Integer> list = List.of(1, -1, 3);

        MaybePath<List<Integer>> result =
            PathOps.traverseEachMaybe(
                list, listEach, n -> n > 0 ? Path.just(n * 2) : Path.nothing());

        assertThat(result.run().isNothing()).isTrue();
      }

      @Test
      @DisplayName("traverseEachMaybe() handles empty structure")
      void traverseEachMaybeHandlesEmptyStructure() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();
        List<Integer> emptyList = List.of();

        MaybePath<List<Integer>> result =
            PathOps.traverseEachMaybe(emptyList, listEach, n -> Path.just(n * 2));

        assertThat(result.run().isJust()).isTrue();
        assertThat(result.run().get()).isEmpty();
      }

      @Test
      @DisplayName("traverseEachMaybe() works with Optional structure")
      void traverseEachMaybeWithOptional() {
        Each<Optional<String>, String> optionalEach = EachInstances.optionalEach();
        Optional<String> opt = Optional.of("hello");

        MaybePath<List<String>> result =
            PathOps.traverseEachMaybe(opt, optionalEach, s -> Path.just(s.toUpperCase()));

        assertThat(result.run().isJust()).isTrue();
        assertThat(result.run().get()).containsExactly("HELLO");
      }

      @Test
      @DisplayName("traverseEachMaybe() works with empty Optional")
      void traverseEachMaybeWithEmptyOptional() {
        Each<Optional<String>, String> optionalEach = EachInstances.optionalEach();
        Optional<String> opt = Optional.empty();

        MaybePath<List<String>> result =
            PathOps.traverseEachMaybe(opt, optionalEach, s -> Path.just(s.toUpperCase()));

        assertThat(result.run().isJust()).isTrue();
        assertThat(result.run().get()).isEmpty();
      }

      @Test
      @DisplayName("traverseEachMaybe() works with Set structure")
      void traverseEachMaybeWithSet() {
        Each<Set<Integer>, Integer> setEach = EachInstances.setEach();
        Set<Integer> set = Set.of(1, 2, 3);

        MaybePath<List<Integer>> result =
            PathOps.traverseEachMaybe(set, setEach, n -> Path.just(n * 10));

        assertThat(result.run().isJust()).isTrue();
        assertThat(result.run().get()).containsExactlyInAnyOrder(10, 20, 30);
      }

      @Test
      @DisplayName("traverseEachMaybe() validates non-null structure")
      void traverseEachMaybeValidatesNonNullStructure() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();

        assertThatNullPointerException()
            .isThrownBy(() -> PathOps.traverseEachMaybe(null, listEach, n -> Path.just(n)))
            .withMessageContaining("structure must not be null");
      }

      @Test
      @DisplayName("traverseEachMaybe() validates non-null each")
      void traverseEachMaybeValidatesNonNullEach() {
        List<Integer> list = List.of(1, 2, 3);

        assertThatNullPointerException()
            .isThrownBy(() -> PathOps.traverseEachMaybe(list, null, n -> Path.just(n)))
            .withMessageContaining("each must not be null");
      }

      @Test
      @DisplayName("traverseEachMaybe() validates non-null function")
      void traverseEachMaybeValidatesNonNullFunction() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();
        List<Integer> list = List.of(1, 2, 3);

        assertThatNullPointerException()
            .isThrownBy(() -> PathOps.traverseEachMaybe(list, listEach, null))
            .withMessageContaining("f must not be null");
      }
    }

    @Nested
    @DisplayName("traverseEachEither() Operation")
    class TraverseEachEitherTests {

      @Test
      @DisplayName("traverseEachEither() traverses list structure successfully")
      void traverseEachEitherTraversesList() {
        Each<List<String>, String> listEach = EachInstances.listEach();
        List<String> list = List.of("a", "b", "c");

        EitherPath<String, List<String>> result =
            PathOps.traverseEachEither(list, listEach, s -> Path.right(s.toUpperCase()));

        assertThat(result.run().isRight()).isTrue();
        assertThat(result.run().getRight()).containsExactly("A", "B", "C");
      }

      @Test
      @DisplayName("traverseEachEither() returns first Left error")
      void traverseEachEitherReturnsFirstError() {
        Each<List<String>, String> listEach = EachInstances.listEach();
        List<String> list = List.of("valid", "", "also-valid");

        EitherPath<String, List<String>> result =
            PathOps.traverseEachEither(
                list,
                listEach,
                s -> {
                  if (s.isEmpty()) {
                    return Path.left("Empty string not allowed");
                  }
                  return Path.right(s.toUpperCase());
                });

        assertThat(result.run().isLeft()).isTrue();
        assertThat(result.run().getLeft()).isEqualTo("Empty string not allowed");
      }

      @Test
      @DisplayName("traverseEachEither() handles empty structure")
      void traverseEachEitherHandlesEmptyStructure() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();
        List<Integer> emptyList = List.of();

        EitherPath<String, List<Integer>> result =
            PathOps.traverseEachEither(emptyList, listEach, n -> Path.right(n * 2));

        assertThat(result.run().isRight()).isTrue();
        assertThat(result.run().getRight()).isEmpty();
      }

      @Test
      @DisplayName("traverseEachEither() works with Map values")
      void traverseEachEitherWithMapValues() {
        Each<Map<String, Integer>, Integer> mapEach = EachInstances.mapValuesEach();
        Map<String, Integer> map = Map.of("a", 1, "b", 2);

        EitherPath<String, List<Integer>> result =
            PathOps.traverseEachEither(map, mapEach, n -> Path.right(n * 100));

        assertThat(result.run().isRight()).isTrue();
        assertThat(result.run().getRight()).containsExactlyInAnyOrder(100, 200);
      }

      @Test
      @DisplayName("traverseEachEither() validates non-null structure")
      void traverseEachEitherValidatesNonNullStructure() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();

        assertThatNullPointerException()
            .isThrownBy(() -> PathOps.traverseEachEither(null, listEach, n -> Path.right(n)))
            .withMessageContaining("structure must not be null");
      }

      @Test
      @DisplayName("traverseEachEither() validates non-null each")
      void traverseEachEitherValidatesNonNullEach() {
        List<Integer> list = List.of(1, 2, 3);

        assertThatNullPointerException()
            .isThrownBy(() -> PathOps.traverseEachEither(list, null, n -> Path.right(n)))
            .withMessageContaining("each must not be null");
      }

      @Test
      @DisplayName("traverseEachEither() validates non-null function")
      void traverseEachEitherValidatesNonNullFunction() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();
        List<Integer> list = List.of(1, 2, 3);

        assertThatNullPointerException()
            .isThrownBy(() -> PathOps.traverseEachEither(list, listEach, null))
            .withMessageContaining("f must not be null");
      }
    }

    @Nested
    @DisplayName("traverseEachValidated() Operation")
    class TraverseEachValidatedTests {

      @Test
      @DisplayName("traverseEachValidated() traverses list structure successfully")
      void traverseEachValidatedTraversesList() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();
        List<Integer> list = List.of(1, 2, 3);

        ValidationPath<List<String>, List<Integer>> result =
            PathOps.traverseEachValidated(
                list, listEach, n -> Path.valid(n * 2, LIST_SEMIGROUP), LIST_SEMIGROUP);

        assertThat(result.run().isValid()).isTrue();
        assertThat(result.run().get()).containsExactly(2, 4, 6);
      }

      @Test
      @DisplayName("traverseEachValidated() accumulates all errors")
      void traverseEachValidatedAccumulatesErrors() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();
        List<Integer> list = List.of(1, -1, 2, -2, 3);

        ValidationPath<List<String>, List<Integer>> result =
            PathOps.traverseEachValidated(
                list,
                listEach,
                n ->
                    n > 0
                        ? Path.valid(n, LIST_SEMIGROUP)
                        : Path.invalid(List.of("Invalid: " + n), LIST_SEMIGROUP),
                LIST_SEMIGROUP);

        assertThat(result.run().isInvalid()).isTrue();
        assertThat(result.run().getError()).containsExactly("Invalid: -1", "Invalid: -2");
      }

      @Test
      @DisplayName("traverseEachValidated() handles empty structure")
      void traverseEachValidatedHandlesEmptyStructure() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();
        List<Integer> emptyList = List.of();

        ValidationPath<List<String>, List<Integer>> result =
            PathOps.traverseEachValidated(
                emptyList, listEach, n -> Path.valid(n * 2, LIST_SEMIGROUP), LIST_SEMIGROUP);

        assertThat(result.run().isValid()).isTrue();
        assertThat(result.run().get()).isEmpty();
      }

      @Test
      @DisplayName("traverseEachValidated() validates non-null structure")
      void traverseEachValidatedValidatesNonNullStructure() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();

        assertThatNullPointerException()
            .isThrownBy(
                () ->
                    PathOps.traverseEachValidated(
                        null, listEach, n -> Path.valid(n, LIST_SEMIGROUP), LIST_SEMIGROUP))
            .withMessageContaining("structure must not be null");
      }

      @Test
      @DisplayName("traverseEachValidated() validates non-null each")
      void traverseEachValidatedValidatesNonNullEach() {
        List<Integer> list = List.of(1, 2, 3);

        assertThatNullPointerException()
            .isThrownBy(
                () ->
                    PathOps.traverseEachValidated(
                        list, null, n -> Path.valid(n, LIST_SEMIGROUP), LIST_SEMIGROUP))
            .withMessageContaining("each must not be null");
      }

      @Test
      @DisplayName("traverseEachValidated() validates non-null function")
      void traverseEachValidatedValidatesNonNullFunction() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();
        List<Integer> list = List.of(1, 2, 3);

        assertThatNullPointerException()
            .isThrownBy(() -> PathOps.traverseEachValidated(list, listEach, null, LIST_SEMIGROUP))
            .withMessageContaining("f must not be null");
      }

      @Test
      @DisplayName("traverseEachValidated() validates non-null semigroup")
      void traverseEachValidatedValidatesNonNullSemigroup() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();
        List<Integer> list = List.of(1, 2, 3);

        assertThatNullPointerException()
            .isThrownBy(
                () ->
                    PathOps.traverseEachValidated(
                        list, listEach, n -> Path.valid(n, LIST_SEMIGROUP), null))
            .withMessageContaining("semigroup must not be null");
      }
    }

    @Nested
    @DisplayName("traverseEachTry() Operation")
    class TraverseEachTryTests {

      @Test
      @DisplayName("traverseEachTry() traverses list structure successfully")
      void traverseEachTryTraversesList() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();
        List<Integer> list = List.of(1, 2, 3);

        TryPath<List<Integer>> result =
            PathOps.traverseEachTry(list, listEach, n -> Path.success(n * 2));

        assertThat(result.run().isSuccess()).isTrue();
        assertThat(result.run().orElse(null)).containsExactly(2, 4, 6);
      }

      @Test
      @DisplayName("traverseEachTry() returns first failure")
      void traverseEachTryReturnsFirstFailure() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();
        List<Integer> list = List.of(1, -1, 3);

        TryPath<List<Integer>> result =
            PathOps.traverseEachTry(
                list,
                listEach,
                n -> {
                  if (n < 0) {
                    return Path.failure(new IllegalArgumentException("Negative: " + n));
                  }
                  return Path.success(n * 2);
                });

        assertThat(result.run().isFailure()).isTrue();
      }

      @Test
      @DisplayName("traverseEachTry() handles empty structure")
      void traverseEachTryHandlesEmptyStructure() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();
        List<Integer> emptyList = List.of();

        TryPath<List<Integer>> result =
            PathOps.traverseEachTry(emptyList, listEach, n -> Path.success(n * 2));

        assertThat(result.run().isSuccess()).isTrue();
        assertThat(result.run().orElse(null)).isEmpty();
      }

      @Test
      @DisplayName("traverseEachTry() works with array structure")
      void traverseEachTryWithArray() {
        Each<String[], String> arrayEach = EachInstances.arrayEach();
        String[] array = {"hello", "world"};

        TryPath<List<String>> result =
            PathOps.traverseEachTry(array, arrayEach, s -> Path.success(s.toUpperCase()));

        assertThat(result.run().isSuccess()).isTrue();
        assertThat(result.run().orElse(null)).containsExactly("HELLO", "WORLD");
      }

      @Test
      @DisplayName("traverseEachTry() validates non-null structure")
      void traverseEachTryValidatesNonNullStructure() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();

        assertThatNullPointerException()
            .isThrownBy(() -> PathOps.traverseEachTry(null, listEach, n -> Path.success(n)))
            .withMessageContaining("structure must not be null");
      }

      @Test
      @DisplayName("traverseEachTry() validates non-null each")
      void traverseEachTryValidatesNonNullEach() {
        List<Integer> list = List.of(1, 2, 3);

        assertThatNullPointerException()
            .isThrownBy(() -> PathOps.traverseEachTry(list, null, n -> Path.success(n)))
            .withMessageContaining("each must not be null");
      }

      @Test
      @DisplayName("traverseEachTry() validates non-null function")
      void traverseEachTryValidatesNonNullFunction() {
        Each<List<Integer>, Integer> listEach = EachInstances.listEach();
        List<Integer> list = List.of(1, 2, 3);

        assertThatNullPointerException()
            .isThrownBy(() -> PathOps.traverseEachTry(list, listEach, null))
            .withMessageContaining("f must not be null");
      }
    }

    @Nested
    @DisplayName("Each Integration with Various Types")
    class EachIntegrationTests {

      @Test
      @DisplayName("traverseEach* methods work with String chars")
      void traverseEachWithStringChars() {
        Each<String, Character> stringEach = EachInstances.stringCharsEach();
        String str = "abc";

        MaybePath<List<Character>> result =
            PathOps.traverseEachMaybe(str, stringEach, c -> Path.just(Character.toUpperCase(c)));

        assertThat(result.run().isJust()).isTrue();
        assertThat(result.run().get()).containsExactly('A', 'B', 'C');
      }

      @Test
      @DisplayName("traverseEach* methods work with custom Each from Traversal")
      void traverseEachWithCustomEach() {
        // Create Each from existing traversal
        Each<List<String>, String> customEach = Each.fromTraversal(Traversals.forList());
        List<String> list = List.of("a", "b", "c");

        EitherPath<String, List<String>> result =
            PathOps.traverseEachEither(list, customEach, s -> Path.right(s + "!"));

        assertThat(result.run().isRight()).isTrue();
        assertThat(result.run().getRight()).containsExactly("a!", "b!", "c!");
      }
    }
  }
}
