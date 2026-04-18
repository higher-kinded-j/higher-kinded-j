// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.function.Function;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;

/**
 * Examples demonstrating {@link EitherPath#bimap} for simultaneously transforming both the error
 * and the success tracks of an {@code EitherPath}.
 *
 * <p>{@code bimap(errorFn, successFn)} is equivalent to {@code .mapError(errorFn).map(successFn)}
 * but expressed in one call. It is useful when an operation's error and success types both need to
 * change at the same boundary, for example when adapting between an internal representation and a
 * public API response.
 *
 * <p>Run from an IDE or via {@code gradle :hkj-examples:run --args="EitherPathBimapExample"} or
 * invoking {@link #main(String[])} directly.
 */
public class EitherPathBimapExample {

  public static void main(String[] args) {
    EitherPathBimapExample example = new EitherPathBimapExample();
    example.transformBothSides();
    example.equivalenceWithMapErrorThenMap();
    example.realisticBoundaryTransform();
  }

  /** Basic demonstration: transform both error and success types at once. */
  public void transformBothSides() {
    EitherPath<String, Integer> original = Path.right(42);

    EitherPath<Integer, String> transformed =
        original.bimap(
            String::length, // Left<String>  -> Left<Integer>
            n -> "Value: " + n // Right<Integer> -> Right<String>
            );

    Either<Integer, String> result = transformed.run();
    assertEquals("Value: 42", result.getRight());

    // On the Left side, only the error mapper runs:
    EitherPath<String, Integer> failing = Path.left("file not found");
    EitherPath<Integer, String> reshaped = failing.bimap(String::length, n -> "v=" + n);
    assertEquals(14, reshaped.run().getLeft().intValue());
    System.out.println("transformBothSides: ok");
  }

  /** {@code bimap(f, g)} is identical to {@code mapError(f).map(g)}. */
  public void equivalenceWithMapErrorThenMap() {
    Function<String, Integer> lfn = String::length;
    Function<Integer, String> rfn = n -> "n=" + n;

    EitherPath<String, Integer> rightPath = Path.right(7);
    assertEquals(rightPath.bimap(lfn, rfn).run(), rightPath.mapError(lfn).map(rfn).run());

    EitherPath<String, Integer> leftPath = Path.left("oops");
    assertEquals(leftPath.bimap(lfn, rfn).run(), leftPath.mapError(lfn).map(rfn).run());
    System.out.println("equivalenceWithMapErrorThenMap: ok");
  }

  /**
   * Realistic example: converting an internal validation result into a public API response at a
   * service boundary. Both sides change type simultaneously.
   */
  public void realisticBoundaryTransform() {
    // Internal domain result: raw user record with a textual parse error
    EitherPath<String, RawUser> internal = Path.right(new RawUser("alice", 30));

    // Public API boundary: wrap error into an ApiError record and success into a UserDto
    EitherPath<ApiError, UserDto> external =
        internal.bimap(
            msg -> new ApiError("BAD_INPUT", msg),
            raw -> new UserDto(raw.name().toUpperCase(), raw.age()));

    Either<ApiError, UserDto> apiResponse = external.run();
    assertEquals(new UserDto("ALICE", 30), apiResponse.getRight());
    System.out.println("realisticBoundaryTransform: ok");
  }

  // ===== Supporting records for the realistic example =====

  record RawUser(String name, int age) {}

  record UserDto(String name, int age) {}

  record ApiError(String code, String message) {}

  private static <T> void assertEquals(T expected, T actual) {
    if (expected == null ? actual != null : !expected.equals(actual)) {
      throw new AssertionError("expected=" + expected + ", actual=" + actual);
    }
  }
}
