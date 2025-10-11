// Copyright (c) 2025 Magnus Smith
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
  private final String nullValue = null;
  private final Integer nullState = null; // For testing null state input

  @Nested
  @DisplayName("Creation (Constructor and Factory)")
  class CreationTests {

    @Test
    @DisplayName("Record constructor should initialize fields correctly")
    void constructor_initializesFields() {
      StateTuple<Integer, String> tuple = new StateTuple<>(testValue, testState);
      assertThat(tuple.value()).isEqualTo(testValue);
      assertThat(tuple.state()).isEqualTo(testState);
    }

    @Test
    @DisplayName("Record constructor should allow null value")
    void constructor_allowsNullValue() {
      StateTuple<Integer, String> tuple = new StateTuple<>(nullValue, testState);
      assertThat(tuple.value()).isNull();
      assertThat(tuple.state()).isEqualTo(testState);
    }

    @Test
    @DisplayName("Record constructor should throw NullPointerException for null state")
    void constructor_throwsNPEForNullState() {
      assertThatNullPointerException()
          .isThrownBy(() -> new StateTuple<>(testValue, nullState))
          .withMessageContaining("tateTuple.construction value cannot be null");
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
    @DisplayName("of() factory should allow null value")
    void of_allowsNullValue() {
      StateTuple<Integer, String> tuple = StateTuple.of(testState, nullValue);
      assertThat(tuple.value()).isNull();
      assertThat(tuple.state()).isEqualTo(testState);
    }

    @Test
    @DisplayName("of() factory should throw NullPointerException for null state")
    void of_throwsNPEForNullState() {
      assertThatNullPointerException()
          .isThrownBy(() -> StateTuple.of(nullState, testValue))
          .withMessageContaining("StateTuple.of value cannot be null");
    }
  }

  @Nested
  @DisplayName("Accessors")
  class AccessorTests {

    @Test
    @DisplayName("value() should return the correct value")
    void value_returnsCorrectValue() {
      StateTuple<Integer, String> tupleWithValue = new StateTuple<>(testValue, testState);
      StateTuple<Integer, String> tupleWithNull = new StateTuple<>(nullValue, testState);

      assertThat(tupleWithValue.value()).isEqualTo(testValue);
      assertThat(tupleWithNull.value()).isNull();
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
      StateTuple<Integer, String> t4 = new StateTuple<>(null, 1); // Null value
      StateTuple<Integer, String> t5 = new StateTuple<>(null, 1); // Null value, same state

      assertThat(t1a).isEqualTo(t1b);
      assertThat(t1a).isNotEqualTo(t2);
      assertThat(t1a).isNotEqualTo(t3);
      assertThat(t1a).isNotEqualTo(t4);
      assertThat(t4).isEqualTo(t5);
      assertThat(t1a).isNotEqualTo(null);
      assertThat(t1a).isNotEqualTo("A"); // Different type
    }

    @Test
    @DisplayName("hashCode() should be consistent with equals")
    void hashCode_consistentWithEquals() {
      StateTuple<Integer, String> t1a = new StateTuple<>("A", 1);
      StateTuple<Integer, String> t1b = new StateTuple<>("A", 1);
      StateTuple<Integer, String> t4 = new StateTuple<>(null, 1);
      StateTuple<Integer, String> t5 = new StateTuple<>(null, 1);

      assertThat(t1a.hashCode()).isEqualTo(t1b.hashCode());
      assertThat(t4.hashCode()).isEqualTo(t5.hashCode());
    }

    @Test
    @DisplayName("toString() should format correctly")
    void toString_formatsCorrectly() {
      StateTuple<Integer, String> tuple = new StateTuple<>("Value", 99);
      assertThat(tuple.toString()).isEqualTo("StateTuple[value=Value, state=99]");

      StateTuple<Integer, String> tupleNullValue = new StateTuple<>(null, 5);
      assertThat(tupleNullValue.toString()).isEqualTo("StateTuple[value=null, state=5]");
    }
  }
}
