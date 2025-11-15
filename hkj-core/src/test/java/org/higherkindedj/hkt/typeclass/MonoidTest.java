// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.typeclass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Monoid interface default methods")
class MonoidTest {

  @Nested
  @DisplayName("combineAll method")
  class CombineAllTests {

    @Test
    @DisplayName("should combine all elements in a non-empty list")
    void shouldCombineAllElements() {
      Monoid<Integer> intAddition = Monoids.integerAddition();
      List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);

      Integer result = intAddition.combineAll(numbers);

      assertThat(result).isEqualTo(15);
    }

    @Test
    @DisplayName("should return empty for an empty list")
    void shouldReturnEmptyForEmptyList() {
      Monoid<Integer> intAddition = Monoids.integerAddition();
      List<Integer> empty = Collections.emptyList();

      Integer result = intAddition.combineAll(empty);

      assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("should work with string concatenation")
    void shouldWorkWithStrings() {
      Monoid<String> stringMonoid = Monoids.string();
      List<String> words = Arrays.asList("Hello", " ", "World", "!");

      String result = stringMonoid.combineAll(words);

      assertThat(result).isEqualTo("Hello World!");
    }

    @Test
    @DisplayName("should work with boolean AND")
    void shouldWorkWithBooleanAnd() {
      Monoid<Boolean> boolAnd = Monoids.booleanAnd();
      List<Boolean> allTrue = Arrays.asList(true, true, true);
      List<Boolean> oneFalse = Arrays.asList(true, false, true);

      assertThat(boolAnd.combineAll(allTrue)).isTrue();
      assertThat(boolAnd.combineAll(oneFalse)).isFalse();
    }

    @Test
    @DisplayName("should work with lists")
    void shouldWorkWithLists() {
      Monoid<List<String>> listMonoid = Monoids.list();
      List<List<String>> lists =
          Arrays.asList(Arrays.asList("a", "b"), Arrays.asList("c"), Arrays.asList("d", "e"));

      List<String> result = listMonoid.combineAll(lists);

      assertThat(result).containsExactly("a", "b", "c", "d", "e");
    }
  }

  @Nested
  @DisplayName("combineN method")
  class CombineNTests {

    @Test
    @DisplayName("should return empty when n is 0")
    void shouldReturnEmptyWhenNIsZero() {
      Monoid<Integer> intAddition = Monoids.integerAddition();

      Integer result = intAddition.combineN(5, 0);

      assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("should return value when n is 1")
    void shouldReturnValueWhenNIsOne() {
      Monoid<Integer> intAddition = Monoids.integerAddition();

      Integer result = intAddition.combineN(5, 1);

      assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("should combine value n times with addition")
    void shouldCombineNTimesWithAddition() {
      Monoid<Integer> intAddition = Monoids.integerAddition();

      Integer result = intAddition.combineN(5, 4);

      assertThat(result).isEqualTo(20); // 5 + 5 + 5 + 5
    }

    @Test
    @DisplayName("should combine value n times with multiplication")
    void shouldCombineNTimesWithMultiplication() {
      Monoid<Integer> intMultiplication = Monoids.integerMultiplication();

      Integer result = intMultiplication.combineN(2, 5);

      assertThat(result).isEqualTo(32); // 2 * 2 * 2 * 2 * 2
    }

    @Test
    @DisplayName("should combine string n times")
    void shouldCombineStringNTimes() {
      Monoid<String> stringMonoid = Monoids.string();

      String result = stringMonoid.combineN("ab", 3);

      assertThat(result).isEqualTo("ababab");
    }

    @Test
    @DisplayName("should throw exception when n is negative")
    void shouldThrowWhenNIsNegative() {
      Monoid<Integer> intAddition = Monoids.integerAddition();

      assertThatThrownBy(() -> intAddition.combineN(5, -1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("n must be non-negative");
    }

    @Test
    @DisplayName("should work with long addition")
    void shouldWorkWithLongAddition() {
      Monoid<Long> longAddition = Monoids.longAddition();

      Long result = longAddition.combineN(10L, 3);

      assertThat(result).isEqualTo(30L);
    }

    @Test
    @DisplayName("should work with double multiplication")
    void shouldWorkWithDoubleMultiplication() {
      Monoid<Double> doubleMultiplication = Monoids.doubleMultiplication();

      Double result = doubleMultiplication.combineN(2.0, 3);

      assertThat(result).isEqualTo(8.0); // 2.0 * 2.0 * 2.0
    }
  }

  @Nested
  @DisplayName("isEmpty method")
  class IsEmptyTests {

    @Test
    @DisplayName("should return true for empty value with integer addition")
    void shouldReturnTrueForEmptyInteger() {
      Monoid<Integer> intAddition = Monoids.integerAddition();

      assertThat(intAddition.isEmpty(0)).isTrue();
      assertThat(intAddition.isEmpty(1)).isFalse();
      assertThat(intAddition.isEmpty(100)).isFalse();
    }

    @Test
    @DisplayName("should return true for empty value with integer multiplication")
    void shouldReturnTrueForEmptyIntegerMultiplication() {
      Monoid<Integer> intMultiplication = Monoids.integerMultiplication();

      assertThat(intMultiplication.isEmpty(1)).isTrue();
      assertThat(intMultiplication.isEmpty(0)).isFalse();
      assertThat(intMultiplication.isEmpty(2)).isFalse();
    }

    @Test
    @DisplayName("should return true for empty string")
    void shouldReturnTrueForEmptyString() {
      Monoid<String> stringMonoid = Monoids.string();

      assertThat(stringMonoid.isEmpty("")).isTrue();
      assertThat(stringMonoid.isEmpty("a")).isFalse();
      assertThat(stringMonoid.isEmpty(" ")).isFalse();
    }

    @Test
    @DisplayName("should return true for empty list")
    void shouldReturnTrueForEmptyList() {
      Monoid<List<String>> listMonoid = Monoids.list();

      assertThat(listMonoid.isEmpty(Collections.emptyList())).isTrue();
      assertThat(listMonoid.isEmpty(Arrays.asList("a"))).isFalse();
    }

    @Test
    @DisplayName("should return true for true with boolean AND")
    void shouldReturnTrueForBooleanAnd() {
      Monoid<Boolean> boolAnd = Monoids.booleanAnd();

      assertThat(boolAnd.isEmpty(true)).isTrue();
      assertThat(boolAnd.isEmpty(false)).isFalse();
    }

    @Test
    @DisplayName("should return true for false with boolean OR")
    void shouldReturnTrueForBooleanOr() {
      Monoid<Boolean> boolOr = Monoids.booleanOr();

      assertThat(boolOr.isEmpty(false)).isTrue();
      assertThat(boolOr.isEmpty(true)).isFalse();
    }

    @Test
    @DisplayName("should work with long addition")
    void shouldWorkWithLongAddition() {
      Monoid<Long> longAddition = Monoids.longAddition();

      assertThat(longAddition.isEmpty(0L)).isTrue();
      assertThat(longAddition.isEmpty(1L)).isFalse();
    }

    @Test
    @DisplayName("should work with double addition")
    void shouldWorkWithDoubleAddition() {
      Monoid<Double> doubleAddition = Monoids.doubleAddition();

      assertThat(doubleAddition.isEmpty(0.0)).isTrue();
      assertThat(doubleAddition.isEmpty(1.0)).isFalse();
    }
  }

  @Nested
  @DisplayName("Monoid laws")
  class MonoidLawTests {

    @Test
    @DisplayName("should satisfy left identity law")
    void shouldSatisfyLeftIdentity() {
      Monoid<Integer> intAddition = Monoids.integerAddition();
      Integer value = 42;

      Integer result = intAddition.combine(intAddition.empty(), value);

      assertThat(result).isEqualTo(value);
    }

    @Test
    @DisplayName("should satisfy right identity law")
    void shouldSatisfyRightIdentity() {
      Monoid<Integer> intAddition = Monoids.integerAddition();
      Integer value = 42;

      Integer result = intAddition.combine(value, intAddition.empty());

      assertThat(result).isEqualTo(value);
    }

    @Test
    @DisplayName("should satisfy associativity law")
    void shouldSatisfyAssociativity() {
      Monoid<Integer> intAddition = Monoids.integerAddition();
      Integer a = 1;
      Integer b = 2;
      Integer c = 3;

      Integer leftAssoc = intAddition.combine(intAddition.combine(a, b), c);
      Integer rightAssoc = intAddition.combine(a, intAddition.combine(b, c));

      assertThat(leftAssoc).isEqualTo(rightAssoc);
    }
  }
}
