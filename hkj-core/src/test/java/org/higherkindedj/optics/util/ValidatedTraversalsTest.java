// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedTraversals Utility Class Tests")
class ValidatedTraversalsTest {

  @Nested
  @DisplayName("valid() - Traversal for Valid case")
  class ValidTraversal {
    private final Traversal<Validated<String, Integer>, Integer> traversal =
        ValidatedTraversals.valid();

    @Test
    @DisplayName("should modify value in Valid")
    void shouldModifyValid() {
      Validated<String, Integer> validated = Validated.valid(42);
      Validated<String, Integer> result = Traversals.modify(traversal, i -> i * 2, validated);
      assertThat(result.isValid()).isTrue();
      assertThat(result.get()).isEqualTo(84);
    }

    @Test
    @DisplayName("should not modify Invalid")
    void shouldNotModifyInvalid() {
      Validated<String, Integer> validated = Validated.invalid("error");
      Validated<String, Integer> result = Traversals.modify(traversal, i -> i * 2, validated);
      assertThat(result.isInvalid()).isTrue();
      assertThat(result.getError()).isEqualTo("error");
    }

    @Test
    @DisplayName("should extract value from Valid")
    void shouldExtractFromValid() {
      Validated<String, Integer> validated = Validated.valid(42);
      List<Integer> result = Traversals.getAll(traversal, validated);
      assertThat(result).containsExactly(42);
    }

    @Test
    @DisplayName("should return empty list for Invalid")
    void shouldReturnEmptyForInvalid() {
      Validated<String, Integer> validated = Validated.invalid("error");
      List<Integer> result = Traversals.getAll(traversal, validated);
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("invalid() - Traversal for Invalid case")
  class InvalidTraversal {
    private final Traversal<Validated<String, Integer>, String> traversal =
        ValidatedTraversals.invalid();

    @Test
    @DisplayName("should modify error in Invalid")
    void shouldModifyInvalid() {
      Validated<String, Integer> validated = Validated.invalid("error");
      Validated<String, Integer> result =
          Traversals.modify(traversal, s -> "Validation Error: " + s, validated);
      assertThat(result.isInvalid()).isTrue();
      assertThat(result.getError()).isEqualTo("Validation Error: error");
    }

    @Test
    @DisplayName("should not modify Valid")
    void shouldNotModifyValid() {
      Validated<String, Integer> validated = Validated.valid(42);
      Validated<String, Integer> result =
          Traversals.modify(traversal, s -> "Validation Error: " + s, validated);
      assertThat(result.isValid()).isTrue();
      assertThat(result.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("should extract error from Invalid")
    void shouldExtractFromInvalid() {
      Validated<String, Integer> validated = Validated.invalid("error");
      List<String> result = Traversals.getAll(traversal, validated);
      assertThat(result).containsExactly("error");
    }

    @Test
    @DisplayName("should return empty list for Valid")
    void shouldReturnEmptyForValid() {
      Validated<String, Integer> validated = Validated.valid(42);
      List<String> result = Traversals.getAll(traversal, validated);
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("validPrism() - Prism for Valid case")
  class ValidPrismMethod {
    private final Prism<Validated<String, Integer>, Integer> prism =
        ValidatedTraversals.validPrism();

    @Test
    @DisplayName("should extract value from Valid")
    void shouldExtractFromValid() {
      Validated<String, Integer> validated = Validated.valid(42);
      Optional<Integer> result = prism.getOptional(validated);
      assertThat(result).isPresent().contains(42);
    }

    @Test
    @DisplayName("should return empty for Invalid")
    void shouldReturnEmptyForInvalid() {
      Validated<String, Integer> validated = Validated.invalid("error");
      Optional<Integer> result = prism.getOptional(validated);
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("invalidPrism() - Prism for Invalid case")
  class InvalidPrismMethod {
    private final Prism<Validated<String, Integer>, String> prism =
        ValidatedTraversals.invalidPrism();

    @Test
    @DisplayName("should extract error from Invalid")
    void shouldExtractFromInvalid() {
      Validated<String, Integer> validated = Validated.invalid("error");
      Optional<String> result = prism.getOptional(validated);
      assertThat(result).isPresent().contains("error");
    }

    @Test
    @DisplayName("should return empty for Valid")
    void shouldReturnEmptyForValid() {
      Validated<String, Integer> validated = Validated.valid(42);
      Optional<String> result = prism.getOptional(validated);
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {
    @Test
    @DisplayName("should collect all valid results from list")
    void shouldCollectAllValid() {
      List<Validated<String, Integer>> validated =
          List.of(Validated.valid(42), Validated.invalid("error1"), Validated.valid(100));
      Traversal<List<Validated<String, Integer>>, Integer> allValid =
          Traversals.<Validated<String, Integer>>forList().andThen(ValidatedTraversals.valid());

      List<Integer> values = Traversals.getAll(allValid, validated);
      assertThat(values).containsExactly(42, 100);
    }

    @Test
    @DisplayName("should collect all validation errors from list")
    void shouldCollectAllErrors() {
      List<Validated<String, Integer>> validated =
          List.of(Validated.valid(42), Validated.invalid("error1"), Validated.invalid("error2"));
      Traversal<List<Validated<String, Integer>>, String> allErrors =
          Traversals.<Validated<String, Integer>>forList().andThen(ValidatedTraversals.invalid());

      List<String> errors = Traversals.getAll(allErrors, validated);
      assertThat(errors).containsExactly("error1", "error2");
    }

    @Test
    @DisplayName("should modify all valid values")
    void shouldModifyAllValid() {
      List<Validated<String, Integer>> validated =
          List.of(Validated.valid(1), Validated.invalid("error"), Validated.valid(2));
      Traversal<List<Validated<String, Integer>>, Integer> allValid =
          Traversals.<Validated<String, Integer>>forList().andThen(ValidatedTraversals.valid());

      List<Validated<String, Integer>> result = Traversals.modify(allValid, i -> i * 10, validated);
      assertThat(result)
          .containsExactly(Validated.valid(10), Validated.invalid("error"), Validated.valid(20));
    }

    @Test
    @DisplayName("should enrich all validation errors")
    void shouldEnrichAllErrors() {
      List<Validated<String, Integer>> validated =
          List.of(Validated.invalid("error1"), Validated.valid(42), Validated.invalid("error2"));
      Traversal<List<Validated<String, Integer>>, String> allErrors =
          Traversals.<Validated<String, Integer>>forList().andThen(ValidatedTraversals.invalid());

      List<Validated<String, Integer>> result =
          Traversals.modify(allErrors, s -> "[ERROR] " + s, validated);
      assertThat(result)
          .containsExactly(
              Validated.invalid("[ERROR] error1"),
              Validated.valid(42),
              Validated.invalid("[ERROR] error2"));
    }
  }
}
