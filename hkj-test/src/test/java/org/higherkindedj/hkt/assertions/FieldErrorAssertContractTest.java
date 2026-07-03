// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.validated.FieldError;
import org.junit.jupiter.api.DisplayName;

/** Coverage contract for {@link FieldErrorAssert}. See {@link AssertContract}. */
@DisplayName("FieldErrorAssert contract")
class FieldErrorAssertContractTest extends AssertContract<FieldError, FieldErrorAssert> {

  private static final FieldError LEAF = FieldError.of("not a postcode");
  private static final FieldError LOCATED = FieldError.of("not a postcode").at("zip").at("address");
  private static final FieldError OTHER = FieldError.of("too short").at("name");

  @Override
  protected Function<FieldError, FieldErrorAssert> entry() {
    return FieldErrorAssert::assertThatFieldError;
  }

  @Override
  protected Stream<Row<FieldError, FieldErrorAssert>> rows() {
    return Stream.of(
        row("hasPath matches", LOCATED, OTHER, a -> a.hasPath("address.zip")),
        row("hasPath empty for a leaf", LEAF, LOCATED, a -> a.hasPath("")),
        row("hasSegments matches", LOCATED, OTHER, a -> a.hasSegments("address", "zip")),
        row("hasSegments empty for a leaf", LEAF, LOCATED, a -> a.hasSegments()),
        row("isUnlabelled", LEAF, LOCATED, FieldErrorAssert::isUnlabelled),
        row("hasMessage matches", LOCATED, OTHER, a -> a.hasMessage("not a postcode")),
        row(
            "hasMessageContaining matches",
            LOCATED,
            OTHER,
            a -> a.hasMessageContaining("postcode")));
  }
}
