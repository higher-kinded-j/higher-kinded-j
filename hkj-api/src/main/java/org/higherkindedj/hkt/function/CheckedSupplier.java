// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.function;

/**
 * A {@link java.util.function.Supplier}-like functional interface whose computation is permitted to
 * throw a checked exception.
 *
 * <p>A standard {@link java.util.function.Supplier Supplier} declares {@code T get()} with no
 * {@code throws} clause, so lambda bodies that invoke checked-throwing APIs (for example {@link
 * java.nio.file.Files#readString Files.readString}, {@code Class.forName}, JDBC, reflection) must
 * be wrapped in a try/catch before being passed in. {@code CheckedSupplier} removes the need for
 * that boilerplate by allowing the lambda itself to declare {@code throws X}.
 *
 * <p>Primary consumer today is {@code org.higherkindedj.hkt.trymonad.Try#attempt(CheckedSupplier)}
 * (defined in {@code hkj-core}), which catches any {@link Exception} thrown by {@link #get()} and
 * wraps it in a {@code Failure}.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * // Without CheckedSupplier, users must write:
 * Try<String> r1 = Try.of(() -> {
 *     try {
 *         return Files.readString(Path.of("data.txt"));
 *     } catch (IOException e) {
 *         throw new RuntimeException(e);
 *     }
 * });
 *
 * // With CheckedSupplier + Try.attempt:
 * Try<String> r2 = Try.attempt(() -> Files.readString(Path.of("data.txt")));
 * }</pre>
 *
 * @param <T> the type of the value produced
 * @param <X> the type of checked exception the supplier may throw
 */
@FunctionalInterface
public interface CheckedSupplier<T, X extends Exception> {

  /**
   * Computes and returns a value, possibly throwing a checked exception {@code X}.
   *
   * @return the computed value (which may be {@code null} if {@code T} permits it)
   * @throws X the declared checked exception, if thrown by the computation
   */
  T get() throws X;
}
