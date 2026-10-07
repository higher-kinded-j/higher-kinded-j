// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.fluent;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.fluent.OpticOps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Updates That Can Fail page makes about its examples. */
@DisplayName("the Updates That Can Fail page")
class FluentBookTest {

  private static final Order BAD_PRICES =
      new Order(
          "ORD-1",
          List.of(
              new LineItem("A", new BigDecimal("-10.00")),
              new LineItem("B", new BigDecimal("25.00")),
              new LineItem("C", new BigDecimal("15000.00"))));
  private static final Order GOOD_PRICES =
      new Order("ORD-2", List.of(new LineItem("A", new BigDecimal("25.00"))));
  private static final User ALICE = new User("  alice  ", "alice@example.com");
  private static final Person PERSON = new Person("Alice", 25, "ACTIVE");
  private static final Team TEAM =
      new Team(
          "Wildcats", List.of(new Player("Alice", 100, "ACTIVE"), new Player("Bob", 85, "ACTIVE")));

  @Test
  @DisplayName("every bad price is reported, in order, and a good order comes back unchanged")
  void everyError() {
    FluentBook.EveryError bad = FluentBook.everyError(BAD_PRICES);

    assertThat(bad.checked())
        .isEqualTo(
            Validated.invalid(
                List.of("Price cannot be negative: -10.00", "Price exceeds maximum: 15000.00")));
    assertThat(bad.report())
        .isEqualTo(
            "2 invalid prices: Price cannot be negative: -10.00; Price exceeds maximum: 15000.00");
    assertThat(FluentBook.everyError(GOOD_PRICES).checked())
        .isEqualTo(Validated.valid(GOOD_PRICES));
  }

  @Test
  @DisplayName("the hand-written loop finds the same two errors as modifyAllValidated")
  void byHand() {
    assertThat(FluentBook.priceErrorsByHand(BAD_PRICES))
        .containsExactly("Price cannot be negative: -10.00", "Price exceeds maximum: 15000.00");
    assertThat(FluentBook.priceErrorsByHand(GOOD_PRICES)).isEmpty();
  }

  @Test
  @DisplayName("modifyEither accepts a good email and rejects a bad one with its reason")
  void failFast() {
    assertThat(FluentBook.failFast(ALICE).message()).isEqualTo("accepted: alice@example.com");
    assertThat(FluentBook.failFast(new User("bob", "bob.example.com")).result())
        .isEqualTo(Either.left("Invalid email: bob.example.com"));
  }

  @Test
  @DisplayName("modifyMaybe trims a good username, and gives Nothing for one of the wrong length")
  void noDetail() {
    FluentBook.NoDetail trimmed = FluentBook.noDetail(ALICE);
    User tooShort = new User("al", "al@example.com");

    assertThat(trimmed.normalised()).isEqualTo(Maybe.just(new User("alice", "alice@example.com")));
    assertThat(FluentBook.noDetail(tooShort).normalised()).isEqualTo(Maybe.nothing());
    assertThat(FluentBook.noDetail(tooShort).safe()).isEqualTo(tooShort);
  }

  @Test
  @DisplayName("modifyAllEither keeps the first error, though it validates every price")
  void firstError() {
    List<BigDecimal> seen = new ArrayList<>();
    Traversal<Order, BigDecimal> prices =
        OrderFocus.items().via(LineItemFocus.price()).toTraversal();

    Either<String, Order> firstFailure =
        OpticOps.modifyAllEither(
            BAD_PRICES,
            prices,
            price -> {
              seen.add(price);
              return FluentBook.checkPriceEither(price);
            });

    assertThat(FluentBook.firstError(BAD_PRICES))
        .isEqualTo(Either.left("Price cannot be negative: -10.00"));
    assertThat(firstFailure).isEqualTo(Either.left("Price cannot be negative: -10.00"));
    assertThat(seen)
        .containsExactly(
            new BigDecimal("-10.00"), new BigDecimal("25.00"), new BigDecimal("15000.00"));
  }

  @Test
  @DisplayName("registration checks the email, then the username, and stops at the first failure")
  void sequential() {
    assertThat(FluentBook.register(new User("alice", "alice@example.com")))
        .isEqualTo(Either.right(new User("alice", "alice@example.com")));
    assertThat(FluentBook.register(new User("al", "al@example.com")))
        .isEqualTo(Either.left("Username must be at least 3 characters"));
    assertThat(FluentBook.register(new User("al", "al.example.com")))
        .isEqualTo(Either.left("Invalid email: al.example.com"));
  }

  @Test
  @DisplayName("the builders give the same results as the static methods")
  void builders() {
    FluentBook.Builders builders = FluentBook.builders(ALICE, BAD_PRICES);

    assertThat(builders.email()).isEqualTo(FluentBook.failFast(ALICE).result());
    assertThat(builders.prices()).isEqualTo(FluentBook.everyError(BAD_PRICES).checked());
  }

  @Test
  @DisplayName("modifyF on a path adds each bonus inside a CompletableFuture")
  void modifyF() {
    assertThat(FluentBook.withBonuses(TEAM).join().players())
        .extracting(Player::score)
        .containsExactly(110, 95);
  }

  @Test
  @DisplayName("OpticOps reads, writes and queries through generated optics")
  void readsAndWrites() {
    FluentBook.ReadsAndWrites result = FluentBook.readsAndWrites(PERSON, TEAM);

    assertThat(result.name()).isEqualTo("Alice");
    assertThat(result.scores()).containsExactly(100, 85);
    assertThat(result.firstScore()).contains(100);
    assertThat(result.updated().age()).isEqualTo(30);
    assertThat(result.doubled().players()).extracting(Player::score).containsExactly(200, 170);
    assertThat(result.hasHighScorer()).isTrue();
    assertThat(result.allPassed()).isTrue();
    assertThat(result.playerCount()).isEqualTo(2);
    assertThat(result.noPlayers()).isFalse();
    assertThat(result.top()).map(Player::name).contains("Alice");
  }

  @Test
  @DisplayName("the static and builder styles compute the same values")
  void styles() {
    FluentBook.Styles styles = FluentBook.styles(PERSON);

    assertThat(styles.age()).isEqualTo(styles.sameAge()).isEqualTo(25);
    assertThat(styles.older()).isEqualTo(styles.alsoOlder());
    assertThat(styles.older().age()).isEqualTo(26);
  }

  @Test
  @DisplayName("the four builders read, set, modify and query every score")
  void builderVerbs() {
    FluentBook.Verbs verbs = FluentBook.builderVerbs(TEAM);

    assertThat(verbs.allScores()).containsExactly(100, 85);
    assertThat(verbs.reset().players()).extracting(Player::score).containsExactly(0, 0);
    assertThat(verbs.bumped().players()).extracting(Player::score).containsExactly(105, 90);
    assertThat(verbs.any()).isTrue();
  }

  @Test
  @DisplayName("an adult's status is set, and a minor comes back as it was")
  void conditional() {
    Person minor = new Person("Tom", 12, "ACTIVE");

    assertThat(FluentBook.classify(PERSON).status()).isEqualTo("ADULT");
    assertThat(FluentBook.classify(minor)).isEqualTo(minor);
  }

  @Test
  @DisplayName("a filtered traversal stars only the top performers")
  void filtered() {
    FluentBook.Starred result = FluentBook.starTopPerformers(TEAM);

    assertThat(result.starred().players())
        .extracting(Player::status)
        .containsExactly("STAR", "ACTIVE");
    assertThat(result.stars()).extracting(Player::name).containsExactly("Alice");
  }

  @Test
  @DisplayName("a fold sums the scores, and a stream picks the high scorers' names")
  void aggregateAndStream() {
    assertThat(FluentBook.totalScore(TEAM)).isEqualTo(185);
    assertThat(FluentBook.highScorerNames(TEAM)).containsExactly("Alice");
  }

  @Test
  @DisplayName("an optic composed once above the loop reads every team")
  void composeOnce() {
    Team other = new Team("Owls", List.of(new Player("Cara", 70, "ACTIVE")));

    assertThat(FluentBook.composeOnce(List.of(TEAM, other)))
        .containsExactly(List.of(100, 85), List.of(70));
  }
}
