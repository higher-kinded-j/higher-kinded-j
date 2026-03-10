// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Pair Test Suite")
class PairTest {

  @Nested
  @DisplayName("Of Tests")
  class OfTests {

    @Test
    @DisplayName("of should create a pair with the given values")
    void of_shouldCreatePair() {
      var pair = Pair.of("hello", 42);
      assertThat(pair.first()).isEqualTo("hello");
      assertThat(pair.second()).isEqualTo(42);
    }

    @Test
    @DisplayName("of should allow null values")
    void of_shouldAllowNulls() {
      var pair = Pair.of(null, null);
      assertThat(pair.first()).isNull();
      assertThat(pair.second()).isNull();
    }
  }

  @Nested
  @DisplayName("MapFirst Tests")
  class MapFirstTests {

    @Test
    @DisplayName("mapFirst should transform the first element")
    void mapFirst_shouldTransformFirst() {
      var pair = Pair.of(10, "hello");
      var result = pair.mapFirst(x -> x * 2);
      assertThat(result.first()).isEqualTo(20);
      assertThat(result.second()).isEqualTo("hello");
    }

    @Test
    @DisplayName("mapFirst should change the type of the first element")
    void mapFirst_shouldChangeType() {
      var pair = Pair.of(42, "world");
      Pair<String, String> result = pair.mapFirst(String::valueOf);
      assertThat(result.first()).isEqualTo("42");
      assertThat(result.second()).isEqualTo("world");
    }
  }

  @Nested
  @DisplayName("MapSecond Tests")
  class MapSecondTests {

    @Test
    @DisplayName("mapSecond should transform the second element")
    void mapSecond_shouldTransformSecond() {
      var pair = Pair.of("hello", 10);
      var result = pair.mapSecond(x -> x * 3);
      assertThat(result.first()).isEqualTo("hello");
      assertThat(result.second()).isEqualTo(30);
    }

    @Test
    @DisplayName("mapSecond should change the type of the second element")
    void mapSecond_shouldChangeType() {
      var pair = Pair.of("hello", 42);
      Pair<String, String> result = pair.mapSecond(String::valueOf);
      assertThat(result.first()).isEqualTo("hello");
      assertThat(result.second()).isEqualTo("42");
    }
  }

  @Nested
  @DisplayName("Bimap Tests")
  class BimapTests {

    @Test
    @DisplayName("bimap should transform both elements")
    void bimap_shouldTransformBoth() {
      var pair = Pair.of(10, "hello");
      var result = pair.bimap(x -> x * 2, String::toUpperCase);
      assertThat(result.first()).isEqualTo(20);
      assertThat(result.second()).isEqualTo("HELLO");
    }

    @Test
    @DisplayName("bimap should change both types")
    void bimap_shouldChangeBothTypes() {
      var pair = Pair.of(42, true);
      Pair<String, String> result = pair.bimap(String::valueOf, String::valueOf);
      assertThat(result.first()).isEqualTo("42");
      assertThat(result.second()).isEqualTo("true");
    }

    @Test
    @DisplayName("bimap with identity functions should return equal pair")
    void bimap_withIdentity() {
      var pair = Pair.of("hello", 42);
      var result = pair.bimap(x -> x, x -> x);
      assertThat(result).isEqualTo(pair);
    }
  }

  @Nested
  @DisplayName("Equals and HashCode Tests")
  class EqualsHashCodeTests {

    @Test
    @DisplayName("equal pairs should be equal")
    void equalPairs() {
      var pair1 = Pair.of("a", 1);
      var pair2 = Pair.of("a", 1);
      assertThat(pair1).isEqualTo(pair2);
      assertThat(pair1.hashCode()).isEqualTo(pair2.hashCode());
    }

    @Test
    @DisplayName("different pairs should not be equal")
    void differentPairs() {
      var pair1 = Pair.of("a", 1);
      var pair2 = Pair.of("b", 2);
      assertThat(pair1).isNotEqualTo(pair2);
    }
  }

  @Nested
  @DisplayName("ToString Tests")
  class ToStringTests {

    @Test
    @DisplayName("toString should include both values")
    void toString_shouldIncludeBothValues() {
      var pair = Pair.of("hello", 42);
      assertThat(pair.toString()).contains("hello").contains("42");
    }
  }
}
