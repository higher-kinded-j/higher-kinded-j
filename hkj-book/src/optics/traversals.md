# Traversals: Practical Guide

## _Handling Bulk Updates_

~~~admonish
[TraversalUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/TraversalUsageExample.java)
~~~

So far, our journey through optics has shown us how to handle singular focus:

* A **`Lens`** targets a part that *must* exist.
* A **`Prism`** targets a part that *might* exist in one specific shape.
* An **`Iso`** provides a two-way bridge between *equivalent* types.

But what about operating on *many* items at once? How do we apply a single change to every element in a nested list? For this, we need the most general and powerful optic in our toolkit: the **Traversal**.

## The Scenario: Updating an Entire League 🗺️

A `Traversal` is a functional "search-and-replace." It gives you a single tool to focus on zero or more items within a larger structure, allowing you to `get`, `set`, or `modify` all of them in one go.

This makes it the perfect optic for working with collections. Consider this data model of a sports league:

**The Data Model:**

```java
public record Player(String name, int score) {}
public record Team(String name, List<Player> players) {}
public record League(String name, List<Team> teams) {}
```

**Our Goal:** We need to give every single player in the entire league 5 bonus points. The traditional approach involves nested loops or streams, forcing us to manually reconstruct each immutable object along the way.

```java
// Manual, verbose bulk update
List<Team> newTeams = league.teams().stream()
    .map(team -> {
        List<Player> newPlayers = team.players().stream()
            .map(player -> new Player(player.name(), player.score() + 5))
            .collect(Collectors.toList());
        return new Team(team.name(), newPlayers);
    })
    .collect(Collectors.toList());
League updatedLeague = new League(league.name(), newTeams);
```

This code is deeply nested and mixes the *what* (add 5 to a score) with the *how* (looping, collecting, and reconstructing). A `Traversal` lets us abstract away the "how" completely.

---

## A Step-by-Step Walkthrough

### Step 1: Generating Traversals

The library provides a rich set of tools for creating `Traversal` instances, found in the **`Traversals`** utility class and through annotations.

* **`@GenerateTraversals`**: Annotating a record will automatically generate a `Traversal` for any `Iterable` field (like `List` or `Set`).
* **`Traversals.forList()`**: A static helper that creates a traversal for the elements of a `List`.
* **`Traversals.forMap(key)`**: A static helper that creates a traversal focusing on the value for a specific key in a `Map`.

```java
import org.higherkindedj.optics.annotations.GenerateTraversals;
import java.util.List;

// We also add @GenerateLenses to get access to player fields
@GenerateLenses
public record Player(String name, int score) {}

@GenerateLenses
@GenerateTraversals // Traversal for List<Player>
public record Team(String name, List<Player> players) {}

@GenerateLenses
@GenerateTraversals // Traversal for List<Team>
public record League(String name, List<Team> teams) {}
```

### Step 2: Composing a Deep Traversal

Just like other optics, `Traversal`s can be composed with `andThen`. We can chain them together to create a single, deep traversal from the `League` all the way down to each player's `score`.

```java
// Get generated optics
Traversal<League, Team> leagueToTeams = LeagueTraversals.teams();
Traversal<Team, Player> teamToPlayers = TeamTraversals.players();
Lens<Player, Integer> playerToScore = PlayerLenses.score();

// Compose them to create a single, deep traversal.
Traversal<League, Integer> leagueToAllPlayerScores =
    leagueToTeams
        .andThen(teamToPlayers)
        .andThen(playerToScore.asTraversal()); // Convert the final Lens
```

The result is a single `Traversal<League, Integer>` that declaratively represents the path to all player scores.

### Step 3: Using the Traversal with Helper Methods

The `Traversals` utility class provides convenient helper methods to perform the most common operations.

* **`Traversals.modify(traversal, function, source)`**: Applies a pure function to all targets of a traversal.

```java
  // Use the composed traversal to add 5 bonus points to every score.
  League updatedLeague = Traversals.modify(leagueToAllPlayerScores, score -> score + 5, league);
```

* **`Traversals.getAll(traversal, source)`**: Extracts all targets of a traversal into a `List`.

```java
  // Get a flat list of all player scores in the league.
  List<Integer> allScores = Traversals.getAll(leagueToAllPlayerScores, league);
  // Result: [100, 90, 110, 120]
```

---

## Complete, Runnable Example

This example demonstrates how to use the `with*` helpers for a targeted update and how to use a composed `Traversal` with the new utility methods for bulk operations.

```java
package org.higherkindedj.example.optics;

import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;

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
        var team2 = new Team("Team Bravo", List.of(new Player("Charlie", 110), new Player("Diana", 120)));
        var league = new League("Pro League", List.of(team1, team2));
  
        System.out.println("Original League: " + league);
        System.out.println("------------------------------------------");
  
        // --- SCENARIO 1: Using `with*` helpers for a targeted, shallow update ---
        var teamToUpdate = league.teams().get(0);
        var updatedTeam = TeamLenses.withName(teamToUpdate, "Team Omega");
        var newTeamsList = new ArrayList<>(league.teams());
        newTeamsList.set(0, updatedTeam);
        var leagueWithUpdatedTeam = LeagueLenses.withTeams(league, newTeamsList);
  
        System.out.println("After updating one team's name with `with*` helpers:");
        System.out.println(leagueWithUpdatedTeam);
        System.out.println("------------------------------------------");
  
        // --- SCENARIO 2: Using composed Traversals for deep, bulk updates ---
        Traversal<League, Integer> leagueToAllPlayerScores =
            LeagueTraversals.teams()
                .andThen(TeamTraversals.players())
                .andThen(PlayerLenses.score().asTraversal());
  
        // Use the `modify` helper to add 5 bonus points to every score.
        League updatedLeague = Traversals.modify(leagueToAllPlayerScores, score -> score + 5, league);
  
        System.out.println("After `modify` (adding 5 points to each score):");
        System.out.println(updatedLeague);
    }
}
```

**Expected Output:**

```
Original League: League[name=Pro League, teams=[Team[name=Team Alpha, players=[Player[name=Alice, score=100], Player[name=Bob, score=90]]], Team[name=Team Bravo, players=[Player[name=Charlie, score=110], Player[name=Diana, score=120]]]]]
------------------------------------------
After `modifyF` (adding 5 points to each score):
League[name=Pro League, teams=[Team[name=Team Alpha, players=[Player[name=Alice, score=105], Player[name=Bob, score=95]]], Team[name=Team Bravo, players=[Player[name=Charlie, score=115], Player[name=Diana, score=125]]]]]
```

---

### Unifying the Concepts

A `Traversal` is the most general of the core optics. In fact, all other optics can be seen as specialised `Traversal`s:

* A `Lens` is just a `Traversal` that always focuses on **exactly one** item.
* A `Prism` is just a `Traversal` that focuses on **zero or one** item.
* An `Iso` is just a `Traversal` that focuses on **exactly one** item and is reversible.

This is the reason they can all be composed together so seamlessly. By mastering `Traversal`, you complete your understanding of the core optics family, enabling you to build powerful, declarative, and safe data transformations.
