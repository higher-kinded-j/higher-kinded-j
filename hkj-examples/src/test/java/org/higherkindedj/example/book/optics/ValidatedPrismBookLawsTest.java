// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics;

import org.higherkindedj.optics.laws.ValidatedPrismLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The law check shown on the book's Validated Prisms page. The page {@code {{#include}}}s the
 * anchored region, so the snippet it displays is this test, and it is green.
 */
@DisplayName("the book's ValidatedPrism obeys the prism laws")
class ValidatedPrismBookLawsTest {

  @Test
  void emailPrismObeysTheLaws() {
    // ANCHOR: laws
    ValidatedPrismLaws.assertValidatedPrismLaws(
        ValidatedPrismBook.EMAIL, "ada@corp.example", "not-an-email");
    // parse-build: parse(build(a)) == Valid(a)
    // build-parse: parse(s) == Valid(a)  =>  build(a) == s   (no lossy parse-normalise)
    // ANCHOR_END: laws
  }

  @Test
  void canonicalUppercaseUuidObeysTheLaws() {
    // ANCHOR: canonical_laws
    ValidatedPrismLaws.assertValidatedPrismLaws(
        ValidatedPrismBook.UPPER_UUID,
        "123E4567-E89B-12D3-A456-426614174000", // the canonical spelling parses
        "123e4567-e89b-12d3-a456-426614174000"); // the one render cannot reproduce is rejected
    // ANCHOR_END: canonical_laws
  }
}
