// Fixture for hkj-book/src/optics/fluent_api.md
//
// The page drives OpticOps over a small person/team/order domain; the records
// live here and the annotation processor generates the *Lenses and *Traversals
// companions during snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.fluent.OpticOps;

@GenerateLenses
record Person(String name, int age, String status) {}

@GenerateLenses
record Player(String name, int score) {}

@GenerateLenses
@GenerateTraversals
record Team(String name, List<Player> players) {}

@GenerateLenses
record User(String username, String email, Optional<String> bio) {}

@GenerateLenses
@GenerateTraversals
record Order(String orderId, List<BigDecimal> itemPrices) {}

class Fixture {
  static final Person alice = new Person("Alice", 25, "ACTIVE");

  static final Team team = new Team("Wildcats", List.of(new Player("Alice", 100), new Player("Bob", 85)));

  static final User user = new User("alice", "alice@example.com", Optional.of("engineer"));

  static final Order order =
      new Order("ORD-123", List.of(new BigDecimal("-10.00"), new BigDecimal("15000.00")));

  static final Traversal<Team, Integer> playerScores =
      TeamTraversals.players().andThen(PlayerLenses.score().asTraversal());

  static final Traversal<Order, BigDecimal> orderPrices = OrderTraversals.itemPrices();

  static Either<String, String> validateEmail(String email) {
    return email.contains("@") ? Either.right(email) : Either.left("Invalid email: " + email);
  }

  static Maybe<String> normaliseUsername(String username) {
    String trimmed = username.trim();
    return trimmed.length() >= 3 && trimmed.length() <= 20 ? Maybe.just(trimmed) : Maybe.nothing();
  }

  static Validated<String, BigDecimal> validatePrice(BigDecimal price) {
    if (price.compareTo(BigDecimal.ZERO) < 0) {
      return Validated.invalid("Price cannot be negative: " + price);
    }
    return price.compareTo(new BigDecimal("10000")) > 0
        ? Validated.invalid("Price exceeds maximum: " + price)
        : Validated.valid(price);
  }

  static Either<String, BigDecimal> validatePriceEither(BigDecimal price) {
    return validatePrice(price).toEither();
  }

  static Validated<Integer, Integer> validateScore(int score) {
    return score >= 0 ? Validated.valid(score) : Validated.invalid(score);
  }

  static CompletableFuture<Integer> fetchBonus(int score) {
    return CompletableFuture.completedFuture(score + 10);
  }
}
