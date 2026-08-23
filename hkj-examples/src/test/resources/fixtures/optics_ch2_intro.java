// Fixture for hkj-book/src/optics/ch2_intro.md
//
// The page's payoff snippet composes generated traversals over the
// league/team/player graph the Collections pages share; the records live here
// and the annotation processor generates the *Traversals and *Lenses
// companions during snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.List;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;

@GenerateLenses
record Player(String name, int score) {}

@GenerateLenses
@GenerateTraversals
record Team(String name, List<Player> players) {}

@GenerateLenses
@GenerateTraversals
record League(String name, List<Team> teams) {}

class Fixture {
  static final League league =
      new League(
          "Pro League",
          List.of(
              new Team("Alpha", List.of(new Player("Alice", 100), new Player("Bob", 90))),
              new Team("Bravo", List.of(new Player("Charlie", 110), new Player("Diana", 120)))));
}
