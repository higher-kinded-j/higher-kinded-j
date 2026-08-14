// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import java.time.LocalDate;
import java.util.UUID;

/** Tutorial 26 domain: a booking. */
public record Booking(UUID id, Guest guest, LocalDate arrival, int nights) {}
