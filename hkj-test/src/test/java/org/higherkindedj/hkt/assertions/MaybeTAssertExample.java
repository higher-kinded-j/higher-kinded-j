// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.MaybeTAssert.assertThatMaybeT;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.maybe_t.MaybeTKindHelper.MAYBE_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe_t.MaybeT;
import org.higherkindedj.hkt.maybe_t.MaybeTKind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.MaybeTAssert}. */
@DisplayName("MaybeTAssert showcase")
class MaybeTAssertExample {

  private final MonadError<OptionalKind.Witness, Unit> outerMonad =
      Instances.monadError(optional());

  private <A> Optional<Maybe<A>> unwrap(Kind<OptionalKind.Witness, Maybe<A>> kind) {
    return OPTIONAL.narrow(kind);
  }

  @Test
  @DisplayName("isPresentJust() and hasJustValue() inspect the inner Just")
  void justInsidePresent() {
    Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> kind =
        MAYBE_T.widen(MaybeT.just(outerMonad, "ok"));

    assertThatMaybeT(kind, this::unwrap).isPresentJust().hasJustValue("ok");
  }

  @Test
  @DisplayName("isPresentNothing() asserts the outer is present but the inner is Nothing")
  void nothingInsidePresent() {
    Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> kind =
        MAYBE_T.widen(MaybeT.nothing(outerMonad));

    assertThatMaybeT(kind, this::unwrap).isPresentNothing();
  }

  @Test
  @DisplayName("isEmpty() asserts the outer monad has no value")
  void outerEmpty() {
    Kind<OptionalKind.Witness, Maybe<String>> emptyOuter = OPTIONAL.widen(Optional.empty());
    Kind<MaybeTKind.Witness<OptionalKind.Witness>, String> kind =
        MAYBE_T.widen(MaybeT.fromKind(emptyOuter));

    assertThatMaybeT(kind, this::unwrap).isEmpty();
  }
}
