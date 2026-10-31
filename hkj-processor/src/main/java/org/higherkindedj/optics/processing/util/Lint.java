// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import java.util.Locale;

/**
 * A javac lint category that generated code may suppress, named by the key {@code SuppressWarnings}
 * takes. The constants are declared in the order of their keys, which is the order a merged
 * suppression lists them in ({@link ProcessorUtils#suppression}).
 *
 * @since 0.6.0
 */
public enum Lint {
  /** A use of an element deprecated without {@code forRemoval}. */
  DEPRECATION,
  /** A raw type written out or inferred. */
  RAWTYPES,
  /**
   * A use of an element deprecated with {@code forRemoval}. javac reports it under this key alone,
   * and by default, so {@code deprecation} does not quiet it, and a {@code -Werror} build fails on
   * it even with no {@code -Xlint} option.
   */
  REMOVAL,
  /** An unchecked conversion or call. */
  UNCHECKED;

  /**
   * The key {@code @SuppressWarnings} names this category by.
   *
   * @return the lower-case key, such as {@code rawtypes}
   */
  public String key() {
    return name().toLowerCase(Locale.ROOT);
  }
}
