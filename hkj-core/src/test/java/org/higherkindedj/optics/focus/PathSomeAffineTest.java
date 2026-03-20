// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.util.Affines;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for the {@code some(Affine)} overloads on FocusPath, AffinePath, and TraversalPath. */
class PathSomeAffineTest {

  record Container(Either<String, Integer> value) {}

  static final Lens<Container, Either<String, Integer>> VALUE_LENS =
      Lens.of(Container::value, (c, v) -> new Container(v));

  @Nested
  @DisplayName("FocusPath.some(Affine)")
  class FocusPathSomeAffine {

    @Test
    @DisplayName("should return AffinePath with correct getOptional for Right")
    void shouldReturnAffinePathForRight() {
      FocusPath<Container, Either<String, Integer>> path = FocusPath.of(VALUE_LENS);
      AffinePath<Container, Integer> result = path.some(Affines.eitherRight());

      Container container = new Container(Either.right(42));
      Optional<Integer> value = result.getOptional(container);

      assertTrue(value.isPresent());
      assertEquals(42, value.get());
    }

    @Test
    @DisplayName("should return empty for Left")
    void shouldReturnEmptyForLeft() {
      FocusPath<Container, Either<String, Integer>> path = FocusPath.of(VALUE_LENS);
      AffinePath<Container, Integer> result = path.some(Affines.eitherRight());

      Container container = new Container(Either.left("error"));
      Optional<Integer> value = result.getOptional(container);

      assertTrue(value.isEmpty());
    }

    @Test
    @DisplayName("should set value correctly")
    void shouldSetValueCorrectly() {
      FocusPath<Container, Either<String, Integer>> path = FocusPath.of(VALUE_LENS);
      AffinePath<Container, Integer> result = path.some(Affines.eitherRight());

      Container container = new Container(Either.right(42));
      Container updated = result.set(100, container);

      assertTrue(updated.value().isRight());
      assertEquals(100, updated.value().getRight());
    }
  }

  @Nested
  @DisplayName("AffinePath.some(Affine)")
  class AffinePathSomeAffine {

    @Test
    @DisplayName("should compose correctly with existing AffinePath")
    void shouldComposeCorrectly() {
      Affine<Optional<Either<String, Integer>>, Either<String, Integer>> someAffine =
          Affines.some();
      AffinePath<Optional<Either<String, Integer>>, Either<String, Integer>> optPath =
          AffinePath.of(someAffine);

      AffinePath<Optional<Either<String, Integer>>, Integer> result =
          optPath.some(Affines.eitherRight());

      Optional<Either<String, Integer>> source = Optional.of(Either.right(42));
      Optional<Integer> value = result.getOptional(source);

      assertTrue(value.isPresent());
      assertEquals(42, value.get());
    }

    @Test
    @DisplayName("should return empty when outer AffinePath has no focus")
    void shouldReturnEmptyWhenOuterEmpty() {
      Affine<Optional<Either<String, Integer>>, Either<String, Integer>> someAffine =
          Affines.some();
      AffinePath<Optional<Either<String, Integer>>, Either<String, Integer>> optPath =
          AffinePath.of(someAffine);

      AffinePath<Optional<Either<String, Integer>>, Integer> result =
          optPath.some(Affines.eitherRight());

      Optional<Either<String, Integer>> source = Optional.empty();
      Optional<Integer> value = result.getOptional(source);

      assertTrue(value.isEmpty());
    }
  }

  @Nested
  @DisplayName("TraversalPath.some(Affine)")
  class TraversalPathSomeAffine {

    @Test
    @DisplayName("should return TraversalPath filtering to present values")
    void shouldReturnTraversalPathFilteringPresent() {
      FocusPath<List<Either<String, Integer>>, List<Either<String, Integer>>> idPath =
          FocusPath.of(Lens.of(l -> l, (l, v) -> v));

      TraversalPath<List<Either<String, Integer>>, Either<String, Integer>> eachPath =
          idPath.each();

      TraversalPath<List<Either<String, Integer>>, Integer> result =
          eachPath.some(Affines.eitherRight());

      List<Either<String, Integer>> source =
          List.of(Either.right(1), Either.left("err"), Either.right(3));

      List<Integer> values = result.getAll(source);
      assertEquals(List.of(1, 3), values);
    }
  }
}
