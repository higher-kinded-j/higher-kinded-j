// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.laws;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.BiPredicate;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeTraverse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifies the {@link TraverseLaws} helpers against a lawful traverse and a violating equality. */
@DisplayName("TraverseLaws")
class TraverseLawsTest {

  private final Traverse<MaybeKind.Witness> goodTraverse = MaybeTraverse.INSTANCE;
  private final Kind<MaybeKind.Witness, Integer> fa = MAYBE.widen(Maybe.just(42));
  private final BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>> eq =
      KindEquivalence.byEqualsAfter(MAYBE::narrow);

  @Test
  @DisplayName("assertIdentity passes for a lawful traverse")
  void identityPasses() {
    TraverseLaws.assertIdentity(goodTraverse, fa, eq);
  }

  @Test
  @DisplayName("assertIdentity fails when the identity law is observed to be violated")
  void identityFails() {
    BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>> neverEqual =
        (x, y) -> false;
    assertThatThrownBy(() -> TraverseLaws.assertIdentity(goodTraverse, fa, neverEqual))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Traverse identity");
  }
}
