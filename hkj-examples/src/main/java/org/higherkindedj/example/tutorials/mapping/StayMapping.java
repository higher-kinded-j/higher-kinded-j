// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import static org.higherkindedj.optics.validated.StandardCodecs.localDate;

import java.time.LocalDate;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * Tutorial 27: the stay mapping. Each date parses through its own leaf; once both have parsed,
 * {@link Stay}'s constructor runs, and its refusal becomes an error at the record's path.
 */
@GenerateMapping
public interface StayMapping extends MappingSpec<Stay, StayDto> {
  default ValidatedPrism<String, LocalDate> arrival() {
    return localDate();
  }

  default ValidatedPrism<String, LocalDate> departure() {
    return localDate();
  }
}
