// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import java.util.List;
import java.util.UUID;

/** Tutorial 27 domain: the guests travelling on one booking. */
public record Party(UUID bookingId, List<Guest> guests) {}
