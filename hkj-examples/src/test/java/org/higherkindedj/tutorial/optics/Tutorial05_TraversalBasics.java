// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 05: Traversal Basics — focusing on many values at once.
 *
 * <p>Pain → Promise. Updating every element of a list inside a record is a copy-then-stream dance:
 *
 * <pre>
 *   var newItems = order.items().stream()
 *       .map(item -&gt; new Item(item.id(), item.price() * 1.1, ...))
 *       .toList();
 *   var updated = new Order(order.id(), newItems, order.status());
 * </pre>
 *
 * <p>A {@link Traversal} captures "focus every element" as a single composable optic:
 *
 * <pre>
 *   Order updated = OrderTraversals.itemsPrice().modify(p -&gt; p * 1.1, order);
 * </pre>
 *
 * <p>Java idiom anchor:
 *
 * <ul>
 *   <li>{@code traversal.modify(fn, s)} ↔ {@code .stream().map(fn).toList()} plus copy-construction
 *       of the surrounding record.
 *   <li>{@link Fold} (the read-only side) ↔ {@code .stream().reduce(...)}.
 *   <li>Traversals compose with lenses and prisms for paths into deeply nested collections.
 * </ul>
 *
 * <p>A Traversal is the "0..many" sibling of {@link Lens} (exactly 1) and Prism / Affine (0..1).
 * Use it when the focus count is unknown ahead of time: a list, every value in a map, every Some in
 * an Optional pipeline.
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
   * Exercise 1: Modify all elements through a Traversal.
   *
   * <pre>
   *   // Nudge:    Traversals.modify(traversal, fn, source) applies fn to every focus.
   *   // Strategy: Traversals.modify(playersTraversal, p -&gt; new Player(p.name(), p.score() * 2), team)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: Traversal.modify updates every focused element")
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
   * Exercise 2: Compose traversals across nested collections.
   *
   * <pre>
   *   // Nudge:    andThen for traversals reads top-down through the structure.
   *   // Strategy: LeagueTraversals.teams().andThen(TeamTraversals.players())
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: compose traversals to reach nested collections")
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
   * Exercise 3: Traversal + Lens composition.
   *
   * <pre>
   *   // Nudge:    Traversals.modify(traversal, fn, source) requires a Traversal end-to-end;
   *   //           a Lens becomes a Traversal via .asTraversal().
   *   // Strategy: TeamTraversals.players().andThen(PlayerLenses.score().asTraversal())
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: compose Traversal with a Lens via asTraversal")
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
   * Exercise 4: Filtering with {@code filtered}.
   *
   * <pre>
   *   // Nudge:    filtered narrows a Traversal to elements satisfying a predicate.
   *   // Strategy: playersTraversal.filtered(p -&gt; p.score() &gt; 100)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: filtered narrows a traversal to matching elements")
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
   * Exercise 5: Read every focused value with {@code getAll}.
   *
   * <pre>
   *   // Nudge:    Traversals.getAll(traversal, source) returns List of focused values.
   *   // Strategy: Traversals.getAll(allNames, team)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5: Traversals.getAll extracts all focused values")
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
   * Exercise 6: Two filter predicates in a single traversal chain.
   *
   * <pre>
   *   // Nudge:    The two answerRequired() lines are the predicates passed to filtered().
   *   //           First filters teams (Team::won); second filters players (score &gt;= 100).
   *   // Strategy: .filtered(Team::won) and .filtered(p -&gt; p.score() &gt;= 100)
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6: nested filters across two collection layers")
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
   * Exercise 7: Conditional batch updates inside a single Traversal.
   *
   * <pre>
   *   // Nudge:    Inside the lambda, branch on p.score() to pick the bonus.
   *   // Strategy: int bonus = p.score() &gt;= 100 ? 10 : 5;
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 7: conditional bonus per player inside Traversal.modify")
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
   * Exercise 8: Traversal {@code asFold} for read-only aggregation.
   *
   * <pre>
   *   // Nudge:    filtered narrows to active; compose with score lens; asFold makes it read-only.
   *   // Strategy: TeamTraversals.players().filtered(Player::active)
   *   //               .andThen(PlayerLenses.score().asTraversal()).asFold()
   *   // Spoiler:  exactly that.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 8: convert filtered Traversal to Fold for foldMap")
  void exercise8_traversalAsFold() {
    @GenerateLenses
    record Player(String name, int score, boolean active) {}

    @GenerateLenses
    @GenerateTraversals
    record Team(String name, List<Player> players) {}

    // Manual implementations (simulating what annotation processors would create)
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

    // TODO: Complete these steps:
    // 1. Create a filtered traversal for active players only
    // 2. Compose with the score lens to focus on scores
    // 3. Convert to Fold using asFold()
    // 4. Use foldMap with Monoids.integerAddition() to compute the total

    // Hint: TeamTraversals.players().filtered(Player::active)
    //           .andThen(PlayerLenses.score().asTraversal()).asFold()
    Fold<Team, Integer> activeScoresFold = answerRequired();

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
