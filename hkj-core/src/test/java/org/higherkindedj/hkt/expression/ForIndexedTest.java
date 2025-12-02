// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link ForIndexed} class.
 *
 * <p>These tests verify that indexed traversal-based comprehensions work correctly for
 * position-aware bulk operations.
 */
@DisplayName("ForIndexed Tests")
class ForIndexedTest {

  // --- Test Data Classes ---

  record Player(String name, int score) {}

  record Item(String label, int quantity) {}

  // --- Common Test Fixtures ---

  private IdMonad idMonad;
  private IndexedTraversal<Integer, List<Player>, Player> playersTraversal;
  private Lens<Player, Integer> scoreLens;
  private Lens<Player, String> nameLens;

  @BeforeEach
  void setUp() {
    idMonad = IdMonad.instance();
    playersTraversal = IndexedTraversals.forList();
    scoreLens = Lens.of(Player::score, (p, s) -> new Player(p.name(), s));
    nameLens = Lens.of(Player::name, (p, n) -> new Player(n, p.score()));
  }

  // --- Basic Operations ---

  @Nested
  @DisplayName("Basic Operations")
  class BasicOperations {

    @Test
    @DisplayName("overIndexed() should create an indexed traversal builder")
    void overIndexedCreatesBuilder() {
      List<Player> players = List.of(new Player("Alice", 100));

      ForIndexed.IndexedSteps<IdKind.Witness, Integer, List<Player>, Player> steps =
          ForIndexed.overIndexed(playersTraversal, players, idMonad);

      assertThat(steps).isNotNull();
    }

    @Test
    @DisplayName("overIndexed() should throw on null traversal")
    void overIndexedThrowsOnNullTraversal() {
      assertThatThrownBy(() -> ForIndexed.overIndexed(null, List.of(), idMonad))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("traversal");
    }

    @Test
    @DisplayName("overIndexed() should throw on null source")
    void overIndexedThrowsOnNullSource() {
      assertThatThrownBy(() -> ForIndexed.overIndexed(playersTraversal, null, idMonad))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("source");
    }

    @Test
    @DisplayName("overIndexed() should throw on null applicative")
    void overIndexedThrowsOnNullApplicative() {
      assertThatThrownBy(() -> ForIndexed.overIndexed(playersTraversal, List.of(), null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("applicative");
    }
  }

  // --- run() Operations ---

  @Nested
  @DisplayName("run() - Execute Indexed Traversal")
  class RunOperations {

    @Test
    @DisplayName("should return unchanged structure when no operations applied")
    void runWithNoOperations() {
      List<Player> players = List.of(new Player("Alice", 100), new Player("Bob", 200));

      Kind<IdKind.Witness, List<Player>> result =
          ForIndexed.overIndexed(playersTraversal, players, idMonad).run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      assertThat(resultList).containsExactly(new Player("Alice", 100), new Player("Bob", 200));
    }

    @Test
    @DisplayName("should handle empty list")
    void runOnEmptyList() {
      List<Player> players = List.of();

      Kind<IdKind.Witness, List<Player>> result =
          ForIndexed.overIndexed(playersTraversal, players, idMonad).run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      assertThat(resultList).isEmpty();
    }
  }

  // --- modify() Operations ---

  @Nested
  @DisplayName("modify() - Index-Aware Field Modification")
  class ModifyOperations {

    @Test
    @DisplayName("should modify all elements with index-aware function")
    void modifyWithIndexAwareness() {
      List<Player> players = List.of(new Player("Alice", 100), new Player("Bob", 100));

      Kind<IdKind.Witness, List<Player>> result =
          ForIndexed.overIndexed(playersTraversal, players, idMonad)
              .modify(scoreLens, (index, score) -> score + (index * 50))
              .run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      // Alice at index 0: 100 + 0*50 = 100
      // Bob at index 1: 100 + 1*50 = 150
      assertThat(resultList).extracting(Player::score).containsExactly(100, 150);
    }

    @Test
    @DisplayName("should chain multiple modify operations with index")
    void chainModifyOperations() {
      List<Player> players = List.of(new Player("alice", 100), new Player("bob", 100));

      Kind<IdKind.Witness, List<Player>> result =
          ForIndexed.overIndexed(playersTraversal, players, idMonad)
              .modify(scoreLens, (i, score) -> score * (i + 1))
              .modify(nameLens, (i, name) -> name.toUpperCase() + "#" + i)
              .run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      assertThat(resultList).containsExactly(new Player("ALICE#0", 100), new Player("BOB#1", 200));
    }

    @Test
    @DisplayName("modify() should throw on null lens")
    void modifyThrowsOnNullLens() {
      List<Player> players = List.of(new Player("Alice", 100));

      assertThatThrownBy(
              () ->
                  ForIndexed.overIndexed(playersTraversal, players, idMonad)
                      .modify(null, (i, x) -> x))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }

    @Test
    @DisplayName("modify() should throw on null modifier")
    void modifyThrowsOnNullModifier() {
      List<Player> players = List.of(new Player("Alice", 100));

      assertThatThrownBy(
              () ->
                  ForIndexed.overIndexed(playersTraversal, players, idMonad)
                      .modify(scoreLens, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("modifier");
    }
  }

  // --- set() Operations ---

  @Nested
  @DisplayName("set() - Index-Based Field Setting")
  class SetOperations {

    @Test
    @DisplayName("should set field based on index")
    void setBasedOnIndex() {
      List<Player> players = List.of(new Player("Alice", 100), new Player("Bob", 200));

      Kind<IdKind.Witness, List<Player>> result =
          ForIndexed.overIndexed(playersTraversal, players, idMonad)
              .set(scoreLens, index -> (index + 1) * 1000)
              .run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      // Index 0 -> 1000, Index 1 -> 2000
      assertThat(resultList).extracting(Player::score).containsExactly(1000, 2000);
    }

    @Test
    @DisplayName("set() should throw on null lens")
    void setThrowsOnNullLens() {
      List<Player> players = List.of(new Player("Alice", 100));

      assertThatThrownBy(
              () -> ForIndexed.overIndexed(playersTraversal, players, idMonad).set(null, i -> 0))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }

    @Test
    @DisplayName("set() should throw on null valueFunction")
    void setThrowsOnNullValueFunction() {
      List<Player> players = List.of(new Player("Alice", 100));

      assertThatThrownBy(
              () -> ForIndexed.overIndexed(playersTraversal, players, idMonad).set(scoreLens, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("valueFunction");
    }
  }

  // --- filterIndex() Operations ---

  @Nested
  @DisplayName("filterIndex() - Index-Based Filtering")
  class FilterIndexOperations {

    @Test
    @DisplayName("should only modify elements at matching indices")
    void filterIndexThenModify() {
      List<Player> players =
          List.of(
              new Player("Alice", 100),
              new Player("Bob", 100),
              new Player("Charlie", 100),
              new Player("Diana", 100));

      Kind<IdKind.Witness, List<Player>> result =
          ForIndexed.overIndexed(playersTraversal, players, idMonad)
              .filterIndex(i -> i % 2 == 0) // Only even indices (0, 2)
              .modify(scoreLens, (i, score) -> score * 2)
              .run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      // Index 0 (Alice): doubled -> 200
      // Index 1 (Bob): unchanged -> 100
      // Index 2 (Charlie): doubled -> 200
      // Index 3 (Diana): unchanged -> 100
      assertThat(resultList).extracting(Player::score).containsExactly(200, 100, 200, 100);
    }

    @Test
    @DisplayName("should filter first N elements")
    void filterFirstN() {
      List<Player> players = List.of(new Player("A", 10), new Player("B", 20), new Player("C", 30));

      Kind<IdKind.Witness, List<Player>> result =
          ForIndexed.overIndexed(playersTraversal, players, idMonad)
              .filterIndex(i -> i < 2) // First 2 elements
              .modify(scoreLens, (i, s) -> s + 100)
              .run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      assertThat(resultList).extracting(Player::score).containsExactly(110, 120, 30);
    }

    @Test
    @DisplayName("filterIndex() should throw on null predicate")
    void filterIndexThrowsOnNullPredicate() {
      List<Player> players = List.of(new Player("Alice", 100));

      assertThatThrownBy(
              () -> ForIndexed.overIndexed(playersTraversal, players, idMonad).filterIndex(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("predicate");
    }
  }

  // --- filter() Operations ---

  @Nested
  @DisplayName("filter() - Index and Value Filtering")
  class FilterOperations {

    @Test
    @DisplayName("should filter based on both index and value")
    void filterByIndexAndValue() {
      List<Player> players =
          List.of(
              new Player("Alice", 50),
              new Player("Bob", 150),
              new Player("Charlie", 100),
              new Player("Diana", 200));

      Kind<IdKind.Witness, List<Player>> result =
          ForIndexed.overIndexed(playersTraversal, players, idMonad)
              .filter((index, player) -> index < 3 && player.score() >= 100)
              .modify(scoreLens, (i, s) -> s + 1000)
              .run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      // Index 0, Alice (50): doesn't match score condition -> unchanged
      // Index 1, Bob (150): matches both -> 1150
      // Index 2, Charlie (100): matches both -> 1100
      // Index 3, Diana (200): doesn't match index condition -> unchanged
      assertThat(resultList).extracting(Player::score).containsExactly(50, 1150, 1100, 200);
    }

    @Test
    @DisplayName("filter() should throw on null predicate")
    void filterThrowsOnNullPredicate() {
      List<Player> players = List.of(new Player("Alice", 100));

      assertThatThrownBy(
              () -> ForIndexed.overIndexed(playersTraversal, players, idMonad).filter(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("predicate");
    }

    @Test
    @DisplayName("should chain filterIndex and filter")
    void chainFilters() {
      List<Player> players = List.of(new Player("A", 10), new Player("B", 20), new Player("C", 30));

      Kind<IdKind.Witness, List<Player>> result =
          ForIndexed.overIndexed(playersTraversal, players, idMonad)
              .filterIndex(i -> i > 0) // Exclude first element
              .filter((i, p) -> p.score() >= 25) // Score >= 25
              .modify(scoreLens, (i, s) -> s * 10)
              .run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      // Index 0 (A, 10): excluded by filterIndex
      // Index 1 (B, 20): excluded by filter (20 < 25)
      // Index 2 (C, 30): matches both -> 300
      assertThat(resultList).extracting(Player::score).containsExactly(10, 20, 300);
    }
  }

  // --- toIndexedList() Operations ---

  @Nested
  @DisplayName("toIndexedList() - Collect Elements with Indices")
  class ToIndexedListOperations {

    @Test
    @DisplayName("should collect all elements with their indices")
    void collectAllWithIndices() {
      List<Player> players = List.of(new Player("Alice", 100), new Player("Bob", 200));

      Kind<IdKind.Witness, List<Pair<Integer, Player>>> result =
          ForIndexed.overIndexed(playersTraversal, players, idMonad).toIndexedList();

      List<Pair<Integer, Player>> collected = IdKindHelper.ID.unwrap(result);
      assertThat(collected).hasSize(2);
      assertThat(collected.get(0).first()).isEqualTo(0);
      assertThat(collected.get(0).second()).isEqualTo(new Player("Alice", 100));
      assertThat(collected.get(1).first()).isEqualTo(1);
      assertThat(collected.get(1).second()).isEqualTo(new Player("Bob", 200));
    }

    @Test
    @DisplayName("should return empty list for empty source")
    void collectFromEmptySource() {
      List<Player> players = List.of();

      Kind<IdKind.Witness, List<Pair<Integer, Player>>> result =
          ForIndexed.overIndexed(playersTraversal, players, idMonad).toIndexedList();

      List<Pair<Integer, Player>> collected = IdKindHelper.ID.unwrap(result);
      assertThat(collected).isEmpty();
    }
  }

  // --- Complex Scenarios ---

  @Nested
  @DisplayName("Complex Scenarios")
  class ComplexScenarios {

    @Test
    @DisplayName("should apply position-based ranking bonus")
    void positionBasedRanking() {
      List<Player> players =
          List.of(
              new Player("Gold", 1000),
              new Player("Silver", 800),
              new Player("Bronze", 600),
              new Player("Fourth", 500));

      Kind<IdKind.Witness, List<Player>> result =
          ForIndexed.overIndexed(playersTraversal, players, idMonad)
              .filterIndex(i -> i < 3) // Top 3 only
              .modify(scoreLens, (rank, score) -> score + (3 - rank) * 100) // Bonus by rank
              .run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      // Rank 0 (Gold): 1000 + 300 = 1300
      // Rank 1 (Silver): 800 + 200 = 1000
      // Rank 2 (Bronze): 600 + 100 = 700
      // Rank 3 (Fourth): unchanged = 500
      assertThat(resultList).extracting(Player::score).containsExactly(1300, 1000, 700, 500);
    }

    @Test
    @DisplayName("should number elements based on position")
    void numberElementsByPosition() {
      List<Player> players = List.of(new Player("Alice", 100), new Player("Bob", 200));

      Kind<IdKind.Witness, List<Player>> result =
          ForIndexed.overIndexed(playersTraversal, players, idMonad)
              .modify(nameLens, (i, name) -> (i + 1) + ". " + name)
              .run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      assertThat(resultList).extracting(Player::name).containsExactly("1. Alice", "2. Bob");
    }

    @Test
    @DisplayName("should support multiple chained operations with filtering")
    void multipleChainedOperationsWithFiltering() {
      List<Player> players =
          List.of(
              new Player("a", 10),
              new Player("b", 20),
              new Player("c", 30),
              new Player("d", 40),
              new Player("e", 50));

      Kind<IdKind.Witness, List<Player>> result =
          ForIndexed.overIndexed(playersTraversal, players, idMonad)
              .filterIndex(i -> i >= 1 && i <= 3) // Middle three
              .filter((i, p) -> p.score() >= 25) // Score >= 25
              .modify(nameLens, (i, n) -> n.toUpperCase())
              .modify(scoreLens, (i, s) -> s + i * 100)
              .run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      // Index 0 (a, 10): excluded by filterIndex
      // Index 1 (b, 20): excluded by filter (20 < 25)
      // Index 2 (c, 30): matches -> "C", 30 + 200 = 230
      // Index 3 (d, 40): matches -> "D", 40 + 300 = 340
      // Index 4 (e, 50): excluded by filterIndex
      assertThat(resultList)
          .containsExactly(
              new Player("a", 10),
              new Player("b", 20),
              new Player("C", 230),
              new Player("D", 340),
              new Player("e", 50));
    }
  }
}
