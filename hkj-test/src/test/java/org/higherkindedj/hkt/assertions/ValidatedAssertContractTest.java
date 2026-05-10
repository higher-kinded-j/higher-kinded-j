// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link ValidatedAssert}. See {@link AssertContract}. */
@DisplayName("ValidatedAssert contract")
class ValidatedAssertContractTest
    extends AssertContract<Validated<String, Integer>, ValidatedAssert<String, Integer>> {

  private static final Validated<String, Integer> VALID_42 = Validated.valid(42);
  private static final Validated<String, Integer> VALID_99 = Validated.valid(99);
  private static final Validated<String, Integer> INVALID_ERR = Validated.invalid("err");
  private static final Validated<String, Integer> INVALID_OTHER = Validated.invalid("other");

  @Override
  protected Function<Validated<String, Integer>, ValidatedAssert<String, Integer>> entry() {
    return ValidatedAssert::assertThatValidated;
  }

  @Test
  void described_factory_is_callable() {
    ValidatedAssert.assertThatValidated(VALID_42, "described").isValid();
  }

  @Override
  protected Stream<Row<Validated<String, Integer>, ValidatedAssert<String, Integer>>> rows() {
    return Stream.of(
        row("isValid", VALID_42, INVALID_ERR, ValidatedAssert::isValid),
        row("isInvalid", INVALID_ERR, VALID_42, ValidatedAssert::isInvalid),
        row("hasValue match", VALID_42, VALID_99, a -> a.hasValue(42)),
        row("hasValue wrong state", VALID_42, INVALID_ERR, a -> a.hasValue(42)),
        row("hasError match", INVALID_ERR, INVALID_OTHER, a -> a.hasError("err")),
        row("hasError wrong state", INVALID_ERR, VALID_42, a -> a.hasError("err")),
        row(
            "hasValueSatisfying match",
            VALID_42,
            VALID_99,
            a -> a.hasValueSatisfying(v -> v == 42, "is 42")),
        row(
            "hasValueSatisfying wrong state",
            VALID_42,
            INVALID_ERR,
            a -> a.hasValueSatisfying(v -> true, "always")),
        row(
            "hasErrorSatisfying match",
            INVALID_ERR,
            INVALID_OTHER,
            a -> a.hasErrorSatisfying("err"::equals, "is err")),
        row(
            "hasErrorSatisfying wrong state",
            INVALID_ERR,
            VALID_42,
            a -> a.hasErrorSatisfying(e -> true, "always")),
        row("hasValueOfType match", VALID_42, INVALID_ERR, a -> a.hasValueOfType(Integer.class)),
        failOnly("hasValueOfType wrong type", VALID_42, a -> a.hasValueOfType(String.class)),
        row("hasErrorOfType match", INVALID_ERR, VALID_42, a -> a.hasErrorOfType(String.class)),
        failOnly("hasErrorOfType wrong type", INVALID_ERR, a -> a.hasErrorOfType(Integer.class)),
        row("isEqualTo match", VALID_42, VALID_99, a -> a.isEqualTo(VALID_42)),
        row("isNotEqualTo", VALID_42, VALID_99, a -> a.isNotEqualTo(VALID_99)));
  }
}
