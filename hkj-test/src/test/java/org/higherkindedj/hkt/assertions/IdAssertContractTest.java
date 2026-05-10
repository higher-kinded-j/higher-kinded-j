// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage contract for {@link IdAssert}. See {@link AssertContract}. */
@DisplayName("IdAssert contract")
class IdAssertContractTest extends AssertContract<Kind<IdKind.Witness, Object>, IdAssert<Object>> {

  private static final Kind<IdKind.Witness, Object> ID_42 = ID.widen(Id.of((Object) 42));
  private static final Kind<IdKind.Witness, Object> ID_99 = ID.widen(Id.of((Object) 99));
  private static final Kind<IdKind.Witness, Object> ID_NULL = ID.widen(Id.of(null));
  private static final Kind<IdKind.Witness, Object> ID_HELLO = ID.widen(Id.of((Object) "hello"));

  @Override
  protected Function<Kind<IdKind.Witness, Object>, IdAssert<Object>> entry() {
    return IdAssert::assertThatId;
  }

  @Override
  protected Stream<Row<Kind<IdKind.Witness, Object>, IdAssert<Object>>> rows() {
    return Stream.of(
        row("hasValue match", ID_42, ID_99, a -> a.hasValue((Object) 42)),
        passOnly("satisfies passes", ID_42, a -> a.satisfies(v -> {})),
        failOnly(
            "satisfies inner throws",
            ID_42,
            a ->
                a.satisfies(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        row(
            "valueMatches",
            ID_42,
            ID_NULL,
            a -> a.valueMatches(v -> v instanceof Integer i && i > 0)),
        row("hasNonNullValue", ID_42, ID_NULL, IdAssert::hasNonNullValue),
        row("hasNullValue", ID_NULL, ID_42, IdAssert::hasNullValue),
        row("hasValueOfType match", ID_42, ID_HELLO, a -> a.hasValueOfType(Integer.class)),
        failOnly(
            "hasValueOfType fails when value is null",
            ID_NULL,
            a -> a.hasValueOfType(Integer.class)),
        row("isEqualToId match", ID_42, ID_99, a -> a.isEqualToId(ID_42)));
  }

  @Test
  void isEqualToId_throws_when_other_is_null() {
    Assertions.assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> IdAssert.assertThatId(ID_42).isEqualToId(null));
  }
}
