// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.estate.api;

import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * The estate's shared mapping vocabulary: every client wire calls a name {@code fullName}, and
 * every email parses the same way. A plain interface, not a spec, so this module needs no
 * processor; a service module's specs extend it from this module's compiled classes.
 */
// ANCHOR: vocabulary
public interface ContactVocabulary {
  @MapField(to = "fullName")
  String name();

  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: vocabulary
