// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.ValidatedAssert}. */
@DisplayName("ValidatedAssert showcase")
class ValidatedAssertExample {

  @Test
  @DisplayName("isValid() and hasValue() assert the success branch")
  void validCase() {
    Validated<List<String>, Integer> result = Validated.valid(42);

    assertThatValidated(result).isValid().hasValue(42);
  }

  @Test
  @DisplayName("isInvalid() and hasError() assert the failure branch")
  void invalidCase() {
    Validated<String, Integer> result = Validated.invalid("missing field 'name'");

    assertThatValidated(result).isInvalid().hasError("missing field 'name'");
  }

  @Test
  @DisplayName("hasErrorSatisfying() lets you assert on collected errors")
  void errorsSatisfying() {
    Validated<List<String>, Integer> result = Validated.invalid(List.of("e1", "e2"));

    assertThatValidated(result)
        .isInvalid()
        .hasErrorSatisfying(errors -> errors.size() == 2, "two errors collected");
  }

  @Test
  @DisplayName("Accepts Kind<ValidatedKind.Witness<E>, A> directly without manual narrowing")
  void acceptsKindDirectly() {
    Kind<ValidatedKind.Witness<String>, Integer> kind = VALIDATED.widen(Validated.valid(99));

    assertThatValidated(kind).isValid().hasValue(99);
  }
}
