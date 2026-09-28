// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.estate.clients;

import lombok.Data;

/** An address on the wire, as a Lombok {@code @Data} bean: Lombok writes its getters and setters. */
@Data
public class AddressBean {
  private String street;
  private String city;
  private String postcode;
}
