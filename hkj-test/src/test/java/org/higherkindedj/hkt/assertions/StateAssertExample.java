// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.StateAssert.assertThatStateTuple;

import org.higherkindedj.hkt.state.StateTuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.StateAssert}. */
@DisplayName("StateAssert showcase")
class StateAssertExample {

  @Test
  @DisplayName("hasValue() and hasState() inspect the result tuple")
  void valueAndState() {
    StateTuple<Integer, String> result = new StateTuple<>("processed", 5);

    assertThatStateTuple(result).hasValue("processed").hasState(5);
  }

  @Test
  @DisplayName("hasStateSatisfying() chains AssertJ on the final state")
  void stateSatisfying() {
    StateTuple<Integer, Integer> result = new StateTuple<>(7, 42);

    assertThatStateTuple(result)
        .hasStateSatisfying(state -> assertThat(state).isPositive().isLessThan(100));
  }
}
