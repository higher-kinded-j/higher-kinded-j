// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.indexed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IndexedTraversal<I, S, A> Tests")
class IndexedTraversalTest {

  // Test Data Structures
  record User(String name, int age, boolean active) {
    User withName(String newName) {
      return new User(newName, age, active);
    }

    User withAge(int newAge) {
      return new User(name, newAge, active);
    }
  }

  record Team(String name, List<User> members) {}

  @Nested
  @DisplayName("forList() - List indexed traversal")
  class ForListTests {

    @Test
    @DisplayName("should modify elements with access to their indices")
    void modifyWithIndex() {
      IndexedTraversal<Integer, List<String>, String> ilist = IndexedTraversals.forList();
      List<String> source = List.of("a", "b", "c");

      List<String> result = IndexedTraversals.imodify(ilist, (i, s) -> s + "#" + i, source);

      assertThat(result).containsExactly("a#0", "b#1", "c#2");
    }

    @Test
    @DisplayName("should handle empty lists")
    void emptyList() {
      IndexedTraversal<Integer, List<String>, String> ilist = IndexedTraversals.forList();
      List<String> source = List.of();

      List<String> result = IndexedTraversals.imodify(ilist, (i, s) -> s + "#" + i, source);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should extract elements with their indices")
    void toIndexedList() {
      IndexedTraversal<Integer, List<String>, String> ilist = IndexedTraversals.forList();
      List<String> source = List.of("a", "b", "c");

      List<Pair<Integer, String>> indexed = IndexedTraversals.toIndexedList(ilist, source);

      assertThat(indexed)
          .containsExactly(new Pair<>(0, "a"), new Pair<>(1, "b"), new Pair<>(2, "c"));
    }

    @Test
    @DisplayName("should extract values ignoring indices")
    void getAll() {
      IndexedTraversal<Integer, List<String>, String> ilist = IndexedTraversals.forList();
      List<String> source = List.of("x", "y", "z");

      List<String> values = IndexedTraversals.getAll(ilist, source);

      assertThat(values).containsExactly("x", "y", "z");
    }

    @Test
    @DisplayName("should count elements correctly")
    void length() {
      IndexedTraversal<Integer, List<String>, String> ilist = IndexedTraversals.forList();
      List<String> source = List.of("a", "b", "c", "d", "e");

      int count = IndexedTraversals.length(ilist, source);

      assertThat(count).isEqualTo(5);
    }

    @Test
    @DisplayName("should work with effectful modifications")
    void modifyWithEffect() {
      IndexedTraversal<Integer, List<Integer>, Integer> ilist = IndexedTraversals.forList();
      List<Integer> source = List.of(10, 20, 30);

      // Use Optional as the effect - fail if index is 1 and value is 20
      Applicative<OptionalKind.Witness> optionalApp = OptionalMonad.INSTANCE;
      Kind<OptionalKind.Witness, List<Integer>> result =
          ilist.imodifyF(
              (i, v) -> {
                if (i == 1 && v == 20) {
                  return OptionalKindHelper.OPTIONAL.widen(Optional.empty());
                }
                return OptionalKindHelper.OPTIONAL.widen(Optional.of(v * (i + 1)));
              },
              source,
              optionalApp);

      Optional<List<Integer>> unwrapped = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(unwrapped).isEmpty();
    }
  }

  @Nested
  @DisplayName("forMap() - Map indexed traversal")
  class ForMapTests {

    @Test
    @DisplayName("should modify values with access to their keys")
    void modifyWithKey() {
      IndexedTraversal<String, Map<String, Integer>, Integer> imap = IndexedTraversals.forMap();
      Map<String, Integer> source = new HashMap<>();
      source.put("a", 1);
      source.put("b", 2);
      source.put("c", 3);

      Map<String, Integer> result =
          IndexedTraversals.imodify(imap, (key, value) -> value * key.length(), source);

      assertThat(result).containsEntry("a", 1).containsEntry("b", 2).containsEntry("c", 3);
    }

    @Test
    @DisplayName("should handle empty maps")
    void emptyMap() {
      IndexedTraversal<String, Map<String, Integer>, Integer> imap = IndexedTraversals.forMap();
      Map<String, Integer> source = Map.of();

      Map<String, Integer> result =
          IndexedTraversals.imodify(imap, (key, value) -> value * 2, source);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should extract key-value pairs")
    void toIndexedListFromMap() {
      IndexedTraversal<String, Map<String, Integer>, Integer> imap = IndexedTraversals.forMap();
      Map<String, Integer> source = Map.of("x", 10, "y", 20);

      List<Pair<String, Integer>> indexed = IndexedTraversals.toIndexedList(imap, source);

      assertThat(indexed).hasSize(2);
      assertThat(indexed).anyMatch(t -> t.first().equals("x") && t.second().equals(10));
      assertThat(indexed).anyMatch(t -> t.first().equals("y") && t.second().equals(20));
    }
  }

  @Nested
  @DisplayName("filterIndex() - Index-based filtering")
  class FilterIndexTests {

    @Test
    @DisplayName("should filter by even indices")
    void filterEvenIndices() {
      IndexedTraversal<Integer, List<String>, String> ilist = IndexedTraversals.forList();
      IndexedTraversal<Integer, List<String>, String> evens = ilist.filterIndex(i -> i % 2 == 0);
      List<String> source = List.of("a", "b", "c", "d", "e");

      List<String> result = IndexedTraversals.imodify(evens, (i, s) -> s.toUpperCase(), source);

      assertThat(result).containsExactly("A", "b", "C", "d", "E");
    }

    @Test
    @DisplayName("should filter first N elements")
    void filterFirstN() {
      IndexedTraversal<Integer, List<String>, String> ilist = IndexedTraversals.forList();
      IndexedTraversal<Integer, List<String>, String> firstThree = ilist.filterIndex(i -> i < 3);
      List<String> source = List.of("a", "b", "c", "d", "e");

      List<String> result = IndexedTraversals.imodify(firstThree, (i, s) -> s + "!", source);

      assertThat(result).containsExactly("a!", "b!", "c!", "d", "e");
    }

    @Test
    @DisplayName("should filter by specific indices set")
    void filterSpecificIndices() {
      IndexedTraversal<Integer, List<String>, String> ilist = IndexedTraversals.forList();
      var indices = java.util.Set.of(0, 2, 4);
      IndexedTraversal<Integer, List<String>, String> specific =
          ilist.filterIndex(indices::contains);
      List<String> source = List.of("a", "b", "c", "d", "e");

      List<Pair<Integer, String>> indexed = IndexedTraversals.toIndexedList(specific, source);

      // Only focused indices are returned (0, 2, 4)
      assertThat(indexed).hasSize(3);
      assertThat(indexed.get(0).first()).isEqualTo(0);
      assertThat(indexed.get(0).second()).isEqualTo("a");
      assertThat(indexed.get(1).first()).isEqualTo(2);
      assertThat(indexed.get(1).second()).isEqualTo("c");
      assertThat(indexed.get(2).first()).isEqualTo(4);
      assertThat(indexed.get(2).second()).isEqualTo("e");
    }
  }

  @Nested
  @DisplayName("filtered() - Value-based filtering with index preservation")
  class FilteredTests {

    @Test
    @DisplayName("should filter by value while preserving indices")
    void filterByValue() {
      IndexedTraversal<Integer, List<User>, User> iusers = IndexedTraversals.forList();
      IndexedTraversal<Integer, List<User>, User> active = iusers.filtered(User::active);
      List<User> source =
          List.of(
              new User("Alice", 30, true), new User("Bob", 25, false), new User("Carol", 35, true));

      List<Pair<Integer, User>> indexed = IndexedTraversals.toIndexedList(active, source);

      // Only active users returned with their original indices preserved
      assertThat(indexed).hasSize(2);
      assertThat(indexed.get(0).first()).isEqualTo(0);
      assertThat(indexed.get(0).second().name()).isEqualTo("Alice");
      assertThat(indexed.get(1).first()).isEqualTo(2);
      assertThat(indexed.get(1).second().name()).isEqualTo("Carol");
    }

    @Test
    @DisplayName("should modify only matching elements")
    void modifyFilteredElements() {
      IndexedTraversal<Integer, List<User>, User> iusers = IndexedTraversals.forList();
      IndexedTraversal<Integer, List<User>, User> active = iusers.filtered(User::active);
      List<User> source =
          List.of(
              new User("Alice", 30, true), new User("Bob", 25, false), new User("Carol", 35, true));

      List<User> result =
          IndexedTraversals.imodify(
              active, (i, u) -> u.withName(u.name() + " [" + i + "]"), source);

      assertThat(result.get(0).name()).isEqualTo("Alice [0]");
      assertThat(result.get(1).name()).isEqualTo("Bob"); // Unchanged
      assertThat(result.get(2).name()).isEqualTo("Carol [2]");
    }
  }

  @Nested
  @DisplayName("filteredWithIndex() - Combined index and value filtering")
  class FilteredWithIndexTests {

    @Test
    @DisplayName("should filter by both index and value")
    void filterByIndexAndValue() {
      IndexedTraversal<Integer, List<Integer>, Integer> ilist = IndexedTraversals.forList();
      // Focus on elements where index is even AND value > 10
      IndexedTraversal<Integer, List<Integer>, Integer> combined =
          ilist.filteredWithIndex((i, v) -> i % 2 == 0 && v > 10);
      List<Integer> source = List.of(5, 20, 15, 30, 25);

      List<Integer> result = IndexedTraversals.imodify(combined, (i, v) -> v * 2, source);

      assertThat(result)
          .containsExactly(
              5, // index 0 even, but 5 <= 10, not modified
              20, // index 1 odd, not modified
              30, // index 2 even AND 15 > 10, modified to 30
              30, // index 3 odd, not modified
              50 // index 4 even AND 25 > 10, modified to 50
              );
    }
  }

  @Nested
  @DisplayName("andThen() - Composition with regular optics")
  class AndThenTests {

    @Test
    @DisplayName("should compose with Traversal preserving index")
    void composeWithTraversal() {
      IndexedTraversal<Integer, List<User>, User> iusers = IndexedTraversals.forList();
      Lens<User, String> nameLens = Lens.of(User::name, User::withName);
      Traversal<User, String> nameTraversal = nameLens.asTraversal();

      IndexedTraversal<Integer, List<User>, String> userNames = iusers.andThen(nameTraversal);

      List<User> source = List.of(new User("Alice", 30, true), new User("Bob", 25, false));

      List<User> result =
          IndexedTraversals.imodify(userNames, (i, name) -> name + " #" + i, source);

      assertThat(result.get(0).name()).isEqualTo("Alice #0");
      assertThat(result.get(1).name()).isEqualTo("Bob #1");
    }

    @Test
    @DisplayName("should preserve outer index through multiple compositions")
    void multipleCompositions() {
      IndexedTraversal<Integer, List<Team>, Team> iteams = IndexedTraversals.forList();
      Lens<Team, List<User>> membersLens = Lens.of(Team::members, (t, m) -> new Team(t.name(), m));
      Traversal<List<User>, User> usersTraversal = Traversals.forList();
      Lens<User, String> nameLens = Lens.of(User::name, User::withName);

      // Compose: Teams (indexed) -> members (list) -> users -> names
      IndexedTraversal<Integer, List<Team>, String> teamUserNames =
          iteams
              .andThen(membersLens.asTraversal())
              .andThen(usersTraversal)
              .andThen(nameLens.asTraversal());

      List<Team> source =
          List.of(
              new Team("Alpha", List.of(new User("Alice", 30, true), new User("Bob", 25, false))),
              new Team("Beta", List.of(new User("Carol", 35, true))));

      // All user names get the team index (outer index)
      List<Team> result =
          IndexedTraversals.imodify(
              teamUserNames, (teamIndex, name) -> name + "@Team" + teamIndex, source);

      assertThat(result.get(0).members().get(0).name()).isEqualTo("Alice@Team0");
      assertThat(result.get(0).members().get(1).name()).isEqualTo("Bob@Team0");
      assertThat(result.get(1).members().get(0).name()).isEqualTo("Carol@Team1");
    }
  }

  @Nested
  @DisplayName("iandThen() - Composition with indexed optics (paired indices)")
  class IAndThenTests {

    @Test
    @DisplayName("should pair indices when composing indexed optics")
    void pairIndices() {
      IndexedTraversal<Integer, List<Map<String, Integer>>, Map<String, Integer>> ilist =
          IndexedTraversals.forList();
      IndexedTraversal<String, Map<String, Integer>, Integer> imap = IndexedTraversals.forMap();

      IndexedTraversal<Pair<Integer, String>, List<Map<String, Integer>>, Integer> composed =
          ilist.iandThen(imap);

      List<Map<String, Integer>> source = List.of(Map.of("a", 1, "b", 2), Map.of("x", 10, "y", 20));

      List<Pair<Pair<Integer, String>, Integer>> indexed =
          IndexedTraversals.toIndexedList(composed, source);

      assertThat(indexed).hasSize(4);
      // Check that we have paired indices
      assertThat(
              indexed.stream()
                  .anyMatch(t -> t.first().first().equals(0) && t.first().second().equals("a")))
          .isTrue();
      assertThat(
              indexed.stream()
                  .anyMatch(t -> t.first().first().equals(1) && t.first().second().equals("x")))
          .isTrue();
    }

    @Test
    @DisplayName("should allow modification using paired indices")
    void modifyWithPairedIndices() {
      IndexedTraversal<Integer, List<Map<String, String>>, Map<String, String>> ilist =
          IndexedTraversals.forList();
      IndexedTraversal<String, Map<String, String>, String> imap = IndexedTraversals.forMap();

      IndexedTraversal<Pair<Integer, String>, List<Map<String, String>>, String> composed =
          ilist.iandThen(imap);

      List<Map<String, String>> source =
          List.of(new HashMap<>(Map.of("name", "Alice")), new HashMap<>(Map.of("name", "Bob")));

      List<Map<String, String>> result =
          IndexedTraversals.imodify(
              composed,
              (indices, value) -> value + "[" + indices.first() + ":" + indices.second() + "]",
              source);

      assertThat(result.get(0).get("name")).isEqualTo("Alice[0:name]");
      assertThat(result.get(1).get("name")).isEqualTo("Bob[1:name]");
    }
  }

  @Nested
  @DisplayName("asTraversal() - Conversion to regular traversal")
  class AsTraversalTests {

    @Test
    @DisplayName("should convert to regular traversal ignoring indices")
    void convertToTraversal() {
      IndexedTraversal<Integer, List<String>, String> ilist = IndexedTraversals.forList();
      Traversal<List<String>, String> traversal = ilist.asTraversal();

      List<String> source = List.of("a", "b", "c");
      List<String> result = Traversals.modify(traversal, String::toUpperCase, source);

      assertThat(result).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("should work with Traversals utility methods")
    void useWithTraversalsUtility() {
      IndexedTraversal<Integer, List<Integer>, Integer> ilist = IndexedTraversals.forList();
      Traversal<List<Integer>, Integer> traversal = ilist.asTraversal();

      List<Integer> source = List.of(1, 2, 3, 4, 5);
      List<Integer> values = Traversals.getAll(traversal, source);

      assertThat(values).containsExactly(1, 2, 3, 4, 5);
    }
  }
}
