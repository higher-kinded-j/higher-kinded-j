// Fixture for hkj-book/src/tutorials/optics/traversals_journey.md
//
// The journey traverses a league of teams of players and filters a list of users; the optics it
// composes are generated from the records declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.List;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;

record Address(String street, String city) {}

@GenerateLenses
record Player(String name, int score) {}

@GenerateLenses
@GenerateTraversals
record Team(String name, List<Player> players) {}

@GenerateLenses
@GenerateTraversals
record League(String name, List<Team> teams) {}

@GenerateLenses
record User(String name, String email, boolean active) {

  boolean isActive() {
    return active;
  }
}

@GenerateTraversals
record Directory(List<User> users) {}

class Fixture {

  static final League league = new League("Premier", List.of());

  static final Team team = new Team("Rovers", List.of());

  static final Traversal<League, Team> leagueToTeams = LeagueTraversals.teams();

  static final Traversal<Team, Player> teamToPlayers = TeamTraversals.players();

  static final Lens<Player, Integer> playerToScore = PlayerLenses.score();

  static final Traversal<Team, Integer> scoresTraversal =
      teamToPlayers.andThen(playerToScore);

  static final Traversal<Directory, User> usersTraversal = DirectoryTraversals.users();

  static final Lens<User, String> emailLens = UserLenses.email();
}
