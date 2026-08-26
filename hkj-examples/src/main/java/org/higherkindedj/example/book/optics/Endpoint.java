// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics;

/**
 * A legacy mutable type with two copy constructors, for the copy-strategies page.
 *
 * <p>{@code new Endpoint(source)} is ambiguous: {@link BaseEndpoint} and {@link Audited} are
 * unrelated, so neither overload is more specific. {@code @ViaCopyAndSet(copyConstructor = ...)}
 * names which one to reach. Both copy the whole of the state, so the generated lens is lawful
 * either way.
 */
public final class Endpoint extends BaseEndpoint implements Audited {

  /**
   * Creates an endpoint.
   *
   * @param host the host; must not be null
   */
  public Endpoint(String host) {
    this.host = host;
  }

  /**
   * Copies an endpoint through its base.
   *
   * @param other the endpoint to copy; must not be null
   */
  public Endpoint(BaseEndpoint other) {
    this.host = other.host();
  }

  /**
   * Copies an endpoint through its audit view.
   *
   * @param other the endpoint to copy; must not be null
   */
  public Endpoint(Audited other) {
    this.host = other.host();
  }

  /**
   * Sets the host.
   *
   * @param host the host; must not be null
   */
  public void setHost(String host) {
    this.host = host;
  }
}
