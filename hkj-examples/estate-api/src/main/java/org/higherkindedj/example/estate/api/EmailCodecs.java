// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.estate.api;

import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * The estate's email leaf, built once and shared by every spec that extends the vocabulary. Its
 * rule is the chapter's deliberately small one from Record Mapping Basics, whose checkpoint quotes
 * it: the page is about where a failing leaf's error lands, not about validating email.
 */
// ANCHOR: email_leaf
public final class EmailCodecs {
  public static final ValidatedPrism<String, EmailAddress> EMAIL =
      ValidatedPrism.of(
          raw ->
              raw.contains("@")
                  ? Validated.validNel(new EmailAddress(raw))
                  : Validated.invalidNel(FieldError.of("not an email address")),
          EmailAddress::value);

  private EmailCodecs() {}
}

// ANCHOR_END: email_leaf
