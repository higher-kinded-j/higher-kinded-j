// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateTuple<S, A> Tests")
class StateTupleTest {

  private final String testValue = "ResultValue";
  private final Integer testState = 10;

  @Nested
  @DisplayName("Creation (Constructor and Factory)")
  class CreationTests {

    @Test
    @DisplayName("Record constructor should initialise fields correctly")
    void constructor_initialisesFields() {
      StateTuple<Integer, String> tuple = new StateTuple<>(testValue, testState);
      assertThat(tuple.value()).isEqualTo(testValue);
      assertThat(tuple.state()).isEqualTo(testState);
    }

    @Test
    @DisplayName("Record constructor should throw NullPointerException for null value")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void constructor_throwsNPEForNullValue() {
      assertThatNullPointerException()
          .isThrownBy(() -> new StateTuple<>(null, testState))
          .withMessage("StateTuple.construction value cannot be null");
    }

    @Test
    @DisplayName("Record constructor should throw NullPointerException for null state")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void constructor_throwsNPEForNullState() {
      assertThatNullPointerException()
          .isThrownBy(() -> new StateTuple<>(testValue, null))
          .withMessage("StateTuple.construction state cannot be null");
    }

    @Test
    @DisplayName("of() factory should create tuple with correct state and value order")
    void of_createsTupleCorrectly() {
      // Note: StateTuple.of(state, value) matches record order (value, state)
      StateTuple<Integer, String> tuple = StateTuple.of(testState, testValue);
      assertThat(tuple.value()).isEqualTo(testValue);
      assertThat(tuple.state()).isEqualTo(testState);
    }

    @Test
    @DisplayName("of() factory should throw NullPointerException for null value")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void of_throwsNPEForNullValue() {
      assertThatNullPointerException()
          .isThrownBy(() -> StateTuple.of(testState, null))
          .withMessage("StateTuple.of value cannot be null");
    }

    @Test
    @DisplayName("of() factory should throw NullPointerException for null state")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void of_throwsNPEForNullState() {
      assertThatNullPointerException()
          .isThrownBy(() -> StateTuple.of(null, testValue))
          .withMessage("StateTuple.of state cannot be null");
    }
  }

  @Nested
  @DisplayName("Accessors")
  class AccessorTests {

    @Test
    @DisplayName("value() should return the correct value")
    void value_returnsCorrectValue() {
      StateTuple<Integer, String> tupleWithValue = new StateTuple<>(testValue, testState);

      assertThat(tupleWithValue.value()).isEqualTo(testValue);
    }

    @Test
    @DisplayName("state() should return the correct state")
    void state_returnsCorrectState() {
      StateTuple<Integer, String> tuple = new StateTuple<>(testValue, testState);
      assertThat(tuple.state()).isEqualTo(testState);

      StateTuple<Integer, String> tuple2 = new StateTuple<>(testValue, 99);
      assertThat(tuple2.state()).isEqualTo(99);
    }
  }

  @Nested
  @DisplayName("Object Methods (equals, hashCode, toString)")
  class ObjectMethodsTests {

    @Test
    @DisplayName("equals() should compare value and state")
    void equals_comparesValueAndState() {
      StateTuple<Integer, String> t1a = new StateTuple<>("A", 1);
      StateTuple<Integer, String> t1b = new StateTuple<>("A", 1); // Same content
      StateTuple<Integer, String> t2 = new StateTuple<>("B", 1); // Different value
      StateTuple<Integer, String> t3 = new StateTuple<>("A", 2); // Different state

      assertThat(t1a).isEqualTo(t1b);
      assertThat(t1a).isNotEqualTo(t2);
      assertThat(t1a).isNotEqualTo(t3);
      assertThat(t1a).isNotEqualTo(null);
    }

    @Test
    @DisplayName("hashCode() should be consistent with equals")
    void hashCode_consistentWithEquals() {
      StateTuple<Integer, String> t1a = new StateTuple<>("A", 1);
      StateTuple<Integer, String> t1b = new StateTuple<>("A", 1);

      assertThat(t1a.hashCode()).isEqualTo(t1b.hashCode());
    }

    @Test
    @DisplayName("toString() should format correctly")
    void toString_formatsCorrectly() {
      StateTuple<Integer, String> tuple = new StateTuple<>("Value", 99);
      assertThat(tuple.toString()).isEqualTo("StateTuple[value=Value, state=99]");
    }
  }
}
