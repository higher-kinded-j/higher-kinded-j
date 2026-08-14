// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

/** Tutorial 26 wire: id and arrival as strings, the guest nested, nights identity-mapped. */
public record BookingDto(String id, GuestDto guest, String arrival, int nights) {}
