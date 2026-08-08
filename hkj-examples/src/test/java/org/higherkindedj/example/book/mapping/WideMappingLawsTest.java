// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.List;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A mapping wider than one {@code Validated.fields()} ladder obeys the same laws and reports the
 * same located, declaration-ordered errors as a narrow one — the chunked assembly is behaviourally
 * invisible.
 */
@DisplayName("a 20-component mapping behaves exactly like a narrow one")
class WideMappingLawsTest {

  @Test
  @DisplayName("the wide mapping obeys the mapping laws")
  void wideMappingObeysTheLaws() {
    MappingLaws.assertMappingLaws(
        WideAccountMappingImpl.INSTANCE.asValidatedPrism(),
        wire("ada@example.org"), // parses
        wire("not-an-email")); // must not parse
  }

  @Test
  @DisplayName("errors locate in declaration order across the chunk boundary")
  void errorsLocateInDeclarationOrderAcrossTheChunkBoundary() {
    // ANCHOR: wide_laws
    // f1 fails in the first ladder, f17 and email in the second: one accumulated result,
    // declaration order preserved across the boundary.
    WideAccountDto wire =
        new WideAccountDto(
            null,
            "v2",
            "v3",
            "v4",
            "v5",
            "v6",
            "v7",
            "v8",
            "v9",
            "v10",
            "v11",
            "v12",
            "v13",
            "v14",
            "v15",
            "v16",
            null,
            "v18",
            "v19",
            "not-an-email");

    Validated<NonEmptyList<FieldError>, WideAccount> parsed =
        WideAccountMappingImpl.INSTANCE.parse(wire);

    assertThatValidated(parsed).isInvalid();
    assertThat(rendered(parsed))
        .containsExactly(
            "f1: must not be null", "f17: must not be null", "email: not an email address");
    // ANCHOR_END: wide_laws
  }

  @Test
  @DisplayName("a valid wire round-trips through parse and build")
  void validWireRoundTrips() {
    WideAccountDto wire = wire("ada@example.org");

    Validated<NonEmptyList<FieldError>, WideAccount> parsed =
        WideAccountMappingImpl.INSTANCE.parse(wire);

    assertThatValidated(parsed).isValid();
    assertThat(parsed.fold(_ -> null, WideAccountMappingImpl.INSTANCE::build)).isEqualTo(wire);
  }

  private static WideAccountDto wire(String email) {
    return new WideAccountDto(
        "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v8", "v9", "v10", "v11", "v12", "v13", "v14",
        "v15", "v16", "v17", "v18", "v19", email);
  }

  private static List<String> rendered(Validated<NonEmptyList<FieldError>, ?> result) {
    return result.fold(nel -> nel.map(FieldError::toString).toJavaList(), _ -> List.of());
  }
}
