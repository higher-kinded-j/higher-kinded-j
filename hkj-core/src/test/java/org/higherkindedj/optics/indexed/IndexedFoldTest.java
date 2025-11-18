// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.indexed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IndexedFold<I, S, A> Tests")
class IndexedFoldTest {

  // Test Data Structures
  record Item(String name, int price, int quantity) {}

  record Order(List<Item> items) {}

  // Helper to create IndexedFold for List
  private <A> IndexedFold<Integer, List<A>, A> listFold() {
    return IndexedTraversals.<A>forList().asIndexedFold();
  }

  @Nested
  @DisplayName("ifoldMap() - Core indexed folding operation")
  class IFoldMapTests {

    @Test
    @DisplayName("should fold with access to indices")
    void foldWithIndices() {
      IndexedFold<Integer, List<Integer>, Integer> ifold = listFold();
      List<Integer> source = List.of(10, 20, 30);

      // Sum each value weighted by its position
      Monoid<Integer> sumMonoid =
          new Monoid<>() {
            @Override
            public Integer empty() {
              return 0;
            }

            @Override
            public Integer combine(Integer a, Integer b) {
              return a + b;
            }
          };

      int result = ifold.ifoldMap(sumMonoid, (i, v) -> v * (i + 1), source);

      // 10*1 + 20*2 + 30*3 = 10 + 40 + 90 = 140
      assertThat(result).isEqualTo(140);
    }

    @Test
    @DisplayName("should handle empty collections")
    void emptyCollection() {
      IndexedFold<Integer, List<Integer>, Integer> ifold = listFold();
      List<Integer> source = List.of();

      Monoid<Integer> sumMonoid =
          new Monoid<>() {
            @Override
            public Integer empty() {
              return 0;
            }

            @Override
            public Integer combine(Integer a, Integer b) {
              return a + b;
            }
          };

      int result = ifold.ifoldMap(sumMonoid, (i, v) -> v * i, source);

      assertThat(result).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("toIndexedList() - Extract elements with indices")
  class ToIndexedListTests {

    @Test
    @DisplayName("should extract all elements with their indices")
    void extractWithIndices() {
      IndexedFold<Integer, List<String>, String> ifold = listFold();
      List<String> source = List.of("a", "b", "c");

      List<Pair<Integer, String>> indexed = ifold.toIndexedList(source);

      assertThat(indexed)
          .containsExactly(new Pair<>(0, "a"), new Pair<>(1, "b"), new Pair<>(2, "c"));
    }

    @Test
    @DisplayName("should return empty list for empty source")
    void emptySource() {
      IndexedFold<Integer, List<String>, String> ifold = listFold();
      List<String> source = List.of();

      List<Pair<Integer, String>> indexed = ifold.toIndexedList(source);

      assertThat(indexed).isEmpty();
    }
  }

  @Nested
  @DisplayName("getAll() - Extract values ignoring indices")
  class GetAllTests {

    @Test
    @DisplayName("should extract all values")
    void extractAllValues() {
      IndexedFold<Integer, List<String>, String> ifold = listFold();
      List<String> source = List.of("x", "y", "z");

      List<String> values = ifold.getAll(source);

      assertThat(values).containsExactly("x", "y", "z");
    }
  }

  @Nested
  @DisplayName("findWithIndex() - Find elements by index-value predicate")
  class FindWithIndexTests {

    @Test
    @DisplayName("should find first matching element with index")
    void findFirstMatch() {
      IndexedFold<Integer, List<Integer>, Integer> ifold = listFold();
      List<Integer> source = List.of(5, 15, 25, 35);

      // Find first element where index is even AND value > 10
      Optional<Pair<Integer, Integer>> found =
          ifold.findWithIndex((i, v) -> i % 2 == 0 && v > 10, source);

      assertThat(found).isPresent();
      assertThat(found.get()).isEqualTo(new Pair<>(2, 25));
    }

    @Test
    @DisplayName("should return empty when no match")
    void noMatch() {
      IndexedFold<Integer, List<Integer>, Integer> ifold = listFold();
      List<Integer> source = List.of(5, 15, 25);

      Optional<Pair<Integer, Integer>> found = ifold.findWithIndex((i, v) -> v > 100, source);

      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("find() - Find elements by value predicate")
  class FindTests {

    @Test
    @DisplayName("should find first matching element with its index")
    void findByValue() {
      IndexedFold<Integer, List<Item>, Item> ifold = listFold();
      List<Item> source =
          List.of(
              new Item("Apple", 100, 5), new Item("Banana", 50, 10), new Item("Cherry", 150, 3));

      Optional<Pair<Integer, Item>> found = ifold.find(item -> item.price() > 100, source);

      assertThat(found).isPresent();
      assertThat(found.get().first()).isEqualTo(2); // Index of Cherry
      assertThat(found.get().second().name()).isEqualTo("Cherry");
    }
  }

  @Nested
  @DisplayName("existsWithIndex() - Check existence by index-value predicate")
  class ExistsWithIndexTests {

    @Test
    @DisplayName("should return true when condition is met")
    void conditionMet() {
      IndexedFold<Integer, List<Integer>, Integer> ifold = listFold();
      List<Integer> source = List.of(1, 2, 3, 4, 5);

      boolean exists = ifold.existsWithIndex((i, v) -> i == v - 1, source);

      assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("should return false when condition is not met")
    void conditionNotMet() {
      IndexedFold<Integer, List<Integer>, Integer> ifold = listFold();
      List<Integer> source = List.of(10, 20, 30);

      boolean exists = ifold.existsWithIndex((i, v) -> i > v, source);

      assertThat(exists).isFalse();
    }
  }

  @Nested
  @DisplayName("allWithIndex() - Check all elements by index-value predicate")
  class AllWithIndexTests {

    @Test
    @DisplayName("should return true when all match")
    void allMatch() {
      IndexedFold<Integer, List<Integer>, Integer> ifold = listFold();
      List<Integer> source = List.of(0, 10, 20, 30);

      // Each value should be index * 10
      boolean all = ifold.allWithIndex((i, v) -> v == i * 10, source);

      assertThat(all).isTrue();
    }

    @Test
    @DisplayName("should return false when not all match")
    void notAllMatch() {
      IndexedFold<Integer, List<Integer>, Integer> ifold = listFold();
      List<Integer> source = List.of(0, 10, 25, 30);

      boolean all = ifold.allWithIndex((i, v) -> v == i * 10, source);

      assertThat(all).isFalse();
    }

    @Test
    @DisplayName("should return true for empty collection")
    void emptyCollection() {
      IndexedFold<Integer, List<Integer>, Integer> ifold = listFold();
      List<Integer> source = List.of();

      boolean all = ifold.allWithIndex((i, v) -> false, source);

      assertThat(all).isTrue(); // Vacuous truth
    }
  }

  @Nested
  @DisplayName("length() and isEmpty() - Count operations")
  class CountTests {

    @Test
    @DisplayName("length should count elements")
    void lengthCount() {
      IndexedFold<Integer, List<String>, String> ifold = listFold();
      List<String> source = List.of("a", "b", "c", "d");

      int count = ifold.length(source);

      assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("isEmpty should return true for empty")
    void isEmptyTrue() {
      IndexedFold<Integer, List<String>, String> ifold = listFold();
      List<String> source = List.of();

      boolean empty = ifold.isEmpty(source);

      assertThat(empty).isTrue();
    }

    @Test
    @DisplayName("isEmpty should return false for non-empty")
    void isEmptyFalse() {
      IndexedFold<Integer, List<String>, String> ifold = listFold();
      List<String> source = List.of("x");

      boolean empty = ifold.isEmpty(source);

      assertThat(empty).isFalse();
    }
  }

  @Nested
  @DisplayName("iandThen() - Composition with indexed folds")
  class IAndThenTests {

    @Test
    @DisplayName("should compose with paired indices")
    void composeIndexedFolds() {
      IndexedFold<Integer, List<Map<String, Integer>>, Map<String, Integer>> listFold =
          IndexedTraversals.<Map<String, Integer>>forList().asIndexedFold();
      IndexedFold<String, Map<String, Integer>, Integer> mapFold =
          IndexedTraversals.<String, Integer>forMap().asIndexedFold();

      IndexedFold<Pair<Integer, String>, List<Map<String, Integer>>, Integer> composed =
          listFold.iandThen(mapFold);

      List<Map<String, Integer>> source = List.of(Map.of("a", 1), Map.of("b", 2, "c", 3));

      List<Pair<Pair<Integer, String>, Integer>> indexed = composed.toIndexedList(source);

      assertThat(indexed).hasSize(3);
      assertThat(
              indexed.stream()
                  .anyMatch(t -> t.first().equals(new Pair<>(0, "a")) && t.second() == 1))
          .isTrue();
    }
  }

  @Nested
  @DisplayName("andThen() - Composition with regular folds")
  class AndThenTests {

    @Test
    @DisplayName("should compose with regular fold preserving index")
    void composeWithRegularFold() {
      IndexedFold<Integer, List<Order>, Order> ordersFold = listFold();
      Fold<Order, Item> itemsFold = Fold.of(Order::items);

      IndexedFold<Integer, List<Order>, Item> composed = ordersFold.andThen(itemsFold);

      List<Order> source =
          List.of(
              new Order(List.of(new Item("A", 10, 1), new Item("B", 20, 2))),
              new Order(List.of(new Item("C", 30, 3))));

      List<Pair<Integer, Item>> indexed = composed.toIndexedList(source);

      // All items have the order's index
      assertThat(indexed).hasSize(3);
      assertThat(indexed.get(0).first()).isEqualTo(0);
      assertThat(indexed.get(1).first()).isEqualTo(0);
      assertThat(indexed.get(2).first()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("filterIndex() - Index-based filtering")
  class FilterIndexTests {

    @Test
    @DisplayName("should filter by index predicate")
    void filterByIndex() {
      IndexedFold<Integer, List<String>, String> ifold = listFold();
      IndexedFold<Integer, List<String>, String> evens = ifold.filterIndex(i -> i % 2 == 0);
      List<String> source = List.of("a", "b", "c", "d", "e");

      List<Pair<Integer, String>> indexed = evens.toIndexedList(source);

      assertThat(indexed)
          .containsExactly(new Pair<>(0, "a"), new Pair<>(2, "c"), new Pair<>(4, "e"));
    }
  }

  @Nested
  @DisplayName("asFold() - Conversion to regular fold")
  class AsFoldTests {

    @Test
    @DisplayName("should convert to regular fold ignoring indices")
    void convertToFold() {
      IndexedFold<Integer, List<Item>, Item> ifold = listFold();
      Fold<List<Item>, Item> fold = ifold.asFold();

      List<Item> source =
          List.of(new Item("A", 100, 1), new Item("B", 200, 2), new Item("C", 150, 3));

      // Use regular fold operations
      int totalPrice =
          fold.foldMap(
              new Monoid<>() {
                @Override
                public Integer empty() {
                  return 0;
                }

                @Override
                public Integer combine(Integer a, Integer b) {
                  return a + b;
                }
              },
              Item::price,
              source);

      assertThat(totalPrice).isEqualTo(450);
    }
  }
}
