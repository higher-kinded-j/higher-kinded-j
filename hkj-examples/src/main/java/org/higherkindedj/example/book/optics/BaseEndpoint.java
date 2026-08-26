// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics;

/**
 * A base class an overloaded copy constructor can take, for the copy-strategies page.
 *
 * <p>{@link Endpoint} declares a copy constructor for this type and one for {@link Audited}, which
 * is what makes {@code new Endpoint(source)} ambiguous and gives {@code @ViaCopyAndSet}'s {@code
 * copyConstructor} attribute something to disambiguate.
 */
public class BaseEndpoint {

  /** The host this endpoint points at. */
  protected String host = "";

  /**
   * Returns the host.
   *
   * @return the host, never null
   */
  public String host() {
    return host;
  }
}
