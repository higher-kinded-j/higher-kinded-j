// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.optics.fluent.generated.FootballClubLenses;
import org.higherkindedj.example.optics.fluent.generated.FootballClubTraversals;
import org.higherkindedj.example.optics.fluent.generated.FootballerLenses;
import org.higherkindedj.example.optics.fluent.model.FootballClub;
import org.higherkindedj.example.optics.fluent.model.Footballer;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.fluent.OpticOps;

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

  public static void main(String[] args) {
    FootballClub team =
        new FootballClub(
            "Wildcats",
            List.of(
                new Footballer("Alice", 25, 100),
                new Footballer("Bob", 30, 95),
                new Footballer("Charlie", 22, 110)));

    System.out.println("=== Fluent API Examples ===\n");

    // ========== Static Methods (Concise) ==========
    System.out.println("--- Static Methods ---");

    // Get team name
    String teamName = OpticOps.get(team, FootballClubLenses.name());
    System.out.println("Team name: " + teamName);

    // Get all player names
    Traversal<FootballClub, String> playerNames =
        FootballClubTraversals.players().andThen(FootballerLenses.name().asTraversal());
    List<String> names = OpticOps.getAll(team, playerNames);
    System.out.println("Player names: " + names);

    // Modify team name
    FootballClub renamedTeam =
        OpticOps.modify(team, FootballClubLenses.name(), name -> name + " FC");
    System.out.println("Renamed team: " + OpticOps.get(renamedTeam, FootballClubLenses.name()));

    // Modify all player scores (add 10 bonus points)
    Traversal<FootballClub, Integer> playerScores =
        FootballClubTraversals.players().andThen(FootballerLenses.score().asTraversal());
    FootballClub bonusTeam = OpticOps.modifyAll(team, playerScores, score -> score + 10);
    System.out.println("Scores after bonus: " + OpticOps.getAll(bonusTeam, playerScores));

    // Query: check if any player has score > 100
    boolean hasHighScorer = OpticOps.exists(team, playerScores, score -> score > 100);
    System.out.println("Has high scorer: " + hasHighScorer);

    // Query: count players
    Traversal<FootballClub, Footballer> playersTraversal = FootballClubTraversals.players();
    int playerCount = OpticOps.count(team, playersTraversal);
    System.out.println("Player count: " + playerCount);

    System.out.println();

    // ========== Fluent Builders (Explicit) ==========
    System.out.println("--- Fluent Builders ---");

    // Get team name
    String teamName2 = OpticOps.getting(team).through(FootballClubLenses.name());
    System.out.println("Team name: " + teamName2);

    // Get all player names
    List<String> names2 = OpticOps.getting(team).allThrough(playerNames);
    System.out.println("Player names: " + names2);

    // Set team name
    FootballClub renamedTeam2 = OpticOps.setting(team).through(FootballClubLenses.name(), "Eagles");
    System.out.println(
        "Renamed team: " + OpticOps.getting(renamedTeam2).through(FootballClubLenses.name()));

    // Modify all player scores
    FootballClub bonusTeam2 =
        OpticOps.modifying(team).allThrough(playerScores, score -> score + 10);
    System.out.println(
        "Scores after bonus: " + OpticOps.getting(bonusTeam2).allThrough(playerScores));

    // Query: check if all players have score >= 90
    boolean allGoodScores = OpticOps.querying(team).allMatch(playerScores, score -> score >= 90);
    System.out.println("All players have good scores: " + allGoodScores);

    // Query: find first player with score > 100
    Lens<Footballer, Integer> scoreReader = FootballerLenses.score();
    Optional<Footballer> highScorer =
        OpticOps.querying(team)
            .findFirst(playersTraversal, player -> OpticOps.get(player, scoreReader) > 100);
    System.out.println(
        "High scorer: "
            + highScorer.map(p -> OpticOps.get(p, FootballerLenses.name())).orElse("none"));

    System.out.println();

    // ========== Composition Example ==========
    System.out.println("--- Composition ---");

    // Compose optics for deep access
    Traversal<FootballClub, Integer> allAges =
        FootballClubTraversals.players().andThen(FootballerLenses.age().asTraversal());

    // Get all ages
    List<Integer> ages = OpticOps.getAll(team, allAges);
    System.out.println("All ages: " + ages);

    // Increment all ages by 1 (birthday!)
    FootballClub olderTeam = OpticOps.modifyAll(team, allAges, age -> age + 1);
    System.out.println("Ages after birthday: " + OpticOps.getAll(olderTeam, allAges));

    // Check if all players are adults
    boolean allAdults = OpticOps.all(team, allAges, age -> age >= 18);
    System.out.println("All adults: " + allAdults);
  }
}
