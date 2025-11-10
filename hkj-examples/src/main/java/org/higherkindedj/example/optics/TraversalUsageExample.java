// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdSelective;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;

/**
 * A runnable example demonstrating how to use and compose Traversals to perform bulk updates on
 * items within nested collections.
 */
public class TraversalUsageExample {

  @GenerateLenses
  public record Player(String name, int score) {}

  @GenerateLenses
  @GenerateTraversals
  public record Team(String name, List<Player> players) {}

  @GenerateLenses
  @GenerateTraversals
  public record League(String name, List<Team> teams) {}

  public static void main(String[] args) {
    var team1 = new Team("Team Alpha", List.of(new Player("Alice", 100), new Player("Bob", 90)));
    var team2 =
        new Team("Team Bravo", List.of(new Player("Charlie", 110), new Player("Diana", 120)));
    var league = new League("Pro League", List.of(team1, team2));

    System.out.println("=== TRAVERSAL USAGE EXAMPLE ===");
    System.out.println("Original League: " + league);
    System.out.println("------------------------------------------");

    // --- SCENARIO 1: Using `with*` helpers for a targeted, shallow update ---
    System.out.println("--- Scenario 1: Shallow Update with `with*` Helpers ---");
    var teamToUpdate = league.teams().get(0);
    var updatedTeam = TeamLenses.withName(teamToUpdate, "Team Omega");
    var newTeamsList = new ArrayList<>(league.teams());
    newTeamsList.set(0, updatedTeam);
    var leagueWithUpdatedTeam = LeagueLenses.withTeams(league, newTeamsList);

    System.out.println("After updating one team's name:");
    System.out.println(leagueWithUpdatedTeam);
    System.out.println("------------------------------------------");

    // --- SCENARIO 2: Using composed Traversals for deep, bulk updates ---
    System.out.println("--- Scenario 2: Bulk Updates with Composed Traversals ---");

    // Create the composed traversal
    Traversal<League, Integer> leagueToAllPlayerScores =
        LeagueTraversals.teams()
            .andThen(TeamTraversals.players())
            .andThen(PlayerLenses.score().asTraversal());

    // Use the `modify` helper to add 5 bonus points to every score.
    League updatedLeague = Traversals.modify(leagueToAllPlayerScores, score -> score + 5, league);
    System.out.println("After adding 5 bonus points to all players:");
    System.out.println(updatedLeague);
    System.out.println();

    // --- SCENARIO 3: Extracting data with `getAll` ---
    System.out.println("--- Scenario 3: Data Extraction ---");

    List<Integer> allScores = Traversals.getAll(leagueToAllPlayerScores, league);
    System.out.println("All player scores: " + allScores);
    System.out.println("Total players: " + allScores.size());
    System.out.println(
        "Average score: " + allScores.stream().mapToInt(Integer::intValue).average().orElse(0.0));
    System.out.println();

    // --- SCENARIO 4: Conditional updates ---
    System.out.println("--- Scenario 4: Conditional Updates ---");

    // Give bonus points only to players with scores >= 100
    League bonusLeague =
        Traversals.modify(
            leagueToAllPlayerScores, score -> score >= 100 ? score + 20 : score, league);
    System.out.println("After conditional bonus (20 points for scores >= 100):");
    System.out.println(bonusLeague);
    System.out.println();

    // --- SCENARIO 5: Multiple traversals ---
    System.out.println("--- Scenario 5: Multiple Traversals ---");

    // Create a traversal for player names
    Traversal<League, String> leagueToAllPlayerNames =
        LeagueTraversals.teams()
            .andThen(TeamTraversals.players())
            .andThen(PlayerLenses.name().asTraversal());

    // Normalise all names to uppercase
    League upperCaseLeague = Traversals.modify(leagueToAllPlayerNames, String::toUpperCase, league);
    System.out.println("After converting all names to uppercase:");
    System.out.println(upperCaseLeague);
    System.out.println();

    // --- SCENARIO 6: Working with empty collections ---
    System.out.println("--- Scenario 6: Empty Collections ---");

    League emptyLeague = new League("Empty League", List.of());
    List<Integer> emptyScores = Traversals.getAll(leagueToAllPlayerScores, emptyLeague);
    League emptyAfterUpdate =
        Traversals.modify(leagueToAllPlayerScores, score -> score + 100, emptyLeague);

    System.out.println("Empty league: " + emptyLeague);
    System.out.println("Scores from empty league: " + emptyScores);
    System.out.println("Empty league after update: " + emptyAfterUpdate);

    System.out.println("------------------------------------------");
    System.out.println("Original league unchanged: " + league);

    selectiveConditionalUpdate();
    selectiveBranchingUpdate();
  }

  // --- NEW SCENARIO: Selective Conditional Updates ---
  private static void selectiveConditionalUpdate() {
    System.out.println("--- Scenario 7: Selective Conditional Updates ---");

    var team1 =
        new Team(
            "Team Alpha",
            List.of(new Player("Alice", 150), new Player("Bob", 90), new Player("Charlie", 110)));
    var team2 = new Team("Team Bravo", List.of(new Player("Diana", 200), new Player("Eve", 80)));
    var league = new League("Pro League", List.of(team1, team2));

    Traversal<League, Integer> leagueToAllPlayerScores =
        LeagueTraversals.teams()
            .andThen(TeamTraversals.players())
            .andThen(PlayerLenses.score().asTraversal());

    // Only give bonus to high scorers (>= 100)
    Predicate<Integer> isHighScorer = score -> score >= 100;

    Kind<Id.Witness, League> updated =
        leagueToAllPlayerScores.modifyIf(
            isHighScorer,
            score -> Id.of(score + 50), // 50 point bonus
            league,
            IdSelective.instance());

    System.out.println("Original league:");
    printLeagueScores(league);
    System.out.println("\nAfter selective bonus (only >= 100):");
    printLeagueScores(ID.narrow(updated).value());
    System.out.println();
  }

  // --- NEW SCENARIO: Selective Branching ---
  private static void selectiveBranchingUpdate() {
    System.out.println("--- Scenario 8: Selective Branching Updates ---");

    var team =
        new Team(
            "Mixed Team",
            List.of(
                new Player("Veteran", 180),
                new Player("Rookie", 50),
                new Player("MidLevel", 100),
                new Player("Expert", 250)));
    var league = new League("Diverse League", List.of(team));

    Traversal<League, Integer> scoreTraversal =
        LeagueTraversals.teams()
            .andThen(TeamTraversals.players())
            .andThen(PlayerLenses.score().asTraversal());

    // Different bonuses for different score ranges
    Predicate<Integer> isExpert = score -> score >= 200;

    Kind<Id.Witness, League> updated =
        scoreTraversal.branch(
            isExpert,
            score -> Id.of(score + 100), // Expert bonus: +100
            score -> Id.of(score + 20), // Regular bonus: +20
            league,
            IdSelective.instance());

    System.out.println("Original scores:");
    printLeagueScores(league);
    System.out.println("\nAfter branching bonuses (experts +100, others +20):");
    printLeagueScores(ID.narrow(updated).value());
    System.out.println();
  }

  private static void printLeagueScores(League league) {
    league
        .teams()
        .forEach(
            team -> {
              System.out.println("  " + team.name() + ":");
              team.players()
                  .forEach(
                      player -> System.out.println("    " + player.name() + ": " + player.score()));
            });
  }
}
