// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import static org.higherkindedj.optics.validated.StandardCodecs.uuid;

import java.util.UUID;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * Tutorial 27: the party mapping. {@link GuestMapping} maps each guest in the list without being
 * named here, so an error inside the list is located by the guest's index.
 */
@GenerateMapping
public interface PartyMapping extends MappingSpec<Party, PartyDto> {
  default ValidatedPrism<String, UUID> bookingId() {
    return uuid();
  }
}
