// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * Tutorial 26's shared vocabulary: the one hand-written leaf, serving both the full guest mapping
 * and its PATCH sibling. Identifiers and dates come from {@code StandardCodecs} instead.
 */
public interface GuestVocabulary {

  /** The email leaf: a located reason on failure, a total render back. */
  ValidatedPrism<String, GuestEmail> EMAIL =
      ValidatedPrism.of(
          raw ->
              raw.contains("@")
                  ? Validated.validNel(new GuestEmail(raw))
                  : Validated.invalidNel(FieldError.of("not an email address")),
          GuestEmail::value);

  default ValidatedPrism<String, GuestEmail> email() {
    return EMAIL;
  }
}
