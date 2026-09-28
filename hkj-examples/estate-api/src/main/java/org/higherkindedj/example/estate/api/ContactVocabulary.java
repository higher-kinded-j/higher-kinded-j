// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.estate.api;

import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * The estate's shared mapping vocabulary: every client wire calls a name {@code fullName}, and
 * every email parses the same way. A plain interface, not a spec, so this module needs no
 * processor; a service module's specs extend it from this module's jar.
 */
// ANCHOR: vocabulary
public interface ContactVocabulary {
  @MapField(to = "fullName")
  String name();

  default ValidatedPrism<String, EmailAddress> email() {
    return ValidatedPrism.of(
        raw ->
            raw.contains("@")
                ? Validated.validNel(new EmailAddress(raw))
                : Validated.invalidNel(FieldError.of("not an email address")),
        EmailAddress::value);
  }
}

// ANCHOR_END: vocabulary
