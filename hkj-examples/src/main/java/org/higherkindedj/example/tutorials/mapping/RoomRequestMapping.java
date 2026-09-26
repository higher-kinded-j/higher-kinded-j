// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import java.util.Optional;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.annotations.OptionalBridge;

/**
 * Tutorial 27: the room request mapping. {@code @OptionalBridge} declares that a {@code null} note
 * means "left out", so it parses to an empty {@code Optional}. The bridge is per component: a
 * {@code null} room type is still a located error. Without the marker the processor refuses the
 * pair, since {@code String} and {@code Optional<String>} differ.
 */
@GenerateMapping
public interface RoomRequestMapping extends MappingSpec<RoomRequest, RoomRequestDto> {
  @OptionalBridge
  Optional<String> note();
}
