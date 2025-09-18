// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.util.ErrorHandling.*;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Validation Utilities Tests")
class ValidationUtilsTest {

  // Simple test Kind
  private interface MyKindWitness {}

  private record MyKind<A>(A value) implements Kind<MyKindWitness, A> {}

  @Nested
  @DisplayName("requireNonNullForWiden() Tests")
  class RequireNonNullForWidenTests {

    @Test
    void shouldReturnInstanceForNonNull() {
      String obj = "test";
      assertThat(requireNonNullForWiden(obj, "String")).isSameAs(obj);
    }

    @Test
    void shouldThrowForNull() {
      assertThatThrownBy(() -> requireNonNullForWiden(null, "MyType"))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Input MyType cannot be null for widen");
    }

    @Test
    void shouldHandleNullTypeName() {
      String obj = "test";
      assertThat(requireNonNullForWiden(obj, null)).isSameAs(obj);

      assertThatThrownBy(() -> requireNonNullForWiden(null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Input null cannot be null for widen");
    }
  }

  @Nested
  @DisplayName("requireNonNullForHolder() Tests")
  class RequireNonNullForHolderTests {

    @Test
    void shouldReturnInstanceForNonNull() {
      String obj = "test";
      assertThat(requireNonNullForHolder(obj, "MyType")).isSameAs(obj);
    }

    @Test
    void shouldThrowForNull() {
      assertThatThrownBy(() -> requireNonNullForHolder(null, "MyType"))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("MyTypeHolder contained null MyType instance");
    }

    @Test
    void shouldHandleNullTypeName() {
      String obj = "test";
      assertThat(requireNonNullForHolder(obj, null)).isSameAs(obj);

      assertThatThrownBy(() -> requireNonNullForHolder(null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("nullHolder contained null null instance");
    }
  }

  @Nested
  @DisplayName("requireNonNullFunction() Tests")
  class RequireNonNullFunctionTests {

    @Test
    void noMessage_shouldSucceedForNonNull() {
      Function<Integer, Integer> f = x -> x;
      assertThat(requireNonNullFunction(f)).isSameAs(f);
    }

    @Test
    void noMessage_shouldThrowForNull() {
      assertThatThrownBy(() -> requireNonNullFunction(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Function cannot be null");
    }

    @Test
    void withMessage_shouldSucceedForNonNull() {
      Function<Integer, Integer> f = x -> x;
      assertThat(requireNonNullFunction(f, "myFunc")).isSameAs(f);
    }

    @Test
    void withMessage_shouldThrowForNull() {
      assertThatThrownBy(() -> requireNonNullFunction(null, "myFunc"))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("myFunc cannot be null");
    }

    @Test
    void withNullMessage_shouldThrowForNull() {
      assertThatThrownBy(() -> requireNonNullFunction(null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("null cannot be null");
    }
  }

  @Nested
  @DisplayName("requireNonNullKind() Tests")
  class RequireNonNullKindTests {

    @Test
    void noMessage_shouldSucceedForNonNull() {
      Kind<MyKindWitness, String> k = new MyKind<>("test");
      assertThat(requireNonNullKind(k)).isSameAs(k);
    }

    @Test
    void noMessage_shouldThrowForNull() {
      assertThatThrownBy(() -> requireNonNullKind(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Kind argument cannot be null");
    }

    @Test
    void withMessage_shouldSucceedForNonNull() {
      Kind<MyKindWitness, String> k = new MyKind<>("test");
      assertThat(requireNonNullKind(k, "myKind")).isSameAs(k);
    }

    @Test
    void withMessage_shouldThrowForNull() {
      assertThatThrownBy(() -> requireNonNullKind(null, "myKind"))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("myKind cannot be null");
    }

    @Test
    void withNullMessage_shouldThrowForNull() {
      assertThatThrownBy(() -> requireNonNullKind(null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("null cannot be null");
    }
  }

  @Nested
  @DisplayName("requireNonEmptyCollection() Tests")
  class RequireNonEmptyCollectionTests {

    @Test
    void shouldSucceedForNonEmptyCollection() {
      List<String> list = List.of("a");
      assertThat(requireNonEmptyCollection(list, "myList")).isSameAs(list);
    }

    @Test
    void shouldThrowForNull() {
      assertThatThrownBy(() -> requireNonEmptyCollection(null, "myList"))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("myList cannot be null");
    }

    @Test
    void shouldThrowForEmpty() {
      assertThatThrownBy(() -> requireNonEmptyCollection(Collections.emptyList(), "myList"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("myList cannot be empty");
    }

    @Test
    void shouldHandleNullParameterName() {
      List<String> list = List.of("a");
      assertThat(requireNonEmptyCollection(list, null)).isSameAs(list);

      assertThatThrownBy(() -> requireNonEmptyCollection(null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("null cannot be null");

      assertThatThrownBy(() -> requireNonEmptyCollection(Collections.emptyList(), null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("null cannot be empty");
    }
  }

  @Nested
  @DisplayName("requireNonEmptyArray() Tests")
  class RequireNonEmptyArrayTests {

    @Test
    void shouldSucceedForNonEmptyArray() {
      String[] arr = {"a"};
      assertThat(requireNonEmptyArray(arr, "myArray")).isSameAs(arr);
    }

    @Test
    void shouldThrowForNull() {
      assertThatThrownBy(() -> requireNonEmptyArray(null, "myArray"))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("myArray cannot be null");
    }

    @Test
    void shouldThrowForEmpty() {
      assertThatThrownBy(() -> requireNonEmptyArray(new String[0], "myArray"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("myArray cannot be empty");
    }

    @Test
    void shouldHandleNullParameterName() {
      String[] arr = {"a"};
      assertThat(requireNonEmptyArray(arr, null)).isSameAs(arr);

      assertThatThrownBy(() -> requireNonEmptyArray(null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("null cannot be null");

      assertThatThrownBy(() -> requireNonEmptyArray(new String[0], null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("null cannot be empty");
    }

    @Test
    void shouldWorkWithDifferentTypes() {
      Integer[] intArray = {1, 2, 3};
      assertThat(requireNonEmptyArray(intArray, "intArray")).isSameAs(intArray);

      Object[] objArray = {new Object()};
      assertThat(requireNonEmptyArray(objArray, "objArray")).isSameAs(objArray);
    }
  }

  @Nested
  @DisplayName("requireCondition() Tests")
  class RequireConditionTests {

    @Test
    void shouldSucceedForTrue() {
      requireCondition(true, "This should not throw");
    }

    @Test
    void shouldThrowForFalse() {
      assertThatThrownBy(() -> requireCondition(false, "Error: %s", "details"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Error: details");
    }

    @Test
    void shouldHandleNoArgs() {
      assertThatThrownBy(() -> requireCondition(false, "Simple error"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Simple error");
    }

    @Test
    void shouldHandleMultipleArgs() {
      assertThatThrownBy(
              () ->
                  requireCondition(
                      false, "Error %s with code %d and flag %b", "failure", 404, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Error failure with code 404 and flag true");
    }

    @Test
    void shouldHandleNullMessage() {
      assertThatThrownBy(() -> requireCondition(false, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHandleNullArgs() {
      assertThatThrownBy(() -> requireCondition(false, "Error: %s", (Object) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Error: null");
    }
  }

  @Nested
  @DisplayName("requireInRange() Tests")
  class RequireInRangeTests {

    @Test
    void shouldSucceedForValueInRange() {
      assertThat(requireInRange(5, 0, 10, "value")).isEqualTo(5);
    }

    @Test
    void shouldSucceedAtBoundaries() {
      assertThat(requireInRange(0, 0, 10, "value")).isEqualTo(0);
      assertThat(requireInRange(10, 0, 10, "value")).isEqualTo(10);
    }

    @Test
    void shouldThrowForLessThanMin() {
      assertThatThrownBy(() -> requireInRange(-1, 0, 10, "value"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("value must be between 0 and 10, got -1");
    }

    @Test
    void shouldThrowForGreaterThanMax() {
      assertThatThrownBy(() -> requireInRange(11, 0, 10, "value"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("value must be between 0 and 10, got 11");
    }

    @Test
    void shouldThrowForNullValue() {
      assertThatThrownBy(() -> requireInRange(null, 0, 10, "value"))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("value cannot be null");
    }

    @Test
    void shouldWorkWithStrings() {
      assertThat(requireInRange("m", "a", "z", "letter")).isEqualTo("m");
      assertThat(requireInRange("a", "a", "z", "letter")).isEqualTo("a");
      assertThat(requireInRange("z", "a", "z", "letter")).isEqualTo("z");
    }

    @Test
    void shouldThrowForStringOutOfRange() {
      assertThatThrownBy(() -> requireInRange("0", "a", "z", "letter"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("letter must be between a and z, got 0");
    }

    @Test
    void shouldHandleEqualMinMax() {
      assertThat(requireInRange(5, 5, 5, "value")).isEqualTo(5);

      assertThatThrownBy(() -> requireInRange(4, 5, 5, "value"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("value must be between 5 and 5, got 4");
    }

    @Test
    void shouldHandleNullParameterName() {
      assertThat(requireInRange(5, 0, 10, null)).isEqualTo(5);

      assertThatThrownBy(() -> requireInRange(null, 0, 10, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("null cannot be null");

      assertThatThrownBy(() -> requireInRange(-1, 0, 10, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("null must be between 0 and 10, got -1");
    }

    @Test
    void shouldThrowForNullMinMax() {
      assertThatThrownBy(() -> requireInRange(5, null, 10, "value"))
          .isInstanceOf(NullPointerException.class);

      assertThatThrownBy(() -> requireInRange(5, 0, null, "value"))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldWorkWithDifferentComparableTypes() {
      // Test with Long
      assertThat(requireInRange(5L, 0L, 10L, "longValue")).isEqualTo(5L);

      // Test with Double
      assertThat(requireInRange(5.5, 0.0, 10.0, "doubleValue")).isEqualTo(5.5);

      // Test with Character
      assertThat(requireInRange('m', 'a', 'z', "charValue")).isEqualTo('m');
    }
  }
}
