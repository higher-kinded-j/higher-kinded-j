// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.typeclass;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Semigroups utility class")
class SemigroupsTest {

  @Nested
  @DisplayName("List semigroup")
  class ListSemigroupTests {

    @Test
    @DisplayName("should combine two lists by concatenation")
    void shouldCombineByConcatenation() {
      Semigroup<List<String>> listSemigroup = Semigroups.list();

      List<String> result = listSemigroup.combine(List.of("a", "b"), List.of("c", "d"));

      assertThat(result).containsExactly("a", "b", "c", "d");
    }

    @Test
    @DisplayName("should handle empty lists")
    void shouldHandleEmptyLists() {
      Semigroup<List<String>> listSemigroup = Semigroups.list();

      List<String> result = listSemigroup.combine(List.of(), List.of("a"));

      assertThat(result).containsExactly("a");
    }

    @Test
    @DisplayName("should satisfy associativity law")
    void shouldSatisfyAssociativity() {
      Semigroup<List<Integer>> listSemigroup = Semigroups.list();
      List<Integer> a = List.of(1);
      List<Integer> b = List.of(2);
      List<Integer> c = List.of(3);

      List<Integer> leftAssoc = listSemigroup.combine(listSemigroup.combine(a, b), c);
      List<Integer> rightAssoc = listSemigroup.combine(a, listSemigroup.combine(b, c));

      assertThat(leftAssoc).isEqualTo(rightAssoc).containsExactly(1, 2, 3);
    }
  }

  @Nested
  @DisplayName("Set semigroup")
  class SetSemigroupTests {

    @Test
    @DisplayName("should combine two sets by union")
    void shouldCombineByUnion() {
      Semigroup<Set<String>> setSemigroup = Semigroups.set();

      Set<String> result = setSemigroup.combine(Set.of("a", "b"), Set.of("c", "d"));

      assertThat(result).containsExactlyInAnyOrder("a", "b", "c", "d");
    }

    @Test
    @DisplayName("should handle overlapping sets")
    void shouldHandleOverlappingSets() {
      Semigroup<Set<String>> setSemigroup = Semigroups.set();

      Set<String> result = setSemigroup.combine(Set.of("a", "b"), Set.of("b", "c"));

      assertThat(result).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    @DisplayName("should satisfy associativity law")
    void shouldSatisfyAssociativity() {
      Semigroup<Set<Integer>> setSemigroup = Semigroups.set();
      Set<Integer> a = new HashSet<>(Set.of(1));
      Set<Integer> b = new HashSet<>(Set.of(2));
      Set<Integer> c = new HashSet<>(Set.of(3));

      Set<Integer> leftAssoc = setSemigroup.combine(setSemigroup.combine(a, b), c);
      Set<Integer> rightAssoc = setSemigroup.combine(a, setSemigroup.combine(b, c));

      assertThat(leftAssoc).isEqualTo(rightAssoc);
    }
  }

  @Nested
  @DisplayName("String semigroup (basic)")
  class StringSemigroupBasicTests {

    @Test
    @DisplayName("should combine two strings by concatenation")
    void shouldCombineByConcatenation() {
      Semigroup<String> stringSemigroup = Semigroups.string();

      String result = stringSemigroup.combine("Hello", " World");

      assertThat(result).isEqualTo("Hello World");
    }

    @Test
    @DisplayName("should handle empty strings")
    void shouldHandleEmptyStrings() {
      Semigroup<String> stringSemigroup = Semigroups.string();

      assertThat(stringSemigroup.combine("", "test")).isEqualTo("test");
      assertThat(stringSemigroup.combine("test", "")).isEqualTo("test");
    }

    @Test
    @DisplayName("should satisfy associativity law")
    void shouldSatisfyAssociativity() {
      Semigroup<String> stringSemigroup = Semigroups.string();
      String a = "a";
      String b = "b";
      String c = "c";

      String leftAssoc = stringSemigroup.combine(stringSemigroup.combine(a, b), c);
      String rightAssoc = stringSemigroup.combine(a, stringSemigroup.combine(b, c));

      assertThat(leftAssoc).isEqualTo(rightAssoc).isEqualTo("abc");
    }
  }

  @Nested
  @DisplayName("String semigroup (with delimiter)")
  class StringSemigroupDelimiterTests {

    @Test
    @DisplayName("should combine two strings with delimiter")
    void shouldCombineWithDelimiter() {
      Semigroup<String> delimitedSemigroup = Semigroups.string(", ");

      String result = delimitedSemigroup.combine("Alice", "Bob");

      assertThat(result).isEqualTo("Alice, Bob");
    }

    @Test
    @DisplayName("should handle empty delimiter")
    void shouldHandleEmptyDelimiter() {
      Semigroup<String> emptyDelimiter = Semigroups.string("");

      String result = emptyDelimiter.combine("Hello", "World");

      assertThat(result).isEqualTo("HelloWorld");
    }

    @Test
    @DisplayName("should work with multi-character delimiter")
    void shouldWorkWithMultiCharDelimiter() {
      Semigroup<String> multiDelimiter = Semigroups.string(" -> ");

      String result = multiDelimiter.combine("Step1", "Step2");

      assertThat(result).isEqualTo("Step1 -> Step2");
    }
  }

  @Nested
  @DisplayName("first semigroup")
  class FirstSemigroupTests {

    @Test
    @DisplayName("should always return the first value")
    void shouldReturnFirst() {
      Semigroup<String> firstSemigroup = Semigroups.first();

      assertThat(firstSemigroup.combine("first", "second")).isEqualTo("first");
      assertThat(firstSemigroup.combine("a", "b")).isEqualTo("a");
    }

    @Test
    @DisplayName("should work with numbers")
    void shouldWorkWithNumbers() {
      Semigroup<Integer> firstSemigroup = Semigroups.first();

      assertThat(firstSemigroup.combine(1, 2)).isEqualTo(1);
      assertThat(firstSemigroup.combine(100, 200)).isEqualTo(100);
    }

    @Test
    @DisplayName("should satisfy associativity law")
    void shouldSatisfyAssociativity() {
      Semigroup<String> firstSemigroup = Semigroups.first();
      String a = "a";
      String b = "b";
      String c = "c";

      String leftAssoc = firstSemigroup.combine(firstSemigroup.combine(a, b), c);
      String rightAssoc = firstSemigroup.combine(a, firstSemigroup.combine(b, c));

      assertThat(leftAssoc).isEqualTo(rightAssoc).isEqualTo("a");
    }
  }

  @Nested
  @DisplayName("last semigroup")
  class LastSemigroupTests {

    @Test
    @DisplayName("should always return the last value")
    void shouldReturnLast() {
      Semigroup<String> lastSemigroup = Semigroups.last();

      assertThat(lastSemigroup.combine("first", "second")).isEqualTo("second");
      assertThat(lastSemigroup.combine("a", "b")).isEqualTo("b");
    }

    @Test
    @DisplayName("should work with numbers")
    void shouldWorkWithNumbers() {
      Semigroup<Integer> lastSemigroup = Semigroups.last();

      assertThat(lastSemigroup.combine(1, 2)).isEqualTo(2);
      assertThat(lastSemigroup.combine(100, 200)).isEqualTo(200);
    }

    @Test
    @DisplayName("should satisfy associativity law")
    void shouldSatisfyAssociativity() {
      Semigroup<String> lastSemigroup = Semigroups.last();
      String a = "a";
      String b = "b";
      String c = "c";

      String leftAssoc = lastSemigroup.combine(lastSemigroup.combine(a, b), c);
      String rightAssoc = lastSemigroup.combine(a, lastSemigroup.combine(b, c));

      assertThat(leftAssoc).isEqualTo(rightAssoc).isEqualTo("c");
    }
  }
}
