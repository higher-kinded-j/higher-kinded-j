// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.hkt.Semigroup;
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
}
