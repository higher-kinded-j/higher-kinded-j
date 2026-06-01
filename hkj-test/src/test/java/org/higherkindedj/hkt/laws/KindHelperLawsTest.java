// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.laws;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifies the {@link KindHelperLaws} round-trip helpers against the {@code Id} KindHelper. */
@DisplayName("KindHelperLaws")
class KindHelperLawsTest {

  private static final IdKindHelper ID = IdKindHelper.ID;

  private final Id<Integer> instance = Id.of(42);
  private final Function<Id<Integer>, Kind<IdKind.Witness, Integer>> widen = ID::widen;
  private final Function<Kind<IdKind.Witness, Integer>, Id<Integer>> narrow = ID::narrow;

  @SuppressWarnings("unchecked")
  private static Class<Id<Integer>> idClass() {
    return (Class<Id<Integer>>) (Class<?>) Id.class;
  }

  @Test
  @DisplayName("assertRoundTrip passes for a lawful helper")
  void roundTripPasses() {
    KindHelperLaws.assertRoundTrip(instance, widen, narrow);
  }

  @Test
  @DisplayName("assertIdempotency passes for a lawful helper")
  void idempotencyPasses() {
    KindHelperLaws.assertIdempotency(instance, widen, narrow);
  }

  @Test
  @DisplayName("assertEdgeCases passes for a lawful helper")
  void edgeCasesPasses() {
    KindHelperLaws.assertEdgeCases(instance, idClass(), widen, narrow);
  }

  @Test
  @DisplayName("assertRoundTrip fails when narrow does not return the original instance")
  void roundTripFails() {
    Function<Kind<IdKind.Witness, Integer>, Id<Integer>> brokenNarrow = kind -> Id.of(99);
    assertThatThrownBy(() -> KindHelperLaws.assertRoundTrip(instance, widen, brokenNarrow))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("KindHelper round-trip");
  }
}
