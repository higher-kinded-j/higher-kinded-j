// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * Tutorial 26's shared vocabulary: the same email leaf serves both the full guest mapping and its
 * PATCH sibling.
 */
public interface GuestVocabulary {

  default ValidatedPrism<String, GuestEmail> email() {
    return GuestCodecs.EMAIL;
  }
}
