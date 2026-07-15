// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics;

import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/optics/validated_prism.html">Validated Prisms</a> page.
 * The page {@code {{#include}}}s the anchored region, so it cannot drift from the API.
 */
public final class ValidatedPrismBook {

  // ANCHOR: prism
  static final ValidatedPrism<String, EmailAddress> EMAIL =
      ValidatedPrism.of(
          EmailAddress::parse, // String -> Validated<NonEmptyList<FieldError>, EmailAddress>
          EmailAddress::value); // EmailAddress -> String   (total)

  // ANCHOR_END: prism

  private ValidatedPrismBook() {}

  public static void main(String[] args) {
    // ANCHOR: usage
    Validated<NonEmptyList<FieldError>, EmailAddress> parsed = EMAIL.parse("  NOPE ");

    // The only way to obtain an EmailAddress is to parse one: that is the point.
    String rendered =
        EMAIL
            .parse("ada@corp.example")
            .map(EMAIL::build) // build always succeeds
            .orElse("");

    ValidationPath<NonEmptyList<FieldError>, EmailAddress> railway =
        EMAIL.parsePath("ada@corp.example");
    // ANCHOR_END: usage
    System.out.println(parsed + " / " + rendered + " / " + railway.run());
  }
}

/** An always-valid domain value: obtainable only by parsing. */
record EmailAddress(String value) {
  static Validated<NonEmptyList<FieldError>, EmailAddress> parse(String raw) {
    return raw.contains("@")
        ? Validated.validNel(new EmailAddress(raw))
        : Validated.invalidNel(FieldError.of("not an email"));
  }
}
