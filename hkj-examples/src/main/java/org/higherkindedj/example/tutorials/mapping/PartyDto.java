// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import java.util.List;

/** Tutorial 27 wire: the party, its guests a list of {@link GuestDto}. */
public record PartyDto(String bookingId, List<GuestDto> guests) {}
