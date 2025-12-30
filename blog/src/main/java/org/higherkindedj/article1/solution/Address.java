// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article1.solution;

/**
 * Address record with lenses.
 *
 * <p>With higher-kinded-j (when Maven Central is accessible), you would use {@code @GenerateLenses}
 * to auto-generate these lens classes.
 */
public record Address(String street, String city, String postcode) {

  /** Lenses for Address fields. */
  public static final class Lenses {
    private Lenses() {}

    public static Lens<Address, String> street() {
      return Lens.of(
          Address::street,
          (newStreet, addr) -> new Address(newStreet, addr.city(), addr.postcode()));
    }

    public static Lens<Address, String> city() {
      return Lens.of(
          Address::city, (newCity, addr) -> new Address(addr.street(), newCity, addr.postcode()));
    }

    public static Lens<Address, String> postcode() {
      return Lens.of(
          Address::postcode,
          (newPostcode, addr) -> new Address(addr.street(), addr.city(), newPostcode));
    }
  }
}
