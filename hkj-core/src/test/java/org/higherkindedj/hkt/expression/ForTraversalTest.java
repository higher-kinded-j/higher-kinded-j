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
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link ForTraversal} class.
 *
 * <p>These tests verify that traversal-based comprehensions work correctly for bulk operations over
 * structures.
 */
@DisplayName("ForTraversal Tests")
class ForTraversalTest {

  // --- Test Data Classes ---

  record Player(String name, int score) {}

  record Team(String name, List<Player> players) {}

  // --- Common Test Fixtures ---

  private IdMonad idMonad;
  private Traversal<List<Player>, Player> playersTraversal;
  private Lens<Player, Integer> scoreLens;
  private Lens<Player, String> nameLens;

  @BeforeEach
  void setUp() {
    idMonad = IdMonad.instance();
    playersTraversal = Traversals.forList();
    scoreLens = Lens.of(Player::score, (p, s) -> new Player(p.name(), s));
    nameLens = Lens.of(Player::name, (p, n) -> new Player(n, p.score()));
  }

  // --- Basic Operations ---

  @Nested
  @DisplayName("Basic Operations")
  class BasicOperations {

    @Test
    @DisplayName("over() should create a traversal builder")
    void overCreatesBuilder() {
      List<Player> players = List.of(new Player("Alice", 100));

      ForTraversal.TraversalSteps<IdKind.Witness, List<Player>, Player> steps =
          ForTraversal.over(playersTraversal, players, idMonad);

      assertThat(steps).isNotNull();
    }

    @Test
    @DisplayName("over() should throw on null traversal")
    void overThrowsOnNullTraversal() {
      assertThatThrownBy(() -> ForTraversal.over(null, List.of(), idMonad))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("traversal");
    }

    @Test
    @DisplayName("over() should throw on null source")
    void overThrowsOnNullSource() {
      assertThatThrownBy(() -> ForTraversal.over(playersTraversal, null, idMonad))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("source");
    }

    @Test
    @DisplayName("over() should throw on null applicative")
    void overThrowsOnNullApplicative() {
      assertThatThrownBy(() -> ForTraversal.over(playersTraversal, List.of(), null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("applicative");
    }
  }

  // --- run() Operations ---

  @Nested
  @DisplayName("run() - Execute Traversal")
  class RunOperations {

    @Test
    @DisplayName("should return unchanged structure when no operations applied")
    void runWithNoOperations() {
      List<Player> players = List.of(new Player("Alice", 100), new Player("Bob", 200));

      Kind<IdKind.Witness, List<Player>> result =
          ForTraversal.over(playersTraversal, players, idMonad).run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      assertThat(resultList).containsExactly(new Player("Alice", 100), new Player("Bob", 200));
    }

    @Test
    @DisplayName("should handle empty list")
    void runOnEmptyList() {
      List<Player> players = List.of();

      Kind<IdKind.Witness, List<Player>> result =
          ForTraversal.over(playersTraversal, players, idMonad).run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      assertThat(resultList).isEmpty();
    }
  }

  // --- modify() Operations ---

  @Nested
  @DisplayName("modify() - Lens-Based Field Modification")
  class ModifyOperations {

    @Test
    @DisplayName("should modify all elements via lens")
    void modifyAllElements() {
      List<Player> players = List.of(new Player("Alice", 100), new Player("Bob", 200));

      Kind<IdKind.Witness, List<Player>> result =
          ForTraversal.over(playersTraversal, players, idMonad)
              .modify(scoreLens, score -> score + 50)
              .run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      assertThat(resultList).extracting(Player::score).containsExactly(150, 250);
      assertThat(resultList).extracting(Player::name).containsExactly("Alice", "Bob");
    }

    @Test
    @DisplayName("should chain multiple modify operations")
    void chainModifyOperations() {
      List<Player> players = List.of(new Player("alice", 100));

      Kind<IdKind.Witness, List<Player>> result =
          ForTraversal.over(playersTraversal, players, idMonad)
              .modify(scoreLens, score -> score * 2)
              .modify(nameLens, String::toUpperCase)
              .run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      assertThat(resultList).containsExactly(new Player("ALICE", 200));
    }

    @Test
    @DisplayName("modify() should throw on null lens")
    void modifyThrowsOnNullLens() {
      List<Player> players = List.of(new Player("Alice", 100));

      assertThatThrownBy(
              () -> ForTraversal.over(playersTraversal, players, idMonad).modify(null, x -> x))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }

    @Test
    @DisplayName("modify() should throw on null modifier")
    void modifyThrowsOnNullModifier() {
      List<Player> players = List.of(new Player("Alice", 100));

      assertThatThrownBy(
              () -> ForTraversal.over(playersTraversal, players, idMonad).modify(scoreLens, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("modifier");
    }
  }

  // --- set() Operations ---

  @Nested
  @DisplayName("set() - Lens-Based Field Setting")
  class SetOperations {

    @Test
    @DisplayName("should set field on all elements")
    void setAllElements() {
      List<Player> players = List.of(new Player("Alice", 100), new Player("Bob", 200));

      Kind<IdKind.Witness, List<Player>> result =
          ForTraversal.over(playersTraversal, players, idMonad).set(scoreLens, 0).run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      assertThat(resultList).extracting(Player::score).containsExactly(0, 0);
    }

    @Test
    @DisplayName("set() should throw on null lens")
    void setThrowsOnNullLens() {
      List<Player> players = List.of(new Player("Alice", 100));

      assertThatThrownBy(() -> ForTraversal.over(playersTraversal, players, idMonad).set(null, 0))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }
  }

  // --- filter() Operations ---

  @Nested
  @DisplayName("filter() - Element Filtering")
  class FilterOperations {

    @Test
    @DisplayName("should only modify elements matching filter")
    void filterThenModify() {
      List<Player> players =
          List.of(new Player("Alice", 100), new Player("Bob", 200), new Player("Charlie", 150));

      Kind<IdKind.Witness, List<Player>> result =
          ForTraversal.over(playersTraversal, players, idMonad)
              .filter(p -> p.score() >= 150)
              .modify(scoreLens, score -> score * 2)
              .run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      // Alice unchanged (filtered out), Bob and Charlie doubled
      assertThat(resultList).extracting(Player::score).containsExactly(100, 400, 300);
    }

    @Test
    @DisplayName("filter() should throw on null predicate")
    void filterThrowsOnNullPredicate() {
      List<Player> players = List.of(new Player("Alice", 100));

      assertThatThrownBy(() -> ForTraversal.over(playersTraversal, players, idMonad).filter(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("predicate");
    }

    @Test
    @DisplayName("should chain filter with set")
    void filterThenSet() {
      List<Player> players = List.of(new Player("Alice", 100), new Player("Bob", 200));

      Kind<IdKind.Witness, List<Player>> result =
          ForTraversal.over(playersTraversal, players, idMonad)
              .filter(p -> p.name().equals("Bob"))
              .set(scoreLens, 999)
              .run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);
      assertThat(resultList).extracting(Player::score).containsExactly(100, 999);
    }
  }

  // --- toList() Operations ---

  @Nested
  @DisplayName("toList() - Collect Elements")
  class ToListOperations {

    @Test
    @DisplayName("should collect all focused elements")
    void collectAllElements() {
      List<Player> players = List.of(new Player("Alice", 100), new Player("Bob", 200));

      Kind<IdKind.Witness, List<Player>> result =
          ForTraversal.over(playersTraversal, players, idMonad).toList();

      List<Player> collected = IdKindHelper.ID.unwrap(result);
      assertThat(collected).containsExactly(new Player("Alice", 100), new Player("Bob", 200));
    }

    @Test
    @DisplayName("should return empty list for empty source")
    void collectFromEmptySource() {
      List<Player> players = List.of();

      Kind<IdKind.Witness, List<Player>> result =
          ForTraversal.over(playersTraversal, players, idMonad).toList();

      List<Player> collected = IdKindHelper.ID.unwrap(result);
      assertThat(collected).isEmpty();
    }
  }

  // --- Complex Scenarios ---

  @Nested
  @DisplayName("Complex Scenarios")
  class ComplexScenarios {

    @Test
    @DisplayName("should support multiple chained operations")
    void multipleChainedOperations() {
      List<Player> players =
          List.of(new Player("alice", 50), new Player("bob", 150), new Player("charlie", 100));

      Kind<IdKind.Witness, List<Player>> result =
          ForTraversal.over(playersTraversal, players, idMonad)
              .filter(p -> p.score() >= 100)
              .modify(nameLens, String::toUpperCase)
              .modify(scoreLens, s -> s + 10)
              .run();

      List<Player> resultList = IdKindHelper.ID.unwrap(result);

      // alice unchanged (score < 100)
      // bob: name uppercased, score +10
      // charlie: name uppercased, score +10
      assertThat(resultList)
          .containsExactly(
              new Player("alice", 50), new Player("BOB", 160), new Player("CHARLIE", 110));
    }
  }
}
