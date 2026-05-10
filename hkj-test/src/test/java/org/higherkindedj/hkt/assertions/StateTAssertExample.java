// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.StateTAssert.assertThatStateT;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateT;
import org.higherkindedj.hkt.state_t.StateTKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.StateTAssert}. */
@DisplayName("StateTAssert showcase")
class StateTAssertExample {

  private final OptionalMonad outerMonad = OptionalMonad.INSTANCE;

  private <A> Optional<StateTuple<Integer, A>> unwrap(
      Kind<OptionalKind.Witness, StateTuple<Integer, A>> kind) {
    return OPTIONAL.narrow(kind);
  }

  @Test
  @DisplayName("whenRunWith() runs the StateT with an initial state and inspects the result")
  void runStateT() {
    Function<Integer, Kind<OptionalKind.Witness, StateTuple<Integer, String>>> runFn =
        s -> outerMonad.of(StateTuple.of(s + 1, "step:" + s));
    StateT<Integer, OptionalKind.Witness, String> stateT = StateT.create(runFn, outerMonad);

    Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> kind = STATE_T.widen(stateT);

    assertThatStateT(kind, this::unwrap).whenRunWith(10).isPresent().hasResult("step:10", 11);
  }
}
