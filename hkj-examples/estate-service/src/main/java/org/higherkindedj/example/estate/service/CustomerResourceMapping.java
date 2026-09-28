// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.estate.service;

import java.util.UUID;
import org.higherkindedj.example.estate.api.ContactVocabulary;
import org.higherkindedj.example.estate.clients.CustomerResource;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.StandardCodecs;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * The customer against the generated client's resource. The api module's vocabulary brings the
 * {@code fullName} rename and the email leaf; the address nests through {@link AddressMapping}; a
 * bean bridges the optional nickname by itself.
 */
// ANCHOR: resource_spec
@GenerateMapping
public interface CustomerResourceMapping
    extends ContactVocabulary, MappingSpec<Customer, CustomerResource> {
  default ValidatedPrism<String, UUID> id() {
    return StandardCodecs.uuid();
  }
}
// ANCHOR_END: resource_spec
