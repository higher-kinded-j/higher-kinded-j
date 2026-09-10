// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
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

  private static final Validated<NonEmptyList<FieldError>, Integer> LOCATED =
      Validated.invalid(
          NonEmptyList.of(FieldError.of("not a UUID").at("id"), FieldError.of("must be positive")));

  @Override
  protected Function<Validated<String, Integer>, ValidatedAssert<String, Integer>> entry() {
    return ValidatedAssert::assertThatValidated;
  }

  @Test
  void described_factory_is_callable() {
    ValidatedAssert.assertThatValidated(VALID_42, "described").isValid();
  }

  @Test
  void hasFieldErrors_renders_each_located_error_in_accumulation_order() {
    assertThatValidated(LOCATED).hasFieldErrors("id: not a UUID", "must be positive");
  }

  @Test
  void hasFieldErrors_rejects_a_different_rendering() {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> assertThatValidated(LOCATED).hasFieldErrors("id: not a UUID"));
  }

  @Test
  void hasFieldErrors_rejects_a_valid_subject() {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> assertThatValidated(VALID_42).hasFieldErrors("anything"));
  }

  @Test
  void hasFieldErrors_rejects_an_error_channel_that_is_not_a_collection() {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> assertThatValidated(INVALID_ERR).hasFieldErrors("err"))
        .withMessageContaining("hasFieldErrors() applies to the located-error channel");
  }

  @Test
  void hasFieldErrors_rejects_a_collection_of_something_other_than_FieldError() {
    Validated<List<String>, Integer> plainLines = Validated.invalid(List.of("e1"));

    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> assertThatValidated(plainLines).hasFieldErrors("e1"))
        .withMessageContaining("element 0 was <e1> (type <String>)");
  }

  /**
   * A {@code NonEmptyList} rejects null elements, but the assertion accepts any {@code Iterable},
   * and {@code Arrays.asList} does not: the null must fail as an assertion, not an NPE.
   */
  @Test
  void hasFieldErrors_rejects_a_null_among_the_located_errors() {
    Validated<List<FieldError>, Integer> withNull =
        Validated.invalid(Arrays.asList(FieldError.of("first"), null));

    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> assertThatValidated(withNull).hasFieldErrors("first"))
        .withMessageContaining("element 1 was <null> (type <null>)");
  }

  @Test
  void hasFieldErrors_accepts_an_empty_located_channel() {
    Validated<List<FieldError>, Integer> none = Validated.invalid(List.of());

    assertThatValidated(none).hasFieldErrors();
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
