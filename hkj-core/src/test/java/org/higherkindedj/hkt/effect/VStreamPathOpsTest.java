// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.test.assertions.VStreamPathAssert.assertThatVStreamPath;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for VStreamPath operations in PathOps.
 *
 * <p>Tests cover concatVStream, traverseVStream, and flattenVStream.
 */
@DisplayName("PathOps VStreamPath Operations")
class VStreamPathOpsTest {

  @Nested
  @DisplayName("concatVStream")
  class ConcatVStreamTests {

    @Test
    @DisplayName("concatenates multiple VStreamPaths")
    void concatenatesMultiple() {
      List<VStreamPath<Integer>> paths =
          List.of(Path.vstreamOf(1, 2), Path.vstreamOf(3, 4), Path.vstreamOf(5));

      VStreamPath<Integer> result = PathOps.concatVStream(paths);

      assertThatVStreamPath(result).producesElements(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("returns empty for empty list of paths")
    void returnsEmptyForEmptyList() {
      VStreamPath<Integer> result = PathOps.concatVStream(List.of());

      assertThatVStreamPath(result).isEmpty();
    }

    @Test
    @DisplayName("single path is returned as-is")
    void singlePathReturnedAsIs() {
      VStreamPath<Integer> single = Path.vstreamOf(1, 2, 3);

      VStreamPath<Integer> result = PathOps.concatVStream(List.of(single));

      assertThatVStreamPath(result).producesElements(1, 2, 3);
    }

    @Test
    @DisplayName("concatenates with empty paths")
    void concatenatesWithEmptyPaths() {
      List<VStreamPath<Integer>> paths =
          List.of(Path.vstreamEmpty(), Path.vstreamOf(1, 2), Path.vstreamEmpty());

      VStreamPath<Integer> result = PathOps.concatVStream(paths);

      assertThatVStreamPath(result).producesElements(1, 2);
    }

    @Test
    @DisplayName("validates non-null paths")
    void validatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.concatVStream(null))
          .withMessageContaining("paths must not be null");
    }
  }

  @Nested
  @DisplayName("traverseVStream")
  class TraverseVStreamTests {

    @Test
    @DisplayName("maps and concatenates results")
    void mapsAndConcatenates() {
      List<Integer> items = List.of(1, 2, 3);

      VStreamPath<String> result =
          PathOps.traverseVStream(items, n -> Path.vstreamOf("a:" + n, "b:" + n));

      assertThatVStreamPath(result).producesElements("a:1", "b:1", "a:2", "b:2", "a:3", "b:3");
    }

    @Test
    @DisplayName("returns empty for empty input")
    void returnsEmptyForEmptyInput() {
      VStreamPath<String> result = PathOps.traverseVStream(List.of(), n -> Path.vstreamPure("x"));

      assertThatVStreamPath(result).isEmpty();
    }

    @Test
    @DisplayName("validates non-null items")
    void validatesNonNullItems() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.traverseVStream(null, n -> Path.vstreamPure(n)))
          .withMessageContaining("items must not be null");
    }

    @Test
    @DisplayName("validates non-null function")
    void validatesNonNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.traverseVStream(List.of(1), null))
          .withMessageContaining("f must not be null");
    }
  }

  @Nested
  @DisplayName("flattenVStream")
  class FlattenVStreamTests {

    @Test
    @DisplayName("flattens nested VStreamPaths")
    void flattensNested() {
      VStreamPath<VStreamPath<Integer>> nested =
          Path.vstreamOf(Path.vstreamOf(1, 2), Path.vstreamOf(3, 4), Path.vstreamOf(5));

      VStreamPath<Integer> result = PathOps.flattenVStream(nested);

      assertThatVStreamPath(result).producesElements(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("flattens with empty inner streams")
    void flattensWithEmptyInner() {
      VStreamPath<VStreamPath<Integer>> nested =
          Path.vstreamOf(Path.vstreamOf(1), Path.vstreamEmpty(), Path.vstreamOf(2));

      VStreamPath<Integer> result = PathOps.flattenVStream(nested);

      assertThatVStreamPath(result).producesElements(1, 2);
    }

    @Test
    @DisplayName("validates non-null nested")
    void validatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> PathOps.flattenVStream(null))
          .withMessageContaining("nested must not be null");
    }
  }
}
