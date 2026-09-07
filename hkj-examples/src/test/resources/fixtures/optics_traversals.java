// Fixture for hkj-book/src/optics/traversals.md
//
// The page runs a league of teams and players from end to end, and reaches for a product catalogue
// for `partsOf`, a company for effectful validation and a project for `reversed`. Every model the
// page names but does not declare is declared here; a snippet that shows one shadows this copy.
//
// The optics in `Fixture` and `LeagueOptics` are written by hand rather than taken from the
// generated companions: a snippet that shows the model shadows the annotated records above, and in
// that unit there is no companion to take them from.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
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

@GenerateLenses
record Product(String name, double price, String tag, int stockLevel) {}

@GenerateLenses
@GenerateTraversals
record Category(String name, List<Product> products) {}

@GenerateLenses
@GenerateTraversals
record Catalogue(String name, List<Category> categories) {}

@GenerateLenses
record Task(String title, int priority) {}

@GenerateLenses
@GenerateTraversals
record Project(String name, List<Task> tasks) {}

@GenerateLenses
record ContactInfo(String email, String phone) {}

@GenerateLenses
@GenerateTraversals
record Employee(String name, List<ContactInfo> contactInfo) {}

@GenerateLenses
@GenerateTraversals
record Company(String name, List<Employee> employees) {}

class StatsService {

  CompletableFuture<Integer> recalculate(int score) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class LeagueOptics {

  static final Traversal<League, Integer> ALL_PLAYER_SCORES =
      Fixture.leagueToPlayers.andThen(Fixture.playerToScore.asTraversal());

  static final Traversal<League, String> ALL_PLAYER_NAMES =
      Fixture.leagueToPlayers.andThen(Fixture.playerToName.asTraversal());
}

class Fixture {

  static final Lens<Player, Integer> playerToScore =
      Lens.of(Player::score, (p, v) -> new Player(p.name(), v));

  static final Lens<Player, String> playerToName =
      Lens.of(Player::name, (p, v) -> new Player(v, p.score()));

  static final Lens<Team, List<Player>> teamToPlayerList =
      Lens.of(Team::players, (t, v) -> new Team(t.name(), v));

  static final Lens<League, List<Team>> leagueToTeamList =
      Lens.of(League::teams, (l, v) -> new League(l.name(), v));

  static final Traversal<League, Team> leagueToTeams =
      leagueToTeamList.asTraversal().andThen(Traversals.forList());

  static final Traversal<League, Player> leagueToPlayers =
      leagueToTeams.andThen(teamToPlayerList.asTraversal()).andThen(Traversals.forList());

  static final Traversal<League, Integer> leagueToAllPlayerScores =
      leagueToPlayers.andThen(playerToScore.asTraversal());

  static final Player player = new Player("Alice", 100);

  static final Team team1 = new Team("Team Alpha", List.of(player, new Player("Bob", 90)));

  static final Team team2 =
      new Team("Team Bravo", List.of(new Player("Charlie", 110), new Player("Diana", 120)));

  static final List<Team> teams = List.of(team1, team2);

  static final League league = new League("Pro League", teams);

  static final List<Product> products =
      List.of(
          new Product("Widget", 100.0, "tools", 5),
          new Product("Gadget", 200.0, "tools", 0),
          new Product("Gizmo", 300.0, "toys", 2),
          new Product("Doohickey", 400.0, "toys", 7),
          new Product("Thingummy", 500.0, "spares", 0));

  static final Catalogue catalogue =
      new Catalogue("Autumn", List.of(new Category("Everything", products)));

  static final Lens<Product, Double> productToPrice =
      Lens.of(Product::price, (p, v) -> new Product(p.name(), v, p.tag(), p.stockLevel()));

  static final Lens<Product, String> productToName =
      Lens.of(Product::name, (p, v) -> new Product(v, p.price(), p.tag(), p.stockLevel()));

  static final Traversal<List<Product>, Double> priceTraversal =
      Traversals.<Product>forList().andThen(productToPrice.asTraversal());

  static final Traversal<List<Product>, String> nameTraversal =
      Traversals.<Product>forList().andThen(productToName.asTraversal());

  static final Lens<List<Product>, List<Double>> productPrices =
      Traversals.partsOf(priceTraversal);

  static final Traversal<Catalogue, Double> allPrices =
      Lens.<Catalogue, List<Category>>of(Catalogue::categories, (c, v) -> new Catalogue(c.name(), v))
          .asTraversal()
          .andThen(Traversals.forList())
          .andThen(
              Lens.<Category, List<Product>>of(
                      Category::products, (c, v) -> new Category(c.name(), v))
                  .asTraversal())
          .andThen(Traversals.forList())
          .andThen(productToPrice.asTraversal());

  static final Lens<Catalogue, List<Double>> pricesLens = Traversals.partsOf(allPrices);

  static final Project project =
      new Project("Apollo", List.of(new Task("Design", 1), new Task("Build", 2)));

  static final Company company =
      new Company(
          "Initech", List.of(new Employee("Alice", List.of(new ContactInfo("A@X.com", "1")))));

  static final StatsService statsService = new StatsService();
}
