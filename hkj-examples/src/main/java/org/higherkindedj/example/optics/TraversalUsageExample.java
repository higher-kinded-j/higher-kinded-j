// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdentityMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/**
 * A runnable example demonstrating how to use and compose Traversals to perform bulk updates on
 * items within nested collections.
 */
public class TraversalUsageExample {

  // 1. Define a nested, immutable data model with collections.
  @GenerateLenses
  public record Player(String name, int score) {}

  @GenerateLenses
  @GenerateTraversals // Generates a Traversal for the List<Player>
  public record Team(String name, List<Player> players) {}

  @GenerateLenses
  @GenerateTraversals // Generates a Traversal for the List<Team>
  public record League(String name, List<Team> teams) {}

  public static void main(String[] args) {

    // 2. Create an initial, nested data structure.
    var team1 = new Team("Team Alpha", List.of(new Player("Alice", 100), new Player("Bob", 90)));
    var team2 =
        new Team("Team Bravo", List.of(new Player("Charlie", 110), new Player("Diana", 120)));
    var league = new League("Pro League", List.of(team1, team2));

    System.out.println("Original League: " + league);
    System.out.println("------------------------------------------");

    // 3. Compose Traversals and Lenses to create a deep focus.
    Traversal<League, Team> leagueToTeams = LeagueTraversals.teams();
    Traversal<Team, Player> teamToPlayers = TeamTraversals.players();
    Lens<Player, Integer> playerToScore = PlayerLenses.score();

    // Compose them to create a single, deep traversal.
    // Convert the Lens to a Traversal to maintain the specific return type.
    Traversal<League, Integer> leagueToAllPlayerScores =
        leagueToTeams
            .andThen(teamToPlayers)
            .andThen(playerToScore.asTraversal()); // Convert Lens to Traversal

    // 4. Use the composed traversal to perform a bulk update.
    var updatedLeague =
        IdKindHelper.ID
            .narrow(
                leagueToAllPlayerScores.modifyF(
                    score -> Id.of(score + 5), league, IdentityMonad.instance()))
            .value();

    System.out.println("After `modifyF` (adding 5 points to each score):");
    System.out.println(updatedLeague);
    System.out.println("------------------------------------------");

    // --- Another example: Composing to update all player names ---
    Lens<Player, String> playerToName = PlayerLenses.name();

    Traversal<League, String> leagueToAllPlayerNames =
        leagueToTeams
            .andThen(teamToPlayers)
            .andThen(playerToName.asTraversal()); // Convert Lens to Traversal

    var leagueWithUpperCasedNames =
        IdKindHelper.ID
            .narrow(
                leagueToAllPlayerNames.modifyF(
                    name -> Id.of(name.toUpperCase()), league, IdentityMonad.instance()))
            .value();

    System.out.println("After `modifyF` (uppercasing all player names):");
    System.out.println(leagueWithUpperCasedNames);
  }
}
