// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article5.typecheck;

import java.util.List;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;

/**
 * A type error with a descriptive message.
 *
 * @param message the error message
 */
public record TypeError(String message) {

  @Override
  public String toString() {
    return message;
  }

  /**
   * Get a Semigroup for combining lists of TypeErrors.
   *
   * <p>This uses Higher-Kinded-J's built-in list semigroup, which concatenates two lists. This is
   * exactly what we need for error accumulation in Validated.
   *
   * @return a Semigroup for List of TypeError
   */
  public static Semigroup<List<TypeError>> semigroup() {
    return Semigroups.list();
  }

  /**
   * Create a single-element error list.
   *
   * @param message the error message
   * @return a list containing one TypeError
   */
  public static List<TypeError> single(String message) {
    return List.of(new TypeError(message));
  }
}
