// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 05: Traversal Basics - Working with Multiple Values
 *
 * <p>A Traversal is an optic that focuses on zero-or-more elements within a structure. It's like a
 * Lens that can target multiple fields, or a Prism that can target multiple cases.
 *
 * <p>Key Concepts: - Focuses on 0 to many elements (unlike Lens which focuses on exactly 1) -
 * modify: applies a function to all focused elements - Bulk updates: change all elements at once -
 * Filtering: focus on elements matching a condition
 *
 * <p>Common uses: - All elements in a List - All values in a Map - All fields of a specific type in
 * nested structures
 */
public class Tutorial05_TraversalBasics {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /*
   * ========================================================================
   * IMPORTANT: Manual Optics Implementation (For Educational Purposes Only)
   * ========================================================================
   *
   * In this tutorial, we manually implement optics to help you understand their internal mechanics.
   * This is ONLY for learning - in real projects, NEVER write these manually!
   *
   * What you should do in real projects:
   * ────────────────────────────────────────────────────────────────────────
   * 1. Annotate your records with @GenerateLenses and @GenerateTraversals
   * 2. The annotation processor automatically generates optimized optics
   * 3. Use the generated optics from companion classes (e.g., TeamLenses, TeamTraversals)
   *
   * Example of real-world usage:
   *
   *   @GenerateLenses
   *   @GenerateTraversals
   *   record Team(String name, List<Player> players) {}
   *
   *   // The processor generates:
   *   // - TeamLenses.name()     -> Lens<Team, String>
   *   // - TeamLenses.players()  -> Lens<Team, List<Player>>
   *   // - TeamTraversals.players()  -> Traversal<Team, Player>
   *
   * Why we show manual implementations here:
   * ────────────────────────────────────────────────────────────────────────
   * - Understanding how Traversals work "under the hood" makes you a better user
   * - You'll appreciate what the annotation processor does for you
   * - Helpful for debugging or when you need custom optics for special cases
   *
   * The key difference:
   * ────────────────────────────────────────────────────────────────────────
   * - Traversals.forList()     : Works directly on a List<A> (list-level operation)
   * - listTraversal() below    : Extracts a List field from a structure, THEN traverses it
   * - @GenerateTraversals      : Auto-generates the field extraction + traversal combo
   */

  /**
   * Manual helper to create a Traversal for List fields within a structure.
   *
   * <p>This demonstrates how to bridge structure-level (Team has List<Player>) and element-level
   * (traverse each Player) operations.
   *
   * <p><b>DO NOT use this in production code!</b> Use @GenerateTraversals instead.
   *
   * @param getter Function to extract the List<A> field from structure S
   * @param setter Function to create a new S with an updated List<A>
   * @param <S> The structure type (e.g., Team)
   * @param <A> The element type (e.g., Player)
   * @return A Traversal<S, A> that focuses on each element within the list field
   */
  static <S, A> Traversal<S, A> listTraversal(
      Function<S, List<A>> getter, BiFunction<S, List<A>, S> setter) {
    return new Traversal<S, A>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
          Function<A, Kind<F, A>> f, S s, Applicative<F> applicative) {
        // 1. Extract the list field from the structure
        List<A> list = getter.apply(s);
        // 2. Traverse the list elements (this uses Traversals.traverseList from hkj-core)
        Kind<F, List<A>> traversedList = Traversals.traverseList(list, f, applicative);
        // 3. Map the result back into the structure
        return applicative.map(newList -> setter.apply(s, newList), traversedList);
      }
    };
  }

  /**
   * Exercise 1: Modifying all elements
   *
   * <p>Use a Traversal to modify all elements in a collection.
   *
   * <p>Task: Double all player scores in a team
   */
  @Test
  void exercise1_modifyingAllElements() {
    @GenerateLenses
    record Player(String name, int score) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String name, List<Player> players) {}

    // Manual implementations (simulating what @GenerateTraversals would create)
    // In production, the annotation processor generates this for you automatically
    class TeamTraversals {
      public static Traversal<Team, Player> players() {
        // This traversal extracts the players field AND traverses each Player element
        return listTraversal(Team::players, (t, ps) -> new Team(t.name(), ps));
      }
    }

    Team team = new Team("Team Alpha", List.of(new Player("Alice", 100), new Player("Bob", 90)));

    Traversal<Team, Player> playersTraversal = TeamTraversals.players();

    // TODO: Replace null with code that modifies all players to double their scores
    // Hint: Use Traversals.modify(playersTraversal, player -> new Player(...), team)
    // Note: Can also use OpticOps.modifyAll() for a more fluent API (covered in Tutorial 09)
    Team updated = answerRequired();

    assertThat(updated.players().get(0).score()).isEqualTo(200);
    assertThat(updated.players().get(1).score()).isEqualTo(180);
  }

  /**
   * Exercise 2: Composing traversals to access nested collections
   *
   * <p>Compose traversals to reach deeply nested collections.
   *
   * <p>Task: Access all players in all teams in a league
   */
  @Test
  void exercise2_composingTraversals() {
    @GenerateLenses
    record Player(String name, int score) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String name, List<Player> players) {}

    @GenerateLenses
    @GenerateTraversals
    record League(String name, List<Team> teams) {}

    // Manual implementations (simulating what @GenerateTraversals would create)
    class TeamTraversals {
      public static Traversal<Team, Player> players() {
        return listTraversal(Team::players, (t, ps) -> new Team(t.name(), ps));
      }
    }

    class LeagueTraversals {
      public static Traversal<League, Team> teams() {
        return listTraversal(League::teams, (l, ts) -> new League(l.name(), ts));
      }
    }

    League league =
        new League(
            "Pro League",
            List.of(
                new Team("Team A", List.of(new Player("Alice", 100), new Player("Bob", 90))),
                new Team("Team B", List.of(new Player("Charlie", 110), new Player("Diana", 120)))));

    // TODO: Replace null with composed traversals that access all players
    // Hint: LeagueTraversals.teams().andThen(TeamTraversals.players())
    Traversal<League, Player> allPlayers = answerRequired();

    // Add 10 bonus points to all players
    League updated =
        Traversals.modify(allPlayers, p -> new Player(p.name(), p.score() + 10), league);

    assertThat(updated.teams().get(0).players().get(0).score()).isEqualTo(110); // Alice
    assertThat(updated.teams().get(1).players().get(1).score()).isEqualTo(130); // Diana
  }

  /**
   * Exercise 3: Traversal with Lens composition
   *
   * <p>Compose a Traversal with a Lens to modify a specific field in all elements.
   *
   * <p>Task: Update only the scores of all players
   */
  @Test
  void exercise3_traversalWithLens() {
    @GenerateLenses
    record Player(String name, int score) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String name, List<Player> players) {}

    // Manual implementations (simulating what @GenerateLenses and @GenerateTraversals would create)
    class PlayerLenses {
      public static Lens<Player, Integer> score() {
        return Lens.of(Player::score, (p, newScore) -> new Player(p.name(), newScore));
      }
    }

    class TeamTraversals {
      public static Traversal<Team, Player> players() {
        return listTraversal(Team::players, (t, ps) -> new Team(t.name(), ps));
      }
    }

    Team team = new Team("Team Alpha", List.of(new Player("Alice", 100), new Player("Bob", 90)));

    // TODO: Replace null with a composition of traversal and lens
    // to focus on just the scores
    // Hint: TeamTraversals.players().andThen(PlayerLenses.score().asTraversal())
    Traversal<Team, Integer> allScores = answerRequired();

    Team updated = Traversals.modify(allScores, score -> score + 5, team);

    assertThat(updated.players().get(0).score()).isEqualTo(105);
    assertThat(updated.players().get(1).score()).isEqualTo(95);
  }

  /**
   * Exercise 4: Filtering elements
   *
   * <p>Use filtered() to focus only on elements matching a condition.
   *
   * <p>Task: Give a bonus only to players with scores above 100
   */
  @Test
  void exercise4_filteringElements() {
    @GenerateLenses
    record Player(String name, int score) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String name, List<Player> players) {}

    // Manual implementations (simulating what @GenerateTraversals would create)
    class TeamTraversals {
      public static Traversal<Team, Player> players() {
        return listTraversal(Team::players, (t, ps) -> new Team(t.name(), ps));
      }
    }

    Team team =
        new Team(
            "Team Alpha",
            List.of(new Player("Alice", 120), new Player("Bob", 90), new Player("Charlie", 105)));

    Traversal<Team, Player> playersTraversal = TeamTraversals.players();

    // TODO: Replace null with a filtered traversal that only targets players with score > 100
    // Hint: playersTraversal.filtered(p -> p.score() > 100)
    Traversal<Team, Player> highScorers = answerRequired();

    Team updated = Traversals.modify(highScorers, p -> new Player(p.name(), p.score() + 10), team);

    assertThat(updated.players().get(0).score()).isEqualTo(130); // Alice (was > 100)
    assertThat(updated.players().get(1).score()).isEqualTo(90); // Bob (unchanged)
    assertThat(updated.players().get(2).score()).isEqualTo(115); // Charlie (was > 100)
  }

  /**
   * Exercise 5: Getting all values
   *
   * <p>Use getAll to extract all focused values into a list.
   *
   * <p>Task: Extract all player names from a team
   */
  @Test
  void exercise5_gettingAllValues() {
    @GenerateLenses
    record Player(String name, int score) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String name, List<Player> players) {}

    // Manual implementations (simulating what @GenerateLenses and @GenerateTraversals would create)
    class PlayerLenses {
      public static Lens<Player, String> name() {
        return Lens.of(Player::name, (p, newName) -> new Player(newName, p.score()));
      }
    }

    class TeamTraversals {
      public static Traversal<Team, Player> players() {
        return listTraversal(Team::players, (t, ps) -> new Team(t.name(), ps));
      }
    }

    Team team = new Team("Team Alpha", List.of(new Player("Alice", 100), new Player("Bob", 90)));

    Traversal<Team, String> allNames =
        TeamTraversals.players().andThen(PlayerLenses.name().asTraversal());

    // TODO: Replace null with code that gets all player names
    // Hint: Traversals.getAll(allNames, team)
    List<String> names = answerRequired();

    assertThat(names).containsExactly("Alice", "Bob");
  }

  /**
   * Exercise 6: Nested filtering
   *
   * <p>Apply multiple filters in a traversal chain.
   *
   * <p>Task: Find all high-scoring players in winning teams
   */
  @Test
  void exercise6_nestedFiltering() {
    @GenerateLenses
    record Player(String name, int score) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String name, List<Player> players, boolean won) {}

    @GenerateLenses
    @GenerateTraversals
    record Tournament(List<Team> teams) {}

    // Manual implementations (simulating what @GenerateLenses and @GenerateTraversals would create)
    class PlayerLenses {
      public static Lens<Player, String> name() {
        return Lens.of(Player::name, (p, newName) -> new Player(newName, p.score()));
      }
    }

    class TeamTraversals {
      public static Traversal<Team, Player> players() {
        return listTraversal(Team::players, (t, ps) -> new Team(t.name(), ps, t.won()));
      }
    }

    class TournamentTraversals {
      public static Traversal<Tournament, Team> teams() {
        return listTraversal(Tournament::teams, (t, ts) -> new Tournament(ts));
      }
    }

    Tournament tournament =
        new Tournament(
            List.of(
                new Team("Team A", List.of(new Player("Alice", 120), new Player("Bob", 80)), true),
                new Team(
                    "Team B", List.of(new Player("Charlie", 95), new Player("Diana", 130)), false),
                new Team("Team C", List.of(new Player("Eve", 110)), true)));

    // TODO: Replace null with a traversal that:
    // 1. Filters to winning teams (won == true)
    // 2. Gets their players
    // 3. Filters to players with score >= 100
    Traversal<Tournament, Player> winningHighScorers =
        TournamentTraversals.teams()
            .filtered(null)
            .andThen(TeamTraversals.players())
            .filtered(null);

    List<String> names =
        Traversals.getAll(
            winningHighScorers.andThen(PlayerLenses.name().asTraversal()), tournament);

    assertThat(names).containsExactly("Alice", "Eve");
  }

  /**
   * Exercise 7: Conditional batch updates
   *
   * <p>Apply different updates based on conditions.
   *
   * <p>Task: Apply different bonuses based on score ranges
   */
  @Test
  void exercise7_conditionalBatchUpdates() {
    @GenerateLenses
    record Player(String name, int score) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String name, List<Player> players) {}

    // Manual implementations (simulating what @GenerateTraversals would create)
    class TeamTraversals {
      public static Traversal<Team, Player> players() {
        return listTraversal(Team::players, (t, ps) -> new Team(t.name(), ps));
      }
    }

    Team team =
        new Team(
            "Team Alpha",
            List.of(
                new Player("Alice", 120), // Gets +10 bonus
                new Player("Bob", 85), // Gets +5 bonus
                new Player("Charlie", 100))); // Gets +10 bonus

    Traversal<Team, Player> playersTraversal = TeamTraversals.players();

    // TODO: Replace 0 with code that gives different bonuses:
    // +10 for score >= 100, +5 for score < 100
    Team updated =
        Traversals.modify(
            playersTraversal,
            p -> {
              int bonus = answerRequired();
              return new Player(p.name(), p.score() + bonus);
            },
            team);

    assertThat(updated.players().get(0).score()).isEqualTo(130); // Alice: 120 + 10
    assertThat(updated.players().get(1).score()).isEqualTo(90); // Bob: 85 + 5
    assertThat(updated.players().get(2).score()).isEqualTo(110); // Charlie: 100 + 10
  }

  /**
   * Congratulations! You've completed Tutorial 05: Traversal Basics
   *
   * <p>You now understand: ✓ How to use Traversals to modify multiple elements at once ✓ How to
   * compose Traversals to reach nested collections ✓ How to combine Traversals with Lenses ✓ How to
   * filter elements with filtered() ✓ How to extract all values with getAll() ✓ How to apply
   * complex conditional updates ✓ When to use Traversals (bulk operations on collections)
   *
   * <p>Next: Tutorial 06 - Optics Composition
   */
}
