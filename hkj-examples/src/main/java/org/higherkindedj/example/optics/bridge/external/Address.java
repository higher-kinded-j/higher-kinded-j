// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.bridge.external;

import java.util.Objects;

/**
 * An immutable Address value object following the Immutables library style.
 *
 * <p>It is the Optics chapter's {@code Address}, with the cast's three components, in the form an
 * Immutables module gives it. This class simulates what the Immutables annotation processor
 * generates from:
 *
 * <pre>{@code
 * @Value.Immutable
 * public interface Address {
 *     String street();
 *     String city();
 *     String postcode();
 * }
 * }</pre>
 *
 * <p>Key patterns:
 *
 * <ul>
 *   <li>Accessor methods (no "get" prefix)
 *   <li>Wither methods for creating modified copies
 *   <li>Builder for construction
 * </ul>
 */
public final class Address {

  private final String street;
  private final String city;
  private final String postcode;

  private Address(String street, String city, String postcode) {
    this.street = Objects.requireNonNull(street, "street");
    this.city = Objects.requireNonNull(city, "city");
    this.postcode = Objects.requireNonNull(postcode, "postcode");
  }

  // Accessor methods (Immutables style - no "get" prefix)
  public String street() {
    return street;
  }

  public String city() {
    return city;
  }

  public String postcode() {
    return postcode;
  }

  // Wither methods - return new instance with one field changed
  public Address withStreet(String street) {
    return new Address(street, this.city, this.postcode);
  }

  public Address withCity(String city) {
    return new Address(this.street, city, this.postcode);
  }

  public Address withPostcode(String postcode) {
    return new Address(this.street, this.city, postcode);
  }

  // Builder pattern
  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Address address)) return false;
    return street.equals(address.street)
        && city.equals(address.city)
        && postcode.equals(address.postcode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(street, city, postcode);
  }

  @Override
  public String toString() {
    return "Address{street='%s', city='%s', postcode='%s'}".formatted(street, city, postcode);
  }

  public static final class Builder {
    private String street;
    private String city;
    private String postcode;

    public Builder street(String street) {
      this.street = street;
      return this;
    }

    public Builder city(String city) {
      this.city = city;
      return this;
    }

    public Builder postcode(String postcode) {
      this.postcode = postcode;
      return this;
    }

    public Address build() {
      return new Address(street, city, postcode);
    }
  }
}
