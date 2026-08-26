// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics;

/**
 * A supertype of {@link Endpoint}, for the copy-strategies page.
 *
 * <p>{@code Endpoint} declares a copy constructor for this type and another for {@link
 * BaseEndpoint}. Neither is more specific, so {@code new Endpoint(source)} is ambiguous until
 * {@code @ViaCopyAndSet}'s {@code copyConstructor} attribute names which one to reach.
 */
public interface Audited {

  /**
   * Returns the host, so an audit copy can read it.
   *
   * @return the host, never null
   */
  String host();
}
