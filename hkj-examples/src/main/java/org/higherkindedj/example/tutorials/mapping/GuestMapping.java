// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.annotations.MappingSpec;

/** Tutorial 26: the guest mapping, with a rename and the vocabulary's email leaf. */
@GenerateMapping
public interface GuestMapping extends GuestVocabulary, MappingSpec<Guest, GuestDto> {
  @MapField(to = "fullName")
  String name();
}
