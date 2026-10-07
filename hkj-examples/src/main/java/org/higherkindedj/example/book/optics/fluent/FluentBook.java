// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.fluent;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.fluent.OpticOps;
import org.higherkindedj.optics.focus.TraversalPath;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/fluent_api.html">Updates That Can Fail</a>
 * page. The page {@code {{#include}}}s the anchored regions, and {@code FluentBookTest} holds the
 * claims the page makes about this code.
 */
public final class FluentBook {

  private FluentBook() {}

  // ANCHOR: check_price
  static final BigDecimal MAXIMUM = new BigDecimal("10000");

  static Validated<String, BigDecimal> checkPrice(BigDecimal price) {
    if (price.signum() < 0) {
      return Validated.invalid("Price cannot be negative: " + price);
    }
    return price.compareTo(MAXIMUM) > 0
        ? Validated.invalid("Price exceeds maximum: " + price)
        : Validated.valid(price);
  }

  // ANCHOR_END: check_price

  // ANCHOR: other_checks
  static Either<String, BigDecimal> checkPriceEither(BigDecimal price) {
    return checkPrice(price).toEither();
  }

  static Either<String, String> checkEmail(String email) {
    return email.contains("@") ? Either.right(email) : Either.left("Invalid email: " + email);
  }

  static Maybe<String> normaliseUsername(String username) {
    String trimmed = username.strip();
    return trimmed.length() >= 3 && trimmed.length() <= 20 ? Maybe.just(trimmed) : Maybe.nothing();
  }

  // ANCHOR_END: other_checks

  static List<String> priceErrorsByHand(Order order) {
    // ANCHOR: by_hand
    List<String> errors = new ArrayList<>();
    for (LineItem item : order.items()) {
      if (item.price().signum() < 0) {
        errors.add("Price cannot be negative: " + item.price());
      } else if (item.price().compareTo(MAXIMUM) > 0) {
        errors.add("Price exceeds maximum: " + item.price());
      }
    }
    // ANCHOR_END: by_hand
    return errors;
  }

  /** What the page's every-error block computes. */
  record EveryError(Validated<List<String>, Order> checked, String report) {}

  static EveryError everyError(Order order) {
    // ANCHOR: every_error
    Traversal<Order, BigDecimal> prices =
        OrderFocus.items().via(LineItemFocus.price()).toTraversal();

    Validated<List<String>, Order> checked =
        OpticOps.modifyAllValidated(order, prices, FluentBook::checkPrice);

    String report =
        checked.fold(
            errors -> errors.size() + " invalid prices: " + String.join("; ", errors),
            _ -> "all prices accepted");
    // ANCHOR_END: every_error
    return new EveryError(checked, report);
  }

  /** What the page's fail-fast block computes. */
  record FailFast(Either<String, User> result, String message) {}

  static FailFast failFast(User user) {
    // ANCHOR: fail_fast
    Lens<User, String> email = UserFocus.email().toLens();

    Either<String, User> result = OpticOps.modifyEither(user, email, FluentBook::checkEmail);

    String message = result.fold(error -> "rejected: " + error, u -> "accepted: " + u.email());
    // ANCHOR_END: fail_fast
    return new FailFast(result, message);
  }

  /** What the page's no-detail block computes. */
  record NoDetail(Maybe<User> normalised, User safe) {}

  static NoDetail noDetail(User user) {
    // ANCHOR: no_detail
    Maybe<User> normalised =
        OpticOps.modifyMaybe(user, UserFocus.username().toLens(), FluentBook::normaliseUsername);

    User safe = normalised.orElse(user);
    // ANCHOR_END: no_detail
    return new NoDetail(normalised, safe);
  }

  static Either<String, Order> firstError(Order order) {
    Traversal<Order, BigDecimal> prices =
        OrderFocus.items().via(LineItemFocus.price()).toTraversal();
    // ANCHOR: first_error
    Either<String, Order> firstFailure =
        OpticOps.modifyAllEither(order, prices, FluentBook::checkPriceEither);
    // ANCHOR_END: first_error
    return firstFailure;
  }

  static Either<String, User> register(User user) {
    // ANCHOR: sequential
    Either<String, User> registered =
        OpticOps.modifyEither(user, UserFocus.email().toLens(), FluentBook::checkEmail)
            .flatMap(
                checked ->
                    OpticOps.modifyEither(
                        checked,
                        UserFocus.username().toLens(),
                        name ->
                            name.length() >= 3
                                ? Either.right(name)
                                : Either.left("Username must be at least 3 characters")));
    // ANCHOR_END: sequential
    return registered;
  }

  /** What the page's builder block computes. */
  record Builders(Either<String, User> email, Validated<List<String>, Order> prices) {}

  static Builders builders(User user, Order order) {
    Lens<User, String> email = UserFocus.email().toLens();
    Traversal<Order, BigDecimal> prices =
        OrderFocus.items().via(LineItemFocus.price()).toTraversal();
    // ANCHOR: builders
    Either<String, User> checkedEmail =
        OpticOps.modifyingWithValidation(user).throughEither(email, FluentBook::checkEmail);

    Validated<List<String>, Order> checkedPrices =
        OpticOps.modifyingWithValidation(order).allThroughValidated(prices, FluentBook::checkPrice);
    // ANCHOR_END: builders
    return new Builders(checkedEmail, checkedPrices);
  }

  // ANCHOR: fetch_bonus
  static CompletableFuture<Integer> fetchBonus(int score) {
    return CompletableFuture.completedFuture(score + 10);
  }

  // ANCHOR_END: fetch_bonus

  static CompletableFuture<Team> withBonuses(Team team) {
    // ANCHOR: modify_f
    Applicative<CompletableFutureKind.Witness> futures = Instances.applicative(completableFuture());

    TraversalPath<Team, Integer> scores = TeamFocus.players().via(PlayerFocus.score());

    Kind<CompletableFutureKind.Witness, Team> pending =
        scores.modifyF(score -> FUTURE.widen(fetchBonus(score)), team, futures);

    CompletableFuture<Team> withBonuses = FUTURE.narrow(pending);
    // ANCHOR_END: modify_f
    return withBonuses;
  }

  /** What the page's OpticOps reads, writes and queries compute. */
  record ReadsAndWrites(
      String name,
      List<Integer> scores,
      Optional<Integer> firstScore,
      Person updated,
      Team doubled,
      boolean hasHighScorer,
      boolean allPassed,
      int playerCount,
      boolean noPlayers,
      Optional<Player> top) {}

  static ReadsAndWrites readsAndWrites(Person alice, Team team) {
    // ANCHOR: reads_and_writes
    Traversal<Team, Integer> playerScores = TeamTraversals.players().andThen(PlayerLenses.score());

    // Read
    String name = OpticOps.get(alice, PersonLenses.name());
    List<Integer> scores = OpticOps.getAll(team, playerScores);
    Optional<Integer> firstScore = OpticOps.preview(team, playerScores);

    // Write
    Person updated = OpticOps.set(alice, PersonLenses.age(), 30);
    Team doubled = OpticOps.modifyAll(team, playerScores, score -> score * 2);

    // Query, without modifying anything
    boolean hasHighScorer = OpticOps.exists(team, playerScores, score -> score > 90);
    boolean allPassed = OpticOps.all(team, playerScores, score -> score >= 50);
    int playerCount = OpticOps.count(team, TeamTraversals.players());
    boolean noPlayers = OpticOps.isEmpty(team, TeamTraversals.players());
    Optional<Player> top =
        OpticOps.find(team, TeamTraversals.players(), player -> player.score() > 90);
    // ANCHOR_END: reads_and_writes
    return new ReadsAndWrites(
        name,
        scores,
        firstScore,
        updated,
        doubled,
        hasHighScorer,
        allPassed,
        playerCount,
        noPlayers,
        top);
  }

  /** What the page's two-styles block computes. */
  record Styles(int age, Person older, int sameAge, Person alsoOlder) {}

  static Styles styles(Person alice) {
    // ANCHOR: styles
    // Static style
    int age = OpticOps.get(alice, PersonLenses.age());
    Person older = OpticOps.modify(alice, PersonLenses.age(), a -> a + 1);

    // Builder style
    int sameAge = OpticOps.getting(alice).through(PersonLenses.age());
    Person alsoOlder = OpticOps.modifying(alice).through(PersonLenses.age(), a -> a + 1);
    // ANCHOR_END: styles
    return new Styles(age, older, sameAge, alsoOlder);
  }

  /** What the page's builder-verbs block computes. */
  record Verbs(List<Integer> allScores, Team reset, Team bumped, boolean any) {}

  static Verbs builderVerbs(Team team) {
    Traversal<Team, Integer> playerScores = TeamTraversals.players().andThen(PlayerLenses.score());
    // ANCHOR: builder_verbs
    List<Integer> allScores = OpticOps.getting(team).allThrough(playerScores);
    Team reset = OpticOps.setting(team).allThrough(playerScores, 0);
    Team bumped = OpticOps.modifying(team).allThrough(playerScores, score -> score + 5);
    boolean any = OpticOps.querying(team).anyMatch(playerScores, score -> score > 90);
    // ANCHOR_END: builder_verbs
    return new Verbs(allScores, reset, bumped, any);
  }

  static Person classify(Person alice) {
    // ANCHOR: conditional
    Person classified =
        OpticOps.get(alice, PersonLenses.age()) >= 18
            ? OpticOps.set(alice, PersonLenses.status(), "ADULT")
            : alice;
    // ANCHOR_END: conditional
    return classified;
  }

  /** What the page's filtered block computes. */
  record Starred(Team starred, List<Player> stars) {}

  static Starred starTopPerformers(Team team) {
    // ANCHOR: filtered
    Traversal<Team, Player> topPerformers =
        TeamTraversals.players().filtered(player -> player.score() >= 90);

    Team starred = OpticOps.setAll(team, topPerformers.andThen(PlayerLenses.status()), "STAR");

    List<Player> stars = OpticOps.getAll(starred, topPerformers);
    // ANCHOR_END: filtered
    return new Starred(starred, stars);
  }

  static int totalScore(Team team) {
    // ANCHOR: aggregate
    int total =
        TeamTraversals.players()
            .andThen(PlayerLenses.score())
            .asFold()
            .foldMap(Monoids.integerAddition(), score -> score, team);
    // ANCHOR_END: aggregate
    return total;
  }

  static List<String> highScorerNames(Team team) {
    // ANCHOR: streams
    List<String> highScorerNames =
        OpticOps.getting(team).allThrough(TeamTraversals.players()).stream()
            .filter(player -> player.score() > 90)
            .map(Player::name)
            .toList();
    // ANCHOR_END: streams
    return highScorerNames;
  }

  static List<List<Integer>> composeOnce(List<Team> teams) {
    // ANCHOR: compose_once
    // Compose once, before the loop
    Traversal<Team, Integer> scores = TeamTraversals.players().andThen(PlayerLenses.score());

    List<List<Integer>> allScores = new ArrayList<>();
    for (Team team : teams) {
      allScores.add(OpticOps.getAll(team, scores));
    }
    // ANCHOR_END: compose_once
    return allScores;
  }
}

// ANCHOR: records
@GenerateFocus
record LineItem(String sku, BigDecimal price) {}

@GenerateFocus
record Order(String id, List<LineItem> items) {}

@GenerateFocus
record User(String username, String email) {}

// ANCHOR_END: records

// ANCHOR: team_records
@GenerateLenses
record Person(String name, int age, String status) {}

@GenerateLenses
@GenerateFocus
record Player(String name, int score, String status) {}

@GenerateFocus
@GenerateTraversals
record Team(String name, List<Player> players) {}
// ANCHOR_END: team_records
