// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial05 TraversalBasics — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
public class Tutorial05_TraversalBasics_Solution {

  // Manual traversal/lens implementations (annotation processor generates these in real projects)
  // These are generic helpers that work with the record types defined in each test method

  /** Helper to create a Traversal for List fields */
  static <S, A> Traversal<S, A> listTraversal(
      Function<S, List<A>> getter, BiFunction<S, List<A>, S> setter) {
    return new Traversal<S, A>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
          Function<A, Kind<F, A>> f, S s, Applicative<F> applicative) {
        List<A> list = getter.apply(s);
        var listKind = Traversals.traverseList(list, f, applicative);
        return applicative.map(newList -> setter.apply(s, newList), listKind);
      }
    };
  }

  /**
   * Why this is idiomatic: {@code Traversals.modify(traversal, fn, source)} rebuilds the outer
   * structure with every focused element transformed in one pass. The traversal owns the "for each"
   * bit; the function only sees the leaf.
   *
   * <p>Alternative: {@code list.stream().map(...).toList()} and rebuild the {@code Team} around it.
   * Same answer; the traversal stays composable so the same call generalises to deeper structures.
   *
   * <p>Common wrong attempt: try to mutate the players in place. The list inside a record is
   * whatever {@code List.of(...)} returns — usually unmodifiable; the traversal-driven rebuild is
   * the only safe path.
   */
  @Test
  void exercise1_modifyingAllElements() {
    @GenerateLenses
    record Player(String name, int score) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String name, List<Player> players) {}

    // Manual implementations
    class TeamTraversals {
      public static Traversal<Team, Player> players() {
        return listTraversal(Team::players, (t, ps) -> new Team(t.name(), ps));
      }
    }

    Team team = new Team("Team Alpha", List.of(new Player("Alice", 100), new Player("Bob", 90)));

    Traversal<Team, Player> playersTraversal = TeamTraversals.players();

    // SOLUTION: Use Traversals.modify() to double all player scores
    Team updated =
        Traversals.modify(
            playersTraversal, player -> new Player(player.name(), player.score() * 2), team);

    assertThat(updated.players().get(0).score()).isEqualTo(200);
    assertThat(updated.players().get(1).score()).isEqualTo(180);
  }

  /**
   * Why this is idiomatic: {@code teams.andThen(players)} produces a single {@code
   * Traversal<League, Player>} — every player in every team. One bonus update is applied across the
   * whole nested structure with no manual iteration.
   *
   * <p>Alternative: nested {@code stream().flatMap(...)} chains. Equivalent for reads; the composed
   * traversal is what makes the corresponding update possible without rebuilding each {@code Team}
   * and {@code League} by hand.
   *
   * <p>Common wrong attempt: write a recursive helper that walks the structure imperatively. Works
   * once, breaks the moment the schema gains a layer; the traversal composition stays a single
   * line.
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

    // Manual implementations
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

    // SOLUTION: Compose traversals to access all players
    Traversal<League, Player> allPlayers =
        LeagueTraversals.teams().andThen(TeamTraversals.players());

    // Add 10 bonus points to all players
    League updated =
        Traversals.modify(allPlayers, p -> new Player(p.name(), p.score() + 10), league);

    assertThat(updated.teams().get(0).players().get(0).score()).isEqualTo(110); // Alice
    assertThat(updated.teams().get(1).players().get(1).score()).isEqualTo(130); // Diana
  }

  /**
   * Why this is idiomatic: {@code players.andThen(score.asTraversal())} narrows the focus to "every
   * score in every player". The transform sees raw {@code Integer}s; the traversal does the
   * navigation and rebuild.
   *
   * <p>Alternative: a single {@code modify} that takes a {@code Player} and rebuilds it with the
   * new score. Same answer; the lens-composed traversal stays stable when more {@code Player}
   * fields appear.
   *
   * <p>Common wrong attempt: forget {@code asTraversal()} and try to compose the lens directly with
   * {@code andThen}. {@code Lens.andThen(Traversal)} returns a traversal in the right direction,
   * but most APIs expect both halves to be the same kind of optic; {@code asTraversal} is the
   * explicit lift.
   */
  @Test
  void exercise3_traversalWithLens() {
    @GenerateLenses
    record Player(String name, int score) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String name, List<Player> players) {}

    // Manual implementations
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

    // SOLUTION: Compose traversal and lens to focus on just the scores
    Traversal<Team, Integer> allScores =
        TeamTraversals.players().andThen(PlayerLenses.score().asTraversal());

    Team updated = Traversals.modify(allScores, score -> score + 5, team);

    assertThat(updated.players().get(0).score()).isEqualTo(105);
    assertThat(updated.players().get(1).score()).isEqualTo(95);
  }

  /**
   * Why this is idiomatic: {@code traversal.filtered(predicate)} narrows the focus to elements the
   * predicate accepts; non-matching elements pass through {@code modify} untouched. The test
   * asserts both effects in one update.
   *
   * <p>Alternative: a single {@code modify} that branches inside the function. Same answer; the
   * filtered traversal advertises the predicate at the optic level so other reads ({@code getAll},
   * {@code asFold}) inherit the same scope.
   *
   * <p>Common wrong attempt: pre-filter the list, modify, and zip back. The order is now fragile
   * and the merge logic has to reconstruct the unmodified items; the filtered traversal handles
   * this correctly.
   */
  @Test
  void exercise4_filteringElements() {
    @GenerateLenses
    record Player(String name, int score) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String name, List<Player> players) {}

    // Manual implementations
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

    // SOLUTION: Create a filtered traversal that only targets players with score > 100
    Traversal<Team, Player> highScorers = playersTraversal.filtered(p -> p.score() > 100);

    Team updated = Traversals.modify(highScorers, p -> new Player(p.name(), p.score() + 10), team);

    assertThat(updated.players().get(0).score()).isEqualTo(130); // Alice (was > 100)
    assertThat(updated.players().get(1).score()).isEqualTo(90); // Bob (unchanged)
    assertThat(updated.players().get(2).score()).isEqualTo(115); // Charlie (was > 100)
  }

  /**
   * Why this is idiomatic: {@code Traversals.getAll(traversal, source)} reads every focused leaf
   * into a list. The traversal is the same one used for writes — read and write share the exact
   * same path.
   *
   * <p>Alternative: stream the players, map the names, collect. Equivalent for reads; the
   * traversal-based read keeps a single source of truth for the navigation.
   *
   * <p>Common wrong attempt: write a separate getter that extracts names. The optic-driven approach
   * keeps a single navigation; the bespoke getter has to be updated in lockstep.
   */
  @Test
  void exercise5_gettingAllValues() {
    @GenerateLenses
    record Player(String name, int score) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String name, List<Player> players) {}

    // Manual implementations
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

    // SOLUTION: Use Traversals.getAll() to get all player names
    List<String> names = Traversals.getAll(allNames, team);

    assertThat(names).containsExactly("Alice", "Bob");
  }

  /**
   * Why this is idiomatic: filter at each layer where it makes sense — winning teams, then
   * high-scoring players within them. The traversal reads as a sentence; the predicates compose
   * with {@code andThen} like every other layer.
   *
   * <p>Alternative: a single nested loop with a compound condition. Equivalent reading; the staged
   * filters separate "which teams" from "which players" so each predicate stays focused.
   *
   * <p>Common wrong attempt: filter the players first, then try to filter the teams. The filters
   * operate at different levels of the structure — flatten them in the wrong order and you cannot
   * tell whether a low-scorer's team won.
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

    // Manual implementations
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

    // SOLUTION: Filter to winning teams and high-scoring players
    Traversal<Tournament, Player> winningHighScorers =
        TournamentTraversals.teams()
            .filtered(Team::won)
            .andThen(TeamTraversals.players())
            .filtered(p -> p.score() >= 100);

    List<String> names =
        Traversals.getAll(
            winningHighScorers.andThen(PlayerLenses.name().asTraversal()), tournament);

    assertThat(names).containsExactly("Alice", "Eve");
  }

  /**
   * Why this is idiomatic: when the bonus depends on the player's score, fold the choice into the
   * per-element function. The traversal still rebuilds the structure once; the lambda decides per
   * element.
   *
   * <p>Alternative: split into two filtered traversals (high vs. low) and apply different modifies.
   * Equivalent; the single-traversal form here keeps the rebuild a one-liner.
   *
   * <p>Common wrong attempt: sort or partition the list before modifying. The original order is
   * meaningful in many domains; the per-element decision preserves it.
   */
  @Test
  void exercise7_conditionalBatchUpdates() {
    @GenerateLenses
    record Player(String name, int score) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String name, List<Player> players) {}

    // Manual implementations
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

    // SOLUTION: Give different bonuses: +10 for score >= 100, +5 for score < 100
    Team updated =
        Traversals.modify(
            playersTraversal,
            p -> {
              int bonus = p.score() >= 100 ? 10 : 5;
              return new Player(p.name(), p.score() + bonus);
            },
            team);

    assertThat(updated.players().get(0).score()).isEqualTo(130); // Alice: 120 + 10
    assertThat(updated.players().get(1).score()).isEqualTo(90); // Bob: 85 + 5
    assertThat(updated.players().get(2).score()).isEqualTo(110); // Charlie: 100 + 10
  }

  /**
   * Why this is idiomatic: {@code asFold()} converts a traversal into a read-only fold so a {@code
   * Monoid} can aggregate. {@code Monoids.integerAddition()} expresses "sum these" directly; the
   * fold walks the structure and combines.
   *
   * <p>Alternative: {@code Traversals.getAll(...).stream().mapToInt(...).sum()}. Same total; the
   * fold keeps the optic-driven navigation and exposes companion queries ({@code length}, {@code
   * all}, {@code exists}) on the same path.
   *
   * <p>Common wrong attempt: try to {@code modify} the traversal to compute a running sum via a
   * side-effecting function. {@code modify} expects a pure leaf transform; use {@code asFold} when
   * the goal is aggregation rather than rewriting.
   */
  @Test
  void exercise8_traversalAsFold() {
    @GenerateLenses
    record Player(String name, int score, boolean active) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String name, List<Player> players) {}

    // Manual implementations
    class PlayerLenses {
      public static Lens<Player, Integer> score() {
        return Lens.of(Player::score, (p, newScore) -> new Player(p.name(), newScore, p.active()));
      }
    }

    class TeamTraversals {
      public static Traversal<Team, Player> players() {
        return listTraversal(Team::players, (t, ps) -> new Team(t.name(), ps));
      }
    }

    Team team =
        new Team(
            "Team Alpha",
            List.of(
                new Player("Alice", 120, true),
                new Player("Bob", 80, false),
                new Player("Charlie", 150, true),
                new Player("Diana", 90, true)));

    // SOLUTION: Filter to active players, compose with score lens, convert to Fold
    Fold<Team, Integer> activeScoresFold =
        TeamTraversals.players()
            .filtered(Player::active)
            .andThen(PlayerLenses.score().asTraversal())
            .asFold();

    int totalActiveScore = activeScoresFold.foldMap(Monoids.integerAddition(), s -> s, team);

    // Alice(120) + Charlie(150) + Diana(90) = 360
    assertThat(totalActiveScore).isEqualTo(360);

    // Bonus: verify fold query methods work too
    assertThat(activeScoresFold.length(team)).isEqualTo(3);
    assertThat(activeScoresFold.all(s -> s > 50, team)).isTrue();
    assertThat(activeScoresFold.exists(s -> s > 140, team)).isTrue();
  }

  /**
   * Congratulations! You've completed Tutorial 05: Traversal Basics
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>✓ How to use Traversals to modify multiple elements at once
   *   <li>✓ How to compose Traversals to reach nested collections
   *   <li>✓ How to combine Traversals with Lenses
   *   <li>✓ How to filter elements with filtered()
   *   <li>✓ How to extract all values with getAll()
   *   <li>✓ How to apply complex conditional updates
   *   <li>✓ How to convert Traversals to Folds for read-only aggregation
   *   <li>✓ When to use Traversals (bulk operations on collections)
   * </ul>
   *
   * <p>═══════════════════════════════════════════════════════════════════════ <b>Alternative: Each
   * Typeclass for Canonical Traversals</b>
   * ═══════════════════════════════════════════════════════════════════════
   *
   * <p>This tutorial showed manual and generated traversal creation. For many common container
   * types, the <b>Each typeclass</b> provides canonical traversals out of the box:
   *
   * <pre>{@code
   * import org.higherkindedj.optics.each.EachInstances;
   * import org.higherkindedj.optics.extensions.EachExtensions;
   *
   * // Java types via EachInstances
   * Each<List<String>, String> listEach = EachInstances.listEach();
   * Each<Map<K, V>, V> mapEach = EachInstances.mapValuesEach();
   * Each<Optional<A>, A> optEach = EachInstances.optionalEach();
   *
   * // HKT types via EachExtensions
   * Each<Maybe<A>, A> maybeEach = EachExtensions.maybeEach();
   * Each<Either<E, A>, A> eitherEach = EachExtensions.eitherRightEach();
   *
   * // Use with Focus DSL
   * TraversalPath<Order, Item> items = FocusPath.of(itemsLens)
   *     .each(EachInstances.listEach());
   * }</pre>
   *
   * <p><b>Key Benefits of Each:</b>
   *
   * <ul>
   *   <li>Uniform interface for any container type
   *   <li>Optional indexed access via {@code eachWithIndex()}
   *   <li>Integrates with Focus DSL via {@code .each(Each)}
   *   <li>Extensible for custom containers
   * </ul>
   *
   * <p>See {@code EachInstancesExample.java} and the Each Typeclass documentation.
   *
   * <p>Next: Tutorial 06 - Optics Composition
   */
}
