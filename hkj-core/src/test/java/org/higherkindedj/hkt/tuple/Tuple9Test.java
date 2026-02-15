// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for {@link Tuple9}. */
@DisplayName("Tuple9 Test Suite")
class Tuple9Test {

  @Nested
  @DisplayName("Constructor and Accessors")
  class ConstructorAndAccessors {

    @Test
    @DisplayName("Constructor creates tuple with correct elements")
    void constructorCreatesCorrectTuple() {
      Tuple9<String, Integer, Boolean, Double, Character, Long, Short, Byte, Float> tuple =
          new Tuple9<>("Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f);

      assertThat(tuple._1()).isEqualTo("Alice");
      assertThat(tuple._2()).isEqualTo(30);
      assertThat(tuple._3()).isTrue();
      assertThat(tuple._4()).isEqualTo(5.5);
      assertThat(tuple._5()).isEqualTo('A');
      assertThat(tuple._6()).isEqualTo(100L);
      assertThat(tuple._7()).isEqualTo((short) 7);
      assertThat(tuple._8()).isEqualTo((byte) 1);
      assertThat(tuple._9()).isEqualTo(3.14f);
    }

    @Test
    @DisplayName("Constructor accepts null elements")
    void constructorAcceptsNullElements() {
      Tuple9<String, Integer, Boolean, Double, Character, Long, Short, Byte, Float> tuple =
          new Tuple9<>(null, null, null, null, null, null, null, null, null);

      assertThat(tuple._1()).isNull();
      assertThat(tuple._2()).isNull();
      assertThat(tuple._3()).isNull();
      assertThat(tuple._4()).isNull();
      assertThat(tuple._5()).isNull();
      assertThat(tuple._6()).isNull();
      assertThat(tuple._7()).isNull();
      assertThat(tuple._8()).isNull();
      assertThat(tuple._9()).isNull();
    }

    @Test
    @DisplayName("Tuple.of() factory creates Tuple9")
    void factoryCreatesCorrectTuple() {
      Tuple9<String, Integer, Boolean, Double, Character, Long, Short, Byte, Float> tuple =
          Tuple.of("Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f);

      assertThat(tuple._1()).isEqualTo("Alice");
      assertThat(tuple._9()).isEqualTo(3.14f);
    }
  }

  @Nested
  @DisplayName("map() - Transform All Elements")
  class MapAllElements {

    @Test
    @DisplayName("map() transforms all nine elements")
    void mapTransformsAllElements() {
      Tuple9<String, Integer, Boolean, Double, Character, Long, Short, Byte, Float> tuple =
          new Tuple9<>("Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f);

      Tuple9<Integer, String, String, Integer, String, String, Integer, Integer, Integer> result =
          tuple.map(
              String::length,
              age -> age + " years",
              active -> active ? "yes" : "no",
              Double::intValue,
              Object::toString,
              l -> l + "L",
              Short::intValue,
              Byte::intValue,
              Float::intValue);

      assertThat(result._1()).isEqualTo(5);
      assertThat(result._2()).isEqualTo("30 years");
      assertThat(result._3()).isEqualTo("yes");
      assertThat(result._4()).isEqualTo(5);
      assertThat(result._5()).isEqualTo("A");
      assertThat(result._6()).isEqualTo("100L");
      assertThat(result._7()).isEqualTo(7);
      assertThat(result._8()).isEqualTo(1);
      assertThat(result._9()).isEqualTo(3);
    }

    @Test
    @DisplayName("map() with identity functions returns equivalent tuple")
    void mapWithIdentityReturnsEquivalent() {
      Tuple9<String, Integer, Boolean, Double, Character, Long, Short, Byte, Float> original =
          new Tuple9<>("Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f);

      Tuple9<String, Integer, Boolean, Double, Character, Long, Short, Byte, Float> result =
          original.map(
              Function.identity(),
              Function.identity(),
              Function.identity(),
              Function.identity(),
              Function.identity(),
              Function.identity(),
              Function.identity(),
              Function.identity(),
              Function.identity());

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("map() throws NullPointerException when any mapper is null")
    void mapThrowsWhenMapperNull() {
      Tuple9<String, Integer, Boolean, Double, Character, Long, Short, Byte, Float> tuple =
          new Tuple9<>("Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f);

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      null,
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity()))
          .withMessageContaining("firstMapper");

      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  tuple.map(
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      Function.identity(),
                      null))
          .withMessageContaining("ninthMapper");
    }
  }

  @Nested
  @DisplayName("Individual mapN() Methods")
  class IndividualMapMethods {

    private final Tuple9<String, Integer, Boolean, Double, Character, Long, Short, Byte, Float>
        tuple = new Tuple9<>("Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f);

    @Test
    @DisplayName("mapFirst() transforms only the first element")
    void mapFirstTransformsOnlyFirst() {
      var result = tuple.mapFirst(String::length);

      assertThat(result._1()).isEqualTo(5);
      assertThat(result._2()).isEqualTo(30);
      assertThat(result._9()).isEqualTo(3.14f);
    }

    @Test
    @DisplayName("mapNinth() transforms only the ninth element")
    void mapNinthTransformsOnlyNinth() {
      var result = tuple.mapNinth(f -> f * 2);

      assertThat(result._1()).isEqualTo("Alice");
      assertThat(result._9()).isEqualTo(6.28f);
    }

    @Test
    @DisplayName("Individual map methods with identity return equivalent tuples")
    void mapWithIdentityReturnsEquivalent() {
      assertThat(tuple.mapFirst(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapSecond(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapThird(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapFourth(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapFifth(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapSixth(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapSeventh(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapEighth(Function.identity())).isEqualTo(tuple);
      assertThat(tuple.mapNinth(Function.identity())).isEqualTo(tuple);
    }

    @Test
    @DisplayName("Individual map methods throw NullPointerException when mapper is null")
    void mapThrowsWhenMapperNull() {
      assertThatNullPointerException().isThrownBy(() -> tuple.mapFirst(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapSecond(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapThird(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapFourth(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapFifth(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapSixth(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapSeventh(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapEighth(null));
      assertThatNullPointerException().isThrownBy(() -> tuple.mapNinth(null));
    }
  }

  @Nested
  @DisplayName("Record Methods - equals, hashCode, toString")
  class RecordMethods {

    @Test
    @DisplayName("equals() returns true for tuples with same values")
    void equalsReturnsTrueForSameValues() {
      var tuple1 = new Tuple9<>("Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f);
      var tuple2 = new Tuple9<>("Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f);

      assertThat(tuple1).isEqualTo(tuple2);
    }

    @Test
    @DisplayName("equals() returns false for tuples with different values")
    void equalsReturnsFalseForDifferentValues() {
      var tuple1 = new Tuple9<>("Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f);

      assertThat(tuple1)
          .isNotEqualTo(new Tuple9<>("Bob", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f));
      assertThat(tuple1)
          .isNotEqualTo(
              new Tuple9<>("Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 9.99f));
    }

    @Test
    @DisplayName("hashCode() is consistent with equals()")
    void hashCodeConsistentWithEquals() {
      var tuple1 = new Tuple9<>("Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f);
      var tuple2 = new Tuple9<>("Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f);

      assertThat(tuple1.hashCode()).isEqualTo(tuple2.hashCode());
    }

    @Test
    @DisplayName("toString() contains all element values")
    void toStringContainsAllValues() {
      var tuple = new Tuple9<>("Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f);

      assertThat(tuple.toString())
          .contains("Alice", "30", "true", "5.5", "A", "100", "7", "1", "3.14");
    }
  }

  @Nested
  @DisplayName("Immutability")
  class Immutability {

    @Test
    @DisplayName("Mapping operations do not modify original tuple")
    void mappingDoesNotModifyOriginal() {
      Tuple9<String, Integer, Boolean, Double, Character, Long, Short, Byte, Float> original =
          new Tuple9<>("Alice", 30, true, 5.5, 'A', 100L, (short) 7, (byte) 1, 3.14f);

      original.map(
          String::toUpperCase,
          n -> n * 2,
          b -> !b,
          d -> d * 2,
          Character::toLowerCase,
          l -> l * 2,
          s -> s * 2,
          b -> b * 2,
          f -> f * 2);
      original.mapFirst(String::toUpperCase);
      original.mapNinth(f -> f * 2);

      assertThat(original._1()).isEqualTo("Alice");
      assertThat(original._2()).isEqualTo(30);
      assertThat(original._3()).isTrue();
      assertThat(original._4()).isEqualTo(5.5);
      assertThat(original._5()).isEqualTo('A');
      assertThat(original._6()).isEqualTo(100L);
      assertThat(original._7()).isEqualTo((short) 7);
      assertThat(original._8()).isEqualTo((byte) 1);
      assertThat(original._9()).isEqualTo(3.14f);
    }
  }
}
