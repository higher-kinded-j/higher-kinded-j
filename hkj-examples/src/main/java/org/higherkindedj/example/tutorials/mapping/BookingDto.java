// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

/** Tutorial 26 wire: the booking DTO, all strings. */
public record BookingDto(String id, GuestDto guest, String arrival, int nights) {}
