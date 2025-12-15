// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
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
