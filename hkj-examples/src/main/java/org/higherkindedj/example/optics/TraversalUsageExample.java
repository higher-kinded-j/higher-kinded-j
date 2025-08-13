// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.ArrayList;
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

    // =======================================================================
    // SCENARIO 1: Using `with*` helpers for a targeted, shallow update
    // =======================================================================
    System.out.println("--- Scenario 1: Using `with*` Helpers ---");

    // To update a single team's name, we first get the team.
    var teamToUpdate = league.teams().getFirst();
    // Use the generated helper to create a new, updated team instance.
    var updatedTeam = TeamLenses.withName(teamToUpdate, "Team Omega");

    // Then, we create a new list of teams with the updated one.
    var newTeamsList = new ArrayList<>(league.teams());
    newTeamsList.set(0, updatedTeam);

    // Finally, use another helper to create the new league instance.
    var leagueWithUpdatedTeam = LeagueLenses.withTeams(league, newTeamsList);

    System.out.println("After updating one team's name with `with*` helpers:");
    System.out.println(leagueWithUpdatedTeam);
    System.out.println("------------------------------------------");

    // =======================================================================
    // SCENARIO 2: Using composed Traversals for deep, bulk updates
    // =======================================================================
    System.out.println("--- Scenario 2: Using Composed Traversal for Bulk Updates ---");

    // 3. Compose Traversals and Lenses to create a deep focus.
    Traversal<League, Team> leagueToTeams = LeagueTraversals.teams();
    Traversal<Team, Player> teamToPlayers = TeamTraversals.players();
    Lens<Player, Integer> playerToScore = PlayerLenses.score();

    // Compose them to create a single traversal from the league to every player's score.
    Traversal<League, Integer> leagueToAllPlayerScores =
        leagueToTeams.andThen(teamToPlayers).andThen(playerToScore.asTraversal());

    // 4. Use the composed traversal to perform a bulk update on all scores.
    var updatedLeague =
        IdKindHelper.ID
            .narrow(
                leagueToAllPlayerScores.modifyF(
                    score -> Id.of(score + 5), league, IdentityMonad.instance()))
            .value();

    System.out.println("After `modifyF` (adding 5 points to each score):");
    System.out.println(updatedLeague);
    System.out.println("------------------------------------------");
  }
}
