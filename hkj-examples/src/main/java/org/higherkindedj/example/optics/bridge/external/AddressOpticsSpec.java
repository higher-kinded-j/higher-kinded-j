// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.bridge.external;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.annotations.OpticsSpec;
import org.higherkindedj.optics.annotations.Wither;

/**
 * Spec interface defining optics for the external {@link Address} type.
 *
 * <p>The {@code @Wither} annotation tells the processor to use the wither methods that Immutables
 * generates (e.g., {@code withCity()}).
 *
 * <p>Usage:
 *
 * <pre>{@code
 * Address address = Address.builder()
 *     .street("123 Main St")
 *     .city("Boston")
 *     .postcode("02101")
 *     .country("USA")
 *     .build();
 *
 * // Get city
 * String city = AddressOptics.city().get(address);
 *
 * // Create new address with different city
 * Address relocated = AddressOptics.city().set("New York", address);
 * }</pre>
 */
@ImportOptics
public interface AddressOpticsSpec extends OpticsSpec<Address> {

  @Wither(value = "withStreet", getter = "street")
  Lens<Address, String> street();

  @Wither(value = "withCity", getter = "city")
  Lens<Address, String> city();

  @Wither(value = "withPostcode", getter = "postcode")
  Lens<Address, String> postcode();

  @Wither(value = "withCountry", getter = "country")
  Lens<Address, String> country();
}
