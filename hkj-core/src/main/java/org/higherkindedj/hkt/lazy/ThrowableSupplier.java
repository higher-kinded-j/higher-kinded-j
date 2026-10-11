// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

/**
 * Represents a supplier of results that may throw any Throwable. Similar to {@link
 * java.util.function.Supplier}, but allows checked exceptions.
 *
 * @param <T> the type of results supplied by this supplier
 */
@FunctionalInterface
public interface ThrowableSupplier<T> {

  /**
   * Gets a result, potentially throwing an exception.
   *
   * @return a result. Never null.
   * @throws Throwable if unable to compute a result
   */
  T get() throws Throwable;
}
