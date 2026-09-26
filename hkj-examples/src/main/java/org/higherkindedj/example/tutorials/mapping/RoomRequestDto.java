// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import org.jspecify.annotations.Nullable;

/** Tutorial 27 wire: a note the guest leaves out arrives as {@code null}, as JSON has it. */
public record RoomRequestDto(String roomType, @Nullable String note) {}
