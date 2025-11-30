// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.cookbook;

import java.util.List;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.Traversals;

/**
 * Cookbook recipes for bulk operations on collections using Traversals.
 *
 * <p>Problem: Updating all elements in a collection or nested collections.
 *
 * <p>Solution: Use Traversals to focus on multiple elements and modify them together.
 */
public class CollectionRecipes {

  // --- Domain Model ---
  @GenerateLenses
  public record Team(String name, List<Player> players) {}

  @GenerateLenses
  public record Player(String name, int score, boolean active) {}

  @GenerateLenses
  public record League(String name, List<Team> teams) {}

  public static void main(String[] args) {
    System.out.println("=== Collection Recipes ===\n");

    recipeModifyAllElements();
    recipeGetAllValues();
    recipeNestedCollections();
    recipeConditionalCollectionUpdate();
  }

  /**
   * Recipe: Modify all elements in a list.
   *
   * <p>Pattern: {@code Traversals.modify(traversal, function, source)}
   */
  private static void recipeModifyAllElements() {
    System.out.println("--- Recipe: Modify All Elements ---");

    List<Player> players =
        List.of(
            new Player("Alice", 100, true),
            new Player("Bob", 85, true),
            new Player("Charlie", 92, false));

    // Traversal for list elements
    Traversal<List<Player>, Player> allPlayers = Traversals.forList();

    // Lens to player's score
    Lens<Player, Integer> scoreLens =
        Lens.of(Player::score, (p, s) -> new Player(p.name(), s, p.active()));

    // Compose to get all scores
    Traversal<List<Player>, Integer> allScores = allPlayers.andThen(scoreLens);

    // Add 10 bonus points to all players
    List<Player> updated = Traversals.modify(allScores, score -> score + 10, players);

    System.out.println("Original: " + players);
    System.out.println("After +10 bonus: " + updated);
    System.out.println();
  }

  /**
   * Recipe: Extract all values from a collection.
   *
   * <p>Pattern: {@code Traversals.getAll(traversal, source)}
   */
  private static void recipeGetAllValues() {
    System.out.println("--- Recipe: Extract All Values ---");

    List<Player> players =
        List.of(
            new Player("Alice", 100, true),
            new Player("Bob", 85, true),
            new Player("Charlie", 92, false));

    Traversal<List<Player>, Player> allPlayers = Traversals.forList();
    Lens<Player, String> nameLens =
        Lens.of(Player::name, (p, n) -> new Player(n, p.score(), p.active()));
    Lens<Player, Integer> scoreLens =
        Lens.of(Player::score, (p, s) -> new Player(p.name(), s, p.active()));

    // Get all names
    List<String> names = Traversals.getAll(allPlayers.andThen(nameLens), players);
    System.out.println("All names: " + names);

    // Get all scores
    List<Integer> scores = Traversals.getAll(allPlayers.andThen(scoreLens), players);
    System.out.println("All scores: " + scores);

    // Calculate average
    double average = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
    System.out.println("Average score: " + average);
    System.out.println();
  }

  /**
   * Recipe: Update nested collections.
   *
   * <p>Pattern: Compose traversals to reach elements in nested lists.
   */
  private static void recipeNestedCollections() {
    System.out.println("--- Recipe: Nested Collections ---");

    Team teamA =
        new Team("Team A", List.of(new Player("Alice", 100, true), new Player("Bob", 85, true)));
    Team teamB =
        new Team(
            "Team B", List.of(new Player("Charlie", 92, true), new Player("Diana", 88, false)));

    League league = new League("Premier League", List.of(teamA, teamB));

    // Build path: League -> teams -> players -> score
    Lens<League, List<Team>> teamsLens =
        Lens.of(League::teams, (l, teams) -> new League(l.name(), teams));
    Lens<Team, List<Player>> playersLens =
        Lens.of(Team::players, (t, players) -> new Team(t.name(), players));
    Lens<Player, Integer> scoreLens =
        Lens.of(Player::score, (p, s) -> new Player(p.name(), s, p.active()));

    // Compose lenses and traversals directly
    Traversal<League, Integer> allScores =
        teamsLens
            .andThen(Traversals.<Team>forList())
            .andThen(playersLens)
            .andThen(Traversals.<Player>forList())
            .andThen(scoreLens);

    // Get all scores across all teams
    List<Integer> scores = Traversals.getAll(allScores, league);
    System.out.println("All scores in league: " + scores);

    // Double all scores
    League updated = Traversals.modify(allScores, s -> s * 2, league);
    List<Integer> newScores = Traversals.getAll(allScores, updated);
    System.out.println("After doubling: " + newScores);
    System.out.println();
  }

  /**
   * Recipe: Conditional update within collections.
   *
   * <p>Pattern: Use filtered traversals or modify with conditional logic.
   */
  private static void recipeConditionalCollectionUpdate() {
    System.out.println("--- Recipe: Conditional Collection Update ---");

    List<Player> players =
        List.of(
            new Player("Alice", 100, true),
            new Player("Bob", 85, false),
            new Player("Charlie", 92, true),
            new Player("Diana", 78, false));

    Traversal<List<Player>, Player> allPlayers = Traversals.forList();
    Lens<Player, Integer> scoreLens =
        Lens.of(Player::score, (p, s) -> new Player(p.name(), s, p.active()));

    // Only boost active players
    Traversal<List<Player>, Integer> allScores = allPlayers.andThen(scoreLens);

    // Using modify with conditional logic
    List<Player> updated =
        Traversals.modify(
            allPlayers,
            player -> {
              if (player.active()) {
                return new Player(player.name(), player.score() + 20, player.active());
              }
              return player;
            },
            players);

    System.out.println("Original players:");
    players.forEach(p -> System.out.println("  " + p));
    System.out.println();
    System.out.println("After +20 bonus for active players:");
    updated.forEach(p -> System.out.println("  " + p));
    System.out.println();
  }
}
