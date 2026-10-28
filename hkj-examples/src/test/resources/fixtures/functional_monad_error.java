// Fixture for hkj-book/src/functional/monad_error.md
//
// The page opens with a lookup that throws a checked exception, refused inside a lambda, then the
// same lookup with a typed error, and a CompletableFuture raising and recovering. This fixture
// declares the exception, the lookups, the ids they are mapped over and the future. It also
// supplies the types MonadError's signature names. It must not import MonadError itself, because
// the signature snippet declares it. The page's other code is included from compiled examples.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;

/** A checked exception, as a repository written with exceptions might declare. */
final class OrderNotFoundException extends Exception {
  OrderNotFoundException(UUID id) {
    super("no order " + id);
  }
}

class Fixture {

  static final List<UUID> ids = List.of();

  static final UUID id = UUID.randomUUID();

  static final CompletableFuture<Order> future = new CompletableFuture<>();

  static final Order placeholder = null;

  static Order findOrThrow(UUID id) throws OrderNotFoundException {
    throw new OrderNotFoundException(id);
  }

  static Either<String, Order> find(UUID id) {
    return Either.left("no order " + id);
  }

  static CompletableFuture<Order> fetchFromArchive(UUID id) {
    return new CompletableFuture<>();
  }
}
