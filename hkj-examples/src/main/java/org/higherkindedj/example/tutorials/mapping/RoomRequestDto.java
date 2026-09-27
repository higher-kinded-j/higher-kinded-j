// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import org.jspecify.annotations.Nullable;

/**
 * Tutorial 27 wire: a note the guest leaves out arrives as {@code null}, as a JSON binder hands it
 * over. The component is {@code @Nullable} because this module is null-marked, and the processor
 * refuses to bridge a component that is not declared nullable.
 */
public record RoomRequestDto(String roomType, @Nullable String note) {}
