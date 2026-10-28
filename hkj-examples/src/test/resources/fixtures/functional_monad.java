// Fixture for hkj-book/src/functional/monad.md
//
// The page opens with the flatMap a reader already writes on four containers, so this fixture
// declares those containers and the lookups they chain. It also supplies the types Monad's
// signature names. It must not import Monad itself, because the signature snippet declares it. The
// page's other code is included from compiled examples.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.Payment;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;

class Fixture {

  static final Optional<Order> optional = Optional.empty();

  static final CompletableFuture<Order> future = new CompletableFuture<>();

  static final List<Order> orders = List.of();

  static final MaybePath<Order> maybePath = Path.nothing();

  static Optional<Payment> findPayment(Order order) {
    return Optional.empty();
  }

  static CompletableFuture<Payment> fetchPayment(Order order) {
    return new CompletableFuture<>();
  }

  static MaybePath<Payment> paymentOf(Order order) {
    return Path.nothing();
  }
}
