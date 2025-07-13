// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.optics.Lens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateTupleLenses Tests")
class StateTupleLensesTest {

  // A sample StateTuple to use across tests.
  private final StateTuple<Integer, String> tuple = new StateTuple<>("Initial Value", 100);

  @Nested
  @DisplayName("value() lens")
  class ValueLensTests {
    private final Lens<StateTuple<Integer, String>, String> valueLens =
        StateTupleLensesManual.value();

    @Test
    @DisplayName("get() should retrieve the value")
    void get_shouldRetrieveValue() {
      // Check that the lens correctly gets the 'value' component.
      assertThat(valueLens.get(tuple)).isEqualTo("Initial Value");
    }

    @Test
    @DisplayName("set() should update the value immutably")
    void set_shouldUpdateValue() {
      // Set a new value using the lens.
      StateTuple<Integer, String> updatedTuple = valueLens.set("New Value", tuple);

      // Verify the new tuple has the updated value and the original state.
      assertThat(updatedTuple.value()).isEqualTo("New Value");
      assertThat(updatedTuple.state()).isEqualTo(100);

      // Verify the original tuple remains unchanged.
      assertThat(tuple.value()).isEqualTo("Initial Value");
    }

    @Test
    @DisplayName("modify() should apply a function to the value")
    void modify_shouldApplyFunctionToValue() {
      // Modify the value using the lens.
      StateTuple<Integer, String> modifiedTuple = valueLens.modify(String::toUpperCase, tuple);

      // Verify the value was modified and the state remains the same.
      assertThat(modifiedTuple.value()).isEqualTo("INITIAL VALUE");
      assertThat(modifiedTuple.state()).isEqualTo(100);
    }
  }

  @Nested
  @DisplayName("state() lens")
  class StateLensTests {
    private final Lens<StateTuple<Integer, String>, Integer> stateLens =
        StateTupleLensesManual.state();

    @Test
    @DisplayName("get() should retrieve the state")
    void get_shouldRetrieveState() {
      // Check that the lens correctly gets the 'state' component.
      assertThat(stateLens.get(tuple)).isEqualTo(100);
    }

    @Test
    @DisplayName("set() should update the state immutably")
    void set_shouldUpdateState() {
      // Set a new state using the lens.
      StateTuple<Integer, String> updatedTuple = stateLens.set(200, tuple);

      // Verify the new tuple has the updated state and the original value.
      assertThat(updatedTuple.state()).isEqualTo(200);
      assertThat(updatedTuple.value()).isEqualTo("Initial Value");

      // Verify the original tuple remains unchanged.
      assertThat(tuple.state()).isEqualTo(100);
    }

    @Test
    @DisplayName("modify() should apply a function to the state")
    void modify_shouldApplyFunctionToState() {
      // Modify the state using the lens.
      StateTuple<Integer, String> modifiedTuple = stateLens.modify(s -> s * 2, tuple);

      // Verify the state was modified and the value remains the same.
      assertThat(modifiedTuple.state()).isEqualTo(200);
      assertThat(modifiedTuple.value()).isEqualTo("Initial Value");
    }
  }
}
