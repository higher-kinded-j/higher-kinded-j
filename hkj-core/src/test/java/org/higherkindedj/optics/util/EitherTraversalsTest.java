// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherTraversals Utility Class Tests")
class EitherTraversalsTest {

  @Nested
  @DisplayName("right() - Traversal for Right case")
  class RightTraversal {
    private final Traversal<Either<String, Integer>, Integer> traversal = EitherTraversals.right();

    @Test
    @DisplayName("should modify value in Right")
    void shouldModifyRight() {
      Either<String, Integer> either = Either.right(42);
      Either<String, Integer> result = Traversals.modify(traversal, i -> i * 2, either);
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(84);
    }

    @Test
    @DisplayName("should not modify Left")
    void shouldNotModifyLeft() {
      Either<String, Integer> either = Either.left("error");
      Either<String, Integer> result = Traversals.modify(traversal, i -> i * 2, either);
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("error");
    }

    @Test
    @DisplayName("should extract value from Right")
    void shouldExtractFromRight() {
      Either<String, Integer> either = Either.right(42);
      List<Integer> result = Traversals.getAll(traversal, either);
      assertThat(result).containsExactly(42);
    }

    @Test
    @DisplayName("should return empty list for Left")
    void shouldReturnEmptyForLeft() {
      Either<String, Integer> either = Either.left("error");
      List<Integer> result = Traversals.getAll(traversal, either);
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("left() - Traversal for Left case")
  class LeftTraversal {
    private final Traversal<Either<String, Integer>, String> traversal = EitherTraversals.left();

    @Test
    @DisplayName("should modify error in Left")
    void shouldModifyLeft() {
      Either<String, Integer> either = Either.left("error");
      Either<String, Integer> result = Traversals.modify(traversal, s -> "Error: " + s, either);
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo("Error: error");
    }

    @Test
    @DisplayName("should not modify Right")
    void shouldNotModifyRight() {
      Either<String, Integer> either = Either.right(42);
      Either<String, Integer> result = Traversals.modify(traversal, s -> "Error: " + s, either);
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(42);
    }

    @Test
    @DisplayName("should extract error from Left")
    void shouldExtractFromLeft() {
      Either<String, Integer> either = Either.left("error");
      List<String> result = Traversals.getAll(traversal, either);
      assertThat(result).containsExactly("error");
    }

    @Test
    @DisplayName("should return empty list for Right")
    void shouldReturnEmptyForRight() {
      Either<String, Integer> either = Either.right(42);
      List<String> result = Traversals.getAll(traversal, either);
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("rightPrism() - Prism for Right case")
  class RightPrismMethod {
    private final Prism<Either<String, Integer>, Integer> prism = EitherTraversals.rightPrism();

    @Test
    @DisplayName("should extract value from Right")
    void shouldExtractFromRight() {
      Either<String, Integer> either = Either.right(42);
      Optional<Integer> result = prism.getOptional(either);
      assertThat(result).isPresent().contains(42);
    }

    @Test
    @DisplayName("should return empty for Left")
    void shouldReturnEmptyForLeft() {
      Either<String, Integer> either = Either.left("error");
      Optional<Integer> result = prism.getOptional(either);
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("leftPrism() - Prism for Left case")
  class LeftPrismMethod {
    private final Prism<Either<String, Integer>, String> prism = EitherTraversals.leftPrism();

    @Test
    @DisplayName("should extract error from Left")
    void shouldExtractFromLeft() {
      Either<String, Integer> either = Either.left("error");
      Optional<String> result = prism.getOptional(either);
      assertThat(result).isPresent().contains("error");
    }

    @Test
    @DisplayName("should return empty for Right")
    void shouldReturnEmptyForRight() {
      Either<String, Integer> either = Either.right(42);
      Optional<String> result = prism.getOptional(either);
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {
    @Test
    @DisplayName("should collect all successes from list")
    void shouldCollectAllSuccesses() {
      List<Either<String, Integer>> eithers =
          List.of(Either.right(42), Either.left("error1"), Either.right(100));
      Traversal<List<Either<String, Integer>>, Integer> allSuccesses =
          Traversals.<Either<String, Integer>>forList().andThen(EitherTraversals.right());

      List<Integer> values = Traversals.getAll(allSuccesses, eithers);
      assertThat(values).containsExactly(42, 100);
    }

    @Test
    @DisplayName("should collect all errors from list")
    void shouldCollectAllErrors() {
      List<Either<String, Integer>> eithers =
          List.of(Either.right(42), Either.left("error1"), Either.left("error2"));
      Traversal<List<Either<String, Integer>>, String> allErrors =
          Traversals.<Either<String, Integer>>forList().andThen(EitherTraversals.left());

      List<String> errors = Traversals.getAll(allErrors, eithers);
      assertThat(errors).containsExactly("error1", "error2");
    }

    @Test
    @DisplayName("should modify all successful values")
    void shouldModifyAllSuccesses() {
      List<Either<String, Integer>> eithers =
          List.of(Either.right(1), Either.left("error"), Either.right(2));
      Traversal<List<Either<String, Integer>>, Integer> allSuccesses =
          Traversals.<Either<String, Integer>>forList().andThen(EitherTraversals.right());

      List<Either<String, Integer>> result = Traversals.modify(allSuccesses, i -> i * 10, eithers);
      assertThat(result).containsExactly(Either.right(10), Either.left("error"), Either.right(20));
    }
  }
}
