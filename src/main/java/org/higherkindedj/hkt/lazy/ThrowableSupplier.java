package org.higherkindedj.hkt.lazy;

import org.jspecify.annotations.Nullable;

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
   * @return a result
   * @throws Throwable if unable to compute a result
   */
  @Nullable T get() throws Throwable;
}
