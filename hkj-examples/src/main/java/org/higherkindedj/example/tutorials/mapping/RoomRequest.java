// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import java.util.Optional;

/** Tutorial 27 domain: a room request, whose note the guest may leave out. */
public record RoomRequest(String roomType, Optional<String> note) {}
