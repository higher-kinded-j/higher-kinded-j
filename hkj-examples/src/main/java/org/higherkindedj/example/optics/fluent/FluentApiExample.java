// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent;

import module java.base;
import module org.higherkindedj.core;

import org.higherkindedj.example.optics.fluent.generated.PlayerLenses;
import org.higherkindedj.example.optics.fluent.generated.TeamLenses;
import org.higherkindedj.example.optics.fluent.generated.TeamTraversals;
import org.higherkindedj.example.optics.fluent.model.Player;
import org.higherkindedj.example.optics.fluent.model.Team;

/**
 * Demonstrates the simple fluent API for optics.
 *
 * <p>This example shows two styles of using the fluent API:
 *
 * <ul>
 *   <li>Static methods for concise, direct operations
 *   <li>Fluent builders for explicit, method-chained workflows
 * </ul>
 */
public final class FluentApiExample {

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    Team team =
        new Team(
            "Wildcats",
            List.of(
                new Player("Alice", 25, 100),
                new Player("Bob", 30, 95),
                new Player("Charlie", 22, 110)));

    System.out.println("=== Fluent API Examples ===\n");

    // ========== Static Methods (Concise) ==========
    System.out.println("--- Static Methods ---");

    // Get team name
    String teamName = OpticOps.get(team, TeamLenses.name());
    System.out.println("Team name: " + teamName);

    // Get all player names
    Traversal<Team, String> playerNames =
        TeamTraversals.players().andThen(PlayerLenses.name().asTraversal());
    List<String> names = OpticOps.getAll(team, playerNames);
    System.out.println("Player names: " + names);

    // Modify team name
    Team renamedTeam = OpticOps.modify(team, TeamLenses.name(), name -> name + " FC");
    System.out.println("Renamed team: " + OpticOps.get(renamedTeam, TeamLenses.name()));

    // Modify all player scores (add 10 bonus points)
    Traversal<Team, Integer> playerScores =
        TeamTraversals.players().andThen(PlayerLenses.score().asTraversal());
    Team bonusTeam = OpticOps.modifyAll(team, playerScores, score -> score + 10);
    System.out.println("Scores after bonus: " + OpticOps.getAll(bonusTeam, playerScores));

    // Query: check if any player has score > 100
    boolean hasHighScorer = OpticOps.exists(team, playerScores, score -> score > 100);
    System.out.println("Has high scorer: " + hasHighScorer);

    // Query: count players
    Traversal<Team, Player> playersTraversal = TeamTraversals.players();
    int playerCount = OpticOps.count(team, playersTraversal);
    System.out.println("Player count: " + playerCount);

    System.out.println();

    // ========== Fluent Builders (Explicit) ==========
    System.out.println("--- Fluent Builders ---");

    // Get team name
    String teamName2 = OpticOps.getting(team).through(TeamLenses.name());
    System.out.println("Team name: " + teamName2);

    // Get all player names
    List<String> names2 = OpticOps.getting(team).allThrough(playerNames);
    System.out.println("Player names: " + names2);

    // Set team name
    Team renamedTeam2 = OpticOps.setting(team).through(TeamLenses.name(), "Eagles");
    System.out.println(
        "Renamed team: " + OpticOps.getting(renamedTeam2).through(TeamLenses.name()));

    // Modify all player scores
    Team bonusTeam2 = OpticOps.modifying(team).allThrough(playerScores, score -> score + 10);
    System.out.println(
        "Scores after bonus: " + OpticOps.getting(bonusTeam2).allThrough(playerScores));

    // Query: check if all players have score >= 90
    boolean allGoodScores = OpticOps.querying(team).allMatch(playerScores, score -> score >= 90);
    System.out.println("All players have good scores: " + allGoodScores);

    // Query: find first player with score > 100
    Lens<Player, Integer> scoreReader = PlayerLenses.score();
    Optional<Player> highScorer =
        OpticOps.querying(team)
            .findFirst(playersTraversal, player -> OpticOps.get(player, scoreReader) > 100);
    System.out.println(
        "High scorer: " + highScorer.map(p -> OpticOps.get(p, PlayerLenses.name())).orElse("none"));

    System.out.println();

    // ========== Composition Example ==========
    System.out.println("--- Composition ---");

    // Compose optics for deep access
    Traversal<Team, Integer> allAges =
        TeamTraversals.players().andThen(PlayerLenses.age().asTraversal());

    // Get all ages
    List<Integer> ages = OpticOps.getAll(team, allAges);
    System.out.println("All ages: " + ages);

    // Increment all ages by 1 (birthday!)
    Team olderTeam = OpticOps.modifyAll(team, allAges, age -> age + 1);
    System.out.println("Ages after birthday: " + OpticOps.getAll(olderTeam, allAges));

    // Check if all players are adults
    boolean allAdults = OpticOps.all(team, allAges, age -> age >= 18);
    System.out.println("All adults: " + allAdults);
  }
}
