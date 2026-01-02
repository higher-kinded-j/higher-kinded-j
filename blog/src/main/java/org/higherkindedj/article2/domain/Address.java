// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.domain;

import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * An address with street, city, and postcode.
 *
 * <p>The {@code @GenerateLenses} annotation generates {@code AddressLenses} with static lens
 * methods for each field.
 */
@GenerateLenses
public record Address(String street, String city, String postcode) {}
