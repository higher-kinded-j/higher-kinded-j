// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.effect.capability.Composable;

/**
 * Internal helper that builds the exception a Path throws when an operation that needs its own Path
 * type is handed a different one.
 *
 * <p>Each constant is one such operation, so every Path words its messages the same way. The
 * message is a single line, shown here one sentence per line:
 *
 * <pre>{@code
 * Type mismatch in via(): expected MaybePath but mapper returned IOPath.
 * Each Path type can only chain with the same type.
 * Use conversion methods (toEitherPath, toMaybePath, etc.) to change types.
 * }</pre>
 *
 * <p>Both types are named by their public Path type. {@code VTaskPath} and {@code VStreamPath} are
 * interfaces whose implementation classes a caller does not normally see, so a message names the
 * interface whichever side it is on. A class with no simple name, such as an anonymous {@link
 * Composable}, is named by its binary name, and so are both types when they share a simple name,
 * such as a caller's own class called {@code MaybePath}.
 */
enum PathTypeMismatch {
  /** {@code via()} or {@code flatMap()}, whose mapper returned a different Path type. */
  VIA("via()", "mapper returned", "chain"),

  /** {@code then()}, whose supplier returned a different Path type. */
  THEN("then()", "supplier returned", "chain"),

  /** {@code zipWith()}, whose other path is a different Path type. */
  ZIP_WITH("zipWith()", "other was", "combine");

  /** The public Path interfaces named in place of their implementation classes. */
  private static final List<Class<?>> INTERFACE_PATHS = List.of(VTaskPath.class, VStreamPath.class);

  private final String operation;
  private final String handed;
  private final String verb;

  PathTypeMismatch(String operation, String handed, String verb) {
    this.operation = operation;
    this.handed = handed;
    this.verb = verb;
  }

  /**
   * Builds the exception for this operation.
   *
   * @param expected the Path type the operation needs
   * @param actual the path it was handed instead
   * @return the exception to throw
   */
  IllegalArgumentException exception(Class<?> expected, Composable<?> actual) {
    Class<?> actualType = actual.getClass();
    boolean sameName = pathTypeName(expected).equals(pathTypeName(actualType));
    Function<Class<?>, String> name = sameName ? Class::getName : PathTypeMismatch::pathTypeName;
    return new IllegalArgumentException(
        "Type mismatch in "
            + operation
            + ": expected "
            + name.apply(expected)
            + " but "
            + handed
            + " "
            + name.apply(actualType)
            + ". Each Path type can only "
            + verb
            + " with the same type."
            + " Use conversion methods (toEitherPath, toMaybePath, etc.) to change types.");
  }

  private static String pathTypeName(Class<?> type) {
    Class<?> named =
        INTERFACE_PATHS.stream()
            .filter(path -> path.isAssignableFrom(type))
            .findFirst()
            .orElse(type);
    String simpleName = named.getSimpleName();
    return simpleName.isEmpty() ? named.getName() : simpleName;
  }
}
